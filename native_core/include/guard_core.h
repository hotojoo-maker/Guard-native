#pragma once

#include <string>
#include <cstdint>
#include <cstddef>
#include <vector>
#include <utility>

// ─────────────────────────────────────────────────────────────
//  Guard Native — libguardcore.so  (Batch 1 / Phase 1 skeleton)
//  Role: decision center only. No hooks, no network, no UI.
//  Java hook layer calls us; we never call WeChat native code.
// ─────────────────────────────────────────────────────────────

namespace guard {

// ── Enums ─────────────────────────────────────────────────────

// Which process we're running in.  BLOCKED processes must not be hooked.
enum class ProcessRole : int {
    UNKNOWN  = 0,
    MAIN     = 1,   // com.tencent.mm
    PUSH     = 2,   // com.tencent.mm:push  (badge-guard only)
    BLOCKED  = 3,   // :sandboxed_process / :isolated_* / :appbrand*
};

// Privacy hiding state machine.
// Iron rule: HIDDEN → only exits via explicit unlock, never automatically.
enum class HiddenState : int {
    VISIBLE    = 0,   // secrets visible; filter disabled
    HIDDEN     = 1,   // secrets hidden; filter active
    UNLOCKING  = 2,   // password entry in progress; filter still active
    LOCKED     = 3,   // force-hidden; kill_switch or admin lock
    SAFE_MODE  = 4,   // tamper/piracy detected; all features off
    EXPIRED    = 5,   // license expired; features degraded
};

// Authorization state.  Batch 1: always AUTH_OK (placeholder).
// Batch 2: real LicenseBox + miyou-server check.
enum class AuthState : int {
    UNKNOWN     = 0,
    AUTH_OK     = 1,
    EXPIRED     = 2,
    TAMPERED    = 3,   // piracy / package hash mismatch
    NO_LICENSE  = 4,
};

// Risk / anti-tamper state.
enum class RiskState : int {
    NONE               = 0,
    PACKAGE_MISMATCH   = 1,
    CONFIG_TAMPERED    = 2,
    GRACE_EXPIRED      = 3,   // offline > grace_days
    PIRATE             = 4,
};

// ── Config constants ──────────────────────────────────────────

constexpr int     CONFIG_VERSION        = 1;
constexpr int     GRACE_WARN_HOURS      = 24;   // silent sync window
constexpr int     GRACE_DEGRADE_HOURS   = 72;   // start degrading
constexpr int     GRACE_LOCKOUT_DAYS    = 10;   // full lockout

// 期望宿主包名 — 打包时注入（D-015 / V3 共存版）。
// 官替版（劫持 com.tencent.mm）默认即此值；共存版改包名时由构建链注入新包名，
// 让共存版不被 anti_tamper 误判为重打包盗版。来源 = 构建注入，不硬编码。
#ifndef GUARD_EXPECTED_PACKAGE
#define GUARD_EXPECTED_PACKAGE "com.tencent.mm"
#endif
constexpr char    EXPECTED_PACKAGE[]    = GUARD_EXPECTED_PACKAGE;
constexpr char    PROCESS_MAIN[]        = "com.tencent.mm";
constexpr char    PROCESS_PUSH[]        = "com.tencent.mm:push";

// ── Module: ProcessRouter ─────────────────────────────────────

/// 读 /proc/self/cmdline 判断进程角色；BLOCKED 进程所有接口返回安全默认值
ProcessRole router_detect(const std::string& process_name);

// ── Module: StateMachine ──────────────────────────────────────

/// 初始化状态机；冷启动默认 HIDDEN，不管上次存的状态
void        sm_init(HiddenState restore_from_mmkv);

/// 原子读当前状态，热路径安全
HiddenState sm_get_state();

/// true = HIDDEN / LOCKED / UNLOCKING，id 过滤应生效（Java hook 短路判断用）
bool        sm_is_hiding_active();

/// B 模块触发（切后台/锁屏/摇一摇）→ HIDDEN
void        sm_enter_hidden();

/// 密码解锁成功 → VISIBLE；LOCKED / SAFE_MODE 下无效
void        sm_enter_visible();

/// 用户开始输密码 → UNLOCKING；仍按隐藏处理，防止解锁过程中泄漏
void        sm_enter_unlocking();

/// kill_switch 触发 → LOCKED；只能重启后重新授权才能恢复
void        sm_enter_locked();

/// 包名/配置异常检测 → SAFE_MODE；功能全部静默失效
void        sm_enter_safe_mode();

// ── Module: WxidMatcher ───────────────────────────────────────

/// 初始化；Batch 1 预埋测试 wxid，Phase 3 改为从 MMKV 加载
void  matcher_init();

/// 判断 wxid 是否密友（个人账号），O(1) 哈希查找
bool  matcher_is_hidden_wxid(const std::string& wxid);

/// 判断 groupId 是否密群（以 @chatroom 结尾），O(1) 哈希查找
bool  matcher_is_hidden_group(const std::string& group_id);

/// Java 层添加密友后调（Bridge.addWxid 之后）
void  matcher_add_wxid(const std::string& wxid);

/// Java 层删除密友后调
void  matcher_remove_wxid(const std::string& wxid);

/// Java 层添加密群后调
void  matcher_add_group(const std::string& group_id);

/// Java 层删除密群后调
void  matcher_remove_group(const std::string& group_id);

/// 退出授权/清空名单时调用
void  matcher_clear();

// ── Module: PushGuard ─────────────────────────────────────────

/// :push 进程专用：是否拦截这个 wxid 的 badge/unread 写入；未隐藏时直接 false（短路）
bool  push_should_block_badge(const std::string& wxid);

/// :push 进程守护是否激活（role==PUSH && 隐藏中）
bool  push_is_guard_active();

// ── Module: AntiTamper ────────────────────────────────────────

/// Batch 1：包名 + config_version 轻量校验；异常返回 RISK_* 枚举，触发 SAFE_MODE
/// Batch 3 TODO: device_hash / customer_seed / package_hash / 蜜罐字段
RiskState tamper_check(const std::string& package_name, int config_version);

// ── Module: ConfigCrypto ──────────────────────────────────────

struct ConfigDecryptResult {
    bool ok;                 ///< true only when AES-GCM tag verifies
    std::string plaintext;   ///< registry JSON on success; empty scatter result on failure
};

/// Phase 1A local prototype only: AES-128-GCM decrypt for encrypted registry blobs.
/// key/nonce/tag are test-vector inputs in this phase, not上线授权 key material.
ConfigDecryptResult decrypt_config(const uint8_t* key,
                                   size_t key_len,
                                   const uint8_t* nonce,
                                   size_t nonce_len,
                                   const uint8_t* ciphertext,
                                   size_t ciphertext_len,
                                   const uint8_t* tag,
                                   size_t tag_len);

/// Runs fixed AES-GCM vectors and tamper failures. Does not touch business hooks.
bool decrypt_config_self_test();

/// Phase 1D-local A-step2: set the runtime binding material (the module's own
/// signing-cert SHA-256, read by Java via PackageManager). Must be called before
/// loading the embedded registry. Folded into derive_registry_key() so a
/// re-signed / repackaged APK derives a wrong key → scatter. Passing null/0
/// clears it (key reverts to the unbound A-step1 derivation → also wrong key).
void set_binding_material(const uint8_t* data, size_t len);

/// Phase 1D-server (S3a): unwrap the envelope's k field into the runtime server
/// seed S_rel. k = ct(32)||tag(16) of AES-128-GCM(S_rel, key=W[:16], nonce=n[:12]).
/// Must be called BEFORE the embedded registry is decrypted. Returns false and
/// clears the seed (→ scatter) on any failure. No valid envelope → no server seed
/// → registry scatters = real lock (registry only opens after a real server reply).
bool unwrap_server_seed(const uint8_t* k, size_t k_len,
                        const uint8_t* nonce, size_t nonce_len);

/// Clear the runtime server seed (logout / lease expired → registry scatters).
void clear_server_seed();

/// Phase 1D-local: derive the registry AES key from scattered in-SO segments +
/// a light non-linear transform + the binding material (A-step2), so no single
/// 16-byte key constant is visible in the binary AND the key is bound to the
/// module signing cert. tools/gen_registry_cipher.py MUST mirror this exact
/// routine (with the baked cert hash as binding), otherwise the embedded
/// ciphertext won't decrypt (→ scatter).
/// [GUARD-TRAP] Do not replace with a literal key array. See PROTECTION_MAP §4.
void derive_registry_key(uint8_t out[16]);

/// Java smoke-test helper: returns a test registry after an in-native AES-GCM roundtrip.
std::string decrypt_config_test_registry();

// ── Module: ConfigRegistry (Phase 1B) ─────────────────────────
//
// Plaintext hook-config registry prototype. Parses a registry JSON into
// structured entries. Phase 1B scope: parse + surface for verification only.
// It does NOT take over any business filtering (ConvFilter etc. untouched).
// Parse failure → scatter registry (ok=false, no entries), never throws.

struct RegistryEntry {
    std::string id;  ///< entry key, e.g. "conv.list"
    std::vector<std::pair<std::string, std::string>> fields;  ///< recipe fields
};

struct ConfigRegistry {
    bool ok;                   ///< false → scatter (parse failure / malformed)
    std::string schema_id;     ///< "scatter" on failure
    std::string wechat_version;
    std::vector<RegistryEntry> entries;
};

/// Parse a registry JSON string. Objects + strings only; any malformed
/// input → scatter. No exceptions (built with -fno-exceptions).
ConfigRegistry registry_parse(const std::string& json);

/// Load the SO-embedded plaintext registry (mirror of registry_8071.json).
ConfigRegistry registry_load_embedded();

/// One-line human-readable summary of the embedded registry (for verify log).
std::string registry_dump_summary();

/// Parses embedded registry + a malformed input; verifies conv.list anchors
/// match ConvFilter.java and that malformed input scatters. No business hooks.
bool registry_self_test();

/// Phase 1E Step1: single recipe lookup for the SO→Java channel.
/// Returns entries[gateway].fields[key]; fail-closed to "" on scatter /
/// unknown gateway / unknown field. Pure read — does NOT drive any Filter.
std::string registry_get_recipe(const std::string& gateway,
                                const std::string& key);

// ── Module: LogLimiter ────────────────────────────────────────

/// 限流日志：每个 tag+msg 每 window_seconds 最多输出一次；Release 构建完全 no-op
void log_limited(const char* tag, const char* msg, int window_seconds = 10);

// ── Global init ───────────────────────────────────────────────

struct InitResult {
    bool        ok;    ///< false = SAFE_MODE，调用方不注册业务 hook
    ProcessRole role;  ///< 当前进程角色
    RiskState   risk;  ///< 检测到的风险类型（ok=false 时有意义）
};

/// 顶层初始化：ProcessRouter → AntiTamper → StateMachine → WxidMatcher
InitResult core_init(const std::string& process_name,
                     const std::string& package_name);

}  // namespace guard
