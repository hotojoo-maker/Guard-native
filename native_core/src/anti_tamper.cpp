// anti_tamper.cpp — Anti-tamper / anti-piracy checks.
//
// Batch 1 (v1): package name + config_version only.
// Detection result drives StateMachine into SAFE_MODE.
// SAFE_MODE = silent feature degradation, never attack logic.
//
// Batch 2 TODO: real LicenseBox (HMAC-SHA256 + AES-GCM signed_config)
// Batch 3 TODO: customer_seed / device_hash / package_hash / honeypot fields
//   Honeypot design: reserved fields in config that legitimate clients never
//   touch; if a cracker modifies them, behavior degrades unpredictably but
//   harmlessly — state flickers, badge occasionally leaks, switches flip.
//   Goal: cracker discovers the problem and contacts official channel.
//
// Popup /弹窗 strategy (Java layer handles display, SO only flags the state):
//   RISK_NONE             → no popup
//   RISK_PACKAGE_MISMATCH → "异常客户端" popup → redirect to official download
//   RISK_PIRATE           → "授权异常" popup → redirect to customer service
//
// Offline grace periods (SO exposes state, Java layer reads + shows popup):
//   0 ~ 24h   : normal, silent background sync
//   24h ~ 72h : warn popup (dismissible: true)
//   > 72h     : degrade popup (dismissible: false)
//   > 10d     : full lockout, only customer service entry remains
//
// popup_policy JSON structure (set by miyou-server, cached in MMKV):
//   {
//     "popup_enabled":    true,
//     "popup_type":       "AUTH_EXPIRED",
//     "title":            "授权异常",
//     "message":          "请联系官方客服恢复授权。",
//     "button_text":      "联系客服",
//     "url":              "https://your-service-url",
//     "interval_minutes": 60,
//     "dismissible":      false,
//     "priority":         10
//   }

#include "guard_core.h"
#include <string>

namespace guard {

RiskState tamper_check(const std::string& package_name, int config_version) {
    // Package name must match exactly — any repackaged APK fails here.
    if (package_name != EXPECTED_PACKAGE) {
        return RiskState::PACKAGE_MISMATCH;
    }

    // Config version must be current — stale or tampered config fails here.
    if (config_version != CONFIG_VERSION) {
        return RiskState::CONFIG_TAMPERED;
    }

    // TODO Batch 3: device_hash binding
    // TODO Batch 3: customer_seed verification
    // TODO Batch 3: package_hash (APK signature digest) verification
    // TODO Batch 3: honeypot field check

    return RiskState::NONE;
}

}  // namespace guard
