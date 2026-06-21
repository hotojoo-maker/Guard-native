# P_ANTIBAN_B36 — normsg 三轴身份上报 + MMTLS + 官方证书实证

> 角色：防封官（guard-antiban_防封官）
> 版本底座：微信 **8.0.71**（versionCode 3080），设备 MI 9 / Android 11，frida 17.11
> 日期：2026-06-18（B36 会话）
> 证据等级：本文 L1 = frida 动态实抓 / tcpdump 实抓；L2 = jadx 静态；L3 = 收敛推断；L4 = 待验证
> 文件夹隔离：本文为防封官独立研究产物，不改 JIDE 文档 / 其他文件夹

---

## 〇、一句话结论

封号根因 = **客户端把"非官方身份"明文上报**，不是微信检测 root/hook（root 机开发一月未封，L1 反证）。身份 = normsg `z3` 设备指纹里的三个字段：**k33 包名 / k49 数据路径 / k18 签名证书 MD5**。官方三个都对 → 不异常。改包动了任一个 → 暴露 → 封。

---

## 一、三轴身份模型 → 落到具体 z3 字段（L1）

| 轴 | z3 字段 | 官方值（本机实抓） | 来源 | 可否伪装 |
|----|---------|-------------------|------|----------|
| 包名 | **k33** | `com.tencent.mm` | Java getPackageName + native /proc/self/cmdline | native 源难伪（共存必暴露） |
| 数据路径 | **k49** | `/data/user/0/com.tencent.mm/` | 真实文件系统路径 | 真实路径难伪 |
| 签名 | **k18** | `18c867f0717aa67b2ab7347505ba07ed` | Java `getPackageInfo(GET_SIGNATURES)` | 可 hook 伪装（LSPatch 签名绕过） |

三个字段 normsg 全部**明文**收进 `z3` 上报。

---

## 二、官方证书实证（L1 + web 核实）

- 本机 com.tencent.mm 签名证书：
  - **MD5 = `18c867f0717aa67b2ab7347505ba07ed`**
  - **SHA-256 = `0fe4ff85c215918396dadc7cd8ce6963339af33d37751a56e54c7206b63a3c7c`**
- 核实：该 MD5 = **腾讯官方 WeChat 证书**（对上 Uptodown 官方 APK 列的 Certificate signature，Developer=Tencent，包名 com.tencent.mm）。
- **关键交叉**：该 MD5 与 z3 的 **k18 一字不差** → 证明 **normsg 把 APK 签名 MD5 当 k18 明文上报**（签名是真上报，不是只读看）。
- 结论：本机 .mm = **100% 官方**（cert + 包名 + 路径 + 版本 8.0.71/3080 四重确认）。

---

## 三、登录抓取实证（L1，官方 mm 真实登录，~2 分钟爆发）

- **auth 登录请求**（:push）：走 `MMProtocalJni.packHybridEcdh`（ECDH 非对称模式），arg6 `len=8109`（8KB protobuf binary）。
- **normsg 上报**（main）：
  - `c$p.aa()` 多次，返回 **7KB → 21KB** 增长 blob（头 `@....3..00000002`，normsg 自有加密的环境/设备数据）。
  - `c$p.ad/ae/af()` 小块（16/16/8B，疑 key/IV/控制）。
  - `z3(0)/z3(3)` 设备指纹**明文**多次（k33/k49/k18 + 机型/SoC/CPU/build/serial/MAC/OAID）。
  - `getPackageInfo(com.tencent.mm, GET_SIGNATURES)` 多次。
- **判定**：登录 = 大上报时刻（auth + 设备指纹 + 签名 + normsg 环境 blob 全在登录这一刻）→ 坐实「登录/活跃才结账」，idle 不报。

---

## 四、MMTLS 传输层（Citizen Lab 2024 + 本机枚举实证）

- MMTLS = 微信自有改版 TLS1.3，**外层**；内层还有 Business-layer 加密（双层套娃）。跑在 **`com.tencent.mm:push`** 进程，核心 `libwechatnetwork.so::Crypt()`。
- 两通道（与 tcpdump 对上）：Shortlink（HTTP port 80）+ Longlink（TCP 443/8080）；冷启各握手一次，AES-GCM，确定性 IV，无前向安全。
- **关键导出（本机枚举，无需 Ghidra）**：
  - `libwechatnetwork.so`：`mmcrypto::AesGcmEncrypt/Decrypt`、`SignatureChecker`、`mars::mmpkg::Packer::UnPackWxPkg`。
  - `libwechatmm.so`：`UtilsJni.AesGcmEncryptWithCompress/HybridEcdhEncrypt/HKDF`。
  - `libMMProtocalJni.so`：`MMProtocalJni.pack/aesEncrypt/genSignature`。
- **网络层结论**（tcpdump 冷启实抓）：normsg 上报复用微信自有 mmtls 通道，**无独立端点、内容 mmtls 加密 → 网络层拦不住、读不了**。要 READ 只能 in-process（Citizen Lab 开源 frida 工具：github `citizenlab/wechat-security-report`）。

---

## 五、设备维度 + 上报时机（L1）

- **无自动连坐**：设备级 ID（OAID/serial/MAC/device hash）mm 与 mn1 同机共享；mn1 已封、mm 同机一月未封 → 服务器不按设备指纹自动连坐干净号。（"未观察到自动连坐"，非绝对。）
- **上报非高频常驻**：warm idle 80s + cold 30s + 交互 = normsg 自然上报 0 次；集中在**极早期启动 + 登录/活跃事件**。

---

## 六、官替 / 共存 判定（走向）

- **共存版**（com.tencent.mn1）：k33/k49 变 → 必暴露 → 封（附录 F 已 L1 实证）。
- **官替版**（重签名）：k33/k49 官方，但 **k18 = 重打包者 cert MD5 ≠ 18c867f0...** → 暴露；**除非 LSPatch 签名绕过把 `getPackageInfo(GET_SIGNATURES)` 喂回官方签名**，k18 才会是官方值。
- **唯一有证据的安全线 = 官替（k33/k49 官方 + k18 签名伪装回官方）**。

---

## 七、工具脚本（tools/，本会话新增，均独立文件）

| 脚本 | 用途 |
|------|------|
| `tools/dump_mm_z3.js` | 主动调 z3(0)/Y8 抓设备指纹明文（已有，复用） |
| `tools/trace_pkg_source.js` | 追 k33 来源（getPackageName / native cmdline） |
| `tools/trace_upload_timing.js` | 测 normsg 上报频率 |
| `tools/dump_normsg_full.js` | c$p 采集器 + 签名 + z3 + dispatchEncrypt |
| `tools/enum_mmtls_exports.js` | 枚举 MMTLS 三 SO 导出符号 |
| `tools/dump_req_plaintext.js` | :push 抓 MMProtocalJni.pack / UtilsJni 请求明文 |
| `tools/dump_sig.js` | dump 签名证书 MD5/SHA256 |

---

## 八、待验证（L4，下一步）

1. 签名是否仅 Java `GET_SIGNATURES`（可 hook 伪装），还是 native（`mmcrypto::SignatureChecker` / c$p）也独立算签名 hash（若是则 k18 伪装不够）。
2. normsg `c$p.aa` 的 21KB blob 内层解密（normsg 自有 AES）。
3. 官替 LSPatch 签名绕过是否真覆盖 normsg 读签名的路径（可行性研究，进行中）。

---

## 九、稳方案 + 兜底（B36 确认）

**主方案（稳）**

- **可 root（自用 / 种子）= 金标准**：root + LSPosed + **原版 APK**。零改包 → k33/k49/k18 天然全官方 → **已 L1 证一月安全**。
- **无 root（商用）= 官替 + LSPatch Lv1 签名绕过（PM hook）** —— A 定论：k18 走 Java `getPackageInfo`，**Lv1 足够，不需 Lv2 openat**：
  - k33（包名）/ k49（路径）：官替本就是 `com.tencent.mm`，**天然官方**。
  - k18（签名）：靠 **Lv1**（hook `PackageParser.generatePackageInfo` + 代理 `PackageInfo.CREATOR`，伪装 Java `getPackageInfo` 返回官方签名）回官方。

**兜底（fail-closed，多层）**

1. **发版前三轴自检门（必卡红线）**：出包后 spawn 候选包 → dump `z3` → 断言 `k33=com.tencent.mm` & `k49=/data/user/0/com.tencent.mm/` & `k18=18c867f0717aa67b2ab7347505ba07ed`。**任一不对 → 阻塞不发**。
2. **targeted 签名硬钉**：模块在 normsg 读签名点强制返回官方 MD5 `18c867f0...`（兜 Lv2 可能漏的路径）。
3. **终极回退**：官替实测不稳（触发微信 native 反篡改）→ 退回 root + LSPosed + 原版。

**证据级**：root+原版 = **L1 已证稳**；官替+**Lv1** = **L4 待验证**（k18 签名读已 L1 证走 Java `getPackageInfo`，Lv1 可覆盖；微信 native so CRC32 反篡改风险仍在），故**兜底 1（自检门）为上线前强制红线**。
> **B36 定案**：主方案锁 **LSPatch Lv1**（去掉 Lv2，依 §十 A 定论）。兜底（自检门 + 签名硬钉 + root 回退）保留。

---

## 十、A/B 定论（B36，调用栈实证）

**A：k18 签名走 Java `getPackageInfo`（即便由 native `c$p.ea` 触发）→ LSPatch Lv1 够**

spawn 冷启抓到的签名读调用栈（L1）：

- **normsg（关键 #5）**：`plugin.normsg.u.yi → normsg.m.b → c$p.ea(native) → getPackageInfo(GET_SIGNATURES)`。即 normsg 由 native `c$p.ea` 触发，但**签名数据实际走 Java PackageManager `getPackageInfo`**，**非 native 直读 APK 签名块**。
- **请求模型（#6）**：`modelbase.l3.<init> → getPackageInfo(GET_SIGNATURES)`（l3 = 加好友 verifyuser 模型）。
- 其它读者：Tinker 热补丁校验、GMS measurement、Qualcomm Boost、本模块 `com.ghost.assist.ModuleMain.bindSigningCert`（已在读）。

⇒ **结论 A**：k18 = Java `getPackageInfo` 源 → **LSPatch Lv1（PM hook `generatePackageInfo` + `PackageInfo.CREATOR`）即可伪装 k18**。Lv2(openat) 仅作保险（normsg 未见 native 直读签名块）。**主方案 LSPatch 从 Lv2 降到 Lv1 够（更稳更简单），Lv2 留保险。**

**B：targeted 签名硬钉落点 = `getPackageInfo(GET_SIGNATURES)`**

一处 hook 覆盖 normsg（c$p.ea→getPackageInfo）+ 请求模型（modelbase.l3）+ 全部签名读者。本模块已在 `ModuleMain.bindSigningCert` 读签名，加 getPackageInfo 强钉返回官方 cert（MD5 18c867f0...）即可，落点明确。

**保留（L4，可选 100% 定论）**：`c$p.ea` 为 native，若它另有 native 直读 APK 签名（Java 栈不可见），Lv1 会漏 → 补 spoof 验证（hook getPackageInfo 返回伪签名 → 看 z3 的 k18 是否变）即可拍死。当前栈证据强烈指向 Lv1 够。

---

## 十一、兜底1 自检门验证 + ② 状态（B36）

- **① 三轴自检门 `tools/gate_three_axis.js` 已验证 ✅**：官方 mm 实跑 → `k33=com.tencent.mm PASS` / `k49=/data/user/0/com.tencent.mm/ PASS` / `k18=18c867f0... PASS` → 「三轴自检门 PASS（可发）」。官替/共存任一轴 ≠ 官方 → FAIL（阻发）。**兜底1 落地可用**（出包前 spawn/attach 候选包跑一遍即可）。
- **② spoof 定论：Lv1 高置信（两路 L1 证据）**：
  - 路1（A 调用栈）：`normsg.u.yi → normsg.m.b → c$p.ea(native) → getPackageInfo(GET_SIGNATURES)`。
  - 路2（spoof 实触发）：spoof 探针**只对栈含 normsg 的 getPackageInfo 下手**，结果实测 `SPOOFED normsg getPackageInfo` **触发 4 次** → 证明 normsg 的签名读**确实经 getPackageInfo（Java）**。
  - ⇒ **LSPatch Lv1（PM hook getPackageInfo）覆盖 normsg 签名读，Lv1 够**（高置信）。
  - **唯一没拍死**：k18 的「可见翻转」readout —— 受 **catch-22**（frida-spawn 的 normsg `u` 实例不可访问读不到 z3；normal-launch 又已过启动期签名读 + z3 疑似缓存 k18）+ 本会话设备多轮 frida 压力（script-load 超时）。残留「c$p.ea 是否另有 native 直读签名」**无任何证据**。待 fresh 设备一次性补：spawn+spoof → 交互触发 normsg → 读 k18 是否翻转。

## 十二、本会话新增工具脚本（tools/，均独立文件）

- `trace_pkg_source.js` `trace_upload_timing.js` `dump_normsg_full.js` `enum_mmtls_exports.js` `dump_req_plaintext.js` `dump_sig.js` `dump_login_coldstart.js` `trace_sig_caller.js` `test_sig_spoof.js` `gate_three_axis.js`
