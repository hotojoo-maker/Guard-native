// log_limiter.cpp — Rate-limited logcat output.
//
// Each (tag, msg) pair may fire at most once per window_seconds.
// In release builds (GUARD_DEV_LOG not defined) this entire file is a no-op,
// so zero overhead and zero logcat exposure in production.
//
// Note: state_machine.cpp includes this file directly for Phase 1 simplicity.
// In Phase 2, extract the declaration to a header and link normally.

#include "guard_core.h"

#ifdef GUARD_DEV_LOG

#include <android/log.h>
#include <unordered_map>
#include <string>
#include <chrono>
#include <mutex>

namespace guard {

namespace {
    struct Entry { int64_t last_ms; };
    std::mutex                              g_log_mutex;
    std::unordered_map<std::string, Entry>  g_log_map;
}

void log_limited(const char* tag, const char* msg, int window_seconds) {
    using namespace std::chrono;
    auto now_ms = duration_cast<milliseconds>(
        system_clock::now().time_since_epoch()).count();

    std::string key = std::string(tag) + "|" + msg;
    std::lock_guard<std::mutex> lock(g_log_mutex);

    auto it = g_log_map.find(key);
    if (it != g_log_map.end()) {
        int64_t elapsed = now_ms - it->second.last_ms;
        if (elapsed < static_cast<int64_t>(window_seconds) * 1000) return;
        it->second.last_ms = now_ms;
    } else {
        g_log_map[key] = {now_ms};
    }

    __android_log_print(ANDROID_LOG_DEBUG, tag, "%s", msg);
}

}  // namespace guard

#else  // !GUARD_DEV_LOG — production: completely silent

namespace guard {
    void log_limited(const char*, const char*, int) {}
}

#endif  // GUARD_DEV_LOG
