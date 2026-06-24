# P_AntiBanGate 防封授权闸 + 防破解可维护性收口 — 工作计划

> ⛔ **ARCHIVED — 只读留档，勿作决策依据。** 设计与决策唯一真源 = [`DESIGN.md`](./DESIGN.md)；时间参数 = [`配方卡_SPEC_v1.md`](./配方卡_SPEC_v1.md)。
> ⚠️ **2026-06-22（C36 续）起：防封黑盒设计的权威单一真源 = `DESIGN.md`**。本文留档（早期工作计划 / 名词解释 / 归一工作项 A），勿在此另立。
> 建立：2026-06-21 / 状态：**⬜ 设计 only · 决策已锁（见 DESIGN §7）**（留档；未动任何代码）
> 触发：用户要求「分析防破解+服务器下发做得怎么样 / 可维护性 / 下版本找 hook 点」+「加入新血管逻辑（1h 授权保活 / 2h 无授权散沙）」
> 修订 2026-06-22：用户追加「评分机制决定信任程度 + 心跳节奏（首装密→稳定疏）+ 防封失效计时（首分钟起观察 / 满 2h 未授权失效）」→ 新增 §3.5 信任评分 / §3.6 心跳节奏，§四 增决策 6/7（数字仍待拍板）。
> 关联：`PROTECTION_MAP.md`（防破解总账）· `HOOKMAP.md`（功能/拦截层）· `ANTIBAN_MAP.md`（防封权威账）· `01_dispatch_总调度/CURRENT_PLAN.md`
> 纪律：改主线代码前必须用户拍板（本文 §四）；单一总账仍是 `PROTECTION_MAP.md`，本文落地后结论回灌该文。

---

## 〇、一句话目标

1. **看懂**：把「明文 fallback」「hook 名三处重复」用大白话讲清，确认它影响的是不是隐私功能。→ §一
2. **收口**：防破解可维护性整改（让下一个混淆版本只改一处）。→ §二（工作项 A）
3. **加牙**：新增「防封授权闸」`isAntiBanReady()`——首装宽限、超时未授权则防封散沙。→ §三（工作项 B）
4. **评分**：信任评分决定信任程度——客户端攒「行为分」只调心跳快慢（伪造也不开放能力）；服务器按上报算「信任档 q」签进信封下发，定租约 / 频率 / 防封宽限。→ §3.5；心跳节奏（首装密 → 稳定疏）→ §3.6
5. **拍板**：列出等用户定夺的决策，定了再动代码。→ §四

---

## 一、名词解释（给你拍板用 · 这是你问的）

### 1.1 什么是「明文 fallback」？

- **配方（recipe）= hook 点的类名/方法名/字段名**（如 `kc5.v0`、`na4.b`、`fc5.g`）。微信被混淆，这些名字每个版本都变，是我们 hook 的「钥匙」。
- 我们的设计本意：把这些钥匙**加密**放进 SO（`registry_cipher`），运行时靠服务器种子解密才拿得到 → 盗版没服务器种子就拿不到钥匙 = **散沙**。
- **但是**：为了 debug 方便 + 怕正版机解密抖动时隐私功能突然失效，代码里**同时**留了一份**明文**钥匙（Java 里 `BuildConfig.DEBUG ? "kc5.v0" : ""` 这种，以及散在各 Filter 的硬编码字面量）。
- **「release 仍留明文 fallback」的意思** = 发行包里**这份明文钥匙没删干净**。逆向者用 jadx 一搜就看到全部 hook 点类名，**直接抄一份本地配方表，绕开整条 SO 加密链**。外部 AI 压测约 10 分钟就定位到了。这是当前防破解 **#1 命门**（项目自己 §10.7 P0 也点名了，但「删 fallback」被冻结到 v2，因为要先和 V3 改包流程对齐）。

### 1.2 什么是 hook 名「三处重复」？

同一个混淆名（例：会话列表的 `kc5.y`）**同时**写在 3 个地方：

| # | 位置 | 作用 | 问题 |
|---|---|---|---|
| ① | `native_core/registry_8071.json` | 加密配方的源 | 真源 |
| ② | Filter 里 `BuildConfig.DEBUG ? "kc5.y" : ""` | debug 兜底 | 和①重复 |
| ③ | Filter 里**内联**硬编码 `"kc5.y"`（如 ConvFilter 4 处） | 直接用 | 和①②再重复 |

- **后果一（防破解）**：明文暴露面变大——②③ 把本该藏起来的钥匙又抄到明文。
- **后果二（可维护性）**：下个版本 `kc5.y` 变名时，**3 个地方都要改，改漏一处就出 bug**。这正是你问的「下版本找 hook 点方不方便」的核心痛点。

### 1.3 是不是「我们的隐私功能」？—— **是，正是隐私功能**

这套配方/钥匙喂的就是 **4 大隐私拦截动脉**：
- `conv.list` = 会话列表隐藏密友（ConvFilter）
- `moments.feed` = 朋友圈隐藏密友帖/赞/评（MomentsFilter）
- `contact.address` = 通讯录隐藏密友（ContactFilter）
- `search.gateway` = 搜索拦截（SearchFilter）

> 所以「明文 fallback + 三处重复」削弱的，就是**隐藏密友这套隐私功能**的防破解强度 + 它的版本可维护性。和「防封」是两回事（防封代码主线现在还没有，见 §三）。

### 1.4 现状量化（4 大动脉 + Push）

| 动脉 | registry 字段 | 真走 getRecipe | 还硬编码在 Java 的混淆名 |
|---|---|---|---|
| conv.list | 9 | 3 | ~6（kc5.y / kc5.a / ik3.n …）|
| moments.feed | 12 | 8 | ~10（jw1.d / wq.* / f435583d …）|
| contact.address | 9 | 6 | 3 |
| search.gateway | 7 | 5（2 个仅元数据）| z15.ef6 / fz2.e / q2.j + 绑定 schema |
| PushFilter | 0 | 0 | 5+（完全没接 registry）|

→ **37 个 registry 字段只 22 个真接线、15 个挂空；registry 外还散着 ~25+ 个混淆字面量。SearchFilter / PushFilter 是重灾区。**

---

## 二、工作项 A：归一（单一真源）—— 用户拍板 2026-06-21

> 用户拍板：**做唯一源**；两条硬约束 = ①**不破坏之前防破解** ②**不降低被破解的难度**。本节据此设计为「只增安全、绝不减安全；分阶段、可回滚、每步装机回归」。

### A.0 三条铁律（用户拍板，写死）

1. **不破坏防破解 / 不破坏功能**：任何一步都不能让正版机的隐私 hook 因解密抖动而失效（§10.5 红线 / F-31 / 铁律29）。
2. **不降低被破解难度**：归一后明文暴露面只能**减少或持平**，绝不增加。
3. **搜索框（SearchFilter）整体保留、归一不碰**（用户拍板 2026-06-21）：搜索框是「巨大过滤器」，能用就不动（铁律29）。归一只在 **会话(conv) / 朋友圈(moments) / 通讯录(contact)** 三动脉做；搜索的活路 `f0.getView` 本就接好，排除它**零损失**。`q2.j / fz2.e / z15.ef6` 一律不删不迁（它们是 8.0.71 死锚/探针，且 z15.ef6 还管 111111 解锁）。

### A.1 安全设计（为什么「单一真源」是提高、不是降低破解难度）

- **归一的方式 = 把散在 Java 明文的混淆名收进加密 registry，并让 release 不再留这些明文** → 逆向能直接搜到的钥匙**变少**。前提是**顺序**：先进 registry 并核验存活，**再**撤 Java 明文，绝不反过来。
- **保留 DEBUG 兜底（仅 DEBUG）**：DEBUG fallback 改成 build 时从 json 生成（值不变、只是不再手写）→ DEBUG 行为不变、release 不变 = **零风险**；正版机若解密抖动，dev 仍可诊断。
- **这轮不做「release 删 fallback」终局**（那步和 V3「每发行证书重生成 cipher」绑定，§10.8）→ 正版机仍有兜底、不会突然密友暴露。本项只把「三处」收成「一处源」，不动 release 的 fail-closed 现状。
- **不是把所有字符串都塞进 registry**：只收**混淆的、跟版本变的钥匙**；稳定 FQCN（如 `com.tencent.mm.ui.conversation.MainUI`）不收——收了徒增维护、不增安全。保持 4 大动脉粗粒度（呼应安全官 skill「粒度与单一真源」§）。

### A.2 核账表（迁移前必做 · 内联明文 → 是否已在 registry → 动作）

> ⚠️ ss4.p 教训：迁任何锚点前先三证核验（代码常量 + `HOOK_MAP_8071_AUTHORITATIVE` + 必要时 L1 装机日志）确认**今天真活跃**；吃不准就停。下表「动作」均待三证后执行。

| 动脉 | 内联明文混淆名 | registry 现状 | 动作 |
|---|---|---|---|
| conv.list | `kc5.y` | 有 `item_class` 但**挂空** | 接线 getRecipe + 撤内联 |
| conv.list | `kc5.a` `kc5.x` `ik3.n` | 无 | 补进 registry + 接线 |
| conv.list | `MvvmConvList`/`ConversationListView`/`MainUI` | 稳定 FQCN | **不收**（不增安全）|
| moments.feed | `f435583d` | 有 `actor_wxid_field` 但**挂空** | 接线 + 撤内联 |
| moments.feed | `jw1.d` `wq.*` `O0` | 无 | 核验存活后补进 + 接线 |
| contact.address | `d` `e`（item 字段）| 有 `contact_field/type_field` 但**挂空** | 接线 + 撤内联 |
| contact.address | `o,p,h` | P1E 明确排除 | 暂不动（单独核账）|
| search.gateway | `z15.ef6` `fz2.e` `q2.j` + `tz2.*` | 仅粗 profile，主锚**全硬编码** | 核验后补进 registry + 接线（最大洼地）|
| PushFilter | NotificationItem/`x.d`/`LauncherUIBottomTabView.l`/`TaskBarContainer` | **0 接 registry** | 评估：混淆名补进；稳定 FQCN 不收 |

### A.3 分阶段步骤（每步独立 git 快照 + 装机回归，绿了再下一步）

- [ ] **A3-0 核账落地**：把 A.2 表逐项三证核验，产出「确认存活 + 与 registry 对齐」清单（只读，零风险）。
- [ ] **A3-1 DEBUG 兜底改 build 生成**：写 `tools/gen_fallback_from_registry`，Filter 的 DEBUG fallback 从 json 生成常量。**release 不变、DEBUG 值不变** → 零安全/功能风险。装机回归隐私四链。
- [ ] **A3-2 接挂空字段**：把已在 registry 但挂空的（`item_class`/`actor_wxid_field`/`contact_field`/`type_field` 等 15 个）接到 getRecipe + 撤对应内联。每接一个装机回归一次。
- [ ] **A3-3 补洼地**：SearchFilter（`z15.ef6`/`fz2.e`/`q2.j`）+ PushFilter 混淆锚点，核验存活后补进 registry + 接线。装机回归搜索/通知链。
- [ ] **A3-4 registry 版本化**：按宿主 versionCode 分块、运行时派发；一个 SO 支持多版本，下版本=加一块。
- [ ] **A3-5 字典↔配方不漂移**：`docs/classmap/v8071.yaml` → `registry.json` 由脚本生成，跑 `check_classmap.ps1` 校验。
- [ ] **A4（依赖 V3，本轮不做）删 release 明文 fallback**：A3 全绿 + 与 V3「每发行证书重生成 cipher」绑定后再做（§10.8），单独快照 + 装机回归。**这步才是真正减少 release 明文的终局，但必须最后做、绝不提前。**

### A.4 验收（每步都要过，对齐安全官「最小测试清单」）

- 正版机：隐私四链（会话/通讯录/朋友圈/搜索）+ 通知/来电照常；`registrySummary entries=N`、各 Filter `recipes ok`。
- 无 server seed：registry scatter、release 敏感 hook 不装（与改前一致，不更差）。
- jadx 搜核心钥匙：迁移过的混淆名在 release 中**变少或持平**（绝不变多）。
- 每步可单独 `git revert` 回滚。

### A.5 蜜罐隔离核查（2026-06-21 实查 · 结论：归一不会动到蜜罐）

> 用户问「之前做的蜜罐会不会被动到」。已实查蜜罐全套与归一管线的边界。**结论：不会动到**；但有 4 个共享常量禁区，归一时绝不能碰。

**蜜罐全套（与归一是两套独立子系统）：**

- 诱饵 `core/PromoConfig.java`（明文 PROMO_URL/TOKEN/ENABLED，真功能不读）
- 绊线 `core/CompatProbe.java`（canary `BASELINE=0xF3C1CAB3` 比诱饵 + `EXPECTED_CERT` 比模块签名 → `markTampered`）
- `core/RiskState.java` / `RiskPromptController.java` / `FunnelPrompt.java`（风险态 + 唯一弹窗 + 展示）
- 引流 URL = `bootstrap_endpoints.json` 的 `cs.endpoint.funnel` → `gen_bootstrap_cipher.py` → `bootstrap_cipher.inc`，经 `NativeBridge.getEndpoint("funnel")` 取（**bootstrap 管线，cert-only，不折服务器种子**）

**为什么归一碰不到（实查证据）：**

1. Filter 只调 `getRecipe / getRecipeOrFallback`，**不引用** PromoConfig/CompatProbe/RiskState（grep 实证）→ 改 Filter 配方解析碰不到蜜罐。
2. PromoConfig/CompatProbe 只在 `ModuleMain §6.5` + 彼此引用，不在 registry 管线、不在 Filter。归一不动 §6.5。
3. **两套加密管线分开**：registry（`gen_registry_cipher.py` → `registry_cipher.inc`，折服务器种子）vs bootstrap/引流 URL（`gen_bootstrap_cipher.py` → `bootstrap_cipher.inc`，cert-only + 域分隔 `_DOM`）。改 `registry_8071.json` 重跑 registry 脚本**只产 `registry_cipher.inc`，不动 `bootstrap_cipher.inc`** → 引流 URL 密文不变。
4. 引流 URL 走 `getEndpoint`（bootstrap），hook 配方走 `getRecipe`（registry）——两条不同出口，归一只动后者。

**⚠️ 4 个共享常量禁区（归一时绝不能碰，碰了会连带炸蜜罐 / 正版误伤）：**

1. `_SEG_A/B/C`（key 段）+ `_CERT_SHA256`：被 `gen_registry_cipher.py`、`gen_bootstrap_cipher.py`、`config_crypto.cpp` 三处共享。归一只改 json 内容 + 接线，**绝不动 key 派生 / 段常量 / cert**，否则 registry 和 bootstrap 一起解不开。
2. `CompatProbe.BASELINE` / `PromoConfig` 任一字面量：归一**不碰 PromoConfig**（它是诱饵、不是死配置，别顺手优化删）；碰了必须重算 BASELINE，否则正版误报篡改。
3. `CompatProbe.EXPECTED_CERT` = registry `_CERT_SHA256`（同一证书 `ca421ec3…`）：归一不换 keystore / cert，故两者保持对齐；若哪天换证书，三处 + 运行时 `setBindingMaterial` 必须一起改。
4. `NativeBridge.getEndpoint`（引流 URL 出口）：归一只动 `getRecipe`，绝不混进 `getEndpoint`。

**工作项 B（血管闸）与蜜罐有交集，需复用不另起**：B 在 1h 后软引流必须走现有 `RiskState` + `RiskPromptController`（唯一弹窗源），不得新开弹窗 / 新判风险（安全官红线 §9）。

---

## 三、工作项 B：新「防封授权闸」isAntiBanReady（你要的血管逻辑）

### 3.1 ⚠️ 关键前提（必读）

把 `guard_native/src` + `native_core` 翻遍——**主线现在没有任何「防封」运行代码**（A2 签名 spoof / android_id / 包名 spoof 都还只在隔壁研究线 `防封_反检测线` 的设计里，没落到主线）。
→ 所以「防封散沙」现在**散的是个还没接上的模块**。本项 = **先把闸门建好，等防封模块落地直接挂**；闸门也可立刻复用到现有隐私 hook。

### 3.2 语义（待你确认 · 见 §四决策2）

- **0–1h（首装宽限）**：防封**开**，让用户能用、有时间授权。
- **1h 仍未授权**：最后通牒——防封仍开，但开始**软引流弹窗**。
- **≥2h 且从未授权过** → 防封**散沙（关）**；曾授权过则走正常 license 到期逻辑。
- **任何时刻授权成功** → 防封**长期开**（只要 license 没过期）。

> 招法解读：白嫖号过 2h 不授权 → 防封掉 → 被宿主当非官方 → **平台自己封它**。模块不自爆、不留痕，对齐 PROTECTION_MAP「只打破解的、散沙不崩、延迟后果」。

### 3.3 落地（复用现成散沙基建，只新增 1 个闸门）

- **新持久化 2 值**：
  - `首装时间`：写一次（Bridge 新键，如 `fits`）；建议存「墙钟 + elapsedRealtime 双锚」，联网后优先 `LeaseClock.trustedNow()`。
  - `是否曾授权`：首次 `EnvelopeStore.saveEnvelope` 成功置 true（存 EnvelopeStore，如 `eva`）。
- **新增唯一闸门** `GuardRuntime.isAntiBanReady()`（仿 `CallGuard.active()` 模式）：

```text
isAntiBanReady():
  if EnvelopeStore.isAuthorizedNow(): return true      // 授权 → 开
  if 曾授权过: return 走正常宽限(GRACE 7天)           // 不误伤付费断网
  age = trustedNow() - 首装时间
  if age < 2h: return true                             // 首装宽限 → 开（1h 起弹引流）
  return false                                         // 从未授权 + 过 2h → 散沙
```

- **挂载点**：
  - 防封模块（A2）的 hook 安装处：`if(!GuardRuntime.isAntiBanReady()) 不装/卸`。
  - 冷启动：`ModuleMain.onApplicationCreated` 第 6.5 步先算一次（与 RiskState.evaluate 同处）。
  - 触发软引流：1h 后经 `RiskPromptController`（单策略源，别新开弹窗）。

### 3.4 诚实风险（要你拍板 · 见 §四）

1. **2h 太短**：防封散沙=可能被封号，比「隐藏功能失效」后果重得多。PROTECTION_MAP 铁律=「只砸确认破解/宽限真耗尽」。2h 就判盗版，对**还没来得及付款的真用户**可能误伤。建议拉长（24–72h）或叠加绊线/重签信号才散。
2. **时钟回拨**：攻击者把系统时间往回调→永远到不了 2h。首装那刻没有服务器时间，只能靠墙钟+elapsedRealtime 单调锚兜底，联网拿 trustedNow() 后收紧。挡普通党、挡不住硬核动态党。
3. **和 V3 共存版冲突**：共存版改了包名，`anti_tamper.cpp` 现硬编码 `com.tencent.mm` 会误判篡改。上这套前先让期望包名随打包注入走（`PROTECTION_MAP.md` §10.8 已记此坑）。

---

### 3.5 信任评分机制（你要的「评分决定信任程度」）

> 核心：**两层评分、职责分开**——客户端只攒「行为分」决定自己的心跳快慢（被伪造最多影响快/慢，不开放任何能力）；真正的「信任档」由服务器用心跳上报算出来、**签进信封下发**（客户端布尔不可信，安全官红线#1 / `DESIGN.md §6`）。这样既好用又难破。

**两层评分（职责分离）：**

| 层 | 算在哪 | 输入信号 | 决定什么 | 被伪造的后果 |
|---|---|---|---|---|
| 行为分（本地·调度用） | 客户端 | 连续成功心跳次数 / 在线授权时长 / 距首装时长 / 时钟回拨次数 / 蜜罐绊线信号 | 下一次心跳间隔（升/降频）| 最多让自己心跳变快或变慢，**不开放任何能力** |
| 信任档 q（服务器·授权用）| miyou-server | 客户端 `reportHealth` 上报（env_result / lease / risk / registry / cert / version）+ 历史 + 一码多机 | 真正信任程度：**租约长短 + 是否升频观察 + 防封是否长期开** | 要伪造得骗过 Ed25519 签名（私钥只在服务器）→ 骗不动 |

**信任档 q（沿用现有信封 `tier` 字段，已实装 `GuardHeartbeat.nextIntervalMs`）：**

| q | 档位 | 含义 | 心跳节奏（现状）| 租约 |
|---|---|---|---|---|
| 1 | 新装 / probe | 刚装、刚激活、观察期 | 10~30min（拟改：首装短促抓激活，见 §3.6）| 短 |
| 0 | 稳定 | 多次心跳正常 | 1~2h（硬封顶 6h）| 长 |
| 2 | 嫌疑 | 缓存异常 / 时钟疑点 | 10min 升频密集观察 | 短 |
| 3 | 影子 | 命中蜜罐（表面正常）| 10min | 短 |
| 4 | 通告 | 危险通告 / 强制升级 | ≤6h 保证下发 | — |

→ **「评分决定信任程度」落地 = 服务器按上报算 q → q 驱动 {心跳频率, 租约长短, 防封宽限}**。客户端只用行为分调自己心跳快慢，**绝不自封信任档**（自封 = 可被本地改，回到 §10.7 P0 薄弱点）。

### 3.6 心跳节奏（现状 vs 你的提案 vs 防封建议）—— 「是否频繁」的回答

**你的提案**：装好后 2min/次 → 1h 后 5min/次 → 再往后 1h/次。

**现状（代码已实装，2026-06-11 你锁的，`GuardHeartbeat`）**：
- 新装 / probe(q=1)：10~30min　稳定(q=0)：1~2h　嫌疑(q≥2)：10min　硬封顶 6h　全程 ±15% 抖动
- 设计注释原话：「稳定态 1~6h 对 CONN 密度无压力；只在冷启动 / 租约将过期拉，**不轮询**。」

**防封官判断：2min/次「持续一小时」过频，不建议（理由有据）：**
1. 现有设计初心就是「不轮询、压低 CONN」；2min×60 ≈ 一小时 30 次出站 = 规律密集信标，正好成反检测指纹（和现状注释直接打架）。
2. CONN 密度红线 = 0.5（`CLAUDE.md §七`）；固定整点信标即使打我们自己服务器，也给宿主进程加了周期网络足迹。
3. **P18 KPI 基线还没建（F-22）→ 任何频率都不能宣称「防封安全」**，必须先用 `frida_stats.js` 实测才有结论（防封官红线#1/#4）。
4. 2min 后台网络耗电，部分 ROM 会后台杀进程 / 引用户起疑。

**你「要快」的诉求是对的**（首装要快抓激活 + 快攒信任分），但「快」应是**短促探测**，不是持续高频：

| 阶段 | 你的提案 | 防封建议（短促 → 回落）|
|---|---|---|
| 首装·未授权 | 2min × 持续 1h | 2~3min（带抖动）**只打头几次**（首 ~10–15min 内抓激活），或激活成功即停 |
| 激活后 / 1h 内 | 5min | 回落到现有 probe 10~30min |
| 再往后 | 1h | 现有稳定 1~2h（封顶 6h）|
| 嫌疑 / 蜜罐 | —（未提）| 10min 升频（已实装）|

→ 既满足「快抓授权 + 快攒信任分」，又不破坏现有低 CONN 设计。**具体数字进 §四 决策 6，等你拍板了再改 `GuardHeartbeat`。**

---

## 四、待你拍板的决策（定了我再动代码）

- **决策 1（动作范围）**：现在就实现，还是先只出设计文档（= 本文）？
  - 选项：(a) 只建本文，等理解后再说（**当前默认**）；(b) 实现工作项 B 闸门；(c) A+B 一起做。
- **决策 2（散沙阈值）**：2h / 24h / 72h / 「2h 软引流但散沙等更久」/ 「必须叠加绊线信号才散」？
- **决策 3（散沙对象）**：只散「防封」（A2 落地后），还是连现有隐私 hook 一起纳入这套时间闸？
- **决策 4（防封模块本体）**：要不要把研究线 `A2SignatureSpoof.java`（签名喂官方）那套**接进主线**？这是「防封逻辑」存在的前提；不接，则本闸门暂时只能管隐私 hook。
- **决策 5（可维护性优先级）**：工作项 A（消三处重复/单源）排在 B 之前、之后、还是并行？
- **决策 6（心跳频率 · 你问的「是否频繁」）**：首装观察用「2min 持续 1h → 5min → 1h」（你的提案，防封官评 ⚠️ **过频、易成指纹、未过 KPI 基线**），还是「2~3min 短促抓激活 → 回落 10~30min → 稳定 1~2h（封顶 6h、±15% 抖动）」（防封建议，护 CONN）？数字定了才改 `GuardHeartbeat`。
- **决策 7（评分 / 信任档来源）**：信任档 q 维持「**服务器按上报算 + 签进信封**」（建议，难破）；客户端「行为分」只在本地调心跳节奏、**不开放能力**——确认这个两层切分否？（红线：客户端不可自封信任档）

---

## 五、状态 / 纪律

- 本文 = **planning only，未改任何代码**。
- 改 `Bridge` / `EnvelopeStore` / `GuardRuntime` / `ModuleMain` / native 前 = 等 §四 拍板 + git 快照 + 装机回归。
- 落地后：结论回灌 `PROTECTION_MAP.md`（单一总账），本文降级为任务履历。

# End（等你拍板）
