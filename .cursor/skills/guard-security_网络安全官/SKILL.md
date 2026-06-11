---
name: guard-security-officer
description: Guard Native 安全与加密官。负责客户端安全、DRM、防破解策略、加密配方、服务器授权信封、SO 解密、Java/SO/服务器交叉校验、租约时间、字段伪装、防重放、蜜罐、影子成功期、打开即弹引流、RiskLevel 和防误杀审查。Use when the user mentions 加密、SO、decrypt_config、AES-GCM、签名、服务器心跳、断网弹窗、时间异常、蜜罐、改 vip、RiskState、LeaseClock, or before refactoring native security code.
---

# guard-security-officer — 安全与加密官

## 定位

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

1. 不把授权根锁做成客户端布尔值。`isVipAuthorized()`、`viptime`、`endtime` 只能是诱饵或展示，不得决定核心能力。
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
- watermark 决定泄露后能否溯源。
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
4. `watermark_pack`：客户 seed、批次水印。
5. `compat_pack`：旧 schema 兼容、缓存恢复策略。

客户端只在签名验真、租约有效、SO 解密成功后启用这些能力包。失败时散沙，不崩、不全开。

## 配方收敛原则

加密配方只能收敛成「五个 pack + 一个 GuardRuntime 出口」，禁止每个功能各自发明一套小配方、小网关、小授权。

| pack | 只管什么 | 不管什么 |
|---|---|---|
| `license_pack` | 授权、租约、客户/设备/版本/签名绑定 | hook 类名、弹窗策略 |
| `registry_pack` | 微信 hook 类名、方法名、字段名、gateway recipe | 授权判断、状态切换、风险后果 |
| `risk_pack` | 蜜罐、tampered、降级、弹窗、影子期、引流冷却 | hook 锚点、用户名单 |
| `watermark_pack` | 客户 seed、批次水印、泄漏溯源 | 功能开关、授权时长 |
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

不要使用固定 10 天锁定策略。确认蜜罐命中后按 24 到 48h 影子期进入长期引流；具体影子期可由服务端 risk 参数调节，但默认不得超过 48h。

## 蜜罐影子期引流策略

蜜罐命中后进入 `TAMPER_SHADOW`。

影子期默认 24 到 48 小时：

- 表面显示已激活。
- 功能可以继续使用。
- 不弹窗，不暴露蜜罐。
- 记录 `tamper_first_seen`。
- 足够骗过初步逆向测试，但不让白嫖版本长期传播。
- 若确有运营原因需要更长影子期，必须由服务端策略显式下发，并在审查报告里说明风险。

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
7. 🟡 **Phase 1D-server 服务器心跳（S2，2026-06-11 解冻；dormant 骨架已建）**：miyou-server 下发 signed envelope + encrypted packs，key 派生折入服务器短命材料 + device/customer 绑定。已建 `net/` 出站层（`EnvelopeClient`/`AuthEnvelopeVerifier`/`EnvelopeStore`/`GuardHeartbeat`/`GuardActivation`），但**未接入 `ModuleMain`、信封 k 未折进 SO key、无 Ed25519、`LeaseClock` 未喂数据**（真锁核心仍未完成，不得宣称真锁完成）。现状权威 + 下一受控步骤见 `PROTECTION_MAP.md` §10.6。
8. 🟡 **Phase 1E LeaseClock + RiskState（P1F 本地一刀 + 两闸，2026-06-10 装机 PASS）**：已落地 `LeaseClock` 骨架（服务器授时外推 + 防回拨，v1 无心跳默认 CLEAN）+ `RiskState` 唯一 L0~L6 等级机（**record-only**，与 isActive 并联、放行不收紧）+ `RiskPromptController` 唯一弹窗（仅 FUNNEL 弹 + 冷却）+ kill↔funnel 拆两闸（kill 已从篡改链剥离）。**仍未做**：服务器授时真数据源、真正的散沙降级后果、蜜罐绊线真检测（随 Phase 1D-server）。

## 当前 SO 基线与工作量

- 当前 `libguardcore.so`：在 Phase 0/Batch1 骨架（加载/JNI/进程角色/隐藏状态机/wxid 匹配/轻量包名/config_version）之上，**已加** `decrypt_config()` + AES-GCM + encrypted registry + 派生 key（去明文 key 常量）+ 证书绑定 + `registry_get_recipe`（取件口）。
- **仍不是安全官标准真锁**：`nativeIsAuthorized()` / `nativeGetAuthState()` 仍是占位（`StateMachine.isVipAuthorized()` 也是 `return true` stub），**缺**：签名 envelope 验真、服务器短命材料折入 key、服务器授时真数据源、真正散沙降级（`LeaseClock` 骨架 + `RiskState` record-only 已 P1F 落地，但服务器侧真锁后果未接）。对外不得宣称真锁完成。
- 最低可用真锁：约 10 到 18 人天。范围：AES-GCM 测试向量、`decrypt_config()`、3 到 5 个核心 registry 抽取、`EncryptedConfigLoader`、基础 signed envelope、解不开散沙。
- 上线标准：约 20 到 35 人天。范围：验签、防重放、device/customer/package/cert 绑定、LeaseClock、RiskState、蜜罐影子期、正版恢复闭环、release 混淆和装机回归。
- 最小可用版：300 到 500 行 C++。
- Phase 1 正式版：800 到 1200 行 C++。
- 带完整自检、签名、AES-GCM、Risk hint、JNI 包装、测试向量：1200 到 1800 行 C++。

SO 只管“验真 + 解密 + 关键风险信号”；弹窗、影子期倒计时、冷却、断网宽限主要放 Java/Kotlin。

## 后续 AI 接手快照

- Phase 1A 已装机验收：logcat 出现 `[native] BATCH1_VERIFY PASS` 与 `[native] PHASE1A_VERIFY PASS`。
- 已有：`decrypt_config()` AES-GCM 原型、固定测试向量、JNI 包装、`NativeBridge` 自测入口、失败 scatter。
- 不要重写 SO、不重写 AES-GCM、不换 crypto 依赖；除非有明确编译失败、验收失败或安全缺陷证据。
- **加密主线已推进到 P1E（Filter 读 registry，2026-06-09）**：1B 抽取 → 1C 加密 registry + search.gateway 收敛 → 1D-local 派生 key（去明文 key 常量）→ A-step2 证书绑定 → P1E 让 ContactFilter/MomentsFilter/ConvFilter 真从 registry 读类名（取件口 `GuardRuntime.getRecipe` + `nativeGetRecipe`）+ conv.list 漂移债结案；均装机 PHASE1A-1E PASS。
- **P1F 两闸 + 风险骨架已落地（2026-06-10，装机 PASS）**：`LeaseClock` 骨架 + `RiskState`（record-only L0~L6）+ `RiskPromptController`（唯一弹窗）+ kill↔funnel 拆两闸；web 驾驶舱同步显示 风险等级/停用闸/引流。详见 `07_archive_归档/P1F_十字防护整合设计/`（DESIGN + worklog）。
- **真锁仍未做（最大的洞）**（⚠️ 2026-06-11：S2 服务器出站/信封/心跳骨架已建但 **dormant**——未接 `ModuleMain`、信封 k 未折进 SO key、无 Ed25519；现状见 `PROTECTION_MAP.md` §10.6）：服务器短命钥匙（Phase 1D-server）、服务器授时真数据源、真正散沙降级、Ed25519 验签**仍未做**；`RiskState` 仍 record-only 不收紧（防误伤正版）；`StateMachine.isVipAuthorized()` 仍是 `return true` 死桩。对外不得宣称真锁完成。
- 不接服务器、不改已验证 hook 回调体、不改 `isVipAuthorized()` stub（仍归授权检查官）。
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
- 当前仍只能宣称“本地 encrypted registry + 证书绑定”；服务器真锁、Ed25519、服务器短命 key 必要条件未完成前，禁止宣称“授权无法破解”。

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
   - 对当前加密主线的发布口径：P1E = AES-GCM encrypted registry + Filter 真读 registry（Contact/Moments/Conv）+ 派生 key + 证书绑定；**P1F**（2026-06-10）= `LeaseClock` 骨架 + `RiskState` record-only L0~L6 + `RiskPromptController` 唯一弹窗 + kill↔funnel 拆两闸（装机 PASS）—— 一起作为 v1 最小发布防护；**服务器真锁（Phase 1D-server）/ 服务器授时真数据源 / 真正散沙降级 / Ed25519 验签 未完成，`isVipAuthorized()` 仍是 stub，`RiskState` 仍 record-only 不收紧，registry+fallback 双份明文未完全消除。**

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
