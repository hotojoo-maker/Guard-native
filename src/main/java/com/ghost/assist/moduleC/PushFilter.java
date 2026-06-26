package com.ghost.assist.moduleC;

import android.app.Notification;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.ghost.assist.core.Bridge;
import com.ghost.assist.core.NativeBridge;
import com.ghost.assist.core.RiskState;
import com.ghost.assist.core.StateMachine;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedList;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * PushFilter — 8.0.71 消息通知 + 角标拦截。
 *
 * 语音/视频来电拦截已拆到 {@link CallGuard}（2026-05-29，避免单文件膨胀，也为后续
 * 消息通知扩展腾空间）。本类只保留：
 *   L1   LinkedList.add(NotificationItem)   后台消息入队拦截（密友 → setResult(false)）
 *   NM   NotificationManager.notify()       唯一通知 hook；VoIP 部分委托 CallGuard.handleNmVoip，
 *                                           其余做 L1-gap 兜底（密友消息漏网二道防线）
 *   未读计数过滤（底部 tab + 顶部「微信(N)」标题）见 installUnreadCorrect（P_NF4）。
 *   旧 L4b(MainTabUI.i 零触发) / L4c(h0.d 已禁用) 已于 2026-06-06 删除，被 UNREADFIX 取代。
 *
 * :push 进程子集：L1 + NM（来电的 SF/VW 由 CallGuard.installForPush 安装）。
 *
 * 关键铁律：F-23（不碰微信 SO）；铁律 30（:push 只读 NativeBridge）；
 * 权威文档 docs/P22_PushFilter_VoIP.md。
 */
public class PushFilter {

    private static final String TAG = "NCL";

    private static final String NI_CLASS        = "com.tencent.mm.booter.notification.NotificationItem";

    private static volatile boolean sInstalled = false;

    // L1 → NM gap-cancel bridge: set by L1 when a hidden friend's NotificationItem is
    // blocked; NM cancels any notify within 200ms (defense in depth).
    private static volatile boolean sL1BlockedLastItem = false;
    private static volatile long    sL1BlockTs         = 0;

    // Count of notifications blocked for hidden friends since last reset.
    private static volatile int sHiddenBlocked = 0;

    // :push 提醒节流（场景B）：避免一条消息多次 LL.add 触发连震。
    private static volatile long sLastPushAlertTs = 0;
    private static final long PUSH_ALERT_THROTTLE_MS = 1200;

    // :push process discovery probe — log new LL.add item classes (deduped + capped)
    private static volatile boolean sPushDumpActive = true;
    private static volatile int sPushSeenCount = 0;
    private static final int PUSH_SEEN_LIMIT = 80;
    private static final java.util.Set<String> sPushSeenClasses =
            Collections.synchronizedSet(new java.util.HashSet<String>());

    /**
     * 主进程通知/未读拦截总闸 = isActive() 外加【tamper 散沙】（块B 扩面，对齐 CallGuard.active()）。
     * 确认篡改超影子期 → RiskState.isTamperDegraded()=true → active()=false → 通知/未读拦截全短路 →
     * 盗版的「密友通知屏蔽 / 未读扣除」失效。正版包永不 degrade → 行为与原来逐字一致（不误伤，铁律29）。
     * 注：:push 子集仍走 NativeBridge.isHidden()（铁律30，:push 无 RiskState/Bridge），不在本闸内。
     */
    private static boolean active() {
        return StateMachine.getInstance().isActive() && !RiskState.isTamperDegraded();
    }

    // =========================================================================
    // Entry points
    // =========================================================================

    /** Main process install — message notifications + badge + CallGuard (call chain). */
    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        install(lpparam, lpparam.classLoader);
    }

    /**
     * Main process install. {@code runtimeCl} 是微信运行时类加载器（app.getClassLoader()，
     * 带 Tinker 补丁的 DelegateLastClassLoader）；hook 微信 UI/插件类必须用它，否则会绑到
     * base.apk 里那份从不被实例化的副本（P_NF4 实测：hook 装上但永不触发）。
     */
    public static void install(XC_LoadPackage.LoadPackageParam lpparam, ClassLoader runtimeCl) {
        if (sInstalled) return;
        sInstalled = true;

        installL1(lpparam);
        installNmHook(lpparam, false);
        installMsgArrivalAlert(lpparam);   // 主进程消息到达 → 按策略震动/铃声（前台+后台-alive）
        installNewMsgArrival(lpparam);     // w.handleMessage 通知 Message（带 talker，前台也触发）
        installForegroundDingMute(lpparam); // 前台 in-app 密友消息「叮」声静音（仅密友，按策略）
        installUnreadCorrect(runtimeCl);    // 底部 tab 红点 + 顶部「微信(N)」标题扣除密友未读（P_NF4）

        CallGuard.install(lpparam);   // VoIP voice/video call suppression

        Log.i(TAG, "[PF] PushFilter installed (main: L1+NM+MSGALERT+NEWMSG+FGMUTE+UNREADFIX) + CallGuard");
    }

    /** :push process install — message push + status-bar call icon (iron rule 30). */
    public static void installForPush(XC_LoadPackage.LoadPackageParam lpparam) {
        if (sInstalled) {
            Log.i(TAG, "[PF] installForPush skip (already installed in this process)");
            return;
        }
        sInstalled = true;
        installL1ForPush();
        installNmHook(lpparam, true);

        CallGuard.installForPush(lpparam);   // SF + VW for push process

        Log.i(TAG, "[PF] installForPush L1+NM (push) + CallGuard(SF+VW) ok");
    }

    // =========================================================================
    // L1 — LinkedList.add(Object) main process variant
    // =========================================================================

    private static void installL1(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Method addMethod = LinkedList.class.getMethod("add", Object.class);
            XposedBridge.hookMethod(addMethod, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Object item = param.args[0];
                    if (item == null) return;
                    String cn = item.getClass().getName();

                    if (sPushDumpActive && sPushSeenCount < PUSH_SEEN_LIMIT
                            && sPushSeenClasses.add(cn)) {
                        sPushSeenCount++;
                        Log.i(TAG, "[PF:LLseen:main] #" + sPushSeenCount + " " + cn);
                        if (sPushSeenCount >= PUSH_SEEN_LIMIT) sPushDumpActive = false;
                    }

                    if (!NI_CLASS.equals(cn)) return;

                    String talker = readFieldH(item);
                    Log.i(TAG, "[PF:L1] LL.add NotificationItem talker=" + talker
                            + " active=" + StateMachine.getInstance().isActive());

                    if (!active()) return;
                    if (talker == null || talker.isEmpty()) return;
                    if (!Bridge.getInstance().shouldHideId(talker)) return;

                    sL1BlockedLastItem = true;
                    sL1BlockTs = System.currentTimeMillis();
                    sHiddenBlocked++;
                    // 武装前台叮声静音窗口：L1（NotificationItem 到达）是装机实证稳定触发、
                    // 且带 talker 的密友消息到达点，比 x.d 可靠（x.d 装机偶发不触发）。
                    sFgDingSuppressUntil = sL1BlockTs + FG_DING_SUPPRESS_MS;
                    param.setResult(false);
                    Log.i(TAG, "[PF:L1] block LL.add talker=" + talker
                            + " hiddenBlocked=" + sHiddenBlocked);

                    // Fire out-of-band alert (VIBRATE/SOUND) per message policy
                    try {
                        android.app.Application ctx = (android.app.Application)
                                Class.forName("android.app.ActivityThread")
                                        .getMethod("currentApplication").invoke(null);
                        NotifyRouter.fireAlert(ctx, NotifyRouter.EventType.MSG);
                    } catch (Throwable t) {
                        Log.w(TAG, "[PF:L1] fireAlert err: " + t);
                    }
                }
            });
            Log.i(TAG, "[PF:L1] LinkedList.add hook ok");
        } catch (Throwable t) {
            Log.w(TAG, "[PF:L1] LinkedList.add hook fail: " + t);
        }
    }

    // =========================================================================
    // L1 — :push process variant (decision via NativeBridge.shouldBlockBadge)
    // =========================================================================

    private static void installL1ForPush() {
        try {
            Method addMethod = LinkedList.class.getMethod("add", Object.class);
            XposedBridge.hookMethod(addMethod, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Object item = param.args[0];
                    if (item == null) return;
                    String cn = item.getClass().getName();

                    if (sPushDumpActive && sPushSeenCount < PUSH_SEEN_LIMIT
                            && sPushSeenClasses.add(cn)) {
                        sPushSeenCount++;
                        Log.i(TAG, "[PF:LLseen:push] #" + sPushSeenCount + " " + cn);
                        if (sPushSeenCount >= PUSH_SEEN_LIMIT) sPushDumpActive = false;
                    }

                    if (!NI_CLASS.equals(cn)) return;

                    String talker = readFieldH(item);
                    Log.i(TAG, "[PF:L1:push] LL.add NotificationItem talker=" + talker
                            + " hidden=" + NativeBridge.isHidden());

                    if (talker == null || talker.isEmpty()) return;
                    if (!NativeBridge.shouldBlockBadge(talker)) return;

                    sL1BlockedLastItem = true;
                    sL1BlockTs = System.currentTimeMillis();
                    sHiddenBlocked++;
                    param.setResult(false);
                    Log.i(TAG, "[PF:L1:push] block talker=" + talker
                            + " hiddenBlocked=" + sHiddenBlocked);

                    // 场景B：主进程被杀（MIUI 狠杀）、:push 收消息 → 按用户策略在 :push 内出震动。
                    // 铁律6：:push 仅限通知链最小拦截 —— 用户已明确授权本震动；策略经
                    // Bridge.readPolicyCrossProcess 跨进程文件读取（:push 不初始化 Bridge）。
                    // 节流避免一条消息多次 LL.add 连震。
                    try {
                        long now = System.currentTimeMillis();
                        if (now - sLastPushAlertTs >= PUSH_ALERT_THROTTLE_MS) {
                            android.app.Application ctx = (android.app.Application)
                                    Class.forName("android.app.ActivityThread")
                                            .getMethod("currentApplication").invoke(null);
                            Bridge.NotifyPolicy policy = Bridge.readPolicyCrossProcess(ctx);
                            if (policy != Bridge.NotifyPolicy.OFF) {
                                sLastPushAlertTs = now;
                                NotifyRouter.fireAlertForPolicy(
                                        ctx, NotifyRouter.EventType.MSG, policy);
                            }
                            Log.i(TAG, "[PF:L1:push] alert policy=" + policy);
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "[PF:L1:push] alert err: " + t);
                    }
                }
            });
            Log.i(TAG, "[PF:L1:push] LinkedList.add hook ok");
        } catch (Throwable t) {
            Log.w(TAG, "[PF:L1:push] LinkedList.add hook fail: " + t);
        }
    }

    // =========================================================================
    // NM — NotificationManager.notify() unified hook (main + :push)
    //   VoIP cancel is delegated to CallGuard.handleNmVoip(); remaining notifies
    //   fall through to the message L1-gap cancel.
    // =========================================================================

    private static void installNmHook(XC_LoadPackage.LoadPackageParam lpparam,
                                      final boolean pushProcess) {
        final String tag = pushProcess ? "[PF:NM:push]" : "[PF:NM]";
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.NotificationManager", lpparam.classLoader,
                    "notify", String.class, int.class, Notification.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            int seenId = (Integer) param.args[1];
                            Notification seenN = (Notification) param.args[2];
                            String seenCh = (seenN != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                                    ? seenN.getChannelId() : null;
                            Log.i(TAG, tag + " seen id=" + seenId + " ch=" + seenCh);

                            boolean hidden = pushProcess
                                    ? NativeBridge.isHidden()
                                    : active();
                            if (!hidden) return;

                            // VoIP call notification → CallGuard handles (cancel + armPending)
                            if (CallGuard.handleNmVoip(param, seenN, seenCh, seenId, tag)) return;

                            // L1-gap cancel — within 200ms of an L1 block, drop the notify.
                            // Friend-scoped via sL1BlockedLastItem (set only when L1 blocked a
                            // hidden friend). No hard-coded notification id (F-36 g).
                            boolean blocked = sL1BlockedLastItem;
                            long    gap     = System.currentTimeMillis() - sL1BlockTs;
                            if (blocked && gap < 200) {
                                sL1BlockedLastItem = false;
                                param.setResult(null);
                                Log.i(TAG, tag + " cancel L1-gap id=" + seenId + " gap=" + gap + "ms");
                            } else {
                                sL1BlockedLastItem = false;
                                Log.i(TAG, tag + " pass id=" + seenId
                                        + " blocked=" + blocked + " gap=" + gap + "ms");
                            }
                        }
                    });
            Log.i(TAG, tag + " NotificationManager.notify hook ok");
        } catch (Throwable t) {
            Log.w(TAG, tag + " hook fail: " + t);
        }
    }

    // =========================================================================
    // MSGALERT — 主进程消息到达点：booter.notification.x.d → 按策略 fireAlert
    //   Frida L1 实证 2026-05-29：x.d(x, String, String, int, int, boolean) 每条到达消息触发，
    //   talker(密友 wxid) 在第一个参数 (x 实例) 的字段 a。
    //   注意：微信原生「消息免打扰」开启时不走通知链 → x.d 不触发（产品取舍，尊重免打扰）。
    //   只在主进程装：覆盖前台 + 后台(微信未被杀)；杀进程的 :push 路径属后续 backlog。
    // =========================================================================

    // 节流：避免 x.d 单条消息多次触发导致连震（实测一条消息触发多次）。
    private static volatile long sLastMsgAlertTs = 0;
    private static final long MSG_ALERT_THROTTLE_MS = 1200;

    // 前台叮声静音窗口：密友消息到达点 x.d 命中时置位 now+FG_DING_SUPPRESS_MS。
    // 前台 in-app 收消息那声「叮」走 MediaPlayer.start()（首条后 setDataSource 被缓存跳过，
    // L1 实证 2026-05-30 probe_fg_correlate：前台 x.d 带密友 talker，setDataSource 不复触发）。
    // 只有密友/密群消息会置位 → 普通好友的叮声永不进入此窗口、照常响。
    private static volatile long sFgDingSuppressUntil = 0;
    private static final long FG_DING_SUPPRESS_MS = 1500;

    private static void installMsgArrivalAlert(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.tencent.mm.booter.notification.x", lpparam.classLoader, "d",
                    "com.tencent.mm.booter.notification.x",
                    String.class, String.class, int.class, int.class, boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                if (!active()) return;
                                String talker = readMsgTalker(param);
                                if (talker == null) return;
                                if (!Bridge.getInstance().shouldHideId(talker)) return;

                                // 密友/密群消息到达 → 武装前台叮声静音窗口（不分策略：OFF 也要把
                                // 微信原生那声叮压住）。普通好友 talker 不命中 shouldHideId → 不武装。
                                sFgDingSuppressUntil = System.currentTimeMillis() + FG_DING_SUPPRESS_MS;

                                Bridge.NotifyPolicy policy = Bridge.getInstance().getNotifyPolicy();
                                // OFF(静默) 不需要 owner-side 提醒；只有 VIBRATE/SOUND 才 fireAlert
                                if (policy == Bridge.NotifyPolicy.OFF) return;

                                long now = System.currentTimeMillis();
                                if (now - sLastMsgAlertTs < MSG_ALERT_THROTTLE_MS) return;
                                sLastMsgAlertTs = now;

                                android.app.Application ctx = (android.app.Application)
                                        Class.forName("android.app.ActivityThread")
                                                .getMethod("currentApplication").invoke(null);
                                NotifyRouter.fireAlert(ctx, NotifyRouter.EventType.MSG);
                                Log.i(TAG, "[PF:MSGALERT] talker=" + talker + " policy=" + policy);
                            } catch (Throwable t) {
                                Log.w(TAG, "[PF:MSGALERT] err: " + t);
                            }
                        }
                    });
            Log.i(TAG, "[PF:MSGALERT] x.d hook ok");
        } catch (Throwable t) {
            Log.w(TAG, "[PF:MSGALERT] x.d hook fail: " + t);
        }
    }

    // =========================================================================
    // NEWMSG — Handler.dispatchMessage(Message)：通知 Message 到达点（带 talker，前台也触发）
    //   L1 实证 2026-05-30：微信通知 Handler(w) 的 Message bundle 带明文 key
    //   "notification.show.talker" = 密友 wxid / 密群 @chatroom，在前台 MediaPlayer 叮声前
    //   ~50ms 触发，前后台都走（竞品 catfish 同一条路）。
    //   ⚠️ 不能 hook w.handleMessage：微信 Tinker 热修复 → 运行时 w 类来自另一 classloader，
    //   findAndHookMethod 按类名挂的是错的类实例 → 永不触发。改 hook 系统类
    //   Handler.dispatchMessage（单 classloader，Tinker 打不乱），dispatchMessage 会为所有
    //   Handler（含 w）的每条消息调用，且在 handleMessage 之前。
    //   性能：用 peekData() 不创建空 Bundle，绝大多数消息无 data → 立即返回。
    //   职责：① 命中密友/密群 → 武装前台叮声静音窗口（FGMUTE 据此掐叮，OFF 档也掐）；
    //        ② VIBRATE/SOUND → fireAlert（与 x.d MSGALERT 共用节流，不重复震）。
    // =========================================================================

    private static final String NOTIFY_TALKER_KEY = "notification.show.talker";

    private static void installNewMsgArrival(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    Handler.class, "dispatchMessage", Message.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                Object a0 = param.args[0];
                                if (!(a0 instanceof Message)) return;
                                Bundle data = ((Message) a0).peekData(); // 不创建空 Bundle
                                if (data == null) return;
                                String talker = data.getString(NOTIFY_TALKER_KEY);
                                if (talker == null || talker.isEmpty()) return;
                                if (!active()) return;
                                if (!Bridge.getInstance().shouldHideId(talker)) return;

                                // 命中密友/密群 → 武装前台叮声静音窗口（OFF 档也武装：静默也要掐原生叮）
                                sFgDingSuppressUntil = System.currentTimeMillis() + FG_DING_SUPPRESS_MS;

                                Bridge.NotifyPolicy policy = Bridge.getInstance().getNotifyPolicy();
                                if (policy == Bridge.NotifyPolicy.OFF) {
                                    Log.i(TAG, "[PF:NEWMSG] talker=" + talker + " policy=OFF (arm mute)");
                                    return;
                                }

                                long now = System.currentTimeMillis();
                                if (now - sLastMsgAlertTs < MSG_ALERT_THROTTLE_MS) return;
                                sLastMsgAlertTs = now;
                                android.app.Application ctx = (android.app.Application)
                                        Class.forName("android.app.ActivityThread")
                                                .getMethod("currentApplication").invoke(null);
                                NotifyRouter.fireAlert(ctx, NotifyRouter.EventType.MSG);
                                Log.i(TAG, "[PF:NEWMSG] talker=" + talker + " policy=" + policy);
                            } catch (Throwable t) {
                                Log.w(TAG, "[PF:NEWMSG] err: " + t);
                            }
                        }
                    });
            Log.i(TAG, "[PF:NEWMSG] Handler.dispatchMessage hook ok");
        } catch (Throwable t) {
            Log.w(TAG, "[PF:NEWMSG] hook fail: " + t);
        }
    }

    // =========================================================================
    // FGMUTE — 前台 in-app 密友消息「叮」声静音
    //   前台收消息那声叮 = MediaPlayer.start()（L1 实证 2026-05-30）。该点拿不到 talker，
    //   故用 x.d（带 talker）武装的 sFgDingSuppressUntil 短窗口做关联：窗口内的 start() = 这条
    //   密友消息的叮 → 静音。窗口只由密友/密群消息武装，普通好友的叮声永不进入 → 照常响。
    //   策略无关地静音微信原生叮（OFF/VIBRATE/SOUND 都不该让原生叮叠加）；震动/自定义铃声由
    //   x.d → NotifyRouter.fireAlert 负责（自有铃声经 sOurSound 豁免，不会被本 hook 误杀）。
    // =========================================================================

    private static void installForegroundDingMute(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    MediaPlayer.class, "start",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            // 我们自己 SOUND 档播放的自定义铃声 → 放行
                            if (NotifyRouter.sOurSound) return;
                            if (!active()) return;
                            long now = System.currentTimeMillis();
                            if (now >= sFgDingSuppressUntil) return;  // 不在密友消息窗口 → 放行（普通好友照响）
                            sFgDingSuppressUntil = 0;                 // 消费：一条消息只压一声叮
                            param.setResult(null);
                            Log.i(TAG, "[PF:FGMUTE] muted foreground ding (start)");
                        }
                    });
            Log.i(TAG, "[PF:FGMUTE] MediaPlayer.start hook ok");
        } catch (Throwable t) {
            Log.w(TAG, "[PF:FGMUTE] hook fail: " + t);
        }
    }

    // =========================================================================
    // UNREADFIX — 底部 tab 红点 + 顶部「微信(N)」标题计数扣除密友未读（P_NF4，仅主进程）
    //   8.0.71 渲染点（Frida 探针实证 2026-05-31）：
    //     底部 tab：com.tencent.mm.ui.LauncherUIBottomTabView.l(int)         主 tab 未读数 setText
    //     顶部标题：com.tencent.mm.plugin.taskbar.ui.TaskBarContainer.setActionBarTitle(String "微信(N)")
    //   密友未读总数来自 ConvFilter.getHiddenUnread()（隐藏态全量过滤时累加 field_unReadCount）。
    //   仅隐藏态生效；只减密友那部分，普通好友未读不受影响。
    // =========================================================================
    private static void installUnreadCorrect(ClassLoader cl) {
        // 底部 tab 红点数字
        try {
            XposedHelpers.findAndHookMethod(
                    "com.tencent.mm.ui.LauncherUIBottomTabView", cl,
                    "l", int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Log.i(TAG, "[PF:UNREADFIX] tab CALLED arg="
                                    + (param.args.length > 0 ? String.valueOf(param.args[0]) : "?")
                                    + " active=" + StateMachine.getInstance().isActive()
                                    + " h=" + com.ghost.assist.moduleD.ConvFilter.getHiddenUnread());
                            if (!active()) return;
                            // 用户开「显示密友未读消息数」→ 不扣，密友未读照常计入
                            if (Bridge.getInstance().isShowHiddenUnread()) return;
                            int h = com.ghost.assist.moduleD.ConvFilter.getHiddenUnread();
                            if (h <= 0) return;
                            if (!(param.args[0] instanceof Integer)) return;
                            int real = (Integer) param.args[0];
                            int fixed = Math.max(0, real - h);
                            if (fixed != real) {
                                param.args[0] = fixed;
                                Log.i(TAG, "[PF:UNREADFIX] tab real=" + real
                                        + " hidden=" + h + " out=" + fixed);
                            }
                        }
                    });
            Log.i(TAG, "[PF:UNREADFIX] LauncherUIBottomTabView.l hooked");
        } catch (Throwable t) {
            Log.w(TAG, "[PF:UNREADFIX] tab hook fail: " + t);
        }

        // 顶部「微信(N)」标题
        try {
            XposedHelpers.findAndHookMethod(
                    "com.tencent.mm.plugin.taskbar.ui.TaskBarContainer", cl,
                    "setActionBarTitle", String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Log.i(TAG, "[PF:UNREADFIX] title CALLED arg=\""
                                    + (param.args.length > 0 ? String.valueOf(param.args[0]) : "?")
                                    + "\" active=" + StateMachine.getInstance().isActive()
                                    + " h=" + com.ghost.assist.moduleD.ConvFilter.getHiddenUnread());
                            if (!active()) return;
                            // 用户开「显示密友未读消息数」→ 不扣，密友未读照常计入
                            if (Bridge.getInstance().isShowHiddenUnread()) return;
                            int h = com.ghost.assist.moduleD.ConvFilter.getHiddenUnread();
                            if (h <= 0) return;
                            if (!(param.args[0] instanceof String)) return;
                            String s = (String) param.args[0];
                            // 匹配 "微信(N)" / "微信（N）"，N 纯数字；"99+" 不匹配 → 不动
                            java.util.regex.Matcher m = java.util.regex.Pattern
                                    .compile("^(.*?)[\\(（](\\d+)[\\)）]$").matcher(s);
                            if (!m.matches()) return;
                            int real = Integer.parseInt(m.group(2));
                            int fixed = Math.max(0, real - h);
                            String out = (fixed <= 0) ? m.group(1).trim()
                                    : m.group(1) + "(" + fixed + ")";
                            if (!out.equals(s)) {
                                param.args[0] = out;
                                Log.i(TAG, "[PF:UNREADFIX] title \"" + s + "\" hidden="
                                        + h + " out=\"" + out + "\"");
                            }
                        }
                    });
            Log.i(TAG, "[PF:UNREADFIX] TaskBarContainer.setActionBarTitle hooked");
        } catch (Throwable t) {
            Log.w(TAG, "[PF:UNREADFIX] title hook fail: " + t);
        }
    }

    /** 从 x.d 的参数提取 talker：优先第一个参数(x 实例)的字段 a；兜底扫 String 参数。 */
    private static String readMsgTalker(XC_MethodHook.MethodHookParam param) {
        try {
            Object x0 = param.args[0];
            if (x0 != null) {
                java.lang.reflect.Field fa = x0.getClass().getDeclaredField("a");
                fa.setAccessible(true);
                Object v = fa.get(x0);
                if (v instanceof String && isHideCandidate((String) v)) return (String) v;
            }
        } catch (Throwable ignored) {}
        for (int i = 1; i < param.args.length; i++) {
            if (param.args[i] instanceof String && isHideCandidate((String) param.args[i])) {
                return (String) param.args[i];
            }
        }
        return null;
    }

    private static boolean isHideCandidate(String s) {
        return s != null && (s.startsWith("wxid_") || s.endsWith("@chatroom"));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Read field "h" (talker wxid) from NotificationItem via its own ClassLoader. */
    private static String readFieldH(Object ni) {
        try {
            Field f = ni.getClass().getDeclaredField("h");
            f.setAccessible(true);
            Object v = f.get(ni);
            if (v instanceof String) return (String) v;
        } catch (Throwable t) {
            Log.w(TAG, "[PF] readFieldH err: " + t);
        }
        return null;
    }
}
