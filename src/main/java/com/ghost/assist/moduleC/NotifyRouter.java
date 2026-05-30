package com.ghost.assist.moduleC;

import android.app.Notification;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import com.ghost.assist.core.Bridge;

/**
 * NotifyRouter — single responsibility: map (talker, event-type) → OFF|VIBRATE|SOUND
 * and apply the resulting policy to a Notification object or Vibrator.
 *
 * PushFilter calls eval() and executes the returned Action.  This class has
 * zero state and zero side-effects beyond what the caller explicitly asks for.
 *
 * Policy source: Bridge.getNotifyPolicy() (MMKV key "nfyp").
 *
 * Table (mirrors Catfish §16.3 three-tier design):
 *
 *  MSG (message) — three-tier policy via Bridge.getNotifyPolicy() (key "nfyp"):
 *    OFF      → BLOCK
 *    VIBRATE  → BLOCK + out-of-band vibrate
 *    SOUND    → BLOCK + custom ringtone
 *
 *  CALL (VoIP voice/video) — two-tier dedicated policy via Bridge.getCallNotifyPolicy()
 *  (key "cnfy", default OFF). Calls NEVER ring (anti-exposure); ringtone is not an option.
 *    OFF      → BLOCK, fully silent (default)
 *    VIBRATE  → BLOCK + out-of-band vibrate only
 *
 *  In every case the WeChat-native call UI / ringtone / wake / vibrate / hangup tone
 *  is still suppressed by PushFilter (SF/VC/AT/MP/VV/VW); fireAlert() only adds the
 *  owner-side vibrate when the policy asks for it.
 */
public final class NotifyRouter {

    private static final String TAG = "NCL";

    // Vibration pattern for message alert: two short pulses
    static final long[] VIB_MSG = {0, 80, 60, 80};

    // Call onset alert (fired ONCE per call on the pending rising edge): a clear
    // double pulse. Not continuous — MIUI truncates long/repeating waveforms and
    // WeChat's own continuous vibration loops to ring-timeout, so we deliberately
    // give one unambiguous "hidden friend is calling" buzz, then stop.
    static final long[] VIB_CALL = {0, 400, 220, 400};

    /**
     * Wall-clock deadline (ms) until which our own vibration is in flight. The
     * PushFilter VV/cancel hooks use this to stop WeChat's call-teardown from
     * cancelling our pulse before it finishes (the call UI is torn down within
     * ~60ms of our vibrate firing). 0 = no vibration in flight.
     */
    public static volatile long sOurVibrationUntilMs = 0;

    /**
     * Set to true while fireAlert() is calling vib.vibrate() so the PushFilter VV hook
     * (which intercepts ALL Vibrator.vibrate() calls in WeChat's process) can skip our
     * own vibration and only block WeChat's VoIP-related vibrations.
     *
     * Thread safety: fireAlert() always runs on the main thread (sMainHandler.post()),
     * and VV hook also fires on whatever thread WeChat calls vibrate() from.
     * The volatile guarantee is sufficient; no need for a lock since we only need
     * to protect one specific call site.
     */
    static volatile boolean sOurVibration = false;

    /**
     * Set to true while fireAlert() is calling mp.start() in SOUND mode so the
     * PushFilter MP hook skips our own MediaPlayer and only blocks WeChat's.
     */
    static volatile boolean sOurSound = false;

    public enum EventType { MSG, CALL, HANGUP }

    /** Routing result returned to PushFilter. */
    public enum Action {
        /** Block entirely — cancel notification, no sound, no vibration. */
        BLOCK,
        /** Pass the notification through after stripping original sound and applying
         *  our vibration pattern (no audible ring). */
        VIBRATE,
        /** Pass through after replacing original sound with the custom ringtone URI
         *  stored in Bridge.KEY_CUSTOM_SOUND (falls back to VIBRATE if unset). */
        SOUND,
        /** Fully pass through — used only for non-hidden friends in SOUND mode. */
        PASS
    }

    private NotifyRouter() {}

    // -------------------------------------------------------------------------
    // eval — map policy + talker → Action
    // -------------------------------------------------------------------------

    /**
     * Determine what to do with a notification.
     *
     * @param talker wxid of the sender (null → treat as non-hidden friend → PASS)
     * @param type   event type
     * @return action to take; BLOCK for OFF, VIBRATE/SOUND for the respective modes
     */
    public static Action eval(String talker, EventType type) {
        if (talker == null) return Action.PASS;
        if (!Bridge.getInstance().shouldHideId(talker)) return Action.PASS;

        // VoIP calls: the notification itself is ALWAYS blocked. The owner-side
        // vibrate (when call policy == VIBRATE) is fired out-of-band via fireAlert(),
        // not by passing a notification through. Calls never ring.
        if (type == EventType.CALL || type == EventType.HANGUP) return Action.BLOCK;

        Bridge.NotifyPolicy policy = Bridge.getInstance().getNotifyPolicy();
        switch (policy) {
            case VIBRATE: return Action.VIBRATE;
            case SOUND:   return Action.SOUND;
            default:      return Action.BLOCK;  // OFF
        }
    }

    // -------------------------------------------------------------------------
    // applyVibrate — strip WeChat sound, arm our vibration
    // -------------------------------------------------------------------------

    /**
     * Modify a Notification in-place: remove WeChat's original sound + vibration,
     * then inject our custom vibration pattern.
     * Call this when action == VIBRATE and the notification is being passed through.
     */
    public static void applyVibrate(Notification n, EventType type) {
        // Strip WeChat's own sound and vibration
        n.sound = null;
        n.defaults &= ~Notification.DEFAULT_SOUND;
        n.defaults &= ~Notification.DEFAULT_VIBRATE;
        n.vibrate = null;

        // Inject our pattern as the notification's vibration.
        // applyVibrate() is only used on the MSG pass-through path; call vibration
        // is fired out-of-band via fireAlert(CALL) + doVibrate(VIB_CALL).
        long[] pattern = VIB_MSG;
        n.defaults |= Notification.DEFAULT_VIBRATE;
        n.vibrate = pattern;
        Log.i(TAG, "[NR] applyVibrate type=" + type);
    }

    /**
     * Fire a message/call alert directly — no notification, no banner, no system popup.
     *
     * Must be called AFTER blocking the notification in L1 (setResult false).
     * This is the core of the "no-banner" guarantee: we never let a notification
     * reach NM.notify(); instead we fire our own Vibrator/sound out-of-band.
     *
     * @param ctx  Application context (Bridge.getInstance().getApp())
     * @param type MSG or CALL
     */
    public static void fireAlert(android.content.Context ctx, EventType type) {
        if (ctx == null) return;

        // VoIP calls use a DEDICATED two-tier policy and NEVER ring.
        if (type == EventType.CALL || type == EventType.HANGUP) {
            Bridge.NotifyPolicy callPolicy = Bridge.getInstance().getCallNotifyPolicy();
            // OFF (default) → silent. Only VIBRATE produces feedback. SOUND is not a
            // call option; if it ever sneaks in we still only vibrate (never ring).
            if (callPolicy == Bridge.NotifyPolicy.OFF) return;
            doVibrate(ctx, VIB_CALL, type, callPolicy);
            return;
        }

        Bridge.NotifyPolicy policy = Bridge.getInstance().getNotifyPolicy();
        if (policy == Bridge.NotifyPolicy.OFF) return;

        // Phase 2: play custom sound when policy == SOUND and URI is configured.
        // For now falls back to vibration (Phase 1).
        if (policy == Bridge.NotifyPolicy.SOUND) {
            String uriStr = Bridge.getInstance().getCustomSound();
            if (uriStr != null && !uriStr.isEmpty()) {
                try {
                    android.media.MediaPlayer mp = new android.media.MediaPlayer();
                    mp.setDataSource(ctx, Uri.parse(uriStr));
                    mp.setAudioStreamType(AudioManager.STREAM_NOTIFICATION);
                    mp.setOnCompletionListener(player -> {
                        sOurSound = false;
                        player.release();
                    });
                    mp.prepare();
                    sOurSound = true;
                    mp.start();
                    Log.i(TAG, "[NR] fireAlert SOUND uri=" + uriStr);
                    return;
                } catch (Throwable t) {
                    Log.w(TAG, "[NR] fireAlert SOUND err, fallback vib: " + t);
                }
            }
            // No custom sound configured → fall through to vibration
        }

        // VIBRATE (or SOUND fallback)
        doVibrate(ctx, VIB_MSG, type, policy);
    }

    /**
     * Fire an alert with an EXPLICIT policy — for the :push process, which does not
     * initialize Bridge (iron rule 30) and therefore cannot use fireAlert()'s
     * Bridge.getNotifyPolicy() read. The :push caller resolves the policy via
     * Bridge.readPolicyCrossProcess(ctx) and passes it here.
     *
     * OFF → nothing. VIBRATE → vibrate. SOUND is treated as VIBRATE in :push v1
     * (custom ringtone is a placeholder "功能更新中" and needs the main-process
     * MediaPlayer path; :push never rings to avoid exposure).
     */
    public static void fireAlertForPolicy(android.content.Context ctx, EventType type,
                                          Bridge.NotifyPolicy policy) {
        if (ctx == null || policy == Bridge.NotifyPolicy.OFF) return;
        long[] pattern = (type == EventType.CALL || type == EventType.HANGUP)
                ? VIB_CALL : VIB_MSG;
        doVibrate(ctx, pattern, type, policy);
    }

    /**
     * Fire our own vibration pattern out-of-band, guarding sOurVibration so the
     * PushFilter VV hook (which intercepts ALL Vibrator.vibrate() in WeChat's
     * process) lets ours through while still blocking WeChat's own VoIP vibration.
     */
    private static void doVibrate(android.content.Context ctx, long[] pattern,
                                  EventType type, Bridge.NotifyPolicy policy) {
        Vibrator vib = (Vibrator) ctx.getSystemService(android.content.Context.VIBRATOR_SERVICE);
        if (vib == null || !vib.hasVibrator()) {
            Log.w(TAG, "[NR] fireAlert no vibrator");
            return;
        }
        long total = 0;
        for (long p : pattern) total += p;
        sOurVibrationUntilMs = System.currentTimeMillis() + total + 200;

        // 消息与来电都用 USAGE_ALARM——这是 Android 唯一不会被 ringer-mode(静音/震动档)
        // 或 doze/勿扰 压掉的 usage，能保证前台 AND 后台/锁屏都可靠震动。
        // 产品口径（2026-05-29）：密友消息震动档 前台后台都要有，不再 best-effort。
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        try {
            sOurVibration = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                VibrationEffect effect = VibrationEffect.createWaveform(pattern, -1);
                vib.vibrate(effect, attrs);
            } else {
                //noinspection deprecation
                vib.vibrate(pattern, -1, attrs);
            }
            Log.i(TAG, "[NR] fireAlert type=" + type + " policy=" + policy
                    + " durMs=" + total + " usage=ALARM");
        } catch (Throwable t) {
            Log.w(TAG, "[NR] fireAlert vib err: " + t);
        } finally {
            sOurVibration = false;
        }
    }

    // -------------------------------------------------------------------------
    // applySound — replace WeChat ringtone with custom URI
    // -------------------------------------------------------------------------

    /**
     * Modify a Notification in-place: replace WeChat's ringtone with the custom
     * sound URI stored in Bridge, strip vibration pattern.
     * Falls back to applyVibrate() if no custom sound is configured.
     */
    public static void applySound(Notification n, EventType type) {
        String uriStr = Bridge.getInstance().getCustomSound();
        if (uriStr == null || uriStr.isEmpty()) {
            // No custom sound configured yet → fall back to vibrate
            applyVibrate(n, type);
            return;
        }
        n.sound = Uri.parse(uriStr);
        n.defaults &= ~Notification.DEFAULT_SOUND;
        n.defaults &= ~Notification.DEFAULT_VIBRATE;
        n.vibrate = null;
        Log.i(TAG, "[NR] applySound type=" + type + " uri=" + uriStr);
    }

    /**
     * True if the notification looks like a VoIP/call event (used to decide EventType).
     * Checks fullScreenIntent, channel name, and notification text.
     */
    public static EventType detectEventType(Notification n) {
        if (n == null) return EventType.MSG;
        if (n.fullScreenIntent != null) return EventType.CALL;
        String ch = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ? n.getChannelId() : "";
        if (ch != null && (ch.contains("voip") || ch.contains("ringtone")
                || ch.contains("call") || ch.contains("reminder"))) {
            return EventType.CALL;
        }
        if (n.extras != null) {
            CharSequence txt = n.extras.getCharSequence(Notification.EXTRA_TEXT, "");
            CharSequence ttl = n.extras.getCharSequence(Notification.EXTRA_TITLE, "");
            String body = (txt != null ? txt.toString() : "") + (ttl != null ? ttl.toString() : "");
            if (body.contains("通话") || body.contains("通話")
                    || body.contains("call") || body.contains("语音")
                    || body.contains("视频")) {
                return EventType.CALL;
            }
        }
        return EventType.MSG;
    }
}
