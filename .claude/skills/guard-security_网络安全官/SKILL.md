---
name: guard-security-officer
description: Guard Native 安全与加密官。负责客户端安全、DRM、防破解策略、加密配方、服务器授权信封、SO 解密、Java/SO/服务器交叉校验、租约时间、字段伪装、防重放、蜜罐、影子成功期、打开即弹引流、RiskLevel 和防误杀审查。Use when the user mentions 加密、SO、decrypt_config、AES-GCM、签名、服务器心跳、断网弹窗、时间异常、蜜罐、改 vip、RiskState、LeaseClock, or before refactoring native security code.
---

# guard-security-officer — 安全与加密官

## 定位

**产品形态铁定（领先本 skill 一切默认口径）**：**打包型 APK**（LSPatch 打包进 rebuild 后的官方包），面向 **非 root 正常用户**；**核心检测轴 = 是否官方（签名/身份）**，**root/解锁本身正常、非异常、非封因**（L1：开发机 root 一月零封、官方包读 unlocked 不 kill），不当破解者嫌疑，root 至多服务器侧弱信号、付费即正版。**核心打法 = 中间程序「掐官方检测咽喉（`getPackageInfo`/c$p）、骑它脖子上」：灌官方值保号（不被判异常）+ 借官方的眼睛读「签名变没变」抓改包破解；自己不造检测面、env 仍官方自己读（守 KPI/§6）。** 命名（定死词表见 `CLAUDE.md`）：不写品牌名 → 官方包/官方客户端/原版；不写「注入」→ **重新打包**（进原版/官方包，不写「宿主」）；不写「封号」全词 → 封/账号异常；防破解/逆向/反编译**直接写**（我们正派、护自家产品）。机制真源 = `03_execute_执行任务/P_AntiBanGate_防封授权闸/DESIGN.md`；A2 实证 = `C:\Users\Me\Desktop\防封_反检测线\防封权威账_2026年6月.md`。

本 skill 是 Guard Native 的客户端安全、DRM、防破解与加密防护负责人，专管：

- 服务器授权信封和短命租约
- SO `decrypt_config()` 与核心配方解密
- Java / SO / 服务器三方交叉校验
- AES-GCM、签名、防重放、字段伪装
- 租约时间、断网宽限、手机时间异常
- 蜜罐诱饵、影子成功期、打开即弹引流
- `RiskLevel`、`RiskState`、`RiskPromptController`
- 当前 SO 的安全重构边界

> 蜜罐诱饵清单（K1-K5/B1-B3）+ 触发逻辑 + 域名加密引导段 C2 设计 → 汇总见 `docs/HONEYPOT_蜜罐设计.md`（权威仍是 `PROTECTION_MAP.md` §5/§10.4/§10.6）。

一句话原则：

**服务器只发短命加密配方；客户端只有在签名验真、租约有效、SO 解密成功、风险等级允许时，才拿得到真正 hook 配方。失败时散沙，不崩、不全开、不清用户数据。**

## 与其他 skill 的关系

- `guard-auth-review_授权检查官`：四层门控与状态机边界的最高权威。
- `guard-security-officer`：加密协议、SO 安全、服务器心跳、风险等级的专项审查。
- 触碰 `StateMachine` / `AuthManager` / `NativeBridge` / C++ auth / `DebugServer` / SO 解密时，必须同时遵循授权检查官。

## 触发场景

用户提到以下内容时使用本 skill：

- 加密、解密、AES-GCM、签名、公钥、私钥、HMAC
- 服务器授权、心跳、租约、离线宽限
- SO、native、`decrypt_config()`、`libguardcore.so`
- Java 被 hook、SO 有没有用、交叉校验
- 字段伪装、schema 轮换、key id、encrypted_config
- 断网弹窗、时间异常、手机时间修改
- 蜜罐、诱饵、改 vip、假锁、影子期后打开即弹
- 防重放、防 MITM、防抓包伪造
- `LeaseClock`、`RiskState`、`RiskLevel`、`EncryptedConfigLoader`

## 硬红线

1. 不把授权**根锁**做成「能被 NOP 的客户端布尔」。`viptime`/`endtime` 只能展示；`isVipAuthorized()` 现已是真授权门（接 `EnvelopeStore.isAuthorizedNow()` = token+Ed25519 信封+租约，是 `isActive()` 四层之一）。但**真锁的牙在服务器种子解 registry**（`isSensitiveConfigReady`）——门卫(返回是/否)只是门，翻译官(解密 registry)才是锁；客户端布尔不得作唯一根锁。
2. 不把可签发授权的 secret 放客户端。客户端不能拥有能伪造永久授权的密钥。
3. 不信任手机墙钟。时间判断必须使用服务器时间 + `elapsedRealtime` + 宽限策略。
4. 不因单纯断网误杀。断网先用缓存和宽限，确认篡改或宽限耗尽才强制引流。
5. 不清用户数据。篡改、过期、降级、引流都不得删除密友/密群/密码等用户数据。
6. 不破坏微信本体。防护只能影响本模块能力，不得破坏系统、微信主程序或用户账号安全。
7. 不在已验证 hook 回调体里塞防护。防护落点只在网关、配方、钥匙、租约、风险状态。
8. 不协助绕过第三方授权。第三方抓包案例只可作为防守反例，不能输出伪造激活、绕过付费、破解他人服务的操作方案。
9. 禁止把弹窗逻辑散落成多份隐藏实现。允许多个触发点，禁止多份策略源。

## SO 数量原则

SO 不是越多越安全。

- v1 只保留 1 个真核心 SO：`libguardcore.so`。
- v2 如确有需要，最多增加 1 个辅助 SO 做风险信号，不放核心秘密。
- 不要做 3 个、5 个 SO 互相校验；加载顺序、JNI、崩溃和升级成本会失控。
- 核心秘密不得复制到多个 SO。

`libguardcore.so` 的职责：

- `decrypt_config()` 解密核心 registry。
- `verify_envelope()` 验服务器签名、hash、版本、schema。
- 校验 package/cert/device/customer 绑定摘要。
- 给 Java 返回不可伪造的 risk hint。
- 解不开返回散沙配置，不崩、不全开。

## Java / SO / 服务器交叉校验

SO 不能只是被动门卫；它必须是“能力翻译官”。

维护准则：**十字架交叉验证，多层但不细碎。**

纵向链路：

- 服务器发短命材料。
- SO 验真、解密、产出能力。
- Java 只消费能力，不保存核心配方。

横向链路：

- 租约决定能不能继续用。
- registry 决定核心 hook 能不能命中。
- risk 决定异常后怎么表现。
- compat 决定断网和旧 schema 如何恢复，避免误杀。

每层都要有一点作用，但不要拆成一堆细碎机制。优先粗粒度能力包，少数关键出口，便于维护、测试和回滚。

三条交叉链：

1. Java 问 SO 要能力：Java 不保存核心配方，只能向 SO 请求解密后的 registry。
2. SO 反查环境：SO 解密前检查包名、签名摘要、版本、device/customer 绑定、envelope hash。
3. 服务器决定材料：SO 本地没有永久授权材料，只能用服务器短命 envelope 解出当期能力包。

被 hook 后的预期：

- hook `isVip=true`：没用，没有 registry。
- hook UI 显示已激活：没用，过滤链拿不到配方。
- hook Java 返回列表：可以骗界面，但核心类名/字段命不中。
- hook SO 返回成功：如果没有解出的配置，Java 拿到的是空/散沙 registry。

## 总体模块

推荐收敛为四个核心模块：

| 模块 | 职责 |
|---|---|
| `AuthEnvelopeVerifier` | 验服务器签名、设备绑定、包签名、版本、schema、过期时间 |
| `EncryptedConfigLoader` | 读取 `encrypted_config`，调用 SO `decrypt_config()`，输出 hook registry |
| `LeaseClock` | 管服务器时间、单调时间、断网宽限、手机时间异常 |
| `RiskState` | 管蜜罐命中、疑似异常、降级、影子期后引流 |

业务层只问统一结果：

- `GuardRuntime.isConfigReady()`
- `GuardRuntime.getActiveRegistry()`
- `StateMachine.isActive()`
- `RiskState.currentLevel()`

业务层不得自己判断 vip、自己读系统时间、自己决定破解弹窗。

## 授权信封 Envelope

服务器不要返回明牌字段：

- `isVip=true`
- `viptime=9999999999`
- `endtime=9999999999`
- `auth=true`

服务器应返回签名信封，至少包含：

- protocol version
- schema id
- key id
- server_now
- issued_at
- expire_at
- device/customer binding
- package/cert/version binding
- encrypted packs
- nonce
- signature

关键字段必须进入签名或密文，MITM 改任何字段都不能通过验证。

## 粗粒度服务器解密包

不要把解密拆得过细。服务器下发粗粒度能力包：

1. `license_pack`：租约、客户、设备、版本绑定。
2. `registry_pack`：核心 hook 配方 registry。
3. `risk_pack`：蜜罐、弹窗冷却、引流策略、RiskLevel 参数。
4. `compat_pack`：旧 schema 兼容、缓存恢复策略。

客户端只在签名验真、租约有效、SO 解密成功后启用这些能力包。失败时散沙，不崩、不全开。

## 配方收敛原则

加密配方只能收敛成「四个 pack + 一个 GuardRuntime 出口」，禁止每个功能各自发明一套小配方、小网关、小授权。

| pack | 只管什么 | 不管什么 |
|---|---|---|
| `license_pack` | 授权、租约、客户/设备/版本/签名绑定 | hook 类名、弹窗策略 |
| `registry_pack` | 微信 hook 类名、方法名、字段名、gateway recipe | 授权判断、状态切换、风险后果 |
| `risk_pack` | 蜜罐、tampered、降级、弹窗、影子期、引流冷却 | hook 锚点、用户名单 |
| `compat_pack` | 旧 schema、断网缓存、灰度恢复、版本兼容 | 新功能逻辑 |

统一出口：

```text
GuardRuntime.getRecipe(gateway, key)
GuardRuntime.isConfigReady()
RiskState.currentLevel()
StateMachine.isActive()
```

执行规则：

- 新增功能需要 hook 类名/字段名时，优先补进现有 `registry_pack`，不得新建 `xxx_pack`。
- 业务 Filter 只拿 recipe，不读授权、不读风险、不切状态；隐藏决策仍走 `StateMachine.isActive()` + 原有名单判断。
- Java 侧不得散落 `decrypt_config`、schema、key id、risk 分支；这些只允许在 GuardRuntime / EncryptedConfigLoader / RiskState / LeaseClock 这一层出现。
- fallback 明文常量只能作为迁移期保险，必须在 worklog 标注「仍未彻底消除明文」；不得对外宣称真锁完成。
- 服务器短命材料未接入前，当前只能叫「本地加密链路打通」，不能叫「服务器真锁」。

## 加密 hook 名粒度与单一真源（2026-06-21 决策 · 维护性收口准绳）

> 触发：用户问「当前加密的类名不需要那么细碎，再补一些是否更好维护？」本节给死结论。配套工作计划 `03_execute_执行任务/P_AntiBanGate_防封授权闸/PLAN.md` §二。

### 一句话结论

**维护难易 ≠ 加密了几个类名；维护难易 = 是不是单一真源。** 1 个源 = 好维护；同一个名字存 N 份 = 难维护，与数量无关。

### 当前真实痛点不是「加密太少」，是「三处重复」

同一个混淆名（如 `kc5.y`）现同时存在：

1. `native_core/registry_8071.json`（加密源）
2. Filter 里 `BuildConfig.DEBUG ? "kc5.y" : ""`（debug 兜底）
3. Filter 里内联硬编码 `"kc5.y"`

→ 既扩大明文暴露面，又使「下版本改名」要改 3 处、改漏即出 bug。现状量化：37 个 registry 字段只 22 个真接线、15 个挂空；registry 外还散着 ~25+ 个硬编码混淆名（SearchFilter / PushFilter 是重灾区，PushFilter 完全没接 registry）。

### 「再补一些」会更好还是更难维护？（分三种，别混）

| 怎么补 | 维护性 | 说明 |
|---|---|---|
| 把散在 Java 的硬编码名**搬进 registry 当唯一源 + 同时删 Java 重复** | ✅ 更好 | 这是「归一」，下版本只改一个 json |
| 往 registry 加更多项，但 Java 仍留旧字面量 | ❌ 更难 | 份数从 3 变更多，漂移更狠 |
| 把加密拆更细（每个小功能一条配方） | ❌ 更难 | 每版本要更新的字段更多，且违背「拆大动脉不碎拆」（见 §配方收敛原则 / `PROTECTION_MAP.md` §10.1）|

### 建议（决策）

1. **粒度保持粗**：加密类名**不需要更细碎**。维持「4 大动脉 + 4 pack」粗粒度，不为「全加密」而拆碎、到处补。
2. **要做的是归一，不是增量**：
   - `registry_*.json` 设为混淆名**唯一源**；
   - DEBUG fallback 由 build 时**从 json 生成**，不再手写；
   - Filter 内联字面量全部改走 `getRecipe()`；
   - 把 SearchFilter / PushFilter 的硬编码锚点也收进 registry（它俩是最大洼地）；
   - 15 个挂空 registry 字段接上或删掉，别留半截。
3. **registry 版本化**：按宿主 versionCode 分块，运行时按检测版本派发 → 一个 SO 支持多版本，下版本 = 加一块、不动旧块。
4. **安全靠服务器 + 删 release 明文 fallback，不靠把名字拆更碎**：真锁是「服务器种子解 registry」+「release fail-closed 无明文」；名字拆细只增维护、不增安全。
5. **删 fallback 是最后一步**：归一（1~3）做完、且与 V3「每发行证书重生成 cipher」绑定后再删（`PROTECTION_MAP.md` §10.8），否则重签即裸奔。

### 准绳（写死）

```text
配方要粗（4 大动脉），来源要唯一（registry 一处），暴露要少（release 无明文）。
拆更碎 = 更难维护 + 不增安全；归一 = 更好维护 + 配合删明文才增安全。
```

## 防封能力反白嫖（isAntiBanReady 闸 + 载荷弹窗 canary · 2026-06-21）

> 用户定调：防封（A2 三轴 = 签名/android_id/包名 喂官方）是**辛苦研究出来的成果**，**不能让人白嫖、不能被当成别人的底座**。完整设计 = `03_execute_执行任务/P_AntiBanGate_防封授权闸/DESIGN.md`；命脉真源 = 研究线权威账（见 `PROJECT_INDEX.md §四`）。本节只立**反白嫖准则**，细节看 DESIGN，不在此复写。

反白嫖三道锁（都收口到 `RiskState` + `RiskPromptController`，红线#9 一个弹窗源）：

1. **防封授权闸 `isAntiBanReady()`（时间闸，只门控 A2 防封，不碰隐私功能）**：首装宽限内授权 → 防封开；从未授权 + 超阈值 → **防封散沙（卸 A2）→ 官方包判非官方 → 号被平台封**。用「被封」反制白嫖，模块不自爆、不留痕、不删数据（红线#5/#6）。闸**不是客户端布尔**（红线#1），吊 `EnvelopeStore` + `LeaseClock`（红线#3，不信墙钟）。⚠️ 阈值以配方卡为准：T_soft=1h 软引流 / T_login=2h 登录砸门（只挡隐私、A2 不撤）/ T_kill=影子期7天+不续命+服务器抖动（A51 去固定短散）。
2. **A2 料锁进加密 registry（防破解 = 防封同一把锁）**：official DER / SSAID 参数 / 官方包名进 `registry_pack`，只有服务器种子 + 验签信封 + `decrypt_config()` 成功才解得出；盗版无种子 → 解不出 → 防封自动散沙（fail-closed，不回退明文）。
3. **载荷弹窗 canary（删弹窗自反噬）**：引流弹窗**稳定核心段** hash 掺进 A2 料 key 派生（USE 不 COMPARE，仿 `CompatProbe.BASELINE`）→ 删/改弹窗 → key 错 → 防封散沙 → 被封。想白嫖就得留着弹窗。坑：每版改弹窗须同源重生成加密料（同 §A.5 共享常量禁区），只把稳定段算进 hash。

边界（诚实）：客户端 APK 防不住被反编译/改（视为预期威胁）；真锁是服务器种子 + 短命租约 + 设备绑定，上面三道是「让白嫖代价 = 被封 + 删弹窗自废」的加固，非无敌。隐私功能走原有 DRM（`isConfigReady`/`isActive` + registry 加密），**不进**这个时间闸——两闸独立、各管各能力，只共用 RiskState/弹窗。

## S3b 压测后硬锁收口（2026-06-12）

背景：V1.2 LSPatch 包经外部 AI 反编译压测，约 10 分钟内定位到
`resources/assets/lspatch/modules/com.ghost.assist.apk`、`libguardcore.so`、
`NativeBridge`、`GuardRuntime`、`server_seed` / recipe 关键字，并尝试走
`nativeIsAuthorized()` / 自建服务器 / 替换公钥三类绕过路线。

判定口径：

- 能反编译、能抽出模块、能看到 `NativeBridge` 都是预期威胁，不算破防。
- 只 hook `nativeIsAuthorized()` / `nativeGetAuthState()` / UI 授权显示，不算破防。
- 真正破防标准只有一个：无合法 envelope / 无 S_rel / 无有效签名时，仍能让
  `GuardRuntime.getRecipe()` 返回真实 registry recipe，并让业务 hook 正常命中。
- 任何安全结论必须围绕 `recipeOk`、`registrySummary != scatter`、
  `GuardRuntime.isConfigReady()` 和关键 gateway 的 `getRecipe()` 结果展开。

V1.2 暴露的实际收口项：

- **Release/PROD 禁止明文 fallback。** 业务 Filter 在 `GuardRuntime.getRecipe()`
  返回空时，必须 fail-closed：跳过该 hook / 该功能不可用；不得继续使用 Java
  字面量类名、方法名、字段名保持功能可用。
- DEV/DEBUG 可以保留 fallback 方便开发，但必须由 `BuildConfig.DEBUG` 或显式
  dev flag 控制，不能混入客户 release 包。
- `ConvFilter`、`SearchFilter`、`ContactFilter`、`MomentsFilter` 是首批必查
  Filter；新增 Filter 默认按 release fail-closed 设计。
- `NativeBridge.isAuthorized()` 不是根锁，不得把它当作唯一防线；根锁必须是
  envelope 验签 + server seed 解封 + registry 解密 + recipe 出口。
- Debug 面板、Phase 文案、`record-only` 日志、`cp-*` 状态词会给逆向者路线图；
  release 包必须删除、降噪或伪装，不得暴露真实阶段和真实网关名。
- `EncryptedConfigLoader` 必须逐步接入 LeaseClock / RiskState；过期、篡改、
  包名/证书不符时应 scatter，而不是只靠 UI 或日志提示。

加密官审查时必须问：

```text
没有服务器 envelope / S_rel 时：
1. registrySummary 是否仍是 scatter？
2. GuardRuntime.isConfigReady() 是否为 false？
3. conv.list / search.gateway / contact.address / moments.feed 是否都拿不到真实 recipe？
4. 业务 Filter 是否停止安装敏感 hook，而不是 fallback 到 Java 明文？
5. release APK 内是否还存在可直接搜索的核心 hook 类名/字段名？
```

任一答案不满足，禁止称为“真锁完成”，只能标记为“迁移期加密链路”。

## 签名策略

优先使用：

- Ed25519，或 ECDSA P-256
- 服务器私钥签名
- 客户端只放公钥验签

禁止把固定共享 secret 放 Java 层后用 MD5/SHA1/HMAC 当根锁。HMAC 可以作为附加摩擦，但不能作为唯一根锁。

## 加密配置

只加密最值钱的核心配方，不做全仓库字符串加密。

加密对象建议：

- 核心类名
- 核心方法名
- 核心字段名
- 微信版本对应 registry
- 少量 feature/risk flag

要求：

- AES-GCM
- 每份配置独立 nonce
- tag 校验失败即失败
- SO 提供 `decrypt_config()`
- 解不开返回散沙配置，不崩、不全开

## Key 来源准则

AES key 不得是 SO 里的静态明文常量。

允许的 key 派生材料：

- 服务器短命 envelope 内的 key material 或 key id。
- app 签名 hash / package hash。
- device/customer 派生摘要。
- schema id / nonce / issued_at。
- SO 内部多段常量拼接和轻量变换。

准则：

- 服务器材料必须参与最终 key 派生；没有服务器短命材料，不得解出真 registry。
- 本地材料只用于绑定和增加静态分析成本，不能替代服务器材料。
- 不追求复杂白盒密码，但要避免 IDA 一眼看到固定 key。
- Frida hook `decrypt_config()` 出参仍是高级威胁，防线重点是短命租约、设备绑定、risk 记录和服务端轮换。
- ⚠️ **当前已知违规（待收口，2026-06-24 核实）**：解服务器信封 `k→S_rel` 的 wrapping key `W`（`config_crypto.cpp` 的 `g_wk_lo`/`g_wk_hi`）现仍是**全局静态明文常量**，违反本准则——抽一台 SO 的 W 可离线解任意设备的合法信封。修复 = W 一机一密 + 重放绑定（合并设计稿 `03_execute_执行任务/P_RB1_重放绑定_ReplayBind/钥匙加固_KeyHardening设计.md`：`W_dev=KDF(本地段+设备材料)` + 已验签摘要折入 key），📄 仅设计未落地，见「阶段计划」第 9/10 项。

## 三端钥匙派生镜像对账（维护铁律 · F-31 静默翻车重灾区）

> 触发：用户问「写进 SO 黑盒方便维护吗」（2026-06-24）。结论已写死在「加密 hook 名粒度与单一真源」节：**维护难易 = 是不是单一真源，与代码在 Java 还是 SO 无关**。本节把「钥匙派生」这条最贵的维护税单列成铁律——它此前只散在设计稿（已并入 `P_RB1_重放绑定_ReplayBind/钥匙加固_KeyHardening设计.md`），未进 skill。

**铁律**：任何 key 派生算法（`derive_registry_key` / `derive_bootstrap_key` / 未来 `derive_wrap_key`(W_dev) / 摘要折入）一旦改动，**Java（如有）/ SO（`config_crypto.cpp`）/ 生成脚本（`tools/gen_*_cipher.py`）三处必须逐字节一致**，否则正版机解不开 → registry 散沙 → 已装机密友隐藏**静默全挂、无报错无崩溃**（F-31）。

落地要求（缺一即 BLOCK）：

1. **单一真源 + 自动生成**：能从一份源生成的（registry / 字段表 / 域名表）一律构建期生成，禁止手抄第二份。
2. **KDF 测试向量自动对账**：派生算法改动必须先出「固定输入 → 期望输出」测试向量，SO 自测（DEBUG-only，勿编进 release SO）+ 脚本单测 +（如有）Java 单测三处断言同一结果；向量不过禁止改 `unwrap` / `derive`。
3. **域分离向量**：不同用途的段常量（registry key 的 `seg_*` vs wrap key 的 `wseg_*`）必须断言互不相等，防误用削弱安全。
4. **改 key 派生 = 全发行线重生成 cipher + 装机回归**：每个 `release_id`（官替 / 共存）各自重生成 `registry_cipher.inc` 并验 `recipeOk=true` + 密友隐藏不挂（呼应「发布 / 共存版加密铁律」）。

一句话：**好不好维护不取决于代码在 Java 还是 SO，取决于「钥匙派生只有一份真源 + 三端用测试向量自动对账」。做到这条，下沉 SO 才安全可维护；做不到，写哪都是 F-31。**

## 字段伪装

字段名可以短名化和轮换，例如：

- `n2Q`
- `Kd1`
- `x7m`
- `r0`
- `b6`

要求：

- 不出现 `vip` / `auth` / `license` / `endtime` / `config` 等明牌词。
- 使用 `schema_id -> 字段映射`。
- 可按版本、月份、客户批次轮换。
- 老 schema 保留 7 到 14 天，避免服务器波动误伤正版。

字段伪装只是迷彩，不是加密。真正安全来自签名和密文。

## 混淆与上线维护准则

开发期保持可读、可测、可审查；上线前由构建脚本生成混淆产物。

禁止：

- 单独拷贝一份“混淆版源码”。
- 手工维护两份 registry 或两份字段表。
- 在业务代码里一次性搜索替换字段名、类名、方法名。
- 把混淆表散落到多个目录导致漂移。

推荐：

1. 一份源码，一份明文 registry 源。
2. release 构建时脚本生成 schema mapping、字段伪装表、encrypted packs。
3. 构建产物归档 mapping，便于线上问题回溯。
4. old schema 保留 7 到 14 天兼容，避免服务器波动误伤正版。
5. 混淆只改变“外观和分发形态”，不改变业务语义。

准则：

```text
开发期可读；上线前生成；产物可追；源码不分叉。
```

## LeaseClock

不得直接用 `System.currentTimeMillis()` 判死刑。

必须记录：

- 上次成功服务器时间
- 上次成功本机 `elapsedRealtime`
- 当前 `elapsedRealtime`
- 租约过期时间
- 上次成功心跳时间
- 历史最大可信时间水位线 `max_trusted_now`
- 上次启动标识 `boot_marker`

离线可信时间：

```text
trusted_now = last_server_now + (current_elapsedRealtime - last_elapsedRealtime)
```

重启漏洞处理：

- Android `elapsedRealtime` 在设备重启后会重新计数。
- 若 `current_elapsedRealtime < last_elapsedRealtime`，必须判定发生过重启或计时异常。
- 重启后不得用负数或异常 delta 延长租约。
- 重启后离线状态最多保持 `last_server_now` 或 `max_trusted_now`，并进入 `TIME_SUSPICIOUS` 要求联网重校。
- 持久化 `max_trusted_now`，任何离线推算不得低于历史水位后再倒退延长授权。
- 可记录 boot marker：如系统 boot count、开机时间摘要、首次启动 elapsed/wall 组合；拿不到可靠 boot id 时，以 elapsed 回绕作为重启证据。

手机墙钟前跳、回拨、跳变，只标记 `TIME_SUSPICIOUS`，不得单独触发永久引流；但与断网超过 24h、elapsed 回绕、蜜罐命中叠加时，可升级风险等级。

## RiskLevel

统一使用等级，不允许各模块散写 if。

| 等级 | 名称 | 触发 | 表现 |
|---|---|---|---|
| L0 | `CLEAN` | 签名通过、租约有效、配置解开、无风险 | 正常 |
| L1 | `OFFLINE_WARN` | 接近 24h 未心跳，或轻微网络异常 | 可关闭提醒，功能正常 |
| L2 | `TIME_SUSPICIOUS` | 断网 1 天 + 时间异常流逝 | 弹窗要求联网，功能暂可用 |
| L3 | `DEGRADED` | 超过 72h 未心跳，或多次时间异常 | 散沙降级，不清数据 |
| L4 | `TAMPER_SHADOW` | 命中蜜罐，如改 vip/假锁/假缓存 | 24 到 48h 影子成功，表面可用 |
| L5 | `TAMPER_FUNNEL` | 蜜罐命中超过影子期 | 打开软件即弹，点确定仍可用 |
| L5R | `PROBATION` | 篡改用户购买后，服务器签名强校验通过 | 试用观察期，可用但保留风险观察 |
| L6 | `TAMPER_PERSISTENT_FUNNEL` | 重复篡改、删除标记后又命中、服务器确认风险 | 每次打开/回前台弹，短冷却 |

## 断网与时间异常策略

- 断网不足 24h：继续使用缓存配置。
- 断网超过 24h：提醒联网。
- 断网超过 24h 且时间异常：进入 `TIME_SUSPICIOUS`，弹窗但不清数据。
- 断网超过 72h：进入 `DEGRADED`，开始散沙。
- 联网成功且服务器确认正常：可恢复到 `CLEAN`。

不要使用固定 10 天锁定策略。安全默认口径是确认蜜罐命中后按 24 到 48h 影子期进入长期引流；具体影子期可由服务端 risk 参数调节。**Guard Native 当前代码有项目级 override**：用户 2026-06-12 拍板盗版转卖场景影子期为 7 天，落点 `RiskState.SHADOW_HOURS_DEFAULT=168h`；后续应改为服务端签名 `risk_pack` 下发，不再硬编码。

## 蜜罐影子期引流策略

蜜罐命中后进入 `TAMPER_SHADOW`。

影子期安全默认 24 到 48 小时；本项目当前发布代码为 7 天 override（见上）：

- 表面显示已激活。
- 功能可以继续使用。
- 不弹窗，不暴露蜜罐。
- 记录 `tamper_first_seen`。
- 足够骗过初步逆向测试，但不让白嫖版本长期传播。
- 若确有运营原因需要更长影子期，必须在审查报告里说明风险；当前 7 天为已落地硬编码，下一步应迁到服务端签名策略。

影子期结束后进入 `TAMPER_FUNNEL`：

- 打开软件即弹窗。
- 回到前台可弹窗。
- 打开设置页/授权页可弹窗。
- 点确定后继续可用。
- 点确定后只给短冷却，默认 10 到 60 秒。
- 冷却结束后，下次前台/启动/设置页事件继续弹。
- 不使用 10 天锁定策略。

重复篡改进入 `TAMPER_PERSISTENT_FUNNEL`：

- 每次冷启动弹。
- 每次回前台弹。
- 每次打开设置/授权相关页面弹。
- 点确定后短暂消失，但风险态不清除。

### 转正与解封闭环

`TAMPER_FUNNEL` / `TAMPER_PERSISTENT_FUNNEL` 的目标是引流转正，不是永久拒绝付费用户。

规则：

- 用户完成购买后，必须联网做服务器强校验。
- 只有服务器返回带签名的 `risk_reset` / 新租约 / 新 registry，客户端才允许降低本地风险等级。
- 本地按钮、清缓存、改时间、删文件不得清除风险态。
- `TAMPER_FUNNEL` 可在强校验通过后恢复到 `CLEAN`。
- `TAMPER_PERSISTENT_FUNNEL` 可先降到 `PROBATION` 或 `OFFLINE_WARN`，观察一段时间后再恢复 `CLEAN`。
- 重复篡改设备可要求换 key id、换 schema、重新绑定 device/customer。

准则：

```text
本地不能自洗白；服务端签名强校验可以转正。
```

实现要求：

- 允许多个触发点。
- 禁止多份隐藏弹窗逻辑。
- 所有触发点必须调用同一个 `RiskPromptController.maybeShow(reason)`。
- 所有等级判断必须来自 `RiskState`。
- 不清用户数据，不破坏微信本体，不影响正版用户恢复。

## 功能失效策略

功能失效靠核心能力拿不到正确配方，不靠崩溃、删数据或破坏微信。

推荐失效点：

1. Registry 失效：`EncryptedConfigLoader` 返回空 registry 或降级 registry。
2. 中央网关失效：`StateMachine.isActive()` 依赖租约、配方、RiskLevel。
3. 数据源失效：过滤层拿到空列表、乱码列表或过期快照，用户原始数据不清除。
4. 策略失效：`TAMPER_FUNNEL` 后 UI 可点，但核心过滤链不给完整能力。

统一出口建议：

```text
GuardRuntime.getActiveRegistry()
```

它根据 `LeaseClock + EncryptedConfigLoader + RiskState` 返回正常 registry、缓存 registry、降级 registry 或空 registry。

## 防重放

租约必须绑定：

- device id 派生值
- customer id
- app version
- package hash
- cert hash
- issued_at
- expire_at
- nonce
- key id
- schema id

客户端必须拒绝：

- 过期响应
- 未来过远响应
- 设备不匹配响应
- 包签名不匹配响应
- 已淘汰 schema 响应
- encrypted_config hash 与签名不一致响应

## 阶段计划

1. ✅ 安全官 skill 落盘：规则写死，后续所有 SO/加密/风险改动按它审查。
2. ✅ SO 现状体检：现有 `libguardcore.so` / C++ auth 已盘点。
3. ✅ Phase 1A 本地加密原型：AES-GCM 测试向量 + `decrypt_config()` + JNI 调用，不接业务。（装机 PHASE1A_VERIFY PASS）
4. ✅ Phase 1B Registry 抽取：核心 hook 配方抽成 registry，明文跑通。（PHASE1B_VERIFY PASS）
5. ✅ Phase 1C Encrypted Registry：`registry_8071.json` 单一源 → `registry_cipher.inc`，SO 解密 registry，失败散沙；搜索收敛 `search.gateway`；另含 1D-local 派生 key（去明文 key 常量）+ A-step2 证书绑定。（PHASE1C/1D_VERIFY PASS）
6. 🟡 **Phase 1C.5 Filter 读 registry（消明文双份，任务名 `P1E_Filter读Registry`）**：让 ContactFilter/MomentsFilter/ConvFilter 真从 registry 读类名（取件口 `GuardRuntime.getRecipe`+`nativeGetRecipe`），conv.list 漂移债已结案；现为 registry+fallback 双份，**删 fallback 未做**（每个 Filter 删前要先核账 registry 完整性）。SearchFilter 暂未迁。⚠️ 注意：这里的任务名 P1E ≠ 下面第 8 条的 Phase 1E。
7. ✅/🟡 **Phase 1D-server（S2/S3a/S4/S3b，2026-06-11）**：miyou-server 已下发 Ed25519 signed envelope；客户端 `EnvelopeClient` / `AuthEnvelopeVerifier` / `EnvelopeStore` / `GuardHeartbeat` / `GuardActivation` 已形成 v1.1 授权闭环。当前 `android_8071` 已切 `prod_server_lock`：`registry_cipher.inc` 为 `GUARD_REGISTRY_REQUIRES_SERVER_SEED=1`，线上 envelope `k/n` unwrap 后 `recipeOk=true`。现状权威 + 下一受控步骤见 `PROTECTION_MAP.md` §10.6。
8. 🟡 **Phase 1E LeaseClock + RiskState（P1F 本地一刀 + 两闸，S3b 已推进）**：`LeaseClock` 已接信封授时并用于到期判定；设置页断网 >72h 强制重验，失败撤销授权但保留 token 自愈；`RiskState` 主链仍偏 record-only，但来电拦截已接 `RiskState.isTamperDegraded()` 单点散沙例外；全链路散沙降级与正版恢复闭环仍未完成。
9. 📄 **Phase 1F W 一机一密（`P_RB1_重放绑定_ReplayBind/钥匙加固_KeyHardening设计.md`，仅设计未落地）**：wrapping key W 从「全局静态明文 `g_wk_lo`/`g_wk_hi`」改为 `W_dev = KDF(本地三段 wseg + 设备材料 SHA-256(ANDROID_ID))`，服务器按设备上报 `dm` 逐设备 wrap `S_rel`。收「抽一把 W 离线通杀所有设备信封」。必走 3 步灰度（双试 → 按设备切 → 删全局 W），缺迁移方案即全量正版散沙。落地受「三端钥匙派生镜像对账」铁律约束。
10. 📄 **Phase 1G 重放绑定 P_RB1（`P_RB1_重放绑定_ReplayBind/钥匙加固_KeyHardening设计.md`，仅设计未落地）**：把已 Ed25519 验签的 envelope 摘要（expire_at / device / release / digest）折进 `derive_registry_key`，让过期 / 重放的旧 `k/n` 推出错 key → 散沙，短租约在 SO 层才真正有牙。优先级 P1（设备绑定已堵转卖，本项属加固）。

## 当前 SO 基线与工作量

- 当前 `libguardcore.so`：在 Phase 0/Batch1 骨架（加载/JNI/进程角色/隐藏状态机/wxid 匹配/轻量包名/config_version）之上，**已加** `decrypt_config()` + AES-GCM + encrypted registry + 派生 key（去明文 key 常量）+ 证书绑定 + `registry_get_recipe`（取件口）。
- **仍不是安全官标准真锁终局**：`nativeIsAuthorized()` / `nativeGetAuthState()` 仍是占位；`StateMachine.isVipAuthorized()` 已接 `EnvelopeStore.isAuthorizedNow()`；当前 `android_8071` 已用 server seed 解 registry。**缺**：共存版 `GUARD_RELEASE_ID` flavor 注入、删剩余 Filter 明文字面量 / fallback 债、V3 官替/共存证书源完整对齐、RiskState 全链路散沙降级与正版恢复闭环。对外不得宣称“授权无法破解”或“服务器真锁终局完成”。
- 最低可用真锁：约 10 到 18 人天。范围：AES-GCM 测试向量、`decrypt_config()`、3 到 5 个核心 registry 抽取、`EncryptedConfigLoader`、基础 signed envelope、解不开散沙。
- 上线标准：约 20 到 35 人天。范围：验签、防重放、device/customer/package/cert 绑定、LeaseClock、RiskState、蜜罐影子期、正版恢复闭环、release 混淆和装机回归。
- 最小可用版：300 到 500 行 C++。
- Phase 1 正式版：800 到 1200 行 C++。
- 带完整自检、签名、AES-GCM、Risk hint、JNI 包装、测试向量：1200 到 1800 行 C++。

SO 只管“验真 + 解密 + 关键风险信号”；弹窗、影子期倒计时、冷却、断网宽限主要放 Java/Kotlin。

**黑盒化边界（写死，2026-06-24）**：想「写进 SO 黑盒」时，**能下沉的只有钥匙层**——key 派生、验签、解密、设备 / 摘要折入（如 `W_dev`、重放摘要）；**不该下沉的是策略与表现层**——弹窗、影子期、冷却、时间宽限、RiskLevel 表现，留 Java（好调试、可热改、崩溃可控）。把策略也塞进 SO = 每改一次都要 NDK 重编 + 难调崩溃 + 放大三端漂移，反而最难维护。黑盒抬高的是「静态分析成本」；真锁仍靠服务器短租约 + 设备绑定（Frida 动态仍能 dump 解密结果，SO ≠ 不可破）。

## 后续 AI 接手快照

- Phase 1A 已装机验收：logcat 出现 `[native] BATCH1_VERIFY PASS` 与 `[native] PHASE1A_VERIFY PASS`。
- 已有：`decrypt_config()` AES-GCM 原型、固定测试向量、JNI 包装、`NativeBridge` 自测入口、失败 scatter。
- 不要重写 SO、不重写 AES-GCM、不换 crypto 依赖；除非有明确编译失败、验收失败或安全缺陷证据。
- **加密主线已推进到 P1E（Filter 读 registry，2026-06-09）**：1B 抽取 → 1C 加密 registry + search.gateway 收敛 → 1D-local 派生 key（去明文 key 常量）→ A-step2 证书绑定 → P1E 让 ContactFilter/MomentsFilter/ConvFilter 真从 registry 读类名（取件口 `GuardRuntime.getRecipe` + `nativeGetRecipe`）+ conv.list 漂移债结案；均装机 PHASE1A-1E PASS。
- **P1F 两闸 + 风险骨架已落地（2026-06-10，装机 PASS）**：`LeaseClock` 骨架 + `RiskState`（record-only L0~L6）+ `RiskPromptController`（唯一弹窗）+ kill↔funnel 拆两闸；web 驾驶舱同步显示 风险等级/停用闸/引流。详见 `07_archive_归档/P1F_十字防护整合设计/`（DESIGN + worklog）。
- **真锁现状（2026-06-11）**：S2/S3a/S4/S3b 已推进到授权码 → token → Ed25519 envelope → AuthGate → server seed 解 registry；`StateMachine.isVipAuthorized()` 不再是 stub。仍未收口：删 Filter fallback、V3 发行线对齐、RiskState 真降级。对外不得宣称真锁终局完成。
- 不改已验证 hook 回调体；授权/状态机边界仍归授权检查官共同审查。
- 业务本体是“密友/密群隐藏 + 通知/红点/搜索/朋友圈等过滤链”，不是通用 DRM demo；registry 抽取必须服务这些已验收链路。
- 安全官只维护安全/加密/DRM/风控路线；授权/状态机/模块边界仍归 `guard-auth-review_授权检查官`。不要把同一策略复制到授权检查官 skill。

## 发布 / 共存版加密铁律

触发关键词：`发布`、`发版`、`签名`、`keystore`、`共存版`、`官替版`、`registry_cipher`、`_CERT_SHA256`、`guardWxPkg`。

- 先读 `docs/RELEASE_RULES.md` 的「双版本发布手册」和「加密接手清单」。
- 官替版与共存版是两条独立发行线：各自固定 `packageName`、keystore、`versionCode`、`release_id`；互不覆盖。
- encrypted registry 发版必须同源更新：`registry_8071.json` → `gen_registry_cipher.py` → `registry_cipher.inc` → `PHASE1C/1D/1E_VERIFY PASS`。
- 生成 cipher 时的签名证书 SHA-256 必须等于运行时 binding material；官替版/共存版签名不同就必须分别生成，不得混用。
- Java 白名单、Xposed scope、C++ `GUARD_EXPECTED_PACKAGE`、服务器 `release_id` 必须来自同一包档案；不能只改 C++。
- 业务 hook 仍生效但 `PHASE1D/1E` 失败时，可能只是 fallback 在兜底；禁止宣称加密链路通过。
- 当前可宣称“v1.1 商业授权闭环 + Ed25519 防伪造信封 + 当前发行线 server seed 解 registry 已接入”；删 fallback / V3 发行线 / RiskState 真降级完成前，禁止宣称“授权无法破解”或“服务器真锁终局完成”。

## 安全任务收尾铁律

凡是触碰 SO / AES-GCM / `decrypt_config()` / `registry_cipher` / `NativeBridge` native 自测 / 授权信封 / `RiskState` 的任务，安全官必须主动收尾，不等用户记得提醒。

收尾必须完成三件事：

1. PASS 日志落盘
   - 必须有终端直采 logcat / build / 脚本输出原文。
   - 用户口述“看到 PASS”只能记为待补证据，不能当 L1。
   - 没有日志文件时，安全官必须给终端配合员一条命令去抓。

2. 单一工作文档更新
   - 只更新当前安全 P 任务的 `worklog.md`。
   - 不把同一结论复制到 `HOOKMAP.md` / `TASK_BOARD.md` / 多个 `result.md`。
   - 总览文档只保留一句话 + 链接，避免散落四处。

3. 发布前安全摘要
   - 必须写清：当前做到哪一阶段、哪些 PASS、哪些只是占位、哪些不能对外宣称已完成。
   - 对当前加密主线的发布口径：P1E = AES-GCM encrypted registry + Filter 真读 registry（Contact/Moments/Conv）+ 派生 key + 证书绑定；P1F/S3b = `LeaseClock` 服务器授时 + `RiskState` record-only L0~L6 + `RiskPromptController` 唯一弹窗 + kill↔funnel 拆两闸；S4 = Ed25519 信封验签；S3a = 当前 `android_8071` `prod_server_lock` 发行线 server seed 解 registry。**仍未完成**：删 Filter fallback、V3 官替/共存发行线对齐、RiskState 真散沙降级与正版恢复闭环，registry+fallback 双份明文未完全消除。

当前 P1E 收口口径（2026-06-09）：

- 加密主线推进到 `P1E_Filter读Registry`：取件口（`GuardRuntime.getRecipe` + `nativeGetRecipe`）+ ContactFilter/MomentsFilter/ConvFilter 真从 registry 读类名 + conv.list 漂移债结案。
- 现为 **registry + 旧常量 fallback 双份**：registry 拿不到/散沙时回退旧值，明文双份未完全消除（删 fallback 前每个 Filter 都要先核账 registry 完整性）。SearchFilter 暂未迁（粗粒度收益有限）。
- 发布前必须补抓当前手机好包的 `NCL` 日志。
- 必须看到 `BATCH1_VERIFY PASS`、`PHASE1A~1E_VERIFY PASS`、`registrySummary ... entries=4`、各 Filter `recipes ... fallbackSelfTest=ok` 后，才可把证据追加到 `07_archive_归档/P1E_Filter读Registry/worklog.md`。
- P1A~P1D 作为历史记录，不再重复写同一发布结论。

### ⚠️ ss4.p 教训（registry 必须跟真活跃锚点一致，迁前先对账）

- registry 加密的是「配方」；若配方里写的是**过期/废弃/证伪的锚点**，加密了也是演戏（真锚点仍明文，或迁了死路径导致功能失效）。
- 实证（2026-06-09）：SearchFilter 代码一句过期注释「TRUE PRIMARY: ss4.p」误导执行窗口把废弃路径 ss4.p（父容器 GONE、onBindViewHolder 0 命中）当主锚点加进 registry；装机 logcat 实证真路径是 `f0.getView`（`[SF:gv]` ×29），ss4.p `[SF:ss4p]` 0 次。已回滚并删除 ss4.p 死代码。
- 铁律：**迁任何 Filter 读 registry 前，先对账（代码常量 + HOOK_MAP_8071 + 必要时 L1 装机日志 三证），确认每个锚点今天真活跃；任一吃不准就停，禁止照过期注释/草稿迁。** registry 是子集时（如 conv.list 当初 contact_fields 缺 f/a/b/c），删 fallback 前必须先补全核准。

## 脚手架同步、公告和版本门控

- 项目已有 P15 脚手架、`NativeBridge`、`AppConfig`、`OverlayWindow`、`DebugServer`、`sync_skills.ps1`；skill 主源在 `.cursor/skills`，同步到 `.claude/skills`。
- 本地版本来自 Gradle `versionCode` / `versionName`；远程版本、发布公告、危险通告、强制升级、`kill_switch` 必须并入 P29 miyou-server 接入。
- 公告系统不得做裸 HTTP 文本开关。客户端只接受带签名的 notice envelope，至少包含 `notice_id`、`latest_version`、`min_supported_version`、`release_notice`、`danger_notice`、`kill_switch`、`cs_url`、`shop_url`、`issued_at`、`expire_at`、`nonce`、`signature`。
- 发布公告只按 `notice_id + versionName` 弹一次；危险通告和 `kill_switch` 优先级更高，但必须可验签、可缓存、可恢复，避免误伤正版用户。
- 版本门控要和安全信封共用验签/防重放能力；不要在业务 UI、DebugServer 或本地偏好里另起一套隐藏策略源。

## 改前审查模板

```markdown
【安全与加密官改前审查】

结论：PASS / WARN / BLOCK

1. 是否触碰授权根锁：
2. 是否触碰 SO 解密：
3. 是否触碰服务器 envelope：
4. 是否触碰 LeaseClock：
5. 是否触碰 RiskState：
6. 是否可能误杀正版：
7. 是否会影响已验证 hook：
8. 是否需要授权检查官共同审查：
9. 允许修改的文件：
10. 禁止修改的文件：
```

## 改后审查模板

```markdown
【安全与加密官改后审查】

结论：PASS / WARN / BLOCK

1. Java/SO/服务器交叉校验是否仍成立：
2. 签名验真是否不可被客户端伪造：
3. encrypted_config 解密失败是否散沙而非全开：
4. 断网/时间异常是否有宽限：
5. 蜜罐是否影子成功期后再引流：
6. 打开即弹是否只针对确认蜜罐风险态：
7. RiskLevel 是否统一出口：
8. 是否没有清用户数据：
9. 是否没有破坏微信本体：
10. 需要补的测试：
```

## 最小测试清单

- 正常联网授权成功。
- MITM 改字段后签名失败。
- encrypted_config 改 1 字节后解密失败。
- Java `isVip` 被 hook 后仍拿不到真 registry。
- SO 无有效 envelope 时返回散沙 registry。
- 断网 23h 仍可用。
- 断网 24h + 时间异常进入提醒。
- 断网 72h 进入降级。
- 手机时间回拨不延长授权。
- 手机时间前跳不立即误杀。
- 改假 vip 进入 `TAMPER_SHADOW`。
- 蜜罐影子期后进入 `TAMPER_FUNNEL`。
- `TAMPER_FUNNEL` 打开软件即弹，点确定后短冷却。
- 重复篡改进入 `TAMPER_PERSISTENT_FUNNEL`。
- 联网恢复后正版可恢复。
