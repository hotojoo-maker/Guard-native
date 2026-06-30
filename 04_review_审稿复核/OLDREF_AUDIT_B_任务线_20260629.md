# OLDREF_AUDIT_B — 旧资料盘点（代理B·任务线）

> 角色：Guard Native「旧资料盘点员·任务线」——只读调研，产出清单交架构师拍板，**不执行任何隔离/移动/删除**。
> 日期：2026-06-29 ｜ 底座：微信 8.0.71（D-014）
> 扫描范围（代理B）：`03_execute_执行任务/` · `07_archive_归档/` · `06_refs_参考资料/` · `02_tools_工具/` · `tools/`
> 不重叠范围：`docs/` · `refs/` · 根目录 md · classmap（归代理A）

---

## 0. 方法与坐标系

- **坐标系来源**：`CLAUDE.md`（产品形态/8071 锁定/29 铁律）+ `PROJECT_INDEX.md` §负一权威链/§零账实/§二目录/§三速查 + `docs/README.md`（8071 主车道 + 隔离区）。
- **判定基线**：8071 现行 = 主车道（不动）；8.0.65/66/68/70、Catfish/微密友/趣味密友/甜密友/蜘蛛密友、iOS、第一次开发期已废弃方案 = 旧资料（盘点）。
- **工具坑（重要）**：`.cursorignore` 把 `07_archive_归档/` 整目录、`tools/probe_*.js`、`tools/*.log`、`06_refs/采集快照_dump_snapshots/` 等隔离出 Cursor 索引——Cursor 的 Grep/Glob 对这些路径返回空或报 `filtered out by .cursorignore`。本盘点对被隔离区改用 PowerShell `Select-String`/`Get-ChildItem` 双工具交叉扫描，已覆盖。
- **证据等级**：L1 动态/L2 静态(读码/读文)/L4 待验证（沿用 CLAUDE.md §三.五）。本报告所有条目均为 L2（文件原文 file:line），未跑动态。

---

## 1. 总结（额外回答①）

- **本区域旧资料占比（粗估，L2）**：
  - `06_refs_参考资料/`：≈100% 是竞品（仅 `competitor_catfish/CATFISH_REVERSE.md` + `README.md`），但**是当前唯一竞品真源、KEEP**。
  - `07_archive_归档/`：21 个归档任务中 **1 个纯竞品（T09_Catfish）**；其余 20 个是 8071 自有功能归档，但普遍**夹带** 8066/8070/Catfish 对照段落。整目录已被 `.cursorignore` 隔离，本就是隔离区。
  - `02_tools_工具/`（48 文件）：竞品探针 ≈7 个（~15%），其余为 8071 自有找点探针。
  - `tools/`（166 文件，含大量 .log）：竞品探针 ≈10 个，旧版本探针 1 个，其余为 8071 自有找点探针 + 防封/加密/发版在用工具。
  - `03_execute_执行任务/`（活跃区）：**无纯旧资料文件**；只有活跃任务里夹带的旧版本对照引用（P18 的 8066 基线、V3T5 的 8065_jadx、P_AntiBanGate 的 8070/68 佐证）+ 大量 `I:\miyou-server` 外部引用（正常在用）。
- **最该先清的几坨（按收益/低风险排序）**：
  1. **`02_tools_工具/` + `tools/` 的竞品探针**（`probe_catfish_*`、`cat_revoke_*`、`trace_catfish_reddot.js`、`catfish_push_stack.js`、`probe_catfish_snsmsg.js`、`verify_maintabui_i.js`、`quwei_miyou_probe/` 整目录）→ 找点已用完，建议归档 `07_archive_归档/tools/`（TOOLS_INDEX §八既定惯例）。
  2. **`tools/trace_w1_insert.js`**（写死 8.0.66 类名 `w1`）→ 旧版本探针，归档。
  3. **`07_archive_归档/T09_Catfish_L0v3/`**（纯竞品，INDEX 自标"授权已过期"）→ 待裁决：是否从 07_archive 进一步隔离至 `docs/isolation/` 竞品区。
  4. **`03_execute_执行任务/P_AntiBanGate_防封授权闸/A3-0_核账_可迁清单.md:30`**——作者已自标 `8066 legacy ... 死锚，建议清` → **已裁决：2026-06-30 整文件减法删除**（git 可查）。

---

## 2. 主清单（每条一行）

> 列：路径 ｜ 类别 ｜ 版本/竞品锚点 ｜ 是否仍被8071主车道引用 ｜ 建议处置 ｜ 证据(file:line)
> 处置词只用：归档07_archive / 隔离docs-archive / 保留 / 待裁决（遵守「不删只搬」铁律）。

### 2.1 竞品（Catfish / 微密友 / 趣味密友 / 甜密友）

| 路径 | 类别 | 锚点 | 8071仍引用? | 建议处置 | 证据 |
|---|---|---|:--:|---|---|
| `06_refs_参考资料/competitor_catfish/CATFISH_REVERSE.md` | 竞品 | Catfish 8.0.70.2 / 微密友 | **是** | **保留**（竞品唯一真源，`docs/isolation/INDEX_COMPETITOR.md` + `CLAUDE.md`§十二 + 多处 07 brief 引它） | `CATFISH_REVERSE.md:1,8,10` |
| `07_archive_归档/T09_Catfish_L0v3/`（brief/PITFALLS/result/t09_probe.js/t09_output.*） | 竞品 | Catfish 8.0.70 `com.tencent.mn1` / `com.catfish.newvip.*` | 否（"授权已过期"） | **待裁决**：保持归档 or 进一步隔离 docs/isolation | `07.../INDEX.md:29`；`T09.../result.md:1,3,33,46,48`；`brief.md:23` |
| `07_archive_归档/P16_朋友圈Proto/scripts/catfish_hook_tracer.js` | 竞品探针 | Catfish hook tracer | 否（已归档） | 保留（已在归档区，保持隔离） | 文件名（Shell 列出） |
| `02_tools_工具/dynamic_crawler_动态爬虫/probe_catfish_hv_refresh.js` | 竞品探针 | `com.catfish.newvip.core.UserControll` 8.0.70.2 | 否 | **归档07_archive**（07/tools/） | `:2,12,34,61` |
| `02_tools_工具/dynamic_crawler_动态爬虫/probe_catfish_push_notify.js` | 竞品探针 | Catfish :push UserControll | 否 | 归档07_archive | `:1,10,24` |
| `02_tools_工具/dynamic_crawler_动态爬虫/probe_catfish_sns.js` | 竞品探针 | Catfish MainEntry/UserControll/ActivityControll | 否 | 归档07_archive | `:13,106,162` |
| `02_tools_工具/dynamic_crawler_动态爬虫/probe_catfish_fg_silence.js` | 竞品探针 | Catfish 前台静默 | 否 | 归档07_archive | `:1,10,29` |
| `02_tools_工具/dynamic_crawler_动态爬虫/probe_catfish_va5y.js` | 竞品探针 | Catfish MainEntry hookNewCon | 否 | 归档07_archive | `:1,8` |
| `02_tools_工具/dynamic_crawler_动态爬虫/cat_revoke_hook.js` | 竞品探针 | Catfish WmyRevokeMsg 撤回 | 否 | 归档07_archive | `:3,12,13` |
| `02_tools_工具/dynamic_crawler_动态爬虫/cat_revoke_scan.js` | 竞品探针 | Catfish 撤回签名扫描 | 否 | 归档07_archive | `:3` |
| `tools/catfish_push_stack.js` | 竞品探针 | Catfish 8.0.70 :push replaceNotification | 否 | 归档07_archive | `:2` |
| `tools/probe_catfish_snsmsg.js` | 竞品探针 | Catfish hookSnsMsgList 织入点 | 否 | 归档07_archive | 文件名（07/P21 worklog:301 引此名） |
| `tools/trace_catfish_reddot.js` | 竞品探针 | Catfish 8.0.70 红点消失追踪 | 否 | 归档07_archive | `:2,3,15,93` |
| `tools/verify_maintabui_i.js` | 竞品参考探针 | 参考 Catfish 8.0.70 showUnReadMsgCount | 否（功能已装机） | 归档07_archive | `:3` |
| `tools/quwei_miyou_probe/`（probe_quwei.js/.log、QUWEI_MIYOU_PROBE.md、probe_unread_hook.js 等 11 文件） | 竞品样本探针 | 趣味/微密友 + `com.catfish.newvip.*` + `E:\apk_diff\天龙共存版1` | 否 | **归档07_archive** | `QUWEI_MIYOU_PROBE.md:4`；`probe_quwei.log:1495,5171`；`probe_unread_hook.js:7,22` |

### 2.2 旧版本（8.0.65 / 66 / 68 / 70）— 03 活跃区夹带

| 路径 | 类别 | 锚点 | 8071仍引用? | 建议处置 | 证据 |
|---|---|---|:--:|---|---|
| `03_execute_执行任务/P18_离线采集/brief.md` | 旧版本 | 8.0.66 空白基线（**未落地**） | 是（P18 仍在 03，⬜本轮跳过） | **待裁决**（P18 本轮跳过；8066 基线计划是否随 P18 一起冻结/归档） | `:13,22`；`result.md:10` |
| `03_execute_执行任务/V3T5_迁移探针/迁移接收端协议画像.md` | 旧版本+外部 | `8.0.65_jadx`（"仅 8.0.65，未采用"）+ `I:\apk2\...jadx_8071` | 是（V3T5=v3 任务） | 保留（已自标未采用；v3 用 8071 jadx 为主） | `:13,15,210` |
| `03_execute_执行任务/P_AntiBanGate_防封授权闸/研究线证据映射_20260625.md` | 旧版本佐证 | "8.0.71（含 8.0.70 静态参考）"/"8.0.71 与 8.0.68 字节级相同" | 是（防封活跃线） | 保留（8071 主，旧版仅字节级佐证） | `:71,161` |
| `03_execute_执行任务/P_AntiBanGate_防封授权闸/A3-0_核账_可迁清单.md` | 旧版本死锚 | `f45.s0｜8066 legacy｜❌(旧版)｜不收（死锚，建议清）` | 否（作者已自标死锚） | **已删**（2026-06-30 整文件减法删除） | `:30` |
| `03_execute_执行任务/P_AntiBanGate_防封授权闸/控制台_v2.html`·`_v3`·`_v4`·`demo/控制台_demo.html` | 误命中(demo假数据) | 字符串 `anomaly:'版本漂移 8.0.70→已纠正'` | 是 | 保留（demo 演示数据，非旧资料；锚点误命中，标注以免后续重判） | `控制台_v3.html:437` 等 |

### 2.3 旧版本（8.0.66）— tools 脚本写死类名

| 路径 | 类别 | 锚点 | 8071仍引用? | 建议处置 | 证据 |
|---|---|---|:--:|---|---|
| `tools/trace_w1_insert.js` | 旧版本探针 | `8.0.66 = com.tencent.mm.plugin.sns.storage.w1` | 否 | **归档07_archive** | `:18,21` |

### 2.4 旧版本/竞品 — 07_archive 已归档任务内夹带段落（已隔离，建议不拆）

> 这些是 8071 自有功能的归档任务，正文里夹带 8066/8070/Catfish **对照/差异限定**段落。拆段会破坏归档完整性，且整目录已被 `.cursorignore` 隔离。建议**保持归档、不拆段**。仅登记证据备查。

| 路径 | 锚点 | 证据 |
|---|---|---|
| `07_archive_归档/P15_脚手架/research.md`·`result.md` | Catfish MainEntry/UserControll 对照 + 8.0.66 基线 + 8.0.70 来源 | `research.md:5,12,29,37`；`result.md:25,45` |
| `07_archive_归档/P19_通讯录隐藏/brief.md` | Catfish 8070/甜密友 + 8.0.66↔8.0.71 + `docs/archive/wechat_8066` | `:3,7,18,53,68,85,89` |
| `07_archive_归档/P21_MomentsRedDot/worklog.md` | Catfish 8.0.70 多层对照 + `apk2/_1__B_rewrite` 历史回查 | `:15,17,40,262,275,301` |
| `07_archive_归档/P16_朋友圈Proto/brief.md`·`result.md` | 8.0.66 归档/基线 | `brief.md:78`；`result.md:90` |
| `07_archive_归档/P17_会话LSPosed/result.md` | `.m (8.0.66)` 旧类名 | `:23,28` |
| `07_archive_归档/P1E_Filter读Registry/worklog.md` | 8.0.66 `.m` | `:193` |
| `07_archive_归档/P20_搜索拦截/brief.md` | `docs/archive/wechat_8066/HOOK_MAP_V1` 历史规划 | `:97` |
| `07_archive_归档/P23_AntiRecall/brief.md` | 竞品 8.0.70 直搬已证伪（F-37） | `:18` |
| `07_archive_归档/P_IMPORT_密友密群导入/brief.md` | `com.tencent.mn1` 竞品 8.0.70.2 跨验 | `:4,16` |
| `07_archive_归档/P_CV1_通讯录V态热切/worklog.md` | 旧版 ListView 对照 + `I:\apk2\...jadx_8071` | `:100,109` |
| `07_archive_归档/M6a_MomentsGroupIcon/brief.md` | 竞品 8.0.66 锚点 / CATFISH_REVERSE §3/§295 | `:41,83` |
| `07_archive_归档/review_snapshots/*.md`（P16_/W1_/W2_/2026-05-27_） | 8.0.66/8.0.70/Catfish 历史审稿快照 | `2026-05-27_搜索路径...:86,107,109`；`W1_..._交接快照.md:14,26,34`；`P16_20260519.md:47,55,96` |

### 2.5 外部只读引用（不归档，只统计）

> 指向仓库外路径，按任务要求只统计、不处置（这些是当前在用的外部资源，非"旧资料"）。

| 外部根 | 性质 | 命中数(本区，L2) | 代表证据 |
|---|---|:--:|---|
| `I:\miyou-server\` | 授权服务器（当前在用） | ≥15 处（03 区集中） | `P_RB1/worklog.md:7,24`；`P_RB1/钥匙加固...:131`；`S3a0/result.md:11`；`P_AntiBanGate/封停删卡...:57`、`F68...:17,25`；`P_LeanCloseout/recon/G8对账...:103` |
| `I:\apk2\` | 历史版本/jadx/KPI 研究（只读） | ≥5 处 | `P18/brief.md:34,35,70`；`V3T5/...:13,210`；`07/P_CV1/worklog.md:100`；`07/P21/worklog.md:262` |
| `E:\apk_diff\` | 改版/竞品 APK 样本库（只读） | 2 处 | `tools/quwei_miyou_probe/QUWEI_MIYOU_PROBE.md:4`；`07/T09/PITFALLS.md:124,130`(apk2 同源样本) |
| `E:\ios-dylib\` / `D:\appleapp\` | iOS 参考 | **0 处（本区）** | 仅 `TOOLS_INDEX.md`§六 / `PROJECT_INDEX.md`§四 提及（属代理A根目录 md） |

---

## 3. iOS 类专项

- **本盘点四区（03/07/06_refs/02_tools/tools）零 iOS 资料文件**：`ios-dylib`/`appleapp`/`蜘蛛密友 dylib` 锚点在本区 .md/.js 内无命中。
- iOS 引用全部集中在根目录 `TOOLS_INDEX.md`§六、`PROJECT_INDEX.md`§四的**外部路径索引**（`E:/ios-dylib/shitou-miyou-core/`、`D:/appleapp/`）——属**代理A范围**，本报告不处置，仅备注交接。

---

## 4. 探针脚本专项（额外回答②：找点用完可归档 vs 仍在用要留）

> 依据：`TOOLS_INDEX.md`（§一登记在用工具 + §八"废弃工具→移 07_archive_归档/tools/"）+ 文件命名/头注释 L2。

### 4.A 找点用完 → 建议归档（竞品类，最该先清）

`02_tools_工具/dynamic_crawler_动态爬虫/`：`probe_catfish_hv_refresh.js` · `probe_catfish_push_notify.js` · `probe_catfish_sns.js` · `probe_catfish_fg_silence.js` · `probe_catfish_va5y.js` · `cat_revoke_hook.js` · `cat_revoke_scan.js`
`tools/`：`catfish_push_stack.js` · `probe_catfish_snsmsg.js` · `trace_catfish_reddot.js` · `verify_maintabui_i.js` · `trace_w1_insert.js`(8.0.66) · `quwei_miyou_probe/`(整目录 11 文件)

### 4.B 找点用完 → 建议归档（8071 自有，但对应功能已装机/归档）

> 非旧资料，但已完成使命；与 4.A 同批可清，降低 02_tools/tools 噪音。**未登记 TOOLS_INDEX**。
- `02_tools_工具/`：`probe_conv_warm*.js`、`conv_refresh_probe*.js`、`probe_visibility_v3/v4.js`、`probe_me_tab_v2~v6*.js`、`probe_revoke_*.js`、`probe_a2.js`/`chk_a2.js`、`search_crawler.js`、`moments_visibility_crawler.js`、`probe_fresh_msg_trigger.js`、`probe_foreground_audio.js`/`probe_audio_lite.js`、`probe_v_class_stim.js`/`probe_v_trigger_enum.js`、`probe_r0d_invoke.js`/`probe_fc5d_init.js`（对应 P26 会话热切/P20 搜索/P21 红点/CA 来电——均已归档）。
- `tools/`：`probe_fire*.js`、`probe_trigger_*.js`、`probe_cl0*.js`、`probe_fc5*.js`、`probe_h0d.js`、`probe_ik3n0.js`、`probe_kc5x_g.js`、`probe_mvvm_full.js`、`probe_nm*.js`、`probe_notif*.js`、`probe_push_*.js`、`probe_tab_unread_8071.js`、`probe_w1_fields.js`、`probe_self_wxid.js`、`trace_tab_badge_v2/v3.js`、`find_reddot_field.js`/`reddot_clear.js` 等找点探针。
- ⚠️ **此堆需架构师二次确认**：部分可能仍作"回归复测"用（如推送/红点回归），建议清前对一遍 P22_PushFilter（仍在 03 活跃）是否引用。标 **待裁决**。

### 4.C 仍在用 → 保留（防封 / 加密 / 发版 / 回归）

> `TOOLS_INDEX.md`§一**明确登记**或属加密/发版基础设施，**不动**。
- **防封官（B35/B56 在用）**：`tools/dump_mm_z3.js` · `dump_normsg_plaintext.js` · `dump_normsg_native.js` · `dump_normsg_s6.js` · `dump_normsg_boot.js` · `dump_wx_detect.js` · `dump_sig_check.js` · `s6_decode.py` · `s6_scan_so.py` · `elf_imports.py`（+ `dump_normsg_*_B56.js` 系列）。
- **伪装订位回归（E2）**：`tools/probe_loc_*_8071.js`（v1~v4 + poc/pick/intent/moments，TOOLS_INDEX 标"留作回归"）。
- **加密/KDF/发版基础设施**：`tools/gen_registry_cipher.py` · `gen_registry_fallback.py` · `gen_bootstrap_cipher.py` · `kdf_common.py` · `gen_kdf_vectors.py` · `gen_gcm_vectors.py` · `verify_aes_block.py` · `test_config_crypto.cpp` · `run_native_tests.ps1` · `check_classmap.ps1` · `guard_status.ps1`/`.cmd` · `verify_push_hooks_8071.js` · `gate_three_axis.js` · `frida_kpi_probe.js`。

---

## 5. 断链 / 文档与实际不符（额外回答③）

| # | 位置 | 问题 | 证据 |
|---|---|---|---|
| D1 | `06_refs_参考资料/README.md` 子目录树 | 描述 5 个子目录**实际不存在**：`apk_samples/`、`wechat_refs_微信参考/`、`catfish_refs_原作者参考/`、`frida_refs_动态采集脚本/`、`采集快照_dump_snapshots/`（实际仅 `competitor_catfish/` + `README.md`） | `README.md` 子目录树段（Shell 读出） vs 实际 `Get-ChildItem` 仅 2 项 |
| D2 | `07_archive_归档/M6a_MomentsGroupIcon/brief.md:83` | 引用 `CATFISH_REVERSE.md §295`、`brief.md:41` 引 §3——**锚点真伪未核**（CATFISH_REVERSE.md 实际有 §三/§295 行但编号体系需对齐） | `M6a/brief.md:41,83`（标 L4 待核） |
| D3 | `06_refs_参考资料/` 与 `TOOLS_INDEX.md`§〇§A | TOOLS_INDEX/README 均把 8.0.66 APK 落点写在 `06_refs_参考资料/apk_samples/wechat_8066.apk`，但该目录/文件**从未落地**（状态 ⬜） | `06_refs/README.md` 树 + `TOOLS_INDEX.md:16,32`（TOOLS_INDEX 属代理A，仅备注） |

> 注：D1/D3 涉及 `06_refs/README.md`（本区）建议**待裁决**——是补建空目录占位，还是改 README 删除未落地子目录描述（属文档修订，需架构师定）。

---

## 6. 待裁决项汇总（交架构师）

1. **02_tools + tools 竞品探针 + `trace_w1_insert.js`（§2.1/§2.3/§4.A）**：是否批量 `git mv` 至 `07_archive_归档/tools/`（TOOLS_INDEX §八既定路径）。低风险、收益高。
2. **§4.B 的 8071 自有"找点用完"探针**：是否随竞品探针一并归档？需先对 P22_PushFilter（活跃）引用面。
3. **`07_archive_归档/T09_Catfish_L0v3/`**：保持现状（已在归档区）还是进一步隔离至 `docs/isolation/`（竞品集中区）。
4. **`03/P_AntiBanGate/A3-0_核账_可迁清单.md:30` 的 8066 死锚行**：作者已自标"建议清" → **已裁决：2026-06-30 整文件减法删除**（git 可查）。
5. **`03/P18_离线采集` 的 8.0.66 基线计划（§2.2）**：P18 本轮跳过——8066 基线是否随 P18 冻结/归档。
6. **`06_refs/README.md` 断链（D1）**：补建占位目录 or 修订 README 删除未落地子目录描述。

---

## 7. 红线遵守声明

- 全程**只读**；唯一写盘 = 本报告。其余文件零改/移/删。
- 处置建议遵守「不删只搬」：仅出现 归档/隔离/保留/待裁决，无"删"。
- 每条均附 file:line 证据；无证据项标 L4/待核（D2）。
- 未与代理A范围（docs/refs/根目录 md/classmap）重叠；iOS/外部路径仅做交接备注。
