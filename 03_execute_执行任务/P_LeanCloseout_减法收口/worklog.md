# P_LeanCloseout 工作日志

## C5a 兜底归一（2026-06-25）

**任务**：4 个 Filter 顶部手写 `BuildConfig.DEBUG ? "x" : ""` 兜底 → 构建期从 `native_core/registry_8071.json` 自动生成；json 单一源 + 一个取值口（`RegistryFallback`）全局生效。零新增加密、粒度不变；不碰 C5b 内联 / 不碰 `.inc`·KDF / 不碰状态机·授权 / 不碰已验证 hook 回调体。

### 改动文件
- 新增 `tools/gen_registry_fallback.py`：读 `registry_8071.json` → 生成两份 `RegistryFallback.java`；文件头标“勿手改、改 json 重跑”。
- 新增（生成物）`src/debug/java/com/ghost/assist/core/RegistryFallback.java`：debug = 真值（37 常量）。
- 新增（生成物）`src/release/java/com/ghost/assist/core/RegistryFallback.java`：release = 全 ""（fail-closed）。
- `moduleD/ConvFilter.java`：3 兜底（mvvmlist_class / adapter_class / wxid_getters）。
- `moduleD/ContactFilter.java`：6 兜底。
- `moduleD/MomentsFilter.java`：8 兜底。
- `moduleB/SearchFilter.java`：5 兜底。
- `build.gradle`：sourceSets 显式声明 `src/debug/java`·`src/release/java`（additive）。
- 共 22 兜底字段归一；逐字节核对 == registry 现值。
- **未碰**：`registry_cipher.inc` / `config_crypto` KDF / 状态机 / 授权 / hook 回调体 / fbOk 自测行 / C5b 内联明文。

### 双审 + 快照
- 安全官改前审查 **PASS**；授权检查官 9 项 **PASS**（Entry/Auth/State/Risk/分层 全 ✅）。用户改前拍“是”。
- git 快照 `769829b`（动代码前存档点，可一键回退）。

### 编译（✅ L1，2026-06-25）
- `:assembleOfficialDebug`  → `BUILD SUCCESSFUL in 15s`（arm64-v8a + armeabi-v7a）。
- `:assembleOfficialRelease` → `BUILD SUCCESSFUL in 39s`（R8 minify + 双 ABI）。
- 仅 deprecated/unchecked 提示（ModuleMain / MomentsRedDotGuard 旧有，与本次无关）。

### ① 装机 debug（小米9 / 609b4b18，✅ L1 logcat 原文）
4 Filter recipes 全 `fallbackSelfTest=ok ready=true`（值 == 原手写兜底）：

```
[CF]  recipes mvvm=com.tencent.mm.plugin.mvvmlist.MvvmList adapter=kc5.v0 getters=[C0, h1, j1, i1, k1, getUsername, getUserName] fallbackSelfTest=ok ready=true
[CTF] recipes adapter=ik3.t0 item=fc5.g contact=com.tencent.mm.storage.z3 getter=c1 live=com.tencent.mm.ui.contact.address.AddressLiveList fallbackSelfTest=ok ready=true
[MF]  recipes friend=na4.b promo=la4.p adapter=e2 wxid=field_userName inner=d sns=h1 like=LikeUserList cmt=CommentUserList fallbackSelfTest=ok ready=true
[SF]  recipes gateway=fts_result_view adapterFamily=q2,f0 renderHook=getView profile=wechat8071_fts_mixed scope=result_render_only fallbackSelfTest=ok ready=true
```

### ③ release 无明文扫描（strings-on-dex 代 jadx，prod_server_lock 包，✅ L1）
- **10/15 鲜明标记 absent**：`kc5.v0` `ik3.t0` `la4.p` `na4.b` `AddressLiveList` `fts_result_view` `wechat8071_fts_mixed` `result_render_only` `q2,f0` `"C0,h1,j1,i1,k1,getUsername,getUserName"` —— 这些只在 C5a 兜底出现过，已从 release 消失。
- debug 包同标记 15/15 present（对照成立）。
- **5 个仍 present，非 C5a，属 C5b 旧账**（本轮明令不碰）：
  - `MvvmList` ← `ContactDiscoveryHook.java:459`（内联）
  - `fc5.g` / `z3` ← `ContactLabelHideGuard.java:31/35`（内联 static final）
  - `LikeUserList` / `CommentUserList` ← `MomentsFilter` 内联 `LikeUserListCount` / `CommentUserListCount`（子串命中，非兜底）
- 结论：C5a 该清的 release 明文已清；改前改后 release 中性（兜底本就 ""）。

### 设备授权状态（与 C5a 无关，记录备查）
- 开发机当前 `[auth] NO_LICENSE -- not bound yet` + `[hb] cached envelope seed=miss` → `registrySummary=scatter` → `PHASE1B~1E_VERIFY FAIL`；`BATCH1 / PHASE1A / C2_VERIFY PASS`。
- 即 `isVipAuthorized=false`（授权门关）→ 过滤关闭；开发机无授权下“密友隐藏正向不误伤”的屏幕实测未做。
- C5a 只改 Filter 类名兜底，碰不到授权/信封/解密；此未授权为开发机环境态，非本次改动引起。

### 收口口径
- **已验（L1/L2）**：① debug fallback 生效（fallbackSelfTest=ok ×4）；③ release C5a 明文已清（10 absent）；双 ABI BUILD SUCCESSFUL；release fail-closed（兜底空）。
- **已验（L1，2026-06-25 完整链）**：`recipeOk=true` · `entries=4`（conv/moments/contact/search）· `tier=0`/`risk=CLEAN`/`registry=ready` · 密友隐藏屏测 PASS（`cached envelope seed=ok`，进程 6226，log `c5a_recipeok_20260625`）。②（dev_cert_only 验 recipeOk）本轮按用户决定 **跳过**，不碰 `.inc`。
- **残留 C5b/D8**：上列 5 处内联明文（ContactDiscoveryHook / ContactLabelHideGuard / MomentsFilter `*Count`），归 C5b 后续。

### 置信度
- ✅ L1：编译、recipes fallbackSelfTest、release 扫描、装机 logcat 原文（见 `c5a_install_20260625.log`）。
- ✅ 已验（2026-06-25）：recipeOk=true 完整链 + 密友隐藏屏测（log `c5a_recipeok_20260625`）。

## C7 删8（2026-06-25）

**任务**：删 registry_8071.json 8 个挂空键（无 getRecipe 读、无 Filter 引 fallback 常量）——3 JDK名(l4_notify/2×list_addall) + 3 死/冗余键(actor_class z15.e56/actor_wxid_field/contact_class storage.l4) + 2 描述串(policy/unlock_entry)。纯减法，不动 Filter/KDF/状态机/授权/回调体。

**双审**：安全官改前+改后 PASS；授权检查官 9 项 PASS（Entry/Auth/State/Risk/分层全✅）。git 快照 `c2ddd65`。

**改动**：registry_8071.json 37→29 键；regen `gen_registry_fallback.py`(29 常量) + `gen_registry_cipher.py --recipe`(.inc 4 gateway·prod_server_lock·server seed folded)；`:assembleOfficialDebug` BUILD SUCCESSFUL 24s（双 ABI）。storage.l4 删前三证（仅注释 ConvFilter:36 + 生成常量；MomentsRedDot 的 l4 是另一类 plugin.sns.model.l4）。

**装机回归（✅ L1，小米9/609b4b18，进程 9191，log `c7del8_recipeok_20260625`）**：
- `available=true` · `role=1 MAIN` · `BATCH1_VERIFY PASS`；`cached envelope seed=ok`
- 种子前 `scatter`/`PHASE1B~1E FAIL`（预期）→ 种子后 `entries=4 [conv/moments/contact/search] recipeOk=true` · `tier=0` · `risk=CLEAN` · `registry=ready`
- **`[CF:L4] cleaned=4`** = 4 密友会话实测隐藏（hide 链无回归，强于 C5a 的 cleaned=0）

## C5a 授权全链收口 · 终端 worker 复跑（2026-06-25 09:2x，装机 L1）

> 与上节 line 54 的 recipeOk 记录（进程 6226 / `c5a_recipeok_20260625`）是**两次独立运行**：本节 = 本会话终端 worker（进程 30513 热 / 4703 冷，tier=1）。两边结论一致（recipeOk=true / entries=4 / 授权通 / 密友隐）。完整原文见 `c5a_authverify_20260625.log` + 截图 `c5a_conv_20260625.png` / `c5a_search_20260625.png`。

### 服务器侧解卡（架构师拍板：删并重建，免 SSH）— 本节独有
- 首激活被拒 `{"ok":false,"msg":"设备数量已达上限"}` (HTTP 400)。查 live zxmqq.shop：DEVTEST-3AB7ED max_devices=1 / used_count=1，唯一占槽 = 假设备 `devtest-c88`（devauth_c88.py 建卡时用假 device_id 占的），非真机。
- 走官方 `/admin/api`：`/cards/delete`（deleted cards1/activations1/devices1/events1）→ `/cards` 重建（mode=pro / release=android_8071 / expire=2027-12-31 / **max_devices=2**）。VERIFY：max_devices=2 / used_count=0。卡号不变，tmp_activate.json 仍有效。现 used_count=1（真机占 1），余 1 槽。

### 机器链 + 屏测（L1）
- `POST /api/activate` → `{"ok":true}`；`/api/native`：authorized=true / auth=AUTH_OK / recipeOk=true / entries=4 / registryAfterSeed=ok / risk=正常 / funnel=false / kill=false。
- 朋友圈面机检 L1（feed-wxids + 截图）：AA熵（wxid_lzd2va16jd16，hidden=true）UI 不渲染；6 个非密友（心静如水/臻盈电器/李小爷/叶炜山/hh Ö/晓彤）正常显示 → 隐密友 + 不误伤双向成立。会话/搜索面由架构师在机跨面亲查确认，拍板收口（终端搜“啊啊啊”截图含糊、未采信，已诚实标注）。

### PHASE1*_VERIFY 口径（L2 核码，留架构师定）
- 冷启动 PHASE1A / BATCH1 / C2 **PASS**；**PHASE1B–E FAIL = 预种子顺序**：`runNativeBridgeVerification` 在 ModuleMain step0、`applyCachedEnvelopeSeed` 在 step2，验证那刻 registry=scatter，`registrySelfTest()` 解的是“服务器种子门控”的真注册表 → 必 FAIL；随后 `seed=ok` 拉成 ready。**非 C5a 回归、非授权问题**（未授权那轮同 FAIL）。是否把验证改到 seed 之后跑 = 改码线决策（铁律27 边界）。

### 遗留 housekeeping
- `logs/` 子目录被遗留句柄锁定（读/写均 permission denied），本轮日志改写到 P 任务根目录。

## KDF 向量对账 + GUARD_DEBUG 泄漏修复（2026-06-25）

**起因**：用户问"加密设计直观/好维护/好接手吗"。盘出三处可维护性洞 → 本轮修：① registry self_test 漂移（json 已裁字段但 self_test 仍硬断言已删的 `l4_notify`/`actor_wxid_field`/`unlock_entry`）② 三端钥匙派生靠手抄镜像、无自动对账（F-31 隐患）③ `GUARD_DEBUG` 经 build.gradle 失效块泄漏进 release SO。

**提交（分支 full-restore，本地未推）**：
- `0ffd5eb` ① **registry 单一真源收口**：删 `registry_self_test()` 对已删字段的断言，actor 改查 `actor_field_names` 含 `f435583d`；脚本对账 self_test==当前 json = PASS。装机：`cached envelope seed=ok` → `registry after seed entries=4 recipeOk=true`（log `logs/sec1_selftest_install_20260625.log`）。
- `64d7af1` ② **KDF 向量自动对账**：`tools/kdf_common.py` = Python derive 单一来源（`gen_registry_cipher`/`gen_bootstrap_cipher` 改复用，删各自内联拷贝，derive 输出逐字节不变已校）；`gen_kdf_vectors.py` → `native_core/src/kdf_vectors.inc`（固定输入→期望 key）；`guard::kdf_self_test()`（`GUARD_DEV_SELFTEST` 门控，存档/还原全局）断言 C++ derive==向量 + registry≠bootstrap 域分离；接 `test_config_crypto.cpp` + `run_native_tests.ps1`（NDK→设备构建期门控）；`registry_loader.cpp` 加 `registry_requires_server_seed()` 让 server-lock 无种子时 registry 自测 SKIP；in-app `NativeBridge.kdfSelfTest` + `ModuleMain` 打 `KDF_VECTOR_VERIFY`（`BuildConfig.DEBUG` gated）。
- `850c0ac` ③ **GUARD_DEBUG 泄漏根因**：删 `build.gradle` 失效的 `cppFlags buildTypes{debug/release}` 块；`log_limiter.cpp` `#ifdef GUARD_DEBUG` → `GUARD_DEV_LOG`。Debug-only 宏统一由 `CMakeLists.txt` 按 `CMAKE_BUILD_TYPE=Debug` 定义。

**证据（L1）**：
- `run_native_tests.ps1`（NDK → 真机 609b4b18 arm64）：`kdf_self_test=PASS` / `ALL=PASS`；**负向测试**改坏 1 个向量字节 → `kdf_self_test=FAIL`，重生成还原 → `PASS`（证闸能真抓漂移、非摆设）。
- 装机：`[native] KDF_VECTOR_VERIFY PASS`（与 `PHASE1A`/`BATCH1`/`C2` 同块，log `logs/kdf_vector_verify_20260625.log`）。
- GUARD_DEBUG 泄漏实锤：修前 `.cxx/RelWithDebInfo` compile_commands 同时含 `-DGUARD_DEBUG`+`-DNDEBUG`；修后干净重编 release compile_commands `GUARD_DEBUG=0` / `GUARD_DEV_*=0`。
- **发版第 8 条 PASS**（log `guarddebug_verify_20260625.log`）：release `.so` `nativeKdfSelfTest` ABSENT、`nativeRegistrySelfTest` PRESENT（对照）；debug `.so` `nativeKdfSelfTest` PRESENT。

**未碰**：registry KDF 派生算法本身 / 状态机 / 授权 / registry 内容 / 已验证 hook 回调体；`GuardRuntime.java`、`EncryptedConfigLoader.java`（另一线 WIP）未纳入本轮任一提交。

**置信度**：✅ L1（run_native_tests 原文 + 装机 logcat 原文 + 干净重编 compile_commands + `nm -D` 符号对照）。

## C7 接6（2026-06-28）

**任务**：6 个「代码另写死一份」内联值 → 改走 registry（与 C5a 同模式：字段初值引 `RegistryFallback` + `resolveRecipes` 里 `recipe()/recipeArr()` 取值）。归一消双源，不改 json、不重生 cipher、不重编 SO（6 键本就在 json/RegistryFallback、且不在 SO 自检 field_eq）。不碰 C5b 回调体（铁律29）。

**双审**：见 `recon/C7_改前双审.md`（2026-06-25）——接6 = 干净（install 期/字段初值取值、值逐字节一致、非回调体），安全官面 PASS、授权检查官 9 项 PASS（WARN 仅针对另一块「删3 连 SO 自检」，与接6 无关）。用户改前拍「接着干 C7-接6」。git 基线 `8144a55`（目标文件改前无未提交改动，可 `git checkout --` 回滚）。

**改动文件（纯 Java，src/main 共享两 flavor）**：
- `moduleD/ContactFilter.java`：`ITEM_CONTACT_FIELD(d)`/`ITEM_TYPE_FIELD(e)` 去 final → 引 `RegistryFallback.CONTACT_ADDRESS__CONTACT_FIELD/TYPE_FIELD`；resolveRecipes 加 `recipe("contact_field"/"type_field")`。
- `moduleD/MomentsFilter.java`：`ACTOR_FIELD_NAMES` 去 final → 引 `RegistryFallback.MOMENTS_FEED__ACTOR_FIELD_NAMES` split；新增 `recipeArr` helper；resolveRecipes 加 `recipeArr("actor_field_names")`。
- `moduleD/ConvFilter.java`：① `CONTACT_FIELD_NAMES` 去 final → 引 `RegistryFallback.CONV_LIST__CONTACT_FIELDS` split + `recipeArr("contact_fields")`；② 新增 `L1_METHODS[]`/`L2_METHOD` 字段（引 `RegistryFallback.CONV_LIST__L1_METHODS/L2_METHOD`）+ `isL1Method()` helper + resolveRecipes 加 `recipeArr("l1_methods")`/`recipe("l2_method")`；install 期 4 处方法名匹配（installMvvmListHooks L1:391/L2:447、installMvvmListL3Hooks 排除:493、installMvvmConvHooks L1+L2:653/671）由内联 `"n"/"m"/"s"` 改用 `isL1Method()`/`L2_METHOD`，label `"L1"+mn`/`"CL1"+mn`。全在 install 期、非回调体。
- **未碰**：`registry_8071.json` / `.inc` / KDF / SO / 状态机 / 授权 / hook 回调体（kc5.y 缓1 不动）。

**编译**：`:compileCoexistDebugJavaWithJavac` + `:assembleOfficialDebug`/`:assembleCoexistDebug` 均 `BUILD SUCCESSFUL`（双 ABI；仅 ModuleMain 旧 deprecated 提示）。ReadLints 三文件零错。

**⚠️ 执行事故 + 根因（L1）**：首轮误建/装 **coexist** flavor（hook `com.tencent.mn` 共存克隆），用户开的是**原版 `com.tencent.mm`** → 模块未注入、logcat 零 NCL init → 密友未隐藏。根因 = 挑错 flavor（build.gradle official→com.tencent.mm / coexist→com.tencent.mn），**非 C7 代码**（代码若运行哪怕崩溃也有 NCL 日志）。修复 = 重建 **official** flavor 重装。

**装机回归（✅ L1，小米9/609b4b18，进程 29417，log `logs/c7jie6_official_recipeok_20260628.txt`）**：
- 种子前 `registrySummary=scatter`（预期 fail-closed）→ 种子后 `[hb] registry after seed ... entries=5 [conv/moments/contact/search/a2.sig] recipeOk=true`。
- 四 Filter 全 `recipes ... fallbackSelfTest=ok ready=true`（CF/MF/CTF/SF）。
- 用户屏测：会话/通讯录/朋友圈三处密友隐藏正常、无崩溃（接6 值逐字节一致，hide 链无回归）。
- 注：`entries=5`（含 a2.sig 第5块）= 当前健康值，旧 DoD 的 `entries=4` 是 a2.sig 加入前的数。

**置信度**：✅ L1（编译原文 + 装机 logcat recipeOk=true/fallbackSelfTest×4 + 用户三链屏测）。
