# PROJECT_INDEX — 项目导航 + 路径表

> 所有路径在一处维护，SKILL.md 不写死路径，统一查本文件
> 更新时间：2026-06-10（文档收敛：FAILURE_LOG 至 F-38；补 PROTECTION_MAP；以 06-09 看板 + 代码为准）

---

## 一、根目录核心文档（13 份）

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
| [`TOOLS_INDEX.md`](./TOOLS_INDEX.md) | 工具 / 脚本索引 | review |
| [`FINDINGS.md`](./FINDINGS.md) | 发现即落盘 / 防压缩断链 | review |
| [`docs/DOC_AUDIT_2026-05-27.md`](./docs/DOC_AUDIT_2026-05-27.md) | 8071 隔离后文档审计报告 | review |

---

## 二、目录结构

```
guard_native/
├── 根目录 (12 份核心 md + 同步脚本)
├── .cursor/skills/       6 个角色 skill（主目录，日常编辑这里）
├── .claude/skills/       6 个角色 skill（镜像，sync_skills.ps1 同步）
├── 00_start_入口/        新会话第一站（PROMPT_TEMPLATES）
├── 01_dispatch_总调度/   CURRENT_PLAN / NEXT_STEP
├── 02_tools_工具/        dynamic_crawler 等
├── 03_execute_执行任务/  P15 / P16 / P17 / P18 等
├── 04_review_审稿复核/   每个 P 任务的审稿报告 + T_TASKS 调研池 + CONFLICTS
├── 05_reports_报告/      阶段报告 / TECH_SYNC_SUMMARY
├── 06_refs_参考资料/     wechat / catfish / frida / 离线采集快照
├── 07_archive_归档/      关闭 30 天的 P 任务搬这里
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
| [`./docs/RESEARCH_SUMMARY.md`](./docs/RESEARCH_SUMMARY.md) | 研究汇总 |

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
