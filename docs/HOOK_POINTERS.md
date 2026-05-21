# HOOK_POINTERS — Catfish 原版 Hook 指针索引

> **用途**：从 Catfish 源码快速定位每个 hook 的实现、字段、类名
> **来源**：甜密友 8.0.61 + 8070 正版 8.0.70（UserControll 交叉对比）
> **日期**：2026-05-19
> **证据等级**：L2 静态已证实（jadx 反编译完整可读）

---

## 一、源文件路径

| 版本 | 文件 | 路径 | 行数 | Hook数 |
|------|------|------|:--:|:--:|
| **甜密友 8.0.61** | UserControll.java | `C:\Users\Me\Desktop\apk2\_4__samples\dynamic_fast\classes16_decompiled\jadx_out\sources\com\catfish\newvip\core\UserControll.java` | 933 | 23 |
| **甜密友 8.0.61** | MainEntry.java | `C:\Users\Me\Desktop\apk2\_4__samples\dynamic_fast\classes16_decompiled\jadx_out\sources\com\catfish\newvip\MainEntry.java` | 863 | 32 |
| **8070 正版** | UserControll.java | `C:\Users\Me\Desktop\apk2\_1__B_rewrite\06_jadx\jadx_output_classes17\sources\com\catfish\newvip\core\UserControll.java` | 1023 | 27 |
| **8070 正版** | MainEntry.java | `C:\Users\Me\Desktop\apk2\_1__B_rewrite\06_jadx\jadx_output_classes17\sources\com\catfish\newvip\MainEntry.java` | 989 | 41 |
| **guard_native 副本** | UserControll.java | `./refs/UserControll.java` | 1023 | 27 |
| **guard_native 副本** | MainEntry.java | `./refs/MainEntry.java` | 1068 | 41 |

**甜密友 = v1 首选参考**（最少注入 + 完全可读 + 无三件套噪音）
**guard_native/refs 已镜像 8070 版本**，可直接引用。

---

## 二、UserControll 23 个共同 Hook（甜密友 + 8070 均含）

### 会话隐藏 (Conversation Filter) — 对应模块 A

| # | Hook | 甜密友行号 | 8070行号 | 方法签名 | 关键字段/类 |
|---|------|:--:|:--:|------|------|
| 1 | `hookConverBack` | 130 | 147 | `public void hookConverBack()` | — (返回键触发 mVipMode 切换) |
| 2 | `hookRecent` | 200 | 218 | `public void hookRecent(List recnetList)` | `VipPreference.getVipSecret()` → `list.remove(user)` |
| 3 | `hookTransFlag` | 185 | 203 | `public boolean hookTransFlag()` | `VipPreference.isVipEnable()` |

### 通讯录隐藏 (Address Book) — 对应模块 A

| # | Hook | 甜密友行号 | 8070行号 | 方法签名 | 关键字段/类 |
|---|------|:--:|:--:|------|------|
| 4 | `hookAddressInfo` | 196 | 214 | `public boolean hookAddressInfo(String user)` | `VipPreference.getVipSecret().contains(user)` |
| 5 | `hookContactCount` | 800 | 837 | `public int hookContactCount(int count)` | `VipPreference.getVipMemberCount()` — count修正 |

### 搜索拦截 (Search Lock) — 对应模块 B

| # | Hook | 甜密友行号 | 8070行号 | 方法签名 | 关键字段/类 |
|---|------|:--:|:--:|------|------|
| 6 | `hookSearchContact` | 382 | 422 | `public boolean hookSearchContact(String user)` | `return false` → 微信显示"未找到" |
| 7 | `hookFts` | 420 | 460 | `public void hookFts(Object data, View view)` | 全局搜索拦截 |
| 8 | `hookSelectUser` | 928 | 971 | `public void hookSelectUser(HashSet data, Intent intent)` | `data.addAll(vips)` — 选择联系人反注 |

### 朋友圈过滤 (Moments Filter) — 对应模块 D

| # | Hook | 甜密友行号 | 8070行号 | 方法签名 | 关键字段/类 |
|---|------|:--:|:--:|------|------|
| 9 | **`hookSnsObject`** ★ | 536 | 573 | `public boolean hookSnsObject(Object snsObject)` | **`SnsObject.parseFrom` 后处理** / `field_UserName` |
| 10 | `hookSnsObject2` | 556 | 593 | `public boolean hookSnsObject2(Object snsObject)` | 同 hookSnsObject，备用路径 |
| 11 | `hookSns` | 498 | 535 | `public void hookSns(int index, BaseAdapter adapter, View view)` | Adapter 渲染层兜底 |
| 12 | `hookSns2` | 521 | 558 | `public void hookSns2(String name, View view)` | View 层过滤 |
| 13 | `hookSnsGroup` | 442 | 479 | `public boolean hookSnsGroup()` | 朋友圈分组可见图标隐藏 |
| 14 | `hookSnsLikes` | 585 | 622 | `public void hookSnsLikes(Object l, Object d)` | `LikeUserList` — 点赞列表过滤 |
| 15 | `hookSnsComments` | 700 | 737 | `public void hookSnsComments(LinkedList<Object> list)` | `CommentUserList` — 评论列表过滤 |
| 16 | `hookSnsCommentOne` | 717 | 754 | `private boolean hookSnsCommentOne(String fieldName, Object snsObject)` | 单条评论/点赞 remove（被 hookSnsObject 调用） |
| 17 | `hookSnsCommentDetail` | 789 | 826 | `public Object hookSnsCommentDetail(Object snsobj)` | 评论详情页过滤 |

### 密友圈状态 (Friend Status) — 对应模块 D

| # | Hook | 甜密友行号 | 8070行号 | 方法签名 | 关键字段/类 |
|---|------|:--:|:--:|------|------|
| 18 | `hookFriendStatus` | 815 | 852 | `public void hookFriendStatus(ConcurrentHashMap map)` | `map.remove(vip)` |
| 19 | `hookFriendStatusList` | 823 | 860 | `public List hookFriendStatusList(List<Object> statusList)` | `field_UserName` — 返回新List(白名单模式) |
| 20 | `hookFriendStatusList2` | 852 | 892 | `public void hookFriendStatusList2(List<Object> statusList)` | `field_UserName` — 原地remove(黑名单模式) |
| 21 | `hookFriendStatusList` | 903 | 946 | `public void hookFriendStatusList(String statusId, ArrayList<Object> statusList)` | statusId + `field_UserName` |
| 22 | `hookStatusTopics` | 873 | 913 | `public List hookStatusTopics(List<Object> statusList)` | `field_UserName` — 话题列表过滤 |
| 23 | `hookFriendStatusItem` | 924 | 967 | `public boolean hookFriendStatusItem(String friendName)` | `VipPreference.getVipSecret().contains(friendName)` |

---

## 三、8070 独有 Hook（v1 不采纳）

| # | Hook | 8070行号 | 功能 | 理由排除 |
|---|------|:--:|------|------|
| 24 | `hookLabUser` | 981 | 实验室用户注入 | v2/v3 装b模块，非核心隐私 |
| 25 | `hookUserLab` | 990 | 实验室用户检查 | 同上 |
| 26 | `hookLabUserEx` | 994 | 实验室用户反向过滤 | 同上 |
| 27 | `hookFinder` | 1020 | 视频号过滤 | v2 功能，v1 不需要 |

---

## 四、MainEntry 调度层（甜密友 32 / 8070 41）

MainEntry 是 UserControll hook 的**调用者和调度者**，不实现过滤逻辑，只做：
1. `isVipMode()` 闸门检查
2. 委托 `UserControll.getInstance().hookXxx()`
3. MLOG 日志

### 关键 MainEntry Hook 入口（v1 需要知道的）

| MainEntry 方法 | 委托到 UserControll | 行号(8070) | 说明 |
|------|------|:--:|------|
| `hookNewCon(List)` | `hookRecent` | 268 | 会话列表构建入口 |
| `hookConversation(List)` | — (直接操作List) | 288 | 会话过滤主入口 |
| `hookAddressUI(List)` | — (直接操作List) | 303 | 通讯录过滤主入口 |
| `hookSnsObject(Object)` | `hookSnsObject` | 409 | 朋友圈 Proto 入口 |
| `hookSnsObject2(Object)` | `hookSnsObject2` | 421 | 朋友圈备用入口 |
| `hookSnsLikes(Object, Object)` | `hookSnsLikes` | 432 | 点赞入口 |
| `hookSnsComments(LinkedList)` | `hookSnsComments` | — | 评论入口 |
| `hookSearchContact(String)` | `hookSearchContact` | 327 | 搜索拦截 |
| `hookFts(Object, View)` | `hookFts` | 342 | 全局搜索 |
| `hookContactCount(int)` | `hookContactCount` | 374 | 通讯录计数 |
| `hookSelectUser(HashSet, Intent)` | `hookSelectUser` | — | 选择联系人 |

---

## 五、关键共享类 / 字段（跨 hook 复用）

| 类/字段 | 路径 | 作用 |
|------|------|------|
| **VipPreference** | `com.catfish.newvip.preference.VipPreference` | 配置中枢：`getVipSecret()` / `isVipEnable()` / `getVipMemberCount()` |
| **VipPreference.getVipSecret()** | — | 返回 `"wxid1,wxid2,..."` 逗号分隔密友列表 |
| **VipPreference.getVipMemberCount()** | — | 返回密友数量（通讯录计数修正用） |
| **field_UserName** | WeChat 状态/话题对象 | 反射读取发帖者 wxid |
| **Utils.a(Class, String)** | `com.catfish.newvip.core.Utils` | 反射获取 Field（`Class.getDeclaredField` 封装） |
| **ReflectHelper** | `com.catfish.newvip.util.ReflectHelper` | 反射工具集：`getMethod()` / `getField()` |
| **MLOG** | `com.catfish.newvip.util.MLOG` | 日志（debug flag 控制开关） |
| **SNSDATA_CLASS** | UserControll 静态字段 | 甜密友=`"sn4.xz5"` / 8070=`"m05.g46"` — 目标版本 SnsObject 类名 |
| **NativeHelper** | `com.catfish.newvip.util.NativeHelper` | JNI 桥（76 native 方法 → libwechatsd.so） |

---

## 六、证据等级说明

| 等级 | 含义 | 本文档适用 |
|:--:|------|------|
| L1 | Frida 动态已证实 | hookSearchContact / hookSnsObject 等（guard_native 已有 Frida 验证脚本） |
| L2 | 静态反编译已证实 | 本文档全部 — jadx 完整可读 |
| L4 | 待动态验证 | hookSnsGroup / hookSnsCommentDetail（guard_native 尚未写 Frida 验证） |

**当前本文档全为 L2**。升 L1 需用 Frida 在 8.0.66 上实际触发并观察效果。

---

**关联文档**：
- `HOOK_MAP_V1.md` — 第一版复刻优先级排序
- `HOOKMAP.md` — 功能模块总图（A~F 域）
- `docs/HOOK_POINTS.md` — 8.0.66 WeChat 内部类/方法（Frida 动态验证）
- `FAILURE_LOG.md` — 22 条已验证失败方案（必须遵守）
