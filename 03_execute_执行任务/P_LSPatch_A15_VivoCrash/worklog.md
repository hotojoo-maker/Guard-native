[2026-06-22 11:10] P_LSPatch_A15_VivoCrash 开工 — vivo V2361GA / Android 15 官替+共存 LSPatch 干净装仍闪退；用户要求落盘查清楚。

[2026-06-22 20:13] L1 复现（用户拍板：落盘为待办 + 继续深挖）：USB 重连后点共存包 com.tencent.mn → 闪退。crash buffer 抓到 FATAL（PID 13184）：
  java.lang.ExceptionInInitializerError
    at org.lsposed.lspatch.metaloader.LSPAppComponentFactoryStub.a(r8-map ...:50)
    at org.lsposed.lspatch.metaloader.LSPAppComponentFactoryStub.<clinit>(...:23)
    at java.lang.Class.newInstance(Native Method)
    at android.app.LoadedApk.createAppFactory(LoadedApk.java:294)
    at android.app.LoadedApk.createOrUpdateClassLoaderLocked(LoadedApk.java:1074)
    at android.app.LoadedApk.getClassLoader / getResources → ContextImpl.createAppContext
    at android.app.ActivityThread.handleBindApplication(ActivityThread.java:8377)
  Caused by: java.lang.NoClassDefFoundError: Class not found using the boot class loader; no stack trace available
  → 与 INVESTIGATION §3.1 完全一致；崩在 LSPatch metaloader 的 AppComponentFactory 阶段，早于 ModuleMain（非 Guard 业务代码）。
  → 设备此前 offline，重启 adb server 无效；改 USB 模式后 online（product PD2361M / model V2361GA）。

深挖待办：
  - [ ] 反编译 LSPatch 439 的 LSPAppComponentFactoryStub.a()（崩在 :50），定位它用 boot class loader 找的是哪个类。
  - [ ] 验证 dexopt 推断（后台 bg-dexopt 改变类加载 → 6/12 能跑、6/22 崩）：dumpsys package 编译状态。
  - [ ] 查 JingMatrix LSPatch 是否有 A15 修复版本。
  - [ ] MI9/A11 同包对照（区分 A15 专属 vs 包损坏）。
  - [ ] 换新版 LSPatch 重打包重装（等可传大文件时）。

[2026-06-22 深挖·根因已定位 L2/L1] = LSPatch 嵌入式(embedded)模式在 Android 15/16 的已知 bug（JingMatrix/LSPatch PR #68 → 合并为 #77）。非 Guard 代码、非微信。
  机理：A15/16 系统 getResource().getPath() 返回带 "jar:file:" 前缀的路径；LSPatch metaloader 老代码用固定 .substring(5) 截前缀（本为截 "file:"），在 "jar:file:" 下截错 → classloader 找不到 stub 依赖类 → NoClassDefFoundError(boot class loader)。
  铁证：① 我方崩溃栈 r8-map-id=143fbbafa9...4594191 与 PR #68 报告者完全一致（同一 metaloader 构建、同一 bug）；② main log 确认我方=嵌入式（"LSPatch: Use manager: false" + "Bootstrap loader from embedment" + 从 base.apk!/assets/lspatch/so 载 liblspatch.so）；③ 维护者 JingMatrix 原话 "this issue can only affect embedded mode patching"。
  修正先前推断：6/12能跑→6/22崩 更可能与 lspatch cache 路径解析相关（PR 内提及清 cache 后转 ClassNotFoundException），非"后台 dexopt"。
  版本影响：我方 439 崩此 bug = 439 不含修复；官方 v0.8 正式包(2026-03-06)早于 #77(2026-03-14 closed/replaced) → 官方 v0.8 正式包很可能仍不含修复，需 master 构建或更新 release 才有 fix。
解决方案：
  - 根治(保持发单一APK给客户)：用含 #77 修复的 lspatch.jar 重新 embedded 打包。先确认含修复的构建从哪取（master/CI）。
  - 快速验证/临时(需装 LSPatch Manager，不适合发客户)：local 模式打包，官方+报告者 @john77k 实测可绕过（local 不走 .substring(5) 出错路径）。
