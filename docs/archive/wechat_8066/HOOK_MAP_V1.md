# HOOK_MAP_V1 — 第一版复刻 Hook 优先级

> ⛔ **DEPRECATED** — 历史规划（**8.0.66**），已迁入 `docs/archive/`。  
> **8071 实现** → [`../../HOOK_MAP_8071_AUTHORITATIVE.md`](../../HOOK_MAP_8071_AUTHORITATIVE.md)

> **输入**：甜密友 23 hook × 8070 27 hook 交叉对比
> **目标**：微信 8.0.66 / LSPosed 模块 / 纯 Java
> **日期**：2026-05-19

---

## 优先级速查

```
P0 ██████████  5 个  会话隐藏核心 + 搜索锁 + 朋友圈 Proto 层
P1 ██████      6 个  通讯录隐藏 + 朋友圈评论/点赞 + 密友圈状态
P2 ░░░░        7 个  渲染层兜底 + 密友圈细节
━ 暂缓         5 个  选择联系人反注/Lab/Finder（v2）
━ 禁止         4 个  8070 独有 Lab/Finder（不在甜密友中，v1 不碰）
```

---

## P0 — 第一版必须复刻（5 个）

### 1. hookSnsObject ★★★ 朋友圈 Proto 层

| 项 | 值 |
|------|-----|
| 功能 | `SnsObject.parseFrom` 后处理 → 过滤 CommentUserList / LikeUserList |
| 甜密友行号 | 536 |
| 8070行号 | 573 |
| 参数 | `Object snsObject` — protobuf SnsObject 实例 |
| 关键字段 | `CommentUserList` / `LikeUserList` (protobuf field) / `field_UserName` |
| 跨版本 | ⭐⭐⭐⭐⭐ protobuf field ID 永不变 |

**实现要点**：
- hook `SnsObject.parseFrom(byte[])` 返回值，不是 hook 调用前
- 遍历 `CommentUserList` / `LikeUserList` → remove 匹配 wxid
- `return true` 表示已修改
- 需要先确认 8.0.66 的 SnsObject 类名（查同目录 `CLASS_MAP_8066.md`）

**甜密友 / 8070 代码逐字相同**（22 行），可直接翻译。

---

### 2. hookSearchContact ★★★ 搜索拦截

| 项 | 值 |
|------|-----|
| 功能 | 搜索框输入密友 wxid → return false → 微信显示"未找到" |
| 甜密友行号 | 382 |
| 8070行号 | 422 |
| 参数 | `String user` — 被搜索的 wxid |
| 返回 | `boolean` — false = 不显示此联系人 |

**实现要点**：
- `VipPreference.getVipSecret().contains(user)` → return false
- return false 后微信的自然行为是显示"添加好友"引导，不需要额外构造数据
- 这是隐藏的**灵魂判据**：即使会话/通讯录漏了，搜索结果也看不到

---

### 3. hookFts ★★ 全局搜索拦截

| 项 | 值 |
|------|-----|
| 功能 | 微信全局搜索（FTS 全文索引）拦截 |
| 甜密友行号 | 420 |
| 8070行号 | 460 |
| 参数 | `Object data` — FTS 搜索结果 / `View view` — 渲染 View |

**实现要点**：
- 与 hookSearchContact 互补：一个管精确搜索，一个管模糊搜索
- FTS data 结构需要 8.0.66 动态确认

---

### 4. hookRecent ★★ 会话列表隐藏

| 项 | 值 |
|------|-----|
| 功能 | 最近联系人列表 → 遍历 remove 密友 |
| 甜密友行号 | 200 |
| 8070行号 | 218 |
| 参数 | `List recnetList` — 最近联系人列表 |

**实现要点**：
- Catfish 8.0.70: `List<va5.y>` — ListView 模式
- guard_native 8.0.66: `MvvmList.o/p` (ArrayList<f45.u>) — RecyclerView + Flow 模式
- **不能照搬 Catfish 的 List.remove() 逻辑**，需适配 MvvmList 三层（8066 见同目录 `HOOK_POINTS.md`）
- guard_native `filter_conv.js` 已实现等效功能（4 层拦截 + notifyDataSetChanged clean-before）

---

### 5. hookConverBack ★★ 会话返回键触发

| 项 | 值 |
|------|-----|
| 功能 | 从会话返回列表时触发 mVipMode 切换 |
| 甜密友行号 | 130 |
| 8070行号 | 147 |
| 参数 | 无 |

**实现要点**：
- Catfish 中此 hook 配合 KeyEvent 监听
- guard_native 可用 ActivityLifecycleCallbacks.onBackPressed 等效替代（更干净，不需要 hook KeyEvent）
- 触发 → `mVipMode = !mVipMode` → 切换显隐状态

---

## P1 — 第一版需要（6 个）

### 6. hookAddressInfo ★★ 通讯录过滤

| 项 | 值 |
|------|-----|
| 功能 | 通讯录中检查单个联系人是否为密友 |
| 甜密友行号 | 196 |
| 参数 | `String user` |
| 返回 | `boolean` — true = 隐藏此联系人 |

### 7. hookContactCount ★ 通讯录计数修正

| 项 | 值 |
|------|-----|
| 功能 | 通讯录总数减掉已隐藏的密友数 |
| 甜密友行号 | 800 |
| 参数 | `int count` |
| 返回 | `count - VipPreference.getVipMemberCount()` |

### 8. hookSnsComments ★★ 朋友圈评论过滤

| 项 | 值 |
|------|-----|
| 功能 | 朋友圈评论列表 → remove 密友评论 |
| 甜密友行号 | 700 |
| 8070行号 | 737 |
| 参数 | `LinkedList<Object> list` |

### 9. hookSnsLikes ★★ 朋友圈点赞过滤

| 项 | 值 |
|------|-----|
| 功能 | 朋友圈点赞列表 → remove 密友点赞 |
| 甜密友行号 | 585 |
| 8070行号 | 622 |
| 参数 | `Object l, Object d` |

### 10. hookFriendStatus ★ 密友圈状态 Map

| 项 | 值 |
|------|-----|
| 功能 | 密友圈 ConcurrentHashMap → remove 密友 key |
| 甜密友行号 | 815 |
| 参数 | `ConcurrentHashMap map` |

### 11. hookFriendStatusItem ★ 密友圈单项检查

| 项 | 值 |
|------|-----|
| 功能 | 检查单个 friendName 是否为密友 |
| 甜密友行号 | 924 |
| 参数 | `String friendName` |
| 返回 | `boolean` |

---

## P2 — 第二版或兜底（7 个）

| # | Hook | 理由暂缓 |
|---|------|------|
| 12 | `hookSnsObject2` | hookSnsObject 备用，P0 已稳则不需要 |
| 13 | `hookSns` | Adapter 渲染层兜底，Proto 层已处理则不需要 |
| 14 | `hookSns2` | View 层兜底，同上 |
| 15 | `hookSnsGroup` | 朋友圈分组可见图标，细节优化 |
| 16 | `hookSnsCommentDetail` | 评论详情页过滤，低频路径 |
| 17 | `hookStatusTopics` | 密友圈话题列表，v1 密友圈功能未开发 |
| 18 | `hookTransFlag` | 转发标记，v1 未做消息控制模块 |

---

## 暂缓 — v2/v3（5 个）

| # | Hook | 说明 |
|---|------|------|
| 19 | `hookSelectUser` | 选择联系人反注（防止在分享/转发界面看到密友） |
| 20 | `hookFriendStatusList` | 密友圈状态列表（白名单模式），v1 密友圈不完整 |
| 21 | `hookFriendStatusList2` | 密友圈状态列表（黑名单模式） |
| 22 | `hookFriendStatusList` (overload) | statusId 过滤重载 |
| 23 | `showUnReadMsgCount` | 未读计数控制，v1 未做消息模块 |

---

## 禁止照搬（4 个 — 8070 独有）

| # | Hook | 理由 |
|---|------|------|
| 24 | `hookLabUser` | 8070 独有，实验室功能，与核心隐私无关 |
| 25 | `hookUserLab` | 同上 |
| 26 | `hookLabUserEx` | 同上 |
| 27 | `hookFinder` | 8070 独有，视频号过滤，v2 再考虑 |

---

## 关键适配：Catfish → guard_native 8.0.66

| Catfish 做法 | guard_native 做法 | 原因 |
|------|------|------|
| `VipPreference.getVipSecret()` | MMKV `g_<seed>` namespace | 自有品牌，不用 Catfish 类名 |
| `MLOG.d()` | Android Log + adb forward 浏览器控制台 | 自有调试体系 |
| `Utils.a(class, field)` | 直接反射 `getDeclaredField` | 不依赖 Catfish 工具类 |
| `SNSDATA_CLASS = "sn4.xz5"` | 8.0.66 SnsObject 混淆名（待确认） | 版本差异 |
| `List.remove()` 会话 | `MvvmList.o/p` clean + notifyDataSetChanged | 8.0.66 RecyclerView 架构 |
| `mVipMode` boolean 内存态 | 3 态状态机 (显形/隐藏/解锁中) | 自有设计，不照搬 Catfish 2 态 |
| NativeHelper JNI 桥 | 去掉，纯 Java 实现 | 不引入 native 三件套 |

---

## 执行顺序（按窗口）

| 窗口 | 做 | Hook | 产出 |
|:--:|------|------|------|
| **W2** 朋友圈 | P0-1 hookSnsObject | `SnsObject.parseFrom` 拦截 | T05 调研 → 实现 |
| **W3** 会话 | P0-4 hookRecent + P0-5 hookConverBack | MvvmList + notifyDataSetChanged | 翻译 filter_conv.js → ConvFilter.java |
| **W1** 脚手架 | 搜索框 1111 + MMKV 状态机 | P0-2 hookSearchContact + P0-3 hookFts | adb forward 调试 + 状态机可视化 |
| **W4** 离线 | 确认 8.0.66 SnsObject 类名 / CommentUserList 字段 | — | Proto 字段 ID 速查表 |

---

**关联文档**：
- `HOOK_POINTERS.md` — 每个 hook 的源码位置、字段、类名
- `HOOKMAP.md` — 功能模块总图（A~F 域）
- `HOOK_POINTS.md`（本目录）— 8066 Frida 验证
- `FAILURE_LOG.md` — 22 条禁止方案
- `TASK_BOARD.md` — 4 窗口当前任务分配
