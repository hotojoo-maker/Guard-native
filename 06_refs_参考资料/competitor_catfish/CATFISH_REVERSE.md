# Catfish 8.0.70 动态逆向文档

---

## ⚠️ 版本锁定声明（必读）

```
应用名称:  微密友（Catfish）
APK 包名:  com.tencent.mn1
微信版本:  8.0.70.2
采集日期:  2026-05-21
采集工具:  Frida 17.9.10 动态探针 T09 v1~v18
```

**本文档所有类名、方法名、字段名均为微信 8.0.70 的混淆名。**

```
⚠️  微信每个版本混淆名全部变化，以下名称仅对 8.0.70 有效：
    - WeChat 8.0.71 的对应类名已经不同
    - WeChat 8.0.66 / 8.0.68 的对应类名更不同
    - 禁止将本文档的混淆类名/方法名直接复制到 Guard Native 代码中
    - Guard Native 当前底座：微信 8.0.71（需独立调研混淆名）
```

**本文档的参考价值是架构思路和功能实现逻辑，不是具体类名。**

---

## 一、UI 注入架构

### 1.1 设置入口注入点

**已实证（截图 2）**：

```
微信"我" → 设置页
com.tencent.mm.plugin.setting.ui.setting_new.MainSettingsUI
                ↑
        Catfish 在此页顶部注入 "微密友设置" 条目
```

- Hook 目标：`MainSettingsUI.onCreate()` 或 `onResume()`
- 注入位置：**顶部第一行**（搜索框下方）
- VISIBLE 态：显示"微密友设置"
- HIDDEN 态：条目消失，不留痕迹

**Activity 完整栈（截图 2）：**
```
com.tencent.mm.plugin.setting.ui.setting_new.MainSettingsUI  ← 当前
com.tencent.mm.plugin.setting.ui.setting.SettingMyUI
com.tencent.mm.plugin.setting.ui.setting_new.MainSettingsUI
com.tencent.mm.ui.LauncherUI
```

### 1.2 密友设置页面内容（截图 1）

| 功能项 | 控件 | 默认值 | 备注 |
|---|---|---|---|
| 显示未读消息数 | Toggle | ON | 密友消息未读角标 |
| 消息防撤回 | Toggle | OFF | C1 |
| 好友语音转发 | Toggle | OFF | C5 |
| 隐藏朋友圈分组 | Toggle | OFF | 隐藏可见范围图标 |
| 显示密友通知 | 三选一（关/震/音）| 关闭 | 通知静音策略 |
| 密友列表 | 跳转 | 已选1个 | A2 |
| 密群列表 | 跳转 | 去选择 | A3 |
| 密码设置 | 跳转 | 去设置 | A4 |
| 位置功能 | Toggle | OFF | E2 虚拟定位 |
| 微密友权限 | 跳转 | 点击授权 | 授权入口 |
| 到期时间 | 展示 | 未授权 | 授权状态 |
| 当前版本 | 展示 | v8.0.70.2 | 版本号 |

**密友设置页 Activity 栈（截图 1）：**
```
[密友设置页]
com.tencent.mm.plugin.setting.ui.setting.SettingMyUI
com.tencent.mm.plugin.setting.ui.setting_new.MainSettingsUI
com.tencent.mm.ui.LauncherUI
com.tencent.mm.plugin.fts.ui.FTSMainUI   ← 全局搜索页（111111 密码触发来源）
```

### 1.3 SettingsEntry 完整方法表（v17 实证）

```java
// UI 构建
createSettingUI()              // 创建整个密友设置页 UI
createSettingItem(...)         // 创建单个设置项
showSettingItem / showSettingItem2(...)  // 显示设置项

// 页面入口
showNewSettingUI()             // 打开密友设置主页
showNewPassUI()                // 打开密码设置页
showNewActiveCodeUI()          // 打开激活码页（授权）
showNewAudioSelView()          // 打开语音转发目标选择页
showMyPassView()               // 显示密码输入视图

// 生命周期
entryInit() / entryInit2()     // 首次注入初始化
onActivityResult(...)          // 接收选人 UI 结果
onClick(...)                   // 点击事件处理
refreshSettingUI()             // 刷新设置页（开关变化时）

// 辅助
findListView / getListView / getRecyclerView / findViewByTag
alert / notice(...)
```

**注入原理**：`SettingsEntry.entryInit()` 在 `MainSettingsUI.onResume()` 后调用，
通过 `findListView()` 或 `getRecyclerView()` 找到设置页列表，顶部插入"密友设置"条目。

---

## 二、B 模块触发机制（动态已证）

### 2.1 FTS 搜索框 111111 → 解锁显形

**机制路径（v11 实证）：**
```
放大镜 → FTSMainUI（全局搜索）
         ↓ Catfish hook TextWatcher on EditText
         每次按键 → monitorFtsEdit(currentText, FTSMainUI实例)
         ↓ currentText == "111111"
         → invokeSearchOnBackPressed()  ← 关搜索页
         → 切 VISIBLE 态
```

**FTS Activity 类名（8.0.70）：**
```
com.tencent.mm.plugin.fts.ui.FTSMainUI
```

**关键方法签名（v11 动态实证）：**
```java
UserControll.monitorFtsEdit(CharSequence text, Activity activity) → void
UserControll.addEditWatcher(EditText et) → void
UserControll.invokeSearchOnBackPressed() → void
UserControll.csn(String stateName) → ?
UserControll.hookFts(Object resultItem, View view) → void  // HIDDEN 态过滤
UserControll.hookSearchContact(String wxid) → ?
```

**动态数据（v11 捕获）：**
```
[monitorFtsEdit] FIRED params=("1111111111", FTSMainUI@77dee5c)
```
- 第一参数 = 当前搜索框文本（每次按键触发）
- 第二参数 = FTSMainUI 实例
- 内部逻辑：`if (text.equals("111111")) → invokeSearchOnBackPressed() + 切 VISIBLE`

**⚠️ 坑记录（T09 实证）：**
- hook monitorFtsEdit 后若未正确透传原始参数，TextWatcher 注册失败 → 111111 失效，需重启 App

### 2.2 B2 切后台 → 立即隐藏（v13+v16b 完整实证）

**完整调用链（v16b 实证）：**
```
Home/Recent 键
  → ActivityControll.notifyStateChanged()   ← 核心入口
  → ShakeHandler.stop()                     ← 停传感器省电
  → ActivityControll.restartLauncher(false) ← 通知主界面重绘（隐藏态）
  → isVipMode: true → false                 ← 状态机切 HIDDEN

回前台
  → ActivityControll.restartLauncher(true/false)
  → ActivityControll.backToLauncher(true)
  → ShakeHandler.start()                    ← 重启传感器
  → ShakeHandler.notifyListeners()

密友设置注入时序
  → ActivityControll.startMyNewSetting(MainSettingsUI实例)
  → ActivityControll.setSettingsEntry(SettingsEntry实例)
```

**关键事实：**
- ✅ 核心入口 = `ActivityControll.notifyStateChanged()`
- ✅ `isAppOnForeground()` 持续轮询，高频但轻量
- ❌ 不走 BroadcastReceiver.CLOSE_SYSTEM_DIALOGS（前期推测错误）

**Guard B2 对照**：`ActivityLifecycleCallbacks.onActivityStopped` + `CLOSE_SYSTEM_DIALOGS` 兜底，效果等价，风险更低。

### 2.3 全局搜索拦截（HIDDEN 态，v12 实证）

**hookFts(Object item, View view) → void**：所有 FTS 结果都过此门控

| 条目类型 | 含义 |
|---|---|
| `hy2.g0` | 联系人结果（含密友 wxid 搜索）|
| `hy2.u1` | 聊天记录 |
| `hy2.s0` | 分类标题 |
| `hy2.p0` | 公众号/小程序 |
| `l71.k` | "更多"按钮 |

- `hookFts` 是 **void 方法**，直接操作 View（GONE/height=0）
- `hookSearchContact(String wxid)` 是"添加朋友"页的独立入口，不在全局搜索路径

### 2.4 B1 摇一摇（v16b 实证）

```
[B1-Shake.start] ()                         ← 回前台/开启时启动监听
[B1-Shake.onAccuracyChanged] (Accelerometer, 3)
[B1-Shake.onSensorChanged] (SensorEvent)    ← 高频传感器数据
[B1-Shake.stop] ()                          ← 切后台时停止监听
[B1-Shake.notifyListeners] ()               ← 摇一摇阈值达到时通知
```

**ShakeHandler** 独立类，实现 `SensorEventListener`，切后台自动 stop，回前台自动 start。

### 2.5 锁屏 B5

通过 ActivityControll 统一管理，`BroadcastReceiver(ACTION_SCREEN_OFF)` 触发 `notifyStateChanged()`。

### 2.6 通知伪装 + push 进程常驻（v17c 实证）

**清后台后还能拦截通知的原因：**
```
com.tencent.mn1:push 进程常驻（系统不杀）
  ↓ Catfish LSPosed 同时注入 push 进程
  ↓ push 进程收到推送
  → NmsHookInvocationHandler.invoke(method="enqueueNotification", args)
  → setNotification(Context, Notification) 改造通知
  → 密友消息 → 改造为来自 weixin wxid 的通知（伪装微信团队）
```

**Guard v2 C2 注意**：LSPosed scope 需同时覆盖 push 进程（当前 v1 只 hook 主进程）。

---

## 三、UserControll 完整方法表（8.0.70 动态扫描，55 个方法）

```
addEditWatcher(EditText), getInstance, hasSnsCommentOne, hookSnsCommentOne,
invokeSearchOnBackPressed, setNotification, addBlackList(List), addBlackList2(ArrayList),
alA(int, List) → String, checkCallingUserSecret(String) → boolean,
checkUserSecret(String) → boolean, ckSetLocation(Activity, View, Object) → boolean,
csn(String), dkF(List) → int, emptyChatting(int, String) → int,
enterVipMode[高频轮询, 禁止hook], exitVipMode[高频轮询, 禁止hook],
hasChattingUser(String) → boolean, hkL(List) → String,
hookAddressInfo(String) → boolean, hookContactCount(int) → int,
hookConverBack() → ?, hookFinder(String) → boolean,
hookFriendStatus, hookFriendStatusItem, hookFriendStatusList(List)[×2],
hookFriendStatusList2, hookFts(Object, View) → void,
hookLabUser(List) → List, hookLabUserEx(ArrayList) → void,
hookRecent(List), hookSearchContact(String) → ?,
hookSelectUser(HashSet, Intent) → void,
hookSns(int, BaseAdapter, View) → void,
hookSns2(String, View) → void,
hookSnsCommentDetail(Object) → void,
hookSnsComments(LinkedList), hookSnsGroup() → boolean,
hookSnsLikes(Object, Object), hookSnsObject(Object) → void,
hookSnsObject2(Object) → void,
hookStatusTopics(List), hookTransFlag() → boolean,
hookUserLab(String) → boolean, init, isVipMode() → boolean,
monitorFtsEdit(CharSequence, Activity) → void,
playSound() → void, registerEditText(EditText) → void,
replaceNotification(Message) → void, setSecretAudioIdx(int, String) → void,
setNotification(Context, Notification) → Notification,
showUnReadMsgCount(int) → int, ttt() → boolean, vibrate() → void, vipDisable()
```

---

## 四、MainEntry 完整方法表（8.0.70 动态扫描）

```
hookNewCon(List), kc(int) → int,
hookNotification(Message) → void,
hookSns(int, BaseAdapter, View),
hookSns2(String, View) → void,
hookSnsCommentDetail(Object),
hookSnsComments(LinkedList),
hookSnsGroup() → boolean,
hookSnsLikes(Object, Object),
hookSnsMsgList,
hookSnsObject(Object) → void,
hookSnsObject2(Object) → void
```

---

## 五、SNS 朋友圈过滤调用链（动态已证）

**滑动朋友圈，每条动态固定触发顺序：**
```
1. hookSns2(wxid, View)              → void，直接操作 View 隐藏整条
2. hookSnsObject(SnsObject)          → 详情页单条（feed 不触发）
3. hookSnsCommentOne("CommentUserList", SnsObject)  → bool，true=过滤我的评论
4. hookSnsCommentOne("LikeUserList", SnsObject)     → bool，true=过滤我的点赞
```

**关键结论：**
- `hookSns2` 是 **void 方法**，直接对 View 做 setVisibility(GONE)/setHeight(0)
- `hookSnsCommentOne` 返回 **boolean**，true = 从列表移除该用户
- `hookSnsLikes(Object, Object)` 未在 feed 滑动时触发
- `hookSnsComments(LinkedList)` 未在 feed 滑动时触发

**隐藏朋友圈分组可见图标（hookSnsGroup）：**
```java
UserControll.hookSnsGroup() → boolean  // 无参！是查询开关的 getter
NativeHelper.getHideGroup() → boolean  // v18 实测返回 true（功能已开启）
```
- 不是过滤 List 的方法，是"是否开启分组隐藏"的布尔开关
- 实际分组图标过滤在渲染层实现（hook SnsObject item View 的图标可见性）

---

## 六、朋友圈小红点过滤链路（tab 图标红点）

### 写入链路（密友给你点赞时）

```
push 进程收到点赞推送
  ↓
w1 (SnsCommentStorage) 写入：
  w1.insertLike(SnsAction)
    └── SnsAction.fromUserName = 点赞者 wxid（密友）
  ↓
w1.y (jadx名: f178674y) += 1     ← 互动计数 +1
  ↓
WeChatTabRedDotEvent / TabRedDotChangeEvent 触发
  ↓
ns.c.b = true    ← 发现 tab 红点控制字段（8.0.71 实证）
ww2.c.b = true   ← 镜像字段
  ↓
底部导航栏"发现" tab 图标显示红点
```

### 消费链路（进消息页后消失）

```
用户进入 SnsMsgUIWithAll（朋友圈互动消息页）
  ↓
FindMoreFriendsUI.L1() 被调用
  ↓
w1.E1()（getToNotifyCount）→ 返回 w1.y
  ↓
重算 z19 = (!x.empty || y!=0)
  ↓
ns.c.b = z19 → 变为 false → 红点消失
```

### Guard 过滤正确插入点（只压密友的）

| 插入点 | 方法 | 时机 | 是否精准 |
|---|---|---|---|
| **① 写入前** | `w1.insertLike(SnsAction)` 检查 `fromUserName` | 密友点赞时 | ✅ 最精准 |
| **② 计数出口** | `w1.E1()` → 返回 0 | 每次读计数 | ⚠️ 清掉所有人 |
| **③ L1 聚合** | `FindMoreFriendsUI.L1()` after 检查 `this.x` | 进发现页时 | ✅ 精准但晚 |
| **④ 冷启动** | 持久化恢复路径 | 冷启动 | ❌ 路径未知 |

**冷启动红点持久化问题**：`w1.y` 在进程被杀前已增加，下次冷启动从本地 DB 恢复，绕过所有 Java hook。根本解法是①写入前拦截，使 `w1.y` 根本不增加，持久化值为 0。

---

## 七、Catfish 完整杂项功能签名（v14 实证 2026-05-21）

| 类别 | Catfish 方法签名 | 用途 | Guard 模块 |
|---|---|---|---|
| **E2 虚拟定位** | `ckSetLocation(Activity, View, Object) → boolean` | 长按位置按钮，弹"伪装位置"选择 | E2（v3） |
| **E2 地址过滤** | `hookAddressInfo(String) → boolean` | 地址栏 hook | E2 |
| **C1 防撤回** | `replaceNotification(Message) → void` | 撤回 Message 到达时替换 | C1（v2） |
| **C2 通知伪装** | `setNotification(Context, Notification) → Notification` | 改造通知 | C2（v2） |
| **C5 语音转发** | `setSecretAudioIdx(int, String) → void` | 标记语音转发目标 | C5（v2+） |
| **反馈** | `playSound() / vibrate()` | 状态切换声/震 | A 总闸 |
| **E1 实验室** | `hookLabUser(List) → List` / `hookLabUserEx(ArrayList)` | 实验室列表过滤 | E1（v3） |
| **E1 用户级** | `hookUserLab(String) → boolean` | 单 wxid 实验室判定 | E1 |
| **黑名单** | `addBlackList(List) / addBlackList2(ArrayList)` | 黑名单列表过滤 | A2 增强 |
| **通讯录计数** | `hookContactCount(int) → int` | 通讯录总数扣除密友 | F07 增强 |
| **视频号** | `hookFinder(String) → boolean` | 视频号 wxid 判定 | 未规划 |
| **选人拦截** | `hookSelectUser(HashSet, Intent) → void` | 转发/分享选人过滤 | C5 关联 |
| **密码校验** | `checkUserSecret(String) → boolean` | 主密码校验 | A4 |
| **调用方密码** | `checkCallingUserSecret(String) → boolean` | 反盗版双层校验 | A4 |
| **聊天清理** | `emptyChatting(int, String) → int` | 切 HIDDEN 时清聊天上下文 | 未规划 |
| **未读修正** | `showUnReadMsgCount(int) → int` | 未读数显示修正 | 角标 |
| **转发标志** | `hookTransFlag() → boolean` | 转发开关 | 未规划 |
| **短名 alA** | `alA(int, List) → String` | 推测相册列表适配 | 未知 |
| **短名 dkF** | `dkF(List) → int` | 推测密友列表计数 | 未知 |
| **短名 hkL** | `hkL(List) → String` | 推测字段提取 | 未知 |
| **短名 ttt** | `ttt() → boolean` | 未知 | 未知 |

**关键发现：**
1. E1 步数装b 在 Catfish 8.0.70 未实现（UserControll 无 hookSteps 方法）
2. C2 通知伪装：hook `setNotification(Context, Notification) → Notification` after，改造 title/text
3. A4 密码双层校验：`checkUserSecret` 正常路径，`checkCallingUserSecret` 反盗版路径

---

## 八、NativeHelper JNI 完整方法表（v16 静态扫描，禁止 hook 会卡死）

**底层引擎**：`top.canyie.pine`（Pine native hook 框架）。Guard 铁律 #2 禁止引入。

```
activeCard(String, Context) → String          // 授权激活
createSignatureHook() → MethodHook            // Pine 签名 hook（反检测）
createTraceSigHook() → MethodHook
getAudioFile(String, String) → int
getBuySwitch / getDoHidden / getFakeLocation / getHideGroup → boolean
getExpiredTime / getWUsername / getm → String
getLocationInfo → String                      // 虚拟位置数据
getVipEnable / getVerified → boolean
getRevokeMsg / getTransVoiceMsg / getSecretNotification → boolean
getSecretAudioIdx → int / getSecretAudioName → String
hookOpen / hookReadlink / hookSignature → void  // native 层 hook（禁动）
invokeAfterCall / invokeBeforeCall(long, CallFrame)  // Pine 回调（绝对禁动）
onActiveResponse(boolean, String, long) → void
playAudio / playStop → int
registerUser(String, Context) → void
releaseHook(long) → void                      // 禁动
reqAddress(String) → String
setBuySwitch / setDoHidden / setFakeLocation / setHideGroup → void
setLocationInfo / setPassword / setPirateTips / setRevokeMsg → void
setSecretAudioIdx / setSecretAudioName / setTransVoiceMsg / setVipEnable → void
showDialog / showNotice / showToast → void
start(Application) → void
verifyUser(Context, boolean) → String
addb(List) → List, chkPwd(Context, String) → boolean
```

**关键结论：**
1. Catfish 核心功能全部下沉到 native SO，Java 只是 getter/setter 桥接
2. Pine 框架直接集成在 SO 中，用于 hook WeChat 方法
3. Guard 纯 Java 路线完全规避此风险路径

---

## 九、ActivityControll 完整触发方法汇总（v16b 实证，20 个唯一方法）

```
notifyStateChanged()                          ← 所有状态变化统一入口
isAppOnForeground() → boolean                 ← 轮询前台状态（高频，轻量）
getCurrActivity() → Activity
getSearchActivity() → FTSMainUI              ← 持有搜索页引用
getLauncherActivity() → LauncherUI
needInterruptActivity(Activity) → void
restartLauncher(boolean)                      ← 重绘主界面
backToLauncher(boolean)
startMyNewSetting(Activity)                   ← 打开密友设置页
setSettingsEntry(SettingsEntry)               ← 设置 UI 注入
startSelectContact(Activity)                  ← 打开选密友联系人页
acceptSelectContactResult(SelectContactUI)    ← 接收选人结果
getResultIntentViaUnsafe(Activity) → Intent
```

**密友选人完整流程：**
```
密友设置页 → 点"密友列表"
  → B2-Act.startSelectContact(SettingsUI)
  → 微信 SelectContactUI 弹出
  → 用户选完确认
  → B2-Act.acceptSelectContactResult(SelectContactUI)
  → B2-Act.getResultIntentViaUnsafe(SelectContactUI) → Intent（含 wxid）
  → 存入 NativeHelper.setVipMember()
```

---

## 十、状态机持久化机制（Catfish vs Guard）

### Catfish 持久化（NativeHelper JNI）

```java
NativeHelper.getDoHidden() → boolean   // HIDDEN/VISIBLE 当前态
NativeHelper.getVipEnable() → boolean  // 是否已授权
NativeHelper.getExpiredTime() → String // 授权过期时间
NativeHelper.setDoHidden(boolean)
NativeHelper.setVipEnable(boolean)
```

**v17 实测数据：**
```
doHidden   = true        ← 当前 HIDDEN
vipEnable  = true        ← 授权有效（patch 的）
expiredTime= 未授权      ← native 层无真实授权
wUsername  = wxid_bvcent54icpn22
verified   = false
```

**授权后默认态推测**：
- 首次授权 → `setVipEnable(true)` + `setDoHidden(false)` → **VISIBLE**
- 第一次按 Home → `setDoHidden(true)` → HIDDEN，持久化
- 进程重启 → `getDoHidden()` 恢复上次状态

### Guard 持久化（SharedPreferences commit）

```java
Bridge.putInt("smst", state.code)  // commit 强同步
StateMachine.restoreState()        // 冷启动恢复
```

**Guard 默认态**：首次安装默认 **HIDDEN**（比 Catfish 更安全）。

---

## 十一、防撤回 + 语音转发 + 密码（v17 实证）

### WmyRevokeMsg（C1 防撤回）

```java
WmyRevokeMsg.init(Object hooker, Object context) → void
WmyRevokeMsg.revoke(String msgId, Map<?,?> extras, Object msg) → void
```
- `revoke` 是核心：msgId=消息ID，extras 含原始消息，msg=消息对象
- Catfish 在 `revoke` 里保存原始消息，覆盖微信撤回逻辑

### 语音转发（NativeHelper + UserControll）

```java
NativeHelper.getTransVoiceMsg() → boolean     // 语音转发总开关
NativeHelper.setTransVoiceMsg(boolean)
NativeHelper.setSecretAudioIdx(int idx)       // 目标联系人序号
NativeHelper.setSecretAudioName(String name)
NativeHelper.getSecretAudioIdx() → int
NativeHelper.playAudio(String) → int
```

### 密码持久化

```java
NativeHelper.setPassword(String pwd) → void    // 写入 native 加密存储
NativeHelper.chkPwd(Context, String) → boolean // 校验
UserControll.checkUserSecret(String) → boolean  // Java 层入口
```

---

## 十二、包名劫持 + 签名伪造三层架构（逆向完整分析）

> ⚠️ Guard 绝对禁止引入此方案（F-23 JNI 铁律 + 封号风险），仅存档研究

### 第一层：PMS Binder 代理

```java
// ServiceManagerWraper.hookPMS()
// PmsHookBinderInvocationHandler.java:37
packageInfo.packageName = "com.tencent.mm";  // 强制改写包名
```

### 第二层：Parcel 反序列化劫持

```java
// SigCracker.hookSignature() → 替换 PackageInfo.CREATOR
packageInfo.signatures[0] = fakeSignature;                            // 注入腾讯官方签名
packageInfo.signingInfo.getApkContentsSigners()[0] = fakeSignature;
// 清空 Parcel.mCreators 和 sPairedCreators 缓存
// 靠 HiddenApiExemptions 绕过 Android P+
```

### 第三层：Native 文件路径重定向（libbypassmm.so）

```
hookOpen:     open 改造版 APK 路径 → 重定向到 assets/origin.apk（255MB 官方包）
hookReadlink: /proc/self/fd/ 返回假路径
SignatureHookImpl: mFakeSig / mRealSig，native 层签名查询返回腾讯证书
```

### libwechatsd.so 签名校验

```
checkSignature  @ 0x2e7770  (11KB) — 检查签名证书（非 APK 文件哈希）
getSignMd5Str   @ 0x245778  (18KB) — 对签名字节做 MD5 摘要
```

**关键结论：Catfish 没有 APK 文件完整性哈希自校验，40+ MMKV 字段裸数据无 HMAC。**

### 完整链路

```
assets/origin.apk (255MB 官方包)
  → libwechatsd.so 提取官方签名字节
    → SigCracker + ServiceManagerWraper
      → 任何 getPackageInfo("com.tencent.mm") → 包名+签名全伪造

native 层:
  libbypassmm.so → hookOpen / hookReadlink → 文件路径重定向

三层覆盖：Java Binder + Parcel 反序列化 + native 文件 IO
```

| | Catfish | Guard |
|---|---|---|
| 包名 | mn1 对外伪装 mm | mm（真实，LSPatch 注入）|
| 签名 | 三层伪造腾讯证书 | LSPatch 劫持模式（v3 P32）|
| native 依赖 | libbypassmm + libwechatsd | 无（v1/v2 铁律）|
| APK 体积 | +255MB（内嵌 origin.apk）| 仅模块本体（KB 级）|

---

## 十三、Catfish 整体架构总结（T09 完整逆向结论）

```
┌─────────────────────────────────────────────────────┐
│                 Catfish 8.0.70 架构                  │
├──────────────┬──────────────────────────────────────┤
│ 入口         │ MainEntry（LSPosed/Pine 双模式）      │
│ 核心控制     │ UserControll（55个方法，Java桥接）    │
│ 状态持久化   │ NativeHelper(JNI) → native SO 加密   │
│ hook 引擎    │ Pine（top.canyie.pine）               │
│ 身份伪造     │ 三层：PMS Binder+Parcel+libbypassmm  │
│ UI 注入      │ SettingsEntry → MainSettingsUI 顶部  │
│ 切后台       │ ActivityControll.notifyStateChanged  │
│ 摇一摇       │ ShakeHandler (SensorEventListener)   │
│ 虚拟定位     │ MyLocation + LocationInfo            │
│ 防撤回       │ WmyRevokeMsg.revoke(id, extras, msg) │
│ 通知伪装     │ NmsHookInvocationHandler (Binder代理)│
│ 搜索解锁     │ monitorFtsEdit(CharSequence, Activity│
│ 签名校验     │ SigCracker（8个方法）                │
│ 授权         │ VerifyHandler + MyAuth + NativeHelper│
└──────────────┴──────────────────────────────────────┘
```

**Guard Native 差异化优势：**
1. 纯 Java，无 native SO，封号特征面极小
2. 默认 HIDDEN（Catfish 默认 VISIBLE）
3. 无内嵌原包（Catfish +255MB）
4. MMKV 有 seed 命名（Catfish 裸 key 无保护）
5. Kill Switch 远程一键停用（Catfish 无此机制）

---

## 附录 A：Guard Native P26 设置注入参考架构（v2 范围）

> 此处仅列架构思路，具体实现在 Guard P26 时另行开 P 任务

```
Hook: MainSettingsUI.onResume()
操作: findListView() / getRecyclerView() → index=0 插入"Guard 设置"条目
状态联动: isActive() == true → 显示；false → 不插入
```

**8.0.70 类名（仅参考，8.0.71 会变）：**
```
com.tencent.mm.plugin.setting.ui.setting_new.MainSettingsUI
com.tencent.mm.plugin.setting.ui.setting.SettingMyUI
```

## 附录 B：待补充项

- [ ] csn(String) 参数含义（状态名枚举）
- [ ] `hookFts` / `hookSearchContact` 返回值的完整行为验证
- [ ] 位置功能具体 hook 点（MyLocation → 微信地图 API 劫持路径）
- [ ] 朋友圈分组可见图标 View 的具体 ID/class（需 layout inspector）
