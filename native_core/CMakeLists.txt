cmake_minimum_required(VERSION 3.22.1)
project(guardcore VERSION 1.0.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Strip debug symbols in release to reduce SO size and limit reverse-engineering surface
set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -fvisibility=hidden -ffunction-sections -fdata-sections")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--gc-sections")

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

# Android log for LogLimiter (release build LogLimiter is no-op via macro)
find_library(log-lib log)
target_link_libraries(guardcore ${log-lib})
