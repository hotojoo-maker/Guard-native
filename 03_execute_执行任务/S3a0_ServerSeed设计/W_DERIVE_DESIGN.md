# W_DERIVE_DESIGN — wrapping key W「一机一密」派生设计（档次 A）

> 角色：**安全官 + 授权检查官 双帽**联合设计。
> 状态：🟡 **设计稿**（本轮只出设计，未改任何代码/SO/服务器/其它 .md）。
> 立项日：2026-06-12。来源：安全官红线「AES key 不得是 SO 里的静态明文常量」（skill §"Key 来源准则" L274/L286）+ PROTECTION_MAP §10.7 P1（重放绑定）+ §10.5「真锁残留」。
> 关联实现档：`P_RB1_重放绑定_ReplayBind/DESIGN.md`（本设计是 P_RB1 的「W 维度」具体配方）。
> 修订：2026-06-12 v2 — 补灰度期 key 区分（§8.1「双试」选定方案，避免正版机静默散沙）、`dm` 服务器自校验（§3.2）、§1 dump-`S_rel` 边界硬化、克隆/双开边缘（§8.2）、自测向量 DEBUG-only（§6/§7）。
> 修订：2026-06-12 v3（双官复审修正）— ① 修正 §3.2 服务器自校验**基准写错**的真 bug：交叉校验应是 `dm[:16 hex]==device_id`，**不是** `payload.d`（L2 实锤：`computeDeviceHash`=SHA-256(ANDROID_ID)前8字节 `AuthManager.java:103`；`payload.d`=对 deviceId 再哈希 `AuthEnvelopeVerifier.java:105`，不同源）；② 标清 §4【终局版】fail-closed vs §8【灰度期】全局 W 兜底的两阶段差异，消除自相矛盾；③ §8 过渡方案整合为 **canonical 3 步灰度落地表**。本次仅改本 .md，未动代码/SO/服务器。

---

## 0. 问题陈述（要堵什么）

`native_core/src/config_crypto.cpp`：

- `unwrap_server_seed(k, nonce)`（:416）用 **wrapping key W** 做 `AES-128-GCM` 解 `k(48B)=ct(32)||tag(16) → S_rel(32)`。
- W = `g_wk_lo[8]` ‖ `g_wk_hi[8]`（:360-361），**全局一把、静态明文常量、全发行线所有设备共用**，运行时拼接。

**威胁（L2）**：破解者从一台 SO 抽出 `g_wk_lo`+`g_wk_hi` 拼成 W → 配上**任意一台设备**抓到的合法信封 `k` → 离线解出 `S_rel` → 折进 `derive_registry_key` 解出 registry。**一把 W 通杀所有设备的信封。**

**档次 A 目标**：W 从「全局一把」改成「**一机一密**」——`W_dev` 折进**设备材料**。从设备 A 抽出的 SO（含本地段）+ 设备 A 的材料，只能算出 `W_dev(A)`，只能解设备 A 自己的信封；拿设备 B 的信封 `k` 用 `W_dev(A)` 解 → GCM tag 不过 → 散沙。

---

## 1. 诚实边界（先写死，防过度承诺）

- A **不**让 W 对「攻击者在自己机器上」保密：本地段仍在 SO、设备材料也在本地，设备主人仍能算出自己的 `W_dev` 解自己的信封（= 他本就有合法访问）。
- A 的精确收益：把「**抽一把 W 离线解所有设备抓到的信封**」降级为「**抽出的材料只对同一台设备有效**」。**跨设备重放/打包分发该 seed-unwrap 路径被堵死**。
- 配合**已有**的 `AuthEnvelopeVerifier` 设备绑定（`payload.d == sha256(deviceId)`），信封变成**双重设备锁**：① 验签层绑设备 ② 解封 key 绑设备。
- A **不**堵「动态 hook `isActive()`/`isConfigReady()` 直接放行」那条缝（那是 P0/动态党，归别的任务）。
- A **不**堵「正版机运行时从内存 dump 出 `S_rel`（`S_rel` 是发行线**全局**一把）→ 在每台目标机运行时注入/改内存 → 重建 registry key」。该路径要对**每台**目标机做运行时 hook/改内存，与上面「动态党」同类，归别的任务。**所以完成 A 后对外仍不得宣称「真锁无法破解 / 真锁终局」**（呼应 §10 实施前置 #6）。
- 一句话：A 治「转卖 / 跨机分发（抽一把 W 离线解所有信封）」，不治「他自己那台机器自用」「运行时动态注入」。与项目主威胁（盗版转卖）对齐。

---

## 2. W_dev 新派生配方（三端逐字节镜像基准）

### 2.1 输入材料

| 记号 | 是什么 | 来源 | 长度 |
|---|---|---|---|
| `wseg_a / wseg_b / wseg_c` | 三段本地常量（替代旧的扁平 `g_wk_lo‖g_wk_hi`） | 编译进 SO（每发行线一套，与 `registry` key 段**域分离**，值不同） | 各 16 B |
| `D_mat` | 设备材料 = `SHA-256(ANDROID_ID)` **全 32 字节** | 客户端 Java 计算后下推 SO；服务器从激活上报取 | 32 B |

> 注：现有 `AuthManager.computeDeviceHash()`（`SHA-256(ANDROID_ID)` 取前 8 字节 hex）已在用作信封设备绑定。本设计复用**同一个 ANDROID_ID 源**，但取**全 32 字节**（更高熵用于派生）。两者同源、互不冲突。

### 2.2 派生算法（伪码，刻意镜像 `derive_registry_key` 的混合风格，便于团队理解 + 三端对齐）

```
# 输出 W_dev[0..15]（16 字节，AES-128 key）
for i in 0..15:
    t = wseg_a[i] ^ wseg_b[(i*5 + 3) & 15]      # 步1：两段本地常量非线性取址异或
    t = rotl8(t, (i % 7) + 1)                    # 步2：按位旋转
    t = t ^ wseg_c[i]                            # 步3：第三段（域分离，确保 W_dev 与 registry key 不同）
    t = (t + i*37) & 0xFF                         # 步4：位置相关加扰
    # ── 折入设备材料 D_mat（32B）：本段是 A 的核心，把「全局」变「一机一密」──
    t = t ^ D_mat[(i*3)      % 32]
    t = rotl8(t, D_mat[(i*3 + 1) % 32] & 7)
    t = t ^ D_mat[(i + 11)   % 32]
    W_dev[i] = t & 0xFF

# rotl8(x, r): r&=7; ((x<<r)|(x>>(8-r))) & 0xFF
```

- **步1~4** 与 `derive_registry_key`（config_crypto.cpp:453-457）同构；**设备材料折入块**与该函数里 server-seed 折入块（:468-472）同构——团队已有先例，降低三端实现分歧。
- `wseg_a/b/c` 与 `derive_registry_key` 的 `seg_a/b/c` **必须取不同值**（域分离），否则 W_dev 与 registry key 相关，削弱安全性。

### 2.3 wrap / unwrap 流程

```
服务器（每设备签发信封时）：
  W_dev = DERIVE_W(wseg_a/b/c[release], D_mat[该设备])
  n_dev = 随机 12B nonce
  ct||tag = AES-128-GCM-Encrypt(key=W_dev, iv=n_dev, plaintext=S_rel[release](32B))
  envelope.k = base64(ct(32) || tag(16))     # 48B，沿用现有 k 格式
  envelope.n = base64(n_dev)                  # ≥12B，沿用现有 n 格式

客户端 SO（unwrap_server_seed）——【灰度版】认新老两种信封（详见 §8.1）：
  ok = false
  if g_device_mat 就位:
      W_dev = DERIVE_W(wseg_a/b/c, g_device_mat)   # g_device_mat 由 Java 下推
      ok = GCM-Decrypt(key=W_dev,    iv=n[:12], ct=k[:32], tag=k[32:48]) → S_rel   # 新信封（一机一密）优先
  if not ok:
      ok = GCM-Decrypt(key=W_global, iv=n[:12], ct=k[:32], tag=k[32:48]) → S_rel   # 老信封（全局 W）兜底，仅灰度
  if not ok: return false（散沙，见 §4；无 g_device_mat 且非老信封同样落这里）
  g_server_seed = S_rel; return true

客户端 SO（unwrap_server_seed）——【终局锁定版】灰度结束后的那次发版：
  # 删掉上面 W_global 整段 + 常量 g_wk_lo/g_wk_hi，只剩 W_dev 一条路
  W_dev = DERIVE_W(wseg_a/b/c, g_device_mat)
  无 g_device_mat / tag 不过 → return false（散沙，见 §4）
```

- **k / n 的字节格式、长度、JNI 签名全部不变**（仍 48B / ≥12B）。只是 wrap 用的 key 从全局 W 变成 `W_dev`。对 `AuthEnvelopeVerifier` 透明。

---

## 3. 设备材料：选型 + 激活上报格式

### 3.1 选型理由（稳定 / 不侵隐私 / 服务器可校验）

- 源 = `Settings.Secure.ANDROID_ID`：
  - **稳定**：同设备 + 同签名 key 跨重装不变；恢复出厂才重置（可接受，重置=换机语义）。
  - **不侵隐私**：非 IMEI / MAC / 手机号；且上报的是 `SHA-256(ANDROID_ID)`（单向哈希），服务器**只存哈希、不存原值**。
  - **已在用**：信封绑定 `payload.d = sha256(deviceId)` 已用同源；服务器已收到该绑定，一致性天然。
- `D_mat = SHA-256(ANDROID_ID)`（32B）。客户端无法上报、ANDROID_ID 取不到（null/"unknown"）→ 视为无材料 → fail-closed（§4）。

### 3.2 设备材料上报字段（`activate` / `fetchEnvelope` / `health` 三类请求都带）

> 定稿口径：新客户端**每次** `activate` / `envelope` / `health` 都补报 `dm`，让升级后的老设备**靠心跳/取信封即可补报**，无需重新激活——灰度迁移更稳（对齐 §8 步骤①「激活/心跳补报 dm」）。

```json
{
  "...": "现有请求字段（卡密 / token / device_id 绑定 等）",
  "dm": "<hex(SHA-256(ANDROID_ID))，64 hex 字符 / 32 字节>"
}
```

- **服务器存 `dm` 前必须自校验，禁止盲存 client 报的值**：`dm` 的**前 16 个 hex 字符（= 前 8 字节）应等于该设备已有的 `device_id` 字段**。理由（L2 代码实锤）：`device_id = AuthManager.computeDeviceHash = SHA-256(ANDROID_ID)` 前 8 字节 hex（`AuthManager.java:103-112`）= `dm = hex(SHA-256(ANDROID_ID))` 的前 16 hex 字符，**同源 `ANDROID_ID`**。
  - ⚠️ **不要拿 `payload.d` 比**：`payload.d = sha256Hex(deviceId)[:32]`（`AuthEnvelopeVerifier.java:105`）是把 `device_id` 字符串**再哈希一次**，与 `dm` 的输入不同源，前缀不相等；按它比会把所有合法 `dm` 判成伪造 → 没人能激活。
  - 该比对只验 `dm` 32 字节里的**前 8 字节**；剩 24 字节即便被伪造，也只会让攻击者**自己 SO** 本地算出的 `g_device_mat`（真 `SHA-256(ANDROID_ID)` 全 32 字节）≠ 服务器 wrap 用的 `dm` → **自己散沙（自害）**。故 8 字节交叉校验作纵深防御已足够。
  - 一致才采信入库；不一致 = client 伪造 / MITM 篡 `dm` → 拒绝。
- 服务器把校验通过的 `dm` **绑定到卡密/设备记录**持久化（只存哈希）。
- 签发信封时按该记录的 `dm` 算 `W_dev` → wrap `S_rel` → 填 `k/n`。
- `dm` 同时应进入信封签名覆盖范围，双保险防 MITM（沿用安全官 skill §"防重放"：device 绑定进签名）。

---

## 4. fail-closed（无材料 / 不匹配 → 散沙，不崩不全开）

> ⚠️ **本表是【步骤3 终局版】语义**（全局 W 已删，只剩 W_dev 一条路）。**【步骤1/2 灰度期】例外**：未下推 `g_device_mat` 的老设备允许回退全局 W 兜底（§8 步骤表 + §2.3【灰度版】双试），此乃迁移期**本就允许**的路，不算违反下方红线。两阶段的判定差异见 §8。

| 情形 | SO 行为（终局版） | 结果 |
|---|---|---|
| Java 未下推 `g_device_mat`（`g_device_mat_len==0`） | `unwrap_server_seed` 直接 `return false`，**不**用旧全局 W 兜底 | `server_seed_ready()=false` → `registry_load_embedded` 散沙 |
| ANDROID_ID = null/"unknown" | Java 不下推 / 下推空 → 同上 | 散沙 |
| 设备材料不匹配（换机 / 攻击者喂错料 / 拿别机信封） | `W_dev` 错 → GCM tag 不过 → `return false` | 散沙（GCM 天然 fail-closed，灰度/终局都成立） |
| 正常 | unwrap 成功 → `g_server_seed` set | `recipeOk=true` |

- 红线（**终局版**）：**绝不**在无/错设备材料时回退旧全局 W（否则 A 等于没做）。灰度期回退仅限「老设备 + 全局 W 信封」这一条，且必须随步骤3收口删除。
- 与安全官 skill「解不开返回散沙配置，不崩、不全开」一致；与 `GUARD_REGISTRY_REQUIRES_SERVER_SEED=1`（prod_server_lock）链路天然衔接。

---

## 5. 与现有 `derive_registry_key` 两个折入材料共存（不冲突）

`derive_registry_key`（config_crypto.cpp:436）**完全不动**。三个材料在**不同层、不同函数**，正交：

| 材料 | 全局名 | 谁设置 | 谁消费 | 作用 | 本设计是否动它 |
|---|---|---|---|---|---|
| 证书 `g_binding` | `set_binding_material` | `ModuleMain.bindSigningCert` | `derive_registry_key`（防重打包） | 重签→registry 散沙 | ❌ 不动 |
| 服务器种子 `g_server_seed` (`S_rel`) | `unwrap_server_seed` 输出 | `derive_registry_key`（真锁） | 无服务器材料→registry 散沙 | ❌ 不动（仍折 S_rel） |
| **设备材料 `g_device_mat`（新）** | **`set_device_material`（新 JNI）** | **`unwrap_server_seed`（算 W_dev）** | 一机一密解 k | ✅ 本设计新增 |

- **关键**：W_dev 只影响「**怎么解出 S_rel**」；`derive_registry_key` 拿到 S_rel 后的逻辑不变。所以 `gen_registry_cipher.py`（按 S_rel + 证书生成 `registry_cipher.inc`）**完全不受影响、不需要 W_dev**。
- 冷启动顺序（实施时）：`bindSigningCert`（证书）→ **`setDeviceMaterial`（新）** → `applyCachedEnvelopeSeed/unwrapServerSeed`（用 W_dev）→ registry 解密。两个材料都必须在 unwrap 前就位。
- 服务器材料仍参与最终 registry key 派生（满足 skill L286）；W_dev 用 device 材料（skill L279 明确允许「device/customer 派生摘要」做 key 材料）。**两条红线都不破。**

---

## 6. 改动清单（仅设计，实施需逐文件再过改前审查）

| 端 | 文件 | 改什么 |
|---|---|---|
| SO | `native_core/src/config_crypto.cpp` | `unwrap_server_seed` 用 `DERIVE_W(wseg_a/b/c, g_device_mat)` 替代静态 W；新增 `g_device_mat[32]/len` + `set_device_material()`；新增 `wseg_a/b/c`；删/留 `g_wk_lo/g_wk_hi`（见 §8 迁移）；加 W_dev 自测向量（**仅 DEBUG / tools 单测，勿编进 release SO**） |
| SO | `native_core/src/guard_core.cpp` + `include/guard_core.h` | 新 JNI `nativeSetDeviceMaterial(byte[])` → `set_device_material` |
| Java | `core/NativeBridge.java` | `setDeviceMaterial(byte[])` + native 声明（**禁混淆**，JNI 静态名红线） |
| Java | `core/AuthManager.java` | 新 `computeDeviceMaterial(ctx)` 返回**全 32 字节** `SHA-256(ANDROID_ID)`（与现 8B `computeDeviceHash` 并存，不改后者） |
| Java | `ModuleMain.java` | 冷启动 `bindSigningCert` 之后、unwrap 之前调 `NativeBridge.setDeviceMaterial(...)` + `GuardRuntime.resetConfigCache()` |
| Java | `net/GuardActivation.java` / `net/EnvelopeClient.java`（`activate`/`fetchEnvelope`/`reportHealth`）/ `net/GuardHeartbeat.java` | 三类请求都带 `dm` 字段（每次补报，便于老设备升级后免重激活回填） |
| 服务器 | `I:\miyou-server` 信封签发 | `activate`/`envelope`/`health` 任一收到即校验（`dm[:16]==device_id`）+ upsert `dm`；签发时按 `dm` 算 `W_dev` 逐设备 wrap `S_rel`→`k/n`；`dm` 进签名/交叉校验 |
| 工具 | 新 `tools/wk_derive_ref.py`（或并入现有） | `DERIVE_W` 参考实现 + 测试向量，供 SO/服务器对账（**不是** `gen_registry_cipher.py` 的职责） |

> ⚠️ 这一轮**一个都不改**；本表是给实施窗口的清单。

---

## 7. 三端镜像校验点（防 silent-fail，铁律 29 重灾区）

**W_dev 真正执行方 = SO（unwrap）+ miyou-server（wrap）两端**；工具脚本作参考/测试向量。三处 `DERIVE_W` 必须**逐字节一致**，否则正版机 unwrap 失败 → registry 散沙 → 已装机密友隐藏**静默全挂、无报错**（F-31 教训）。

镜像校验（实施时必须全过）：

1. **KDF 向量**：给定固定 `wseg_a/b/c` + 固定 `D_mat` → 期望 `W_dev`（16B）。SO 自测（`decrypt_config_self_test` 同款，**仅 DEBUG 构建编入，release SO 不带固定向量**）+ 服务器单测 + 工具脚本，三处断言同一 `W_dev`。
2. **round-trip 向量**：服务器 `wrap(W_dev, n, S_rel)=k` → SO `unwrap(k, n)=S_rel`，断言 `S_rel` 还原一致。
3. **域分离向量**：断言 `DERIVE_W(...)` ≠ `derive_registry_key(...)`（防 `wseg`==`seg` 误用）。

---

## 8. 迁移风险（旧信封不兼容 —— 必须有过渡方案）

- 已签发的旧信封 `k` 是用**全局 W** wrap 的。服务器切到 `W_dev` wrap 后，旧信封 `k` 用 `W_dev` unwrap **会失败**。
- 已激活但**从未上报 `dm`** 的老设备：服务器无 `dm` → 算不出 `W_dev` → 给它发 `W_dev` 信封会让它直接散沙。
- 没有过渡方案 → **升级即全量正版机散沙**。这是本设计**最高风险点**，故落地强制走下面**3 步灰度**（缺步即 BLOCK）。

#### 3 步灰度落地（canonical，实施必照此序，每步前 git 快照 + 改前审查 + 装机回归）

| 步骤 | 客户端 SO | 服务器 (miyou-server) | 仍持有全局 W？ | 可宣称 | fail-closed 语义 |
|:--:|---|---|:--:|---|---|
| **① 铺路（双试上线）** | 升级版加 `setDeviceMaterial` + `derive_wrap_key`；`unwrap` 走**双试**（先 W_dev 后全局 W，§2.3【灰度版】）；`activate`/`envelope`/`health` **每次补报 `dm`** | `activate`/`envelope`/`health` 任一收到即**自校验+upsert `dm`**（§3.2）；**仍只发全局 W 信封**（先不切） | ✅ 在（SO+服务器都在） | 仅「客户端已支持一机一密、待切换」 | 灰度语义：无 `g_device_mat` 的老设备走全局 W 兜底（不散沙） |
| **② 切换（按设备灰度发 W_dev）** | 不变（双试已能认两种信封） | 对**已回填 `dm` 且客户端版本支持**的设备改发 **`W_dev` 信封**；其余设备仍发全局 W | ✅ 在 | 仍**不得**称「真锁终局」（SO 里全局 W 仍可被抽出通杀） | 灰度语义同上；W_dev 信封落错设备 → 全局 W 也解不开 → 散沙 |
| **③ 收口（终局锁定）** | **删** `g_wk_lo/g_wk_hi` + 双试里的全局 W 分支，只剩 W_dev（§2.3【终局锁定版】） | **停发**全局 W 信封 | ❌ 删除 | 此刻才可称「W 一机一密落袋」（仍不等于「服务器真锁终局」，见 §1） | §4【终局版】红线生效：无/错 `dm` → 直接散沙，绝不回退全局 W |

- 切③的前置门槛：绝大多数活跃设备已回填 `dm`（服务器看板核账），且②已稳定运行一个观察窗口。
- ③ 与 V3 发行线必须绑同一套本地 AI 发版流程（每发行线重生成 cipher + 装机回归），对齐 `PROTECTION_MAP §10.6 ②③`「删 fallback = 重签即死」同理。

### 8.1 灰度期 SO 如何区分「该用 W_dev 还是旧全局 W」（实施必答，缺则 silent-fail）

灰度期**同一版 SO 会同时收到两种 wrap 的信封**：老设备拿全局 W 信封、新设备拿 W_dev 信封。SO 必须有确定办法选对 key，否则用错 key → GCM tag 不过 → 正版机**静默散沙、无报错无崩溃**（F-31 重灾区）。**本设计选定「双试」方案：**

- **双试（✅ 选定）**：SO 先用 `W_dev` 试解，tag 不过再用全局 `W` 试解；两把都不过才散沙（§4 fail-closed 语义不变）。客户端伪码见 §2.3【灰度版】。
  - 优点：信封**字节格式 / 长度 / JNI 签名完全不动**，跨端改动最小——`config_crypto.cpp` 加密核心属铁律 29 高危区，少动一处少一处 silent-fail 风险。
  - 安全性不被削弱：W_dev 信封落到别的设备，全局 W **也解不开**它（它本就是 W_dev wrap 的）→ 仍散沙；只保留「老设备 + 全局 W 信封」这条灰度期**本就允许**的路。
  - 代价：W_dev 不命中时多一次 GCM 解（16 轮 AES，≈ 可忽略）。
- **备选（版本标志，✗ 不选）**：信封加 1 字节 wrap 版本（v0=全局W / v1=W_dev），SO 按标志选 key。更确定，但①要动信封格式 + 三端；②**该字节必须进签名覆盖**，否则攻击者把 v1 篡成 v0 强制走更弱的全局 W = **降级攻击**。综合「改动量 + 降级风险」本轮不选；双试天然无此降级面。
- **终局锁定版**（灰度结束后那次发版）：删掉双试里的全局 W 分支 + `g_wk_lo/g_wk_hi`，只剩 W_dev 一条路（§2.3【终局锁定版】）。**安全增益此刻才真正落袋**——灰度期内全局 W 仍在 SO 里，老攻击照样能抽 W 通杀，故灰度期内**不得对外宣称「真锁终局」**（与 §1、§10 实施前置 #6 一致）。

### 8.2 灰度期边缘项（排障白名单，避免当 bug 查）

- **克隆 / 双开 / 工作资料分身**：这些环境的 `ANDROID_ID` 与主体不同 → 算出不同 `W_dev` → unwrap 失败散沙。属**预期行为**（等同换机语义），非 bug；排障时先排除此项。
- **恢复出厂 / 刷机后**：`ANDROID_ID` 重置 → `dm` 变 → 需重新激活补报 `dm` 后服务器才能签对 `W_dev` 信封。同属预期。

---

## 9. 装机验收标准

正向：
- 正版激活上报 `dm` → 服务器按 `dm` wrap → SO `setDeviceMaterial(dm)` → **`k` unwrap 成功** → `registrySummary ... schema=r8071_v1 ... entries=4` → `recipeOk=true` → `[CTF:addAll] removed`（密友照常隐藏，不误伤）。

负向（A 的价值证明）：
- 把设备 A 的信封 `k/n` 搬到设备 B（不同 ANDROID_ID）→ B 算出不同 `W_dev` → unwrap 失败 → `registrySummary=scatter` / `isConfigReady=false` / 敏感 hook 不安装。
- 不 `setDeviceMaterial`（或 ANDROID_ID=unknown）→ unwrap fail-closed → 散沙。

回归 / KPI：
- 三端 KDF/round-trip 测试向量全 PASS。
- 装 SO 前后 `frida_stats` 各项不超红线（unwrap 仅多一次 16 轮 KDF，开销可忽略）。
- 旧信封灰度：老设备升级前仍正常（全局 W 信封），升级补报 dm 后切 W_dev 信封正常。

---

## 10. 授权检查官会签结论

```
【授权检查官 + 安全官 联合审查报告（设计层）】

审查对象: W_dev「一机一密」派生设计（W_DERIVE_DESIGN.md，仅设计、未改码）
触发原因: 安全官红线「AES key 不得是 SO 静态明文常量」+ P_RB1 重放绑定

必答（设计层）:
1. 是否乱接状态机          → 否。W_dev 纯钥匙层（unwrap_server_seed），不碰 StateMachine/isActive/Filter。
2. 是否混淆入口口令与授权   → 否。不涉及 EntryGate / 111111。
3. 是否未授权越权          → 否。不改 AuthGate 语义；信封授权链不变。
4. 是否绕过 AuthGate        → 否。W_dev 只决定「能否解出 S_rel」，授权仍走 AuthEnvelopeVerifier + EnvelopeStore。
5. 是否破坏 Unlock→SM→Bus→Filter 链 → 否。不触该链。
6. 是否影响 Java/C++ 分层   → 否。Java 算 D_mat 下推、SO 派生+解密，符合「SO 验真+解密」职责；不操作 UI/DB。
7. 保护区文件是否未审先改   → 本轮未改任何代码（仅设计）。实施时 config_crypto/guard_core/NativeBridge/AuthManager/GuardActivation 均为保护区，每个改前须再审。
8. 模块边界是否清晰        → 是。新增材料 g_device_mat 与 g_binding/g_server_seed 正交分层（§5）；不与 registry key 派生混淆（域分离）。
9. 最小修复建议            → 见下「实施前置」。

门控状态:
EntryGate:  ✅ 不涉及
AuthGate:   ✅ 不改语义（信封授权链不变）
StateGate:  ✅ 不涉及
RiskGate:   ✅ 属钥匙/真锁层强化；fail-closed 合规（§4）
分层合规:   ✅ Java 算材料 / SO 解密 / 服务器签发，职责清晰

合规结论（设计）: PASS
实施结论:        WARN（满足下列前置才可动代码）
```

**实施前置（WARN 转 PASS 的条件，缺一即 BLOCK）：**
1. 🔴 **【BLOCK 级真 bug】服务器 `dm` 自校验基准必须是 `dm[:16 hex] == device_id`，禁止用 `payload.d`**（§3.2）。链路实锤：`ANDROID_ID → dm=SHA-256(ANDROID_ID)(64hex) → device_id=dm[:16] (computeDeviceHash) → payload.d=SHA-256(device_id)[:32]`。写成 `dm vs payload.d` = **把所有正版 `dm` 判伪造 → 全员激活失败**。`payload.d` 继续只用于现有信封签名设备绑定，不参与 `dm` 比对。
2. **必须带 §8 迁移/灰度方案（canonical 3 步）**，否则升级即全量正版散沙 → BLOCK。
3. **必须先出 §7 三端测试向量并三处对齐**，再动 `unwrap_server_seed` → 否则 BLOCK。
4. **灰度版 / 终局版语义分两层、不得混写**：灰度版允许 dual-try（先 W_dev 后 W_global，仅兼容老信封/老设备补报窗口）；终局版无 `g_device_mat` 或 W_dev tag 不过 = 直接 scatter，绝不回退 W_global（§4 表头已标，执行时按所处步骤取对应语义）。
5. **git 快照 + 逐保护区文件改前审查 + 分步装机回归**（每步先验正版 `recipeOk=true` + 密友隐藏不挂）。
6. `wseg_a/b/c` 与 `derive_registry_key` 的 `seg_a/b/c` **取不同值**（域分离向量必过）。
7. `NativeBridge` 新 native 方法 **keep 不混淆**（JNI 静态名红线）。
8. 安全官口径：完成前对外仍**不得**宣称「真锁终局完成」；A 只收「跨机分发该路径」，不收「自用 / 动态 hook」。

---

## 11. 一句话总结

把解封 key 从「全局一把静态明文 W」改成「`W_dev = KDF(本地三段 + 设备材料 SHA-256(ANDROID_ID))`」，服务器按设备上报的 `dm` 逐设备 wrap `S_rel`。**抽一台 SO 的料只能解它自己**；配合已有信封设备绑定，跨机分发该路径双重锁死。代价是旧信封不兼容（必须灰度迁移），实施碰多个保护区 + 加密核心（铁律 29 高危），必须三端测试向量对齐 + 分步装机。
