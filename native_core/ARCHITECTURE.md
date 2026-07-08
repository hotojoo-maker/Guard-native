# ARCHITECTURE — libguardcore.so 架构设计

> 状态：⬜ Phase 0 规划文档  
> 关联：`RULES.md` / `API.md` / `ROADMAP.md`

---

## 一、模块总图

```
┌─────────────────────────────────────────────────────────┐
│                   libguardcore.so                        │
│                                                          │
│  JniBridge ──→ ProcessRouter ──→ 判断角色分发            │
│      │                                                   │
│      ├──→ [主进程角色]                                    │
│      │       StateMachine    hidden/visible/locked       │
│      │       WxidMatcher     wxid / groupId set         │
│      │       LicenseBox      HMAC / AES-GCM / 授权      │
│      │       ConfigStore     配置版本 / 开关             │
│      │       NotifyPolicy    通知策略判断                 │
│      │       StateBridge     写共享状态 → :push 读        │
│      │       LogLimiter      限流日志                     │
│      │                                                   │
│      └──→ [:push 角色]                                    │
│              StateBridge     只读共享状态                 │
│              WxidMatcher     只读 hidden wxid            │
│              LogLimiter      限流日志（极少量）           │
│                                                          │
└─────────────────────────────────────────────────────────┘

Java hook 层（XposedHelpers）只通过 NativeBridge.java 调用上层接口
微信 native 层                   ← 永远不接触
```

---

## 二、进程角色（ProcessRouter）

| 角色常量 | 进程名 | 允许的 SO 职责 |
|---------|--------|--------------|
| `ROLE_MAIN` | `com.tencent.mm` | 全部模块 |
| `ROLE_PUSH` | `com.tencent.mm:push` | StateBridge（只读）+ WxidMatcher + LogLimiter |
| `ROLE_UNKNOWN` | 其他 | nativeInit 立即返回，不做任何操作 |

判断方式：读 `/proc/self/cmdline`，纯 C++ 实现，不调 ActivityManager。

---

## 三、状态机设计（StateMachine）

### 3.1 状态枚举

```
VISIBLE      正常显示，密友/密群可见
HIDDEN       隐藏中，id 过滤全生效
LOCKED       强制隐藏（kill_switch 或授权过期）
UNLOCKING    用户正在输入密码
EXPIRED      授权过期，降级为只读
SAFE_MODE    异常降级（包名/哈希/蜜罐异常）
```

### 3.2 状态转移（仅合法转移）

```
任意状态  ──→  LOCKED     (kill_switch = true)
任意状态  ──→  SAFE_MODE  (蜜罐/包名/哈希异常)
VISIBLE   ──→  HIDDEN     (B 触发 / 启动恢复)
HIDDEN    ──→  UNLOCKING  (用户开始输密码)
UNLOCKING ──→  VISIBLE    (密码正确)
UNLOCKING ──→  HIDDEN     (密码错误 / 超时)
LOCKED    ──→  HIDDEN     (kill_switch 恢复，需重启)
EXPIRED   ──→  VISIBLE    (仅基础功能，密友功能不可用)
```

### 3.3 持久化

- 主进程：MMKV `g_<seed>` namespace，key 短哈希
- :push 进程：通过 StateBridge 读主进程写入的共享状态（只读）
- 冷启动顺序：`nativeInit()` → 任何业务 hook

---

## 四、StateBridge（跨进程状态）

### 4.1 目标

主进程写 HIDDEN 状态 + wxid 集合 → `:push` 进程读取，在 badge/unread 写入链判断是否拦截。

### 4.2 实现策略（⬜ Phase 4 再定）

候选方案：

| 方案 | 优点 | 缺点 |
|------|------|------|
| MMKV 多进程模式（`MULTI_PROCESS_MODE`） | 零 C++ 开发量，已有 Java MMKV | 粒度粗，每次读需 JNI 转 |
| mmap 共享内存文件 | 纯 C++，极快 | 需要 SELinux 权限评估 |
| Android Ashmem | 官方 IPC 方案 | API level 限制 |

**Phase 3 默认使用 MMKV 多进程**；Phase 4 若性能不足再换 mmap。

### 4.3 :push 进程只读约束

`:push` 进程内的 C++ 代码：
- ✅ 读 `is_hidden` 状态
- ✅ 读 `hidden_wxid_set`
- ✅ 判断 wxid 是否命中
- ❌ 写任何状态
- ❌ 调 UI 相关 API

---

## 五、WxidMatcher

```cpp
// 内部数据结构
std::unordered_set<std::string> hidden_wxids_;
std::unordered_set<std::string> hidden_group_ids_;

// 判断接口（O(1)）
bool IsHiddenWxid(const std::string& wxid);
bool IsHiddenGroup(const std::string& group_id);  // group_id ends with @chatroom
```

数据来源：主进程从 MMKV 加载，写入 StateBridge；:push 进程从 StateBridge 读取后填充本地 set。

---

## 六、LicenseBox（v2 实现，Phase 1 只留接口占位）

设计要点：
- 设备 seed：`device_hash`（ANDROID_ID + 硬件指纹，不用 IMEI）
- 时间加盐：UTC 天级 `yyyyMMdd`，防重放
- 签名：HMAC-SHA256（mbedTLS，不依赖系统 OpenSSL）
- 离线验证：embedded public key + AES-GCM 加密的 license blob
- 网络验证：v2 接入 miyou-server（Phase 5+ 再做）

**Phase 1 占位实现**：`nativeIsAuthorized()` 直接返回 `true`。

---

## 七、NotifyPolicy

根据用户配置判断密友消息的通知策略：

```
notify_mode:
  0  SILENT        默认，什么都不响
  1  VIBRATE       只震动
  2  SPECIAL_SOUND 特殊提示音（≠ 微信默认音）

show_secret_unread_count: bool
  true  → 解锁后/密友入口内显示未读数
  false → 完全不显示（默认）
```

规则：密友消息 / 密友来电 / 密友朋友圈互动，**全部走 SecretNotifyMode**，不污染微信原生 tab badge。

---

## 八、LogLimiter

- 限流：每个 tag 每 10 秒最多 1 条日志（防 logcat 暴露）
- 生产环境：`#ifdef GUARD_DEBUG` 控制，Release 构建全关
- :push 进程：更严格，每 tag 每 30 秒最多 1 条
