package com.ghost.assist.core;

/**
 * NativeBridge — JNI wrapper for libguardcore.so.
 *
 * Rules (CLAUDE.md rule 23 / 27):
 *   • loadLibrary is called from this class's static initializer,
 *     NOT from WeChat's JNI_OnLoad chain.
 *   • Every caller must check isAvailable() before using native methods
 *     in case the SO failed to load (e.g. first install, ABI mismatch).
 *   • This class owns nothing except the bridge; business logic stays
 *     in each hook module (ConvFilter, MomentsFilter, etc.).
 *
 * Phase 1: all native methods are stubs returning safe defaults.
 * Phase 3: call init() from ModuleMain.handleLoadPackage() before
 *           registering any hook.
 */
public final class NativeBridge {

    // ── Load ──────────────────────────────────────────────────

    private static volatile boolean sAvailable = false;

    static {
        try {
            System.loadLibrary("guardcore");
            sAvailable = true;
        } catch (UnsatisfiedLinkError e) {
            // SO not present (e.g. wrong ABI, first install).
            // All methods return safe defaults — never throw here.
            android.util.Log.w("NCL", "libguardcore.so not loaded: " + e.getMessage());
        }
    }

    /** Returns false if the SO failed to load; callers should fall back to Java-only path. */
    public static boolean isAvailable() { return sAvailable; }

    private NativeBridge() {}

    // ── Process role constants (mirror guard::ProcessRole) ────

    public static final int ROLE_UNKNOWN = 0;
    public static final int ROLE_MAIN    = 1;
    public static final int ROLE_PUSH    = 2;
    public static final int ROLE_BLOCKED = 3;

    // ── Auth state constants (mirror guard::AuthState) ────────

    public static final int AUTH_UNKNOWN    = 0;
    public static final int AUTH_OK         = 1;
    public static final int AUTH_EXPIRED    = 2;
    public static final int AUTH_TAMPERED          = 3;
    public static final int AUTH_NO_LICENSE        = 4;
    public static final int AUTH_ACCOUNT_MISMATCH  = 5;
    public static final int AUTH_DEVICE_MISMATCH   = 6;

    // ── Hidden state constants (mirror guard::HiddenState) ────

    public static final int STATE_VISIBLE   = 0;
    public static final int STATE_HIDDEN    = 1;
    public static final int STATE_UNLOCKING = 2;
    public static final int STATE_LOCKED    = 3;
    public static final int STATE_SAFE_MODE = 4;
    public static final int STATE_EXPIRED   = 5;

    // ── Risk state constants (mirror guard::RiskState) ────────

    public static final int RISK_NONE               = 0;
    public static final int RISK_PACKAGE_MISMATCH   = 1;
    public static final int RISK_CONFIG_TAMPERED    = 2;
    public static final int RISK_GRACE_EXPIRED      = 3;
    public static final int RISK_PIRATE             = 4;

    // ── Offline grace thresholds (hours / days) ───────────────
    // Matches constants in guard_core.h. Java layer reads these to
    // decide which popup_policy variant to show.

    public static final int GRACE_WARN_HOURS     = 24;
    public static final int GRACE_DEGRADE_HOURS  = 72;
    public static final int GRACE_LOCKOUT_DAYS   = 10;

    // ── Init ──────────────────────────────────────────────────

    /**
     * Must be called before any hook callback queries the bridge.
     * Safe to call multiple times (idempotent in native layer).
     *
     * @param processName  process name from handleLoadPackage (e.g. "com.tencent.mm")
     * @param packageName  host app package name (expected "com.tencent.mm")
     * @return true on success; false means SO entered SAFE_MODE
     */
    public static boolean init(String processName, String packageName) {
        if (!sAvailable) return false;
        return nativeInit(processName, packageName);
    }

    // ── Process ───────────────────────────────────────────────

    /** @return one of ROLE_* constants */
    public static int getProcessRole() {
        if (!sAvailable) return ROLE_UNKNOWN;
        return nativeGetProcessRole();
    }

    // ── Auth ──────────────────────────────────────────────────

    /** Batch 1: always true (placeholder). Batch 2: real LicenseBox check. */
    public static boolean isAuthorized() {
        if (!sAvailable) return false;
        return nativeIsAuthorized();
    }

    /** @return one of AUTH_* constants */
    public static int getAuthState() {
        if (!sAvailable) return AUTH_UNKNOWN;
        return nativeGetAuthState();
    }

    // ── Hidden state ──────────────────────────────────────────

    /**
     * True when id-filtering should be active (HIDDEN / LOCKED / UNLOCKING).
     * Hot-path method called from every hook callback — native is O(1).
     */
    public static boolean isHidden() {
        if (!sAvailable) return false;
        return nativeIsHidden();
    }

    public static void setHidden(boolean hidden) {
        if (!sAvailable) return;
        nativeSetHidden(hidden);
    }

    /** Convenience: B-module triggers call this on background / lock events. */
    public static void enterHidden() {
        if (!sAvailable) return;
        nativeEnterHidden();
    }

    /** Convenience: B6 password unlock calls this. */
    public static void enterVisible() {
        if (!sAvailable) return;
        nativeEnterVisible();
    }

    public static void enterSafeMode() {
        if (!sAvailable) return;
        nativeEnterSafeMode();
    }

    // ── WxidMatcher ───────────────────────────────────────────

    /**
     * Whether this wxid is in the hidden friends list.
     * Safe to call from :push process.
     */
    public static boolean isHiddenWxid(String wxid) {
        if (!sAvailable || wxid == null) return false;
        return nativeIsHiddenWxid(wxid);
    }

    /**
     * Whether this groupId is in the hidden groups list.
     * groupId must end with "@chatroom".
     */
    public static boolean isHiddenGroup(String groupId) {
        if (!sAvailable || groupId == null) return false;
        return nativeIsHiddenGroup(groupId);
    }

    // ── PushGuard ─────────────────────────────────────────────

    /**
     * Used by :push process badge/unread write-chain hook.
     * Short-circuits to false when not hiding (CLAUSE.md rule 27).
     */
    public static boolean shouldBlockBadge(String wxid) {
        if (!sAvailable || wxid == null) return false;
        return nativeShouldBlockBadge(wxid);
    }

    // ── Config / risk ─────────────────────────────────────────

    public static int getConfigVersion() {
        if (!sAvailable) return 0;
        return nativeGetConfigVersion();
    }

    /** @return one of RISK_* constants; non-zero means popup should show */
    public static int getRiskState() {
        if (!sAvailable) return RISK_NONE;
        return nativeGetRiskState();
    }

    // ── Combined shouldHide helper ────────────────────────────
    // Matches docs/PRODUCT_GATE.md §三 shouldHide() formula.
    // Phase 3: replace Bridge.getWxids().contains() calls with this.

    public static boolean shouldHideWxid(String wxid) {
        if (!sAvailable) return false;
        return isAuthorized() && isHidden() && isHiddenWxid(wxid);
    }

    public static boolean shouldHideGroup(String groupId) {
        if (!sAvailable) return false;
        return isAuthorized() && isHidden() && isHiddenGroup(groupId);
    }

    /**
     * Set the auth state in the C++ engine directly (used after successful bindAccount()).
     * v1: no-op when SO is unavailable; the state is authoritative in Java AuthManager.
     */
    public static void setAuthState(int authState) {
        if (!sAvailable) return;
        try {
            // v1 stub: auth state lives in Java; C++ reads it on next cold-start evaluate()
            android.util.Log.i("NCL", "[native] setAuthState=" + authState);
        } catch (Throwable t) {
            android.util.Log.w("NCL", "[native] setAuthState err: " + t);
        }
    }

    // ── Phase 1A: ConfigCrypto (AES-GCM prototype, no business wiring) ─

    /** Runs fixed AES-GCM vectors + tamper checks inside libguardcore.so. */
    public static boolean decryptConfigSelfTest() {
        if (!sAvailable) return false;
        return nativeDecryptConfigSelfTest();
    }

    /** Smoke-test registry decrypted from an in-SO test vector. */
    public static String decryptConfigTestRegistry() {
        if (!sAvailable) return "";
        return nativeDecryptConfigTestRegistry();
    }

    /**
     * Decrypt an encrypted registry blob. On failure returns scatter JSON
     * (empty entries), never throws, never opens all features.
     */
    public static String decryptConfig(byte[] key, byte[] nonce, byte[] ciphertext, byte[] tag) {
        if (!sAvailable || key == null || nonce == null || tag == null) {
            return "{\"schema_id\":\"scatter\",\"wechat_version\":\"0.0.0\",\"entries\":{}}";
        }
        if (ciphertext == null) ciphertext = new byte[0];
        return nativeDecryptConfig(key, nonce, ciphertext, tag);
    }

    // ── Phase 1B: ConfigRegistry (plaintext parse, no business wiring) ─

    /**
     * Parses the SO-embedded registry and verifies conv.list anchors match
     * ConvFilter.java, plus malformed-input scatter paths. Does not touch
     * any business hook. Returns false if SO unavailable or any check fails.
     */
    public static boolean registrySelfTest() {
        if (!sAvailable) return false;
        return nativeRegistrySelfTest();
    }

    /** One-line summary of the embedded registry (for verification log). */
    public static String registrySummary() {
        if (!sAvailable) return "";
        return nativeRegistrySummary();
    }

    /**
     * DEBUG-only KDF cross-check: verifies guard::derive_registry_key /
     * derive_bootstrap_key match the Python kdf_common vectors embedded in the
     * debug SO (native_core/src/kdf_vectors.inc) — catches F-31 derive drift.
     * The native symbol exists ONLY in Debug-config SO builds (GUARD_DEV_SELFTEST,
     * see CMakeLists.txt — NOT GUARD_DEBUG, which leaks into release), so callers
     * MUST guard with BuildConfig.DEBUG; release never links it. Release SO's
     * guard::kdf_self_test() is a no-op stub anyway.
     */
    public static boolean kdfSelfTest() {
        if (!sAvailable) return false;
        return nativeKdfSelfTest();
    }

    // ── Phase 1E Step1: single recipe getter (read-only SO→Java channel) ─

    /**
     * Fetch one recipe field from the decrypted registry:
     * {@code entries[gateway].fields[key]}. Returns "" (fail-closed) when the
     * SO is unavailable, the registry scattered, or the gateway/field is
     * unknown. Pure read — does NOT take over any Filter; callers opt in.
     */
    public static String getRecipe(String gateway, String key) {
        if (!sAvailable || gateway == null || key == null) return "";
        String v = nativeGetRecipe(gateway, key);
        return v == null ? "" : v;
    }

    // ── C2: cert-only bootstrap AUTH server endpoint (domain hidden in SO) ──

    /**
     * Fetch one AUTH server endpoint URL from the cert-only bootstrap blob.
     * key ∈ {"primary","backup1","backup2"}. Returns "" (hard fail-closed) when
     * the SO is unavailable, the blob scattered (repackaged / wrong cert), or
     * the key is unknown — AppConfig.guardServerList() then yields no server.
     * The plaintext domain lives only in native_core/bootstrap_endpoints.json
     * (build input); the shipped SO carries only AES-GCM ciphertext.
     */
    public static String getEndpoint(String key) {
        if (!sAvailable || key == null) return "";
        String v = nativeGetEndpoint(key);
        return v == null ? "" : v;
    }

    // ── Phase 1D-local A-step2: signing-cert binding ──────────

    /**
     * Push the module's own signing-cert SHA-256 into the SO. The registry key
     * derivation folds this in, so a re-signed / repackaged APK derives a wrong
     * key → scatter. Must be called BEFORE registrySelfTest()/registrySummary()
     * (i.e. before the embedded registry is decrypted). No-op if SO unavailable.
     */
    public static void setBindingMaterial(byte[] certSha256) {
        if (!sAvailable || certSha256 == null || certSha256.length == 0) return;
        nativeSetBindingMaterial(certSha256);
    }

    /**
     * Phase 1F (牙③ W_dev): push the per-device material D_mat = SHA-256(ANDROID_ID)
     * (full 32B) to the SO for the per-device wrap key. MUST be the real device
     * ANDROID_ID, never the official SSAID that A2 feeds the host (A2 同源隔离).
     * Batch 0: SO stores it only (not yet used to unwrap k). No-op if SO unavailable.
     */
    public static void setDeviceMaterial(byte[] deviceMaterial) {
        if (!sAvailable || deviceMaterial == null || deviceMaterial.length == 0) return;
        nativeSetDeviceMaterial(deviceMaterial);
    }

    // ── Phase 1D-server (S3a): server seed (S_rel) from envelope k ─

    /**
     * Unwrap the envelope's {@code k} (server seed material) into the SO so
     * {@code derive_registry_key} folds it. {@code k} = ct(32)‖tag(16) from
     * AES-128-GCM(S_rel, W[:16], n[:12]); {@code nonce} = envelope {@code n}
     * (16 bytes, first 12 used). Returns false on any failure — the SO then
     * scatters the registry (fail-closed). Call BEFORE registry decrypt.
     */
    public static boolean unwrapServerSeed(byte[] k, byte[] nonce) {
        if (!sAvailable || k == null || nonce == null) return false;
        return nativeUnwrapServerSeed(k, nonce);
    }

    /**
     * Single safe outlet for S3a callers: update the runtime server seed and
     * invalidate GuardRuntime's registry readiness cache in the same step.
     */
    public static boolean applyServerSeedAndReset(byte[] k, byte[] nonce) {
        boolean ok = unwrapServerSeed(k, nonce);
        GuardRuntime.resetConfigCache();
        return ok;
    }

    // TODO Batch 2: nativeGetNotifyMode()
    // TODO Batch 2: nativeShouldShowSecretUnreadCount()
    // TODO Batch 2: nativeShouldNotifySecret(String wxid)
    // TODO Batch 2: nativeIsPrivacyEnabled()
    // TODO Batch 2: nativeCanUseFeature(String featureName)
    // TODO Batch 3: FeatureGate (virtual_location / step_count / balance_display)

    // ── Native declarations ───────────────────────────────────

    private static native boolean nativeInit(String processName, String packageName);
    private static native int     nativeGetProcessRole();
    private static native boolean nativeIsAuthorized();
    private static native int     nativeGetAuthState();
    private static native boolean nativeIsHidden();
    private static native void    nativeSetHidden(boolean hidden);
    private static native void    nativeEnterHidden();
    private static native void    nativeEnterVisible();
    private static native void    nativeEnterSafeMode();
    private static native boolean nativeIsHiddenWxid(String wxid);
    private static native boolean nativeIsHiddenGroup(String groupId);
    private static native boolean nativeShouldBlockBadge(String wxid);
    private static native int     nativeGetConfigVersion();
    private static native int     nativeGetRiskState();
    private static native boolean nativeDecryptConfigSelfTest();
    private static native String  nativeDecryptConfigTestRegistry();
    private static native String  nativeDecryptConfig(byte[] key, byte[] nonce,
                                                      byte[] ciphertext, byte[] tag);
    private static native boolean nativeRegistrySelfTest();
    private static native String  nativeRegistrySummary();
    // DEBUG-only: bound only in Debug-config SO builds (GUARD_DEV_SELFTEST, see
    // guard_core.cpp). Call exclusively under BuildConfig.DEBUG so release never
    // links this symbol.
    private static native boolean nativeKdfSelfTest();
    private static native void    nativeSetBindingMaterial(byte[] certSha256);
    private static native void    nativeSetDeviceMaterial(byte[] deviceMaterial);
    private static native boolean nativeUnwrapServerSeed(byte[] k, byte[] nonce);
    private static native String  nativeGetRecipe(String gateway, String key);
    private static native String  nativeGetEndpoint(String key);
}
