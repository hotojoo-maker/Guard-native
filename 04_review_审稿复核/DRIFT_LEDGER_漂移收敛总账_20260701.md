# DRIFT_LEDGER — 漂移收敛总账（文档 / 加密 / 共存 / 官替）

> 起草：2026-07-01 · Vchat guard_native-AK53（架构师体检 → 两 AI 交叉印证 → code-true 复核）
> 状态：✅ 定稿 v1.0（四域口径用户拍板 2026-07-01 · Vchat guard_native-AK53）；§3 整改清单待逐项推进，状态列随进度回填。本页定稿时为只读产出、未碰代码。
> 定位：**「这四条线漂不漂、漂在哪、修没修」的单一真源**。四域漂移评级 + 整改清单状态只在本页定；其余文档（DESIGN / SSOT / skill / DECISION_LOG）只链接本页、不复写（G10）。
> 真相基准：G8 代码即真相。本页每条结论附 `file:line` 证据；与文档冲突以代码为准。
> 边界（别混轴，本页只引不抄）：
> - cert / registry / 发行线口径真源 = `docs/RELEASE_LINE_SSOT_发行线统一口径.md`（v2）
> - 加密 / 真锁规则真源 = `.cursor/skills/guard-security_网络安全官/SKILL.md`
> - 决策履历 = `DECISION_LOG.md`
> - 文档旧料盘点：原 `OLDREF_AUDIT_A/B` + `精简方案_提案` + `MN1_OLDREF清单` 已删收敛（2026-07-01），有效结论并入本页 §3
> 维护铁律：① 改任一线 → 回填本页「状态」列（G9）；② 机器闸（§4）落地后，本页「状态」应由闸结果驱动、不再靠人工标。

---

## 0. 一句话

**容易漂移 = 是（结构性高漂移）；方便管理 = 部分。** 项目已搭一套很硬的防漂护栏（G8/G9/G10 + 单一源自动生成 + KDF 测试向量 + 对账文化），靠它撑住没塌；但护栏**没闭合**，且已出现**真源对真源打架**「连护栏自己都漂了一格」（`kdf_vectors.inc` 仍绑旧证书）。根治 = 文档层取齐口径 + 机器层上硬闸（把能机器检的结论从「靠人记」变「靠 build 拦」）。

---

## 1. 四域漂移评级（统一口径 · 已拍板 2026-07-01）

> 两个 AI 评级的唯一实质分歧在本表；用户 2026-07-01 拍板取齐为下表单一口径，取齐理由见备注。

| 域 | 漂移风险 | 现状 | 主要缺口（详见 §3） |
|---|:--:|---|---|
| **加密** | 🔴 高 | json→gen→SO/fallback 管道通、fail-closed 已落、KDF 三端机制已建 | 护栏自己漂（`kdf_vectors.inc` 绑旧 cert）+ 三端对账无发版硬闸 + ConvFilter/SearchFilter 回调内联 + PushFilter 零接入 |
| **文档** | 🟠 中高 | 7 层权威链 + SSOT + 对账文化齐全 | 227 个 md、权威面太多、旧料偏多、真源交叉引用错号、断链/GBK |
| **共存** | 🟡 中 | v2 已收成「一套配方」（cert/S_rel 共用官替） | cert 轴 2 周 3 变已拍死、剩 cleanup（死 inc / 过期注释 / 1 台残留真机） |
| **官替** | 🟡 中 | 锚定线、相对稳 | 与共存共担全部「多端逐字节一致」要求 |

**取齐备注**：
- 加密定「高」（高于「中高」）：复核确认连防漂工具 `kdf_vectors.inc` 自己都停在旧证书 → 属「看不见的漂移」最危险类（F-31 静默散沙）。
- 共存定「中」（低于「曾经的高」）：cert 轴 v2（D-026）已基本拍死，剩物理 cleanup，不再是活跃决策反复。
- 评级是判断权重差（「折腾过几次」vs「当前还会不会静默翻车」），非事实冲突；两份评估方向一致、可互相印证。

---

## 2. 根因（一处事实，多端逐字节拷贝）

四域天生难管，不是文档写得乱，而是**同一个事实必须在很多「端」逐字节一致**；任一端没跟上 → **F-31 静默散沙**（正版机不崩、不报错，密友就是不藏了 = 看不见的漂移）：

| 一个事实 | 必须同时对上的端 |
|---|---|
| 证书前缀 `e3e13a49` | `build.gradle`（per-flavor EXPECTED）· `tools/kdf_common.py:68-73` · `registry_cipher.inc`（绑定）· 服务器 `release_lines.cert_prefix` · 运行时 `ModuleMain.bindSigningCert` · 5~7 份文档 |
| `S_rel` / `W` 指纹 | `release/secrets/<id>.json` · SO（`config_crypto.cpp`）· 服务器 `release_lines` · 发版文档 |
| registry 混淆名（`kc5.y` …）| `registry_8071.json` · `RegistryFallback`（debug，生成）· Filter 回调体（**仍内联**）|
| KDF 段常量 / 折入 cert | `tools/kdf_common.py`（Python）· `config_crypto.cpp`（C++ 手抄镜像）· `kdf_vectors.inc`（生成的对账向量）|
| `release_id` / 包名 | `build.gradle` flavor · `AppConfig.java:52` · 服务器 `release_lines` · 文档口径 |

---

## 3. 整改清单（P0/P1/P2 · code-true）

> 状态：⬜ 未做 · 🟡 进行中 · ✅ 已闭合。每条带「证据 / 落点 / 验收 / 归属」。碰 registry/KDF/发版门控的项须过授权检查官 + 安全官改前/改后审。

### 🔴 P0 — 三端对账无发版硬闸（堵 F-31 静默翻车）

| # | 问题 | 证据（code-true） | 落点 | 验收 | 状态 |
|---|------|------|------|------|:--:|
| P0-1 | `kdf_self_test()` 仅 `#ifdef GUARD_DEV_SELFTEST`（release no-op），`run_native_tests.ps1` 是独立脚本、没东西强制发版前跑 → 改 SO 一个 seg 字节、忘跑，release 照样出包、上机才散沙 | `config_crypto.cpp:615-687`、`CMakeLists.txt:48-50`、`run_native_tests.ps1`（未被 gradle 调用）| `build.gradle` preBuild 依赖 或 CI + 发版 skill 收尾 BLOCK 项 | 故意改 1 个 seg 字节 → 出包被 BLOCK | ⬜ |
| P0-2 | **护栏自己漂**：`kdf_vectors.inc:7` `kKdfTestBinding=ca421ec3`（旧 debug 证书），但 `gen_kdf_vectors.py:30` `TEST_BINDING=kc.CERT_SHA256=e3e13a49` → 向量在证书切换后没重生成；仍能过自测（旧 gen 内部自洽）= **假绿**，没覆盖现网证书 | `kdf_vectors.inc:7`、`gen_kdf_vectors.py:26-30`、`kdf_common.py:68-73` | 重跑 `python tools/gen_kdf_vectors.py`；并把 P0-1 硬闸做成「**从源重生成 + git diff 非空即 BLOCK**」（不能只跑已提交向量）| 重生成后 `kdf_vectors.inc` binding=e3e13a49；CI diff 干净 | 🟡 2026-07-03（P85：已重跑 gen→binding=e3e13a49 + `run_native_tests.ps1` ALL=PASS〔kdf_self_test=PASS〕；**发版硬闸 P0-1/§4B 仍 ⬜**、改动未提交，见 §9.6）|

> 呼应安全官 skill「三端钥匙派生镜像对账」铁律落地要求②（KDF 测试向量自动对账）。P0-2 是「连防漂工具都漂了」的现场实证，最该顺手先修。

### 🟠 P1 — 消明文 fallback 债 / registry 收口归一

| # | 问题 | 证据 | 落点 | 验收 | 状态 |
|---|------|------|------|------|:--:|
| P1-1 | MomentsFilter 内联混淆名收口（`registry-unify-v1` Devin 分支）是否已合 + 内联清零（json + RegistryFallback 侧 item_notify/wq.c1/wq.y0/ii5.b 已备齐）| `registry_8071.json:24-28`、`.devin/tasks/registry-unify-v1.md` | MomentsFilter.java | 无手写短混淆字面量；编译 0 error | 🟡 |
| P1-2 | ConvFilter 回调体仍内联 `"kc5.y"`（安装期已走 registry，回调体没迁）| `ConvFilter.java:378,434,599,1097`；代码 `:56-64` 自述「刻意保留的债·铁律13-22/29」| 决策：按「改已验证 hook」高规格评估是否迁；不迁则单一处登记「刻意保留」| 选定后状态明确（迁/登记），不留模糊 | 🟡 |
| P1-3 | SearchFilter 半迁：gateway 走 registry、item 死锚 `z15.ef6`/`fz2.e` 内联；且 `fz2.e` 在 classmap 标 `deprecated 证伪`，仍被内联引 | `SearchFilter.java:64-65,84-92`；`docs/classmap/v8071.yaml:207-212` | 锚点真伪先对账（ss4.p 教训）→ 收 registry 或代码标「刻意保留」单一登记 | 锚点今天真活跃 L1 核实；状态明确 | ⬜ |
| P1-4 | PushFilter = registry 最大洼地（零接入）：内联 `com.tencent.mm.booter.notification.x`（classmap 标证伪）+ 短混淆 `l`/`d`/`a`/`h` | `PushFilter.java:322,467,541,566`；`docs/classmap/v8071.yaml:264-270` | 版本易碎短名收新 entry `push.notify`；全包名稳定类留 final | 迁前对账每锚点活跃；recipeOk + 通知/来电链 L1 不挂 | ⬜ |

### 🟡 P2 — 清漂移注释 / 死文件（防接手 AI 被带偏）

| # | 问题 | 证据 | 落点 | 验收 | 状态 |
|---|------|------|------|------|:--:|
| P2-1 | `RELEASE_LINE_SSOT` 头部 + §0.5 把 cert 决策标 `D-018/D-019`，实际 `DECISION_LOG` 里 D-018=A2、D-019=RiskState，真正 cert-converge=**D-026** → 真源交叉引用错号 | `docs/RELEASE_LINE_SSOT_发行线统一口径.md:6,21-23`；`DECISION_LOG.md` D-018/D-019/D-026 | 改引用为 D-026 | SSOT 决策号指向 D-026 | ✅ 2026-07-01（头部/§0.5/§4.3 D-019→D-026 已改；残留 §22 cert-sync-v1 仍标 D-018 = 小残留待理） |
| P2-2 | `kdf_common.py:64-67` 注释仍写「coexist 用独立 keystore cert=8f47a47a…per-flavor registry」（v1 口径），v2（D-026）已合一套配方、coexist 共用 e3e13a49 | `kdf_common.py:64-67` | 注释改 v2 口径 | 注释不再提 coexist 单生 | ✅ 2026-07-01（已改 v2 口径） |
| P2-3 | 死文件物理仍在：`registry_cipher_coexist.inc` + `bootstrap_cipher_coexist.inc`（registry_loader 已不引用、CMake 已退役 GUARD_REGISTRY_COEXIST）| `native_core/src/*_coexist.inc`；`registry_loader.cpp:31`；`CMakeLists.txt:40-42` | 移 `native_core/src/_archived/` 或头加 DEPRECATED | 运行不引用、物理归档 | ⬜ |
| P2-4 | 两份签名 SSOT：`P_CertConverge/SSOT_签名唯一` 曾 DEPRECATED 且自述「与 v2 全反」，易让人读反 | 原文件 `03_execute_执行任务/P_CertConverge_证书收敛/SSOT_签名唯一_统一真源.md` **现已不存在**（Glob + 磁盘核实 2026-07-02 · 整个 `P_CertConverge_证书收敛/` 目录已删除收敛）| 目标文件随目录删除 → 无反口径可读；活指针 `RELEASE_LINE_SSOT:117` / `TASK_BOARD:88` / `CURRENT_PLAN:41` 均已正确写「已删除/SUPERSEDED」，`DECISION_LOG:232` 为历史叙事保留 | 接手不会读到反口径 | ✅ 2026-07-02（本项原为指向已删文件的幽灵 TODO，核实后标闭合 · Vchat FFN98）|
| P2-5 | registry 字段核账：现 5 entry/34 字段；安全官 skill 旧文「37 字段 22 接线 15 挂空」与代码不符 → 以代码为准重核哪些真被消费、挂空的接上或删 | `registry_8071.json`（5 entry/34 字段）；安全官 skill §加密 hook 名粒度 | 核账后接线或删，别留半截 | 字段消费状态清晰 | 🟡 2026-07-03（P85：skill 字段数 37→34/5entry 双镜像已改，见 §9.6；接线/挂空 recount + 挂空接/删仍 ⬜）|
| P2-6 | 文档减面：`CHATGPT_项目全景手册.md` + `refs/`（含同名 `FAILURE_LOG.md`）已删除收敛 | 2026-07-01 Glob 核实二者已不存在 | — | ✅ 2026-07-01（断链源消失；OLDREF 盘点档亦已删收敛）|

---

### 🟡 P2（补）— 文档债扫尾（2026-07-01 并入自 DOC_DEBT_MASTER_清单，该临时工单已删 · Vchat ML96）

> DOC_DEBT_MASTER 的 P0（A2 live / E3 已装机 / F-42→F-43 / P_CertConverge·CONFLICTS 断链 / CLAUDE·guard-review）已闭合（commit `e724ee5`+`d2a38bf`，三 AI 交叉核）；其仍开的唯一项并入本账，避免再造竞争清单（G10）。

| # | 项 | 证据（code-true） | 性质 | 状态 |
|---|------|------|------|:--:|
| P2-7 | 技能数矛盾（旧态：CLAUDE「10」漏防封官 / PROJECT_INDEX「8」/ 误记 11 项目 skill）| `.cursor/skills` 实测 16 目录（10 guard + 6 通用）；`CLAUDE.md` §十 / `PROJECT_INDEX.md:132` | 文档对齐 | ✅ 2026-07-02（复核纠偏：功能 skill=10 guard + 6 通用，CLAUDE §零/§十=10、PROJECT_INDEX:132=10、实测=10 三处一致；「8/11/漏防封官」系删 auth-gate 前旧态。空目录 `auth-gate_授权门控` 已删）|
| P2-8 | `_CORE_现状真源/`（发版/授权/防封 3 页最新真源）没进 PROJECT_INDEX 权威链 / CLAUDE 入口 | grep `_CORE` 全仓仅自引；PROJECT_INDEX §负一 + CLAUDE 接手四步均无 | 权威链层0（已拍：_CORE=先读现状层）| ✅ 2026-07-02（CLAUDE §一「接手顺序」+ PROJECT_INDEX 权威链层0 + 核心文档表 + AGENTS / docs README / dispatch skill 五处已接 _CORE；.claude 镜像已同步）|
| P2-9 | `build.gradle:90` 过时注释「共存 8f47a47a」≠ `:150` 实际 `e3e13a49`（`:116/:147` 已注退役）| build.gradle:90/116/147/150 | 【改代码·注释】 | ✅ 2026-07-02（L90 注释改 v2：两线共用 e3e13a49、8f47a47a 已退役）|
| P2-10 | `nativeReloadState()` 文档有代码无（幽灵 API）| grep 仅 `.md`（CLAUDE + native_core/*.md），无 `.java/.cpp` | 【改代码 或 文档下线该 API】 | ✅ 2026-07-02（下线 4 处假调用：CLAUDE 铁律27 / RULES ×2 / ARCHITECTURE；ROADMAP TODO 保留；code grep 0 命中已复核）|
| P2-11 | 冷启动写死测试 wxid `wxid_lzd2va16jd1622` | `ModuleMain.java:429` + `native_core/src/wxid_matcher.cpp:28` | 【改代码·关 debug 档】 | ⬜ |
| P2-12 | 体量：5 god file（`SettingsEntry` 3050…）+ 强制前置阅读链 ~80KB + 根目录 ~1.5GB / 全树 ~5GB dev 产物（APK/日志，gitignored 不入库、碍导航）| 新 AI 盘点（数字已复算）| 【拆分/清理·另立任务】 | ⬜ |

> **镜像口径更正**（DOC_DEBT + 首个调查 AI 均认错对象）：非「`.claude` 缺 guard-server」（两镜像 16=16 SKILL.md 目录均含 guard-server）；真实差异 = `.cursor` 多一个 `guard-git_保姆/DEV_SECRETS.md`（已 gitignore + `sync_skills.ps1 /XF` 故意排除，**无泄漏**，非漂移）。

---

## 4. 机器层「统一」= 终极防漂（两道硬闸 · 待排期）

> 文档层取齐口径靠纪律（会再漂）；机器层硬闸靠 build（不会漂）。这两道是把「结论」从「靠人记」变「靠机器拦」的根。

- **硬闸 A（cert 四端）**：`tools/verify_cert_chain.ps1`（SSOT §6 已设计、未落地）—— 实签 / `BuildConfig.GUARD_EXPECTED_CERT` / `registry_cipher.inc` 绑定 cert / 服务器 cert_prefix 四端逐字节比，不齐 BLOCK `assemble*`。
- **硬闸 B（KDF 三端）**：`gen_kdf_vectors.py` 从 `kdf_common.py` 重生成 → `git diff` 非空即 BLOCK；`run_native_tests.ps1` 接进发版门（P0-1/P0-2）。

---

## 5. 明确不动（不是债，别返工）

- AES-GCM SO 实现 + `decrypt_config_self_test` + `envelope_expiry_self_test`：已有完整测试，不重写（安全官 skill 接手快照铁律）。
- registry 粒度保持粗（4 大动脉 + 4 pack），不为「全加密」拆碎。
- `g_wk` 全局静态 W 明文回退（`config_crypto.cpp:374-375`）：已知 Batch3 债，归 `P_RB1` 牙③ 线，**不在本「归一」清单**（是另一条加固线，单独排期）。
- ConvFilter 回调体内联 `kc5.y` 若评估为「保留」：属铁律 13-22/29 的安全取舍，登记即可，非必迁。

---

## 6. 两 AI 交叉印证记录

| 项 | 架构师 L2 | 本轮 code-true 复核 | 结论 |
|---|---|---|---|
| 总判（结构性高漂 + 护栏未闭合）| ✅ | ✅ | 一致 |
| 根因（一处事实多端拷贝 → F-31 静默）| ✅ | ✅ | 一致 |
| `kdf_vectors.inc` 绑旧证书 ca421ec3 | ✅ 提出 | ✅ `kdf_vectors.inc:7` 实锤 | **属实**（P0-2）|
| ConvFilter 回调体硬编码 `kc5.y` | ✅ 提出 | ✅ `:378/434/599/1097` 实锤 | **属实**（P1-2）；补：`:56-64` 自述刻意保留债 |
| PushFilter 零接入 / SearchFilter 死锚 / W 全局明文 | ✅ | ✅ | 一致 |
| cert 决策号 `D-017→D-018→D-019` | ❌ 照抄 SSOT 错引 | ✅ 实为 D-026（D-018=A2/D-019=RiskState）| 本页以 **D-026** 为准（反向印证 P2-1 文档错引）|
| 四域评级（共存=高/加密=中高 vs 加密=高/共存=中）| 共存=高 | 加密=高 | §1 取齐：加密=高、共存=中（判断权重差，非事实冲突）|

---

## 7. 维护

- 本页 = 四域漂移状态 + 整改清单单一真源。任一线改动后回填「状态」列（G9）；评级或清单变更先改本页再动别处（G10）。
- §4 硬闸落地后，§3 各「状态」改由闸结果 / CI 驱动，减少人工标注漂移。

---

## 8. 交接（老的 AI → 收尾 AI/Devin · 2026-07-01）

> 我 = 老的 AI（架构师/评审线 · Cursor · Vchat guard_native-AK53）。做了：四域漂移体检 + 两 AI 交叉印证 + 本总账定稿（口径用户已拍）。**分工：你执行、我只读扫描复核 + 我管本总账 SSOT 落档。** 不与你并行改同一文件。

**⚠️ 两个路由前提（先看）**
1. 本总账在主仓 `04_review_审稿复核/` **未提交**，你从 `origin/main` 起 worktree **看不到**它 → 可执行项见下（已贴全，不依赖你能读到本文件）。
2. 你的禁入区 = `signing/ / net/ / *.inc`。下面 **P0 全部命中禁入区（`*.inc` + native build + 发版门）→ 留 Cursor 侧做，不派你**。

**派给你（Devin-safe · worktree/PR · 执行前以「符号+文件」现查行号，别照死行号——你刚把 A2 闸拆到 `AntiBanGate.java`、debug 门控归一 `AppConfig`，本表行号已被带旧）**
- **P2-1**：`docs/RELEASE_LINE_SSOT_发行线统一口径.md` cert 决策错引 `D-018/D-019` → `D-026`（:6 / :22 / :23 / :119 全核一遍；实际 D-018=A2、D-019=RiskState）。
- **P2-2**：`tools/kdf_common.py:66-67` 过期 coexist 注释（仍写独立 keystore 8f47a47a / per-flavor）→ 改 v2「一套配方·共用 e3e13a49」口径。
- **P1-1/P1-3/P1-4**（`src/main/java`，可做但**碰 registry 锚点须先对账活跃 + 走授权检查官/安全官改前审**）：Moments 内联收尾 / Search 死锚（`fz2.e` classmap 标证伪要先核）/ PushFilter 收 `push.notify`（`booter.notification.x` 证伪要先核）。

**留 Cursor 侧（你别碰）**
- **P0-1 / P0-2**：重生成 `kdf_vectors.inc`（活漂在旧 cert ca421ec3）+ 三端对账接发版硬闸（`build.gradle`/CMake/`run_native_tests.ps1`）。命中你禁入 `*.inc` + 发版门。
- **P1-2**：ConvFilter 回调体内联 `kc5.y` = 铁律 13-22/29 不碰已验证 hook 回调体，需高规格评估，Cursor 侧定。

**回填**：仿你们 SSOT 分工——你 PR body 列「本总账 §3 哪条、改了哪几行」，我（Cursor）回填本表「状态」列，别新开总结文档。

---

## 9. 本轮实战回填（2026-07-01 · Vchat AK53）

> cert 漂移轴（§1 🟡/🟠）当晚**现场咬人**：共存 release 装机后 `certBind=a40da80a`（克隆宿主原始章）→ A2 cert mismatch 散沙；初稿 result.md 误标共存 ✅（疑把官替日志当共存）。这正是「一处事实多端拷贝 + 无 build 前硬闸」的活案例。

| 项 | 结果 |
|---|---|
| 共存 cert bug 修复 | ✅ 克隆宿主先 official jks 重签 e3e13a49 再 LSPatch；装机 L1 全绿（`certBind=e3e13a49`+`A2 installed`+`recipeOk=true`，pid 23735）。出货包 `build/lspatch_out_coexist_fix/mn_e3host_8071-439-lspatched.apk` |
| 证据落盘 | ✅ `P_HotUpdateFreeze/logs/coexist_verify_20260701.txt`（修复前后双证）；`result.md` §1/§4/§5 已修正 |
| 文档收编（收尾 AI commit `ac2a781`） | ✅ F-43 改写根因 + `DECISION_LOG` D-030（克隆宿主必先 apksigner 重签）+ RELEASE_RULES 4→5 步（step0=重签宿主）+ guard-release skill 坑7 + 3 镜像同步 |
| P2-1（SSOT D 号错引） | ✅ D-019→D-026（残留 §22 小项） |

### 9.1 仍开口（明天/后续）
1. **官替出货包：实测候选也坏 → 已修（2026-07-01 AK53）**。`build/lspatch_out_rel/wx_host`=debug ca421ec3（不可出货）；`02_tools_工具/lspatch_out/host_official_clean...lspatched.apk` 的内嵌 `origin.apk`=**0fe4ff85** → 同 F-43 会散沙（不可出货）。已用同修法做正确官替包 **`build/lspatch_out_official_fix/official_e3host_8071-439-lspatched.apk`**（文件+origin 双 e3e13a49，L2 预判过）。**装机 L1（冷启 certBind/recipeOk）待补**——避免打断现跑官替，未实装。
2. ✅ **`tools/lspatch_pack.ps1` 加 `-RebindHost`（2026-07-01 已落 + 实测·机器闸#1 上线）**：release 模式自动重签克隆宿主为 e3e13a49 再 LSPatch，不再靠人记 step0。实测 `-Flavor coexist -BuildType release` → rebind 宿主 e3e13a49 + 输出 origin.apk=e3e13a49。坑：局部变量别叫 `$rebindHost`（PS 大小写不敏感、撞 `$RebindHost` switch），已用 `$resignedHost`。
3. **P0 `verify_cert_chain` 四端硬闸**（§4 硬闸 A）：老 AI 同意起位——cert-bleed-through 这类装前比对就该 BLOCK。
4. **P2-2**：`kdf_common.py:66-67` 过期 coexist 注释 → ✅ 已改 v2 口径（2026-07-01）。

### 9.2 已排期：cert 教训 G10 大收敛（d · 待下一轮 · 跟收尾 AI 分工）

> cert-bleed-through / cert-converge 教训现散在 **7 处**：`FAILURE_LOG` F-43 · `DECISION_LOG` D-027/D-030 · `RELEASE_RULES`（5 步 + 三部分真相）· `guard-release` skill 坑7（×3 镜像）· `result.md` · 本 ledger §9。各处虽已对齐，但「一处事实多端展开」本身就是下次漂移源（G10）。
- **目标**：选 1 处权威（建议 `RELEASE_RULES`「F-43/D-030 教训 + 5 步」段为**发版操作**权威；机制根因留 `DECISION_LOG` D-027/D-030），其余压成「一句话 + 链接」。
- **范围**：7 处 + 3 skill 镜像。**高风险**（动多文件、易撞收尾 AI）→ **单开一轮、先跟收尾 AI 分工**（主仓 SSOT 谁落档按 `.devin/CONNECT.md §4`）。
- **验收**：同一 cert 教训只在权威处展开；别处 grep 只剩链接；无矛盾。
- **本轮已顺带减**：a（D-030 官替 L3→已 L2 坐实）✅、b（`RELEASE_LINE_SSOT` §22 D-018 错号→并入 D-026）✅。

### 9.3 07-02 live 双版实证 + 规划档减法（Vchat M88）

- 设备 `609b4b18` 现装官替（pid 31345）+ 共存（pid 772）冷启 L1：`certBind=e3e13a49` / `role=1 MAIN` / `BATCH1_VERIFY PASS` / `recipeOk=true` / `risk=正常`。证据落盘 `03_execute_执行任务/P_HotUpdateFreeze_官方热更新冻结/logs/live_verify_20260702_{ncl,full}.txt`（gitignored），关键行摘入 tracked `result.md §3.1`。
- 因此减法：`CURRENT_PLAN §P2` + `TASK_BOARD` P_NC1 行「待装机 / RiskState 真降级未做」陈状态删除 → 指向 `_CORE/发版_当前真源` + `PROTECTION_MAP §10.6` + 本账为单一真源（G10，本行不复述）。`result.md §4` 官替 L1「待补」→「✅ 07-02 现装包实证」+ 诚实边界（现装包 vs 出货候选文件级同一性未比对）。
- 发现（补记 P2 类）：`coexist_verify_20260701.txt` 实在盘上（前轮 M83「缺失/glob 零命中」= Glob 跳 gitignored 文件的假阴性；日志一直在，是搜索方式滤掉了）。
- **RiskState 口径 6 文件一致改**（`授权页§④` / `PROTECTION_MAP §10.6` 三处 / `PROJECT_INDEX` / `docs/RELEASE_RECIPE契约` / `guard-security` skill ×2 镜像）：flat「RiskState 真降级未做」→「杂项 5 功能 `isTamperDegraded()` 散沙**已落码**（5 caller：CallGuard/PushFilter/AntiRecall/FakeLocation/FakeBalance）；密友四链走 crypto registry 散沙不走本轴；真未做窄口径 = `EncryptedConfigLoader` 直接 risk-gating + 负向 L1」，单一真源指向 `core/RiskState.java` 类注释。安全官 + 授权检查官改前审 = PASS（纯口径精确化，不动代码/门/gate，方向是「更精确不过度宣称真锁」）。快照 `snap/riskstate-converge/20260702-1106`。**（2026-07-03 P91 后续再收敛：forward 文档去裸数字「5」→「数以 `isTamperDegraded()` 调用点为准」；当时 5 caller = CallGuard/PushFilter/AntiRecall/FakeLocation/FakeBalance 留作历史证据，真源仍 `core/RiskState.java`。）**
- **接手序去重（G10 · 收 P2-8 尾）**：M45 已定 `CLAUDE §一` 为唯一接手序；本轮补收 `docs/README`（删 0-6 竞争阅读序 → 只留 8071 车道特有 4 文件 + 指 CLAUDE §一）+ `AGENTS.md`（删重抄五步 → 指 CLAUDE §一）。`PROJECT_INDEX §负一「文档权威链」`= 冲突仲裁轴（非接手序）、已标「冲突时按此顺序」，保留不动。真源：接手序单一 = `CLAUDE §一`。
- **Catfish 竞品隔离结案（承 2026-06-29 归柜）**：`refs/` → `docs/isolation/` 迁移遗留的「待改链接清单」热路径组（`CLAUDE` / `PROJECT_INDEX`〔含目录树〕/ `TOOLS_INDEX` / `FAILURE_LOG` / `docs/HOOK_MAP_8071` / `docs/DOC_AUDIT` / `00_start/PROMPT_TEMPLATES` / `guard-execute-one skill`）已全部改向；全仓 grep 复核热路径零残留 `refs/`（仅 `07_archive_` + `docs/archive/` 冻结冷库按迁移档 §E 放行；`INDEX_COMPETITOR:29` 系改名历史注、保留）。本轮删 `PROJECT_INDEX` 目录树过期 `refs/(现有,不动)` 行 + 加迁移指针；`_MIGRATION_竞品归柜_20260629.md §②` 标结案。
- **看板 A 步（`TASK_BOARD §五` 折叠）**：§五「P 任务历史」19 项 ✅ + 未完项明细全部收进 `<details>`（审计轨迹不删——日期/证据路径/commit hash 全留、只折叠），顶部留一行 🟡/⬜ 未完项摘要 + 指 `PROJECT_INDEX §零`。**采「折叠」非「删行→指针」**：§零 是功能清单、不含 P15/cert-sync/arch-audit/debug-gate 等 infra 项，删行会丢这些 → 折叠保全（比两 AI 建议的「压成一行」更稳，不丢 infra 审计）。看板热路径变短、明细可展开。

### 9.4 残留漂移一批修（Vchat N15 · 2026-07-02）

> 起因：用户问「文档是否还漂移」。子代理全仓扫 + 我 code-true 眼核。核心热路径事实（F-43 / 两线共用 `e3e13a49` / pv v1.6 / CLAUDE·PROJECT_INDEX 10+6 skill）已齐；剩小漂（陈旧存货 / 误号 / 断链），本轮一次性修（纯文本、零代码、零逻辑）。改前用户拍「全修」。

| # | 漂移 | 落点 | 修法 |
|---|------|------|------|
| 1 | 活机制文档仍写共存 cert `8f47a47a` | `P_AntiBanGate/DESIGN.md:344` | → 官替+共存共用 `e3e13a49`（D-026），旧 `8f47a47a` 标退役 |
| 2 | FAILURE_LOG 天花板停在 F-42 | `docs/HOOK_MAP_8071_AUTHORITATIVE.md:441` | `F-01~F-42` → `F-01~F-43`（补 F-43 行） |
| 3 | cert-converge 误引 D-019（实 D-026） | `TASK_BOARD.md:88` · `CURRENT_PLAN.md:41` | D-019 → D-026 |
| 4 | 本账自漂：`258 个 md`、`17 目录/11 项目 skill/17=17` | 本账 §1 / §3 P2-7 / P2-7 镜像注 | 258→227；17/11→实测 16 目录（10 guard+6 通用）/16=16 |
| 5 | 死引用（幽灵路径/角色） | `PROJECT_INDEX:138` CONFLICTS · `08_release/README:24,27` + `RISK_REGISTER:3` + `DECISION_LOG:89` guard-risk-check · `DECISION_LOG:245` auth-gate skill | CONFLICTS→漂移账(DRIFT_LEDGER)；guard-risk-check→guard-review质检门控（SO入库那处→guard-security）；auth-gate→guard-auth-review（已并入） |
| 6 | 同文件版本自相矛盾 v1.1 vs v1.6 | `guard-auth-review` skill :171/:640（+ `.claude` 镜像 sync） | v1.1 → v1.6（对齐同文件 :488） |
| 7 | 交接卡陈旧数 | `01_dispatch/架构师交接卡.md:32/:38` | 169 md→227；至 F-41→F-43 |
| 8 | 已被 D-026 取代的任务卡无提示 | `.devin/tasks/cert-sync-v1.md` 头部 | 加 SUPERSEDED 横幅指 D-026 / RELEASE_LINE_SSOT v2（历史表格留档不改） |

- **未动**（避免改历史/超范围）：`.devin/cert-sync-v1` 正文两把印章表格（仅加头部横幅）；`DECISION_LOG` 各决策叙事本体；P2-12 本地磁盘 GB 数（那是 gitignored 本地 dev 产物、非 git tracked，git tracked 工作树已 2.56MB 见 E 步）；`kdf_vectors.inc:7` 仍绑旧 cert `ca421ec3`（= §3 P0-2 代码级漂移，禁入区 `*.inc`，留 Cursor 侧接发版硬闸，本轮不碰）。
- **镜像**：改 `.cursor/skills/guard-auth-review` 后跑 `sync_skills.ps1`（synced 16 SKILL.md，二次印证 skill 目录数=16）。

### 9.5 签名铁律去重收口（Vchat N15 起草改动 → N51 提交 · 2026-07-02）

> 起因：G 步 = 签名铁律块在 6 个 skill 逐字拄写（× `.claude` 镜像 = 12 份）违反 G10。N15 已把改动落到工作树（15 文件、+44 -82），但掉线未提交。N51 接手 code-true 核 diff + 用户拍「按 N15 方案提交」，本轮只做提交、不再动内容。

| # | 收敛 | 落点 | 修法 |
|---|------|------|------|
| 1 | 新增签名铁律单一权威块 | `CLAUDE.md §十三.五` (+14 行) | D-026 版 cert/规则完整落此；skill 只留一行 cert 值 + 指针 |
| 2 | 5 个 skill 签名铁律整块 → 单行指针 | `execute-one` · `auth-review` · `review` · `terminal` · `git 保姆` × `.cursor`/`.claude` 镜像 = **10 份** | 整块 7 行 → 2 行（`## 🔐 签名铁律` + `> release cert e3e13a49… · 完整规则见 CLAUDE §十三.五`） |
| 3 | `dispatch` skill 签名铁律块 → 单行指针 + 保留 1 行派发提醒 | `.cursor` + `.claude` 镜像 = **2 份** | 整块 7 行 → 3 行（顶部 2 行标准 + 1 行"派发发布/共存/签名/打包任务前先让执行窗口读 RELEASE_RULES"= 调度职责，非签名值） |
| 4 | `security` skill 真漂修正（顺手一锹） | `.cursor` + `.claude` 镜像 = 2 份 (-1+1) | "官替共存各自固定 keystore" → "共用同一把 release jks（D-026）" |
| 5 | `dispatch` skill §外围签名一致段简化 | `dispatch` 底部 | 展开的 cert 值 → 指回 CLAUDE §十三.五 |

- **净收敛**：15 文件 / +44 -82 = **净减 38 行**；cert 值从 6 skill×2镜像 = 12 份 → **收到 CLAUDE 1 处 + skill 单行**。
- **未动**：`guard-release` / `guard-server` / `guard-security` 主体（角色专用的详细签名段、非同一块拄写模板，不撞 G10）；`guard-antiban` / `guard-execute-one` 等 skill 内部散在的签名提醒（若非 `## 🔐 签名铁律` 标题块，本轮不动）。
- **用户拍板**：G 方案 3 选 1（A 挪禁忌表 / B 单行下多留 / C 完全删）先拍 C；发现 N15 已改到工作树后重拍「按 N15 方案（保 1 行派发提醒）提交」= 兼采 B/C，避免重改。

### 9.6 P0-2 修复 + P2-5 字段数 / F-42 / build.gradle:90 陈述漂修（2026-07-03 · P85）

> 起因：新会话按聊天记录续「验上机 registry + 修漂移」。上机 registry 已 live 复证（`certBind=e3e13a49` / `recipeOk=true`，设备 609b4b18 冷启 07-03 15:55）。顺着修 P0-2 + 三处文档漂，用户拍「C 全改」。

- **P0-2（文件级闭合，系统闸仍开）**：`python tools/gen_kdf_vectors.py` 重生成 `kdf_vectors.inc`，binding `ca421ec3`→`e3e13a49`（4 行变：binding + 3 个 cert 派生向量；`kKdfVecWrap` 不变=范围正确）。`run_native_tests.ps1`（无 PC 编译器→NDK Mode2，临时 elf 推 `/data/local/tmp` 跑完自删、非 app 装机）ALL=PASS：`kdf_self_test=PASS`（C++↔Python 新证书对上）、`decrypt/envelope=PASS`、`registry_self_test=SKIP`（server-lock 无种子=预期 fail-closed）。**仍 ⬜**：发版硬闸（P0-1/§4B「从源重生成+diff 非空即 BLOCK」）未接；本改动未提交。
- **严重度纠偏（承前轮口径）**：P0-2 = Debug-only 自测（`#ifdef GUARD_DEV_SELFTEST`、不进 release SO、不上机）→ **非运行时漏洞、非「现网散沙」**；是「生成物没跟源头重跑」的陈旧 + 信号。前述「假绿」措辞偏重，以本条为准。
- **P2-5 → 收指针（减法）**：`registry_8071.json` code-true = 5 entry / 34 字段（conv7+moments13+contact8+search5+a2.sig1）。安全官 skill `.cursor`+`.claude` 双镜像（213/231 行）「37/22/15 死数」→ **收成「字段/接线以 `registry_8071.json` + `getRecipe*` 为准」（不写死数，永不再漂）**；双镜像 SHA256 一致。历史 recon 档（`P_LeanCloseout`）留档不动。
- **RiskState 消费者数 → 收指针（减法·已咬人的矛盾）**：`isTamperDegraded()` code-true = 5 调用点（AntiRecall/PushFilter/FakeLocation/CallGuard/**FakeBalance**）。防封§⑤「消费者 4」(漏 FakeBalance) vs 授权§④「5 caller」= 两现状页打架 → 两页 + `core/RiskState.java:20` 注释（也漏 FakeBalance）全收成「消费者以 `isTamperDegraded()` 调用点为准（grep 即得）」，删死数。
- **#2 死行号 / #3「别再信」段 → dead-check 后不动**：行号多与符号成对 + `PROTECTION_MAP §1` 本标「历史快照」（群删丢导航=净负）；⑧ 段目标已 caveat / D-020 取代 / 前瞻有效（硬删丢有用警告）。高价值减法（静默打架死数）已在 P2-5 + RiskState 收完。
- **小2**：`docs/isolation/INDEX_COMPETITOR.md:29` Guard「F-01~F-42」→「F-01~F-43」。
- **可选3**：`_CORE/发版_当前真源.md:79`「build.gradle:90 注释过时」提醒本身已过时（:90 早修为 v2 口径）→ 改述「共存独立 8f47a47a = 旧说法作废，code-true build.gradle:148/150 = e3e13a49」。
- **未提交**：本会话改动（`kdf_vectors.inc` + 安全官 skill ×2 镜像 + `_CORE` 授权/发版/防封 3 页 + `INDEX_COMPETITOR` + 本 ledger + `core/RiskState.java` 注释）全在工作区，未 git commit（等用户发话）；P0-2 native 重编出货 SO 未做。
