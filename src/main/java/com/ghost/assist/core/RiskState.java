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
 *   • 主体仍 record-only：评估 + 记录等级 + 驱动「唯一弹窗」，不收紧 isActive()
 *     主链（密友隐藏 ConvFilter/MomentsFilter/ContactFilter 不受本类散沙影响，
 *     GUARD_GATE_TRUTH §4 防误伤正版）。
 *   • ⚠️ 例外（2026-06-12 起，2026-06-26 块B 扩面已落地）：`isTamperDegraded()` 已是真闸——
 *     确认篡改过影子期（TAMPER_FUNNEL/PERSISTENT）后，杂项功能 `active()=isActive()&&!isTamperDegraded()`
 *     单点散沙。当前消费者：CallGuard / PushFilter / AntiRecall / FakeLocation（杂项=来电/通知未读/防撤回/定位）。
 *     故「不关闭任何功能」已不再成立。**密友隐藏四链(Conv/Moments/Contact/Search)不走本轴**——
 *     走 isActive(=授权+crypto config-ready)，重签即 crypto registry 散沙，与 RiskLevel 独立、不连坐（#4 canonical）。
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
        // 离线超宽限(>144h) / license 真过期 → 引流，但【可恢复】：联网验证授权
        // 正常即自动降回 CLEAN（不像篡改链要服务器 risk_reset）。
        OFFLINE_FUNNEL(53, "离线引流"),
        TAMPER_SHADOW(4, "蜜罐影子期"),
        TAMPER_FUNNEL(5, "篡改引流"),
        PROBATION(55, "试用观察"),
        TAMPER_PERSISTENT_FUNNEL(6, "重复篡改引流");

        public final int code;
        public final String label;
        Level(int code, String label) { this.code = code; this.label = label; }
    }

    /**
     * 影子期默认时长（小时）。用户拍板 2026-06-12：盗版包发布后【7 天】才弹，
     * 越长越难让破解者把「改动」和「弹窗」对上因果（改完当场看一切正常，7 天后才发作）。
     * ⚠️ 安全官 skill 默认上限 48h（§"蜜罐影子期引流策略"）；此处 7 天为用户拍板 override，
     *    理由 = 盗版多为转卖、不能让破解者当场发现；属客户端硬编码。
     *    TODO：后续改服务端 risk_pack 下发（默认仍 ≤48h，长影子期由服务器签发）。
     * risk_pack 接入后可由服务端覆盖。
     */
    private static final long SHADOW_HOURS_DEFAULT = 168L;   // 7 天（用户 override；skill 默认 48h）

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
     * 是否应【功能散沙降级】（破解后真功能失效，安全官「功能失效策略」）。
     * 只在【确认篡改 + 过了影子期】(TAMPER_FUNNEL/PERSISTENT) 为 true：
     *   • 影子期内(TAMPER_SHADOW) = false → 破解者当场测一切正常，绊线藏住。
     *   • 离线/过期(OFFLINE_FUNNEL) = false → 不散沙正版客户功能（只弹软提醒）。
     *   • 正版包签名对、诱饵未改 → 永远 CLEAN → 永远 false（不误伤）。
     * 消费者：CallGuard 等敏感功能拿它当「失效闸」。
     */
    public static boolean isTamperDegraded() {
        return sLevel == Level.TAMPER_FUNNEL || sLevel == Level.TAMPER_PERSISTENT_FUNNEL;
    }

    /**
     * 是否到了「该弹引流窗」的等级。只有确认篡改超过影子期、或重复篡改才为 true。
     * RiskPromptController 是唯一消费者（唯一弹窗出口）。
     */
    public static boolean shouldFunnel() {
        return sLevel == Level.TAMPER_FUNNEL
            || sLevel == Level.TAMPER_PERSISTENT_FUNNEL
            || sLevel == Level.OFFLINE_FUNNEL;
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
    /**
     * 无 Context 重算（S3b：心跳成功喂 LeaseClock 后 record-only 刷新等级 + 记日志）。
     * 评估路径不使用 ctx（篡改链走 NativeBridge/Bridge），故可空传，安全。
     */
    public static synchronized Level evaluate() {
        return evaluate((Context) null);
    }

    public static synchronized Level evaluate(Context ctx) {
        Level tamperLevel = evaluateTamper(ctx);
        Level offlineLevel = LeaseClock.currentLevel();

        Level result = maxSeverity(tamperLevel, offlineLevel);
        // license 真过期（购买授权到期，非单纯断网）→ 引流（可恢复：续费/重激活
        // 后 isLicenseExpired=false → 下次 evaluate 自动降回）。不误伤断网正版：
        // 断网但未过期时 isLicenseExpired=false，不会进这条。
        boolean licenseExpired = com.ghost.assist.net.EnvelopeStore.isLicenseExpired();
        if (licenseExpired && severity(result) < severity(Level.OFFLINE_FUNNEL)) {
            result = Level.OFFLINE_FUNNEL;
        }
        // 未授权(观望/白嫖) = 零弹（SPEC §3 #1 / D-018 + 架构师-F17 推翻 SPEC §4 T_soft）：
        //   不抬 OFFLINE_FUNNEL、不软引流、不撤 A2。隐私本就因未授权 isActive=false 放行；
        //   引导付费靠设置页本身（per-row 门控 + "请先完成授权"），不弹窗。
        //   退款另走 isRefunded 撤闸（场景#6，唯一连坐 A2+隐私），不在此软引流。
        sLevel = result;
        Log.i(TAG, "[risk] evaluate level=" + result.label
                + " (tamper=" + tamperLevel.label + " offline=" + offlineLevel.label
                + " licExpired=" + licenseExpired + ")");
        return result;
    }

    /**
     * DEBUG-only：装机验证强制进引流态（release 包 BuildConfig.DEBUG=false → 空操作）。
     * 真检测链（蜜罐绊线 / 离线>144h）属段2/段3；段1 先用它验「funnel→弹窗→跳转」闭环。
     */
    public static synchronized void debugForceFunnel() {
        if (!com.ghost.assist.BuildConfig.DEBUG) return;
        sLevel = Level.TAMPER_FUNNEL;
        Log.i(TAG, "[risk] DEBUG force funnel (debug-only)");
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
     * v1 来源：SO 反篡改 RISK_*（包名/配置/盗版）、AUTH_TAMPERED。
     * 注1：AUTH_NO_LICENSE / MISMATCH **不算**篡改（首装/换号正常用户，不引流）。
     * 注2：kill_switch 已剥离为独立「停用闸」(ModuleMain §5)，不再是引流/篡改信号
     *      —— 停用与引流是两根独立线（P1F C 拍板 2026-06-10，见 PROTECTION_MAP §10.2）。
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
            case OFFLINE_FUNNEL:            return 4;  // 引流但比篡改轻、可联网自恢复
            case PROBATION:                 return 5;
            case TAMPER_SHADOW:             return 6;
            case TAMPER_FUNNEL:             return 7;
            case TAMPER_PERSISTENT_FUNNEL:  return 8;
            default:                        return 0;
        }
    }
}
