# STATUS · 防封/加密线（当前真态 · 读这一页就够）

> 新 AI 接手：先读这一页 + `DECISION_LOG.md`，不用让用户重讲。
> 维护铁律：谁改谁更新（G9）；冲突以代码为准（G8）；同一结论只此一处或指向单一真源（G10）。
> 最后更新：2026-06-26
> 单一真源：机制 = `03_execute_执行任务/P_AntiBanGate_防封授权闸/DESIGN.md` ｜ 规则 = 安全官 skill ｜ 坑 = `FAILURE_LOG.md` ｜ 决策 = `DECISION_LOG.md`

---

## 几条线（绕防封/加密的，3 条交织）

- **线1 AntiBanGate（A2 防封）**：A2 签名轴 / `isAntiBanReady` / 统一风控引擎（DESIGN.md）
- **线2 RB1（钥匙加固）**：W_dev 一机一密（牙③）+ 重放绑定（牙④）
- **线3 LeanCloseout（减法收口）**：删明文 fallback / registry 归一 / 文档减法

旁支：ServerSeed（S3a0）、隐私功能线（藏密友，稳定）、LSPatch 崩溃（与防封无关）。

---

## 各到哪 + 验没验（证据等级 + 日期）

| 件 | 到哪 | 验证 |
|---|---|---|
| A2 能抓能骑 | `A2SignatureSpoof` 已接 `ModuleMain` 6.7 | L1 2026-06-25（`recon/A2_RIDE_TEST_20260625.md`）|
| A2 闸 `isAntiBanReady` | = 本地 cert 完整性（Route B / D-018；canary 不进门）| L2 + **装机 L1 PASS（E99 2026-06-26）**：首装未授权 ready=true/der=751B/level=正常 · 隐藏不连坐(removed wxid)/0 崩溃 · 重签散沙收红队 |
| W_dev 牙③（设备绑定）| batch0/1 双试已装机 | L1 2026-06-26 `recipeOk=true`（commit `caf2142`）|
| 重放绑定 牙④ | 设计 only（a 案）| L4 未落码 |
| 蜜罐绊线 / canary | 改诱饵被抓、自家包不误报 | L1 2026-06-12 + **静态重验 OK（E99）**：PromoConfig 未改/check() 未动/BASELINE 不变 → 三值仍平；canary 不进 A2 闸(B加强) |
| 引流弹窗 | debug 强制能弹能跳 | L1 2026-06-12 + 静态重验 OK（E99）：FunnelPrompt/RiskPromptController 06-12 以来零提交 |
| 不误伤正版 | 正版 `level=正常`、密友隐藏 | L1 2026-06-12 + **L1 重验 PASS（E99 2026-06-26）**：当前码 level=正常 + 密友隐藏(removed wxid) + 0 崩溃 |
| 「等 7 天→散沙」时间链 | — | ⚠️ 未现场验 |
| 删 release 明文 fallback（D8）| 六缺口未闭、5 处内联残留 | 阻塞（`recon/R5_加密进度核查_20260625.md`）|

---

## 下一步

1. **【AI-1 代码】** ✅ A2 闸改本地完整性（Route B · cert-only）+ 本地 DER 常量 — 码已落·lint 净·DER 校验过·**装机 Test1（首装未授权防住）/ Test2（隐藏不连坐）L1 PASS（E99）·本轮 commit**；Test3 重签散沙收红队。
2. **【重验】** ✅ 静态重验已由 AI-1 兼做（E99）：canary / 弹窗 / 不误伤 的 06-12 机制静态成立（见上表）；不误伤正版另有 L1 重验 PASS。
3. **【上线前门控】** 红蓝对抗（`红队压测验证任务书_20260626.md`，含 **Test3 重签散沙** live）。

---

## 最新拍板

- **D-018（2026-06-26）**：A2 防封闸改吊本地完整性 + 本地 DER（取代 D-017 的「授权 + 服务器种子」口径）；取舍 = 防封对「完整但未付费」者免费。详见 `DECISION_LOG.md` D-018。
