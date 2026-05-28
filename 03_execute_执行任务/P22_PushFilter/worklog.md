# P22 通知隐私层 — 工作日志

---

## 2026-05-29 语音/视频来电拦截收口（装机验证通过）

**会话**：守护内核10（wuxianChat 014931）。底座小米9/8.0.71。分支 `fix/voip-call-intercept`。

### 起因
上个 AI 重建来电链时丢了浮窗/AM 层、用了次优写法（removeView-after-attach、写死通知 id、MP/VW 全 HIDDEN 拦）。本会话审代码 + 装机抓包逐层修复。

### 改动（全部装机 L1 实证）
1. **NM 删写死 id** `-525958226`（只对一个测试密友有效）→ 靠 `sL1BlockedLastItem` 判密友。
2. **MP 加 `sVoIPCallPending` 门**（原只判 isActive → 误杀 HIDDEN 态所有媒体）。
3. **FB 改 addView-block**：hook `WindowManagerImpl.addView` 命中 `.plugin.ball.view.*` 不让 add（根治半透明痕迹），退役 onAttach→removeView。
4. **补 AM**（`AudioManager.setMode` block）+ **PiP `setPictureInPictureParams` 剥离** + **UL `onUserLeaveHint` 兜底**。
5. **VC onDetach 改计数器** `sRemovedByUsCount`（修：视频两个渲染 view 第 2 个 detach 误判挂断 → 60ms 清 pending → 声音漏）。
6. **撤掉 stopForeground 判挂断**（视频服务每 ~10s 循环 start/stopForeground → 误判 → 嘟漏 + 死循环）；pending 只由 120s fallback 清零。
7. **来电振动 = 单次 onset**（`VIB_CALL={0,400,220,400}` USAGE_ALARM，上升沿 fireAlert 一次）；撤掉自发持续 ring + 撤掉放行微信官方振动（官方持续振会响到超时死循环）。VV 两模式都拦微信振动。
8. callNotifyPolicy：**静默=全静默 / 震动=来电单次提示**（用户口径）。

### 验收（用户确认「完美」）
语音+视频 × 静默+震动 × 前后台双向：零声/零亮屏/零浮窗/零小窗/无挂断嘟；震动只在来电瞬间振一次不死循环；拦截本体不受影响。

### 证伪归档 → FAILURE_LOG F-36（a~g 七条）；权威文档 → docs/P22_PushFilter_VoIP.md 重写。

### 【授权检查官 改后审查报告】
- 审查时间：2026-05-29　触发：来电拦截多层改动（Filter 链）+ 振动策略
- 涉及文件：`moduleC/PushFilter.java`、`moduleC/NotifyRouter.java`（均非授权保护区）
- 1 乱接状态机：**否**（只读 `isActive()`/`NativeBridge.isHidden()`，无写）
- 2 混淆口令/授权：**否**（不碰 111111/AuthGate）
- 3 越权：**否**（未授权 isActive()=false 自然放行）
- 4 绕过 AuthGate：**否**
- 5 破坏 SearchUnlock→sm→RefreshBus 链：**否**，不涉及
- 6 Java/C++ 分层：**合规**（VW/NM/SF 主进程读 Java sm、:push 读 NativeBridge；铁律 30）
- 7 保护区未经审先改：**否**（PushFilter/NotifyRouter 非保护区）
- 8 模块边界：⚠️ PushFilter ~1000 行（消息+来电+角标三件事）→ **需拆 CallGuard**（命中 §零.六 C-1），下一步执行
- 9 最小修复：拆 CallGuard 后行为不变复验
- 门控：EntryGate ✅ / AuthGate ✅ / StateGate ✅ / RiskGate ✅（振动为机主侧，不增微信可见特征）/ 分层 ✅
- **结论：PASS**（拆 CallGuard 为待办架构债，不阻塞）

### 提交
`3d38f34`(语音快照) → `1fe644e`(FB addView-block+AM/UL/PiP) → `3a0b76b`(振动收口+除死循环+挂断嘟)。

---

## 2026-05-25 18:45 第二轮 Bug 修复（本会话）

### 修复内容

**Bug_AutoSilent（✅ 已修）**
- 根因：`MUTE_STREAMS[]` 包含 `STREAM_RING`，设为 0 触发系统静音图标
- 修复：只保留 `STREAM_VOICE_CALL + STREAM_MUSIC`，去除 RING/SYSTEM/ALARM/NOTIFICATION
- 文件：`PushFilter.java` MUTE_STREAMS 数组 + sMutedVols 缩减为 2 元素

**Bug_VibrationBlocked（✅ 已修）**
- 根因：VV hook 拦截 WeChat 进程所有 `Vibrator.vibrate()`，`sMutedVols` 历史残留值
  可能使 `muting=true`，误杀 `NotifyRouter.fireAlert()` 的震动调用
- 修复：`NotifyRouter.sOurVibration` 豁免旗；fireAlert() 调 vibrate 前置 true，finally 清除；
  VV hook 第一条检查 `sOurVibration=true` 时直接 return
- 文件：`NotifyRouter.java` + `PushFilter.java` VV hook

**NM gap-cancel 无 ID 限制（✅ 已修）**
- 根因：旧代码 `id == -525958226` 只匹配角标通知；弹窗 banner 用的是不同 ID
- 修复：去掉 ID 限制，改为 1s 窗口内任何 WeChat NM.notify() 均取消
- 新增：extras-based talker 检测（`"talker"` / `"fromUser"` key），覆盖前台直调 NM 路径
- 文件：`PushFilter.java` installNmHookImpl()

**文档收敛**
- 创建 `brief.md` 一页纸入口
- 修复 HOOKMAP.md 第 72-81 行 Mojibake 乱码
- 更新 TASK_BOARD.md P22 行，关闭 Bug_ScreenWake / Bug_AutoSilent
- 重写 result.md，彻底废弃 oz4.d.Te() 推荐

### 待验证
- NotifyPolicy VIBRATE 模式震动（装机后发密友消息，看 `[NR] fireAlert` 日志）
- NotifyPolicy SOUND 模式提示音
- 底部 tab 角标（Bug_UnreadBadge）需单独爬虫

---

## 2026-05-25 16:02 第一轮装机验证

## 2026-05-25 16:02 终端操作员装机验证

### 测试条件
- HIDDEN 态
- 密友 wxid: wxid_lzd2va16jd1622
- 密友来电（VoIP）

### 用户报告
屏幕会亮，状态栏显示来电

### 日志原文 (adb logcat -d, 过滤 PF:VW / WakeLock)

```
05-25 16:01:38.942 21560 21560 I NCL     : [PF:VW] WakeLock.acquire hook ok
05-25 16:02:06.855 21560 22666 I NCL     : [PF:VV] blocked vibrate
05-25 16:02:07.135 21560 22666 I NCL     : [PF:VV] blocked vibrate
05-25 16:02:07.517 21560 22666 I NCL     : [PF:VV] blocked vibrate
05-25 16:02:07.830 21560 22666 I NCL     : [PF:VV] blocked vibrate
05-25 16:02:08.179 21560 22666 I NCL     : [PF:VV] blocked vibrate
05-25 16:02:08.515 21560 22666 I NCL     : [PF:VV] blocked vibrate
05-25 16:02:09.324 21560 21560 I NCL     : [PF:VW] blocked WakeLock flags=0x2000000a
05-25 16:02:20.223 21560 21560 I LSPosed-Bridge: Crash unexpectedly: java.lang.RuntimeException: WakeLock under-locked ILinkVoIPSmallView
05-25 16:02:20.223 21560 21560 I LSPosed-Bridge: 	at android.os.PowerManager$WakeLock.release(PowerManager.java:2479)
05-25 16:02:20.223 21560 21560 I LSPosed-Bridge: 	at android.os.PowerManager$WakeLock.release(PowerManager.java:2441)
05-25 16:02:20.231 21560 21560 E AndroidRuntime: java.lang.RuntimeException: WakeLock under-locked ILinkVoIPSmallView
05-25 16:02:20.231 21560 21560 E AndroidRuntime: 	at android.os.PowerManager$WakeLock.release(PowerManager.java:2479)
05-25 16:02:20.231 21560 21560 E AndroidRuntime: 	at android.os.PowerManager$WakeLock.release(PowerManager.java:2441)
05-25 16:02:22.116 22951 22951 I NCL     : [PF:VW] WakeLock.acquire hook ok
05-25 16:02:34.222 22951 22951 I NCL     : [PF:L1] block talker=wxid_lzd2va16jd1622 hiddenBlocked=1
```

### 三项发现

1. hook 已加载 ✅
2. 拦截的 WakeLock flags 是 0x2000000a，不是预期的 ACQUIRE_CAUSES_WAKEUP (0x10000001)
3. 🔴 WakeLock under-locked 崩溃：acquire 被拦截后 release 仍然执行，导致 ILinkVoIPSmallView 崩溃

### 结论
- 微信 VoIP 来电用 flags=0x2000000a 亮屏，不是 0x10000001
- 当前实现只拦截了 acquire 没同步处理 release，造成 under-locked crash
- 振动拦截正常 (PF:VV blocked vibrate x6)
