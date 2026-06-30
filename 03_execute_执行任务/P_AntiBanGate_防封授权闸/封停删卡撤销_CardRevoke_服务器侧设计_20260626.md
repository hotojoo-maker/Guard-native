# 封停/删卡撤销 · CardRevoke 服务器侧设计 + 施工单（块A）

> 日期：2026-06-26 ｜ Vchat `服务器维护ai-1`(F18) ｜ 服务器运维
> 需求权威：`授权风险场景矩阵_SPEC.md` §3#6 / §4 / §6 ｜ 客户端字段权威：`src/.../net/AuthEnvelopeVerifier.java`、`EnvelopeStore.java`
> 状态：**服务器侧已本地落码 + 自测绿（25/25）**；客户端块B `markCardRevoked` 已落码（2026-06-28 重命名前为 `markRefunded`）；**未部署**（待预算）
> 改 envelope/crypto → **待安全官复审**（本稿即送审材料）
> **命名（定死）**：本项目**无「退款」概念**。撤销唯一服务器动作 = 后台**封停 / 删卡密**。`rf` 仅保留作信封 wire key + 客户端存储键的历史缩写；服务器侧 `cards.refunded_at` / `refund_card` / `/admin/api/cards/refund` 为 miyou-server 仓库的历史符号名（本仓库不改，引用时保留真名）。

---

## 0. 一句话

封停/删卡撤销 = SPEC 里**唯一连坐 A2 的非篡改场景**（后台停了该卡 / 删了卡密）。撤销真生效 =
**后台标记 → 信封下发签名 `rf=1` + 散沙 → 客户端读 `rf` 立刻撤 A2+隐私并本地 latch**。

---

## 1. 字段契约（**已对齐**，非新拍 —— 客户端代码已锁定）

| 端 | 符号 | 形态 | 出处（L2） |
|----|------|------|-----------|
| 信封 payload | `rf`（wire key，历史缩写） | int，`1`=已封停/删卡，缺省 `0` | 客户端 `AuthEnvelopeVerifier`：`public int cardRevoked; e.cardRevoked = p.optInt("rf",0)` |
| 客户端持久 | `EnvelopeStore.K_CARD_REVOKED`（值 `"rf"`） | 可信时间戳（首次见 `rf=1` 落） | SPEC §4（E99 批） |
| 客户端方法 | `markCardRevoked()` / `isCardRevoked()` | latch + 判定 | SPEC §4 |
| A2 出口 | `isAntiBanReady = isIntegrityIntact && !isCardRevoked` | 封停/删卡是唯一例外 | SPEC §2/§4 |

> **结论：字段无需再议**，服务器照 `rf` 发即可（本稿服务器侧已实现）。`rf` 进 Ed25519 签名覆盖 → 不可剥离/伪造。`rf` 仅是 2 字符不透明键，语义=封停/删卡撤销（非退款）。

---

## 2. 信号链（“server push”的真实形态 = 搭信封顺风车）

信封是**拉取式**（客户端心跳 `/guard/envelope` 主动来取），无独立 push 通道。所以「封停/删卡撤销 push」=
**下一次信封把 `rf=1` 带下去**（客户端心跳周期级延迟，秒~分钟）：

```
客服后台点「封停/删卡」(/admin/api/cards/refund 历史接口名)
   → cards.refunded_at = now           （DB 标记历史列名，不改 status）
   → 审计 customer_audit_log: card_refund（历史事件名）
        ↓ 该设备下次心跳
verify_token(token) → status ok + cardRevoked=true   （不 hard-fail，否则信封发不出）
   → build_guard_envelope_payload(card_revoked=True):
        · payload["rf"] = 1            （进签名）
        · is_decoy=True → 包垃圾种子    （registry 解不开 = 隐私散沙，服务器侧也死）
   → Ed25519 签名下发
        ↓ 客户端
AuthEnvelopeVerifier 读 rf=1
   → EnvelopeStore.markCardRevoked()（落 K_CARD_REVOKED 可信时间戳，**离线永久 latch**）
   → isAntiBanReady=false（撤 A2）+ 撤隐私（revokeKeepToken/clear + RiskState 散沙）
```

- **两闸一起死**：隐私靠散沙（服务器包垃圾种子，**不依赖客户端配合也死**）；A2 靠客户端读 `rf` latch。
- **离线持久**：`K_CARD_REVOKED` 一旦落 = 永久撤销态，断网也撤，联网恢复不了（除非客服后台撤销标记——本期不做撤销，重购走新卡；D-020 已拍「可被有效授权恢复」为后续目标）。

---

## 3. 服务器落点（✅ 本次已落码 · `I:\miyou-server`，符号为该仓库历史真名）

| 落点 | 文件:符号 | 说明 |
|------|----------|------|
| 存储 | db.py:`cards.refunded_at`（`_ensure_column` 幂等加列） | 非空=已封停/删卡。**先改 SCHEMA.md 迁移记录**（铁律#1）✅ |
| 标撤销 | db.py:`refund_card(card_key,reason)` | 标 `refunded_at`，**不改 status**（让 verify 仍能签信封送 rf）；审计 `card_refund` |
| 回位 | db.py:`verify_token` | ok 结果加 `cardRevoked` 位（读 `refunded_at`），不 hard-fail |
| 拒激活 | db.py:`activate_card` | 已撤销卡新激活 → `REFUNDED`（历史返回码）+ 审计（已激活设备走信封撤，不走 activate） |
| 信封 | crypto_utils.py:`build_guard_envelope_payload(card_revoked=)` | `rf=1` + `is_decoy=True`(复用现有 decoy 散沙路径，不新造检测面) |
| 路由 | server.py:`/guard/envelope` | 把 `verify_result.cardRevoked` 传进 build |
| 后台 | server.py:`POST /admin/api/cards/refund` | 客服一键封停/删卡（admin 鉴权，历史接口名） |

> ⚠️ 上表 `refunded_at` / `refund_card` / `card_refund` / `/admin/api/cards/refund` / `REFUNDED` 是 miyou-server 仓库的**历史符号名**，本次客户端重命名**不动服务器仓库**（跨仓库契约 + 部署成本）；若后续服务器侧统一改名，须与客户端 wire key `rf` 一并走版本灰度。

---

## 4. 客户端块B（已落码 · 2026-06-28 符号重命名后）

1. `EnvelopeStore`：`K_CARD_REVOKED="rf"`；`markCardRevoked()`（落可信时间戳，`LeaseClock.trustedNow`）；`isCardRevoked()`/`getCardRevokedAt()`；`debugSetCardRevoked()`（DEBUG）。
2. 收到验签通过的信封且 `e.cardRevoked==1` → `markCardRevoked()` + 撤隐私（`isAuthorizedNow()` 首判即 false → `isActive` 四层断）。
3. `GuardRuntime.isAntiBanReady`：`!isCardRevoked() && CompatProbe.isIntegrityIntact`（封停/删卡是唯一例外）。
4. latch 后即使后续信封无 `rf` 也保持撤销态（防回拨/防漏发）。

---

## 5. 边界 / 边缘

- **送达前提**：信封要能签发（token 有效 + license 未过期 + 该线走真锁 48B `k` 路径）`rf` 才到客户端。
- **license 已过期 + 封停/删卡** 的 corner：`s=0` 时客户端 `verifyAndParse` 提前返回 null，读不到 `rf`。但过期隐私本已失效；A2 对过期=仍装（场景#4）。此 corner 概率低（撤销多在有效期内），列为 L4 待观察，块B latch 一次即可永久。
- **HMAC 回退线（无 rel_keys）**`k=32B`，客户端要求 48B → 本就解析失败，与撤销无关；生产线（android_8071）走 48B AESGCM，正常。
- **重购/换机**：新卡 `refunded_at` 空 = 正常；旧已撤销卡保持撤销态。device_id 主键关联历史见 spec§7。

---

## 6. 红线核对

- `rf` 进 Ed25519 签名覆盖（不可剥离/伪造）✅
- 撤销**不清用户密友/密码**（红线#8）✅
- 散沙**复用现有 decoy 路径**，不新增环境检测面（防封 KPI）✅
- 后台只存 `refunded_at` 时间戳，无密钥原文（红线#1/#10）✅
- 改 schema 先文档（铁律#1）；自测走临时库不碰 auth.db（红线#9）✅

---

## 7. 验证

- `customer_ops_selftest.py` **25/25 ALL_PASS**：含 `refund_card`→审计 `card_refund`、`verify_token.cardRevoked=True`、已撤销卡 activate 拒 `REFUNDED`、信封撤销 `rf=1` / 非撤销无 `rf`。
- `py_compile` 全过；0 lint。
- 待预算：部署 + 真机（后台标记→真机心跳→密友散沙+弹无、A2 撤）。
