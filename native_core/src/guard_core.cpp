// guard_core.cpp — JNI entry point for libguardcore.so
//
// All exported symbols follow the JNI naming convention:
//   Java_com_ghost_assist_core_NativeBridge_nativeXxx
//
// This file owns nothing except the global init state and JNI glue.
// Business logic lives in the individual module .cpp files.
//
// Iron rules enforced here:
//   • No dlopen of WeChat SOs
//   • No hook of any native method
//   • No call into WeChat's JNI_OnLoad chain
//   • nativeInit must complete before any hook callback queries us

#include <jni.h>
#include <atomic>
#include <mutex>
#include <string>
#include <vector>
#include "guard_core.h"

// ── Global state ──────────────────────────────────────────────

namespace {

std::atomic<bool>           g_initialized{false};
std::atomic<guard::ProcessRole> g_role{guard::ProcessRole::UNKNOWN};
std::mutex                  g_init_mutex;

// Convenience: convert jstring → std::string, release automatically.
// Returns empty string on null input (safe default, never throws).
std::string jstr(JNIEnv* env, jstring js) {
    if (!js) return {};
    const char* c = env->GetStringUTFChars(js, nullptr);
    if (!c) return {};
    std::string s(c);
    env->ReleaseStringUTFChars(js, c);
    return s;
}

std::vector<uint8_t> jbytes(JNIEnv* env, jbyteArray array) {
    if (!array) return {};
    const jsize len = env->GetArrayLength(array);
    if (len <= 0) return {};
    std::vector<uint8_t> out(static_cast<size_t>(len));
    env->GetByteArrayRegion(array, 0, len, reinterpret_cast<jbyte*>(out.data()));
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return {};
    }
    return out;
}

// If init failed, every query returns a safe "don't hide anything" default.
// This prevents accidental exposure on partial failure.
inline bool not_ready() { return !g_initialized.load(std::memory_order_acquire); }

constexpr const char* SCATTER_REGISTRY =
        "{\"schema_id\":\"scatter\",\"wechat_version\":\"0.0.0\",\"entries\":{}}";

}  // namespace

// ── JNI exports ───────────────────────────────────────────────

extern "C" {

// ── Init ──────────────────────────────────────────────────────

JNIEXPORT jboolean JNICALL
Java_com_ghost_assist_core_NativeBridge_nativeInit(
        JNIEnv* env, jclass, jstring jProcessName, jstring jPackageName) {

    std::lock_guard<std::mutex> lock(g_init_mutex);
    if (g_initialized.load()) return JNI_TRUE;  // idempotent

    std::string process_name = jstr(env, jProcessName);
    std::string package_name = jstr(env, jPackageName);

    guard::InitResult result = guard::core_init(process_name, package_name);

    g_role.store(result.role, std::memory_order_release);

    if (!result.ok) {
        // Package mismatch or critical tamper → SAFE_MODE, still mark init done
        // so callers get safe defaults rather than crashing on every call.
        guard::sm_enter_safe_mode();
    }

    g_initialized.store(true, std::memory_order_release);
    guard::log_limited("NCL", "nativeInit done", 0);
    return result.ok ? JNI_TRUE : JNI_FALSE;
}

// ── Process role ──────────────────────────────────────────────

JNIEXPORT jint JNICALL
Java_com_ghost_assist_core_NativeBridge_nativeGetProcessRole(
        JNIEnv*, jclass) {
    return static_cast<jint>(g_role.load(std::memory_order_acquire));
}

// ── Auth (Batch 1: placeholder — always AUTHORIZED) ──────────
//
// TODO Batch 2: replace with real LicenseBox HMAC-SHA256 + AES-GCM check.
// TODO Batch 2: connect to miyou-server signed_config verification.

JNIEXPORT jboolean JNICALL
Java_com_ghost_assist_core_NativeBridge_nativeIsAuthorized(
        JNIEnv*, jclass) {
    if (not_ready()) return JNI_FALSE;
    // BATCH 1 PLACEHOLDER — always authorized until LicenseBox is wired
    return JNI_TRUE;
}

JNIEXPORT jint JNICALL
Java_com_ghost_assist_core_NativeBridge_nativeGetAuthState(
        JNIEnv*, jclass) {
    if (not_ready()) return static_cast<jint>(guard::AuthState::UNKNOWN);
    // BATCH 1 PLACEHOLDER — always AUTH_OK
    return static_cast<jint>(guard::AuthState::AUTH_OK);
}

// ── Hidden state ──────────────────────────────────────────────

JNIEXPORT jboolean JNICALL
Java_com_ghost_assist_core_NativeBridge_nativeIsHidden(
        JNIEnv*, jclass) {
    if (not_ready()) return JNI_FALSE;
    return guard::sm_is_hiding_active() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_ghost_assist_core_NativeBridge_nativeSetHidden(
        JNIEnv*, jclass, jboolean hidden) {
    if (not_ready()) return;
    if (hidden) guard::sm_enter_hidden();
    else        guard::sm_enter_visible();
}

JNIEXPORT void JNICALL
Java_com_ghost_assist_core_NativeBridge_nativeEnterHidden(
        JNIEnv*, jclass) {
    if (not_ready()) return;
    guard::sm_enter_hidden();
}

JNIEXPORT void JNICALL
Java_com_ghost_assist_core_NativeBridge_nativeEnterVisible(
        JNIEnv*, jclass) {
    if (not_ready()) return;
    guard::sm_enter_visible();
}

JNIEXPORT void JNICALL
Java_com_ghost_assist_core_NativeBridge_nativeEnterSafeMode(
        JNIEnv*, jclass) {
    if (not_ready()) return;
    guard::sm_enter_safe_mode();
}

// ── WxidMatcher ───────────────────────────────────────────────

JNIEXPORT jboolean JNICALL
Java_com_ghost_assist_core_NativeBridge_nativeIsHiddenWxid(
        JNIEnv* env, jclass, jstring jWxid) {
    if (not_ready()) return JNI_FALSE;
    return guard::matcher_is_hidden_wxid(jstr(env, jWxid)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_ghost_assist_core_NativeBridge_nativeIsHiddenGroup(
        JNIEnv* env, jclass, jstring jGroupId) {
    if (not_ready()) return JNI_FALSE;
    return guard::matcher_is_hidden_group(jstr(env, jGroupId)) ? JNI_TRUE : JNI_FALSE;
}

// ── PushGuard ─────────────────────────────────────────────────

JNIEXPORT jboolean JNICALL
Java_com_ghost_assist_core_NativeBridge_nativeShouldBlockBadge(
        JNIEnv* env, jclass, jstring jWxid) {
    if (not_ready()) return JNI_FALSE;
    return guard::push_should_block_badge(jstr(env, jWxid)) ? JNI_TRUE : JNI_FALSE;
}

// ── Config / risk ─────────────────────────────────────────────

JNIEXPORT jint JNICALL
Java_com_ghost_assist_core_NativeBridge_nativeGetConfigVersion(
        JNIEnv*, jclass) {
    return static_cast<jint>(guard::CONFIG_VERSION);
}

JNIEXPORT jint JNICALL
Java_com_ghost_assist_core_NativeBridge_nativeGetRiskState(
        JNIEnv*, jclass) {
    if (not_ready()) return static_cast<jint>(guard::RiskState::NONE);
    // AntiTamper result is captured at init time and stored in StateMachine's
    // SAFE_MODE flag; surface it back here for Java-side popup decisions.
    auto state = guard::sm_get_state();
    if (state == guard::HiddenState::SAFE_MODE) {
        return static_cast<jint>(guard::RiskState::PACKAGE_MISMATCH);
    }
    return static_cast<jint>(guard::RiskState::NONE);
}

// ── Phase 1A crypto prototype (not connected to business hooks) ─

JNIEXPORT jboolean JNICALL
Java_com_ghost_assist_core_NativeBridge_nativeDecryptConfigSelfTest(
        JNIEnv*, jclass) {
    return guard::decrypt_config_self_test() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_ghost_assist_core_NativeBridge_nativeDecryptConfigTestRegistry(
        JNIEnv* env, jclass) {
    const std::string registry = guard::decrypt_config_test_registry();
    return env->NewStringUTF(registry.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_ghost_assist_core_NativeBridge_nativeDecryptConfig(
        JNIEnv* env, jclass, jbyteArray jKey, jbyteArray jNonce,
        jbyteArray jCiphertext, jbyteArray jTag) {
    const std::vector<uint8_t> key = jbytes(env, jKey);
    const std::vector<uint8_t> nonce = jbytes(env, jNonce);
    const std::vector<uint8_t> ciphertext = jbytes(env, jCiphertext);
    const std::vector<uint8_t> tag = jbytes(env, jTag);

    const uint8_t* ct_ptr = ciphertext.empty() ? nullptr : ciphertext.data();
    const auto result = guard::decrypt_config(key.data(), key.size(),
                                              nonce.data(), nonce.size(),
                                              ct_ptr, ciphertext.size(),
                                              tag.data(), tag.size());
    if (!result.ok) return env->NewStringUTF(SCATTER_REGISTRY);
    return env->NewStringUTF(result.plaintext.c_str());
}

// ── Phase 1B registry prototype (parse + verify only, no business wiring) ─

JNIEXPORT jboolean JNICALL
Java_com_ghost_assist_core_NativeBridge_nativeRegistrySelfTest(
        JNIEnv*, jclass) {
    return guard::registry_self_test() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_ghost_assist_core_NativeBridge_nativeRegistrySummary(
        JNIEnv* env, jclass) {
    const std::string summary = guard::registry_dump_summary();
    return env->NewStringUTF(summary.c_str());
}

// ── Phase 1E Step1: single recipe getter (read-only SO→Java channel) ──
//
// Returns entries[gateway].fields[key] from the decrypted registry, or "" on
// scatter / unknown gateway / unknown field (fail-closed). No business wiring:
// callers (later GuardRuntime → Filters) opt in; this only surfaces a value.

JNIEXPORT jstring JNICALL
Java_com_ghost_assist_core_NativeBridge_nativeGetRecipe(
        JNIEnv* env, jclass, jstring jGateway, jstring jKey) {
    const std::string recipe =
            guard::registry_get_recipe(jstr(env, jGateway), jstr(env, jKey));
    return env->NewStringUTF(recipe.c_str());
}

// ── C2: cert-only bootstrap endpoint getter (AUTH server domains) ──
//
// Returns the https URL for key ∈ {"primary","backup1","backup2"} from the
// decrypted bootstrap blob, or "" on scatter / unknown key (hard fail-closed →
// AppConfig.guardServerList() yields no server → repackaged build can't connect).
// Decrypts cert-only (no server seed); see docs/HONEYPOT_蜜罐设计.md §4.

JNIEXPORT jstring JNICALL
Java_com_ghost_assist_core_NativeBridge_nativeGetEndpoint(
        JNIEnv* env, jclass, jstring jKey) {
    const std::string ep = guard::bootstrap_get_endpoint(jstr(env, jKey));
    return env->NewStringUTF(ep.c_str());
}

// ── Phase 1D-local A-step2: signing-cert binding for the registry key ─

JNIEXPORT void JNICALL
Java_com_ghost_assist_core_NativeBridge_nativeSetBindingMaterial(
        JNIEnv* env, jclass, jbyteArray jMaterial) {
    const std::vector<uint8_t> material = jbytes(env, jMaterial);
    guard::set_binding_material(material.data(), material.size());
}

// ── Phase 1D-server (S3a): unwrap envelope k → server seed ────

JNIEXPORT jboolean JNICALL
Java_com_ghost_assist_core_NativeBridge_nativeUnwrapServerSeed(
        JNIEnv* env, jclass, jbyteArray jK, jbyteArray jNonce) {
    const std::vector<uint8_t> k = jbytes(env, jK);
    const std::vector<uint8_t> n = jbytes(env, jNonce);
    return guard::unwrap_server_seed(k.data(), k.size(), n.data(), n.size())
            ? JNI_TRUE : JNI_FALSE;
}

// ── Batch 2 / 3 stubs (compile-only, not connected) ──────────
//
// TODO Batch 2: nativeGetNotifyMode()
// TODO Batch 2: nativeShouldShowSecretUnreadCount()
// TODO Batch 2: nativeShouldNotifySecret(wxid)
// TODO Batch 2: nativeCanUseFeature(featureName)
// TODO Batch 2: nativeIsPrivacyEnabled()
//
// TODO Batch 3: FeatureGate for virtual_location / step_count / balance_display
// TODO Batch 3: AntiTamper v2 (customer_seed + device_hash + honeypot)
// TODO Batch 3: ConfigEncryptor (AES-GCM config blob)
//
// Offline grace / popup policy (Java layer handles network + UI):
//   0~24h  : normal silent sync
//   24~72h : warn popup (dismissible)
//   >72h   : degrade popup (non-dismissible)
//   >10d   : full lockout, customer service entry only

}  // extern "C"
