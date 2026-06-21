# S3a-0 设计补丁 — 服务器短命 seed 折入 SO key（仅设计，不接冷启动）

> 任务来源：2026-06-11 授权检查官「WARN / 受控通过」+ 安全官「BLOCK 直接 S3a 硬接入 → 先做 S3a-0 设计」双裁决。
> 性质：**设计文档，不动代码逻辑、不接 `ModuleMain` 冷启动**。证据等级 **L2 静态**（只读核查代码，未跑装机）。
> 权威关联：`PROTECTION_MAP.md` §10.6（S2 现状唯一权威）· 安全官 skill「Key 来源准则 / 粗粒度解密包」· `docs/RELEASE_RULES.md` §加密接手清单。

> **现状提示（2026-06-11 口径统一）**：本文是 S3a-0 历史设计，不再代表当前实现状态。当前 `android_8071` 已切 `prod_server_lock`：`registry_cipher.inc` 为 `GUARD_REGISTRY_REQUIRES_SERVER_SEED=1`，线上 envelope unwrap 后 `recipeOk=true`；S4 Ed25519 与 S3b LeaseClock 也已装机 PASS。现状以本目录 `result.md` + `PROTECTION_MAP.md` §10.6 为准。

---

## 0. 一句话目标

把「怎么折 server seed 进 key、无信封时怎么 scatter、如何不误伤当前 v1」设计清楚，**再动代码**。本轮交付 = 本文。

### 0.1 发包 / 分发边界（用户口径）

S3a 只管真锁材料如何参与 registry key，**不改变发包方式**：

- APK 由本地 AI 构建签名，只产出 **官替版 APK** 和 **共存版 APK**。
- 网盘只放 APK；用户放哪里都可以，网盘路径不写入发版档案，也不作为项目状态。
- 服务器只负责授权 / envelope / 真锁材料登记，**不参与打包、不托管 APK、不决定下载路径**。
- 后续任何“服务器真锁材料流程”描述都只指授权和真锁材料，不指 APK 分发。

---

## 1. 现状盘点（L2，逐条 file:line）

### 1.1 通路已全部就位（plumbing 通，只是没「必须」）
```
EnvelopeClient.fetchEnvelope(token)
  → AuthEnvelopeVerifier.verifyAndParse → Envelope.keyMaterial(k=ct32||tag16) + keyNonce(n)
  → NativeBridge.unwrapServerSeed(k, n)               [NativeBridge.java:304]
  → JNI nativeUnwrapServerSeed                         [guard_core.cpp:281]
  → guard::unwrap_server_seed(k, n)                    [config_crypto.cpp:412]
       k 用 W[:16]=g_wk_lo||g_wk_hi 解 AES-128-GCM(nonce=n[:12]) → S_rel(32) → g_server_seed
  → derive_registry_key()：g_server_seed_len>0 时折入 S_rel  [config_crypto.cpp:462-466]
```
生成端镜像：`gen_registry_cipher.py::derive_registry_key(server_seed)`（`:76`），`server_seed` 来自 `GUARD_S_REL_B64` 环境变量（`:102`），默认空。

### 1.2 当前是「fail-OPEN（server seed 可选）」——这正是冲突点
- `gen_registry_cipher.py`：默认 `_S_REL=b""` → `slen=0` → **不折** server seed → 当前 `registry_cipher.inc` 是 **cert-only** 产物。
- `config_crypto.cpp::derive_registry_key`：`g_server_seed_len==0` 时**跳过** server seed 折入 → 退回 cert-only A-step2 key → 与 cert-only 的 `.inc` 匹配 → 解密成功。
- 结果：**没有服务器也能解开 registry**（P_NC1 / `PHASE1C/1D_VERIFY` PASS）。这是 v1 能离线工作的原因，也是「还不是真锁」的原因。

### 1.3 PROTECTION_MAP §10.6 的诚实口径（与上一致）
> 「服务器短命 key 材料 `k` **未折进 SO registry key**（`derive_registry_key` 现只折证书 SHA-256 → 动态 dump / 自跑 key 仍可取 registry）。」

### 1.4 半成品 TODO（S3a 必须补全）
- `GuardActivation.java:57`：`certHex=""` / `appVersion=""` 是 TODO，**心跳还没把 cert 同源传下去**。

---

## 2. 核心冲突（安全官 BLOCK 的根因）

| | 现状（cert-only，fail-open） | 真 S3a（S_rel-folded，fail-closed） |
|---|---|---|
| `.inc` 生成 | 不折 S_rel | 折入 S_rel |
| 无信封运行 | cert-only key → **解开** | cert-only key ≠ S_rel-folded key → **scatter** |
| v1 离线自用 | 正常 | **隐私 hook 全挂（误伤自己/正版）** |

**直接把 S3a 接上去 = 把 v1 离线能力打死。** 所以必须引入 **DEV/PROD 分档** + **S_rel 三源同源**，且本轮**只设计不切换**。

---

## 3. 设计（按裁决的 4 个允许范围）

### 3.1 S_rel 来源：发版档案 / 服务器 envelope / 生成脚本「三源同源」

唯一真源 = **发版档案（release archive，离线）**。每条 `release_id`（如 `android_8071_hijack` / `android_8071_coexist`）持有一组**绑定材料**：

```text
release_id:      android_8071_hijack
S_rel:           <32 字节随机，离线生成，永不入库/不进聊天>   ← 唯一真源
W (wrapping key):<16 字节，= SO 内 g_wk_lo||g_wk_hi 必须等值>
cert_sha256:     <该发行证书 SHA-256，= setBindingMaterial 同源>
```

三处消费同一个 `S_rel`：
1. **生成脚本**：打包时从发版档案读 `S_rel`，折进 `registry_cipher.inc` 的 key。
2. **服务器（miyou-server）**：持 `W`，下发信封时用 `k = AES-128-GCM(S_rel, key=W, nonce=n)`。客户端 unwrap 出的就是同一个 `S_rel`。
3. **SO 运行端**：持 `W`（`g_wk`），unwrap 信封 `k` → `S_rel` → 折进 key。

> **不变式（铁律）**：同一 `release_id` 下，`gen 脚本折入的 S_rel` ≡ `服务器 k 解出的 S_rel` ≡ `发版档案的 S_rel`。任一不等 → registry scatter。
> **W 与 S_rel 是两份不同材料**（`config_crypto.cpp:358` 已有 [GUARD-TRAP] 警告），档案里必须分开记，禁止合并。
> 发版档案存储遵守 `RELEASE_RULES.md`：S_rel / W / keystore **离线两份、禁入公开仓库/聊天/日志**。

### 3.2 `tools/gen_registry_cipher.py`：server seed 参数设计（仅设计）

现状 `GUARD_S_REL_B64` 环境变量是「临时口子」。S3a 形态改为**发版档案驱动**：

- 入参来源：从发版档案（按 `release_id`）读 `S_rel`，不再裸用 ad-hoc 环境变量（环境变量保留为 DEV 后门）。
- 行为分档（**本轮只设计，不改默认**）：
  - `DEV`（默认，= 当前）：无 `S_rel` → 不折 → cert-only `.inc`（向后兼容，v1 不变）。
  - `PROD`（S3a）：**必须**提供 `S_rel`，否则脚本**报错退出**（拒绝产出「假装真锁」的 cert-only 包）。
- 产物可追：脚本已 `print("server_seed_folded:", ...)`；PROD 下追加打印 `release_id` + `S_rel 指纹（sha256(S_rel)[:8]，非原文）` 进构建日志，供回溯。

### 3.3 `config_crypto.cpp`：「server seed required」开关 / DEV-PROD 分支（仅设计）

引入**编译期**开关（建议名 `GUARD_REQUIRE_SERVER_SEED`，由构建注入，默认关）：

```text
derive 时：
  DEV  (GUARD_REQUIRE_SERVER_SEED 未定义)：g_server_seed_len==0 → 跳过折入（= 现状，cert-only 可解）。
  PROD (GUARD_REQUIRE_SERVER_SEED=1)    ：registry 解密入口先判 g_server_seed_len，
                                          ==0 → 直接 make_scatter()（fail-closed），
                                          不退回 cert-only 真 registry。
```

要点：
- 落点在**安全层**（`registry_load_embedded` / `derive_registry_key` 调用前的守门），**不进** Filter / hook 回调体（守授权检查官红线）。
- DEV 兼容档明确写注释 `// [GUARD-TRAP] DEV-only：PROD 必须 require server seed`，防被顺手当成最终态。
- `unwrap_server_seed` 失败已是 fail-closed（`:416` 先清 seed）——PROD 下「unwrap 失败 = 无 seed = scatter」天然成立。

### 3.4 触发边界：仍只 DebugServer，**禁** ModuleMain 冷启动

- `GuardActivation.activate()` 仍是唯一编排入口，触发点**仅** `DebugServer`（DEV-gate 后）。
- **禁止**把 `GuardHeartbeat.start()` 接进 `ModuleMain` 冷启动（保护区，安全官硬约束）。
- S3a-0 阶段：unwrap→fold 链只在 DEV-gated 激活流里**手动**验证，不进正常冷启动。
- 配套补全 `GuardActivation.java:57` 的 `certHex`：必须与 `setBindingMaterial`（`ModuleMain.java:262`）**同源**——cert + server seed 两份材料都要参与，缺一不一致即 scatter。

---

## 4. 不做（本轮明确排除，守裁决）

- ❌ S3a-0 当时不碰 `StateMachine.isVipAuthorized()`（历史约束；现状已在 v1.1 授权闭环中接 `EnvelopeStore.isAuthorizedNow()`）。
- ❌ 不让 Filter / hook 回调读授权 / risk / 服务器时间；业务层继续只读 `GuardRuntime.getRecipe()` / `StateMachine.isActive()`。
- ❌ 不接 `GuardHeartbeat.start()` 进冷启动。
- ❌ 不改 `derive_registry_key` 默认行为、不重新生成 `registry_cipher.inc`、不切 PROD 开关。
- ❌ S3a-0 当时不做 Ed25519（S4）、不做 LeaseClock 喂数（S3b）；现状二者均已装机 PASS，见 `result.md`。

---

## 5. 与 V3 改包路线的冲突（落地前必须先对齐，来自 PROTECTION_MAP §10.6 ②③）

S3a fail-closed 一旦切 PROD，**没有 fallback 兜底**：
1. **删 fallback = 重签即死**：任何「证书/seed 变了却没重生成 `registry_cipher`」的重签/改包 → 钥匙错 → registry 散沙 → 隐私 hook 静默全挂。⇒ S3a-PROD 必须与 V3 本地 AI 发版流程**绑定同一套步骤**（每个 `release_id` 都重生成 cipher + 装机回归）。
2. **共存版改包名 → 误判篡改**：`anti_tamper` 期望包名必须随 `WX_PKG`/`guardWxPkg` 注入，不能硬编码 `com.tencent.mm`。
⇒ **S3a 切 PROD 前置条件**：V3 包名注入 + 每发行线独立 `registry_cipher.inc` 本地生成步骤先就位。

---

## 6. 建议的后续步序（每步前过授权检查官 + 安全官「改前审查」+ 先 git 快照）

```text
S3a-0  本文（设计）                                          ← 当前，仅文档
S3a-1  发版档案 schema 落地（S_rel / W / cert 三材料分槽）+ gen 脚本读档案（DEV 默认不变）
S3a-2  config_crypto 加 GUARD_REQUIRE_SERVER_SEED 守门（默认关，DEV 行为零变化）+ 主机自测覆盖两档
S3a-3  GuardActivation certHex 同源补全 + DEV-gated 手动验 unwrap→fold→解密链（仍不进冷启动）
S3a-4  （需 miyou-server 在线 + V3 本地 AI 发版流程就位后）才谈切 PROD fail-closed
```

---

## 7. 安全官改前审查（本设计自审）

```markdown
结论：PASS（仅设计，不落代码）
1. 是否触碰授权根锁：否（不碰 isVipAuthorized stub）
2. 是否触碰 SO 解密：设计层触