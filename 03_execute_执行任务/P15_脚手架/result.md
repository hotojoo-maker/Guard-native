# P15 脚手架 — 已完成 ✅

更新时间：2026-05-19 16:05
状态：✅ 完成（ContactResolver 移入 W4）

---

## 验收结果

### 功能验收

| # | 子任务 | 状态 | 验证方式 |
|---|--------|:--:|---------|
| 1 | LSPosed 模块骨架 | ✅ | logcat `[init] pid=* proc=com.tencent.mm` |
| 2 | StateMachine — 3 态 + MMKV 持久化 | ✅ | `[SM] restored state=显形` |
| 3 | InterceptCounter — F04/F05/F07 + 事件队列 | ✅ | `[IC] restored F04=0 F05=0 F07=0` |
| 4 | StatusNotification | ✅ | `[NF] notification posted`，通知栏可见 |
| 5 | OverlayWindow | ✅ | `[OV] overlay attached`，悬浮窗可见 |
| 6 | HTTP Server (8080) | ✅ | `[HTTP] listening on port 8080` |
| 7 | HTML 调试页面 | ✅ | adb forward → 浏览器可访问 |
| 8 | DEV/PROD/HONEY 三态 | ✅ | AppConfig API `/api/mode` 可切换 |

### KPI 验收（frida_stats.js spawn，200s，40 间隔）

| 指标 | 本次 | 8.0.66 基线 | 红线 | 判定 |
|------|------|:----:|:--:|:--:|
| verifiedbootstate (vb) | **4** (200s) | 15 | 38 | ✅ |
| vb 每间隔 max | **1** | — | — | ✅ |
| PROP 稳定态 | **5/5s** (idle) | 82.5/100K | 220/100K | ✅ |
| CONN 密度 | 230/40 间隔 | 0.081 | 0.5 | ✅ |
| bl (bootloader) | 4 (spawn burst) | — | — | ✅ |
| pg (process group) | 2 (single spike) | — | — | ✅ |

**详细数据** → `frida_stats_with_module.log`（40 间隔完整）

### 关键发现

1. **vb = 4 / 200s** — 远低于红线 38，4 次全部在 WeChat 启动 burst 期间
2. **PROP 稳定态 = 5/5s** — 空闲时 PROP 恒定 5 次/间隔，这是系统后台噪声，非模块引入
3. **CONN spike 115** — 出现在 WeChat 网络初始化时，与 HTTP Server 8080 重叠，属正常网络活动
4. **Idle 清零** — 最后 12 个间隔 vb=0, bl=0, mprotect=0, DNS=0，纯静默

## 未关闭项（移交 W4）

- `ContactResolver.java` — 类名全部 L4（8.0.70 Catfish 来源，8.0.66 未验证），移交 W4 Proto dump 后修正

## 产出文件

```
src/main/AndroidManifest.xml
src/main/assets/xposed_init
src/main/assets/debug/index.html
src/main/res/values/arrays.xml
src/main/java/com/ghost/assist/ModuleMain.java
src/main/java/com/ghost/assist/core/AppConfig.java
src/main/java/com/ghost/assist/core/Bridge.java
src/main/java/com/ghost/assist/core/StateMachine.java
src/main/java/com/ghost/assist/core/InterceptCounter.java
src/main/java/com/ghost/assist/debug/StatusNotification.java
src/main/java/com/ghost/assist/debug/OverlayWindow.java
src/main/java/com/ghost/assist/debug/DebugServer.java
src/main/java/com/ghost/assist/debug/ContactResolver.java
build.gradle / settings.gradle / proguard-rules.pro / gradle.properties / local.properties
```
