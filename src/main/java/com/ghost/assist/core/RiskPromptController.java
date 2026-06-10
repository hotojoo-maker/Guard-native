package com.ghost.assist.core;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

/**
 * RiskPromptController — 唯一弹窗出口（引流漏斗的单一策略源）。
 *
 * ═══════════════════════════════════════════════════════════════
 * 设计来源：P1F DESIGN.md §1 横轴 + 安全官 skill「允许多触发点，禁止多份隐藏弹窗逻辑」。
 *
 * 铁律：
 *   • 允许多个触发点（冷启动 / 回前台 / 打开设置页…）都调 maybeShow(reason)，
 *     但**只有这一个方法**决定弹不弹、弹什么 —— 禁止散落第二份弹窗策略。
 *   • 弹窗等级判断只来自 RiskState.currentLevel()，本类不自己算风险。
 *   • 只对「确认篡改超影子期 / 重复篡改」(FUNNEL) 弹引流窗，绝不误伤正版：
 *       CLEAN / OFFLINE_WARN / 影子期(TAMPER_SHADOW) → 不弹（不暴露蜜罐）。
 *   • 实际显示复用现成 PiracyNotice（AlertDialog → SHOP_URL），不用 Service（铁律 24）。
 *   • 点确定后给短冷却，冷却内不重复弹；不清用户数据、不破坏微信本体。
 * ═══════════════════════════════════════════════════════════════
 */
public final class RiskPromptController {

    private static final String TAG = "NCL";

    /** 点确定后的短冷却（秒）。安全官口径 10~60s，取 30s。risk_pack 接入后可由服务端覆盖。 */
    private static final long COOLDOWN_MS = 30_000L;

    // 用单调时钟记冷却，避免墙钟回拨绕过冷却。
    private static volatile long sLastShownElapsed = -1L;

    private RiskPromptController() {}

    /**
     * 唯一弹窗入口。所有触发点（启动/回前台/设置页）都调它。
     *
     * @param ctx    Activity/Application context
     * @param reason 触发来源（仅日志，便于排查；不影响是否弹）
     */
    public static void maybeShow(Context ctx, String reason) {
        if (ctx == null) return;

        RiskState.Level level = RiskState.currentLevel();
        if (!isFunnelLevel(level)) {
            // 正常 / 离线提醒 / 影子期 → 不弹（影子期故意不暴露蜜罐）。
            return;
        }

        // 持续引流型（重复篡改）：每次都弹，但仍走短冷却防连弹。
        // 普通引流型：同样走冷却。
        long now = SystemClock.elapsedRealtime();
        if (sLastShownElapsed >= 0 && (now - sLastShownElapsed) < COOLDOWN_MS) {
            return; // 冷却内，不重复弹
        }
        sLastShownElapsed = now;

        Log.i(TAG, "[risk] funnel prompt level=" + level.label + " reason=" + reason);
        // 复用现成展示层；点确定跳官方渠道引流。文案后续可由 risk_pack 服务端下发。
        PiracyNotice.show(
                ctx,
                "功能异常",
                "检测到运行环境异常，部分功能可能不稳定。\n前往官方渠道获取完整授权版本。",
                "前往官方渠道",
                AppConfig.SHOP_URL);
    }

    private static boolean isFunnelLevel(RiskState.Level level) {
        return level == RiskState.Level.TAMPER_FUNNEL
            || level == RiskState.Level.TAMPER_PERSISTENT_FUNNEL;
    }
}
