# R4b · E9（W 一机一密）设计草案（只读 · 不动代码）

> 出具：2026-06-25 · 角色 **只读侦察 AI**（设计 only，未改任何代码 / 未落任何 SO·Java·脚本）。
> 上游：`R4_W_E9_KDF核账.md`（现状核账）+ `P_RB1_重放绑定_ReplayBind/钥匙加固_KeyHardening设计.md`（W_dev 真源）+ `A2_androidid_修复方案_v1.md`（同源隔离真源）。
> 铁律真源（三端对账 / Key 来源 / fail-closed）= **安全官 skill**，本文引用不重写。
> 成色：L1 动态 / L2 静态实证 / L3 高概率推断 / L4 待验证 / 草案=设计提案。命名：官方包 / 原版 / 灌官方值。
> ⚠️ 本草案内一切 hex 为**示例值（SAMPLE）**，由文档算法即时计算用于演示表格与断言逻辑；**落码时由 `tools/wk_derive_ref.py` 选定真 `wseg` 后生成并冻结真值**，本文不臆造定稿常量（守 G1）。

---

## 0. 三句话
1. E9 = 把解信封的 wrapping key W 从「全局静态明文一把」改成 `W_dev = KDF(wseg + 设备材料)` 一机一密（堵 R4 §R-1 牙③ P0）。
2. 改的是 key 派生 → 受 skill「三端对账」铁律强约束：**先过测试向量、三端逐字节一致，再动 `unwrap`/`derive`，否则 F-31 静默散沙**。
3. 设备材料与 A2 android_id 轴同读 `ANDROID_ID`（R4 §R-4）→ E9 必须与 A2 memoize **同盘**，否则 W_dev 全机塌同值、牙③自废。

---

## ① 三端测试向量表（固定输入 → 期望输出）

### 1.1 对账方法（铁律真源 = 安全官 skill §三端对账）
- 三端 = **SO**（`config_crypto.cpp` host 测试 `tools/test_config_crypto.cpp`）+ **生成脚本**（新 `tools/wk_derive_ref.py`）+ **服务器**（`miyou-server` 逐设备 wrap）。
- 每个向量：固定输入 → 期望输出（16B hex）；三端各自独立算，**逐字节断言 == 期望输出**；任一端不等 = **BLOCK**，禁动 `unwrap`/`derive`。
- 域分离断言（V4）防 `wseg` 与 `seg` 取同值削弱安全（skill §3 硬要求）。

### 1.2 W_dev 派生算法（草案 · 镜像 `derive_registry_key` 结构，折设备材料）
```
# 输入 wseg_a/b/c[16]（落码定值，≠ seg_a/b/c）, D_mat[32] = SHA-256(ANDROID_ID)
for i in 0..15:
    t = wseg_a[i] ^ wseg_b[(i*5+3)&15]
    t = rotl8(t, (i%7)+1)
    t = t ^ wseg_c[i]
    t = (t + i*37) & 0xFF
    t = t ^ D_mat[(i*3)%32]          # 折设备材料（占 registry key 的 server-seed 槽位模式）
    t = rotl8(t, D_mat[(i*3+1)%32] & 7)
    t = t ^ D_mat[(i+11)%32]
    W_dev[i] = t & 0xFF
# rotl8(x,r): r&=7; ((x<<r)|(x>>(8-r)))&0xFF
```

### 1.3 向量表（示例值由上述算法即时算得，演示用）
| 向量 | 用途 | 固定输入 | 期望输出 16B(hex) | 成色 |
|---|---|---|---|:--:|
| **V0** | 现役 `derive_registry_key` cert-only（回填基线，证明现役也缺向量）| binding=`_CERT_SHA256`(32B) · seed=∅ | `335efbb31a1c99e4830bf3d254731d38` | L2※ |
| **V0b** | 现役 cert+seed（示例 seed 仅演示算法）| binding=`_CERT_SHA256` · seed=SHA256("…SREL_SAMPLE") | `b57f8d0632e8024ae31926c264b20cde` | L3 |
| **V1** | W_dev 设备A（草案）| wseg=示例三段 · D_mat_A | `c46a7d53ce52806f1bdf6ec39bd5974b` | 草案 |
| **V2** | W_dev 设备B（per-device 区分）| wseg=示例三段 · D_mat_B | `21e7b34c6dd01dd9fd8f04d5763c50e6` | 草案 |
| **V3** | per-device 区分断言 | V1.out vs V2.out | **必须 ≠**（实算 `WDEV_A≠WDEV_B=True`）| 草案 |
| **V4** | 域分离断言 | wseg_{a,b,c} vs seg_{a,b,c} | **三段全 ≠**（实算 `True,True,True`）| 草案 |

※ V0 成色：用与 `config_crypto.cpp`/`gen_registry_cipher.py` **逐字节一致的镜像算法**计算（L2）；需 `test_config_crypto.cpp` host 实跑断言同值后方升 **L1**。

示例常量（**SAMPLE · 落码换真值**，可复现：`SHA256(label)[:16]` / `SHA256(label)`）：
```
WSEG_A = 193e940f187bd9e09b8d77dfb0a86a29   (= SHA256("GUARD_E9_WSEG_A_SAMPLE")[:16])
WSEG_B = 429ca46f10e3fe3c6552a2d41ed3d551   (= SHA256("GUARD_E9_WSEG_B_SAMPLE")[:16])
WSEG_C = 476742f355c749659ed58a9b154f65b2   (= SHA256("GUARD_E9_WSEG_C_SAMPLE")[:16])
D_mat_A= 5e173255b786989ab121723e571435ed054c5043d363b03c09cd4438f48e5d45  (= SHA256("ANDROIDID_SAMPLE_AAAA"))
D_mat_B= 534e46e7a1c27a14f058c11f76388d7ef5643b1fda72b454dc3d6cad308da81a  (= SHA256("ANDROIDID_SAMPLE_BBBB"))
```

### 1.4 向量基础设施落地（落码步，本草案不落）
1. 新 `tools/wk_derive_ref.py`：W_dev 参考实现 + 选定真 `wseg` + 一次性生成 V1–V4 真值。
2. 扩 `tools/test_config_crypto.cpp`：加 `derive_wrap_key` 固定输入→期望输出断言 + V4 域分离断言（`wseg != seg`）+ **回填 V0**（现役 registry key 向量）。
3. `miyou-server`：W_dev 参考实现单测对同一向量。
4. 铁律：**四处（SO host-test / wk_derive_ref.py / miyou-server / 期望表）同值，向量不过 = BLOCK。**

---

## ② W_dev × A2 android_id 同读隔离方案（R4 §R-4 收口）

### 2.1 冲突（L2 + L3）
- A2 android_id 轴：hook `Settings.Secure.getString(_, "android_id")` afterHook **无条件灌官方 SSAID** 给官方包（A2 修复方案 v1 §2.4）。
- E9 设备材料：`computeDeviceMaterial(ctx)` = `SHA-256(ANDROID_ID)` 全 32B，**与 A2 同一 API、同进程**。
- 若我方读未隔离 → A2 一开，`computeDeviceMaterial` 读到的是**官方 SSAID** → 全机 `D_mat` 收敛同值 → `W_dev` 全机塌同值 → **牙③自废 + envelope `d` 设备绑定全废**（= R4 §R-4 / DESIGN 决策#11 D1）。

### 2.2 隔离方案（与 A2 v1「memoize 预热」同盘，**不另开污染口**）
> A2 v1 已**砍掉** caller 区分（ThreadLocal/栈扫描），主隔离 = **唯一读点 + memoize + 时序**（A2 修复方案 v1 §2）。E9 必须骑同一条：

1. **同读点**：`computeDeviceMaterial(ctx)` **必须调 `AuthManager.rawAndroidId(ctx)`**（A2 收口的唯一读 + `volatile` memoize），**禁**再裸调 `Settings.Secure.getString`。
   - 落码 gate：全仓 `grep Settings\.Secure.*ANDROID_ID` **仍只剩 `rawAndroidId` 一处**（8B `computeDeviceHash` + 32B `computeDeviceMaterial` 都走它）。
2. **同预热**：`ModuleMain` 冷启 step2（A2 install 之前）已预热 `computeDeviceHash`；E9 把预热扩成「预热 `rawAndroidId` 一次」即可（`computeDeviceMaterial` = 对缓存值再 hash，**不触发新读**）。
3. **同时序**：`setDeviceMaterial(SHA-256(rawAndroidId) 32B)` 在 `bindSigningCert → setDeviceMaterial → unwrap` 窗口下推 SO，**全部早于 A2 install**（step7）。
4. **fail-safe 断言（扩 A2 §3 铁律2）**：A2 install 前断言「device hash + device material 都已从**缓存真值**算出」；未缓存 → **不装 A2**（宁可防封不开，绝不污染 device 误伤正版）。
5. **caller-scope 仅作兜底（L4 待验证）**：A2 修复 v1 §6 留口——若官方包另有 `ContentResolver.query` / native 读 android_id 路（memoize 拦不住）→ 补 afterHook 点时**只对官方包/.mm caller 灌官方值、放过我方**。先 frida 全程 trace 定（probe 别打密心跳，守 CONN 0.5）。

### 2.3 验收（两机硬测，呼应 R4 §R-4 / A2 §5）
- 两机冷启 A2 已装 → `D_mat(机A) != D_mat(机B)` → `W_dev(A) != W_dev(B)` → envelope `d(A) != d(B)`（牙③ + 设备绑定双不塌）。
- 每台 `D_mat` == 装 A2 前基线；官方包侧 `getString` 仍读官方 SSAID（A2 不被削弱）。
- frida L1：官方包 caller → 官方 SSAID；我方 `rawAndroidId`/`computeDeviceMaterial` 路 → 真 ANDROID_ID。

---

## ③ 灰度 3 步 + 回退预案（改 key 派生最高危，缺步即 BLOCK）

> 步骤源 = `钥匙加固设计 §5`；本节补**每步回退预案 + 触发器**。总铁律：任一步**正版 `recipeOk` 掉**即回退该步，回退 = 恢复 key 派生让正版 `recipeOk=true`，**不清数据 / 不破坏原版**（红线#5/#6）。

| 步 | 客户端 SO | 服务器 | 全局 W | 监控触发器 | 回退预案 |
|:--:|---|---|:--:|---|---|
| **①铺路双试** | 加 `setDeviceMaterial` + 双试（先 `W_dev` 后全局 W）+ 每请求补报 `dm` | 收 `dm` 自校验(`dm[:16]==device_id`) + upsert；**仍只发全局 W 信封** | 在 | `dm` 上报率；双试中 W_dev 命中率（应≈0，因服务器仍发全局 W）| W_dev 失败自动回退全局 W → **正版零影响**；如客户端崩/异常 → 撤客户端 SO 这版，服务器不变 |
| **②按设备切** | 不变（双试认两种）| 对已回填 `dm` 的设备改发 **W_dev 信封**；其余全局 W | 在 | 该设备 `unwrap` 成功率 / `recipeOk` 率 / `scatter` 率 | 单设备 W_dev unwrap 失败（dm 漂移/克隆）→ **服务器逐设备翻回发全局 W 信封**（客户端双试仍收）；面积大 → 全量翻回全局 W |
| **③收口锁定** | **删** `g_wk_lo/hi` + 双试里全局 W 分支，只剩 `W_dev` | **停发**全局 W 信封 | 删 | 全网 `scatter` 率突增 = 灾难信号 | ⚠️ **不可平滑回退**：须**重新部署上一版 SO（含全局 W）+ 服务器重开全局 W 信封**；故 ③ 前置硬门槛见下 |

**③ 前置硬门槛（全绿才切，否则停在②）**：
1. 绝大多数活跃设备已回填 `dm`（服务器看板核账，定阈值如 ≥98%）。
2. ② 稳定观察窗口（如 ≥1 个发版周期）`recipeOk` 率无异常。
3. 三端向量（V1–V4）+ 回填 V0 全 PASS。
4. 与 V3 发行线绑**同一套本地 AI 发版流程**（每发行线重生成 `registry_cipher` + 装机回归），对齐 `PROTECTION_MAP §10.6 ②③`「删 fallback = 重签即死」。

**灰度期边缘（排障白名单，非 bug）**：克隆/双开/分身（不同 ANDROID_ID → 不同 `W_dev` → 散沙，等同换机）；恢复出厂/刷机（ANDROID_ID 重置 → 需重激活补报 `dm`）。

---

## 4. 落码前置清单（缺一 BLOCK · 引用 `钥匙加固设计 §7`）
1. 授权检查官 + 安全官 改前审 PASS（动 `config_crypto.cpp`/`NativeBridge`/`AuthManager`/`ModuleMain`/服务器）。
2. git 快照。
3. **三端测试向量先过、四处对齐**（①§1.4）；向量不过禁动 `unwrap`/`derive`。
4. W_dev × A2 隔离落实（②）：`computeDeviceMaterial` 只走 `rawAndroidId` + 预热 + fail-safe 断言。
5. 灰度 3 步 + 回退预案就位（③）。
6. 分步装机回归（每步先验正版 `recipeOk=true` + 密友隐藏不挂 + 两机 `d` 各异）。
7. JNI 新方法（`setDeviceMaterial`）proguard keep 不混淆。
8. 完成 ①②③ 前**不得**宣称「服务器真锁终局完成」。

---

*本草案 = E9 设计提案（只读、未改码、未落 wseg 真值）。hex 均示例，落码由 `wk_derive_ref.py` 定稿冻结。落码按 §4 前置，从 git 快照起；铁律真源 = 安全官 skill。*
