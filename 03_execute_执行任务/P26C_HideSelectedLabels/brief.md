# P26C 定稿 — 隐藏指定通讯录标签（独家）【待实现】

> 定稿日期：2026-05-29（总调度会话锁定）
> 状态：**待实现**（排在「密友导入」「密群导入」之后；那两个同技术套路打通后本任务顺手收）
> 探针数据源：见 [`result.md`](./result.md)（d4 / field_labelID / field_labelName / WCDB lazy load / onBindViewHolder 限制）

---

## 一、产品行为

1. 设置页「隐藏通讯录标签」→ 点击拉起微信官方标签页 `ContactLabelManagerUI`（通讯录 → 标签）
2. 用户在官方页点选要隐藏的标签 → 抓 `labelName` 存入隐藏集合
3. 被选中的标签名在标签列表不显示

> 区别于 P19B（藏整个标签入口）：P26C = 藏「用户指定的某几个标签」。

---

## 二、存储与门控（定）

| 项 | 定稿 |
|----|------|
| 存什么 | 隐藏**标签名集合**（String Set），不用序号 id（删/重排会失效） |
| 存哪里 | **MMKV 持久化**（Bridge，新增 key，跟密友 hidden set 同口径） |
| 隐不隐 | **跟随状态机 V/H**：HIDDEN 态生效隐藏 / VISIBLE 态全显（与密友/密群一致） |
| 设置页提示 | 「改名标签后需重新选」 |

---

## 三、过滤机制

- 镜像 `ContactGroupHide` 的 `getCount/getItem/getView` 位置重映射（避 V.GONE / 铁律 16）
- 按 `labelName` 匹配跳过隐藏行
- **前置探针（阶段 0）**：确认 d4 adapter（`f2.f2`）是否 cursor 驱动 + `labelName` 运行时读取点（addAll 时 name=null，WCDB lazy load）

---

## 四、选择交互（对齐密友/密群导入方案）

- 选择交互与「密友导入 / 密群导入」统一为同一套「复用官方选择器 / 官方页点选 + 拦结果」，本任务复用其成熟方案
- 待密友/密群导入定型后回填本节具体交互

---

## 五、依赖与排期

1. 先做**密友导入** + **密群导入**（同为「拦官方选择器结果」技术套路）
2. 上面两个打通 → P26C 标签顺手收（同技术）

---

## 六、实现期注意（用户要求）

- 设置页 `SettingsEntry.java` 已较大且属核心保险区；新增标签/导入设置项时**按模块拆分设置页代码**，不要继续往单文件堆
- 动 `SettingsEntry` / `Bridge` / `ModuleMain` 前先过 `guard-auth-review`（核心保险区 + 新 Filter 链）
- 并发：动 Bridge/ModuleMain/SettingsEntry 这些公共文件前，确认会话排序窗口已提交，避免撞车
