package com.ghost.assist.core;

import android.content.Context;
import android.util.Log;

/**
 * RiskState — 唯一风险等级机（RiskGate 的 Java 出口）。
 *
 * ═══════════════════════════════════════════════════════════════
 * 设计来源：P1F 十字防护整合（DESIGN.md §3 / §5）+ 安全官 skill RiskLevel 表。
 *
 * 边界铁律（授权检查官会签 WARN 约束①②）：
 *   • RiskGate 是【第四道独立门】，与 StateMachine.isActive() 的三层结构
 *     （授权 + 密友总开关 + HIDDEN 态）**并联**，绝不并进 isActive()。
 *   • v1 = RECORD ONLY：本类只评估并记录等级、只驱动「唯一弹窗」，
 *     **不静默关闭任何功能**（GUARD_GATE_TRUTH §4：v1 放行不收紧，防误伤正版）。
 *     真正的「散沙降级」落点在服务器真锁阶段（Phase 1D-server）接入。
 *   • 全项目只准本类写风险等级；业务层只读 currentLevel()。
 * ═══════════════════════════════════════════════════════════════
 */
public final class RiskState {

    private static final String TAG = "NCL";

    /** RiskLevel — 唯一等级表（不允许各模块散写 if）。code 对外稳定。 */
    public enum Level {
        CLEAN(0, "正常"),
        OFFLINE_WARN(1, "离线提醒"),
        TIME_SUSPICIOUS(2, "时间异常"),
        DEGRADED(3, "降级"),
        TAMPER_SHADOW(4, "蜜罐影子期"),
        TAMPER_FUNNEL(5, "篡改引流"),
        PROBATION(55, "试用观察"),
        TAMPER_PERSISTENT_FUNNEL(6, "重复篡改引流");

        public final int code;
        public final String label;
        Level(int code, String label) { this.code = code; this.label = label; }
    }

    /** 影子期默认时长（小时）。risk_pack 接入后由服务端覆盖；默认不超过 48h。 */
    private static final long SHADOW_HOURS_DEFAULT = 24L;

    // 蜜罐首次命中时间（可信时间，毫秒）。0 = 未命中。持久化以跨进程/重启保留。
    private static final String KEY_TAMPER_FIRST_SEEN = "rtfs";

    private static volatile Level sLevel = Level.CLEAN;

    private RiskState() {}

    /** 业务/UI 只读出口。 */
    public static Level currentLevel() { return sLevel; }

    public static boolean isTamper() {
        return sLevel == Level.TAMPER_SHADOW
            || sLevel == Level.TAMPER_FUNNEL
            || sLevel == Level.TAMPER_PERSISTENT_FUNNEL;
    }

    /**
     * 是否到了「该弹引流窗」的等级。只有确认篡改超过影子期、或重复篡改才为 true。
     * RiskPromptController 是唯一消费者（唯一弹窗出口）。
     */
    public static boolean shouldFunnel() {
        return sLevel == Level.TAMPER_FUNNEL || sLevel == Level.TAMPER_PERSISTENT_FUNNEL;
    }

    /**
     * 唯一评估入口。冷启动 auth 评估后、以及 LeaseClock 心跳后调用。
     * 输入信号：
     *   1. 篡改信号（confirmed tamper / 蜜罐命中 / 本地 kill_switch）→ 影子期 → 引流
     *   2. LeaseClock 时间/断网等级（离线/时间异常/降级）
     * 取「更危险者」为最终等级（篡改链优先级高于离线链）。
     *
     * v1 record-only：只算 + 记 + 决定弹不弹，不动 isActive、不关功能。
     */
    public static synchronized Level evaluate(Context ctx) {
        Level tamperLevel = evaluateTamper(ctx);
        Level offlineLevel = LeaseClock.currentLevel();

        Level result = maxSeverity(tamperLevel, offlineLevel);
        sLevel = result;
        Log.i(TAG, "[risk] evaluate level=" + result.label
                + " (tamper=" + tamperLevel.label + " offline=" + offlineLevel.label + ")");
        return result;
    }

    /**
     * 蜜罐绊线入口：当检测到诱饵被改 / 包被篡改时调用，置篡改首见时间。
     * 不立即升级到引流——影子期内仍表面可用（不暴露蜜罐）。
     */
    public static synchronized void markTampered(Context ctx) {
        long now = LeaseClock.trustedNow();
        long first = Bridge.getInstance().getLong(KEY_TAMPER_FIRST_SEEN, 0L);
        if (first == 0L) {
            Bridge.getInstance().putLong(KEY_TAMPER_FIRST_SEEN, now);
            Log.i(TAG, "[risk] tamper first seen → shadow period start");
        }
        evaluate(ctx);
    }

    /**
     * 转正/解封：仅在服务器签名强校验通过后调用（Phase 1D-server）。
     * 本地按钮 / 清缓存 / 改时间 / 删文件**不得**调用此方法自洗白。
     */
    public static synchronized void serverRiskReset(Context ctx) {
        Bridge.getInstance().putLong(KEY_TAMPER_FIRST_SEEN, 0L);
        Log.i(TAG, "[risk] server risk_reset accepted");
        evaluate(ctx);
    }

    // ── 内部 ─────────────────────────────────────────────────

    /** 评估篡改链：confirmed tamper → 影子期/引流；否则 CLEAN。 */
    private static Level evaluateTamper(Context ctx) {
        boolean confirmedTamper = isConfirmedTamper();
        long first = Bridge.getInstance().getLong(KEY_TAMPER_FIRST_SEEN, 0L);

        // 本轮命中篡改但还没记首见时间 → 记下，进影子期。
        if (confirmedTamper && first == 0L) {
            long now = LeaseClock.trustedNow();
            Bridge.getInstance().putLong(KEY_TAMPER_FIRST_SEEN, now);
            first = now;
        }
        if (first == 0L) return Level.CLEAN;

        long elapsedMs = LeaseClock.trustedNow() - first;
        long shadowMs = SHADOW_HOURS_DEFAULT * 3600_000L;
        // 影子期内：表面可用、不暴露；超期：引流。
        return (elapsedMs >= 0 && elapsedMs < shadowMs)
                ? Level.TAMPER_SHADOW
                : Level.TAMPER_FUNNEL;
    }

    /**
     * 确认篡改信号（不含「断网/时间」这种可恢复的良性异常）。
     * v1 来源：SO 反篡改 RISK_*（包名/配置/盗版）、AUTH_TAMPERED、本地 kill_switch。
     * 注：AUTH_NO_LICENSE / MISMATCH **不算**篡改（首装/换号正常用户，不引流）。
     */
    private static boolean isConfirmedTamper() {
        try {
            int risk = NativeBridge.getRiskState();
            if (risk == NativeBridge.RISK_PACKAGE_MISMATCH
                    || risk == NativeBridge.RISK_CONFIG_TAMPERED
                    || risk == NativeBridge.RISK_PIRATE) {
                return true;
            }
            if (NativeBridge.getAuthState() == NativeBridge.AUTH_TAMPERED) {
                return true;
            }
            // 本地 kill_switch（v1 stub=false）：作为「我随时停用」走通引流链的开关。
            if (AppConfig.getInstance().isKillSwitch()) {
                return true;
            }
        } catch (Throwable t) {
            Log.w(TAG, "[risk] tamper probe err: " + t);
        }
        return false;
    }

    /** 取两条链里更危险的等级（篡改链整体高于离线链）。 */
    private static Level maxSeverity(Level a, Level b) {
        return severity(a) >= severity(b) ? a : b;
    }

    private static int severity(Level l) {
        switch (l) {
            case CLEAN:                     return 0;
            case OFFLINE_WARN:              return 1;
            case TIME_SUSPICIOUS:           return 2;
            case DEGRADED:                  return 3;
            case PROBATION:                 return 4;
            case TAMPER_SHADOW:             return 5;
            case TAMPER_FUNNEL:             return 6;
            case TAMPER_PERSISTENT_FUNNEL:  return 7;
            default:                        return 0;
        }
    }
}
