# 防封授权闸 实施 SPEC（配方卡 · 定稿 v1）

> 建立：2026-06-23（C82 授权检查官/执行 落盘，D-017 指挥拍板后）
> 用途：把 `DESIGN.md`/`PLAN.md` 的「为什么」提炼成 AI 能机械照做的「怎么做」。单值、无矛盾。
> 时间口径以 **A51 指挥拍板（2026-06-24）**为准：**T_kill 去掉固定短散沙，改「影子期 7 天 + 不续命 + 服务器抖动」**（像租约自然到期、反分析）；T_soft=1h / T_login=2h（只挡隐私功能、A2 不撤）沿用。旧 24h（C81）/ A51 中途 2h / 72h（C36）仅演进存档。
> 机器可读副本：同目录 `配方卡_params.json`（A 参数表 + isAntiBanReady 真值表）。
> 成色：L1 动态实证 / L2 静态实证 / L4 待验证 / 设计only（仅设计、无证据）。
>
> **铁律：A2 是「装了就跑、跑了就生效」的开机默认保护（倒序：装→跑→生效，全在登录之前）；从未授权**不设固定短散沙**，走影子期（7 天）+ 不续命 + 服务器抖动 → 到期像租约自然失效才撤（A51 去掉 2h/24h 固定短散、覆盖 C81）。登录/授权是另一条线（解锁隐私功能），不进 A2 判定；登录砸门只挡隐私功能、A2 不撤。**

---

## A. 单值参数表（每阈值只出现一次）

| 参数 | 值 | 含义 | 谁判 | 成色 |
|---|---|---|---|---|
| T_soft 软引流起点 | **1h** | 从未授权满 1h → 起**可关**软引流弹窗 | 服务器/客户端兜底 | 设计only |
| T_login 登录砸门 | **2h** | 从未授权满 2h → 弹**关不掉**登录砸门、强制输授权码（输一次后不再弹）；只挡进隐私功能、不挡官方包、不动 A2；**非风号** | 服务器/客户端兜底 | 设计only |
| T_kill 防封解体 | **影子期7天+不续命+服务器抖动** | 从未授权不设固定短散；A2 影子期内仍开/看着正常 → 到期不续命 → A2 像租约自然到期悄悄失效 → 宿主读非官方 → 平台自判/封；模块不自爆（A51：去掉 2h/24h 固定短散，反分析） | 服务器主控 / 客户端按影子期兜底 | 设计only（A51 指挥拍板 2026-06-24，覆盖 C81 24h） |
| 付费断网宽限（曾授权+断网） | **7天** | 付费用户掉线不误杀 | 客户端(LeaseClock)/服务器 | 设计only |
| 蜜罐影子期（触发线A） | **7天** | 碰假诱饵 → 延迟弹窗期 | 服务器（现客户端硬编码） | L2（SHADOW_HOURS 现硬编码 7天） |
| 红线影子租约（触发线C） | **7天** | 铁证篡改 → 影子租约让其觉正常 → 到期不续命 | 服务器 | 设计only（原 7-10 → 定 7） |
| 隐私功能离线宽限 | **72h** | 会话/通讯录/朋友圈/搜索 断网宽限（**独立闸、非 A2 时间闸，在功能界面弹**） | 客户端(LeaseClock)/服务器 | L2/已实现（配方卡只标注、不重做） |
| 信任分及格线 | **60%（占位）** | 一周累计 ≥60% 及格 → 长租约 | **服务器判分**（红线#1）；客户端只上报、不自评/不自封信任档 | 设计only（占位，待第4步 KPI 基线校准） |
| 心跳起步 | **2-3min 带抖动，激活即停** | 首装短促抓激活 | 客户端调频/服务器签发 | 设计only |
| 心跳回落 | **10-30min（probe）** | 激活后 | 同上 | 设计only（PLAN §3.6） |
| 心跳稳定 | **1-2h（封顶 6h，±15% 抖动）** | 稳定态 | 同上 | L2（GuardHeartbeat 已实装） |
| 心跳嫌疑/蜜罐 | **10min 升频** | 密集观察 | 服务器 | L2（已实装） |
| CONN 密度红线 | **0.5** | 心跳频率硬约束，**测出 >0.5 即停** | 验收门 | L2（CLAUDE §七） |
| verifiedbootstate 红线 | **38** | KPI 上限 = `__system_property_get` **调用次数**（非 orange/green 值）；官方读 root/解锁三件套是它的额度，我方读 `ro.boot.*` 每次 +1 → **客户端零新增环境读取**（硬约束） | 验收门 | L1（P15 基线 4） |
| 设备绑定 | **wxid + device(=android_id)** | 永久标签按 wxid+device | 服务器 | L2（computeDeviceHash 已有） |
| 解绑 | **仅后台管理员** | 用户自解不了 | 服务器 | 设计only |

> 「激活」定义：暂定 = 登录成功拿到本人 wxid；料无明文 → 标 **设计only**（待 S9 服务器口径确认）。

---

## B. 状态真值表 — isAntiBanReady() 单一定义

> 本闸**只决定 A2 装/卸**；**不检查登录、不检查隐私授权**。隐私功能走原有 DRM（isConfigReady/isActive）+ registry，两闸独立、互不连坐（DESIGN §1/§2）。
> 哑端优先：在线时直接用服务器签名结论；下列伪码 = 断网本地兜底（红线#1：布尔不可信，吊 EnvelopeStore + LeaseClock）。

```
isAntiBanReady():            # 只决 A2 装/卸；默认开机保护，不受登录/授权门控
  if EnvelopeStore.isAuthorizedNow(): return true   # 已授权
  if 曾授权过:  return (LeaseClock 租约未到期 或 离线宽限 7天内)  # 付费断网
  age = LeaseClock.trustedNow() - 首装时间          # 红线#3 不信墙钟；仅用于 1h/2h 的 UI 副作用
  # 从未授权(观望/白嫖): 不设固定短散沙; A2 由影子租约决定(server-issued, 默认 7天 ± 抖动; 断网本地按影子期兜底)
  if 影子租约未到期: return true              # 影子期内 A2 仍开、看着正常（默认保护）
  return false                                # 影子租约到期不续 → 撤 A2（像租约自然到期, fail-closed）
# 软引流(1h)/登录砸门(2h) = age 触发的 UI 副作用，走唯一 RiskPromptController；
# 登录砸门只挡进隐私功能、A2 不撤（与解体解耦）；A2 撤只看影子租约到期/不续命，不看固定小时数（A51 反分析）
```

| 分支 | 条件 | isAntiBanReady（A2） | UI/隐私门副作用 | 成色 |
|---|---|:--:|---|---|
| 已授权 | isAuthorizedNow | **true** | 无 | 设计only |
| 付费断网 | 曾授权 & 租约/7天内 | **true** | 无 | 设计only |
| 首装早期 | 从未授权 & age<1h | **true** | 无 | 设计only |
| 软引流期 | 从未授权 & 1h≤age<2h | **true** | 软引流弹窗（可关） | 设计only |
| 登录砸门期 | 从未授权 & age≥2h & 影子租约未到期 | **true** | 登录砸门（关不掉，只挡进隐私功能；A2 仍开） | 设计only（A51：与解体解耦） |
| 解体 | 从未授权 & 影子租约到期不续 | **false** | A2 像租约自然到期悄悄失效 → 平台自封；**无固定小时阈值**（影子期 7天 ± 服务器抖动） | 设计only（A51：去掉固定短散沙、反分析） |

---

## C. 有序落地 checklist（架构师 5 步；每步独立 git 快照 + 装机回归绿了再下一步）

> **S0 = git 快照**。验收：`git log --oneline -1` 见快照提交 = PASS。

**① 配方卡（本文）**—锁定本 SPEC。PASS：指挥签字、本表无 ❓（文档步）。

**② 哑客户端靠服务器**—GuardRuntime 新增 isAntiBanReady()（吊 EnvelopeStore+LeaseClock；**不检查登录**）；持久化 2 值（首装时间=Bridge 新键 / 曾授权=EnvelopeStore）；在线用服务器签名结论、断网才本地兜底、**本地只降级不自升级**。
  - 验收（一条）：冷启后 `adb logcat -d | findstr ANTIBAN-GATE`
  - PASS：`ready=true reason=fresh src=local`；模拟影子租约到期不续 → `ready=false reason=lease_expire`（A51：解体走影子期+不续命、无固定小时阈值）；联网 `src=server`；各分支行为对。
  - 成色：设计only / L4 待验证（探针需做时间注入小钩）。

**③ A2 接主线 + 修 android_id 同源**—A2SignatureSpoof 接 ModuleMain.handleLoadPackage `if(isAntiBanReady()) install`（影子期内默认 true → 冷启即跑、在登录前；A51：解体走影子期+不续命、非固定小时）；料进加密 registry；**A2 android_id hook 仅官方包 caller**、放过 computeDeviceHash。
  - 验收（沿用研究线 §10.7 已证范式）：
```
$p=(adb -s <设备> shell "pidof <目标包名>").Trim().Split(' ')[0]
frida -D <设备> -p $p -l 脚本\a2_probe_mn.js -o a2_verify.txt
adb -s <设备> shell input keyevent 3
adb -s <设备> shell monkey -p <目标包名> -c android.intent.category.LAUNCHER 1
```
  - PASS：`c$p.aa/ad` 读 `toByteArray len=751 md5=18c867f0717aa67b2ab7347505ba07ed`；他包签名不变；android_id=官方 SSAID（本机 `05f894e8e1e260fa`）；登录不崩。
  - 成色：A2 签名/android_id 机制 **L1 已证**（防封账 §八/§九/§十.8）；接主线集成 **L4 待验证**。
  - ⚠️ 治理门：本步受 **D-015 阶段铁律 + D-017 拍板**约束（见 DESIGN 附录A 决策10）。

**④ KPI 基线**—官方包（无模块）跑 frida_stats.js 建零点 → 装模块后对照。
  - 验收：`frida -p <pid> -l 脚本\frida_stats.js`（先官方、后模块各一次）
  - PASS：verifiedbootstate≤38 / PROP/100K≤220 / normsg/100K≤5124 / CONN≤0.5。
  - 成色：设计only；**P18 零点未建（F-22）→ 此步是补零点的前提；60%信任分/心跳频率在此之前不能宣称安全**。

**⑤ 判定核心搬 C++ 黑盒**—isAntiBanReady/宽限计算 + Ed25519 验签 下沉 libguardcore.so；状态不落明文。
  - 验收：`jadx 搜 isAntiBanReady/grace/isVip` + MMKV dump 查明文 key
  - PASS：Java 仅剩 `if(nativeReady()) 装hook`；无明文判定；dump 无 `grace_until/isVip/risk_level`。
  - 成色：设计only；放后期（A2+KPI 之后，~5-10 人天）。守铁律#23（仅自有 SO，禁注入微信 JNI）+#27（nativeInit 先）。

---

## D. 雷区/硬闸（「测不过就停」）

| # | 硬闸 | 测试 | 测不过就停 | 来源 |
|---|---|---|---|---|
| D1 | android_id 同源 | A2 接后两台不同机冷启，envelope `d`（device hash）仍不同 | 两机 `d` 塌成同一官方值 → 设备绑定全废；A2 android_id 必仅官方包 caller、放过 computeDeviceHash | 防封账§9 / DESIGN 附录B·附录C / S6 |
| D2 | 撤明文顺序 | 先进 registry+getRecipe 验活（entries=N、recipes ok、隐私四链绿）再撤 Java 明文 | 没验活就撤明文 → 正版机解密抖动时密友暴露 | PLAN A.1/A.3 / DESIGN§5.1 |
| D3 | native 最小化 | native 仅自有 SO 状态机/加密/验签/wxid；不 dlopen 微信 SO、不注入微信 JNI | 注入微信 JNI → CodecLooper SIGSEGV+强制下线 | 铁律#23/F-23 |
| D4 | 不改已验证 hook | 隐私四链+来电/通知 回归仍绿；不动已 PASS hook | 顺手优化已验证 hook → 静默失效无报错 | 铁律#29/F-31 |
| D5 | fail-closed | 无 server seed → registry scatter → release 敏感 hook 不装；解不开=散沙 | 解不开回退明文 / release 留明文 fallback → 逆向直抄配方绕过 SO 链 | DESIGN§5.1 / PLAN A.1 |

---

## E. 成色总账

- **L1 已证**：A2 签名轴 Java 可 hook+喂官方→c$p 读官方（防封账§八/§十.8）；SSAID 算法+`05f894e8`（§九）；包名 cmdline 冷启=0（§13.4）；签名轴无隐藏血管（§十二，L1+L2）。
- **L2**：isAntiBanReady 全仓不存在/需新建（GuardRuntime 现仅 isConfigReady）；主线零 A2 代码（PLAN§3.1，主线 Glob 零 A2SignatureSpoof.java）；隐私 72h 离线已实现（LeaseClock GRACE_DEGRADE 72h）；心跳稳定态/蜜罐已实装；设备绑定（computeDeviceHash）。
- **设计only**：所有时间阈值（1h 软引流 / 2h 登录砸门 / 解体=影子期7天+不续命〔A51 去掉固定短散〕）+ 60%信任分 + 心跳起步（架构师拍板、无 KPI 实证）。

---

## F. 版本适用面（D-017：按版本轴组织 + 上报带版本标识）

> 版本轴三类：**官替**（占 `com.tencent.mm`、非官方签名）/ **共存**（改包名、与官方并存）/ **管理**（miyou-server 后台）。

| 参数/步骤 | 官替 | 共存 | 管理(后台) | 备注 |
|---|:--:|:--:|:--:|---|
| A2 签名轴（official_der 喂官方） | ✅ | ✅ | — | 两形态都非官方签名，都要喂（防封账§十一） |
| A2 android_id 轴（官方 SSAID） | ✅ | ✅ | — | SSAID 由签名 key 派生，两形态都偏移 |
| A2 包名/路径轴 | — | ✅ | — | 官替已占官方包名、不需要；共存包名≠官方→需灌官方值（§十三） |
| isAntiBanReady 时间闸（1h 软引流 / 2h 登录砸门；解体=影子期7天+不续命，A51） | ✅ | ✅ | — | 模块内运行，两形态共用 |
| 隐私 72h 离线宽限 | ✅ | ✅ | — | 隐私 DRM，模块内运行 |
| 心跳节奏（起步/回落/稳定/嫌疑） | ✅客户端 | ✅客户端 | ✅签发 | 客户端调频、后台签名下发周期 |
| 信任分 60% / 信任档 q | — | — | ✅ | 服务器算分（红线#1） |
| 蜜罐影子期 7天 / 红线影子租约 7天 / 不续命 | △触发 | △触发 | ✅裁决 | 客户端命中上报，后台裁决/发租约 |
| 设备绑定 wxid+device / 永久标签 / 解绑 | △上报 | △上报 | ✅ | 绑定/解绑只后台 |
| KPI 红线（CONN 0.5 / vbs 38） | ✅验收 | ✅验收 | — | 发版前验收门 |

**D-017 上报口径**：客户端每次上报（心跳/激活）**必须携带「包名 + 版本标识(release_id)」**，供后台**按版本统计**（官替/共存/各 release 的激活量、信任分布、异常率）并识别错配/盗版。包名+release_id 非秘密 → 明文上报，但走签名信封防伪造。

---

## G. 包名/打包 MD5 校验方案（只设计文本，不动 build/tools；那块归指挥审）

> 目的：用「包名 + 打包产物 MD5」做**版本身份指纹**，避免官替/共存/各 release 之间料错配、混乱、盗版冒充。对齐现有 `tools/gate_three_axis.js` 自检门 + registry cert-fold「MD5 即指纹」模式。

### G.1 校验对象（checksum 谁）
1. **包名 k33**：官替=`com.tencent.mm` / 共存=独立包名（每形态固定值）。
2. **打包产物 MD5**：最终 APK 的 MD5；以及关键内嵌料各自 MD5——`registry_cipher.inc`、`bootstrap_cipher.inc`、`official_der.hex`。
3. **签名证书 SHA-256**：模块/宿主签名证书指纹（= registry key 折进的 `_CERT_SHA256`，cert 错→registry scatter）。
4. **A2 官方 DER 基线 MD5 = `18c867f0717aa67b2ab7347505ba07ed`**（与 gate_three_axis k18 同一基线）。

### G.2 校验物放哪（release_manifest）
- 一份**明文版本清单** `release_manifest.json`（非秘密、不进 registry_pack），每条 release 一行：
  `{ release_id, 形态(official_replace|coexist), package_name, apk_md5, registry_cipher_md5, official_der_md5, cert_sha256, build_date }`。
- 存两处：① **发版机本地**（出包流水线读，做发版门断言）；② **miyou-server 后台**（按版本统计 + 校验客户端上报的包名/release_id）。

### G.3 何时跑
1. **发版前（出包流水线自检门）**：扩 `gate_three_axis.js` 同款模式——断言 `official_der_md5==18c867f0…` + `registry_cipher_md5` 与清单一致 + 包名与形态匹配（官替必 `com.tencent.mm`，共存必独立包名）。**任一不符 = FAIL = 阻发**。
2. **装机后 L1**：adb 抓自检日志确认装的就是清单里那份（`[GATE]`/`[native] certBind`/`registrySummary`）。
3. **后台登记**：客户端上报带 `package_name + release_id`（+ 可选 apk_md5），后台比对清单 → 按版本统计 + 识别错配/盗版/冒充。

### G.4 对齐现有（不重造）
- 复用 `gate_three_axis.js` 的 **k18=18c867f0 签名 MD5 断言**（已有发版门范式）。
- 复用 **cert-fold**（`bindSigningCert → NativeBridge.setBindingMaterial → derive_registry_key`，证书错→registry 散沙）的「指纹即真锁材料」模式；MD5 校验物本身只做**完整性/防错配指纹**，不当真锁。

### G.5 边界（诚实）
- MD5 = 完整性指纹，防混乱/错配/冒充，**非防破解真锁**（真锁仍是 server seed + 短租约 + 设备绑定）。
- **运行时** APK/dex 自校验属阶段⑤ P32 `SignatureGuard` 范围；本方案只到**发版门 + 后台登记**，**不加运行时代码**（运行时校验要指挥审后单独排）。

---

## 仍待后续（S9 / KPI）—诚实缺口，别编
1. **登录砸门弹窗实现锚点**：新增，三份料无 hook 点/Activity 锚点/代码（现仅 FunnelPrompt 可关闭引流窗）→ **设计only**。
2. **服务器下发 isAntiBanReady 结论的信封协议/字段** → **待 S9**（miyou-server）。
3. **KPI 零点** → **待第4步**建（F-22）。
4. **「激活」定义**（登录成功拿本人 wxid？心跳成功？授权成功？）→ 暂定登录成功拿 wxid，料无明文 → **设计only**。
5. **C++ 下沉函数级清单**（哪些下沉、哪些留 Java）→ **待第5步**（DESIGN §5.3 只给原则）。

# End（配方卡定稿 v1；落代码按 C 节从 S0 git 快照起；step③ 受 D-015/D-017 治理门约束）
