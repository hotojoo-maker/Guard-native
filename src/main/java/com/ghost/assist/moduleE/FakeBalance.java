package com.ghost.assist.moduleE;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;

import com.ghost.assist.core.Bridge;
import com.ghost.assist.core.RiskState;
import com.ghost.assist.core.StateMachine;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * E3 改余额 —— 全局伪造钱包/零钱余额显示（装b，纯显示层，不碰真钱/支付）。
 *
 * L1 动态实证（2026-06-30，探针 tv_probe.js / e3_hook_probe.js）：余额数字跨两套控件，
 * 多页面（服务页 MallIndexUIv2 / 钱包首页 / 零钱专页）共用同一套赋值入口：
 *  ① com.tencent.mm.plugin.wallet_core.ui.view.WcPayMoneyLoadingView（字符串·元，内含 robinhood TickerView）
 *     金额入口：setMoney/setNewMoney/setFirstMoney(String) + 混淆 e/g/f(String,...)；setPrefixSymbol(String) 是 ¥ 符号须排除。
 *  ② com.tencent.kinda.framework.widget.base.KindaMoneyLoadingView（long·分）：setMoney(long,boolean)。
 *
 * 【门控】杂项功能口径（同 E2 定位 / C1 防撤回）：isVipAuthorized() && isEditBalanceEnabled() && 已填金额，不绑 H/V。
 *   散沙降级 RiskState.isTamperDegraded() → 盗版失效（正版恒 false 不误伤，铁律29）。
 * 【排除】零钱通（收益类账户）显示真实值：isExcludedAccount() 按控件所在行/页相邻文本含「零钱通」判定跳过。
 * 【红线】只 beforeHook 改入参显示值，不读回、不碰支付数据/网络/真实余额。
 */
public final class FakeBalance {

    private static final String TAG = "NCL";

    private static final String WCPAY_CLASS = "com.tencent.mm.plugin.wallet_core.ui.view.WcPayMoneyLoadingView";

    // 零钱通（收益类账户）排除：L1 view 树实证（wallet dump 2026-07-09），零钱与零钱通金额
    // 同走 WcPayMoneyLoadingView，动态 id（0xb/0xc/0xd）跨版本不稳；唯一稳的区分 = 控件所在
    // 行/页的相邻标签文本是否含「零钱通」。"零钱通" 串本身包含 "零钱"，判定时须先判前者。
    private static final String LABEL_LQT = "\u96f6\u94b1\u901a"; // 零钱通
    private static final String LABEL_LQ  = "\u96f6\u94b1";       // 零钱

    private FakeBalance() {}

    public static void install(XC_LoadPackage.LoadPackageParam lpparam, ClassLoader cl) {
        // 只在 WcPayMoneyLoadingView（唯一最终显示层）改：L1 实证 wallet_trace 2026-07-09——
        // 零钱/零钱通/服务页余额最终都经 WcPay.f(String) 渲染，且该层能可靠区分零钱通。
        // 不再 hook KindaMoneyLoadingView.setMoney：它在 onCreateLayout 早期（控件尚未挂到行）触发、
        // 认不出零钱通，在该层改会污染零钱通并穿透到 WcPay 显示（本 bug 根因）。
        installWcPay(cl);
    }

    /** 当前是否应改余额：授权 + 未散沙降级 + 开关开 + 已填自定义金额（留空=不改，显示真实余额）。 */
    private static boolean shouldFake() {
        try {
            if (!StateMachine.getInstance().isVipAuthorized()) return false;
            if (RiskState.isTamperDegraded()) return false;
            if (!Bridge.getInstance().isEditBalanceEnabled()) return false;
            String v = Bridge.getInstance().getFakeBalanceYuan();
            return v != null && !v.isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    /** 假余额（元，字符串）。仅在 shouldFake()=true（已填值）时被调用，返回设置页填入的金额。 */
    private static String fakeYuan() {
        try {
            String v = Bridge.getInstance().getFakeBalanceYuan();
            if (v != null) return v;
        } catch (Throwable ignored) {}
        return "";
    }

    // 控件分类记忆：某控件一旦被明确识别为零钱通(1)/零钱(-1)，后续即使某次认不出(0)也沿用，
    // 防止 onCreateLayout 等中间态 classify=0 时把零钱通误判成通用余额而改值。WeakHashMap 随控件回收。
    private static final java.util.Map<View, Integer> sRowMemo =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<View, Integer>());

    /**
     * 是否应对该金额控件改值——用户口径：零钱通显示真实值，其余照改。
     *
     * 关键（L1 实证 2026-07-09）：setMoney 会在控件「尚未 attach、行标签还搜不到」时先被调用一次，
     * 若此时就改，会把零钱通提前污染成假值，之后 attach 再排除也来不及（微信只重刷当前显示值）。
     * 因此：
     *   行内识别为「零钱通」→ 不改；
     *   识别为「零钱」→ 改；
     *   认不出（无标签）→ 仅当已 attach（= 转账/红包等通用金额页）才改；未 attach 一律先不改，
     *     等 attach 后的调用再判定。零钱通全程不会被提前污染。
     */
    private static boolean shouldChangeMoney(Object moneyView) {
        try {
            if (!(moneyView instanceof View)) return true;
            View v = (View) moneyView;
            int d = classifyRow(v);
            if (d != 0) {
                sRowMemo.put(v, Integer.valueOf(d));         // 记住明确分类
            } else {
                Integer memo = sRowMemo.get(v);              // 中间态：沿用历史分类，防误判
                if (memo != null) d = memo.intValue();
            }
            boolean attached = v.isAttachedToWindow();
            if (d == 1) return false;            // 零钱通 → 不改
            if (d == -1) return true;            // 零钱 → 改
            return attached;                     // 纯未知：attach=通用金额页改；未 attach 先不改
        } catch (Throwable t) {
            return true;
        }
    }

    /** 从控件往上逐层找所在行/卡片：子树文本含「零钱通」→1；先命中「零钱」→-1；找不到→0。命中即停。 */
    private static int classifyRow(View moneyView) {
        ViewParent p = moneyView.getParent();
        for (int depth = 0; depth < 8 && p instanceof View; depth++) {
            View pv = (View) p;
            int hit = scanRowLabel(pv);
            if (hit != 0) return hit;
            p = pv.getParent();
        }
        return 0;
    }

    /**
     * 搜子树 TextView 的「行标题」：精确等于「零钱通」→1；精确等于「零钱」→-1；否则 0（继续往上找）。
     * 必须精确 equals、不能 contains：L1 实证（wv_lqpage 2026-07-09）「我的零钱」页含推广文案
     * 「转入零钱通，能赚又能花」，contains 会把它误判成零钱通、把该页误排除。只认单独成行的标题。
     */
    private static int scanRowLabel(View row) {
        java.util.ArrayList<String> texts = new java.util.ArrayList<>();
        collectTexts(row, texts, 0);
        boolean hasLqt = false, hasLq = false;
        for (int i = 0; i < texts.size(); i++) {
            String s = texts.get(i);
            if (s.equals(LABEL_LQT)) hasLqt = true;
            else if (s.equals(LABEL_LQ)) hasLq = true;
        }
        if (hasLqt) return 1;
        if (hasLq) return -1;
        return 0;
    }

    private static void collectTexts(View v, java.util.ArrayList<String> out, int depth) {
        if (v == null || depth > 5 || out.size() > 20) return;
        if (v instanceof TextView) {
            CharSequence t = ((TextView) v).getText();
            if (t != null) {
                String s = t.toString().trim();
                if (!s.isEmpty()) out.add(s);
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) collectTexts(vg.getChildAt(i), out, depth + 1);
        }
    }

    // ① WcPayMoneyLoadingView（字符串·元）：遍历声明方法，hook「首参 String 的金额 setter」。
    //    用名字关键词排除 ¥符号/字体/颜色等非金额 String 方法；升版混淆名(e/g/f)变了也能继续命中。
    private static void installWcPay(ClassLoader cl) {
        try {
            Class<?> clazz = XposedHelpers.findClass(WCPAY_CLASS, cl);
            int hooked = 0;
            for (Method m : clazz.getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 0 || p[0] != String.class) continue;
                String lower = m.getName().toLowerCase();
                if (lower.contains("symbol") || lower.contains("prefix") || lower.contains("font")
                        || lower.contains("color") || lower.contains("typeface") || lower.contains("style")) {
                    continue; // 非金额 String setter
                }
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            if (!shouldFake()) return;
                            if (!shouldChangeMoney(param.thisObject)) return;
                            param.args[0] = fakeYuan();
                        } catch (Throwable t) {
                            Log.w(TAG, "[FBAL] wcpay err: " + t);
                        }
                    }
                });
                hooked++;
            }
            Log.i(TAG, "[FBAL] WcPay hooked setters=" + hooked);
        } catch (Throwable t) {
            Log.w(TAG, "[FBAL] WcPay install fail: " + t);
        }
    }

}
