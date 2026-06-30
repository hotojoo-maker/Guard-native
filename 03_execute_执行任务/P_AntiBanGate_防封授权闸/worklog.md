# P_AntiBanGate 防封授权闸（重构·版本轴）— worklog

> 2026-06-23 建档 · 底座 微信 8.0.71 · 状态：**A2 Route B（D-018）码已落 + 装机回归 Test1 首装未授权防住 / Test2 隐藏不连坐 L1 PASS（E99，2026-06-26）；Test3 重签散沙收《红队压测验证任务书》；本轮 commit（A2 范围）**。Route B 已取代 A2-1 的「registry server-seed 门控」——DER 改本地常量 `OFFICIAL_DER_HEX`、闸吊 `CompatProbe.isIntegrityIntact`（cert-only）。详见 2026-06-26b/c/d 三节 + `DECISION_LOG.md` D-018。

---

## 2026-06-29 · registry 单一真源归一（C5a 收口）— Devin 任务派发

- **触发**：PROTECTION_MAP §P0-1 仍剩 MomentsFilter 5 处混淆字面量内联（`jw1.d` / `wq.c1` / `wq.y0` / `ii5.b` 等），`ConvFilter` / `ContactFilter` 已归一，本次收尾 MomentsFilter。
- **执行者**：Devin（任务卡 `.devin/tasks/registry-unify-v1.md`）；文件白名单只含 `registry_8071.json` / `RegistryFallback.java` x2 / 三个 Filter / `gen_registry_fallback.py`。
- **禁入文件**：`signing/**` / `registry_cipher*.inc` / `net/**` / `A2SignatureSpoof.java`。
- **产出预期**：`registry_8071.json` `moments.feed` 补全混淆名 → `RegistryFallback` 重生成 → `MomentsFilter.resolveRecipes()` 覆盖 → `assembleOfficialDebug` 0 error → PR `devin/registry-unify-v1`。
- **SSOT 更新**：PROTECTION_MAP §P0-1 / TASK_BOARD P_RegistryUnify 均已落档（🟡）。

---

## 2026-06-28 · 「退款」错名清除 + 撤销机制统一命名「封停/删卡撤销 / isCardRevoked」（用户拍板）

- **触发**：用户指「不存在退款，应叫封停/删卡机制」。定名 = **封停/删卡撤销**（非「废除」——废除含永久义、与 D-020 可恢复矛盾；撤销三线 = 到期 / 封停删卡 / 篡改散沙）。
- **代码符号统一**（6 Java，lint 净）：`isRefunded→isCardRevoked` · `markRefunded→markCardRevoked` · `getRefundedAt→getCardRevokedAt` · `debugSetRefunded→debugSetCardRevoked` · `K_REFUNDED→K_CARD_REVOKED` · `Envelope.refunded→cardRevoked` · `apiForceRefund→apiForceCardRevoke` · `/api/forcerefund→/api/forcecardrevoke`。涉 EnvelopeStore/AuthEnvelopeVerifier/GuardRuntime/DebugServer/RiskState/FakeLocation。
- **保留不动**：信封 wire key `"rf"` + prefs 存储值 `"rf"`（跨 miyou-server 契约 + 升级不丢已落 latch）；服务器仓库历史符号（`cards.refunded_at` / `refund_card` / `/admin/api/cards/refund`）不动（另仓库）。
- **文档落名**：术语词表 / SSOT(§3/§6/§8/§9) / DESIGN / DECISION_LOG(D-020) / PROTECTION_MAP / STATUS / guard-server skill；RefundPush 设计稿改名 `封停删卡撤销_CardRevoke_服务器侧设计_20260626.md`。
- **未改（dated 历史/过程档·作记录保留·可缓）**：worklog 旧条目 / 块B草稿 / 红队压测任务书 / SPEC §4 / RB1 worklog+部署清单 / F68 / PLAN / tmp_wiki。
- **待办**：`.claude/skills` 镜像跑 `sync_skills.ps1`（guard-server skill 已改 `.cursor` 主源）；装机回归（debug 端点 `/api/forcecardrevoke` 验「撤销立刻散」）。

---

## 2026-06-23 · DESIGN 评估（架构师 C81）

- 评估 `DESIGN.md`（统一风控引擎设计）：方向对；签名命脉成色 **L1+L2**（A2 签名轴 Java 可 hook + 喂官方 → c$p 读官方）。
- 指出两处问题：① 时间口径 **72h/2h 自相矛盾**（DESIGN §2/§5 vs §4A）；② **KPI 基线缺**（P18 未建，F-22）。
- 证据：`DESIGN.md`、研究线 `防封权威账_2026年6月.md`（原 `PLAN.md` 已于 2026-06-30 减法删除，git 历史可查）。

## 2026-06-23 · 配方卡 v0 → v1（C82，设计only）

- 把 DESIGN/PLAN「为什么」提炼成 AI 可机械照做的「怎么做」：A 单值参数表 / B isAntiBanReady 真值表 / C 5 步 checklist / D 雷区 5 条 / E 成色总账。
- v0 → v1 修正：
  - **A2 倒序修正**——删 `isAntiBanReady()` 顶部登录门；A2 = 装→跑→生效的开机默认保护，从未授权满 **24h** 才撤（登录/授权是另一条线，不进 A2 判定）。
  - 6 个 ❓ 全填死：软引流 1h / 登录砸门 2h / 解体 24h / 付费断网宽限 7天 / 蜜罐影子期 7天 / 红线影子租约 7天（原 7-10 定 7）/ 隐私离线 72h（已实现）/ 信任分 60%（占位待 KPI）。
- 时间口径以架构师 C81 拍板为准（覆盖 DESIGN 旧 72h/2h）。

## 2026-06-23 · 授权检查官独立审（C82 换帽，纯只读）

- 9 项报告 + 四门状态：架构 **WARN**（设计only 零代码）。
- 落地修正 4 条：① GuardRuntime 类注释更新（唯一 auth-aware 出口）；② A2 料只进 `registry_pack`，禁新增 a2_pack；③ 登录砸门只经 RiskPromptController（唯一弹窗 红线#9）；④ 首装时间 Bridge 新键用 4 字符短哈希。
- 2 watch：isAntiBanReady 时间线用 `trustedNow-首装时间` **自算**，禁复用 LeaseClock.currentLevel() 的 24/72/144h 离线阶梯（否则两闸连坐）；A2 料只进 registry_pack。
- step③「A2 接主线」当时判 = **时机 BLOCK → D-015 阶段铁律**（架构不 BLOCK，时机 BLOCK）。
- 读过保护区：`core/GuardRuntime.java`（仅 isConfigReady，无 isAntiBanReady）/`net/EnvelopeStore.java`（isAuthorizedNow=Ed25519+device，P0 硬化）/`core/LeaseClock.java`（trustedNow 防回拨，24/72/144h）/`core/StateMachine.java`（isActive 四层）/`core/Bridge.java`（无首装时间键）/`ModuleMain.java`（无 A2 install）。

## 2026-06-23 · D-017 决策（总调度 C81 写入 DECISION_LOG）

- 防封授权闸 / A2 定性 = **重构**（既有防破解·授权·反检测架构的重构），按**版本轴**组织：官替 / 共存 / 管理（后台）；**不按 D-015 新功能 ①→⑤ 阶段序卡**。
- 解 step③「A2 接主线」时机 BLOCK（A2 = 官替/共存「能活下去」的内在刚需，非提前的未来功能）。
- **不撤回 D-015 / D-016**：新功能「功能先稳」仍有效；落代码仍须 授权检查官改前/改后审 + 安全官共审 + 守 2 watch。
- 官替/共存同 keystore；`P_AntiBanGate` 进看板登记。
- 证据：`DECISION_LOG.md` D-017（L137）、`TASK_BOARD.md`（L130）。

## 2026-06-23 · 配方卡落盘（C82）

- 写入本任务文件夹（UTF-8 无 BOM，已校验）：
  - `配方卡_SPEC_v1.md`（A–E 全文 + §F 版本适用面〔官替/共存/管理 + D-017 上报带包名/release_id〕 + §G 包名/打包 MD5 校验方案）。
  - `配方卡_params.json`（16 参数 + isAntiBanReady 6 分支真值表；风格沿用 `native_core/registry_8071.json`）。
- 校验：BOM=false ×2 / JSON.parse OK / ReadLints 零错误。

## 2026-06-23 · 服务器重构方案（C85，进行中 · 据总调度同步）

- 按版本统计 / 评分调整 / 下发 isAntiBanReady 结论；miyou-server / 管理版重构（安卓线）。
- 本 worklog 仅记录该并行线状态，细节以 C85 产出为准。

## 2026-06-24 · A51 指挥简化（网络安全官落盘 · 仅改文档，零代码）

> 本轮指挥分两步：先「24h→2h」，后据指挥『去掉 2h 散沙、让人抓不到难分析』二次改定 = **去固定短散 → 影子期 + 不续命 + 服务器抖动**。文档已对齐到最终模型（中途 2h 仅作演进存档）。

- 指挥决策（§7 #19–24），已写入文档（UTF-8，编辑器工具改、无乱码）：
  1. **防封解体 = 去掉固定短散沙 → 影子期7天 + 不续命 + 服务器抖动**：从未授权（观望/白嫖）不设 2h/24h 固定短散（固定短散 = 给破解者送分：装上掐表即可反推触发点）；A2 影子期内仍开/看着正常 → 到期不续命 → 像**租约自然到期**悄悄失效（破解者看到「租约过期」、对不上「没授权」触发点 = 反分析）。复用现有 7 天影子期 + 服务器抖动 = 减法。
  2. **登录砸门(2h)与解体解耦**：2h 只弹关不掉的登录砸门、**只挡进隐私功能、A2 不撤、非封号**；原「2h 撤 A2」暴露窗消除。
  3. **root → 服务器侧软风险 → 蜜罐档**：客户端**不硬查 root**（不读 `ro.boot.*` / 不扫 su·magisk 文件名，守红线#5 + §6 工程红线），只随心跳报「微信本就枚举的信息」，服务器判高危机 → 蜜罐待遇（短租约 / 影子期 / 过期废）。
  4. **付费但 root → 服务器认付费即转正版**（不当蜜罐养，避免误杀付费客户）。
  5. **『没服务器通信』= 短期断网不算（走宽限）、长期(几天)才往蜜罐推**（红线#4 不误杀断网）。
  6. **三态定义**：正版（没篡改+没root 或 root但付费 + 授权）/ 蜜罐（root/篡改/长期无通信）/ 观望（装了没授权）——后两者都走影子期+不续命、不固定短散。
  7. **「无误杀正版」钉死**：登录砸门弹窗拦截、真客户必走授权；后续不再把「解体误杀正版」当风险项复议。
- 落点：`DESIGN.md`（头部修订行 + §5A root 精化 + §7 决策 #19–24 + §2/§4A/§5 + §7#1 覆盖标注）/ `配方卡_SPEC_v1.md`（头部时间口径 + 铁律 + A 表 T_kill + B 真值表〔login_enforcer/dissolve 解耦〕 + 伪码 + §E/§F + 验收步骤）/ `配方卡_params.json`（t_kill + 真值表分支解耦 + _note）/ `落码前安全共审清单_v0.md`（G-闸真降级 go 条件 + 时钟回拨条目）。
- ⚠️ 安全官说明：二次修订后**原 2h 暴露窗消除**（A2 影子期内仍开）；反分析更强（解体像租约自然到期、无固定本地触发点）。新增误杀防护：付费但 root → 转正版；短期断网不推蜜罐（红线#4）。**蜜罐/观望 两线机制收敛到同一影子期 + 不续命 = 减法。**
- 仍设计only、零代码；落代码按配方卡 §C 从 S0 git 快照起 + 安全官 / 授权检查官双签。

## 2026-06-24 · 检测面/KPI 口径校准（研究线 composer 代理复核 · 直接改文档、零代码）

> 触发：指挥反驳「官方知道设备是否已解锁」。派 composer 子代理只读复核研究线 `防封_反检测线/防封权威账_2026年6月.md` + `证据/`，回收 L1/L2 实证后**直接改正文**（非追加修订标注，指挥要求）。

- **官方确实知道解锁（L1 证实指挥）**：normsg(`libwechatnormsg.so`) 启动期实读 `verifiedbootstate=orange`/`vbmeta.device_state=unlocked`/`flash.locked=0` + 枚举 `com.topjohnwu.magisk`，进 ~7KB `field3` 密文上报。但**读到 ≠ 封号**（同机满脏机官方包一月零封、读到 unlocked 不 kill，L1）。
- **关键反转（落地依据）**：`verifiedbootstate` KPI 数的是 `__system_property_get` **调用次数**（非值，L2）；F-19（8.0.66 L1）我方读 `ro.boot.*` → 计数 +1 逼近红线 38 → **我方读 = 自伤**。
- **KPI 方向结论 = 不反转、只强化**：维持 A39/A51「客户端零环境读取、root→服务器侧软分」；把「零新增环境读取」从防封画像偏好**升级为 KPI 硬约束**。
- **改动索引（直接改正文）**：
  - `DESIGN.md` §0.6#3：整合「官方知道解锁三件套 + 读到≠封号 + verifiedbootstate 数调用次数 + 我方读自伤」。
  - `DESIGN.md` §6 工程红线：补「不叠 KPI 足迹」+ `ro.boot` 读取触 verifiedbootstate +1 理由。
  - `配方卡_SPEC_v1.md` §A：verifiedbootstate 行注明「数调用次数 / 客户端零新增环境读取硬约束」。
- **诚实缺口（子代理标）**：服务端对 unlocked 的封号/降权规则 = L4；8071 上「第三方读 ro.boot → KPI 增量」未复验（仅 8.0.66 L1）；P18 零点仍未建（F-22）。

---

## 2026-06-25 · A2-1 签名轴落码 + 改后双审 + 账实回正（执行/架构师视角 · Vchat guard_native-D68）

> 本节为账实回正：此前 worklog/TASK_BOARD/PROJECT_INDEX 均写「设计only·未动代码」，与工作区实况背离。代码事实经源码 + git 核实（L1/L2）。

### 落码事实（L1 git / L2 源码）
- A2-1 签名轴代码已落工作区，**未提交**，在快照 `5dd0a52 snapshot(pre-A2-1)` 之上（改前 git 快照已做）：
  - `core/A2SignatureSpoof.java`（新，untracked）：afterHook `ApplicationPackageManager.getPackageInfo(self)`，灌官方 DER 到 `signatures[]` + `signingInfo` 两路径（对齐设计稿 §3 硬要求）；只动自身包、独立新类、fail-closed。
  - `core/GuardRuntime.java`（改）：新增 `isAntiBanReady() = isAuthorizedNow() && isConfigReady() && hasRecipe("a2.sig","official_der")` + 常量 `A2_SIG_GATEWAY`/`A2_SIG_OFFICIAL_DER` + `antiBanGateSelfTest`（DEBUG-only）。
  - `ModuleMain.java`（改）：6.6 self-test（DEBUG）+ 6.7 门控安装（`isAntiBanReady()` 真才装，否则记 `[A2SIG] not installed: scatter`）。
- 与 `A2接入设计稿_签名轴_20260625.md` §5「本期最小真闸」**逐字一致**；完整时间闸（T_soft/T_login/T_kill/影子期）= A2-4 后补（设计稿 §1/§21）。

### 改前双审（2026-06-23 已完成，本次复核确认）
- 授权检查官 9 项：架构 WARN（4 落地修正 + 2 watch，见 2026-06-23 节）。
- 安全官 `落码前安全共审清单_v0.md`：go/no-go 门齐。

### 改后双审（2026-06-25 本次补做，针对已落代码）
- **授权检查官 9 项 → WARN**：架构/边界/红线全过 —— 独立新类、单出口 `isAntiBanReady`(GuardRuntime)、**不连坐 `isActive()`**、不进已验证 hook、不写 StateMachine、纯主进程 Java。5 门：Entry ✅ / Auth ✅(吊 isAuthorizedNow 非裸布尔) / State ✅(不连坐) / Risk ✅(本期不接) / 分层 ✅。WARN 项 = 流程（改后审 + worklog/ledger 此前缺位，本次补）+ watch（`isConfigReady` 现不因过期/篡改信封散沙 = GuardRuntime 既有 TODO 债，非 A2 新增）。
- **安全官 改后审 10 项 → WARN**：红线 #1/#5/#6/#7 全守（闸吊 envelope+recipe、不清数据、不破坏官方包、不进已验证 hook）；fail-closed ✅（料缺→不装、release 无明文 DER）。WARN：① **闸空转**（见下）；② official_der 的「锁」强度受 registry **W 全局静态债（E9）** 封顶 —— W 一机一密前「抽一把 W 离线解任意设备信封」对 official_der 同样成立，A2 反白嫖强度 ≤ E9。
- **A2-1 不碰 device 材料/android_id**（那是 A2-3），故 `落码前清单` 最重的 **G-A2 同源**（computeDeviceHash 被官方 SSAID 污染→误杀正版/跨机重放）**本期不适用**；`EnvelopeStore`/`LeaseClock`/`Bridge` 本期**未改**。

### 闸现状（最关键，L2）
- `registry_8071.json` 仅 4 entry（conv.list / moments.feed / contact.address / search.gateway），**无 `a2.sig`/`official_der`**。
- 故 `hasRecipe("a2.sig","official_der")=""` → `isAntiBanReady()` 恒 false → A2 **从不安装** = 当前**死码、无线上行为**（fail-closed 正确，功能未生效）。

### 账实回正（本次，仅文档，UTF-8）
- 本 worklog 头部状态：设计only → 码已落(未提交)·双审 WARN·闸空转待料；本节新增。
- `TASK_BOARD.md` P_AntiBanGate 行 + `PROJECT_INDEX.md §零` 行：「设计only·未动代码」→「A2-1 码已落(未提交)·双审 WARN·闸空转待 official_der」。
- 未改 `DESIGN.md`/`配方卡`（设计真源不动，仅状态回正；守安全官收尾铁律：只更新本任务 worklog + 总览一行）。

### 下一步（需用户点头 + S0 快照 + 三端对账 + per-release regen 方可执行；动 registry/SO）
1. official_der（`md5=18c867f0…`, len 751）进 `registry_8071.json` 新 gateway `a2.sig` / key `official_der`（hex）。
2. `tools/gen_registry_cipher.py` 重生成 `registry_cipher.inc`（官替/共存各发行线各自 regen；KDF 测试向量三端对账过 = 安全官铁律）。
3. 装机 L1（DoD 见设计稿 §9）：`isAntiBanReady=true`→A2 装；`c$p.ad`/`bu5.a.a` 读官方 `18c867f0`、NON-OFFICIAL 残留 0；无 seed→散沙不装；密友隐藏不挂；KPI≈0。
4. 绿 → A2-1 作为干净 commit 落地（worklog 补 L1 + PASS 日志落盘）。

## 2026-06-25b · official_der 落料（Plan A，用户拍板放宽红线7）

> 用户决策（Vchat guard_native-D68）：**Plan A** —— DER 直接写进 `registry_8071.json`；**明确放宽红线7**（理由：私库代码不外泄）。本节记此例外 + 落料 + 校验。

- **红线7 例外（登记）**：设计稿 §红线7「官方 DER 原文不进 git 明文」本次**经用户同意放宽**（私库前提）。若日后仓库可能外泄/转交，应改 Plan B（DER 进非 git 秘密 recipe + gen merge）。
- **料来源 + 校验（L1）**：`防封_反检测线/脚本/official_der.hex` → `python` 校验 `len=751` / `md5=18c867f0717aa67b2ab7347505ba07ed` = **MATCH**（与设计稿 §4 / dimcollect baseline 一致）。
- **落点（L2）**：`native_core/registry_8071.json` 新增 `entries["a2.sig"]["official_der"]=<751B hex>`（取件口 `getRecipe("a2.sig","official_der")` = `GuardRuntime.A2_SIG_GATEWAY/A2_SIG_OFFICIAL_DER`，与代码逐字对齐）。
- **校验（L1）**：`JSON_OK entries=[conv.list, moments.feed, contact.address, search.gateway, a2.sig]`；`gen_registry_cipher.py --dry-run`（dev_cert_only）→ `schema entries` 含 `a2.sig`、`pt_len 2555`、**未写 `.inc`**（dry-run）。
- **未做（待 S_rel + 装机，运行时才生效）**：
  - `registry_cipher.inc` **per-release regen**（官替/共存各自 `--recipe release/secrets/<release_id>.json` = prod_server_lock + 该线 S_rel + 该线 cert）；当前**未 regen**（无 S_rel，dry-run only）。
  - 装机 L1（设计稿 §9）：种子后 `isAntiBanReady=true`→A2 装；`c$p.ad`/`bu5.a.a` 读官方 `18c867f0`、NON-OFFICIAL 残留 0；无 seed→散沙不装；密友隐藏不挂；KPI≈0。
- **回退路径**：未提交；`git checkout -- native_core/registry_8071.json` 可还原到快照 `5dd0a52`（`registry_cipher.inc` 本就未动）。
- **注**：只加 entry = data 变更，**不改 `derive_registry_key`** → KDF 三端测试向量不变（区别于「改 KDF=全线对账」）；但每发行线仍须各自 regen `.inc` + 验 `recipeOk` 含 a2.sig。

## 2026-06-25c · 签名轴「两半」账实区分（防混记，用户 2026-06-25 指出）

> 触发：用户指出「签名轴抓破解已实现」。核对代码 + PROTECTION_MAP §10.9 后**确认用户对**，记此区分，免与 A2-1 混账（呼应本轮 D1 账实教训）。

签名轴有**两半、两个文件、两个机制、目标互补**：

| 半 | 文件 | 干什么 | 状态 |
|---|---|---|---|
| **灌官方值（保号）** | `core/A2SignatureSpoof.java`（A2-1，本轮落） | `getPackageInfo` afterHook 喂官方 DER（熟料）→ 官方包自检读到熟料（官方态）= 保号 | 🟡 码已落·料已进明文源·**运行时空转**待 `.inc` per-release regen + 装机 |
| **抓破解（防白嫖）** | `core/CompatProbe.java`（`checkSignature` + `check`） | 读**我们自己模块 APK** 签名证书，≠ 预期 `ca421ec3…`（=被重签=改过码）→ `RiskState.markTampered`→影子期7天→散沙+引流；并查诱饵 `PromoConfig` 绊线 | ✅ **装机 PASS**（PROTECTION_MAP §10.9，2026-06-12） |

- 二者**不连坐、不同源、不同文件**：A2 灌值受 `isAntiBanReady` 闸；CompatProbe 抓破解走 `RiskState`（record-only 主链 + `markTampered`）。
- 研究线另证 L1「能抓」：`getPackageInfo`/`c$p` 咽喉能观察签名、分 `18c867f0`(官方) vs `e89b158e`(非官方)（`recon/A2_RIDE_TEST`、防封线 `A2_CSP_JAVA_CALLER`）；`DESIGN §0` 收录「借眼睛抓破解」打法。
- **互补非重复**：CompatProbe 抓「我们模块被重签」；A2 在咽喉「灌官方值」。「在 `getPackageInfo` afterHook 顺手读宿主原签名比对」那条目前**未单独实现**，但抓破解目标已由 CompatProbe 覆盖 = **非缺口**（日后若要，仅 `A2SignatureSpoof` 内加一段读-比对，非新文件）。
- 修正：本轮对话中曾把「借眼睛抓破解」说成「留给签名轴(未来)」= **口径偏轻**；实况 = 抓破解已装机（CompatProbe），此处更正。

---

## 2026-06-26 · 官方授时源定位 + 写入主线（架构/安全官视角 · Vchat guard_native-E57）

> 触发：用户问「有了官方授时是不是更稳」+「写入主线」。复稿 `DESIGN`/`配方卡` 时间模型后确认更稳，并定位官方授时源已在手。

- **结论：更稳 ✅**——反白嫖命门 = 时间必须前进；当前仅我方服务器 `sn` 单源，盗版「屏蔽我方服务器 + 反复重启（`elapsedRealtime` 归零）」可冻住 `trustedNow` → 影子期永不到期 → 白嫖不死。官方授时独立于我方服务器，堵此洞。
- ~~**官方授时源已定位（L2 + 复用 L1）**：`ConvFilter.extractConvTime(item)` 读 `field_conversationTime`（每会话最近消息服务器盖戳时间，epoch ms；Frida L1 实证 2026-05-29，P_CF3 排序本就在用）。~~
- ~~**接法（待落码）**：会话列表取 `max(extractConvTime)` → 新增 `LeaseClock.noteOfficialTime(ms)`（只抬 `max_trusted`、绝不降 + 未来上限兜底）。~~
- ⚠️ **作废（2026-06-28）**：`field_conversationTime` 锚点已弃（要登录 + 有会话、空闲偏旧）。官方授时第二源真锚点 = **`jy0.hd.b()`**（微信 `MicroMsg.TimeHelper`，L1 实证**改表杀不掉**；`hd.c()` 跟墙钟、禁用），以 `DESIGN.md §6` / `A2接入设计稿 §5②` 为准。接法改为读 `jy0.hd.b()` → `LeaseClock.noteOfficialTime(ms)`（仍未落码）。
- **写入主线**：本 worklog + `A2接入设计稿_签名轴 §5`（找授时点 待定→已定）+ `DESIGN.md §6`（双授时源防冻结）。设备 `609b4b18` + frida 在线（可选 L1 复证一条实值）。

## 2026-06-26b · A2 防封闸改吊本地完整性（Route B）— 任务启动 + 改前审查（执行/安全官/授权检查官 · Vchat guard_native-E87）

> 触发：用户 2026-06-26 拍板 —— A2 防封改吊「本地完整性（签名 cert/canary 未被改）」，**不再吊授权/server seed**；官方 DER 本地化（公开值）。首装/断网/未授权都防封；被改/重签→散沙。隐私 `isActive`/`isConfigReady` 不动（仍 server-seed），两闸独立。实现走 **Route B（本地常量 + 完整查，减法版）**。

- **本节状态（零代码改动）**：已读 CLAUDE.md / DESIGN.md §5.1·§6 / A2接入设计稿 §5·§6 / 安全官 skill「防封能力反白嫖」+ 硬红线 / 授权检查官 §九 / 代码（A2SignatureSpoof · GuardRuntime.isAntiBanReady · ModuleMain §6.6-6.7 · CompatProbe · RiskState · PromoConfig · registry_8071.json a2.sig）。**仅出改前审查 + 「精确改哪几行」施工单**，待用户点头 + S0 快照后才动手。
- **关键账实（L2 源码）**：当前 A2 运行时仍空转（`registry_cipher.inc` 未含 `a2.sig` → `hasRecipe` 空 → `isAntiBanReady` 恒 false → A2 从不安装，见 2026-06-25 节）。Route B 改完后 A2 **首次具备实装条件**（本地完整即装、无需 server seed）。official_der 明文已在 `registry_8071.json:44`（751B / md5 `18c867f0`，公开值，红线7 已于 2026-06-25 经用户放宽）。
- **⚠️ 与既有锁定决策的张力（已在改前审查标红、待用户确认）**：本改**反转** DESIGN「反白嫖三道锁 #1 / 附录A 决策 #1/#2/#3/#28」—— 原 `isAntiBanReady` 吊授权、白嫖到期撤 A2（以「号被封」反制白嫖）；新口径 = 防封惠及**所有未破解副本**（含白嫖/断网/未授权），只在 cert/canary 篡改时散沙（**保留反破解杠杆、放弃反白嫖杠杆**；隐私付费门 `isActive` 不动仍是主变现闸）。落码后 DESIGN/安全官 skill 文档将与代码不一致，文档收敛属 AI-2 线 / 需另行用户同意（G5），本任务不碰文档减法。

## 2026-06-26c · Route B 落码（4 文件 · 未提交 · 装机待跑 · E87）

> 用户拍 A/B(强化)/C 后落码。**B 强化（必做）**：A2 安装门只认 cert，canary 不进门（见下）。

**S0 快照**：`snap/A2-routeB-S0/20260626-1900`（stash，在 `snap/A2gate/20260626-1848` 之上；工作树 44 项改动前后一致、零丢失）。

**改的 4 文件（L2）**：
1. `core/CompatProbe.java` — 新增纯查询 `isIntegrityIntact(ctx, modulePath)`：**仅 cert**——读到证书且确证 ≠ `EXPECTED_CERT` → false（散沙）；读不到/相符/异常 → true（保护优先，逆序线 fail-open）。**既有 `check()`/`checkSignature()` 一行未动**（铁律29，仍喂 RiskState 影子期）。**canary 刻意不进本门**（吊编译期 `BASELINE`、漏重算会整片误封）。
2. `core/GuardRuntime.java` — `isAntiBanReady()` → `isAntiBanReady(Context, String)` = `CompatProbe.isIntegrityIntact`；删无用 `EnvelopeStore` import、加 `Context` import；`antiBanGateSelfTest` 改打 cert 子信号；注释更新为 Route B/D-018；常量 `A2_SIG_*` 保留（代码不再读）。
3. `core/A2SignatureSpoof.java` — `resolveOfficialDer()` 改读本地常量 `OFFICIAL_DER_HEX`（751B，**逐字校验 = `registry_8071.json` a2.sig**，md5 `18c867f0717aa67b2ab7347505ba07ed` MATCH）；类/install 注释更新（不再走 registry，fail-open）。
4. `ModuleMain.java` — §6.6/§6.7 两调用点传 `(app, sModulePath)` + 注释更新。

**自检（L1/L2）**：ReadLints 四文件**零错误**；`DER_EMBED_MATCH True`（embed hex ⊆ json 源）；grep 无遗留 no-arg `isAntiBanReady()` / `antiBanGateSelfTest(TAG)` / `getRecipe(A2_SIG)` 调用；`EnvelopeStore` 在 `GuardRuntime` 仅余注释（import 已删、lint 净；`ModuleMain` 自身心跳仍用 `EnvelopeStore`，未动）。

**未做（需设备 / 待跑）**：装机回归三件 —— ① 首装未授权冷启 → A2 装 · `isAntiBanReady=true` · 读官方 `18c867f0` · NON-OFFICIAL 残留 0；② 正版 `level=正常` · 密友隐藏照常（不连坐 isActive）；③ 重签包 → `isIntegrityIntact=false` → A2 不装。**绿后**：本 worklog 补 PASS + 翻 STATUS 行 + commit。

## 2026-06-26d · 装机回归 Test 1+2 PASS + test3 收红队（执行/终端 · Vchat guard_native-E99）

> 用户选 B：1+2 装机绿 + test3（重签散沙）静态已证、收进《红队压测验证任务书_20260626.md》→ 落 worklog + commit。设备 `609b4b18`（MI 9 / cepheus）· build `assembleOfficialDebug` → BUILD SUCCESSFUL · `adb install -r` Success（固定签名 `guardFixed`，无签名冲突）。

### Test 1 首装未授权冷启 — ✅ PASS（L1 原文 · 见本节内联；原始 logcat 本地 transient 未入库）
- `[auth] NO_LICENSE → not bound yet` + `[native] setAuthState=4` + `[auth] evaluate=4 (record-only, not gating)` = 设备**当场就是未授权态**。
- `[ANTIBAN-GATE] ready=true certIntegrityIntact=true (gate=local-cert; D-018: unpaid/offline also protected)`。
- `[A2SIG] installed (self=com.tencent.mm, der=751B)` + `[hb] registry ... [a2.sig] recipeOk=true` + `[risk] level=正常`。
- 崩溃扫描 = 0。**结论**：未授权（NO_LICENSE）下 A2 仍装、der=751B、level=正常 → Route B「未付费也保号」+ 两闸独立（A2 起 ↔ 授权倒）当场 L1 坐实。

### Test 2 隐藏不连坐 — ✅ PASS（L1 原文 · 见本节内联；原始 logcat 本地 transient 未入库）
- `state=H` + `[CF:clean] start ids=1 src=BUS-H mvvm=MvvmConvList` + `[CF] L4collect removed wxid=wxid_lzd2va16jd1622` + `[CF:clean] removed=2 src=BUS-H` + `level=正常`；用户目测密友隐藏、微信运行正常。
- 崩溃扫描 = 0。**结论**：A2 改后隐藏路照常（不连坐）。诚实注：设备当前未授权态、隐藏仍生效（v1 授权 record-only），恰证 A2 改未碰隐藏路。

### P1 静态重验（兼 AI-2 掉线那份 · 全绿 · 只读）
- DER：`len=751` / `md5=18c867f0717aa67b2ab7347505ba07ed` / `registry_contains_same_hex=True`（`OFFICIAL_DER_HEX` 逐字节 = `registry_8071.json` 源）。
- `CompatProbe` diff = **37 增 0 删** → `check()`/`checkSignature()`/`BASELINE` 一行未动（additive-only）。
- `PromoConfig.java` 未改 + 06-12 以来零提交 → canary 三值仍平 `BASELINE`（正版 canary 不误报）；`FunnelPrompt`/`RiskPromptController` 06-12 以来零提交 → 引流链不变。
- B 加强坐实：canary 不进 A2 闸 → 逆序线误封风险 = 0。
- 4 文件 ReadLints 零错（本会话亲验）；`ModuleMain` L209 绊线 / L221 selftest / L229 闸 三调用点均已传 `(app, sModulePath)`。

### Test 3 重签散沙 — 收红队（未现场跑）
- 按用户决策收进《红队压测验证任务书_20260626.md》（重签/破解本属红蓝对抗）。静态已证（L2）：`CompatProbe.isIntegrityIntact` 仅在 cert SHA-256 == `EXPECTED_CERT` 时返 true、差签即 false（确定性死逻辑）；live 重签散沙留红队。

### 本轮落地
- 本 worklog（本节）+ `STATUS_防封加密线.md` 验证列翻新 + `DECISION_LOG.md` D-018 状态 → 装机 1+2 PASS。
- commit：4 文件 Route B + 三 .md（worklog/STATUS/DECISION_LOG）。**只提交 A2 范围**（RB1 / LeanCloseout / skill / 其它线 WIP 不动）。

## 2026-06-26e · 块B 防封反盗版闭环补全（③退款 + ②散沙扩面 + ①T_soft + ④续费债）— 落码完·装机待跑（执行/安全官/授权检查官 · Vchat 本地开发ai-1·F22）

> ⚠️ **账实更正（2026-06-27 · F86 · 以代码为准 G8）**：本节 ①「T_soft 软引流」**已被架构师-F17 推翻并回退**（D-018：未授权 = 零弹，不抬 `OFFLINE_FUNNEL`、不软引流、不撤 A2）。现 `core/RiskState.java` **无** `T_SOFT_MS`/`KEY_INSTALL_SEEN`/`isPastSoftGrace()`，`evaluate()` 仅对 license 真过期抬 `OFFLINE_FUNNEL`（见 `RiskState.java:126` 注释）。下方 ① 段（含「改文件 8」「待验 #3」）为**历史记录、不代表现状**。②散沙扩面 / ③退款撚闸 仍在码、有效。

> 触发：架构师（E99/块0 SPEC §4·§6）拍死块B 四决策。改前已报矛盾（配方卡 vs D-018）→ 架构师全拍：① 时间闸只 T_soft（T_login 降 v2、未授权永不撤 A2 除退款）② 散沙只杂项（防撤回/定位/通知/未读，密友四链绝不进 RiskState 散沙）③ 退款撤闸字段 `isAntiBanReady = isIntegrityIntact && !isRefunded`（唯一例外）④ 续费弹窗保留现状只记红线#9 债。

**S0 快照**：`stash@{0}` = `snap/blockB-pre/20260626-2242`（含本轮前工作树全状态，`git stash apply --index` 已复原、零丢失）。

**改的 8 文件（L2 · ReadLints 全零错）**：

③ 退款撤闸（信号源 server push 留块A，本轮只落客户端字段+撞闸）
1. `net/AuthEnvelopeVerifier.java` — `Envelope` 加 `refunded` 位（`rf`，`p.optInt("rf",0)`；服务器签名覆盖整 payload，伪造该位需私钥）。
2. `net/EnvelopeStore.java` — 加 `K_REFUNDED`(可信时间戳) + `isRefunded()`/`getRefundedAt()`/`markRefunded()`(幂等,记首见,`LeaseClock.trustedNow()`) + `debugSetRefunded(bool)`(DEBUG-only)；`isAuthorizedNow()` 顶部加 `if(isRefunded())return false`（→ 隐私 isActive 四层断）；`saveEnvelope` 收到 `e.refunded==1` → `markRefunded()`；`revokeKeepToken` **不**清 refund（退款不自愈，区别于离线/到期）。
3. `core/GuardRuntime.java` — `isAntiBanReady = !EnvelopeStore.isRefunded() && CompatProbe.isIntegrityIntact`（退款=唯一连坐 A2 的非篡改场景）；selftest 打 `refunded=`。
4. `debug/DebugServer.java` — 加 `/api/forcerefund`(POST `{on}`)→`apiForceRefund`→`EnvelopeStore.debugSetRefunded`（装机验「退款立刻散」；release `BuildConfig.DEBUG=false` 空操作）。

② 散沙扩面（只杂项，复用 `RiskState.isTamperDegraded()`=确认篡改超影子期；正版恒 false → 行为逐字不变，不误伤·铁律29）
5. `moduleC/AntiRecall.java` — 防撤回 hook 在 `isVipAuthorized && isAntiRecallEnabled` 后加 `if(isTamperDegraded())return`。
6. `moduleE/FakeLocation.java` — 定位注入器加 `if(isTamperDegraded() || EnvelopeStore.isRefunded())return`。⚠️**额外**：定位注入器原本只吊 Bridge 开关、不 auth-gate，退款不会自动关它 → 我加了 `isRefunded` 让「退款隐私一起死」对定位也成立（narrow,只影响退款者,正版/未授权恒 false）。**请架构师确认保留**（超 ③ 字面但合 SPEC #6「隐私立刻 die」精神）。
7. `moduleC/PushFilter.java` — 加私有 `active()=isActive()&&!isTamperDegraded()`，主进程 6 个闸（L1/NM主支/MSGALERT/NEWMSG/FGMUTE/UNREADFIX×2）由 `isActive()` 换 `active()`。**:push 子集不动**（铁律30 无 RiskState/Bridge，仍 NativeBridge.isHidden；与 CallGuard :push 子集一致）→ 限制：主进程被杀仅 :push 收消息那条边缘路径暂不随 tamper 散沙（罕见，记此）。

① T_soft 软引流（只 RiskState）
8. `core/RiskState.java` — 加 `T_SOFT_MS=1h` + `KEY_INSTALL_SEEN`(首装可信时间基准) + `isPastSoftGrace()`；`evaluate()` 加分支：`!isAuthorizedNow() && !isRefunded() && isPastSoftGrace() && 当前<OFFLINE_FUNNEL` → 抬 `OFFLINE_FUNNEL`（软弹窗·可关·引导付费，复用现有 RiskPromptController 软文案）。**不撤 A2**（D-018）、**不连坐隐私四链**（未授权本就 isActive=false 放行）。触发节奏 = 冷启/心跳 evaluate（与到期/篡改 funnel 同款，不新增 onResume re-evaluate，守铁律29）。

④ 续费弹窗 = **现状保留**（`SettingsEntry.showRenewReminderIfNeeded`，7天窗+可信日去重，已实现），**未动码**；记债：走自有 AlertDialog 未走 `RiskPromptController` → 撞安全官红线#9（单一弹窗源）→ **块D 收口统一**（SPEC §4 已钉）。

**改后审查（执行兼安全官+授权检查官 · 结论 PASS）**：
- 安全官：真锁未削弱（refund 是本地标志、不碰 server seed/registry/Ed25519）；不清用户数据（markRefunded/散沙只早退）；散沙走 isTamperDegraded（过影子期）+ 软引流走软文案可关；RiskState 仍唯一等级、RiskPromptController 仍唯一弹窗（续费债块D 收）。
- 授权检查官：未乱接状态机（散沙=只读 isTamperDegraded）；退款/未授权均**收紧**非放开；:push 仍 NativeBridge（铁律30 守住）；保护区(GuardRuntime/EnvelopeStore/RiskState/DebugServer)改动经 E99 拍板 + 本审；模块边界清晰、无需拆码。门控 Entry✅/Auth✅(收紧)/Config✅(未动)/State✅(未动)/Risk✅(扩面)。
- 一处需架构师拍：FakeLocation 退款散沙（见 6 ⚠️）。

**🔬 待验证（需设备 609b4b18 装机 L1，绿后补 PASS + 翻 STATUS + commit）**：
1. 退款立刻散：`/api/forcerefund {on:true}` → 冷启/回前台 → `[ANTIBAN-GATE] ready=false refunded=true`（A2 不装）+ 密友重新可见（isActive 断）+ 0 崩溃；`{on:false}` 复位恢复。
2. 散沙扩面：DEBUG `forcefunnel`(进 TAMPER_FUNNEL) → 防撤回/定位/通知/未读失效；密友隐藏四链**仍生效**（不连坐）。
3. T_soft：未授权冷启 → 首装记基准（首次不弹）；>1h 后冷启 → `unpaidSoft=true` `level=离线引流` → 软弹窗可关；A2 仍 `ready=true`。
4. 不误伤正版：授权态 `level=正常`、四链+杂项全常态、无引流弹窗、0 崩溃。

## 2026-06-28 配方卡时间模型对齐 D-020+A3（Vchat 配方卡对齐-A3 · 只改文档不碰代码）

> 触发：配方卡停在 A51+C18（2026-06-24）旧口径，与 D-020（2026-06-27）+ A3 补丁冲突；SSOT/DESIGN 已对齐，配方卡是唯一未跟上的。流程：先只读对账 → Vchat 回报 → 用户拍 A3 补丁（封停/删卡=72h、仅自然到期=7天）后落改。

**改 2 文件（只文档 · ReadLints 全零错）**：
1. `配方卡_SPEC_v1.md`：顶部时间口径+铁律段重写；A 表删 `T_kill 影子期7天`、加 `首装未授权撤72h`+`服务器封停/删卡撤72h`、`自然到期续费宽限`明确仅7天、`T_login 2h 降v2`、`T_soft 1h` 标可选UI非撤闸；B `isAntiBanReady` 真值表重写为 11 分支（篡改立刻散/已授权/封停删卡72h宽限+撤/付费断网/自然到期7天宽限+撤/首装无授时fail-open/首装<72h/首装≥72h撤/可信时间异常fail-open，带「可恢复」列）；C②③验收口径 / E 成色 / F 版本表 / 待办段均改挂 D-020+A3。
2. `配方卡_params.json`：删 `t_kill`、加 `unauth_revoke`(72h)+`card_revoke_grace`(72h)、`auth_expiry_grace` 改仅自然到期7天、`t_login` 标降v2、`truth_table.branches` 重写为 11 分支带 `recoverable`；顺手对齐 `conn_density_redline` meaning（原 json 误写"即停"，SPEC 为"非即停"）。

**关键修正（A3 补丁，纠正 D-020 原文）**：封停/删卡 = **72h**（非 D-020 原文 7天），仅自然到期 = 7天；全局只两个宽限值 72h / 7天；撤后除篡改可重授权恢复（`isCardRevoked` latch 待改可恢复）。`未授权时间闸`（固定72h·官方授时 jy0.hd.b() 驱动）与 `篡改7天影子期`（D-019·RiskState 线）两条独立线、文档已显式标注别混。

**纪律**：数值真源只配方卡一处（G10），机制引 SSOT §3 路径不复制正文；UTF-8 无 BOM；未碰代码/SO/registry/SSOT/DESIGN/术语词表。遗留待拍：`T_soft 1h` 去留（建议随 T_login 一并降 v2）。

## 待办（落代码前必过）

- 🟡 **安全官共审**：A2-1 只动 `GuardRuntime`(isAntiBanReady) + `ModuleMain` + 新类 `A2SignatureSpoof` → 改前(2026-06-23) + 改后(2026-06-25) 双审已过(WARN，见 2026-06-25 节)；`EnvelopeStore` / `LeaseClock` / `Bridge`（首装时间键 / device 同源）本期**未改**，留 A2-3/A2-4 再共审。
- ⬜ **MD5 方案落地**：扩 `tools/gate_three_axis.js` 发版门 + `release_manifest` 后台存（运行时自校验归阶段⑤ P32，本方案不加运行时代码，待指挥审）。
- ⬜ **P18 KPI 基线**：官方包跑 `frida_stats.js` 建零点（F-22），校准 60% 信任分 / 心跳频率。
- ⬜ step③ android_id 同源硬测（D1）：A2 接后两机 envelope `d` 仍各异。
