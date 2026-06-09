# P20 搜索拦截 — 工作日志

> 多智能体串行接手的"会议纪要"。
> 顶部 = 五子形态状态表（最新会话收尾时重写）。
> 下方 = 按时间倒序的 session 块（append-only）。
> 接手 P20 时只需读：顶表 + 最近 1–2 个 session 块。

---

## 五子形态状态表（截至 2026-05-27 19:00）

| # | 子Tab | 状态 | 锁点路径 | 最近一次装机 | 锚点日志 |
|---|------|:----:|---------|------|------|
| 1 | 联系人·个人 | ✅ | `SearchFilter.java:548-625` `f0.getView` afterHook → GONE+setDivider(null) | 2026-05-27 凌晨 v15.1 | `[SF:gv] blocked pos=N id=wxid_lzd2va16jd1622` ×14 |
| 2 | 聊天记录·个人 | ✅ | `SearchFilter.java:1004-1046` `extractFz2Wxid` c==3 改读 `Bridge.getWxidByUin()` | 2026-05-27 晚 | 用户口述"个人聊天记录能过滤"（具体锚点待装机日志补） |
| 3 | 联系人·群聊 | ⚠️ 回退 | `SearchFilter.java:468-537` `q2.j` beforeHook `g.a==2` → `tz2.s1.s` | v15.1 ✅ → 2026-05-27 晚 ❌ | v15.1 期：`[SF:q2] blocked id=44786160583@chatroom`；今晚同 ID 不被隐藏 |
| 4 | 最近联系 | ⬜ 未单独验 | 推测复用 `f0.getView` | 未单独装机 | — |
| 5 | 联系人·标签 | ❌ 零接入 | 无代码 | — | — |
| 附 | UI 空白条 | ⚠️ 全形态都有 | `View.GONE + lp.height=0` 后遗症 | 全晚多次目击 | 截图存 wuxianChat 130 会话 |

**未结子任务（2026-06-06 收口后更新）**：

| 子任务 | 目标 | 阻塞点 |
|------|------|------|
| P20-S1 | 群聊 / 密群搜索 | ✅ 已由 `q2/f0.getView` 主路径收口 |
| P20-S2 | 聊天记录关键词场景 + 空白条 | ✅ 已由最终渲染层 id 过滤 + `lp.height=1` 收口 |
| P20-S3 | 标签 row 接入 | 归后续 UI/边缘专项，不阻塞 P20 主线 |
| P20-S4 | 最近联系 单独装机验证 | 归后续回归项，不阻塞 P20 主线 |

证据：`tools/p20_search_logcat_runner_20260606_180946.log`（隐藏密群/密友 blocked，普通群放行）。

**铁律提醒（仅 P20 子集）**：

- F-31：不动已装机验证 hook（含 `f0.getView` / 历史 `q2.j`）。
- F-32x：`ss4.p.onBindViewHolder` 永久废弃。
- F-32y：`q2.j` 历史装上 0 触发；保留 backstop 不动。
- F-32z：5-hook offset（`getCount setResult(orig-skip)` + 4 个位置翻译）永久废弃 → 干扰 q2 data swap，造成结果区空白 + ANR。**今晚再被想起一次：路径①已死，不要复活。**

---

## 2026-05-27 18:13 会话 Guard Native130（Opus 4.7，总调度）

### 背景

接手上一会话（Guard Native127→129）。129 末尾停在"群聊不过滤"原因诊断、推荐"路径② 源头 List 清洗"。

### 用户报告（今晚装机现象）

1. ✅ 个人聊天记录能过滤（说明 UIN→wxid 那一行补丁在 `SearchFilter.java:1008` 已生效）。
2. ✅ 个人联系能过滤。
3. ❌ 群聊未过滤 — 测试 ID `44786160583@chatroom`（名"测试密群"）。
4. ⚠️ 所有过滤后均出现空白条。

截图证据：搜索词 `测试`，FTS Activity = `com.tencent.mm.plugin.fts.ui.FTSMainUI`，群聊 section 中"测试密群"row 完好显示。

### 决策树推演

```text
群聊不过滤根因三选一：
  A. SF 类 hook 未加载 → 日志会零 [SF:q2] / [SF:gv]
  B. 当前 group ID 未在 hidden → 用户已确认在 hidden
  C. g.a 值不是 2 → 走错分支
路径② 前置：需 q2 适配器 declared fields + notifyDataSetChanged 调用栈
```

→ 拍板"A 路探针"（用户选 `a`）。已生成完整探针提示词（含阶段 0 环境验、阶段 1 logcat 抓现场、阶段 2 frida q2 introspection、阶段 3 代码交叉），格式直接可粘给终端操作员。

### 文档收敛动作（本会话）

1. **新建** `P20/worklog.md`（本文件）。
2. **重写** `P20/result.md` 为"当前装机状态快照"，统一以五子形态表口径。
3. **微调** `P20/brief.md` 顶部"当前分源进度"表，与今晚现实对齐。
4. **微调** `P20/brief_S1.md` 顶部"当前状态"，标记群聊回退。

未动代码、未动 hook、未装机。

### 遗制（next 会话先做这两件）

1. **探针 A 路尚未发车**。提示词已成稿，但用户两个前置问题未答：
   - 终端探针由谁主驾（本会话切角色 / 另开 Cursor 终端会话）？
   - frida 环境是否就绪（frida-server / Gadget / xposed bridge）？
2. **bug排查/ 已 79 文件、其中 49 个 final_v* log**：到点需归档清理一轮，保留 `v15_1*` 与 `v22*` 即可，其他塞进 archive/。

### 当前装机原始日志参考路径

- `bug排查/final_v15_1_s1_groupchat.log`（v15.1 群聊曾经 ✅ 的实证）
- `bug排查/final_v22_full_hot_switch.log` / `final_v22_group_test.log`（最新装机系列，含群聊未拦现象）

### 探针 A 路 完整提示词（备查）

> 用户拍板走"路径② 前置探针"。提示词已生成，未发车。粘给终端操作员即可执行。提示词全文存于会话 130 wuxianChat 历史，本处不复制（保持本文件简洁）。要点：阶段 0 环境验 + 阶段 1 logcat 三关键词扫 + 阶段 2 frida `q2` declared fields & methods dump + 阶段 3 代码交叉。

### 升级 F-xx 的候选？

暂无。"群聊回退"还在 现象 阶段，未到根因。"空白条 跨形态出现"虽明确但根因明（GONE+height=0 后遗症），且解药也明（路径② 源头清洗），暂不抽 F-xx。
