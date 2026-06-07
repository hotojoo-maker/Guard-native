# E2 — 装机日志归档说明

本任务 5 次装机均为现场互动复测（用户实机点击 → 看微信表现 → 反馈），**未单独保存 logcat 截取**。

## 下次复测时的 logcat 截取脚本

```powershell
# E2 注入 + 捕获 + 按钮改名 全标签
adb logcat -d 2>&1 | findstr /R "\[FLOC\]" | Out-File -Encoding UTF8 -Append .\logs\floc_$(Get-Date -Format yyyyMMdd_HHmmss).log

# 关键日志 tag 速查
#  [FLOC] injector installed       → pz0.h.c hook 已挂上
#  [FLOC] inject lat=... lng=...   → 一次定位分发被改写（每次定位刷新会出）
#  [FLOC] pick captured ...        → 选点页 setResult 抓到坐标
#  [FLOC] btn rename PASS/FAIL     → 选点页按钮改名结果
#  [FLOC] send suppressed          → setResult 被改 CANCELED（已废弃路径）
```

## 8.0.72 升级回归时的 KPI 对比

```powershell
# 基线（E2 关）
adb shell am force-stop com.tencent.mm
adb shell monkey -p com.tencent.mm -c android.intent.category.LAUNCHER 1
Start-Sleep -Seconds 5
frida -U -n com.tencent.mm -l tools\frida_stats.js -t 30 > logs\floc_kpi_off_baseline.log

# E2 开
# (用户手动开开关 + 设伪坐标，然后)
frida -U -n com.tencent.mm -l tools\frida_stats.js -t 30 > logs\floc_kpi_on.log

# 对比
diff (Get-Content logs\floc_kpi_off_baseline.log) (Get-Content logs\floc_kpi_on.log)
```

## 历史装机环境

- 设备：小米 9 / Android 11
- 微信：8.0.71（D-014 当前底座）
- LSPosed 主进程 + `:push` 进程白名单
- Guard 模块已启用
- 编译耗时：单次 ~6m29s（Gradle 增量）
