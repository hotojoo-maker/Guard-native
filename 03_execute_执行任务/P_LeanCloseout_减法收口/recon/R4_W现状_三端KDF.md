# R4 · W（wrapping key）现状 + 三端 KDF 现状（只读侦察）

> **角色**：只读侦察 AI。只盘点、不改码、不改它文档（仅新建本报告）。
> **证据口径**：L2 = 源码直证（file:line）；L3 = 多信号收敛；L4 = 待验证（无 L1/L2 直证，多为跨仓/服务器侧）；❓ = 文档张力待裁。
> **复核日期**：2026-06-25
> **输入（均只读）**：`P_RB1_重放绑定/钥匙加固_KeyHardening设计.md`、`native_core/src/config_crypto.cpp`、`tools/gen_registry_cipher.py`、`tools/gen_bootstrap_cipher.py`、`src/main/java/com/ghost/assist/core/NativeBridge.java`、`PROTECTION_MAP §10.6/§10.7`、`DESIGN.md §0/§5`、安全官 skill「Key 来源准则/三端对账」、任务卡 §4(E9/B2)。
> **与 R3 不重叠**：R3 = 发版线/cert 轴/s_rel 管道（D8 前置）；**R4 = W（解信封钥匙）+ 三端钥匙派生 + per-device/防重放（牙③④）**。

---

## 0. TL;DR（一句话）

**钥匙的「设备牙」「防重放牙」都还没长——代码里零实现，只在设计稿。** registry 钥匙现役 2 颗咬合牙（cert + server seed）已三端镜像对齐；但解信封的 **W 是全局静态明文一把（`config_crypto.cpp:360-361`）**→ 抽一台 SO 的 W + 任意机合法信封 = 离线通杀全 release；`unwrap_server_seed` **不验时间/签名/重放**（`:416-434`）→ 旧信封可重放。这正是决策#25(P0)/#26(P1) 要补的，也是任务卡 E9/B2「W→W_dev」的目标。**当前不能称「真锁终局」。**

---

## 1. 五颗牙状态（对照代码逐颗核 · L2）

> 钥匙加固设计 §0：registry 钥匙 = 多颗牙的「与门」，缺一即散沙。

| # | 牙（折进钥匙的料）| 设计状态 | **代码实测** | 证据 file:line |
|---|---|---|---|---|
| ① | 模块签名证书 SHA-256 | ✅ 现役 | ✅ 真折入：SO `g_binding` + 脚本 `_CERT_SHA256` | `config_crypto.cpp:458-462`；`gen_registry_cipher.py:89-92`；`NativeBridge.setBindingMaterial:306-309` |
| ② | 服务器种子 S_rel | ✅ 现役 | ✅ 真折入：SO `g_server_seed` + 脚本 `server_seed` | `config_crypto.cpp:468-472`；`gen_registry_cipher.py:95-98`；`NativeBridge.unwrapServerSeed:320-333` |
| ③ | 设备指纹（W 一机一密）| ⬜ 设计only | ❌ **代码零实现** | grep `setDeviceMaterial/W_dev/wseg/computeDeviceMaterial/g_device_mat` → **仅命中设计稿/skill，无任何 .java/.cpp/.py** |
| ④ | 信封摘要（防重放）| ⬜ 设计only | ❌ **代码零实现** | grep `setEnvelopeDigest` → **仅设计稿**；`unwrap_server_seed` 无时间/签名/重放校验（`config_crypto.cpp:416-434`） |
| 外围 | Ed25519 验签 / 弹窗 canary / 蜜罐 canary | ✅ 现役 | ✅（不在本文件，见 §10.6 S4 / §10.9） | — |

**结论**：现役 = 牙①②（2 咬合）+ 外围 3 道 = 5 道；满配 7 道。**缺口 = 牙③(P0/决策#25) + 牙④(P1/决策#26)，两颗都是「设计已写、代码未动」。**

---

## 2. W（解信封 wrapping key）现状（L2）

| 项 | 现状 | 证据 |
|---|---|---|
| W 是什么 | AES-128 key，解信封 `k` → `S_rel`（再折进 registry 钥匙） | `config_crypto.cpp:355-359` [GUARD-TRAP] 注释：W ≠ S_rel ≠ HMAC secret，三种不同 per-release 料 |
| W 存哪 | **SO 内全局静态明文常量**，拆两半 `g_wk_lo[8]` + `g_wk_hi[8]`，用时拼回 | `config_crypto.cpp:360-361`（常量）；`:424-426`（unwrap 时 `memcpy` 拼 `wk[16]`）|
| 谁还有 W | miyou-server config 持同 16 字节（注释自述）；recipe 有 `wrap_key_b64` 字段（R3 §1.2 例.json）| `config_crypto.cpp:356-357` |
| **缝（牙③要治）** | **一把 W 通杀所有设备**：抽一台 SO 的 W + 任意机抓的合法 `k` → 离线 GCM 解出 `S_rel` → 解 registry | 钥匙加固 §1（L2）；`unwrap_server_seed:424-429` |
| ⚠️ W 的生成链 | `gen_registry_cipher.py` **不生成/不注入 W**（只生 `registry_cipher.inc`）；本次 tools/ 未见任何把 `wrap_key_b64` 写进 SO 的生成器 → **W = 手改 `config_crypto.cpp` 常量 + 服务器手动同步**（与 R3 的 cert 轴同病：手维护、非流水线）| `gen_registry_cipher.py` 全文无 W；tools glob 无 wk 生成器（L2 缺席证据）|

---

## 3. 三端 KDF 现状（`derive_registry_key` 镜像）（L2）

「三端」= ① SO `config_crypto.cpp` ② 生成脚本 `gen_registry_cipher.py` ③ Java（**只当材料搬运工，不自己算 key**）。

| 端 | 角色 | 现状 | 证据 |
|---|---|---|---|
| SO | 真算 key：seg_a/b/c 非线性混 + 折 cert(g_binding) + 折 S_rel(g_server_seed) | ✅ | `config_crypto.cpp:436-475` |
| 生成脚本 | 镜像同一派生（出 `registry_cipher.inc`）| ✅ **与 SO 逐字节对齐**（seg 常量同值、混淆/折叠顺序同、取址同）| `gen_registry_cipher.py:80-100` vs `config_crypto.cpp:444-473` |
| Java | 只推材料：`setBindingMaterial`(cert) / `unwrapServerSeed`(S_rel)；**不含 device/digest 推送** | 牙①②齐、牙③④缺 | `NativeBridge:306-309/320-333/364-365`（无 `nativeSetDeviceMaterial`/`nativeSetEnvelopeDigest`）|

**附：bootstrap key（C2 引流域名/AUTH 端点）** = `derive_bootstrap_key`（`config_crypto.cpp:477-514`），同 seg + cert，但折固定 `dom` 域分离常量、**永不折 server seed**（否则 prod_server_lock 死锁：要服务器才有 seed、要 seed 才解端点）。镜像端 = `gen_bootstrap_cipher.py`。此设计正确，不在牙③④范围。

**当前无三端漂移**（牙①②）：SO↔脚本 `derive_registry_key` 实测同源（L2）。⚠️ 但牙③(W_dev)/牙④(digest) 一旦落地 = 改派生 = F-31 静默翻车重灾区，**必须先过三端测试向量再动**（铁律真源 = 安全官 skill §三端对账；钥匙加固 §4）。

---

## 4. unwrap 防重放缺口（牙④）（L2）

`unwrap_server_seed`（`config_crypto.cpp:416-434`）= 纯 `AES-128-GCM(W, n, k) → S_rel`，**无 `expire_at` 校验、无签名校验、无 nonce 重放记录**。
→ 抓一次合法 `k/n`（或 dump `g_server_seed`），过期/旧信封照样 unwrap，registry **永久解开**（钥匙加固 §1 缝2 / §3）。短租约在「SO 层」目前**没有牙**（到期判断现仍在 Java，破解客户端可跳过）。

---

## 5. W_dev（牙③）落地缺口清单（任务卡 E9/B2 = 这件事）

| # | 缺口 | 现状 | 等级 |
|:--:|---|---|:--:|
| G1 | SO `setDeviceMaterial` + `g_device_mat` + `wseg_a/b/c` | 不存在 | L2 |
| G2 | SO `unwrap_server_seed` 改用 `W_dev = KDF(wseg + D_mat)` 取代全局 W | 未改 | L2 |
| G3 | Java `computeDeviceMaterial`（全 32B `SHA-256(ANDROID_ID)`，与现 8B `computeDeviceHash` 并存）| 不存在（现仅 `computeDeviceHash` 8B，R3/落码前清单已知）| L2 |
| G4 | Java 三类请求（激活/信封/心跳）带 `dm` + 冷启动时序 `bindSigningCert→setDeviceMaterial→unwrap` | 未接 | L2 |
| G5 | 工具 `tools/wk_derive_ref.py`（W_dev 参考实现 + 三端测试向量）| **不存在**（tools glob 无）| L2 |
| G6 | 服务器逐台 wrap `S_rel→k/n` + `dm` 自校验（基准 `dm[:16hex]==device_id`，**禁用 payload.d**）| 服务器侧，本仓不可证 | L4 |
| G7 | 灰度 3 步（铺路双试→按设备切→收口删全局 W）+ 各步装机回归 | 未启动 | L2 |
| G8 | `wseg_a/b/c` 必须与 `seg_a/b/c` 域分离向量过 | 待建（随 G5）| L2 |

**红线（钥匙加固 §2，照搬不复写）**：服务器 `dm` 自校验基准 = `dm[:16 hex]==device_id`，**禁用 `payload.d`**（两者不同源，按 payload.d 比会把全员正版判伪造→全员激活失败）；`dm` 进信封签名覆盖范围防 MITM；边界诚实：W_dev 只治「跨机分发/转卖」，**不治** 设备主人本机自用 / 运行时动态注入。

---

## 6. 与 R1(C7)/R3 的边界（不重叠）

- **R1/C7** = registry 字段挂空（37=22+15）——字段「读没读」。
- **R3** = 发版线 / cert 轴 / s_rel 管道 / V3 证书源（D8 前置闭合度）。
- **R4（本文）** = W 这把钥匙 + 三端派生 + 牙③④（per-device / 防重放）——钥匙「咬不咬设备、防不防重放」。三者互补，无交叉结论。

---

## 7. 诚实边界 / L4 待确认

1. **服务器侧 W / 逐台 wrap / dm 自校验**：在 `I:\miyou-server`，本仓不可证（L4）。W_dev 落地是「客户端 SO + 脚本 + 服务器」三方联动，缺服务器侧无法闭环。
2. **W 与 recipe `wrap_key_b64` 是否真同值**：SO 是硬编码 `g_wk_lo/hi`，recipe 有 `wrap_key_b64`，但未见生成器联动——两者一致性靠人肉同步（L2 代码；同步纪律 L4）。
3. **外围 Ed25519/canary 现役** 取自 `PROTECTION_MAP §10.6/§10.9`（文档 L2），本次未复跑 L1。
4. 牙④「短租约 SO 层有牙」需 §3 四处联动（服务器签 digest→Java 下推→SO 折入→脚本镜像），任一端缺即不成立。

---

## 8. 本次未做（守只读红线）

- 未改任何代码 / `config_crypto.cpp` / `gen_*_cipher.py` / `NativeBridge.java` / `registry_8071.json`。
- 未改任务卡 / 钥匙加固设计 / 其它文档正文。
- 未执行 git 写动作；未实现牙③④（那是后续带三端向量 + 双审 + 灰度 + 装机回归的落地步骤）。

# End（R4 · 只读 L2；牙①②现役且三端镜像对齐 / 牙③④代码零实现仅设计 / W 全局静态一把 / unwrap 无防重放；落地走钥匙加固 §7 前置 + 安全官 §三端对账，从 git 快照起）
