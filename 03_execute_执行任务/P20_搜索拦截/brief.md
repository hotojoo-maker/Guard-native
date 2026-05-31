# P20 — 搜索（全局 FTS + 密码入口）

> **产品总闸** → 必读 [`docs/PRODUCT_GATE.md`](../../docs/PRODUCT_GATE.md)  
> 底座：微信 8.0.71 / 小米9 / LSPosed

---

## 任务范围（两件事，禁止混）

| # | 功能 | 模块 | 状态 |
|---|------|------|:----:|
| A | 隐藏态下全局搜索藏密友 / 密群 / 聊天记录 | `SearchFilter` + P0 hook | 🟡 联系人已通；群聊/聊天记录未完成 |
| B | 全局搜索框密码入口（显形） | `SearchUnlock` | 🟡 已装，待装机验收 |

---

## A — 全局搜索藏人（≠ 密码）

### UI

```text
主界面 → 右上角放大镜 → FTS 全局搜索页
```

### 门控（与 P16/P17/P19 相同）

```text
LicenseGate.isOk() && StateMachine.isActive() && Bridge.getWxids() 非空
```

### 期望行为（隐藏态）

| 用户操作 | 期望 |
|----------|------|
| 搜密友昵称 | 无此人、无「添加好友」真实资料 / 显示未添加态 |
| 搜与密友聊天内容关键词 | 无「聊天记录」命中 |
| 搜密群名称 / groupId 相关关键词 | 无密群结果 |
| 乱输 abc | 仅正常空结果，**不**改变状态机 |

### 当前分源进度（2026-05-27 晚装机后）

| 来源 | 状态 | 下一步 |
|------|:--:|------|
| 联系人·个人 | ✅ v15.1 装机实证（凌晨） | 回归即可 |
| 联系人·群聊 | ⚠️ v15.1 实证后今晚装机回退 | P20-S1：探针 A 路查回退根因（A/B/C 三选一） |
| 联系人·标签 | ❌ 零接入 | P20-S3：jadx 查标签 row 类后再补 extract 分支 |
| 最近联系 | ⬜ 未单独验 | P20-S4：定点装机验证是否被 `f0.getView` 顺带拦 |
| 聊天记录·个人 | ✅ 装机 2026-05-27 晚（UIN→wxid 一行补丁生效） | 补锚点日志；空白条问题 P20-S2 一并解 |
| 全形态空白条 | ⚠️ 全形态过滤后均留空白条 | P20-S2：路径② 源头 List 清洗根治 |

> 当前装机实情见 [`result.md`](./result.md)；详细历程见 [`worklog.md`](./worklog.md)。
> 禁止把"联系人搜索已拦截"写成"搜索整体完成"。全局搜索是聚合过滤器，必须按来源分别验收。

### 实现线（8.0.71）

| 优先级 | 点 | 说明 |
|:--:|-----|------|
| P0 | `f0.getView` afterHook | 联系人结果已实证；群聊需补 groupId 实证 |
| P0 | `hookSearchContact(String)` 等价 | 联系人存在性 → 假「未找到」（待 Frida 定类名） |
| P1 | 聊天记录 FTS / `z15.ef6` | talker 未解析，单独开 P20-S2 |
| 禁止 | 监测搜索框输入内容来藏人 | 那是密码线，见 B |

### 验收 log

```text
[SF:gv] blocked pos=N id=wxid_xxx      (联系人已实证)
[SF:gv] blocked pos=N id=xxx@chatroom  (群聊待实证)
```

---

## B — 密码入口（= 入口，不藏人）

| 项 | 值 |
|----|-----|
| 路径 | 同上 FTS 全局搜索 EditText |
| 默认密码 | `111111` |
| 触发 | 输满 6 位，无回车 |
| 条件 | **仅** `State.HIDDEN` |
| 成功 | 清空框 → `attemptUnlock` → `VISIBLE` → `finish` 搜索页 |
| 乱输 | 无状态变化 |

代码：`src/main/java/com/ghost/assist/moduleB/SearchUnlock.java`

### 验收

1. 隐藏态输满 `111111` → 自动回主界面，密友在列表/通讯录可见  
2. 隐藏态输 `11111` / 乱码 → 仍隐藏  
3. 显形态在聊天框打 `111111` → 不触发（若仍触发需收窄到 FTS Activity）

---

## 必读

- [`docs/PRODUCT_GATE.md`](../../docs/PRODUCT_GATE.md)
- [`docs/archive/wechat_8066/HOOK_MAP_V1.md`](../../docs/archive/wechat_8066/HOOK_MAP_V1.md) § P0-2 / P0-3（历史规划）
- [`refs/MainEntry.java`](../../refs/MainEntry.java) `hookSearchContact` / `hookFts`
- [`FAILURE_LOG.md`](../../FAILURE_LOG.md) F-25（Throwable）

---

## 产出

```
03_execute_执行任务/P20_搜索拦截/
├── brief.md          ← 本文件
├── result.md         ← 装机后填
└── scripts/          ← Frida 定 8071 注入点（待补）
```

---

## KPI

P20 关单前跑 `frida_stats.js`，见 F-22。
