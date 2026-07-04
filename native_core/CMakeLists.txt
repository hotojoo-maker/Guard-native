cmake_minimum_required(VERSION 3.22.1)
project(guardcore VERSION 1.0.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 期望宿主包名（D-015 / V3 共存版）：打包时由 -DGUARD_WX_PKG 注入。
# 官替版默认 com.tencent.mm；共存版构建传新包名。anti_tamper 据此判重打包。
if(NOT DEFINED GUARD_WX_PKG)
    set(GUARD_WX_PKG "com.tencent.mm")
endif()

# Strip debug symbols in release to reduce SO size and limit reverse-engineering surface
set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -fvisibility=hidden -ffunction-sections -fdata-sections")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--gc-sections")

# 16KB页对齐(Android15/16): NDK r25默认4KB→16KB页设备dlopen崩;16KB向下兼容4KB(11-14),一份SO通吃;升NDK r27+后可删本行(默认即16KB)。装机实证:0x4000对齐+4KB机不回归(MI9 L1)。
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")

# Disable RTTI and exceptions — not needed, reduces binary size
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fno-rtti -fno-exceptions")

add_library(
    guardcore
    SHARED
    src/guard_core.cpp
    src/core_init.cpp
    src/process_router.cpp
    src/state_machine.cpp
    src/wxid_matcher.cpp
    src/push_guard.cpp
    src/anti_tamper.cpp
    src/log_limiter.cpp
    src/config_crypto.cpp
    src/registry_loader.cpp
)

target_include_directories(guardcore PRIVATE include)

# 把期望包名灌成编译期宏，guard_core.h 的 EXPECTED_PACKAGE 取它。
target_compile_definitions(guardcore PRIVATE GUARD_EXPECTED_PACKAGE="${GUARD_WX_PKG}")

# 一套配方（2026-06-30「同一套配方」决策）：官替+共存共用同一把 cert e3e13a49 + 同一份
# registry_cipher.inc / bootstrap_cipher.inc。不再按包名分 per-flavor registry（GUARD_REGISTRY_COEXIST
# 已退役）；共存仅包名(GUARD_WX_PKG=com.tencent.mn)与发行线(GUARD_RELEASE_ID)不同，配方同源。

# DEBUG-only hooks: logging + KDF self-test. Defined ONLY when CMAKE_BUILD_TYPE
# is Debug (AGP: debug→Debug, release→RelWithDebInfo). Never use -DGUARD_DEBUG
# via Gradle cppFlags — the old defaultConfig.buildTypes block was ineffective
# and leaked into release (2026-06-25 fixed).
if(CMAKE_BUILD_TYPE STREQUAL "Debug")
    target_compile_definitions(guardcore PRIVATE GUARD_DEV_SELFTEST GUARD_DEV_LOG)
endif()

# Android log for LogLimiter (release build LogLimiter is no-op via macro)
find_library(log-lib log)
target_link_libraries(guardcore ${log-lib})
