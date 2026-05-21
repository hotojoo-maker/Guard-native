# 跨版本类名映射表

> 最后更新: 2026-05-20 | 证据: L1=动态已证实, L2=静态已证实, L3=高概率推断
> 用途: Guard Native 运行时自发现锚点 + 各版本 hardcode 候选
> 源: 甜密友(8.0.61) jadx + 官方8.0.66 Frida + 官方8.0.71 jadx + Catfish 8.0.70 FINDINGS

---

## 朋友圈 Feed

### 帖子容器（Adapter List 中的 item 类）

| 逻辑角色 | 8.0.61 (甜密友基版) | 8.0.63 | 8.0.66 (官方) | 8.0.70 (官方/Catfish) | 8.0.71 (官方) | 稳定字符串锚点 |
|---------|---------------------|--------|--------------|---------------------|--------------|--------------|
| List item 类 | ? (bu4.c 提供 wxid 方法) | ? | `k24.b` | ? | `la4.p` (ImproveSnsInfo) | `"MicroMsg.Improve.SnsInfo"` |
| wxid 获取路径 | `bu4.c.a(int)` → String | ? | `item.d` → `i24.p` → `field_userName` | ? | `c1()` → `SnsObject.Username` | `field_userName` |
| snsId 字段 | ? | ? | `field_snsId` (i24.p) | ? | `SnsObject.Id` | `field_snsId` |
| 证据 | L2 (jadx Catfish classes16) | — | L1 (Frida filter_moments.js) | — | L2 (jadx 8.0.71) | — |

### 帖子 ViewModel

| 逻辑角色 | 8.0.61 | 8.0.63 | 8.0.66 | 8.0.70 | 8.0.71 | 锚点 |
|---------|--------|--------|--------|--------|--------|------|
| ViewModel 类 | ? | ? | `i24.p` (ImproveSnsInfo) | ? | `la4.p` (ImproveSnsInfo) | `"MicroMsg.Improve.SnsInfo"` |
| SnsObject 获取 | ? | ? | `i24.p.c1()` | ? | `la4.p.c1()` | `com.tencent.mm.protocal.protobuf.SnsObject` |
| TimeLineObject | ? | ? | `i24.p.h1()` | ? | `la4.p.h1()` | — |
| 证据 | — | — | L2 (jadx 8.0.66, Window2 实测) | — | L2 (jadx la4/p.java) | — |

### 朋友圈数据层 (MvvmStorage)

| 逻辑角色 | 8.0.66 | 8.0.70 | 8.0.71 | 锚点 |
|---------|--------|--------|--------|------|
| MvvmStorage 类 | `na4.g` | ? | `na4.g` | `"MicroMsg.Improve.DataFlow"` |
| 泛型参数 | `MvvmStorage<la4.p>` | ? | `MvvmStorage<la4.p>` | — |
| 证据 | L2 | — | L2 (jadx na4/g.java) | — |

### MvvmList 批量插入方法（朋友圈数据入口）

| 方法 | 8.0.66 | 8.0.70 | 8.0.71 |
|------|--------|--------|--------|
| 批量插入 | `m(List, boolean)` | ? | `n(List, boolean)` |
| 备用路径 | `s(List)` | ? | ? |
| 其他路径 | `o(List,bool)`, `v(List,bool)`, `u(List,bool)` | ? | ? |
| 证据 | L1 (Frida hook 已证实命中) | — | L2 (jadx MvvmList.java 行378/442/464) |

---

## 赞评元素

### LikeUserList / CommentUserList / WithUserList 统一元素

| 逻辑角色 | 8.0.61 (甜密友) | 8.0.66 | 8.0.70 | 8.0.71 | 跨版本锚点 |
|---------|----------------|--------|--------|--------|-----------|
| 元素类名 | `sn4.xz5` | `z15.e56` | 推测: `z15.e56` | `z15.e56` | `com.tencent.mm.protocal.protobuf.SnsObject` |
| wxid 字段 | `d` (String) | `f435583d` (protobuf field 1) | 推测: `f435583d` | `f435583d` | protobuf field 1 |
| 昵称字段 | ? | `f435584e` (field 2) | 推测: `f435584e` | `f435584e` | protobuf field 2 |
| 时间戳字段 | ? | `f435593q` / `f435594r` (long) | ? | `f435593q` / `f435594r` | protobuf field 11/12 |
| 子回复列表 | ? | `f435597u` (LinkedList\<l56\>) | ? | `f435597u` | protobuf field 15 |
| 证据 | L2 (jadx Catfish UserControll) | L2 (jadx la4/d.java:40) | — | L2 (jadx z15/e56.java) | — |

### SnsObject protobuf 字段（跨版本不变）

| 字段 | 类型 | Protobuf 字段号 | 说明 |
|------|------|:--:|------|
| `Username` | String | 1 | 发帖人 wxid |
| `Nickname` | String | 2 | 发帖人昵称 |
| `LikeCount` | int | 7 | 点赞总数 |
| `LikeUserListCount` | int | 8 | 点赞列表项数 |
| `LikeUserList` | `LinkedList<e56>` | 9 | **点赞用户列表** |
| `CommentCount` | int | 10 | 评论总数 |
| `CommentUserListCount` | int | 11 | 评论列表项数 |
| `CommentUserList` | `LinkedList<e56>` | 12 | **评论用户列表** |
| `WithUserCount` | int | 13 | 与谁一起人数 |
| `WithUserListCount` | int | 14 | 与谁一起列表项数 |
| `WithUserList` | `LinkedList<e56>` | 15 | **与谁一起列表** |
| `CreateTime` | int | — | 创建时间 |
| `ObjectDesc` | wq5 (protobuf) | — | 内容描述 |
| `ReferUsername` | String | — | 引用/转发用户名 |

---

## 会话列表

| 逻辑角色 | 8.0.66 (官方) | 8.0.70 (Catfish) | 8.0.71 (官方) |
|---------|-------------|-----------------|-------------|
| 会话 item 类 | `f45.u` | `va5.y` | `kc5.y` |
| 内部数据类 | `u.d` → `m3` (extends `mk.h2`) | `y.d` → `e4` (storage) | `y.f286305d` → `l4` (storage.Contact) |
| wxid 方法 | `m3.j1()` | `e4.h1()` 或 `y.v()` | `l4.h1()` |
| 未读字段 | `m3.field_unReadCount` | ? | ? |
| Adapter | `f45.s0` (MvvmConversationAdapter) | ? (List-based, non-Cursor) | `kc5.f0` (ConversationItemBuilder) |
| MvvmList 子类 | `MvvmConvList` (extends MvvmList\<f45.u\>) | — (ListView, 无 MvvmList) | `MvvmConvList` (extends MvvmList\<kc5.y\>) |
| 批量插入 | `MvvmList.m(List, bool)` | — | `MvvmList.n(List, bool)` |
| 数据源 | `f45.t.g()` → `g0` {a,b,c} | Pine native hook 回调 `hookNewCon(List<va5.y>)` | `kc5.m` (ConversationDataController) |
| 内部 List 字段 | h/o/p `ArrayList<f45.u>` | 外部传入 ArrayList | f135087o/f135088p `ArrayList<kc5.y>` |
| 证据 | L1 (Frida frida_filter_final.js) | L2 (jadx Catfish classes17 + FINDINGS) | L2 (jadx CONVERSATION_DATA_PATH_8071.md) |

### 会话列表架构差异

| 维度 | 8.0.66 | 8.0.70 (Catfish) | 8.0.71 |
|------|--------|-----------------|--------|
| View 层 | ListView (ConversationListView) | ListView | ListView |
| 数据架构 | MvvmList + Kotlin Flow | 预构建 ArrayList | MvvmList + Kotlin Flow |
| Adapter | CursorAdapter → MvvmList 投影 | ListAdapter | CursorAdapter → MvvmList 投影 |
| Hook 点 | 反射修改 h/o/p 内部 List | 拦截传入 List → remove() | 反射修改 f135087o/f135088p |
| Flow 覆盖风险 | 有 (Flow 重下发) | 无 (无 Flow) | 有 (Flow 重下发) |

---

## 通讯录

| 逻辑角色 | 8.0.66 | 8.0.70 (Catfish) | 8.0.71 |
|---------|--------|-----------------|--------|
| Fragment | `MvvmAddressUIFragment` | ? | `MvvmAddressUIFragment` |
| 联系人 item | ? | `jf4.k` → `b` → `jf4.a` → `field_UserName` | `fc5.g` (包装 `z3`) |
| wxid 获取 | ? | `jf4.a.field_UserName` | `z3.c1()` → `field_username` |
| Adapter | ? | ? | `fc5.m` (UIComponent → AddressLiveList) |
| MvvmList 子类 | ? | ? | `AddressLiveList extends MvvmList<fc5.g>` |
| 内部 List | ? | ? | `f135087o` (ArrayList\<fc5.g\>) |
| 证据 | — | L2 (jadx Catfish UserControll FINDINGS) | L2 (jadx fc5/g.java + FINDINGS) |

---

## MvvmList 内部字段（反射 hook 必需）

| 版本 | 类全名 | 内部 ArrayList 字段名 | 元素类型 |
|------|--------|---------------------|---------|
| 8.0.66 会话 | `com.tencent.mm.plugin.mvvmlist.MvvmList` | `h`, `o`, `p` | `f45.u` |
| 8.0.66 朋友圈 | 同上 | `h`, `o`, `p` | `k24.b` |
| 8.0.71 会话 | 同上 | `f135087o`, `f135088p` | `kc5.y` |
| 8.0.71 朋友圈 | 同上 | `f135087o`, `f135088p` | `la4.p` |
| 8.0.71 通讯录 | 同上 | `f135087o` | `fc5.g` |

---

## 朋友圈 UI 组件

| 逻辑角色 | 8.0.66 | 8.0.71 | 变化 |
|---------|--------|--------|:--:|
| Activity | `com.tencent.mm.plugin.sns.ui.improve.ImproveSnsTimelineUI` | 同 | 不变 |
| RecyclerView | `ImproveRecyclerView` | 同 | 不变 |
| Adapter | `y1` (plugin.sns.ui.improve.component.y1) | `e2` | 改名 |
| Adapter.MvvmList 字段 | `y1.H` | `e2.H` | 不变 |

---

## 稳定锚点优先级（跨版本兼容）

### Tier 1 — 跨版本完全不变
- `com.tencent.mm.protocal.protobuf.SnsObject` — Protobuf 类
- `SnsObject.Username`, `SnsObject.LikeUserList`, `SnsObject.CommentUserList` — 字段名
- `field_userName`, `field_snsId` — Adapter item 字段名
- `"MicroMsg.Improve.SnsInfo"` — Log TAG (la4.p)
- `"MicroMsg.Improve.DataFlow"` — Log TAG (na4.g)
- `com.tencent.mm.plugin.mvvmlist.MvvmList` — 类全名不变

### Tier 2 — 小版本间稳定（需验证）
- `la4.p` — 8.0.71 ImproveSnsInfo；8.0.66 对应 `i24.p`（**混淆名每版重新分配**）
- `na4.g` — 8.0.71 MvvmStorage；稳定性待跨版本验证
- `z15.e56` — 赞评元素，8.0.66 对应 `gu4.ux5`（**同上，每版变**）
- 以上 Tier 2 类名本质是 Tier 3，**真正稳定的只有 Log TAG + Protobuf 字段名**
- e56 的 protobuf 字段偏移名 (f435583d~f435602z) — protobuf 层极少变
- `kc5.y` — 会话 item，8.0.66→8.0.71 已验证不变

### Tier 3 — 版本间会变（需运行时发现）
- MvvmList 方法名 (m→n, s→?) — 8.0.66→8.0.71 已变
- MvvmList 内部 List 字段名 (h/o/p → f135087o/f135088p)
- Adapter 类名 (y1 → e2)
- 混淆 item 类名 (k24.b / f45.u / va5.y / kc5.y / fc5.g)

---

## Catfish 特有类（非微信官方，对比参考）

| Catfish 类 | 对应微信概念 | 版本 |
|-----------|------------|------|
| `com.catfish.newvip.MainEntry` | Hook 注册中心 (32 hook 方法) | 全版本 |
| `com.catfish.newvip.core.UserControll` | 业务逻辑 (VIP过滤/黑白名单) | 全版本 |
| `com.catfish.newvip.preference.VipPreference` | MMKV 偏好读写 | 全版本 |
| `com.catfish.newvip.util.NativeHelper` | JNI 桥接 (94 native_*) | 全版本 |
| `com.catfish.newvip.core.SigCracker` | 签名绕过 | 全版本 |

---

## 证据索引

| 版本 | 数据来源 | 证据等级 |
|------|---------|:--:|
| 8.0.61 (甜密友) | jadx classes16.dex + HOOK_IMPLEMENTATION_ANALYSIS.md | L2 |
| 8.0.66 (官方) | Frida 动态 (filter_moments.js/filter_main.js) + jadx classes9.dex | L1+L2 |
| 8.0.70 (Catfish) | jadx classes10/17 + FINDINGS.md | L2 |
| 8.0.71 (官方) | jadx 1.5.1 208K Java + ARCHITECTURE_COMPARISON | L2 |
| 跨版本对照 | VERSION_CLASSMAP.md (apks/) | L2 |
