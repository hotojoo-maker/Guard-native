# P_AntiBanGate 防封授权闸（重构·版本轴）— worklog

> 2026-06-23 建档 · 底座 微信 8.0.71 · 状态：**设计only 进行中**（落代码待安全官共审）

---

## 2026-06-23 · DESIGN 评估（架构师 C81）

- 评估 `DESIGN.md`（统一风控引擎设计）：方向对；签名命脉成色 **L1+L2**（A2 签名轴 Java 可 hook + 喂官方 → c$p 读官方）。
- 指出两处问题：① 时间口径 **72h/2h 自相矛盾**（DESIGN §2/§5 vs §4A）；② **KPI 基线缺**（P18 未建，F-22）。
- 证据：`DESIGN.md`、`PLAN.md`、研究线 `防封权威账_2026年6月.md`。

## 2026-06-23 · 配方卡 v0 → v1（C82，设计only）

- 把 DESIGN/PLAN「为什么」提炼成 AI 可机械照做的「怎么做」：A 单值参数表 / B isAntiBanReady 真值表 / C 5 步 checklist / D 雷区 5 条 / E 成色总账。
- v0 → v1 修正：
  - **A2 倒序修正**——删 `isAntiBanReady()` 顶部登录门；A2 = 装→跑→生效的开机默认保护，从未授权满 **24h** 才撤（登录/授权是另一条线，不进 A2 判定）。
  - 6 个 ❓ 全填死：软引流 1h / 登录砸门 2h / 解体 24h / 付费断网宽限 7天 / 蜜罐影子期 7天 / 红线影子租约 7天（原 7-10 定 7）/ 隐私离线 72h（已实现）/ 信任分 60%（占位待 KPI）。
- 时间口径以架构师 C81 拍板为准（覆盖 DESIGN 旧 72h/2h）。

## 2026-06-23 · 授权检查官独立审（C82 换帽，纯只读）

- 9 项报告 + 四门状态：架构 **WARN**（设计only 零代码）。
- 落地修正 4 条：① GuardRuntime 类注释更新（唯一 auth-aware 出口）；② A2 料只进 `registry_pack`，禁新增 a2_pack；③ 登录砸门只经 RiskPromptController（唯一弹窗 红线#9）；④ 首装时间 Bridge 新键用 4 字符短哈希。
- 2 watch：isAntiBanReady 时间线用 `trustedNow-首装时间` **自算**，禁复用 LeaseClock.currentLevel() 的 24/72/144h 离线阶梯（否则两闸连坐）；A2 料只进 registry_pack。
- step③「A2 接主线」当时判 = **时机 BLOCK → D-015 阶段铁律**（架构不 BLOCK，时机 BLOCK）。
- 读过保护区：`core/GuardRuntime.java`（仅 isConfigReady，无 isAntiBanReady）/`net/EnvelopeStore.java`（isAuthorizedNow=Ed25519+device，P0 硬化）/`core/LeaseClock.java`（trustedNow 防回拨，24/72/144h）/`core/StateMachine.java`（isActive 四层）/`core/Bridge.java`（无首装时间键）/`ModuleMain.java`（无 A2 install）。

## 2026-06-23 · D-017 决策（总调度 C81 写入 DECISION_LOG）

- 防封授权闸 / A2 定性 = **重构**（既有防破解·授权·反检测架构的重构），按**版本轴**组织：官替 / 共存 / 管理（后台）；**不按 D-015 新功能 ①→⑤ 阶段序卡**。
- 解 step③「A2 接主线」时机 BLOCK（A2 = 官替/共存「能活下去」的内在刚需，非提前的未来功能）。
- **不撤回 D-015 / D-016**：新功能「功能先稳」仍有效；落代码仍须 授权检查官改前/改后审 + 安全官共审 + 守 2 watch。
- 官替/共存同 keystore；`P_AntiBanGate` 进看板登记。
- 证据：`DECISION_LOG.md` D-017（L137）、`TASK_BOARD.md`（L130）。

## 2026-06-23 · 配方卡落盘（C82）

- 写入本任务文件夹（UTF-8 无 BOM，已校验）：
  - `配方卡_SPEC_v1.md`（A–E 全文 + §F 版本适用面〔官替/共存/管理 + D-017 上报带包名/release_id〕 + §G 包名/打包 MD5 校验方案）。
  - `配方卡_params.json`（16 参数 + isAntiBanReady 6 分支真值表；风格沿用 `native_core/registry_8071.json`）。
- 校验：BOM=false ×2 / JSON.parse OK / ReadLints 零错误。

## 2026-06-23 · 服务器重构方案（C85，进行中 · 据总调度同步）

- 按版本统计 / 评分调整 / 下发 isAntiBanReady 结论；miyou-server / 管理版重构（安卓线）。
- 本 worklog 仅记录该并行线状态，细节以 C85 产出为准。

---

## 待办（落代码前必过）

- ⬜ **安全官共审**：改 `GuardRuntime` / `EnvelopeStore` / `LeaseClock` / `Bridge`（首装时间键）前，与网络安全官共审（机制本体 + 真锁）。
- ⬜ **MD5 方案落地**：扩 `tools/gate_three_axis.js` 发版门 + `release_manifest` 后台存（运行时自校验归阶段⑤ P32，本方案不加运行时代码，待指挥审）。
- ⬜ **P18 KPI 基线**：官方包跑 `frida_stats.js` 建零点（F-22），校准 60% 信任分 / 心跳频率。
- ⬜ step③ android_id 同源硬测（D1）：A2 接后两机 envelope `d` 仍各异。
