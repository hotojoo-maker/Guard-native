package com.ghost.assist.core;

import android.os.SystemClock;
import android.util.Log;

/**
 * LeaseClock — 服务器授时 + 单调时钟外推 + 历史水位（RiskGate 的时间维度）。
 *
 * ═══════════════════════════════════════════════════════════════
 * 设计来源：P1F DESIGN.md §4 + 安全官 skill「LeaseClock」节。
 *
 * 为什么不直接用 System.currentTimeMillis()：手机墙钟可被用户回拨/前跳，
 * 不能用它判死刑。可信时间 = 上次服务器时间 + 单调时钟流逝量：
 *   trusted_now = last_server_now + (elapsedRealtime_now - last_elapsedRealtime)
 *
 * v1 现状（诚实口径）：
 *   • 服务器心跳端点 = Phase 1D-server，**未接**。本类先用「本地缓存的
 *     last_server_now」+ 单调时钟跑骨架；从未成功心跳过 → 默认 CLEAN，
 *     不凭空降级（不误伤）。
 *   • 时间可获取官方服务器（用户已确认方向）：接口就绪后调 onServerHeartbeat()
 *     写入 last_server_now，本类逻辑不变。
 *   • 只输出 RiskState.Level 的「离线/时间」分支，绝不自己弹窗、不关功能。
 *
 * 边界：本类只读/写自己的时间水位，不碰 StateMachine / AuthGate / Filter。
 * ═══════════════════════════════════════════════════════════════
 */
public final class LeaseClock {

    private static final String TAG = "NCL";

    // 持久化键（Bridge MMKV g_<seed>，短名）。
    private static final String KEY_LAST_SERVER_NOW = "lsn";   // 上次成功服务器时间(ms)
    private static final String KEY_LAST_ELAPSED    = "lel";   // 对应的 elapsedRealtime(ms)
    private static final String KEY_MAX_TRUSTED     = "mtn";   // 历史最大可信时间水位(ms)
    private static final String KEY_EXPIRE_AT       = "lea";   // 租约过期(ms)，0=无租约
    private static final String KEY_LAST_HEARTBEAT  = "lhb";   // 上次心跳的可信时间(ms)
    private static final String KEY_OFFICIAL_BASE   = "ofb";   // A2 时间闸：首次拿到官方对时(hd.b)有效值的水位(ms)，0=未拿到；首装未授权 72h 倒计时起算基准（D-020）

    // 断网阶梯（小时）。对齐 NativeBridge.GRACE_* 与安全官口径 24h/72h。
    private static final long WARN_MS    = NativeBridge.GRACE_WARN_HOURS    * 3600_000L; // 24h
    private static final long DEGRADE_MS = NativeBridge.GRACE_DEGRADE_HOURS * 3600_000L; // 72h
    // 引流阶梯：断网超 72h×2=144h（6天）→ OFFLINE_FUNNEL 引流（可恢复：联网即降回）。
    // 用户拍板 2026-06-12。数字段1走明文常量（轻迷彩），以后随 risk_pack 服务端下发。
    private static final long FUNNEL_MS  = DEGRADE_MS * 2;                                // 144h

    // 官方对时(hd.b)未来上限兜底（架构师补丁 2026-06-28 · D-020）：比 hd.b 原值——若 > 现有可信 + 2 年
    // = 时间被 hook 的篡改铁证（官方服务器时间不可能差 2 年）→ 丢值 + markTampered（D-019 不可逆）。
    // 正版触发不了（hd.b 抗改表）。TODO：2 年可随最长授权期 / 服务器下发调；勿写进配方卡（另一路在改）。
    private static final long FUTURE_CAP_MS = 2L * 365L * 24L * 3600_000L;                 // 2 年

    private LeaseClock() {}

    /**
     * 服务器心跳成功时调用（Phase 1D-server 接入后由 envelope 验签通过后驱动）。
     * 写入服务器时间 + 当前单调时钟，并抬高历史水位。
     *
     * @param serverNowMs 服务器下发的当前时间（已验签）
     * @param expireAtMs  租约过期时间（已验签）；<=0 表示不带租约
     */
    public static synchronized void onServerHeartbeat(long serverNowMs, long expireAtMs) {
        long elapsed = SystemClock.elapsedRealtime();
        Bridge b = Bridge.getInstance();
        b.putLong(KEY_LAST_SERVER_NOW, serverNowMs);
        b.putLong(KEY_LAST_ELAPSED, elapsed);
        b.putLong(KEY_LAST_HEARTBEAT, serverNowMs);
        if (expireAtMs > 0) b.putLong(KEY_EXPIRE_AT, expireAtMs);
        long maxTrusted = b.getLong(KEY_MAX_TRUSTED, 0L);
        if (serverNowMs > maxTrusted) b.putLong(KEY_MAX_TRUSTED, serverNowMs);
        Log.i(TAG, "[lease] heartbeat server_now=" + serverNowMs + " expire=" + expireAtMs);
    }

    /**
     * 记录官方对时第二独立源（hd.b()，MicroMsg.TimeHelper · 抗改表 · L1 2026-06-27）。
     * 与服务器授时共用 max_trusted_now 水位「只抬不降」（防回拨 / 反复重启冻结 trustedNow）。
     * 官方授时即通过抬高 max_trusted_now 纳入 trustedNow()（故 trustedNow 逻辑不变、不破坏现有消费者）。
     *
     * 未来上限兜底（架构师补丁 · D-020）：比 hd.b 原值（非我方外推中间值）——
     *   • officialMs ≤ 0（全新装 / 未登录 / 无锚 / 读取异常）→ 忽略（fail-open，不计时）。
     *   • 有可信基准且 officialMs > 现有可信 + 2 年 → 丢值，返回 true（调用方 markTampered，D-019 不可逆）。
     *   • 否则采信：抬 max_trusted_now 水位 + 首次有效值记 officialBase（首装 72h 倒计时起算）。
     *
     * @param officialMs hd.b() 原值（epoch ms），由 OfficialClock 读出
     * @return true = 检测到异常未来值（调用方应 markTampered）；false = 正常 / 忽略
     */
    public static synchronized boolean noteOfficialTime(long officialMs) {
        if (officialMs <= 0L) return false;                       // 无值 → fail-open，忽略
        Bridge b = Bridge.getInstance();
        long maxTrusted = b.getLong(KEY_MAX_TRUSTED, 0L);
        long current = maxTrusted;                                // 现有可信（不含墙钟、不含本次官方值）
        long lastServer = b.getLong(KEY_LAST_SERVER_NOW, 0L);
        if (lastServer > 0L) {
            long delta = SystemClock.elapsedRealtime() - b.getLong(KEY_LAST_ELAPSED, 0L);
            if (delta >= 0L) current = Math.max(current, lastServer + delta);
        }
        // 仅在有可信基准时判未来上限；current<=0（全新装无基准）→ 采信首值（hd.b 抗改表，可信）。
        if (current > 0L && officialMs > current + FUTURE_CAP_MS) {
            Log.w(TAG, "[lease] official time abnormal future (>cap) → discard + tamper");
            return true;
        }
        if (officialMs > maxTrusted) b.putLong(KEY_MAX_TRUSTED, officialMs);   // 只抬不降
        if (b.getLong(KEY_OFFICIAL_BASE, 0L) <= 0L) {
            b.putLong(KEY_OFFICIAL_BASE, officialMs);                          // 首次有效值 = 72h 起算锚
            Log.i(TAG, "[lease] official base set (72h countdown anchor)");
        }
        return false;
    }

    /** A2 时间闸：首次官方对时有效值水位(ms)，0=尚未拿到（首装未授权 72h 从此起算；无值 fail-open 不计时）。 */
    public static synchronized long getOfficialBaseMs() {
        return Bridge.getInstance().getLong(KEY_OFFICIAL_BASE, 0L);
    }

    /**
     * 离线可信时间。从未心跳过 → 返回历史水位或墙钟（不低于水位），
     * 但绝不用它去延长授权（见 currentLevel 逻辑）。
     */
    public static synchronized long trustedNow() {
        Bridge b = Bridge.getInstance();
        long lastServer = b.getLong(KEY_LAST_SERVER_NOW, 0L);
        long maxTrusted = b.getLong(KEY_MAX_TRUSTED, 0L);

        if (lastServer == 0L) {
            // 没成功心跳过：用墙钟，但不低于历史水位（防回拨）。
            long wall = System.currentTimeMillis();
            return Math.max(wall, maxTrusted);
        }
        long lastElapsed = b.getLong(KEY_LAST_ELAPSED, 0L);
        long elapsedNow = SystemClock.elapsedRealtime();
        long delta = elapsedNow - lastElapsed;
        if (delta < 0) {
            // 单调时钟回绕 = 设备重启过；不外推，退回历史水位。
            return Math.max(lastServer, maxTrusted);
        }
        long extrapolated = lastServer + delta;
        return Math.max(extrapolated, maxTrusted);
    }

    /** 是否检测到重启/计时异常（elapsedRealtime 回绕）。 */
    public static synchronized boolean isClockSuspicious() {
        Bridge b = Bridge.getInstance();
        long lastElapsed = b.getLong(KEY_LAST_ELAPSED, 0L);
        if (lastElapsed == 0L) return false; // 还没基准，不判异常
        return SystemClock.elapsedRealtime() < lastElapsed;
    }

    /**
     * 距上次心跳的离线时长（毫秒）。从未心跳过 → 返回 -1（未知，不判离线）。
     */
    public static synchronized long offlineMillis() {
        Bridge b = Bridge.getInstance();
        long lastHeartbeat = b.getLong(KEY_LAST_HEARTBEAT, 0L);
        if (lastHeartbeat == 0L) return -1L;
        long diff = trustedNow() - lastHeartbeat;
        return diff < 0 ? 0L : diff;
    }

    /** 租约是否已过期（带租约且 trustedNow 超过 expire_at）。 */
    public static synchronized boolean isLeaseExpired() {
        long expire = Bridge.getInstance().getLong(KEY_EXPIRE_AT, 0L);
        if (expire <= 0L) return false; // 无租约（v1 未接服务器）→ 不判过期
        return trustedNow() > expire;
    }

    /**
     * 输出时间/断网维度的风险等级（供 RiskState 取并集）。
     *
     * v1（未接服务器）：lastHeartbeat==0 → 没有任何服务器基准 → CLEAN，
     *   绝不凭空降级（这是「不因单纯断网误杀」红线）。
     * 接服务器后：
     *   - 租约过期 → DEGRADED
     *   - 断网 >72h → DEGRADED
     *   - 断网 >24h 且时钟异常 → TIME_SUSPICIOUS
     *   - 断网 >24h → OFFLINE_WARN
     *   - 时钟异常（重启回绕）→ TIME_SUSPICIOUS
     */
    public static synchronized RiskState.Level currentLevel() {
        long offline = offlineMillis();
        boolean suspicious = isClockSuspicious();

        if (offline < 0L) {
            // 从未心跳：仅在时钟明显回绕时标 TIME_SUSPICIOUS，否则 CLEAN。
            return suspicious ? RiskState.Level.TIME_SUSPICIOUS : RiskState.Level.CLEAN;
        }
        // 断网超 144h（6天）→ 引流（可恢复：联网心跳成功 → offline 归零 → 降回 CLEAN）。
        if (offline > FUNNEL_MS) return RiskState.Level.OFFLINE_FUNNEL;
        if (isLeaseExpired()) return RiskState.Level.DEGRADED;
        if (offline > DEGRADE_MS) return RiskState.Level.DEGRADED;
        if (offline > WARN_MS && suspicious) return RiskState.Level.TIME_SUSPICIOUS;
        if (offline > WARN_MS) return RiskState.Level.OFFLINE_WARN;
        if (suspicious) return RiskState.Level.TIME_SUSPICIOUS;
        return RiskState.Level.CLEAN;
    }
}
