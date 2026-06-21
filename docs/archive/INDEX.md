# archive/ — 历史资料总索引

> **不是当前底座。** 微信 **8.0.71** 实现只认 [`../HOOK_MAP_8071_AUTHORITATIVE.md`](../HOOK_MAP_8071_AUTHORITATIVE.md)。  
> 本目录仅供对照、排错、版本 diff；**禁止**把下表类名/方法名直接写进 LSPosed 代码。

---

## wechat_8066/ — 微信 8.0.66

| 文件 | 用途 | 对 8071 | 风险 |
|------|------|:------:|------|
| [`wechat_8066/CLASS_MAP_8066.md`](./wechat_8066/CLASS_MAP_8066.md) | 8066 混淆类速查 | ⚠️ 仅 diff | 直搬类名 |
| [`wechat_8066/HOOK_MAP_V1.md`](./wechat_8066/HOOK_MAP_V1.md) | v1 P0/P1 优先级规划（**DEPRECATED**） | ❌ 非类名表 | 误当 8071 hook 名单 |
| [`wechat_8066/HOOK_POINTS.md`](./wechat_8066/HOOK_POINTS.md) | 8066 Frida 验证 + 伪代码 | ⚠️ 思路参考 | 8066 类名已变 |
| [`wechat_8066/VERSION_CLASSMAP.md`](./wechat_8066/VERSION_CLASSMAP.md) | 跨版本字段对照 | ⚠️ diff | 勿当 8071 实证 |

### tasks/（文档员）

| 文件 | 说明 |
|------|------|
| [`tasks/T05_SnsObject_文档员.md`](./wechat_8066/tasks/T05_SnsObject_文档员.md) | T05 SnsObject 8066 定位 |
| [`tasks/T07_ContactStorage_文档员.md`](./wechat_8066/tasks/T07_ContactStorage_文档员.md) | T07 ContactStorage 8066 |

> 注：原「资料员副本」两份（T05/T07）为重复内容，2026-06-22 已删除。

---

## planning/ — 早期 P 任务调研

| 文件 | 版本 | 说明 |
|------|------|------|
| [`planning/P16_朋友圈Proto_research_8066.md`](./planning/P16_朋友圈Proto_research_8066.md) | 8.0.66 | P16 SnsObject 调研；8071 实现见 `P16_朋友圈Proto/result.md` |

---

## 开发期流程文档（2026-06-22 迁入隔离）

> 这几份是开发期的上手 / 流程 / 设计 / 研究文档（多为 8.0.66 时代或已被现行权威取代）；仅留作历史，**禁止**当当前流程依据。

| 文件 | 原位置 | 说明 |
|------|--------|------|
| [`AI_WORKFLOW_CHECKLIST.md`](./AI_WORKFLOW_CHECKLIST.md) | docs/ | 8066「F04 会话隐藏」工作流清单（DEPRECATED） |
| [`USER_AI_USAGE_GUIDE.md`](./USER_AI_USAGE_GUIDE.md) | docs/ | 非程序员 AI 协作指南（指向上面 8066 清单，DEPRECATED） |
| [`wechat_8066/RESEARCH_SUMMARY.md`](./wechat_8066/RESEARCH_SUMMARY.md) | docs/ | 8066 数据架构研究汇总（已被 8071 权威取代） |
| [`SKILL_WORKFLOW.md`](./SKILL_WORKFLOW.md) | docs/ | AI 角色工作流（已被 10 角色 skills + CLAUDE §十 取代） |
| [`SETTINGS_UI_V2.md`](./SETTINGS_UI_V2.md) | docs/ | 设置页 v2 历史设计（现行以 HOOK_MAP §9 / P_SE8 为准） |

---

## 二进制样本（仍在 06_refs）

| 路径 | 说明 |
|------|------|
| `06_refs_参考资料/apk_samples/wechat_8066.apk` | 可选对比样本，**非**当前装机底座 |
| `06_refs_参考资料/apk_samples/wechat_8066_jadx/` | jadx 产物 |

---

## 何时允许打开 archive

- 写「8071 vs 8066 字段差异」对照表
- 解释 FAILURE_LOG 里某条为何禁止 MvvmList.m() 等
- P18 离线采集做版本 diff

**其余时候**：关闭本目录，只读 8071 主车道（见 [`../README.md`](../README.md)）。

---

## 维护记录

| 日期 | 动作 |
|------|------|
| 2026-05-27 | 8066 迁入 `wechat_8066/`；复检见 [`../DOC_AUDIT_2026-05-27.md`](../DOC_AUDIT_2026-05-27.md) |
| 2026-06-22 | 减法整理：迁入开发期文档 5 份（`AI_WORKFLOW_CHECKLIST`/`USER_AI_USAGE_GUIDE`/`wechat_8066/RESEARCH_SUMMARY`/`SKILL_WORKFLOW`/`SETTINGS_UI_V2`）；删除死文件 4 份（T05/T07 资料员副本、`refs/CATFISH_8070_CLEANUP_PLAN`、`P18/logs/README`）；入链已改 `PROJECT_INDEX` §三 |
