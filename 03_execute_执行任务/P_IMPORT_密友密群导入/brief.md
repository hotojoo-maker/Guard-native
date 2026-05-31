# P_IMPORT 定稿 — 密友 / 密群 批量导入（复用官方 SelectContactUI）【待实现】

> 定稿：2026-05-29（总调度会话锁定）
> 证据：L1 frida 实证 `bug排查/probe_selectcontact_8071.log` + `probe_selectcontact_IN_8071.log`（com.tencent.mn1 竞品 8.0.70.2 跨验，类名与 8.0.71 A3 一致）
> 技术套路：复用微信官方多选选择器 `SelectContactUI`，拦启动参数预选 + 拦返回结果 → 增删一体

---

## 一、产品行为

- 设置页（已注入微信设置页的「微密友」入口）「添加密友 / 添加密群」→ 拉起微信官方 `SelectContactUI`
- 进去时**预选当前已有密友/密群**（顶部头像）→ 勾选=加，点顶部头像取消=删（微信原生交互，零成本）
- 点「完成」→ 读返回的选中全集 → diff 当前集 → add/remove → `RefreshBus.notifyHiddenChanged` 热切生效

---

## 二、Intent 规格（L1 钉死）

**拉起选择器（startActivityForResult → `com.tencent.mm.ui.contact.SelectContactUI`）extras：**

| key | 密友 | 密群 | 备注 |
|-----|:--:|:--:|------|
| `list_type` | **1** | **2** | 友=1（本轮实证），群=2（A3 §3.1） |
| `list_attr` | 16471 | 16471 | 友/群一致 |
| `already_select_contact` | 现有密友 wxid CSV | 现有密群 chatroom CSV | **预选 key**（增删一体前提） |
| `titile` | "选择密友" | （群相应标题） | ⚠️ 微信原拼写 `titile`，非 title |
| `from_select_contact` | true | true | |

**返回（setResult(-1, Intent)）：**

| key | 值 |
|-----|----|
| `Select_Contact` | 选中 wxid/chatroom CSV（**主**） |
| `Select_Conv_User` | 同上（冗余） |
| `Is_Chatroom` | false（友）/ 群相应 |
| `show_all_select_contact_count` | 总数（参考） |

---

## 三、增删一体（无需单独删除页）

- `already_select_contact` 预选现有集 → 用户在原生 UI 加/减
- 返回 `Select_Contact` = **当前完整选中集**（实证：连续返回 6/9/4 个，是全集非增量）
- 落地：`resultSet` diff `已存集` → 新增 addHiddenId/addGroupId、缺失 removeHiddenId/removeGroupId

---

## 四、存储与门控

- 选定集合 → **MMKV 持久化**（密友 wxid set / 密群 chatroom set，沿用现有 Bridge）
- 隐不隐 → **跟随状态机 V/H**（与现有密友/密群一致）

---

## 五、实现要点（用户要求：分模块，别堆 SettingsEntry）

- **新建独立模块类**（如 `ContactImportGuard`）：负责拉起 SelectContactUI（友/群两个入口）+ 拦结果 + diff + 写 Bridge
- `SettingsEntry` 仅把「添加密友 / 添加密群」onClick 指向该模块（最小侵入核心保险区）
- 结果拦截方式：参考竞品 `getResultIntentViaUnsafe` / 或 hook SelectContactUI.setResult / 或 onActivityResult（探针阶段定）
- 特征面 100% 自有：类名/key 用我们自己的（机制参考竞品 80%）

---

## 六、动手前门控

- 动 `SettingsEntry`（核心保险区）/ `Bridge` 前先过 `guard-auth-review`
- 并发：动 Bridge/SettingsEntry 前确认会话排序窗口（fix/conv-order-by-time）已提交，避免撞车
- 建议独立 feature 分支隔离本次实现

---

## 七、状态：待实现（图纸 100% 齐，纯写代码）
