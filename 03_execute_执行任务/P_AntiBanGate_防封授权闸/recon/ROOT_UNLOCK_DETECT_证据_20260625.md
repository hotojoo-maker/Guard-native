# ROOT / 解锁检测 证据核账（2026-06-25）

> 角色：只读证据研究 worker。范围：只挖现有证据/日志，**不跑新环境探测、不跑新主动 frida**（踩防封铁律5）。
> 底座：官方包 com.tencent.mm 8.0.71（libwechatnormsg.so MD5 `c0e2cba0d0f017fe1ec9ab61a2871d32`）；部分共存包 com.tencent.mn 复现。
> 证据级：L1 动态实证 / L2 静态实证 / L3 收敛 / L4 待验。
> 问的是什么：架构师在评「检测到 root → 进影子模式」，前提 = 能不能像 A2 借眼睛读签名那样，**借官方的眼睛读到微信自己采集的 root/解锁结果**（而不是我方自读环境、踩铁律5）。

---

## ① 一句话结论

- **微信 8.0.71 确实采 root/解锁信号**，但分两条路、且都不给「现成判定结果」：
  - **解锁 / bootloader / 完整性**（verifiedbootstate / vbmeta / flash.locked / oem_unlock / ro.secure / ro.debuggable）= **纯 native** `__system_property_get`（libwechatnormsg.so）读，明文只活在 native 漏斗 `f`/`aa`(=FUN_00196e74) 内部 → 打进 protobuf `field3`（~6–7KB 高熵密文）。**Java 边界无此结果**。
  - **root 工具 / 环境画像**（Magisk 包、RE 工具无障碍、frida/xposed/root 的 logcat 痕迹）= **走 Java 边界**：`getInstalledPackages` 枚举 `com.topjohnwu.magisk`、`Settings.Secure` 读无障碍、`Runtime.exec("logcat …")`。
- **借眼睛读 root「判定结果」= 不可行**：解锁轴在 native 读 + 判定在 native/服务端（field3 密文，拿不到）；root 工具轴虽走 Java，但微信只采**原始输入**（包列表/无障碍列表/logcat 文本），**不暴露 `isRooted=true` 这种成品布尔**——判定同样沉在 native/服务端。
- **能做到的最接近** = 在微信**已经调用**的 Java 读取点（`getInstalledPackages`/`Settings.Secure`/`Runtime.exec`）上「搭车」观察它枚举到了 Magisk/RE 工具——但这只是看到**输入**，「这台是 root」仍是**我方自己的判断**，不是借微信的判断。
- **现实校准（important）**：本机被读成 **unlocked + Magisk root**，官方包冷启动 6 分钟 + 真登录**全程未被 kill**（L1）→ root/解锁本身**不是即时封因**（与 `CLAUDE.md`/DESIGN §0「root 一月零封」一致）。所以「root = 强信号 → 影子」这个**前提在官方处置层面缺 L1**。

---

## ② root / 解锁 信号清单表

> 「读取点」= 微信在哪读；「借眼睛」= 能否像 A2 借 `getPackageInfo` 那样在 Java 边界读到微信的**读取/结果**；「防封代价」= 我方若要利用这点会不会自增环境读取面（踩铁律5 / 违「我方零环境读取」）。

| # | 信号 | 查什么 | 读取点 (Java/native) | 落点结构 | 证据级 | 证据文件 | 借眼睛可行性 | 防封代价 |
|---|---|---|---|---|---|---|---|---|
| 1 | `ro.boot.verifiedbootstate`=orange | bootloader 是否解锁 | **native** `__system_property_get` @+0xc68a4（启动早期微读取器簇③） | native 漏斗 `f`/`aa`(FUN_00196e74) → protobuf `field3` 密文 | L1 | DYNAMIC_PLAINTEXT §2.1 / OFFSET_MAP §1 / REPORT_CHAIN §2.1 | **不可行**（native 读，Java 无结果点） | 我方自读 = 踩铁律5（禁 `ro.boot.*`）；native 钩 = 增 native 面 |
| 2 | `ro.boot.vbmeta.device_state`=unlocked | 解锁状态 | **native** @+0xc6750（③） | 同上 | L1 | DYNAMIC_PLAINTEXT §2.1 / OFFSET_MAP §1 | 不可行 | 同上 |
| 3 | `ro.boot.veritymode`=enforcing | dm-verity | **native** @+0xc6c18（③） | 同上 | L1 | DYNAMIC_PLAINTEXT §2.1 | 不可行 | 同上 |
| 4 | `ro.boot.flash.locked`=0 | 刷机锁 | **native** @+0x3b03fc（巨函数深处①） | 同上 | L1 | OFFSET_MAP §1 | 不可行 | 同上 |
| 5 | `sys.oem_unlock_allowed`=1 | 允许解锁 | **native** @+0x3b0000（①） | 同上 | L1 | OFFSET_MAP §1 | 不可行 | 同上 |
| 6 | `ro.secure`=1 / `ro.debuggable`=0 | 安全/可调试 | **native** @+0x3afa88 / +0x3afd48（①） | 同上 | L1 | DYNAMIC_PLAINTEXT §2.1 / OFFSET_MAP §1 | 不可行 | 同上 |
| 7 | `ro.bootloader` | bootloader 版本 | **native** @+0x15a654 | 同上 | L1 | DYNAMIC_PLAINTEXT §2.1 | 不可行 | 同上 |
| 8 | 包枚举 `com.topjohnwu.magisk` | **Magisk = root** | **Java** `getInstalledPackages`（c$p / dim.collect `c$p.aa`） | 进 native 漏斗 → field3 | L1 | CSP_COLLECT_EXPAND §一 / 防封官 skill §9 | **部分可行**（Java 边界可 hook，能看到「枚举到 Magisk」） | 被动搭车观察=低增面；我方主动枚举包判 root=新增环境读取面 |
| 9 | `enabled_accessibility_services` | RE 工具（实捕 `bin.mt.plus` MT管理器） | **Java** `Settings.Secure.getString` | 进 native 漏斗 → field3 | L1 | CSP_COLLECT_EXPAND §一 | 部分可行（Java 可 hook） | 被动观察低增面；主动读=增面 |
| 10 | `logcat -b all -t 2000` | frida/xposed/root/崩溃痕迹 | **Java** `qm5.h.run` `Runtime.exec` | 后台线程任务（上报链未单证） | L1 | CSP_COLLECT_EXPAND §三 | 部分可行（Runtime.exec 边界可观察） | 被动观察低增面；主动跑 logcat=增面 |
| 11 | **不做**：su/magisk **文件名扫描** | 文件路径查 root | （libwechatnormsg native grep **零命中**） | — | L1（零命中） | DYNAMIC_PLAINTEXT §3.2 | N/A | N/A（微信 8.0.71 此 SO 不走文件名扫 root） |
| 12 | 扩面候选 `getauxval`(AT_SECURE)/`dladdr`/`sigaction` | 反调试/setuid 环境 | **native**（巨函数内，调用点未单独反编译） | 未 L1 动态 | L2 | DETECT_SURFACE_EXPAND §2 | 不可行（native） | 我方不应为这些新增面 |

补充（身份轴，非 root 但同咽喉，作对照）：签名 MD5 经 **Java** `getPackageInfo(GET_SIGNATURES)`（`t8.c0` / `c$p`）读 → **借眼睛已证可行**（A2：喂官方 DER 后 c$p 实读官方 `18c867f0`）。证据 OFFSET_MAP §3 / A2_CSP_JAVA_CALLER。**签名能借眼睛、root/解锁不能** —— 因为签名是「读自身 `getPackageInfo` 返回值」这种有 Java 返回点的读法，而 root/解锁是 native 属性读 + 原始环境输入，判定不落 Java。

---

## ③ 借眼睛读 root 可行性 初判

**问题拆成两层：**

1. **借「微信已判定的 root/解锁结论」** → ❌ **不可行**。
   - 解锁轴：native `__system_property_get` 读 → 直接进 `aa`(FUN_00196e74) 内部序列化 + 加密 → `field3` 密文（`f` 入参 `[B=null`，加密前明文只在该函数寄存器/栈，**不过任何函数边界**，CSP_AU_NATIVE §三 / REPORT_CHAIN §3.1 L1）。Java 层根本没有「unlocked? rooted?」的返回点可 hook。
   - root 工具轴：虽在 Java 采原始输入，但微信**只收集不下结论**（包列表/无障碍/logcat 文本原样进漏斗），「这台是 root」的判定沉在 native/服务端密文里，Java 边界同样无成品布尔。

2. **借「微信查 root 的 Java 读取点」搭车观察输入** → 🟡 **技术上部分可行，但不等于借到判定，且有防封代价**。
   - 可 hook `getInstalledPackages`（看微信是否在枚举 → 命中 `com.topjohnwu.magisk`）、`Settings.Secure.getString`（无障碍 RE 工具）、`Runtime.exec(logcat)`。这些是 Java 边界、A2 同款打法位置。
   - 但「命中 Magisk 包 → 判 root」是**我方自己写的判断逻辑**，等于我方做 root 检测，不是「官方替我们判好了我们读结果」。
   - **代价分级**：
     - **被动**（只在微信已发起的调用上观察返回）：增面很低（微信本来就调）。
     - **主动**（我方自己去 `getInstalledPackages`/读无障碍/跑 logcat 判 root）：**新增环境读取面**，违「我方零环境读取」原则；解锁轴更直接踩铁律5（禁 `ro.boot.*`）。

**对「root → 影子模式」设计的初判（L3 推断）：**

- 设计依赖的「借官方眼睛拿现成 root 判定」**不成立**；只能退化为「我方自判 root」，而我方自判会增检测面（与防封主张相反）。
- 且**前提存疑**：本机 root+unlocked，官方包冷启 6 分钟 + 真登录未 kill（L1）→ 官方对 root **非即时处置**；「root = 该进影子」的强信号假设在**官方处置层面无 L1**。
- 若仍要做，更稳的口径是**把 root 当服务器侧弱信号**（付费即正版，与 DESIGN §0 一致），而非客户端读 root 触发本地影子；客户端硬轴维持「签名身份」。**最终决策交架构师**（本文只给证据 + 初判，不拍设计）。

---

## ④ 待验（L4）清单 + 怎么验

> 纪律：以下都需要**新动态/主动验证**，本只读轮不做；要做须架构师拍板 + 按防封纪律（官方包冷启动 + 快 attach，warm 只读）走，并标 L4→L1。

| # | 待验 | 当前级 | 为什么重要 | 怎么验（不在本轮做） |
|---|---|---|---|---|
| 1 | root/解锁结果是否**真进 field3 上报**（vs 只本地读） | L3（内容=采集明文，强相关；wire 密文未开） | 决定它是不是「服务器可见输入」 | rung-2b：在 `aa`(FUN_00196e74 @+0x96e74) 加密指令前下 native 钩从寄存器读 buffer（重型，CSP_AU_NATIVE 已记工作清单） |
| 2 | 官方包**登录/注册链路**是否对 root/unlocked 有后置 kill | L4（冷启 6 分钟未杀，登录链未单测） | 决定 root 到底有没有处置、何时 | 官方包冷启动 + 完整登录一次，只读观察是否触发 kill（DYNAMIC_PLAINTEXT §4 待补） |
| 3 | `WCProbe$Info.l/m` 是否做 **APK/代码哈希**自检（不止签名） | L4 | 关系到改包是否还有代码哈希硬伤 | 跑 `csp_lm_native.js` 或静态深挖（研究线工作清单已列） |
| 4 | 扩面候选（GL 指纹 / getauxval / sigaction 反调试）是否进上报 | L2（导入表坐实，动态未抓） | 是否有 native 反调试会绊到我方 | `frida_8071_detect_observer_v2.js` 冷启动复验（DETECT_SURFACE_EXPAND §4） |
| 5 | 「命中 Magisk 包 → 服务器是否据此处罚」 | L4（只证采集，未证判分） | 决定 root 软信号的真实权重 | 服务器侧黑盒，**无石锤前不写结论**（防封纪律：只报采集/上报事实） |

---

## ⑤ 血管分支图（心脏 `FUN_00196e74` 的调用图）

> 心脏 = native 漏斗 `aa`/`f` = `FUN_00196e74 @ libwechatnormsg.so+0x96e74`（3,787,336 B，3.61MB CFF，Ghidra 反编译溢出）。运行时地址 L1 坐实（CSP_AU_NATIVE：ArtMethod off+24 → +0x96e74）。下面按「上游触发 → 心脏 → 分支读取器 → 下游上报 + 旁支」拆，证据级逐处标。

```
                      [上游触发 who calls]  Java: c$p.aa / WCProbe$Info.f(int,int,int,[B=null)   (L1)
                      触发时机: 进程启动 + 周期(~2min) + UI(附近的人/加好友/切后台回前台 ×3轮)   (L1 REPORT_CHAIN§1)
                                  │  ArtMethod off+24 跨进 native
                                  ▼
   ┌──────────────────────  心脏 FUN_00196e74 (aa/f, CFF 编排器)  ──────────────────────┐
   │ 直调 native 原语(L2 静态 Calls 边): __system_property_get / __system_property_find  │
   │   / popen / getifaddrs / uname / lstat / gettimeofday / dlopen·dlsym·dlclose / pclose│
   │   (dlopen+dlsym 运行时解析 getenv/fdopen/fgets/AAsset* → 静态导入表看不到, L1)        │
   └───────┬───────────────┬───────────────┬───────────────┬───────────────────────────┘
           │①编入深处      │③启动早期       │设备指纹簇       │大检测块(均 decompile timeout=CFF, L2)
           │0x3afxxx-3b0xxx│0xc6xxx 微读取器 │0x15a–0x16b 各小函数│
           ▼               ▼               ▼               ▼
   ro.secure/ro.debuggable  verifiedbootstate  bootloader FUN_0015a15c   FUN_0011b504(96KB) Calls __system_property_*
   sys.oem_unlock_allowed   vbmeta.device_state brand/device/hardware    FUN_00136068(136KB) Calls dlopen/dlsym
   ro.boot.flash.locked     veritymode          name/manufacturer        FUN_0016b718(162KB) Calls dlopen/dlsym
   bootimage.fingerprint    build.fingerprint   model FUN_00166600        (version.release 落此=大检测块,非纯读版本)
   uname(内核)
   ★解锁/完整性「两摊」: ① 巨函数深处后期复核 + ③ 启动早期微读取器双查 (L1×L2 OFFSET_MAP§1)
                                  │ 全部明文只活在心脏寄存器/栈, 立即序列化+加密(内联, au JNI 0 触发)
                                  ▼
   [下游上报] protobuf 信封 field3(~6256–7056B 高熵密文)  (L1 REPORT_CHAIN§3.2 / CSP_AU_NATIVE§二)
                                  │ field8=时间戳 field1="00000002" field2=2 field3=密文
                                  ▼
                          u.uc(==f 字节) → NormsgDataService → 网络   (L1)

   [旁支 = 不进心脏的 Java 采集动脉]
     · getInstalledPackages 枚举 com.topjohnwu.magisk(Magisk)            Java  (L1 CSP_COLLECT_EXPAND§一)
     · Settings.Secure.getString enabled_accessibility_services(RE工具)  Java  (L1)
     · qm5.h.run → Runtime.exec("logcat -b all -t 2000")(frida/xposed/root痕迹) Java (L1 §三)
   [旁支 = 其它 SO native]
     · libilink2.so   fopen /proc/self/maps  (fake_dlfcn 扫注入 SO)  @+0x1138b0  (L1)
     · libmeminfo.so  fopen /proc/<pid>/smaps  @+0x6a28                          (L1)
     · libmatrix-mallctl.so  /proc/self/maps                                     (L1)
```

**血管图给设计的三个判读：**

1. **解锁/完整性这一整片血管全在 native 心脏内**（深处①+启动微读取器③），明文不出心脏函数边界 → **没有任何 Java 分支可搭车**，呼应 ②/③：unlocked 借不了。
2. **唯一长在 Java 上的「可搭车血管」是旁支**：root 工具枚举 / 无障碍 / logcat（采原始输入）+ 签名 `getPackageInfo`（A2 已借）。即「能借的血管」= 这几条 Java 旁支，「心脏主血管」= 借不到。
3. **大检测块（96KB/136KB/162KB）是 CFF 黑箱**：`version.release` 落进 162KB 块说明它不是单纯读版本、而是大检测编排；要看清里面分支须 rung-2b 重型 native RE（耗时不保证），当前 L2 容器级即够支撑「都在 native」的结论。

---

## 证据文件索引（本轮只读引用，未改）

- `证据/8071_DYNAMIC_DETECT_PLAINTEXT_20260618.md`（L1：root/解锁明文 + 偏移；§3.2 不扫 su/magisk 文件名；§3.3 unlocked 未当场 kill）
- `证据/8071_STATIC_DYNAMIC_OFFSET_MAP_20260618.md`（L1×L2：三层检测结构，解锁轴劈两摊；§3 签名 Java t8.c0）
- `证据/8071_NORMSG_DETECT_REPORT_CHAIN_20260618.md`（L1：采集→native 漏斗 `f`→field3 密文→u.uc/NormsgDataService→网络；f 入参 [B=null）
- `证据/8071_DETECT_SURFACE_EXPAND_20260618.md`（L2：导入表扩面候选 GL/getauxval/dladdr/sigaction）
- `证据/CSP_COLLECT_EXPAND_20260619.md`（L1：c$p Java 采 android_id/无障碍/输入法 + 包枚举 Magisk；qm5.h.run logcat）
- `证据/CSP_AU_NATIVE_CAPTURE_20260620.md`（L1：漏斗 `aa`=FUN_00196e74@+0x96e74，field3 加密内联、明文不过边界）
- 对照：`研究线证据映射_20260625.md` §3.4、防封官 skill §9 / 实测手法库

# End
