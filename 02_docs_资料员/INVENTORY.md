# INVENTORY — 项目资产盘点

> 最后盘点: 2026-05-19
> 盘点人: doc-audit

---

## 一、根目录核心文档（10 份，全部存在）

| 文件 | 存在 | 备注 |
|------|:--:|------|
| CLAUDE.md | ✅ | 主入口 + 22 铁律 |
| AGENTS.md | ✅ | Cursor/Claude Code 兼容入口 |
| PROJECT_INDEX.md | ✅ | 路径表（待更新 8.0.66 APK 路径） |
| HOOKMAP.md | ✅ | 6 模块功能总图 |
| TASK_BOARD.md | ✅ | 4 窗口分工 |
| DECISION_LOG.md | ✅ | 重大决策履历 |
| RISK_REGISTER.md | ✅ | 风险表 |
| FAILURE_LOG.md | ✅ | 22 铁律永久归档 |
| TOOLS_INDEX.md | ✅ | 工具/脚本索引 |
| FINDINGS.md | ✅ | 发现即落盘（PROJECT_INDEX 漏列） |

## 二、Skills（10 份，双向镜像完整）

| 角色 | .cursor/skills/ | .claude/skills/ |
|------|:--:|:--:|
| guard-dispatch_总调度 | ✅ | ✅ |
| guard-doc-audit_资料员 | ✅ | ✅ |
| guard-execute-one_单任务执行 | ✅ | ✅ |
| guard-review_审稿复核 | ✅ | ✅ |
| guard-risk-check_风险复核 | ✅ | ✅ |

## 三、工作流目录

| 目录 | 状态 | 备注 |
|------|:--:|------|
| 00_start_入口/ | ✅ | README + PROMPT_TEMPLATES |
| 01_dispatch_总调度/ | ✅ | CURRENT_PLAN + NEXT_STEP |
| 02_docs_资料员/ | ✅ | README + T_TASKS/T05 + T07 |
| 03_execute_执行任务/ | ⚠️ | 仅 P15/P18，缺 P16/P17 |
| 04_review_审稿复核/ | ⚠️ | 仅 README，缺 RETRACTED_CONCLUSIONS.md |
| 05_reports_报告/ | ⚠️ | 仅 README |
| 06_refs_参考资料/ | ⚠️ | 仅 README，无实际参考资料 |
| 07_archive_归档/ | ⚠️ | 仅 README |
| 08_release_发布/ | ⚠️ | 仅 README |

## 四、源码（src/）

| 文件 | 行数 | 状态 |
|------|:--:|:--:|
| AndroidManifest.xml | 45 | ✅ |
| assets/xposed_init | 1 | ✅ |
| assets/debug/index.html | 221 | ✅ |
| ModuleMain.java | 106 | ✅ F-16 进程白名单 |
| core/AppConfig.java | 79 | ✅ DEV/PROD/HONEY 三态 |
| core/Bridge.java | 71 | ✅ g_a7f2 namespace |
| core/StateMachine.java | 179 | ✅ 3态 + MMKV + 事件总线 |
| core/InterceptCounter.java | 115 | 🔴 第73行编译错误 |
| debug/StatusNotification.java | 124 | ✅ |
| debug/OverlayWindow.java | 214 | ✅ |
| debug/DebugServer.java | 281 | ⚠️ unlock 不验证密码 |
| debug/ContactResolver.java | 96 | ⚠️ 类名 L4 未验证 |
| res/values/arrays.xml | 6 | ✅ scope = com.tencent.mm |

## 五、docs/（7 份，完整）

| 文件 | 存在 |
|------|:--:|
| AI_WORKFLOW_CHECKLIST.md | ✅ |
| CLASS_MAP_8066.md | ✅ |
| HOOK_MAP_V1.md | ✅ |
| HOOK_POINTERS.md | ✅ |
| HOOK_POINTS.md | ✅ |
| RESEARCH_SUMMARY.md | ✅ |
| USER_AI_USAGE_GUIDE.md | ✅ |

## 六、refs/（11 份，完整）

| 文件 | 存在 |
|------|:--:|
| MainEntry.java | ✅ |
| UserControll.java | ✅ |
| VipPreference.java | ✅ |
| ReflectHelper.java | ✅ |
| filter_moments.js | ✅ |
| CATFISH_8070_CLEANUP_PLAN.md | ✅ |
| CATFISH_8070_INDEX.md | ✅ |
| FAILURE_LOG.md | ✅ |
| FEATURE_MATRIX.md | ✅ |
| SOURCE_MAP.md | ✅ |
| VERSION_8065_ANALYSIS.md | ✅ |

## 七、缺失/待补

| 优先级 | 项目 | 说明 |
|:--:|------|------|
| 🔴 | build.gradle | 项目无法编译 |
| 🔴 | InterceptCounter:73 编译错误 | `mF04Count` 等字段不存在 |
| 🟡 | INVENTORY.md | 本文件，本次新建 |
| 🟡 | CONFLICTS.md | 暂无冲突需要仲裁 |
| 🟡 | PROJECT_INDEX 缺 8.0.66 APK 路径 | 外部资料表需更新 |
| 🟡 | PROJECT_INDEX 缺 FINDINGS.md | 核心文档表漏列 |
| 🟡 | 04_review/RETRACTED_CONCLUSIONS.md | 暂无撤回结论 |
| ⬜ | 06_refs_参考资料/ 空 | 待从外部迁移参考资料 |
