# GUARD_GATE_TRUTH — 门控 / 状态机 / 入口权威口径

> 本文件只裁决门控、权限、状态机、入口可见性与进程路由语义。8.0.71 hook 点事实见 `docs/HOOK_MAP_8071_AUTHORITATIVE.md`。

## 0. 裁决规则

1. 本文件高于 `HOOKMAP.md`、P 任务文档中的旧口径。
2. 代码实测日志高于口述；没有 L1/L2 证据不得把状态升为已验证。
3. 当前修复期不做大重构；先冻结边界，后续再小步迁移。

## 1. Gate Model

```text
RiskGate    风险门：包名 / 签名 / SAFE_MODE（整线停用走 envelope RELEASE_KILLED 错误码，无独立 killSwitch 字段，2026-06-30 减法收口）
EntryGate   入口门：默认口令 111111 / 设置入口，只负责打开入口或显形入口
AuthGate    授权门：wxid + device + license，决定能不能使用功能
ConfigGate  配方门：server seed / encrypted registry ready，决定敏感 hook 是否可安装/生效
FeatureGate 功能门：密友开关、通知策略、防撤回等独立功能开关
StateGate   状态门：HIDDEN / VISIBLE / UNLOCKING，决定密友过滤是否生效
```

核心规则：

- 入口不等于授权。
- 授权不等于显形。
- 显形不等于授权。
- Release 严格模式下，授权通过但 registry 未解开仍不得启用敏感隐藏链。
- 风险失败优先级最高，进入 SAFE_MODE 后功能静默失效。
- Filter 只能读门控，不得写状态。

## 2. B2 后台收纳铁律

切后台 / Home / 手势离开微信 / 锁屏，属于隐私安全触发器，不是普通 UI 状态变化。

只要当前处于 `VISIBLE`，检测到用户离开微信前台，必须立即单向进入 `HIDDEN`。

禁止事项：

1. 禁止把 B2 改成 toggle。
2. 禁止把 B2 做成可选优化或可关闭开关。
3. 禁止因为用户只是短暂切后台、打开最近任务、看通知、锁屏，就跳过隐藏。
4. 禁止把 B2 与授权入口、搜索入口、设置页显示逻辑混在一起。
5. 禁止在 `HIDDEN` 态触发 B2 后自动恢复 `VISIBLE`。
6. 禁止为了减少刷新、减少日志、减少误触而延迟进入 `HIDDEN`。

正确语义：

- `VISIBLE + 切后台/Home/锁屏` → 立即 `enterHidden`。
- `HIDDEN + 切后台/Home/锁屏` → 保持 `HIDDEN`，无副作用。
- 解锁显形只能由用户主动入口触发，例如 `111111` / 设置页按钮。
- 离开微信永远不能导致显形。

验收标准：

- V 态切后台后，回到微信时必须已经是 HIDDEN。
- 密友会话 / 通讯录 / 搜索 / 朋友圈均应按 HIDDEN 态过滤。
- 不允许出现“回到微信后仍显形，需要等下一次刷新才隐藏”的行为。

## 3. 口令与设置入口可见性

设置功能入口位于微信设置相关页面（如“微信 → 设置 → 个人资料/设置入口”链路中的注入点）。

默认口令为 `111111`。只要口令正确，就可以从隐藏态显形并看到设置入口；这个动作与授权无关。

入口可见性是隐私表现的一部分：

- `VISIBLE`：入口可见，用户可以进入功能设置。
- `HIDDEN`：入口必须消失，不暴露密友功能存在。
- 输入 `111111` 是用户主动入口手势；可用于从 `HIDDEN` 显形并重新显示入口。
- 授权只决定能不能使用过滤、名单、通知策略等功能；不得阻止正确口令显形和入口展示。

禁止事项：

- 禁止在 `HIDDEN` 态保留设置入口。
- 禁止让切后台/Home/锁屏后入口仍可见。
- 禁止把设置入口可见性与搜索结果过滤混在同一个 Filter 里实现。
- 禁止把 `111111` 当授权码；也禁止用授权失败拦截正确口令显形。

## 4. v1 Accepted Shortcuts

当前 v1 为了保持已验证体验，允许以下临时简化：

- `SearchUnlock` 输入 `111111` 后，可触发 `HIDDEN → VISIBLE` 并关闭搜索页；此显形动作与授权无关。
- `isVipAuthorized()` 已在 v1.6 授权闭环中接入 `EnvelopeStore.isAuthorizedNow()`；无有效 token/envelope/license 时 Filter 放行，功能等于未授权未启用。
- `StateMachine.isActive()` 当前还叠加 `GuardRuntime.isSensitiveConfigReady()`；release 严格模式无有效 server seed / registry scatter 时，敏感隐藏链静默失效，debug/dev 可保留诊断 fallback。
- `NO_LICENSE / MISMATCH` 当前可放行，商业化 v2 前不擅自收紧。

这些是 v1 shortcut，不得继续扩展为授权、绑定、通知策略或其他功能逻辑。

## 5. State Read / Write Boundary

允许写状态：

- `StateMachine` 自身。
- `TriggerGuard` 的 B1/B2/B5 隐私触发路径。
- `SettingsEntry` 内明确的用户按钮。
- 未来 Java/C++ 双写桥，仅做同步，不改变业务语义。

只允许读状态：

- `ConvFilter`
- `ContactFilter`
- `MomentsFilter`
- `SearchFilter`
- `PushFilter` 的 `:push` 最小拦截路径

过滤器禁止调用：

```text
enterHidden
exitHidden
beginUnlock
attemptUnlock
```

过滤器只负责过滤或透传，不负责切状态、授权、打开入口。
