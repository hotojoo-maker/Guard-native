# 发布与运营

> 从 CLAUDE.md §十三~十六移出。

## 危险通告 / 一键停用（安全兜底）

**触发场景**：大面积账号异常 / 检测密度暴增 / 新版微信未适配 → 必须能远程拔插

**当前实现**（无需单独 `kill_switch` 字段；2026-06-30 减法收口）：

| 应急维度 | 怎么做 | 客户端表现 |
|---|---|---|
| **整条发行线停用**（最常用） | 后台改 `release_lines.status=killed` | EnvelopeClient.activate / fetchEnvelope 返 `RELEASE_KILLED` → token 清 / 不开 hook → Toast「该版本已停用，请联系客服」 |
| **单卡封停 / 退款** | 后台卡密 `status=banned/refunded` | envelope `cardRevoked=1` → 客户端立刻撤 A2 + 隐私（SPEC §4，唯一连坐两闸的非篡改场景） |
| **暂停新激活但不影响老用户** | 后台 `release_lines.status=paused` | 老用户继续走；新激活返 `RELEASE_PAUSED`「该版本暂停新激活」 |
| **服务器宕机** | 不用做 | 客户端 72h 离线宽限，过期自动降级（fail-closed） |

> 历史：曾有本地 `AppConfig.isKillSwitch()` v1 占位（永远 false 的孤儿 stub）+ `ModuleMain §5` 启动门控读它。**2026-06-30 减法收口**：RELEASE_KILLED + cardRevoked + RELEASE_PAUSED 已覆盖全部应急维度，stub 与门控代码删除（commit 见下方时间线）。

详细决策 → [`DECISION_LOG.md`](../DECISION_LOG.md) D-013

---

## 商业模式（v2 接入）

- **小众高端客户** + 隐私优先 + **不要求量大**（避免泛滥触发账号异常阈值）
- **每客户独立 seed/签名**（反聚类）
- **未授权引导**：校验未通过 → 提示独家功能 → 引导至官方购买渠道
- **授权按时间**（miyou-server `cs_url` + `shop_url` + 设备邀请码现成）
- **只零售、不批量分发**（面向终端用户，控制传播规模）

---

## 对外品牌

- **内部代号**：Guard Native（不对外）
- **客户端品牌**：待定（**禁用** "微信"二字 + vip/pirate/hide 等敏感词）
- **域名/服务器实体**：跟产品做切割（zxmqq.shop / miyou.lol 已就绪）
- **授权发放**：经独立销售渠道，与产品实体切割

---

## 发布铁律 / Release Rules

### 1.0 后签名证书不可变

Guard Native / Guard Pack 从 **1.0 正式发布**开始，同一客户包必须永久保持以下不变：

| 不可变项 | 说明 |
|---------|------|
| `packageName` | Android 覆盖安装的身份标识 |
| APK 签名证书 / keystore | 签名不一致 = 无法覆盖安装，客户必须卸载重装 |
| `customerSeed` | MMKV 命名空间根 / 混淆基准 |
| 授权绑定规则 | `licensedWxid + deviceHash + customerSeed` 组合不可拆分重组 |

后续版本只允许 **`versionCode` 递增**，禁止降级发布。

### 强制要求

1. **1.0 发布前必须建立客户包档案**，至少记录：
   - `packageName`
   - keystore 文件路径（离线存储位置）
   - key alias
   - `versionCode` 起始值
   - `customerSeed`
   - `licensedWxid` / device 绑定策略说明
2. **keystore 必须离线备份，至少两份**（不同物理位置）
3. **禁止将正式 keystore 放入**：公开仓库 / 聊天窗口 / 日志 / AI 对话 / 临时目录
4. **keystore 丢失 = 该客户包后续无法无损更新**（无法覆盖安装，只能新包重装）
5. **禁止 1.0 后重新生成 keystore 给同一客户继续发包**
6. **每次发版 `versionCode` 必须递增**，不得复用或回滚

### 例外处理

如果必须更换证书或包名，只能视为 **"新产品 / 新客户包"**：
- 不能覆盖安装旧版本
- 必须提前告知客户需要重新安装 + 重新授权
- 旧包档案归档到 `07_archive_归档/`，不得删除

---

## 双版本发布手册（官替版 / 共存版）

### 签名与安装唯一真相（v2 · 2026-06-30 cert-converge）

> 命名 / cert / registry 真源 = `docs/RELEASE_LINE_SSOT_发行线统一口径.md`（v2 一套配方）。本节为速记，冲突以 SSOT 为准。

以 `./gradlew signingReport` 和 Android 安装器为准：

| 产物 | 当前签名 | 用途 |
|------|----------|------|
| `officialDebug` / `coexistDebug` 模块 APK | `ca421ec3` debug key | 只用于模块更新、公告、C2/心跳 smoke；因 `GUARD_EXPECTED_CERT` 仍按正式 cert 注入，registry/A2 会 cert mismatch/scatter |
| `officialRelease` 模块 APK | `e3e13a49` official key | 官替正式发行候选 |
| `coexistRelease` 模块 APK | **`e3e13a49` official key（v2 已合并）** | 共存正式发行候选；signingConfig 改指 `guardOfficialRelease` |

> v1 共存专用 `8f47a47a` keystore 已退役（jks 保留作历史，未引用于任何 buildType）。详见 SSOT §0.5 / §2 / §8。

安装判定分两层，禁止混：

- **只装模块**：Android 比较 `com.ghost.assist` 现装模块签名；签名不同只能卸载模块后重装，**不动** `com.tencent.mm` 数据。
- **LSPatch 官替/共存整包**：Android 比较宿主包签名（`com.tencent.mm` / `com.tencent.mn`）；宿主签名不同不能覆盖，除非有同一 keystore 或确认卸载宿主。
- `certBind=<前缀>` 是模块/registry 绑定材料，不等于宿主安装签名；不能拿它判断 `adb install -r` 是否能覆盖。

发版候选必须用 release 变体；debug 变体只能算调试 smoke。

### 共存 / 官替版打包 + 干净装机（唯一速查，每次照此跑）

> 每次重编/装机就这 4 步。专治两个高频坑：**装到旧模块**、**启动崩 LSPatch metaloader**。
> 完整发版（换 s_rel + 服务器同步）见下方「s_rel 轮换 + 双版本 LSPatch 发版工作流」；本块是其精简装机口径，不另立第二套。
>
> **2026-06-30 cert-converge v2 起：发版候选必须走 `Release` 变体 + official keystore**（详 D-026 / D-027 / F-43）。`Debug` 变体的模块 cert = `ca421ec3` ≠ `GUARD_EXPECTED_CERT` = `e3e13a49` → registry 散沙 + A2 不装；`Debug` 现仅作模块更新 / 公告 / C2-smoke 用，不作发行候选（见上方 §"签名与安装唯一真相" 表）。

**一键脚本（推荐 · `tools/lspatch_pack.ps1`，commit `2cd643c`）**：

```powershell
# 共存版 release（自动跑 :assembleCoexistRelease + 读 keystore.properties + LSPatch）
.\tools\lspatch_pack.ps1 -Flavor coexist -BuildType release -Clean -Build

# 官替版 release（同理）
.\tools\lspatch_pack.ps1 -Flavor official -BuildType release -Clean -Build
```

脚本完事会打印产物路径 + 下一步 3 行装机命令。脚本细节看 `tools/lspatch_pack.ps1` 头注。

**手动 4 步（如不想用脚本 / 排错时）**：

```text
# 1. clean 重编共存 release 模块（必须 clean，否则 gradle 判 UP-TO-DATE 打进旧 dex）
./gradlew clean :assembleCoexistRelease
#    产物：build/outputs/apk/coexist/release/guard-native-coexist-release.apk（GUARD_WX_PKG=com.tencent.mn，e3e13a49 签）
#    自动跑 checkStringLeakCoexistRelease 硬闸（dex+SO 扫泄漏）

# 2. LSPatch 重新打包进共存宿主（-k 必须用 official release keystore = e3e13a49）
#    keystore 密码从 signing/keystore.properties 读（gitignore，不进源码）
java -jar 02_tools_工具/lspatch.jar 02_tools_工具/mn_clean_origin_8071.apk \
  -m build/outputs/apk/coexist/release/guard-native-coexist-release.apk \
  -l 2 -k signing/guard-native-official-release.jks <storePass> guardofficial <keyPass> \
  -o 02_tools_工具/lspatch_out -f
#    校验日志：Embedding modules - com.ghost.assist
#    产物：02_tools_工具/lspatch_out/<宿主名>-<versionCode>-lspatched.apk
#    宿主整包签名 = e3e13a49（apksigner verify --print-certs 实测）→ ModuleMain.bindSigningCert
#    读 hostApkPath(app)=app.getApplicationInfo().sourceDir 拿到 e3e13a49 = 与 registry 派生同源 → 解开

# 3. 干净装机（绝不 install -r 覆盖；卸 + 重启清 dex/odex + 装）
adb uninstall com.tencent.mn
adb reboot                       # 等 sys.boot_completed=1 再继续
adb install 02_tools_工具/lspatch_out/<上一步产物>.apk

# 4. 用户桌面点开图标（禁 am start / monkey —— 会崩 metaloader）
```

铁律（2026-06-29 实测踩坑固化 · 2026-06-30 cert merge 补强）：

- **必 `clean`**：省 `clean` → gradle 判 UP-TO-DATE → 把旧模块打进包（表现：日志 self/cert 对不上、自测缺失）。
- **必干净装**：`install -r` 覆盖 + `monkey`/`am start` 起 → 崩 LSPatch metaloader（`ExceptionInInitializerError`）；卸载 + 重启 + **桌面点开**才稳（详发版官 skill §坑4）。
- 共存只动 `com.tencent.mn`，不碰官方 `com.tencent.mm`、也不碰其他克隆。
- 官替版同理：flavor 换 `:assembleOfficialRelease`、宿主换原版微信 clean APK（`02_tools_工具/host_official_clean_8.0.71.apk`）、目标包换 `com.tencent.mm`、LSPatch `-k` 同一把 `guard-native-official-release.jks`（cert-converge v2 起两 flavor 共用 official keystore）。
- **2026-06-30 cert binding 真相**（F-43 教训）：`bindSigningCert` 读宿主整包 sourceDir（即 LSPatch `-k` 用的 keystore），**不再**读模块自身 `sModulePath`——LSPatch metaloader 会把内嵌模块 APK 在 extract 时用 LSPatch 内置 debug keystore 重打包重签（实测 cert = `ca421ec3`），与发版 release keystore `e3e13a49` 不一致。改读宿主 sourceDir 后机制对所有 LSPatch 形态都稳。详 `DECISION_LOG.md` D-027 / `FAILURE_LOG.md` F-43。

### 下次发新版最短流程（先看这里）

> 目标：新版本发版只走一条线，不再临时猜“官替/共存/服务器配方”。

**一句话模型：**

```text
一个发行包 = 一个 release_id = 一份 registry_cipher = 服务器一条 release_line + 一份 S_rel
```

**A. 先定版本线**

| 发行包 | 宿主包名 | 客户端 release_id | 服务器 release_line |
|--------|----------|-------------------|---------------------|
| 官替版 | `com.tencent.mm` | 例如 `android_8072_hijack` | 同名 |
| 共存版 | `com.tencent.mn`（固定） | 例如 `android_<版本>_coexist` | 同名 |

铁律：客户端 `BuildConfig.GUARD_WX_PKG` 和 `BuildConfig.GUARD_RELEASE_ID` 必须按 flavor 注入；不能只改包名、不改 release_id。否则会出现“共存包名 `com.tencent.mn`，但服务器归到官替线 `android_8071`”的错位。

**B. 客户端生成配方**

1. 为每条新线准备 `release/secrets/<release_id>.json`（真密钥文件，gitignore，禁止进聊天/文档）。
2. 用该 recipe 生成 `native_core/src/registry_cipher.inc`：

```text
python tools/gen_registry_cipher.py --recipe release/secrets/<release_id>.json
```

3. 编对应 flavor：官替编 official，共存编 coexist。
4. LSPatch 重新打包进对应宿主 APK：官替用原版微信 APK，共存用已改包名克隆 APK。

**C. 服务器上传配方 / 登记发行线**

服务器不接收 hook 热更新，只登记发行线并发同一条线的 server seed：

1. 在 `I:\miyou-server\config.py` 增加或更新同名 `GUARD_REL_KEYS[release_id]`，只同步 `s_rel/W` 所需材料，部署前先备份远端 `config.py` 和 `data/auth.db`。
2. 在后台「高级设置 → 发版档案」登记同名 release：`package_line`、`product_version`、宿主包名、证书指纹、`S_rel/W` 指纹、registry hash。
3. 部署主备服务器；注意常规 `deploy_release_health.py` 只推 `server.py/db.py`，涉及 `config.py` 的配方上传必须单独核对、备份、覆盖、重启。
4. 验证 `/api/v1/ping`、`/admin/api/releases`，再用测试卡取 envelope 实测 `s_rel` 指纹，不只看后台登记列。

**D. 装机验收**

必须看到：

```text
role=1 MAIN
BATCH1_VERIFY PASS
[hb] registry after seed ... recipeOk=true
```

后台版本总账应显示为：

```text
新设备 / 观察中 / 稳定设备 / 需处理
```

新设备或观察期不是异常；只有 tier>=2、封停、seed/配方失败、风险态、失败计数才算“需处理”。

**E. 不动老客户**

已经发布给客户的 `release_id` 冻结：不原地换 `S_rel`、不换 registry、不改证书/包名。要发新内容就开新 `release_id`，老线只做 `deprecated` 或按节奏通知升级。

> 目标：后续发版由 AI 按包档案自动改包、签名、生成加密配方和打包；用户只选择发哪条版本线，不手动碰签名。

### 两条版本线

| 版本线 | 包名规则 | 覆盖关系 | 典型用途 |
|--------|----------|----------|----------|
| 官替版 | 固定 `com.tencent.mm` | 只覆盖官替版 | 卸官方后安装，体验最接近原微信 |
| 共存版 | 固定 `com.tencent.mn`（首发已定，不可改） | 只覆盖同包名共存版 | 官方包保留，隐私版独立共存 |

铁律：

1. **官替版只能覆盖官替版**：包名固定 `com.tencent.mm`，签名证书必须一致。
2. **共存版只能覆盖共存版**：包名固定 `com.tencent.mn`（首发已定），签名证书必须一致。
3. **官替版和共存版互不覆盖**：它们是两条独立发行线，分别维护 `versionCode`、release_id 和客户包档案；**v2 起共用同一把 release keystore（official jks → `e3e13a49`），区分仅靠包名 / release_id**。
4. **同一版本线禁止换签名**：换签名 = Android 不能覆盖安装，且证书绑定会导致 encrypted registry 散沙。

### 发包 / 分发边界（2026-06-11 用户口径）

**本地打包，网盘只放 APK。**

- AI 的职责是在本机按包档案打出两个可分发 APK：**官替版 APK** + **共存版 APK**。
- 网盘只放 APK；网盘路径、目录名、下载链接由用户随意决定，**不属于项目状态，不写入发版档案**。
- 服务器只用于授权 / 公告 / 真锁材料登记；**不参与打包、不托管 APK、不决定网盘路径**。
- 发版流程不得要求用户说明“APK 放哪个网盘 / 哪个目录”；只要本地 APK 产物和装机验证通过即可。

本机改包工具路径：

```text
apktool: C:\Users\Me\Desktop\guard_native\02_tools_工具\apktool.jar
```

> 说明：`apktool.jar` 用于共存版静态改包名 / manifest / provider authority 等 APK 结构处理；LSPatch 仍只负责注入模块，不负责改包名。

### AI 发版档案

每条版本线必须有一份本地包档案，AI 发版时只读档案，不临时猜参数。

最少记录：

```text
release_line: hijack | coexist        # 仅 package_line 维度命名，非 release_id 后缀
package_name: com.tencent.mm 或共存后缀包名
release_id: android_8071 或 android_8071_coexist   # 官替裸 android_8071；共存带 _coexist 后缀
keystore_path: signing/guard-native-official-release.jks (v2 两线共用)
key_alias: 签名 alias
version_code_next: 下一个可用 versionCode
customer_seed: 客户/批次 seed
guard_wx_pkg: 运行时宿主包名
cert_sha256_source: 从 keystore/签名自动读取，不手写（v2 两线都是 e3e13a49）
```

禁止事项：

- 禁止把正式 keystore 内容、密码、私钥复制进聊天窗口或文档。
- 禁止 AI 重新生成 keystore 给旧客户继续发包。
- 禁止手工改 `_CERT_SHA256` 后不做 `PHASE1D_VERIFY` / `PHASE1E_VERIFY` 验证。
- 禁止复用已经发过的 `versionCode`。

### 发版时 AI 必须同步的参数

官替版和共存版都必须同源更新以下位置：

| 参数 | 作用 |
|------|------|
| `guardWxPkg` | 写入 C++ `GUARD_EXPECTED_PACKAGE`，供 anti_tamper 判断宿主包 |
| Java 宿主包白名单 | `ModuleMain` 必须识别目标包名和 `:push` 进程 |
| Xposed scope | 独立模块模式下 scope 必须指向目标宿主包 |
| `release_id` | 服务端发行线区分官替 / 共存 |
| 签名证书 SHA-256 | 参与 registry key 派生，签名不一致会 scatter |
| `versionCode` | 每次发版递增，保证覆盖安装 |

当前状态提醒：

- C++ 包名注入已预留：`-PguardWxPkg=...`（CMake `-DGUARD_WX_PKG` → `GUARD_EXPECTED_PACKAGE`）。
- Java 宿主包白名单已随 `BuildConfig.GUARD_WX_PKG` 区分 official/coexist；共存版进程识别不再写死 `com.tencent.mm`。
- ✅ `GUARD_RELEASE_ID` 已 per-flavor 注入（`build.gradle:138/154` + `AppConfig.java:52 = BuildConfig.GUARD_RELEASE_ID`，2026-06 完成）。官替上报 `android_8071`、共存上报 `android_8071_coexist`，服务器按 release_id 各自聚合。
- 共存版如果由 MT 管理器等工具改包名，仍必须把最终包名同步给模块构建链（`GUARD_WX_PKG` 与 `GUARD_RELEASE_ID` 必须同源）。

> ⚠️ **包名注入坑（2026-06-12 实证修正）**：`GUARD_WX_PKG` 不能只喂 `anti_tamper` 的 `EXPECTED_PACKAGE`，还**必须**驱动 `guard_core.h` 的 `PROCESS_MAIN` / `PROCESS_PUSH`（进程角色判定）。历史上后两者写死 `com.tencent.mm`，导致共存版（`com.tencent.mn`）进程角色判为 `UNKNOWN` → `BATCH1_VERIFY FAIL` → 业务 hook 不安装 → **不报错但不隐藏**。已改为 `PROCESS_MAIN = GUARD_EXPECTED_PACKAGE`、`PROCESS_PUSH = GUARD_EXPECTED_PACKAGE ":push"`（官替版值不变，零影响）。新增任何"按包名分支"的 native 常量，一律从 `GUARD_EXPECTED_PACKAGE` 派生，禁止再写死。

### 加密接手清单（AI 发版必读）

当前加密链路是：

```text
native_core/registry_8071.json
  → tools/gen_registry_cipher.py
  → native_core/src/registry_cipher.inc
  → libguardcore.so registry_load_embedded()
  → GuardRuntime.getRecipe()
  → Filter 读取 hook 配方
```

各文件职责：

| 文件 | 职责 | AI 禁忌 |
|------|------|---------|
| `native_core/registry_8071.json` | hook 配方唯一明文源 | 不确认活跃锚点，不新增/删除字段 |
| `tools/gen_registry_cipher.py` | 生成 AES-GCM registry cipher | 不手写 `_CERT_SHA256` 后跳过验证 |
| `native_core/src/config_crypto.cpp` | AES-GCM + key 派生 + 证书/服务器材料折入 | 不把派生 key 改成单个明文常量 |
| `native_core/src/registry_loader.cpp` | 解密 registry + 自测 + recipe getter | 不放宽 scatter 失败策略 |
| `src/main/java/com/ghost/assist/core/GuardRuntime.java` | Java 侧配方唯一出口 | Filter 不得绕过它直接读加密细节 |

加密现状口径：

- 已完成：AES-GCM encrypted registry、签名证书绑定、`GuardRuntime.getRecipe()` 取配方、失败 scatter、S4 Ed25519 信封验签、S3b-A/B 服务器授时 + 设置页 72h 离线强验。
- 已完成（当前 `android_8071` 发行线）：`prod_server_lock` 生成链路已把服务器 `S_rel` 折入 `registry_cipher.inc`，产物 `GUARD_REGISTRY_REQUIRES_SERVER_SEED=1`；线上 envelope `k/n` unwrap 后 `recipeOk=true`，无有效 server seed 时 registry scatter。
- 仍未完成：删剩余 Filter 明文字面量 / fallback 债、RiskState 全链路散沙降级与正版恢复闭环（当前仅来电拦截有篡改散沙例外）。`GuardHeartbeat.java:90` install_id 仍 = device_id 的 TODO（per-install UUID）= 服务器侧"重装锁闸"功能前置条件。
- 对外只能说：**v1.6 商业授权闭环 + Ed25519 防伪造信封 + 当前发行线 server seed 解 registry 已接入**。
- 对外禁止说：**服务器真锁终局完成** 或 **授权无法破解**。

发版时必须保证：

1. `registry_8071.json` 是唯一配方源；`registry_cipher.inc` 只能由脚本生成，禁止手改。
2. 生成 cipher 时使用的证书 SHA-256，必须等于运行时 `bindSigningCert()` / 后续宿主签名读取到的证书 SHA-256。
3. v2 已合并签名：官替 / 共存 release 共用 `e3e13a49`，**共用一份 `registry_cipher.inc`**（绑 `e3e13a49`，详 SSOT §1）；不再需要 per-flavor `registry_cipher_coexist.inc`（已退役）。
4. `tools/gen_registry_cipher.py::derive_registry_key()` 必须与 `config_crypto.cpp::derive_registry_key()` 保持一致；漂移会导致 tag 校验失败并 scatter。
5. `registry_loader.cpp::registry_self_test()` 必须通过；它会验证 schema、核心 4 条 registry、tamper cipher、wrong key、miss gateway/field。
6. standalone 测试如果调用 `registry_self_test()`，必须先设置和生成端一致的 binding material；否则 scatter 是正确结果，不是误报。
7. 装机日志必须看到 `PHASE1C_VERIFY PASS`、`PHASE1D_VERIFY PASS`、`PHASE1E_VERIFY PASS`，才说明 encrypted registry、证书绑定、recipe 出口都通。
8. **release SO 不得残留调试宏/符号（发版前必查）**：2026-06-25 曾实测 `build.gradle` 的 `defaultConfig.externalNativeBuild.cmake.buildTypes{debug/release}` **失效**，`-DGUARD_DEBUG` 泄漏进 release SO。**已修（2026-06-25）**：删 Gradle `cppFlags`；可剖代码改 `GUARD_DEV_SELFTEST` + `GUARD_DEV_LOG`（`CMakeLists.txt` 仅 `CMAKE_BUILD_TYPE=Debug` 定义）。**发版前仍必查两条**：① release 的 `compile_commands.json` 不含 `-DGUARD_DEBUG` / `GUARD_DEV_*`；② release `.so` 的 `nm -D` 中 `nativeKdfSelfTest` **absent**（对照 `nativeRegistrySelfTest` 仍在）。
9. **release 包不得残留测试/调试痕迹（dex+SO strings 体检，已接 gradle 硬闸）**：`assembleOfficialRelease` / `assembleCoexistRelease` 后由 `finalizedBy :checkStringLeak{Official,Coexist}Release`（`build.gradle`）自动调 `tools/check_release_strings.ps1` 扫产物 APK 的 dex+SO。命中 **FAIL 词**（dex：`[ANTIBAN-GATE]` / `[ANTIBAN-BRANCH]` / `KDF_VECTOR_VERIFY` / `GuardRuntimeAntiBanTest`；SO：`nativeKdfSelfTest`）即脚本 exit 1 → **断构建**，泄漏包发不出。debug 变体不挂（本含 DEBUG 码）。手动 / 排错：`gradlew checkStringLeakOfficialRelease -PstringGateApk=<apk>`，或 `powershell -NoProfile -File tools/check_release_strings.ps1 -Apk <apk>`（exit 0=PASS / 1=泄漏 / 2=文件错）。**WARN 只复核不阻断**：`[D2D3:`（`MomentsFilter` cleanD2D3/dump 由运行期 `sDiagSeen` 门控、非 `BuildConfig.DEBUG`，按设计进 release）、`kdf_self_test` / `decrypt_config_self_test`（release `RelWithDebInfo` 保留的内部函数名）、keep 残留 `kdfSelfTest` / `fallbackSelfTest` / `*SelfTest`。把 WARN 清到可升 FAIL = 减法收口线另开任务（D2D3 诊断改 `BuildConfig.DEBUG` 门控走双审〔铁律29〕 / strip release SO）。本条是第 8 条 SO `nm -D` 的 **dex 层补网**（native 靠 `#ifdef` 物理隔离，Java 靠 R8 逻辑隔离需 strings 兜底）。

scatter 排查顺序：

1. 看 `PHASE1D_VERIFY` 是否 FAIL：优先查签名证书 SHA-256 是否和生成 cipher 时一致。
2. 看 `PHASE1C_VERIFY` 是否 FAIL：优先查 `registry_cipher.inc` 是否由当前 `registry_8071.json` 重新生成。
3. 看 `PHASE1E_VERIFY` 是否 FAIL：优先查 `GuardRuntime.getRecipe()` / `EncryptedConfigLoader` / `nativeGetRecipe()` 出口。
4. 共存版整片 scatter：优先查最终包名、运行时证书源、`guardWxPkg`、Java 白名单和 scope 是否同源。
5. 业务 hook 仍生效但加密验证 FAIL：可能是旧 fallback 在兜底，不能当作加密链路通过。
6. **`recipeOk=false` 但配方/证书/s_rel 都对 → 查设备风控 decoy（不是配方坏）**：服务器对 `tier_code>=3`（蜂窝/decoy）设备发**随机垃圾种子**（`crypto_utils` `is_decoy → secrets.token_bytes`）→ 客户端解出垃圾 → registry scatter。`tier` 由 `risk_score>=risk_honey` 触发，而 `risk_score` 主要被**重装churn**(install_id 变 +35+…)、token churn 顶上去。排错：用测试卡激活后看 `[hb] synced tier=`；tier≥3 就是被风控误判，不是发版材料问题。

### 服务器设备风控语义（2026-06-12 上线，发版/排错必读）

- **device_id = `SHA256(ANDROID_ID)[:8]`**，同签名 App 共享 ANDROID_ID → **官替 `com.tencent.mm` 与共存 `com.tencent.mn` 在同一台手机上 device_id 相同、共用一条 `guard_device_state`**。
- 现行风控（`db.py`）：
  - **激活即清风险**：有效卡密激活绑定设备 → `risk_score/tier/reinstall` 清零（授权用户随便重装不触雷）。
  - **每日重装宽限 5 次**：同日前 5 次重装不加分，超出才罚。
  - **同机双版本不互 churn**：仅当 `release_id` 相同才计 reinstall/token churn；官替↔共存跨线翻转视为版本共存、不罚。
  - **后台「恢复正常」真清风险**：`update_device_status('normal')` 会重置 risk/tier（不只是改 status 标记）。
- **后台「版本状态」→「展开真锁/异常设备」** 可列出本发行线 tier≥2/decoy/失败 设备并一键「恢复正常(清风险)」。
- 测试机被反复 install 顶成 decoy 时：**重新激活**或后台**恢复正常**即脱离；别再狂重装（255×0.85ⁿ 自愈很慢）。

### 标准发版流程

1. 选择版本线：`官替版` 或 `共存版`。
2. AI 读取对应包档案，确认 `packageName`、keystore、`versionCode`、`release_id`。
3. AI 自动读取签名证书 SHA-256，重新生成 encrypted registry。
4. AI 写入宿主包名到 Java / scope / C++。
5. AI 构建并使用对应 keystore 签名 APK。
6. AI 装机或交给用户装机验证。
7. 必须抓到以下日志后才算发布候选：
   - `BATCH1_VERIFY PASS`
   - `PHASE1A_VERIFY PASS`
   - `PHASE1B_VERIFY PASS`
   - `PHASE1C_VERIFY PASS`
   - `PHASE1D_VERIFY PASS`
   - `PHASE1E_VERIFY PASS`
8. 验证业务主链路：显隐切换、会话过滤、通讯录过滤、朋友圈过滤、搜索过滤、红点/通知相关链路。
9. 产物只收口为本地 APK：官替版 APK / 共存版 APK。上传网盘由用户自由处理，路径不进项目发版档案。
10. 通过后递增并回写包档案里的 `version_code_next`。

### s_rel 轮换 + 双版本 LSPatch 发版工作流（2026-06-12 实操固化）

> 一次完整发版（换种子 / 出官替 + 共存）实际跑通的步骤，照此复刻，别再各处猜。

**A. 换 s_rel（可选；同 release_id 换种子 = 旧装机包会散沙，需重装）**
1. 改 `release/secrets/<release_id>.json` 的 `s_rel_b64`（新随机 32B；`wrap_key` 不变 = 不用动 SO 的 W）。
2. `python tools/gen_registry_cipher.py --recipe release/secrets/<release_id>.json` → 重生成 `native_core/src/registry_cipher.inc`（确认 `GUARD_REGISTRY_REQUIRES_SERVER_SEED=1`）。
3. 指纹对账：客户端/服务器两边 `sha256(s_rel)[:8]` 必须一致（只记指纹，不记原文）。

**B. 出包（每条发行线各编一次 · 2026-06-30 cert-converge v2 起强制 Release）**
4. 官替版：`./gradlew :assembleOfficialRelease`（`GUARD_WX_PKG=com.tencent.mm`）。
5. 共存版：`./gradlew :assembleCoexistRelease`（`GUARD_WX_PKG=com.tencent.mn`）。
6. LSPatch 重新打包（**`-k` 用官替 release keystore，两 flavor 共用**）：`java -jar 02_tools_工具/lspatch.jar <宿主APK> -m <对应flavor模块APK> -l 2 -k signing/guard-native-official-release.jks <storePass> guardofficial <keyPass> -o 02_tools_工具/lspatch_out -f`。密码从 `signing/keystore.properties` 读（gitignore，不进源码 / 不进文档）；或一键 `tools\lspatch_pack.ps1 -Flavor <flavor> -BuildType release -Clean -Build`。
   - 官替宿主 = 微信原版 APK（`02_tools_工具/host_official_clean_8.0.71.apk`）；共存宿主 = 改好包名的克隆 APK（`com.tencent.mn`，`02_tools_工具/mn_clean_origin_8071.apk`）。
   - **cert binding 真相（2026-06-30 F-43）**：`bindSigningCert` 读宿主整包 sourceDir（即 LSPatch `-k` 用的 keystore），**不**读模块自身——LSPatch metaloader 会重打包内嵌模块用自家 debug keystore 重签（`ca421ec3`），与发版 release `e3e13a49` 不一致 → 改读宿主后跨 LSPatch 形态都对得上。详 D-027。
   - 老话「LSPatch 外层签名不影响 registry 解密」（cert-converge 前）已**作废**：现在 LSPatch `-k` 那把 keystore 就是 cert binding 输入源、必须 = registry 派生 cert = `e3e13a49`。
   - 校验：日志 `Embedding modules - com.ghost.assist`；可拆包比对内嵌 `libguardcore.so` 哈希 = 重编模块 SO。

**C. 服务器同步（关键，别漏）**
7. `I:\miyou-server\config.py` 的 `GUARD_REL_KEYS[<release_id>].srel_b64` 换成同一新 s_rel（`secrets.local` 同步）。
8. `release_lines` 登记该线：`package_line`（hijack/coexist）、`product_version`、`s_rel_fingerprint` 等（后台「版本状态」可见、可分线管理、可独立 deprecate/kill）。
9. 部署到主+备两节点：**注意 `deploy_release_health.py` 默认只推 `server.py`/`db.py`，不推 `config.py`（s_rel 在此）**；推 `config.py` 前先逐字节核对远端基线一致、远端备份 `config.py`+`auth.db`，再覆盖、`py_compile`、重启、`/api/v1/ping` 200。
10. 同 release_id 换 s_rel = 旧装机包散沙；要「新版不影响老用户」必须用**新 release_id**（如 `android_8071_coexist`，需 flavor 专属 `GUARD_RELEASE_ID` + 独立 s_rel + 服务器加线）。

**D. 装机验证（L1，缺一不可）**
11. 冷启动看：`available=true`、`certBind=<证书前缀>`、`BATCH1_VERIFY PASS`、`role=1 MAIN`（共存版尤其要确认 role 正确，见上方包名注入坑）。
12. 心跳取回信封后看：`[hb] registry after seed ... entries=4 ... recipeOk=true`。
13. `PHASE1B~1E` 在拿到 server seed 前 FAIL/scatter 是预期（prod_server_lock）。
14. 装机若用 `am start` 起不来或崩在 LSPatch metaloader，优先让用户**桌面点开图标**（真启动），再抓日志。

### 共存版特别说明

共存版的核心原则是：**官方包负责官方身份和第三方跳转，隐私版负责隐私功能**。

**共存固定身份（8071）**：包名 `com.tencent.mn`、签名 `coexistRelease e3e13a49`（v2 已合并，与官替共用 official jks）；release_id `android_8071_coexist`。详 `docs/RELEASE_LINE_SSOT_发行线统一口径.md` §1。

注意：

- 共存包名固定 `com.tencent.mn`，首次确定后不得再改（改包名 = 新产品线）。
- 共存版签名首次确定后不得再换；换签名无法覆盖安装。
- 共存版更新只覆盖同一共存包名，不影响官方包。
- 官替版更新只覆盖 `com.tencent.mm`，不影响共存版。
- 共存版上线前必须单独验证登录、推送、聊天记录迁移、微信内支付、第三方支付跳转边界。

### 发布口径

允许对外说：

- 官替版和共存版可分开更新。
- 后续 bug 修复可在同一版本线内覆盖安装。
- 每个客户包有固定签名和固定包名，避免升级混乱。

禁止对外说：

- 禁止承诺官替版和共存版可互相覆盖。
- 禁止承诺换签名后还能无损升级。
- 禁止在服务器真锁未完成前宣称“授权无法破解”。
