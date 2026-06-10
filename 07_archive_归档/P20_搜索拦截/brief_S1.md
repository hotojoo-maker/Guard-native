# P20-S1 群聊搜索拦截 — brief

> 执行窗口直接读这个，不要并入主 brief（避免 P20 主 brief 继续膨胀）

## 当前状态

✅ **已收口**（2026-06-06，P20 主线已完成）。
时间线：
- 2026-05-27 凌晨 — v15.1 装机实证 ✅（`[SF:q2] blocked id=44786160583@chatroom`，见 `bug排查/final_v15_1_s1_groupchat.log`）。
- 2026-05-27 晚 — 同 ID `44786160583@chatroom`（名"测试密群"）装机不被隐藏 ❌。SF hook 仍在编译产物里，但当前未命中或未触发拦截。
- 2026-06-06 — `tools/p20_search_logcat_runner_20260606_180946.log` 实证：同轮 `z15.ef6` 聊天记录分源出现，最终 `q2/f0.getView` 渲染层 `tz2.p0` 行中，隐藏密群 `44786160583@chatroom` 和密友 `wxid_lzd2va16jd1622` 被 `[SF:gv] blocked`，普通群 `45592178108@chatroom` 放行。
- **结论**：P20-S1 已关闭；群聊/密群搜索并入 P20 主线收口。

## 任务范围（铁律）

| 在范围 | 不在范围 |
|------|------|
| HIDDEN 态搜索结果中的**密群条目**拦截（昵称命中后弹出"群聊"section 含密群 row）| 聊天记录 z15.ef6 chat-record FTS（已由 P20 主线渲染层收口，不在本 S1 扩写） |
| 用 `Bridge.allHiddenIds()` 中已存的 groupId（`*@chatroom`）匹配 | 群聊行"包含: 密友名"高亮（→ P26C 单独任务） |
| | 最近联系 / 联系人搜索 / SearchUnlock / StateMachine / AuthManager / PushFilter |

## 已证实路径

| 层 | 假设 | 来源 |
|----|------|------|
| 渲染 | 与联系人同 adapter `q2`，同 `f0.getView` declared，复用 v15.1 主路径 | 2026-06-06 L1 |
| 数据项 | `tz2.p0` / `tz2.s1` 等搜索结果 item | 2026-06-06 L1 |
| groupId 字段 | item id = "xxxxxxxxxxxxxxxx@chatroom" | 2026-06-06 L1 |
| Hidden 命中 | `Bridge.allHiddenIds()` 返回 `wxids ∪ groupIds`（A3 装机 2026-05-21 ✅） | `Bridge.java` allHiddenIds 实现 |

## 现有代码兜底（复用 v15.1）

`SearchFilter.java` v15.1 主路径已自带两层兜底：

1. **精确提取**：`extractQ2BindWxid(g)` 中 `g.a == 2` 分支 → `tz2.s1.s` 直接返回 groupId
2. **模糊兜底**：`scanWxidLiteral` 递归扫所有字段（深度 ≤ 2），识别 `wxid_` / `@chatroom` / `@app` 字面值

即便 `tz2.s1` 类名假设错或 `g.a` 字段值变，只要群聊 item 内某处存在 `*@chatroom` 字符串，模糊兜底也能命中。

## 验证步骤（历史存档，不再作为待办）

### Step 1 — 端口转发（终端）

```powershell
adb forward tcp:8080 tcp:8080
```

### Step 2 — 加密群 groupId（user 浏览器）

打开 `http://localhost:8080` → "加入隐藏"输入框 → 输入测试密群 groupId（格式 `<16位16进制>@chatroom`）→ 点"+ 加入隐藏" → 确认 page 显示 `[H]` 标识

### Step 3 — 切 H 态搜该密群名（user 手机）

- H 态 → 主页放大镜 → 输入该密群名（任一群成员都不输入，输群本身的群名/昵称）
- 等结果出 4-6 秒
- 截图

### Step 4 — 抓 log（终端）

```powershell
cd c:\Users\Me\Desktop\guard_native
adb logcat -d -s NCL:* | Select-String "\[SF:gv\] blocked|@chatroom" | Out-File -Encoding utf8 bug排查\final_v15_1_s1_groupchat.log
Get-Content bug排查\final_v15_1_s1_groupchat.log
```

## 判定矩阵

| 装机结果 | 处置 |
|------|------|
| `[SF:gv] blocked id=*@chatroom` ×N | P20-S1 群聊搜索 ✅，已完成 |
| `[SF:gv] blocked` 出现但 id 不是 `@chatroom` 结尾 | 提取链命中错（密群 item 内某 wxid 误抽）；分析 dataItem.class 决定是改 extractQ2BindWxid 还是 scanWxidLiteral |
| `[SF:gv] blocked` 完全 0 触发 | 群聊条目走另一 adapter / RecyclerView；临时打开 `[SF:q2] data item dump` 探针看 g.class.getName() → jadx 反编译查 8.0.71 群聊真实结构 |
| 命中但密群 row 仍可见 | GONE 视觉没生效；可能 group row 的 `lp` 不是 MarginLayoutParams，需补 padding 归零 |

## 相关 F-xx（只看这些）

| 编号 | 一句话 |
|------|------|
| F-32x | `ss4.p` 永久废弃，不要试 RecyclerView 路径 |
| F-32y | `q2.j` 装上 0 触发，不要把它当主入口 |
| F-32z | 5-hook offset 永久废弃，不要为消 row gap 改 getCount setResult |
| F-31 | 不动已装机验证通过的 hook 点（v15.1 联系人主路径不动） |

## 关键文件

- 代码主路径：`src/main/java/com/ghost/assist/moduleB/SearchFilter.java` v15.1 `[SF:gv]` 段
- 已实证日志：`bug排查/final_v15_*.log`
- DebugServer API：`POST /api/hidden body={"action":"add","wxid":"<groupId>"}`
- Web UI：`src/main/assets/debug/index.html` "加入隐藏"输入框
- 历史 groupId 参考（如果 user 不记得测试密群 ID 可从此找）：`bug排查/final_v10*.log` 或 `runtime_*.log` 内 `*@chatroom` 字面

## 禁止事项（hard rules 复述）

- ❌ 不碰聊天记录 z15.ef6
- ❌ 不碰 SearchUnlock / StateMachine / AuthManager / PushFilter
- ❌ 不因没命中就全量隐藏群聊分区
- ❌ 不把 UIN 当 groupId
- ❌ 不在 SearchFilter 调 enterHidden / exitHidden / beginUnlock / attemptUnlock
- ❌ 不未经用户同意改 md 文档（除本 brief 落盘）

## 完成判据（已满足）

| # | 完成项 | 装机日志锚点 |
|:-:|------|------|
| 1 | `[SF:gv] blocked id=<groupId>@chatroom` ×N 出现 | `final_v15_1_s1_groupchat.log` |
| 2 | HIDDEN 态搜密群名时，密群 row 视觉消失 | 用户截图 |
| 3 | VISIBLE 态搜密群名时，密群 row 恢复显示 | 用户截图（对照组） |
| 4 | 普通群（不在 hidden set）不被误隐藏 | 用户截图（对照组） |

4 项已满足，P20-S1 关单。

---

**起头时间**：2026-05-27 06:35
**预计耗时**：1 装机轮回（如复用 v15.1 路径直接命中）至 1 调研 + 1 装机（如需新探针）
