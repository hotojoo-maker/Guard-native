2026-05-25 04:23

- 现象：P25 在 `ACTION_CLOSE_SYSTEM_DIALOGS` 分支加 `sForegroundCount>0 skip` 后，用户反馈“轻轻上划离开微信”不再立刻触发 V→H（广播到达时 foreground 仍为 1，导致误挡合法离开路径）。
- 定性：`CLOSE_SYSTEM_DIALOGS` 到达时序早于 lifecycle 计数归零，`foreground>0` 不能作为“未离开”的硬门槛；但该广播本身噪声大（关搜索页/弹窗也会发），不能无脑 enterHidden。
- 最小修复：在 `TriggerGuard` 增加 `TRIM_MEMORY_UI_HIDDEN`（UI 真离开前台）信号，做到“真离开立刻隐藏”；同时将 `close_dialogs + foreground>0` 从 hard-skip 改为 defer+二次确认，避免 `fs_gesture` 误触回归。

2026-05-25 04:27

- 用户诉求：希望“轻轻滑一点点不触发”，但“真正离开要尽快隐藏”。
- 约束：Android 层无法量化“滑动距离/范围”，只能用时间阈值表达“手势半途取消/回弹不触发”。
- 实装：`TRIM_MEMORY_UI_HIDDEN` 从“立即 enterHidden”改为 `UI_HIDDEN_CONFIRM_DELAY_MS=140ms` 的确认窗口；期间若 Activity 回来前台（`onActivityStarted`）会自动取消。

2026-05-25 04:42

- 质检结论（用户确认口径）：**B2 默认必须“立即隐藏”**（不做 defer/确认窗/延迟），除非用户后续明确要求加延迟。
- 当前实现（L1）：`ACTION_CLOSE_SYSTEM_DIALOGS` 触发后直接 `enterHidden()`；仅抑制已知误触窗口（`FTSMainUI` finish 后短窗口内 `fs_gesture`）。
- 装机 L1 证据（节选）：
  - `05-25 04:39:16.985 [TG] B2-close_dialogs:fs_gesture → enterHidden`
  - `05-25 04:39:16.994 [SM] enterHidden`
  - `05-25 04:39:29.424 [TG] B2-close_dialogs:fs_gesture → enterHidden`
  - `05-25 04:39:29.431 [SM] enterHidden`

