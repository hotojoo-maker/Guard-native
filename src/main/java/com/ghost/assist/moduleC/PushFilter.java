package com.ghost.assist.moduleC;

import android.app.Notification;
import android.util.Log;

import com.ghost.assist.core.Bridge;
import com.ghost.assist.core.StateMachine;

import android.app.Activity;
import android.app.Notification;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * PushFilter — v2 notification / unread / badge suppression for hidden friends.
 *
 * Hook chain (confirmed 2026-05-22):
 *
 *   L1  LinkedList.add(NotificationItem)
 *         this.h = talker wxid ← MF already confirmed this path
 *         setResult(false) → item never queued → a(Context) never called
 *                          → NotificationManager.notify() never fired
 *         CONFIRMED by: [MF:LLadd cls=NotificationItem] h=wxid_xxx
 *
 *   NM  NotificationManager.notify() — diagnostic only, confirms block worked
 *         If we still see id=-525958226 after L1 block, something bypassed the queue.
 *
 *   L4b MainTabUI.i()  — tab unread digit → 0 when hidden (belt+suspenders)
 *   L4c h0.d(int)      — OEM badge count → 0 when hidden (belt+suspenders)
 *
 * Dead paths (do not restore):
 *   NotificationItem.a(Context)  — public final + ART AOT direct-call, Xposed misses
 *   x.a(f9)                      — zero-hit in 8.0.71
 */
public class PushFilter {

    private static final String TAG = "NCL";

    private static final String NI_CLASS        = "com.tencent.mm.booter.notification.NotificationItem";
    private static final String MAINTABUI_CLASS = "com.tencent.mm.ui.MainTabUI";
    private static final String H0_CLASS        = "com.tencent.mm.booter.notification.h0";

    private static volatile boolean sInstalled = false;

    // Intent keys WeChat uses to pass caller wxid to the call Activity
    private static final String[] CALL_INTENT_KEYS = {
        "ContactUsername", "username", "voip_to_username",
        "fromUsername", "talker", "Voip_User"
    };

    // Set by L1 when a hidden friend's NotificationItem is blocked.
    // NM.notify() hook checks this within the 6ms window (confirmed timing 2026-05-22)
    // and cancels the notification. Reset on first use.
    private static volatile boolean sL1BlockedLastItem = false;
    private static volatile long    sL1BlockTs         = 0;

    // Count of notifications blocked for hidden friends since last reset.
    // L4c uses this to subtract from WeChat's total badge count instead of zeroing.
    // Reset when WeChat reports 0 unread (all messages read).
    private static volatile int sHiddenBlocked = 0;

    // -------------------------------------------------------------------------
    // Entry point — main process (Application.onCreate)
    // -------------------------------------------------------------------------

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        if (sInstalled) return;
        sInstalled = true;
        installL1(lpparam);
        installNmDiag(lpparam);
        installCallActivityBlock(lpparam);
        installMediaPlayerBlock(lpparam);
        installVibratorBlock(lpparam);
        installL4b(lpparam);
        installL4c(lpparam);
        Log.i(TAG, "[PF] PushFilter installed");
    }

    /** No-op stub — :push process; all hooks in main process. */
    public static void installForPush(XC_LoadPackage.LoadPackageParam lpparam) {
        Log.i(TAG, "[PF] installForPush noop");
    }

    // =========================================================================
    // L1 — LinkedList.add(Object): NotificationItem queue gate
    //
    // WeChat queues outgoing notifications via LinkedList.add(NotificationItem).
    // this.h field = talker wxid (confirmed by MF probe).
    // setResult(false) prevents enqueue → a(Context) never called → no notification.
    // =========================================================================

    private static void installL1(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Method addMethod = LinkedList.class.getMethod("add", Object.class);
            XposedBridge.hookMethod(addMethod, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Object item = param.args[0];
                    if (item == null) return;
                    if (!NI_CLASS.equals(item.getClass().getName())) return;

                    String talker = readFieldH(item);
                    Log.i(TAG, "[PF:L1] LL.add NotificationItem talker=" + talker
                            + " active=" + StateMachine.getInstance().isActive());

                    if (!StateMachine.getInstance().isActive()) return;
                    if (talker == null || talker.isEmpty()) return;
                    if (!Bridge.getInstance().shouldHideId(talker)) return;

                    sL1BlockedLastItem = true;
                    sL1BlockTs = System.currentTimeMillis();
                    sHiddenBlocked++;
                    param.setResult(false); // block queue entry
                    Log.i(TAG, "[PF:L1] block LL.add talker=" + talker
                            + " hiddenBlocked=" + sHiddenBlocked);

                    // Fire out-of-band alert (VIBRATE/SOUND) per policy — no banner, no NM.notify()
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
    // NM diag — NotificationManager.notify(): diagnostic only
    //
    // If NM.notify(id=-525958226) fires AFTER L1 blocked → a(Context) bypassed queue.
    // If it does NOT fire after L1 block → L1 is the correct and complete solution.
    // =========================================================================

    private static void installNmDiag(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.NotificationManager", lpparam.classLoader,
                    "notify", String.class, int.class, Notification.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            // Block voip/ringtone channel when HIDDEN (caller not identifiable in 8.0.71)
                            if (StateMachine.getInstance().isActive()
                                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                Notification n = (Notification) param.args[2];
                                String ch = n != null ? n.getChannelId() : null;
                                if (ch != null && (ch.contains("voip") || ch.contains("ringtone"))) {
                                    param.setResult(null);
                                    Log.i(TAG, "[PF:NM] cancel voip ch=" + ch);
                                    return;
                                }
                            }

                            int id = (Integer) param.args[1];
                            if (id != -525958226) return;
                            boolean blocked = sL1BlockedLastItem;
                            long    gap     = System.currentTimeMillis() - sL1BlockTs;
                            if (blocked && gap < 200) {
                                // L1 blocked a hidden friend's item ≤200ms ago → cancel this notification
                                sL1BlockedLastItem = false;
                                param.setResult(null);
                                Log.i(TAG, "[PF:NM] cancel id=" + id + " gap=" + gap + "ms");
                            } else {
                                sL1BlockedLastItem = false;
                                Log.i(TAG, "[PF:NM] pass id=" + id + " blocked=" + blocked + " gap=" + gap + "ms");
                            }
                        }
                    });
            Log.i(TAG, "[PF:NM] NotificationManager.notify diag hooked");
        } catch (Throwable t) {
            Log.w(TAG, "[PF:NM] diag hook fail: " + t);
        }
    }

    // =========================================================================
    // CA — Activity.onCreate(): intercept incoming call UI
    //
    // Fuzzy-match class name (voip/call/video/dial) — version-adaptive.
    // Extract caller wxid from Intent extras using known WeChat keys.
    // If hidden + hidden friend → finish() immediately, call UI never renders.
    // =========================================================================

    private static void installCallActivityBlock(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    Activity.class, "onCreate", Bundle.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            if (!StateMachine.getInstance().isActive()) return;

                            Activity activity = (Activity) param.thisObject;
                            String name = activity.getClass().getName().toLowerCase();
                            if (!name.contains("voip") && !name.contains("call")
                                    && !name.contains("video") && !name.contains("dial")) return;

                            // wxid not accessible via Intent/fields in 8.0.71 Flutter call UI.
                            // Block ALL incoming call activities when HIDDEN — correct behavior
                            // since HIDDEN = complete WeChat presence concealment.
                            activity.finish();
                            Log.i(TAG, "[PF:CA] finish call activity="
                                    + activity.getClass().getSimpleName());
                        }
                    });
            Log.i(TAG, "[PF:CA] Activity.onCreate hook ok");
        } catch (Throwable t) {
            Log.w(TAG, "[PF:CA] hook fail: " + t);
        }
    }

    private static String extractCallerFromIntent(Intent intent) {
        if (intent == null) return null;
        for (String key : CALL_INTENT_KEYS) {
            String v = intent.getStringExtra(key);
            if (v != null && !v.isEmpty()) return v;
        }
        // Diagnostic: dump ALL extras (any type) to find the correct key
        try {
            Log.i(TAG, "[PF:CA:intent] action=" + intent.getAction()
                    + " data=" + intent.getDataString());
            Bundle extras = intent.getExtras();
            if (extras == null) {
                Log.i(TAG, "[PF:CA:extras] null");
            } else {
                Log.i(TAG, "[PF:CA:extras] size=" + extras.size());
                for (String k : extras.keySet()) {
                    Object val = extras.get(k);
                    String type = val == null ? "null" : val.getClass().getSimpleName();
                    Log.i(TAG, "[PF:CA:extras]   " + k + "[" + type + "]=" + val);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "[PF:CA:extras] err: " + t);
        }
        return null;
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
    // L4c — h0.d(int): OEM desktop badge dispatcher (main process)
    // =========================================================================

    private static void installL4c(XC_LoadPackage.LoadPackageParam lpparam) {
        // L4c disabled — sHiddenBlocked accumulation causes over-subtraction,
        // zeroing non-hidden friends' badges. Will be replaced by WeChatDND (官方免打扰)
        // which natively excludes hidden friends from badge count.
        Log.i(TAG, "[PF:L4c] disabled (pending WeChatDND)");
    }

    // =========================================================================
    // MP — MediaPlayer.start(): foreground message "ding" + video hang-up tone
    //
    // Frida-confirmed (2026-05-25): WeChat plays notification sound via MediaPlayer
    // when the app is in the foreground.  Block all start() calls when HIDDEN so
    // no "ding" leaks even if the notification itself was already blocked by L1.
    //
    // Exception: NotifyRouter.SOUND mode creates its own MediaPlayer.
    // NotifyRouter.sOurSound is set true around that call so we skip it here.
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
    // VV — Vibrator.vibrate(): suppress WeChat's default notification vibration
    //
    // When HIDDEN, block all Vibrator.vibrate() calls EXCEPT the custom pattern
    // fired by NotifyRouter.fireAlert() (guarded by NotifyRouter.sOurVibration).
    // This ensures VIBRATE policy produces only our two-pulse pattern, not WeChat's.
    // =========================================================================

    private static void installVibratorBlock(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // API 26+ overload: vibrate(VibrationEffect)
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
            // Legacy overload: vibrate(long[], int)
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
    // Helper: read field "h" (talker wxid) from NotificationItem
    // =========================================================================

    private static String readFieldH(Object ni) {
        // Use the object's own Class (tinker classloader) — avoids cross-classloader rejection
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
