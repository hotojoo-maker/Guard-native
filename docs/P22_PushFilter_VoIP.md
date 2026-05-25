# P22 通知隐私层 — 架构 + 完整 Hook 链路文档

> **P22 = A5 通知隐私，v1 核心隐私，不是 v2 增值。**
> 实证日期：2026-05-22 ~ 2026-05-25（含 亮屏 BUG 根因 + SF/AT 补层）
> 微信版本：8.0.71（D-014）
> 实现文件：`src/main/java/com/ghost/assist/moduleC/PushFilter.java`
>             `src/main/java/com/ghost/assist/moduleC/NotifyRouter.java`

---

## 零、架构铁律（2026-05-25 定稿）

```
┌─────────────────────────────────────────────────────────────┐
│  C++ libguardcore（:push / 主进程 init）                     │
│    → native state / auth / push_guard 早期判断               │
│    → ❌ 不 hook NotificationManager / WakeLock / 音频/UI   │
└─────────────────────────────────────────────────────────────┘
                              ↓ 只读判断
┌─────────────────────────────────────────────────────────────┐
│  Java 主进程（真正拦截层）                                    │
│    PushFilter L1  — LinkedList.add(NotificationItem) block  │
│    PushFilter NM  — NotificationManager.notify() cancel     │
│    PushFilter SF  — Service.startForeground() block ✅ NEW   │
│    PushFilter AT  — AudioTrack.play() block 🟡 NEW          │
│    PushFilter VV  — Vibrator.vibrate() block                │
│    CallGuard VC   — VoIPRenderTextureView removeView        │
│    CallGuard VW   — WakeLock.acquire() block                │
└─────────────────────────────────────────────────────────────┘

:push 进程同步安装（installForPush）:
    NM / TC / SF / VW — 主进程杀后仍能拦截 VoIP 通知
    state authority   = C++ NativeBridge.isHidden()（非 Java StateMachine）
```

| 组件 | 职责 | 状态 |
|------|------|:--:|
| PushFilter L1/NM | 后台/通知栏消息拦截 | ✅ 装机 2026-05-22 |
| PushFilter SF | startForeground 状态栏图标 + 来电通知 | ✅ 装机 2026-05-25 |
| PushFilter AT | AudioTrack.play() block — VoIP 挂断音① + STREAM_RING in-app② | ✅ 装机实证 2026-05-25（语音挂断 ×2；probe STREAM_RING ×3 确认） |
| **PushFilter MP** | **MediaPlayer.start() block — 消息叮 + 视频挂断嘟（probe ×9 确认）** | 🟡 **代码已写 2026-05-25，待装机验证** |
| **PushFilter PiP** | **Activity.enterPictureInPictureMode() block — 悬浮小窗** | 🟡 **代码已写 2026-05-25，待装机** |
| CallGuard VC | 前台 VoIP overlay 隐藏 | ✅ 装机 2026-05-24 |
| CallGuard VW | WakeLock 亮屏拦截 | ✅ 代码已写 |
| PushFilter VV | Vibrator.vibrate() 震动拦截 | ✅ 装机 2026-05-25 |
| NotifyRouter | OFF/VIBRATE/SOUND 三档策略路由 | 🟡 代码已写待装机 |
| ForegroundMute | 前台 in-app 声音 — MP+AT 已覆盖（probe 2026-05-25 实证） | 🟡 待装机验证 |
---

## 一、亮屏 BUG 根因（2026-05-25 实证）

### 根因：`fullScreenIntent` + `Service.startForeground()` 双路径

**错误排查路线**（依次排除）：

| 路径 | 探针结论 | 状态 |
|------|---------|:--:|
| PowerManager.WakeLock.acquire() | 有触发但不是主因 | ❌ 不够 |
| AudioManager.requestAudioFocus() | 0 命中（WeChat VoIP 不走 AudioFocus） | ❌ 废弃 |
| AudioManager.setMode(MODE_RINGTONE=1) | 0 命中 | ❌ 废弃 |
| TelecomManager.addNewIncomingCall() | 0 命中（WeChat VoIP 不走 TelecomManager） | ❌ 废弃 |
| **Notification.fullScreenIntent** | **✅ 主因** — Android Framework 收到 fullScreenIntent 自动唤屏 | ✅ |
| **Service.startForeground()** | **✅ 状态栏图标来源** — 绕过 NM.notify()，直接发前台服务通知 | ✅ |

**关键发现**：微信 VoIP 来电走两条通知路径：
1. `NM.notify(ch=reminder_channel_id)` — 带 `fullScreenIntent`，Android 系统自动唤屏
2. `Service.startForeground(id, n)` — 产生状态栏通话图标，完全绕过 `NotificationManager.notify()`

**装机实证日志（2026-05-25）**：
```
17:52:38.832  [PF:VC] streams muted ring/voice/music=8/1/0
17:52:38.832  [PF:SF] blocked startForeground
17:52:38.843  [PF:NM] cancel fullScreenIntent id=41 ch=voip_notify_channel_silent
17:52:38.849  [PF:NM] cancel voip ch=voip_ringtone_channel_xxx
17:52:38.852  [PF:NM] cancel ongoing-call id=-525958226
```

---

## 二、各层详细实现

### L1 — LinkedList.add(NotificationItem)

**作用**：后台消息最早拦截点，在入队前 block，NM.notify() 永远收不到该 item。

```java
// 识别：item.getClass().getName() == NI_CLASS
// 读 wxid：item.getClass().getDeclaredField("h")（Tinker classloader 问题，禁止 findField）
// NotifyPolicy 分支：
//   BLOCK   → setResult(false)，NM hook 200ms 内 cancel bypass
//   VIBRATE → 放行，sL1VibrateMode=true（NM 剥离声音，注入震动 pattern）
//   SOUND   → 放行，sL1VibrateMode=false（NM 替换自定义铃声）
```

**坑**：`item.getClass().getDeclaredField("h")` 需要用 `item.getClass()` 找字段，不能用 `XposedHelpers.findField(NI_CLASS, "h")`（Tinker classloader 不同）。

---

### NM — NotificationManager.notify()

**作用**：VoIP 来电通知 + L1 漏网兜底。

**两个触发条件**：
1. channel 含 `voip` / `ringtone` / `call` / `ring` / `reminder` → HIDDEN 态 cancel
2. L1 block 后 200ms 内到来的任意通知 → cancel（gap=3ms 实证，安全余量足够）
3. `sVoIPCallPending=true` 时，通知文字含通话关键词 → cancel ongoing-call 通知

**关键点**：`fullScreenIntent` 判断必须在 `cancel` **之前** strip，否则：
- 仅 strip fullScreenIntent 而不 `setResult(null)` → 通知仍会 post 成 banner（有震动/声音）
- 正确做法：`param.setResult(null)` 完全取消

**VoIP 渠道名（2026-05-24 diag 实证）**：
```
reminder_channel_id        — 前台来电（id=40，带 fullScreenIntent）
voip_notify_channel_silent — 静音来电通知
voip_ringtone_channel_xxx  — 铃声来电渠道
```

---

### SF — Service.startForeground()

**作用**：拦截状态栏通话图标（`NM.notify()` 之外的平行路径）。

**原理**：`Service.startForeground(int id, Notification n)` 由 Android Framework 直接在状态栏注册前台服务 icon，绕过应用层的 `NotificationManager.notify()`。微信 VoIP 的"语音通话中"图标走此路径。

**识别规则**：
```java
boolean isCallNotif =
    n.fullScreenIntent != null               // 来电唤屏意图
    || channelName contains "voip/call/ring" // 通话渠道名
    || text contains ["通话","通話","call","视频","语音"]; // 关键词

if (isCallNotif && HIDDEN) {
    muteVoIPStreams(am);   // 立即静音（SF 杀掉 service，VC 不一定触发）
    armVoIPPending();      // 设 flag + 35s fallback 清零
    param.setResult(null); // 取消 startForeground
}
```

**两个 overload**：
```java
// overload 1: API < 29
startForeground(int id, Notification notification)
// overload 2: API >= 29
startForeground(int id, Notification notification, int foregroundServiceType)
```
两个都需要 hook（见 `installStartForegroundBlock`）。

**坑**：
- `param.setResult(null)` 对 void 方法有效（强制 return）
- 获取 AudioManager：`((Service) param.thisObject).getSystemService(AUDIO_SERVICE)`
- :push 进程同样需要此 hook（`installForPush` 调用）

---

### VC — VoIPRenderTextureView.onAttachedToWindow（CallGuard）

**作用**：隐藏前台 VoIP 浮层界面（overlay，FrameLayout 挂 WindowManager）。

**F-26 修复**：`onAttachedToWindow` 定义在父类 `android.view.View`，`findAndHookMethod` 不遍历继承链。正确写法：
```java
Method m = View.class.getDeclaredMethod("onAttachedToWindow");
XposedBridge.hookMethod(m, hook);
// hook 内部：按 view.getClass().getName() 过滤 VoIPRenderTextureView
```

**overlay 层级**：WeChat 前台来电 overlay = `FrameLayout` 直接 attach 到 `WindowManager`，不在 Activity DecorView 下。`adb shell dumpsys activity activities` 只有 `LauncherUI`（CA 层证伪根据）。

**NotifyPolicy 分支**（VC 内）：
- OFF → 纯静默，只 removeView
- VIBRATE → removeView + `Vibrator [0,180,40,120]ms` 告知主人
- SOUND → 同 VIBRATE（自定义音 Phase 2）

**生命周期与 sVoIPCallPending 的关系**：
- 当 SF 早期 block（`startForeground` 被 null），VoIP service 被杀，VC 可能不触发
- 所以 SF 自己调 `muteVoIPStreams()` + `armVoIPPending()`，不依赖 VC 的流静音

---

### VV — Vibrator.vibrate() block

**作用**：拦截挂断震动脉冲。

**触发条件**：`sVoIPCallPending == true` 或 `sMutedVols` 有非负值（流已静音中）。

**递归防卫 sOurVibration**：
```java
// NotifyRouter.fireHangupVibration() 也调 Vibrator.vibrate()
// 若不加防护，会被 VV hook 再次拦截
sOurVibration = true;
try { NotifyRouter.fireHangupVibration(vib); }
finally { sOurVibration = false; }

// VV hook 开头：
if (sOurVibration) return; // 我们自己发的震动，跳过
```

---

### AT — AudioTrack.play() block（2026-05-25 新增）

**作用**：拦截 TRTC SDK 挂断音（"嘟"声）。

**为什么 VV 拦不住**：TRTC SDK（腾讯实时通信 C++ SDK）在挂断时播放的短音频走自己的 `AudioTrack` 实例，不触发 `Vibrator.vibrate()`，且其音量控制独立于 Android `AudioManager.setStreamVolume()`。

**实现**：
```java
// 当 sVoIPCallPending == true，block 所有 AudioTrack.play()
// sVoIPCallPending 由 armVoIPPending() 设置，35s 后自动清零
XposedHelpers.findAndHookMethod(android.media.AudioTrack.class, "play", hook);
```

**副作用考量**：`sVoIPCallPending` 只在来电窗口为 true（最长 35s），正常通话结束后由 `restoreVoIPStreams()` 提前清零（5s 后），不影响其他音频应用。

---

### VW — PowerManager.WakeLock.acquire() block

**作用**：拦截带 `ACQUIRE_CAUSES_WAKEUP` 标志的 WakeLock，防止消息通知自动亮屏。

**坑：under-locked crash**：
```
RuntimeException: WakeLock under-locked xxx
```
原因：block `acquire()` 后 WeChat 仍会调 `release()`，导致 under-lock。
修复：`WeakHashMap<PowerManager.WakeLock, Boolean> sBlockedWakeLocks` 记录被 block 的对象，`release()` hook 检查此 map，命中则 `setResult(null)`。

**相关标志（宽匹配）**：
```java
// 覆盖所有可能的亮屏 WakeLock
private static final int SCREEN_WAKE_FLAGS =
    PowerManager.SCREEN_BRIGHT_WAKE_LOCK     // 0x0000000a
    | PowerManager.ACQUIRE_CAUSES_WAKEUP     // 0x10000000
    | PowerManager.ON_AFTER_RELEASE;         // 0x20000000
```

---

### muteVoIPStreams / restoreVoIPStreams

**作用**：在 SF block 时立即静音 ring/voice/music 三个 stream（VC 可能不触发）。

**坑：adjustStreamVolume(ADJUST_MUTE) 触发系统 toast**：
```
"静音模式已开启" — 系统 UI 弹出
```
原因：`AudioManager.adjustStreamVolume(STREAM_RING, ADJUST_MUTE, 0)` 会触发铃声静音的系统 UI。
修复（2026-05-25）：删除此行，仅保留 `setStreamVolume(stream, 0, 0)`，效果相同无 toast。

**恢复时机**：VC 调 `restoreVoIPStreams(am)` → 5s `handler.postDelayed`；或 `sClearVoIPPending`（35s fallback）。

---

### sVoIPCallPending 生命周期

```
来电信号
  │
  ├─ NM hook 识别 fullScreenIntent / voip channel
  │    └─ armVoIPPending() → sVoIPCallPending=true + 35s 定时器
  │
  ├─ SF hook 识别来电 startForeground
  │    └─ armVoIPPending() + muteVoIPStreams()
  │
  ├─ VC hook (VoIPRenderTextureView.onAttachedToWindow)
  │    └─ removeView + [VIBRATE/SOUND] 自定义震动
  │    └─ 来电结束 → wm.removeView detach → restoreVoIPStreams (5s delay)
  │         └─ sVoIPCallPending = false（提前清零）
  │
  └─ 35s 定时器 sClearVoIPPending（fallback，VC 未触发时）
       └─ sVoIPCallPending = false + restoreVoIPStreams
```

**为什么需要 35s fallback**：当 SF 早期 block 了 startForeground，VoIP service 被杀，`VoIPRenderTextureView` 永远不会 attach，VC hook 不触发，`sVoIPCallPending` 会永远卡在 true。35s 后自动清零保证下一个非来电的震动/音频不被误拦。

---

### PiP — Activity.enterPictureInPictureMode()（2026-05-25 新增）

**问题**：前台 VoIP 通话切后台时，WeChat 调 `Activity.enterPictureInPictureMode()` 进入画中画模式，在桌面主屏上显示一个浮动小窗（含摄像头画面 / 语音条）。VC 层的 `removeView` 只移除了 in-app overlay，不阻止系统级 PiP 窗口。

**两个 overload**（均需 hook）：
```java
// API 24 (legacy)
boolean Activity.enterPictureInPictureMode()
// API 26+（WeChat 8.0.71 实际走此路径）
boolean Activity.enterPictureInPictureMode(PictureInPictureParams params)
```

**实现**：
```java
// HIDDEN + sVoIPCallPending → return false → WeChat 跳过 PiP，直接最小化
param.setResult(false);
Log.i(TAG, "[PF:PiP] blocked enterPictureInPictureMode");
```

**注意**：PictureInPictureParams 用 `Class.forName()` 加载（避免 minSdk 编译问题）。

---

### sVoIPCallPending 生命周期（修订，2026-05-25）

**35s → 120s 的原因**：

装机测试发现，来电被接通后 WeChat 停止发新通知（已建立通话，无需再 ring），35s fallback 提前触发，音频流恢复，后续再有通知时出现 4s 漏音窗口。

**新生命周期（4 个清零路径）**：

```
来电信号
  │
  ├─ NM/SF hook → armVoIPPending() → sVoIPCallPending=true + 120s 定时器
  │
  ├─ VC onAttachedToWindow：
  │    removeView → sRemovedByUs=true
  │
  ├─ VC onDetachedFromWindow (our removeView)：
  │    sRemovedByUs=false → armVoIPPending() 重新 arm 120s  ← 关键：从此点起算
  │
  ├─ VC onDetachedFromWindow (WeChat 自己 detach = 通话结束)：
  │    取消定时器 + sVoIPCallPending=false + restoreVoIPStreams()  ← 即时清零
  │
  └─ 120s 定时器 sClearVoIPPending（最终兜底）：
       sVoIPCallPending=false + restoreVoIPStreams()
```

---

## 三、:push 进程适配

**背景**：Android 保持 `:push` 进程存活，主进程被杀后 `:push` 继续接收推送。若只 hook 主进程，主进程杀后 VoIP 通知全部泄漏。

**`installForPush()` 安装内容**：
```
installNmHookImpl(lpparam, true)    — NM cancel VoIP 通知
installTelecomBlockImpl(true)       — TC block（WeChat 不走，保留占位）
installStartForegroundBlock(lpparam, true) — SF block 状态栏图标
installWakeLockHooksFor(true)       — WakeLock 亮屏
```

**:push 状态判断**：
```java
// 主进程：StateMachine.getInstance().isActive()
// :push 进程：NativeBridge.isHidden()（C++ 权威源，CLAUDE.md §6.9 铁律）
boolean hidden = pushProcess
    ? NativeBridge.isHidden()
    : StateMachine.getInstance().isActive();
```

**禁止在 :push 进程做**（参考 CLAUDE.md §2 LSPosed 进程白名单）：
- UI 操作 / ActivityManager / getRunningAppProcesses
- WebServer / Overlay / Toast / 通知栏发送
- 复杂反射 dump / 全局 List hook / 网络授权请求
- 业务页面过滤（搜索/通讯录/会话过滤全在主进程）

---

## 四、NotifyRouter — 三档通知策略

**设计思路**：PushFilter 只做决策调用，策略细节集中在 NotifyRouter，不让 PushFilter 变成大杂烩。

```
NotifyRouter.eval(talker, EventType) → Action
  ├─ BLOCK   → L1 setResult(false) / NM cancel
  ├─ VIBRATE → pass + applyVibrate(notification)
  ├─ SOUND   → pass + applySound(notification)
  └─ PASS    → 完全透传（非密友）
```

**EventType / Action**：
```java
enum EventType { MSG, CALL, HANGUP }
enum Action    { BLOCK, VIBRATE, SOUND, PASS }
```

**三档行为矩阵**：

| 事件 | OFF（默认） | VIBRATE | SOUND |
|------|:----------:|:-------:|:-----:|
| 消息通知 | block+cancel | 放行，震动 pattern | 放行，替换铃声 |
| VoIP 来电 | block+cancel | 放行，长震动×2 | 放行，替换铃声 |
| VoIP 挂断 | — | 短震动×1 | 短铃×1 |

**自定义振动 pattern**：
```java
static final long[] VIB_MSG  = {0, 100};           // 消息：1短
static final long[] VIB_CALL = {0, 400, 100, 400}; // 来电：2长脉冲
static final long[] VIB_HANGUP = {0, 80};           // 挂断：1短
```

**fireHangupVibration()** — 从 VV hook 触发，必须 post 到主线程（VV hook 在 WeChat 线程，Vibrator 调用需主线程）。

---

## 五、调用链时序（前台 VoIP 完整流，2026-05-25 实证）

```
密友来电信号（WeChat 内部网络）
  │
  ├─ [oz4.d] VoIP 管理层初始化
  │
  ├─ Service.startForeground(id, n)          ← SF hook 拦截 ✅
  │    └─ 状态栏图标被 block
  │    └─ muteVoIPStreams() + armVoIPPending()
  │
  ├─ NM.notify(id=40, ch=reminder_channel_id)← NM hook 拦截 ✅
  │    └─ fullScreenIntent → param.setResult(null)（完全 cancel）
  │    └─ armVoIPPending() (35s fallback start)
  │
  ├─ NM.notify(ch=voip_ringtone_channel)     ← NM hook 拦截 ✅
  │    └─ voip channel 关键词 → cancel
  │
  └─ [VoIPRenderTextureView.onAttachedToWindow] ← VC hook ✅
       ├─ wm.removeView(FrameLayout)（overlay 对旁观者消失）
       └─ policy=VIBRATE → Vibrator [0,180,40,120]ms（主人感知）
       └─ 通话结束 → restoreVoIPStreams (5s delay) → sVoIPCallPending=false
```

---

## 六、已证伪路径（永久禁止）

| 路径 | 证伪原因 | 证伪日期 |
|------|---------|---------|
| `Activity.onCreate` CA 层（voip/call/video 模糊匹配） | dumpsys 实证：前台来电不新开 Activity，只有 LauncherUI overlay | 2026-05-24 |
| `Dialog.show` | 0 命中 | 2026-05-24 |
| `Ringtone.play` | 0 命中 | 2026-05-24 |
| `AudioManager.requestAudioFocus(AudioFocusRequest)` VA | 0 命中（WeChat VoIP 走内部音频路径） | 2026-05-24 |
| `AudioManager.setMode(MODE_RINGTONE=1)` VA2 | WeChat VoIP 不调 MODE_RINGTONE=1 | 2026-05-24 |
| `STREAM_RING adjustStreamVolume(ADJUST_MUTE)` | 触发系统"静音模式已开启" toast；setStreamVolume(0) 已够 | 2026-05-25 |
| `WMG.addView` Java hook | 8 overload 签名不匹配；改用 View.onAttachedToWindow filter | 2026-05-24 |
| `TelecomManager.addNewIncomingCall()` TC | hook 注册成功，来电时 0 命中；WeChat VoIP 不走 TelecomManager | 2026-05-25 |
| WakeLock 作为亮屏主因 | fullScreenIntent 是真正的唤屏机制；WakeLock 是副产物 | 2026-05-25 |

---

## 七、待办（按优先级）

| 优先级 | 项 | 状态 | 预期日志 |
|:------:|------|:----:|---------|
| 🟡 P0 | AT hook 装机验证（视频挂断"嘟"音） | 🟡 代码已写 | `[PF:AT] blocked AudioTrack.play()` |
| 🟡 P0 | NotifyPolicy VIBRATE/SOUND 端到端装机 | 🟡 代码已写 | `[PF:L1] VIBRATE pass talker=` |
| P1 | ForegroundMute 前台 in-app 声音/震动 | ⬜ 待实现 | — |
| P1 | :push 进程 badge Java hook 接线 | ⬜ 待实现 | — |
| P2 | C2 通知伪装（weixin 身份） | ⬜ | — |
| P2 | C3 单会话未读 L3 + L4b/L4c | 🟡 | — |
| P3 | SOUND 自定义来电音效 | ⬜ | — |
| P3 | WeChatDND 官方免打扰 trace | 📋 | — |

---

## 八、关键常量速查

```java
// 通知渠道关键词（NM + SF 共用）
"voip" / "ringtone" / "call" / "ring" / "reminder"

// 通话文字关键词（ongoing-call cancel）
"通话" / "通話" / "call" / "视频" / "语音"

// 已确认的 WeChat VoIP 渠道 ID（2026-05-24）
"reminder_channel_id"          // 前台来电（带 fullScreenIntent）
"voip_notify_channel_silent"   // 静音来电
"voip_ringtone_channel_xxx"    // 铃声来电

// VoIP 渲染 View 类名（2026-05-24）
"com.tencent.mm.plugin.voip.video.render.VoIPRenderTextureView"
"com.tencent.mm.voipmp.v2.render.VoIPMPVoIPVideoView"

// VoIP 管理类（音频焦点调用链）
"oz4.d"  — 方法 c / Te

// Stream 数组（muteVoIPStreams）
int[] MUTE_STREAMS = {
    AudioManager.STREAM_RING,        // 铃声
    AudioManager.STREAM_VOICE_CALL,  // 通话
    AudioManager.STREAM_MUSIC        // 媒体（TRTC 背景音）
};
```
