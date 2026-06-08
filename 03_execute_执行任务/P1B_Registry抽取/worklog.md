# P1B Registry 抽取 worklog

## 目标

- Phase 1B：把**已装机验证的过滤 hook 配方**抽成明文 registry。
- 范围：会话 / 朋友圈 / 通讯录 / 搜索 共 4 条（第 1 项会话先收口，再批量补齐 2–4 项）。
- 口径：明文 registry → SO 解析 → 打印验证命中；**不接管** Filter 实际过滤逻辑（红线 29 / F-31）。
- 不接服务器、不改授权根锁、不动已验证 hook 回调体。

## 改前审查

### 安全与加密官（改前）— PASS
- 触碰 SO 解密：复用已验证 `decrypt_config()`（不改），新增 registry **解析**。
- 不触碰服务器 envelope / LeaseClock / RiskState / 用户数据清理。

### 授权检查官（大框架）— PASS（带 3 条约束）
1. `registry_loader.cpp` 独立成模块——不混进 config_crypto、不混业务过滤、不接 ConvFilter。
2. registry 解析失败 → 散沙（不全开不崩），沿用 `make_scatter` 红线。
3. `guard_core.h` 只新增 registry 声明，不碰既有 `AUTH_*` / `RISK_*` 常量。

四门状态：EntryGate/AuthGate/StateGate ✅ 不涉及；RiskGate ⚠️ 轻触（解不开散沙）；分层合规 ✅。

## 权威锚点（L2 已证实，均来自已装机的 Filter 源码）

- **conv.list**（`moduleD/ConvFilter.java`，P17）：MvvmList `com.tencent.mm.plugin.mvvmlist.MvvmList`、adapter `kc5.v0`、item `kc5.y`、contact `com.tencent.mm.storage.l4`、L1 `n/m`、L2 `s`、L4 `notifyDataSetChanged`、wxid getter `C0,h1,j1,i1,k1,...`。
- **moments.feed**（`moduleD/MomentsFilter.java`，D1/D2/D3）：item `na4.b`/`la4.p`、adapter `e2`、wxid 字段 `field_userName`、内层 `d`、点赞/评论 `LikeUserList`/`CommentUserList`、actor `z15.e56`、actor wxid 字段 `f435583d`。
- **contact.address**（`moduleD/ContactFilter.java`，P19）：live list `AddressLiveList`、adapter `ik3.t0`、item `fc5.g`、contact `com.tencent.mm.storage.z3`、字段 `d`(→z3)/`e`(type)、wxid getter `c1`。
- **search.fts**（`moduleB/SearchFilter.java`，P20）：bind adapter `q2`、bind 方法 `j`、联系人 item `tz2.u1`(g.a==1, wxid=`f.s`)、群聊 item `tz2.s1`(g.a==2, groupId=`s`)、表头 `tz2.g0`。

## 实现

- `native_core/registry_8071.json`：明文源（schema `r8071_v1`，4 条：conv/moments/contact/search），人读 + 1C 自动生成的来源。
- `native_core/src/registry_loader.cpp`：手写 JSON 解析（仅对象 + 字符串），`-fno-exceptions/-fno-rtti` 下不抛异常，任何畸形输入 → `make_scatter(ok=false)`。
  - `registry_parse()` / `registry_load_embedded()` / `registry_dump_summary()` / `registry_self_test()`。
- `native_core/include/guard_core.h`：+`<vector>`/`<utility>`，新增 `RegistryEntry` / `ConfigRegistry` + 4 个函数声明（不碰 AUTH_*/RISK_*）。
- `native_core/src/guard_core.cpp`：新增 JNI `nativeRegistrySelfTest()` / `nativeRegistrySummary()`。
- `core/NativeBridge.java`：新增 `registrySelfTest()` / `registrySummary()` 包装 + 2 个 native 声明。
- `ModuleMain.java`：自测块加 registry 验证打印 + `PHASE1B_VERIFY`。
- `native_core/CMakeLists.txt`：加 `src/registry_loader.cpp`。

## 改后审查

### 安全与加密官（改后）— PASS
- 解析失败散沙：`registry_parse` 任何畸形 → `make_scatter`，自测覆盖 空串/截断/缺字段/尾部垃圾/entry 非对象。
- C++ 纯字符串解析，不碰 UI/DB/Adapter/微信 SO；不清用户数据；不破坏微信本体。
- 三条约束全部符合。

## 验证

- `.\gradlew.bat assembleDebug`：BUILD SUCCESSFUL（arm64-v8a + armeabi-v7a 重编 CMake）。
- `ReadLints`：5 个改动文件无 linter errors。
- 装机：`adb install -r` Success（固定签名 key，覆盖安装，未卸旧包）。
- 装机自测（第 1 项，pid 9760，11:13:07）：`registrySelfTest=true` / `registrySummary=...entries=1...` / `PHASE1B_VERIFY PASS`。
- 装机自测（4 条全量，pid 11003，11:19:00）：
  - `registrySelfTest=true`
  - `registrySummary=schema=r8071_v1 ver=8.0.71 entries=4 [conv.list adapter=kc5.v0 l4=notifyDataSetChanged] [moments.feed adapter=e2] [contact.address adapter=ik3.t0] [search.fts]`
  - `PHASE1B_VERIFY PASS`
  - 无回归：`PHASE1A_VERIFY PASS` / `BATCH1_VERIFY PASS` 仍 PASS。
- 证据落盘（终端直采，G6 满足）：
  - `03_execute_执行任务/P1B_Registry抽取/logs/phase1b_verify_20260608.log`（第 1 项）
  - `03_execute_执行任务/P1B_Registry抽取/logs/phase1b_verify_4entries_20260608.log`（4 条全量）

## 未执行 / 已知约束

- registry 当前为**明文 + SO 内嵌**（1B 计划如此）；尚未走 AES-GCM 加密（1C/后续）。
- `registry_8071.json` 与 `registry_loader.cpp` 内嵌串需手工保持同步，直到 1C 由 `tools/` 自动生成。
- registry **未接管** ConvFilter 过滤逻辑——本轮只解析 + 验证命中。

## 下一步

- Phase 1B 4 条 registry 已全部明文跑通并装机验证 ✅。
- Phase 1C：把明文 registry 切到 encrypted registry（`decrypt_config` 接 registry：用 `tools/gen_gcm_vectors.py` 从 `registry_8071.json` 生成 AES-GCM 密文 → SO 解密 → 解析），消除「明文+手工双份」漂移；解不开散沙。
- 之后：LeaseClock + RiskState（断网宽限 / 时间异常 / 蜜罐影子期）。
- 仍不接管 Filter 过滤逻辑、不接服务器、不动已验证 hook（红线 29 / F-31）。
