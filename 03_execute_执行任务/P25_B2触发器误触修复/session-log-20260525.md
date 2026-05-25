# P25 终端会话日志 — 2026-05-25

## 操作目的

1. 复现 111111 切 V → 好友不出现的 bug
2. 确认 B2 修复后 `foreground` 守门行为
3. 确认切屏 → H 正常链路

## 关键发现

### 1. B2 修复前：close_dialogs 误触（第一轮 logcat）

```
02:14:32.406  SU 口令命中 111111 → exitHidden → V ✅
02:14:32.436  BUS-V → restoreInPlace injected=6
02:14:32.437  SU close search act=FTSMainUI
02:14:33.174  [TG] B2-close_dialogs(fs_gesture) → enterHidden  🔴 炸了
02:14:33.178  SM notify old=显形 new=隐藏   ← 0.7s 内被踢回 H
```

根因：关搜索页触发 CLOSE_SYSTEM_DIALOGS 广播，B2 误判为"用户切屏"，执行 enterHidden。

### 2. B2 修复后：foreground 守门生效（第二轮实时 logcat）

```
05-25 04:08:45.121  [TG] B2-close_dialogs(fs_gesture) foreground=1, skip
05-25 04:08:47.421  [TG] B2-close_dialogs(fs_gesture) foreground=1, skip
05-25 04:08:48.490  [TG] B2-close_dialogs(fs_gesture) foreground=1, skip
05-25 04:08:54.804  [TG] B2-close_dialogs(fs_gesture) foreground=1, skip
```

修复后 B2 加了 `sForegroundCount > 0` 守门（P25 result.md §三）。微信在前台时广播到来 → skip，不再误切 H。

### 3. 切屏 → H 正常链（用户实证）

用户确认：Home 键回桌面 → 微信进后台 → B2 foreground=0 → enterHidden ✅

## 状态

- P25 B2 修复：✅ 装机实证 pass（P25 result.md + 本轮实时 logcat 双重确认）
- P26 好友 V 态不出现：⬜ 待第 2 层探针（`kc5.r0.d` invoke）—— 与 B2 修复无关，独立 bug
