package com.ghost.assist.core;

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
}
