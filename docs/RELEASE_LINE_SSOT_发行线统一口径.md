# RELEASE_LINE_SSOT — 发行线统一口径（单一真源 · v2）

> 版本：**v2 · 一套配方**（2026-06-30 用户拍板「印章 + 配方 + 服务器钥匙」三处合并；身份仍按包名 / release_id 区分）
> 历史：v1（2026-06-29）= 两把印章两套配方；2026-06-30 因「同一件事散在 30+ 处、AI 一个 AI 一个口径」回合为一套
> 性质：**发行线命名 + 证书 + registry + 服务器四端同源的单一真源**。命名 / cert / registry / release_id 冲突一律以本表为准。
> 关系：发版流程 = `docs/RELEASE_RULES.md`；服务器机制 = `I:\miyou-server\OPS_GUARD_NATIVE.md` + `SCHEMA.md`；决策溯源 = `DECISION_LOG.md` D-017（按版本轴分线）/ D-018（A2 改吊本地完整性）/ D-026（cert-converge v2 一套配方·当前权威）/ D-027（cert binding 改读宿主）。
> 铁律：G8 代码即真相 · G9 改完必更 · G10 一结论一处（命名 / cert / registry 口径只在本页定，别处引用不复写）。

---

## 0. 一句话

**官替 / 共存共用一套加密身份**（cert `e3e13a49` + S_rel `7255096e` + 同一份 `registry_cipher.inc`）；**身份区分靠包名 + release_id**（官替 `com.tencent.mm`/`android_8071` vs 共存 `com.tencent.mn`/`android_8071_coexist`）；debug 仍签 `ca421ec3`，仅作模块 / 公告 / C2 心跳 smoke，**不**作发行候选。

---

## 0.5 关键决策时间线（防 AI 来回打架）

| 日期 | 决策 | 真源 |
|------|------|------|
| 2026-06-12 | D-017：官替 / 共存同一固定 keystore（`ca421ec3` debug） | `DECISION_LOG.md` D-017 |
| 2026-06-29 | cert-sync v1：官替 → `e3e13a49`、共存 → `8f47a47a`，两套印章两套配方 | 无独立 D 号（阶段性，并入 `DECISION_LOG.md` D-026 撤回段；D-018 是 A2、勿混）；服务器 zxmqq.shop L1 |
| **2026-06-30** | **cert-converge v2：回合为一套配方**（共存 release 改用 `e3e13a49` official jks + 共用 `registry_cipher.inc` + 服务器 cert_prefix 合并） | `DECISION_LOG.md` D-026；服务器 L1（本页 §4.2） |
| **2026-06-30** | **cert binding 输入源改读宿主整包**（`ModuleMain.bindSigningCert` / `CompatProbe.checkSignature` / `GuardRuntime.isAntiBanReady` / `antiBanGateSelfTest` 4 处从读 `sModulePath` 改读 `app.getApplicationInfo().sourceDir`）—— 修 LSPatch metaloader 重打包模块时换 keystore（`ca421ec3`）导致 cert mismatch / registry 散沙 | `DECISION_LOG.md` D-027；`FAILURE_LOG.md` F-43 |

读到任何把「两把印章」当现行的文档 = 介于 v1 与 v2 之间的旧态，**以本页 v2 为准**。

---

## 1. 口径总表（v2 · 权威）

| 维度 | 官替 `hijack` | 共存 `coexist` |
|------|---------------|----------------|
| **release_id** | `android_8071` | `android_8071_coexist` |
| 宿主包名 | `com.tencent.mm` | `com.tencent.mn` |
| `package_line` | `hijack` | `coexist` |
| **正式签名 cert（前缀）** | `e3e13a49` | **`e3e13a49`（与官替同）** |
| **正式 keystore 文件** | `signing/guard-native-official-release.jks` | **同一把** `signing/guard-native-official-release.jks` |
| **registry_cipher 源** | `native_core/src/registry_cipher.inc`（绑 `e3e13a49`） | **同一份** `native_core/src/registry_cipher.inc` |
| **服务器 `release_lines.cert_prefix`** | `e3e13a49` | **`e3e13a49`（2026-06-30 已合并）** |
| **服务器 `release_lines.s_rel_fingerprint`** | `7255096e` | `7255096e`（两线同源） |
| **服务器 `release_lines.wrap_key_fingerprint`** | `22b72751` | `22b72751`（2026-06-30 已对齐） |
| **服务器 `release_lines.ed25519_key_id`** | `e1` | `e1`（2026-06-30 已对齐） |
| **服务器 `release_lines.lease_profile`** | `{stable_lease:259200,...}` | 同一份 JSON（2026-06-30 已对齐） |
| `schema_id` | `r8071_v1` | `r8071_v1` |
| 微信版本 | `8.0.71` | `8.0.71` |
| `product_version` | `v1.8` | `v1.8` |
| debug 签名 cert | `ca421ec3`（仅 smoke） | `ca421ec3`（仅 smoke） |

**版本号备注**：当前版本线 `v1.8` / `versionCode 16`（真源 `build.gradle:65-68` + commit `1cb7716`，G8）；**已出货给客户仍 = v1.7 / `versionCode 15`**（v1.8 正式包尚未出货，装机记录见 `_CORE/发版_当前真源.md §⑦`）。历史：`v1.3 → v1.6`（`vc13→14`，跳过 1.4/1.5）→ `v1.7`（`vc15`）→ `v1.8`（`vc16`）。服务器 `release_lines.product_version` 两线**已 L1 对齐 v1.8**（2026-07-08；配方零漂移，指纹见 §1 表 `s_rel 7255096e` / `cert e3e13a49`）。pv=v1.8 与出货 v1.7 的口径（`pv` 是展示位、v1.7/v1.8 共用同一份配方）**单一真源在** `_CORE/发版_当前真源.md §⑦`，此处不复写。

**身份与加密分离**：身份（统计 / 封禁 / 后台管理 / 同机双装识别）走 `release_id` + `package_name` + `package_line`；加密（registry 解密 / envelope 钥匙派生）走 cert + S_rel。前者两线**保持不同**、后者两线**完全相同**。

---

## 2. 三把证书（完整 SHA-256 · cert 是公开指纹，可写；keystore 口令 / 私钥禁写）

| 前缀 | 完整 SHA-256 | Keystore | 口径 |
|------|--------------|----------|------|
| **`e3e13a49`** | `e3e13a4974fe4c40432a583b11309edb4705c8a50b03f474d6b693817adecf36` | `guard-native-official-release.jks` | **官替 + 共存 release 共用（v2 · 2026-06-30）** |
| `8f47a47a` | `8f47a47afde078c47713db78668e6ed9ab027bc9b86d58a9e646a1f3998d5685` | `guard-native-coexist-release.jks` | **已退役**（v1 共存旧签，jks 保留作历史，未引用于任何 buildType） |
| `ca421ec3` | `ca421ec3a33708ceb3f70c37f4616751094736c496fe21b3b0e2cea480cdb6a0` | `guard-native-debug.keystore` | debug smoke only；不作发行候选 |

⚠️ jks 物理文件在 `.gitignore:119 signing/*.jks`（正确，私钥不入公开仓）→ Glob / git ls-files 会 0 命中，必须用 `Get-ChildItem -Force` 看物理文件。

---

## 3. 现状差距（代码 / 服务器真相 vs 本口径）

| # | 差距项 | 现状（证据） | 目标（本口径 v2） | 状 |
|---|--------|--------------|-------------------|:--:|
| D1 | 共存 release_id flavor 注入 | `build.gradle:154` GUARD_RELEASE_ID per-flavor + `AppConfig.java:52` 读 `BuildConfig.GUARD_RELEASE_ID`（L2） | 已注入 | ✅ |
| D2 | debug 签名 | `signingReport`：officialDebug / coexistDebug = `ca421ec3`（L1） | 仅 smoke，不作候选 | ✅ |
| D3 | registry per-flavor 接 CMake | `CMakeLists.txt:42` + `registry_loader.cpp:33-37`（L2）— **v1 遗物，v2 决策后可简化为统一选 `registry_cipher.inc`** | v2 共用一份 inc | 🟡 待清理代码注释 |
| D4 | 服务器 cert_prefix 合并 | `release_lines` 两线 cert_prefix = `e3e13a49`、s_rel / wrap / ed25519 / lease 完全对齐（L1 · 2026-06-30 21:52 UTC，主节点 zxmqq.shop） | 两线全字段同源（除身份 4 字段） | ✅ |
| D5 | 客户端 coexistRelease signingConfig | `build.gradle` coexistRelease 已改指 `guardOfficialRelease`（上轮 AI 改） | 共用 official jks | ✅ |
| D6 | `registry_cipher_coexist.inc` 文件 | 物理仍在 `native_core/src/` 下，运行时已不引用 | 头部加 DEPRECATED 注或归档 | 🟡 待清理 |
| D7 | `GuardHeartbeat.java:90` installId | 写死 = deviceId（TODO 未做） | per-install UUID（用于服务器锁闸识别重装） | 🟡 待落地（绑锁闸功能） |
| D8 | 备节点 `miyou.lol` | 未购未同步 | 主备同步 | 🟡 不阻塞（主节点足够） |
| D9 | 部署残留 1 台 mn 旧包真机 | `0dd9a5a3`/`com.tencent.mn`，release_id 仍 `android_8071_mn`，服务器无该线 → 真锁解不开（每次心跳重生） | 该设备需升级为 coexist 包 | 🟡 部署侧待处理 |

> 注：D3 在 v2 决策下变成「冗余兼容」——CMake / loader 里的 `#if` 选择路径仍会工作（只是两条分支都指向同一个 inc），不影响运行，只是代码注释和文档要清。

---

## 4. 落地三摊（v2 状态）

### 4.1 客户端（guard_native） — ✅ 已完成（2026-06-30 MMK12）

1. `build.gradle`：coexistRelease signingConfig 改用 `guardOfficialRelease`（→ official jks → `e3e13a49`）。
2. `build.gradle`：debug 仍走 AGP 默认 `~/.android/debug.keystore`（`ca421ec3`，与 `signing/guard-native-debug.keystore` 同一文件）；`guardFixed` 为预留死配置（未引用），可后期接入避免换机器漂移（不阻塞本次）。
3. `native_core/CMakeLists.txt` / `registry_loader.cpp`：per-flavor 选择路径保留但实际两条分支同结果；coexist.inc 待归档。
4. `AppConfig.java:52` GUARD_RELEASE_ID 已 per-flavor 注入。

### 4.2 服务器（miyou-server） — ✅ 已完成（2026-06-30 MMK12 · L1）

1. `release_lines.android_8071_coexist.cert_prefix`：`8f47a47a` → **`e3e13a49`**。
2. `release_lines.android_8071_coexist.cert_sha256_fp`：完整 SHA-256 同步替换。
3. 对齐 `ed25519_key_id=e1` / `wrap_key_fingerprint=22b72751` / `min_supported_version=v1.2` / `lease_profile` 复制官替 JSON。
4. `s_rel_fingerprint` `7255096e` 未动（两线本就同源）。
5. 备份 `/root/authdb_pre_certconv_20260630_215208.bak`；KDF selftest 镜像 = pinned vector；server.log 干净。
6. 改动方式：远程 SSH + python3 sqlite3 原子事务 UPDATE + 反检读回 + 失败 rollback；客户端无须重启 / 重装（envelope 每次心跳重读 `release_lines`）。

> L1 证据汇总：
> - 改前两线 cert_prefix 对照：`e3e13a49` / `8f47a47a`（2026-06-30 21:33 SELECT 留档）
> - 改后两线 cert_prefix：`e3e13a49` / `e3e13a49`（2026-06-30 21:52 SELECT 留档）
> - KDF selftest：`W_dev KDF wrap vector` = `18ad22f076c7dd2994215ba005373d8a` matches pinned `kKdfVecWrap`

### 4.3 文档收口 — ✅ 本次进行中（cert-converge-v2 文档同步）

- 本页（SSOT）整页重写为 v2。
- `docs/RELEASE_RULES.md` 签名表 + RELEASE_ID 段 + 共存身份段同步 v2。
- `PROTECTION_MAP.md` §10.9 签名行同步 v2。
- 2 份 `guard-release` skill 镜像（`.cursor` / `.claude`）同步 v2。
- 3 份 `guard-security` skill 镜像删「共存 RELEASE_ID 未注入」旧话。
- `04_review_审稿复核/CERT_RECON_证书对账表_20260629.md`：过程对账档，已于 2026-07-01 删除收敛（本页为唯一真源）。
- `03_execute_执行任务/P_CertConverge_证书收敛/`：任务卡 + 旧 SSOT（与 v2 全反）已于 2026-07-01 删除收敛（本页为唯一真源）。
- `03_execute_执行任务/P_AntiBanGate_防封授权闸/共存差异清单.md` 缺口①标已闭合，§1 #6 改 v2。
- `01_dispatch_总调度/CURRENT_PLAN.md` 删「共存 RELEASE_ID 未注入」「两套配方」旧话。
- `DECISION_LOG.md` 加 D-026（cert-converge v2）、D-027（cert binding 改读宿主整包）、D-028（心跳稳定档 60min）、D-029（W_dev Batch3 门禁服务器单方判定）。

---

## 5. 验证口径（L1 装机 · 两版各跑）

冷启动 logcat：

- `available=true`
- `role=1 MAIN`（共存尤其确认非 UNKNOWN）
- `BATCH1_VERIFY PASS`
- **`certBind=e3e13a49`（两版都同此前缀，v2 合并后预期一致）**；debug smoke 看到 `ca421ec3` + `cert mismatch/scatter` 属预期（debug 故意 scatter）
- `cp cert mismatch` = 0
- 心跳取回 server seed 后：`recipeOk=true`、`schema=r8071_v1`、`registry=ready`

任一不达 = 未对齐，禁止当发布候选。

**重要前提**：v2 服务器侧已合并；任何手机上**已装的共存包（com.tencent.mn）若仍是 v1 老包（被 `8f47a47a` 签的）**，服务器现派钥匙按 `e3e13a49` 算 → 老包解不开 → `recipeOk=false`、tier 走 TAMPER_SHADOW。**必须按 v2 重新打包共存包 + 卸老包 + 装新包**（签名不同无法覆盖）。

---

## 6. 红线 / 回滚

- 改 `build.gradle` / CMake / registry 前 git 快照；改服务器前备份 `auth.db`（红线#9）。
- registry / cert 任一不同源 = 预期 **scatter（散沙）**，不崩、不全开（fail-closed）。
- keystore 口令 / 私钥 / `S_rel` 原文 / `W` 原文禁写入任何文档或聊天（只写 cert SHA-256 与 `sha256[:8]` 指纹）。
- 服务器回滚：`cp /root/authdb_pre_certconv_20260630_215208.bak /opt/miyou-server/data/auth.db`。
- 客户端回滚：git revert + 重编 + 重装（共存老包按 `8f47a47a` 签的话需先卸再装）。
- v2 → v1 回退场景：把 `release_lines.android_8071_coexist.cert_prefix` 改回 `8f47a47a`、`build.gradle` coexistRelease signingConfig 改回 `guardCoexistRelease`、回滚 inc 选择路径。**强烈不建议**：v2 已通且文档已同步，回退只为复刻"AI 一会儿这样一会儿那样"的混乱。

---

## 7. 证据索引

| 结论 | 文件:行 / 证据 | 等级 |
|------|----------------|:----:|
| 官替 cert / release_id flavor 注入 | `build.gradle:135-138` | L2 |
| 共存 cert / release_id / pkg 注入 | `build.gradle:146-156` | L2 |
| coexistRelease signingConfig 已改 official | `build.gradle`（cert-converge-v2 客户端改动） | L2 |
| AppConfig.GUARD_RELEASE_ID = BuildConfig | `src/main/java/com/ghost/assist/core/AppConfig.java:52` | L2 |
| installId 仍 = deviceId（TODO） | `src/main/java/com/ghost/assist/net/GuardHeartbeat.java:90` | L2 |
| registry per-flavor 选择路径 | `native_core/CMakeLists.txt:42` + `registry_loader.cpp:33-37` | L2 |
| debug 签名实测值 | `./gradlew signingReport`（2026-06-29 实跑）= `ca421ec3` | **L1** |
| 三把 cert 完整 SHA-256（实测） | `keytool -list -v`（2026-06-29 实跑） | **L1** |
| 服务器两线 cert_prefix 合并 | `release_lines` 两线 = `e3e13a49`（2026-06-30 21:52 SELECT） | **L1** |
| 服务器两线 S_rel 同源 | `release_lines.s_rel_fingerprint` 两线 = `7255096e` | **L1** |
| KDF 镜像端 == pinned vector | `guard_selftest.py` 2026-06-30 21:52 跑过 | **L1** |
| 服务器密钥配置 | `I:\miyou-server\config.py:83`（GUARD_EXPECTED_CERT_PREFIX = `e3e13a49`） | L2 |
| 设计稿钦定 coexist 命名 | `I:\miyou-server\OPS_GUARD_NATIVE.md §4`、`SCHEMA.md §8.5` | L2 |
| 共存差异清单（v1 旧） | `03_execute_执行任务/P_AntiBanGate_防封授权闸/共存差异清单.md` | L2 |

---

## 8. 历史档案（v1 已退役条目，仅供溯源）

- **`signing/guard-native-coexist-release.jks`**（cert `8f47a47a`）：v1 cert-sync 共存专用 jks，v2 改用 official jks 后未引用；jks 保留在仓供溯源，不删（删 jks 不释放任何东西）。
- **`native_core/src/registry_cipher_coexist.inc`**：v1 共存专用 inc（绑 `8f47a47a`），v2 已不引用；待头部加 DEPRECATED 注或归档至 `native_core/src/_archived/`。
- **`bootstrap_cipher_coexist.inc`**（如有）：同上。
- **v1 cert-sync 部署记录**：`/root/authdb_pre_v16_20260629_103649.bak`（服务器 6-29 备份）。
- **v2 cert-converge 部署记录**：`/root/authdb_pre_certconv_20260630_215208.bak`（服务器 6-30 备份）。

---

*本页 = 发行线 / 证书 / registry 命名口径单一真源。任一端改动后回填本页（G9）；命名或 cert 再变更须先改本页再改代码（G10）。*
