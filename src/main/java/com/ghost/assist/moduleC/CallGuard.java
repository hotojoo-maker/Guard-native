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

import com.ghost.assist.core.NativeBridge;
import com.ghost.assist.core.StateMachine;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * CallGuard — 8.0.71 语音/视频来电拦截层（从 PushFilter 拆出，行为不变）。
 *
 * 权威设计文档：docs/P22_PushFilter_VoIP.md（装机验证 2026-05-29）。
 * 与 PushFilter 的关系：PushFilter 管「消息通知 + 角标」并持有唯一的
 * NotificationManager.notify hook；该 hook 的 VoIP 部分委托 {@link #handleNmVoip}。
 *
 * 主进程 hook 链：
 *   SF   Service.startForeground()                     状态栏来电图标（亮屏根因之一）
 *   CA   VideoActivity onResume/onStart → moveTaskToBack 全屏来电屏（后台→前台）
 *   VC   VoIPRender*View.onAttachedToWindow → removeView 前台渲染浮层
 *   FB   WindowManagerImpl.addView block FloatBall*     「等待接听」浮球（零渲染）
 *   AM   AudioManager.setMode() block                   防 TRTC 激活通话音频模式
 *   AT   AudioTrack.play() block                        TRTC SDK 通话/挂断音
 *   MP   MediaPlayer.start() block                      视频挂断「嘟」/in-app 音
 *   VV   Vibrator.vibrate(*) block                      拦微信自身振动（我们 onset 豁免）
 *   VW   WakeLock.acquire/release block                 亮屏 + under-locked 修复
 *   PiP  enterPictureInPictureMode + setPictureInPictureParams + onUserLeaveHint(UL)
 *
 * :push 进程子集（installForPush）：SF + VW（铁律 30：只读 NativeBridge）。
 *
 * 生命周期：sVoIPCallPending 由 SF/NM armPending() 置位，仅由 120s fallback 清零
 * （不用 stopForeground 判挂断——视频服务每 ~10s 循环 start/stopForeground，见 F-36）。
 * 来电振动：上升沿 fireAlert(CALL) 单次 onset（静默=不振，震动=振一次），不做持续响铃。
 *
 * 铁律：F-26（父类方法 getDeclaredMethod+hookMethod）、F-23（不碰微信 SO）、
 * 铁律 30（:push 只读 NativeBridge）、F-36（来电拦截 7 条已证伪路径永久禁止）。
 */
public final class CallGuard {

    private static final String TAG = "NCL";

    private CallGuard() {}

    // -------------------------------------------------------------------------
    // VoIP lifecycle state (shared across SF / NM / VC / AT / MP / FB / PiP / VW)
    // -------------------------------------------------------------------------
    private static volatile boolean sVoIPCallPending = false;

    // True when running inside the :push process. Iron rule 30: :push must not
    // touch Java Bridge / NotifyRouter (no MMKV there). Set in installForPush().
    private static volatile boolean sPushProcess = false;

    // Count of VoIP render views WE removeView()'d that have not yet fired their
    // matching onDetach. A single boolean broke when a video call attaches BOTH
    // VoIPRenderTextureView and VoIPMPVoIPVideoView (F-36 d).
    private static volatile int sRemovedByUsCount = 0;

    // 120s fallback. docs/P22_PushFilter_VoIP.md §三.
    private static final long    VOIP_PENDING_TTL_MS = 120_000L;
    private static Runnable      sClearVoIPPendingRunnable = null;
    private static final Handler sMainHandler = new Handler(Looper.getMainLooper());

    // Onset-vibration session gate, DECOUPLED from the 120s audio-pending. One onset
    // per call "session"; a new session = first call activity after >ONSET_QUIET_MS of
    // silence. The SF/NM cycle within a single call fires every ~10s, so this gate keeps
    // one call = one onset, while back-to-back real calls (minutes apart) each re-fire.
    private static final long    ONSET_QUIET_MS = 15_000L;
    private static volatile long sLastCallActivityMs = 0;

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

    // WakeLock under-locked crash fix: WeakHashMap of locks WE blocked the acquire()
    // of, so we also short-circuit their release() (worklog 2026-05-25).
    private static final Map<Object, Boolean> sBlockedWakeLocks =
            Collections.synchronizedMap(new WeakHashMap<>());

    // Cover-all wake flags (any of these set → likely a screen-on intent)
    private static final int SCREEN_WAKE_FLAGS =
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK   // 0x0000000a
            | PowerManager.ACQUIRE_CAUSES_WAKEUP   // 0x10000000
            | PowerManager.ON_AFTER_RELEASE;       // 0x20000000

    // =========================================================================
    // Entry points
    // =========================================================================

    /** Main process install — full VoIP call suppression chain. */
    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        installStartForegroundBlock(lpparam, false);
        installCallActivityBlock(lpparam);
        installCallViewBlock(lpparam);
        installFloatBallBlock(lpparam);
        installAudioModeBlock(lpparam);
        installAudioTrackBlock(lpparam);
        installMediaPlayerBlock(lpparam);
        installVibratorBlock(lpparam);
        installWakeLockBlock(lpparam, false);
        installPipBlock(lpparam);
        Log.i(TAG, "[CG] CallGuard installed (main, full chain)");
    }

    /** :push process subset — status-bar call icon + screen-wake only (iron rule 30). */
    public static void installForPush(XC_LoadPackage.LoadPackageParam lpparam) {
        sPushProcess = true;
        installStartForegroundBlock(lpparam, true);
        installWakeLockBlock(lpparam, true);
        Log.i(TAG, "[CG] installForPush SF+VW (push-process) ok");
    }

    // =========================================================================
    // NM delegation — called from PushFilter's single NotificationManager.notify hook
    // =========================================================================

    /**
     * VoIP-notification detection + cancel for the NM layer. PushFilter owns the only
     * NotificationManager.notify hook; it calls this first. Returns true if this notify
     * was a VoIP call notification (and was cancelled here); false → caller falls back
     * to the message L1-gap logic.
     */
    public static boolean handleNmVoip(XC_MethodHook.MethodHookParam param, Notification n,
                                       String ch, int id, String tag) {
        // 1) Strip fullScreenIntent → cancel (prevent auto wake-screen)
        if (n != null && n.fullScreenIntent != null) {
            armPending();
            param.setResult(null);
            Log.i(TAG, tag + " cancel fullScreenIntent id=" + id + " ch=" + ch);
            return true;
        }
        // 2) VoIP channel name match → cancel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && ch != null && matchesAny(ch, VOIP_CHANNEL_KEYWORDS)) {
            armPending();
            param.setResult(null);
            Log.i(TAG, tag + " cancel voip ch=" + ch);
            return true;
        }
        // 3) Text keyword match (last-resort, e.g. ongoing-call banner)
        if (n != null && containsCallKeyword(n)) {
            armPending();
            param.setResult(null);
            Log.i(TAG, tag + " cancel call-text id=" + id);
            return true;
        }
        return false;
    }

    // =========================================================================
    // SF — Service.startForeground() block
    // =========================================================================

    private static void installStartForegroundBlock(XC_LoadPackage.LoadPackageParam lpparam,
                                                    final boolean pushProcess) {
        final String tag = pushProcess ? "[PF:SF:push]" : "[PF:SF]";
        try {
            XposedHelpers.findAndHookMethod(
                    Service.class, "startForeground", int.class, Notification.class,
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                XposedHelpers.findAndHookMethod(
                        Service.class, "startForeground", int.class, Notification.class, int.class,
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
        // NOTE: deliberately NOT hooking stopForeground as a "call ended" signal — the
        // video VoIP service cycles start/stopForeground every ~10s (F-36 c). pending is
        // cleared by the 120s fallback only, keeping AT/MP armed across the whole call.
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
        armPending();
        param.setResult(null);
        Log.i(TAG, tag + " blocked startForeground"
                + (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? " ch=" + n.getChannelId() : ""));
    }

    // =========================================================================
    // CA — VideoActivity full-screen ring UI → moveTaskToBack
    // =========================================================================

    private static void installCallActivityBlock(XC_LoadPackage.LoadPackageParam lpparam) {
        for (final String method : new String[] {"onResume", "onStart"}) {
            try {
                Method m = Activity.class.getDeclaredMethod(method);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!StateMachine.getInstance().isActive()) return;
                        if (!VOIP_ACTIVITY_CLASS.equals(param.thisObject.getClass().getName())) return;
                        try {
                            ((Activity) param.thisObject).moveTaskToBack(true);
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

    /** True if the view backs WeChat's FloatBall "等待接听" window (probe 2026-05-29). */
    private static boolean isFloatCallView(String cn) {
        return cn != null && cn.contains(".plugin.ball.view.");
    }

    // =========================================================================
    // VC — VoIPRender*View.onAttachedToWindow → removeView
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
                    // FB float-ball blocked at WindowManagerImpl.addView (no render frame);
                    // this hook only removes the VoIP render views.
                    if (!Arrays.asList(VOIP_VIEW_CLASSES).contains(cn)) return;

                    android.view.ViewParent parent = view.getParent();
                    View top = view;
                    while (parent instanceof View) {
                        top = (View) parent;
                        parent = parent.getParent();
                    }
                    try {
                        android.view.ViewManager vm = (android.view.ViewManager)
                                top.getContext().getSystemService(android.content.Context.WINDOW_SERVICE);
                        sRemovedByUsCount++;
                        vm.removeView(top);
                        Log.i(TAG, "[PF:VC] removeView " + cn);
                    } catch (Throwable t) {
                        Log.w(TAG, "[PF:VC] removeView fail: " + t);
                    }
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

                    if (sRemovedByUsCount > 0) {
                        // One of OUR removeView()s; re-arm and wait for the next.
                        sRemovedByUsCount--;
                        armPending();
                        Log.i(TAG, "[PF:VC] onDetach (ours) re-arm 120s, pendingDetach="
                                + sRemovedByUsCount);
                    } else {
                        // WeChat detached a view we never removed → call truly ended.
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
    // FB — WindowManagerImpl.addView() block for FloatBall* views
    // =========================================================================

    private static void installFloatBallBlock(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.view.WindowManagerImpl", lpparam.classLoader, "addView",
                    View.class, android.view.ViewGroup.LayoutParams.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (!StateMachine.getInstance().isActive()) return;
                            if (!sVoIPCallPending) return;
                            Object v = param.args[0];
                            if (!(v instanceof View)) return;
                            String cn = v.getClass().getName();
                            if (isFloatCallView(cn)) {
                                param.setResult(null);
                                Log.i(TAG, "[PF:FB] blocked addView " + cn);
                                return;
                            }
                            // Diagnostic: log other call-window overlays + window params so
                            // the video mini-window (a plain FrameLayout) can be pinned.
                            String lpInfo = "";
                            Object lp = param.args[1];
                            if (lp instanceof android.view.WindowManager.LayoutParams) {
                                android.view.WindowManager.LayoutParams w =
                                        (android.view.WindowManager.LayoutParams) lp;
                                lpInfo = " type=" + w.type + " flags=0x" + Integer.toHexString(w.flags)
                                        + " gravity=" + w.gravity + " w=" + w.width + " h=" + w.height;
                            }
                            Log.i(TAG, "[PF:FB] seen addView (call window) " + cn + lpInfo);
                        }
                    });
            Log.i(TAG, "[PF:FB] WindowManagerImpl.addView hook ok");
        } catch (Throwable t) {
            Log.w(TAG, "[PF:FB] hook fail: " + t);
        }
    }

    // =========================================================================
    // AM — AudioManager.setMode() block during a call window
    // =========================================================================

    private static void installAudioModeBlock(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    AudioManager.class, "setMode", int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (!StateMachine.getInstance().isActive()) return;
                            if (!sVoIPCallPending) return;
                            param.setResult(null);
                            Log.i(TAG, "[PF:AM] blocked setMode(" + param.args[0] + ")");
                        }
                    });
            Log.i(TAG, "[PF:AM] hooked");
        } catch (Throwable t) {
            Log.w(TAG, "[PF:AM] hook fail: " + t);
        }
    }

    // =========================================================================
    // AT — AudioTrack.play() block (only while sVoIPCallPending)
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
    // MP — MediaPlayer.start() block (only while sVoIPCallPending; F-36 f)
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
    // VV — Vibrator.vibrate(*) block (WeChat's own; our onset exempt via sOurVibration)
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
                            param.setResult(null);
                            Log.i(TAG, "[PF:VV] blocked vibrate(long[])");
                        }
                    });
            Log.i(TAG, "[PF:VV] hooked");
        } catch (Throwable t) {
            Log.w(TAG, "[PF:VV] hook fail: " + t);
        }
    }

    // =========================================================================
    // VW — PowerManager$WakeLock.acquire()/release() block (screen-wake)
    // =========================================================================

    private static void installWakeLockBlock(XC_LoadPackage.LoadPackageParam lpparam,
                                             final boolean pushProcess) {
        try {
            final Class<?> wl = PowerManager.WakeLock.class;
            XposedHelpers.findAndHookMethod(wl, "acquire",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            tryBlockWakeLock(param, pushProcess);
                        }
                    });
            XposedHelpers.findAndHookMethod(wl, "acquire", long.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            tryBlockWakeLock(param, pushProcess);
                        }
                    });
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

    private static void tryBlockWakeLock(XC_MethodHook.MethodHookParam param, boolean pushProcess) {
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
            return;
        }
        if ((flags & SCREEN_WAKE_FLAGS) == 0) return;
        sBlockedWakeLocks.put(lock, Boolean.TRUE);
        param.setResult(null);
        Log.i(TAG, "[PF:VW] blocked acquire flags=0x" + Integer.toHexString(flags));
    }

    // =========================================================================
    // PiP — enterPictureInPictureMode + setPictureInPictureParams + onUserLeaveHint(UL)
    // =========================================================================

    private static void installPipBlock(XC_LoadPackage.LoadPackageParam lpparam) {
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
        // PiP param strip — swallow auto-enter-PiP during a call window.
        try {
            Class<?> pipParams = Class.forName("android.app.PictureInPictureParams");
            XposedHelpers.findAndHookMethod(
                    Activity.class, "setPictureInPictureParams", pipParams,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (!StateMachine.getInstance().isActive()) return;
                            if (!sVoIPCallPending) return;
                            param.setResult(null);
                            Log.i(TAG, "[PF:PiP] stripped setPictureInPictureParams");
                        }
                    });
            Log.i(TAG, "[PF:PiP] setPictureInPictureParams hook ok");
        } catch (Throwable t) {
            Log.w(TAG, "[PF:PiP] setParams hook fail: " + t);
        }
        // UL — onUserLeaveHint fallback (Home pressed mid-call → moveTaskToBack).
        try {
            Method ulh = Activity.class.getDeclaredMethod("onUserLeaveHint");
            ulh.setAccessible(true);
            XposedBridge.hookMethod(ulh, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!StateMachine.getInstance().isActive()) return;
                    if (!sVoIPCallPending) return;
                    if (!VOIP_ACTIVITY_CLASS.equals(param.thisObject.getClass().getName())) return;
                    try {
                        ((Activity) param.thisObject).moveTaskToBack(true);
                        Log.i(TAG, "[PF:UL] moveTaskToBack on userLeaveHint");
                    } catch (Throwable t) {
                        Log.w(TAG, "[PF:UL] moveTaskToBack fail: " + t);
                    }
                }
            });
            Log.i(TAG, "[PF:UL] onUserLeaveHint hook ok");
        } catch (Throwable t) {
            Log.w(TAG, "[PF:UL] hook fail: " + t);
        }
    }

    // =========================================================================
    // VoIP lifecycle helpers
    // =========================================================================

    private static android.content.Context appContext() {
        try {
            return (android.content.Context) Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Arm/refresh the 120s pending timer; fire ONE onset vibration per call session. */
    private static void armPending() {
        sVoIPCallPending = true;
        if (sClearVoIPPendingRunnable != null) {
            sMainHandler.removeCallbacks(sClearVoIPPendingRunnable);
        }
        // Onset vibration: one per call session (VIBRATE policy only; OFF stays silent).
        // Gated by ONSET_QUIET_MS, NOT by the 120s pending — so consecutive calls each
        // re-fire while the ~10s SF/NM cycle inside one call does not. WeChat's own
        // vibration is blocked by VV (no loop). Main process only (iron rule 30).
        long now = System.currentTimeMillis();
        if (!sPushProcess && now - sLastCallActivityMs > ONSET_QUIET_MS) {
            android.content.Context ctx = appContext();
            if (ctx != null) NotifyRouter.fireAlert(ctx, NotifyRouter.EventType.CALL);
        }
        sLastCallActivityMs = now;
        sClearVoIPPendingRunnable = () -> {
            sVoIPCallPending = false;
            Log.i(TAG, "[PF:VC] sVoIPCallPending=false (120s fallback)");
        };
        sMainHandler.postDelayed(sClearVoIPPendingRunnable, VOIP_PENDING_TTL_MS);
    }

    private static void clearVoIPPending(AudioManager am) {
        sVoIPCallPending = false;
        if (sClearVoIPPendingRunnable != null) {
            sMainHandler.removeCallbacks(sClearVoIPPendingRunnable);
            sClearVoIPPendingRunnable = null;
        }
        if (am != null) {
            sMainHandler.postDelayed(() -> restoreVoIPStreams(am), 5_000L);
        }
    }

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
}
