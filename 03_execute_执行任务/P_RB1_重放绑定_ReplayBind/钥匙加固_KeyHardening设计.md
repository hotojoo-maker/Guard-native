# 钥匙加固设计（W 一机一密 + 重放绑定）

> **定位**：registry 钥匙派生加固的**单一真源**。本稿**合并并取代** `W_DERIVE_DESIGN`（W 一机一密）+ `P_RB1/DESIGN`（重放绑定）+ `S3a0`（服务器种子折 key 机制），三者 2026-06-24 减法删除、核心已并入本稿；S3a0 的「S_rel 三方同源」发版铁律不在本稿，见 服务器运维 skill（`release_id` 同源）+ 安全官 skill「发布/共存版加密铁律」，完整配方 = `docs/RELEASE_RECIPE契约.md`；**更早版本见 git history**。
> **状态（2026-06-27 晚回正 · G91 核账，证据 = git log + `I:\miyou-server\REVIEW_2026-06-27.md` §A）**：牙③ W_dev batch0/1 已落码+装机 L1（commit `caf2142`：`derive_wrap_key`/`setDeviceMaterial`/双试 unwrap；全局 W 仍作回退）；**Batch2（服务器按设备 wrap）已部署主节点**（prod KDF 自检过，灰度② 进行中，dm 回填 ~25%）；**Batch3（删全局 W）未做**，冻结待 dm ≥90% + 观察窗（REVIEW §B）；**牙④ a 案（§3）已落码**（commit `f6cc31b` + 负向单测 `c5597b1`，2026-06-27）。改码前双官审（授权检查官 + 安全官）+ git 快照 + 分步装机回归。
> **上层总图**：`../P_AntiBanGate_防封授权闸/DESIGN.md`（统一风控引擎）。本稿是其 §5「A2 料进加密链 / 钥匙加密」的下钻。
> **规则铁律**：三端对账 / Key 来源准则 / fail-closed 散沙 等**铁律真源 = 安全官 skill**（本稿引用、不复写）。
> **命名规约**：官方包 / 原版 / 灌官方值（不写品牌名，详 `CLAUDE.md` 词表）。

---

## 0. 一句话 + 4 颗钥匙牙 + 外围 3 道

**钥匙加固 = 给 registry 钥匙再焊 2 颗牙（设备 + 防重放），让「抽一台 SO 通杀」「录个旧信封重放」都失效。**

registry 钥匙（`derive_registry_key`）= 4 颗钥匙牙咬合的**与门**，缺一即散沙：

| # | 牙（折进钥匙的材料）| 状态 | 治什么 |
|---|---|---|---|
| ① | 模块签名证书 SHA-256 | ✅ 现役（A-step2）| 重打包重签 → 钥匙错 |
| ② | 服务器种子 S_rel | ✅ 现役（prod_server_lock）| 没服务器种子 → 解不出 |
| ③ | 设备指纹（W 一机一密）| 🟡 batch0/1 已落（`caf2142`）+ Batch2 服务器已部署主节点（2026-06-27，灰度② dm~25%）；Batch3 删全局 W 待（dm≥90%）| 抽一台 SO 通杀所有机 |
| ④ | 信封摘要（重放绑定）| ✅ a 案已落码（2026-06-27，commit `f6cc31b`/`c5597b1`：SO 比 `expire_at`、不折静态 key）| 录个旧/过期信封重放 |

> 钥匙**外面**另有 3 道独立牙（已落地/规划落点不在本稿）：Ed25519 验签 · 弹窗 canary · 蜜罐 canary。
> 现役（2026-06-27）= 牙①cert + 牙②S_rel + 牙④expire（3 颗已落）+ 牙③ 灰度②（全局 W 未删、部分生效）+ 外围 3 道；满配 = 4 颗钥匙牙 + 外围 3 道 = 7 道（差牙③ Batch3 收口）。

---

## 1. 要堵的两个缝（L2）

- **跨机分发**：解信封的 wrapping key `W`（`config_crypto.cpp:360` `g_wk_lo‖g_wk_hi`）是**全局一把、静态明文常量**。抽一台 SO 的 W + 任意机抓的合法信封 `k` → 离线解出 `S_rel` → 解 registry。**一把 W 通杀所有设备。** → 牙③治。
- **重放 / 过期**：`unwrap_server_seed()`（`config_crypto.cpp:416`）**不验时间、不验签、无重放保护**。抓一次合法 `k/n` 或 dump 出 `g_server_seed`，旧信封照样 unwrap，registry **永久解开**。 → 牙④治。

---

## 2. 牙③ — W 一机一密

**思路**：W 从「全局静态明文一把」→ `W_dev = KDF(本地三段 wseg_a/b/c + 设备材料 D_mat)`，`D_mat = SHA-256(ANDROID_ID)` **全 32 字节**。服务器按设备上报的 `dm` **逐台** wrap `S_rel` → `k/n`；抽 A 机的料只能解 A 机自己的信封。配已有信封设备绑定（`payload.d == sha256(deviceId)`）= **双重设备锁**。

**W_dev 派生伪码**（三端逐字节镜像基准；与 `derive_registry_key` 同构，降低三端分歧）：

```
# 输出 W_dev[0..15]（AES-128 key）
for i in 0..15:
    t = wseg_a[i] ^ wseg_b[(i*5+3)&15]      # 两段本地常量非线性取址异或
    t = rotl8(t, (i%7)+1)
    t = t ^ wseg_c[i]                        # 第三段（域分离）
    t = (t + i*37) & 0xFF
    # ── 折设备材料 D_mat（把「全局」变「一机一密」的核心）──
    t = t ^ D_mat[(i*3)%32]
    t = rotl8(t, D_mat[(i*3+1)%32] & 7)
    t = t ^ D_mat[(i+11)%32]
    W_dev[i] = t & 0xFF
# rotl8(x,r): r&=7; ((x<<r)|(x>>(8-r)))&0xFF
# wseg_a/b/c 必须与 derive_registry_key 的 seg_a/b/c 域分离（取不同值），否则削弱安全性
```

**红线**：
- 🔴 服务器 `dm` 自校验基准 = **`dm[:16 hex] == device_id`**，**禁用 `payload.d`**（不同源：`device_id = computeDeviceHash = SHA-256(ANDROID_ID)[:8]`；`payload.d = SHA-256(deviceId)[:32]`。按 `payload.d` 比会把所有正版 `dm` 判伪造 → 全员激活失败）。
- `dm` 进信封签名覆盖范围，双保险防 MITM。
- **与 A2 同盘隔离**：`D_mat` 必须来自 `AuthManager.rawAndroidId(ctx)` 的真值缓存；`computeDeviceHash`(8B) 与 `computeDeviceMaterial`(32B) 共用这个唯一读点。A2 灌给官方包的官方 SSAID **不得**进入 `D_mat`。冷启动顺序固定为 `rawAndroidId` 预热 → `setDeviceMaterial` → unwrap → A2 install；预热失败则不装 A2，宁可防封能力不开，也不污染设备材料。
- **边界（诚实）**：A 只治「跨机分发 / 转卖」，**不治**「设备主人自己那台机自用」「运行时动态注入」。完成后仍**不得**称「真锁终局」。

---

## 3. 牙④ — 重放 / 过期绑定（2026-06-26 修正：不折静态 key · a 案）

> ⚠️ **修正（评审逮到的自锁坑）**：`registry_cipher.inc` 是 build 静态物、key 编译期定死；`expire_at` 每续约变。**把 expire 折进 `derive_registry_key` = 正版一续约就解不开、把自己锁死**。故 expire / 重放**绝不进静态 registry key**（原“折摘要进 key”方案作废）。

**最终形（a 案）**：

- **静态 registry key 只折稳定料**：`cert + S_rel + device(牙③) + release`（随发行线 / 设备稳定，不随续约变）。续约不动 key、不重生 `registry_cipher.inc`。
- **expire / 重放在 SO 层用可信时间检查**：`unwrap_server_seed` 成功后，SO 比 `expire_at <= trusted_now`（`LeaseClock.trustedNow()`）→ 过期则不应用种子 / 散沙。`trusted_now` 抗冻是这条有牙的前提：否则冻住时钟就永不过期。
  - ⚠️ **抗冻现状校正（L1 2026-06-27 · G92，证据 `../P_AntiBanGate_防封授权闸/recon/convtime_L1_evidence_20260627.log`）**：官方授时 floor 的真锚点 = **`jy0.hd.b()`**（微信 `MicroMsg.TimeHelper`，L1 实证**改表杀不掉**；`hd.c()` 跟墙钟、禁用；非 `field_conversationTime`）。但 **`LeaseClock.noteOfficialTime` 当前未落码**（DESIGN §6 已校正，旧称「已接官方授时 floor」作废）→ `trustedNow()` 现仅靠「我方服务器 `sn` + elapsedRealtime + `maxTrusted`」抗冻，**对「未对我方服务器 / 未授权」场景退回墙钟、可冻（gap）**。补接 `jy0.hd.b()` 后，牙④ 抗冻才对未授权也成立。jy0.hd **登录依赖**（全新装 / 未登录无值 → fail-open）。
- 重放：过期 `k` 被上面拒；有效期内重放同一 `k` = 仍有效、无害（**不需 nonce 账本**）。

**联动（缺一不可）**：① 服务器 `expire_at` 进 Ed25519 签名 → ② Java 验签后把 `expire_at` 下推 SO（`setEnvelopeExpiry`）→ ③ SO unwrap 后比 `trusted_now` floor，过期散沙。**不动静态 registry key、不重生 cipher**（续约只换 `k`）。

**优先级 P1**：牙③ 先堵「跨机通杀」；牙④（本案）堵「旧 / 过期信封重放」，吊官方授时防冻的 `trusted_now`。**诚实**：(a) 是 SO 层布尔检查（比折 key 略弱、理论可在 SO 内 NOP），但在黑盒 SO + 牙③ 设备绑定 + 官方授时防冻下，对本威胁模型够用（项目口径「不追求破不了」）。

---

## 4. 三端对账（铁律真源 = 安全官 skill，本节只引用不重写）

> **不在此重写**——「三端钥匙派生镜像对账」是 F-31 静默翻车重灾区，**铁律真源 = 安全官 skill §「三端钥匙派生镜像对账」**（三端逐字节一致 + KDF 测试向量自动对账 + 改派生先过向量再装机、向量不过即 BLOCK）。本稿不复写，避免两份漂移（违 skill 自身「单一真源」）。
>
> **与本稿的关系**：牙③（W_dev，改 key 派生）落地全程受该铁律约束——先出测试向量、三端（SO `config_crypto.cpp` / 生成脚本 `gen_*_cipher.py` /（如有）Java）对齐，向量不过禁止动 `unwrap` / `derive`。牙④ = a 案（§3：SO 层比 `expire_at`、**不折静态 key**），**不改 key 派生、不受三端向量约束**（勿照旧「摘要折入」口径）。
>
> 一句话：**钥匙加固 = 给钥匙加牙；加牙必过三端对账（规则看 skill），否则正版机静默散沙。**

---

## 5. fail-closed + 灰度 3 步

**fail-closed**：无 / 错设备材料、无种子、信封过期 → 散沙（GCM 天然 fail-closed），**不崩、不全开、不删数据、不破坏原版**（红线#5/#6）。

**灰度 3 步（改钥匙派生 = 旧信封不兼容，最高风险；缺步即 BLOCK）**：

| 步 | 客户端 SO | 服务器 | 全局 W | 可宣称 |
|:--:|---|---|:--:|---|
| ① 铺路双试 | 加 `setDeviceMaterial` + 双试（先 W_dev 后全局 W）+ 每次补报 `dm` | 收到即自校验 + upsert `dm`；**仍只发全局 W 信封** | 在 | 仅「客户端已支持、待切」 |
| ② 按设备切 | 不变（双试认两种）| 对已回填 `dm` 的设备改发 **W_dev 信封**；其余仍全局 W | 在 | 仍**不得**称「真锁终局」 |
| ③ 收口锁定 | **删** `g_wk_lo/hi` + 双试里全局 W 分支，只剩 W_dev | **停发**全局 W 信封 | 删 | 「W 一机一密落袋」（仍非「服务器真锁终局」）|

> 🟢 当前位置（2026-06-27 晚回正 · G91）：① 已落（客户端 `caf2142`）；**② 进行中 —— 服务器主节点已部署、对已回填 dm 的设备发 W_dev 信封，dm 回填 ~25%**；③ 未做（冻结待 dm≥90% + 观察窗）。证据真源 = `I:\miyou-server\REVIEW_2026-06-27.md` §A/§B。

- 切 ③ 前置：绝大多数活跃设备已回填 `dm`（服务器看板核账）+ ② 稳定一个观察窗口。
- ③ 与 V3 发行线绑同一套本地 AI 发版流程（每发行线重生成 cipher + 装机回归），对齐 `PROTECTION_MAP §10.6 ②③`「删 fallback = 重签即死」。
- 灰度期边缘（排障白名单，非 bug）：克隆/双开/分身（不同 ANDROID_ID → 不同 W_dev → 散沙，等同换机）；恢复出厂/刷机（ANDROID_ID 重置 → 需重激活补报 `dm`）。

---

## 6. 守 3 条维护原则（钉死 · 详见 skill）

```text
1. 粗粒度别碎散：全家 1 个保险柜 SO（libguardcore.so）+ 少数出口；别每小功能往 SO 塞一段。
2. 单一真源 + 自动对账：钥匙派生写一份真源，三端用测试向量自动断言一致（铁律见 skill §三端对账）；禁人肉同步。
3. fail-closed：解不开就散沙，不崩、不全开。
```

> 对齐 `PROTECTION_MAP.md §10.1`（拆大动脉、不碎拆）+ 安全官 skill「粗粒度能力包 + 少数关键出口」「黑盒化边界：只下沉钥匙层、策略留 Java」。

---

## 7. 涉及文件 + 实施前置

**文件（实施时逐个再过改前审查）**：
- SO：`native_core/src/config_crypto.cpp`（牙③ ✅已落 `caf2142`：`unwrap_server_seed` 用 W_dev + `g_device_mat` + `wseg_a/b/c`；牙④ a 案 ⬜未落：unwrap 后比 `expire_at <= trusted_now`、**不折 key**）、`guard_core.cpp` + `include/guard_core.h`（JNI `setDeviceMaterial` ✅已落 / 牙④ `setEnvelopeExpiry` ⬜未落，**非** `setEnvelopeDigest`）。
- Java：`core/NativeBridge.java`（新 native，**keep 不混淆**）、`core/AuthManager.java`（`computeDeviceMaterial` 全 32B，与现 8B `computeDeviceHash` 并存）、`net/AuthEnvelopeVerifier.java` + `net/EnvelopeStore.java`（下推 `dm` / 已验签摘要）、`ModuleMain.java`（冷启动时序：`bindSigningCert` → `setDeviceMaterial` → unwrap）、`net/GuardActivation.java`/`EnvelopeClient.java`/`GuardHeartbeat.java`（三类请求带 `dm`）。
- 工具：`tools/gen_registry_cipher.py`（镜像派生）+ `tools/kdf_common.py`（W_dev 参考实现 `derive_wrap_key` + 向量；**2026-06-26 已并入此处，未单独建 `wk_derive_ref.py`**——三端镜像直接以 `kdf_common.py` 为 Python 真源，禁止另起一份重写）。
- 服务器：`I:\miyou-server`（逐设备 wrap + `dm` 自校验 + 摘要进签名）。

**前置（缺一 BLOCK）**：
1. 授权检查官 + 安全官 改前审 PASS。
2. git 快照。
3. **三端测试向量先过、三处对齐**（§4 / skill），再动 `unwrap_server_seed` / `derive_registry_key`。
4. 灰度方案（§5 三步）就位。
5. 分步装机回归（每步先验正版 `recipeOk=true` + 密友隐藏不挂）。
6. JNI 新方法 keep 不混淆。
7. `wseg_a/b/c` 与 `seg_a/b/c` 域分离向量必过。
8. A2 同源隔离自检通过：全仓裸读 `Settings.Secure...ANDROID_ID` 只剩 `rawAndroidId` 一处；两机 `D_mat` / `W_dev` / envelope `d` 均不同；官方包侧仍读官方 SSAID。
9. 完成前**不得**宣称「真锁终局完成」。

---

## 8. 验收（负向为主）

- 正向：正版激活上报 `dm` → 服务器按 `dm` wrap → SO `setDeviceMaterial` → `k` unwrap 成功 → `registrySummary ... entries=5`（**A2-1 a2.sig 进 registry 后为 5**；`registry_self_test` 已同步硬要求 `==5`，照旧写 4 会让自测 fail） → `recipeOk=true` → `[CTF:addAll] removed`（密友照常隐藏，不误伤）。
- 负向①（牙③）：A 机信封 `k/n` 搬到 B 机（不同 ANDROID_ID）→ 不同 `W_dev` → unwrap 失败 → `registrySummary=scatter` / `isConfigReady=false` / 敏感 hook 不装。
- 负向②（牙④）：过期 `k/n` 喂 SO → 散沙。
- 三端 KDF / round-trip / 域分离 向量全 PASS。
- 装 SO 前后 `frida_stats` 各项不超红线（unwrap 仅多一次 KDF，开销可忽略）。
- 旧信封灰度：老设备升级前正常（全局 W），补报 `dm` 后切 W_dev 正常。

---

*本稿 = 钥匙加固单一真源（已并入 W_DERIVE / P_RB1 / S3a0 核心；三份原稿 2026-06-24 减法删除，更早细节见 git history）。落代码按 §7 前置，从 git 快照起。*
