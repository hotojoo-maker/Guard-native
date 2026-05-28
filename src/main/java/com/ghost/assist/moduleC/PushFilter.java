package com.ghost.assist.moduleC;

import android.app.Activity;
import android.app.Notification;
import android.app.Service;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;

import com.ghost.assist.core.Bridge;
import com.ghost.assist.core.NativeBridge;
import com.ghost.assist.core.StateMachine;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * PushFilter — 8.0.71 通知/来电/角标全链路拦截
 *
 * 架构权威：docs/P22_PushFilter_VoIP.md（2026-05-25 定稿）
 * 装机来源：worklog 2026-05-25 16:02 logcat（L1+VV 实证片段）
 *
 * 主进程 hook 链：
 *   L1   LinkedList.add(NotificationItem)            消息入队拦截
 *   NM   NotificationManager.notify()                通知到表现层兜底
 *   SF   Service.startForeground()                   状态栏来电图标（NM 之外的平行通知路径）
 *   VC   VoIPRenderTextureView.onAttachedToWindow    前台 VoIP overlay 隐藏（CallGuard）
 *   AT   AudioTrack.play()                           TRTC SDK 挂断/in-app 音频
 *   MP   MediaPlayer.start()                         视频挂断「嘟」(仅来电通话窗口 sVoIPCallPending 内)
 *   VV   Vibrator.vibrate(*)                         默认通知震动
 *   VW   PowerManager$WakeLock.acquire/release       亮屏 WakeLock + under-locked 修复
 *   PiP  Activity.enterPictureInPictureMode(*)       VoIP 通话切后台时的画中画小窗
 *   L4b  MainTabUI.i()                                底部 tab unread 数字
 *   L4c  h0.d(int)                                    OEM 桌面角标（已禁用 stub）
 *
 * :push 进程子集（仅消息推送）：
 *   L1 + NM + SF + VW
 *
 * 已证伪、永久禁止：
 *   - CA Activity.onCreate finish 模糊匹配（前台来电不新开 Activity，只有 LauncherUI overlay 实证 2026-05-24）
 *   - AudioManager.requestAudioFocus / setMode(RINGTONE) / adjustStreamVolume(ADJUST_MUTE)
 *   - TelecomManager.addNewIncomingCall / Ringtone.play / Dialog.show / WMG.addView (8 overload)
 *   - NotificationItem.a(Context) (public final + ART AOT 内联，Xposed 拦不住)
 *   - x.a(f9) 8.0.71 零命中
 *
 * 关键铁律：
 *   F-26  hook 父类方法不能用 findAndHookMethod（不遍历继承链），用 getDeclaredMethod + hookMethod
 *   F-23  禁止注入微信 JNI 链；本类全部走 Android 平台 API hook，不碰微信 SO
 *   铁律 30  :push 进程不读 Java StateMachine，只读 NativeBridge.shouldBlockBadge()
 *   2026-05-25  STREAM_RING 不参与 mute（触发系统"静音模式已开启" toast）
 */
public class PushFilter {

    private static final String TAG = "NCL";

    private static final String NI_CLASS        = "com.tencent.mm.booter.notification.NotificationItem";
    private static final String MAINTABUI_CLASS = "com.tencent.mm.ui.MainTabUI";

    private static volatile boolean sInstalled = false;

    // -------------------------------------------------------------------------
    // L1 → NM gap-cancel bridge
    // -------------------------------------------------------------------------
    // Set by L1 when a hidden friend's NotificationItem is blocked.
    // NM hook checks this within a 200ms window and cancels the notification
    // if WeChat bypassed the LinkedList queue (defense in depth).
    private static volatile boolean sL1BlockedLastItem = false;
    private static volatile long    sL1BlockTs         = 0;

    // Count of notifications blocked for hidden friends since last reset.
    private static volatile int sHiddenBlocked = 0;

    // -------------------------------------------------------------------------
    // VoIP lifecycle state (shared across SF / NM / VC / AT / PiP / VW)
    // -------------------------------------------------------------------------
    // True from the first SF/NM signal of an incoming call until VC onDetach
    // (call ended) or 120s fallback timer fires (whichever comes first).
    private static volatile boolean sVoIPCallPending = false;

    // True when running inside the :push process. Iron rule 30: :push must not
    // touch Java Bridge / NotifyRouter (no MMKV there). Set in installForPush().
    private static volatile boolean sPushProcess = false;

    // True only between our own removeView() and the matching onDetach hook;
    // distinguishes "we removed it" from "WeChat removed it (call ended)".
    private static volatile boolean sRemovedByUs = false;

    // 120s fallback. See docs/P22_PushFilter_VoIP.md §二 sVoIPCallPending lifecycle.
    private static final long   VOIP_PENDING_TTL_MS = 120_000L;
    private static Runnable     sClearVoIPPendingRunnable = null;
    private static final Handler sMainHandler =
            new Handler(Looper.getMainLooper());

    // Snapshot of stream volumes muted by us; null = nothing muted.
    private static volatile int[] sMutedVols = null;

    // STREAM_RING NOT muted (triggers OEM "Silent mode on" toast; 2026-05-25 fix).
    private static final int[] MUTE_STREAMS = {
            AudioManager.STREAM_VOICE_CALL,
            AudioManager.STREAM_MUSIC
    };

    // VoIP render view class names (2026-05-24 diag confirmed)
    private static final String[] VOIP_VIEW_CLASSES = {
            "com.tencent.mm.plugin.voip.video.render.VoIPRenderTextureView",
            "com.tencent.mm.voipmp.v2.render.VoIPMPVoIPVideoView"
    };

    // Full-screen incoming-call Activity (background → foreground ringing screen).
    // L1 evidence 2026-05-29: accessibility label on the leaked ring UI.
    private static final String VOIP_ACTIVITY_CLASS =
            "com.tencent.mm.plugin.voip.ui.VideoActivity";

    // Channel-name keywords for VoIP detection (NM + SF shared)
    private static final String[] VOIP_CHANNEL_KEYWORDS = {
            "voip", "ringtone", "call", "ring", "reminder"
    };

    // Title/body text keywords (used when channel name not conclusive)
    private static final String[] VOIP_TEXT_KEYWORDS = {
            "通话", "通話", "call", "视频", "语音"
    };

    // WakeLock under-locked crash fix: WeakHashMap of locks WE blocked the
    // acquire() of, so we also short-circuit their release() (worklog 2026-05-25:
    // RuntimeException: WakeLock under-locked ILinkVoIPSmallView).
    private static final Map<Object, Boolean> sBlockedWakeLocks =
            Collections.synchronizedMap(new WeakHashMap<>());

    // Cover-all wake flags (any of these set → likely a screen-on intent)
    private static final int SCREEN_WAKE_FLAGS =
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK   // 0x0000000a
            | PowerManager.ACQUIRE_CAUSES_WAKEUP   // 0x10000000
            | PowerManager.ON_AFTER_RELEASE;       // 0x20000000

    // :push process discovery probe — log new LL.add item classes (deduped + capped)
    private static volatile boolean sPushDumpActive = true;
    private static volatile int sPushSeenCount = 0;
    private static final int PUSH_SEEN_LIMIT = 80;
    private static final java.util.Set<String> sPushSeenClasses =
            Collections.synchronizedSet(new java.util.HashSet<String>());

    // =========================================================================
    // Entry points
    // =========================================================================

    /**
     * Main process install — full hook chain (msg + call + badge).
     */
    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        if (sInstalled) return;
        sInstalled = true;

        // Message notifications
        installL1(lpparam);
        installNmHook(lpparam, false);

        // VoIP call suppression chain
        installStartForegroundBlock(lpparam, false);
        installCallActivityBlock(lpparam);
        installCallViewBlock(lpparam);
        installAudioTrackBlock(lpparam);
        installMediaPlayerBlock(lpparam);
        installVibratorBlock(lpparam);
        installWakeLockBlock(lpparam, false);
        installPipBlock(lpparam);

        // Badge / unread
        installL4b(lpparam);
        installL4c(lpparam);

        Log.i(TAG, "[PF] PushFilter installed (main, full chain)");
    }

    /**
     * :push process install — minimal subset (msg push + status-bar call icon).
     * Iron rule 6 + 30: :push only uses NativeBridge.shouldBlockBadge() for decisions.
     * No Bridge / StateMachine / NotifyRouter / UI calls here.
     */
    public static void installForPush(XC_LoadPackage.LoadPackageParam lpparam) {
        if (sInstalled) {
            Log.i(TAG, "[PF] installForPush skip (already installed in this process)");
            return;
        }
        sInstalled = true;
        sPushProcess = true;
        installL1ForPush();
        installNmHook(lpparam, true);
        installStartForegroundBlock(lpparam, true);
        installWakeLockBlock(lpparam, true);
        Log.i(TAG, "[PF] installForPush L1+NM+SF+VW (push-process) ok");
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

                    // Fire out-of-band alert (VIBRATE/SOUND) per policy
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

                            Notification n = (Notification) param.args[2];

                            // 1) Strip fullScreenIntent → cancel (prevent auto wake-screen)
                            if (n != null && n.fullScreenIntent != null) {
                                armVoIPPending();
                                param.setResult(null);
                                Log.i(TAG, tag + " cancel fullScreenIntent id=" + seenId
                                        + " ch=" + seenCh);
                                return;
                            }

                            // 2) VoIP channel name match → cancel
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                                    && seenCh != null && matchesAny(seenCh, VOIP_CHANNEL_KEYWORDS)) {
                                armVoIPPending();
                                param.setResult(null);
                                Log.i(TAG, tag + " cancel voip ch=" + seenCh);
                                return;
                            }

                            // 3) Text keyword match (last-resort, e.g. ongoing-call banner)
                            if (n != null && containsCallKeyword(n)) {
                                armVoIPPending();
                                param.setResult(null);
                                Log.i(TAG, tag + " cancel call-text id=" + seenId);
                                return;
                            }

                            // 4) L1-gap cancel — within 200ms of L1 block, drop any NM.notify()
                            //    Friend-scoped via sL1BlockedLastItem (set only when L1 blocked a
                            //    hidden friend). No hard-coded notification id — that only matched
                            //    one test account; the gap window itself is the friend gate.
                            int id = (Integer) param.args[1];
                            boolean blocked = sL1BlockedLastItem;
                            long    gap     = System.currentTimeMillis() - sL1BlockTs;
                            if (blocked && gap < 200) {
                                sL1BlockedLastItem = false;
                                param.setResult(null);
                                Log.i(TAG, tag + " cancel L1-gap id=" + id + " gap=" + gap + "ms");
                            } else {
                                sL1BlockedLastItem = false;
                                Log.i(TAG, tag + " pass id=" + id
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
    // SF — Service.startForeground() block
    //   Root cause of the status-bar "talking" call icon (bypasses NM.notify).
    //   docs/P22_PushFilter_VoIP.md §二 SF
    // =========================================================================

    private static void installStartForegroundBlock(XC_LoadPackage.LoadPackageParam lpparam,
                                                    final boolean pushProcess) {
        final String tag = pushProcess ? "[PF:SF:push]" : "[PF:SF]";

        // Overload 1: startForeground(int id, Notification n)
        try {
            XposedHelpers.findAndHookMethod(
                    Service.class, "startForeground",
                    int.class, Notification.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            handleStartForeground(param, pushProcess, tag);
                        }
                    });
            Log.i(TAG, tag + " hooked startForeground(int, Notification)");
        } catch (Throwable t) {
            Log.w(TAG, tag + " hook 2-arg fail: " + t);
        }

        // Overload 2 (API 29+): startForeground(int, Notification, int)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                XposedHelpers.findAndHookMethod(
                        Service.class, "startForeground",
                        int.class, Notification.class, int.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                handleStartForeground(param, pushProcess, tag);
                            }
                        });
                Log.i(TAG, tag + " hooked startForeground(int, Notification, int)");
            } catch (Throwable t) {
                Log.w(TAG, tag + " hook 3-arg fail: " + t);
            }
        }
    }

    private static void handleStartForeground(XC_MethodHook.MethodHookParam param,
                                              boolean pushProcess, String tag) {
        boolean hidden = pushProcess
                ? NativeBridge.isHidden()
                : StateMachine.getInstance().isActive();
        if (!hidden) return;

        Notification n = (Notification) param.args[1];
        if (n == null) return;

        boolean voipLike = (n.fullScreenIntent != null)
                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    && n.getChannelId() != null
                    && matchesAny(n.getChannelId(), VOIP_CHANNEL_KEYWORDS))
                || containsCallKeyword(n);
        if (!voipLike) return;

        Object svc = param.thisObject;
        if (svc instanceof Service) {
            AudioManager am = (AudioManager) ((Service) svc)
                    .getSystemService(android.content.Context.AUDIO_SERVICE);
            muteVoIPStreams(am);
        }
        armVoIPPending();
        param.setResult(null);
        Log.i(TAG, tag + " blocked startForeground"
                + (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? " ch=" + n.getChannelId() : ""));
    }

    // =========================================================================
    // CA — VideoActivity (full-screen incoming-call ring UI) → moveTaskToBack
    //   Background→foreground calls launch the full ring Activity (avatar +
    //   answer/decline). VC only catches the in-call render view, not this.
    //   moveTaskToBack (not finish) so the call + WeChat's own vibration survive.
    // =========================================================================

    private static void installCallActivityBlock(XC_LoadPackage.LoadPackageParam lpparam) {
        // F-26 trap: onResume/onStart are inherited from the Activity base, so
        // findAndHookMethod(VideoActivity, ...) does not intercept. Hook the base
        // Activity method via getDeclaredMethod + hookMethod, filter by class name.
        for (final String method : new String[] {"onResume", "onStart"}) {
            try {
                Method m = Activity.class.getDeclaredMethod(method);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!StateMachine.getInstance().isActive()) return;
                        if (!VOIP_ACTIVITY_CLASS.equals(param.thisObject.getClass().getName())) return;
                        try {
                            Activity act = (Activity) param.thisObject;
                            act.moveTaskToBack(true);
                            Log.i(TAG, "[PF:CA] moveTaskToBack VideoActivity (" + method + ")");
                        } catch (Throwable t) {
                            Log.w(TAG, "[PF:CA] moveTaskToBack fail: " + t);
                        }
                    }
                });
                Log.i(TAG, "[PF:CA] hooked Activity." + method);
            } catch (Throwable t) {
                Log.w(TAG, "[PF:CA] hook " + method + " fail: " + t);
            }
        }
    }

    /**
     * True if the view is one of WeChat's FloatBall framework views
     * (com.tencent.mm.plugin.ball.view.FloatBall* / FloatMenuView / ContentFloatBallView),
     * which back the floating "等待接听" call window. Probe-confirmed 2026-05-29.
     * Only acted on inside a call window (sVoIPCallPending) so non-call floating
     * balls (mini-program, etc.) are untouched.
     */
    private static boolean isFloatCallView(String cn) {
        return cn != null && cn.contains(".plugin.ball.view.");
    }

    /** Remove a view from its WindowManager (walks up to the root attached view). */
    private static void removeFromWindow(View view, String tag) {
        android.view.ViewParent parent = view.getParent();
        View top = view;
        while (parent instanceof View) {
            top = (View) parent;
            parent = parent.getParent();
        }
        try {
            android.view.ViewManager vm = (android.view.ViewManager)
                    top.getContext().getSystemService(android.content.Context.WINDOW_SERVICE);
            vm.removeView(top);
            Log.i(TAG, tag + " removeView " + view.getClass().getName());
        } catch (Throwable t) {
            Log.w(TAG, tag + " removeView fail: " + t);
        }
    }

    // =========================================================================
    // VC — VoIPRenderTextureView.onAttachedToWindow → removeView
    //   F-26: onAttachedToWindow defined on View parent; must hook
    //   View.class.getDeclaredMethod + hookMethod (findAndHookMethod misses).
    //   docs/P22_PushFilter_VoIP.md §二 VC
    // =========================================================================

    private static void installCallViewBlock(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Method onAttach = View.class.getDeclaredMethod("onAttachedToWindow");
            XposedBridge.hookMethod(onAttach, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!StateMachine.getInstance().isActive()) return;
                    View view = (View) param.thisObject;
                    String cn = view.getClass().getName();

                    // FB — floating "等待接听" call window (FloatBall* framework,
                    // probe-confirmed 2026-05-29). Background→home shows this overlay.
                    // Gate on the call window so non-call floating balls survive.
                    if (isFloatCallView(cn)) {
                        if (!sVoIPCallPending) return;
                        removeFromWindow(view, "[PF:FB]");
                        return;
                    }

                    if (!Arrays.asList(VOIP_VIEW_CLASSES).contains(cn)) return;

                    // Walk up to root FrameLayout attached to WindowManager
                    android.view.ViewParent parent = view.getParent();
                    View top = view;
                    while (parent instanceof View) {
                        top = (View) parent;
                        parent = parent.getParent();
                    }
                    try {
                        android.view.ViewManager vm = (android.view.ViewManager)
                                top.getContext().getSystemService(android.content.Context.WINDOW_SERVICE);
                        sRemovedByUs = true;
                        vm.removeView(top);
                        Log.i(TAG, "[PF:VC] removeView " + cn);
                    } catch (Throwable t) {
                        Log.w(TAG, "[PF:VC] removeView fail: " + t);
                    }
                    // Owner-side call vibrate is fired once on the pending rising
                    // edge inside armVoIPPending() (covers voice + video uniformly).
                }
            });
            Log.i(TAG, "[PF:VC] onAttachedToWindow hook ok");
        } catch (Throwable t) {
            Log.w(TAG, "[PF:VC] hook fail: " + t);
        }

        try {
            Method onDetach = View.class.getDeclaredMethod("onDetachedFromWindow");
            XposedBridge.hookMethod(onDetach, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    View view = (View) param.thisObject;
                    String cn = view.getClass().getName();
                    if (!Arrays.asList(VOIP_VIEW_CLASSES).contains(cn)) return;

                    if (sRemovedByUs) {
                        // Our own removeView triggered this; re-arm 120s
                        sRemovedByUs = false;
                        armVoIPPending();
                        Log.i(TAG, "[PF:VC] onDetach (ours) re-arm 120s");
                    } else {
                        // WeChat detached → call truly ended
                        AudioManager am = (AudioManager) view.getContext()
                                .getSystemService(android.content.Context.AUDIO_SERVICE);
                        clearVoIPPending(am);
                        Log.i(TAG, "[PF:VC] onDetach (call end) clear pending");
                    }
                }
            });
            Log.i(TAG, "[PF:VC] onDetachedFromWindow hook ok");
        } catch (Throwable t) {
            Log.w(TAG, "[PF:VC] detach hook fail: " + t);
        }
    }

    // =========================================================================
    // AT — AudioTrack.play() block (only while sVoIPCallPending == true)
    //   TRTC SDK hang-up "嘟" tone bypasses Vibrator + bypasses AudioManager volume.
    //   docs/P22_PushFilter_VoIP.md §二 AT
    // =========================================================================

    private static void installAudioTrackBlock(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    AudioTrack.class, "play",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (!sVoIPCallPending) return;
                            if (!StateMachine.getInstance().isActive()) return;
                            param.setResult(null);
                            Log.i(TAG, "[PF:AT] blocked AudioTrack.play()");
                        }
                    });
            Log.i(TAG, "[PF:AT] hooked");
        } catch (Throwable t) {
            Log.w(TAG, "[PF:AT] hook fail: " + t);
        }
    }

    // =========================================================================
    // MP — MediaPlayer.start() block (foreground "ding" + video hang-up tone)
    // =========================================================================

    private static void installMediaPlayerBlock(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    MediaPlayer.class, "start",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (NotifyRouter.sOurSound) return;
                            if (!StateMachine.getInstance().isActive()) return;
                            // Only silence media inside a hidden-friend call window, mirroring AT.
                            // Without this guard HIDDEN mode muted ALL playback (non-friend videos,
                            // user's own voice messages), violating the "friends-only" filter rule.
                            if (!sVoIPCallPending) return;
                            param.setResult(null);
                            Log.i(TAG, "[PF:MP] blocked MediaPlayer.start");
                        }
                    });
            Log.i(TAG, "[PF:MP] hooked");
        } catch (Throwable t) {
            Log.w(TAG, "[PF:MP] hook fail: " + t);
        }
    }

    // =========================================================================
    // VV — Vibrator.vibrate(*) block
    //   Exempts NotifyRouter.sOurVibration (our own VIBRATE policy pulse).
    // =========================================================================

    private static void installVibratorBlock(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                XposedHelpers.findAndHookMethod(
                        Vibrator.class, "vibrate", VibrationEffect.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                if (NotifyRouter.sOurVibration) return;
                                if (!StateMachine.getInstance().isActive()) return;
                                if (allowCallVibration()) {
                                    Log.i(TAG, "[PF:VV] allow WeChat call vibration (VIBRATE mode)");
                                    return;
                                }
                                param.setResult(null);
                                Log.i(TAG, "[PF:VV] blocked vibrate(VibrationEffect)");
                            }
                        });
            }
            //noinspection deprecation
            XposedHelpers.findAndHookMethod(
                    Vibrator.class, "vibrate", long[].class, int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (NotifyRouter.sOurVibration) return;
                            if (!StateMachine.getInstance().isActive()) return;
                            if (allowCallVibration()) {
                                Log.i(TAG, "[PF:VV] allow WeChat call vibration (VIBRATE mode)");
                                return;
                            }
                            param.setResult(null);
                            Log.i(TAG, "[PF:VV] blocked vibrate(long[])");
                        }
                    });
            Log.i(TAG, "[PF:VV] hooked");
        } catch (Throwable t) {
            Log.w(TAG, "[PF:VV] hook fail: " + t);
        }
    }

    /**
     * During a VoIP call window, when the dedicated call policy is VIBRATE we let
     * WeChat's OWN vibration play (UI/sound/screen are still suppressed by the other
     * hooks). This avoids fighting MIUI's truncation of app-initiated vibrations —
     * WeChat's native incoming-call vibration is the most reliable source.
     * Main-process only (Bridge has no MMKV in :push).
     */
    private static boolean allowCallVibration() {
        return !sPushProcess
                && sVoIPCallPending
                && Bridge.getInstance().getCallNotifyPolicy() == Bridge.NotifyPolicy.VIBRATE;
    }

    // =========================================================================
    // VW — PowerManager$WakeLock.acquire() + .release()
    //   Block screen-wake WakeLocks; mirror block in release() to avoid
    //   RuntimeException: WakeLock under-locked (worklog 2026-05-25).
    // =========================================================================

    private static void installWakeLockBlock(XC_LoadPackage.LoadPackageParam lpparam,
                                             final boolean pushProcess) {
        try {
            final Class<?> wl = PowerManager.WakeLock.class;

            // acquire()
            XposedHelpers.findAndHookMethod(wl, "acquire",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            tryBlockWakeLock(param, pushProcess);
                        }
                    });
            // acquire(long timeout)
            XposedHelpers.findAndHookMethod(wl, "acquire", long.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            tryBlockWakeLock(param, pushProcess);
                        }
                    });
            // release()
            XposedHelpers.findAndHookMethod(wl, "release",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (sBlockedWakeLocks.remove(param.thisObject) != null) {
                                param.setResult(null);
                                Log.i(TAG, "[PF:VW] swallow release (was blocked)");
                            }
                        }
                    });
            // release(int flags)
            XposedHelpers.findAndHookMethod(wl, "release", int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (sBlockedWakeLocks.remove(param.thisObject) != null) {
                                param.setResult(null);
                                Log.i(TAG, "[PF:VW] swallow release(int) (was blocked)");
                            }
                        }
                    });
            Log.i(TAG, "[PF:VW] hooked");
        } catch (Throwable t) {
            Log.w(TAG, "[PF:VW] hook fail: " + t);
        }
    }

    private static void tryBlockWakeLock(XC_MethodHook.MethodHookParam param,
                                         boolean pushProcess) {
        // Iron rule 30: :push has no Java Bridge/MMKV — must read NativeBridge only.
        boolean hidden = pushProcess
                ? NativeBridge.isHidden()
                : StateMachine.getInstance().isActive();
        if (!hidden) return;
        Object lock = param.thisObject;
        int flags;
        try {
            Field f = PowerManager.WakeLock.class.getDeclaredField("mFlags");
            f.setAccessible(true);
            flags = f.getInt(lock);
        } catch (Throwable t) {
            return; // can't inspect → leave alone
        }
        if ((flags & SCREEN_WAKE_FLAGS) == 0) return;
        sBlockedWakeLocks.put(lock, Boolean.TRUE);
        param.setResult(null);
        Log.i(TAG, "[PF:VW] blocked acquire flags=0x" + Integer.toHexString(flags));
    }

    // =========================================================================
    // PiP — Activity.enterPictureInPictureMode(*) block
    //   Prevents the floating talk-window when WeChat backgrounded mid-call.
    //   docs/P22_PushFilter_VoIP.md §二 PiP
    // =========================================================================

    private static void installPipBlock(XC_LoadPackage.LoadPackageParam lpparam) {
        // Overload 1: API 24+ no-arg
        try {
            XposedHelpers.findAndHookMethod(
                    Activity.class, "enterPictureInPictureMode",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (!StateMachine.getInstance().isActive()) return;
                            if (!sVoIPCallPending) return;
                            param.setResult(false);
                            Log.i(TAG, "[PF:PiP] blocked enterPiP()");
                        }
                    });
        } catch (Throwable t) {
            Log.w(TAG, "[PF:PiP] hook no-arg fail: " + t);
        }
        // Overload 2: API 26+ with PictureInPictureParams (load via reflection to
        // avoid minSdk issues)
        try {
            Class<?> pipParams = Class.forName("android.app.PictureInPictureParams");
            XposedHelpers.findAndHookMethod(
                    Activity.class, "enterPictureInPictureMode", pipParams,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (!StateMachine.getInstance().isActive()) return;
                            if (!sVoIPCallPending) return;
                            param.setResult(false);
                            Log.i(TAG, "[PF:PiP] blocked enterPiP(params)");
                        }
                    });
            Log.i(TAG, "[PF:PiP] hooked");
        } catch (Throwable t) {
            Log.w(TAG, "[PF:PiP] hook 1-arg fail: " + t);
        }
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
        // sHiddenBlocked accumulation caused over-subtraction, zeroing non-hidden
        // friends' badges. Will be replaced by WeChatDND (官方免打扰) which
        // natively excludes hidden friends from the badge count.
        Log.i(TAG, "[PF:L4c] disabled (pending WeChatDND)");
    }

    // =========================================================================
    // VoIP lifecycle helpers
    // =========================================================================

    /** Arm or refresh the 120s sVoIPCallPending fallback timer. */
    private static void armVoIPPending() {
        sVoIPCallPending = true;
        if (sClearVoIPPendingRunnable != null) {
            sMainHandler.removeCallbacks(sClearVoIPPendingRunnable);
        }
        // No custom vibration here: in VIBRATE mode the VV hook lets WeChat's own
        // call vibration through (see allowCallVibration()); in OFF mode it's blocked.
        sClearVoIPPendingRunnable = () -> {
            sVoIPCallPending = false;
            // No AudioManager context here; restoreVoIPStreams runs in VC.onDetach.
            // If we hit the 120s fallback without VC detach, leave streams as-is
            // (worst case: user mutes manually). Avoid grabbing am from random thread.
            Log.i(TAG, "[PF:VC] sVoIPCallPending=false (120s fallback)");
        };
        sMainHandler.postDelayed(sClearVoIPPendingRunnable, VOIP_PENDING_TTL_MS);
    }

    /** Clear pending state and restore audio streams (called from VC.onDetach call-end). */
    private static void clearVoIPPending(AudioManager am) {
        sVoIPCallPending = false;
        if (sClearVoIPPendingRunnable != null) {
            sMainHandler.removeCallbacks(sClearVoIPPendingRunnable);
            sClearVoIPPendingRunnable = null;
        }
        // Restore streams after a short delay (let WeChat's own cleanup finish first)
        if (am != null) {
            sMainHandler.postDelayed(() -> restoreVoIPStreams(am), 5_000L);
        }
    }

    /** Snapshot + zero ring/voice/music streams (STREAM_RING intentionally excluded). */
    private static void muteVoIPStreams(AudioManager am) {
        if (am == null || sMutedVols != null) return; // idempotent
        try {
            int[] snap = new int[MUTE_STREAMS.length];
            for (int i = 0; i < MUTE_STREAMS.length; i++) {
                snap[i] = am.getStreamVolume(MUTE_STREAMS[i]);
                am.setStreamVolume(MUTE_STREAMS[i], 0, 0);
            }
            sMutedVols = snap;
            Log.i(TAG, "[PF:SF] muteVoIPStreams voice/music=" + snap[0] + "/" + snap[1]);
        } catch (Throwable t) {
            Log.w(TAG, "[PF:SF] muteVoIPStreams err: " + t);
        }
    }

    /** Restore stream volumes from the snapshot taken by muteVoIPStreams. */
    private static void restoreVoIPStreams(AudioManager am) {
        if (am == null) return;
        int[] snap = sMutedVols;
        if (snap == null) return;
        try {
            for (int i = 0; i < MUTE_STREAMS.length && i < snap.length; i++) {
                am.setStreamVolume(MUTE_STREAMS[i], snap[i], 0);
            }
            Log.i(TAG, "[PF:SF] restoreVoIPStreams ok");
        } catch (Throwable t) {
            Log.w(TAG, "[PF:SF] restoreVoIPStreams err: " + t);
        } finally {
            sMutedVols = null;
        }
    }

    // =========================================================================
    // Small utility helpers
    // =========================================================================

    private static boolean matchesAny(String s, String[] needles) {
        if (s == null) return false;
        for (String k : needles) if (s.contains(k)) return true;
        return false;
    }

    private static boolean containsCallKeyword(Notification n) {
        if (n == null || n.extras == null) return false;
        CharSequence ttl = n.extras.getCharSequence(Notification.EXTRA_TITLE, "");
        CharSequence txt = n.extras.getCharSequence(Notification.EXTRA_TEXT, "");
        String body = (ttl != null ? ttl.toString() : "")
                + (txt != null ? txt.toString() : "");
        return matchesAny(body, VOIP_TEXT_KEYWORDS);
    }

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
