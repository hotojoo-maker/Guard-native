# PROJECT_INDEX — 项目导航 + 路径表

> 所有路径在一处维护，SKILL.md 不写死路径，统一查本文件
> 更新时间：2026-06-10（文档收敛：FAILURE_LOG 至 F-38；补 PROTECTION_MAP；以 06-09 看板 + 代码为准）
> 2026-06-10 增补 §零 功能总清单（编号 ↔ 任务 ↔ 状态 ↔ 文件夹）作为账实唯一权威，治理"看板与目录对不上"。

---

## 零、功能总清单（编号 ↔ 任务 ↔ 状态 ↔ 文件夹）·账实唯一权威

> 给人看的一页纸：哪个功能、做没做完、代码/资料在哪个文件夹。各处看板（TASK_BOARD / HOOKMAP / CURRENT_PLAN）若与本表冲突，**以本表为准**，并回头修看板。
> 状态口径：✅=已装机验证 · 🟡=代码已写未完全验证 · ⬜=没做/计划/暂存。

### ✅ 已完成（能用，已装机验证）

| 功能 | 编号 | 文件夹 |
|------|------|--------|
| 朋友圈隐藏密友帖 / 点赞 / 评论 | D1/D2/D3（P16） | 代码 `src/.../moduleD`；归档 `07_archive_归档/P16_朋友圈Proto` |
| 会话列表隐藏密友 / 密群 | P17 + A3 | `07_archive_归档/P17_会话LSPosed` |
| 通讯录隐藏密友 + 标签泄漏修复 | P19 / P19B | `07_archive_归档/P19_通讯录隐藏`、`P19B_ContactLabel` |
| 搜索拦截 + 密码 111111 解锁 | P20 | `07_archive_归档/P20_搜索拦截` |
| 朋友圈小红点归零 | P21 | `07_archive_归档/P21_MomentsRedDot` |
| 防撤回 | P23（C1） | `07_archive_归档/P23_AntiRecall` |
| 密友 / 密群批量导入 | P_IMPORT（A2/A3） | `07_archive_归档/P_IMPORT_密友密群导入` |
| 来电拦截 + 通知拦截 + 未读计数过滤 | P22 / P_PF2 / P_NF4 | `03_execute_执行任务/P22_PushFilter` |
| 伪装定位（全局伪造位置） | E2 | `07_archive_归档/E2_FakeLocation` |
| 会话热切 V↔H（部分场景） | P_CV1 / P_ConvWarm / P26（热切版） | `07_archive_归档/P_CV1…`、`P_ConvWarm`、`P26_会话场景freshwarm` |
| B 触发：摇一摇 / 切后台 / 锁屏 / 搜索解锁 | P20B（B1/B2/B5/B6） | `07_archive_归档/P20B_BTriggers_SearchUnlock` |
| 朋友圈"可见分组"图标隐藏 | M6a | `07_archive_归档/M6a_MomentsGroupIcon`；时间线/详情页 `pt GONE` L1（2026-06-10），个人相册页=Flutter 无原生 pt、v1 不做。详见 `HOOKMAP.md` M6a 行 / 权威 §6a |

### 🟡 进行中 / 部分（别当已完成）

| 功能 | 编号 | 文件夹 / 说明 |
|------|------|--------|
| native 加密 / 授权真锁链 | P_NC1 + S3a/S4/S3b | P1A~P1E 已有 `BATCH1/PHASE1A~1E PASS`；S3a `android_8071` 已 `prod_server_lock` + server seed 解 registry；S4 Ed25519、S3b LeaseClock 已装机 PASS。仍待删 Filter fallback / V3 发行线对齐 / RiskState 真降级。详见 `03_execute_执行任务/S3a0_ServerSeed设计/result.md` 与 `PROTECTION_MAP.md` §10.6 |
| 普通消息通知 + 铃声完整体验 | P22 的 P_NF1/P_NF2/P_NF3 | 转 v1.1；`03_execute_执行任务/P22_PushFilter` |

### ⬜ 没做 / 计划 / 暂存

| 功能 | 编号 | 文件夹 / 说明 |
|------|------|--------|
| 本机隐藏自有朋友圈（独家） | P26B | `03_execute_执行任务/P26B_HideOwnMoments`（待实现） |
| 隐藏指定通讯录标签（独家） | P26C | `03_execute_执行任务/P26C_HideSelectedLabels`（待实现） |
| 离线采集 + KPI 基线 | P18 | `03_execute_执行任务/P18_离线采集`（本轮跳过） |
| 改包共存版·迁移聊天记录调研 | V3T5 | `03_execute_执行任务/V3T5_迁移探针`（静态报告已出） |
| 后续计划：类名 seed 化 · classmap 加密 · v2 设置页 UI · 授权 LicenseGate · miyou-server 接入 · v3 改包(LSPatch) | 见 `TASK_BOARD.md` §六 | — |

> 注：原 `P20C_HVRecovery` 为无文档的临时工作区（内含 500MB+ APK 样本，git 未跟踪），2026-06-10 已清理删除。

### 🛡️ 防封 / 加固 / 授权线（2026-06-22 补登，先前未登记于本表）

> 这三项不是产品「功能」，是防封 / 防破解 / 加固工作，先前只散在各自 DESIGN/result + 防封官 / 安全官 skill 里，未进「账实唯一权威」。本次补登，治理「做了没录账」。

| 任务 | 编号 | 状态 | 文件夹 / 说明 |
|------|------|------|--------|
| 统一风控引擎（防封授权闸 × A2 三轴 × 蜜罐 / 引流 / 延迟弹窗 canary）| P_AntiBanGate | ⬜ 设计 only（未动代码，待用户拍板 + 装机回归）| `03_execute_执行任务/P_AntiBanGate_防封授权闸/`（DESIGN + PLAN；归属 网络安全官 + 防封官）|
| normsg 三轴身份上报实证（封号根因 = k33 包名 / k49 数据路径 / k18 签名 MD5 明文上报）| P_ANTIBAN_B36 | ✅ 研究结论 L1（frida / tcpdump 实抓 2026-06-18）| `07_archive_归档/P_ANTIBAN_B36_normsg三轴/result.md`（防封官研究产物；命脉真源在外部防封线）|
| 重放绑定 ReplayBind（SO envelope 摘要折入 key 派生，堵 W 静态明文缝）| P_RB1 | ⬜ 未开工（仅排期，P1 加固项）| `03_execute_执行任务/P_RB1_重放绑定_ReplayBind/DESIGN.md`（来源 PROTECTION_MAP §10.7）|

### ⚠️ 四个重复编号（同号两义，看清单别被绕晕）

| 重复号 | ① 已完成 / 归档（不改名） | ② 计划 / 另一义（还没做或活跃用此义） |
|--------|------------------------|------------------------|
| **P22** | SearchCrawler 搜索链路调研（`07_archive_归档/P22_SearchCrawler`，research.md + result.md：`fz2.e c=3` UIN→wxid 映射 / 聊天记录内联行过滤） | PushFilter 通知 / 来电 / 未读策略层（`03_execute_执行任务/P22_PushFilter`，活跃·主用此义） |
| **P25** | B2 触发器误触发 V→H 修复 | 类名 / 字符串 seed 化本地生成流程 |
| **P26** | 好友 V 态热切 fresh-item | v2 设置页 UI 优化（注入微信设置） |
| **P26C** | 隐藏指定通讯录标签（文件夹实体） | 搜索高亮（看板旧写法） |

> 处理原则：**已完成的归档目录一律不改名**（防止链接断）；以后提"计划那一件"时**用名字、不要用号**，避免再撞。

---

## 一、根目录核心文档（14 份）

> doc-audit / 资料员 / 文档员 三个角色已于 2026-05-27 合并入 **guard-review_质检门控** 的"资料功能"档。

| 文件 | 作用 | 谁维护 |
|------|------|--------|
| [`CLAUDE.md`](./CLAUDE.md) | 主入口 / 29 条铁律 / 无分歧共识 | 所有人 |
| [`docs/PRODUCT_GATE.md`](./docs/PRODUCT_GATE.md) | **产品总闸**：状态机 / VIP 授权 / 密码入口 | dispatch |
| [`AGENTS.md`](./AGENTS.md) | Cursor / Claude Code 兼容入口 | — |
| [`PROJECT_INDEX.md`](./PROJECT_INDEX.md) | 本文件 / 路径表 / 导航 | review |
| [`HOOKMAP.md`](./HOOKMAP.md) | 6 模块功能总图 / 状态看板 | review |
| [`TASK_BOARD.md`](./TASK_BOARD.md) | 4 窗口分工 / P 任务进度 | dispatch |
| [`DECISION_LOG.md`](./DECISION_LOG.md) | 重大决策履历 | dispatch |
| [`RISK_REGISTER.md`](./RISK_REGISTER.md) | 风险表 | risk-check |
| [`FAILURE_LOG.md`](./FAILURE_LOG.md) | F-01~F-38 失败方案档案（F-38 最新：伪装订位坐标候选证伪）| review |
| [`PROTECTION_MAP.md`](./PROTECTION_MAP.md) | **上线前防破解总账 / 四阶段路线图 / 发版门控** | security |
| [`ANTIBAN_MAP.md`](./ANTIBAN_MAP.md) | **防封官权威账**：反检测 / 防封号最新水位线、证据等级、阻塞项 | antiban |
| [`TOOLS_INDEX.md`](./TOOLS_INDEX.md) | 工具 / 脚本索引 | review |
| [`FINDINGS.md`](./FINDINGS.md) | 发现即落盘 / 防压缩断链 | review |
| [`docs/DOC_AUDIT_2026-05-27.md`](./docs/DOC_AUDIT_2026-05-27.md) | 8071 隔离后文档审计报告 | review |

---

## 二、目录结构

```
guard_native/
├── 根目录 (核心 md + 同步脚本)
├── .cursor/skills/       8 个角色 skill（核心4：总调度/执行/质检/终端 + 专项4：授权检查官/授权门控别名/网络安全官/git保姆；主目录，日常编辑这里）
├── .claude/skills/       8 个角色 skill（镜像，sync_skills.ps1 同步；以主目录大写 SKILL.md 为准）
├── 00_start_入口/        新会话第一站（PROMPT_TEMPLATES）
├── 01_dispatch_总调度/   CURRENT_PLAN / NEXT_STEP
├── 02_tools_工具/        dynamic_crawler 等
├── 03_execute_执行任务/  P15 / P16 / P17 / P18 等
├── 04_review_审稿复核/   每个 P 任务的审稿报告 + T_TASKS 调研池 + CONFLICTS
├── 05_reports_报告/      阶段报告 / TECH_SYNC_SUMMARY
├── 06_refs_参考资料/     wechat / catfish / frida / 离线采集快照
├── 07_archive_归档/      已收口 P 任务归档（2026-06-10 首次归档 21 个，见 `07_archive_归档/INDEX.md`）
├── 08_release_发布/      蜜罐 seed / 签名 / APK 输出
├── docs/                 8071 主车道 README + GUARD_GATE_TRUTH + HOOK_MAP_8071_AUTHORITATIVE
│   │                     + CONV_REFRESH_PROBLEM + PRODUCT_GATE + P22_PushFilter_VoIP …
│   ├── archive/          8066/历史规划（见 archive/INDEX.md，含 HOOK_MAP_V1 / HOOK_POINTS / CLASS_MAP_8066 / T05/T07）
│   └── isolation/        竞品 Catfish 索引
├── native_core/          libguardcore.so 模块地图（API / ARCHITECTURE / MAP / ROADMAP / RULES）
├── refs/                 (现有, 不动) MainEntry / UserControll / filter_moments.js
└── src/                  代码主目录 (W1~W3 产出，com.ghost.assist.*)
```

> **已删除目录（2026-05-27）**：`02_docs_文档员/` + `02_docs_资料员/` → 角色合并入 `guard-review_质检门控`。T05/T07 历史定义已存档至 `docs/archive/wechat_8066/tasks/`。

---

## 三、内部资料速查（高频访问）

### Hook 点参考（8071 主车道）

| 文件 | 内容 |
|------|------|
| [`./docs/README.md`](./docs/README.md) | **文档入口**：8071 默认读序 + 隔离区说明 |
| [`./docs/HOOK_MAP_8071_AUTHORITATIVE.md`](./docs/HOOK_MAP_8071_AUTHORITATIVE.md) | **8.0.71 hook 权威事实** |
| [`./docs/GUARD_GATE_TRUTH.md`](./docs/GUARD_GATE_TRUTH.md) | 门控 / 状态机裁决 |
| [`./docs/CONV_REFRESH_PROBLEM.md`](./docs/CONV_REFRESH_PROBLEM.md) | 会话 H↔V 热切问题单 |
| [`./docs/archive/wechat_8066/RESEARCH_SUMMARY.md`](./docs/archive/wechat_8066/RESEARCH_SUMMARY.md) | 研究汇总（8066 旧账，2026-06-22 已归档隔离） |

### 历史 / 竞品（非默认）

| 索引 | 内容 |
|------|------|
| [`./docs/archive/INDEX.md`](./docs/archive/INDEX.md) | 8066 类名表、HOOK_MAP_V1、HOOK_POINTS… |
| [`./docs/isolation/INDEX_COMPETITOR.md`](./docs/isolation/INDEX_COMPETITOR.md) | Catfish 行为参考 |

### Catfish 参考代码

| 文件 | 内容 |
|------|------|
| [`./refs/MainEntry.java`](./refs/MainEntry.java) | Catfish 入口 989 行（40+ hook 方法）|
| [`./refs/UserControll.java`](./refs/UserControll.java) | Catfish 业务 944 行（状态机 / 搜索 / 摇一摇 / 通知伪装）|
| [`./refs/VipPreference.java`](./refs/VipPreference.java) | MMKV 封装参考 |
| [`./refs/ReflectHelper.java`](./refs/ReflectHelper.java) | 反射工具参考 |

### Frida 脚本

| 文件 | 内容 |
|------|------|
| [`./refs/filter_moments.js`](./refs/filter_moments.js) | 朋友圈过滤 Frida v21（已验证）|

### 现有 refs（保留）

| 文件 | 内容 |
|------|------|
| [`./refs/VERSION_8065_ANALYSIS.md`](./refs/VERSION_8065_ANALYSIS.md) | 8.0.65 静态分析 + 跨版本字段差异 |
| [`./refs/CATFISH_8070_INDEX.md`](./refs/CATFISH_8070_INDEX.md) | Catfish 8070 索引 |
| [`./refs/SOURCE_MAP.md`](./refs/SOURCE_MAP.md) | 源码映射 |

---

## 四、外部资料路径（绝对路径，**只读不复制**）

### 防封研究独立线（命脉单一真源 · 2026-06-21）

```
C:\Users\Me\Desktop\防封_反检测线\
├── 防封权威账_2026年6月.md   ★防封研究结论(命脉/三轴/算法)唯一真源 §0–§十七
├── 工作清单.md / 工具箱_SKILLS.md / 索引.md
├── 证据/   原始实捕快照(只读)
├── 脚本/   A2SignatureSpoof.java / official_der.hex / ssaid_calc.py / so_identity_scan.js …
└── logs/
```

> 主线 `ANTIBAN_MAP.md` 已降为**落地账**；防封**研究结论**一律以本线权威账为准（单一数据源，2026-06-21 收口）。

### apk2 项目（D 线封号研究 / 历史版本研究）

```
I:/apk2/
├── _3__D_wechat_ban/official_wechat_ban_research/03_anti_frida/
│   ├── TECH_SYNC_SUMMARY.md             4 版本×3 场景全对照
│   ├── logs/OFFICIAL_DENSITY_COMPARISON_66_68_70.md
│   ├── frida_stats.js                   16 指标采集工具
│   └── COLLECTION_SOP.md                采集标准流程
├── _4__samples/
│   ├── sample_history_research/
│   │   ├── VERSION_INDEX.md             7 版本权威索引
│   │   ├── ANTI_DETECTION_DEEP_DIVE.md   防检测深度拆解
│   │   ├── 防检测_双线对比分析.md         重型/轻量线对比
│   │   └── 甜密友_8061_差分结果.md
│   └── dynamic_fast/
│       ├── HOOK_IMPLEMENTATION_ANALYSIS.md  Catfish 全 hook 源码级
│       └── classes17_decompiled/jadx_out/   反编译产物
├── _1__B_rewrite/05_docs/
│   ├── CURRENT_MAINLINE_STATUS.md       主线总表（apk2 老项目）
│   └── CONCLUSION_CHANGELOG.md          结论修正履历
├── QE66_RESUME.md                       P0~P14 七阶段全量水位线
└── _archive_A_smali/network_compare/
    └── NETWORK_COMPARE_SUMMARY.md        官方/密友/封号失败三版对照
```

### 授权服务器

```
I:/miyou-server/
├── CLAUDE.md             双链路架构 + HMAC 签名公告
├── USAGE.md              客户端接入示例
└── server.py             Python 3.12 + SQLite
```

### 官方版本 APK（基准/对比）

| 版本 | 路径 | 大小 |
|------|------|------|
| 8.0.38 | `I:/apk2/_3__D_wechat_ban/official_wechat_ban_research/apks/微信8.0.38-2023-6-28.apk` | 263MB |
| 8.0.51 | `同上目录/微信8.0.51-2024-9-27.apk` | 276MB |
| 8.0.56 | `同上目录/微信8.0.56-2025-1-23.apk` | 261MB |
| 8.0.60 | `同上目录/微信8.0.60-2025-6-10.apk` | 258MB |
| 8.0.63 | `同上目录/微信8.0.63-2025-9-12.apk` | 256MB |
| **8.0.66** | **`同上目录/官方原版8.0.66-2025-12-06.apk`** | **255MB** |
| 8.0.68 | `同上目录/官方原版8.0.68-2026-1-68.apk` | 255MB |
| 8.0.70 | `I:/apk2/_1__B_rewrite/00_original_apk/微信8.0.70官方原版.apk` | 244MB |

> 基准目录: `I:/apk2/_3__D_wechat_ban/official_wechat_ban_research/apks/`
> 每个版本有 `{版本}_benchmark/` 子目录存放 SO 提取产物 + Ghidra 输出

### 密友/改版样本（E:/apk_diff/）

```
E:/apk_diff/
├── 香蕉密友/                   8040 早期
├── Source_Suspect疑似原作者/   8024 微密友
├── 微密友市场3月版本/          8061 OEM 市场版
├── 微密友63/                   8063 三件套首集齐
├── 甜蜜友-版本重点研究价值/    8061 轻量线 (Catfish 同源)
├── 衍生蜘蛛密友版本/           Flutter 引擎版
└── 主样本 → I:/apk2/微密友8070官替稳定版-原版.apk
```

### iOS 参考

```
E:/ios-dylib/shitou-miyou-core/   iOS 蜘蛛密友 dylib（字段名零混淆，可反推 Android）
D:/appleapp/                     iOS 独立研究分支
```

### 构建产物（不在仓库内）

```
I:/apk2_build/
├── official/                   官方版反编译 / 归档
└── modified/                   改包/试错 smali
```

---

## 五、AI 接手快速定位表

| 你的问题 | 先读 |
|---------|------|
| 项目当前在做什么 | [`CLAUDE.md`](./CLAUDE.md) + [`TASK_BOARD.md`](./TASK_BOARD.md) |
| 某功能怎么实现 | [`HOOKMAP.md`](./HOOKMAP.md) 找对应模块 → 详细资料链接 |
| 8071 某 hook 点怎么写 | [`./docs/HOOK_MAP_8071_AUTHORITATIVE.md`](./docs/HOOK_MAP_8071_AUTHORITATIVE.md) |
| 8066 历史类名（仅 diff） | [`./docs/archive/INDEX.md`](./docs/archive/INDEX.md) |
| Catfish 行为参考 | [`./docs/isolation/INDEX_COMPETITOR.md`](./docs/isolation/INDEX_COMPETITOR.md) |
| 我能不能做 X | [`FAILURE_LOG.md`](./FAILURE_LOG.md) F-01~F-38 档案 + CLAUDE.md §三 29 条战略铁律 先查 |
| Catfish 怎么做的 | [`./refs/MainEntry.java`](./refs/MainEntry.java) + [`UserControll.java`](./refs/UserControll.java) |
| 历史版本对比 | 外部 `apk2/_4__samples/sample_history_research/VERSION_INDEX.md` |
| 防封号边界 | 外部 `apk2/QE66_RESUME.md` + [`CLAUDE.md`](./CLAUDE.md) §六 KPI |
| 授权服务器 | 外部 `I:/miyou-server/CLAUDE.md` |
| 工具/脚本在哪 | [`TOOLS_INDEX.md`](./TOOLS_INDEX.md) |

---

## 六、路径维护规则

1. 路径更新只改本文件，其他 md/SKILL.md 都引用本文件
2. 新增外部资料时在 §四 加一行
3. 内部新增目录在 §二 加节
4. AI 接手发现路径错误 → 改本文件 + 在 `04_review_审稿复核/CONFLICTS.md` 记一笔（guard-review 资料功能）
