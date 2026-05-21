# P15 脚手架 — 调研记录

## 参考源码分析

### Catfish MainEntry.java (989行)
- 入口：`MainEntry.start(Application)` — 双检锁 + singleton
- 状态机：`UserControll.mVipMode` (boolean) — 简单的显/隐切换
- 存储：MMKV (com.tencent.mmkv.MMKV) + VipPreference
- UI：SettingsEntry（设置面板）+ Toast + Notification
- Hook：40+ hook 方法，全部经过 MainEntry 路由

### Catfish UserControll.java (944行)
- 状态：`mVipMode` 单一 boolean（无解锁中状态）
- 搜索触发：hook EditText TextWatcher → monitorFtsEdit
- 密码：hardcoded `chkPwd()` via native (NativeHelper)
- 摇一摇：ShakeHandler + SensorManager
- 广播：SCREEN_OFF + CLOSE_SYSTEM_DIALOGS
- 通知伪装：`replaceNotification()` → 替换 talker 为 "weixin"

## 本实现差异

| 项目 | Catfish | Guard Native |
|------|---------|--------------|
| 状态机 | 2态 boolean | 3态 enum (VISIBLE/HIDDEN/UNLOCKING) |
| 存储 | MMKV 直接 | Bridge 抽象 (可换 SharedPref/MMKV) |
| 密码 | native chkPwd | Java 明文比较 (v1 scaffold) |
| 调试 | 无 | HTTP 8080 + 悬浮窗 + 通知 |
| 搜索 | EditText hook | 预留接口 (B6, v1 第2周) |
| 包名 | com.catfish.newvip | com.ghost.assist |
| 进程白名单 | 无 (F-16 问题) | 第一行检查 `com.tencent.mm` |

## 设计决策

1. **Bridge 而非直接 MMKV**：v1 scaffold 使用 SharedPreferences；v2 可替换为 MMKV 而不改调用方
2. **NanoHTTPD → ServerSocket**：避免外部依赖，ServerSocket 足够 debug
3. **DebugServer 单线程 accept**：scaffold 阶段足够；v2 可换线程池
4. **ContactResolver 反射 8.0.66**：尝试标准路径，失败时返回友好 JSON
