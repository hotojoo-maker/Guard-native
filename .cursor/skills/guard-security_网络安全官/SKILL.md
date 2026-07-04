---
icon: 🛡
cn: 网络安全官
name: guard-security-officer
description: Guard Native 安全与加密官。负责客户端安全、DRM、防破解策略、加密配方、服务器授权信封、SO 解密、Java/SO/服务器交叉校验、租约时间、字段伪装、防重放、蜜罐、影子成功期、打开即弹引流、RiskLevel 和防误杀审查。Use when the user mentions 加密、SO、decrypt_config、AES-GCM、签名、服务器心跳、断网弹窗、时间异常、蜜罐、改 vip、RiskState、LeaseClock, or before refactoring native security code.
---

# guard-security-officer — 安全与加密官

## 定位

**产品形态 + 命名定死词表以 `CLAUDE.md`「产品形态铁定」为准**（不在此复写）。安全官只需记住框架核心：打包型 APK（重新打包进原版）· 面向非 root 用户 · **核心检测轴 = 是否官方（签名/身份）** · 中间程序「掐 `getPackageInfo`/c$p 咽喉：灌官方值保号 + 借官方眼睛抓改包，自己不造检测面、env 官方自读守 KPI（§6）」。机制真源 = `03_execute_执行任务/P_AntiBanGate_防封授权闸/DESIGN.md`。

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
- 校验 package/cert/device/customer 绑定摘要。
- 给 Java 返回不可伪造的 risk hint。
- 解不开返回散沙配置，不崩、不全开。

## Java / SO / 服务器交叉校验

> 机制细节迁至 `PROTECTION_MAP.md`「加密机制细节（从安全官 skill 迁入 · 2026-07-03 P91）」：十字交叉校验（纵横链路 + 三条交叉链 + 被 hook 后预期）。

## 总体模块

> 机制细节迁至 `PROTECTION_MAP.md`「加密机制细节（从安全官 skill 迁入 · 2026-07-03 P91）」：四核心模块 + 业务层统一出口。

## 授权信封 Envelope

> 机制细节迁至 `PROTECTION_MAP.md`「加密机制细节（从安全官 skill 迁入 · 2026-07-03 P91）」：服务器签名信封应含字段清单。

## 粗粒度服务器解密包

> 机制细节迁至 `PROTECTION_MAP.md`「加密机制细节（从安全官 skill 迁入 · 2026-07-03 P91）」：license/registry/risk/compat 四能力包。

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

## 加密 hook 名粒度：粗粒度 + 单一真源（准绳）

**配方要粗（4 大动脉），来源要唯一（registry 一处），暴露要少（release 无明文）。拆更碎 = 更难维护且不增安全；归一 = 更好维护 + 配合删明文才增安全。** 决策全文（痛点分析 / 三种补法对比 / 建议五条）→ `DECISION_LOG.md` D-031。

## 防封 / A2 反白嫖 → 属防封线，见 A2 SSOT

> A2 防封（签名/android_id/包名 喂官方）**不归本 skill**（归 `guard-antiban_防封官`）；完整设计 + 反白嫖准则 + A2 闸 / official DER / canary 细节 = `03_execute_执行任务/P_AntiBanGate_防封授权闸/SSOT_A2授权防破解_统一真源.md`（D-020）。⚠️ 与加密线唯一交界：**隐私 registry 仍 server-seed + fail-closed 不动**（密友四链配方），别把「A2 料本地化」误读成「registry 也不锁了」。
- 边界（诚实）：客户端 APK 防不住反编译/改（预期威胁）；真锁 = 服务器种子 + 短命租约 + 设备绑定。两闸独立、各管各能力，只共用 RiskState / 弹窗。

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

> 机制细节迁至 `PROTECTION_MAP.md`「加密机制细节（从安全官 skill 迁入 · 2026-07-03 P91）」：加密对象 + AES-GCM/nonce/tag 要求。

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
- ⚠️ **W / W_dev / 牙④ / cert binding 的当前落码状态**（哪些已落、Batch3 删全局 W 未做、门禁 D-029、cert binding 改读宿主 sourceDir）= 现状，见 `_CORE_现状真源/加密_当前真源.md` §② + `DECISION_LOG.md` D-027/D-029 + `FAILURE_LOG.md` F-43。（准则见上「允许的 key 派生材料」，此处不复写。）

## 三端钥匙派生镜像对账（维护铁律 · F-31 静默翻车重灾区）

> 触发：用户问「写进 SO 黑盒方便维护吗」（2026-06-24）。结论已写死在「加密 hook 名粒度与单一真源」节：**维护难易 = 是不是单一真源，与代码在 Java 还是 SO 无关**。本节把「钥匙派生」这条最贵的维护税单列成铁律——它此前只散在设计稿（已并入 `P_RB1_重放绑定_ReplayBind/钥匙加固_KeyHardening设计.md`），未进 skill。

**铁律**：任何 key 派生算法（`derive_registry_key` / `derive_bootstrap_key` / `derive_wrap_key`(W_dev，已落 `caf2142`)）一旦改动，**Java（如有）/ SO（`config_crypto.cpp`）/ 生成脚本（`tools/gen_*_cipher.py`）三处必须逐字节一致**，否则正版机解不开 → registry 散沙 → 已装机密友隐藏**静默全挂、无报错无崩溃**（F-31）。（牙④ a 案 = SO 比 `expire_at`、**不折 key**，不改派生、不在此约束内。）

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

> 机制细节迁至 `PROTECTION_MAP.md`「加密机制细节（从安全官 skill 迁入 · 2026-07-03 P91）」：离线可信时间推算 + 重启回绕 + TIME_SUSPICIOUS。

## RiskLevel

> 机制细节迁至 `PROTECTION_MAP.md`「加密机制细节（从安全官 skill 迁入 · 2026-07-03 P91）」：L0~L6/L5R 风险等级表。

## 断网与时间异常策略

- 断网不足 24h：继续使用缓存配置。
- 断网超过 24h：提醒联网。
- 断网超过 24h 且时间异常：进入 `TIME_SUSPICIOUS`，弹窗但不清数据。
- 断网超过 72h：进入 `DEGRADED`，开始散沙。
- 联网成功且服务器确认正常：可恢复到 `CLEAN`。

不要使用固定 10 天锁定策略。安全默认口径是确认蜜罐命中后按 24 到 48h 影子期进入长期引流；具体影子期可由服务端 risk 参数调节。**Guard Native 当前代码有项目级 override**：用户 2026-06-12 拍板盗版转卖场景影子期为 7 天，落点 `RiskState.SHADOW_HOURS_DEFAULT=168h`；后续应改为服务端签名 `risk_pack` 下发，不再硬编码。

## 蜜罐影子期引流策略

> 机制细节迁至 `PROTECTION_MAP.md`「加密机制细节（从安全官 skill 迁入 · 2026-07-03 P91）」：影子期→funnel→persistent + 转正解封闭环。

## 功能失效策略

> 机制细节迁至 `PROTECTION_MAP.md`「加密机制细节（从安全官 skill 迁入 · 2026-07-03 P91）」：失效点 + GuardRuntime.getActiveRegistry 统一出口。

## 防重放

> 机制细节迁至 `PROTECTION_MAP.md`「加密机制细节（从安全官 skill 迁入 · 2026-07-03 P91）」：租约绑定字段 + 客户端必须拒绝的响应。

## 阶段计划 / 加密链现状 → 加密_当前真源

> 「做到哪一步（Phase 1A~1G / S2~S4 / 牙③④）的 ✅🟡🔵 判据」是**现状**，迁至 `_CORE_现状真源/加密_当前真源.md` §②（2026-07-03 P91 拆分）。维护加密只读那一页；改代码回本 skill 过规则。

## 当前 SO 基线与工作量

> SO 基线现状（已加 `decrypt_config`/派生 key/证书绑定、仍占位的 `nativeIsAuthorized`、发行线同源 S_rel/cert）+ 人天估算 = 现状，迁至 `_CORE_现状真源/加密_当前真源.md` §③（2026-07-03 P91）。**以下两条规则留此。**

SO 只管“验真 + 解密 + 关键风险信号”；弹窗、影子期倒计时、冷却、断网宽限主要放 Java/Kotlin。

**黑盒化边界（写死，2026-06-24）**：想「写进 SO 黑盒」时，**能下沉的只有钥匙层**——key 派生、验签、解密、设备 / 摘要折入（如 `W_dev`、重放摘要）；**不该下沉的是策略与表现层**——弹窗、影子期、冷却、时间宽限、RiskLevel 表现，留 Java（好调试、可热改、崩溃可控）。把策略也塞进 SO = 每改一次都要 NDK 重编 + 难调崩溃 + 放大三端漂移，反而最难维护。黑盒抬高的是「静态分析成本」；真锁仍靠服务器短租约 + 设备绑定（Frida 动态仍能 dump 解密结果，SO ≠ 不可破）。

## 后续 AI 接手快照 → 加密_当前真源

> 接手快照的**现状部分**（Phase 1A~1E/P1F 已装机 PASS 清单、真锁现状 2026-06-11）迁至 `_CORE_现状真源/加密_当前真源.md` §②③（2026-07-03 P91）。**以下规则留此**：

- 不重写 SO / AES-GCM / 换 crypto 依赖——除非有明确编译失败、验收失败或安全缺陷证据。
- 不改已验证 hook 回调体；授权/状态机边界归 `guard-auth-review_授权检查官` 共同审查。
- 业务本体 = 密友/密群隐藏 + 通知/红点/搜索/朋友圈过滤链，**不是通用 DRM demo**；registry 抽取必须服务这些已验收链路。
- 安全官只维护安全/加密/DRM/风控路线；不要把同一策略复制到授权检查官 skill。

## 发布 / 共存版加密铁律

触发关键词：`发布`、`发版`、`签名`、`keystore`、`共存版`、`官替版`、`registry_cipher`、`_CERT_SHA256`、`guardWxPkg`。

- 先读 `docs/RELEASE_RULES.md` 的「双版本发布手册」和「加密接手清单」。
- 官替版与共存版是两条独立发行线：各自固定 `packageName`、`versionCode`、`release_id`，**共用同一把 release jks**（D-026）；互不覆盖。
- encrypted registry 发版必须同源更新：`registry_8071.json` → `gen_registry_cipher.py` → `registry_cipher.inc` → `PHASE1C/1D/1E_VERIFY PASS`。
- 生成 cipher 时的签名证书 SHA-256 必须等于运行时 binding material；官替版/共存版签名不同就必须分别生成，不得混用。
- Java 白名单、Xposed scope、C++ `GUARD_EXPECTED_PACKAGE`、服务器 `release_id` 必须来自同一包档案；不能只改 C++。
- 业务 hook 仍生效但 `PHASE1D/1E` 失败时，可能只是 fallback 在兜底；禁止宣称加密链路通过。
- 当前可宣称“商业授权闭环（客户端 pv 见发版真源 §③）+ Ed25519 防伪造信封 + 当前发行线 server seed 解 registry 已接入”；删 fallback / V3 发行线 / RiskState 全链路降级+正版恢复闭环完成前，禁止宣称“授权无法破解”或“服务器真锁终局完成”。

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
   - 对当前加密主线的发布口径：P1E = AES-GCM encrypted registry + Filter 真读 registry（Contact/Moments/Conv）+ 派生 key + 证书绑定；P1F/S3b = `LeaseClock` 服务器授时 + `RiskState` record-only L0~L6 + `RiskPromptController` 唯一弹窗 + kill↔funnel 拆两闸；S4 = Ed25519 信封验签；S3a = 当前 `android_8071` `prod_server_lock` 发行线 server seed 解 registry。**仍未完成**：删 Filter fallback、V3 官替/共存发行线对齐、RiskState 全链路降级+正版恢复闭环（杂项功能 `isTamperDegraded` 散沙已落码，数以调用点为准 · 真源 `core/RiskState.java`），registry+fallback 双份明文未完全消除。

当前 P1E 收口口径 / 发布前 PASS 清单（做到哪、要看哪些 PASS）= 现状 → `_CORE_现状真源/加密_当前真源.md` §②（2026-07-03 P91 拆分）。

### ⚠️ ss4.p 教训（迁前先对账）→ FAILURE_LOG F-44

**迁任何 Filter 读 registry 前先三证对账（代码常量 + HOOK_MAP_8071 + 必要时 L1 装机日志）确认锚点今天真活跃，禁止照过期注释/草稿迁。** 实证（ss4.p 死路径误当主锚点）详情 → `FAILURE_LOG.md` F-44。

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
