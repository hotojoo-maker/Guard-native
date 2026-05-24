// core_init.cpp — Top-level initialization coordinator.
//
// Wires together ProcessRouter → AntiTamper → StateMachine → WxidMatcher.
// Called exactly once from guard_core.cpp nativeInit().

#include "guard_core.h"

namespace guard {

InitResult core_init(const std::string& process_name,
                     const std::string& package_name) {
    InitResult result{};

    // 1. Detect process role first — BLOCKED roles get safe defaults immediately.
    result.role = router_detect(process_name);
    if (result.role == ProcessRole::BLOCKED ||
        result.role == ProcessRole::UNKNOWN) {
        result.ok   = false;
        result.risk = RiskState::NONE;
        return result;
    }

    // 2. Anti-tamper check (package name + config_version).
    result.risk = tamper_check(package_name, CONFIG_VERSION);
    if (result.risk != RiskState::NONE) {
        result.ok = false;
        // sm_enter_safe_mode() will be called by nativeInit() on ok=false
        return result;
    }

    // 3. State machine: default HIDDEN on every cold start.
    // TODO Phase 3: restore from MMKV (pass saved state here instead of HIDDEN).
    sm_init(HiddenState::HIDDEN);

    // 4. WxidMatcher: seed test data for Batch 1.
    // TODO Phase 3: call matcher_reload_from_mmkv() here instead.
    if (result.role == ProcessRole::MAIN) {
        matcher_init();
    }
    // :push only gets a matcher if StateBridge populates it (Phase 4).
    // For now it reads the same in-process set, which is fine since we're
    // testing with MMKV multi-process in Phase 3.

    result.ok = true;
    return result;
}

}  // namespace guard
