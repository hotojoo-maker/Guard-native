# P22 通知隐私层（A5，v1 核心隐私）— 一页纸入口

> **接手先读这里。** 不用翻探针脚本，不用猜旧版方向。
> 最后更新：2026-05-25（NotifyPolicy 震动/静音 bug 修复；FloatBall ✅）

---

## 一、当前状态速览

| 子模块 | 状态 | 说明 |
|--------|:----:|------|
| **L1 消息拦截** | ✅ | `LinkedList.add(NotificationItem)` → 密友 → setResult(false) |
| **NM 取消** | ✅ | `NM.notify()` voip channel + fullScreenIntent + L1-gap cancel（1s 窗口，无 ID 限制） |
| **SF startForeground** | ✅ | `Service.startForeground()` 双 overload block；状态栏来电图标根因 |
| **VC 来电 UI 隐藏** | ✅ | `VoIPRenderTextureView/VoIPMPVoIPVideoView` onAttach → removeView |
| **AT AudioTrack** | ✅ | `AudioTrack.play()` block；TRTC SDK 视频挂断声根因 |
| **VV Vibrator** | ✅ | 全 overload block；`sOurVibration` 豁免旗防误杀自身 fireAlert() |
| **AM AudioMode** | ✅ | `AudioManager.setMode()` block 防 TRTC 激活通话模式 |
| **FB FloatBall** | ✅ | `WindowManagerImpl.addView()` 拦截 `FloatBall*` view（2026-05-25 装机确认） |
| **PiP / UL** | ✅ | setPictureInPictureParams 剥离 + onUserLeaveHint 兜底 |
| **Bug_ScreenWake** | ✅ | 已修：根因 = `fullScreenIntent` + `Service.startForeground()`；WakeLock 是副产物 |
| **Bug_AutoSilent** | ✅ | 已修：MUTE_STREAMS 去除 STREAM_RING，不再触发静音模式图标 |
| **NotifyPolicy OFF** | ✅ | 默认路径，L1 block + NM gap cancel |
| **NotifyPolicy VIBRATE** | 🟡 | 代码已写（fireAlert + sOurVibration），待装机验证 |
| **NotifyPolicy SOUND** | 🟡 | 代码已写（MediaPlayer fallback to vibrate），待装机验证 |
| **底部 tab 角标 L4b** | ❓ | 方法名 8.0.71 可能已变，需 jadx 重查 |
| **底部 tab 角标 L4c** | 🟡 | 减法逻辑已改，待装机验证 |
| **Bug_UnreadBadge** | ⬜ | 底部消息 tab 小红点未过滤密友 unread count；需爬虫找 WeChat 内部计数方法 |
| **ForegroundMute** | ⬜ | 微信前台 in-app 声音/震动（非 NM 路径）；需爬虫 |
| **C2 通知伪装** | ⬜ | v1 待做 |

---

## 二、架构铁律（新 AI 必读）

### ❌ 废弃方案（不得再用）
| 废弃路径 | 原因 |
|---------|------|
| `oz4.d.Te()` requestAudioFocus | 前台来电 0 命中，死路 |
| `AudioManager.setMode(RINGTONE=1)` 预静音 | 微信 VoIP 不走此路径 |
| `TelecomManager.addNewIncomingCall` | 微信不走 Telecom API |
| `AudioManager.adjustStreamVolume(ADJUST_MUTE)` | 触发系统"静音模式已开启" toast |
| `Service.startForeground` 之外用 `NM.cancel()` 消图标 | SF 绕过 NM，cancel 无效 |
| `STREAM_RING` mute | 触发 OEM 静音图标，不是 VoIP 音频路径 |

### ✅ 当前主线
```
来电拦截链：
  SF(Service.startForeground block)
  + NM(fullScreenIntent strip + voip channel cancel)
  + VC(VoIPRenderTextureView removeView)
  + AT(AudioTrack.play block)
  + AM(AudioManager.setMode block)
  + FB(FloatBall WindowManagerImpl.addView block)

消息通知链：
  L1(LinkedList.add block) → NM gap-cancel(1s 无ID限制)
  + extras-talker 兜底（前台路径）
  + NotifyRouter.fireAlert() 提供 VIBRATE/SOUND 替代提醒
```

### VoIP 来电全流程（2026-05-25 实证）
```
微信收到来电 push
  ├─ Service.startForeground() → [SF] blocked ✅
  ├─ NM.notify(fullScreenIntent) → [NM] cancel ✅  
  ├─ NM.notify(voip_ringtone_channel) → [NM] cancel ✅
  ├─ VoIPRenderTextureView onAttach → [VC] removeView ✅
  ├─ AudioTrack.play() → [AT] blocked ✅
  ├─ Vibrator.vibrate() → [VV] blocked ✅
  └─ FloatBallView addView → [FB] blocked ✅
结果：零亮屏 / 零声音 / 零震动 / 零 UI / 零静音图标
```

---

## 三、三档通知策略（密友消息）

| 策略 | 弹窗 | 声音/震动 | 实现 |
|------|:----:|:--------:|------|
| **OFF（默认）** | ❌ | ❌ | L1 block + NM gap cancel |
| **VIBRATE** | ❌ | 震动 ×2 | L1 block + `NotifyRouter.fireAlert()` |
| **SOUND** | ❌ | 提示音（无配置时 fallback 震动）| L1 block + `NotifyRouter.fireAlert()` |

**VoIP 来电一律 BLOCK**（不受三档策略控制）。

---

## 四、关键文件

| 文件 | 用途 |
|------|------|
| `src/main/java/.../moduleC/PushFilter.java` | 所有 hook 实现（L1/NM/SF/VC/AT/VV/AM/FB/PiP/UL/VW）|
| `src/main/java/.../moduleC/NotifyRouter.java` | 三档策略路由 + fireAlert() |
| `src/main/java/.../core/Bridge.java` | `getNotifyPolicy()` / `setNotifyPolicy()` / key=`nfyp` |
| `src/main/java/.../moduleB/SettingsEntry.java` | 三档选择 UI（胶囊选择器）|
| `docs/P22_PushFilter_VoIP.md` | 完整架构文档（踩坑/死路/实现细节）|
| `HOOKMAP.md §A5` | hook 状态总表 |

---

## 五、待办（下一步）

1. **装机验证 NotifyPolicy VIBRATE/SOUND**：发密友消息，确认有震动/提示音但无弹窗
   - 看日志：`[PF:L1] VIBRATE block+alert` + `[NR] fireAlert` + 无 `[PF:VV] blocked`
2. **Bug_UnreadBadge（底部 tab 小红点）**：
   - 先跑爬虫找 WeChat unread count 更新入口（三层方法论：先爬虫）
   - 候选：`MainTabUI.i()` / `h0.d(int)` / WeChat DND API
3. **L4b 重查**：jadx 搜索 8.0.71 对应方法名（`MainTabUI` 未读数字）

---

> **禁止事项**：不得把 `oz4.d.Te()` 写回主线；不得在过滤链里碰 `NativeBridge.isHidden()`（主进程权威源 = Java StateMachine）。
