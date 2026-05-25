// auth_engine.cpp — Authorization state atom.
//
// P4-1: Java evaluates wxid+device match, calls nativeSetAuthState() to push result.
//        C++ only stores and serves; no IO, no network, no WeChat coupling.
// P4-2: Full Ed25519 LicenseBox verification will move here.
//
// Iron rule §6.10: AUTH_OK = wxid match && device match.
// All other states → features off.

#include "guard_core.h"
#include <atomic>

namespace guard {

static std::atomic<int> g_auth_state{static_cast<int>(AuthState::UNKNOWN)};

void auth_set_state(AuthState s) {
    g_auth_state.store(static_cast<int>(s), std::memory_order_release);
}

AuthState auth_get_state() {
    return static_cast<AuthState>(g_auth_state.load(std::memory_order_acquire));
}

bool auth_is_authorized() {
    return auth_get_state() == AuthState::AUTH_OK;
}

}  // namespace guard
