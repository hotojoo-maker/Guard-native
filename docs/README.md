# docs/ — 文档入口（微信 8.0.71 主车道）

> **当前唯一执行底座**：微信 **8.0.71**（D-014）  
> 历史版本（8.0.66 / 8.0.70）与早期规划 → 只通过 [`archive/INDEX.md`](./archive/INDEX.md) 查阅，**禁止直搬类名进代码**。  
> 竞品 Catfish → 只通过 [`isolation/INDEX_COMPETITOR.md`](./isolation/INDEX_COMPETITOR.md) 查阅，学行为不学类名。

---

## 默认阅读顺序（8071）

| 顺序 | 文件 | 用途 |
|:--:|------|------|
| 1 | [`GUARD_GATE_TRUTH.md`](./GUARD_GATE_TRUTH.md) | 门控 / 状态机 / 口令裁决 |
| 2 | [`HOOK_MAP_8071_AUTHORITATIVE.md`](./HOOK_MAP_8071_AUTHORITATIVE.md) | **8071 hook 点权威事实** |
| 3 | 根目录 [`HOOKMAP.md`](../HOOKMAP.md) + [`TASK_BOARD.md`](../TASK_BOARD.md) | 模块进度总图（注意文件头日期） |
| 4 | `03_execute_执行任务/P*/result.md` | 单任务装机实证 |
| 5 | [`CONV_REFRESH_PROBLEM.md`](./CONV_REFRESH_PROBLEM.md) | 会话 H↔V 热切问题单（H→V 仍 🟡） |

---

## 隔离区（非默认）

| 区 | 索引 | 内容 |
|----|------|------|
| **历史版本** | [`archive/INDEX.md`](./archive/INDEX.md) | 8066 类名表、HOOK_MAP_V1、HOOK_POINTS、T05/T07… |
| **竞品参考** | [`isolation/INDEX_COMPETITOR.md`](./isolation/INDEX_COMPETITOR.md) | Catfish 逆向 + `refs/*.java` |

---

## 审计

- [`DOC_AUDIT_2026-05-27.md`](./DOC_AUDIT_2026-05-27.md) — 隔离后全盘复检记录

## 根目录仍保留、不归档

- [`FAILURE_LOG.md`](../FAILURE_LOG.md) — 证伪铁律（含 8066 试错正文）
- [`FINDINGS.md`](../FINDINGS.md) — 发现流水

---

## 已迁移文件的短重定向

若书签仍指向 `docs/CLASS_MAP_8066.md` 等，同路径保留 **一行重定向** → `archive/wechat_8066/`。
