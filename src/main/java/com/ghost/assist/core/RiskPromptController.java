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

    /** 点确定后的短冷却（秒）。安全官口径 10~60s；用户要求「弹得更勤」取下限 10s。
     *  risk_pack 接入后可由服务端覆盖。 */
    private static final long COOLDOWN_MS = 10_000L;

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
        // 段1：用新展示层 FunnelPrompt（旧 PiracyNotice 不动）。引流 URL 从 SO 加密
        // 引导段解出（不在 Java 明文常量里）；SO 散沙/重打包 → 空 → 回退 SHOP_URL。
        String url = NativeBridge.getEndpoint("funnel");
        if (url == null || url.isEmpty()) url = AppConfig.SHOP_URL;

        // 文案按等级分（不误伤付费客户红线）：
        //   • 确认篡改/盗版 → 吓人文案（盗版风险、可能封号），逼转正。
        //   • 离线超宽限 / license 过期 → 软文案（不指控盗版，付费客户断网也可能撞上）。
        String title;
        String message;
        if (isTamperLevel(level)) {
            title = "安全风险提示";
            message = "检测到当前为非官方破解版本，存在安全风险，"
                    + "可能导致微信账号被封禁或资料泄露。\n"
                    + "请尽快前往官方渠道获取安全授权版本。";
        } else {
            title = "版本提示";
            message = "当前授权需要重新验证，部分功能可能受限。\n"
                    + "请前往官方渠道获取最新版本，或复制下方链接在浏览器打开。";
        }
        FunnelPrompt.show(ctx, url, title, message);
    }

    private static boolean isTamperLevel(RiskState.Level level) {
        return level == RiskState.Level.TAMPER_FUNNEL
            || level == RiskState.Level.TAMPER_PERSISTENT_FUNNEL;
    }

    private static boolean isFunnelLevel(RiskState.Level level) {
        return level == RiskState.Level.TAMPER_FUNNEL
            || level == RiskState.Level.TAMPER_PERSISTENT_FUNNEL
            || level == RiskState.Level.OFFLINE_FUNNEL;
    }
}
