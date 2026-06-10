# P20 搜索拦截 — 装机状态快照

**最近更新**：2026-06-06 18:15（P20 搜索主线收口）
**总体状态**：✅ 主线完成（联系人 / 群聊密群 / 聊天记录关键词场景均已 L1）

> 本文件 = "最新装机现实"。详细历程见 [`worklog.md`](./worklog.md)。
> 任务范围见 [`brief.md`](./brief.md) / [`brief_S1.md`](./brief_S1.md)。

---

## 分源状态

| # | 子Tab | 状态 | 锁点路径（SearchFilter.java） | 最近装机 | 锚点 / 备注 |
|---|------|:----:|------|------|------|
| 1 | 联系人·个人 | ✅ | `f0.getView` afterHook + `lp.height=1` + setDivider(null) | 2026-05-27 v15.1 / 2026-06-02 v15.2 | `[SF:gv] blocked pos=N id=wxid_lzd2va16jd1622`；灰白块已收口 |
| 2 | 群聊 / 密群 | ✅ | `f0.getView` afterHook → `tz2.p0/s1` groupId | 2026-06-06 | `[SF:gv] blocked pos=1 id=44786160583@chatroom`；普通群 `45592178108@chatroom` 放行 |
| 3 | 聊天记录关键词场景 | ✅ | `z15.ef6/ch6.e` 分源出现；最终 `q2/f0.getView` 渲染层 `tz2.p0` id 折叠 | 2026-06-06 | `tools/p20_search_logcat_runner_20260606_180946.log`：同轮隐藏密群 + 密友 blocked，普通群放行 |
| 4 | 普通群 / 普通好友对照 | ✅ | 同主路径 | 2026-06-06 | 不在 hidden set 时不 blocked |

**附加说明**：P26C「群聊行包含密友名高亮」属于 UI 优化，不阻塞 P20 搜索主线。

---

## 当前装机锚点日志

```text
[SF:gv] hooking single-hook getView on com.tencent.mm.plugin.fts.ui.f0
[SF:gv] single-hook getView filter installed on com.tencent.mm.plugin.fts.ui.f0
[SF:lv] q2 ListView divider cleared
[SF:DUMP] z15.ef6 #1/#2/#3 fields:                         — 聊天记录分源出现 ✅
[SF:gv] blocked pos=1 id=44786160583@chatroom               — 密群 ✅
[SF:gv] blocked pos=3 id=wxid_lzd2va16jd1622                — 密友 ✅
row id=45592178108@chatroom without blocked                 — 普通群放行 ✅
```

证据文件：`tools/p20_search_logcat_runner_20260606_180946.log`。

---

## 未结子任务

| 子任务 | 目标 | 阻塞点 |
|------|------|------|
| **P26C** | 群聊行"包含:密友名"高亮 UI 优化 | 不阻塞 P20 主线 |

---

## 现行 hook 清单（不动名单 F-31）

- `SearchFilter.java` `f0.getView` afterHook — ✅ 主路径：联系人 / 群聊密群 / 聊天记录关键词场景
- `SearchFilter.java` `q2.j` beforeHook — ❌ F-32y 主路径废弃，仅保留 backstop
- `SearchFilter.java` `z15.ef6/ch6.e` dump — 理解层 / 诊断层，不作为主过滤路径

---

## 已废铁律（仅 P20 子集；归档在本文件 + HOOKMAP §8b-路径状态，**非** FAILURE_LOG.md）

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
