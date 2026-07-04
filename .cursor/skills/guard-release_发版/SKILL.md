---
icon: 📦
cn: 发版
name: guard-release-officer
description: Guard Native 发版官（发布 / 出包 / 官替版 / 共存版 / 换 s_rel / LSPatch 注入 / miyou-server 同步 / 装机验证）。把"真锁种子轮换 + 双版本出包 + LSPatch 注入 + 服务器同步 + 装机 L1 验证"串成一条可复刻流水线，用同一套签名 / 工具 / 流程 / 服务器方式。用户说"发版 / 发布新版本 / 做官替版 / 做共存版 / 出包 / 打包 / 换种子 / s_rel 轮换"时使用本 skill。
---

# guard-release-officer — 发版官 / 双版本出包流水线

## 定位

本 skill 是 Guard Native 的**发版总指挥**，把一次完整发版串成固定流水线：

```text
（可选）换 s_rel → 出包(官替/共存 flavor) → LSPatch 重新打包进宿主 → miyou-server 同步 → 装机 L1 验证
```

> 权威细节流程在 [`docs/RELEASE_RULES.md`](../../../docs/RELEASE_RULES.md) 的「s_rel 轮换 + 双版本 LSPatch 发版工作流」。本 skill 只做角色入口 + 检查清单 + 防坑，不复制全部细节。

## 触发场景

用户提到：发版、发布新版本、出包、打包、官替版、共存版、换 s_rel / 换种子、LSPatch、改包名 release、guardWxPkg。

## 每次发版用户必须亲自提供（AI 生不了）

- **官替版**：微信原版 APK（按目标微信版本）。
- **共存版**：用户用 MT 管理器改好包名（如 `com.tencent.mn`）的克隆 APK。
- 一部可 USB 调试的手机 + 测试卡密。

## 不可变（跨版本固定，换了等于新产品线）

| 项 | 值 / 位置 |
|---|---|
| 签名 keystore | **发版用** `signing/guard-native-official-release.jks`（official key、alias=`guardofficial`，密码在 `signing/keystore.properties`，gitignore；2026-06-30 D-026 cert-converge v2 起官替+共存共用此把）。**调试 smoke 用** `signing/guard-native-debug.keystore`（guardFixed；store/key pass=android，alias=androiddebugkey；仅模块更新/公告/C2-smoke 用，cert mismatch 注定 scatter，不作发版候选） |
| 模块证书 SHA-256 | **官替 / 共存 release 均 `e3e13a49`（v2 已合并）** / debug `ca421ec3`（仅 smoke）= 本线 registry `_CERT_SHA256`（模块包必须用本线证书签，否则 registry 解不开）。详 `docs/RELEASE_LINE_SSOT_发行线统一口径.md` §1。|
| 工具 | `02_tools_工具/lspatch.jar`（JingMatrix LSPatch）、`02_tools_工具/apktool.jar`（共存改包名）|
| 真锁配方 | `release/secrets/<release_id>.json`（机密，gitignore，含 `s_rel`/`wrap_key`，禁进 git/聊天/文档）|
| W（wrap_key） | 客户端 SO `g_wk` = 服务器 `wrap_key[:16]`；换 s_rel **不动** W |

## 头号原则：复用当前框架优先（新版本 / 新平台都先复用，别另起炉灶）

不管以后出**新微信版本**还是**iOS**，默认都**复用现有框架**，只加"数据 + 配方"，不重写机制：

- **服务器**：一套 `miyou-server` 吃全部。新版本/新平台 = `products` / `release_lines` 加一行（`platform × wechat_version × schema × cert`），**零代码**（SCHEMA.md §1/§8）。授权链路 activate→token→envelope、Ed25519、卡密、风控、`s_rel/W` 机制一律不动。
- **安全模型**：真锁 = envelope + `s_rel/W` 折进 SO key + encrypted registry + Ed25519 验签。新版本只换 `registry`（hook 锚点）+ 新 `release_id` + 新 `s_rel`，模型不变。
- **客户端注入层是唯一平台特定的**：Android = LSPosed/LSPatch + `libguardcore.so`；iOS = dylib/Frida（`USAGE.md` 旧线）。这层代码不能跨平台直接复用，但**对接的是同一套服务器框架和同一套配方/授权概念**。
- **铁律**：新微信版本 = 新 `release_id` + 新 `registry` 明文源（对账活跃锚点）+ 出包，**不改服务器机制**；能加行解决的绝不加代码；能复用 flavor/发行线的绝不新建一套。

> 一句话：服务器 + 安全模型 + 发行线管理 → **强复用**；客户端注入实现 → 平台特定但插同一套框架。

## 二号原则：版本隔离（发新版本不动老客户）

- **每个版本的官替/共存各占独立 `release_id`**（如 `android_8072_hijack` / `android_8072_coexist`），各自 s_rel。客户端 `GUARD_RELEASE_ID` 按 flavor 烧死对应 id。
- **一旦某 `release_id` 有装机包在用，它的 s_rel / registry 就冻结**；要换内容 = 开**新 `release_id`**，**绝不在原线上原地改 s_rel**。
- 发新版本 = 新 `release_id`（老线置 `deprecated` 但不删）→ 老客户手机继续走老线、不受影响。这才是"安全"。
- ⚠️ **今日教训（2026-06-12，已修复）**：在**在用线** `android_8071` 上**原地轮换 s_rel**，把已装机的 v1.2 官替/共存包全打散（服务器发新 seed、客户端 registry 是另一份 → 解不开）。更糟：`config.py` 文件里的 s_rel 还漂回过旧值，一次重启就把线上带回旧 seed。**根因 = 原地改在用线 + 文件态漂移**。自测无真客户才敢这么救；正式发布后，在用线的 s_rel 一律冻结，换内容只开新 `release_id`。
- 排错口径：怀疑 s_rel 漂移时，**用测试卡激活→取信封→W 解 k→sha256[:8] 实测**线上真发的 s_rel，别只信 `release_lines.s_rel_fingerprint`（那列是登记时写死的，重启不刷新）。

## 主流程（5 步）

> 📌 **打包/装机唯一真源** = `docs/RELEASE_RULES.md`「双版本发布手册」（含共存 4 步快查 + 干净装机铁律）。本 skill 只列角色速记，命令以 RELEASE_RULES 为准、防漂移。

- **A 换 s_rel（可选）**：改 `release/secrets/<id>.json` 的 `s_rel_b64`（新随机 32B，W 不变）→ `python tools/gen_registry_cipher.py --recipe release/secrets/<id>.json` 重生成 `registry_cipher.inc`（确认 `GUARD_REGISTRY_REQUIRES_SERVER_SEED=1`）。两边指纹 `sha256(s_rel)[:8]` 对齐。
- **B 出包**（**发版候选用 Release 变体**，cert-converge v2 D-026 起强制）：`./gradlew :assembleOfficialRelease`（`com.tencent.mm`）或 `:assembleCoexistRelease`（`com.tencent.mn`）。自动跑 `checkStringLeak{Official,Coexist}Release` 硬闸（dex+SO 扫泄漏）。Debug 变体只作模块更新 / 公告 / C2-smoke、cert mismatch 注定 scatter、**不作发版候选**。
- **C LSPatch 重新打包**（**`-k` 必须用 official release keystore**，cert binding 改读宿主 sourceDir = `-k` 那把 = `e3e13a49`）：`java -jar 02_tools_工具/lspatch.jar <宿主APK> -m <对应flavor模块APK> -l 2 -k signing/guard-native-official-release.jks <storePass> guardofficial <keyPass> -o 02_tools_工具/lspatch_out -f`。密码从 `signing/keystore.properties` 读。校验日志 `Embedding modules - com.ghost.assist`，可拆包比对内嵌 `libguardcore.so` 哈希。**一键替代**：`.\tools\lspatch_pack.ps1 -Flavor <official|coexist> -BuildType release -Clean -Build`（commit `2cd643c`）。
- **D 服务器同步**（交 guard-server_服务器运维）：`config.py` 的 `GUARD_REL_KEYS[<id>].srel` 换同一新 s_rel + `release_lines` 登记（package_line/product_version）+ 部署主节点（备节点未购，`--all` 才带；详坑 3 / 服务器 skill L101）。
- **E 装机 L1 验证**：冷启动看 `available=true`、`role=1 MAIN`、`BATCH1_VERIFY PASS`；心跳后 `recipeOk=true`。日志落盘才算发布候选。**注**：装机时 logcat 若见 UI 类 `ClassNotFound`（朋友圈 `e2` / 通讯录 `MvvmContactListUI` 等），多半是**微信还没登录 / 没进对应页**（前台 activity = `LoginPasswordUI` 即未登录）—— 登录 + 打开该页即 hook 上、非缺陷；内核是否健康只看 `certBind=e3e13a49` / `role` / `recipeOk`。

## 七条坑（已实证，违反即翻车）

1. **包名注入要连进程名**：`GUARD_WX_PKG` 不能只喂 `anti_tamper` 的 `EXPECTED_PACKAGE`，还必须驱动 `guard_core.h` 的 `PROCESS_MAIN`/`PROCESS_PUSH`。漏了 → 共存版 `role=UNKNOWN` + `BATCH1 FAIL` → 不报错但不隐藏。新增"按包名分支"的 native 常量一律从 `GUARD_EXPECTED_PACKAGE` 派生。
2. **同 release_id 换 s_rel = 旧装机包散沙**。要"新版不影响老用户"必须用**新 release_id**（如 `android_8071_coexist`，需 flavor 专属 `GUARD_RELEASE_ID` + 独立 s_rel + 服务器加线）。
3. **服务器 `config.py` 不在默认部署集里**：统一入口 `python deploy.py <preset> [--go]`（默认 DRY-RUN，自带快照/auth.db 备份/py_compile/冒烟/任一红自动回滚；`--all` 才带备节点、备机未购勿加）；`standard`/`code` 集**不含 `config.py`**（s_rel 在此），推配方用 `p1c` preset 单独推 + 先核远端基线逐字节一致 + 远端备份 `config.py`+`auth.db`。旧 `deploy_release_health.py` 已 DEPRECATED。
4. **装机反复 install/卸载会出半损坏僵尸**（启动崩 LSPatch metaloader `NoClassDefFoundError` / 卸载报 `DELETE_FAILED_INTERNAL_ERROR`）→ **重启手机**清 dex/odex 状态再装。重启后仍每次崩（`ExceptionInInitializerError`→`NoClassDefFoundError`，崩到 `crashed too many times: killing`）= 代码/打包 bug，非 dex 缓存，查模块首个 static init（`Loading legacy module` 后、任何 `NCL` 日志前就崩）。
5. **改包重签固有限制**：第三方 App 调起 / 跳转微信支付会失败（微信内支付正常）。需第三方支付跳转的客户走官方包；共存版定位 = 官方管支付跳转 + 共存版管隐私。
6. **装机签名核对 + 设备状态**：
   - 装官替前 `apksigner verify --print-certs` 比对手机现装 `com.tencent.mm` cert 与官替 release cert `e3e13a49`（v2 共存 release 同 `e3e13a49`；debug smoke = `ca421ec3`）。
   - 手机 `com.tencent.mm` 若 clean 未打补丁且 cert ≠ 我方官替 cert `e3e13a49`（609b4b18 = `0fe4ff85`）= 正版官方微信，该机测不了官替（`-r` 跨签名失败、卸正版丢数据），官替验证用另一台空机；该机用共存 `com.tencent.mn` 测。
   - 「官方原版 host APK」可能已是 LSPatched（含 `assets/lspatch/origin.apk`，再 LSPatch → 0 字节）；用其 `assets/lspatch/origin.apk` 作 clean host，或用真 clean 原版（root `host_official_com.tencent.mm_8.0.71.apk` 已 patched，`_8.0.70` clean 但版本不对）。
7. **克隆宿主签名 bleed-through + LSPatch sigbypass**（F-43 / D-030，2026-07-01 实证）：LSPatch `-l 2` sigbypass 运行时返回的是**宿主原始签名**，**不是 LSPatch `-k`**。光设 -k official 不够 → 装机后 `[native] certBind` 读到的是宿主原始（克隆 `a40da80a` / 官方原版 `0fe4ff85`）≠ EXPECTED `e3e13a49` → A2 不装、registry scatter、"授权异常"。**克隆宿主必须先 `apksigner sign --ks signing/guard-native-official-release.jks` 真正重签为 e3e13a49**，再 LSPatch -k 同把 official。`apksigner verify --print-certs` 只看文件级、**不能证明运行时 cert binding**——必须 logcat 实证 `certBind=e3e13a49`。详 RELEASE_RULES 共存 5 步快查 step 0。

## 边界（不越权，交对应 skill）

- 加密配方 / 真锁 / RiskState / decrypt_config → `guard-security_网络安全官`
- miyou-server 服务端改动 / 部署 / release_lines → `guard-server_服务器运维`
- 状态机 / 授权 / 模块边界 / 过滤位置 → `guard-auth-review_授权检查官`
- build / adb / lspatch / frida / logcat 命令 → `guard-terminal_终端操作`
- 任务编排 / 看板 / 接手 → `guard-dispatch_总调度`
- 快照 / 备份 / 回退 → `guard-git_保姆`

## 收尾铁律

1. 必须装机 L1（`role=1 MAIN` + `BATCH1 PASS` + `recipeOk=true`，终端直采日志落盘）才算发布候选；用户口述只记待补。
2. 只更新当前发版 `worklog` + `docs/RELEASE_RULES.md`，不把同一结论复制到多处。
3. 对外口径：可说"v1.6 起授权闭环 + Ed25519 防伪造信封 + 当前发行线 server seed 解 registry"（当前出货 v1.7，版本真源见 `_CORE_现状真源/发版_当前真源.md §③`）；**禁说**"服务器真锁终局完成 / 授权无法破解"。
4. keystore 密码 / 私钥 / `s_rel` / `wrap_key` 原文禁进 git / 聊天 / 文档；只记 `sha256[:8]` 指纹。
5. 1.0 正式发布后：`packageName` / keystore / `customerSeed` / `release_id` 永久不可变，只递增 `versionCode`（见 RELEASE_RULES「1.0 后签名证书不可变」）。
