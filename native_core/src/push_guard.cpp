// push_guard.cpp — Minimal badge/unread guard for :push process.
//
// This module only answers yes/no questions.
// It does not hook anything.  The Java hook layer in :push calls us.
//
// :push process rules (CLAUDE.md rule 6):
//   ✅ read hidden state
//   ✅ read hidden wxid/groupId
//   ✅ block badge write for matched wxid
//   ❌ no UI / no ActivityManager / no network / no complex reflection

#include "guard_core.h"

namespace guard {

bool push_is_guard_active() {
    // Guard only fires when we're actually hiding secrets.
    // Short-circuit path: if not hiding, skip all further checks.
    return sm_is_hiding_active();
}

bool push_should_block_badge(const std::string& wxid) {
    // Iron rule (CLAUDE.md rule 27): short-circuit when not hidden.
    // This keeps :push overhead near zero during VISIBLE state.
    if (!sm_is_hiding_active())            return false;
    if (wxid.empty())                       return false;
    // Block if this wxid or its group is in the hidden set.
    // groupId ends with @chatroom — matcher handles both in one call.
    return matcher_is_hidden_wxid(wxid) || matcher_is_hidden_group(wxid);
}

// TODO Batch 2: push_get_notify_mode() for NotifyPolicy
// TODO Batch 4: StateBridge mmap path if MMKV multi-process latency is too high

}  // namespace guard
