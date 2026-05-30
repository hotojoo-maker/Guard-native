package com.ghost.assist.moduleC;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.widget.TextView;

import com.ghost.assist.core.Bridge;
import com.ghost.assist.core.StateMachine;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * AntiRecall — 防撤回（P23，C1 模块）
 *
 * Hook chain（8.0.71 静态反编译实证，2026-05-31，tinker patch dex）：
 *   jy0.t.f(String session, long msgId, com.tencent.mm.modelbase.p0 msg,
 *           String replaceMsg, String, String announcementId) → void
 *   = doRevokeMsg（TAG "MicroMsg.BigBallSysCmdMsgConsumer"，日志原文 "doRevokeMsg ..."）
 *
 * 这是微信「执行撤回」的唯一统一出口：收到对方撤回指令(sysmsg type="revokemsg")后，
 * jy0.t 解析 session/newmsgid/replacemsg 字段 → 调 f() 从存储取原消息并替换。
 * 全文件仅在 revokemsg 分支调用 f() 一次，故 hook 它天然覆盖全部消息类型
 * （文字/图片/视频/语音/文件）+ 全部好友与群聊，且不影响正常消息。
 *
 * 方案（v4，chatfish 式「提示 + 原文」）：
 *   微信原生撤回 = 原地覆盖：把原消息 f9 的 content 改写成"对方撤回了一条消息"
 *   （f9.c1(replaceMsg)）+ setType(0x10002710) 变系统提示类型 —— 同一条消息被改掉，
 *   不是新插一条。所以单纯改写 replaceMsg 会让原文一起丢。
 *
 *   v4 做法：
 *     1. 对「收到的消息被对方撤回」(orig.isSend==0)：beforeHookedMethod setResult(null)
 *        跳过 f() → 原消息（文字/图片/视频）100% 保留不动；
 *     2. 同时用微信自己的存储路径，插一条 type=10000 的系统提示消息
 *        「<HH:mm> 已拦截对方撤回消息」，createTime = 原消息时间 - 1ms → 显示在原文上方。
 *     3. 对「自己撤回自己发的消息」(orig.isSend==1)：直接放行，让原生撤回正常工作。
 *
 *   插入路径（8.0.71 smali 实证，tools/re_8071）：
 *     storage = jy0.c9.b().u()  → 强转 com.tencent.mm.storage.h9（MsgInfoStorage 单例）
 *     orig    = h9.k3(talker, svrMsgId)         // 取原消息 f9（jy0.t.f 内同款调用）
 *     f9 tip  = new com.tencent.mm.storage.f9()
 *               tip.setType(10000)               // 系统提示灰条
 *               tip.y1(talker)                   // setTalker  (a8.field_talker)
 *               tip.j1(0)                        // setIsSend=0 (a8.field_isSend)
 *               tip.c1(text)                     // setContent (a8.field_content)
 *               tip.d1(origCreateTime - 1)       // setCreateTime → 排在原文上方
 *               tip.s1(2)                        // setStatus   (a8.field_status)
 *     h9.r9(tip)                                 // insert，返回 rowid（j8 实证）
 *
 * 授权门（不依赖密友 f1，全局生效）：
 *   isVipAuthorized() && Bridge.isAntiRecallEnabled()
 *
 * 版本历史：
 *   v4  2026-05-31  原文保留 + 插入自有系统提示灰条（chatfish 式）。f9/h9 字段方法名
 *                   全部由 rl/a8.smali + com/tencent/mm/ui/chatting/j8.smali 反汇编实证。
 *   v3  2026-05-31  改 hook 点 a2.b → jy0.t.f（doRevokeMsg）+ setResult(null) 全跳过。
 *                   a2.b 已动态证伪：收到撤回时不触发（从竞品 8.0.70 直搬，8.0.71 不走）。
 *   v2  2026-05-25  改为 skip 方案（setResult null），移除无效 snapshot
 *   v1  2026-05-25  snapshot+restore（DB 写入后失效，已废弃）
 *
 * 提示染红（v4.1）：微信聊天文字走自绘控件 MMNeat7extView（不经框架 TextView），
 *   故对其「首参 CharSequence」的方法逐个 hook，命中 TIP_MARK 时包 ForegroundColorSpan(红)。
 *   详见 installTipColor()。已 L1 装机实证生效（2026-05-31）。
 *
 * ⚠️ jy0.t / jy0.c9 / h9.r9 / a8 的 y1/j1/c1/d1/s1 / MMNeat7extView 均为混淆名，微信升级
 *    可能变名 → 后续进 classmap 字典。
 *    type=10000 渲染为灰条系统提示 + insert 实时刷新：已 L1 装机实证
 *    （2026-05-31，logcat "[AR] recall blocked + tip inserted" ×4：文字/表情/图片/视频）。
 *
 * 旧 hook 点（已废弃，勿恢复）：
 *   8.0.70 Catfish: a2.b(m05.ys4, sc3.z4, ...)
 *   8.0.71 误搬:    a2.b(p0, z15.ut4, ge3.z4)  ← 收撤回时零触发（[AR] 无日志），F-xx
 */
public class AntiRecall {

    private static final String TAG = "NCL";

    private static final String WX_CLASS   = "jy0.t";
    private static final String WX_METHOD  = "f";
    private static final String PARAM_MSG  = "com.tencent.mm.modelbase.p0";

    // --- 微信内部存储路径（8.0.71 smali 实证）---
    private static final String CLS_MSGINFO = "com.tencent.mm.storage.f9"; // MsgInfo
    private static final String CLS_STOREMGR = "jy0.c9";                   // 存储管理器，b()→jy0.e
    private static final int SYS_MSG_TYPE = 10000;                         // 系统提示灰条类型

    /** 系统提示文案：———— <HH:mm> 已拦截对方撤回的消息 ————（时间 = 拦截发生时刻）。 */
    private static String buildTipText() {
        String time = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                .format(new java.util.Date());
        // 用 box-drawing「─」(U+2500) 连续横线，em-dash「—」字间有缝
        return "─── " + time + " " + TIP_MARK + "的消息 ───";
    }

    // --- 提示染红（UI 层）---
    // 唯一标记短语：仅本模块插入的提示灰条会含此串，作为高速过滤闸（startsWith/indexOf 极廉价）。
    private static final String TIP_MARK = "已拦截对方撤回";
    // 微信品牌红（0xFFFA5151）。要换色改这里即可。
    private static final int TIP_COLOR = 0xFFFA5151;
    private static volatile boolean sColorHookInstalled = false;

    /**
     * 把含 TIP_MARK 的系统灰条文字染红。
     *
     * 微信聊天文字走自绘控件 MMNeat7extView（不经框架 TextView.setText），其 setText 已被混淆
     * 改名 → 枚举该类「首参为 CharSequence」的方法逐个 hook；另 hook 框架 TextView.setText 兜底。
     * 命中 TIP_MARK 时把入参包成 SpannableString + ForegroundColorSpan(红)——用 span 而非
     * setTextColor，span 随文字走，微信渲染器后续的 setTextColor(灰) 无法覆盖它。
     *
     * 性能/反检测：闸门是 indexOf(短语)，未命中立刻 return（O(短)），只有我们这一条会真正干活；
     * hook 的是框架/微信 UI 方法（非微信 JNI），不触 F-23。已 L1 实证染红生效（2026-05-31）。
     */
    public static void installTipColor(ClassLoader classLoader) {
        if (sColorHookInstalled) return;
        XC_MethodHook dye = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    if (param.args.length == 0 || !(param.args[0] instanceof CharSequence)) return;
                    CharSequence cs = (CharSequence) param.args[0];
                    if (cs == null || cs.length() < TIP_MARK.length()) return;
                    if (cs.toString().indexOf(TIP_MARK) < 0) return;
                    // 已带红色 span → 不重复包裹
                    if (cs instanceof Spanned
                            && ((Spanned) cs).getSpans(0, cs.length(), ForegroundColorSpan.class).length > 0) {
                        return;
                    }
                    SpannableString sp = new SpannableString(cs); // 保留原有 emoji span
                    sp.setSpan(new ForegroundColorSpan(TIP_COLOR), 0, sp.length(),
                            Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                    param.args[0] = sp;
                } catch (Throwable ignore) { /* 染色失败不影响文字显示 */ }
            }
        };
        int hooked = 0;
        // 微信聊天文字走自定义 MMNeat7extView（自绘，不经框架 TextView.setText）→ 主路径。
        // 其 setText 已被混淆改名 → 枚举所有「首参为 CharSequence」的方法逐个 hook。
        // dye 只在文字含 TIP_MARK 时动作，故 hook 多余方法无副作用。
        try {
            Class<?> neat = XposedHelpers.findClass(
                    "com.tencent.mm.ui.widget.MMNeat7extView", classLoader);
            for (java.lang.reflect.Method m : neat.getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (p.length >= 1 && CharSequence.class.isAssignableFrom(p[0])) {
                    try { XposedBridge.hookMethod(m, dye); hooked++; } catch (Throwable ignore) {}
                }
            }
            Log.i(TAG, "[AR] MMNeat7extView CharSequence-methods hooked=" + hooked);
        } catch (Throwable t) {
            Log.e(TAG, "[AR] tip-color hook(MMNeat7extView) failed: " + t);
        }
        // 框架 TextView.setText 兜底（部分系统提示/旧路径可能仍走它）
        try {
            XposedHelpers.findAndHookMethod(TextView.class, "setText", CharSequence.class, dye);
            hooked++;
        } catch (Throwable ignore) {}
        try {
            XposedHelpers.findAndHookMethod(TextView.class, "setText",
                    CharSequence.class, TextView.BufferType.class, dye);
            hooked++;
        } catch (Throwable ignore) {}
        if (hooked > 0) {
            sColorHookInstalled = true;
            Log.i(TAG, "[AR] tip-color hook installed (setText x" + hooked + ")");
        } else {
            Log.e(TAG, "[AR] tip-color hook: no setText method hooked");
        }
    }

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        // 兼容旧调用：默认用 base.apk classloader（可能 hook 到未使用的副本，仅兜底）
        install(lpparam, lpparam.classLoader);
    }

    /**
     * @param classLoader 必须传 app.getClassLoader()——8.0.71 tinker 热补丁下 jy0.t
     *                    由 app 的 DelegateLastClassLoader 加载，lpparam.classLoader(base.apk)
     *                    那份不是运行时实际使用的类（hook 上去不触发）。
     */
    public static void install(XC_LoadPackage.LoadPackageParam lpparam, ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                WX_CLASS, classLoader,
                WX_METHOD,
                String.class,            // session / talker
                long.class,              // xmlSrvMsgId
                PARAM_MSG,               // com.tencent.mm.modelbase.p0
                String.class,            // replaceMsg
                String.class,
                String.class,            // announcementId
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            if (!StateMachine.getInstance().isVipAuthorized()) return;
                            if (!Bridge.getInstance().isAntiRecallEnabled()) return;

                            String talker = (param.args[0] instanceof String) ? (String) param.args[0] : null;
                            long svrMsgId = (param.args[1] instanceof Number)
                                    ? ((Number) param.args[1]).longValue() : 0L;

                            // 取存储单例 + 原消息（与 jy0.t.f 内同款调用）
                            Object storage = null;
                            Object orig = null;
                            try {
                                Object mgr = XposedHelpers.callStaticMethod(
                                        XposedHelpers.findClass(CLS_STOREMGR, classLoader), "b");
                                storage = XposedHelpers.callMethod(mgr, "u"); // ie3.m0 → h9
                                if (talker != null) {
                                    orig = XposedHelpers.callMethod(storage, "k3", talker, svrMsgId);
                                }
                            } catch (Throwable ignore) { /* 查询失败 → 下方默认拦截兜底 */ }

                            // 「自己撤回自己发的消息」(isSend==1) → 放行，让原生撤回正常工作。
                            // 仅在能确证 isSend==1 时放行；查询失败/未知一律拦截（保住防撤回主功能）。
                            if (orig != null) {
                                try {
                                    Object isSend = XposedHelpers.callMethod(orig, "D0");
                                    if (isSend instanceof Number && ((Number) isSend).intValue() == 1) {
                                        return; // my own recall — do not block, do not tip
                                    }
                                } catch (Throwable ignore) {}
                            }

                            // 收到的消息被对方撤回：跳过原生覆盖 → 原文（含图片/视频）保留
                            param.setResult(null);

                            // 尽力插一条系统提示灰条在原文上方（失败不影响"原文已保留"）
                            if (storage != null && orig != null && talker != null) {
                                try {
                                    long origTime = 0L;
                                    Object ct = XposedHelpers.callMethod(orig, "getCreateTime");
                                    if (ct instanceof Number) origTime = ((Number) ct).longValue();

                                    Object tip = XposedHelpers.newInstance(
                                            XposedHelpers.findClass(CLS_MSGINFO, classLoader));
                                    XposedHelpers.callMethod(tip, "setType", SYS_MSG_TYPE);
                                    XposedHelpers.callMethod(tip, "y1", talker);          // setTalker
                                    XposedHelpers.callMethod(tip, "j1", 0);               // setIsSend=0
                                    XposedHelpers.callMethod(tip, "c1", buildTipText());  // setContent
                                    XposedHelpers.callMethod(tip, "d1", origTime - 1L);   // setCreateTime
                                    XposedHelpers.callMethod(tip, "s1", 2);               // setStatus
                                    XposedHelpers.callMethod(storage, "r9", tip);         // insert
                                    Log.i(TAG, "[AR] recall blocked + tip inserted, talker=" + talker);
                                } catch (Throwable e) {
                                    Log.e(TAG, "[AR] tip insert failed (original preserved): " + e);
                                }
                            } else {
                                Log.i(TAG, "[AR] recall blocked (no tip; orig unavailable), talker=" + talker);
                            }
                        } catch (Throwable t) {
                            Log.e(TAG, "[AR:before] err: " + t);
                        }
                    }
                }
            );
            Log.i(TAG, "[AR] hook installed: " + WX_CLASS + "#" + WX_METHOD + " (doRevokeMsg)");
        } catch (Throwable t) {
            Log.e(TAG, "[AR] install failed: " + t);
        }
        // 提示染红（独立 UI hook，失败不影响主拦截）
        installTipColor(classLoader);
    }
}
