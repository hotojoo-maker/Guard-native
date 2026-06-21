# P20B brief — 状态机 B 模块 + 历史 P21 入口

> 执行窗口直接读这个，不用翻 TASK_BOARD 全文
> 接手人：W1 主开发 / 2026-05-21 开 / 半天工时
> 历史说明：早期把 P21 朋友圈小红点和 B 触发器写在同一 brief；当前 P21/P21B 已迁到 `07_archive_归档/P21_MomentsRedDot/worklog.md`。

---

## 当前状态

🟡 P20B 历史任务。状态机 3 态已实现，密码进出已通；P21 小红点已独立收口：
1. **P21/P21B 朋友圈小红点**：Layer0b + WithAll/bm live Cursor ✅，详见 P21 worklog。
2. B1/B2/B5 状态机自动触发器。
3. B7 屏蔽更新小红点（用户已给 8.0.71 类名）。
4. `KEY_STATE` 写盘 apply → commit（防杀进程丢盘）。

## 本任务实现的 hook 点

### 历史项：朋友圈小红点（MomentsRedDotGuard）

| 候选 hook | 类名（用户调研，8.0.71）| 命中预期 |
|---|---|---|
| 主线 A | `com.tencent.mm.plugin.sns.storage.SnsCommentStorage.E1(...)` after → 隐藏态返回 0 | 评论数 = 0 → 无红点 |
| 主线 B | `FriendSnsPreference` 相关字段 | 朋友圈偏好红点状态 |
| 兜底 C | `FindMoreFriendsUI.M1() / l0()` after → 返回 false / 0 | 主界面发现入口红点 |
| 兜底 D | Event 通道拦截：`FindMoreFriendEntryRedDotEvent` / `EnterSnsTimeLineUIEvent` / `NotifyTabTipsToShowEvent` / `ResetBadgeCountEvent` | 阻断红点事件分发 |

> iOS 端实证：密友 hook 了 `WCTimeLineViewController.viewDidAppear` 过滤内容，但小红点 badge 走**独立通知路径**没覆盖 → 隐藏密友后仍冒红点。Android 端我们提前解决这个差异化点。

> 当前结论：P21 Layer0b + P21B WithAll/bm live Cursor 已装机；本 brief 不再承载 P21 待办。

### 优先 #2：状态机自动触发器（TriggerGuard）

| #  | 功能 | 监听点（Android API，非微信类）| 默认 | 触发动作 |
|----|------|-----|:--:|------|
| B1 | 摇一摇 | `SensorManager.TYPE_ACCELEROMETER` 阈值 15 m/s² | **关** | `StateMachine.enterHidden()` |
| B2 | 切后台 | `ActivityLifecycleCallbacks.onActivityStopped` 进程计数归零 | **开** | 同上 |
| B5 | 锁屏 | `BroadcastReceiver(ACTION_SCREEN_OFF)` | **开** | 同上 |
| **B2 兜底** | Home / 手势上划 / Recent | `BroadcastReceiver(ACTION_CLOSE_SYSTEM_DIALOGS)` | 跟 B2 同开关 | 同上 |

**B3 / B4 不在 P21 范围**（B3 被 B2 覆盖；B4 返回键判断页面复杂、延后）

### 优先 #3：屏蔽更新小红点（UpdateGuard）

| hook 点（用户调研 8.0.71）| 动作 |
|---|---|
| `com.tencent.mm.ui.setting.SettingsAboutMicroMsgUI` 字段（候选 `fl4.o`）| 红点 View visibility → GONE |
| `SettingsUI.P7()` after | 返回值改 false（如果是 hasUpdate getter）|

## 状态机入口（已就位，本任务调用方）

```java
StateMachine.getInstance().enterHidden();   // 任意触发器调它
StateMachine.getInstance().isActive();      // 已是 HIDDEN 直接返 true
```

`enterHidden()` 内部：
1. 改 mState = HIDDEN
2. mActive = true
3. `Bridge.putInt(KEY_STATE, HIDDEN.code)` **同步落盘**（commit 而非 apply）
4. notifyListeners → 所有 Filter 立即重 evaluate

> **本任务要改 1 行**：`Bridge.putInt` 当前 apply 异步，改成 commit 强同步（防杀进程丢盘）

## 冷启动恢复时序（不能动）

```
ModuleMain.onApplicationCreated:
  ...
  6. StateMachine.restoreState()    ← 必须在 hook install 之前
  7. install 5 个 hook
```

这是 P15 已经踩对的顺序，**P21 禁止反过来**。

## 相关 F-xx（只看这些）

| 编号 | 一句话 |
|------|--------|
| F-24 | 禁止 `extends Service` — Notification 用 `NotificationManager.notify()`，悬浮窗用 `WindowManager.addView()`；B 模块的 Receiver 用 `Context.registerReceiver()` 不要建 Service |
| F-25 | 所有 `findAndHookMethod` 必须 `catch (Throwable)` — B7 hook 微信类时遵守 |
| 铁律 8 | `verifiedbootstate` 红线 = 38 — B 模块不动这个，但装机后跑一次 frida_stats 确认无增量 |

## 待完成

- [ ] `moduleB/TriggerGuard.java` — B1/B2/B5 三合一触发器
- [ ] `moduleB/UpdateGuard.java` — B7 更新小红点（fl4.o / SettingsAboutMicroMsgUI / SettingsUI.P7()）
- [ ] `Bridge.putInt` `KEY_STATE` 写盘改 commit
- [ ] `AppConfig` 加 4 个开关（b1/b2/b5/momentsRedDot）
- [ ] `ModuleMain` install 调用
- [ ] 装机验收（见下）

## 关键文件

```
已迁出:
  src/main/java/com/ghost/assist/moduleD/MomentsRedDotGuard.java  ← P21/P21B，见 P21 worklog

新增/修改:
  src/main/java/com/ghost/assist/moduleB/TriggerGuard.java        ← B1/B2/B5
  src/main/java/com/ghost/assist/moduleB/UpdateGuard.java         ← B7

  src/main/java/com/ghost/assist/core/Bridge.java                 ← putInt commit
  src/main/java/com/ghost/assist/core/AppConfig.java              ← 4 个 boolean
  src/main/java/com/ghost/assist/ModuleMain.java                  ← install 调用
```

## 验收（用户装机走 6 步）

```
1. 打开微信 → 看通知栏「[Guard] 状态=显」
2. 按 Home 键              → 通知栏切到「状态=隐」、震动一下
3. 重开微信                 → 通知栏「状态=隐」、会话/朋友圈密友藏住
4. 放大镜输 111111         → 通知栏切「状态=显」
5. 锁屏                    → 通知栏「状态=隐」、震动
6. 解锁回微信               → 仍隐藏（解锁后不自动显形是产品需求）
```

冷启动测试（关键）：

```
7. 隐藏态 → 强杀微信进程（adb force-stop） → 重启
   预期：从持久化恢复 HIDDEN，第一波数据不外泄
```

## 装机命令包（Cursor → Claude Code）

**Step 1 — build**

```powershell
cd "c:\Users\Me\Desktop\guard_native"
.\gradlew assembleDebug 2>&1 | Select-String "error:|cannot|symbol|BUILD"
```

**Step 2 — install**

```powershell
adb install -r "build\outputs\apk\debug\guard-native-debug.apk"
```

**Step 3 — 强停微信**

```powershell
adb shell am force-stop com.tencent.mm
```

**Step 4 — 清日志**

```powershell
adb logcat -c
```

**Step 5 — 用户手动操作（验收 6 步）**

**Step 6 — 拉日志**

```powershell
adb logcat -d 2>&1 | Select-String "NCL.*TG|NCL.*UG|NCL.*SM" | Select-Object -Last 100
```

## 风险

- **误触爆炸**：onActivityPaused（局部 Activity 切换）误当 stop → 每次点开聊天都隐藏。**应对**：只在进程级 foregroundCount 归零时触发
- **接收器延迟**：BroadcastReceiver 比 onActivityStopped 慢 100-300ms。**应对**：两个都注册做双保险
- **冷启动竞态**：第一波 addAll 可能比 restoreState 早。**应对**：P15 已踩对（restoreState → install hook 顺序），P21 不动
- **B7 阻塞主线**：8.0.71 更新小红点类名未知。**应对**：UpdateGuard 写骨架 + TODO，不阻塞 B1/B2/B5 装机
