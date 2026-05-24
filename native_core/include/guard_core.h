#pragma once

#include <string>
#include <cstdint>

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
constexpr char    EXPECTED_PACKAGE[]    = "com.tencent.mm";
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
