# P1A 本地加密原型 worklog

## 目标

- 只做 Phase 1A 本地 `decrypt_config()` 原型。
- 不接服务器、不接业务 hook、不改授权根锁。
- AES-GCM 成功返回测试 registry；失败返回空/散沙结果，不崩溃、不全开。

## 改前审查

结论：PASS。

- 触碰 SO 解密：仅新增本地原型和测试向量。
- 触碰 `NativeBridge`：仅增加轻量自测/解密入口，不接 `StateMachine` / `AuthManager` / hook 回调体。
- 不触碰服务器 envelope、`LeaseClock`、`RiskState`、`DebugServer`、用户数据清理逻辑。

## 实现

- `native_core/src/config_crypto.cpp`
  - 自包含 AES-128-GCM 原型。
  - 固定测试向量仅用于 Phase 1A，不是上线 key。
  - 覆盖错误 key、错误 nonce、篡改 ciphertext、篡改 tag。
- `native_core/include/guard_core.h`
  - 新增 `ConfigDecryptResult`、`decrypt_config()`、`decrypt_config_self_test()`、`decrypt_config_test_registry()`。
- `native_core/src/guard_core.cpp`
  - 新增 JNI：`nativeDecryptConfigSelfTest()`、`nativeDecryptConfigTestRegistry()`、`nativeDecryptConfig(...)`。
- `src/main/java/com/ghost/assist/core/NativeBridge.java`
  - 新增 Java 包装：`decryptConfigSelfTest()`、`decryptConfigTestRegistry()`、`decryptConfig(...)`。

## 修复（2026-06-08）

- 根因：`config_crypto.cpp` 的 AES `MixColumns` 按 row-major 取块，与 `ShiftRows` / 标准 AES column-major 状态布局不一致。
- 现象：本地「加密再解密」roundtrip 能过，但外部固定 AES-GCM 向量（`tools/gen_gcm_vectors.py` 生成、与 C++ 固定向量逐字节一致）不过 → `decryptSelfTest=false`。
- 处理：`MixColumns` 改回标准 column-major；只动纯 crypto 路径，未触碰业务 hook、未触碰授权根锁。
- 同步改动文件：`config_crypto.cpp`、`guard_core.h`、`CMakeLists.txt`、`NativeBridge.java`、`ModuleMain.java`。

## 验证

- `.\gradlew.bat assembleDebug`
  - 结果：BUILD SUCCESSFUL。
  - 覆盖 ABI：`arm64-v8a`、`armeabi-v7a`。
- `ReadLints`
  - 相关文件：无 linter errors。
- 装机自测（主进程 `runNativeBridgeVerification`，hook 注册前 / `nativeInit` 后）：
  - `decryptSelfTest=true`
  - `decryptTestRegistry=true`
  - `decryptScatter=true`
  - `BATCH1_VERIFY PASS`
  - `PHASE1A_VERIFY PASS`
  - 签名：APK 与 `signing/guard-native-debug.keystore` SHA-256 指纹一致，覆盖安装 Success，未卸旧包。
  - ⚠️ 证据来源 = 用户装机口述 + Composer 日志（非终端直采落盘）。按通用禁忌 G6，**终端直采 logcat 原文待补档**：
    `adb logcat -d -s NCL:I > 03_execute_执行任务/P1A_本地加密原型/logs/phase1a_verify_20260608.log`

## 未执行

- 本机无 `g++`，未运行 `tools/test_config_crypto.cpp` host smoke test。
- 终端直采 logcat 原文未落盘（见上方 ⚠️）。

## 下一步

Phase 1B：抽核心 3 到 5 个 hook registry。先明文 registry 跑通，再切到 encrypted registry，不改已验证 hook 回调体。
