---
icon: 🚪
cn: 授权门控
name: auth-gate
description: 授权检查官快捷入口（授权执行官/授权门控/auth-gate）。Guard Native 四层门控大框架审查；动 StateMachine/AuthManager/SearchUnlock/防盗版前必用。
---
> ⚠️ 输出前自查：禁止错别字、黑话、客户看不懂的话。

# auth-gate — 授权门控（快捷入口）

## 🔐 固定签名铁律（所有角色必读 · 2026-06-30 cert-converge v2 起更新）
- **发版用** `signing/guard-native-official-release.jks`（official key、alias `guardofficial`，密码在 `signing/keystore.properties`，gitignore；D-026 起官替/共存 release 共用）。**调试 smoke 用** `signing/guard-native-debug.keystore`（guardFixed，仅模块更新 / 公告 / C2-smoke）。
- `build.gradle` 的 release flavor 必须指向 official jks（cert `e3e13a49`）；debug 走 AGP 默认 debug keystore（cert `ca421ec3`，注定 cert mismatch → registry scatter，**不作发版候选**）。禁止依赖或重建 `~/.android/debug.keystore`。
- **cert binding 输入源 = 宿主整包 sourceDir（D-027 / F-43）**：`ModuleMain.bindSigningCert` 改读 `app.getApplicationInfo().sourceDir`、**不**读模块自身 `sModulePath`——LSPatch metaloader 会重打包内嵌模块用自家 debug keystore 重签（`ca421ec3`），与发版 `e3e13a49` 不一致；改读宿主后跨 LSPatch 形态都对得上。
- `INSTALL_FAILED_UPDATE_INCOMPATIBLE` 必须先停、比对已装 APK 与固定 key 指纹，未经用户确认禁止卸载。
- 缺少固定 key 时停止 build/装机；日志只能写当前 P 任务 `logs/`，禁止写进 docs/skill 目录。

> 完整规则见同目录上级：`guard-auth-review_授权检查官/SKILL.md`
> Claude Code 输入：`/auth-gate_授权门控` 或 `/guard-auth-review_授权检查官`

本 skill 与 **guard-auth-review_授权检查官** 内容一致，仅缩短 invoke 名。

**一句话**：入口口令只开门；授权决定能不能用；状态决定隐/显；风险决定静默全关。任何 AI 不得乱接 StateMachine。

**必须 full 审查（L2）**：改 `SearchUnlock` / `StateMachine` / `RefreshBus` / `AuthManager` / `NativeBridge` / `SettingsEntry` / `DebugServer` 写接口 / C++ auth 层。

**执行前**：输出【授权检查官审查报告】9 项 + PASS/WARN/BLOCK（以 `guard-auth-review_授权检查官/SKILL.md` 为准，必须包含“模块边界是否清晰”）。

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
