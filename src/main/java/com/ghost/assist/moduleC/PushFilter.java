package com.ghost.assist.moduleC;

import android.app.Notification;
import android.os.Build;
import android.util.Log;

import com.ghost.assist.core.Bridge;
import com.ghost.assist.core.NativeBridge;
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
 *   L4b  MainTabUI.i()                      底部 tab unread 数字
 *   L4c  h0.d(int)                          OEM 桌面角标（已禁用 stub）
 *
 * :push 进程子集：L1 + NM（来电的 SF/VW 由 CallGuard.installForPush 安装）。
 *
 * 关键铁律：F-23（不碰微信 SO）；铁律 30（:push 只读 NativeBridge）；
 * 权威文档 docs/P22_PushFilter_VoIP.md。
 */
public class PushFilter {

    private static final String TAG = "NCL";

    private static final String NI_CLASS        = "com.tencent.mm.booter.notification.NotificationItem";
    private static final String MAINTABUI_CLASS = "com.tencent.mm.ui.MainTabUI";

    private static volatile boolean sInstalled = false;

    // L1 → NM gap-cancel bridge: set by L1 when a hidden friend's NotificationItem is
    // blocked; NM cancels any notify within 200ms (defense in depth).
    private static volatile boolean sL1BlockedLastItem = false;
    private static volatile long    sL1BlockTs         = 0;

    // Count of notifications blocked for hidden friends since last reset.
    private static volatile int sHiddenBlocked = 0;

    // :push process discovery probe — log new LL.add item classes (deduped + capped)
    private static volatile boolean sPushDumpActive = true;
    private static volatile int sPushSeenCount = 0;
    private static final int PUSH_SEEN_LIMIT = 80;
    private static final java.util.Set<String> sPushSeenClasses =
            Collections.synchronizedSet(new java.util.HashSet<String>());

    // =========================================================================
    // Entry points
    // =========================================================================

    /** Main process install — message notifications + badge + CallGuard (call chain). */
    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        if (sInstalled) return;
        sInstalled = true;

        installL1(lpparam);
        installNmHook(lpparam, false);
        installL4b(lpparam);
        installL4c(lpparam);
        installMsgArrivalAlert(lpparam);   // 主进程消息到达 → 按策略震动/铃声（前台+后台-alive）

        CallGuard.install(lpparam);   // VoIP voice/video call suppression

        Log.i(TAG, "[PF] PushFilter installed (main: L1+NM+L4+MSGALERT) + CallGuard");
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

                    if (!StateMachine.getInstance().isActive()) return;
                    if (talker == null || talker.isEmpty()) return;
                    if (!Bridge.getInstance().shouldHideId(talker)) return;

                    sL1BlockedLastItem = true;
                    sL1BlockTs = System.currentTimeMillis();
                    sHiddenBlocked++;
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
                                    : StateMachine.getInstance().isActive();
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
                                if (!StateMachine.getInstance().isActive()) return;
                                // OFF(静默) 不需要 owner-side 提醒；只有 VIBRATE/SOUND 才 fireAlert
                                if (Bridge.getInstance().getNotifyPolicy()
                                        == Bridge.NotifyPolicy.OFF) return;
                                String talker = readMsgTalker(param);
                                if (talker == null) return;
                                if (!Bridge.getInstance().shouldHideId(talker)) return;

                                long now = System.currentTimeMillis();
                                if (now - sLastMsgAlertTs < MSG_ALERT_THROTTLE_MS) return;
                                sLastMsgAlertTs = now;

                                android.app.Application ctx = (android.app.Application)
                                        Class.forName("android.app.ActivityThread")
                                                .getMethod("currentApplication").invoke(null);
                                NotifyRouter.fireAlert(ctx, NotifyRouter.EventType.MSG);
                                Log.i(TAG, "[PF:MSGALERT] talker=" + talker
                                        + " policy=" + Bridge.getInstance().getNotifyPolicy());
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
    // L4b — MainTabUI.i(): bottom tab unread digit (main process)
    // =========================================================================

    private static void installL4b(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    MAINTABUI_CLASS, lpparam.classLoader, "i",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (!StateMachine.getInstance().isActive()) return;
                            Object result = param.getResult();
                            int real = (result instanceof Integer) ? (Integer) result : 0;
                            param.setResult(0);
                            Log.i(TAG, "[PF:L4b] real=" + real + " out=0");
                        }
                    });
            Log.i(TAG, "[PF:L4b] hooked");
        } catch (Throwable t) {
            Log.w(TAG, "[PF:L4b] install fail: " + t);
        }
    }

    // =========================================================================
    // L4c — h0.d(int): OEM desktop badge dispatcher (DISABLED stub)
    // =========================================================================

    private static void installL4c(XC_LoadPackage.LoadPackageParam lpparam) {
        // sHiddenBlocked accumulation caused over-subtraction. Will be replaced by
        // WeChatDND (官方免打扰) which natively excludes hidden friends from the count.
        Log.i(TAG, "[PF:L4c] disabled (pending WeChatDND)");
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
