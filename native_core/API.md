# API — libguardcore.so 接口说明

> 状态：🟡 Batch 1 骨架编译通过，接口占位  
> Java 侧：`NativeBridge.java`（`src/main/java/com/ghost/assist/core/`）  
> 铁律：nativeInit 必须在所有业务 hook 之前完成（CLAUDE.md 铁律 27）

---

## 一、接口速查表

| 接口 | 进程 | 批次 | 一句话 |
|------|:----:|:----:|--------|
| `nativeInit` | 主 + push | B1 | 初始化整个 SO，必须第一个调 |
| `nativeGetProcessRole` | 主 + push | B1 | 查当前是主进程还是 push 进程 |
| `nativeIsAuthorized` | 主 | B1 占位 | 有没有授权（B1 永远返回 true）|
| `nativeGetAuthState` | 主 | B1 占位 | 返回授权状态枚举 |
| `nativeIsHidden` | 主 + push | B1 | 当前是否处于隐藏状态 |
| `nativeSetHidden` | 主 | B1 | 切换隐藏/显形 |
| `nativeEnterHidden` | 主 | B1 | 进入 HIDDEN（B 模块触发时调）|
| `nativeEnterVisible` | 主 | B1 | 进入 VISIBLE（密码解锁时调）|
| `nativeEnterSafeMode` | 主 | B1 | 进入安全模式 |
| `nativeIsHiddenWxid` | 主 + push | B1 | 这个 wxid 是不是密友 |
| `nativeIsHiddenGroup` | 主 + push | B1 | 这个 groupId 是不是密群 |
| `nativeShouldBlockBadge` | push | B1 | :push 进程要不要拦这个 wxid 的未读 |
| `nativeGetConfigVersion` | 主 | B1 | 当前 SO 的配置版本号 |
| `nativeGetRiskState` | 主 | B1 | 当前风险状态（有没有异常）|

---

## 二、接口详细说明

---

### nativeInit(processName, packageName)

**中文用途**：初始化 libguardcore.so。按顺序做：进程判断 → 包名校验 → 状态机初始化 → 密友名单加载。

**Java 什么时候调用**：`ModuleMain.handleLoadPackage()` 的最开始，在注册任何 hook 之前。

**返回 true**：初始化成功，功能可用。  
**返回 false**：包名异常或严重错误，SO 进入 SAFE_MODE，所有接口返回安全默认值（不崩溃）。

```java
// 正确调用示例（Phase 3 接入后）
boolean ok = NativeBridge.init(lpparam.processName, lpparam.packageName);
if (!ok) { /* 弹窗引导，不注册业务 hook */ }
```

---

### nativeGetProcessRole()

**中文用途**：查询当前进程角色。

**Java 什么时候调用**：init 之后，用于决定注册哪些 hook（主进程注册全部，push 进程只注册 badge 拦截）。

**返回值**：
- `ROLE_MAIN (1)`：主进程，全部功能可用
- `ROLE_PUSH (2)`：push 进程，只允许 badge/unread 最小守护
- `ROLE_BLOCKED (3)`：沙箱/小程序进程，立即退出，不做任何 hook
- `ROLE_UNKNOWN (0)`：未知，安全默认值，不做任何 hook

---

### nativeIsAuthorized()

**中文用途**：判断当前是否有有效授权。

**Java 什么时候调用**：每次 hook 回调里的授权门控检查（`shouldHide()` 公式第一步）。

**返回 true**：有授权，可以继续判断隐藏状态。  
**返回 false**：无授权，所有密友/密群功能不可用，直接透传微信原始行为。

> ⬜ **Batch 1 占位**：永远返回 `true`。  
> TODO Batch 2：接入 LicenseBox（HMAC-SHA256 + AES-GCM signed_config 离线校验）。

---

### nativeGetAuthState()

**中文用途**：返回授权状态的详细枚举，供 Java 层决定显示哪种弹窗。

**Java 什么时候调用**：App 启动后检查授权，或服务器同步后刷新状态。

**返回值**：
- `AUTH_OK (1)`：正常
- `AUTH_EXPIRED (2)`：过期，弹续费提示
- `AUTH_TAMPERED (3)`：破解/篡改检测，弹引流弹窗
- `AUTH_NO_LICENSE (4)`：无 license，引导购买
- `AUTH_UNKNOWN (0)`：初始化未完成

> ⬜ **Batch 1 占位**：永远返回 `AUTH_OK (1)`。

---

### nativeIsHidden()

**中文用途**：查询当前是否处于"隐藏激活"状态（密友/密群 id 过滤是否生效）。

**Java 什么时候调用**：每个 hook 回调的第一步短路判断，频繁调用（热路径）。

**返回 true**：当前是 HIDDEN / LOCKED / UNLOCKING，id 过滤应该生效。  
**返回 false**：VISIBLE / SAFE_MODE / EXPIRED / 未初始化，不过滤，完全透传微信。

```java
// hook 模板（铁律 27 短路写法）
protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
    if (!NativeBridge.isHidden()) return; // 短路，不隐藏时零开销
    // 继续判断具体 wxid ...
}
```

---

### nativeSetHidden(boolean hidden)

**中文用途**：切换隐藏/显形状态的快捷方法。

**Java 什么时候调用**：
- `hidden = true`：B 模块触发事件（切后台、锁屏、摇一摇）
- `hidden = false`：B6 密码解锁成功

**无返回值**。LOCKED 和 SAFE_MODE 状态下调用此方法无效（状态不会被覆盖）。

---

### nativeEnterHidden()

**中文用途**：直接进入 HIDDEN 状态（等同于 `setHidden(true)` 的语义更清晰版本）。

**Java 什么时候调用**：B 模块任何触发事件（切后台、锁屏、摇一摇、返回键）。

**无返回值**。

---

### nativeEnterVisible()

**中文用途**：解锁进入 VISIBLE 状态。

**Java 什么时候调用**：`SearchUnlock.java` 检测到密码 `111111` 输入正确后调用。

**无返回值**。LOCKED / SAFE_MODE 下调用无效。

---

### nativeEnterSafeMode()

**中文用途**：强制进入安全模式，所有功能关闭。

**Java 什么时候调用**：主动检测到异常（包名不对、kill_switch 触发、服务端下发降级指令）。

**无返回值**。安全模式是终态，只有重启 + 正常授权才能恢复。

---

### nativeIsHiddenWxid(String wxid)

**中文用途**：判断这个 wxid 是不是密友（在隐藏名单里）。

**Java 什么时候调用**：所有 hook 里过滤具体条目时，在 `nativeIsHidden()` 返回 true 之后调用。

**返回 true**：wxid 在密友名单，应该隐藏这条数据。  
**返回 false**：不在名单，正常显示。

> 🟡 **Batch 1**：预埋测试数据 `wxid_lzd2va16jd1622 = true`，其余全部返回 false。

---

### nativeIsHiddenGroup(String groupId)

**中文用途**：判断这个 groupId 是不是密群（在隐藏群名单里）。

**Java 什么时候调用**：会话/通讯录/搜索 hook 里，groupId 以 `@chatroom` 结尾的条目。

**返回 true**：groupId 在密群名单，应该隐藏。  
**返回 false**：不在名单，正常显示。

> ⬜ **Batch 1**：密群名单为空，始终返回 false。Phase 3 从 MMKV 加载。

---

### nativeShouldBlockBadge(String wxid)

**中文用途**：判断 `:push` 进程里是否应该拦截这个 wxid 的未读/红点写入。

**Java 什么时候调用**：`:push` 进程收到消息/朋友圈互动，准备写 badge/unread 之前。

**返回 true**：拦截，不让这条未读写进微信 badge 池。  
**返回 false**：不拦截（包括：未隐藏、wxid 不在名单、未初始化）。

内部逻辑：
```
nativeIsHidden() == false → 直接 false（短路，不做额外判断）
nativeIsHidden() == true  → 再查 isHiddenWxid(wxid)
```

---

### nativeGetConfigVersion()

**中文用途**：返回当前 SO 内嵌的配置版本号（整数）。

**Java 什么时候调用**：启动时对比 MMKV 里缓存的 signed_config 版本，版本不一致时触发重新加载。

**返回值**：当前为 `1`（Batch 1）。每次 signed_config 结构有变化时递增。

---

### nativeGetRiskState()

**中文用途**：返回当前风险/异常状态，供 Java 层决定显示哪种弹窗、是否进行引流。

**Java 什么时候调用**：启动后读取，用于决定是否弹"异常提示"弹窗。

**返回值**：
- `RISK_NONE (0)`：正常
- `RISK_PACKAGE_MISMATCH (1)`：包名异常（可能是重打包）
- `RISK_CONFIG_TAMPERED (2)`：配置版本不匹配
- `RISK_GRACE_EXPIRED (3)`：离线超过 grace_days（Batch 2 接入后生效）
- `RISK_PIRATE (4)`：破解检测（Batch 3 接入后生效）

---

## 三、C++ 模块内部函数说明

### ProcessRouter

```cpp
ProcessRole router_detect(process_name)
// 读 /proc/self/cmdline，返回进程角色枚举
```

### StateMachine

```cpp
sm_init(restore_from_mmkv)
// 初始化状态机，冷启动默认 HIDDEN

sm_get_state()           // 读当前状态（原子读，热路径安全）
sm_is_hiding_active()    // true = HIDDEN / LOCKED / UNLOCKING（id 过滤应生效）

sm_enter_hidden()        // B 模块触发 → HIDDEN
sm_enter_visible()       // 密码解锁 → VISIBLE
sm_enter_unlocking()     // 开始输密码 → UNLOCKING（仍按隐藏处理）
sm_enter_locked()        // kill_switch → LOCKED（无法正常解锁）
sm_enter_safe_mode()     // 异常检测 → SAFE_MODE（功能全关）
```

### WxidMatcher

```cpp
matcher_init()                     // 初始化，Batch 1 预埋测试 wxid
matcher_is_hidden_wxid(wxid)       // 查密友 set，O(1)
matcher_is_hidden_group(group_id)  // 查密群 set，O(1)
matcher_add_wxid(wxid)             // Java 层添加密友后调（Phase 3）
matcher_remove_wxid(wxid)          // Java 层删除密友后调（Phase 3）
matcher_add_group(group_id)        // Java 层添加密群后调（Phase 3）
matcher_remove_group(group_id)     // Java 层删除密群后调（Phase 3）
matcher_clear()                    // 清空名单（退出授权时调）
```

### PushGuard

```cpp
push_should_block_badge(wxid)  // :push 进程专用，判断是否拦截 badge 写入
push_is_guard_active()         // :push 进程守护是否激活（role==PUSH && 隐藏中）
```

### AntiTamper

```cpp
tamper_check(package_name, config_version)
// Batch 1：校验包名 + 版本号，异常返回 RISK_* 枚举
// Batch 3 TODO：device_hash / customer_seed / 蜜罐字段
```

### LogLimiter

```cpp
log_limited(tag, msg, window_seconds)
// Debug 构建：rate-limited __android_log_print
// Release 构建：完全 no-op（零开销）
```

---

## 四、Java 侧常量（NativeBridge.java）

```java
// 进程角色
ROLE_UNKNOWN = 0 / ROLE_MAIN = 1 / ROLE_PUSH = 2 / ROLE_BLOCKED = 3

// 授权状态
AUTH_UNKNOWN = 0 / AUTH_OK = 1 / AUTH_EXPIRED = 2 / AUTH_TAMPERED = 3 / AUTH_NO_LICENSE = 4

// 隐藏状态
STATE_VISIBLE = 0 / STATE_HIDDEN = 1 / STATE_UNLOCKING = 2
STATE_LOCKED = 3 / STATE_SAFE_MODE = 4 / STATE_EXPIRED = 5

// 风险状态
RISK_NONE = 0 / RISK_PACKAGE_MISMATCH = 1 / RISK_CONFIG_TAMPERED = 2
RISK_GRACE_EXPIRED = 3 / RISK_PIRATE = 4

// 离线宽限期（小时/天）
GRACE_WARN_HOURS = 24      // 开始弹提醒（可关闭）
GRACE_DEGRADE_HOURS = 72   // 功能降级（弹窗不可关闭）
GRACE_LOCKOUT_DAYS = 10    // 完全锁死
```
