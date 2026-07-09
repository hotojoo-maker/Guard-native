# SSOT — A2 / 授权 / 防破解 统一真源（合并稿）

> **状态**：🟢 主线已收口（2026-06-28 · H36 用户确认场景模型 + 首装零上报倒计时）：本文 + `DESIGN.md` / `授权风险场景矩阵_SPEC.md` / 安全官 skill / `PROTECTION_MAP.md` A2 段 —— **D-018 考古已清、A2 结论已指向本文、退款措辞→封停/删卡**（DESIGN 留机制细节 / SPEC 留场景详表，均标「以本文为准」）；`配方卡_SPEC_v1.md` 只存数值。**周边历史/过程/术语/STATUS 档的旧词另清**（见 §9）。
>
> **定位**：本文 = A2 防封 / 授权 / 防破解这条线的**唯一可读真源**。安全官 / 接手 AI 读**这一份**即可，不跳转、不互相矛盾、code-true。
>
> **真相基准（G8 代码即真相）**：文档与代码冲突以代码为准。本文用 `[码]` 标现状（代码已是这样）、`[D-020]` 标已拍板待落码（决策已定、代码未到）。
>
> **维护铁律**：① 改这条线 → 先改本文 + 代码，别处只放链接、**绝不复制**（G10）。② **不做「⚠️X 被 Y 取代、其余仍有效」考古层**——旧口径直接删、写对的单句。③ 决策履历归 `DECISION_LOG`（本文只指针，不复制决策正文）。

---

## 0. 一句话

服务端真锁 + A2 喂官方**让数据正常**（尽力非保证）+ 「篡改 / 未授权 / 到期」按**逆序线**分档撤（可逆=隐私散沙、不可逆=**不再保号**〔不再把生料加工成熟料喂官方；官方怎么判我方读不到、不预测〕只挂高置信信号）+ 蜜罐删即自爆。目标：**自己好维护、别人白嫖不值**，不追求破不了。

---

## 1. 核心打法 — 中间程序「骑官方脖子、借官方眼睛」

- **口径（G1 禁猜）**：我方**不猜官方怎么判、不猜「封 / 账号异常」的逻辑、不保证号一定安全**。只做一件事——在官方查身份咽喉**灌官方值，尽可能让我方这边的数据（签名 / android_id / 包名）读出来是「官方、正常」**。官方最终判分加密直发其服务器、**我方读不到**；故一律写「**尽量让数据正常**」，不写「保证保号 / 防住封」。
- 官方包自己长了一套查身份的本事（查签名 / 包名 / android_id）。我方**绝不另造检测**（另造 = 新增检测面 = 自找风险），只做一层中间程序卡在官方身份检查的**唯一咽喉** `getPackageInfo` → native `c$p`。
- **一灌（让数据正常）**：来查身份的，一律灌**官方态/正常态**（签名→官方 DER `18c867f0`、android_id→官方签名对应 SSAID、包名→官方）→ 我方数据读出来尽量像官方正常输入 → **尽量**不被判异常（尽力、非保证、不预测官方）。
- **一读（抓改包）**：顺手借官方这只眼读输入——签名被换 / 包被改 → 散沙 / 影子期，让破解者白忙。
- **零环境读取**：`env`（`ro.boot.*`）始终官方自己读，我方零读取（守 KPI：第三方读 `verifiedbootstate` +1 自伤）。root 不硬查，至多服务器侧软信号，付费即正版。
- **诚实边界**：咽喉口只读得到官方查的**输入**（签名 / 包名，走 Java 抓得到）；官方最终判分加密直发其服务器、我方读不到。「抓破解」= 我方自己盯输入变没变，不是偷看官方答案；「让数据正常」= 我方尽力、**非保证**。

### 1.1 A2 三轴在官替 / 共存里的差异

| 轴 | 官替版 | 共存版 | 口径 |
|---|---:|---:|---|
| **签名** | 要喂 | 要喂 | 两种形态都是重新打包/重签，签名都会偏离官方 DER；签名是已证硬轴。 |
| **android_id / SSAID** | 停喂 | 停喂 | ⚠️ 现状=**停喂**（2026-07-09 核代码）：非 root 算不出本机官方 SSAID，全局硬喂会让所有客户撞同一 id；`A2SignatureSpoof.feedOfficialSsaid` 的 `setResult` 已注释、`OFFICIAL_SSAID` 成死码，hook 仅保留被动观测（借官方眼睛，`observeAccessibility`）。留系统自然值（每机唯一稳定；服务端无 `user_key` 无法核真伪、只看一致）。root 机需精确值时改由 `ssaid_calc.py` 逐机喂。算法背景（HMAC-SHA256、`user_key` 每机随机 root-only）保留备查，但当前不喂。 |
| **包名 / 路径** | 通常不需要 | 要喂 | 官替已占官方包名；共存包名不同，需仅在 `normsg` / `bu5` / `c$p` 检测 caller 下选择性灌官方包名/路径，禁止全局改 `getPackageName`。 |

一句话：**签名轴两种形态都要喂；包名/路径是共存版额外轴；android_id 现状=停喂**（非 root 硬喂会撞车，`feedOfficialSsaid` 已 NO-OP、仅被动观测；要喂需 root 逐机 `ssaid_calc`）。

**共存包名铁律**：禁止把代码里的 `.mm` 粗暴全替成 `.mn`。共存要分两种包名：运行 / 作用域 / 数据目录 / native 期望包名用**真实共存包名**；喂给官方检测咽喉的身份输入用**官方包名 / 官方路径 / 官方 DER**。签名必须同时覆盖 `signatures[]` 和 `signingInfo` 两条读取路径，只改一条会漏。

---

## 2. 两闸（失败方向相反，必须分开、互不连坐）

| 闸 | 出口（代码） | 吊什么 | 失败方向 | 为什么这个方向 |
|---|---|---|---|---|
| **A2 防封闸** | `GuardRuntime.isAntiBanReady()` | 本地 cert 完整性 **+ 时间闸**`[码]`（D-020 已落码·L2 待 L1） | **fail-OPEN**（拿不准就装） | 误判 = **不再保号**（不再加工，咽喉只剩生料）→ 之后官方怎么判我方读不到、不预测，宁错放勿错杀 |
| **隐私闸** | `StateMachine.isActive()` 四层 AND | ① `isVipAuthorized`（=`EnvelopeStore.isAuthorizedNow`：token+Ed25519+license）② `isSensitiveConfigReady`（registry server-seed）③ `f1` 总开关 ④ HIDDEN 态 | **fail-CLOSED**（拿不准就散） | 误判可逆（密友暂没藏住，重授权即回）+ 这是变现门，宁错关（白嫖用不了）勿错开 |

- **关键洞**：把「不可逆的不再保号（撤了=咽喉只剩生料，官方怎么判我方读不到）」和「可逆的隐私散沙」用两个相反的失败方向分开管。这是整套设计最聪明的一步。
- **真防护边界**：光 hook `isActive`/`isVipAuthorized` 改 true **没用**——`isSensitiveConfigReady` 第 2 层封死（没合法 envelope，`getRecipe()` 出不来真 registry）。读 / hook 这个布尔**不算破防**（红队唯一破防标准 = 无 envelope 仍让 `getRecipe()` 出真 registry）。

---

## 3. A2 撤闸 — 场景矩阵（D-020）

### 3.1 撤 A2 的四触发（否则 fail-open 装）

> 以下均为 **A2 保号闸（不可逆，故留缓冲）** 的时序；**隐私闸（可逆）= 封停 / 删卡 / 到期一律立刻关**，不享缓冲（D-021）。

1. **重签篡改**（cert ≠ `EXPECTED_CERT` / 蜜罐绊线 = 铁证）→ **立刻散**（不等服务器；归 §4 篡改线·**不可逆**）。
2. **首装未授权**（白嫖）→ 满 **72h** 撤（断网也撤；从未授权无快照可兜底）。
3. **曾授权自然到期 / 失效** → **7 天**宽限后撤（曾授权有快照，宽限内不误杀正版）。
4. **服务器封停 / 删卡密** → **72h** 宽限后撤（复用首装 72h；全局只两个宽限值 72h/7天；针对性停短缓冲防误封，D-021）。

### 3.2 现状 vs 目标（诚实）

- `[码]` 现状（D-020 已落码 · L2 静态实证 · 待 L1 装机）：`isAntiBanReady()`（`GuardRuntime.java:138-148`）= `CompatProbe.isIntegrityIntact()` 篡改立刻散 + `isWithinAntiBanWindow()` 时间闸（`GuardRuntime.java:158-189`，try/catch fail-open）。分支（纯函数 `evalAntiBanWindow`）：首装未授权 `officialBase+72h` / 曾授权到期 `licenseExpire+7天` / 封停删卡 `cardRevokedAt+72h`，各无值 fail-open（宽限常量 72h/7天 行 135-136）。**已不再是「只有 cert、无时间闸」**。
- ① A2 时间闸 ② 官方对时接线（`LeaseClock.noteOfficialTime`/`getOfficialBaseMs` + `OfficialClock.readOfficialNowMs` + 接线 `ModuleMain §6.55`）③ latch 可恢复（`EnvelopeStore.clearCardRevokedIfAuthorized`）+ 超 2 年=丢值+`markTampered`（§7.1）—— **均已落码（L2）**，file:line 详 §8。
- **待 L1 装机**（无装机日志，**不得宣称 L1**）：DEBUG self-test 已就位——`antiBanGateSelfTest`（`[ANTIBAN-GATE]`）+ `antiBanBranchSelfTest`（8 分支 `[ANTIBAN-BRANCH]` expect/actual/PASS-FAIL），`GuardRuntime.java:200-238`。隐私闸封停/到期立刻关已是现状（§3.3）；剩余目标见 §9（Batch3 删全局 W / canary② 绑行为 / 删 Filter 明文 fallback）。

### 3.3 场景矩阵（照此实现）

| # | 场景 | A2 让数据正常 | 隐私 | 谁来撤·宽限 | 误杀护栏 |
|---|---|:---:|:---:|---|---|
| 1 | 付费授权中 | 装 | 开 | — | — |
| 2 | 临到期（前 7 天） | 装 | 开 | 不撤·**功能页弹窗·每天 1 次**续费提醒 | — |
| 3 | 自然到期 | 装→7 天后撤 | **立刻关** | A2 服务器不续租约·7 天宽限；隐私到期即关 | 服务器可信时间·付费即恢复 |
| 4 | 服务器封停 / 删卡密 | 装→**72h** 后撤 | **立刻散** | A2 心跳卡失效→**72h** 宽限；隐私 rf=1 即散 | A2 误封 **72h** 内可恢复·隐私重授权即回 |
| 5 | 首装未授权（白嫖/观望） | 装→72h 倒计时后撤 | 关 | **72h 从「官方授时有值」起算**（断网也撤）；倒计时只读官方授时驱动、**我方零上报** | 全新装无授时值 → fail-open 恒装；72h 足够激活·联网即恢复 |
| 6 | 断网（短） | 装 | 开 | 不撤 | 用上次授权快照兜底，绝不因「暂连不上」判未授权 |
| 7 | 重签 / 篡改（盗版） | 立刻散 | registry 解不开也散 | 本地 cert 立刻（零宽限） | 正版不可能重签→零误报（§4·不可逆） |
| 8 | root + 付费 | 装 | 开 | 认正版 | root 不硬查 |

### 3.4 可恢复（除篡改）

- 时间 / 授权撤（#3/#4/#5）后，**重新输入有效授权 → 恢复**（服务器权威：发新有效信封 → 客户端恢复）。
- ⇒ `[码]` `isCardRevoked`（`rf` 键）latch **已改「可被有效授权恢复」**（D-020 已落码·L2 待 L1）：收到 `rf≠1`+license 未过期的有效信封 → `EnvelopeStore.clearCardRevokedIfAuthorized`（`:233`）清 latch、两闸恢复（封停/删卡不再永久焊死）；本地改时间/清缓存不触发（须 Ed25519 验签）。
- **篡改（§4 / D-019）仍不可恢复**——重签 = 盗版铁证，单向门。

### 3.5 反分析铁律

- **正版相关**（到期 #3 / 封停 #4）= 给宽限 + 软失效（客户友好）。
- **盗版 / 篡改**（#7）= 影子期（改完当场正常）→ 不续命 → 悄悄发作（反分析，破解者定位不到因果）。
- **固定短散沙 = 给破解者送分**（掐表反推触发点），禁止。

---

## 4. 篡改 / 蜜罐 / canary / 影子期（D-019 不可逆）

- **触发源**：诱饵 `PromoConfig`（明文 URL+base64 token+开关，真功能不读它）+ 绊线 `CompatProbe`（`check()` 比编译期 `BASELINE` canary、`checkSignature()` 比 `EXPECTED_CERT`）→ `RiskState.markTampered`。
- **时序**：命中 → **7 天影子期**（`RiskState.SHADOW_HOURS_DEFAULT=168h`，表面照常）→ `TAMPER_FUNNEL` → `RiskPromptController.maybeShow()`（唯一弹窗）→ `FunnelPrompt` 引流。引流 URL 在 SO 加密引导段（`NativeBridge.getEndpoint("funnel")`），非 Java 明文。
- **散沙扩面**`[码]`：`RiskState.isTamperDegraded()` 被 **5 个**杂项功能消费——`CallGuard` / `PushFilter` / `AntiRecall` / `FakeLocation` / `FakeBalance`（`active()=isActive()&&!isTamperDegraded()`）。**密友隐藏四链不进 RiskState 散沙**（record-only 是故意，铁律 29 防误伤正版；篡改时密友散沙靠 registry 重签覆盖）。
- **canary 走 COMPARE 不走 USE**：命中→标记→影子期，**不折进 key**（折 key=误判即解不出即封、无缓冲；逆序线只用 COMPARE+影子期）。URL **永不折 key**（换 URL 会批量封旧用户）。
- **不可逆（D-019）**：篡改散沙无客户端洗白/恢复路（正版 cert 不变→`isConfirmedTamper` 对正版恒 false→根本不会误判进影子期；任何恢复路=给盗版反复重置的洗白口）。
- **诚实缺口**：canary② =「hash 弹窗稳定段 / 绑行为」**未做**（现状 canary=诱饵指纹+模块签名）→ 留诱饵常量、只 NOP 弹窗显示 = 不触发 canary；好在重签必触发 cert 绊线兜底。

---

## 5. 隐私真锁现状（诚实 · 不得称「真锁终局」）

- 当前 `android_8071` 已 `prod_server_lock`：registry 需服务器种子 `S_rel`，无种子 → `recipeOk=false` → 散沙。
- registry key 咬合牙：①cert（重签→key 错→散，NOP 不掉）②server seed（S_rel）③**W_dev 一机一密**（Batch2 服务器按设备 wrap 已部署主节点；**Batch3 删全局 W 未做** → 全局静态明文 W 仍在 → 抽一台 W+任意合法信封可离线通杀，**故未到真锁**）④重放绑定 a 案（SO 比 `expire_at`、不折 key）已落码。
- **仍未做**：删 Filter 明文 fallback、V3 官替/共存证书源与包名注入、每发行线独立回归。
- **口径**：可称「商业授权闭环 + Ed25519 防伪造信封 + 当前发行线 server seed 解 registry」；**禁称**「服务器真锁终局完成 / 授权无法破解」。

---

## 6. 命名（定死，照此写）

- **没有「退款」概念**。撤销的唯一服务器动作 = **服务器封停 / 删卡密**（后台封停或删卡 → 卡不可用 → 撤）。
- 代码符号 = **`isCardRevoked` / `markCardRevoked` / `K_CARD_REVOKED`**（2026-06-28 重命名）；旧名 `isRefunded` 作废，`rf` 仅留作信封 wire key + 本地存储键（历史缩写）。语义 = **封停 / 删卡密**，不是「退款」。
- **A2 已落码 live**（`A2SignatureSpoof.java` 存在、`isAntiBanReady` 存在、reboot 日志 `[A2SIG] installed`）。**禁写**「A2 不存在 / 主线零 A2 代码 / isAntiBanReady 需新建」。
- 命名规约（详 `CLAUDE.md` 定死词表）：不写品牌名→官方包/原版；不写「注入」（指产品形态）→重新打包；不写「封号」全词→封/账号异常。

---

## 7. 命门 — 可信时间不可冻结（时间闸能否真生效全靠它）

- 盗版「屏蔽我方服务器 + 反复重启（`elapsedRealtime` 归零）」可**冻住 `trustedNow`** → 72h 永不到 → 白嫖不死（`recon/WX_SVRTIME_RECON` 已点命门）。
- 必须：服务器时间 + `elapsedRealtime` 单调 + `max_trusted_now` 水位线（不信手机墙钟）+ **第二时间源（官方对时 `jy0.hd.b()`，微信 TimeHelper，改表杀不掉·L1 已验）**。
- 72h **从「官方对时有值（有数据）」起算**——全新装无值时 fail-open 装，有值后计时（依据：「不可能下载 72h 不用」）。
- **首装零上报（铁律）**：首装第一次启动就**必装 A2**（fail-open 保底）；首装无 token → `GuardHeartbeat` 不启动、`GuardActivation` 仅用户主动激活 → 我方**零自主联网**。倒计时**只读官方授时驱动、绝不靠给服务器发信息**（避免我方网络行为给新号添检测面 / 招风险）。
- `[码]` 现状（D-020 已落码·L2·待 L1 装机）：锚点 `jy0.hd.b()` 已找到（L1·2026-06-27），`LeaseClock.noteOfficialTime`/`getOfficialBaseMs` **已接**——`OfficialClock.readOfficialNowMs` ← 接线 `ModuleMain §6.55`（`:217-229`）：首个有效值记 `officialBase` 作 72h 起算锚、`max` 水位只抬不降、超 2 年=丢值+`markTampered`（§7.1）。装机 logcat 未采 → 标 L2、待 L1。
- **fail-open**：可信时间拿不到 / 异常 → 按「未到点」处理（宁不撤、勿误杀正版）。

### 7.1 官方对时未来上限 = 超 2 年判篡改影子（设计思路 · 防后续 AI 犟）

- **事实**：本产品单版本授权 **≤ 1 年**；这里比的值 = 微信官方对时 `jy0.hd.b()`，是「**微信服务器时间**」，不是本地墙钟、不是我方外推。
- **推论**：微信服务器时间不可能差 2 年 → 正版机 `hd.b` 永不超「现有可信 + 2 年」。一旦超，唯一解释 = 进程里 **hook 了时间 = 篡改铁证**（同「签名被改」性质，正版根本触发不了、误杀近乎零）。
- **口径（落码照此）**：
  - `hd.b` **原值** ≤ 现有可信 + 2 年 → 正常采信（`max_trusted_now` 只抬不降）。
  - `hd.b` **原值** > 现有可信 + 2 年 → **① 丢值**（不焊死 max、防瞬间误撤护正版）**+ ② `markTampered`**（D-019 不可逆影子）。比的是 `hd.b` 原值、**非我方外推中间值**。
  - 读不到 / 全新装无值 / 异常 → **fail-open 装**。
- **2 年 = 产品最长授权期（≤1年）之上的安全 margin**；阈值是常量、可调，但**不得低于产品最长授权期**。
- ⚠️ **后续 AI 别用「万一时间 glitch / 抽风」反驳**——`hd.b` 源是微信服务器、改表杀不掉（L1），无合法路径到 2 年。此口径 = 用户 2026-06-28 拍板（`DECISION_LOG` D-021），真源 = 本 SSOT，**勿改回**。

---

## 8. 代码锚点（code-true 对照 · 改代码先核这里）

| 概念 | 类 / 方法 | 现状 `[码]` |
|---|---|---|
| A2 防封闸 | `core/GuardRuntime.isAntiBanReady`（`:138`） | ✅ `isIntegrityIntact()` 篡改立刻散 **+ 时间闸** `isWithinAntiBanWindow`（`:158`）/纯函数 `evalAntiBanWindow`（`:176`，首装·封停 72h、到期 7 天、fail-open）·L2 待 L1 |
| 完整性查 | `core/CompatProbe.isIntegrityIntact` | cert≠EXPECTED 才 false；读不到/相符/异常=true（fail-open） |
| A2 喂官方 | `core/A2SignatureSpoof`（`OFFICIAL_DER_HEX`） | live，由 `ModuleMain §6.7` 在 `isAntiBanReady` 为 true 时装 |
| 隐私总闸 | `core/StateMachine.isActive` | 四层 AND（含 `isSensitiveConfigReady`） |
| 真授权门 | `net/EnvelopeStore.isAuthorizedNow` | `isCardRevoked?false : token+验签信封+license 未过期` |
| 封停/删卡 latch | `net/EnvelopeStore.isCardRevoked/markCardRevoked`（`:192`/`:205`） | ✅ 已可恢复：`clearCardRevokedIfAuthorized`（`:233`）收有效信封清 latch（调用点 `:113-117`）·L2 待 L1 |
| 篡改散沙 | `core/RiskState.isTamperDegraded` | 消费者 5：CallGuard/PushFilter/AntiRecall/FakeLocation/FakeBalance（以调用点为准，grep 即得） |
| 影子期 | `core/RiskState.SHADOW_HOURS_DEFAULT` | 168h（7 天，硬编码 override） |
| 唯一弹窗 | `core/RiskPromptController.maybeShow` | 引流 URL 走 SO `getEndpoint("funnel")` |
| 可信时间 | `core/LeaseClock` + `core/OfficialClock` | ✅ 服务器授时 + 官方对时第二源已接：`noteOfficialTime`/`getOfficialBaseMs`（`LeaseClock:85`/`:109`）← `OfficialClock.readOfficialNowMs`（`:36`）← `ModuleMain §6.55`（`:217`）·L2 待 L1 |

---

## 9. 待办缺口（L4 · 下次接手看这里）

| 缺口 | 现状 | 落点 |
|---|---|---|
| ~~A2 时间闸（72h/7天）~~ | ✅ 已落码(L2)·待 L1 装机 | `GuardRuntime.isAntiBanReady` `:138` / `evalAntiBanWindow` `:176`（72h/7天 fail-open + DEBUG 8 分支自测 `:221`） |
| ~~官方对时第二源接线~~ | ✅ 已落码(L2)·待 L1 装机 | `LeaseClock.noteOfficialTime` `:85` + `OfficialClock` + 接线 `ModuleMain §6.55` `:217` |
| ~~latch 改可恢复~~ | ✅ 已落码(L2)·待 L1 装机 | `EnvelopeStore.clearCardRevokedIfAuthorized` `:233`（收有效信封清 rf latch） |
| 续费弹窗走统一弹窗源（红线#9） | 弹窗本体 ✅ 已实现并接线（`SettingsEntry.showRenewReminderIfNeeded`·前7天·按天去重每天1次·读 `secondsToLicenseExpiry`·去续费）；仅「走独立 AlertDialog、未走 `RiskPromptController`」债 | 块D 收口 |
| ~~主真源收编降指针~~ ✅（H36 2026-06-28） | DESIGN/SPEC/skill/PROTECTION_MAP A2 段已清 D-018 考古 + 结论指向本文 | — |
| 周边历史/过程档残留「退款」旧词 | ✅ **已清（2026-06-28）**：代码符号（→`isCardRevoked`/`markCardRevoked`/`K_CARD_REVOKED`）+ 术语词表 + SSOT + DESIGN + DECISION_LOG + PROTECTION_MAP + STATUS + guard-server skill + RefundPush（改名 `封停删卡撤销_CardRevoke_…`）。🟡 **未清（dated 历史/过程档，作记录保留·可缓）**：worklog 旧条目 / 块B草稿 / 红队压测任务书 / SPEC §4 / RB1 worklog+部署清单 / F68 / PLAN / tmp_wiki | 按需再扫；`rf` 仅作 wire/存储键保留 |
| Batch3 删全局 W | 未做（待 dm≥90%） | `config_crypto.cpp` + 服务器 |
| canary② 绑行为 | 未做 | `CompatProbe` |
| 删 Filter 明文 fallback | 未做（绑 V3 发版） | 各 Filter + 发版流程 |

---

## 附：决策来源（只指针，不复制正文）

- `DECISION_LOG` **D-021**：D-020 细化——封停/删卡=72h、隐私立刻关、超 2 年=篡改影子、到期前 7 天功能页弹窗。
- `DECISION_LOG` **D-020**：A2 加时间闸 + 封停/删卡 + 可恢复（取代 D-018「未授权永不撤」）。
- `DECISION_LOG` **D-019**：篡改散沙单向不可逆（无洗白）。
- `DECISION_LOG` **D-018**：A2 吊本地 cert 完整性 fail-open / 官方 DER 本地常量 / 两闸独立（「未授权永不撤」部分已被 D-020 取代）。

*2026-06-28 H36 收口：② DESIGN/SPEC/skill/PROTECTION_MAP 的 A2 段 D-018 考古已清、结论指向本文 ✅；① 本文是否升「正式 SSOT」由用户拍。剩余旧词散在历史/过程档 + 代码注释 + 术语词表/STATUS，见 §9 按需另清。*
