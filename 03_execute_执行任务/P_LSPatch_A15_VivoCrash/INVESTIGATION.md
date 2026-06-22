# P_LSPatch_A15_VivoCrash — vivo Android 15 干净安装仍闪退（发版红线调查）

> 状态：**🔴 调查中 / 发版阻塞**（LSPatch 免 root 客户包）
> 设备：vivo V2361GA（PD2361）/ Android **15** / arm64
> 微信底座：8.0.71（versionCode 3080）
> LSPatch：JingMatrix **v0.8 build 439**（产物名含 `-439-lspatched`）
> 开工：2026-06-22

---

## 零、一句话结论（2026-06-22）

**干净卸 + 只装一次** 6/12 打的官替版 APK，桌面启动仍闪退 → **同类 A15 免 root 客户会重演**，不能当 dev 机偶发现象。

崩溃点在 **LSPatch 加载内嵌模块 `com.ghost.assist` 的最后一步**（`NoClassDefFoundError: boot class loader`），**尚未进入 ModuleMain / NCL 业务链**。

---

## 一、证据等级表

| 结论 | 等级 | 出处 |
|------|:----:|------|
| 崩溃栈 = LSPatch metaloader + NoClassDefFoundError | **L1** | adb crash buffer + main logcat（2026-06-22 10:31，PID 23014） |
| 干净卸 mm + 只装一次官替仍闪退 | **L1** | 用户口述 + adb uninstall Success（2026-06-22） |
| LSPatch 能加载 liblspatch.so、能 begin load com.ghost.assist | **L1** | 同上完整启动序列 |
| 6/12 同机 coexist mn 曾正常运行（NCL 日志） | **L1** | `S3a0…/s3a_coexist_vivo_trigdiag_20260612.txt` |
| 6/12 官替 mm「能起」仅用户口述、无 logcat | **L4** | `CURRENT_PLAN.md:108` |
| 根因 = 反复装卸僵尸 | **已证伪** | 干净装一次仍崩（本节测试） |
| 根因 = Tinker 热更新 | **已证伪** | 崩在 AppComponentFactory 阶段，Tinker 未启动 |
| 根因 = vivo DynamicLoadDetectManager 拦截 | **已证伪** | openDexFile 报错来自 monkey(uid 2000)，非微信进程 |
| 根因 = 今天 vivo 系统/安全 app 更新 | **已证伪** | 系统镜像 build 2026-05-09；iqoo.secure 2026-05-17 |
| 根因 = LSPatch 439 × Android 15 classloader 回归 | **L3** | 干净装仍崩 + 6/12 L1 曾成功 → 需换 LSPatch 重 patch 验证 |

---

## 二、设备与包状态（2026-06-22 adb）

| 项 | 值 |
|---|---|
| 品牌/型号 | vivo V2361GA |
| Android | 15 |
| 已装腾讯包 | `com.tencent.mm`（官替）、`com.tencent.mn`（共存） |
| mm 首装 | 2026-06-12 01:11 |
| mm 最后更新 | 2026-06-22（测试前多次变化；干净测试前已 uninstall Success） |
| 安装来源 | `installerPackageName=android`（sideload/adb，非应用商店） |
| 签名 | 非腾讯原签（重签 LSPatch 包） |

**说明**：`com.tencent.mm` 在此设备上 = **官替 LSPatch 包**，不是官方微信（崩溃栈含 `org.lsposed.lspatch.*` 为铁证）。

---

## 三、崩溃栈（L1 原文摘要）

### 3.1 典型 FATAL（mm / mn 相同）

```
FATAL EXCEPTION: main
Process: com.tencent.mm  (或 com.tencent.mn)
java.lang.ExceptionInInitializerError
  at org.lsposed.lspatch.metaloader.LSPAppComponentFactoryStub.<clinit>
Caused by: java.lang.NoClassDefFoundError: Class not found using the boot class loader
```

### 3.2 崩溃前完整序列（mm，PID 23014，2026-06-22 10:31）

```
I LSPatch-MetaLoader: Bootstrap loader from embedment
D nativeloader: Load .../assets/lspatch/so/arm64-v8a/liblspatch.so ... ok
I LSPatch : Use manager: false
I LSPatch : Signature bypass level: 2
I LSPatch : hooked app initialized
I LSPosed-Bridge: Loading legacy module com.ghost.assist from .../cache/lspatch/com.ghost.assist/258984449.apk
I LSPatch : Load modules
E AndroidRuntime: FATAL ... NoClassDefFoundError (boot class loader)
```

**解读**：LSPatch 注入链前半段成功；失败发生在 **Load modules** 之后、业务 ModuleMain 之前。

### 3.3 误报排除

```
E System: openDexFile load detect error
  at com.vivo.framework.securitydetect.DynamicLoadDetectManager.reportDexLoaded
```

该条属于 **monkey 进程（uid 2000）**，不是微信进程；与本次闪退无关。

---

## 四、干净安装冲烟测试（2026-06-22）

| 步骤 | 操作 | 结果 |
|------|------|------|
| 1 | `adb reboot` | ✅ |
| 2 | `adb uninstall com.tencent.mm` | ✅ Success |
| 3 | 安装 `量子密友_8071_官替版.apk`（6/12，249.7MB） | ✅ 用户手动安装（adb 因 vivo USB 不稳 + 安装确认中断） |
| 4 | 桌面点图标 | ❌ **仍闪退**（用户确认） |

**判定**：不是「反复 install -r 僵尸」独有现象（对比 F-39 文档口径需修正适用范围）。

---

## 五、与历史成功记录的矛盾

| 时间 | 包 | 证据 | 结果 |
|------|-----|------|------|
| 2026-06-12 | coexist `com.tencent.mn` | `s3a_coexist_vivo_trigdiag_20260612.txt` — 大量 `NCL`/`BATCH1 PASS`/`[init] ready` | **L1 成功** |
| 2026-06-12 | official `com.tencent.mm` | 用户口述，logcat 未抓 | **L4 待补** |
| 2026-06-22 | official `com.tencent.mm` | 干净装 + 用户确认闪退 | **L1 失败** |

**待查**：6/12 → 6/22 之间，同一 APK 文件、同一 A15 设备，为何从「能跑」变「必崩」？
- 候选 A：LSPatch 439 在 A15 上不稳定（偶发/回归）
- 候选 B：手机侧 ART/odex 或 lspatch cache 污染（但干净卸后仍崩，权重降低）
- 候选 C：官替 mm 与共存 mn 注入差异（需对比拆包 / 同机 mn 复测）
- 候选 D：6/12「官替能起」口述不准，仅 mn 曾 L1 成功

---

## 六、已证伪假设

| 假设 | 证伪依据 |
|------|----------|
| 腾讯 Tinker 热更新导致 | 崩溃早于 Application/Tinker 加载 |
| 今天 vivo 系统/安全中心更新 | build 2026-05-09；iqoo.secure 2026-05-17 |
| 仅 dev 反复装卸才会崩 | 干净卸 + 只装一次仍崩 |
| Guard Native ModuleMain / verification 日志导致 | 未进入 NCL 业务初始化 |
| vivo DynamicLoadDetect 直接拦截微信 dex | 报错进程为 monkey，非 mm |

---

## 七、发版影响

| 形态 | 影响 |
|------|------|
| **LSPatch 官替/共存（免 root 客户）** | 🔴 **阻塞** — A15 代表性机型干净装失败 |
| **LSPosed root 测试机（MI 9 A11）** | 不受本次栈影响（不同注入路径） |
| **模块 Java 代码本身** | 尚无证据表明需改业务 hook |

**客户是否会重演**：**会**（至少 vivo V2361GA / Android 15 + 当前 439 patch 包）。

---

## 八、下一步调查清单

- [ ] **P0** 换最新 LSPatch 重 patch 官替 + 共存，同机重复「干净装一次」测试
- [ ] **P0** USB 稳定后补抓 logcat 落盘（`logs/vivo_a15_crash_YYYYMMDD.txt`）
- [ ] **P1** 对比 6/12 成功时 mn 包 vs 今天 mm 包：内嵌 `assets/lspatch/*`、模块 APK 哈希、LSPatch 版本
- [ ] **P1** 同 APK 在 MI 9 / Android 11 干净装对照（区分 A15 vs 包损坏）
- [ ] **P1** 查 JingMatrix LSPatch issue：Android 15 + `boot class loader` + `Load modules`
- [ ] **P2** 发版门控新增：A15 干净安装冲烟（见 `PROTECTION_MAP` §9 扩展建议）
- [ ] **P2** 修正 `guard-release` / S3a0 文档：「反复装卸僵尸」与「A15 系统性崩溃」分流诊断

---

## 九、相关路径

| 资源 | 路径 |
|------|------|
| 官替 APK（6/12） | `Desktop/新建文件夹 (7)/量子密友_8071_官替版.apk` |
| 共存 APK（6/12） | `Desktop/新建文件夹 (7)/量子密友_8.0.71_共存版.apk` |
| adb 备用副本 | `guard_native/tmp_official_install.apk` |
| 6/12 成功 log | `03_execute_执行任务/S3a0_ServerSeed设计/s3a_coexist_vivo_trigdiag_20260612.txt` |
| LSPatch 工具 | `02_tools_工具/lspatch.jar`（git 忽略则本地） |
| 发版 skill | `.cursor/skills/guard-release_发版/SKILL.md` |

---

## 十、变更记录

| 日期 | 变更 |
|------|------|
| 2026-06-22 | 初版：干净装失败 L1 + 完整启动序列 + 证伪表 + 发版阻塞判定 |
