# 产出 1：core/ 核心类依赖图

`src/main/java/com/ghost/assist/core/` 共 **17 个类**（同包，故彼此无 `import`，靠静态调用耦合）。

## 1.1 类清单与职责（按行数）

| 类 | 行数 | 职责（一句话） |
|---|--:|---|
| `Bridge` | 499 | 存储/状态层：SharedPreferences(`g_a7f2`) 封装 + 内存缓存（密友/密群名单、各开关、通知策略、伪装定位、授权绑定、UIN↔wxid 表、feed/conv 环） |
| `NativeBridge` | 412 | `libguardcore.so` 的 JNI 封装：进程角色 / 授权态 / 隐藏态 / wxid·群匹配 / registry 解密 / recipe·endpoint 取件 / 绑定材料下推。SO 缺失时全 fail-closed |
| `StateMachine` | 218 | 3 态机 VISIBLE/HIDDEN/UNLOCKING；`isActive()` = 四合一总闸 |
| `RiskState` | 232 | 风险等级（正常/篡改/盗版/到期）状态 + funnel 判定 |
| `GuardRuntime` | 239 | 配方（类名/字段名）唯一出口 + A2 防封时间闸 |
| `LeaseClock` | 193 | 可信时间（服务器授时 + 官方对表 + 单调水位），租约倒计时 |
| `FunnelPrompt` | 203 | 引流弹窗文案/动作 |
| `AppConfig` | 168 | 运行模式（PROD/DEV/HONEY）+ 服务器列表 + 调试开关 |
| `AuthManager` | 172 | 授权绑定（wxid+device）管理 |
| `CompatProbe` | 160 | 模块证书完整性 / 兼容性探针（A2 第 1 步） |
| `InterceptCounter` | 114 | 拦截计数（调试/KPI） |
| `RiskPromptController` | 93 | 风险态 → 弹窗策略路由 |
| `RefreshBus` | 91 | H↔V 切换事件总线（Filter 订阅刷新） |
| `PiracyNotice` | 74 | 盗版提示文案 |
| `EncryptedConfigLoader` | 70 | 加密 registry 解密/可用性判定（GuardRuntime 的后端） |
| `OfficialClock` | 52 | 官方时间锚（LeaseClock 的对表源） |
| `PromoConfig` | 28 | 推广/引流配置 |

## 1.2 依赖边（实际代码静态调用，已剔除 javadoc/注释里的提名）

```
AppConfig            → NativeBridge
AuthManager          → Bridge, NativeBridge
CompatProbe          → PromoConfig, RiskState
EncryptedConfigLoader→ NativeBridge
FunnelPrompt         → AppConfig
GuardRuntime         → CompatProbe, EncryptedConfigLoader, LeaseClock
InterceptCounter     → Bridge
LeaseClock           → Bridge, NativeBridge, RiskState
NativeBridge         → GuardRuntime
PiracyNotice         → AppConfig, NativeBridge
RiskPromptController → AppConfig, FunnelPrompt, NativeBridge, RiskState
RiskState            → Bridge, LeaseClock, NativeBridge
StateMachine         → AppConfig, Bridge, GuardRuntime, RefreshBus
Bridge               → （无）  ← 纯数据汇，叶子
OfficialClock        → （无 / 仅注释提 LeaseClock）
PromoConfig          → （无 / 仅被 CompatProbe·PromoConfig 引用）
RefreshBus           → （无 / 仅注释提 StateMachine）
```

> 注：`GuardRuntime.isAntiBanReady()` 还调用了 `com.ghost.net.EnvelopeStore` 和 `LeaseClock`（A2 时间闸），`EnvelopeStore` 属**禁入区**，此处只标接口，未读其实现。

## 1.3 分层视图

```
            ┌─────────────────── 入口 ───────────────────┐
            │  ModuleMain（handleLoadPackage）             │
            └───────────────┬────────────────────────────┘
                            │ 装/读
   总闸层      StateMachine.isActive()  ←── RefreshBus（H↔V 事件）
                  │  │  │  └────────────┐
                  │  │  └── isFeatureEnabled()      （Bridge）
                  │  └───── isSensitiveConfigReady()（GuardRuntime）
                  └──────── isVipAuthorized()       （net.EnvelopeStore·禁入）
                            │
   配方/解密三角   GuardRuntime ⇄ EncryptedConfigLoader → NativeBridge ⇄ GuardRuntime
                            │（recipe 出口）            │（JNI）
   计时/风险      LeaseClock ⇄ RiskState → NativeBridge
                  ↑OfficialClock（对表）
   弹窗/引流      RiskState → RiskPromptController → FunnelPrompt / PiracyNotice / PromoConfig
   存储底座      Bridge（叶子，被几乎所有人读写）  +  AppConfig（模式/配置）
                  原生底座 NativeBridge（被 9 个类调用，热点）
```

## 1.4 环路（重点）

发现 3 处**有向环**（互相调用，提高耦合/改动风险）：

1. **配方解密三角（核心）**：`NativeBridge → GuardRuntime → EncryptedConfigLoader → NativeBridge`
   - 这是「值钱链」，但环路意味着三者必须同时理解，单测难度高。
2. **计时/风险互依**：`LeaseClock ⇄ RiskState`（双向）
   - `LeaseClock` 读 `RiskState`，`RiskState` 又读 `LeaseClock`，时序敏感。
3. **A2 闸经原生回环**：`GuardRuntime → LeaseClock → NativeBridge → GuardRuntime`

## 1.5 热点（被依赖度）

入度统计（被几个 core 类调用）：

| 类 | 入度 | 说明 |
|---|--:|---|
| `NativeBridge` | 6 | AppConfig/AuthManager/EncryptedConfigLoader/LeaseClock/PiracyNotice/RiskPromptController/RiskState（≥6）——**改它影响面最大** |
| `Bridge` | 4 | AuthManager/InterceptCounter/LeaseClock/RiskState/StateMachine——存储热点 |
| `AppConfig` | 4 | FunnelPrompt/PiracyNotice/RiskPromptController/StateMachine |
| `RiskState` | 3 | CompatProbe/LeaseClock/RiskPromptController |
| `GuardRuntime` | 2 | NativeBridge/StateMachine |

## 1.6 建议（不在本任务范围内落地，仅记录）

- 配方解密三角与 `LeaseClock⇄RiskState` 两个环路建议在后续重构时引入单向接口（如让 `EncryptedConfigLoader` 不反向调 `NativeBridge` 的 GuardRuntime 入口），降低改动连坐。
- `NativeBridge` 入度过高，是「上帝原生网关」，可考虑按域（auth / hidden / registry / crypto）拆成多个窄接口，便于 JNI 名称裁剪（呼应 PROTECTION_MAP §10.7 P1.3 JNI 降噪）。
