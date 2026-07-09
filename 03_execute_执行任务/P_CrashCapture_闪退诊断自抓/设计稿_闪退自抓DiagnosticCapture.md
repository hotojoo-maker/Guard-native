# 设计稿 · 闪退自抓 / 诊断捕捉（Crash Capture · 非 root）

> 角色：网络安全官（诊断面）+ 授权检查官（模块边界）联审草案。
> 状态：L4 设计草案（2026-07-02 · N71 会话）。**未落码**；落码前必过「授权检查官 + 安全官」改前审查。
> 触发：用户问「客户装上去坏了 / 闪退，能不能非 root 做一个捕捉包覆盖安装提取日志？闪退超过 2 次这种能做到吗？」
> 单一真源：本设计稿。落码后机制指回这里；坑进 `FAILURE_LOG.md`；决策进 `DECISION_LOG.md`。
> 证据锚点：init 序列 = `src/main/java/com/ghost/assist/ModuleMain.java`（`onApplicationCreated`）；诊断基础设施 = `debug/DebugServer.java` / `debug/DebugTelemetry.java` / `debug/OverlayWindow.java`；门面 = `core/AppConfig.java`（`isDevBuild/isDiagnostics/isDebugSurface`）；设备实测 = 本会话 MI9 `609b4b18` Android 11 / MIUI V125。

---

## §0 一句话结论 + 硬边界

- **能做，且「闪退超过 N 次」能可靠判定**——靠 Android 11+ 的 `ApplicationExitInfo`（读自己包的历史退出原因，含 Java 崩 / native 崩 / 被系统杀），非 root。
- **硬边界（诚实）**：
  1. 非 root 的 App **只能读自己 UID 的 logcat + 自己包的退出记录**，读不了别的 App。→ 「另开一个捕捉 App 去抓微信日志」在非 root 下**行不通**；捕捉能力必须**做进微信本体包**（同 `com.tencent.mn` / 同 `e3e13a49` 签名，才能覆盖安装）。
  2. 完整 **native tombstone 栈** 由系统进程写，非 root 读不到全量 → 只能拿到我方崩前最后几行 + `ApplicationExitInfo` 的原因码/摘要。
  3. **每次都崩、从没起来过** → 我方代码没机会跑 → 自抓不到，只能走系统 Bug report 兜底。

---

## §1 背景与目标

- 出货形态 = LSPatch 打包型 APK（250MB，微信 + 内嵌模块 + LSPatch 启动器），面向**非 root 普通用户**。
- 客户现场若「装上去坏了 / 闪退」，我们拿不到 adb，需要一条**非 root、客户几步操作就能回传日志**的取证链。
- 本会话实测已证：LSPatch 冷启在「反复覆盖装没重启 + 内存紧」时，会在 `LSPAppComponentFactoryStub.<clinit>` 抛 `NoClassDefFoundError`（发版坑 #4），崩几次后自恢复。这类现场正是要抓的对象。

**目标**：在微信本体包内，做一套**闪退计次 + 自动进诊断/安全模式 + 日志落盘 + 一键回传**的能力，默认零打扰，出问题时可取证、甚至自愈到「能打开」。

---

## §2 闪退分型 × 非 root 可抓性矩阵

| 崩在哪 | 例子 | 非 root 自抓 | 靠什么 |
|---|---|:--:|---|
| **我方代码之后** | hook 抛异常 / `onApplicationCreated` 内 Java 崩 | ✅ 最好抓 | 提前装的 `UncaughtExceptionHandler` 当场抓 + 落文件 |
| **我方代码之前** | metaloader `NoClassDefFoundError`（本会话那种） | ✅ 但需「之后能起一次」 | logcat 环形缓冲崩后仍在 → 下次起来 `logcat -d` 补抓；`ApplicationExitInfo` 记原因 |
| **native 崩** | libguardcore / hook 触发 SIGSEGV·SIGABRT | ⚠️ 半抓 | 完整 tombstone 读不到；只拿崩前面包屑 + `ApplicationExitInfo` = `REASON_CRASH_NATIVE` |
| **每次都崩、从未起来** | 打包坏 / 与新版微信不兼容持续崩 | ❌ 自抓不了 | 系统 Bug report / 客户连一次 adb |

> 关键事实（已核 `ModuleMain.java`）：我方主进程最早落点 = hook `Application.onCreate` 的 `afterHookedMethod`。metaloader 崩发生在此**之前**（`LoadedApk.createAppFactory`），故该次实例我方处理器抓不到，只能靠 logcat 回放 + `ApplicationExitInfo` 事后补。

---

## §3 三个数据源（都非 root）

### 3.1 `ApplicationExitInfo`（API 30+ · 本设计的星）
- `ActivityManager.getHistoricalProcessExitReasons(pkg, 0, N)` 读**自己包**最近若干次进程退出，每条含：
  - `reason`：`REASON_CRASH`(Java) / `REASON_CRASH_NATIVE`(SIGSEGV/SIGABRT，metaloader 崩也归此) / `REASON_LOW_MEMORY`(MIUI 内存杀) / `REASON_ANR` / `REASON_SIGNALED` …
  - `timestamp` / `description` / `importance`。
- **非 root、只读自己包**——所以 metaloader 崩、native 崩、MIUI 内存杀**全都能看到**，正好覆盖我方自建计数器的盲区。
- 本设备 Android 11 = API 30 ✅ 满足。低于 API 30 的机型退化到 §3.2。

### 3.2 我方连续崩计数器（全版本可用）
- 早期（`onApplicationCreated` 第一段，甚至 `handleLoadPackage`）读一个计数文件：`attempts++` 并持久化 + 记 `firstCrashTs`。
- 冷启走到 `[init] ready` 且**存活 N 秒**后，把计数**清 0**（用 Handler 延时确认存活，防「起来立刻又崩」误清）。
- 崩在 ready 之前 → 计数不清 → 下次启动即见 `attempts ≥ 2`。
- 盲区：只统计「我方代码跑到了增量那行」的崩；metaloader 之前的崩不计 → 由 §3.1 兜。

### 3.3 崩前面包屑 + logcat 回放
- **最早装 `UncaughtExceptionHandler`**（`onApplicationCreated` 第一件事，早于任何 hook）：主线程任何后续异常先被我方接住 → dump 自 UID `logcat -d` + 堆栈到文件，再放行原崩。
- **面包屑**：每步 init（`certBind→native verify→config→state→hooks…`）写一行「到第 N 步」到文件；即便闪退没被手抓拦住，磁盘也留「死在哪一步」，定位 native 崩尤其管用。
- 落盘位置：`Android/data/<pkg>/files/diag/`（应用私有外部目录，**免存储权限**，手机自带文件管理器可见、可分享）。

---

## §4 核心：「闪退 ≥ N 次」检测 + 自动诊断/安全模式

> 回答用户「闪退超过 2 次这种能做到吗」= **能**。

### 4.1 判定（冷启极早期，读三源取并集）
```
recentCrashes = max(
    我方连续崩计数(§3.2),
    ApplicationExitInfo 里「窗口内(如 10 min) 崩类原因」的条数(§3.1)
)
阈值 N（默认 2）+ 窗口（默认 10 min）走服务器可下发，客户端兜底常量。
```

### 4.2 分级响应（拍板项，见 §10）
| recentCrashes | 行为 |
|:--:|---|
| `< N` | 正常启动，零打扰（默认态） |
| `≥ N` | **进诊断模式**：拉高日志详级 + dump `logcat -d` + `ApplicationExitInfo` 原因 + 面包屑 合并落盘；桌面轻提示「检测到多次异常，点此导出诊断」 |
| `≥ N+更严重(可选)` | **进安全模式 SAFE_BOOT**：只装诊断 + 授权/防篡改姿态，**跳过业务/隐私 hook**，保证微信一定能打开 → 让客户能导出/等服务器下发「禁用某 hook」修复配置 |

- **计数在「ready + 存活 N 秒」后清 0**，避免长期误进诊断。
- SAFE_BOOT 的价值不止取证：**换微信版本导致某 hook 崩** / **坏 registry** 时，安全模式让 App 先能开，再走版本适配或服务器热修，是一条自愈兜底（呼应 `docs/VERSION_UPGRADE_SOP.md`）。

---

## §5 落点与接口（未落码 · 待审）

- 新类 `debug/CrashSentinel.java`（或 `core/`）：
  - `earlyArm(Context, processName)`：`onApplicationCreated` 第一段调；装 `UncaughtExceptionHandler` + 读三源算 `recentCrashes` + 决定 `DiagMode/SafeBoot`。
  - `noteStep(String)`：面包屑。
  - `markReadyAndScheduleReset()`：`[init] ready` 后调。
  - `exportBundle()`：合并 logcat/面包屑/ExitInfo → 文件；返回路径。
- `ModuleMain.onApplicationCreated`：第 0 段插 `CrashSentinel.earlyArm(...)`；末尾 `[init] ready` 后插 `markReadyAndScheduleReset()`；`SafeBoot` 为真时**跳过 §7 那批业务 hook 安装**（用一个 `if (!safeBoot)` 包住 install 群）。
- 复用现成基建：`DebugServer`（已有 `debug server skipped (release+prod)`）、`DebugTelemetry`（环形事件+指标）、`OverlayWindow`（提示条）、`AppConfig.isDiagnostics/isDebugSurface`（门面）。**不新造架构**。

---

## §6 导出 / 回传（非 root，客户零 adb）

1. **应用内一键分享**：`Intent.ACTION_SEND` 把 `diag/*.zip` 甩给微信/邮件/网盘 → 客户直接发回来。
2. **自动回传**（可选）：诊断包脱敏后走 miyou-server（复用授权信道）；仅在客户同意/诊断模式下。
3. **兜底（每次都崩、从没起来）**：教客户 **设置 → 开发者选项 → 错误报告 / Bug report**（系统打包全量 logcat + tombstone，不依赖我方代码），或连一次 USB 用 adb 拉。

---

## §7 安全 / 防封红线核对（落码必过）

- **非 root、只读自己包**：不碰他包、不读 `ro.boot.*`（铁律 5）、不注入微信 JNI（铁律 23）、不 `startService/extends Service`（铁律 24）；`ApplicationExitInfo`/`logcat -d` 均系统公开 API。
- **release 面收敛**：诊断模式在 release **默认关**，靠「口令 / 服务器下发 / `recentCrashes≥N` 自动」触发，不常开——**不能把 logcat dump 工具白送逆向者**。产物脱敏：不落 wxid 明文、不落 `s_rel/W/key`、cert 只留前 4 字节（沿用 `certBind` 现有纪律）。
- **SAFE_BOOT 不得成为「关保护」后门**：安全模式跳过的是**业务/隐私 hook**（让 App 能开取证），**不得**因此泄露隐藏名单或永久停用 A2/授权姿态；需限频 + 非持久（清 0 即恢复），防攻击者靠「诱导崩溃」强制降级。此点 = 授权检查官 + 安全官**必审项**。
- 不影响已验证 hook 回调体（铁律 29）：`CrashSentinel` 只加在 init 骨架与 install 群外层，不进任何已验证 hook 内部。

---

## §8 分阶段落地 + DoD

| 阶段 | 范围 | DoD（绿长啥样） |
|---|---|---|
| **P0 最小原型** | 早期 `UncaughtExceptionHandler` + 面包屑 + `logcat -d` 落 `files/diag/` + 一键分享 | 人为在某 hook 抛异常 → 文件里抓到堆栈 + 面包屑「死在第 N 步」 |
| **P1 计次+ExitInfo** | 接 `ApplicationExitInfo` + 连续崩计数 + `recentCrashes≥2` 进诊断模式 + 提示条 | MI9 连崩 2 次 → 第 3 次起来自动落诊断包 + 提示；正常起来 N 秒后计数清 0 |
| **P2 安全模式** | `SafeBoot` 跳业务 hook + 服务器可下发阈值/热修 | 注入「必崩的坏 hook」→ 连崩后 App 仍能在安全模式打开并导出 |

- 每阶段先过授权检查官 + 安全官改前审查；改 `ModuleMain` init 骨架前 git 快照。
- 日志/证据落当前 P 任务 `logs/`，不写进 docs/skill。

---

## §9 待用户拍板

1. 阈值 N 与窗口（默认「10 分钟内崩 2 次进诊断」）是否合适？
2. 是否要 P2 安全模式（SAFE_BOOT 跳业务 hook 自愈），还是先只做 P0+P1 取证？
3. 诊断产物默认「本地落盘 + 手动分享」，还是允许「诊断模式下自动回传 miyou-server」？
4. release 触发诊断的口令入口 = 复用 `111111` 体系另设诊断口令，还是纯靠 `recentCrashes` 自动？
