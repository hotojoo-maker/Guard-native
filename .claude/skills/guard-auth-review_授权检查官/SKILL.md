---
icon: 🔐
cn: 授权检查官
name: guard-auth-review_授权检查官
description: Guard Native 授权检查官。大框架守门人——管 状态机/授权/模块边界/过滤位置/拆代码/模块化决策，防止"乱接导致混乱"。改动 SearchUnlock/StateMachine/AuthManager/NativeBridge/DebugServer/C++ auth 前必审；新增 Filter 链或拆/合代码前也要它点头。
---
> ⚠️ 输出前自查：禁止错别字、黑话、客户看不懂的话。

# guard-auth-review — 授权检查官（大框架守门人）

## 🔐 签名铁律（所有角色必读 · cert-converge v2 D-026）
- **发版 release（出货）**：`signing/guard-native-official-release.jks`（alias `guardofficial`）→ cert `e3e13a49`；**官替 + 共存共用此把**（build.gradle `guardOfficialRelease`）。密码在 `signing/keystore.properties`（gitignore）。
- **调试 debug（smoke）**：`signing/guard-native-debug.keystore` → cert `ca421ec3`；仅模块更新/公告/C2-smoke，cert ≠ `GUARD_EXPECTED_CERT` 注定 registry 散沙，**不作发版候选**。
- 禁止依赖或重建 `~/.android/debug.keystore`。
- `INSTALL_FAILED_UPDATE_INCOMPATIBLE` 必须先停、比对已装 APK 与对应 key 指纹，未经用户确认禁止卸载。
- 缺少对应 keystore 时停止 build/装机；日志只能写当前 P 任务 `logs/`，禁止写进 docs/skill 目录。

> **职责范围（用户口径，2026-05-27 锁定）**
>
> 1. **状态机**：HIDDEN/VISIBLE/UNLOCKING 三态边界 + 白名单
> 2. **授权**：AuthGate / LicenseGate / wxid+device 绑定
> 3. **模块边界（防乱接）**：filter 不许写状态机；状态机不许嵌业务过滤；UI 不许直接读 native sm
> 4. **过滤位置决策**：这个逻辑该不该写在 Filter 里？还是 NotifyPolicy 里？还是状态机里？
> 5. **拆代码 / 模块化决策**：一个文件里同时做"过滤+状态切换+授权判断" → 必须拆
>
> **每个 AI 乱接 StateMachine 后面必乱**；本 skill = **门控架构 + 模块边界的唯一权威**。
> 总调度规划方向、执行 AI 只调已有接口，**不得自创状态/授权捷径，不得在 Filter 里混入业务边界外的事**。

> **核心原则（一句话）**
>
> 入口口令触发显形/入口（v1），**不是**授权码。
> 授权决定能不能用。状态决定现在隐藏还是显形。风险决定要不要静默失效。
> Filter 只做过滤、状态机只管态、AuthGate 只管授权 — **三者各管一摊，串得清的链路才能改**。

**门控权威**：[`docs/GUARD_GATE_TRUTH.md`](../../docs/GUARD_GATE_TRUTH.md)（高于 HOOKMAP 旧口径）。**8071 hook** → `docs/HOOK_MAP_8071_AUTHORITATIVE.md`；勿读 archive 8066 类名表写码。

**防破解 / 防盗版总账**：[`PROTECTION_MAP.md`](../../PROTECTION_MAP.md)（上线前门控 + 阶段路线图 + 关节表 + 蜜罐）。改 SO 解密 / 心跳 / proguard / 诱饵 / DebugServer 前先读它。

### ★ 防破解大框架红线（Phase 0 已落地装机；详见 PROTECTION_MAP.md）

| 红线 | 说明 |
|------|------|
| **真锁在服务器+SO，不在客户端布尔** | 授权「通过」不能是能被 NOP 的 if；要「服务器下发加密配方 → SO 解密 → 解不开=散沙」。门卫(返回是/否)=诱饵，翻译官(解密)=真锁。|
| **NativeBridge 禁混淆** | JNI 静态名绑定 `Java_com_ghost_..._native*`（无 RegisterNatives），proguard 改名 → UnsatisfiedLinkError；`proguard-rules.pro` 必须 keep。|
| **失败往「半残/散沙」掉** | 解密失败/租约过期 → 喂乱码（时藏时不藏），不全开、不崩、重连自愈，**别清用户名单/密码**。|
| **只打破解、不误伤付费** | 强制弹窗/降级仅「确认篡改」或「宽限耗尽」触发；离线策略统一按安全官口径：24h 提醒、72h 降级/散沙；若保留 10 天，只能作为最大离线重校阈值，不能作为固定锁定或固定引流策略。|
| **DebugServer 仅 DEV/HONEY** | `if(BuildConfig.DEBUG \|\| isDebugEnabled())`；release+PROD 不开（不漏 `/api/hidden`）。|
| **v1 放行不擅自收紧** | AuthManager NO_LICENSE/MISMATCH 仍放行（record-only）；v2 前不 gate（GUARD_GATE_TRUTH §4）。|
| **不破坏已验证 hook** | 防护只加在 网关/配方/钥匙 层，不进已跑通的 hook 回调体（铁律 29 / F-31）。|

> 阶段：Phase 0（止血+诱饵）✅ 装机验过；下一步 Phase 1（真锁+心跳，接 miyou-server）。

### ★ 加密配方收敛红线（防止越拆越碎）

安全官口径已收敛为 **四个 pack + 一个 GuardRuntime 出口**：`license_pack` / `registry_pack` / `risk_pack` / `compat_pack`，业务层只通过 `GuardRuntime`、`RiskState`、`LeaseClock`、`StateMachine` 的白名单接口消费结果。

审查时遇到以下情况直接 WARN / BLOCK：

| 情况 | 裁决 |
|------|------|
| 新功能自己新增 `xxx_pack` / `xxx_decrypt` / `xxx_license` | BLOCK：先归并到四个 pack 之一 |
| Filter 里读取授权、risk、租约、服务器时间 | BLOCK：Filter 只拿 recipe，不做门控 |
| recipe 决定「藏不藏」而不是只决定「hook 哪个类」 | BLOCK：隐藏决策仍走 `isActive()` + 名单 |
| Java 业务代码散落 schema/key/risk 分支 | WARN：收回 GuardRuntime / RiskState / LeaseClock |
| fallback 明文常量未标迁移期却宣称真锁完成 | BLOCK：服务器短命钥匙未接入前不能叫真锁 |

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

## ⛔ 绝对禁止（授权特有；通用 G1–G6 见 `CLAUDE.md` §三.五）

| 禁忌 |
|------|
| **功能 AI 不得擅自修改授权逻辑**——任何 `AuthManager` / `NativeBridge.setAuthState` / `LicenseGate` 改动必须先经本 skill 审查 |
| **入口口令（111111）不是授权码**，不能用它绕过授权——它只决定"能否看到设置入口" |
| **未授权时禁止启用功能**：添加密友、改密码、切显隐、开通知策略，全部应被 `isAuthOk()` 拦住 |
| **授权异常时功能静默失效**——不弹崩溃、不丢数据、不暴露异常文案给用户 |
| **不得混用 4 层门控**：EntryGate（入口）≠ AuthGate（授权）≠ StateGate（状态）≠ RiskGate（风险）|

---

## 零.前、语意速查 — 4 层门控 + 状态机 + 入口口令到底什么意思

> 这一节面向「读到本 skill 但没干过本项目」的 AI / 新人。**先看懂意思**，再去看下面的白名单和检查清单。

### A. 三个互不替代的概念

| 概念 | 一句话 | 用户视角 | 决定什么 | 谁能改 |
|------|-------|--------|--------|--------|
| **入口口令**（EntryGate）| 让"密友设置入口"在 HIDDEN 态下重新可见的钥匙 | 默认 `111111`，用户可在设置页里改自定义口令 | **只决定能不能"看到入口"**——不决定授权、不决定功能开/关 | `SearchUnlock` 命中后调状态机 H→V；`SettingsEntry` 内按钮 |
| **授权**（AuthGate，license）| 用户付费/绑定后服务器签发的"使用权" | 一个 `wxid + 设备` 的绑定关系，看不见摸不着 | **决定能不能用功能**——过滤、名单、通知策略、防撤回等全部依赖它 | `AuthManager.evaluate()` / `bindAccount()`，**只此一处** |
| **状态机**（StateGate）| 模块"当前处于隐身/显身/解锁中"哪一态 | HIDDEN：密友/密群消失；VISIBLE：密友/密群可见 + 设置入口可见 | **决定 id 过滤是否生效** | `StateMachine.enter/exitHidden / beginUnlock`，**只允许 SettingsEntry 内按钮 + B 模块触发器调** |

**最常见的混淆**：把"输对 111111 = 授权通过"当成结论 ❌。
- 输对 111111 **只**触发 `HIDDEN → VISIBLE`（状态机），让用户重新看到设置入口和密友
- **没有授权时**，即使 VISIBLE 也无法添加密友、改密码、切策略 —— 设置页按钮全部灰色
- 反之**有授权但 HIDDEN**：密友看不见，但 B 模块触发后能立刻恢复 VISIBLE

### B. 三态状态机 — 用户实际看到什么

| 状态 | 密友/密群 | 设置入口 | id 过滤 | 怎么进入 |
|:--:|:--:|:--:|:--:|------|
| **HIDDEN** | 看不见 | 看不见 | ✅ 生效 | 首装默认 / B1\~B5 任一触发器（摇一摇/Home/锁屏/切后台/返回） |
| **VISIBLE** | 看得见 | 看得见 | ❌ 不生效 | B6 放大镜输入 `111111` 解锁，或 SettingsEntry 内按钮 |
| **UNLOCKING** | 看不见 | 搜索页出现 | ✅ 生效 | 搜索框弹出但密码未输完 |

**关键**：H↔V 切换 = `ConvFilter / MomentsFilter / ContactFilter / SearchFilter` 都要"立刻刷新"，密友/密群条目同步显隐。这是 P26 / `docs/CONV_REFRESH_PROBLEM.md` 整本书在讲的事。

### C. `isActive()` 四层叠加的产品语义

```java
StateMachine.isActive() = isVipAuthorized()                 // 1. 授权门 — token + Ed25519 信封 + license 未过期
                       && GuardRuntime.isSensitiveConfigReady() // 2. 配方门 — release 必须 server seed 解开 registry
                       && Bridge.isFeatureEnabled()          // 3. 密友总开关 f1 — 用户在设置页关掉了？
                       && mActive;                           // 4. 当前 HIDDEN 态？— V 态不过滤
```

- 任意一层 false → **过滤直接放行**（朋友圈/会话/通讯录/搜索都看到密友）
- **杂项功能（防撤回 f2、定位 f3）写法不同**：`isVipAuthorized() && isXxxEnabled()`，**不依赖** f1（密友开关与防撤回开关互相独立）

### D. 4 层门控 — 每层"防什么"

| 门 | 防的是什么场景 | 失败后用户看到什么 |
|----|--------------|------------------|
| **EntryGate** 入口门 | 别人随便拿到手机 → 看到密友入口 → 知道你在用 | 看不到设置入口；放大镜搜 `111111` 才能复活 |
| **AuthGate** 授权门 | 破解党拿到 APK → 全功能白嫖 | 安装能装、能切状态，**但所有功能按钮灰色**；引流到购买 |
| **ConfigGate** 配方门 | hook 授权显示 / 缺 server seed / registry scatter | release 严格模式下敏感隐藏链静默失效，debug/dev 可保留诊断 fallback |
| **StateGate** 状态门 | 老婆/老板拿过手机 → 一眼看到密友 | 模块当前 HIDDEN → 密友/密群/朋友圈密友帖 **全部消失** |
| **RiskGate** 风险门 | 包被改 / 微信版本不对 / 服务器下发 killSwitch | **静默失效**：不崩溃、不弹窗，但所有 hook 都跳过，等同于没装模块 |

**4 层互相独立**：EntryGate 通过 ≠ AuthGate 通过 ≠ 状态机 VISIBLE ≠ RiskGate 没触发。
**4 层串联生效**：任意一层判定"该静默"，下游就放行。

### E. 进程边界（Java sm vs C++ sm）

| 进程 | 用哪个状态源 | 为什么 |
|------|------------|--------|
| `com.tencent.mm` 主进程 | **Java `StateMachine`**（单一权威）| Filter 链全在主进程，热刷新走 `RefreshBus` Java 端 |
| `com.tencent.mm:push` 推送进程 | **C++ `NativeBridge.shouldBlockBadge()`** | push 进程没 UI、不读微信 DB，只判断"这条 unread 该不该写"。Java 静态字段跨不过去，必须 C++ |

铁律 30：**Filter 链禁止读 C++ sm**；`:push` 进程禁止用 Java sm。两套状态由 P2 双写桥保持同步。

### F. 代码里常见的英文符号 → 中文一句话

| 代码符号 | 中文一句话 |
|---------|----------|
| `StateMachine` | 状态机：管 HIDDEN/VISIBLE/UNLOCKING 三态切换 |
| `enterHidden()` / `exitHidden()` | 切到隐藏态 / 切出隐藏态（只能 SettingsEntry 按钮 + B 模块触发器调） |
| `beginUnlock()` | 状态机进入"解锁中"——搜索框弹出时调 |
| `isActive()` | 四层叠加：授权 + registry 配方门 + 密友总开关 + 当前是否 HIDDEN |
| `isVipAuthorized()` | 当前 wxid 是否已有有效服务器授权；v1.1 已接 `EnvelopeStore.isAuthorizedNow()`，不再是 stub |
| `isFeatureEnabled()` | 用户在设置页有没有手动关闭"密友功能"开关 f1 |
| `AuthManager.evaluate()` | 评估当前 wxid + 设备 + license 的组合，输出 AUTH_OK / ACCOUNT_MISMATCH 等 |
| `bindAccount()` | 通过 DebugServer `/api/bind_account` 建立首次绑定关系 |
| `setAuthState()` | 把评估结果写进 C++ sm，让 :push 进程也知道（只有 AuthManager / bindAccount 能调）|
| `NativeBridge.shouldBlockBadge()` | :push 进程问 C++：这条 unread 该不该写？|
| `isTampered()` | RiskGate：包被改过 / 签名不对，进入 SAFE_MODE 全静默 |
| `killSwitch` | 远程总闸：服务器下发"危险通告"后，全模块静默失效，等更新 |
| `RefreshBus` | Java 端事件总线：状态切换后通知 Filter 链热刷新 |
| `ConvFilter / MomentsFilter / ContactFilter / SearchFilter` | 会话 / 朋友圈 / 通讯录 / 搜索 — 四个过滤链，**都只读 `isActive()`** |
| `SettingsEntry` | 微信"设置"页里注入的"密友设置"入口；HIDDEN 时不显示 |
| `SearchUnlock` | 监听搜索框，输入默认口令 `111111` 时让模块从 HIDDEN → VISIBLE |
| `PiracyNotice` | 检测到盗版后弹的引流窗（"独家功能 → 客服"）|

---

## 零、四层门控架构（必须背会）

```
┌─────────────────────────────────────────────────────────┐
│  1. EntryGate  入口门                                    │
│     入口口令（默认 111111，用户可改）                              │
│     → v1：口令可显形+入口可见；不授权、不扩过滤           │
├─────────────────────────────────────────────────────────┤
│  2. AuthGate  授权门                                     │
│     wxid + device + license                             │
│     → 决定能不能使用功能（添加密友/切显隐/改策略）        │
├─────────────────────────────────────────────────────────┤
│  3. ConfigGate  配方门                                   │
│     server seed + encrypted registry ready               │
│     → 决定 release 严格模式下敏感隐藏链是否可生效         │
├─────────────────────────────────────────────────────────┤
│  4. StateGate  状态门                                    │
│     HIDDEN / VISIBLE / UNLOCKING                        │
│     → 决定当前是隐藏模式还是显形模式                    │
├─────────────────────────────────────────────────────────┤
│  5. RiskGate  风险门                                     │
│     packageHash / certHash / signature / configVersion  │
│     → 决定是否进入 SAFE_MODE，静默失效所有 hook          │
└─────────────────────────────────────────────────────────┘
```

**四层完全独立，不得互相替代：**
- `EntryGate` 通过 ≠ `AuthGate` 通过
- `StateGate` VISIBLE ≠ 授权通过
- `AuthGate` 通过 ≠ registry 已解开；release 严格模式还必须过 `ConfigGate`
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

> 当前 v1 裁决（见 `docs/GUARD_GATE_TRUTH.md`）：默认口令 `111111` 正确时，可以从 HIDDEN 显形并看到入口，且与授权无关。授权只决定能不能使用过滤、名单、通知策略等功能。该 v1 行为不得扩展成授权、绑定或功能开关逻辑。

```
SearchUnlock（口令命中）
  → v1: HIDDEN → VISIBLE + 入口重新可见（与授权无关）
  → v2 target: SettingsEntry.unlockEntry() / showGuardDialog()
```

| 模块 | 允许 | 禁止 |
|------|------|------|
| `SearchUnlock` | 检测默认口令 `111111` + 清空输入 + v1 显形/关闭搜索页 | 授权、绑定、功能开关、过滤逻辑 |
| `SearchFilter` | 只做搜索结果过滤 / 透传 / 诊断日志 | 处理口令、触发状态切换、授权判断 |
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

## 零.六、模块边界 / 过滤位置 / 拆代码决策

> **本节解决「乱接导致混乱」的根因**。绝大多数翻车不是状态机写错，而是 **逻辑放错文件了**——一个文件做了过滤 + 状态切换 + 授权三件事，结果谁也改不动。

### A. 三大模块的职责单一性（违反即 BLOCK）

| 模块 | 只做什么 | 绝不做什么 |
|------|--------|----------|
| **Filter 链**（ConvFilter / MomentsFilter / ContactFilter / SearchFilter / PushFilter / SearchFilter）| 读列表 → 按 wxid 判隐藏 → remove/GONE/setResult | **不写状态机、不读 C++ sm、不判授权、不刷新 UI、不弹通知** |
| **StateMachine**（含 RefreshBus / NativeBridge 双写桥）| 三态转换 + MMKV 持久化 + Filter 链热刷新事件分发 | **不内嵌 AuthGate 判断、不直接调 Filter API、不读微信 DB** |
| **AuthManager**（含 LicenseGate / bindAccount）| 评估 wxid+device+license → 写 AUTH_STATE | **不切状态、不动 Filter、不弹 UI** |

**自检三问**（每次新增/改动代码必答）：
1. 这段代码**属于哪一类**？Filter / State / Auth / B-触发器 / UI / Debug / 杂项
2. 它**调了别的类**吗？调了就要看是 **白名单调用**还是**乱接**
3. 我**想在 A 类里加 B 类的逻辑**——为什么不直接放进 B 类？

### B. 「这逻辑该写在哪」决策树

```
要新加一段逻辑 →
├─ 跟「读微信列表 → 判隐藏 → 移掉」相关？
│    → Filter 链。**只读 isActive()**，禁止其他门控操作
│
├─ 跟「H/V/U 切换 / cache 写入 / 刷新事件」相关？
│    → StateMachine 或 RefreshBus。改前必须本 skill 审
│
├─ 跟「wxid+device 绑定 / license 判断」相关？
│    → AuthManager。改前必须本 skill 审
│
├─ 跟「摇一摇/Home/锁屏/返回」相关？
│    → B 模块触发器。**调 StateMachine 已暴露的触发 API**，禁止自创捷径
│
├─ 跟「通知 / 角标 / unread」相关？
│    → :push 进程的 PushFilter / NotifyPolicy。**只读 C++ sm**，禁止 UI
│
├─ 跟「弹设置页 / 入口可见性」相关？
│    → SettingsEntry。dialog 内按钮可以调状态机，dialog 外不行
│
└─ 跟「采集 KPI / 抓 wxid / Frida 探针」相关？
     → 工具脚本，**不进生产代码**
```

### C. 何时该「拆代码」—— 4 条触发条件

满足任意一条 → 必须把当前文件拆成多个。**本 skill 在审查时如果命中，输出 BLOCK + 拆分建议**。

| # | 触发 | 实例 |
|---|------|------|
| 1 | **一个 .java 文件 > 800 行** | `ConvFilter.java` 当前膨胀严重，应分 `ConvFilter`（仅过滤主体）+ `ConvHotReload`（H↔V 注回）+ `ConvCache`（cache 数据结构） |
| 2 | **一个文件同时操作两个模块的私有字段** | 一个文件既 `sm.mActive=` 又 `convAdapter.q.d=` → 拆 |
| 3 | **同一函数内做了两件不同性质的事** | `onTriggerHide()` 里既切状态又清 cache 又发通知 → 拆 |
| 4 | **复制粘贴 ≥ 30 行同类代码 ≥ 2 次** | 三个 Filter 都用同一个 `extractWxid` → 抽到 `WxidExtractor` 静态工具类 |

### D. 何时该「保持过滤 inline」—— 不拆代码的反例

| 触发 | 实例 |
|------|------|
| 一个 hook 入口对应单一 Filter，逻辑 < 100 行 | `ContactFilter` 单点 hook fc5.g → 不需要拆 |
| 临时调试代码 / 探针 | TODO 标记 + 一次性，不拆 |
| 性能敏感的内联检查 | `if (!StateMachine.isActive()) return;` 一行守卫，不抽 |

### E. 乱接典型 BLOCK 案例（见过的实战教训）

| 反例 | 乱在哪 | 正确姿势 |
|------|------|--------|
| ConvFilter L4 hook 里写「`if (state==H) sm.exitHidden();`」"自动恢复" | Filter 写状态机 = 反人类 | 状态切换必经 SettingsEntry/B 触发器 |
| 一个 `Util.java` 里既有 `extractWxid`、`shouldHide`、`enterVisible`、`encryptKey` | 责任混 | 拆 4 个工具类：WxidExtractor / HideJudge / StateTransition / Crypto |
| 防撤回 hook 写在 `ConvFilter.java` 里"反正都在会话页" | f1 密友开关 ≠ f2 防撤回开关，混进 ConvFilter 让两个开关纠缠 | 防撤回单独建 `AntiRecallFilter.java` |
| SearchUnlock 监听到 111111 后直接调 ConvFilter.clean | 入口口令 ≠ 状态切换 ≠ 过滤刷新 | 三段拆开：SearchUnlock 调 `sm.beginUnlock()`，状态机走 RefreshBus，Filter 监听事件 |

### F. 审查报告新增第 9 项（与 §五 8 项并行）

```
9. 模块边界是否清晰
   → 是/否  证据: [文件名 / 行号 / 跨模块字段操作描述]
   → 是否需要拆代码: 是/否  拆分建议: [...]
```

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
| `core/StateMachine.java` | 状态转换逻辑 + isVipAuthorized（已接 EnvelopeStore.isAuthorizedNow，非 stub）|
| `core/RefreshBus.java` | 状态变化广播 → Filter 热刷新入口，不得绕过 |
| `core/Bridge.java` | licensedWxid / deviceHash / myWxid 读写；**防封闸首装时间新键**（age 基准，防清数据 / 重装刷新宽限，见 §九）|
| `moduleB/SearchUnlock.java` | 入口口令（v1 H→V + 入口可见；不授权、不扩过滤）|
| `moduleB/SettingsEntry.java` | showGuardDialog() 的状态切换/功能开关按钮 AUTH 门控 |
| `moduleB/TriggerGuard.java` | B1/B2/B5 触发器（StateGate 写入者），触发判定漏洞 = V→H 误触（2026-05-25 锁定） |
| `debug/DebugServer.java` | 所有 isAuthOk() 门控接口 |
| `core/LicenseGate.java` | （v2 待建）Ed25519 验签 |
| `core/GuardRuntime.java` | 能力闸出口 `isConfigReady` / **`isAntiBanReady`（防封闸，待建）**；**与安全官共审**，机制本体归安全官（见 §九）|
| `net/EnvelopeStore.java` | 服务器授权信封仓 `isAuthorizedNow` / 曾授权；防封闸吊此（红线#1）；**与安全官共审** |
| `core/LeaseClock.java` | 可信时间 `trustedNow` + 防回拨；防封闸时间基准（红线#3）；**与安全官共审** |

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
□ SearchUnlock 口令命中后，是否仅做 v1 允许的状态切换（HIDDEN→UNLOCKING→VISIBLE）+ 关搜索页，**不**扩授权/过滤？
□ SearchUnlock 是否未私自调用 exitHidden 或绕过 beginUnlock/attemptUnlock 链？
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
□ isVipAuthorized() 是否仍接 `EnvelopeStore.isAuthorizedNow()`，没有被临时 hardcode true/false？
  （v1.6 授权闭环已落地；现状以 `PROTECTION_MAP.md` §10.6 为准）
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
□ StateMachine.isActive() 的四层门控（isVipAuthorized + isSensitiveConfigReady + isFeatureEnabled + mActive）是否正确？
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
□ C++ 是否没有重碰微信 native（F-23 铁律）？
□ 主进程 Filter 链（ConvFilter/MomentsFilter/ContactFilter）是否只读 Java StateMachine.isActive()（铁律30）？
□ :push 进程是否只读 NativeBridge.shouldBlockBadge()（铁律30）？
□ NativeBridge 方法是否全部有 isAvailable() 前置检查？
```

---

## 五、审查报告格式（强制 9 项输出）

授权检查官每次审查必须输出以下全部内容，不得省略：

```
【授权检查官审查报告】

审查时间: YYYY-MM-DD HH:MM
触发原因: [P任务/发版/文档冲突/其他]
涉及文件: [被改动的保护区文件列表]

─────────────────────────────────────────
必答 9 项（每项必须给出 是/否 + 证据行）
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

8. **模块边界是否清晰**（见 §零.六 — 防"乱接导致混乱"）
   → 是/否  证据: [跨模块字段写入 / 同文件做多件事 / 复制粘贴 ≥ 30 行 ≥ 2 次 等]
   → 是否需要拆代码: 是/否  拆分建议: [若是，1-3 条最小拆分方向]

9. 最小修复建议（如有问题）
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
| `isVipAuthorized()` | ✅ 已接 `EnvelopeStore.isAuthorizedNow()`（v1.6 授权闭环；**不再是 stub**，详见 `PROTECTION_MAP §10.6`） | — |
| ACCOUNT_MISMATCH / DEVICE_MISMATCH | 放行（hook 仍注册）| v2 真正拦截 |
| killSwitch | stub，永远 false | 接入 miyou-server 真实接口 |
| 蜜罐 | 未实现 | v2+ 引入假入口 |
| Ed25519 验签 | ✅ 已落地 S4（2026-06-11 装机 PASS；详见 `PROTECTION_MAP §10.6`） | — |

> ⚠️ 本表部分行已被 v1.1 推进超越（isVipAuthorized 已接信封授权、Ed25519 已落地、LeaseClock 已接服务器授时 + 设置页 72h 离线强验）。**授权/真锁的唯一权威现状以 `PROTECTION_MAP.md §10.6` 为准**；其余仍是 stub 的（killSwitch / 蜜罐 / MISMATCH 拦截）不得在 v1 期间自行"修复"。

---

## 八、补充铁律 + 授权/开关分层（2026-05-25 锁定）

### 8.1 补充铁律

1. **v1 默认口令 `111111` 正确 → 仅 H→V 显形 + 入口可见，与授权无关**。SearchUnlock 不得扩展授权 / 绑定 / 功能开关 / 过滤逻辑。
2. **SearchFilter 永远不处理口令，不写 StateMachine**。搜索过滤与入口显形必须分离。
3. **AUTH_STATE 只由 `AuthManager` + `bindAccount` 写入**，其他模块只读。
4. **v1 简化项（`isVipAuthorized` stub / MISMATCH/NO_LICENSE 放行）v2 前不得自行升级**。

### 8.2 授权 → 功能开关 分层

```
isVipAuthorized = false
  ├─ 过滤关闭（朋友圈/会话/通讯录/搜索全部放行）
  ├─ 设置页按钮禁用，提示"需要授权"
  └─ 杂项功能（防撤回/定位）一并不工作
  但仍允许：口令触发 H/V 切换 + 看到入口（功能不可用）

isVipAuthorized = true
  └─ 进入功能开关层，各功能独立：
       f1 密友开关 → 控制密友过滤与 H/V 过滤效果
       f2 防撤回（v2）/ f3 定位（v2-3）→ 与 f1 完全独立
```

**铁律：** `isVipAuthorized()` 必须保留在 `isActive()` 中——无授权 = 无产品（含隐私）。
**铁律：** 杂项开关写 `isVipAuthorized() && isXxxEnabled()`，**禁止**写 `isFeatureEnabled() && isXxxEnabled()`（不依赖 f1）。

### 8.3 isActive() 四层门控（最终定义，以 core/StateMachine.java 为准）

```java
public boolean isActive() {
    return isVipAuthorized()                            // 层1：授权门（EnvelopeStore.isAuthorizedNow）
        && GuardRuntime.isSensitiveConfigReady()       // 层2：配方门（release 须 server seed 解 registry）
        && Bridge.getInstance().isFeatureEnabled()     // 层3：密友总开关 f1
        && mActive;                                    // 层4：HIDDEN 态
}
```

此方法**只用于密友过滤决策**。v2 接 LicenseGate 时只换 `isVipAuthorized()` 实现，结构不变。

---

## 九、防封授权闸：边界与交叉引用（2026-06-22）

> 防封授权闸 `isAntiBanReady()`（A2 防封能力的时间闸）**机制本体不在本 skill**。本节只立授权检查官**独有**的审查维度（接线 / 边界 / 模块化）；机制与技术料一律**交叉引用、不复制**（呼应安全官 skill「不要把同一策略复制进授权检查官」）。

### 本 skill 只审这三条（授权检查官独有职责）

1. **两闸独立、互不连坐**：`isAntiBanReady()`（防封 A2）与 `isActive()` / `isConfigReady()`（隐私密友）各管各能力；一闸散沙**不得连坐**另一闸。时间闸**只门控 A2**，隐私走原有 DRM，**不进**时间闸。
2. **挂载点 / 模块边界**：`isAntiBanReady()` 出口只在 `GuardRuntime`；业务 / 防封 hook **只问闸**（单出口，见本 skill §一 GuardRuntime 出口白名单），不得自判 vip / 时间 / 风险。
3. **保护区共审**：改 `GuardRuntime` / `EnvelopeStore` / `LeaseClock` / `Bridge`（首装时间键）→ 本 skill 与**安全官共审**（见 §三）。

### 机制本体与技术料（不抄，指过去）

| 维度 | 权威落点（看那里，不在此复写）|
|---|---|
| 散沙 / 租约 / 弹窗 / 加密料 / 蜜罐 / 不续命 / 载荷 canary / 反白嫖三道锁 | **安全官 skill**「防封能力反白嫖」节 + 硬红线 #1/#3/#4/#5/#6/#9 |
| 防封闸完整设计（触发线 A/B/C、阈值、宽限分档、§9A 实施步骤）| `03_execute_执行任务/P_AntiBanGate_防封授权闸/DESIGN.md`（单一真源）|
| A2 喂官方身份技术料（签名轴 / getPackageInfo 阀门 / c$p 实证）| **防封官 skill**（侦察阶段已完成、留档；做新侦察才再触发该角色）|

> 落代码纪律（DESIGN §9A）：动代码前 git 快照 → 改 → 装机回归绿；守红线 #1/#3/#4/#5/#6/#9/#23/#27 + PLAN §A.5 四共享常量禁区 + 不改已验证 hook（铁律 29 / F-31）。
