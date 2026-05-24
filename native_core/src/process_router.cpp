// process_router.cpp — Determine which process we're running in.
//
// Reads /proc/self/cmdline directly (pure C++).
// Never calls ActivityManager.getRunningAppProcesses (iron rule #7).

#include "guard_core.h"
#include <fstream>
#include <string>

namespace guard {

// cmdline bytes are NUL-separated; reading as a string stops at first NUL,
// which gives us exactly the process name we need.
static std::string read_cmdline() {
    std::ifstream f("/proc/self/cmdline", std::ios::binary);
    if (!f.is_open()) return {};
    std::string name;
    std::getline(f, name, '\0');  // read until first NUL terminator
    return name;
}

ProcessRole router_detect(const std::string& process_name) {
    // Prefer the caller-supplied name (ModuleMain already has it from
    // handleLoadPackage), fall back to reading /proc/self/cmdline.
    const std::string& name = process_name.empty() ? read_cmdline() : process_name;

    if (name == PROCESS_MAIN)  return ProcessRole::MAIN;
    if (name == PROCESS_PUSH)  return ProcessRole::PUSH;

    // Processes we must never hook — treat as BLOCKED.
    // Even if somehow loaded there, every query returns safe defaults.
    if (name.find(":sandboxed_process") != std::string::npos ||
        name.find(":isolated_")         != std::string::npos ||
        name.find(":appbrand")          != std::string::npos) {
        return ProcessRole::BLOCKED;
    }

    return ProcessRole::UNKNOWN;
}

}  // namespace guard
