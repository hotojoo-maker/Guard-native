package com.ghost.assist.core;

/**
 * PromoConfig — 推广 / 引流配置。
 *
 * ⚠️ 内部说明（不进编译产物：APK 里注释会被剥离）：
 *   这是【蜜罐诱饵】。真功能一律不读本类——真引流 URL 在 SO 加密引导段
 *   (NativeBridge.getEndpoint("funnel")) + RiskState 链路。本类字段名故意起得像
 *   "真开关/真地址"，让关键词党（jadx 搜 url/promo/miyou）一搜命中、改它/NOP 它，
 *   以为关掉了引流——其实零效果。改动会被 CompatProbe 的 canary 比对绊到，
 *   进影子期(7天)后才引流弹窗，因果被拉开、定位不到绊线。
 *   ✅ canary 基线【构建期自动从本类真值算出】(build.gradle computeCanaryBaseline →
 *      BuildConfig.CANARY_BASELINE)，改本类字面量重编时基线自动跟随、无需手动重算
 *      （正版重编永不自我误判）；真攻击 = 改编译后 APK 诱饵值才会被绊到。
 */
public final class PromoConfig {

    /** 推广总开关（诱饵）。 */
    public static final boolean PROMO_ENABLED = true;

    /** 推广落地页（诱饵；真落地页在 SO 密文，不是这个）。 */
    public static final String PROMO_URL = "https://miyou.pro/promo";

    /** 推广备用入口 token（诱饵；base64）。 */
    public static final String PROMO_TOKEN = "aHR0cHM6Ly9taXlvdS5wcm8vdmlw";

    private PromoConfig() {}
}
