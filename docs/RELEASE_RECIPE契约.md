# RELEASE_RECIPE 契约 —— 真锁发版配方（客户端 ↔ miyou-server 双边唯一真相）

> **定位**：真锁（S3a 服务器短命种子折进 SO key）落地时，**客户端构建** 和 **miyou-server（另一个 AI 负责）** 必须用**同一份 per-release 配方**，否则三方 S_rel 不同步 → 所有人 registry 散沙 → 隐私 hook 全挂。本契约固定两边字段语义、机密边界、发版步骤。
> **权威**：真锁现状 + 风险以 `../PROTECTION_MAP.md` §10.6 为准；本文只定「配方文件」这一契约。
> **状态**：🟡 契约 + 客户端读取已就绪；**未切 prod_server_lock 硬失败**（仍 dev_cert_only，不破坏现网）。切硬失败 = S3a-PROD，需两边发布流水线打通 + 授权检查官/安全官改前审查 + git 快照。

---

## 1. 一个版本 = 一份配方

- 文件：`release/secrets/<release_id>.json`（真配方，**含机密，gitignore，绝不进 git**；两个 AI 之间安全传递）。
- 模板：`release/release_recipe.example.json`（无真密钥，进 git，给人对字段）。
- 版本号 `release_id` 对上 = 两边用同一份 = 不会错配。

## 2. 字段语义 + 谁读

| 字段 | 含义 | 客户端构建 | miyou-server（另一个 AI） | 机密 |
|---|---|---|---|---|
| `release_id` | 版本标识 | 对齐 | 对齐 | 公开 |
| `schema_id` | registry schema（`r8071_v1`） | 对齐 | 信封 `z` 字段对齐 | 公开 |
| `wechat_version` | `8.0.71` | 对齐 | 信封 `w` 字段对齐 | 公开 |
| `kid` | 密钥 id（`e1`） | 对齐 | 信封 `kid` | 公开 |
| `registry_mode` | `dev_cert_only` / `prod_server_lock` | gen 脚本模式 | — | 公开 |
| `s_rel_b64` | 32 字节服务器短命种子 | **gen 折进 registry 密文（不进客户端二进制）** | 发信封时生成 `k` 用 | **机密** |
| `wrap_key_b64` | 16 字节 W（解 `k` 用） | 必须 = SO 里 `config_crypto.cpp` 的 W（`g_wk_lo`+`g_wk_hi`） | 用它加密 `k` | **机密** |
| `ed25519_public_b64` | 验签公钥 | 内置进 `AuthEnvelopeVerifier` | 对应私钥仅服务器持有 | 公钥公开 |
| `endpoints` | 授权服务器域名 | gen_bootstrap_cipher 用 | — | 公开 |
| `funnel_url` | 盗版引流落地页 | `AppConfig.SHOP_URL` | miyou.pro 客服侧 | 公开 |

## 3. 机密边界（铁律）

- `s_rel` / `wrap_key` 是机密：**真配方不进 git**（`release/secrets/` 已 gitignore），两个 AI 之间安全传递。
- `s_rel` **从不**进客户端二进制——它只在**构建时**被 `gen_registry_cipher.py` 折进 registry 密文；运行时客户端从服务器信封的 `k` 解出。
- `wrap_key`(W) 会编进客户端 SO（解 `k` 用），是已知弱点；防线靠短命租约 + 设备绑定 + 服务器轮换（安全官 skill）。
- Ed25519 私钥只在服务器，永不进配方/客户端。

## 4. 发版步骤（两边同步）

1. 生成真配方：`release/secrets/<release_id>.json`（填真随机 `s_rel`(32B)、`wrap_key`(16B)）。
2. **客户端**：
   - `config_crypto.cpp` 的 W 必须等于 `wrap_key_b64`（改了要重编 SO）。
   - `python tools/gen_registry_cipher.py --recipe release/secrets/<release_id>.json`（prod_server_lock 模式从配方读 `s_rel` 折进 `registry_cipher.inc`）。
   - `AuthEnvelopeVerifier` 内置 `ed25519_public_b64`。
   - `gen_bootstrap_cipher.py`（域名引导段）。
   - NDK 重编 + 装机回归。
3. **miyou-server（另一个 AI）**：加载同一份配方，用 `s_rel`+`wrap_key` 生成信封 `k = AES-128-GCM(s_rel, key=wrap_key, nonce=n[:12])`，用对应 Ed25519 私钥签信封。
4. 验收：真卡密激活 → 信封 `k` → 客户端解出 `s_rel` → registry 解开（prod_server_lock 下无有效信封 = 散沙）。
5. 换版本 = 换 `release_id` + 换 `s_rel`（可选换 `wrap_key`/密钥对），两边同步换。

## 5. ⚠️ 切 prod_server_lock 硬失败前必读

- 现状仍 `dev_cert_only`（registry 只靠证书绑定解，不需 `s_rel`）——不破坏现网。
- 切 `prod_server_lock` 且**删 Filter 明文 fallback** 后，任何「证书/种子没同步」的包 → registry 散沙 → 隐私 hook 全挂（PROTECTION_MAP §10.6「删 fallback = 重签即死」）。
- 因此「切硬失败」必须：① 客户端 unwrap 用真信封装机验过（S3a-1 仅证明没破坏，未证明真 unwrap）② 服务器发同一 `s_rel` 的 `k` ③ 与 V3 改包证书源对齐（§10.6 ⚠️）④ 绑同一条发布流水线。
