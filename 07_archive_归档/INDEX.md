# 07_archive_归档 — 已收口 P 任务归档索引

> 归档 = **move 不 delete**，全程 git 可回溯（见 `04_review_审稿复核/精简方案_提案_2026-06-10.md` 档 B）。
> 首次归档执行：**2026-06-10**（HOOKMAP 主线收口触发；底座微信 8.0.71 / D-014）。
> 看板/权威文档里指向这些任务的链接已统一改成 `07_archive_归档/<P号>/`。

---

## 本次归档（2026-06-10，21 个任务）

| P 号 | 主题 | 原状态 | 详情文件 |
|------|------|:--:|------|
| P15_脚手架 | 脚手架 | ✅ | result.md |
| P16_朋友圈Proto | 朋友圈 D1/D2/D3 Proto | ✅ | result.md |
| P17_会话LSPosed | 会话过滤 LSPosed | ✅ | result.md（已并入繁体重复目录） |
| P19_通讯录隐藏 | 通讯录 F07 | ✅ | result.md |
| P19B_ContactLabel | 通讯录【标签】成员隐藏 F07B | ✅ | result.md |
| P20_搜索拦截 | 搜索 + 密码入口 | ✅ | result.md |
| P21_MomentsRedDot | 朋友圈小红点（Layer0b + P21B） | ✅ | worklog.md + logs/ |
| P23_AntiRecall | 防撤回 C1（F08） | ✅ | brief.md |
| P25_B2触发器误触修复 | B2 触发器误触修复 | ✅ | worklog.md |
| P26_好友热切fresh触发 | 好友热切 fresh-warm | ✅ | result.md |
| P1A_本地加密原型 | 本地加密原型（native 早期） | 早期原型 | worklog.md |
| P1B_Registry抽取 | Registry 抽取（native 早期） | 早期原型 | worklog.md |
| P1C_Registry加密 | Registry 加密（native 早期） | 早期原型 | worklog.md |
| P1E_Filter读Registry | Filter 读 Registry | 早期原型 | worklog.md |
| P1F_十字防护整合设计 | 十字防护整合设计 | 设计稿 | DESIGN.md + worklog.md |
| P22_SearchCrawler | 搜索链路爬虫调研 | 调研（已并入 P20） | result.md |
| T09_Catfish_L0v3 | Catfish L0v3 互动红点探针 | 调研（授权已过期） | — |
| E2_FakeLocation | 伪装订位 | ✅ | result.md |
| P_CV1_通讯录V态热切 | 通讯录 V↔H 热切 | ✅ | worklog.md |
| P_ConvWarm | 会话回暖 | ✅ | result.md |
| P_IMPORT_密友密群导入 | 密友/密群导入（A2/A3） | ✅ | brief.md |

> **P17 重复目录合并**：原存在繁体 `P17_会話LSPosed` 与简体 `P17_会话LSPosed` 两个目录，已将繁体内容并入简体（繁体仅有的 `probe_contacts.js` 在简体中已存在，无唯一内容丢失）后删除繁体目录。

---

## 仍在 03_execute_执行任务/（KEEP，未归档）

进行中 / 待办 / 转 v1.1 / 未来版本，**不归档**：

`M6a_MomentsGroupIcon`（朋友圈分组图标，进行中）· `P18_离线采集`（⬜ 本轮跳过）· `P20B_BTriggers_SearchUnlock`（🟡 触发器）· `P20C_HVRecovery` · `P22_PushFilter`（🟡 转 v1.1）· `P26B_HideOwnMoments` · `P26C_HideSelectedLabels` · `V3T5_迁移探针`（v3）

---

## 回溯方法

- 整体回退：`git log --oneline` 找到归档提交，`git revert <hash>`。
- 单任务找回：`git mv 07_archive_归档/<P号> 03_execute_执行任务/<P号>`，或直接读 `07_archive_归档/<P号>/`。

---

## 探索期 hook 发现脚本归档（2026-06-29 · 探针归档官/窗口1）

> 来源：第一次开发期为找 8.0.71 hook 点引入的 Frida 探针 + 竞品逆向探针，**找点已用完**。按「移不删」搬入 `07_archive_归档/tools/`（TOOLS_INDEX §八既定路径），**普通文件移动、未跑 git**。现行工具（防封官 `dump_*` / `s6_*` / `elf_imports`、KPI `frida_kpi_probe.js`、`check_classmap.ps1`）**未动**，仍在 `tools/`。合计 **72 个文件**。

| 归档子目录 | 来源 | 数量 | 内容概览 |
|------|------|:--:|------|
| `07_archive_归档/tools/`（根） | `tools/` | 19 | `trace_*.js`（签名/包名/上报时序/红点/badge/wxid 探查 12）、`catfish_push_stack.js`、`trace_catfish_reddot.js`、`verify_maintabui_i.js`、`like_source_trace.js`、`run_probe.py`、`run_trace.py`、`tmp_trace_*` |
| `07_archive_归档/tools/dynamic_crawler_动态爬虫/` | `02_tools_工具/dynamic_crawler_动态爬虫/` | 37 | 动态 hook 点发现器整目录：`probe_*.js`、`cat_revoke_*.js`、`crawler_core.js`、`search_crawler.js`、`moments_visibility_crawler.js`、`conv_refresh_probe*.js`、`chk_a2.js` / `probe_a2.js`、`README.md` |
| `07_archive_归档/tools/quwei_miyou_probe/` | `tools/quwei_miyou_probe/` | 16 | 趣味/微密友竞品样本探针：`probe_quwei.*`、`probe_unread_*`、`patch_*`、`trace_*`、`QUWEI_MIYOU_PROBE.md` |

> 未搬（待定）：`tools/dump_normsg_event_probe_v2_B56.js`——任务点名搬，但经查仍是**防封官现行工具**（`guard-antiban` skill · `ANTIBAN_MAP.md:224` · `JIDE_...:1630` 在引用），按「有引用就留」原则暂留 `tools/`，待定夺。