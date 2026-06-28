# 架构基线盘点 — arch_audit_2026q3

任务：`devin/arch-audit-v1` — 对 `core/` + `moduleD/*Filter` 当前架构做基线盘点。
**只产文档，不改任何代码。**

- 盘点时间：2026-06-28
- 代码快照来源：用户本机工作树 `C:\Users\Me\Desktop\guard_native`（经 Tailscale/SSH 拉取分析）
- 底座：WeChat 8.0.71 / package `com.ghost.assist`
- 白名单（只读）：`src/main/java/com/ghost/assist/**`、`PROTECTION_MAP.md`、`HOOKMAP.md`、`docs/**`
- 禁入（未读取）：`signing/**`、`net/`、`A2SignatureSpoof.java`、所有 `*.inc`

> 行号均基于上面这次工作树快照；与 GitHub `main` 若有差异以快照为准，落地修改时请二次核对。
> `net/`（`EnvelopeStore` 等）属禁入区，本盘点凡引用到它只标注接口名/调用点，未读其实现。

## 文档清单

| # | 文件 | 内容 |
|---|------|------|
| 1 | [`01_core_dependency_graph.md`](01_core_dependency_graph.md) | core/ 17 个核心类的依赖图（谁调用谁）+ 分层 + 环路 |
| 2 | [`02_moduleD_filter_table.md`](02_moduleD_filter_table.md) | moduleD 下 `*Filter` 的入口/出口/状态表（实测 5 个，非 4 个） |
| 3 | [`03_role_overlap_runtime_bridge.md`](03_role_overlap_runtime_bridge.md) | GuardRuntime / NativeBridge / Bridge 三角色职责重叠分析（合/拆建议） |
| 4 | [`04_debug_scatter.md`](04_debug_scatter.md) | 12 处 debug 散点（`BuildConfig.DEBUG` + `isDebugEnabled()`）+ 归一方向 |
| 5 | [`05_inline_hardcode_p0-1.md`](05_inline_hardcode_p0-1.md) | PROTECTION_MAP §10.7 P0-1 剩余 5 处内联硬编码：位置+影响面+难度 |

## 一句话结论

- **core/ 不是扁平的 17 个类，而是 3 个强耦合三角 + 1 条授权/计时主链**；最关键的环路是
  `NativeBridge ↔ GuardRuntime ↔ EncryptedConfigLoader`（配方解密三角）和 `LeaseClock ↔ RiskState`（计时/风险互依）。
- **Filter 共用同一把闸** `StateMachine.isActive()`（授权 + registry ready + 总开关 + HIDDEN 四合一），
  但 5 个 `*Filter` 里只有 4 个是「密友数据过滤器」，`MomentsGroupIconFilter` 是纯 UI 图标隐藏、不读闸不读名单，**归类与其它四个不同**。
- **三角色边界基本清晰但有两处债**：① Bridge 与 NativeBridge 都能回答「这个 wxid 是不是密友」（双真相源，代码注释已写 Phase 3 收口未做）；
  ② GuardRuntime 同时担「配方出口」和「A2 防封时间闸」两职，后者读了 `net.EnvelopeStore`，与其自述「不碰授权」有张力。
- **debug 门控不统一**：有的点只看 `BuildConfig.DEBUG`，有的 `BuildConfig.DEBUG || isDebugEnabled()`，有的只看 `isDebugEnabled()`——同一意图三种写法，建议归一到单一 `DebugGate`。
- **P0-1 内联硬编码剩 5 处**（ContactDiscoveryHook / ContactLabelHideGuard / MomentsFilter Like-Comment），删净后明文暴露面再降，但收益有限、难度低-中，详见文档 5。
