package com.ghost.assist.core;

import com.ghost.assist.BuildConfig;
import com.ghost.assist.net.EnvelopeStore;

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

    // ── A2 防封授权闸（Phase A2-1 · 签名轴）─────────────────────
    //
    // isAntiBanReady() 是 A2 防封能力（喂官方签名）的唯一闸出口，与隐私
    // isActive() / isConfigReady() 两闸独立、互不连坐（设计稿 §5 / DESIGN §1 /
    // 授权检查官 §九）。隐私功能仍走 isActive()，本闸只门控 A2，不读 StateMachine。
    //
    // 本期最小真闸（设计稿 §5；安全官红线 #1「不是裸客户端布尔」）：
    //   有效授权信封 + registry 解开 + 官方 DER 料解得出 → 才装 A2。
    // 盗版无 server seed → 官方 DER 料解不出 → recipeOk=false → 闸 false → A2 散沙
    // （fail-closed，设计稿 §4/§9）。完整时间闸（T_soft/T_login/T_kill/影子期）=
    // A2-4 后补；本期到期宽限沿用授权链（isAuthorizedNow 内部已用 LeaseClock 防回拨
    // 判到期），不在此引入墙钟时间逻辑（红线 #3）。

    /** A2 签名轴官方 DER 取件口（registry gateway/field；料缺即闸 false 散沙）。 */
    public static final String A2_SIG_GATEWAY      = "a2.sig";
    public static final String A2_SIG_OFFICIAL_DER = "official_der";

    public static boolean isAntiBanReady() {
        return EnvelopeStore.isAuthorizedNow()
                && isConfigReady()
                && hasRecipe(A2_SIG_GATEWAY, A2_SIG_OFFICIAL_DER);
    }

    /**
     * DEBUG-only cold-start self-test for the A2 anti-ban gate. Logs the final
     * gate decision plus its three sub-signals so the active branch
     * (authorized / unauthorized / DER-material-missing) is readable in logcat
     * under the {@code ANTIBAN-GATE} marker. No JUnit harness exists in this
     * repo; this mirrors the existing native KDF / registry self-tests and is
     * gated by BuildConfig.DEBUG at the call site so release never logs it.
     */
    public static void antiBanGateSelfTest(String tag) {
        boolean auth   = EnvelopeStore.isAuthorizedNow();
        boolean cfg    = isConfigReady();
        boolean recipe = hasRecipe(A2_SIG_GATEWAY, A2_SIG_OFFICIAL_DER);
        android.util.Log.i(tag, "[ANTIBAN-GATE] ready=" + isAntiBanReady()
                + " authorizedNow=" + auth
                + " configReady=" + cfg
                + " a2RecipeOk=" + recipe);
    }
}
