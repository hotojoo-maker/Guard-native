package com.ghost.assist.core;

/**
 * GuardRuntime — single entry point for hook recipes (class/field names).
 *
 * Phase 1E Step1 (skeleton, NOT wired into any Filter).
 *
 * Why this class exists
 * ---------------------
 * The encrypted registry (AES-GCM, P1A–P1D) only protects anything once the
 * Filters stop hard-coding class names and read them from the decrypted SO
 * registry instead. This class is the ONE funnel that surfaces those recipes
 * to the Java side, so the "where does a class name come from" decision lives
 * in a single place (auth-review module-boundary requirement).
 *
 * Scope / boundaries (guard-auth-review PASS, 2026-06-09)
 * ------------------------------------------------------
 *   • RiskGate-adjacent (config / key layer) only. It does NOT read or write
 *     the StateMachine, AuthManager, or any AUTH_* state — recipe lookup is
 *     orthogonal to authorization and hide/visible state.
 *   • fail-closed: when the registry is not usable (SO missing, scatter, or a
 *     future LeaseClock / RiskState gate trips) every lookup returns "" so the
 *     caller skips its hook install (no class name → no hook → no crash).
 *   • Step1 does NOT change any Filter. Migrating ConvFilter/SearchFilter/etc.
 *     to read from here is Step2+ and needs a fresh auth-review round.
 *
 * TODO Phase 1D-server: fold the real gate into {@link #isRegistryActive()} —
 *   LeaseClock (short-lived lease) + EncryptedConfigLoader + RiskState — so an
 *   expired lease / tampered package degrades to scatter (empty recipes).
 */
public final class GuardRuntime {

    private GuardRuntime() {}

    /**
     * Whether the recipe registry is currently usable.
     *
     * Step1 skeleton: gated only on the SO being loaded. Phase 1D-server will
     * fold in LeaseClock + RiskState here; until then any future gate failure
     * must also make this return false (fail-closed).
     */
    public static boolean isRegistryActive() {
        return NativeBridge.isAvailable();
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
        if (!isRegistryActive()) return "";
        return NativeBridge.getRecipe(gateway, key);
    }
}
