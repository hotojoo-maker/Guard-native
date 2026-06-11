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
            Log.w(TAG, "[act] activate rejected (bad card / network)");
            return new Result(false, "activate failed");
        }
        EnvelopeStore.saveToken(token);
        Log.i(TAG, "[act] token acquired, kicking heartbeat");

        // 立刻取一次信封；失败不致命（心跳后续会再拉）
        String certHex = ""; // TODO S3a: 传模块签名 cert SHA-256（与 setBindingMaterial 同源）
        String appVersion = "";
        GuardHeartbeat.syncOnce(deviceId, certHex, appVersion);
        GuardHeartbeat.start(deviceId, certHex, appVersion);
        return new Result(true, "activated");
    }
}
