# P1F — 十字交叉防护整合设计（不拆散版）

> 作者：安全与加密官（guard-security-officer）
> 日期：2026-06-10　证据基线：2026-06-10 只读勘察（L2，读码核实）
> 关联权威：`PROTECTION_MAP.md`（上线门控总账）·`.cursor/skills/guard-security_网络安全官/SKILL.md`（安全规则）·授权检查官 skill（状态机/边界权威，本设计落地前必须共审）
> 阶段定位：本文 = 设计 + 改前审查。**未动代码**。

---

## 0. 目标（用户原话翻译成工程口径）

| 用户要的 | 工程口径 |
|---|---|
| 他能用 | 正常授权 + 在线 → 核心配方解得开 → 功能全开 |
| 我随时能让他用不了 | 服务器 `kill_switch` / 租约到期 → 风险降级 → 配方散沙（不崩、不删数据） |
| 屏蔽我服务器就弹窗引流 | 断网超宽限 / 拒连服务器 → 风险升级 → 唯一弹窗引流 `zxmqq.shop` |
| 时间炸弹 | 服务器授时 + 单调时钟 + 租约 `expire_at`，到点即降级 |
| 蜜罐 | 诱饵 `isVipAuthorized` 被改 → 绊线 → 影子期后引流 |
| 防抓包 | 信封 Ed25519 签名 + nonce 防重放 + 字段伪装 + HTTPS 三层叠 |
| 什么都有点、不拆太散 | 全部手段只汇入「两根轴 + 三个唯一出口 + 五个能力包」，绝不遍地散写 if |

**北极星（PROTECTION_MAP §0）**：让破解成本 > 软件价值；每种手段只埋一处；只打破解的，绝不误伤付费客户；价值搬到服务器裁决 + SO 解密 + 每客户短命钥匙。

---

## 1. 十字架构（这就是「不拆散」的核心）

```
                 ┌──────────────────────────────────────┐
   纵轴=能力链     │  miyou-server  发签名信封 + 加密能力包    │
   (谁能用)       └───────────────────┬──────────────────┘
                                     │  signed envelope (Ed25519 + nonce)
                                     ▼
                   ┌──────────────────────────────────────┐
                   │  SO libguardcore  验真→解密→产出能力      │
                   │  verify_envelope() / decrypt_config()  │
                   └───────────────────┬──────────────────┘
                                     │  能力（不可伪造）
                                     ▼
   ┌─────────────┐   getRecipe()   ┌──────────────────┐
   │  LeaseClock  │───┐            │   GuardRuntime    │  ← 业务只问这一个出口
   │  (服务器授时) │   │            │   (唯一配方出口)   │
   └─────────────┘   │            └──────────────────┘
   ┌─────────────┐   │  currentLevel()        │ getRecipe / isConfigReady
   │  RiskState   │───┼──────────────┐         ▼
   │  (L0~L6 唯一) │   │            ┌──────────────────┐
   └─────────────┘   │            │ StateMachine      │  ← isActive() 唯一中央网关
   横轴=后果链        │            │ .isActive()       │
   (用不了怎么表现)    ▼            └──────────────────┘
              ┌────────────────────────┐
              │ RiskPromptController    │  ← 唯一弹窗出口（允许多触发点，禁多份策略源）
              │ .maybeShow(reason)      │  → PiracyNotice / OverlayWindow → zxmqq.shop
              └────────────────────────┘
```

- **纵轴（能力链）**：服务器发短命材料 → SO 验真+解密 → `GuardRuntime` 产出能力 → 业务只消费，不自判授权。
- **横轴（后果链）**：`LeaseClock`（时间）+ `RiskState`（风险）→ `RiskPromptController`（唯一弹窗）→ 引流。
- **十字交点 = 三个唯一出口**：业务全项目只准调这三句。

```text
GuardRuntime.getRecipe(gateway, key)   // 取 hook 配方（解不开=""）
RiskState.currentLevel()               // 取风险等级
StateMachine.isActive()                // 取中央总闸（租约+配方+风险 三者与）
```

---

## 2. 五个能力包（服务器下发，粗粒度，不细碎）

沿用 skill「五包 + 一出口」，新增功能只往现有包补字段，**禁止新建 `xxx_pack`**。

| pack | 只管什么 | 不管什么 |
|---|---|---|
| `license_pack` | 授权、租约、客户/设备/版本/签名绑定、`kill_switch` | hook 类名、弹窗策略 |
| `registry_pack` | 微信 hook 类名/方法名/字段名/gateway recipe（即现 `registry_8071.json` 四条） | 授权判断、状态切换、风险后果 |
| `risk_pack` | 蜜罐参数、影子期时长、弹窗冷却、引流冷却、RiskLevel 阈值 | hook 锚点、用户名单 |
| `watermark_pack` | 客户 seed、批次水印、泄漏溯源指纹 | 功能开关、授权时长 |
| `compat_pack` | 旧 schema 兼容、断网缓存恢复、灰度、版本兼容 | 新功能逻辑 |

**字段伪装**：信封字段短名化 + schema 轮换（`K2i_m` 之类），不出现 `vip/auth/license/endtime/config` 明牌词；老 schema 保留 7~14 天防误伤正版。伪装≠加密，真安全来自签名+密文。

---

## 3. RiskLevel（唯一等级表，全项目不准散写 if）

| 等级 | 名称 | 触发 | 表现 |
|---|---|---|---|
| L0 | `CLEAN` | 签名通过、租约有效、配置解开 | 正常 |
| L1 | `OFFLINE_WARN` | 接近 24h 未心跳 / 轻微网络异常 | 可关提醒，功能正常 |
| L2 | `TIME_SUSPICIOUS` | 断网 1 天 + 时间异常流逝 | 弹窗要求联网，暂可用 |
| L3 | `DEGRADED` | >72h 未心跳 / 多次时间异常 | 散沙降级，**不清数据** |
| L4 | `TAMPER_SHADOW` | 命中蜜罐（改 vip/假锁） | 24~48h 影子成功，表面可用 |
| L5 | `TAMPER_FUNNEL` | 蜜罐命中超影子期 | 打开即弹，点确定仍可用 + 短冷却 |
| L5R | `PROBATION` | 篡改用户购买后服务器强校验通过 | 试用观察期 |
| L6 | `TAMPER_PERSISTENT_FUNNEL` | 重复篡改 + 服务器确认 | 每次前台/启动/设置页弹 |

**转正闭环**：本地按钮/清缓存/改时间/删文件**不得**自洗白；只有服务器返回带签名的 `risk_reset`/新租约/新 registry 才允许降级风险。

---

## 4. LeaseClock（服务器授时——用户已确认时间走官方服务器）

时间判断**不得**直接用 `System.currentTimeMillis()` 判死刑。采用「服务器授时 + 单调时钟外推 + 历史水位」三件套：

记录字段：
- `last_server_now`（上次成功心跳的服务器时间）
- `last_elapsedRealtime`（对应时刻的本机单调时钟）
- `max_trusted_now`（历史最大可信时间水位，持久化）
- `expire_at`（租约过期，来自 `license_pack`）
- `boot_marker`（重启证据）

离线外推：

```text
trusted_now = last_server_now + (current_elapsedRealtime - last_elapsedRealtime)
```

重启/回拨防护：
- `current_elapsedRealtime < last_elapsedRealtime` → 判定重启/计时异常，进 `TIME_SUSPICIOUS`，要求联网重校。
- 离线推算**不得低于** `max_trusted_now` 后再倒退延长授权。
- 墙钟前跳/回拨/跳变只标 `TIME_SUSPICIOUS`，不单独永久引流；与断网>24h / elapsed 回绕 / 蜜罐命中叠加才升级。

断网时间炸弹阶梯（对接现成 `NativeBridge` GRACE 常量 24/72/10）：
- 0~24h：用缓存配置，静默后台同步。
- 24~72h：`OFFLINE_WARN` 可关提醒。
- >72h：`DEGRADED` 偷偷散沙（像 bug，不是报错）。
- 宽限耗尽 / `kill_switch` / 绊线确认 → `TAMPER_FUNNEL` 强制弹窗引流。

---

## 5. 模块清单（4 个核心模块，对齐 skill，绝不拆成 10 个小文件）

| 模块 | 职责 | 落点 |
|---|---|---|
| `AuthEnvelopeVerifier` | 验服务器签名/设备绑定/包签名/版本/schema/过期 | SO `verify_envelope()` + Java 薄壳 |
| `EncryptedConfigLoader` | 读 `encrypted_config`，调 SO `decrypt_config()`，出 registry（**已存在**，扩为接 envelope） | `core/EncryptedConfigLoader.java` |
| `LeaseClock` | 服务器时间/单调时钟/断网宽限/时间异常 | 新增 `core/LeaseClock.java` |
| `RiskState` | 蜜罐命中/降级/影子期/引流，唯一 RiskLevel 出口 | 新增 `core/RiskState.java` |

唯一弹窗：`RiskPromptController.maybeShow(reason)` 包住现成 `PiracyNotice` + `OverlayWindow`，所有触发点都调它，**禁止多份弹窗策略源**。

SO 职责（`libguardcore.so`，只管验真+解密+风险信号，不放业务）：
- `verify_envelope()` 验签/hash/版本/schema/绑定（**新增**）
- `decrypt_config()` 解 registry（已存在）
- key 派生折入**服务器短命材料 + cert SHA-256**（现仅本地材料，**待补服务器材料**）
- 给 Java 返回不可伪造 risk hint

---

## 6. 用户 5 诉求 → 落在十字哪根线（验证「什么都有点」）

| 诉求 | 落点 | 出口 |
|---|---|---|
| 我随时停用 | `license_pack.kill_switch` / `expire_at` → `RiskState→DEGRADED/FUNNEL` → registry 散沙 | `StateMachine.isActive()` |
| 屏蔽服务器→弹窗 | `LeaseClock` 断网阶梯超期 → `RiskState→FUNNEL` | `RiskPromptController` |
| 时间炸弹 | `LeaseClock` 服务器授时 + `expire_at` + 重启检测 | `RiskState.currentLevel()` |
| 蜜罐 | 诱饵 `isVipAuthorized` 绊线 → `TAMPER_SHADOW` 影子期 → `FUNNEL` | `RiskState` + `RiskPromptController` |
| 防抓包 | 信封 Ed25519 + nonce 防重放 + `K2i_m` 字段伪装 + HTTPS | `AuthEnvelopeVerifier` |
| 十字交叉 | Java↔SO↔服务器三方 × 五包，每层都有点作用但不细碎 | 全链 |

被 hook 后的预期（交叉校验的意义）：
- hook `isVip=true` → 没用，没有 registry。
- hook UI 已激活 → 没用，过滤链拿不到配方。
- hook SO 返回成功 → 没解出的配置就是空/散沙 registry。
- 唯一仍强的威胁：Frida hook `decrypt_config` 出参 dump → 靠短命租约 + 设备绑定 + 服务端轮换 + risk 记录压制，不在静态层解决。

---

## 7. 诚实切分：现在能做 vs 必须等服务器

### ✅ 本地现在就能收敛（不需服务器、不需猜、不碰已验证 hook）
1. 新增 `RiskState`（唯一 L0~L6 等级机）+ `RiskPromptController.maybeShow()`（唯一弹窗），接现成 `PiracyNotice/OverlayWindow/AppConfig.isKillSwitch()/GRACE 常量`。
2. 新增 `LeaseClock` 骨架（先用本地缓存时间 + `elapsedRealtime` + `max_trusted_now`，默认 `CLEAN`；服务器授时接口留口）。
3. 接活现成死代码 `AuthManager.evaluate()`（record-only → 进 `RiskState`）。
4. 蜜罐绊线：诱饵 `isVipAuthorized` 被改 → 置 `RiskState=TAMPER_SHADOW`。
5. 本地 `kill_switch` 开关走通整条引流链（服务器接口后续替换）。
6. 删 Filter 明文 fallback（**每个删前先按 ss4.p 教训三证核账 registry 完整性**）——兑现 registry 加密。

### ⛔ 必须等 miyou-server（不能凭空造接口）
- `AuthEnvelopeVerifier`：Ed25519 验签、签名信封、防重放 nonce。
- 服务器短命材料折入 key 派生（**真锁主体**，现仅本地材料）。
- 服务器授时心跳端点（LeaseClock 真数据源）+ 真正的远程 `kill_switch`。
- 服务器侧 `risk_reset` 转正闭环。
> skill 估：最低可用真锁 10~18 人天；上线标准 20~35 人天。

---

## 8. 改前审查（安全官，初审）

```markdown
【安全与加密官改前审查】
结论：WARN（可动，但带强约束）

1. 是否触碰授权根锁：是（RiskState 取代散写授权 if；isVipAuthorized 保留为诱饵）
2. 是否触碰 SO 解密：本地阶段否；服务器阶段是（verify_envelope/key 折服务器材料）
3. 是否触碰服务器 envelope：服务器阶段是（本地阶段只留口）
4. 是否触碰 LeaseClock：是（新增）
5. 是否触碰 RiskState：是（新增，唯一出口）
6. 是否可能误杀正版：高风险点——断网宽限/时间异常/影子期默认值必须保守，必测付费客户离线
7. 是否会影响已验证 hook：禁止——防护只加在网关/配方/钥匙/租约/风险层，不进 hook 回调体（铁律 29/F-31）
8. 是否需要授权检查官共同审查：【是，必须】动 StateMachine/AuthManager/NativeBridge → 落地前拉授权检查官会签
9. 允许修改的文件：core/RiskState.java(新) core/LeaseClock.java(新) core/RiskPromptController.java(新) EncryptedConfigLoader.java GuardRuntime.java ModuleMain.java(接线) anti_tamper.cpp(绊线标记) 各 Filter(仅删 fallback，需逐个核账)
10. 禁止修改的文件：已验证 hook 回调体（ConvFilter/MomentsFilter/ContactFilter/SearchFilter/PushFilter 的过滤逻辑本体）、guard_core.cpp 已跑通 JNI（除非编译/验收/安全缺陷证据）
```

---

## 9. 建议落地顺序

1. **P1F-本地一刀**（本设计 §7 ✅ 六项）：唯一 RiskState + 唯一弹窗 + LeaseClock 骨架 + 接死代码 + 蜜罐绊线 + 本地 kill_switch 走通；可一次装机验收，不碰服务器、不碰已验证 hook。
2. **删 fallback**（§7.6）：逐个 Filter 三证核账后删明文，兑现 registry 加密。
3. **P1D-server 真锁**（§7 ⛔）：miyou-server 心跳 + 签名信封 + Ed25519 + 服务器材料折 key + 服务器授时 + 远程 kill。
4. 蜜罐影子期 / 强制引流 / 转正闭环联调（risk_pack 服务端参数）。

> 落地任一步前：① 授权检查官会签（§8.8）；② 装机 PASS 日志直采落 worklog；③ 不对外宣称真锁完成，直到 P1D-server 完成。
