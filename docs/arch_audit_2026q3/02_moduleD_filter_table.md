# 产出 2：moduleD/*Filter 入口 / 出口 / 状态表

## 2.0 数量校正（盘点发现）

任务卡写「4 个过滤器」，但 `moduleD/` 下实际命名为 `*Filter` 的有 **5 个**：

| `*Filter` 类 | 是「密友数据过滤器」？ | 备注 |
|---|:--:|---|
| `ContactFilter` | ✅ | 通讯录主列表 |
| `ConvFilter` | ✅ | 会话列表 |
| `MomentsFilter` | ✅ | 朋友圈 feed |
| `ContactLabelMemberFilter` | ✅ | 标签成员列表（命名不带 Filter 词根但同类，文件名 `ContactLabelMemberFilter`） |
| `MomentsGroupIconFilter` | ❌ | **纯 UI 图标隐藏**，不读总闸、不读密友名单、无 registry recipe——与上面四个不是一类 |

**所以「核心 4 个密友过滤器」= ContactFilter / ConvFilter / MomentsFilter / ContactLabelMemberFilter。**
`MomentsGroupIconFilter` 单列，见 §2.3。下表 §2.2 先给核心 4 个。

> moduleD 里还有非 `*Filter` 的同族类（`ContactGroupHide`、`ContactHotReload`、`ConvHotReload`、`ContactDiscoveryHook`、`ContactLabelHideGuard`、`MomentsRedDotGuard`）不在本表范围，仅在涉及时引用。

## 2.1 共用状态闸（4 个核心过滤器都查这一把）

```
StateMachine.isActive()  (core/StateMachine.java:93-98)
  = isVipAuthorized()                 // net.EnvelopeStore：token+Ed25519验签+license未过期（禁入区）
  && GuardRuntime.isSensitiveConfigReady()  // registry 闸：release 必须 server-seed 解开 registry
  && Bridge.isFeatureEnabled()        // 密友功能总开关 key="f1"
  && mActive                          // 状态机 HIDDEN 态
```

- 任一为假 → Filter 短路、微信恢复原始行为。
- H↔V 切换通过 `RefreshBus`（订阅者收到事件后重过滤/热恢复）。

## 2.2 核心 4 个密友过滤器

### ContactFilter（通讯录主列表，482 行）

| 项 | 内容 |
|---|---|
| 入口 | `install(lpparam)`（:117）；先 `resolveRecipes()` 取 registry，失败则跳过安装 |
| registry gateway | `contact.address`（adapter_class=`ik3.t0`、live_list=`AddressLiveList`、mvvmlist_class） |
| Hook 点 | L4 `ik3.t0.notifyDataSetChanged`（clean-before 主闸）+ `AddressLiveList` 构造器 warm-attach（1s 延时，数据异步加载） |
| 数据链 | adapter `ik3.t0` → `AddressLiveList`(extends MvvmList<fc5.g>) → item `fc5.g`，wxid = `z3.c1()`→`field_username` |
| 状态门 | `StateMachine.isActive()`；`RefreshBus` 收 V↔H 做热切（StateMachine/RefreshBus 引用 13 处） |
| 名单源 | `Bridge.allHiddenIds()`（密友∪密群，2 处调用） |
| 出口（动作） | 命中即从 backing list 移除 `fc5.g` 条目（clean-before），再 notify |

### ConvFilter（会话列表，2512 行 — 最复杂）

| 项 | 内容 |
|---|---|
| 入口 | `install(lpparam)`（:221）；先 `resolveRecipes()`，registry not ready 则不安装 |
| registry gateway | `conv.list`（mvvmlist_class、adapter_class=`kc5.v0`、wxid_getters、contact_fields、l1_methods、l2_method） |
| Hook 点（多层） | L0w `MvvmList.e/k/l`（写入前过滤）、`ik3.n.handleEvent(List)`（上游刷新事件）、L1/L2 `MvvmList.n/m/s`、L4 `kc5.v0.notifyDataSetChanged`（主清洗）、addAll、adapter discovery、冷启动补刀（15s 窗口）、冷启动/锁屏白色遮罩 |
| 数据链 | adapter `kc5.v0`/`MvvmConvList` → item `kc5.y`.d → `com.tencent.mm.storage.l4`，wxid = `l4.C0()`→`field_digestUser` |
| 状态门 | `StateMachine.isActive()`/`getState()`；`RefreshBus`（引用 29 处）；H→V 热恢复经 `ConvHotReload` |
| 名单源 | `Bridge.allHiddenIds()`（9 处） |
| 出口（动作） | clean-before 移除密友 conv 项 + 冷启动两次补刀抹残影 + 遮罩遮闪现 + UIN↔wxid 映射写 `Bridge.putUinMapping`（供 SearchFilter 读） |

### MomentsFilter（朋友圈 feed，1456 行）

| 项 | 内容 |
|---|---|
| 入口 | `install(lpparam)`（:144）；带 registry recipe |
| registry gateway | `moments.feed`（adapter_class、item_friend、item_promo 等） |
| Hook 点 | D1 `na4.b`/`la4.p` extractPosterWxid → 删整条帖；D2/D3 `la4.p.getLikeUserList()`/`getCommentList()` afterHook → 过滤密友点赞/评论；L0v3 `SnsMsgUIWithRelevance`/`jw1.d` 藏密友点赞评论的顶部提醒 |
| 名单源 | `Bridge.getWxids()`（**只取密友 wxid，不含密群**——朋友圈以人为单位） |
| 状态门 | `StateMachine.isActive()`（StateMachine/RefreshBus 引用 9 处）；另有 `AppConfig.isDebugEnabled()` 分支（:500） |
| 出口（动作） | 删除密友整条帖；从点赞/评论列表里剔除密友条目 |

### ContactLabelMemberFilter（标签成员列表，113 行）

| 项 | 内容 |
|---|---|
| 入口 | `install(lpparam)`（:48） |
| registry gateway | **无**（不取 registry，类名/字段直接内联） |
| Hook 点 | `MvvmContactListUI` 上 `ArrayList.addAll(Collection)` beforeHook |
| 数据链 | item `ye5.j`（≠ 主列表 fc5.g），wxid = `ye5.j.d`（形如 `wxid_xxx-15-0`，去尾 `-N-M`） |
| 状态门 | `StateMachine.isActive()`（三层门，只读不写；5 处引用），**不刷 UI、不碰授权** |
| 名单源 | `Bridge.allHiddenIds()`（1 处） |
| 出口（动作） | `Iterator.remove()` 移除密友成员 |

## 2.3 MomentsGroupIconFilter（异类，211 行）— 单列

| 项 | 内容 |
|---|---|
| 性质 | **纯 UI 图标隐藏**，不是密友数据过滤器 |
| 入口 | `install(lpparam)`（:67） |
| registry gateway | 无 |
| Hook 点 | Layer1 `ViewStub.inflate()` afterHook（图标首次 inflate 命中）；Layer2 兜底 |
| 目标 | 隐藏「自己发的、仅可见分组/部分可见」朋友圈条目右下角图标（`app:id/pt`）；**绝不碰删除图标 `app:id/pi`** |
| 状态门 | **无 `StateMachine`/`RefreshBus`**（0 引用） |
| 名单源 | 无（不读密友名单） |
| 开关/计数 | `AppConfig` + `InterceptCounter` |
| 出口（动作） | 对 id 名 == `"pt"` 的 View 设 `GONE` |

## 2.4 横向对比小结

| Filter | gateway | 名单源 | 总闸 isActive | RefreshBus 热切 | 主要动作 |
|---|---|---|:--:|:--:|---|
| ContactFilter | contact.address | allHiddenIds | ✅ | ✅ | clean-before remove |
| ConvFilter | conv.list | allHiddenIds | ✅ | ✅ | clean-before + 补刀 + 遮罩 |
| MomentsFilter | moments.feed | getWxids（仅人） | ✅ | 部分 | 删帖 + 滤赞/评 |
| ContactLabelMemberFilter | 无（内联） | allHiddenIds | ✅ | ❌ | Iterator.remove |
| MomentsGroupIconFilter | 无 | 无 | ❌ | ❌ | View GONE（UI） |

**两个一致性偏差值得注意：**
1. `ContactLabelMemberFilter` 不取 registry，类名内联 —— 与 §10.7 P0-1「剩余内联」相关（见文档 5）。
2. `MomentsFilter` 用 `getWxids()` 而非其它三个的 `allHiddenIds()` —— 合理（朋友圈无群维度），但 API 不统一，易被误改。
