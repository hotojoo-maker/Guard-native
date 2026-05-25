---
name: guard-auth-review_授权检查官
description: Guard Native 授权检查官（别名：授权执行官、授权门控、auth-gate）。大框架守门人，统管四层门控 Entry/Auth/State/Risk、状态机白名单、防盗版分支。改动 SearchUnlock/StateMachine/AuthManager/NativeBridge/DebugServer/C++ auth 前必须审查。
---

# guard-auth-review — 授权检查官（大框架守门人）

> **定位（用户口径）**
>
> 授权 + 防盗版 + 状态机分支复杂，**每个 AI 乱接 StateMachine 后面必乱**。
> 本 skill = **全项目门控架构的唯一权威**；总调度可规划方向，执行 AI 只调已有接口，**不得自创状态/授权捷径**。

> **核心原则（一句话）**
>
> 入口口令只开门，不解锁，不授权。
> 授权决定能不能用。状态决定现在隐藏还是显形。风险决定要不要静默失效。

---

## ★ 大框架守门职责（高于单次改码审查）

| 职责 | 说明 | 谁不能做 |
|------|------|----------|
| **架构权威** | 四层门控分工、Java/C++ 双 sm 边界、H/V 热切链路 — **以本 skill 为准** | 总调度/执行 AI 不得「顺手改架构」 |
| **接入审批** | 任何模块想读/写状态或授权 → 必须在 **白名单** 内 | Filter/Search/Notify 等 **禁止** 私自 `exitHidden` |
| **分支守门** | 防盗版（TAMPERED/PiracyNotice）、killSwitch、SAFE_MODE 分支不得被功能代码短路 | 执行 AI 不得 hardcode AUTH_OK 测完忘了删 |
| **改前 + 改后审查** | 保护区改动：改前 PASS/WARN + 改后再审一轮 | 质检通过 **不能** 替代本 skill |
| **框架合规预审** | 总调度派活前：若任务 **可能碰** 状态/授权 → 先输出「能否做 + 只能动哪几个接口」 | 总调度不得派「你去接一下状态机」类模糊任务 |

**与其他 skill 关系：**

```
总调度 ──规划──► 若碰门控 → 授权检查官【框架预审】──► 执行 AI（只调白名单接口）
执行 AI ──改码──► 授权检查官【改前审查】──► 改码 ──► 授权检查官【改后审查】
质检门控 ──铁律/KPI──► 与授权检查官并行，互不替代
终端 ──只验 log──► 不改门控代码
```

---

## ⛔ 绝对禁止 / ABSOLUTE PROHIBITIONS

> **这一节优先级高于本 skill 所有其他内容。**

| 中文 | English |
|------|---------|
| **功能 AI 不得擅自修改授权逻辑** | Feature AIs must NOT modify auth logic without this review |
| **入口口令不是授权码，不能绕过授权** | Entry passcode ≠ license; cannot bypass auth |
| **未授权时禁止添加密友、改密码、切显隐、开通知策略** | No friend add/pwd change/toggle/policy when not AUTH_OK |
| **授权异常时功能静默失效，不破坏数据** | Auth failure → silent no-op, never corrupt data |
| **不得混用 4 层门控（EntryGate ≠ AuthGate ≠ StateGate ≠ RiskGate）** | Never conflate the four gate layers |
| **不确定 → 停下来问用户，禁止猜测** | Uncertain → STOP, ask user |

---

## 零、四层门控架构（必须背会）

```
┌─────────────────────────────────────────────────────────┐
│  1. EntryGate  入口门                                    │
│     入口口令（默认 111111，用户可改）                              │
│     → 只负责打开设置页，不切状态，不授权                 │
├─────────────────────────────────────────────────────────┤
│  2. AuthGate  授权门                                     │
│     wxid + device + license                             │
│     → 决定能不能使用功能（添加密友/切显隐/改策略）        │
├─────────────────────────────────────────────────────────┤
│  3. StateGate  状态门                                    │
│     HIDDEN / VISIBLE / UNLOCKING                        │
│     → 决定当前是隐藏模式还是显形模式                    │
├─────────────────────────────────────────────────────────┤
│  4. RiskGate  风险门                                     │
│     packageHash / certHash / signature / configVersion  │
│     → 决定是否进入 SAFE_MODE，静默失效所有 hook          │
└─────────────────────────────────────────────────────────┘
```

**四层完全独立，不得互相替代：**
- `EntryGate` 通过 ≠ `AuthGate` 通过
- `StateGate` VISIBLE ≠ 授权通过
- `RiskGate` 失败 → 全链路静默，不看前三层

---

## 零.五、状态机 / 授权 — 唯一合法接入白名单（乱接 = BLOCK）

> **铁律**：除下表外，**任何文件不得** 调用 `StateMachine` 写操作、`RefreshBus` 广播、`NativeBridge.setAuthState`，或在 Filter 里用 C++ sm 替代 Java sm（铁律 30）。

### StateGate — 谁可以 **写** 状态（H/V/U）

| 模块 | 允许的操作 | 禁止 |
|------|-----------|------|
| `SettingsEntry.showGuardDialog()` 内按钮 | 调 `StateMachine.enterHidden/exitHidden/beginUnlock/...` | 对话框外任何路径切状态 |
| B 模块触发器（摇一摇/Home/锁屏等） | 调 StateMachine **已暴露**的触发 API | 新建触发器不经过审查 |
| `StateMachine` 自身 | 内部转换 + MMKV 持久化 | 内嵌 AuthGate 判断 |
| `NativeBridge` 双写桥（P2） | Java 态变更 → 同步 C++ sm | 主进程 Filter 读 C++ 态决策 |

### StateGate — 谁可以 **只读** 状态

| 模块 | 只读 API | 禁止 |
|------|---------|------|
| ConvFilter / MomentsFilter / ContactFilter / SearchFilter | `StateMachine.isActive()` | `NativeBridge.isHidden()` 替代 Java sm |
| `:push` PushFilter 等 | `NativeBridge.shouldBlockBadge()` | UI 操作 / 读微信 DB |
| DebugServer 读接口 | `getState()` 类只读 | 无 isAuthOk 的写接口 |

### EntryGate — 口令链（不得扩写）

```
SearchUnlock（口令命中）
  → SettingsEntry.unlockEntry() / showGuardDialog()   // 只开门
  → （用户点对话框按钮后才进 StateMachine）
```

| 模块 | 允许 | 禁止 |
|------|------|------|
| `SearchUnlock` | 检测口令 + 清空输入 + 调 SettingsEntry | **任何** StateMachine 写操作 |
| `SearchFilter` | 过滤 + `tryUnlockFromSearchResults` **先于** filter | 触发状态切换 |
| `SettingsEntry` | 入口可见性 + dialog 内按钮切状态 | 绕过 dialog 自动切 H/V |

### AuthGate — 谁可以写 AUTH_STATE

| 模块 | 允许 | 禁止 |
|------|------|------|
| `AuthManager.evaluate()` / `bindAccount()` | 写授权态 + 调 `NativeBridge.setAuthState` | — |
| `DebugServer /api/bind_account` | 建立绑定（唯一无门控写入口） | 其他写 API 绕过 isAuthOk |
| 功能 hook / Filter / UI | **只读** `AuthManager.isAuthOk()` 或现有门控 | 私自 setAuthState / hardcode AUTH_OK |

### RiskGate — 防盗版 / 总闸

| 模块 | 允许 | 禁止 |
|------|------|------|
| `anti_tamper.cpp` / `guard_core.cpp` | SAFE_MODE / killSwitch / 包签名校验 | C++ 操作 UI/Adapter/微信 DB |
| `PiracyNotice` | AUTH_TAMPERED 引流 | 正版用户误弹 |
| 功能 AI | 读 `isTampered()` 后果（静默） | 改 tamper 阈值「方便测试」 |

**乱接典型反例（见即 BLOCK）：**
- ❌ ConvFilter 里 `sm.exitHidden()`「方便刷新」
- ❌ SearchUnlock 口令命中直接 `enterVisible()`
- ❌ NotifyPolicy 改完顺手 `RefreshBus.notify...` 但不经 StateMachine
- ❌ 主进程 `NativeBridge.isHiddenWxid()` 做过滤决策
- ❌ DebugServer 新 API 无 `isAuthOk()` 门控
- ❌ 执行 AI「临时」`isVipAuthorized return true` 实装后未恢复 stub

---

## 一、强制触发条件（满足任意一条，改动前必须先审查）

> **"必须先审查"** = 在写任何代码前，先调用本 skill，输出 PASS/WARN/BLOCK 报告，用户确认后才能动代码。

### 两种审查模式

| 模式 | 谁触发 | 输出 | 何时用 |
|------|--------|------|--------|
| **框架合规预审** | 总调度派活前 | 「能碰吗 + 白名单接口 + 禁止项」≤10 行 | P 任务可能碰状态/授权/防盗版，但还没写码 |
| **改前/改后审查** | 执行 AI 改码前/后 | 完整 8 项报告 + 四门状态 | 已动或拟动保护区文件 |

改动涉及以下任意内容，**强制触发**：

| # | 内容 | 典型文件/符号 |
|---|------|-------------|
| 1 | 搜索框入口口令逻辑 | `SearchUnlock.java` / `111111` / `sm.getPassword()` |
| 2 | 状态机任意转换 | `StateMachine.java` / `HIDDEN` / `VISIBLE` / `UNLOCKING` |
| 3 | 状态变化广播 | `RefreshBus.java` / `notifyHiddenChanged` |
| 4 | 授权评估 / License | `AuthManager.java` / `LicenseGate.java` / `NativeBridge.java` |
| 5 | 授权判断方法 | `isVipAuthorized` / `isAuthorized` / `nativeIsAuthorized` / `isAuthOk` |
| 6 | 授权绑定数据 | `licensedWxid` / `deviceHash` / wxid + device 双绑定 |
| 7 | 门控架构 | `EntryGate` / `AuthGate` / `StateGate` / `RiskGate` / `TamperGate` |
| 8 | 设置界面权限按钮 | `SettingsEntry.showGuardDialog` / `DebugServer` 写操作接口 |
| 9 | C++ 授权 / 防破解 / 总闸 | `guard_core` / `anti_tamper` / `state_machine` / `process_router` / `killSwitch` / `SAFE_MODE` |
| 10 | 未授权用户操作能力 | 任何影响"未授权能做什么"的判断逻辑 |

**非保护区文件也适用**：只要改动结果影响上述任一内容，同样强制触发。

---

## 一.五、各角色权限边界（强制遵守）

> 总架构师可以设计方案，但**不能替代授权检查官批准**。

| 角色 | 允许 | 禁止 |
|------|------|------|
| **总架构师**（dispatch/设计） | 提出授权架构方案 | 在未经授权检查官审查的情况下直接实施授权改动 |
| **执行 AI**（execute-one） | 调用已有授权接口 | 私自修改 `isVipAuthorized` / `AuthManager` / `NativeBridge.setAuthState` / `LicenseGate` |
| **终端操作员**（terminal） | 验证 logcat 中的授权日志 | 修改任何授权相关代码 |
| **文档员**（doc/资料） | 记录已经审查通过的授权结论 | 把口述/推断升格为"已验证授权设计"写入文档 |
| **授权检查官**（本 skill） | 大框架守门 + 只读审查 + PASS/WARN/BLOCK + 白名单裁决 | 替用户决策；**非保护区也须拦截乱接 sm** |

---

## 二、框架合规预审格式（总调度派活前用，≤10 行）

总调度若派的任务 **可能碰** 状态机/授权/防盗版，**必须先** invoke 本 skill 输出预审，再交给执行 AI：

```
【框架合规预审】P<N>_xxx

门控涉及: Entry / Auth / State / Risk（圈出）
允许动: [白名单内的文件+方法，如 SettingsEntry 对话框按钮]
禁止动: [如 SearchUnlock 加 sm、Filter 读 C++ sm]
执行 AI 只能: [只读 isActive / 调已有 API / 不得新建状态捷径]
结论: 可派活 / 需收窄范围 / 禁止派活（原因）
```

**禁止派活的任务描述示例：**
- ❌「优化一下状态切换体验」（无白名单接口）
- ❌「Filter 里顺便刷新 hidden 态」（乱接 StateGate）
- ❌「SearchUnlock 命中后直接显示密友」（混淆 Entry + State）

---

## 三、保护区文件（Protected Zone）

> 改动任意保护区文件前，必须先运行本 skill 审查。

### Java 层
| 文件 | 保护原因 |
|------|---------|
| `core/AuthManager.java` | wxid + device 双绑定评估逻辑 |
| `core/NativeBridge.java` | AUTH_* 常量 + setAuthState / getAuthState |
| `core/StateMachine.java` | 状态转换逻辑 + isVipAuthorized stub |
| `core/RefreshBus.java` | 状态变化广播 → Filter 热刷新入口，不得绕过 |
| `core/Bridge.java` | licensedWxid / deviceHash / myWxid 读写 |
| `moduleB/SearchUnlock.java` | 入口口令语义（只开门，不授权，不切状态，entry passcode）|
| `moduleB/SettingsEntry.java` | showGuardDialog() 的状态切换/功能开关按钮 AUTH 门控 |
| `moduleB/TriggerGuard.java` | B1/B2/B5 触发器（StateGate 写入者），触发判定漏洞 = V→H 误触（2026-05-25 锁定） |
| `debug/DebugServer.java` | 所有 isAuthOk() 门控接口 |
| `core/LicenseGate.java` | （v2 待建）Ed25519 验签 |

### C++ 层
| 文件/目录 | 保护原因 |
|----------|---------|
| `native_core/src/guard_core.cpp` | SAFE_MODE / killSwitch 总闸 |
| `native_core/src/anti_tamper.cpp` | 包名/证书/签名反篡改 |
| `native_core/src/state_machine.cpp` | C++ 状态机（:push 进程权威源）|
| `native_core/src/process_router.cpp` | 进程角色判断 |
| `native_core/include/guard_core.h` | AUTH_STATE / RISK_STATE 常量定义 |

### 配置/密钥相关
任何包含以下关键词的文件：
`licensedWxid` `deviceHash` `customerSeed` `certHash` `packageHash`
`licenseKey` `Ed25519` `HMAC` `LicenseBox` `killSwitch` `AUTH_` `TAMPERED`

---

## 四、审查检查单

### 3.1 EntryGate 审查

```
□ SearchUnlock 密码命中后，是否只打开设置页（不切状态机）？
□ SearchUnlock 是否不调用 StateMachine.exitHidden / enterVisible / beginUnlock / attemptUnlock？
□ 入口口令存储在 StateMachine（sm.getPassword()），是否被误用为授权码？
□ 入口口令是否可被用户自由修改（不影响授权绑定）？
□ 入口口令修改 API（DebugServer）是否已加 isAuthOk() 门控？
□ 日志是否写 "[SU] entry passcode matched" / "[SU] open settings"，不写 unlock/VISIBLE？
```

### 3.2 AuthGate 审查

```
□ AuthManager.evaluate() 是否正确评估 wxid + deviceHash + licensedWxid？
□ 5 种状态是否全部处理：AUTH_OK / ACCOUNT_MISMATCH / DEVICE_MISMATCH / NO_LICENSE / TAMPERED？
□ AUTH_TAMPERED → PiracyNotice 引流弹窗 + 功能全关？
□ v1 放行逻辑（MISMATCH/NO_LICENSE 仍注册 hook）是否有明确注释，不误解为"已授权"？
□ isVipAuthorized() 是否仍是 stub（返回 true），而非接了真实 AuthGate？
  （v2 前不允许提前接，禁止用推断替代）
□ DebugServer 写操作是否全部加了 isAuthOk() 门控？
  覆盖：apiSetFeature / apiSetNotifyPolicy / apiTrigger(show/toggle/unlock)
        apiSetMode / apiHidden(POST) / apiSetMyWxid(POST)
□ /api/bind_account 是否正确不门控（这是建立授权的唯一入口）？
□ NativeBridge.setAuthState() 是否只由 AuthManager/bindAccount 调用，不被功能 hook 随意调用？
```

### 3.3 StateGate 审查

```
□ HIDDEN / VISIBLE / UNLOCKING 三态转换逻辑是否干净？
□ 状态机是否不包含任何授权判断（授权由 AuthGate 负责）？
□ StateMachine.isActive() 的三层门控（isVipAuthorized + isFeatureEnabled + mActive）是否正确？
□ 冷启动是否强制 HIDDEN（F-27 铁律）？
□ Java StateMachine 与 C++ sm 是否同步（P2 双写桥）？
□ 主进程是否只用 Java StateMachine，:push 进程是否只用 C++ NativeBridge.shouldBlockBadge()（铁律30）？
□ 状态切换（HIDDEN↔VISIBLE）是否只通过 B 模块触发器，不通过入口口令直接切换？
```

### 3.4 RiskGate 审查

```
□ anti_tamper.cpp 是否校验：包名 / 证书 hash / 客户 seed 完整性？
□ SAFE_MODE 进入条件是否明确？进入后是否禁止所有 hook 注册？
□ AUTH_TAMPERED 状态下是否触发 PiracyNotice 引流弹窗？
□ killSwitch 是否只能由 miyou-server 远程下发（v2），本地不可手动覆盖（v2 前 stub=false）？
□ RiskGate 失败是否静默失效（不崩溃、不弹 crash、不破坏微信数据）？
□ 蜜罐路径（如果有）是否只针对盗版包，不误伤正版用户？
```

### 3.5 Java/C++ 分层审查

```
□ C++ 是否只做：badge拦截 / 授权校验 / 反篡改 / 进程角色判断 / killSwitch？
□ C++ 是否没有操作：RecyclerView / MvvmList / notifyDataSetChanged / 微信 DB / UI？
□ C++ 是否没有 native hook 微信 SO（F-23 铁律）？
□ 主进程 Filter 链（ConvFilter/MomentsFilter/ContactFilter）是否只读 Java StateMachine.isActive()（铁律30）？
□ :push 进程是否只读 NativeBridge.shouldBlockBadge()（铁律30）？
□ NativeBridge 方法是否全部有 isAvailable() 前置检查？
```

---

## 五、审查报告格式（强制 8 项输出）

授权检查官每次审查必须输出以下全部内容，不得省略：

```
【授权检查官审查报告】

审查时间: YYYY-MM-DD HH:MM
触发原因: [P任务/发版/文档冲突/其他]
涉及文件: [被改动的保护区文件列表]

─────────────────────────────────────────
必答 8 项（每项必须给出 是/否 + 证据行）
─────────────────────────────────────────
1. 是否乱接状态机（SearchUnlock/SettingsEntry/DebugServer 私自切 H/V）
   → 是/否  证据: [代码行/方法名]

2. 是否混淆入口口令和授权（111111 被当成 License / 绕过 AuthGate）
   → 是/否  证据: [代码行/方法名]

3. 是否未授权越权操作（非 AUTH_OK 仍可添加密友/改密码/改 NotifyPolicy/开功能）
   → 是/否  证据: [代码行/方法名]

4. 是否绕过 AuthGate（调用授权接口但跳过 isAuthOk 检查）
   → 是/否  证据: [代码行/方法名]

5. 是否破坏 SearchUnlock → StateMachine → RefreshBus → Filter 链
   → 是/否  证据: [SearchUnlock 命中后的调用路径]

6. 是否影响 Java/C++ 分层（C++ 操作 UI/Adapter/DB，或主进程走 C++ sm）
   → 是/否  证据: [代码行/方法名]

7. 是否有保护区文件被改但未经本 skill 审查先行
   → 是/否  证据: [文件名 + 改动描述]

8. 最小修复建议（如有问题）
   → [1-3 条最小改动描述，不改代码，只写方向]

─────────────────────────────────────────
门控状态
─────────────────────────────────────────
EntryGate:  ✅ 通过 / ⚠️ 警告: [描述] / ❌ 阻断: [描述]
AuthGate:   ✅ 通过 / ⚠️ 警告: [描述] / ❌ 阻断: [描述]
StateGate:  ✅ 通过 / ⚠️ 警告: [描述] / ❌ 阻断: [描述]
RiskGate:   ✅ 通过 / ⚠️ 警告: [描述] / ❌ 阻断: [描述]
分层合规:   ✅ 通过 / ⚠️ 警告: [描述] / ❌ 阻断: [描述]

─────────────────────────────────────────
最终结论: PASS / WARN / BLOCK
─────────────────────────────────────────
BLOCK 原因: [如有，必填]
```

**结论规则：**
- `PASS`：8 项全部无问题，可继续
- `WARN`：有警告项，需用户确认后继续
- `BLOCK`：任意 1 项发现问题，**必须修复后才能提交/装机/发版**

---

> ⚠️ **授权审查通过 ≠ 代码质量合格。** 授权检查官只审安全链路；编译阻断 / 铁律违反 / KPI 红线由 `guard-review_质检门控` 负责。两者必须都通过，才能装机/发版。

## 六、修改授权逻辑的正确流程

```
1. 功能 AI / 执行角色 识别到要改保护区文件
   ↓
2. 停下来，调用 guard-auth-review_授权检查官 skill
   ↓
3. 授权检查官读取当前代码，输出审查报告
   ↓
4. 用户确认 PASS / WARN / BLOCK
   ↓
5. PASS/WARN（用户同意）→ 执行改动
   BLOCK → 修复问题 → 重新审查
   ↓
6. 改动完成后，授权检查官再次审查（验证改动没有新增问题）
   ↓
7. 报告写入当前 P 任务的 worklog.md
```

**禁止流程：**
- ❌ 执行角色看到 isVipAuthorized() 是 stub → 顺手"优化"成真实判断
- ❌ 执行角色为了"方便测试"→ 临时 hardcode AUTH_OK
- ❌ 执行角色合并 StateGate 和 AuthGate（如 isActive() 直接返回 isAuthOk()）
- ❌ 执行角色在 SearchUnlock 里加入状态机切换（违反 EntryGate 语义）
- ❌ 总架构师以"方案已定"为由跳过授权检查官审查
- ❌ 任何角色把 111111 改成授权码
- ❌ 任何角色绕过 StateMachine 直接调用 ConvFilter.clean / restore / notify
- ❌ 任何角色让 SettingsEntry / DebugServer / SearchFilter 私自修改状态机
- ❌ 任何角色在用户未明确同意 PASS/WARN 前执行授权保护区改动

---

## 七、v1 已知已接受的授权简化（不得升级前自行改）

| 简化项 | 当前 v1 状态 | v2 计划 |
|-------|------------|--------|
| `isVipAuthorized()` | stub，永远返回 true | 替换为 `LicenseGate.check()` |
| ACCOUNT_MISMATCH / DEVICE_MISMATCH | 放行（hook 仍注册）| v2 真正拦截 |
| killSwitch | stub，永远 false | 接入 miyou-server 真实接口 |
| 蜜罐 | 未实现 | v2+ 引入假入口 |
| Ed25519 验签 | 未实现 | v2 接入 LicenseBox |

**以上简化是设计决策，不是 bug，不得在 v1 期间"修复"。**

---

## 八、补充铁律（上方 ⛔ 未覆盖的关键规则）

1. **SearchUnlock 不调用 StateMachine.exitHidden / enterVisible / beginUnlock / attemptUnlock。** 状态切换只在 SettingsEntry 对话框按钮内走 StateMachine。
2. **H/V 热切完整链路：入口口令命中 → SettingsEntry(对话框按钮) → StateMachine → RefreshBus → Filter。** 任何角色不得绕过此链路。
3. **授权评估结果（AUTH_STATE）只由 AuthManager + bindAccount 写入，其他模块只读。**
4. **v1 简化项（isVipAuthorized stub / 放行策略）不得在 v2 前擅自升级。**

---

## 九、授权与功能开关分层设计（2026-05-25 锁定）

### 9.1 授权是所有功能的前提

```
没有授权（isVipAuthorized=false）
  → 一切功能全部关闭，包括：
      - 密友过滤（isActive=false，朋友圈/会话/通讯录/搜索全部放行）
      - 设置页所有功能按钮（禁用，提示"需要授权"）
      - 杂项功能（防撤回/定位等，同样不工作）
  → 但以下仍然可以：
      - 输入口令触发状态机 H/V 切换（状态机本身不受授权影响）
      - 看到"量子密友"入口，进入设置页（界面可见，功能不可用）

有授权（isVipAuthorized=true）
  → 进入功能开关层，各功能独立控制
```

**铁律：isVipAuthorized() 必须保留在 isActive() 中。**
❌ 禁止以"过滤是隐私保护、不应受 license 影响"为由将其移除。
产品设计明确：无授权 = 无产品，包括隐私保护功能。

### 9.2 功能开关各自独立（授权后才有意义）

```
f1：密友功能开关（isFeatureEnabled）
    控制：密友过滤、H/V 状态机过滤效果
    不影响：防撤回、定位等杂项

f2：防撤回开关（v2 待加）
    控制：消息防撤回功能
    不受 f1 影响，独立开关

f3：定位开关（v2/v3 待加）
    控制：位置伪装功能
    不受 f1 影响，独立开关
```

**铁律：杂项功能开关（f2/f3 等）与密友开关（f1）完全独立。**
❌ 禁止把杂项功能的启用判断写成 `isFeatureEnabled() && isXxxEnabled()`（依赖 f1）。
✅ 正确写法：`isVipAuthorized() && isXxxEnabled()`（只依赖授权 + 自身开关）。

### 9.3 isActive() 三层门控最终定义（不得修改）

```java
public boolean isActive() {
    return isVipAuthorized()                         // 层1：授权门
        && Bridge.getInstance().isFeatureEnabled()   // 层2：密友功能总开关（f1）
        && mActive;                                  // 层3：当前 HIDDEN 态
}
```

此方法只用于**密友过滤**决策，不用于其他功能。
v2 接入 LicenseGate 时，只替换 `isVipAuthorized()` 的实现，结构不变。
