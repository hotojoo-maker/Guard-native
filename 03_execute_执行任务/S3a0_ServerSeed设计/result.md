# S3a-1 / v1.1 授权闭环收口结果（2026-06-11）

## 结论

当前可收口为：**v1.1 商业授权最小闭环已通 + S3a runtime seed apply 原型已接 + S4 Ed25519 验签 + S3b-A/B LeaseClock 授时/72h 强验已通**。

不宣称“服务器真锁完成”。S3a-PROD 硬失败、V3 证书/包名发版形态对齐、删 Filter fallback、RiskState 真正散沙降级仍是后续项。

## 已完成

- 服务器 `I:\miyou-server`：
  - `/api/v1/activate`：授权码 -> token。
  - `/api/v1/guard/envelope`：token -> signed envelope。
  - envelope payload 包含 `pv`（量子密友版本）、`w`（微信版本）、`z`（schema）、`k/n`（S3a seed wrap）、`up`（更新通知）。
  - 后台支持卡密/设备封停、渠道/代理、release 维度、更新通知定向下发。
- Android 客户端：
  - `StateMachine.isVipAuthorized()` 改为 `EnvelopeStore.isAuthorizedNow()`。
  - 无有效 token/envelope/license 时 Filter 放行，功能等于没装。
  - 设置页未授权时主动弹授权输入；授权异常只在设置授权界面显示。
  - 授权成功后立即重建设置页，密友开关可用。
  - 更新通知 `up` 已消费；带链接时弹自绘卡片并用浏览器打开。
  - 设置子页面不再误显示量子密友入口。
  - 冷启动遮罩只在“已授权 + 开启密友 + HIDDEN + 真冷启动窗口”显示。

## 验证

- Android：
  - `ReadLints`：通过。
  - `.\gradlew assembleDebug`：`BUILD SUCCESSFUL`。
  - `adb install -r build\outputs\apk\debug\guard-native-debug.apk`：`Success`。
- 服务器：
  - `python -m py_compile server.py db.py crypto_utils.py config.py`：通过。
  - 主节点 `https://zxmqq.shop/api/v1/ping`：200。
  - 备节点 `http://45.158.230.216:8080/api/v1/ping`：200。
  - envelope sanity 曾验证：`k_len=48`、`n_len=16`、`z=r8071_v1`、`w=8.0.71`、`pv=v1.1`。
- 用户现场回报：
  - 授权闭环测试 OK。
  - 设置子页面入口误显示已修复。
  - 冷启动遮罩误触发已修复。
  - 更新通知弹窗已生效。

## 追加：客户端 S3a 线上配方验证（2026-06-11 23:35）

- 已正式生成 `native_core/src/registry_cipher.inc`，生成端使用 `release/secrets/android_8071.json`（本地机密，gitignore，不入库）。
- 生成结果确认：`GUARD_REGISTRY_REQUIRES_SERVER_SEED=1`，即无服务器 seed 时 registry 必须 scatter。
- 装机后早期 `registrySummary=scatter` / `PHASE1B/C/D/E FAIL` 是预期：自检发生在 heartbeat 取回服务器 seed 之前。
- heartbeat 取回线上 envelope 后，客户端成功 unwrap server seed，并解开 registry：

```text
[hb] registry after seed summary=schema=r8071_v1 ver=8.0.71 entries=4 ... recipeOk=true
[hb] synced tier=2 lease=1781192954 risk=正常
```

- 隐藏链同步正常：

```text
[CF:focus] LauncherUI focus HIDDEN → clean+notify
[CF:filter] ... active=true
[CF:L4] entry adapter=v0 cleaning=false state=H
```

- 非密钥状态可记录：`release_id=android_8071`、`kid=e1`、`alg=Ed25519`、`schema_id=r8071_v1`、`wechat_version=8.0.71`、`k_len=48`、`n_len=16`、`s_rel_sha256_8=64f599e0`。
- 禁止记录：`s_rel_b64`、`wrap_key_b64`、服务器私钥、token、envelope 原文。

## 测试卡密

```text
TEST-ACTIVE-001      正常可激活，到期时间 2026-06-18
TEST-EXPIRED-001     已过期
TEST-BANNED-001      卡密封停
TEST-DEVICE-BAN-001  首次激活成功，后台封设备后 envelope 失败
```

## 后续

1. S3a-PROD：在 V3 证书源 / 包名注入 / 本地 AI 发版步骤对齐后，再谈硬失败 + 删 fallback。
2. RiskState：当前仍偏 record-only，后续再接真正散沙降级与风险恢复闭环。
3. 备机：`miyou.lol` HTTPS 反代完成后，再打开客户端 backup。
4. 运营：下载包托管、强制更新、灰度规则。

## 追加：s_rel 轮换（2026-06-12）

- 触发：v1.2 自测阶段轮换服务器短命种子 S_rel（用户决策；从未正式发布，无 1.0 不可逆约束）。
- 操作：
  - 新随机 32B s_rel 写入 `release/secrets/android_8071.json`（gitignore，未入库）；`wrap_key`/`release_id`/`ed25519_public` 等其余字段不变。
  - `wrap_key_b64` 经校验等于 SO `config_crypto.cpp` 的 W（`g_wk_lo`+`g_wk_hi`），不会因 W 不一致而散沙。
  - `python tools/gen_registry_cipher.py --recipe release/secrets/android_8071.json` 重生成 `native_core/src/registry_cipher.inc`：`GUARD_REGISTRY_REQUIRES_SERVER_SEED=1`、4 条 registry（conv/moments/contact/search）、新随机 nonce。
  - `.\gradlew :assembleOfficialDebug` 重编 SO（arm64-v8a + armeabi-v7a）+ 官替版 debug APK，`BUILD SUCCESSFUL`；产物 `build/outputs/apk/official/debug/guard-native-official-debug.apk`。
- 非密钥可记录：`s_rel_sha256_8=7255096e`（旧 `64f599e0` 作废）、`release_id=android_8071`、`registry_mode=prod_server_lock`、`kid=e1`、`alg=Ed25519`。
- 禁止记录：`s_rel_b64`、`wrap_key_b64`（原文仅在本地 secrets 文件）。
- 服务器（2026-06-12 已部署）：miyou-server 主(zxmqq.shop) + 备(miyou.lol) `GUARD_REL_KEYS[android_8071].srel` 已换同一新 s_rel（服务器自算指纹 `7255096e`），`release_lines` 登记 `package_line=hijack` / `product_version=v1.2` / `admin_note=1.2官替`；两节点 py_compile + 重启 + `/api/v1/ping` 200；改前远端已备份（`/root/miyou-server-backup-srel-*`）。安全做法：仅当远端 `config.py` 与本地基线逐字节一致才覆盖。
- ✅ L1 装机验证（2026-06-12 12:06，官替版覆盖安装，设备 609b4b18，新进程 PID 12777）：冷启动 `[native] certBind=ca421ec3`、`available=true`、`BATCH1_VERIFY PASS`、`PHASE1A_VERIFY PASS`；`PHASE1B~1E_VERIFY FAIL` + `summary=scatter` 为 prod_server_lock 预期（自检在心跳取回 seed 之前）；心跳取回新信封后 `[hb] registry after seed summary=schema=r8071_v1 ver=8.0.71 entries=4 ... recipeOk=true`、`tier=1`、`risk=CLEAN`、`registry=ready`。端到端（新 s_rel SO + 新服务器 seed）打通。证据：`s3a_srel_coldstart_20260612.txt`。

## 追加：共存版打包（2026-06-12）

- 宿主：用户在小米机用 MT 克隆出的 `com.tencent.mn` 包 `wechat_8071_original_clone.apk`（aapt2 验包名=`com.tencent.mn`），adb pull 到 `02_tools_工具/`。
- 模块：`assembleCoexistDebug` 重编（含新 s_rel + `GUARD_WX_PKG=com.tencent.mn`），产物 `build/outputs/apk/coexist/debug/guard-native-coexist-debug.apk`。
- 注入：`lspatch.jar <clone.apk> -m <coexist模块> -l 2 -k guard-native-debug.keystore ... -o lspatch_out -f`。产物 `02_tools_工具/lspatch_out/wechat_8071_original_clone-439-lspatched.apk`（约 251MB）。
- 校验：`Embedding modules - com.ghost.assist` + `Done`；内嵌 SO 与重编 coexist 模块 SO 哈希一致（sha256[:16]=`aeeeec0b5c6d47bf`，区别于官替 `3e2ccd2b`，因 `GUARD_EXPECTED_PACKAGE` 不同）。
- ⚠️ 当前共存版仍走**共享发行线 `android_8071`**（`AppConfig.GUARD_RELEASE_ID` 全局），与官替共用同一 s_rel/registry_cipher：可激活、可解、可隐藏，但版本列表里不单列、且 s_rel 轮换会同时影响官替。真正独立线（`android_8071_coexist` + 独立 s_rel + flavor 专属 release_id + 服务器加线）属后续 V3。
- 共存版 = 包名 `com.tencent.mn`，与官方微信 `com.tencent.mm` 并存（装它不用卸官方）；第三方支付跳转仍走官方微信，隐私功能走共存版。

### 共存版进程角色 bug + 修复（2026-06-12，L1 捕获）

- 装机冷启动实证（`com.tencent.mn` PID 15942，`s3a_coexist_coldstart_*.txt`）：`available=true`、`certBind=ca421ec3`、`PHASE1A PASS` 都对，但 **`role=0 (expect 1=MAIN)` + `BATCH1_VERIFY FAIL`**，:push 同样 `role=0(expect 2)`。后果：进程角色非 MAIN → 业务 hook 不安装 → 不报错但不隐藏。
- 根因（L2）：`guard_core.h` 的 `PROCESS_MAIN`/`PROCESS_PUSH` 写死 `com.tencent.mm`，没跟随构建注入的 `GUARD_EXPECTED_PACKAGE`（`EXPECTED_PACKAGE` 跟了，这两个漏了）。共存包名 `com.tencent.mn` 对不上 → UNKNOWN。
- 修复：`PROCESS_MAIN = GUARD_EXPECTED_PACKAGE`、`PROCESS_PUSH = GUARD_EXPECTED_PACKAGE ":push"`（官替版值不变=零影响）。已重编 coexist（模块 SO `aeeeec0b`→`f721850a`）+ 重 LSPatch。发版铁律已写入 `docs/RELEASE_RULES.md`（包名注入坑 + s_rel 轮换/双版本工作流）。
- ✅ 修复验证（2026-06-12 13:14，重启后干净安装，com.tencent.mn PID 12250）：`certBind=ca421ec3`、`available=true`、`init=true`、**`role=1 (expect 1=MAIN)`**、**`BATCH1_VERIFY PASS`**（修复前是 role=0 / BATCH1 FAIL）。`PHASE1B~1E FAIL`+`scatter` 因共存版尚未激活（无 token→无信封→无 seed），与官替版激活前同理；激活后取回信封即 `recipeOk=true`。证据：`s3a_coexist_fixed_20260612.txt`。
- 插曲（已解）：修复版反复 `install -r`/卸载十余次后，`com.tencent.mn` 变半损坏僵尸（启动崩 LSPatch metaloader `NoClassDefFoundError`、卸载报 `DELETE_FAILED_INTERNAL_ERROR`）→ **重启手机**清掉、干净安装即恢复。教训：装机反复折腾后若崩在 LSPatch 加载器，先重启清 dex/odex 状态，别误判为代码/兼容问题（同把 `lspatch.jar` 的官替版 `com.tencent.mm` 一直正常 = 工具与克隆包本身兼容）。

## 追加：官替版 LSPatch 打包（2026-06-12）

- 输入：`官方原版8.0.71-2026-5-19.apk`（微信 8.0.71 原版）+ 模块 `build/outputs/apk/official/debug/guard-native-official-debug.apk`（官替 flavor，含新 s_rel SO）。
- 命令（V3-T1 实证流程，JingMatrix LSPatch）：`java -jar 02_tools_工具/lspatch.jar <wx.apk> -m <module.apk> -l 2 -k signing/guard-native-debug.keystore android androiddebugkey android -o 02_tools_工具/lspatch_out -f -v`。
- 产物：`02_tools_工具/lspatch_out/官方原版8.0.71-2026-5-19-439-lspatched.apk`（约 250MB，git 忽略）。
- 校验：LSPatch 日志 `Embedding modules - com.ghost.assist` + `Done`；内嵌 `assets/lspatch/modules/com.ghost.assist.apk` 的 `libguardcore.so` 与新编模块 SO 哈希一致（sha256[:16]=`3e2ccd2be1202a40`）。
- 证书绑定：`bindSigningCert()` 读**模块自身**签名（guardFixed=`ca421ec3...`=registry `_CERT_SHA256`），LSPatch 外层签名不影响 registry 解密。
- 固有限制（V3-T1 实证）：改包重签 → 第三方 App 调起/跳转微信支付校验签名失败（微信内支付可用）；需第三方支付跳转的客户走共存版。
- 仍待：服务器切新 s_rel 后装机抓 `recipeOk=true`；本包签名非腾讯，安装前需卸载官方微信。

## 追加：s_rel 线上回退事故 + 修复 + 版本隔离原则（2026-06-12）

- **事故**：加共存独立发行线时，用测试卡向主节点取真信封、W 解 k 实测，发现**线上当时发的是旧 s_rel `64f599e0`**（应为新 `7255096e`）。已装机的 v1.2 官替/共存包是新 s_rel → 拿到旧信封 → registry 解不开 → 散沙。
- **根因（L2）**：`config.py` 的 `GUARD_REL_KEYS[android_8071].srel` 漂回旧值（本地+远端都旧），13:53 那次发卡密格式部署重启把旧 seed 带上线；`release_lines.s_rel_fingerprint` 那列是登记时写死、重启不刷新，所以一度误判还是新的。
- **修复**：以 `release/secrets/android_8071.json`（客户端真配方 = 新 s_rel 权威源）为准，把 `config.py` 的 android_8071 s_rel 复位为新 `7255096e`，新增 `android_8071_coexist` 线（先复用同一新 s_rel + 同 W）；部署主/备 + 重启 + **信封实测确认线上 s_rel=`7255096e`**。后台版本状态现两行：`android_8071`(hijack) + `android_8071_coexist`(coexist)，均 v1.2/enabled/7255096e。
- 客户端：服务端已新增 `android_8071_coexist` 线，但客户端 `GUARD_RELEASE_ID` 尚未改成 flavor 专属；当前代码仍硬编码 `android_8071`，因此共存包仍会上报 `android_8071`。要真正走 `android_8071_coexist`，需先把 `GUARD_RELEASE_ID` 改成 BuildConfig/flavor 注入，再重编 coexist + 重装。
- **版本隔离原则（用户拍板）**：每版官替/共存各占独立 release_id、各自 s_rel；在用线 s_rel 冻结，换内容只开新 release_id；发新版本不动老客户。已写入 `guard-release_发版` skill「二号原则」。排错：怀疑 s_rel 漂移用信封实测，别只信登记列。

## 追加：共存触发器/隐藏失效复盘 + 服务器风控误伤（2026-06-12 下午）

> 触发：用户报共存版"很多功能（触发器/点击自动返回）不工作"，官替版完美。L1 现场（`s3a_coexist_vivo_trigdiag_20260612.txt`，vivo `com.tencent.mn` PID 6734）逐条证伪了"代码回归 / LSPatch / install 链中断"等猜测，定位到**两个独立问题**，别混为一谈。

**问题 A — 隐藏不工作（recipeOk=false）= 服务器发 decoy 假种子**
- L1：`role=1 MAIN` / `BATCH1 PASS` / `[TG] install done` / `[init] ready` 全正常，但 `[hb] synced tier=3` + `[hb] registry after seed recipeOk=false`。
- 根因：一下午反复 `install -r`（小米 + vivo）把 vivo 这台 guard 设备（`bddb06b2…`）的 `risk_score` 顶到 255 ≥ honey(110) → `tier=3` → `crypto_utils` 发随机垃圾种子 → registry scatter。**是自家防盗版蜜罐误伤测试机**，不是配方/包/LSPatch 坏。详见 `FAILURE_LOG` F-40。
- 后台「标记正常」只改 `device_status`，不动 `tier/risk` → 仍发 decoy（`FAILURE_LOG` F-41）。
- 修法：服务端 `_reset_device_risk()`（激活即清）+ 每日重装宽限 5 次（`REINSTALL_FREE_PER_DAY`，2026-06-12 已落 `db.py` 待部署）；存量高 risk 设备需重新激活或 admin 重置（255×0.85ⁿ 自愈很慢）。

**问题 B — 点搜索/设置秒弹回 = ContactLabelHideGuard 误 finish**
- `getMethod("onResume")` 命中父类 `MMActivity.onResume` → 误 finish `FTSMainUI`/`MainSettingsUI` → "点搜索/设置秒弹回、密友/密群加不进"。已加真实类名 whitelist 修复（`ContactLabelHideGuard.java` 17:08，重编 17:09，重 LSPatch 17:09）。详见 `FAILURE_LOG` F-39。

**服务器现状核查（L1，2026-06-12 下午）**
- 版本列表 = 两条独立发行线：`android_8071`(hijack) + `android_8071_coexist`(coexist)，均 v1.2 / s_rel `7255096e` / enabled。→ 服务器侧确为两个版本。
- ⚠️ **客户端 drift**：`AppConfig.GUARD_RELEASE_ID` 实际仍**硬编码 `android_8071`**。故当前 coexist 客户端仍上报 `android_8071`，未真正走 `android_8071_coexist` 线。要分线需把 `GUARD_RELEASE_ID` 改成 flavor 专属并重编。
- **"旧配方不自动覆盖删除"核查（L2）**：`release_lines` 全程**无 DELETE**；init 为 `INSERT OR IGNORE` + `COALESCE(NULLIF())` 只填空；`update_release_policy` 按单 `release_id` 更新、只改提交字段、不碰其它线。→ **符合"旧版本不自动删 / 不自动覆盖"**。唯一覆盖面是**手动**对在用 `release_id` 重提 s_rel 或改 config.py `GUARD_REL_KEYS`（今日 s_rel事故即此），代码层无"在用线冻结"护栏，靠版本隔离原则人守。

**发版门控补充**：客户发行包必须用 release flavor（非 debug——debug 会起 DebugServer:8080 暴露 `/api/hidden`），且需在 `tier≤1 + 有效卡` 的干净设备上抓到 `recipeOk=true` 的 L1 才算验收。

## 追加：v1.3 双版本发版档案（2026-06-12，磁盘哈希已核 L2）

> 当前在用版本登记，下次接手以此分清官替/共存。`versionCode 13` / `versionName 1.3` / `GUARD_PRODUCT_VERSION v1.3`（build.gradle 已核）。两个包都内嵌 `com.ghost.assist` 模块，含本轮两处修复：CLH 误 finish 搜索/设置页（F-39）+ 选人器/伪装定位目标包跟随宿主（ContactImportGuard/FakeLocation 用 `BuildConfig.GUARD_WX_PKG`）。

| 版本 | 宿主包 | 产物 | 大小 | 内嵌 libguardcore.so sha256[:16] |
|------|--------|------|------|-----------------------------------|
| 官替 | `com.tencent.mm` | `02_tools_工具/lspatch_out/官方原版8.0.71-2026-5-19-439-lspatched.apk` | 249.7 MB | arm64=`2dc48e7205ef71f8` / v7a=`de0f18e34ff4bd55` |
| 共存 | `com.tencent.mn` | `02_tools_工具/lspatch_out/wechat_8071_original_clone-439-lspatched.apk` | 250.8 MB | arm64=`f721850a9f406ff2` / v7a=`b79c694ee475d79c` |

- 两包 SO 哈希不同是**预期**：`GUARD_EXPECTED_PACKAGE` 不同（mm vs mn），同源码不同注入包名 → 不同 SO。两包内嵌模块 apk 均 3.65 MB。
- 服务器发行线：官替→`android_8071`、共存→`android_8071_coexist`（均 v1.2 登记 / s_rel `7255096e` / enabled）。⚠️ 客户端 `GUARD_RELEASE_ID` 仍硬编码 `android_8071`，故共存包实报 hijack 线（见上方 drift 说明）；真分线需改 flavor 专属并重编。
- ⚠️ 仍待验收：① 在 `tier≤1 + 有效卡` 干净设备抓 `recipeOk=true` L1（之前唯一一次是 tier=3 decoy，recipeOk=false，不算数）；② 客户正式发行应切 release flavor（当前两包内嵌的是 debug 模块，会起 DebugServer:8080）。

## 追加：v1.3 双版本出包 + 服务器风控修复部署上线（2026-06-12 傍晚）

> 接上一节 F-39/40/41：上节记“待部署”，本节为**已部署上线 + 追加修复 + v1.3 出包**的收口。服务器三次改动均 py_compile 通过、`deploy_release_health.py` 部署主(zxmqq.shop/45.207.206.55)+备(45.158.230.216)、两边 `/api/v1/ping` 200、改前远端备份 `miyou-server-backup-release-health-*`。

**服务器风控修复（均已上线）**
- `_reset_device_risk()` 激活即清风险：`activate_card` 新激活 + 重复激活两路径绑定设备后清 `risk_score/tier_code/reinstall_count/churn/shadow`。授权用户随便重装不再触蜜罐。
- 每日重装宽限 `REINSTALL_FREE_PER_DAY=5`：`evaluate_guard_device` 同一天前 5 次重装(install_id 变)不加分(标 `reinstall_grace`)，超出才罚。新增 `reinstall_day/reinstall_today` 列(`_ensure_column` 幂等迁移，`init_db` 启动即建)。
- **同机双版本不互 churn**：官替 `com.tencent.mm` + 共存 `com.tencent.mn` 同签名 → 同 ANDROID_ID → 同 `device_id`(`AuthManager.computeDeviceHash=SHA256(ANDROID_ID)[:8]`) → **共用一条 `guard_device_state` 行**；两版本心跳让 install_id/token 来回翻被误判 churn → 双双 decoy。修法：仅当 `release_id` 与行内相同才计 reinstall/token churn，跨线翻转视为版本共存不罚(`same_line` 守卫)。
- **「恢复正常」真清风险(修 F-41)**：`update_device_status(status='normal')` 现调 `_reset_device_risk`；之前只改 `device_status` 标记、下次心跳按旧 risk 重算又 decoy。
- **后台「版本状态」展开列异常设备**：`server.py` release 详情「展开真锁/异常设备」除真锁指纹外，拉 `/health/devices?release_id=` 列本线 tier≥2/decoy/失败 设备，每台带「恢复正常(清风险)」「封设备」。解决“只给‘1 版本异常’干数字、看不到是哪台”。

**客户端「跟随宿主包」修复（本会话）**
- `ContactImportGuard.java`(选人器 `SelectContactUI` + 选群器 `GroupCardSelectUI`) + `FakeLocation.java`(位置选点 `RedirectUI`)：`WECHAT_PKG` 由写死 `"com.tencent.mm"` 改为 `BuildConfig.GUARD_WX_PKG`。共存版跨包拉官方微信界面被安卓拦(SecurityException) → 加不进密友/密群、伪装定位拉不起；改后跟随宿主包。
- `ContactLabelHideGuard.java`：onResume 真实类名 whitelist(F-39)。
- native 包名身份全程走 `GUARD_EXPECTED_PACKAGE`(`-DGUARD_WX_PKG`)，无写死；registry 里 `com.tencent.mm.*` 是类名(克隆包一致，勿动)。

**v1.3 出包**
- `build.gradle`：versionCode 12→13、versionName 1.2→1.3、`GUARD_PRODUCT_VERSION` v1.2→v1.3。
- 两变体 `:assembleOfficialDebug` + `:assembleCoexistDebug` BUILD SUCCESSFUL，各自 LSPatch：
  - 共存 host=`02_tools_工具/wechat_8071_original_clone.apk` → `lspatch_out/wechat_8071_original_clone-439-lspatched.apk`（内嵌 SO arm64 `f721850a9f406ff2`）。
  - 官替 host=`官方原版8.0.71-2026-5-19.apk` → `lspatch_out/官方原版8.0.71-2026-5-19-439-lspatched.apk`（内嵌 SO arm64 `2dc48e7205ef71f8`）。
  - 两 SO 哈希不同因 `GUARD_EXPECTED_PACKAGE` 不同(mm vs mn)，符合预期。
- 后台 `release_lines.product_version` 主+备已刷 v1.3（`android_8071` + `android_8071_coexist` 均 v1.3、保留 enabled，未删未停用——遵“老版本手动删、不自动替换”）。

**仍待 / 口径**
- v1.3 **L1 验收未抓**：需 `tier≤1 + 有效卡(TEST-ACTIVE-001)` 干净设备装 v1.3 + 激活，抓 `recipeOk=true` + 隐藏/搜索/设置/选人器全过才算发布候选；当前仅本机口述 + 代码/部署证据。
- 装机按用户规矩：先手动卸旧版再装 v1.3，不靠覆盖。
- `GUARD_RELEASE_ID` flavor 专属仍未落地(上节 drift 未变)：coexist 客户端仍上报 `android_8071`，未真正走 `android_8071_coexist` 线。
