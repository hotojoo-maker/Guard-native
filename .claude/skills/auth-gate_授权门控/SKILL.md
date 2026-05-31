---
name: auth-gate
description: 授权检查官快捷入口（授权执行官/授权门控/auth-gate）。Guard Native 四层门控大框架审查；动 StateMachine/AuthManager/SearchUnlock/防盗版前必用。
---

# auth-gate — 授权门控（快捷入口）

> 完整规则见同目录上级：`guard-auth-review_授权检查官/SKILL.md`
> Claude Code 输入：`/auth-gate_授权门控` 或 `/guard-auth-review_授权检查官`

本 skill 与 **guard-auth-review_授权检查官** 内容一致，仅缩短 invoke 名。

**一句话**：入口口令只开门；授权决定能不能用；状态决定隐/显；风险决定静默全关。任何 AI 不得乱接 StateMachine。

**必须 full 审查（L2）**：改 `SearchUnlock` / `StateMachine` / `RefreshBus` / `AuthManager` / `NativeBridge` / `SettingsEntry` / `DebugServer` 写接口 / C++ auth 层。

**执行前**：输出【授权检查官审查报告】8 项 + PASS/WARN/BLOCK。

---

（下文与 guard-auth-review 同步；编辑请改 `guard-auth-review_授权检查官/SKILL.md` 后运行 `sync_skills.ps1`）

请读取并严格遵循：

```
.cursor/skills/guard-auth-review_授权检查官/SKILL.md
```

若无法读取，核心铁律：
- SearchUnlock：v1 口令可 HIDDEN→VISIBLE（见 GUARD_GATE_TRUTH），**不当授权码**、不扩过滤逻辑
- H/V 链路：SearchUnlock → StateMachine → RefreshBus → Filter（SettingsEntry 入口可见性）
- 文档：`docs/README.md` · 8071 hook → `docs/HOOK_MAP_8071_AUTHORITATIVE.md`
- 主进程 Filter 只读 Java StateMachine.isActive()；:push 只读 NativeBridge.shouldBlockBadge()
- AUTH_STATE 只由 AuthManager/bindAccount 写入
