# P1C Registry 加密 worklog

## 目标

- Phase 1C：把 Phase 1B 的**明文 registry** 切成 **AES-GCM 加密 registry**。
- 单一源 = `native_core/registry_8071.json`；密文由脚本生成，消除「明文 + 手工内嵌」双份漂移。
- 解不开散沙（不全开不崩）；仍**不接管** Filter 过滤逻辑、不接服务器、不动已验证 hook。

## 改前审查（安全与加密官）— PASS

1. 触碰授权根锁：否（`auth_engine.cpp` 不动）。
2. 触碰 SO 解密：复用 Phase 1A 已验证 `decrypt_config()`，**不改 crypto**，仅把 registry 输入从明文换成密文。
3. 触碰服务器 envelope：否（1C 仍本地；1D 才接服务器）。
4. 触碰 LeaseClock / RiskState：否。
5. 可能误杀正版：否（解不开走散沙）。
6. 影响已验证 hook：否（Filter 回调体不动，红线 29 / F-31）。
7. 模块边界：registry_loader 仍独立模块，未接 ConvFilter。

> 约束承接 Phase 1B 授权检查官 3 条：独立模块 / 失败散沙 / 不碰 AUTH_*·RISK_*，本轮全部保持。

## 实现

- `tools/gen_registry_cipher.py`（新增）：读 `registry_8071.json` → 剥离 `_` 文档键 + minify → AES-GCM 加密 → 生成 `native_core/src/registry_cipher.inc`（`kRegistryKey/Nonce/Cipher/Tag`）。
  - **1C key/nonce 是本地占位**（脚本内常量，已标 TODO）；Phase 1D 折入服务器派生材料（skill Key 来源准则：服务器材料必须参与最终 key 派生）。
- `native_core/src/registry_cipher.inc`（生成产物）：pt 1328B → AES-GCM 密文 + tag；4 条 entries（conv/moments/contact/search）。
- `native_core/src/registry_loader.cpp`：
  - 删除明文 `kEmbeddedRegistry` 常量，改 `#include "registry_cipher.inc"`。
  - `registry_load_embedded()` 改走 `decrypt_config(key,nonce,cipher,tag)` → 成功则 `registry_parse(plaintext)`，失败 → `make_scatter()`。
  - `registry_self_test()` 新增加密路径篡改自测：翻转一字节密文 → GCM tag 失败 → 无 entries 泄漏。

## 改后审查（安全与加密官）— PASS

1. encrypted_config 解密失败是否散沙而非全开：是——`decrypt_config` 失败 → `make_scatter(ok=false)`；篡改密文自测覆盖。
2. 签名/完整性：AES-GCM tag 校验，改 1 字节即失败（1A 已证 + 1C 篡改自测）。
3. 没清用户数据 / 没破坏微信本体：是（C++ 纯解密+解析）。
4. 仍独立模块、不接业务：是。
5. 漂移：明文常量已删，密文为脚本生成产物，源唯一 = registry_8071.json。

## 验证

- `python tools/gen_registry_cipher.py`：wrote registry_cipher.inc，pt_len 1328 / ct_len 1328，entries=[conv.list, moments.feed, contact.address, search.fts]。
- `.\gradlew.bat assembleDebug`：BUILD SUCCESSFUL（arm64-v8a + armeabi-v7a）。
- `ReadLints`：`registry_loader.cpp` 无 linter errors。
- 装机：`adb install -r` Success（固定签名、覆盖、未卸旧包）。
- 装机自测（主进程 pid 12289，2026-06-08 11:23:41）：
  - `registrySelfTest=true`（含篡改密文 → 散沙自测）
  - `registrySummary=schema=r8071_v1 ver=8.0.71 entries=4 [conv.list adapter=kc5.v0 l4=notifyDataSetChanged] [moments.feed adapter=e2] [contact.address adapter=ik3.t0] [search.fts]`
  - `PHASE1B_VERIFY PASS`
  - 无回归：`PHASE1A_VERIFY PASS` 仍 PASS。
- 证据落盘（终端直采，G6 满足）：`03_execute_执行任务/P1C_Registry加密/logs/phase1c_encrypted_registry_20260608.log`。

## 未执行 / 已知约束

- 1C key/nonce 为本地占位常量，**非真锁**；Frida hook `decrypt_config` 出参仍可拿到 registry（属高级威胁，1D 短命租约 + 服务器材料 + 设备绑定才是防线）。
- registry **未接管** Filter 过滤逻辑——Filter 仍各自硬编码类名；让 Filter 读 registry 是后续步骤。
- `registry_cipher.inc` 为生成产物；改 `registry_8071.json` 后必须重跑 `gen_registry_cipher.py`。

## 补记：搜索入口语义收敛（2026-06-08）

- 动机：搜索是巨型过滤器（好友/最近联系人/群聊/聊天记录），不应把每个结果类拆成独立能力。registry 只表达「搜索结果渲染网关 + 提取策略」。
- 改动：`entries.search.fts`（bind_adapter/contact_item/chatroom_item 等细碎类名）→ `entries.search.gateway`（粗粒度）：
  - `gateway=fts_result_view`、`adapter_family=q2,f0`、`render_hook=getView`
  - `policy=hide_if_target_in_hidden_union`、`extractor_profile=wechat8071_fts_mixed`
  - `scope=result_render_only`、`unlock_entry=excluded_search_unlock_111111`
- `registry_self_test()` 改查 search.gateway（gateway / render_hook / extractor_profile / unlock_entry / adapter_family 含 q2+f0）。
- `ModuleMain.java` 补 `PHASE1C_VERIFY`（复用 `registrySelfTest` && summary 含 search.gateway），未删 `PHASE1B_VERIFY`。
- 边界：未动 `SearchUnlock.java`（111111 不受影响，EntryGate 与搜索网关显式分离）、未让 `SearchFilter.java` 读 registry、未动 StateMachine/AuthManager/DebugServer/已验证 hook。
- 验证：`gen_registry_cipher.py` 重生密文（pt 1322B）；`assembleDebug` BUILD SUCCESSFUL；装机 `PHASE1A/1B/1C_VERIFY PASS`（pid 14775，11:47:42）。
- 证据落盘：`03_execute_执行任务/P1C_Registry加密/logs/phase1c_search_gateway_20260608.log`。

## 下一步

- Phase 1D：接 miyou-server signed envelope，key 派生折入服务器短命材料 + device/customer 绑定，把 1C 的本地占位 key 升级为真锁；解不开散沙。
- 之后：LeaseClock + RiskState（断网宽限 / 时间异常 / 蜜罐影子期 / 打开即弹）。
- 再后：让 Filter 链从 `GuardRuntime.getActiveRegistry()` 读配方，替换硬编码类名（需授权检查官审「配方注入入口」）。

## Phase 1D-local：派生 key（去明文 key 常量）— 2026-06-08

- 动机：1C 的 key 是 `registry_cipher.inc` 里明文数组 `kRegistryKey`（"reg_1c_key_!"），就贴在密文旁边，dump .so / IDA 一眼可拿，直接解。这是 1C 留下的最大静态洞。
- 改前审查（安全与加密官）— PASS：仅改 key 来源，不改 AES-GCM 算法本体；不碰授权根锁 / 服务器 envelope / LeaseClock / RiskState / 已验证 hook；不需授权检查官共审（不碰 NativeBridge/StateMachine/AuthManager）。
- 改动：
  - `native_core/src/config_crypto.cpp`：新增 `derive_registry_key()`——3 段散装常量（seg_a/b/c）+ `rotl8`/异或/加位非线性混合，运行时派生 16 字节 key。
  - `native_core/src/registry_loader.cpp`：`registry_load_embedded()` 改调 `derive_registry_key()`；`registry_self_test()` 新增「错 key 必须散沙」自测（翻 1 bit → GCM tag 失败 → 零 entries）。
  - `tools/gen_registry_cipher.py`：镜像同一 `derive_registry_key()`（必须与 C++ 逐字节一致）；nonce 改 `os.urandom(12)` 每次构建随机，消除 GCM nonce 复用。
  - `native_core/src/registry_cipher.inc`：重生成产物，**已去掉 `kRegistryKey`**，只剩 nonce/cipher/tag。
  - `native_core/include/guard_core.h`：新增 `derive_registry_key()` 声明 + GUARD-TRAP 注释。
- 验证：`gen_registry_cipher.py` 重生（pt_len 1322，entries=4 含 search.gateway）；`assembleDebug` BUILD SUCCESSFUL（arm64-v8a + armeabi-v7a 重编 CMake）；ReadLints 无错。
- 装机自测（设备 609b4b18，主进程 pid 18239，14:19:10）：`decryptSelfTest=true` / `registrySelfTest=true`（含错 key 散沙）/ `registrySummary=schema=r8071_v1 ver=8.0.71 entries=4 [...] [search.gateway]` / `PHASE1A_VERIFY PASS` / `PHASE1B_VERIFY PASS` / `PHASE1C_VERIFY PASS` 全部不回归。
- 证据落盘（终端直采，G6 满足）：`03_execute_执行任务/P1C_Registry加密/logs/phase1d_local_derivedkey_20260608.log`。
- 边界（诚实）：仍本地、非真锁——Frida hook `decrypt_config` 出参依旧能拿到 registry（高级威胁）；设备绑定 + 服务器短命钥匙 = Phase 1D-server。防重打包（包签名 cert hash 参与 key 派生）= A-step2，未做。
- 质检轻档自审：4 项全绿（铁律 / 自洽 / 编译 / 路径），判定通过。

## A-step2：签名证书绑定（防重打包）— 2026-06-08

- 授权检查官框架预审 PASS（门控涉及 RiskGate + 配方钥匙层；懒加载架构；不碰 Filter 回调体 / StateMachine 三态 / AuthGate / isVipAuthorized stub）。
- 动机：A-step1 派生 key 仍是纯本地常量推导，重签名/重打包的包照样能解。绑定模块签名证书 → 别人重签 = 证书变 = key 算错 = registry 散沙。
- 改动：
  - `config_crypto.cpp`：新增 `set_binding_material()`；`derive_registry_key()` 折入绑定材料（cert SHA-256）。
  - `guard_core.cpp`：新增 `nativeSetBindingMaterial(byte[])` JNI（复用 `jbytes`）。
  - `core/NativeBridge.java`：新增 `setBindingMaterial(byte[])` 包装 + native 声明。
  - `ModuleMain.java`：`bindSigningCert(app)` 在 native 验证前读模块自身 APK 签名证书 SHA-256 推下；新增 `PHASE1D_VERIFY`；`initZygote` 存 `sModulePath`。
  - `gen_registry_cipher.py`：烤入固定 keystore 的 cert SHA-256（`_CERT_SHA256`），镜像折入派生。
  - `guard_core.h`：新增 `set_binding_material()` 声明 + 更新 derive 文档。
- 踩坑（诚实）：第一版按包名 `getPackageInfo("com.ghost.assist", GET_SIGNATURES)` 被 Android 11 包可见性挡（`NameNotFoundException` → registrySelfTest=false → scatter）。改用 `getPackageArchiveInfo(sModulePath, GET_SIGNING_CERTIFICATES)` 读模块自身 APK 文件签名，绕开可见性，一发即过。该失败轮顺带反向实证了 **fail-closed**（证书读不到 → 散沙不全开）。
- 验证：`gen_registry_cipher.py` 重生；`assembleDebug` BUILD SUCCESSFUL；装机 pid 20858（14:34:05）`certBind set sha256[0..3]=ca421ec3`（= keystore 指纹 `CA:42:1E:C3` 逐字节一致）；`PHASE1A/1B/1C/1D_VERIFY PASS` 无回归。证据：`logs/phase1d_certbind_astep2_20260608.log`（含失败轮 `phase1d_local_derivedkey` 已被覆盖前的散沙实证记录见同目录）。
- 形态边界（V3 决策，2026-06-08）：当前绑定读的是 **v1 LSPosed 独立模块 APK** 的签名，对 v1 防重签模块有效。**V3 改包（LSPatch 免 root）落地时必须改两处**：① `gen_registry_cipher.py` 的 `_CERT_SHA256` 换成 V3 发行签名证书；② 运行时证书源从 `sModulePath`（Xposed 模块路径）改为读「正在运行的宿主包自身签名」（`getPackageInfo(getPackageName(), GET_SIGNING_CERTIFICATES)`，查自己不被可见性挡）。机制不变，只换证书源。
- 诚实底线：本地防线只挡「重签名转卖」90% 懒人；动态高手可本地 NOP 掉派生 → 真锁仍是 B（服务器短命钥匙）。
