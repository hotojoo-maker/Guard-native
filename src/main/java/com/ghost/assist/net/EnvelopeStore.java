package com.ghost.assist.net;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;

import com.ghost.assist.core.LeaseClock;

/**
 * EnvelopeStore — S2 信封 / token 本地缓存（离线冷启动复用）。
 *
 * 设计要点：
 *   • 独立 SharedPreferences 命名空间（不写 Bridge，避免动核心保险区）。
 *   • 缓存「签名信封原文 blob + token + 租约元数据」，供冷启动在租约内离线复用。
 *   • 同时记 wall 时钟 + elapsedRealtime（SystemClock），给 S3b LeaseClock 做
 *     防回拨的离线授时外推（trusted_now = last_server_now + Δelapsed）。
 *   • 本类只存取，不做门控、不解密、不判隐藏。
 *
 * 注意：缓存 blob 内含短命 key 材料 k。这是「离线宽限」必要代价——
 * 真锁威胁模型本就假设设备可被 dump，防线是「短命(≤6h)+服务器材料+设备绑定」，
 * 不是指望本地不落盘。租约过期后 blob 失效（S3b LeaseClock 判）。
 */
public final class EnvelopeStore {

    private static final String TAG = "NCL";
    private static final String PREFS = "ncl_env";   // seed 化命名空间

    private static final String K_TOKEN   = "tk";    // activate token
    private static final String K_BLOB    = "bl";    // 签名信封原文
    private static final String K_SYNC_W  = "sw";    // 上次同步 wall 时钟 (ms)
    private static final String K_SYNC_E  = "se";    // 上次同步 elapsedRealtime (ms)
    private static final String K_SRV_NOW = "sn";    // 信封内服务器时间 (s)
    private static final String K_LEASE   = "ex";    // 租约到期 (s)
    private static final String K_LICENSE = "le";    // 授权到期 (s)
    private static final String K_TIER    = "tr";    // 风险层 q
    private static final String K_RISK    = "rk";    // 风险分 r
    private static final String K_ERR     = "er";    // 最近授权异常
    private static final String K_PVER    = "pv";    // 量子密友版本
    private static final String K_UP_M    = "um";    // 更新通知模式
    private static final String K_UP_T    = "ut";    // 更新标题
    private static final String K_UP_D    = "ud";    // 更新文案
    private static final String K_UP_U    = "uu";    // 更新链接

    private static volatile SharedPreferences sPrefs;

    private EnvelopeStore() {}

    /** 冷启动早期调用一次（与 AppConfig.init 同期）。未 init 时所有读写安全降级。 */
    public static void init(Context ctx) {
        try {
            if (ctx != null && sPrefs == null) {
                sPrefs = ctx.getApplicationContext().getSharedPreferences(PREFS, 0);
            }
        } catch (Throwable t) {
            Log.w(TAG, "[env] store init err: " + t.getClass().getSimpleName());
        }
    }

    // ── token ─────────────────────────────────────────────────

    public static void saveToken(String token) {
        if (sPrefs == null || token == null) return;
        sPrefs.edit().putString(K_TOKEN, token).apply();
    }

    public static String getToken() {
        return sPrefs == null ? "" : sPrefs.getString(K_TOKEN, "");
    }

    public static boolean hasToken() {
        return !getToken().isEmpty();
    }

    // ── 信封 ──────────────────────────────────────────────────

    /** 存一份验证通过的信封 + 同步时刻（wall + elapsed 双记，给离线授时用）。 */
    public static void saveEnvelope(AuthEnvelopeVerifier.Envelope e) {
        if (sPrefs == null || e == null) return;
        sPrefs.edit()
                .putString(K_BLOB, e.rawSignedBlob == null ? "" : e.rawSignedBlob)
                .putLong(K_SYNC_W, System.currentTimeMillis())
                .putLong(K_SYNC_E, SystemClock.elapsedRealtime())
                .putLong(K_SRV_NOW, e.serverNow)
                .putLong(K_LEASE, e.leaseExpire)
                .putLong(K_LICENSE, e.licenseExpire)
                .putInt(K_TIER, e.tier)
                .putInt(K_RISK, e.risk)
                .putString(K_PVER, e.productVersion == null ? "" : e.productVersion)
                .putInt(K_UP_M, e.updateMode)
                .putString(K_UP_T, e.updateTitle == null ? "" : e.updateTitle)
                .putString(K_UP_D, e.updateMessage == null ? "" : e.updateMessage)
                .putString(K_UP_U, e.updateUrl == null ? "" : e.updateUrl)
                .remove(K_ERR)
                .apply();
    }

    public static String getCachedBlob() {
        return sPrefs == null ? "" : sPrefs.getString(K_BLOB, "");
    }

    public static boolean hasCachedEnvelope() {
        return !getCachedBlob().isEmpty();
    }

    // ── 租约元数据（S3b LeaseClock 会接管严格判定，这里只给粗略读取）──

    public static long getLeaseExpireSec() { return sPrefs == null ? 0 : sPrefs.getLong(K_LEASE, 0); }
    public static long getLicenseExpireSec() { return sPrefs == null ? 0 : sPrefs.getLong(K_LICENSE, 0); }
    public static long getServerNowSec()   { return sPrefs == null ? 0 : sPrefs.getLong(K_SRV_NOW, 0); }
    public static long getSyncWallMs()     { return sPrefs == null ? 0 : sPrefs.getLong(K_SYNC_W, 0); }
    public static long getSyncElapsedMs()  { return sPrefs == null ? 0 : sPrefs.getLong(K_SYNC_E, 0); }
    public static int  getTier()           { return sPrefs == null ? 1 : sPrefs.getInt(K_TIER, 1); }
    public static int  getRisk()           { return sPrefs == null ? 0 : sPrefs.getInt(K_RISK, 0); }

    /**
     * 授权是否真到期。S3b-B：用 `LeaseClock.trustedNow()`（服务器授时 + 单调时钟外推 +
     * 历史水位防回拨）而非手机墙钟——手机前跳不会误杀、回拨不能续命。
     * 注：trustedNow 在「从未心跳」时回落墙钟，但本判定的前置 hasCachedEnvelope 已保证
     * 至少成功心跳过一次（onServerHeartbeat 已喂服务器时间），所以真正 gating 时是服务器基准。
     * 断网不在此处掉授权（只看真到期）；离线宽限/提醒由 LeaseClock 等级承担（record-only）。
     */
    public static boolean isLicenseExpired() {
        long exp = getLicenseExpireSec();
        if (exp <= 0) return false;
        long trustedNowSec = LeaseClock.trustedNow() / 1000L;
        return exp <= trustedNowSec;
    }

    public static boolean isAuthorizedNow() {
        return hasToken() && hasCachedEnvelope() && !isLicenseExpired();
    }

    public static void saveAuthError(String message) {
        if (sPrefs == null) return;
        sPrefs.edit().putString(K_ERR, message == null ? "" : message).apply();
    }

    public static String getAuthError() {
        return sPrefs == null ? "" : sPrefs.getString(K_ERR, "");
    }

    public static String getProductVersion(String fallback) {
        String v = sPrefs == null ? "" : sPrefs.getString(K_PVER, "");
        return v == null || v.isEmpty() ? fallback : v;
    }

    public static int getUpdateMode() { return sPrefs == null ? -1 : sPrefs.getInt(K_UP_M, -1); }
    public static String getUpdateTitle() { return sPrefs == null ? "" : sPrefs.getString(K_UP_T, ""); }
    public static String getUpdateMessage() { return sPrefs == null ? "" : sPrefs.getString(K_UP_D, ""); }
    public static String getUpdateUrl() { return sPrefs == null ? "" : sPrefs.getString(K_UP_U, ""); }

    /** 清缓存（解绑 / 卡密失效时）。不清用户密友名单——那不归这里。 */
    public static void clear() {
        if (sPrefs == null) return;
        sPrefs.edit().clear().apply();
    }

    /**
     * S3b-B：撤销本地授权但**保留 token**（重连自愈）。清信封 blob + 租约/到期元数据，
     * 使 `isAuthorizedNow()` 立刻为 false；保留 token 让下次联网用同一 token 自动重拉
     * envelope 恢复，不必重新输卡密。不清用户密友名单/密码（安全官红线）。
     */
    public static void revokeKeepToken() {
        if (sPrefs == null) return;
        sPrefs.edit()
                .remove(K_BLOB)
                .remove(K_LEASE)
                .remove(K_LICENSE)
                .remove(K_SRV_NOW)
                .apply();
    }
}
