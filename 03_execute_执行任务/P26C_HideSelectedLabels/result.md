# P26C 独家 — 隐藏指定通讯录标签 — 探针归档

**日期**: 2026-05-24  
**状态**: 🟡 探针已通 + Web 标签列表已推；**指定标签过滤 hook 待写**

> 与 P19B（标签内密友泄漏）分列：P19B = HIDDEN 态藏密友 wxid；P26C = 用户自选藏**整标签**（本机 UI）。

---

## 一、标签管理页（ContactLabelManagerUI）

| 项目 | 值 |
|------|-----|
| Activity | `com.tencent.mm.plugin.label.ui.ContactLabelManagerUI` |
| 标签列表 item | `com.tencent.mm.storage.d4`（extends `rl.g2` → `s45.f0`） |
| 标签 ID | `field_labelID`（int，父类 `rl.g2`） |
| 标签名称 | `field_labelName`（String，父类 `rl.g2`） |
| 数据入口 | `ArrayList.addAll(d4)` → `ContactLabelManagerUI.u7` |
| Adapter | `f2.f2`（Tinker patch dex，Frida 无法直接访问） |
| WCDB 表 | `labelID` PK, `labelName`, `labelPYFull`, `labelPYShort`, `createTime`, `isTemporary`, `lastUseTime` |

**已知限制（L1）**：`addAll` 时 `field_labelName`=null，`field_labelID`=-2000000（WCDB lazy load；真实值在 **onBindViewHolder** 从 Cursor 填充）。→ 过滤不能单靠 addAll 读 ID，需结合 Web 探针缓存或 bind 层。

---

## 二、标签内「添加成员」搜索（MvvmContactListUI）— P19B ✅

| 项目 | 值 |
|------|-----|
| Activity | `com.tencent.mm.ui.mvvm.MvvmContactListUI` |
| item | `ye5.j` |
| wxid | `d`（`wxid_xxx-N-M`，去后缀 `-N-M`） |
| 数据入口 | `ArrayList.addAll(ye5.j)` → Kotlin Flow `jk3.k.emit` |

详见 `P19B_ContactLabel/result.md`（F07B 装机 ✅）。

---

## 三、已实现代码（ContactFilter.java）

| 方法 | 目标 | 功能 |
|------|------|------|
| `installLabelAddAllHook()` | `addAll(ye5.j)` | 标签内添加搜索 → 过滤密友 wxid ✅ |
| `installLabelListProbe()` | `addAll(d4)` | 标签列表 → 推 Web UI `/api/labels` 🔬 |

---

## 四、Web UI

| 变更 | 说明 |
|------|------|
| 标签列表卡片 | 独立卡片，密群列表下方、配置上方 |
| `/api/labels` | `{"sz":N,"items":[{"id":X,"name":"Y"},...]}` |
| 存储 | `Bridge.setLabelListJson()` / `getLabelListJson()` |

---

## 五、泄漏 / 功能路径覆盖

| 路径 | 状态 |
|------|:--:|
| A) 标签成员列表藏密友 | ✅ F07B `ye5.j`（P19B） |
| B) 标签内添加 → 搜索密友 | ✅ `installLabelAddAllHook` |
| C) 标签列表指定隐藏 | 🟡 探针已通；待写过滤 + 用户选 ID |

---

## 六、脚本

| 脚本 | 用途 |
|------|------|
| `P19B_ContactLabel/scripts/probe_label_tab.js` | v4，`ye5.j` / `MvvmContactListUI` |
| `P19B_ContactLabel/scripts/probe_label_id.js` | v9，TextView unicode 标签名采集 |

---

## 七、下一步（过滤 hook）

1. Bridge 存用户隐藏 labelId Set（来自 Web UI 多选）
2. 过滤层：`addAll(d4)` 不可靠读 ID → 候选 **onBindViewHolder** / 按 Web 探针 JSON 的 id↔position 映射
3. `RefreshBus` 变更后重进标签页验收
