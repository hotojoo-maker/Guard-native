// state_machine.cpp — Privacy hiding state machine.
//
// Iron rules (from CLAUDE.md rule 27 + product spec §4.3):
//   • Default state on cold start: HIDDEN
//   • HIDDEN only exits via explicit sm_enter_visible() call (never automatic)
//   • LOCKED and SAFE_MODE cannot be exited by normal transitions
//   • Thread-safe: all transitions guarded by g_sm_mutex

#include "guard_core.h"
#include <mutex>
#include <atomic>

namespace guard {

namespace {
    std::mutex              g_sm_mutex;
    // Atomic for reads (frequent hot path), mutex for writes (rare transitions)
    std::atomic<HiddenState> g_state{HiddenState::HIDDEN};
}

void sm_init(HiddenState restore_from_mmkv) {
    std::lock_guard<std::mutex> lock(g_sm_mutex);
    // SAFE_MODE and LOCKED survive a reload — they can only clear after a
    // clean re-auth cycle (not implemented in Batch 1).
    if (restore_from_mmkv == HiddenState::SAFE_MODE ||
        restore_from_mmkv == HiddenState::LOCKED) {
        g_state.store(restore_from_mmkv, std::memory_order_release);
    } else {
        // Default: start hidden regardless of last saved state.
        // Users must explicitly unlock (B6 password) to see secrets.
        g_state.store(HiddenState::HIDDEN, std::memory_order_release);
    }
    log_limited("NCL_SM", "sm_init done", 0);
}

HiddenState sm_get_state() {
    return g_state.load(std::memory_order_acquire);
}

// Returns true when id-filtering should be active (Java hook layer checks this)
bool sm_is_hiding_active() {
    auto s = sm_get_state();
    return s == HiddenState::HIDDEN    ||
           s == HiddenState::UNLOCKING ||
           s == HiddenState::LOCKED;
}

// ── Transition functions ──────────────────────────────────────
// Each checks pre-conditions and logs the transition.

void sm_enter_hidden() {
    std::lock_guard<std::mutex> lock(g_sm_mutex);
    auto current = g_state.load();
    // SAFE_MODE and LOCKED are terminal — don't override them
    if (current == HiddenState::SAFE_MODE || current == HiddenState::LOCKED) return;
    g_state.store(HiddenState::HIDDEN, std::memory_order_release);
    log_limited("NCL_SM", "→ HIDDEN", 5);
}

void sm_enter_visible() {
    std::lock_guard<std::mutex> lock(g_sm_mutex);
    auto current = g_state.load();
    if (current == HiddenState::SAFE_MODE || current == HiddenState::LOCKED) return;
    g_state.store(HiddenState::VISIBLE, std::memory_order_release);
    log_limited("NCL_SM", "→ VISIBLE", 5);
}

void sm_enter_unlocking() {
    std::lock_guard<std::mutex> lock(g_sm_mutex);
    auto current = g_state.load();
    // Only HIDDEN → UNLOCKING is valid; anything else is a no-op
    if (current != HiddenState::HIDDEN) return;
    g_state.store(HiddenState::UNLOCKING, std::memory_order_release);
    log_limited("NCL_SM", "→ UNLOCKING", 5);
}

void sm_enter_locked() {
    std::lock_guard<std::mutex> lock(g_sm_mutex);
    g_state.store(HiddenState::LOCKED, std::memory_order_release);
    log_limited("NCL_SM", "→ LOCKED", 5);
}

void sm_enter_safe_mode() {
    std::lock_guard<std::mutex> lock(g_sm_mutex);
    g_state.store(HiddenState::SAFE_MODE, std::memory_order_release);
    log_limited("NCL_SM", "→ SAFE_MODE", 0);
}

}  // namespace guard
