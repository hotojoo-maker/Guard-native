package com.ghost.assist.net;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;

import com.ghost.assist.core.AuthManager;
import com.ghost.assist.core.LeaseClock;
import com.ghost.assist.core.NativeBridge;

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
    private static final String K_RN_DAY  = "rd";    // #3 续费预警上次弹出日（可信 epoch day，按天去重）
    private static final String K_CARD_REVOKED = "rf";   // #6 封停/删卡撤销：首见封停/删卡的可信时间戳(ms)，0=未撤（SPEC §4 / SSOT §3）。值 "rf" = 历史缩写的不透明键，保留作 wire/存储兼容，语义=封停/删卡撤销（非退款）

    // 牙④ a案 重放/过期绑定: crypto 种子硬过期宽限 = 配方卡 SPEC A.付费断网宽限 = 7 天(秒)。
    // 硬过期点 = leaseExpire + 本宽限。与隐私 72h 离线宽限(独立闸)不是一回事，别混。
    private static final long Y4_PAID_OFFLINE_GRACE_SEC = 7L * 24L * 3600L;

    private static volatile SharedPreferences sPrefs;
    private static volatile Context sAppCtx;
    private static volatile String sVerifiedBlob = "";
    private static volatile String sVerifiedDevice = "";
    private static volatile AuthEnvelopeVerifier.Envelope sVerifiedEnvelope;

    private EnvelopeStore() {}

    /** 冷启动早期调用一次（与 AppConfig.init 同期）。未 init 时所有读写安全降级。 */
    public static void init(Context ctx) {
        try {
            if (ctx != null && sPrefs == null) {
                sAppCtx = ctx.getApplicationContext();
                sPrefs = sAppCtx.getSharedPreferences(PREFS, 0);
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
        // #6 封停/删卡 rf 位驱动 latch（D-020 可恢复）：
        //   • rf=1 → markCardRevoked() 落持久标志（幂等，只记首见）。
        //   • rf≠1 + license 未过期（= 服务器下发有效授权）→ clearCardRevokedIfAuthorized() 清 latch、两闸恢复。
        // 篡改（D-019/RiskState）不走此路、仍不可逆。
        if (e.cardRevoked == 1) {
            markCardRevoked();
        } else {
            clearCardRevokedIfAuthorized(e);
        }
        resetVerifiedCache();
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
        return isLicenseExpired(getLicenseExpireSec());
    }

    private static boolean isLicenseExpired(long exp) {
        if (exp <= 0) return false;
        long trustedNowSec = LeaseClock.trustedNow() / 1000L;
        return exp <= trustedNowSec;
    }

    public static boolean isAuthorizedNow() {
        if (isCardRevoked()) return false;   // #6 封停/删卡（rf）→ 立刻撤隐私（isVipAuthorized=false → isActive 四层断），与 A2 一起死
        if (!hasToken()) return false;
        AuthEnvelopeVerifier.Envelope e = getVerifiedCachedEnvelope();
        return e != null && !isLicenseExpired(e.licenseExpire);
    }

    // ── #3 续费预警（到期前提醒；隐私侧到期当场失效、无到期后宽限）──

    /** 距授权到期的秒数；<=0 = 已到期，Long.MAX_VALUE = 无到期信息。用可信时间，改墙钟无效。 */
    public static long secondsToLicenseExpiry() {
        long exp = getLicenseExpireSec();
        if (exp <= 0) return Long.MAX_VALUE;
        return exp - LeaseClock.trustedNow() / 1000L;
    }

    /** 当前可信时间的 epoch 天（预警按天去重用；墙钟改不动）。 */
    public static long trustedDay() { return LeaseClock.trustedNow() / 86400000L; }

    public static long getRenewNoticeDay() { return sPrefs == null ? 0 : sPrefs.getLong(K_RN_DAY, 0); }
    public static void setRenewNoticeDay(long day) {
        if (sPrefs != null) sPrefs.edit().putLong(K_RN_DAY, day).apply();
    }

    // ── #6 封停/删卡撤销（SSOT §3 / D-020：连坐 A2+隐私 的非篡改场景；非退款）──
    //
    // 信号源 = 服务器（块A）push 的信封 `rf` 位（暂缓未建，字段先约定）。客户端职责：
    //   • 收到 rf=1 信封 → markCardRevoked() 落持久标志（可信时间戳，幂等只记首见）。
    //   • isCardRevoked()=true → isAuthorizedNow()=false（隐私 fail-closed 立刻撤）+ A2 走
    //     cardRevokedAt+72h 宽限后撤（D-020：两闸独立，A2 fail-open 给 72h 防误封）。
    //   • 收到 rf≠1 + license 未过期的有效信封 → clearCardRevokedIfAuthorized() 清 latch、两闸恢复。
    // D-020 改「可被有效授权恢复」（取代旧「永久不自愈」）：服务器误封后发新有效信封即恢复；本地改时间/
    //   清缓存不触发（须 Ed25519 验签）。与 revokeKeepToken（离线/到期可自愈）区别：封停只认服务器有效信封恢复。
    // 篡改（D-019/RiskState）不走此路、仍不可逆。不清用户数据（密友名单/密码）——安全官红线#5。

    /** 是否已被封停/删卡（rf 持久标志 > 0）。 */
    public static boolean isCardRevoked() {
        return getCardRevokedAt() > 0L;
    }

    /** 首见封停/删卡的可信时间戳(ms)；0=未撤。 */
    public static long getCardRevokedAt() {
        return sPrefs == null ? 0L : sPrefs.getLong(K_CARD_REVOKED, 0L);
    }

    /**
     * 标记封停/删卡（块A server push / 信封 rf 位驱动）。幂等：只记首见时间，重复调不覆盖。
     * 用可信时间（防墙钟改），落盘后立刻使两闸断开。
     */
    public static void markCardRevoked() {
        if (sPrefs == null) return;
        if (getCardRevokedAt() > 0L) return;                 // 幂等：保留首见时间
        sPrefs.edit().putLong(K_CARD_REVOKED, LeaseClock.trustedNow()).apply();
        resetVerifiedCache();
        Log.i(TAG, "[env] card-revoked marked → revoke A2+privacy");
    }

    /**
     * DEBUG-only 封停/删卡撞闸自测（装机 L1 验「撤闸立刻散」用；release BuildConfig.DEBUG=false → 空操作）。
     * on=true 置撤闸、on=false 清撤闸（仅供反复测试复位；release 永不可达）。
     */
    public static void debugSetCardRevoked(boolean on) {
        if (!com.ghost.assist.BuildConfig.DEBUG || sPrefs == null) return;
        if (on) {
            sPrefs.edit().putLong(K_CARD_REVOKED, LeaseClock.trustedNow()).apply();
        } else {
            sPrefs.edit().remove(K_CARD_REVOKED).apply();
        }
        resetVerifiedCache();
        Log.i(TAG, "[env] DEBUG setCardRevoked=" + on + " (debug-only)");
    }

    /**
     * D-020：封停/删卡 latch「可被有效授权恢复」。收到服务器新有效信封（rf≠1 + 验签通过 + license
     * 未过期）→ 清除 cardRevoked 持久标志，两闸（A2 + 隐私）恢复。仅服务器权威可恢复——本地改时间 /
     * 清缓存不触发（e 须经 Ed25519 验签，由 saveEnvelope 调用链保证）。篡改（D-019）不走此路、仍不可逆。
     */
    private static void clearCardRevokedIfAuthorized(AuthEnvelopeVerifier.Envelope e) {
        if (sPrefs == null || e == null) return;
        if (getCardRevokedAt() <= 0L) return;            // 未撤，无需恢复
        if (e.cardRevoked == 1) return;                  // 仍封停，不恢复（防御性，调用点已分流）
        if (isLicenseExpired(e.licenseExpire)) return;   // license 已过期 → 不算有效授权，不恢复
        sPrefs.edit().remove(K_CARD_REVOKED).apply();
        Log.i(TAG, "[env] card-revoked cleared by valid envelope → restore A2+privacy (D-020)");
    }

    /**
     * P0 hardening: local cache is not an auth source. Every authorization check
     * reuses only a cached result for the exact signed blob + device id; any
     * manual tk/bl/le edit must still pass Ed25519 + device/schema sanity.
     */
    public static AuthEnvelopeVerifier.Envelope getVerifiedCachedEnvelope() {
        String blob = getCachedBlob();
        if (blob.isEmpty() || sAppCtx == null) return null;
        String deviceId = AuthManager.computeDeviceHash(sAppCtx);
        AuthEnvelopeVerifier.Envelope cached = sVerifiedEnvelope;
        if (cached != null && blob.equals(sVerifiedBlob) && deviceId.equals(sVerifiedDevice)) {
            return cached;
        }
        AuthEnvelopeVerifier.Envelope e = AuthEnvelopeVerifier.verifyAndParse(blob, deviceId);
        if (e == null) {
            resetVerifiedCache();
            return null;
        }
        sVerifiedBlob = blob;
        sVerifiedDevice = deviceId;
        sVerifiedEnvelope = e;
        return e;
    }

    /** Apply a verified cached envelope's server seed before Filter hooks install. */
    public static boolean applyCachedEnvelopeSeed() {
        AuthEnvelopeVerifier.Envelope e = getVerifiedCachedEnvelope();
        if (e == null || isLicenseExpired(e.licenseExpire)) return false;
        pushSeedExpiryToNative(e);   // 牙④: 先下推硬过期点，过期信封 unwrap 即散沙
        return NativeBridge.applyServerSeedAndReset(e.keyMaterial, e.keyNonce);
    }

    /**
     * 牙④ a案: 把「硬过期点 + 可信时间」下推 SO，供 unwrap_server_seed 拒旧/过期信封。
     * hard_expire = leaseExpire + 7天断网宽限 (配方卡 SPEC A.付费断网宽限)；leaseExpire<=0
     * (无租约信息) → 推 0 = 关闭检查 (不误伤正版)。trusted_now 吊 LeaseClock 官方授时
     * floor 防冻结。不折静态 registry key (续约不自锁)。
     */
    public static void pushSeedExpiryToNative(AuthEnvelopeVerifier.Envelope e) {
        if (e == null) return;
        long hardExpire = e.leaseExpire > 0 ? e.leaseExpire + Y4_PAID_OFFLINE_GRACE_SEC : 0L;
        long trustedNowSec = LeaseClock.trustedNow() / 1000L;
        NativeBridge.setEnvelopeExpiry(hardExpire, trustedNowSec);
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
        resetVerifiedCache();
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
        resetVerifiedCache();
    }

    private static void resetVerifiedCache() {
        sVerifiedBlob = "";
        sVerifiedDevice = "";
        sVerifiedEnvelope = null;
    }
}
