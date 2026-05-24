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

// If init failed, every query returns a safe "don't hide anything" default.
// This prevents accidental exposure on partial failure.
inline bool not_ready() { return !g_initialized.load(std::memory_order_acquire); }

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
