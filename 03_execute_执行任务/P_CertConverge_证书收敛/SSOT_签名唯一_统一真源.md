# SSOT — 签名 / Keystore 唯一真源（构建期签名轴）

> ⚠️ **2026-06-30 DEPRECATED · SUPERSEDED by `docs/RELEASE_LINE_SSOT_发行线统一口径.md` v2**  
> 本文起草于 cert-sync-v1（两把印章两套配方）方向；用户已于 2026-06-30 拍板回合为 **v2 一套配方**（官替 / 共存 共用 official jks → `e3e13a49`）。本文档下方对"两把"的论述已与 v2 全反，不要据此动手。**接手请直接看 `docs/RELEASE_LINE_SSOT_发行线统一口径.md`**（命名 / cert / registry / 服务器四端口径单一真源）。

---

> **状态（历史）**：🟡 设计稿（2026-06-29 · Vchat guard_native-MI79 起草，待用户拍 Q1 + 落码）
> **定位（历史）**：本文 = 「**我们模块用哪把 keystore 签哪条发行线**」这条**构建期签名轴**的唯一可读真源。发版官 / 安全官 / 接手 AI 读这一份即可，不跳转、不互相矛盾、code-true。
> **与其它真源的边界（别混轴）**：
> - `SSOT_A2授权防破解_统一真源.md` = **运行时**防封 / 授权 / 防破解轴（cert 怎么喂 registry、影子期、真锁、A2 灌官方 DER）。本文只负责「签名 keystore 唯一」，cert 如何折进 registry / 喂官方值的运行时逻辑以那份为准、本文只引不抄。
> - `.devin/tasks/cert-sync-v1.md` = 历史任务（两线两证的 intent 起点，已部分落码）。
> - `CERT_RECON_证书对账表_20260629.md` = 现状对账快照（只读）。
> - `P_CertConverge 任务卡.md` = 本 SSOT 的执行计划。
> **真相基准（G8 代码即真相）**：文档与代码冲突以代码为准。本文用 `[码]` 标现状（代码已是这样）、`[决]` 标已拍板待落码、`❓` 标待用户拍。
> **维护铁律**：① 改这条轴 → 先改本文 + 代码，别处只放链接、绝不复制（G10）。② 旧口径直接删 / 改写对的单句，不留「X 被 Y 取代」考古层。③ 决策履历归 `DECISION_LOG`，本文只指针。

---

## 0. 一句话

**官替一把、共存一把、debug 退役。** 每条发行线全程（debug + release + LSPatch 重打包）用**同一把** release keystore 签；老的 `ca421ec3`（debug keystore）从「日常签名 key」**降级为密钥轮换祖先**（只用来证明 official 是它的合法轮换后代，使老装机包能平滑升级），不再直接签任何调试 / 出货包。

---

## 1. 三把钥匙 + 唯一映射

| 前缀 | 完整 SHA-256 | Keystore 文件 | 唯一角色（本 SSOT 定死） |
|---|---|---|---|
| **e3e13a49** | `e3e13a4974fe4c40432a583b11309edb4705c8a50b03f474d6b693817adecf36` | `signing/guard-native-official-release.jks`（alias `guardofficial`，口令在 gitignore 的 `signing/keystore.properties`） | **官替线唯一签名**（debug + release + LSPatch 全程） |
| **8f47a47a** | `8f47a47afde078c47713db78668e6ed9ab027bc9b86d58a9e646a1f3998d5685` | `signing/guard-native-coexist-release.jks` | **共存线唯一签名**（debug + release + LSPatch 全程） |
| **ca421ec3** | `ca421ec3a33708ceb3f70c37f4616751094736c496fe21b3b0e2cea480cdb6a0` | `signing/guard-native-debug.keystore`（在 git，口令 `android`） | **退役 → 仅作密钥轮换祖先**（不再签任何包；只在 `.lineage` 里证明 official 是其合法后代） |

> 证据：`tools/kdf_common.py:68-73`（official cert）· `build.gradle:135,150`（per-flavor EXPECTED）· `build.gradle:100-126`（三个 signingConfig）· `signing/guard-debug-to-official.lineage`（轮换证据）。

---

## 2. 四端对齐（每条发行线内逐字节相等）

「四端」= 一条发行线里这四个值必须**逐字节相等**，否则正版自我误判（F-31：registry 散沙 / 影子期）：

| 端 | 取值处 | official | coexist |
|---|---|---|---|
| ① 实际签名（当前签名者） | `apksigner` / `getApkContentsSigners()[0]` | e3e13a49 | 8f47a47a |
| ② `BuildConfig.GUARD_EXPECTED_CERT` | `build.gradle` per-flavor | e3e13a49 | 8f47a47a |
| ③ registry 绑定 cert（KDF binding） | `kdf_common.CERT_SHA256` → `registry_cipher*.inc` | e3e13a49 | 8f47a47a |
| ④ 运行时 certBind | `ModuleMain.bindSigningCert` → `NativeBridge.setBindingMaterial` | e3e13a49 | 8f47a47a |

- ① 读的是「**当前签名者**」（`getApkContentsSigners`，`CompatProbe.java:135-152`）。这是密钥轮换的关键：轮换后此 API 返回**轮换到的当前 key**（official），而非最旧祖先（ca421ec3）。
- 目标态：officialDebug / officialRelease 四端都 = e3e13a49；coexistDebug / coexistRelease 四端都 = 8f47a47a。
- 现状 `[码]`：official release 四端已对齐（L1 PASS，certBind=e3e13a49）；**debug 变体四端不对齐**（实际签仍 ca421ec3，见 §3）；**coexist registry/bootstrap 已 per-flavor 接线**（CMake + loader #if），剩 coexist.inc 按 8f47a47a 生成 + kdf coexist cert 待补（见 §8）。

---

## 3. debug 签名政策 = 唯一（= 老 A/B/C 里的 B），ca421ec3 退役

`[决]`（用户 2026-06-29 拍板，取代 cert-sync-v1 遗留的「debug=ca421ec3 仅开发签」）：

- **debug buildType 不再强制 `guardFixed`（ca421ec3）**；debug 跟随 flavor 的 release signingConfig：
  - officialDebug → `guardOfficialRelease`（e3e13a49）
  - coexistDebug → `guardCoexistRelease`（8f47a47a）
- 结果：四端在 debug 上也天然对齐 → 冷启动 `CompatProbe.checkSignature` 不再 `[cp] cert mismatch` → 不进影子期；`isIntegrityIntact`（A2 门）返回 true。
- `ca421ec3` 不再签任何包，仅留作 official 的轮换祖先（保老装机包升级）。

> 现状根因 `[码]`：`build.gradle:172-174` debug 强制 `guardFixed`=ca421ec3；coexist flavor EXPECTED=8f47a47a → `CompatProbe.checkSignature`（`ModuleMain.java:209`）当场 mismatch → `RiskState.markTampered` → 影子期。这是 cert-sync-v1 只切 release、没动 debug 的副作用（L1 实证：共存 debug 冷启 `[cp] cert mismatch`，pid 17808，2026-06-29）。

### 3.1 代价（运维约束，必须写进发版/构建文档）

唯一政策下 **debug 也要正式 release key 签** → **每台 build 机必须有 gitignore 的 `signing/*.jks` + `signing/keystore.properties`**，否则连 `assemble*Debug` 都签不出。`keystore.properties` 缺失时构建应**明确 BLOCK 报错**（不可静默回退 ca421ec3，否则又制造四端不齐）。

---

## 4. 密钥轮换（lineage）策略 — 保老装机包平滑升级

APK Signing v3 密钥轮换：用带 lineage 的 key 签名，老装机包可 `-r` 平滑升级（不丢数据），且运行时 `getApkContentsSigners` 返回轮换到的当前 key。

| 线 | lineage 文件 | 现状 | 升级语义 |
|---|---|---|---|
| **官替** | `signing/guard-debug-to-official.lineage` | ✅ 存在（ca421ec3 → e3e13a49） | 老 ca421ec3 官替装机包可平滑升级到 e3e13a49 |
| **共存** | （无 `debug-to-coexist.lineage`） | ❓ **待用户 Q1** | 见下 |

### 4.1 ❓ 待拍：共存线轮换 — Q1

共存包（`com.tencent.mn`）**没有** debug→coexist 的 lineage 文件。是否需要补，取决于：

- **若共存之前已有 ca421ec3 签的真实装机用户** → 必须补一条 `ca421ec3 → 8f47a47a` 的轮换 lineage，老共存包才能平滑升级；否则老用户升级会签名冲突（须卸载重装、丢数据）。
- **若没有真实共存装机用户 / 接受重装** → 8f47a47a 干净起线，无需 lineage。

> ⚠️ 此项未定前**禁止动共存签名相关代码**。用户答 Q1 后本节落定。

---

## 5. 为什么四端必须含 debug — registry / bootstrap 的 cert 绑定

SO 侧两块密文都把**签名 cert 折进解密 key**（`kdf_common.derive_registry_key` / `derive_bootstrap_key`），cert 不对 = GCM tag 失败 = 散沙：

- **registry**（`registry_cipher*.inc`）：折 cert + server seed（`GUARD_REGISTRY_REQUIRES_SERVER_SEED=1`）。`registry_loader.cpp:194-206`。
- **bootstrap**（`bootstrap_cipher.inc`）：折 cert、不折 seed（握手前要用）。`registry_loader.cpp:32-36`。
- **运行时喂入**：`ModuleMain.bindSigningCert`（`:151,:328-362`）读模块自身 APK 当前签名者 → `NativeBridge.setBindingMaterial(sha)`。

### 5.1 RegistryFallback 只兜「hook 锚点」，不兜「真锁链路」

- `RegistryFallback`：**debug 版 = 真值字面量**、**release 版 = 全空**（fail-closed）。`src/{debug,release}/.../RegistryFallback.java`。
- 消费：`GuardRuntime.getRecipeOrFallback(...)`（如 `ConvFilter.java:112`）——SO registry 散沙时回退到 RegistryFallback。
- **推论**：会话 / 朋友圈 / 通讯录 / 搜索这些 **hook 锚点** debug 走 fallback 照跑，**不依赖 SO registry 解密**；但 **A2 官方 DER（`a2.sig`）、SO recipe 通道、bootstrap 端点**只在 SO 密文里，cert 不对就 scatter，**RegistryFallback 不兜**。
- 所以唯一政策（debug 也签本线 key）才能让 debug 上 **registry / bootstrap / A2 也解得开** → debug 真正能测全链路（否则只能测 hook 锚点）。

---

## 6. 硬闸（build 前四端逐字节比对，不齐就 BLOCK）

`[决]` 待落：`tools/verify_cert_chain.ps1`（或 gradle task），对每个将出包的变体比：

1. ① 实际签名（`apksigner verify --print-certs` / lineage 当前签名者）
2. ② `BuildConfig.GUARD_EXPECTED_CERT`
3. ③ 该线 `registry_cipher*.inc` 绑定 cert（= `kdf_common` 传入值）

三者逐字节相等才放行 `assemble*`；任一不齐 → **FAIL，BLOCK 构建**。L1 清单同步补：`[cp] cert mismatch`=0 · certBind 前缀 = EXPECTED 前 8 · release 变体四端 ✅。

---

## 7. 代码 / 文件锚点（code-true 对照 · 改前先核）

| 概念 | 文件:行 | 现状 |
|---|---|---|
| per-flavor EXPECTED | `build.gradle:135`（official）/ `:150`（coexist） | `[码]` 已 per-flavor |
| debug 强制 guardFixed | `build.gradle:172-174` | `[码]` 现状（**唯一政策要改**，§3） |
| 三个 signingConfig | `build.gradle:100-126` | guardFixed / guardOfficialRelease / guardCoexistRelease |
| python cert 常量 | `kdf_common.py:68-73` | `[码]` **只有 official**；缺 coexist（§9） |
| registry inc 引用 | `registry_loader.cpp:33-37` | `[码]` #if 选 coexist/official inc（CMake `GUARD_REGISTRY_COEXIST`） |
| 运行时 certBind | `ModuleMain.java:151,328-362` | 读当前签名者喂 SO |
| 签名比对绊线 | `CompatProbe.java:79-91,135-152` | 读当前签名者比 EXPECTED；支持轮换 |
| RegistryFallback | `src/{debug,release}/.../RegistryFallback.java` | debug=字面量 / release=空 |
| 官替轮换 lineage | `signing/guard-debug-to-official.lineage` | ✅ 存在 |

---

## 8. 待办缺口（执行窗口照这里干）

| # | 缺口 | 落点 | 依赖 |
|---|---|---|---|
| **Q1** | ❓ 共存是否补 `ca421ec3→8f47a47a` lineage | `signing/` + 发版流程 | **待用户答**（§4.1） |
| **G-debug** | debug 跟随 flavor release 签（去 guardFixed 强制） | `build.gradle:172-174` | §3 已拍 |
| **G2** | coexist 真机 8f47a47a recipeOk L1（CMake/loader #if 接线已完成） | 装机 | G-kdf/G-boot |
| **G-kdf** | `kdf_common.py` 补 coexist cert（按线取 / 传参） | `kdf_common.py` | — |
| **G-boot** | 用 8f47a47a 生成 coexist `bootstrap_cipher` | gen 脚本 + CMake | G2 同类 |
| **G-gate** | 四端硬闸脚本 | `tools/verify_cert_chain.ps1` | — |
| **G-doc** | 文档减法（§3.A）：发版 skill §不可变 / RELEASE_RULES LSPatch 段 / PROTECTION_MAP §10.9 / VERSION_UPGRADE_SOP / 共存差异清单 等 10+ 处 → 改成本 SSOT 口径；LSPatch 签名从 `-k debug.keystore` 改对应线 jks(+lineage) | 各文档 | 本 SSOT 落定后 |

---

## 附：决策来源（只指针，不复制正文）

- `DECISION_LOG` **D-017**：官替 / 共存同一把 `ca421ec3` —— **已被本 SSOT 取代**（每线一把 release key + ca421ec3 退役为轮换祖先）。待在 `DECISION_LOG` 记新条目（建议下一可用编号）引用本文。
- `.devin/tasks/cert-sync-v1.md`：两线两证 intent 起点（official/coexist 各 cert 折 registry）。
- `CERT_RECON_证书对账表_20260629.md`：现状对账快照。
