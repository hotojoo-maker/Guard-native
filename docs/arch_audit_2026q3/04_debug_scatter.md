# 产出 4：debug 散点列表 + 归一建议

## 4.0 两套 debug 门控机制（这是问题根源）

| 机制 | 含义 | release 行为 | 在哪定义 |
|---|---|---|---|
| `BuildConfig.DEBUG` | **编译期**常量；R8 在 release 直接把分支剪掉 | 恒 `false`，代码被剥离 | Android 自动生成 |
| `AppConfig.isDebugEnabled()` | **运行期**判定 = `mMode == DEV \|\| HONEY` | 仍存在，运行时决定 | `core/AppConfig.java:119` |

> 两者语义不同：`BuildConfig.DEBUG` 是「这是不是调试包」（编译期、可被 strip）；`isDebugEnabled()` 是「当前运行模式是不是诊断/蜜罐」（运行期、留在 release）。
> **散点问题**：同样是「要不要做调试动作」，代码里有人只看前者、有人只看后者、有人 `||` 两者——三种写法并存。

## 4.1 散点清单（白名单源码内全量）

### A. `BuildConfig.DEBUG`（编译期门）

| # | 位置 | 形态 | 用途 | 类型 |
|--:|---|---|---|---|
| 1 | `core/GuardRuntime.java:50` | `return !BuildConfig.DEBUG;` | `isStrictRecipeMode()` —— **配方 fallback 的总开关（canonical）** | 真·代码门 |
| 2 | `moduleD/ConvFilter.java:142` | `boolean fbOk = BuildConfig.DEBUG && "kc5.v0".equals(recipe(...))` | fallbackSelfTest 自检日志 | 真·代码门 |
| 3 | `moduleD/ContactFilter.java:96` | 同上（`"ik3.t0"`） | fallbackSelfTest | 真·代码门 |
| 4 | `moduleD/MomentsFilter.java:124` | 同上（`"na4.b"`） | fallbackSelfTest | 真·代码门 |
| 5 | `moduleB/SearchFilter.java:108` | 同上（`"getView"`） | fallbackSelfTest | 真·代码门 |
| 6 | `core/RiskState.java:142` | `if (!BuildConfig.DEBUG) return;` | `debugForceFunnel()` 装机验证入口 | 真·代码门 |
| 7 | `ModuleMain.java:234` | `if (BuildConfig.DEBUG) {` | A2 闸 self-test（antiBanGateSelfTest/BranchSelfTest） | 真·代码门 |
| 8 | `ModuleMain.java:287` | `if (BuildConfig.DEBUG \|\| AppConfig...isDebugEnabled()) {` | 启动 HTTP DebugServer | **混合门** |
| 9 | `ModuleMain.java:446` | `if (BuildConfig.DEBUG) {` | KDF 向量自测（nativeKdfSelfTest，release SO 无此符号） | 真·代码门 |

### B. `AppConfig.isDebugEnabled()`（运行期门）

| # | 位置 | 形态 | 用途 | 类型 |
|--:|---|---|---|---|
| 10 | `core/AppConfig.java:119` | `mMode == DEV \|\| HONEY` | **定义点** | 定义 |
| 11 | `ModuleMain.java:287` | `BuildConfig.DEBUG \|\| isDebugEnabled()` | （与 #8 同行，混合） | 混合门 |
| 12 | `ModuleMain.java:299` | `if (AppConfig.getInstance().isDebugEnabled()) {` | 运行期诊断分支 | 真·代码门 |
| 13 | `moduleD/MomentsFilter.java:500` | `if (AppConfig.getInstance().isDebugEnabled()) {` | 朋友圈诊断日志 | 真·代码门 |

> 合计 **12 个有效门控点**（#10 是定义、#8/#11 是同一行的两种机制叠加）。
> 另有一批**注释里**提到 `BuildConfig.DEBUG`（`NativeBridge.java:274/403`、`GuardRuntime.java:197/219`、`RiskState.java:138`、`ModuleMain.java:443`、`DebugServer.java:459/470`）——只是文档说明，非代码门，已排除计数。

## 4.2 不一致的三种写法（核心问题）

```
只看编译期：     if (BuildConfig.DEBUG) { ... }                         // #2-7,9,12... 多数
只看运行期：     if (AppConfig.getInstance().isDebugEnabled()) { ... }  // #12,#13
两者 OR：        if (BuildConfig.DEBUG || isDebugEnabled()) { ... }     // #8/#11（DebugServer）
```

后果：
- **易错**：新增调试代码时，作者要凭记忆选「该用哪个 / 要不要 OR」，选错会导致 release 包里诊断动作意外可达（或调试包里反而不可达）。
- **难审计**：逆向/红队要确认「release 是否暴露调试面」时，得逐处看用了哪种门（PROTECTION_MAP §10.7 P0-4 正是要「删路线图式 release 暴露」）。

## 4.3 归一建议

**目标：所有 debug 门控只经一个语义明确的门面，调用点不再各自拼 `BuildConfig.DEBUG` / `isDebugEnabled()`。**

建议在 `AppConfig`（或新建 `core/DebugGate`）提供两个**语义分明**的入口，替换全部散点：

```java
// 1) 仅调试包可达、release 必被 R8 剥离（编译期）——给「绝不能进客户包」的动作（self-test、KDF 向量、强制态入口）
public static boolean isDevBuild()      { return BuildConfig.DEBUG; }

// 2) 运行期诊断/蜜罐模式（release 也可能为真）——给「HONEY 模式下要表现」的动作
public static boolean isDiagnostics()   { return mMode == Mode.DEV || mMode == Mode.HONEY; }

// 3) 「调试包 或 诊断模式」——给 DebugServer 这类两者皆可启的（取代 #8/#11 的手拼 OR）
public static boolean isDebugSurface()  { return BuildConfig.DEBUG || isDiagnostics(); }
```

落地映射：

| 现状散点 | 归一到 |
|---|---|
| #1 `GuardRuntime.isStrictRecipeMode()` | 保留（它本身就是 canonical 出口，建议改写为 `!AppConfig.isDevBuild()`） |
| #2-#5 各 Filter fbOk 自检、#6 forceFunnel、#7 A2 self-test、#9 KDF | → `isDevBuild()` |
| #12 #13 运行期诊断 | → `isDiagnostics()` |
| #8 / #11 DebugServer 启动 | → `isDebugSurface()` |

收益：
- 三种写法收敛成三个**命名清晰**的门，作者按「这动作能不能进客户包」一眼选对。
- 红队/审计只需 grep 三个方法名即可确认 release 暴露面，配合 §10.7 P0-4「删路线图式暴露」一并收口。
- 纯重构、不改行为（语义等价），难度低。

> 注：本任务只产文档，未改代码。以上为归一方向建议，落地需另开任务。
