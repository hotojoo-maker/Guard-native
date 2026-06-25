package com.ghost.assist.core;

/**
 * EncryptedConfigLoader — guarded access to the SO-decrypted registry pack.
 *
 * P_SEC1 scope: centralize the "is registry usable?" decision without changing
 * any business Filter semantics. Filters still read recipes through
 * GuardRuntime; hide/show decisions remain in StateMachine + hidden lists.
 */
public final class EncryptedConfigLoader {

    private static volatile boolean sChecked = false;
    private static volatile boolean sReady = false;
    private static volatile String sSummary = "";

    private EncryptedConfigLoader() {}

    /**
     * Return true only when libguardcore is loaded and the encrypted registry
     * decrypts to a non-scatter registry. With prod_server_lock, that requires
     * a valid server seed (S_rel) applied via EnvelopeStore → NativeBridge
     * before decrypt; without seed, summary stays "scatter" (fail-closed).
     * LeaseClock/RiskState scatter-on-tamper is still TODO in GuardRuntime.
     */
    public static boolean isConfigReady() {
        ensureChecked();
        return sReady;
    }

    /** Human-readable registry status for debug verification only. */
    public static String getActiveRegistrySummary() {
        ensureChecked();
        return sSummary;
    }

    /**
     * Fetch one registry field. Empty string means fail-closed: caller should
     * keep its fallback or skip hook install, never open all features.
     */
    public static String getRecipe(String gateway, String key) {
        if (gateway == null || key == null) return "";
        if (!isConfigReady()) return "";
        return NativeBridge.getRecipe(gateway, key);
    }

    /** Clear cached readiness after binding material or future envelope refresh. */
    public static void reset() {
        sChecked = false;
        sReady = false;
        sSummary = "";
    }

    private static void ensureChecked() {
        if (sChecked) return;
        synchronized (EncryptedConfigLoader.class) {
            if (sChecked) return;
            if (!NativeBridge.isAvailable()) {
                sSummary = "scatter";
                sReady = false;
                sChecked = true;
                return;
            }
            String summary = NativeBridge.registrySummary();
            if (summary == null || summary.isEmpty()) summary = "scatter";
            sSummary = summary;
            sReady = !"scatter".equals(summary);
            sChecked = true;
        }
    }
}
