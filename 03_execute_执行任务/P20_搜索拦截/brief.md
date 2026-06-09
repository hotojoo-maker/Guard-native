# P20 — 搜索（全局 FTS + 密码入口）

> **产品总闸** → 必读 [`docs/PRODUCT_GATE.md`](../../docs/PRODUCT_GATE.md)  
> 底座：微信 8.0.71 / 小米9 / LSPosed

---

## 任务范围（两件事，禁止混）

| # | 功能 | 模块 | 状态 |
|---|------|------|:----:|
| A | 隐藏态下全局搜索藏密友 / 密群 / 聊天记录 | `SearchFilter` + P0 hook | ✅ P20 主线已收口（联系人 / 群聊密群 / 聊天记录关键词场景） |
| B | 全局搜索框密码入口（显形） | `SearchUnlock` | ✅ 8071 装机已验 |

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

### 当前分源进度（2026-06-06 收口）

| 来源 | 状态 | 下一步 |
|------|:--:|------|
| 联系人·个人 | ✅ v15.1 装机实证 | 已收口 |
| 群聊 / 密群 | ✅ 2026-06-06 L1 装机实证 | 已收口 |
| 聊天记录关键词场景 | ✅ 2026-06-06 L1 装机实证 | 已收口；技术主路径为最终 `q2/f0.getView` 渲染层 id 过滤，不是 `ch6.e` talker 直解 |
| 普通群 / 普通好友 | ✅ 对照放行 | 已收口 |
| 全形态空白条 | ✅ v15.2 收口 | `lp.height=1`，禁止回到 0/负值 |

> 当前装机实情见 [`result.md`](./result.md)；详细历程见 [`worklog.md`](./worklog.md)。
> P20 主线已按来源收口。P26C「群聊行包含密友名高亮」属于 UI 优化，不阻塞 P20。

### 实现线（8.0.71）

| 优先级 | 点 | 说明 |
|:--:|-----|------|
| P0 | `f0.getView` afterHook | 主路径：联系人 / 群聊密群 / 聊天记录关键词场景均由最终渲染 item 的 wxid/groupId 精确过滤 |
| P0 | `hookSearchContact(String)` 等价 | 联系人存在性 → 假「未找到」（待 Frida 定类名） |
| 理解层 | 聊天记录 FTS / `z15.ef6` / `ch6.e` | 分源已出现，但不作为主过滤路径；禁止写成已解析 talker |
| 禁止 | 监测搜索框输入内容来藏人 | 那是密码线，见 B |

### 验收 log

```text
[SF:gv] blocked pos=N id=wxid_xxx      (联系人已实证)
[SF:gv] blocked pos=N id=xxx@chatroom  (群聊/密群已实证)
[SF:DUMP] z15.ef6 ...                  (聊天记录分源出现)
[SF:gv] blocked pos=N id=wxid/groupId  (聊天记录关键词场景最终渲染层折叠)
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
