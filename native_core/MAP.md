# MAP — libguardcore.so 模块地图

> 状态：🟡 Batch 1 骨架已编译，接口占位，待 Phase 3 装机验证  
> 原则：C++ 只负责**判断**，Java 负责**执行 hook**，:push 只做**最小守护**

---

## 一句话定位

```
libguardcore.so = 判断中心
Java hook 层    = 执行层（XposedHelpers，不归 SO 管）
:push 进程      = 最小守护（只拦 badge/unread 写入）
```

---

## 模块一览

| 模块 | 文件 | 一句话 | 批次 |
|------|------|--------|:----:|
| **ProcessRouter** | `process_router.cpp` | 判断当前跑在哪个进程 | Batch 1 |
| **StateMachine** | `state_machine.cpp` | 管理隐藏/显形/安全模式六个状态 | Batch 1 |
| **WxidMatcher** | `wxid_matcher.cpp` | 判断 wxid 或群 groupId 是不是密友/密群 | Batch 1 |
| **PushGuard** | `push_guard.cpp` | 后台 :push 进程收到消息时，要不要拦红点/未读 | Batch 1 |
| **AntiTamper** | `anti_tamper.cpp` | 轻量检查包名和配置版本，发现异常进安全模式 | Batch 1 |
| **LogLimiter** | `log_limiter.cpp` | 限流日志，防止 logcat 暴露太多信息，Release 完全静默 | Batch 1 |
| **LicenseBox** | *(未实现)* | HMAC-SHA256 + AES-GCM 授权校验 | Batch 2 |
| **AuthGate** | *(未实现)* | 完整授权状态机（NO_AUTH / AUTHORIZED / EXPIRED）| Batch 2 |
| **NotifyPolicy** | *(未实现)* | 密友消息静默/震动/特殊提示音策略判断 | Batch 2 |
| **FeatureGate** | *(未实现)* | 基础功能开关（虚拟定位/步数/余额显示）| Batch 3 |
| **AntiTamper v2** | *(未实现)* | 完整反破解（device_hash + customer_seed + 蜜罐）| Batch 3 |

---

## ProcessRouter — 判断当前进程

**干什么**：读 `/proc/self/cmdline`，告诉其他模块"我们现在跑在哪个进程里"。

```
com.tencent.mm        → MAIN   主进程，全部功能可用
com.tencent.mm:push   → PUSH   只允许 badge/unread 最小守护
:sandboxed_process    → BLOCKED 立即退出，什么都不做
:isolated_*           → BLOCKED 同上
:appbrand*            → BLOCKED 同上
其他                  → UNKNOWN 安全默认值，什么都不做
```

**为什么不调 ActivityManager**：沙箱进程无权限，会 FATAL 崩溃（FAILURE_LOG F 铁律 #7）。

---

## StateMachine — 隐藏/显形/安全模式

**干什么**：保存当前隐私状态，管理状态流转。所有判断"现在要不要藏密友"都先问它。

```
VISIBLE    → 密友/密群可见，id 过滤不生效
HIDDEN     → 密友/密群隐藏，id 过滤生效（主工作状态）
UNLOCKING  → 用户正在输密码，仍按隐藏处理
LOCKED     → 强制隐藏（kill_switch 触发），无法正常解锁
SAFE_MODE  → 检测到异常/破解，所有功能关闭，只剩引流
EXPIRED    → 授权过期（Batch 2 接入后生效）
```

**产品铁律**（来自功能表 §4.3）：
- 切后台 → `sm_enter_hidden()`
- 回来后仍然 HIDDEN — **不自动变 VISIBLE**
- 只有用户主动输密码 → `sm_enter_visible()`

**默认状态**：冷启动默认 `HIDDEN`，不管上次存的是什么。

---

## WxidMatcher — 密友/密群判断

**干什么**：维护两个哈希集合，O(1) 查询一个 wxid 或 groupId 是不是需要隐藏的对象。

```
hidden_wxids    → 个人密友，例如 wxid_lzd2va16jd1622
hidden_groups   → 密群，groupId 以 @chatroom 结尾
```

**Batch 1**：预埋测试数据 `wxid_lzd2va16jd1622 = true`，用于验收测试。

**Phase 3**：从 MMKV 加载真实名单（Bridge.getWxids() / Bridge.getGroupIds()）。

---

## PushGuard — 后台红点/未读最小守护

**干什么**：专门给 `:push` 进程用的判断接口。微信 APP 被划掉后，`:push` 进程仍然在后台收消息。如果不处理，密友消息会写进 badge/unread，用户解锁后就看到了。

```
判断流程：
  1. nativeIsHidden() == false → 直接放行（短路，减少性能消耗）
  2. nativeIsHidden() == true + wxid 在密友名单 → 返回 true（拦截写入）
```

**:push 进程只能做**：读状态 + 判断 wxid + 拦截 badge 写入 + 少量限流日志  
**:push 进程不能做**：UI / ActivityManager / 网络 / 复杂反射（铁律 6）

---

## AntiTamper — 包名/配置版本轻量检查

**干什么**：启动时做最轻量的合法性检查。发现异常就让状态机进 SAFE_MODE，功能全部静默失效。

```
Batch 1 检查项：
  ① 包名是否 com.tencent.mm
  ② config_version 是否匹配当前 SO 版本
```

**异常后的表现**（SAFE_MODE）：
- 功能静默失效（不崩溃）
- Java 层弹窗引导授权/客服（弹窗逻辑在 Java 层，SO 只返回 RISK 状态）

**不是攻击逻辑**：只做"授权异常降级"，不主动破坏任何数据。

```
Batch 3 待做：
  device_hash 绑定 / customer_seed / package_hash / 蜜罐字段
```

---

## LogLimiter — 日志限流

**干什么**：每个 tag+msg 组合，每 N 秒最多输出一条日志。防止 logcat 密集输出暴露模块存在。

```
Debug 构建 (-DGUARD_DEBUG)：有日志输出（带限流）
Release 构建：                完全静默，零输出，零开销
```

**:push 进程**：更严格，窗口期更长（30 秒），减少后台进程 logcat 暴露面。

---

## 文件结构速查

```
native_core/
├── CMakeLists.txt        构建配置（arm64-v8a + armeabi-v7a）
├── include/
│   └── guard_core.h      所有模块接口声明 + 枚举定义
├── src/
│   ├── guard_core.cpp    JNI 入口（Java 调 C++ 的门）
│   ├── core_init.cpp     启动协调（按顺序初始化各模块）
│   ├── process_router.cpp
│   ├── state_machine.cpp
│   ├── wxid_matcher.cpp
│   ├── push_guard.cpp
│   ├── anti_tamper.cpp
│   └── log_limiter.cpp
├── MAP.md                本文件（模块地图）
├── API.md                所有接口说明（中文）
├── ARCHITECTURE.md       架构设计
├── RULES.md              开发铁律
└── ROADMAP.md            Phase 0–6 计划
```
