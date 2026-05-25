# P22 PushFilter — 探针阶段结果归档

> ⛔ **本文档为探针阶段原始记录，已全面超期（2026-05-25）。**
>
> **不得** 按本文档推荐的 `oz4.d.Te()` / `requestAudioFocus` 路线写正式 hook。
> 这两条路径已在 2026-05-24 装机实证中确认为**死路（0 命中）**。
>
> **正确入口**：`03_execute_执行任务/P22_PushFilter/brief.md`（一页纸现状）
> **完整架构**：`docs/P22_PushFilter_VoIP.md`
> **状态总表**：`HOOKMAP.md §A5`

---

## 探针阶段发现（仅供历史参考）

**日期**: 2026-05-24
**脚本**: probe_foreground_call.js

### 有效发现（2026-05-24 实测）

| 探针 | 结论 |
|------|------|
| `View.onAttachedToWindow` | ✅ VoIP 视频 View 挂载信标 → 成为 CallGuard VC 主线 |
| `Service.startForeground()` | ✅ 状态栏来电图标真正来源 → 成为 SF hook 主线（后续 2026-05-25 实证）|
| `Notification.fullScreenIntent` | ✅ 亮屏真正来源 → NM hook strip 主线 |

### 已证伪/废弃路径

| 路径 | 结论 | 归档原因 |
|------|------|---------|
| `oz4.d.Te()` requestAudioFocus | ❌ **永久废弃** | 前台来电 0 命中，混淆类名随版本变；8.0.71 死路 |
| `AudioManager.requestAudioFocus` deny | ❌ **永久废弃** | 前台来电无此调用 |
| `AudioManager.setMode(RINGTONE=1)` 预静音 | ❌ **永久废弃** | 微信 VoIP 不走 RINGTONE 模式 |
| `TelecomManager.addNewIncomingCall` | ❌ **永久废弃** | 微信不走 Telecom API |
| `Dialog.show` | ❌ **永久废弃** | 来电弹窗不是 Dialog |
| `Ringtone.play` | ❌ **永久废弃** | 铃声不走 Ringtone |
| `WMG.addView`（脚本版）| ❌ **永久废弃** | 参数签名不匹配（需用 WindowManagerImpl 版本）|
| `AudioManager.adjustStreamVolume(ADJUST_MUTE)` | ❌ **永久废弃** | 触发"静音模式已开启" toast |
| `STREAM_RING` mute | ❌ **永久废弃** | 触发 OEM 静音模式图标（Bug_AutoSilent 根因）|

---

> 本文档不再更新。所有新发现直接写入 `docs/P22_PushFilter_VoIP.md`。
