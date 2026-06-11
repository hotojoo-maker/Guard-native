package com.ghost.assist.net;

import android.content.Context;
import android.util.Log;

import com.ghost.assist.core.AppConfig;
import com.ghost.assist.core.AuthManager;

/**
 * GuardActivation — S2 激活编排（卡密 → token → 起心跳）。
 *
 * 把「激活」这件事收到一处，DebugServer / 将来 SettingsEntry 都只调这里，
 * 不各自拼网络逻辑（对齐"少数关键出口"原则）。
 *
 * 边界：
 *   • 只做「卡密→token→缓存→起心跳」，不碰 StateMachine / 不开任何功能。
 *   • token 有没有 ≠ 功能能不能用：真锁链还要 envelope→k→registry 解开（S3a）。
 *   • 设备号统一走 AuthManager.computeDeviceHash（与授权评估同一处，避免漂移）。
 */
public final class GuardActivation {

    private static final String TAG = "NCL";

    private GuardActivation() {}

    /** 激活结果（给 UI/调试入口回显，不含敏感原文）。 */
    public static final class Result {
        public final boolean ok;
        public final String message;
        Result(boolean ok, String message) { this.ok = ok; this.message = message; }
    }

    /**
     * 用卡密激活：拿 token → 缓存 → 立刻取一次信封 → 起低频心跳。
     * 阻塞（含网络），调用方放后台线程。
     */
    public static Result activate(String cardKey) {
        if (cardKey == null || cardKey.trim().isEmpty()) {
            return new Result(false, "empty card key");
        }
        Context ctx = AppConfig.getInstance().getAppContext();
        if (ctx == null) {
            return new Result(false, "no app context");
        }
        EnvelopeStore.init(ctx);

        String deviceId = AuthManager.computeDeviceHash(ctx);
        String token = EnvelopeClient.activate(cardKey.trim(), deviceId);
        if (token == null || token.isEmpty()) {
            String msg = authErrorText(EnvelopeClient.getLastErrorCode());
            EnvelopeStore.saveAuthError(msg);
            Log.w(TAG, "[act] activate rejected (bad card / network)");
            return new Result(false, msg);
        }
        EnvelopeStore.saveToken(token);
        Log.i(TAG, "[act] token acquired, kicking heartbeat");

        // 立刻取一次信封；失败不致命（心跳后续会再拉）
        String certHex = ""; // TODO S3a: 传模块签名 cert SHA-256（与 setBindingMaterial 同源）
        String appVersion = AppConfig.GUARD_PRODUCT_VERSION;
        int tier = GuardHeartbeat.syncOnce(deviceId, certHex, appVersion);
        if (tier < 0) {
            EnvelopeStore.clear();
            EnvelopeStore.saveAuthError("授权异常，请联系售后");
            Log.w(TAG, "[act] envelope sync failed after token");
            return new Result(false, "envelope failed");
        }
        GuardHeartbeat.start(deviceId, certHex, appVersion);
        return new Result(true, "activated");
    }

    private static String authErrorText(String code) {
        if ("CARD_EXPIRED".equals(code)) return "授权已到期，请联系售后";
        if ("CARD_BANNED".equals(code) || "CARD_DISABLED".equals(code)) return "授权码已停用，请联系售后";
        if ("DEVICE_BANNED".equals(code)) return "设备已封停，请联系售后";
        if ("DEVICE_LIMIT".equals(code)) return "设备数量已达上限，请联系售后";
        if ("TOKEN_INVALID".equals(code)) return "授权已失效，请重新激活";
        return "授权异常，请联系售后";
    }
}
