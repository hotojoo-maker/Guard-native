# P22 通知隐私层 — 工作日志

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
