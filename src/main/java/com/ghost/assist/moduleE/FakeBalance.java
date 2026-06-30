package com.ghost.assist.moduleE;

import android.util.Log;

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
 * 【红线】只 beforeHook 改入参显示值，不读回、不碰支付数据/网络/真实余额。
 */
public final class FakeBalance {

    private static final String TAG = "NCL";

    private static final String WCPAY_CLASS = "com.tencent.mm.plugin.wallet_core.ui.view.WcPayMoneyLoadingView";
    private static final String KINDA_CLASS = "com.tencent.kinda.framework.widget.base.KindaMoneyLoadingView";

    private FakeBalance() {}

    public static void install(XC_LoadPackage.LoadPackageParam lpparam, ClassLoader cl) {
        installWcPay(cl);
        installKinda(cl);
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

    /** 假余额（分，long）—— 元×100 四舍五入，供 Kinda 控件使用。 */
    private static long fakeFen() {
        try {
            return Math.round(Double.parseDouble(fakeYuan()) * 100d);
        } catch (Throwable t) {
            return 0L;
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

    // ② KindaMoneyLoadingView（long·分）：setMoney(long, boolean)。
    private static void installKinda(ClassLoader cl) {
        try {
            Class<?> clazz = XposedHelpers.findClass(KINDA_CLASS, cl);
            XposedHelpers.findAndHookMethod(clazz, "setMoney", long.class, boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (!shouldFake()) return;
                                param.args[0] = Long.valueOf(fakeFen());
                            } catch (Throwable t) {
                                Log.w(TAG, "[FBAL] kinda err: " + t);
                            }
                        }
                    });
            Log.i(TAG, "[FBAL] Kinda setMoney hooked");
        } catch (Throwable t) {
            Log.w(TAG, "[FBAL] Kinda install fail: " + t);
        }
    }
}
