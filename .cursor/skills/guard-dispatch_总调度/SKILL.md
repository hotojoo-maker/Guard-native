---
name: guard-dispatch_总调度
description: Guard Native 总调度——制定 P 任务计划/分配 4 窗口/接手新会话/更新 TASK_BOARD。新会话第一件事就是用这个 skill；分配任务时也用这个 skill。
---

# guard-dispatch — 总调度

---

## ⛔ 绝对禁止 / ABSOLUTE PROHIBITIONS

> **这一节优先级高于本 skill 所有其他内容。**
> **This section overrides everything else in this skill.**

| 中文 | English |
|------|---------|
| **禁止猜测** | No guessing |
| **禁止推断**（无 L1/L2 证据） | No inference without L1/L2 evidence |
| **没有证据 → 停下来，主动问用户** | No evidence → STOP, ask the user |
| **没有资料/日志 → 停下来，主动问用户** | No materials/logs → STOP, ask the user |
| **⬜ 状态只读名字，禁止展开** | ⬜ items: name only, never expand |
| **文档状态矛盾 → 停下来报告，问用户仲裁** | Doc conflict → STOP, report, ask user to resolve |
| **不确定 = 不分配任务，先问清楚** | Uncertain = do not assign; clarify first |
| **未经用户明确同意，禁止改任何 md 文档** | No md edits without explicit user approval |
| **用户口述/截图/抓包片段 ≠ 已验证，禁止直接写入文档** | User paste ≠ verified; do not write to docs |

**接手新会话时必须做的 3 件事（缺一不做）：**
1. 读 `TASK_BOARD.md` §一，只看 ✅ 和有装机日志的条目当作"已完成"
2. 读 `HOOKMAP.md`，⬜ 行只读名字，🟡 行只看有 L1 日志的部分
3. 发现任何 ✅ 但无日志证据的条目 → **立刻标出，问用户确认，不继续推进**

---

## 适用场景

| 场景 | 触发关键词 |
|------|----------|
| 新会话接手 | "接手"/"开始"/"继续" |
| 分配任务 | "下一步做什么"/"分配窗口" |
| 收尾交接 | "今天进度"/"写交接" |

---

## 工作流（5 步）

### 1. 接手三步铁律
1. 读 `CLAUDE.md`
2. 读 `HOOKMAP.md`
3. 读 `TASK_BOARD.md`

### 2. 识别会话角色
- 看 `TASK_BOARD.md` §一表中"占用至"列空的窗口 → 推荐用户进入
- 用户没说窗口 → 默认进 W1 主开发

### 3. 领取 P 任务
- 修改 `TASK_BOARD.md` §一对应行的"占用至"，填会话 ID + 截止时间
- 在 `01_dispatch_总调度/CURRENT_PLAN.md` 写 1-2 行当前计划

### 4. 任务过程
- 切到 `guard-execute-one_单任务执行` skill 干活
- 每完成一个子任务更新 `03_execute_执行任务/P<N>/result.md`

### 5. 收尾
- 跑 `frida_stats.js` 对比基线（F-22 铁律）
- **文档写入门控**：改 `HOOKMAP.md` / `TASK_BOARD.md` / `worklog.md` / `result.md` / `FAILURE_LOG.md` 前，**必须先向用户展示拟写入内容，等明确同意后再改**
- 更新 `HOOKMAP.md` 对应行状态 ⬜→🟡 或 🟡→✅（仅用户同意后）
- 在 `04_review_审稿复核/W<N>_<日期>.md` 写 5-10 行交接快照（仅用户同意后）
- 释放 `TASK_BOARD.md` §一占用

---

## 输出格式

```
【总调度报告】

接手状态: 新会话 | 续接 P<N>
当前窗口: W<N> (主题: xxx)
P 任务: P<N>_xxx
预计耗时: X 天

下一步:
1. ...
2. ...
3. ...

风险提示: (如有)
```

---

## Cursor ↔ Claude Code 分工协议

**分工**：
- **Cursor**（本 agent）= 写/改代码、文档归档、方案设计
- **Claude Code**（终端）= adb / frida / build / logcat 命令执行

**Cursor 给 Claude Code 下指令必须用可复制块**：

每个步骤用 **"一行说明 + 一个 code block"** 格式，不能把解释文字混进 code block：

```markdown
**Step N — 干什么**

[命令]

**Step N+1 — 干什么**

[命令]
```

实际示例（正确）：

```markdown
**Step 1 — 装机**

adb install -r "build/outputs/apk/debug/guard-native-debug.apk"

**Step 2 — 强停微信**

adb shell am force-stop com.tencent.mm

**Step 3 — 查日志**

adb logcat -d 2>&1 | findstr "NCL"
```

**禁止**：
- ❌ 用散文描述"你可以让 Claude Code 跑一下 xxx"（用户不知道该复制什么）
- ❌ 在代码块里混入解释文字（让用户不知道哪段是命令）
- ❌ 把文件改动指令发给 Claude Code（Claude Code 不改代码，改代码是 Cursor 的活）
- ❌ 在命令前加 "告诉 Claude Code：" 等前缀（用户看到就知道是命令，不需要引导语）

---

## 铁律

- 同时只在一个窗口写代码，避免根目录看板冲突
- 任何分歧 → 不自决，先问用户
- 跨窗口共享数据 → 经过 `06_refs_参考资料/`
- 失败教训 → 必须写进 `FAILURE_LOG.md`

---

## 文档写入门控（强制）

**以下文件，默认只读；用户说「写入文档」「更新 HOOKMAP」「归档」等明确指令前，禁止改：**
- 根目录：`HOOKMAP.md` `TASK_BOARD.md` `FAILURE_LOG.md` `CLAUDE.md`
- P 任务：`03_execute_执行任务/**/worklog.md` `result.md` `brief.md`
- 审稿：`04_review_审稿复核/**`

**正确流程：**
1. 在回复里先给出「拟写入草稿」（表格/段落）
2. 问用户：「确认写入 [文件名] 吗？」
3. 用户明确同意后，才执行文件修改

**禁止：**
- ❌ 用户聊天里提到一条信息 → 顺手写进 worklog
- ❌ 用户口述 iOS 抓包/竞品结论 → 标成 L1 实证写入
- ❌ 跨版本推断（如 8.0.66 的 F-27 套到 8.0.71）写入文档

---

## 反模式

- ❌ 不读 CLAUDE.md 就接手
- ❌ **未经用户同意改 md 文档**
- ❌ 不更新 TASK_BOARD 就开干
- ❌ 跑多窗口都改根目录 md
- ❌ 关任务不跑 frida_stats.js
- ❌ 测试失败只分析不写诊断脚本 — **必须产出可执行命令给用户**
- ❌ 给说明书不给提示词 — **用户要的是 "Run this:" 不是 "You should check X"**
- ❌ 不换窗口时反复 invoke skill — **同一窗口连续干活直接用，别每次问要不要切 agent**

## 何时切窗口 vs 不切

| 场景 | 决策 | 原因 |
|------|:--:|------|
| 单次设备命令（跑 Frida/adb） | ❌ 不切 | 用户敲完贴回来，中间 agent 没增益 |
| 当前上下文 < 5 轮 | ❌ 不切 | 切换成本 > 收益 |
| 同一 P 任务的连续调试 | ❌ 不切 | 上下文连贯最重要 |
| 全新 P 任务（不同模块） | ✅ 切 | 独立上下文，并行不冲突 |
| 上下文已 50+ 轮膨胀 | ✅ 切 | 新 agent 轻装上阵 |

切窗口时必须主动说出目标 skill 全名，让用户可复制 invoke：
- 写代码 → `/guard-execute-one_单任务执行`
- 跑设备 → `/guard-terminal_终端操作`
- 审稿 / 发布门控 / 资料盘点 → `/guard-review_质检门控`（三合一）
