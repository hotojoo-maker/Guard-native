# P_RB1 重放绑定（ReplayBind）— SO 端 envelope 摘要折入 key 派生

> 立项：2026-06-12（用户拍板「3b 立档期」）。状态：⬜ 未开工（仅排期，未动代码）。
> 来源：PROTECTION_MAP §10.7 P1.1 + 安全官 skill「Key 来源准则 / 防重放」。
> 优先级：**P1（不紧急，但要正经做）**——见下「为什么不紧急」。

---

## 1. 背景 / 要堵的缝

当前 SO 的 `unwrap_server_seed()`（`native_core/src/config_crypto.cpp:416`）只用**写死在 SO 里的 wrapping key W**（`g_wk_lo`/`g_wk_hi`）做 AES-GCM 解 `k → S_rel`，**不验时间、不验设备、不验签名、无重放保护、无失败计数**。

后果（L2）：
- 攻击者在**自己那台设备**上抓到一次合法 `k/n`（或 dump 内存里的 `g_server_seed` / dump 解出的 registry），就能让 registry **永久解开**——SO 没时钟，过期信封的 `k/n` 照样能 unwrap。
- W 是静态明文常量，违反安全官 skill L274「AES key 不得是 SO 里的静态明文常量」/ L286「服务器材料必须参与最终 key 派生」（W 这把解封 key 本身没有服务器材料）。

## 2. 为什么不紧急（但仍要做）

- **转卖这条已被设备绑定堵死**：`AuthEnvelopeVerifier` 验 `payload.d == sha256(deviceId)`，A 机的信封到 B 机直接散沙。所以「把破解包/信封卖给别人」别人机器上不工作。
- W 静态这条缝只剩「**他自己那台机器 + 租约未过期 + 会 hook Java**」的自用，**分发不出去**。
- 因此对「拿去卖」这个主威胁不致命；属加固项，不是救火项。

## 3. 目标

让「能不能解出 registry」绑定到「**已 Ed25519 验签的 envelope 摘要**」，使：
- 过期 / 重放的旧 `k/n` → 推出错 key → registry 散沙；
- 离线重放一个抓到的旧信封失效，短租约在 SO 层才真正有牙。

## 4. 方案（四处联动，缺一不可）

1. **服务器（miyou-server）**：信封 payload 里带（或已有）`expire_at` / `device` / `release` / `signed-digest`，确保这些字段进 Ed25519 签名。
2. **客户端 Java**：`AuthEnvelopeVerifier` 验签通过后，把「已验签摘要（expire_at + device + release + payload digest）」下推 SO（新增 `NativeBridge.setEnvelopeDigest(...)` 类入口）。
3. **SO**：`derive_registry_key()`（`config_crypto.cpp:436`）把该已验签摘要也折进 key 派生（与 `gen_registry_cipher.py::derive_registry_key()` **逐字节镜像**）。
4. **构建**：用匹配的派生重新生成 `registry_cipher.inc`（`tools/gen_registry_cipher.py`），全发行线重出。

## 5. 涉及文件

- `native_core/src/config_crypto.cpp`（`derive_registry_key` / 新摘要折入）
- `native_core/src/guard_core.cpp` + `core/NativeBridge.java`（新 JNI 入口）
- `src/main/java/com/ghost/assist/net/AuthEnvelopeVerifier.java` / `EnvelopeStore.java`（下推已验签摘要）
- `tools/gen_registry_cipher.py`（镜像派生 + 重生成 cipher）
- miyou-server 信封签名字段（`I:\miyou-server`）

## 6. 风险

- **碰加密核心 + 重生成 registry_cipher**：一旦派生对不齐，正版机 `recipeOk=false` → registry 散沙 → **已装机跑通的密友隐藏静默全挂（铁律 29 / F-31）**。
- 改 `derive_registry_key` 必须 Java/SO/tools 三处**逐字节一致**，否则正版自废。
- 服务器侧需同步，属跨仓库改动。

## 7. 工作量 / 前置

- 估：安全官 skill「上线标准」量级的一部分，约数人天（含服务器 + 装机回归）。
- **前置（硬性）**：① `guard-auth-review` + `guard-security` 改前审查 PASS；② git 快照；③ 分步装机回归（每步先验正版 `recipeOk=true` + 密友隐藏不挂）。

## 8. 验收（负向为主）

- 拿一个**已过期**的合法 `k/n` 喂 SO → `registrySummary=scatter` / `isConfigReady=false`。
- 换设备 / 改 release → 同样散沙。
- 正版正常激活 → `recipeOk=true`、密友隐藏照常（不误伤）。
- Java/SO/tools 三处派生一致性自测向量通过。

## 9. 不做 / 边界

- 不追求白盒密码、不去 hook 微信 SO（铁律 23）。
- W 不必「删」（bootstrap 解封 key 无法折服务器材料）；目标是降低「离线重放一个抓到的信封」的价值，不是消灭 W。
- Frida 运行时 dump `decrypt_config` 出参仍是更高威胁层，靠短租约 + 设备绑定 + 服务端轮换 + 水印，不在本档范围。
