package com.ghost.assist.core;

import android.content.Context;

import com.ghost.assist.BuildConfig;

/**
 * GuardRuntime — single entry point for hook recipes (class/field names).
 *
 * P_SEC1 / P1E: GuardRuntime + encrypted registry access shell.
 *
 * Why this class exists
 * ---------------------
 * The encrypted registry only protects the valuable hook recipes when Java code
 * reads those recipes through one controlled outlet. This class is that outlet:
 * business code asks GuardRuntime for a recipe, and GuardRuntime delegates the
 * readiness / scatter decision to EncryptedConfigLoader.
 *
 * Scope / boundaries (guard-auth-review PASS, 2026-06-09)
 * ------------------------------------------------------
 *   • RiskGate-adjacent (config / key layer) only. It does NOT read or write
 *     the StateMachine, AuthManager, or any AUTH_* state — recipe lookup is
 *     orthogonal to authorization and hide/visible state.
 *   • fail-closed: when the registry is not usable (SO missing, scatter, or a
 *     future LeaseClock / RiskState gate trips) every lookup returns "" so the
 *     caller keeps its fallback or skips its hook install.
 *   • Filters may consume recipes, but hide/show decisions still belong to
 *     StateMachine.isActive() + the hidden-id lists.
 *
 * Server seed (S_rel) is applied before registry decrypt via EnvelopeStore;
 * TODO: fold LeaseClock + RiskState into EncryptedConfigLoader so
 *   expired/tampered envelopes degrade to scatter.
 */
public final class GuardRuntime {

    private GuardRuntime() {}

    /**
     * Whether the encrypted registry is currently usable.
     */
    public static boolean isConfigReady() {
        return EncryptedConfigLoader.isConfigReady();
    }

    /**
     * Customer release builds must not fall back to embedded hook recipes. The
     * switch is build-time only so R8 can strip debug fallback literals.
     */
    public static boolean isStrictRecipeMode() {
        return !BuildConfig.DEBUG;
    }

    /**
     * Sensitive hooks are allowed only when the registry is ready in strict
     * release mode. Debug/dev keeps the old behavior for diagnostics.
     */
    public static boolean isSensitiveConfigReady() {
        return !isStrictRecipeMode() || isConfigReady();
    }

    /** Backward-compatible alias used by early P1E notes. */
    public static boolean isRegistryActive() {
        return isConfigReady();
    }

    /** Debug-only one-line summary of the active registry. */
    public static String getActiveRegistrySummary() {
        return EncryptedConfigLoader.getActiveRegistrySummary();
    }

    /** Clear cached registry readiness after binding material / future refresh. */
    public static void resetConfigCache() {
        EncryptedConfigLoader.reset();
    }

    /**
     * Look up one recipe field: {@code gateway.key} (e.g.
     * {@code getRecipe("conv.list", "adapter_class")} → {@code "kc5.v0"}).
     *
     * @return the recipe value, or "" when the registry is inactive / scattered
     *         or the gateway/field is unknown (fail-closed — callers must treat
     *         an empty result as "skip this hook").
     */
    public static String getRecipe(String gateway, String key) {
        return EncryptedConfigLoader.getRecipe(gateway, key);
    }

    /**
     * Transitional helper for Filter install-time anchors. Release+PROD returns
     * empty on registry miss so callers can skip installing that sensitive hook;
     * debug/dev returns the known literal fallback.
     */
    public static String getRecipeOrFallback(String gateway, String key, String fallback) {
        String v = getRecipe(gateway, key);
        if (v != null && !v.isEmpty()) return v;
        return isStrictRecipeMode() ? "" : fallback;
    }

    public static String[] getRecipeListOrFallback(String gateway, String key, String[] fallback) {
        String v = getRecipe(gateway, key);
        if (v == null || v.isEmpty()) {
            return isStrictRecipeMode() ? new String[0] : fallback;
        }
        String[] parts = v.split(",");
        return parts.length > 0 ? parts : (isStrictRecipeMode() ? new String[0] : fallback);
    }

    public static boolean hasRecipe(String gateway, String key) {
        String v = getRecipe(gateway, key);
        return v != null && !v.isEmpty();
    }

    // ── A2 防封授权闸（Route B · 本地完整性轴）─────────────────────
    //
    // isAntiBanReady() 是 A2 防封能力（喂官方签名）的唯一闸出口，与隐私
    // isActive() / isConfigReady() 两闸独立、互不连坐（DESIGN §1 / 授权检查官 §九）。
    // 隐私功能仍走 isActive()（server-seed 不动），本闸只门控 A2，不读 StateMachine。
    //
    // D-018（用户 2026-06-26 拍板）：A2 防封改吊【本地完整性】，不再吊授权 / server seed。
    //   防封惠及所有「未被重签」的副本——首装 / 断网 / 未授权都保号；只有 CompatProbe
    //   读到模块证书且确证 ≠ 预期（= 被重打包重签）→ isIntegrityIntact=false → A2 散沙。
    //   逆序线 fail-open：证书读不到 / 相符 → 装 A2（保护优先，误判 = 账号异常不可逆）。
    //   canary 刻意不进本门（吊编译期基线、漏算会整片误封），仍走 CompatProbe.check→
    //   markTampered→影子期引流（不变）。放弃「白嫖到期撤 A2」反白嫖杠杆，变现靠隐私付费门。
    //   官方 DER 已本地化（公开值，见 A2SignatureSpoof.OFFICIAL_DER_HEX）。
    //
    // 退款例外（场景#6 / SPEC §4）：isAntiBanReady = isIntegrityIntact && !EnvelopeStore.isRefunded()。
    //   退款是【唯一】连坐 A2 的非篡改场景（用户明确不想用了）；信号源 = 服务器(块A)下发信封 rf 位
    //   → EnvelopeStore.markRefunded() 落持久标志。其余场景（首装/断网/未授权/到期）A2 永不因时间撤。

    /** A2 官方 DER registry 取件口（D-018 起代码不再读；registry entry 保留、料已常量化）。 */
    public static final String A2_SIG_GATEWAY      = "a2.sig";
    public static final String A2_SIG_OFFICIAL_DER = "official_der";

    public static boolean isAntiBanReady(Context ctx, String modulePath) {
        // 退款（场景#6 / SPEC §4）= 唯一连坐 A2 的非篡改场景：用户明确不想用了 → 立刻撤 A2。
        // 其余（首装/断网/未授权/到期）永不因时间撤 A2（D-018）。
        if (com.ghost.assist.net.EnvelopeStore.isRefunded()) return false;
        return CompatProbe.isIntegrityIntact(ctx, modulePath);
    }

    /**
     * DEBUG-only cold-start self-test for the A2 anti-ban gate. Logs the final
     * gate decision plus the cert-integrity sub-signal under the
     * {@code ANTIBAN-GATE} marker (Route B / D-018: gate = local module-cert
     * integrity, not authorization). No JUnit harness exists in this repo; this
     * mirrors the existing native KDF / registry self-tests and is gated by
     * BuildConfig.DEBUG at the call site so release never logs it.
     */
    public static void antiBanGateSelfTest(String tag, Context ctx, String modulePath) {
        boolean integrity = CompatProbe.isIntegrityIntact(ctx, modulePath);
        boolean refunded = com.ghost.assist.net.EnvelopeStore.isRefunded();
        android.util.Log.i(tag, "[ANTIBAN-GATE] ready=" + isAntiBanReady(ctx, modulePath)
                + " certIntegrityIntact=" + integrity
                + " refunded=" + refunded
                + " (gate=local-cert && !refunded; D-018: unpaid/offline also protected)");
    }
}
