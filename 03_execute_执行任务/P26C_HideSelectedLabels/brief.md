# P26C brief — 隐藏指定通讯录标签（独家）

> 🟡 探针已通，过滤待写 — 详见 [`result.md`](./result.md)

## 产品

用户自选隐藏某几个标签 → 本机「通讯录 → 标签」不显示；不删数据、不同步云端。

## 与 P19B 区分

| | P19B F07B | P26C F07C |
|--|-----------|-----------|
| 目的 | HIDDEN 态藏**密友 wxid** | 藏**整标签**（独家） |
| item | `ye5.j` | `d4` |
| 字段 | `d` | `field_labelID` / `field_labelName`（`rl.g2`） |

## L1 限制

`addAll(d4)` 时 ID=-2000000、name=null；真实值 onBindViewHolder 才填充。

## 已有

- `installLabelListProbe()` → `/api/labels` → Web 标签卡片
- `installLabelAddAllHook()` → P19B ✅

## 待做

- Bridge 隐藏 labelId Set + Web 多选 UI
- 过滤 hook（非 addAll 直读 ID）
- SettingsEntry「隐藏指定标签」
