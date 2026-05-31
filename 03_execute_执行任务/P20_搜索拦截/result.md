# P20 搜索拦截 — 装机状态快照

**最近更新**：2026-05-27 19:00（会话 Guard Native130）
**总体状态**：🟡 部分完成（2 ✅ / 1 ⚠️ / 2 ⬜❌）

> 本文件 = "最新装机现实"。详细历程见 [`worklog.md`](./worklog.md)。
> 任务范围见 [`brief.md`](./brief.md) / [`brief_S1.md`](./brief_S1.md)。

---

## 五子形态状态

| # | 子Tab | 状态 | 锁点路径（SearchFilter.java） | 最近装机 | 锚点 / 备注 |
|---|------|:----:|------|------|------|
| 1 | 联系人·个人 | ✅ | `:548-625` `f0.getView` afterHook + setDivider(null) | 2026-05-27 凌晨 v15.1 | `[SF:gv] blocked pos=N id=wxid_lzd2va16jd1622` ×14 |
| 2 | 聊天记录·个人 | ✅ | `:1004-1046` `extractFz2Wxid` c==3 → `Bridge.getWxidByUin(g)` | 2026-05-27 晚 | UIN→wxid 一行补丁生效，待补 `[SF:fz2c3]` 锚点日志 |
| 3 | 联系人·群聊 | ⚠️ 回退 | `:468-537` `q2.j` beforeHook `g.a==2` → `tz2.s1.s` | v15.1 ✅ → 2026-05-27 晚 ❌ | 同 ID `44786160583@chatroom` 今晚装机不被隐藏，根因待探针 |
| 4 | 最近联系 | ⬜ | 推测复用 `f0.getView` | 未单独装机 | — |
| 5 | 联系人·标签 | ❌ | 无代码 | — | 待 jadx 查标签 row 类 |

**附加问题**：⚠️ **UI 空白条**（`View.GONE + lp.height=0` 后遗症）— 全形态过滤后都留空白条。下一步走"路径② 源头 List 清洗"根治，需 q2 适配器探针前置。

---

## 当前装机锚点日志

```text
[SF:gv] hooking single-hook getView on com.tencent.mm.plugin.fts.ui.f0
[SF:gv] single-hook getView filter installed on com.tencent.mm.plugin.fts.ui.f0
[SF:lv] q2 ListView divider cleared
[SF:gv] blocked pos=1 id=wxid_lzd2va16jd1622  (×14)        — 个人 ✅
```

聊天记录与群聊的最新装机锚点待补（见 `bug排查/final_v22*.log` 系列）。

---

## 未结子任务

| 子任务 | 目标 | 阻塞点 |
|------|------|------|
| **P20-S1** | 查群聊 hook 回退根因 | 等"探针 A 路"装机产 logcat |
| **P20-S2** | 路径② 源头 List 清洗根治空白条 | 等 q2 适配器 declared fields dump |
| **P20-S3** | 标签 row 接入 | 需 jadx 查标签 row 类 |
| **P20-S4** | 最近联系 单独装机验证 | 一次定点装机 + log |

---

## 现行 hook 清单（不动名单 F-31）

- `SearchFilter.java:548-625` `f0.getView` afterHook — ✅ v15.1 实证
- `SearchFilter.java:468-537` `q2.j` beforeHook — 历史 ✅，今晚回退，**仍不动**（先查回退根因）
- `SearchFilter.java:1004-1046` `extractFz2Wxid` — c==3 走 `Bridge.getWxidByUin()`，**今晚刚生效**

---

## 已废铁律（仅 P20 子集，详 FAILURE_LOG.md）

- **F-32x** `ss4.p.onBindViewHolder` 永久废弃（父级 GONE 不上屏）
- **F-32y** `q2.j` 主路径废弃（装上 0 触发；今晚回退另有根因）— 保留为 backstop
- **F-32z** 5-hook offset（`getCount setResult(orig-skip)`）永久废弃 — 凌晨那轮 ANR 实证

---

## B — 密码入口（SearchUnlock）

| 项 | 状态 |
|---|---|
| 装机日期 | 2026-05-22 ✅ |
| 默认密码 | `111111` |
| 触发 | 隐藏态全局搜索 EditText 输满 6 位 |
| 行为 | 清空框 → `attemptUnlock` → `VISIBLE` → `finish` 搜索页 |
| 待验收 | 三场对照（隐藏满 6 / 隐藏 5 位 / 显形态打 `111111`） |

---

## KPI

P20 关单前跑 `frida_stats.js`（详 F-22）。今晚未跑。

---

## 关联文档

- 任务书：[`brief.md`](./brief.md) / [`brief_S1.md`](./brief_S1.md)（群聊专项）
- 工作日志：[`worklog.md`](./worklog.md)
- 历史里程碑：
  - [`04_review_审稿复核/2026-05-27_搜索框过滤主路径v15.1锁定.md`](../../04_review_审稿复核/2026-05-27_搜索框过滤主路径v15.1锁定.md)
  - [`04_review_审稿复核/2026-05-27_搜索路径锁定+全栈修复.md`](../../04_review_审稿复核/2026-05-27_搜索路径锁定+全栈修复.md)
- 产品口径：[`docs/PRODUCT_GATE.md`](../../docs/PRODUCT_GATE.md)
