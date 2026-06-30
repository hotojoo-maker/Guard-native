# OLDREF_AUDIT_A · 旧资料盘点（文档线 / 代理A）

> 角色：Guard Native「旧资料盘点员·文档线」——**只读调研**，产出清单交架构师拍板，不执行任何隔离/移动/删除。
> 日期：2026-06-29
> 扫描范围（仅本区域，其余目录归代理B）：`docs/` 整棵树（重点 `docs/archive/`）、`refs/`、根目录所有 `*.md`、`docs/classmap/v8071.yaml`。
> 坐标系来源：`CLAUDE.md`（产品形态/8071 锁定/铁律）、`PROJECT_INDEX.md` §负一/§零/§二/§三、`docs/README.md`（8071 主车道 + 隔离区）、`docs/archive/INDEX.md`、`docs/isolation/INDEX_COMPETITOR.md`。
> 处置词表（项目铁律「不删只搬」）：仅用 `保留` / `保留隔离docs-archive` / `归档07_archive` / `待裁决`；**不出现「删」**。

---

## 〇、判定基线（8071 主车道 vs 旧资料）

- **当前唯一执行底座 = 微信 8.0.71**（`CLAUDE.md` D-014；`docs/README.md:3`）。
- **产品形态已铁定 = LSPatch 打包型 APK**（`PROJECT_INDEX.md:13`）；早期「LSPosed Xposed 模块」表述 = 过时口径。
- 旧资料 = 8.0.65/66/68/70 等 ≠8071 的 hook/类名/research/规划；竞品 Catfish/密友系；iOS 参考；第一次开发期引入、现已废弃/证伪/被取代者。
- 关键结论先说：`docs/archive/` 与 `docs/isolation/` 两个隔离区**已建好且到位**（`docs/archive/INDEX.md`、`DOC_AUDIT_2026-05-27.md` 为证）；本区域真正待架构师处理的增量是 **① 根目录 `CHATGPT_项目全景手册.md`（过时+断链）② `refs/` 一坨竞品/旧版本资料（受控但未物理隔离）**。

---

## 一、docs/ 主车道（当前·建议保留不动）

| 路径 | 类别 | 锚点 | 仍被8071主车道引用 | 建议处置 | 证据(file:line) |
|------|------|------|:--:|------|------|
| `docs/README.md` | 当前主车道 | 8071 文档入口 | 是（自身=入口） | 保留 | `docs/README.md:1-18` |
| `docs/GUARD_GATE_TRUTH.md` | 当前主车道 | 门控/状态机权威 | 是 | 保留 | `docs/README.md:13` |
| `docs/HOOK_MAP_8071_AUTHORITATIVE.md` | 当前主车道 | 8071 hook 权威 | 是 | 保留（旧资料引用为健康diff，见§六） | `docs/README.md:14`；`PROJECT_INDEX.md:24` |
| `docs/CONV_REFRESH_PROBLEM.md` | 当前主车道 | 会话H↔V热切问题单 | 是 | 保留 | `docs/README.md:17` |
| `docs/HONEYPOT_蜜罐设计.md` | 当前主车道 | 蜜罐汇总 | 是 | 保留 | `docs/README.md:18` |
| `docs/PRODUCT_GATE.md` | 当前主车道 | 产品总闸/状态机/口令 | 是 | 保留 | `PROJECT_INDEX.md:105` |
| `docs/P22_PushFilter_VoIP.md` | 当前主车道 | 来电/通知拦截(P22) | 是 | 保留 | `PROJECT_INDEX.md:138-139` |
| `docs/A3_GROUP_FILTER_IMPL.md` | 当前主车道 | 密群过滤实现(A3) | 是 | 保留 | 功能账实 `PROJECT_INDEX.md:49` |
| `docs/RELEASE_RULES.md` | 当前主车道 | 发布/签名/共存规则 | 是 | 保留 | `CLAUDE.md` §十三 |
| `docs/RELEASE_RECIPE契约.md` | 当前主车道 | 发布配方契约 | 是 | 保留 | 发版线现行 |
| `docs/ARCHITECTURE.md` | 当前主车道 | 8071技术架构共识(从CLAUDE§六移出) | 是 | 保留 | `docs/ARCHITECTURE.md:1-3`（正文均8071主路径） |
| `docs/DEBUG_CONSOLE_V2.md` | 当前主车道(开发工具) | 调试控制台V2结构化遥测 | 是 | 保留 | `docs/DEBUG_CONSOLE_V2.md:1-5` |
| `docs/classmap/v8071.yaml` | 当前主车道 | 8071混淆类对照字典 | 是 | 保留（内含3条旧符号登记，见§六） | `docs/classmap/v8071.yaml:24-26` |
| `docs/DOC_AUDIT_2026-05-27.md` | 当前主车道(审计留档) | 8071隔离后全盘复检报告 | 是（历史快照） | 保留 | `docs/DOC_AUDIT_2026-05-27.md:1-8` |

> 说明：本表 14 份均为 8071 现行，**非盘点对象**，列出仅证明未误判主车道。

---

## 二、docs/archive/ — 已隔离历史（建议：维持隔离docs-archive）

> 该目录 2026-05-27 起即为隔离区，`docs/archive/INDEX.md` 已逐条标注用途/风险/DEPRECATED。**已到位，无需新动作**；仅登记盘点。

| 路径 | 类别 | 版本锚点 | 仍被8071主车道引用 | 建议处置 | 证据(file:line) |
|------|------|------|:--:|------|------|
| `docs/archive/INDEX.md` | 隔离区索引 | 历史总索引 | 是（隔离区导航） | 保留 | `docs/archive/INDEX.md:1-4` |
| `docs/archive/wechat_8066/CLASS_MAP_8066.md` | 旧版本 | 8.0.66 混淆类 | 是（仅diff对照） | 保留隔离docs-archive | `docs/archive/INDEX.md:12` |
| `docs/archive/wechat_8066/HOOK_POINTS.md` | 旧版本 | 8.0.66 Frida伪代码 | 是（8071权威§F04作历史来源） | 保留隔离docs-archive | `docs/archive/INDEX.md:14`；`HOOK_MAP_8071_AUTHORITATIVE.md:12,181,317` |
| `docs/archive/wechat_8066/HOOK_MAP_V1.md` | 早期废弃 | v1 P0/P1规划 DEPRECATED | 是（diff引用） | 保留隔离docs-archive | `docs/archive/INDEX.md:13`；`HOOK_MAP_8071_AUTHORITATIVE.md:13,318` |
| `docs/archive/wechat_8066/VERSION_CLASSMAP.md` | 旧版本 | 跨版本字段对照 | 否（archive内部） | 保留隔离docs-archive | `docs/archive/INDEX.md:15` |
| `docs/archive/wechat_8066/RESEARCH_SUMMARY.md` | 旧版本 | 8066数据架构研究汇总 | 否（已被8071权威取代） | 保留隔离docs-archive | `docs/archive/INDEX.md:44`；`PROJECT_INDEX.md:161` |
| `docs/archive/wechat_8066/tasks/T05_SnsObject_文档员.md` | 旧版本 | T05 SnsObject 8066定位 | 否 | 保留隔离docs-archive | `docs/archive/INDEX.md:21` |
| `docs/archive/wechat_8066/tasks/T07_ContactStorage_文档员.md` | 旧版本 | T07 ContactStorage 8066 | 否 | 保留隔离docs-archive | `docs/archive/INDEX.md:22` |
| `docs/archive/planning/P16_朋友圈Proto_research_8066.md` | 旧版本 | 8.0.66 P16调研 | 否（8071实现在P16 result） | 保留隔离docs-archive | `docs/archive/INDEX.md:32` |
| `docs/archive/AI_WORKFLOW_CHECKLIST.md` | 早期废弃 | 8066「F04会话隐藏」工作流 DEPRECATED | 否 | 保留隔离docs-archive | `docs/archive/INDEX.md:42` |
| `docs/archive/USER_AI_USAGE_GUIDE.md` | 早期废弃 | 非程序员协作指南(指向上面8066清单) DEPRECATED | 否 | 保留隔离docs-archive | `docs/archive/INDEX.md:43` |
| `docs/archive/SKILL_WORKFLOW.md` | 早期废弃 | AI角色工作流(已被10角色skills取代) | 否 | 保留隔离docs-archive | `docs/archive/INDEX.md:45` |
| `docs/archive/SETTINGS_UI_V2.md` | 早期废弃 | 设置页v2历史设计(现以HOOK_MAP§9/P_SE8为准) | 否 | 保留隔离docs-archive | `docs/archive/INDEX.md:46` |

---

## 三、docs/isolation/ — 竞品隔离（建议：维持）

| 路径 | 类别 | 竞品锚点 | 仍被8071主车道引用 | 建议处置 | 证据(file:line) |
|------|------|------|:--:|------|------|
| `docs/isolation/INDEX_COMPETITOR.md` | 竞品(隔离索引) | Catfish 8.0.70 行为参考索引 | 是（竞品隔离导航） | 保留 | `docs/isolation/INDEX_COMPETITOR.md:1-12`；`docs/README.md:27` |

---

## 四、refs/ — 第一次开发期竞品/旧版本参考（受控但未物理隔离 → 待裁决）

> 全 10 份均为开发期引入：Catfish 竞品(8.0.70) + 旧版本(8.0.65) + apk2 研究副本。其中 6 份仍被当前权威/索引作「竞品参考 / 资料来源」引用 → **不能简单移动**，物理隔离需同步改 3 处入链（见§七待裁决）。

| 路径 | 类别 | 版本/竞品锚点 | 仍被8071主车道引用 | 建议处置 | 证据(file:line) |
|------|------|------|:--:|------|------|
| `refs/MainEntry.java` | 竞品 | Catfish `com/catfish/newvip/MainEntry.java`(989行/82方法) | 是（竞品参考） | 待裁决（维持refs参考区 or 隔离docs-isolation） | `refs/SOURCE_MAP.md:5`；`isolation/INDEX_COMPETITOR.md:20`；`PROJECT_INDEX.md:174` |
| `refs/UserControll.java` | 竞品 | Catfish `.../core/UserControll.java`(944行/状态机) | 是（竞品参考） | 待裁决 | `refs/SOURCE_MAP.md:6`；`isolation/INDEX_COMPETITOR.md:21`；`PROJECT_INDEX.md:175` |
| `refs/VipPreference.java` | 竞品 | Catfish `.../preference/VipPreference.java`(MMKV封装) | 是（竞品参考） | 待裁决 | `refs/SOURCE_MAP.md:8`；`isolation/INDEX_COMPETITOR.md:22`；`PROJECT_INDEX.md:176` |
| `refs/ReflectHelper.java` | 竞品 | Catfish `.../util/ReflectHelper.java`(反射工具) | 是（竞品参考） | 待裁决 | `refs/SOURCE_MAP.md:7`；`PROJECT_INDEX.md:177` |
| `refs/filter_moments.js` | 竞品/旧Frida | apk2 `prod/filter_moments.js`(朋友圈v21,8066/8070类名) | 是（竞品策略参考） | 待裁决 | `refs/SOURCE_MAP.md:11`；`isolation/INDEX_COMPETITOR.md:23` |
| `refs/CATFISH_8070_INDEX.md` | 竞品 | Catfish 8.0.70 完整资源索引 | 是（refs参考登记） | 待裁决 | `refs/CATFISH_8070_INDEX.md:1`；`PROJECT_INDEX.md:190` |
| `refs/FEATURE_MATRIX.md` | 竞品/旧 | 密友功能矩阵F01-F09(8066/8070时代) | 是（8071权威作资料来源） | 待裁决 | `refs/FEATURE_MATRIX.md:1,12-13`；`HOOK_MAP_8071_AUTHORITATIVE.md:15,319` |
| `refs/VERSION_8065_ANALYSIS.md` | 旧版本 | 微信8.0.65静态分析(versionCode 2960) | 是（refs参考登记） | 待裁决 | `refs/VERSION_8065_ANALYSIS.md:1-14`；`PROJECT_INDEX.md:189` |
| `refs/SOURCE_MAP.md` | 旧资料地图 | refs→apk2/Catfish源路径映射(指向外部I:/apk2) | 是（refs参考登记） | 待裁决 | `refs/SOURCE_MAP.md:1-11`；`PROJECT_INDEX.md:191` |
| `refs/FAILURE_LOG.md` | 重复/旧 | apk2 `prod/FAILURE_LOG.md`副本(F-01=8070套8066) | 否（主车道用根FAILURE_LOG.md） | 待裁决（与根目录同名易混，见§五.2） | `refs/SOURCE_MAP.md:9`；`refs/FAILURE_LOG.md:1,7-16` |

---

## 五、根目录 *.md（17 份）

### 5.1 当前主车道（建议保留，非盘点对象）

`CLAUDE.md` · `AGENTS.md` · `PROJECT_INDEX.md` · `HOOKMAP.md` · `TASK_BOARD.md` · `DECISION_LOG.md` · `RISK_REGISTER.md` · `FAILURE_LOG.md` · `PROTECTION_MAP.md` · `ANTIBAN_MAP.md` · `TOOLS_INDEX.md` · `FINDINGS.md` · `GIT_GUIDE.md` · `术语词表_大白话对照.md`
证据：`PROJECT_INDEX.md:104-118`（根目录核心文档表）。
`STATUS_防封加密线.md` = 当前防封/加密线状态页（最后更新 2026-06-28，D-021），保留。证据：`STATUS_防封加密线.md:1,5`。

### 5.2 待盘点（旧/待裁决）

| 路径 | 类别 | 锚点 | 仍被8071主车道引用 | 建议处置 | 证据(file:line) |
|------|------|------|:--:|------|------|
| `CHATGPT_项目全景手册.md` | 早期废弃 🔴 | 给外部AI的导览，**2026-05-22（8071隔离前）**；形态写「LSPosed Xposed 模块」(过时,现=LSPatch打包型)；FAILURE_LOG 写「F-01~F-31」(现F-42)；必读顺序表第7行引用 `docs/HOOK_POINTS.md`(已隔离→断链)；文件 GBK 编码(违 G7) | 否（CLAUDE/PROJECT_INDEX 才是真入口，本表 §一未收录） | **待裁决：归档07_archive 或 隔离docs-archive + 断链/编码待修** | head 实读：标题L1 / 更新2026-05-22 / 形态LSPosed / 必读表第7行=docs/HOOK_POINTS.md；对照 `PROJECT_INDEX.md:13`(形态)、`CLAUDE.md`(F-42) |
| `07_archive_归档/JIDE_ANDROID_ACC_STRIKE_MAPPING_20260617.md` | 防封研究/含iOS对照 | JiDe iOS 防封样本 × Android 8.0.71 环境检测方向纠偏(2026-06-17,1950行)；自述「纠偏记录，旧 AccStrike/AutoAccessibility 映射不作当前方向」 | 是（自述权威账=ANTIBAN_MAP.md，属防封线历史附录） | ✅ 已归档 `07_archive_归档/`（2026-06-29，用户拍板；ANTIBAN_MAP ×3 + 防封官 skill 指针已更新） | `07_archive_归档/JIDE_ANDROID_ACC_STRIKE_MAPPING_20260617.md:1-7` |

---

## 六、docs/classmap/v8071.yaml — 内部旧/废弃符号登记（文件保留，标出条目）

> 文件本身是 8071 当前字典（保留不动）；按任务要求标出其中 `status: deprecated` 与「8066 legacy」条目，供架构师确认是否仍需留考古。

| 条目(file:line) | 符号 | 性质 | status | 说明 |
|------|------|------|:--:|------|
| `docs/classmap/v8071.yaml:37-42` | `f45.s0` | 8066 legacy | ok | 会话列表Adapter 8.0.66历史名，feature 标「8066 legacy」，仅作跨版本对照，8.0.71 不用 |
| `docs/classmap/v8071.yaml:207-212` | `fz2.e` | 已证伪 | deprecated | 搜索联系人结果item，wxid直取路径已证伪，仅留探针 |
| `docs/classmap/v8071.yaml:264-270` | `com.tencent.mm.booter.notification.x` | 已证伪 | deprecated | 通知booter类，8.0.71零命中已证伪废弃 |

> 健康性核验：`HOOK_MAP_8071_AUTHORITATIVE.md` 对 8066/竞品资料的全部引用均显式标注「8066历史·仅diff·DEPRECATED·不得作8071实证」(`:12-16,181,305,317-319`)，属**健康隔离引用**，未发现把旧资料当默认写码依据的 🔴 情形。

---

## 七、额外回答

### 1. 本区域旧资料占比 + 最该先清的几坨

- 文档线纯旧资料计 ≈ **24 份**：`refs/`(10) + `docs/archive/`(12，含INDEX) + 根目录 `CHATGPT_项目全景手册.md`(1)，另 `JIDE_...`(1) 归防封研究历史。占文档线总量(docs 28 + refs 10 + 根 17 + classmap 1 ≈ 56)约 **43%**。
- **最该先处理（按优先级）**：
  1. 🔴 **`CHATGPT_项目全景手册.md`**——根目录入口级、面向外部 AI，过时口径(形态/版本/铁律计数全旧)+断链+GBK 编码，会直接把接手 AI 带偏，优先处置。
  2. **`refs/` 一坨**——量最大且仍散在根级 `refs/`，仅被「索引登记」未物理收口；建议架构师决定是否并入 `docs/isolation/` 物理隔离。
  3. `docs/archive/`——已隔离到位，**保持现状即可**，无需新动作。

### 2. 重复冗余

| 冗余点 | 详情 | 证据 |
|------|------|------|
| 同名 FAILURE_LOG 两份 | `refs/FAILURE_LOG.md`(apk2 prod副本,F-01起讲8070套8066) vs 根 `FAILURE_LOG.md`(Guard Native 自有 F-01~F-42)——同名不同源，易被接手AI误取 | `refs/SOURCE_MAP.md:9`；`refs/FAILURE_LOG.md:1` |
| 多份「项目导览/全景」职责重叠 | `CHATGPT_项目全景手册.md`(过时) 与当前真入口 `CLAUDE.md`+`PROJECT_INDEX.md` 职责重叠，前者已被后者取代 | `PROJECT_INDEX.md:104-118` |
| 功能矩阵 vs 失败档部分重叠 | `refs/FEATURE_MATRIX.md` 的失败/状态列 与根 `FAILURE_LOG.md`、`HOOKMAP.md` 功能账有重叠面 | `refs/FEATURE_MATRIX.md:12-17` |

### 3. 断链（md 链接指向已不存在/已迁移路径）

| 源文件 | 失效链接 | 实情 | 证据 |
|------|------|------|------|
| `CHATGPT_项目全景手册.md`(必读表第7行) | `docs/HOOK_POINTS.md` | 已隔离至 `docs/archive/wechat_8066/HOOK_POINTS.md`(2026-05-27)；根 `docs/` 下无该文件(Glob docs 树仅 archive 版) | `docs/README.md:44`(迁移重定向说明)；`docs/archive/INDEX.md:14` |
| `CHATGPT_项目全景手册.md` | 「FAILURE_LOG F-01~F-31」 | 现已 F-42(计数过时,非路径断链但属过时引用) | `CLAUDE.md` FAILURE_LOG 至 F-42 |

> 注：`refs/SOURCE_MAP.md` 整表、`HOOK_MAP_8071_AUTHORITATIVE.md:16`、`isolation/INDEX_COMPETITOR.md:31` 指向 `I:/apk2/...` 等仓库外路径 = **外部只读引用**（不归档，仅统计）。文档线内出现的外部根路径引用主要 2 类：`I:/apk2/`（最多，refs/SOURCE_MAP 全表 + 权威资料来源）、`E:/apk_diff/`·`E:/ios-dylib/`·`I:/miyou-server/`（集中在 `PROJECT_INDEX.md` §四，主车道当前文件，非旧资料）。

---

## 八、给架构师的待裁决项（需拍板，盘点员不执行）

1. **`CHATGPT_项目全景手册.md`**：归档 `07_archive_归档/` 还是隔离 `docs/archive/`？是否先修断链(`docs/HOOK_POINTS.md`)+转 UTF-8 再封存？（建议：因其面向外部 AI 且严重过时，优先处置）
2. **`refs/` 整目录(10份)**：维持「refs 受控参考区(索引登记)」现状，还是物理并入 `docs/isolation/`？若移动，需**同步改 3 处入链**：`HOOK_MAP_8071_AUTHORITATIVE.md:12-16,181,319`、`docs/isolation/INDEX_COMPETITOR.md:20-23`、`PROJECT_INDEX.md:174-191`。
3. **`refs/FAILURE_LOG.md`**：与根 `FAILURE_LOG.md` 同名易混，是否重命名(如 `FAILURE_LOG_apk2旧.md`)后隔离？
4. **`JIDE_ANDROID_ACC_STRIKE_MAPPING_20260617.md`**：✅ 已归档 `07_archive_归档/`（2026-06-29，用户拍板）；根目录引用（ANTIBAN_MAP ×3 + 防封官 skill ×2）路径指针已更新，未断链。
5. **`docs/classmap/v8071.yaml` 内 2 条 `deprecated` 符号**(`fz2.e`/`booter.notification.x`)：保留考古 or 清理出字典？（脚本已不报缺失，影响小）

---

## 红线声明

- 本次仅产生本报告一个写操作，`docs/` / `refs/` / 根目录其余文件**零改动**。
- 所有处置建议止于「归档/隔离/保留/待裁决」，**未出现「删」**。
- 每条结论均附 file:line 证据；`CHATGPT_项目全景手册.md` 因 GBK 编码致 Read 工具失败，已用 PowerShell `Get-Content` 兜底实读其 head 取证。
