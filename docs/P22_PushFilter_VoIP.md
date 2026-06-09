# 微信 8.0.71 通知 / 推送 / 来电拦截 — 权威文档（A5 通知隐私层）

> **接手先读这里。** 本文是 8.0.71 通知/推送/语音视频来电拦截的**唯一权威设计文档**。
> 证据等级：**L1 装机已证实**（logcat 原文，2026-05-29 收口）。
> 微信版本：**8.0.71**（D-014）。底座：小米 9 / Android 11 / MIUI。
> 实现文件：`moduleC/PushFilter.java`（来电+消息 hook）+ `moduleC/NotifyRouter.java`（三档策略+振动）。
> 拆分状态：来电链计划抽到 `moduleC/CallGuard.java`（行为不变，见 §九）。
>
> ⛔ **AI 铁律**：本文件每一层都有装机 logcat 背书。**修改任何已验证 hook 前必须先问用户（铁律 29）**。§七「已证伪」清单里的路径**永久禁止重新尝试**。

---

## 一、产品规格（用户口径，2026-05-29 锁定）

**范围**：HIDDEN 态 + 已授权 + 密友名单内（`isActive()` 三层门全开）才生效。**V 态 / 未授权 / 非密友 一律放行，正常通知不受影响。**

**密友的「语音/视频来电」与「消息」分两套独立策略**：

| 事件 | 设置项 | 选项 | 行为 |
|------|--------|------|------|
| **语音/视频来电** | `callNotifyPolicy`（MMKV `cnfy`，默认 OFF） | **静默** / **震动** | 一律拦掉微信原生 UI/铃声/亮屏/浮窗/状态栏图标。静默=零振动；震动=来电瞬间**单次**振动提示一次（不持续）。 |
| **消息** | `notifyPolicy`（MMKV `nfyp`，默认 OFF） | **静默** / **震动** / **声音** | 目标为普通消息按档提醒；当前缺口集中在普通消息通知链路与 SOUND 档铃声功能，需继续收口。 |

**关键产品取舍（来电振动）**：来电**不做持续响铃式振动**。原因见 §七.B（官方持续振动会死循环、自发振动被 MIUI 截断）。震动模式只在来电瞬间给机主**一次明确提示**。

设置页 UI：`SettingsEntry` 悬浮面板「密友消息通知」+「语音/视频通知」两个区块。当前下一步只做普通消息通知与铃声功能，不重做来电主链。

---

## 二、来电拦截链（语音/视频，装机验证 2026-05-29）

微信 8.0.71 VoIP 来电同时走**多条平行路径**，必须多层拦截。所有层都在**主进程** + 门控 `isActive()`；多数音频/浮窗层额外门控 `sVoIPCallPending`（来电窗口）。

| 层 | hook 点 | 作用 | 关键实现 | 期望日志 |
|----|---------|------|---------|---------|
| **SF** | `Service.startForeground()` ×2 overload | 拦状态栏通话图标（绕过 NM 的平行路径，亮屏根因之一）| `fullScreenIntent`/voip channel/通话文字识别 → `muteVoIPStreams` + `armVoIPPending` + `setResult(null)` | `[PF:SF] blocked startForeground ch=reminder_channel_id` |
| **NM** | `NotificationManager.notify(tag,id,n)` | 拦来电通知 + 防唤屏 + 消息 L1-gap 兜底 | `fullScreenIntent` strip→cancel / voip channel / 通话文字 → `setResult(null)`；L1-gap：`sL1BlockedLastItem` 200ms 窗口**无 id 限制** | `[PF:NM] cancel voip ch=reminder_channel_id` |
| **VC** | `VoIPRenderTextureView` / `VoIPMPVoIPVideoView` `.onAttachedToWindow` | 移除前台来电渲染浮层 | F-26：`View.class.getDeclaredMethod("onAttachedToWindow")+hookMethod`，走到顶父 `WindowManager.removeView`；`onDetach` 用 **`sRemovedByUsCount` 计数器**辨别我们移除 vs 微信拆除 | `[PF:VC] removeView ...` / `onDetach (ours) re-arm pendingDetach=N` |
| **FB** | `WindowManagerImpl.addView(View, LayoutParams)` | 拦「等待接听」浮球（语音）| 命中 `.plugin.ball.view.*` → **`setResult(null)` 不让 add**（view 零渲染，无半透明痕迹）。视频浮窗是 `android.widget.FrameLayout`（见 §四，待精确特征） | `[PF:FB] blocked addView ...` / `seen addView (call window) ...` |
| **AM** | `AudioManager.setMode(int)` | 防 TRTC 激活通话音频模式 | 仅 `sVoIPCallPending` 窗口内 block | `[PF:AM] blocked setMode(0)` |
| **AT** | `AudioTrack.play()` | 拦 TRTC SDK 通话/挂断音 | 仅 `sVoIPCallPending` 窗口内 block | `[PF:AT] blocked AudioTrack.play()` |
| **MP** | `MediaPlayer.start()` | 拦视频挂断「嘟」+ in-app 音 | 仅 `sVoIPCallPending` 窗口内 block（**不可只判 isActive**，否则误杀 HIDDEN 态所有媒体） | `[PF:MP] blocked MediaPlayer.start` |
| **VV** | `Vibrator.vibrate(*)` 全 overload | 拦微信自身振动（两个模式都拦）| `sOurVibration` 豁免我们自己的 onset 脉冲；**不放行微信官方振动**（官方持续振会死循环，见 §七.B） | `[PF:VV] blocked vibrate(...)` |
| **VW** | `PowerManager$WakeLock.acquire/release` | 拦亮屏 WakeLock | 读 `mFlags` 识别 screen-wake；release 用 `WeakHashMap` 防 under-locked crash；**`pushProcess` 参数**：:push 走 `NativeBridge.isHidden()`，主进程走 `StateMachine`（铁律 30） | `[PF:VW] blocked acquire flags=0x...` / `swallow release` |
| **PiP** | `Activity.enterPictureInPictureMode(*)` + `setPictureInPictureParams(*)` | 拦切后台画中画小窗 + 剥离自动进入 PiP | 仅 `sVoIPCallPending` 窗口内 `setResult(false)`/swallow | `[PF:PiP] blocked enterPiP` / `stripped setPictureInPictureParams` |
| **UL** | `Activity.onUserLeaveHint()` | 按 Home 离开通话页时直接退后台 | VideoActivity + pending → `moveTaskToBack(true)` | `[PF:UL] moveTaskToBack on userLeaveHint` |
| **CA** | `Activity.onResume/onStart`（filter VideoActivity）| 全屏来电屏（后台→前台暴露）| `com.tencent.mm.plugin.voip.ui.VideoActivity` → `moveTaskToBack(true)`（不 finish，保留通话）| `[PF:CA] moveTaskToBack VideoActivity` |

**:push 进程子集**（主进程被杀仍拦推送/来电通知）：`installForPush()` = L1 + NM + SF + VW（铁律 30：只读 `NativeBridge.isHidden()`，禁止 UI/NotifyRouter）。

---

## 三、`sVoIPCallPending` 生命周期（关键，2026-05-29 收口）

```
来电信号
  ├─ SF / NM 识别来电 → armVoIPPending()
  │     ├─ sVoIPCallPending = true
  │     └─ 上升沿(false→true) + 主进程 → NotifyRouter.fireAlert(CALL)  ← 单次 onset 振动
  ├─ VC onAttach → removeView，sRemovedByUsCount++
  ├─ VC onDetach：sRemovedByUsCount>0 → 计数-1 + 重 arm（是我们移的）；=0 → 真·微信拆除
  └─ 清零：仅靠 120s fallback（VOIP_PENDING_TTL_MS）
```

**铁律（本次踩坑换来）**：
1. **`sVoIPCallPending` 只由 120s fallback 清零**。⛔ **不要用 `Service.stopForeground` 当挂断信号** —— 视频 VoIP 服务每 ~10s 循环 start/stopForeground，会被误判成挂断 → 提前清 pending → 挂断嘟漏 + onset 重复触发死循环（§七.B/§七.C）。pending 撑满整通电话 = AT/MP 全程拦住挂断嘟。
2. **VC onDetach 必须用计数器 `sRemovedByUsCount`**，不能用单 boolean —— 视频会 attach **两个** 渲染 view（`VoIPRenderTextureView` + `VoIPMPVoIPVideoView`），单 boolean 会把第 2 个 detach 误判成挂断 → 60ms 内清 pending → 声音漏。
3. pending 撑 120s 期间 AT/MP/AM 持续 block → HIDDEN 态此窗口内媒体被静音（可接受代价，120s 后自动恢复）。

---

## 四、视频浮窗（小窗口）现状

- **语音**「等待接听」浮球 = `com.tencent.mm.plugin.ball.view.*`，FB 层 addView-block ✅ 已盖。
- **视频**小窗口 = `android.widget.FrameLayout`（探针 `[PF:FB] seen addView (call window) android.widget.FrameLayout` 实证），**不是** ball.view 类。当前**未按类名 block**（盲拦所有 FrameLayout 会误杀正常 UI）。
- **诊断已内置**：FB hook 对通话窗口内的非 ball view 打印 `type/flags/gravity/尺寸`，后续可按 WindowManager.LayoutParams 特征精确 block。
- 实测「接近完美」：视频后台↔前台基本不露（CA + PiP + UL 组合压制），桌面偶有极浅半透明痕迹（可忽略）。

---

## 五、振动设计（NotifyRouter）

```
callNotifyPolicy（cnfy）:
  OFF（默认） → 全静默：fireAlert(CALL) 直接 return；VV 拦微信振动 → 零振动
  VIBRATE     → 来电上升沿 fireAlert(CALL) → doVibrate(VIB_CALL) 一次
                VIB_CALL = {0,400,220,400} 双脉冲，USAGE_ALARM（后台/锁屏/勿扰都能振）
```

- **只在来电瞬间震一次**，不持续。`sOurVibration` 豁免旗保证这次 onset 不被自己的 VV hook 拦掉。
- 消息振动（`notifyPolicy`）独立：`VIB_MSG = {0,80,60,80}`，default usage（尽力而为，后台可漏）。
- 设置面板点「震动」当场预览振动（用户体验）。

---

## 六、亮屏 BUG 根因（历史，已修）

| 路径 | 结论 |
|------|------|
| `Notification.fullScreenIntent` | **主因** —— Framework 收到自动唤屏。NM/SF strip+cancel 解决。 |
| `Service.startForeground()` | 状态栏图标来源 + 亮屏副因。SF block 解决。 |
| WakeLock | 副因。VW block（含 release 防 under-locked crash）。 |

---

## 七、已证伪 / 永久禁止（违反即回退）

### A. 历史证伪（2026-05-24~25）
| 路径 | 原因 |
|------|------|
| `oz4.d.Te()` requestAudioFocus | 来电 0 命中 |
| `AudioManager.setMode(RINGTONE=1)` 预静音 | 微信 VoIP 不走 |
| `TelecomManager.addNewIncomingCall` | 微信不走 Telecom |
| `AudioManager.adjustStreamVolume(ADJUST_MUTE)` / `STREAM_RING` mute | 触发系统「静音模式已开启」toast/图标 |
| `Activity.onCreate` CA finish 模糊匹配 | 前台来电不新开 Activity（用 onResume/onStart filter VideoActivity 代替）|
| `Dialog.show` / `Ringtone.play` | 0 命中 |

### B. 来电持续振动（2026-05-29 证伪，三条死路）
| 尝试 | 失败原因 |
|------|---------|
| 单个长波形 / 重复波形（`createWaveform`，含 USAGE_ALARM 重复）| **MIUI 把所有第三方 app 振动波形截成一下短的**，连重复波形也截 |
| 定时器每 ~1.2s 连发独立短振 | 配合 stopForeground 判挂断时被循环误触发，且 pending 反复 re-arm → 死循环 |
| 放行微信官方持续振动（不 block VV）| 我们藏了来电 UI，微信不知用户已处理 → **官方振动一直响到 ~60s 超时**，对方挂了也停不下来 → 死循环 |

→ **结论**：来电振动只做**单次 onset**（§五）。

### C. 挂断检测 / pending 清零（2026-05-29 证伪）
| 尝试 | 失败原因 |
|------|---------|
| `Service.stopForeground` 当挂断信号 | 视频服务每 ~10s 循环 start/stopForeground，非挂断标记 → 提前清 pending → 挂断嘟漏 + onset 重触发 |
| 单 boolean `sRemovedByUs` 辨别 VC 移除 | 视频 attach 两个渲染 view，第 2 个 detach 误判挂断 → 60ms 清 pending → 声音漏。用计数器 `sRemovedByUsCount` 修复 |
| FB `onAttachedToWindow → removeView` | view 已 attach 上屏，移除前**渲染一帧** → 桌面半透明痕迹。改 `WindowManagerImpl.addView` block |
| NM L1-gap 写死 `id == -525958226` | 那是某个测试密友的通知 id；换密友失效。靠 `sL1BlockedLastItem` 标志判密友即可 |
| MP/VW 只判 `isActive()`（不判 pending）| HIDDEN 态全拦所有 MediaPlayer/亮屏 → 误杀非密友媒体/正常亮屏。必须加 `sVoIPCallPending` 门 |

---

## 八、关键常量速查

```java
// VoIP 渲染 view（VC）
"com.tencent.mm.plugin.voip.video.render.VoIPRenderTextureView"
"com.tencent.mm.voipmp.v2.render.VoIPMPVoIPVideoView"
// 全屏来电 Activity（CA / UL）
"com.tencent.mm.plugin.voip.ui.VideoActivity"
// 浮球（FB，语音）
".plugin.ball.view."   // 视频浮窗 = android.widget.FrameLayout（待特征精确化）
// 通知渠道关键词（NM/SF）
"voip" "ringtone" "call" "ring" "reminder"
// 通话文字关键词
"通话" "通話" "call" "视频" "语音"
// 已确认渠道 id
"reminder_channel_id"（前台来电，带 fullScreenIntent）
// 振动
VIB_CALL = {0,400,220,400} USAGE_ALARM（来电 onset 单次）
VIB_MSG  = {0,80,60,80} default（消息）
// 生命周期
VOIP_PENDING_TTL_MS = 120_000
```

---

## 九、模块拆分（CallGuard）

`PushFilter.java` 已 ~1000 行（消息 L1/NM/L4b/L4c + 来电全链）。来电链（SF/NM-voip/VC/FB/AM/AT/MP/VV/VW/PiP/UL/CA + sVoIPCallPending 生命周期 + mute/restore）抽到 **`moduleC/CallGuard.java`**，`PushFilter` 只留消息通知 + 角标。NM 单 hook 点保留在 PushFilter，VoIP 语义委托 CallGuard。行为不变，装机复验。
（消息通知后续也会变复杂，提前分家便于维护。）

---

## 十、装机验证清单（L1 取证锚点）

| 场景 | 期望 logcat | 验收 |
|------|------------|------|
| 密友语音来电（前/后台）| `[PF:SF] blocked` + `[PF:NM] cancel voip` + `[PF:VC] removeView` | 零声/零亮屏/零浮窗 |
| 密友视频来电（前↔后台）| 同上 + `[PF:AT] blocked` + `[PF:FB] ...` | 零全屏/零小窗（极浅痕迹可忽略）|
| 挂断（视频）| 挂断期 `[PF:AT] blocked` 覆盖 | 无大嘟声 |
| 震动模式来电 | `[NR] fireAlert type=CALL ... usage=ALARM`（**只一次**）| 振一次即停，不死循环 |
| 静默模式来电 | 无 `[NR] fireAlert type=CALL` | 零振动 |
| 密友消息（前/后台）| `[PF:L1] block LL.add talker=...` | 零提示 |
