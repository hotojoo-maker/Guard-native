// wxid_matcher.cpp — O(1) lookup for hidden wxid / groupId sets.
//
// Batch 1: pre-seeded with one test wxid for validation.
// Phase 3+: sets are populated from MMKV via NativeBridge.reloadWxids().
// Thread-safe: g_matcher_mutex guards all set mutations.

#include "guard_core.h"
#include <unordered_set>
#include <mutex>
#include <string>

namespace guard {

namespace {
    std::mutex                       g_matcher_mutex;
    std::unordered_set<std::string>  g_wxids;
    std::unordered_set<std::string>  g_groups;
}

void matcher_init() {
    std::lock_guard<std::mutex> lock(g_matcher_mutex);
    g_wxids.clear();
    g_groups.clear();

    // ── Batch 1 test seed ──────────────────────────────────────
    // Validation criterion #6: nativeIsHiddenWxid("wxid_lzd2va16jd1622") == true
    // Remove or replace with MMKV-loaded data in Phase 3.
    g_wxids.insert("wxid_lzd2va16jd1622");
    // ──────────────────────────────────────────────────────────
}

bool matcher_is_hidden_wxid(const std::string& wxid) {
    if (wxid.empty()) return false;
    std::lock_guard<std::mutex> lock(g_matcher_mutex);
    return g_wxids.count(wxid) > 0;
}

bool matcher_is_hidden_group(const std::string& group_id) {
    if (group_id.empty()) return false;
    std::lock_guard<std::mutex> lock(g_matcher_mutex);
    return g_groups.count(group_id) > 0;
}

void matcher_add_wxid(const std::string& wxid) {
    if (wxid.empty()) return;
    std::lock_guard<std::mutex> lock(g_matcher_mutex);
    g_wxids.insert(wxid);
}

void matcher_remove_wxid(const std::string& wxid) {
    std::lock_guard<std::mutex> lock(g_matcher_mutex);
    g_wxids.erase(wxid);
}

void matcher_add_group(const std::string& group_id) {
    if (group_id.empty()) return;
    std::lock_guard<std::mutex> lock(g_matcher_mutex);
    g_groups.insert(group_id);
}

void matcher_remove_group(const std::string& group_id) {
    std::lock_guard<std::mutex> lock(g_matcher_mutex);
    g_groups.erase(group_id);
}

void matcher_clear() {
    std::lock_guard<std::mutex> lock(g_matcher_mutex);
    g_wxids.clear();
    g_groups.clear();
}

}  // namespace guard
