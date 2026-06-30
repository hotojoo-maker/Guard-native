# 统一风控引擎设计（权威单一真源 · 一眼懂版）

> **状态**：决策已锁。A2 闸 / 时间闸 / 官方授时第二源 **已落码 committed**（见 附录B + commit 1b83346）；统一 RiskState 引擎大脑下沉 SO / 服务端真锁仍 **设计 only**（落代码按 §7 从 git 快照起）。
> **真源分工（不复写）**：时间/参数 = `配方卡_SPEC_v1.md` + `配方卡_params.json` ｜ 命脉「怎么判/怎么喂」= 研究线 `C:\Users\Me\Desktop\防封_反检测线\防封权威账_2026年6月.md` ｜ 机制设计 = 本文 ｜ 决策史/演进/侦察 = 本文附录。
> **归属**：网络安全官（机制本体 = DRM/防破解/风控）+ 防封官（防封门控面）。
> **命名规约**：`官方包` / `.mm` / `目标App`，不写品牌名。
> `PLAN.md` / `A3-0_核账_可迁清单.md` 均已于 2026-06-30 减法删除（git 可查）；原 PLAN §A.5 共享常量禁区已并入本文附录 F。

---

## 0. 30 秒施工摘要（接手先看这一节）

- **一句话**：服务端真锁 + A2 喂官方 + canary 删即自爆 + 影子期不续命 → **自己好维护、别人白嫖不值（删一下就自爆、破了也用不久，不追求破不了）**。
- **产品形态**：**打包型 APK**（模块经 LSPatch 打包进 rebuild 后的官方包），主要面向普通用户（多非 root）；**核心检测轴 = 是否官方（签名/身份）**；**root/解锁本身正常、非异常、非封因**（L1：开发机 root 一月零封、官方包读 unlocked 不 kill）。
- **★核心打法 = 中间程序「掐住官方检测的咽喉、骑在它脖子上」（一灌一读、两头通吃）**：官方包自己本来就长了一套查身份/环境的本事（查签名、查包名、查 root/越狱、查改没改包）。我方**绝不另造一套检测**（另造 = 新增检测面 = 自找被判异常），而是做一层**中间程序**卡在官方所有身份检查的**唯一咽喉**（`getPackageInfo` → native `c$p`）上：
  - **一灌（喂/灌官方态）→ 让数据正常**：所有来查身份的，到我们这儿一律**灌入官方态/正常态**（签名料→官方 `18c867f0`、android_id 料→官方签名对应 SSAID、包名料→官方）。官方自检输入尽量像官方正常数据；**不保证、不预测官方判分**（这就是 A2，§四）。
  - **一读（借官方的眼睛）→ 抓破解**：官方本来就在这口子查「签名变没变、是不是被人重打了包」。我方**顺手借它这只眼睛**读这个输入——一旦发现签名被换、包被改（= 有人在破解、白嫖我们的成品）→ 散沙 / 进影子期，**让破解者白忙、用不久**。
  - **诚实边界**：咽喉口能读到的是官方查的**输入**（签名 / 包名这些，走 Java、抓得到，§八）；官方**最终那张判分结论**它加密成 ~7KB 直发自己服务器、我方**读不到**（§九）。所以「抓破解」是**我方自己在咽喉口盯输入变没变**，不是偷看官方的判分答案。
  - **不破红线**：环境（`ro.boot.*` 那些）始终是**官方自己读**、我方**零环境读取**（守 §6 工程红线 + verifiedbootstate KPI）；中间程序只骑 `getPackageInfo` 这一层（签名 / 包名），不去碰环境读取。
- **两个闸（各管各、互不连坐）**：撤闸口径以 **SSOT §2/§3** 为准（D-020 · code-true）；下表只给概念。

  | 闸 | 管什么 | 吊在哪 |
  |---|---|---|
  | `isAntiBanReady()` | A2 防封能力 | **本地 cert 完整性 + 时间闸**（首装 72h / 自然到期 7 天 / 封停删卡 72h，fail-open；D-020/D-021，见 SSOT §2/§3） |
  | `isActive()` / `isConfigReady()` | 隐私功能（隐藏密友） | 原有 DRM + registry 加密（server-seed，不动） |

- **时间真源 = 配方卡（本文不复述数值）**：`T_soft` / `T_login` / `T_kill` / 影子期 / 各宽限 → 全看 `配方卡_SPEC_v1.md §A`。
- **到期宽限拍板（2026-06-24 C18）**：正版授权到期**不立刻撤 A2**；继续保护 **7 天续费宽限**（可加服务器抖动），宽限耗尽仍未续费才 `isAntiBanReady=false`、A2 防封料撤。短期过期 = 催续费，不等于立刻散沙。
- **硬信号只有一个**：破解者要白嫖必须改包重签 → **签名/canary 删即自爆**。其余（root/解锁/环境）= 服务器侧软风险分，客户端不碰。
- **第一步（已落码）**：`GuardRuntime.isAntiBanReady()` 已实现（`GuardRuntime.java`，含完整时间闸，committed 1b83346；见 附录B）；后续 = 真号 L4 验证 + 拆类（见 `anti-ban-gate-extract`）。

**给后续执行 AI 的硬顺序（照抄执行，不自创路线）**：

1. **P0 E9/W_dev 同盘隔离** ✅已落（`caf2142`）：`rawAndroidId` memoize 预热 → `computeDeviceMaterial` 只吃真设备材料 → `setDeviceMaterial` 早于 A2 install（`ModuleMain` 0b 段）；否则 A2 一开会把设备材料污染成官方 SSAID，W_dev 全机塌同值。
2. **P1 接 A2 三轴**：签名 / android_id / 包名路径只在官方检测咽喉灌官方值；A2 只受 `isAntiBanReady()` 门控，不连坐隐私 `isActive()`。
3. **P1 重放绑定（牙④ a 案）** ⬜未落：旧信封不能永久解 registry；**不折静态 key**（续约自锁），改 SO `unwrap_server_seed` 后比 `expire_at <= trusted_now`（官方授时 floor 防冻结），过期散沙。详 `../P_RB1_重放绑定_ReplayBind/钥匙加固_KeyHardening设计.md §3`。
4. **最后删 release 明文 fallback**：删前逐个 Filter 核账 registry 完整性 + 装机回归，禁止一口气删已验证 hook 的明文兜底。
5. **永远禁止**：root/解锁/`ro.boot.*` 作为本地散沙触发器；没有 L1/L2 证据的结论写成已证。

---

## 1. 统一引擎（一个风险机 + 一个弹窗 + 一个闸出口）

```
触发线 A（篡改/破解）─┐
触发线 B（未授权）    ─┼─→ RiskState ─→ GuardRuntime 能力闸 ─→ {隐私 hook, A2 防封 hook}
触发线 C（动红线）    ─┘            └─→ RiskPromptController.maybeShow()（唯一弹窗）
                                    └─→ 服务器强校验 ⇒ 转正/降级（唯一恢复闭环）
```

- **RiskState** = 风险等级机（判定在 SO、客户端只消费不自判）；对外只吐「给料/不给料」、不暴露内部等级（§5.3 现象统一）。
- **能力闸唯一出口**：业务/防封 hook 只问闸（`isConfigReady` / `isAntiBanReady` / `isActive`），不自己判时间/授权/风险。
- **弹窗唯一源**：所有弹窗只走 `RiskPromptController.maybeShow(reason)`（红线#9）；禁止多份隐藏弹窗。
- **失效 = 散沙（拿不到能力），不崩、不删数据、不破坏原版（官方包）**（红线#5/#6）。
- **减法**：三条触发线最后都汇成同一个结果「料解不出 → 散沙」，一个出口；不为每种触发各搞一套后果。环境异常（断网/时钟/版本漂移）并进 B 宽限，不单独立刻散。
- **黑盒化**：判定大脑下沉 `libguardcore.so` + 状态不落明文 + 散沙现象统一（详 §5.3）。目标 = 逆出来也用不久，非纯黑盒不可破（诚实）。

### 1.1 总映射表（RiskState ↔ 触发线 ↔ 三态 ↔ 动作 · 看这一张就够）

> 本表 `isAntiBanReady`(A2) 列以 **SSOT §3 场景矩阵**为准（D-020）；下表作机制叙述（触发线↔三态↔动作），A2 撤闸口径不以本表为准。

| 情形 | 触发线 | 三态归类 | `isAntiBanReady`(A2) | 隐私 | 用户可见现象 |
|---|---|---|---|---|---|
| 授权有效 | — | **正版** | 开 | 开 | 全功能 |
| 正版授权到期 | B | **正版续费宽限** | **7 天内继续开 → 宽限耗尽才撤** | 按隐私授权闸催续费/砸门 | 短期只催续费，不立刻散沙 |
| 从未授权（白嫖） | B | **观望** | 影子期内开 → 到期不续 | 登录砸门挡（`T_login`） | 看着正常 → 悄悄失效 |
| 碰蜜罐诱饵 | A | **蜜罐** | 影子期养 → 过期废 | — | 延迟弹引流 |
| 铁证篡改（签名/canary） | C | **蜜罐** | **SO 本地立刻停料**（不等服务器） | 散 | 静默散沙 → 不再保号（咽喉只剩生料，官方怎么判我方读不到） |
| root/解锁/IP 软信号 | （软分） | **蜜罐档**（服务器定） | 服务器压短租约 | — | **不本地散**，服务器裁 |

> 三态定义：**正版** = 没篡改 + 没 root（或 root 但付费验真）+ 授权；**蜜罐** = root/篡改/长期无通信；**观望** = 装了没授权。后两者都走「影子期 + 不续命」，不固定短散（反分析）。

---

## 2. 架构：一脑 + 两闸 + 三抽屉 + 三角色

**共享一套信任大脑，分开两个能力闸**：信任评分 + `RiskState` + 租约 `LeaseClock`/`EnvelopeStore` + 永久标签 + 唯一弹窗共享；`isAntiBanReady()`（A2）与 `isActive()`/`isConfigReady()`（隐私）两闸各开各、互不连坐。

### 三抽屉

| 抽屉 | 角色落点 | 只管 |
|---|---|---|
| **心跳 Heartbeat** | 腿(Java)联网 + 大脑(SO)算 | 联服务器 / 验真 / 续租约 / 算可信时间 / 攒信任分 / 决定给不给料 |
| **砸门 Enforcer** | 大脑(SO)判 + 后台裁 | 惩罚 = 暗记 + 影子租约 + 到期不续命（见 §3 触发线 C） |
| **蜜罐 Honeypot** | 现有 `CompatProbe`/`PromoConfig` | 埋假诱饵 → 碰假料 → 影子期 → 引流（= 触发线 A） |

### 三角色

| 角色 | 是谁 | 只管 | 绝不碰 |
|---|---|---|---|
| 大脑 | SO 黑盒 `libguardcore.so` | 验真 + 解料 + 算租约 + 出结论(给料/散沙) | UI / 联网 / 存明文 |
| 腿 | Java 薄层(`NativeBridge` + 各 Filter) | 联网搬运 + 问大脑要结论 + 按结论装/不装 hook + 弹唯一窗 | 自判授权/信任/风险 |
| 后台 | 服务器 `miyou-server` | 收心跳 → 算信任分 → 发种子/租约 → 永久标签 / 解绑 | （唯一能给长租约/解绑/洗白的地方） |

> 原则：单一职责 + 关注点分离 + 单一真源 + 失败关闭 + 服务器权威 + 最小权限（腿不持秘密）。

---

## 3. 三条触发线

> 三线分工：**B** 没付钱（给时间宽限）· **A** 碰假诱饵（影子期养）· **C** 破真防线铁证（立刻停料）。**「铁证立刻停料」是同一机制**（签名/canary 自有铁证）——A 的快变体与 C 的旁路指同一处，只是触发源不同。

### 触发线 A — 篡改/破解（现有蜜罐线）
- 命中蜜罐 → 影子期（表面正常、延迟弹）→ 过期 → 打开即弹引流 → 重复篡改升级。
- 蜜罐源：`CompatProbe`(canary `BASELINE`/`EXPECTED_CERT`) + `PromoConfig`(诱饵) + 载荷弹窗 canary（§5.2）+ 防封专属诱饵（假 `isAntiBanReady=true`/假官方 DER）。
- **本地立刻散沙快变体**：当篡改信号是**我方 100% 自有铁证**（签名变 / `CompatProbe.BASELINE` 动 / 载荷弹窗稳定核心段被删）→ SO 本地零误报确认 → **立刻停喂防封料**（不等服务器、不走影子期延迟），堵「服务器被截胡返回 0」洞。现象仍统一静默；服务器侧仍暗记 + 永久标签；散沙后官方怎么判是官方包自己的事（延迟、我方读不到）、模块不自爆。
- **诱饵/软信号档**（`PromoConfig` 诱饵 / 假 vip / env 软画像）**不走立刻散**，仍影子期养着 + 服务器裁。
- **禁止把 device root / 解锁当散沙触发**：会误杀解锁正版 + 读 `ro.boot.*` 踩红线#5 + 增检测面。root 只进 附录 C 服务器侧软风险分。

### 触发线 B — 未授权（防封授权闸）

> 撤闸口径以 **SSOT §3** 为准（D-020/D-021）：未授权 = 首装 72h（官方授时有值起算）/ 曾授权自然到期 7 天 / 封停删卡 72h 后撤 A2、可被重新授权恢复；隐私闸封停/到期立刻关。本线机制叙述保留。

- `isAntiBanReady()` 判（单一伪码见 §6）；挂载在 A2 hook 安装处 `if(!isAntiBanReady()) 不装/卸`，冷启动 `ModuleMain.onApplicationCreated` 算一次；软引流走 `RiskPromptController`。
- **撤闸语义（D-020/D-021）**：未授权 → 首装 72h（官方授时有值起算）/ 自然到期 7 天 / 封停删卡 72h 后撤 A2（详 SSOT §3）；盗版/篡改才走影子期+不续命（反分析），正版相关走宽限+软失效。
- 环境异常（断网/时钟回拨/版本漂移）并入本线宽限，不单独立刻散（红线#4 不误杀）。

### 触发线 C — 人为动红线（暗记 + 影子租约 + 到期不续命，不立刻封）
- **「红线」= 人为、可 100% 确认的篡改**（正版机不可能误触）：① 模块签名变（重打包）→ `EXPECTED_CERT`/registry `_CERT_SHA256` 不符；② canary 稳定常量被动（`CompatProbe.BASELINE`）；③ 真 registry / 官方料被改；④ 改真授权布尔 / 删·改真引流弹窗（载荷 canary 稳定段）；⑤ hook 模块核心授权 / native 自测函数。
- **两档分流**：**铁证（签名/canary）= SO 本地立刻停料**（决策#15）；**诱饵/软信号 = 影子期养着 + 服务器裁**。
- **不续命模型**：命中 → 上报 → 后台永久标签（该 wxid/device 永不长租约）+ 发影子租约让其觉正常 → 到期不续 → 料散沙 → 不再保号（咽喉只剩生料，官方怎么判我方读不到）。**不立刻 kill**（更难逆向 + 更简单 + 误判可服务端转正）。
- 不续命几乎免费：租约到期本就让 `isActive`/`isAntiBanReady` 变 false → 料散；只需补「检测篡改→标记→下周期不续」（服务器做，客户端断网本地兜底）。
- 「改 vip」归 A 诱饵方向（决策#12）。

---

## 4. A2 三轴（被 `isAntiBanReady()` 门控的「防封能力」）

三轴 = **签名料 / android_id 料 / 包名料**（「料」= 喂给官方的材料，下文统一这么叫、好懂）。全在 Java afterHook 喂官方（命脉/锚点详见研究线权威账 §十六/§十七）：

| 轴 | hook 点 | 喂官方 | 料 |
|---|---|---|---|
| 签名料 | `getPackageInfo(self, GET_SIGNATURES\|GET_SIGNING_CERTIFICATES)` afterHook（扩 `ModuleMain.bindSigningCert` L329） | `signatures[]`/`signingInfo` → 官方 DER | `official_der.hex`(751B/`18c867f0`) |
| android_id | `Settings.Secure.getString(_,"android_id")`（大血管 `c$p.aa/ea` 经 Java 读） | → **本机**官方签名对应 SSAID | root 实验机 `ssaid_calc.py` 算本机值一次入库；非 root 不必 runtime 读 `user_key` |
| 包名/路径 | `getPackageInfo`/`getApplicationInfo(self)`（仅 `normsg`/`bu5` caller，**不碰 `getPackageName`**/363 处会崩） | `packageName`/`sourceDir` → 官方 | — |

- **安装条件**：`isAntiBanReady() == true` 才装；否则散沙。
- **官替 / 共存差异**：签名与 android_id 两形态都要照顾（重签都会偏）；包名/路径主要是共存版额外轴，官替已占官方包名通常不需要改包名。
- **device×A2 同源必修（执行口径 2026-06-25 收敛）**：device id / W_dev 与 A2 android_id 轴同读 `ANDROID_ID`，但吃的是两种值：
  - **我方真设备材料**：`AuthManager.rawAndroidId(ctx)` 先 memoize 真值，`computeDeviceHash`(8B) / `computeDeviceMaterial`(32B) 都只吃缓存真值。
  - **灌给官方包的 SSAID**：A2 `Settings.Secure.getString(..., "android_id")` 只给官方检测路径返回「官方签名对应 SSAID」。
  - **落码 gate**：A2 install 前必须完成 `rawAndroidId` 预热 + `setDeviceMaterial`；未缓存真值则不装 A2。若 L1 发现官方包另走 `ContentResolver.query` / native 读取，再补 caller-scope 兜底；禁止无条件污染我方读点。
- **发版红线**：出包前过 `tools/gate_three_axis.js` 自检门（k33/k49/k18 全官方才放行）。

### 4.1 三轴·借眼睛可行性核账（2026-06-25，三轴不等价）

> 触发：架构师问「root 能不能借官方眼睛 → 影子模式」「android_id 算法已逆出怎么喂」。核账后**三轴借眼睛能力不等价**，记此防重复挖。详细证据 = `recon/ROOT_UNLOCK_DETECT_证据_20260625.md` + 防封线 `证据/SSAID_ANDROID_ID_ALGO_20260619.md`。

- **签名轴 ✅ 借得到（L1）**：c$p 经 Java `getPackageInfo` 读自身签名、**有 Java 返回点** → afterHook 喂官方 DER 后 `c$p.ad` 实读 `18c867f0`（L1）。**非 root 可喂**，是当前唯一稳的硬轴。
- **root / 解锁轴 ❌ 借不到官方「现成判定」（L1/L2）**：解锁（verifiedbootstate/vbmeta/flash.locked…）= 纯 native `__system_property_get`、明文不出 native、直接加密进 `field3`，**Java 边界无结果点**；root 工具（Magisk 包/无障碍/logcat）虽走 Java，但微信**只采原始输入、不给 `isRooted` 成品布尔**，判定沉 native/服务端。→ **借不到微信的 root 判定**；自判 root = 自读环境 = 踩铁律5、增检测面。**故 root 只当服务器侧弱信号，客户端不读、不本地触发**（与 §0 / §1.1 表一致；现实校准：本机 root+unlocked 冷启 6 分钟+登录未 kill，root 非即时封因）。
- **android_id 轴 ✅ 大血管喂点已证（L1）· 值本机定（L2）**：`c$p.aa/ea` **不 native 自算**，经 Java `Settings.Secure.getString("android_id")` 采集 → 中间程序在该 Java 读点 hook 喂值 → `c$p` 上传即为喂入态（`recon/A2_FEED_DIFF_KPI_实验_20260625.md`：共存 `3bd03c4c…` → 喂后 `05f894e8…`，大血管同轮读到）。**算法全球同构**：`HMAC-SHA256(user_key, BE32(len)||官方DER)[:8]`；**`user_key` 每机随机**（`settings_ssaid.xml`，root-only 可读）→ 同一官方 DER 在 A 机 official SSAID ≠ B 机（之前模型说「算法每台一样」= 公式对、**漏了 user_key 每机不同**）。**非 root 不必 runtime 读 `user_key` 才能喂**——值 = **本机**「官方 DER 对应 SSAID」（root 实验机 `ssaid_calc.py` 算一次入库/发版常量；勿喂别机的 `05f894e8`）。服务端大概率拿不到 `user_key`、难做签名↔SSAID 密码学校验 → **控大血管上传态**比「客户端现算 HMAC」更关键；只写“让数据尽量正常”，不预测官方怎么判。

**C++ 边界澄清（写死）**：C++/native **不比 Java 多一分权限**；`user_key`/环境读取是 OS 权限墙（root-only），下沉 C++ **破不了**。C++ 该下沉的是「咽喉 hook + 判定 + 灌值 + key 派生 + 防篡改」黑盒化（§5.3），不是去读权限墙后的东西。

**L4 待验清单（未实验，先记，勿写成结论）**：
1. **只喂签名（不动 android_id）够不够？**（官方认不认、含「server 能否核 SSAID」角度）→ 真号观察待补。
2. **本机 official SSAID 入库路径是否产品化？**（root 实验机 `ssaid_calc.py` 算一次 vs 发版常量 vs 首装探测）→ 喂点已证，值源待产品定；runtime 读 `user_key` 非必须。
3. **A2 喂料接产品后是否仍 KPI 中性？** → 研究线喂料 delta≈0，产品装机仍需复验。
4. **root → 影子模式**：因「借不到官方判定 + 自判破防封 + 前提（root 即时封）缺 L1」**暂不做**；除非第 1 条证实「只喂签名不够、必须 android_id 自洽」才重议（届时只能限 root 机或服务器侧软分）。

---

## 5. 防破解（A2 料锁进加密链 + 载荷弹窗 canary + 黑盒化）

### 5.1 A2 料本地化（official DER = 本地公开常量）

> A2 official DER = **本地公开常量**（非 server-seed）；场景行为以 **SSOT §3** 为准（D-020）。现口径：

- **A2 官方 DER = 本地常量**（`A2SignatureSpoof.OFFICIAL_DER_HEX`，751B / md5 `18c867f0`，**公开值**：官方证书谁都能从官方包抽，锁进种子 = 锁「功能开关」非锁「秘密」）。红线7 经用户 2026-06-25 放宽（私库 + 公开值），允许本地明文常量。
- **A2 闸 = 本地完整性、fail-OPEN**（见 §6）：所有「未被重签」的副本（首装/断网/未授权）都喂官方态、尽量让数据正常；只有读到模块证书且确证 ≠ `EXPECTED_CERT`（= 被重打包重签）才 A2 散沙。
- **划清边界（别误读）**：A2 料本地化 **≠** 动隐私 `registry_pack`。**隐私 registry（密友四链配方）仍 server-seed + 验签信封 + `decrypt_config()` 成功才解得出、仍 fail-closed**（盗版无种子 → 解不出真 registry → 隐私散沙、release 无明文 fallback）。防破解杠杆从「A2 料锁种子」改为「重签 → cert 不符 → A2 散沙 + 隐私 registry 靠重签覆盖」（SPEC §4：散沙扩面只杂项，密友四链靠 registry 重签覆盖、不靠 RiskState）。

### 5.2 载荷弹窗 canary（删弹窗 → 标记 → 影子期 → 引流，**不折 key、不自爆**）

> 2026-06-24 收敛：**否决**早前「弹窗 hash 折进 A2 key (USE)」方案（依据见末「为何 URL/USE 不折 key」）。

- **做什么**：引流弹窗当 canary 绊线，删/改弹窗 → 命中 → `markTampered` → 影子期（配方卡 7 天，可恢复）→ `RiskPromptController` 引流。逼破解者留着弹窗。
- **canary 料两条铁律**：① **每发行线恒定、绝不轮换**（现状 `CompatProbe` 比对的「诱饵 `PromoConfig` 指纹 + 模块签名 SHA-256」已符合 ✅）；② **绑「弹窗真显示」行为** → hash **弹窗稳定代码段 / 逻辑指纹**（**不是 URL 字符串**——留字符串 + NOP 显示就破；现状 ② 未做）。
- **走 COMPARE 不走 USE（逆序线铁律）**：命中 → `markTampered` → **影子期（可恢复缓冲）** → 散沙。**防封是逆序线**（误判 → 不再保号，后果未知、官方怎么判我方读不到、不预测）；canary 误判时影子期 7 天 + 可恢复给缓冲、不立刻撤。**USE（折进 key、解不出即散）在逆序线反而危险**——误判 = 直接解不出 = 立刻不保号、无缓冲。故逆序线只用 COMPARE + 影子期。
- **为何 URL / USE 不折 key（2026-06-24 否决依据）**：
  - 🔴 **URL 折 key = 换 URL 批量让旧用户不保号**：URL 要轮换（抗 Frida 换引流页 / 切备机），一换 → key 变 → 旧包 A2 料解不出 → 旧用户批量不保号（逆序线灾难）。
  - 🟡 引流 URL 当 canary 太弱：留 URL 字符串骗过 hash、只 NOP 弹窗显示。
  - 🟡 服务器 / 引流 URL 本在 C2 加密引导段解耦（防死锁），折进 key = 重新焊死、换地址要重生成料。
  - → **URL（服务器 / 引流）永不进 key**：放 C2 加密段 + 服务器 `risk_pack` 下发，随便轮换、不封旧包。
- **诚实边界**：纯客户端 canary（hash 代码 / 绑行为）动态党都能 patch；定位 = 抓静态党（90%）+ 标记，不追「必自爆」，动态党靠服务器弱信号 + 短租约。
- **工程坑**：改诱饵 / 弹窗稳定段 → 同步重算 canary 基线（见本文附录 F 共享常量禁区），否则正版误报。

### 5.3 状态黑盒化（做「灰盒」，不做「纯黑盒永不可破」）
目标不是逆不出，是逆出来也用不久：

| 黑盒层 | 做法 | 评 |
|---|---|---|
| 大脑下沉 SO | 判定从 Java 搬进 `libguardcore.so`；Java 只剩 `if(nativeReady()) 装hook` | ✅ 值得（红线#23 例外允许模块自有 SO 做状态机） |
| 真锁留服务端 | 客户端不存「答案」只问服务器；SO 被扒光也找不到永久授权 | ✅ 最硬黑盒 |
| 状态不落明文 | MMKV 不写明牌 key；4 字符短哈希 + value 加密 | ✅ 值得 |
| 弹窗/倒计时 UI | 留 Java，不硬塞 SO | ❌ 不值得 |

- 现象统一：黑盒对外只吐「给料/不给料」，逆向分不清哪条触发命中。
- 诚实边界：SO 能被 IDA 逆、Frida 能 hook 出入参；黑盒真价值 = 配合短命租约 + 设备绑定 + 服务端轮换。

---

## 6. `isAntiBanReady()` 判定 → 见 SSOT §2/§3/§7/§8（D-020 权威 · code-true）

> A2 撤闸的真值表 / 伪码 / 代码锚点 = `SSOT_A2授权防破解_统一真源.md` §2/§3/§7/§8（D-020/D-021 · code-true）。旧 D-018「未授权永不撤」伪码已废除——D-020 加首装 72h（官方授时有值起算）/ 自然到期 7 天 / 封停删卡 72h（D-021）时间闸，可被重新授权恢复（篡改不可逆，D-019）。本节只留 fail-open 理由 + 红线。

（撤闸伪码 / 真值表见 SSOT §8 代码锚点；本节不复述。canary 不进本门，仍走 `CompatProbe.check → markTampered → 影子期 → 引流`。）

- **fail-open 理由**：防封是逆序线（误判 = 撤 A2 → 不再保号，咽喉只剩生料；之后官方怎么判我方读不到、不预测），故读不到/异常一律「装」（保护优先）。改包必重签 → cert 已覆盖重打包/盗版场景。
- **未授权撤闸（D-020/D-021）**：首装超 72h / 自然到期超 7 天 / 封停删卡超 72h → 撤 A2、可被重新授权恢复（详 SSOT §3）；变现仍靠**隐私付费门 `isActive`**（四层 AND·封停/到期立刻关，SPEC §2）。
- **时间闸数值**：首装 72h / 自然到期 7 天 / 封停删卡 72h（72h 复用首装·常量，D-021）/ 临到期续费提醒 `SettingsEntry.showRenewReminderIfNeeded`；**登录砸门 `T_login` 降 v2**（无 Activity 锚点）。
- **散沙扩面（SPEC §4）= 只杂项**（防撤回/定位/通知/未读，现仅 `CallGuard` 单点）；**密友隐藏四链绝不进 RiskState 散沙**（record-only 是故意，铁律29 + 防误伤正版）；篡改时密友隐藏的散沙靠 **registry 重签覆盖**。
- **LeaseClock 仍管隐私闸授时**（A2 闸已不读时间；隐私到期判定/续费提醒仍用）：不读墙钟，服务器时间 + elapsedRealtime + 防回拨 `max_trusted_now`（红线#3）。
  - **官方授时第二源（防冻结）现状校正（L1 2026-06-27 · G92，证据真源 `recon/convtime_L1_evidence_20260627.log`，详见结论不复写于此 G10）**：
    - 锚点 = **`jy0.hd.b()`**（微信 `MicroMsg.TimeHelper` 地板值），**不是** `field_conversationTime` —— 后者要登录 + 有会话，已弃（旧「`ConvFilter.extractConvTime`→`field_conversationTime` L1 2026-05-29」口径作废）。
    - `hd.b()` **L1 实证改表杀不掉**（回拨墙钟 2 天纹丝不动、按真实秒走）；**`hd.c()` 跟墙钟、改表能拨 → 禁用**。
    - **登录依赖**：登录前微信不主动对时（jy0.hd 的 MMKV "time" 115s+ 零写入 L1）；未登录时 `hd.b()` 真值 = 「上次登录残留锚 + 单调外推」，**全新装/从没登录过 → 无值**（L3 未直测；这类无微信号可保，fail-open 装即可）。
    - ✅ **`LeaseClock.noteOfficialTime` + `OfficialClock.readOfficialNowMs` 已落码（工作区·待验证）**：`ModuleMain` 冷启反射读 `jy0.hd.b()` → `noteOfficialTime`（`max` 只抬 `max_trusted`、超现有可信+2年=丢值+`markTampered`、首值记 `officialBase` 作 72h 起算）、纯读零暴露面、拿不到/全新装 fail-open。⏳ 待 8 分支 self-test + 真机回归绿才提交。

### 红线（安全官 9 条 + 防封官 + 工程红线）
- #1（**D-020/D-021**）`isAntiBanReady` = **本地 cert 完整性 + 时间闸**（首装 72h / 自然到期 7 天 / 封停删卡 72h，fail-open）；**授权 / 租约 / server-seed 归隐私闸 `isActive`/`isConfigReady`**，不在 A2 闸内。
- #3 不信墙钟（`LeaseClock`，隐私闸用）。#4 不因单纯断网误杀（A2 fail-open 本就不撤；隐私走宽限）。
- #5/#6 散沙 = 卸能力，**不删数据、不破坏原版（官方包）**。#9 弹窗一个策略源（⚠️ 续费提醒现走独立 `AlertDialog`、未走 `RiskPromptController` = 待收口债，SPEC §4 / 块D）。
- 防封官：散沙后咽喉只剩生料，官方怎么判是平台的事、我方读不到，**模块不自爆**。KPI 红线见 §8。
- **工程红线：不新增官方包没有的检测面 + 零环境读取** —— 不扫 su/magisk/frida 文件名（`.mm` 也不扫）、**不读 `ro.boot.*`**（每读一次 verifiedbootstate +1，故零读取）。root/env 一律服务器侧软风险（附录 C）。
- **残留自爆点（押后验）**：`libilink2.so` `/proc/self/maps` 是否扫到我方注入 SO 待 L1（附录 B / §7 S7）；上线前补验。

---

## 7. 实施 checklist（唯一一套 · 决策已锁 · 客户端先行）

> 纪律：每步 = 动代码前 git 快照 → 改 → 装机回归绿 → 再下一步；守红线 #1/#3/#4/#5/#6/#9/#23/#27 + 本文附录 F 四个共享常量禁区 + 不改已验证 hook（铁律29/F-31）。本节合并原 §9 接点 + 配方卡 §C。

### 阶段一 · 客户端先行（~3-5 人天）
- **S0 快照**：动代码前 git 快照。
- **S1 建闸 `isAntiBanReady()`**：`GuardRuntime`（撤闸逻辑见 SSOT §8）= 本地 cert 完整性 + 时间闸（首装 72h 官方授时起算 / 失效 7 天，fail-open）；持久化首装时间/曾授权。验收：各分支行为对（`ANTIBAN-GATE` logcat tag）。
- **S2 wxid 登录门槛 + 本人绑定**：腿抓登录本人 wxid → 绑定材料；没登录不给料。验收：未登录不激活。
- **S3 不续命接线**：现有 `CompatProbe`→`markTampered` 的 record-only 升级成分流——铁证→永久标签+不续命；诱饵→影子期。复用租约到期→闸变 false→散。
- **S4 假 vip 诱饵 + 弹窗 canary 稳定段**：埋假 vip（简单不过度）；弹窗 canary 只哈希稳定核心段。
- 验收：隐私四链照常 + 闸/绑定/不续命/蜜罐分流行为对 + jadx 明文面不增。

### 阶段二 · 接 A2 防封（~3-5 人天）
- **S5 A2 本体接主线**：`A2SignatureSpoof`（签名料/android_id 料/包名料 喂官方）接 `ModuleMain.handleLoadPackage`(L61)：`if(isAntiBanReady()) A2SignatureSpoof.install(lpp)`；`bindSigningCert`(L329) 扩 getPackageInfo afterHook 喂官方 DER；A2 料进加密 registry（§5.1）。
- **S6 device×A2 同盘隔离（必修）**：`rawAndroidId` 唯一读点 + memoize 预热；`computeDeviceHash` / `computeDeviceMaterial` 只读缓存真值；`setDeviceMaterial` 早于 A2 install。验收：A2 开后各机 device id / `D_mat` / envelope `d` 仍各异，官方包侧 android_id 仍为官方 SSAID。
- **S7 KPI 出包前体检**：官方包跑 `frida_stats.js` 建参考 → 装 A2 后对照（环境类零读取达标、密度类异常才查，§8）；**并补 libilink2 `/proc/self/maps` L1 验**（残留 L4 / 决策#18）。

### 阶段三 · 后期
- **S8 大脑下沉 SO**：判定 + Ed25519 验签 C 化下沉 `libguardcore.so`（~5-10 天，放 A2 + KPI 之后）。
- **S9 服务端（miyou-server）**：信任评分 + 永久标签 + 解绑后台 + 周期签名下发。没接前只叫「本地加密链路」非真锁。

---

## 8. 验收（L4 + KPI）

- **机制层**：`isAntiBanReady` 各分支（授权/曾授权断网/影子期内/到期）行为正确；A2 受闸开/散沙；删弹窗 → 散沙（载荷 canary 生效）；蜜罐命中 → 影子期 → 引流；服务器强校验 → 转正。
- **足迹层（接 A2 后）**：出包前 frida_stats 体检——环境类 vbs/PROP 零读取达标、密度类 normsg/CONN 明显异常才查（数字见 CLAUDE §七 参考）。
- **终验**：真号 L4 长期不被判异常（研究线 §10.7 口径，本设计只验机制 + 足迹层）。

---

# 附录

## 附录 A. 决策台账（已锁 24 条 · 条目存档）

> 落代码以此为准；时间数值一律以配方卡为准。
> ⚠️ **下表为历史决策台账（演进存档）**；A2 撤闸现行口径以 **SSOT §2/§3 + DECISION_LOG D-020** 为准（D-020 加首装 72h / 失效 7 天时间闸，取代 D-018「未授权永不撤」）。台账条目不逐条改写。

| # | 决策 |
|---|---|
| 1 | 时间阈值全归配方卡（`T_soft`/`T_login`/`T_kill`=影子期+不续命+抖动；影子租约 7 天） |
| 2 | 散沙对象 = 仅防封 A2 进时间闸；隐私走原有 DRM |
| 3 | 惩罚模型 = 暗记 + 影子租约 + 到期不续命（不立刻 kill/封） |
| 4 | 架构 = 一脑两闸 + 三抽屉 + 三角色 |
| 5 | 绑定 = wxid + device(=android_id) 双绑；永久标签 wxid+device；解绑只后台；没登录不给料 |
| 6 | 评价体系 = 服务器算信任分默认 60%/及格；周期外置 + 服务器签名下发 |
| 7 | 黑盒化 = 大脑下沉 SO + 真锁服务端 + 状态不落明文 + 现象统一；UI 留 Java |
| 8 | 弹窗 canary = 只哈希稳定核心段 |
| 9 | 文档 = 本文单一权威（PLAN/A3-0 留档） |
| 10 | A2 本体接主线 = 接 |
| 11 | device×A2 同源 = 修（A2 android_id hook 仅 `.mm` caller） |
| 12 | 「改 vip」= 走 A 诱饵方向（简单不过度做假） |
| 13 | 时钟回拨 = 接受（首装墙钟兜底，联网后服务器收紧） |
| 14 | 大脑下沉 SO = 放后期（A2 + KPI 之后，~5-10 人天） |
| 15 | 本地立刻散沙 = 只绑自有 canary/签名铁证，不绑 device root |
| 16 | root/解锁 = 服务器侧软风险分；客户端不读 `ro.boot.*` / 不扫文件名 |
| 17 | IP = 服务器侧软风险分（精确 IP + 短时窗 + 多台命中，禁同省粒度），叠分不当闸 |
| 18 | libilink2 maps 残留 L4 = 押后（S7 补 L1） |
| 19 | 防封解体 = 去固定短散 → 影子期 + 不续命 + 服务器抖动（反分析） |
| 20 | root → 服务器软风险 → 蜜罐档；客户端不硬查；付费验真转正 |
| 21 | 无误杀正版 = 登录砸门拦截；登录砸门(`T_login`)与解体解耦，只挡隐私、A2 不撤 |
| 22 | 三态 = 正版 / 蜜罐 / 观望（见 §1.1） |
| 23 | 付费但 root = 服务器认付费即转正版 |
| 24 | 「没服务器通信」= 短期断网不算，长期才往蜜罐推 |
| 25 | 【评审建议·待拍板·20260624】牙③ W 一机一密 → **P0**：W 现全局静态明文（钥匙加固§1）+ registry key 无 per-device 料 → **一个付费用户可离线重建整条 release 的 registry key → 解明文配方公开**；落地前 server seed 解 registry 不得称「真锁」 |
| 26 | 【评审建议·机制已改 a 案·20260626】牙④ 重放绑定 → **P1**：原「不紧急」理由（转卖已被信封设备绑定堵死）论证不成立。机制 = **a 案**（SO `unwrap` 后比 `expire_at <= trusted_now`、**不折静态 key**；折 key 会让正版续约自锁，已否决）；per-device 由牙③ W_dev 设备绑定担（已落 `caf2142`）。详 `../P_RB1_重放绑定_ReplayBind/钥匙加固_KeyHardening设计.md §3`。⬜ 未落码 |
| 27 | 【评审盘点·L2·20260624】release fail-closed：取件口 `GuardRuntime` + 4 Filter 锚点字段机制 ✅ fail-closed；但明文未清零（`PushFilter` 未接 registry / ~25+ 内联硬编码混淆名 / 15 个 registry 字段挂空）；删明文 = 改已验证 Filter → 须设备在场 + 装机回归（F-31），不能一口气 |
| 28 | 【C18 指挥拍板·20260624】正版授权到期也会进 `isAntiBanReady=false`，但**不是立刻 false**：到期后给 **7 天续费宽限**（服务器时间 / `LeaseClock`，可加 6-12h 抖动），宽限内 A2 仍开；宽限耗尽仍未续费 → 服务器不续防封租约 → A2 撤 |

> 25–27 = 网络安全官评审增补（2026-06-24），**评审建议待指挥拍板**后方升「已锁」；区别于 1–24 已锁决策。证据：牙③依钥匙加固§1 +本文 §5/registry key 牙表；fail-closed = 只读源码 L2，与 skill/PROTECTION_MAP 自述一致。28 = C18 已拍板，配方卡同步落值。

## 附录 B. 现状 As-Is + 检测面（侦察实证 · 只读未改码）

**代码现状（L2）**：
- 闸/脑几乎全在 Java（`NativeBridge`/`GuardRuntime`/`RiskState`/`RiskPromptController`/`LeaseClock`/`EnvelopeStore`）；`isConfigReady`/`isActive`/`isAntiBanReady` 均已落码。**上次提交态** `isAntiBanReady = !isCardRevoked() && isIntegrityIntact()`（无时间闸）；**工作区已加时间闸**（`evalAntiBanWindow` 封停72h/到期7天/首装72h/全 fail-open，D-021）⏳ 待验证+提交（见 SSOT §8/§9）。
- **A2 已落码 live**（SSOT §6）：`A2SignatureSpoof.java` 存在、由 `ModuleMain §6.7` 在 `isAntiBanReady()`=true 时装、reboot 日志 `[A2SIG] installed`；喂官方 DER（本地公开常量）。✅ 时间闸 + 官方授时 `noteOfficialTime` + 超2年影子 + latch 可恢复 **已落码（工作区·待验证）**，待 8 分支+真机回归绿提交（D-020/D-021）。
- SO 只下沉「料」没下沉「脑」：`decrypt_config`/`registry_get_recipe`/派生 key/wxid matcher 已在 SO；`nativeIsAuthorized`=永远 AUTH_OK 占位、Ed25519 验签仍在 Java。
- 蜜罐很小：`PromoConfig`(1 诱饵) + `CompatProbe`(canary + `EXPECTED_CERT` 2 绊线)，主链 record-only；唯一真降级 = `CallGuard.active()`。`isVipAuthorized` 是真授权门。
- device id 已进真锁：`AuthManager.computeDeviceHash`(L123)=SHA256(`Settings.Secure.ANDROID_ID`) 前 8 字节，已进 envelope `d`；**全仓零 `ro.boot.*`**；⚠️ 与 A2 android_id 轴同源。

**检测面（研究线 L1/L2 汇总）**：
1. **签名 = 唯一硬判别键（L1）**：`.mm` 咽喉 `c$p` 读签名 MD5/包名/android_id；注入包被读到非官方签名（testkey `e89b158e`）→ A2 三轴喂官方就是盖这面。
2. `.mm` **不扫**进程内 LSP 桥、不扫 su/magisk/frida 文件名（L1 负向）→ 自爆风险只在签名轴。
3. **环境（root/解锁）：本地读到不即杀 = L1；服务端是否硬判 = 未实证**。`verifiedbootstate` KPI 数的是 `__system_property_get` 调用次数（基线 4/红线 38），F-19 实证我方读 `ro.boot.*` = 自伤 → **客户端零环境读取（KPI 硬约束）**，root/env 一律服务器侧软分。
4. **我方读不到 `.mm` 判定结果**：检测打成 ~7KB 加密 field3 发其服务器、Java 拿不到 → 「搭便车读官方判定」不成立 → 只能用自有信号（canary/签名）判篡改。
- 残留 L4：`libilink2.so` `/proc/self/maps` 是否对我方 SO 产生 distinct 信号未实捕，S7 补 L1。

## 附录 C. 报到信任过滤 + 绑定 + 周期外置（服务器侧）

- **报到材料**（首装上报，走签名信封防伪造）：device 指纹（=android_id 派生）+ 本人 wxid。
- **服务器侧软风险项**（加权进信任分，不单独当闸）：root/解锁画像（仅服务器从官方上报字段推断、**客户端不枚举/不扫文件名**）→ 高危机压短租约或压蜜罐档；IP（对抗指纹重置；精确 IP + 短时窗 + 多台命中，**禁同省粒度**）。真闸仍是三件：设备指纹黑名单 + 服务器扣种子 + SO 自有 canary/签名。
- **本地 vs 服务器**：服务器权威；断网用「上次签名状态 + `LeaseClock`」兜底，**只能维持/降级、不能自洗白**；屏蔽联网 ≠ 白嫖（到期 fail-closed 散）。
- **绑定**：登录本人 wxid + device 双绑；永久标签 wxid+device；解绑只后台管理员；wxid/device 不敏感不加密，但上报走签名信封。
- **周期外置**：所有周期/阈值集中配方卡一处 + 将来服务器签名下发；周期非秘密、不加密（区别于配料钥匙）。

## 附录 D. 人性诱捕链（叙事 · 按需复用，非新机制）

把已有机制（诱饵/料锁/canary/黑盒化/影子期不续命/信任档租约）编排成攻击者旅程，**不新建机制**：

1. 显眼饵（登录砸门好搜）→ 破解者本能删它 2. 删一个冒一个（第二弹窗）3. 真防线难搜+打乱混淆 4. 删/改=自爆(canary) or 中蜜罐(假开关) 5. 服务器全知默不作声（只发短租约）6. 租约暗号（正常用户租约逐步升长、篡改者永远平的一周；SO 据租约形态自读「被篡改」，无明面 isBanned 可 hook）7. 装死放饵（让他以为破解成功；配方按版本轮换、用不久传不开）8. 秋后算账（解体 + 延迟弹窗砸下来）。

**可复用模板**：显眼饵 + 难搜真防线 + 删即自爆 + 假开关蜜罐 + 影子期养着 + 租约暗号 + 延迟不续命。
**诚实边界**：Frida 动态 hook 解料出参仍是高级威胁；靠短租约 + 换版本 + 设备绑定让破解结果用不久、传不开，非无敌。

## 附录 E. 演进史（口径以正文/配方卡为准，本节仅追溯）

C36（06-22）减法 + 黑盒化 + 三触发汇一出口；C36 续 三抽屉 + 不续命 + 绑定 + 周期外置；C36 续2 决策锁定 + 实施步骤。C72（06-22）登录砸门 + 人性诱捕链。C81（06-23）配方卡为时间真源。A39（06-24）检测面 As-Is + 铁证本地立刻散 + root/IP 服务器软分 + 工程红线。A51（06-24）去固定短散 → 影子期 + 不续命 + 抖动；登录砸门与解体解耦；root → 蜜罐档、付费验真转正；三态定义。

## 附录 F. 共享常量禁区（原 `PLAN §A.5`，2026-06-30 并入）

> 归一 / 改配方 / 换证书时**绝不能碰**的 4 个共享常量；碰了会连带炸蜜罐 / 正版误伤。原 `PLAN.md §A.5`（蜜罐隔离核查 2026-06-21）随 PLAN 减法删除，核心约束并入本文。

1. **`_SEG_A/B/C`（key 段）+ `_CERT_SHA256`**：被 `gen_registry_cipher.py` / `gen_bootstrap_cipher.py` / `config_crypto.cpp` 三处共享。归一只改 json 内容 + 接线，**绝不动 key 派生 / 段常量 / cert**，否则 registry 和 bootstrap 一起解不开。
2. **`CompatProbe.BASELINE` / `PromoConfig` 任一字面量**：**不碰 PromoConfig**（它是诱饵、不是死配置，别顺手删）；BASELINE 现【构建期自动从 PromoConfig 算】（`build.gradle computeCanaryBaseline` → `BuildConfig.CANARY_BASELINE`），改诱饵重编自动跟随。
3. **`CompatProbe.EXPECTED_CERT` = registry `_CERT_SHA256`**（官替 `e3e13a49` / 共存 `8f47a47a`，debug `ca421ec3` 退役中）：换证书时三处 + 运行时 `setBindingMaterial` 必须一起改。
4. **`NativeBridge.getEndpoint`（引流 URL 出口）**：归一只动 `getRecipe`，绝不混进 `getEndpoint`（引流 URL 走 bootstrap/`getEndpoint`，hook 配方走 registry/`getRecipe`，两条出口分开）。

---

# End（设计 only · 决策已锁；落代码按 §7 从 S0 git 快照起；本文 = 单一权威真源）
