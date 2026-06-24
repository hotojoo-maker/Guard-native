# AUDIT · 接手审计（LeanCloseout 交接验收）

> **角色**：Guard Native 接手审计 AI（全新、只读、**未改任何代码 / 未改别处文档**；本文件 = 唯一新建产物）。
> **出具**：2026-06-25 · **场景**：原总指挥掉线，新 AI 只凭交接卡 + recon 接手，先验「卡够不够」再查「进度真不真」。
> **只读输入**：`任务卡.md` + `recon/{R1_C7挂空核账, R2_内联明文核账, R2b_C7挂空对账, R3_V3现状_D8前置, R4_W现状_三端KDF, R4_W_E9_KDF核账, R4b_E9设计草案}` + `worklog.md` + `c5a_install_20260625.log`。
> **旁证（仅确认锚点可达，未深读正文）**：全仓 `DESIGN.md` 落点 · `PROTECTION_MAP §10.5/§10.6/§10.9` 存在性 · `s_rel 7255096e` 落点。
> **成色口径**：L1 动态日志原文 / L2 静态实证 / L3 收敛推断 / L4 待验证（无包内证据）。
> **红线遵守**：只读 · 不改代码/别处文档 · 结论标级 · G1 不猜（无证据标 L4）。

---

## 0. 一句话总评

**侦察/设计层 ≈ 95% 收口，改码落地层只动了 C5a 一步；交接卡的「单一真源」此刻有 3 处自我漂移——`§8` 现状表落后于它自己的 recon。**

- 能接手：`§0~§7`（原则/分工/顺序/铁规/红线/不做/worker 模板）质量高、自洽、可照搬，红线与派工链清楚。【L2】
- 但 `§8 现状表` 与已完成的 `R2b` / `C5a 装机 log` **对不上**：R2b 标「在跑」实为已完成、`C7 接4/缓3` 实为 `接6/缓1`、「接手下一步」还停在「等 C5a log」而 log 已到且 PASS。**接手前必须先用 R2b + 装机 log 覆盖 §8，否则按卡面 `接4/缓3/等log` 行动就是按过期账本干活。**【L2】

---

## 1. 进度总评（分模块 + 一句证据）

> 拆两轨：**侦察/设计**（核账、定方案）与 **改码落地**（真改文件 + 装机回归）。% 为审计估算（L3，依据见证据列）。

| 步（§8） | 内容 | 改码落地 | 侦察/设计 | 一句证据（在哪 / L级） |
|---|---|:--:|:--:|---|
| **C5a** | 4 Filter 22 兜底字段 → 构建期从 json 生成 | **~90%** | 100% | `c5a_install_20260625.log`：debug `fallbackSelfTest=ok×4`、release 10 absent（**L1**）；尾巴 = recipeOk 真解 / 正向屏测未闭（见 §4 风险） |
| **C7-删8** | 删 8 个挂空 registry 键 | 0% | 100% | `R2b §4`：删8 = JDK名3 + dead/冗余3 + 描述串2（**L2**） |
| **C7-接(卡:4 / 实:6)** | 挂空字段接回 registry | 0% | 100% | `R2b §4`：接6 = l1_methods/l2_method/contact_fields/actor_field_names/contact_field/type_field（**L2**） |
| **C6** | Push/Search 锚点收 registry | 0% | ~90% | `R2 §2.4/2.5`：Search `z15.ef6`/`fz2.e` 脆→三证/留明文；Push 全限定名低漂移→延后、守 :push（**L2**） |
| **C5b + C7缓1** | 已验证 hook 回调体内 `kc5.y` 等 | 0%（铁律29 故意缓） | 100% | `R2 §4`：A 类 6 处回调体 + `R2b`：C7缓1 = item_class（**L2**） |
| **E9 W_dev** | W 全局静态 → 一机一密 KDF | 0% | ~80% | `R4_W_E9 §1.1` W 全局静态实锤 + `R4b` 草案/向量(SAMPLE)/A2隔离/灰度3步；真 wseg 未冻、服务器侧 L4（**L2+草案**） |
| **D8** | 删 release 明文 fallback | 0%（⛔冻结） | 100% | `R3 §3.2`：6 缺口 G1~G6 + 解冻最小路径（**L2/L3**） |
| 服务器测试卡（recipeOk 真解） | server seed 完整链验 | 0%（本轮跳②） | — | `worklog:54` 留待；`install log` 无 seed→scatter（**L4 包外**） |
| 管理后台（miyou-server） | 后台/发行线状态 | 包内未跟踪 | — | 包内 0 命中（**L4 包外**） |

**总体（L3）**：`侦察/设计 ≈ 95%`（R1–R4b 全 done、C7 准数已出、E9 草案齐）；`改码落地 ≈ 10%`（仅 C5a，且尾巴未闭）。**closeout 远未收口，但「该怎么收」已基本盘清。**

---

## 2. 一致性核对表（卡说 vs 实际）

| # | 条目 | 卡（§8）说 | 实际（recon / log） | 判定 |
|:--:|---|---|---|:--:|
| 1 | recon 报告存在 | 列 R1/R2/R3/R4_W_E9/R2b/R4b | 7 个 Rx **全在磁盘**，另有未列的 `R4_W现状_三端KDF.md` | 对（多 1 份未列，见 #11） |
| 2 | **R2b 状态** | 「🟡 在跑（R1 15 vs R2 13 对齐出准数）」 | R2b **已完成**：并集 15 = 删8/接6/缓1，有终判与分歧收敛 | 🔴 **漂移** |
| 3 | **C7 接 N** | 「C7-**接4**」 | R2b 终判 **接6**（R1 接4 + l1_methods/l2_method 由「受限」升「接」） | 🔴 **漂移** |
| 4 | **C7 缓 N** | 「C5b+C7**缓3**（kc5.y 等6处 + n,m/s）」 | R2b：C7 **缓1**（仅 item_class）；n,m/s 已改判「接」 | 🔴 **漂移** |
| 5 | C7 删 N | 「C7-删8」 | R2b 删8 | ✅ 对 |
| 6 | C5a debug fallback | 「① debug `fallbackSelfTest=ok×4`」 | install log 4 行 SF/MF/CF/CTF `fallbackSelfTest=ok ready=true` | ✅ 对（L1） |
| 7 | C5a release 明文 | 「③ 该清 10 名全 absent，残留 5=C5b 旧账」 | install log RELEASE 10 absent / 5 present，5 = ContactDiscoveryHook:459 / ContactLabelHideGuard:31,35 / MomentsFilter `*Count` | ✅ 对（L1） |
| 8 | C5a recipeOk | 「recipeOk 待服务器测试卡完整链验；跳②」 | install log 无 seed→scatter→PHASE1B~1E FAIL；recipeOk 真解未验 | ✅ 对（诚实标待验） |
| 9 | **C5a 正向屏测** | §8 行标「✅ 验过」，未提屏测 | `worklog:54` 明示「密友隐藏正向屏幕实测**未做**」 | 🟡 **欠注**（✅ 未带 pv 口径尾巴） |
| 10 | **接手下一步** | 「等 C5a 装机 log（判过/没过）」 | `c5a_install_20260625.log` 已在 + worklog 已落 L1 PASS | 🔴 **漂移**（停在已完成的前一步） |
| 11 | R4 侦察成果 | 仅列 `R4_W_E9_KDF核账.md` | 磁盘有 **两份 R4**（`R4_W现状_三端KDF.md` + `R4_W_E9_KDF核账.md`），内容高度重叠 | 🟡 重复/未全列 |
| 12 | 服务器 s_rel | §8/§3 **未给** s_rel 值 | `7255096e` 仅在 `S3a0_ServerSeed设计/result.md`（**包外**） | L4 包内不可证 |
| 13 | DESIGN.md §0 锚点 | 「接手第一步先读 DESIGN.md §0」（无路径） | 全仓唯一 `DESIGN.md` 在 `P_AntiBanGate_防封授权闸/` | 🟡 可达但需自寻路 |
| 14 | PROTECTION_MAP §10.6/§10.9 | §3 指为现状权威 | 存在（§10.6 行261 / §10.9 行445 / §10.5 行246） | ✅ 对 |
| 15 | 管理后台 | §8 未列入总图 | 包内 0 命中 | 🟡 未覆盖（L4） |

> **结论（L2）**：硬证据（C5a 的 L1 装机 log）**全对得上**；漂移集中在 **§8 现状表自身没跟上 R2b 与 C5a log**（#2/#3/#4/#10 四处，同一根因：卡在 R2b 完成前写就、之后未回填）。这恰好打脸 §3「现状权威只认 §8」——此刻 §8 反而比 recon 旧。

---

## 3. 交接文档评分

**评分：82 / 100 —— 够新 AI 接手，但接手第一动作必须是「拿 R2b + 装机 log 校正 §8」。**【L3】

**强项（照搬级，别动）**
- `§0` 三原则（用户零代码 / AI 可接续 / 机器验证）+ `§1` 人机分工 + **驱动循环 6 步 + 并发铁律（侦察并行·改码串行）+ worker 提示词模板** = 派工可直接复用。【L2】
- `§6 硬红线` 齐：真锁在服务器+SO、secret 不进客户端、不信手机墙钟、fail-closed、四 pack+`GuardRuntime` 单出口、`:push` 白名单、禁 `ro.boot.*`、G1、标 L1~L4。【L2】
- `§3 设计锚` 指过去不复写（守单一真源），且 §10.6/§10.9 锚点实测可达。【L2】

**缺什么（接手前需补/校正）**
1. 🔴 **§8 现状表 3 处漂移**（核对表 #2/#3/#4/#10）：R2b「在跑→已完成」、`接4→接6`、`缓3→缓1`、「下一步：等 C5a log→C5a 已 PASS」。
2. 🟡 **DESIGN.md 无路径**：`§3`/接手第一步只写「DESIGN.md §0」，新 AI 须自行 glob（全仓唯一，但徒增摩擦）。
3. 🟡 **服务器侧无状态、无 runbook**：`管理后台` 未入 §8；`服务器测试卡`（recipeOk 真解所需 server seed）只标「待」，没写「找谁/怎么拿/在哪验」。
4. 🟡 **C5a ✅ 口径偏乐观**：`fallbackSelfTest=ok` 证的是 **DEBUG fallback == registry 值**，**不等于** 加密 registry 运行时真解（那要 recipeOk=true + server seed）；§8 一个「✅」把「兜底归一」与「功能正向/真解」混为一谈，应拆注。
5. 🟡 **R4 两份未合并**（核对表 #11）：重复侦察，建议合一 + 在 §8 全列（质检 skill §④重复文件）。

---

## 4. 风险 / 遗漏盘点（呼应任务四问之四）

| 风险 | 卡是否已记 | 审计判定 |
|---|:--:|---|
| **E9 × A2 同读 ANDROID_ID 坑** | ✅ §8 E9 行 + `R4_W_E9 §R-4` + `R4b §2` | 记得**充分**：`computeDeviceMaterial` 必走 `rawAndroidId` + memoize 预热 + 早于 A2 install，否则全机 `W_dev` 塌同值、牙③自废（L2+L3） |
| **D8 六缺口 G1~G6** | ✅ §8 + `R3 §3.2` | 记得**充分**：含 cert 轴硬编码 `ca421ec3`、V3 证书源未切、`GUARD_RELEASE_ID` 未 flavor 注入、回归垫缺（L2/L3） |
| **C5b 5 处残留内联明文** | ✅ C5a 行 + worklog + install log | 记得**充分**：ContactDiscoveryHook / ContactLabelHideGuard / MomentsFilter `*Count`，本轮明令不碰（L1/L2） |
| **pv 口径（正向验证）** | 🟡 仅 worklog，§8 未带 | **欠注**：审计读「pv 口径」= 正向屏测口径（密友隐藏不误伤）。C5a DoD 含「密友隐藏正常 + 负向 scatter」，但**正向屏测未做**（开发机未授权）；§8 的 ✅ 应明标此尾巴未闭（若「pv」另有所指请指挥纠正——G1，不臆断） |
| **recipeOk 真解本轮未独立验** | 🟡 卡写「跳②」但未点破后果 | C5a 走的是 fallback 路径验证；加密 registry 真解（recipeOk=true）这轮没证过，C5a 不能等同「registry 链健康」 |
| **§8 自身会过期（元风险）** | ❌ | 卡自封「现状权威」却已落后于 recon；需固化「每步完回填 §8 + 引 recon 行号」的纪律（§8 末尾「进度更新规矩」有写，但本轮没执行到位） |
| **D-017 与「每发行证书重生」措辞张力** | ✅ `R3 §4` 标 ❓ 待裁 | 仍悬而未决：若 keystore 永久固定，cert 轴 per-release 可降为「一次对齐」，D8 前置据此收窄——**需指挥拍板** |
| **删 8 键须同步生成脚本** | 🟡 R1/R2b §5 提示，§8 未强调 | `native_core/*.cpp` / `gen_*_cipher.py` 若按 key 遍历，删键须同步改脚本，否则三端漂移（L2/L4） |

---

## 5. 我接手后的「下一步」（证明真接住了）

> 全部走任务卡 §5「改前双审 + 用户拍是/否 + git 快照 + 改后装机回归」；改 `.md`（含校正 §8）先经用户同意（G5）。

1. **先校正账本（零代码，最高优先）**：用 R2b + `c5a_install_20260625.log` 回填 §8 —— R2b「在跑→已完成」、`接4→接6`、`缓3→缓1`、「接手下一步：C5a 已 PASS → 直接做 C7」。**经用户同意后改卡**，否则后续全在过期账上跑偏。
2. **C7 落地（按 R2b 终判，纯减优先）**：
   - 先 **删8**（JDK名/ dead /描述串，零防护价值，最低风险）；其中 `#5 contact_class(l4)` 删前补 L4 复核「有无反射按全限定名加载」。
   - 再 **接6**（`recipe()/recipeArr()` 归一 + 删内联副本）。
   - **缓1**（item_class，铁律29 回调体）不动。
   - ⚠️ 删/接均动 `registry_8071.json` → 同步核 `gen_registry_cipher.py` / `*.cpp` 是否按 key 遍历；装机回归须**带 server seed 测试卡**验 `recipeOk=true`，否则只证 fallback 不证真解。
3. **闭 C5a 尾巴**：拿到 server seed 测试卡后补 `recipeOk=true` + 密友隐藏**正向屏测**，把核对表 #8/#9 从「待验」转 L1。
4. **E9 单独立项**（看 R4b，**安全官主审**）：先建 `tools/wk_derive_ref.py` 冻结真 `wseg`（≠ seg）+ `test_config_crypto.cpp` 向量 + 回填 V0；落 `computeDeviceMaterial` 只走 `rawAndroidId`（与 A2 同盘）；灰度 3 步、向量不过即 BLOCK。动 `config_crypto/NativeBridge/AuthManager/ModuleMain`。
5. **D8 维持 ⛔**：按 R3 解冻序 —— C5~C7 落地 → `GUARD_RELEASE_ID` flavor 注入(G4) → 裁 D-017 ❓ + V3 证书源对齐(G2/G3/G5) → 回归垫(G6) → 官替/共存各装机 `recipeOk=true` L1 → 才删 fallback。
6. **顺手清账**：R4 两份合一并在 §8 全列；DESIGN.md 在 §3 补全路径。

---

## 6. 诚实边界 / 只读声明

- 本审计 **只读**，未改任何代码 / `registry_8071.json` / 任务卡 / R1~R4b / 其它文档正文；未执行 git 写动作。**本文件为唯一新建产物。**
- 服务器侧（`管理后台` / `s_rel 7255096e` / 逐台 wrap / recipeOk 真解）在 `I:\miyou-server` 或包外文档，本次按「只读交接包」框架**不深入**，标 L4。
- 「pv 口径」按上下文解为「正向验证口径」；若指挥另有定义，请纠正（G1，不臆断）。
- C5a 的 L1 取自 `c5a_install_20260625.log`（卡注「全量 buffer 为 PS UTF-16 scratch 已删」）；本审计采信该摘录文件，未复跑装机。
- 行号 / 字段数基于 2026-06-25 工作区快照；若源文件后续变动需复核（G6）。

# End（接手审计 · 只读；交接卡 82/100 可接手，但 §8 三处漂移〔R2b在跑/接4缓3/等C5a log〕须先校正；改码落地仅 C5a 一步，侦察/设计 ≈95%）
