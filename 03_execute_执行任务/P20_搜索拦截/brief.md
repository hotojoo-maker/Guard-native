# P20 — 搜索（全局 FTS + 密码入口）

> **产品总闸** → 必读 [`docs/PRODUCT_GATE.md`](../../docs/PRODUCT_GATE.md)  
> 底座：微信 8.0.71 / 小米9 / LSPosed

---

## 任务范围（两件事，禁止混）

| # | 功能 | 模块 | 状态 |
|---|------|------|:----:|
| A | 隐藏态下全局搜索藏密友 + 聊天记录 | `SearchFilter` + P0 hook | 🟡 |
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
| 乱输 abc | 仅正常空结果，**不**改变状态机 |

### 实现线（8.0.71）

| 优先级 | 点 | 说明 |
|:--:|-----|------|
| P0 | `ArrayList.addAll` + `z15.ef6` | `SearchFilter` 已 hook，按 wxid 浅层扫描 remove |
| P0 | `hookSearchContact(String)` 等价 | 联系人存在性 → 假「未找到」（待 Frida 定类名） |
| P1 | `hookFts` / View 行隐藏 | Catfish 思路，8071 字段待 dump |
| 禁止 | 监测搜索框输入内容来藏人 | 那是密码线，见 B |

### 验收 log

```text
[SF:addAll] z15.ef6 seen=N removed=M   (M>0 且为密友)
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
- [`docs/HOOK_MAP_V1.md`](../../docs/HOOK_MAP_V1.md) § P0-2 / P0-3
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
