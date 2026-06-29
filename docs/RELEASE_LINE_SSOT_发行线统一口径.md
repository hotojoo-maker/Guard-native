# RELEASE_LINE_SSOT — 发行线统一口径（单一真源）

> 建立：2026-06-29 · 用户拍板「口径 v1」（无真客户 / 无存量用户 → 干净重建，不受「在用线冻结」红线约束）
> 性质：**发行线命名 + 证书 + registry + 服务器四端同源的单一真源（SSOT）**。命名/cert/registry/release_id 冲突一律以本表为准。
> 关系：证据盘点 = `04_review_审稿复核/CERT_RECON_证书对账表_20260629.md`；发版流程 = `docs/RELEASE_RULES.md`；服务器机制 = `I:\miyou-server\OPS_GUARD_NATIVE.md` + `SCHEMA.md`；官替/共存差异 = `03_execute_执行任务/P_AntiBanGate_防封授权闸/共存差异清单.md`。
> 铁律：G8 代码即真相 · G9 改完必更 · G10 一结论一处（命名/cert/registry 口径只在本页定，别处引用不复写）。

---

## 0. 一句话

两条发行线（官替 `hijack` / 共存 `coexist`），**release 一线一签；debug 当前仍签 `ca421ec3`，只做模块/公告/C2 smoke，不作发行候选**；registry 按 flavor 各绑各自 cert；服务器 `release_lines` 两线 `cert_prefix` 与客户端逐字节同源。

---

## 1. 口径总表（v1 · 权威）

| 维度 | 官替 hijack | 共存 coexist |
|------|-------------|--------------|
| **release_id** | `android_8071` | `android_8071_coexist` |
| 宿主包名 | `com.tencent.mm` | `com.tencent.mn` |
| package_line | `hijack` | `coexist` |
| 正式签名 cert（前缀） | `e3e13a49` | `8f47a47a` |
| **debug 签名 cert** | `ca421ec3`（debug smoke only） | `ca421ec3`（debug smoke only） |
| keystore 文件 | `signing/guard-native-official-release.jks` | `signing/guard-native-coexist-release.jks` |
| registry_cipher 源 | `native_core/src/registry_cipher.inc`（绑 e3e13a49） | `native_core/src/registry_cipher_coexist.inc`（绑 8f47a47a） |
| 服务器 release_lines.cert_prefix | `e3e13a49` | `8f47a47a` |
| schema_id | `r8071_v1` | `r8071_v1` |
| 微信版本 | `8.0.71` | `8.0.71` |
| product_version | `v1.6` | `v1.6` |

**签名边界**：`ca421ec3` 仍是 debug 模块签名（`signingReport` L1），只用于模块更新 / 公告 / C2 心跳 smoke；正式发行、registry 预期 cert、服务器 `cert_prefix` 仍是官替 `e3e13a49` / 共存 `8f47a47a`。

**版本号备注**：本次 `v1.3 → v1.6`（`versionCode 13 → 14`），**有意跳过 1.4 / 1.5**（未发布）；此版 = 发行线/签名统一重建首发。客户端真源 = `build.gradle`（versionCode / versionName / GUARD_PRODUCT_VERSION，已改）；服务器 `release_lines.product_version` 同步 v1.6。

**✅ jks 已就位 + cert keytool 实测（2026-06-29 · L1）**：`signing/` 两把正式 jks 均在（6/29 0:46 生成）+ `guard-debug-to-official.lineage`（签名血缘）。keytool 实测证书 SHA-256：official=`e3e13a4974…decf36`、coexist=`8f47a47afd…d5685`，**与本口径逐字节一致**（对账表 §0「未跑 keytool」据此升 L1）。
> ⚠️ 工具教训：jks 被 `.gitignore:119 signing/*.jks` 排除（正确，私钥不入公开仓）→ **Glob/git ls-files 会 0 命中**，必须用 `Get-ChildItem -Force` 看物理文件，勿据 Glob 误判「jks 缺失」。

---

## 2. 三把证书（完整 SHA-256 · cert 是公开指纹，可写；keystore 口令/私钥禁写）

| 前缀 | 完整 SHA-256 | Keystore | 口径 |
|------|--------------|----------|------|
| `e3e13a49` | `e3e13a4974fe4c40432a583b11309edb4705c8a50b03f474d6b693817adecf36` | `guard-native-official-release.jks` | 官替（hijack）唯一签名 |
| `8f47a47a` | `8f47a47afde078c47713db78668e6ed9ab027bc9b86d58a9e646a1f3998d5685` | `guard-native-coexist-release.jks` | 共存（coexist）唯一签名 |
| `ca421ec3` | `ca421ec3a33708ceb3f70c37f4616751094736c496fe21b3b0e2cea480cdb6a0` | `guard-native-debug.keystore` | **已退役**（D-017 旧时代「官替共存同一把」） |

---

## 3. 现状差距（代码真相 vs 本口径 · 全部 L2）

| # | 差距项 | 现状（证据） | 目标（本口径） |
|---|--------|--------------|----------------|
| D1 | 共存 release_id | `build.gradle` 已注入 `android_8071_coexist` | `android_8071_coexist` |
| D2 | debug 签名 | `signingReport`：officialDebug/coexistDebug = `ca421ec3` | 保持为 debug smoke；正式候选必须用 release 变体 |
| D3 | registry per-flavor | **✅ 已接 CMake（2026-06-29 cert-sync 后 · MJ25 核）**：`CMakeLists.txt:42` `com.tencent.mn`→`GUARD_REGISTRY_COEXIST=1` + `registry_loader.cpp:33-36` `#if` 选 `registry_cipher_coexist.inc` vs `registry_cipher.inc` | 按 flavor 选 inc：coexist 嵌 `registry_cipher_coexist.inc`（绑 8f47a47a）**✅ 达成** |
| D4 | 服务器 cert_prefix | **✅ 已对齐（2026-06-29 主节点 zxmqq.shop · L1）**：官替→`e3e13a49`、共存→`8f47a47a`；共存线 `active→enabled`、product_version→v1.6、全局兜底 `ca→e3e13a49`；S_rel `7255096e` 未动；清测试机 5 台/171 条；备份 `/root/authdb_pre_v16_20260629_103649.bak` | official=`e3e13a49`、coexist=`8f47a47a` |
| D5 | 文档漂移 | `mn` 仅出现于 `build.gradle:155` + `施工提示词_keystore锁死.md:57`；`ca421ec3` 散落 10+ 处 | mn→coexist；ca421ec3 降级「仅历史/已退役」（清单见对账表 §3.A） |

> 注：`registry_cipher_coexist.inc` **已确认按 `8f47a47a` 生成（L1 · 2026-06-29 MJ25 实测解密）**：用 `8f47a47a` 派生 key 能 AES-GCM 解开、用 `e3e13a49` 解不开（InvalidTag）；`registry_cipher.inc` 反之。原「L4 待验」已升 L1。（另：官替 inc 同日重生成补回 `moments.feed` 4 锚点，pt==当前源·L1。）

---

## 4. 落地三摊（确认后分别执行）

### 4.1 客户端（guard_native）
1. `build.gradle`：coexist `GUARD_RELEASE_ID` 已收敛为 `android_8071_coexist`。
2. `build.gradle` buildTypes.debug：保留 debug smoke 口径；正式候选走 release 变体（依赖 `signing/keystore.properties` 就位）。
3. registry per-flavor 接 CMake：**✅ 已接**（`CMakeLists.txt:42` + `registry_loader.cpp:33`）；coexist.inc 绑 `8f47a47a` 已 L1 解密确认（见 §3 注）。
4. 重编官替 + 共存两版，装机 L1（见 §5）。

### 4.2 服务器（miyou-server）— ✅ 已完成（2026-06-29 主节点 zxmqq.shop · L1）
1. `release_lines`：`android_8071`.cert_prefix=`e3e13a49`、`android_8071_coexist`.cert_prefix=`8f47a47a` ✅。
2. `config.py` `GUARD_REL_KEYS` 两线 `S_rel/W` 同源确认（S_rel `7255096e` 未动）✅。
3. 清测试脏设备 + 共存线 `active→enabled` + product_version→v1.6 ✅。

> L1 证据（2026-06-29）：① 部署重启冒烟全绿 ② 测试卡取信封 W 解 k = `7255096e`（s_rel 与客户端同源）③ `e3e13a49` 风险 45/正常、未判异常。备份 `/root/authdb_pre_v16_20260629_103649.bak`。
> **服务器尾巴**：`latest_version` 已于 2026-06-29 刷 v1.6（经核：更新弹窗由 `server_settings.update_enabled` 驱动、当前=`0` 未开启，且 `get_update_config_for` 不读 `latest_version`，故**无「提示无包」风险**）；旧 `mn` 激活记录已删（2026-06-29，删前备份 `/root/authdb_pre_mnclean_20260629_110957.bak`）；⚠️ **仍有 1 台 mn 旧包真机在线**（`0dd9a5a3`/`com.tencent.mn`，release_id 仍 `android_8071_mn`，11:08 心跳）——服务器无 mn 线/无 mn 钥匙，其真锁解不开，且服务器侧清记录无效（每次心跳重生），根因 = 该设备需升级为 coexist 包（见 §3 D1）；备节点 `miyou.lol` 未购。

### 4.3 文档收口
- `mn` → `coexist`：`build.gradle` 注释、`施工提示词_keystore锁死.md`。
- `ca421ec3` 降级：按对账表 §3.A 清单（发版 skill、PROTECTION_MAP、RELEASE_RULES、DECISION_LOG D-017 等）逐处标「仅历史/已退役」。

---

## 5. 验证口径（L1 装机 · 两版各跑）

冷启动 logcat：

- `available=true`
- `role=1 MAIN`（共存尤其确认非 UNKNOWN）
- `BATCH1_VERIFY PASS`
- `certBind=<cert 前缀>`：release 候选官替=`e3e13a49` / 共存=`8f47a47a`；debug smoke 看到 `ca421ec3` + `cert mismatch/scatter` 属预期
- `cp cert mismatch` = 0
- 心跳取回 server seed 后：`recipeOk=true`、`schema=r8071_v1`、`registry=ready`

任一不达 = 未对齐，禁止当发布候选。

---

## 6. 红线 / 回滚

- 改 `build.gradle` / CMake / registry 前 git 快照；改服务器前备份 `auth.db`（红线#9）。
- registry / cert 任一不同源 = 预期 **scatter（散沙）**，不崩、不全开（fail-closed）。
- keystore 口令 / 私钥 / `S_rel` 原文 / `W` 原文禁写入任何文档或聊天（只写 cert SHA-256 与 `sha256[:8]` 指纹）。
- 回滚：客户端 git revert + 重编；服务器从 `auth.db` 备份恢复。

---

## 7. 证据索引

| 结论 | 文件:行 | 等级 |
|------|---------|:----:|
| official cert / release_id | `build.gradle:135,137` | L2 |
| coexist signingConfig / cert / pkg / release_id(mn) | `build.gradle:146,150,151,155` | L2 |
| debug 签名实际值 | `./gradlew signingReport`（2026-06-29 实跑）= `ca421ec3` | **L1** |
| registry per-flavor `#if` 选 inc（已接） | `native_core/src/registry_loader.cpp:33-36` + `CMakeLists.txt:42` | L2 |
| coexist.inc 绑 8f47a47a（解密实测） | `8f47a47a` 解开 / `e3e13a49` InvalidTag（2026-06-29 MJ25） | **L1** |
| coexist inc 文件存在 | `native_core/src/registry_cipher_coexist.inc` | L2 |
| official/coexist jks cert 实测 | `keytool -list -v`（2026-06-29 实跑）= e3e13a49 / 8f47a47a | **L1** |
| 三把 cert 完整 SHA-256 | `04_review_审稿复核/CERT_RECON_证书对账表_20260629.md §0` | L2 |
| 服务器两线密钥 | `I:\miyou-server\config.py:93,97` | L2 |
| 设计稿钦定 coexist 命名 | `I:\miyou-server\OPS_GUARD_NATIVE.md §4`、`SCHEMA.md §8.5` | L2 |
| 共存包/registry 同源已知坑 | `共存差异清单.md`、`docs/RELEASE_RULES.md` | L2 |

---

*本页 = 发行线/证书/registry 命名口径单一真源。任一端改动后回填本页（G9）；命名或 cert 再变更须先改本页再改代码。*
