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
| **文档** | 🟠 中高 | 7 层权威链 + SSOT + 对账文化齐全 | 258 个 md、权威面太多、~43% 旧料、真源交叉引用错号、断链/GBK |
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
| P0-2 | **护栏自己漂**：`kdf_vectors.inc:7` `kKdfTestBinding=ca421ec3`（旧 debug 证书），但 `gen_kdf_vectors.py:30` `TEST_BINDING=kc.CERT_SHA256=e3e13a49` → 向量在证书切换后没重生成；仍能过自测（旧 gen 内部自洽）= **假绿**，没覆盖现网证书 | `kdf_vectors.inc:7`、`gen_kdf_vectors.py:26-30`、`kdf_common.py:68-73` | 重跑 `python tools/gen_kdf_vectors.py`；并把 P0-1 硬闸做成「**从源重生成 + git diff 非空即 BLOCK**」（不能只跑已提交向量）| 重生成后 `kdf_vectors.inc` binding=e3e13a49；CI diff 干净 | ⬜ |

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
| P2-4 | 两份签名 SSOT：`P_CertConverge/SSOT_签名唯一` 已 DEPRECATED 且自述「与 v2 全反」，易让人读反 | `03_execute_执行任务/P_CertConverge_证书收敛/SSOT_签名唯一_统一真源.md:3-4` | 顶部已标 DEPRECATED（确认指针到 RELEASE_LINE_SSOT）/ 或归档 | 接手不会读到反口径 | 🟡 |
| P2-5 | registry 字段核账：现 5 entry/34 字段；安全官 skill 旧文「37 字段 22 接线 15 挂空」与代码不符 → 以代码为准重核哪些真被消费、挂空的接上或删 | `registry_8071.json`（5 entry/34 字段）；安全官 skill §加密 hook 名粒度 | 核账后接线或删，别留半截 | 字段消费状态清晰 | ⬜ |
| P2-6 | 文档减面：`CHATGPT_项目全景手册.md` + `refs/`（含同名 `FAILURE_LOG.md`）已删除收敛 | 2026-07-01 Glob 核实二者已不存在 | — | ✅ 2026-07-01（断链源消失；OLDREF 盘点档亦已删收敛）|

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
