# R4 · W 一机一密 / E9 · KDF 三端核账（只读侦察）

> 出具：2026-06-25 · 角色 **只读侦察 AI**（未改任何代码；落码须设备在场 + 授权检查官+安全官双审 + git 快照）。
> 任务来源：LeanCloseout 任务卡 §4 step5（E9/B2）+ 安全官 skill「Key 来源准则」「三端钥匙派生镜像对账」。
> 读取范围：`native_core/src/config_crypto.cpp` / `guard_core.cpp` / `include/guard_core.h` / `registry_loader.cpp` · `tools/gen_registry_cipher.py` / `gen_bootstrap_cipher.py` / `test_config_crypto.cpp` · `core/{NativeBridge,AuthManager,ModuleMain}.java` · `P_RB1_重放绑定_ReplayBind/钥匙加固_KeyHardening设计.md`。
> 成色口径：L1 动态 / L2 静态实证 / L3 高概率推断 / 设计only。**铁律真源 = 安全官 skill「三端对账」，本文引用不重写。**
> 命名：官方包 / 原版 / 灌官方值（不写品牌名）。

---

## 0. 一句话

**W（解信封 → S_rel 的 wrapping key）现在是「全局静态明文一把」（`config_crypto.cpp:360-361`），抽一台 SO 的 16 字节即可离线解所有设备的合法信封 → 解 registry。这就是 DESIGN 决策25「牙③ P0」。E9 = 把 W 改成 `W_dev = KDF(本地段 + 设备材料)` 一机一密；改的是「钥匙派生」，受 skill「三端对账」铁律强约束（向量不过 = BLOCK，否则 F-31 静默散沙）。**

---

## 1. ① config_crypto.cpp 核账（W / 三个派生 / unwrap）

### 1.1 W = 全局静态明文（L2，实锤）

```
config_crypto.cpp:360  const uint8_t g_wk_lo[8] = { 0x58,0x18,0x26,0xdd,0xa4,0xed,0x49,0x0d };
config_crypto.cpp:361  const uint8_t g_wk_hi[8] = { 0xf5,0x5b,0xdb,0x4d,0x0c,0x03,0x88,0xe3 };
```
- **是全局静态明文常量**（两个 8 字节半段，`:358-359` 自带 `[GUARD-TRAP]` 注释）。
- **不经任何 KDF**：`unwrap_server_seed` `:424-426` 直接 `memcpy` 拼成 `wk[16]`，`:429` `gcm_decrypt(wk, n, k…)→S_rel`。
- **全仓只此一处**（grep `g_wk_lo/g_wk_hi` 仅命中 `config_crypto.cpp`）；**不在任何 gen 脚本里**。镜像对端 = `miyou-server` 配置（`:356-357` 注释自承「miyou-server config holds the same 16 bytes」）。→ **W 当前是「SO + 服务器」两端料，不是三端。**

### 1.2 三个派生/解封函数位置（L2）

| 函数 | 位置 | 折入的料 | 备注 |
|---|---|---|---|
| `derive_registry_key(out[16])` | `config_crypto.cpp:436-475` | `seg_a/b/c`(`:444-452` 内联) ^ `g_binding`(cert，`set_binding_material`) ^ `g_server_seed`(若 `server_seed_ready`) | registry 主钥匙 = 牙①+牙② 咬合 |
| `derive_bootstrap_key(out[16])` | `config_crypto.cpp:477-514` | `seg_a/b/c` ^ `g_binding` ^ `dom`(`:496` 域分离，**不折 server seed**) | C2 引导段，cert-only 永不锁服务器（防死锁） |
| `unwrap_server_seed(k,n)` | `config_crypto.cpp:416-434` | `wk = g_wk_lo‖g_wk_hi`（静态）→ `gcm_decrypt → S_rel(32)` | **不验时间/签/重放/设备**；失败清种子=散沙(fail-closed) |

辅助：`set_binding_material` `:398` / `server_seed_ready` `:412` / `clear_server_seed` `:408`；prod_server_lock 闸 = `registry_loader.cpp:194`（`GUARD_REGISTRY_REQUIRES_SERVER_SEED && !server_seed_ready()` → 先散沙）。

---

## 2. ② 三端 KDF 镜像点清单（L2）

> 「三端」= SO 真跑 + 生成脚本镜像 + （如有）Java 镜像。**核账结论：Java 侧无任何 KDF**，只下推材料（不算镜像端）。所以现役 KDF 实际是「SO + python 脚本」两端镜像；W 是「SO + 服务器」两端料。

| 钥匙/派生 | SO（`config_crypto.cpp`） | 生成脚本 | Java | 测试 harness |
|---|---|---|---|---|
| `derive_registry_key` | `:436`（seg_a/b/c `:444` + cert + server_seed） | `gen_registry_cipher.py:80`（`_SEG_A/B/C :48` + `_CERT_SHA256 :65` + server_seed） | 无 | `test_config_crypto.cpp` 间接（解密成功即对齐） |
| `derive_bootstrap_key` | `:477`（seg + cert + `dom :496`） | `gen_bootstrap_cipher.py:77`（`_SEG_A/B/C` + `_DOM :54` + `_CERT_SHA256`） | 无 | 同上（registry_self_test 不含 bootstrap） |
| **W（wrapping key，非 KDF）** | `:360-361` 静态明文，`:424` 拼 | **不在脚本** | 无 | 无 |

Java 侧只「下推材料」，不跑 KDF（L2）：
- `NativeBridge.setBindingMaterial(certSha256)` `:306` → JNI `guard_core.cpp:289` → `set_binding_material`。
- `NativeBridge.unwrapServerSeed(k,n)` `:320`（+`applyServerSeedAndReset :329`）→ JNI `guard_core.cpp:299` → `unwrap_server_seed`。
- `AuthManager.computeDeviceHash` `:104` = `SHA-256(ANDROID_ID)[:8]` hex（设备号；5 处调用：AuthManager `:47/:90`、ModuleMain `:263`、SettingsEntry `:1421`、EnvelopeStore `:155`、GuardActivation `:47`）。
- `computeDeviceMaterial`(32B) / `setDeviceMaterial` / `setEnvelopeDigest` **当前全不存在**（W_dev 设计提案）。
- 冷启时序：`bindSigningCert`(`ModuleMain:280` → `setBindingMaterial:314`) → 之后 unwrap；**当前无 setDeviceMaterial 这一步**。

测试 harness 现状（关键，L2）：
- `tools/test_config_crypto.cpp`：host 端跑 `decrypt_config_self_test()` + `registry_self_test()`；手动 `set_binding_material(kCertSha256:30)` 镜像构建期 cert fold（`kCertSha256` 必须 == 脚本 `_CERT_SHA256`，注释 `:27-29` 自承）；`clear_server_seed`（cert-only）。
- `config_crypto.cpp::decrypt_config_self_test() :516`：测 AES/GCM/tamper-scatter，但**用 `kTestKey` 字面量**（`:15` "guard_p1a_key!"），**不测 `derive_*`/W 的「固定输入→期望输出」向量**。
- → **现状缺口：`derive_registry_key`/`derive_bootstrap_key`/W 三者都无独立 KDF 单测向量；SO↔脚本一致性只靠「装机集成」（脚本加密→SO 运行时解→`recipeOk=true`）兜底，不是 skill 要求的「固定输入→期望输出」单测。**

---

## 3. ③ P_RB1 W_dev 现状 + E9 最小落地步 + 向量缺口

### 3.1 W_dev 设计现状（L2，读 `钥匙加固_KeyHardening设计.md`）
- **⬜ 设计 only，未动代码。** 牙①(cert)✅现役 · 牙②(S_rel)✅现役 · **牙③(W_dev 设备)⬜** · 牙④(信封摘要重放绑定)⬜。
- W_dev 伪码已定（§2）：`W_dev = KDF(wseg_a/b/c + D_mat)`，`D_mat = SHA-256(ANDROID_ID)` **全 32B**，与 `derive_registry_key` 同构。
- 红线：服务器 `dm` 自校验基准 = **`dm[:16hex]==device_id`，禁用 `payload.d`**（不同源会全员激活失败）；`wseg ≠ seg`（域分离）；`dm` 进信封签名覆盖。
- 灰度 3 步（§5）：①铺路双试 → ②按设备切 → ③删全局 W 锁定。**缺步即 BLOCK**。

### 3.2 E9 最小落地步（精确改点 · L2 文件:符号 / L3 顺序判断）

1. **SO `config_crypto.cpp`**：
   - 新增 `g_device_mat[32]` + `g_device_mat_len` + `set_device_material()`（仿 `set_binding_material:398`）。
   - 新增 `wseg_a/b/c[16]` 三段常量——**取值必须 ≠ `seg_a/b/c`**（域分离，否则削弱）。
   - 新增 `derive_wrap_key(out[16])` = W_dev（设计 §2 伪码：wseg ^ D_mat 非线性混）。
   - 改 `unwrap_server_seed:424-426`：`wk` 从 `g_wk_lo‖g_wk_hi` → `derive_wrap_key(wk)`；灰度①双试（先 W_dev，失败回退全局 W），③步删全局 W 分支 + `g_wk_lo/hi`。
2. **JNI `guard_core.cpp` + `include/guard_core.h`**：新增 `nativeSetDeviceMaterial`（仿 `:289` set_binding_material JNI）+ 头声明（`:174` 旁）。
3. **Java**：
   - `NativeBridge.java`：新 `setDeviceMaterial(byte[])` + native 声明（**proguard keep 不混淆**）。
   - `AuthManager.java`：新 `computeDeviceMaterial(ctx)` = `SHA-256(ANDROID_ID)` 全 32B（与现 8B `computeDeviceHash:104` 并存）。
   - `ModuleMain.java`：冷启时序在 `bindSigningCert`→`setBindingMaterial:314` 之后、unwrap 之前插 `setDeviceMaterial`（设计 §7：bindSigningCert → setDeviceMaterial → unwrap）。
   - `GuardActivation/EnvelopeClient/GuardHeartbeat`：三类请求带 `dm`（给服务器逐台 wrap）。
4. **生成/测试**：新 `tools/wk_derive_ref.py`（W_dev 参考 + 向量）；扩 `tools/test_config_crypto.cpp` 加 W_dev 向量 + `wseg vs seg` 域分离断言。（`gen_registry_cipher.py` 牙③不动；仅牙④折摘要才改。）
5. **服务器 `miyou-server`**：逐设备 `W_dev` wrap `S_rel` + `dm` 自校验(`dm[:16]==device_id`) +（牙④）摘要进 Ed25519 签名。

### 3.3 测试向量缺口（落码前必补，否则违 skill「三端对账」+ F-31）
- ❌ 无 W_dev 向量（固定 `wseg+D_mat` → 期望 `W_dev`）。
- ❌ 无 `wseg vs seg` 域分离断言（skill §3：段常量必须互不相等）。
- ❌ 连现役 `derive_registry_key/derive_bootstrap_key` 都无独立 KDF 向量（只靠装机集成）——E9 是补这套向量基础设施的契机。
- 三端（SO / `wk_derive_ref.py` / miyou-server）+ harness(`test_config_crypto.cpp`) 须同一向量逐字节断言；**向量不过 = BLOCK，禁动 `unwrap`/`derive`**。

---

## 4. 风险（L2/L3）

| # | 风险 | 等级 | 证据 / 后果 |
|---|---|:--:|---|
| **R-1** | **牙③缺失：W 全局静态明文一把通杀** | 🔴 L2 | `config_crypto.cpp:360-361` 静态常量；抽一台 SO 16 字节 + 任意机合法 `k` → 离线解所有设备 `S_rel` → 解 registry。= DESIGN 决策25 P0；**修复前 server seed 解 registry 不得称真锁。** |
| **R-2** | **牙④缺失：unwrap 不验时间/签/重放** | 🟠 L2 | `unwrap_server_seed:416-434` 无时间/重放/设备校验；抓一次合法 `k/n` 或 dump `g_server_seed`，旧/过期信封照解，registry 永久解开。短租约在 SO 层无牙。P1（设备绑定已堵转卖）。 |
| **R-3** | **改 key 派生 = 旧信封不兼容（最高危）** | 🔴 L3/F-31 | W→W_dev 改派生；缺三端对账/灰度 → 正版机静默散沙、密友暴露、无报错。必走灰度 3 步 + 向量先过。 |
| **R-4** | **D1 同源叠加：W_dev 设备料与 A2 android_id 轴撞** | 🔴 L2+L3 | `computeDeviceMaterial` 与 A2 都走 `Settings.Secure.ANDROID_ID` 唯一读点（`AuthManager:104`）。A2 一开若未隔离 → 设备料被灌官方 SSAID → 全机 `W_dev` 塌同值 → **牙③自废 + 设备绑定全废**。E9 必须与 A2 memoize 预热同盘（A2 预热须覆盖 `computeDeviceMaterial` 读点）。 |
| **R-5** | **向量基础设施债** | 🟡 L2 | 当前无任何 KDF「固定输入→期望输出」向量，全靠装机集成；E9 改派生前必须先建 harness，否则 F-31 无单测兜底。 |
| **R-6** | **三端面扩大 + 牙别混** | 🟡 L2/L3 | W_dev 落地新增 `wk_derive_ref.py` → W 从 2 端变 3 端，纳入发版重生成。注意：`W_dev` 折**设备**、`registry key` 折 **cert+seed**，牙不同别混；`wseg ≠ seg` 必断言。 |

---

## 5. 给指挥/总调度的 3 句结论
1. **W 确为全局静态明文一把（R-1 实锤 L2）**，牙③ P0 成立；这是「server seed 解 registry 还不能叫真锁」的根。
2. **E9 是「改 key 派生」最高危一类**：现状连基础 KDF 向量都没有（R-5），必须先建 `wk_derive_ref.py` + `test_config_crypto.cpp` 向量 + 域分离断言，再动 `unwrap`；**全程灰度 3 步**，否则正版静默散沙（R-3）。
3. **E9 与 A2 必须同盘**（R-4）：两者同走 ANDROID_ID 唯一读点，设备料污染会把牙③和设备绑定一起作废——E9 落码前需与 A2 memoize 预热方案对齐。

---

*本文 = 只读侦察核账，未改任何代码；铁律真源（三端对账/Key 来源/fail-closed）= 安全官 skill，本文引用不重写。落 E9 须按 `钥匙加固_KeyHardening设计.md §7` 前置：双官审 + git 快照 + 三端向量先过 + 灰度 3 步 + 分步装机回归。*
