# 竞品归柜 · 搬运交付 — 2026-06-29

> 角色：竞品归柜官（Guard Native 重构·并行窗2）
> 动作：把竞品/旧版本参考料集中进 `docs/isolation/`（竞品柜）。**move 非 delete，未跑 git。**
> 红线遵守：只动 `refs/`、`docs/isolation/`、`07_archive_归档/T09_*`；**未碰**根目录 .md 正文 / `docs/archive/`；下方「待改链接清单」**不自改**，交持有方。

---

## ① 已搬清单（11 项 → `docs/isolation/`）

| # | 原路径 | 新路径 | 备注 |
|:--:|------|------|------|
| 1 | `refs/MainEntry.java` | `docs/isolation/MainEntry.java` | Catfish 入口 989 行 |
| 2 | `refs/UserControll.java` | `docs/isolation/UserControll.java` | Catfish 业务 944 行 |
| 3 | `refs/VipPreference.java` | `docs/isolation/VipPreference.java` | MMKV 封装参考 |
| 4 | `refs/ReflectHelper.java` | `docs/isolation/ReflectHelper.java` | 反射工具参考 |
| 5 | `refs/filter_moments.js` | `docs/isolation/filter_moments.js` | 朋友圈 Frida v21 |
| 6 | `refs/CATFISH_8070_INDEX.md` | `docs/isolation/CATFISH_8070_INDEX.md` | Catfish 8070 索引 |
| 7 | `refs/FEATURE_MATRIX.md` | `docs/isolation/FEATURE_MATRIX.md` | 功能矩阵 8066/8070 |
| 8 | `refs/SOURCE_MAP.md` | `docs/isolation/SOURCE_MAP.md` | 源路径映射（含外部 I:/apk2） |
| 9 | `refs/VERSION_8065_ANALYSIS.md` | `docs/isolation/VERSION_8065_ANALYSIS.md` | 8.0.65 静态分析 |
| 10 | `refs/FAILURE_LOG.md` | `docs/isolation/FAILURE_LOG_catfish.md` | **改名**（避免与根 `FAILURE_LOG.md` 混；apk2 竞品副本） |
| 11 | `07_archive_归档/T09_Catfish_L0v3/`（brief.md / PITFALLS.md / result.md / t09_probe.js / t09_output.log / t09_output.txt，共 6 文件） | `docs/isolation/T09_Catfish_L0v3/` | 纯 Catfish 竞品（授权已过期） |

> 搬后 `refs/` 已空。竞品总索引 = `docs/isolation/INDEX_COMPETITOR.md`（已同步更新，指向以上新位置）。

---

## ② 待改链接清单（不自改，交持有方修订）

> 以下 .md 仍指向旧 `refs/...` 路径；搬后均断链。统一改向：`refs/<file>` → `docs/isolation/<file>`，其中 `refs/FAILURE_LOG.md` → `docs/isolation/FAILURE_LOG_catfish.md`。
> 相对路径写法按各文件所在层级换算（如根目录用 `docs/isolation/...`，docs/ 内用 `isolation/...`）。

### A. 根目录 .md（红线：本窗未碰正文）

| 文件 | 行 | 旧引用 → 新目标 |
|------|----|----------------|
| `CLAUDE.md` | 336 / 337 / 338 | `./refs/MainEntry.java` / `UserControll.java` / `filter_moments.js` → `./docs/isolation/...` |
| `PROJECT_INDEX.md` | 174 / 175 / 176 / 177 / 183 / 189 / 190 / 191 / 301 | `./refs/{MainEntry,UserControll,VipPreference,ReflectHelper,filter_moments,VERSION_8065_ANALYSIS,CATFISH_8070_INDEX,SOURCE_MAP}` → `./docs/isolation/...` |
| `TOOLS_INDEX.md` | 52 | `./refs/filter_moments.js` → `./docs/isolation/filter_moments.js` |
| `FAILURE_LOG.md` | 4 / 11 | `./refs/FAILURE_LOG.md` → `./docs/isolation/FAILURE_LOG_catfish.md` |

### B. docs/（非 archive）

| 文件 | 行 | 旧引用 → 新目标 |
|------|----|----------------|
| `docs/HOOK_MAP_8071_AUTHORITATIVE.md` | 15 / 319 | `refs/FEATURE_MATRIX.md` → `isolation/FEATURE_MATRIX.md` |
| `docs/DOC_AUDIT_2026-05-27.md` | 39 | `refs/VERSION_8065_ANALYSIS.md` → `isolation/VERSION_8065_ANALYSIS.md` |

### C. 00_start_入口 / skills

| 文件 | 行 | 旧引用 → 新目标 |
|------|----|----------------|
| `00_start_入口/PROMPT_TEMPLATES_提示词模板.md` | 128 | `refs/MainEntry.java` → `docs/isolation/MainEntry.java` |
| `.cursor/skills/guard-execute-one_单任务执行/SKILL.md` | 90 | `./refs/MainEntry.java` `./refs/UserControll.java` → `docs/isolation/...` |
| `.agents/skills/guard-execute-one_单任务执行/SKILL.md` | 90 | 同上（镜像，sync_skills.ps1 同步） |

### D. 07_archive_归档/（被 .cursorignore，Shell 补搜；红线：本窗未碰）

| 文件 | 行 | 旧引用 → 新目标 |
|------|----|----------------|
| `07_archive_归档/P19_通讯录隐藏/brief.md` | 87 / 88 | `refs/MainEntry.java` / `refs/UserControll.java` → `docs/isolation/...` |
| `07_archive_归档/P20_搜索拦截/brief.md` | 98 | `../../refs/MainEntry.java` → `../../docs/isolation/MainEntry.java` |
| `07_archive_归档/P21_MomentsRedDot/worklog.md` | 40 | `refs/MainEntry.java` → `docs/isolation/MainEntry.java` |
| `07_archive_归档/review_snapshots/W1_20260519_交接快照.md` | 47 | `refs/filter_moments.js` → `docs/isolation/filter_moments.js` |
| `07_archive_归档/review_snapshots/W2_20260519_交接快照.md` | 67 | `refs/filter_moments.js` → `docs/isolation/filter_moments.js` |

### E. docs/archive/（红线：**不动**，仅登记备查）

> 本窗按红线**未碰** `docs/archive/`；以下引用是否修订由架构师定（archive 为历史隔离区，可按"历史原样保留"放行）。

| 文件 | 行 | 旧引用 |
|------|----|--------|
| `docs/archive/USER_AI_USAGE_GUIDE.md` | 21 / 31 / 47 / 95 / 179 / 182 / 247 / 274 / 275 | `refs/FAILURE_LOG.md`、`refs/FEATURE_MATRIX.md` |
| `docs/archive/AI_WORKFLOW_CHECKLIST.md` | 226 / 242 / 338 / 341 / 347 | `refs/FAILURE_LOG.md`、`refs/FEATURE_MATRIX.md` |
| `docs/archive/wechat_8066/HOOK_POINTS.md` | 250 / 275 | `refs/FAILURE_LOG.md`、`refs/filter_moments.js`（注：275 行另引 `refs/filter_conv.js` = 本次清单外、refs 内不存在该文件） |
| `docs/archive/wechat_8066/RESEARCH_SUMMARY.md` | 188 | `refs/FAILURE_LOG.md` |
| `docs/archive/wechat_8066/tasks/T05_SnsObject_文档员.md` | 13 / 14 | `./refs/MainEntry.java`、`./refs/VERSION_8065_ANALYSIS.md` |

---

## ③ 备注 / 待核

1. **`SOURCE_MAP.md` 内部互引**：原在 `refs/` 时其表内若以 `refs/<file>` 或相对名互指其它 refs 文件，搬后可能断；本窗按「只搬不改正文」**未改其内容**，请持有方搬后复核（代理A 报告记其主要指向外部 `I:/apk2`）。
2. **`refs/filter_conv.js`**：`docs/archive/wechat_8066/HOOK_POINTS.md:275` 提到该文件，但本次 refs/ 10 份清单**不含**它，搬前 refs/ 也无此文件（疑早已不存在）→ 标待核，未处置。
3. **`refs/` 目录现已空**：是否保留空目录占位 or 由架构师决定后续处理（本窗未删目录本身，仅搬走文件）。
4. 本交付文档与竞品总索引同处 `docs/isolation/`，均为本窗合规写入范围。
