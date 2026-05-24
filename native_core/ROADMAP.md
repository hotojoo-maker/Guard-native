# ROADMAP — libguardcore.so 开发计划

> 状态：⬜ Phase 0（当前）  
> 铁律：每个 Phase 只做一个小目标，完成验证后再开下一 Phase  
> 阶段门控：Phase N+1 禁止在 Phase N 验证通过前开启

---

## Phase 0 — 文档初始化 ✅（已完成）

**目标**：建立 native_core 目录 + 所有规划文档  
**产出**：`README.md` / `ARCHITECTURE.md` / `API.md` / `RULES.md` / `ROADMAP.md` / `MAP.md`  
**门控**：✅ 文档通过，用户确认方向

---

## Phase 1 — C++ 最小骨架 🟡（代码编译通过，待装机验证）

> **当前状态**（2026-05-22）：
> - ✅ `gradlew buildCMakeDebug` BUILD SUCCESSFUL
> - ✅ arm64-v8a / armeabi-v7a `libguardcore.so` 已生成
> - ✅ 14 JNI 符号全部导出（llvm-nm 验证）
> - ✅ `NativeBridge.java` + `ModuleMain` 验证桩已接入
> - 🟡 **待装机验证**（见下方 Batch 1 验收清单）

**目标**：能编译出 `libguardcore.so`，JNI 接口全部返回安全默认值  
**前置**：Phase 0 文档通过

### 任务清单（代码）

- [x] 新建 `native_core/CMakeLists.txt`
- [x] 新建 `native_core/include/guard_core.h`（接口声明 + 中文注释）
- [x] 新建 `native_core/src/guard_core.cpp`（JNI 入口，14 个接口）
- [x] 新建 `native_core/src/process_router.cpp`（读 /proc/self/cmdline）
- [x] 新建 `native_core/src/state_machine.cpp`（6 状态，冷启动默认 HIDDEN）
- [x] 新建 `native_core/src/wxid_matcher.cpp`（unordered_set，预埋测试 wxid）
- [x] 新建 `native_core/src/push_guard.cpp`（:push 专用短路判断）
- [x] 新建 `native_core/src/anti_tamper.cpp`（包名 + config_version 校验）
- [x] 新建 `native_core/src/log_limiter.cpp`（限流，Release 静默）
- [x] 新建 `native_core/src/core_init.cpp`（启动协调）
- [x] 修改 `build.gradle`：加入 `externalNativeBuild`（arm64-v8a + armeabi-v7a）
- [x] 新建 `src/main/java/.../core/NativeBridge.java`：全部 native 方法声明
- [x] 修改 `ModuleMain.java`：接入 `:push` 进程 + 加验证桩 `runNativeBridgeVerification()`

### Batch 1 装机验收清单（每项附 logcat 关键字）

> 日志 TAG = `NCL`（`adb logcat -s NCL`）  
> 状态：全部 ⬜ 待装机

| # | 验收目标 | 预期日志关键字 | 状态 |
|---|---------|--------------|:----:|
| 1 | 主进程 nativeInit 成功 | `[native] init=true` | ⬜ |
| 2 | :push 进程 nativeInit 成功 | `[native:push] init=true role=2(expect 2=PUSH)` | ⬜ |
| 3 | nativeIsHidden 默认 true | `[native] isHidden=true (expect true)` | ⬜ |
| 4 | setHidden(false) 后为 false | `[native] isHidden after setHidden(false)=false (expect false)` | ⬜ |
| 5 | 密友 wxid 返回 true | `[native] isHiddenWxid(wxid_lzd2va16jd1622)=true (expect true)` | ⬜ |
| 6 | 非密友 wxid 返回 false | `[native] isHiddenWxid(wxid_other)=false (expect false)` | ⬜ |
| 7 | processRole = MAIN(1) | `[native] role=1 (expect 1=MAIN)` | ⬜ |
| 8 | :push role = PUSH(2) | `[native:push] role=2(expect 2=PUSH)` | ⬜ |
| 9 | 无崩溃/tombstone | logcat 无 `Fatal signal` 无 `SIGABRT` | ⬜ |
| — | 全通 PASS 总结行 | `[native] BATCH1_VERIFY PASS` | ⬜ |

### 验收门控

所有 ⬜ 变 ✅ 之后，Phase 1 状态 🟡 → ✅，才能开 Phase 2。  
任何一项 ❌ → 先查日志根因，不得推进。

**不做**：
- 不实现状态机持久化（MMKV 桥接在 Phase 3）
- 不实现 StateBridge
- 不实现 LicenseBox

---

## Phase 2 — Android 独立测试 App 加载 .so ⬜

**目标**：用独立 Test App（不含 LSPosed）验证 SO 加载、JNI 调用、wxid set CRUD  
**前置**：Phase 1 编译通过

### 任务清单

- [ ] 新建 `app_test/` 模块（纯 Java Test App）
- [ ] 实现 `StateMachine` C++ 内存状态机（setHidden / isHidden）
- [ ] 实现 `WxidMatcher`（addWxid / removeWxid / isHidden）
- [ ] 实现 `MMKV` 持久化（Phase 2 接入 MMKV Native API 或 Java MMKV 桥接）
- [ ] Test App 界面：nativeInit → setHidden(true) → addWxid → isHiddenWxid → 显示结果

**验收**：
- Test App 运行：nativeIsHidden() = true after setHidden(true) ✅
- addWxid("wxid_test") → nativeIsHiddenWxid("wxid_test") = true ✅
- 重启 App → MMKV 恢复 → 状态保持 ✅

**不做**：不接 LSPosed，不 hook 微信

---

## Phase 3 — LSPosed 主进程 init ⬜

**目标**：在微信主进程 `handleLoadPackage` 中加载 SO，nativeInit 成功  
**前置**：Phase 2 全部验收

### 任务清单

- [ ] `ModuleMain.handleLoadPackage` 加入 SO 加载 + nativeInit
- [ ] 铁律 27 检查：hook 注册前 nativeInit 已完成
- [ ] HIDDEN 态恢复：启动时从 MMKV 恢复 → nativeReloadState()
- [ ] 现有 Java hook 接入 NativeBridge：
  - `ConvFilter.java` → `nativeIsHiddenWxid` 替换 `Bridge.getWxids().contains()`
  - `ContactFilter.java` → 同上
  - `MomentsFilter.java` → 同上
  - `SearchFilter.java` → 同上

**验收**：
- 装机日志：`nativeInit OK role=MAIN` ✅
- 已验收功能 D1/D2/D3 / F07 / A3 / P17 / P21 行为不变 ✅（铁律 29 不动已验收 hook）
- `frida_stats.js` KPI 无增量 ✅（F-22 铁律）

**不做**：不动 :push 进程，不实现 LicenseBox

---

## Phase 4 — LSPosed :push init ⬜

**目标**：在 `:push` 进程加载 SO，badge/unread 拦截生效  
**前置**：Phase 3 全部验收

### 任务清单

- [ ] LSPosed scope 加入 `:push` 进程
- [ ] `:push` 进程 `handleLoadPackage` 加入 SO 加载（ROLE_PUSH 分支）
- [ ] `StateBridge` 实现（Phase 3 用 MMKV 多进程，Phase 4 验证是否足够）
- [ ] 找 `:push` 进程 badge/unread 写入链（Frida 探针，由用户配合设备操作）
- [ ] 实现 badge hook：`nativeShouldBlockBadge(wxid)` 拦截密友 badge 写入

**验收**：
- 装机日志：`:push` 进程 `nativeInit OK role=PUSH` ✅
- 密友发消息 → 微信 badge 数字不增加 ✅
- 非密友发消息 → badge 正常 ✅
- `frida_stats.js` KPI 无增量 ✅

**注意**：需要 Frida 配合找 `:push` 进程 badge 写入点，必须等用户提供设备操作输出

---

## Phase 5 — Java hook 全面接入 NativeBridge ⬜

**目标**：所有 Java hook 统一通过 NativeBridge 判断，移除 Java 侧 Bridge.getWxids() 直接比对  
**前置**：Phase 4 全部验收

### 任务清单

- [ ] 统一 `shouldHide()` 公式接入 `nativeIsPrivacyEnabled()` + `nativeIsAuthorized()`
- [ ] `LicenseGate.java` 改为调 `nativeIsAuthorized()`
- [ ] `nativeCanUseFeature()` 实现（防撤回 / 虚拟定位基础功能开关）
- [ ] `nativeGetNotifyMode()` / `nativeShouldShowSecretUnreadCount()` 接入通知策略
- [ ] `NotifyPolicy` C++ 模块实现

**验收**：全部已验收功能行为不变 ✅（D1/D2/D3 / P17 / P19 / P21 / A3）

---

## Phase 6 — P21/P22 接入 + LicenseBox v1 ⬜

**目标**：P21 朋友圈小红点接入 NativeBridge；LicenseBox 第一版（离线 embedded seed）  
**前置**：Phase 5 全部验收

### 任务清单

- [ ] P21 v18 badge hook 切换到 `nativeShouldBlockBadge()`
- [ ] P22 密群调试 UI tab 接入 `nativeIsHiddenGroup()`
- [ ] `LicenseBox` C++ 实现：embedded seed + AES-GCM license blob 离线校验
- [ ] `nativeIsAuthorized()` 从占位返回 true → 真实校验
- [ ] 运行 `frida_stats.js` KPI 门控（F-22 铁律）

**验收**：
- 授权校验通过，功能正常 ✅
- 伪造 license → 降级到 AUTH_TAMPERED ✅
- KPI 全部在安全线以下 ✅

---

## 阶段依赖图

```
Phase 0 文档
    └─→ Phase 1 C++ 骨架
            └─→ Phase 2 Test App 验证
                    └─→ Phase 3 主进程 init
                            └─→ Phase 4 :push init
                                    └─→ Phase 5 全面接入
                                            └─→ Phase 6 P21/P22 + License
```

---

## 对应 TASK_BOARD P 任务

| Phase | 对应 P 任务 | 前置条件 |
|-------|-----------|---------|
| Phase 1 | 新建 P_NC1（native_core 最小骨架）| v1 功能稳定后 |
| Phase 2 | P_NC2（Test App 验证） | P_NC1 编译通过 |
| Phase 3 | P_NC3（主进程接入） | P_NC2 ✅ |
| Phase 4 | P_NC4（:push 接入）| P_NC3 ✅ |
| Phase 5-6 | P_NC5-6 | P_NC4 ✅ |
