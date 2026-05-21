# T09 结果报告 — Catfish mn1 互动红点探针
> 日期：2026-05-21
> 目标：com.tencent.mn1（Catfish 8.0.70，isVipEnable 已 patch 永远 true）
> 设备：MI 9 (609b4b18)

---

## 结论速览

| hook 点 | 触发？ | 关键数据 | Guard Native 用途 |
|---------|:------:|---------|-----------------|
| `hookNewCon(List)` | ✅ 多次 | before=3 after=2（过滤 1 条会话） | ConvFilter 主线 — 已实现 ✅ |
| `kc(int)` | ✅ 多次 | input=1→0 / 2→0 / 3→0（全部隐藏） | 桌面角标修正 — **Guard 未实现** ⚠️ |
| `hookNotification(Message)` | ✅ 多次 | 触发确认，未抓到 talker | 通知拦截入口确认 — **Guard 未实现** ⚠️ |
| `isVipEnable()` | ✅ 高频 | 始终 true（patch 生效） | 授权门控，参考设计 |
| `hookFriendStatusList(List)` | ❌ 未触发 | 需进通讯录页 | ContactFilter — 已实现 ✅ |
| `hookStatusTopics(List)` | ❌ 未触发 | 需进朋友圈状态 | MomentsFilter — 已实现 ✅ |
| `NmsHookInvocationHandler.invoke` | ❌ 未触发 | 需密友发消息触发通知 | Binder 层通知压制 — **Guard 未实现** ⚠️ |

---

## 关键发现

### 1. hookNewCon — 会话列表过滤已验证

```
[hookNewCon] FILTERED before=3 after=2
[hookNewCon] FILTERED before=3 after=2
[hookNewCon] FILTERED before=3 after=2
```

- 会话列表 3 条，过滤后 2 条，**持续命中**
- Catfish 在 8.0.70 用 `hookNewCon(List)` 作为会话过滤主入口
- Guard 的 ConvFilter（L1 MvvmList.n）路径不同但目标一致，**已验证可用**

---

### 2. kc(int) — 桌面角标未读数修正（Guard 缺失！）

```
[kc] input=1 output=0 hidden=1
[kc] input=2 output=0 hidden=2
[kc] input=3 output=0 hidden=3
```

- Catfish 通过 `kc(int)` 将桌面图标的未读角标从 N 改为 0
- **Guard Native 目前没有这个功能** — 即使会话列表隐藏了密友，桌面角标数字仍然暴露"有未读消息"
- Catfish 实现：注入 `com.tencent.mm.booter.notification.h0.d(int)` 方法（classes10.dex），调用 `MainEntry.kc(count)` 修正

**Guard v1 补丁方案**：
```java
// 找 8.0.71 的等价类（h0 → 找 OEM 角标发送方法）
// hook 入口，调用 Bridge.getHiddenUnreadCount() 扣除
```
优先级：P22 或 P23 顺手做

---

### 3. hookNotification — 通知拦截入口确认

```
[hookNotification] called   ← 3次
```

- 确认 Catfish 确实走 `hookNotification(Message)` 拦截通知
- 当前探针未抓到 `talker` wxid（NmsHook 未触发，说明这次通知可能是系统通知，不是密友消息通知）
- 完整通知压制路径：`hookNotification` → `NmsHookInvocationHandler`（Binder 动态代理）两层

**Guard v2 参考架构**：
```
通知进入 → hookNotification(Message) 预过滤
         → NmsHookInvocationHandler.invoke() Binder 层阻断
         → extras["notification.show.talker"] 取 wxid → 匹配密友列表 → null
```

---

### 4. isVipEnable — 高频调用（约每秒 10+ 次）

```
[isVipEnable] = true  × 300+次
```

- Catfish 在极高频率下检查授权状态（心跳 + 每次 hook 入口都调用）
- 我们的 patch（直接 return true）完全生效
- **Guard 设计启示**：`isActive()` 总闸不要每次 hook 都反射读 MMKV，在内存缓存 boolean，避免性能开销

---

---

### 5. 朋友圈过滤调用链（v5~v7 新增）

#### 5.1 每条动态的固定触发顺序

```
hookSns2("wxid_xxx", View)             ← void，直接操作 view（hide/show）
hookSnsObject(SnsObject@xxx)           ← 单条详情页才触发（feed 滑动不触发）
hookSnsCommentOne("CommentUserList")   ← boolean，false=不隐藏，true=隐藏
hookSnsCommentOne("LikeUserList")      ← boolean，false=不隐藏，true=隐藏
```

#### 5.2 hookSns2 是 void 方法

```
[hookSns2] wxid=wxid_o7qfwxi3hga022 ret=undefined
[hookSns2] wxid=gh_d1c058de0340      ret=undefined
```

- **void 返回**：它不 return true/false，而是直接拿到第 2 参数 `View`，对其做 `setVisibility(GONE)` 或 `setHeight(0)`
- 这意味着 Guard Native 需要在同等位置 hook WeChat 的 feed adapter，拿到 wxid + view 后自行判断并隐藏

#### 5.3 hookSnsCommentOne 返回 boolean

```
[hookSnsCommentOne] type=CommentUserList ret=false   （普通用户，不隐藏）
[hookSnsCommentOne] type=LikeUserList   ret=false   （普通用户，不隐藏）
```

- `false` = 该 SnsObject 的评论/点赞列表不需要过滤（发帖人不是密友）
- `true`  = 发帖人是密友，过滤评论/点赞中出现我的名字（防止我去点赞被看到）

#### 5.4 hookSnsLikes / hookSnsComments 未触发

- 本次滑动 feed 没有触发，可能是详情页或者特定操作（主动点赞 API 回调）才走

#### 5.5 Guard Native D 模块对照

| Catfish 方法 | 作用 | Guard 等价路线 |
|---|---|---|
| `hookSns2(wxid, view)` | 朋友圈条目 view 级隐藏 | hook WeChat feed adapter → 匹配 wxid → view GONE |
| `hookSnsCommentOne(type, obj)` | 评论/点赞列表过滤 | hook SnsObject 评论/点赞 list → 移除含我 wxid 的条目 |

---

## 未触发 hook 补充说明

| hook | 触发条件 | 下次抓取方法 |
|------|---------|------------|
| `hookFriendStatusList` | 进通讯录主页并滑动 | 进通讯录 → 下拉刷新 |
| `hookStatusTopics` | 进朋友圈并刷新 | 进朋友圈 → 下拉 |
| `NmsHookInvocationHandler` | 密友账号发消息 | 让密友发一条消息触发通知 |

---

## 对 Guard Native 的直接影响

### 已验证可不改的（Guard 路径不同但效果等价）

- ConvFilter → MvvmList.n/m（Guard 用，非 hookNewCon）✅
- ContactFilter → ArrayList.addAll(fc5.g)（Guard 用，非 hookFriendStatusList）✅
- MomentsFilter → addAll(na4.b)（Guard 用，非 hookStatusTopics）✅

### 需要补做的（Guard 缺口）

| 功能 | Catfish 实现 | Guard 当前状态 | 建议 |
|------|------------|--------------|------|
| 桌面角标修正 | `kc(int)` → h0.d 注入 | ❌ 未实现 | P22 补做，找 8.0.71 h0 等价类 |
| 通知压制 | `hookNotification` + `NmsHookInvocationHandler` | ❌ 未实现（C2 v2） | v2 参考 Catfish 两层架构 |
