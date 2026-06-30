# CERT_RECON — 证书/Keystore 全仓对账表（**已被 SSOT v2 取代 · 2026-06-30**）

> ⚠️ **本表整体过时 · 仅供溯源（2026-06-30 MMK12 cert-converge-v2）**  
> 命名 / cert / registry 真源 = `docs/RELEASE_LINE_SSOT_发行线统一口径.md`（v2 · 一套配方）。  
> 本表写于 cert-sync-v1（两把印章两套配方）时代；v2 已合并为一套配方（官替 / 共存 release 共用 `e3e13a49` + 共用 `registry_cipher.inc` + 服务器两线 cert_prefix 合并），下方矩阵 / 缺口 / G 编号大半失真。**不要据此下任何结论；先去 SSOT。**

---

> 角色（历史）：全局视角 · 架构师拍板用  
> 方法（历史）：L2 读码 + grep 全仓；**未跑 keytool / 未 apksigner 实测**（「实际 APK 签」列按 build.gradle 规则推导）  
> 背景（历史）：`P_CertSync`（2026-06-29 ✅）已把官替 release 切到 `e3e13a49`；大量旧文档仍写 `ca421ec3`
>
> ⚠️ **2026-06-29 复审更正（FSJ38 审计师 · 对代码核）**：本表 §1 L28 / §2 L47 / §4 G2 / §6「coexist registry 未接入 CMake / 未接线」**已过时**——代码 `native_core/CMakeLists.txt:42-44`（`com.tencent.mn`→`GUARD_REGISTRY_COEXIST=1`）+ `registry_loader.cpp:33-37`（`#if` 二选一 include coexist/official inc）**已按 flavor 接线**（MJ25 当日完成，`docs/RELEASE_LINE_SSOT_发行线统一口径.md` D3 + L61 L1 实测解密证）。**G2 🔴 视为已解**；下方矩阵/缺口未回填，**以代码为准**。
>
> ⚠️ **2026-06-30 二次更正（MMK12 · 服务器侧 L1 + 客户端 L2）**：cert-converge-v2 完成 —— 两线 cert 合并为 `e3e13a49`（共存 `8f47a47a` 退役）；CMake per-flavor 选择路径"两条分支同结果"= 实际共用 `registry_cipher.inc`；服务器 `release_lines` 两线 cert_prefix / s_rel / wrap / ed25519 / lease 全部对齐。下方所有写"共存 cert = 8f47a47a"的行**全部按 SSOT v2 §1 推翻**。

---

## 0. 三把钥匙（勿混）

| 前缀 | 完整 SHA-256（64 hex） | Keystore | 时代/用途 |
|------|------------------------|----------|-----------|
| **ca421ec3** | `ca421ec3a33708ceb3f70c37f4616751094736c496fe21b3b0e2cea480cdb6a0` | `signing/guard-native-debug.keystore`（guardFixed） | D-017 旧时代「官替共存同一把」；**现仅 debug buildType 签名** |
| **e3e13a49** | `e3e13a4974fe4c40432a583b11309edb4705c8a50b03f474d6b693817adecf36` | `signing/guard-native-official-release.jks`（需 keystore.properties） | **官替 release**（cert-sync-v1 目标） |
| **8f47a47a** | `8f47a47afde078c47713db78668e6ed9ab027bc9b86d58a9e646a1f3998d5685` | `signing/guard-native-coexist-release.jks` | **共存 release**（cert-sync-v1 目标） |

---

## 1. 四端对齐矩阵（按构建变体）

> **四端** = ① 实际模块 APK 签名 ② `BuildConfig.GUARD_EXPECTED_CERT` ③ `kdf_common.CERT_SHA256` / registry 生成 cert ④ 运行时 `certBind`（ModuleMain 读模块 APK 签 feeding SO）

| 变体 | ① 实际签（规则） | ② EXPECTED_CERT | ③ registry .inc 绑定 cert | ④ 预期 certBind | 对齐? | 备注 |
|------|------------------|-----------------|---------------------------|-----------------|:-----:|------|
| **officialDebug** | ca421ec3 | e3e13a49 | e3e13a49（`registry_cipher.inc`） | ca421ec3 | **❌** | debug 签 ≠ 预期 ≠ registry 绑定 → **CompatProbe mismatch + registry 解不开** |
| **officialRelease** | e3e13a49 | e3e13a49 | e3e13a49 | e3e13a49 | **✅** | cert-sync-v1 目标态；TASK_BOARD L131 L1 PASS |
| **coexistDebug** | ca421ec3 | 8f47a47a | 8f47a47a（`registry_cipher_coexist.inc`） | ca421ec3 | **❌❌** | debug 签 ca421ec3 ≠ 预期/inc → scatter（预期 fail-closed）|
| **coexistRelease** | 8f47a47a | 8f47a47a | 8f47a47a（`registry_cipher_coexist.inc`，CMake 按 flavor 选） | 8f47a47a | **✅** | per-flavor registry/bootstrap 已接线；8f47a47a 真机 recipeOk 待单验 |

证据：
- debug 签 guardFixed：`build.gradle:172-174`
- flavor EXPECTED_CERT：`build.gradle:135,150`
- kdf_common official cert：`tools/kdf_common.py:68-73`
- coexist registry/bootstrap 按 flavor 接线：`registry_loader.cpp:33-37/46-50` 用 #if 选 coexist inc；`CMakeLists.txt:42-44` 对 com.tencent.mn 定义 `GUARD_REGISTRY_COEXIST`

---

## 2. 代码真源（当前应以谁为准）

| 端 | 官替 official | 共存 coexist | 文件:行 |
|----|---------------|--------------|---------|
| BuildConfig EXPECTED | e3e13a49 | 8f47a47a | `build.gradle:135,150` |
| CompatProbe 读 | BuildConfig | BuildConfig | `CompatProbe.java:45` |
| Python CERT_SHA256 | e3e13a49（仅 official） | 须 `--cert-sha256` 覆盖 | `kdf_common.py:68-67` |
| registry_cipher.inc | e3e13a49（prod_server_lock） | — | `native_core/src/registry_cipher.inc` |
| registry_cipher_coexist.inc | — | 8f47a47a | coexist 变体经 CMake/loader #if 接入 |
| debug 签名 | ca421ec3 | ca421ec3 | `build.gradle:100-105,173` |

---

## 3. 文档/Skill 引用盘点（按 cert 时代分类）

### 3.A 仍写 ca421ec3（**旧时代 · 需更新或标 DEPRECATED**）

| 路径 | 行/段 | 写什么 | 建议 |
|------|-------|--------|------|
| `.cursor/skills/guard-release_发版/SKILL.md` | 35,77-78 | 模块证书 = ca421ec3 | 改：debug=ca421ec3 / official release=e3e13a49 / coexist=8f47a47a |
| `.agents/skills/guard-release_发版/SKILL.md` | 同上 | 镜像 | 同步 |
| `PROTECTION_MAP.md` | 143,478,484 | certBind=ca421ec3；不误报=ca421ec3 | 改 §10.9 分 debug/release 两行 |
| `docs/RELEASE_RULES.md` | 351,353 | LSPatch -k debug.keystore；guardFixed=registry | 保留 debug 流程，**补 release 用 official/coexist jks** |
| `docs/VERSION_UPGRADE_SOP.md` | 118 | regen 须等于 debug keystore | 改：按 flavor 取 cert |
| `DECISION_LOG.md` | D-017 | 官替共存同 keystore ca421ec3 | 标 **已被 cert-sync-v1 取代**；D-017 留档 |
| `03_execute/.../共存差异清单.md` | 25,63 | EXPECTED_CERT=ca421ec3 | 改 8f47a47a（共存） |
| `施工提示词_keystore锁死.md`（已删 2026-06-30）| 54-55 | 三处同源 ca421ec3 | 文件已删除 |
| `03_execute/.../R3_V3现状_D8前置.md` | 52-54,106 | 硬编码 ca421ec3 | 标 **侦察快照 2026-06-25，已被 cert-sync 覆盖** |
| `03_execute/.../R5_加密进度核查.md` | 77 | G2 cert 仍 ca421ec3 | 标 **过时**（cert-sync 后 G2 官替已动） |
| `PLAN.md`（已删 2026-06-30）| 139,141 | _CERT_SHA256 = ca421ec3 | 文件已删除（§A.5 并入 DESIGN 附录 F）|
| `03_execute/.../S3a0/result.md` | 92,105,108,117 | L1 certBind=ca421ec3 | **历史 L1 留档**，注明 debug 包 |
| 各 skill 铁律段（auth/terminal/git/execute/review × N） | 12 | 唯一固定 key = debug.keystore | 改：**debug 开发签** + release 各 flavor jks |

### 3.B 已写 e3e13a49 / 8f47a47a（**新时代 · 与代码一致**）

| 路径 | 内容 |
|------|------|
| `build.gradle` | official e3e13a49 / coexist 8f47a47a + 注释 |
| `tools/kdf_common.py` | CERT_SHA256 = e3e13a49 + coexist 须单独生成注释 |
| `tools/gen_registry_cipher.py` | `--cert-sha256` / `--out` per-flavor |
| `.devin/tasks/cert-sync-v1.md` | 三端同步任务书（权威过程） |
| `TASK_BOARD.md` | P_CertSync ✅ certBind=e3e13a49 |
| `CompatProbe.java` | 读 BuildConfig（非硬编码） |

### 3.C 决策冲突（文档互打架）

| 文档 A | 文档 B | 冲突 |
|--------|--------|------|
| D-017 / 发版 skill：同一把 ca421ec3 | cert-sync-v1 / build.gradle：per-flavor release jks | **已发生迁移，旧文档未跟** |
| RELEASE_RULES：LSPatch 一律 -k debug.keystore | build.gradle：release 用 official/coexist jks | **dev 流程 vs release 流程未分章** |
| R3 侦察：G2 cert 无 per-release | cert-sync 已做 per-release（官替） | 侦察过时 |

---

## 4. 已知缺口（待架构师裁决）

| # | 缺口 | 严重度 | 证据 |
|---|------|:------:|------|
| G1 | **officialDebug / coexistDebug 四端不对齐** | 🔴 | §1 矩阵 |
| G2 | coexistRelease 8f47a47a 真机 recipeOk 单独装机 L1 待补 | 🟡 | per-flavor registry 已接线；当前装机日志为 debug(ca421ec3) seed 后 recipeOk=true |
| G3 | **无 build 前自动四端对账脚本** | 🟡 | 全靠人工 + 文档 |
| G4 | **L1 清单未写「cert mismatch 必须为 0」** | 🟡 | RELEASE_RULES D 步未列 `[cp] cert mismatch` |
| G5 | **10+ skill/文档仍写 ca421ec3 为唯一真源** | 🟡 | §3.A |
| G6 | **keystore.properties / .jks 不在 git**（正常）但无「缺文件 BLOCK build」** | 🟡 | build.gradle 有 guardOfficialRelease 但 properties 缺失时行为待核 |
| G7 | **gen_registry_cipher.py 头部注释仍举 debug.keystore 例** | 🟢 | `:19-21` |

---

## 5. 政策选项（拍板用）

| 选项 | 做法 | 优点 | 缺点 |
|------|------|------|------|
| **A · dev 专用 ca421ec3** | debug 变体的 EXPECTED_CERT + registry 也绑 ca421ec3（单独 dev inc 或 debug 专用 regen） | 日常 `assemble*Debug` 不 mismatch | release 与 dev 两套 registry；须维护双 inc |
| **B · dev 也签 release key** | debug buildType 改用 flavor 的 release keystore（或取消 guardFixed 覆盖） | 四端天然对齐 | 日常开发碰正式 key；LSPatch 流程要分 dev/release |
| **C · 现状 + 硬闸** | 接受 debug 会 mismatch，**禁止用 debug 包做 L1 发版门控**；只验 `*Release` + 脚本 BLOCK | 改动最小 | 开发机仍可能进影子期弹窗 |

**当前代码隐含 = C 的半成品**（release 官替对齐了，debug 没对齐，且无硬闸）。

---

## 6. 建议下一步（本表之后）

1. **硬闸脚本**（`tools/verify_cert_chain.ps1` 或 gradle task）：四端 hex 逐字节比，FAIL 则 BLOCK assemble  
2. **共存 8f47a47a 真机装机 L1 + 核 coexist.inc 按 8f47a47a 生成**  
3. **批量改 §3.A 文档**：ca421ec3 降级为「仅 debug 开发签」  
4. **L1 清单补 3 条**：`[cp] cert mismatch` = 0 · `certBind` 前缀 = EXPECTED 前 8 · `recipeOk=true`（激活后）

---

## 7. 验证命令（架构师/发版官自跑 L1）

```powershell
# 1) 看模块 APK 实际证书（替换路径）
apksigner verify --print-certs build\outputs\apk\official\release\*.apk

# 2) 冷启动 logcat 四端
adb logcat -s NCL | findstr /i "certBind cp cert mismatch recipeOk"

# 3) 对比 BuildConfig（反编译或 debug 面板）
# GUARD_EXPECTED_CERT 应等于 apksigner 输出 SHA-256
```

---

> 本表只读产出；**未改任何代码/文档**。下一步请架构师在 §5 选 A/B/C 后，再派「硬闸」或「文档漂移修复」窗口。
