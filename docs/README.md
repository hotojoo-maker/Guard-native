# docs/ — 文档入口（微信 8.0.71 主车道）

> **当前唯一执行底座**：微信 **8.0.71**（D-014）  
> 历史版本（8.0.66 / 8.0.70）与早期规划 → 只通过 [`archive/INDEX.md`](./archive/INDEX.md) 查阅，**禁止直搬类名进代码**。  
> 竞品 Catfish → 只通过 [`isolation/INDEX_COMPETITOR.md`](./isolation/INDEX_COMPETITOR.md) 查阅，学行为不学类名。

---

## 8071 车道内容（接手总序以 `CLAUDE.md §一` 为准，本表只列本车道特有文件）

> `_CORE_现状真源/` + `HOOKMAP` + `TASK_BOARD` + P result 已在 `CLAUDE.md §一` 接手顺序里，本表不再复列（G10 防多套序打架）。

| 文件 | 用途 |
|------|------|
| [`GUARD_GATE_TRUTH.md`](./GUARD_GATE_TRUTH.md) | 门控 / 状态机 / 口令裁决 |
| [`HOOK_MAP_8071_AUTHORITATIVE.md`](./HOOK_MAP_8071_AUTHORITATIVE.md) | **8071 hook 点权威事实** |
| [`CONV_REFRESH_PROBLEM.md`](./CONV_REFRESH_PROBLEM.md) | 会话 H↔V 热切问题单（H→V 仍 🟡） |
| [`HONEYPOT_蜜罐设计.md`](./HONEYPOT_蜜罐设计.md) | 蜜罐诱饵 + 触发逻辑 + 域名加密引导段 C2（汇总，权威在 `../PROTECTION_MAP.md`） |

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
