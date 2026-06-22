# 统一风控引擎设计（权威单一真源）— 信任大脑 × 三抽屉(心跳/砸门/蜜罐) × 防封授权闸 × A2 三轴 × 不续命惩罚 × 状态黑盒化 × wxid/device 绑定

> 建立：2026-06-21 / 修订：2026-06-22（C36：减法+黑盒化+三触发汇一出口；C36 续：三抽屉+信任大脑两出口+不续命惩罚+状态机/评价体系+wxid/device 绑定+周期外置+As-Is；C36 续2：**决策锁定**[A2 接/device×A2 修/改 vip 走 A/时钟接受/SO 下沉后期] + 实施步骤 §9A）/ 状态：**⬜ 设计 only 决策已锁，未改任何代码**（落代码 = git 快照 + 按 §9A 步骤 + 装机回归）
> **本文 = 防封黑盒设计权威单一真源**；`PLAN.md` / `A3-0_核账_可迁清单.md` 留档（早期工作计划/核账，以本文为准）。
> 归属：**网络安全官**（机制本体 = DRM/防破解/风控）+ **防封官**（只认「防封被门控」这一面）。
> 命脉单一真源（怎么判/怎么喂）→ 研究线 `C:\Users\Me\Desktop\防封_反检测线\防封权威账_2026年6月.md`（§十~§十七）；本文只设计「防封能力的门控 + 防破解 + 引流」机制，不复述命脉结论。
> 关联：`PLAN.md §三`（isAntiBanReady 骨架）· 安全官 skill（RiskState/RiskPromptController/LeaseClock/EncryptedConfigLoader）· `docs/HONEYPOT_蜜罐设计.md` · `PROTECTION_MAP.md`。

---

## 北极星 · 总原则（用户定调 2026-06-21）

**好维护（我们自己）+ 难破解（别人）——不追求「破不了」（天下没有破不了），只追求「破解成本高 + 收益寿命短」。**

- **好维护** = 单一真源（命脉→研究线权威账 / 设计→本文 / 准则→skill，三处不复写）+ 粗粒度（A2=一个 getPackageInfo afterHook，不碎拆）+ 一个引擎（RiskState+唯一弹窗+GuardRuntime）+ 版本化（registry 按 versionCode 分块、脚本生成，不手维护多份、源码不分叉）。
- **难破解** = 真锁在服务端（服务器种子 + 短命租约 + 设备绑定，客户端无永久材料）+ 被封威慑（白嫖→散沙→号被封）+ 载荷弹窗自废 + 蜜罐影子期；客户端混淆只增成本、**不算真锁**。
- **非破不了（诚实）** = Frida 运行时 hook `decrypt_config()` 出参防不死 → 靠短命租约 + 设备绑定 + 服务端轮换让「破解结果用不久、传不开」。
- **平衡铁律**：抗破解手段（载荷 canary / 料进加密）只钉**稳定核心段** + **构建脚本自动生成**加密料，把维护成本压回来——绝不为抗破解把源码拆碎、手维护多份（违背好维护）。

## 0. 一句话

**你原有的「蜜罐 / 引流 / 延迟弹窗」和新的「防封授权闸（首装宽限→超时散沙，C36：1h 软 / 72h 散）+ A2 三轴 + 载荷弹窗 canary + 人为红线即封」本来就是一套**——全部汇到**一个 `RiskState` 等级机 + 一个 `RiskPromptController` 弹窗源 + 一个 `GuardRuntime` 能力闸**。新东西 = 多接一条触发线、多挂一个能力，**不另起炉灶**。

---

## 0.5 现状 As-Is（C35 三份侦察实证 · 2026-06-22 · 只读未改码）

> 落地从「现状」起步，避免给空气加固。事实带文件/行号，证据级 L2 为主。

- **闸/脑几乎全在 Java**：`NativeBridge`/`GuardRuntime`/`RiskState`/`RiskPromptController`/`LeaseClock`/`EnvelopeStore` 都在 Java；`isConfigReady`/`isActive` 是真实现，**`isAntiBanReady` 全仓不存在**（需新建）。
- **主线零防封 A2 代码**（L2 核实 `PLAN §3.1`）：无微信 getPackageInfo afterHook 喂官方 DER、无 android_id/包名 spoof、无 `A2SignatureSpoof.java`；现有 getPackageInfo 仅两处都读**自己**模块证书（`ModuleMain.bindSigningCert` L280 / `CompatProbe` L76）。
- **SO 只下沉了「料」没下沉「脑」**：`decrypt_config`/`registry_get_recipe`/`unwrap_server_seed`/派生 key/wxid matcher 已在 SO；但 `nativeIsAuthorized`=永远 AUTH_OK 占位、**Ed25519 验签仍在 Java**、SO 无 `verify_envelope`。
- **蜜罐很小**：仅 `PromoConfig`(1 诱饵) + `CompatProbe`(canary `BASELINE` + 签名 `EXPECTED_CERT` 2 绊线)，全走 A 线 7 天影子期；**主链 record-only**，唯一真降级 = `CallGuard.active()`（盗版过 7 天来电拦截失效）；K 系/假 vip/假 isAntiBanReady/假官方 DER **都没落地**。`isVipAuthorized` 是**真授权门**（非假锁，旧文档已作废）。
- **device id 已有且已进真锁**：`AuthManager.computeDeviceHash`(L104) = SHA256(`Settings.Secure.ANDROID_ID`) 前 8 字节；已进 envelope 设备绑定 `d`（`AuthEnvelopeVerifier` L103，Ed25519）；**全仓零 `ro.boot.*`（红线#5 净）**；⚠️ 与 A2 android_id 轴**同源**（同一 API）。
- **引流出口独立**：funnel URL 走 `bootstrap_cipher`（cert-only，不折服务器种子），与 registry（折种子）两条独立管线。

---

## 1. 统一引擎（单一真源，红线#9：一个弹窗策略源）

```
触发线 A（篡改/破解）─┐
                      ├─→ RiskState(L0–L6) ─→ GuardRuntime 能力闸 ─→ {隐私 hook, A2 防封 hook}
触发线 B（未授权超时）─┘                      └─→ RiskPromptController.maybeShow(reason)（唯一弹窗）
                                              └─→ 服务器强校验 ⇒ 转正/降级（唯一恢复闭环）
```

- **能力闸**：`GuardRuntime.isConfigReady()` / `isAntiBanReady()` / `StateMachine.isActive()` 统一出口；业务/防封 hook 只问闸，不自己判时间/授权/风险。
- **唯一弹窗**：所有引流弹窗只走 `RiskPromptController.maybeShow(reason)`；可多触发点，**禁止多份隐藏弹窗**。
- **不崩、不删数据、不破坏宿主**：失效 = 能力拿不到（散沙），不是崩溃/清数据（红线#5/#6）。
- **★闸的范围（用户拍板 2026-06-21）**：`isAntiBanReady()` 的 时间闸**只门控 A2 防封能力**；**隐私功能**（会话/通讯录/朋友圈/搜索 隐藏密友）由**原有 DRM** `isConfigReady()`/`isActive()` + registry 加密门控，**不进时间闸、互不散沙**。两闸独立、各管各的能力，只是都向同一 `RiskState`/`RiskPromptController` 汇报。
- **★减法（C36，2026-06-22）**：**三条**触发线（见 §2）最后都汇成同一个结果「料解不出 → 散沙」，不为每种触发各搞一套后果。砍掉两块：①「环境异常（断网/时钟/ROM/版本漂移）立刻散」→ 并进 B 宽限（红线#4 不误杀）；②「客户端自判 vip/信任档/风险」→ 只留 GuardRuntime 一个出口（少 hook 点）。**少 = 好维护 + 少破绽。**
- **★状态黑盒化（C36，详见 §4.3）**：判定大脑下沉 `libguardcore.so` + 状态不落明文 + 散沙现象统一；弹窗/倒计时 UI 留 Java（不硬塞 SO）。目标 = 逆出来也用不久，**非纯黑盒不可破**（诚实）。

---

## 1A. 架构：一个信任大脑 + 两个能力出口（C36 续）

**既不捆死成一个、也不两个各搞一套**：共享一套信任，分开两个能力闸。

- **共享（一套信任大脑）**：信任评分 + 状态机 `RiskState` + 租约 `LeaseClock`/`EnvelopeStore` + 永久标签 + 唯一弹窗 `RiskPromptController`。
- **分开（两个能力闸）**：防封能力 `isAntiBanReady()`（A2）一个闸、隐私能力 `isActive()`/`isConfigReady()`（隐藏密友）一个闸；各自开/关、互不连坐。

### 三抽屉（职责划分 · 好维护/AI 一目了然）

| 抽屉 | 角色落点 | 只管 |
|---|---|---|
| **心跳 Heartbeat** | 腿(Java)联网 + 大脑(SO)算 | 联服务器 / 验真 / 续租约 / 算可信时间 / 攒信任评分（≥60% 给长租约）/ 决定给不给料 |
| **砸门 Enforcer** | 大脑(SO)判 + 后台裁 | 惩罚 = 暗记 + 影子租约(7-10天) + **到期不续命**（见 §2 触发线 C / §5）|
| **蜜罐 Honeypot** | 现有 `CompatProbe`/`PromoConfig` | 埋假诱饵 → 碰假料 → 影子期 → 引流（= 触发线 A）|

三抽屉**共用**一个出口（给不给料）+ 一个弹窗 + 一个风险机 = 减法。

### 三角色职责（软件工程 + 网络安全）

| 角色 | 是谁 | 只管 | 绝不碰 |
|---|---|---|---|
| 大脑 | SO 黑盒 `libguardcore.so` | 验真 + 解料 + 算租约 + 出结论(给料/散沙) | UI / 联网 / 存明文 |
| 腿 | Java 薄层(`NativeBridge`+各 Filter) | 联网搬运 + 问大脑要结论 + 按结论装/不装 hook + 弹唯一窗 | 自判授权/信任/风险 |
| 后台 | 服务器 `miyou-server` | 收心跳 → 算信任分 → 发种子/租约 → 永久标签 / 解绑 | （唯一能给长租约/解绑/洗白的地方）|

> 原则：单一职责 + 关注点分离 + 单一真源（料一处/弹窗一处/评分一处）；失败关闭 + 服务器权威 + 最小权限（腿不持秘密）+ 粗粒度纵深。

---

## 2. 三条触发线

### 触发线 A — 篡改/破解（你原有的蜜罐线，已存在）
`命中蜜罐` →  `L4 TAMPER_SHADOW`（影子期，表面正常、延迟弹）→ 过影子期 → `L5 TAMPER_FUNNEL`（打开即弹引流）→ 重复篡改 `L6`。
- 蜜罐源：`CompatProbe`(canary `BASELINE`/`EXPECTED_CERT`) + `PromoConfig`(诱饵) + **载荷弹窗 canary**（§4）+ 防封专属诱饵（假 `isAntiBanReady=true`/假官方 DER）。
- 延迟弹窗 = 影子期 `SHADOW_HOURS`（当前硬编码 7 天，应迁服务端 `risk_pack`）。

### 触发线 B — 未授权超时（新防封授权闸）
`isAntiBanReady()` 按时间判：
```
isAntiBanReady():
  if EnvelopeStore.isAuthorizedNow(): return true            # 授权 → 长期开
  if 曾授权过: return 正常宽限(GRACE 24/72h)                  # 不误伤付费断网
  age = LeaseClock.trustedNow() - 首装时间
  if age < T_grace:  return true                             # 首装宽限 → 开（T_soft 起软引流）
  return false                                               # 从未授权 + 超时 → 防封散沙
```
- **挂载**：A2 hook 安装处 `if(!isAntiBanReady()) 不装/卸`；冷启动 `ModuleMain.onApplicationCreated §6.5` 算一次（与 `RiskState.evaluate` 同处）；软引流走 `RiskPromptController`。
- **超时散沙语义**：从未授权 + 超 `T_kill` → 卸 A2 → 宿主读到非官方身份 → 平台自己判非官方（用「被封」反制白嫖；模块不自爆、不留痕）。
- **C36 阈值**：`T_soft=1h`（起软引流）/ `T_kill=72h`（真散→封号）。
- **环境异常并入本线（减法）**：断网 / 时钟回拨 / ROM 差异 / 微信版本漂移 **不单独立刻散**，并进本线宽限（红线#4 不误杀真用户）。

### 触发线 C — 人为动红线（C36 续：改为「暗记 + 影子租约 + 到期不续命」，不再立刻封）
> 用户定调（C36 续，2026-06-22）：**不立刻 kill、不立刻封**。检测到铁证篡改 → 服务器暗记、表面照常发 7-10 天影子租约让其觉正常 → 暗地永久标签 → **下个租约周期不给续命** → 租约到期 → 料散沙 → 该封的封。

- **「红线」= 人为、可 100% 确认的篡改**（正版机不可能误触）：
  1. 模块签名变了（被重打包）→ `EXPECTED_CERT` / registry `_CERT_SHA256` 不符。
  2. canary 稳定常量被动过（`CompatProbe.BASELINE`）。
  3. 真 registry / 官方料（official DER 等）被改。
  4. 改真授权布尔 / 删·改真引流弹窗（载荷 canary 稳定段）。
  5. hook 模块核心授权 / native 自测函数（可检测的那几个）。
- **后果（不续命模型）**：命中任一 → 上报 → 后台**永久标签**（该 wxid/device 永不长租约）+ 发影子租约让其觉正常 → **到期不续** → 料散沙 → 宿主判非官方 → 封号。期间可弹引流 / 或当场悄悄不给防封料，但**不立刻 kill/封**。
- **为什么改不续命**：① 更难逆向（看着像普通租约到期，不像被当场抓）；② 更简单（复用租约「续不续」，不另搞 kill 路径）；③ 更不误伤（留缓冲，误判可服务端强校验转正）。
- **不续命几乎免费**：租约到期本来就让 `isActive`/`isAntiBanReady` 变 false → 料散；只需补「检测篡改→标记→下周期不续」这个决定（服务器后面做，客户端断网本地兜底）。
- **与 A 的分工（⚠️ 待最终确认）**：A = 碰「故意埋的假诱饵」→ 普通影子期；C = 破「真防线」铁证 → 永久标签 + 不续命（更狠）。**「改 vip」建议归 A**（埋假 vip 诱饵；真门 `isVipAuthorized` 被 hook 成 true 也拿不到料，服务器种子层另挡一道）——待你拍。
- **减法落点 / 红线**：C 不新增计时，复用 §4 加密料/canary + 同一出口 + 同一影子/租约机制；现象统一不弹专属报错；不删数据/不破坏宿主（#5/#6）；封号是平台行为，模块不自爆。

---

## 3. A2 三轴（被闸门控的「防封能力」）

三轴全在 Java afterHook 喂官方（命脉/锚点详见研究线权威账 §十六/§十七）：

| 轴 | hook 点 | 喂官方 | 料 |
|---|---|---|---|
| 签名 | `getPackageInfo(self, GET_SIGNATURES\|GET_SIGNING_CERTIFICATES)` afterHook（扩 `ModuleMain.bindSigningCert` L280） | `signatures[]`/`signingInfo` → 官方 DER | `official_der.hex`(751B/`18c867f0`) |
| android_id | `Settings.Secure.getString(_,"android_id")` | → 官方 SSAID | `ssaid_calc.py` 算 |
| 包名/路径 | `getPackageInfo`/`getApplicationInfo(self)`（仅 normsg/bu5 caller，**不碰 getPackageName**/363 处会崩） | `packageName`/`sourceDir` → 官方 | — |

- **安装条件**：`isAntiBanReady() == true` 才装；否则散沙。
- **发版红线**：出包前过 `tools/gate_three_axis.js` 自检门（k33/k49/k18 全官方才放行）。

---

## 4. 防破解：A2 料锁进加密链 + 载荷弹窗 canary

### 4.1 A2「喂官方的料」进加密 registry（防破解 = 防封同一把锁）
- `official_der` / SSAID 派生参数 / 官方包名 **不明文写死**，进 `registry_pack`（AES-GCM 加密）→ 只有 **服务器种子 + 验签信封 + SO `decrypt_config()` 成功** 才解得出。
- 盗版无服务器种子 → 解不出官方 DER → A2 喂不了官方 → **防封自动散沙**。fail-closed：解不开 = 散沙、**不回退明文**（红线：release 无明文 fallback）。

### 4.2 载荷弹窗 canary（让「删弹窗」自反噬）
- 把 `RiskPromptController` 引流弹窗**稳定核心段**的 hash/常量 **掺进 A2 料的 key 派生**（USE 不 COMPARE，仿 `CompatProbe.BASELINE`）。
- 删/改弹窗 → hash 变 → key 错 → 官方 DER 解不出 → 防封散沙 → 号被封。即「想白嫖就得留着弹窗」。
- **工程坑**：弹窗当 key 料 → 每版本改弹窗须同源重生成加密料（同 `PLAN.md §A.5` 共享常量禁区）→ 只把**稳定核心段**算进 hash，别算每版都动的文案。
- **边界（诚实）**：真锁是服务器种子+短命租约+设备绑定；载荷弹窗只加固「删不掉弹窗」这一层。Frida 运行时 hook `decrypt_config()` 出参仍是高级威胁（防线靠短命租约+服务端轮换）。

### 4.3 状态黑盒化（C36 新增 · 能做成「灰盒」，做不到「纯黑盒永不可破」）

目标不是「逆不出」，是「逆出来也用不久」。分 3 层，从最值得到不值得：

| 黑盒层 | 做法 | 评 |
|---|---|---|
| 大脑下沉 SO | 判定（`isAntiBanReady` / 宽限计算 / 给不给料）从 Java 搬进 `libguardcore.so`；Java 只剩 `if(nativeReady()) 装hook`，看不到为什么 ready | ✅ 值得（红线#23 例外允许模块自有 SO 做状态机；#27 已有 nativeInit/nativeReloadState 先例）|
| 真锁留服务端 | 客户端不存「答案」只问服务器；SO 被扒光也找不到永久授权（它不在客户端）| ✅ 最硬黑盒 |
| 状态不落明文 | MMKV 不写 `grace_until`/`isVip`/`risk_level` 明牌 key；key 4 字符短哈希 + value 加密；dump 出来是乱码 | ✅ 值得 |
| 弹窗 / 影子期倒计时 / 冷却 UI | **留 Java，不硬塞 SO** | ❌ 不值得（UI 必在 Java；倒计时进 SO 也被 Frida 看；硬塞违背减法）|

- **现象统一**（呼应 §1 减法 / §2 触发线 C）：黑盒对外只吐「给料 / 不给料」，看不到内部是 L0 还是 L5 → 逆向分不清哪条触发命中。
- **诚实做不到**：SO 能被 IDA 逆（更难，skill 明列为预期威胁）；Frida 能 hook SO 出入参。黑盒真价值 = 配合短命租约 + 设备绑定 + 服务端轮换。
- **跟减法对上**：黑盒化 = 把判定收进 SO 一处 + 状态藏起来 → 客户端可被搜 / 被 hook 的点更少 = 减法（不是加检查，是挪答案 + 藏状态）。

---

## 5. 三种「时机」别混（C36 续：惩罚统一走「不续命」）

| | 对象 | 计时器 | 触发 | 表现 |
|---|---|---|---|---|
| 人为红线(C) | **破真防线的人** | 影子租约 7-10 天 | 动红线（签名/registry/canary/改真授权/删真弹窗/hook授权）| 暗记+永久标签+**到期不续命**→散→封 |
| 蜜罐影子期(A) | **碰假诱饵的篡改者** | `SHADOW_HOURS`(7天) | 命中蜜罐诱饵 | 延迟弹、先不暴露蜜罐；过期不续命→引流 |
| 授权闸宽限(B·T_grace) | **没篡改、只没付款的真用户** | `LeaseClock` age | 未授权 | 给时间授权，T_soft=1h 软引流，T_kill=72h 散 |

三者最后都走同一引流弹窗 / 同一料出口 / 同一「续不续租约」机制。**惩罚统一 = 暗记 + 影子租约 + 到期不续命**（不立刻 kill/封）；**真用户别误进影子期 / 红线**；**环境异常并进 B 宽限，不立刻散**（红线#4）。

---

## 5A. 信任状态机 + 后台评价体系 + 绑定 + 周期外置（C36 续）

### 信任状态机（后台可见的「期」）

| 状态 | 含义 | 心跳/租约 |
|---|---|---|
| 新设备期 | 刚装、分低（默认 60%）| 心跳密集抓激活、短租约 |
| 建立信任期 | 攒分中、分往上爬 | 心跳中、短租约 |
| 稳定期 | 评分≥60% 稳定 | 心跳疏、长租约（后台仍可随时封停）|
| 确认改包 | 铁证篡改（重签/canary）| 永久标签 + 影子租约 + 不续命 |
| 蜜罐弹窗期 | 碰假诱饵 | 影子期 → 过期引流 |

→ 映射现有 `RiskState` L0~L6 + 信任档；客户端不自判，只消费。

### 后台评价体系（信任评分 · 服务器算 · 红线#1）

- **服务器后台算分**（客户端只上报心跳信号、不自评），默认 **60%**、**60% = 及格线**，一周累计 ≥60% 及格。
- **心跳节奏**：首装密集（抓激活）→ 1h–24h 每 10min（带 ±抖动，注意 CONN 红线 0.5）→ 24h 后每 1h。
- ⚠️ **无样本占位**：P18 KPI 基线未建（F-22）→ 60% 是占位，需真实信号/基线回来校准；现在不能宣称「60% 就安全」。

### 本地 vs 服务器（断网/域名被转发/被 hook）

- **服务器 = 权威**（在线以它为准：算分、标签、发签名租约）。
- **客户端 = 本地兜底**：断网用「上次服务器签名状态 + `LeaseClock` 可信时间」继续判；**只能维持/降级，不能自升级/洗白**（洗白只能服务器签名）。
- **屏蔽联网 ≠ 白嫖**：拿不到新租约 → 到期本地散沙（fail-closed）。网断了往严走、不往松走（红线#4 给宽限，但宽限完照样散）。

### 绑定（wxid + device · 解绑只后台）

- **登录门槛**：没登录（拿不到本人 wxid）→ 不激活、不给料。
- **绑定材料**：登录本人 `wxid`（原生 row id，不敏感）+ `device id`（=android_id，见 §0.5）。永久标签按 **wxid+device**（wxid 跨设备追人、device 跨账号追机器）。
- **解绑只后台管理员**：用户自己解不了；真用户换机/换号 → 客服 → 管理员后台解绑。防共享/防转卖。
- **不敏感 = 不加密**：wxid/device 是标识非秘密，记录/上报明文即可；但上报走**签名信封**防伪造别人的 wxid。
- ⚠️ **device×A2 同源必修**：device id 与 A2 android_id 轴同一 API；A2 接主线前必须把 A2 hook 限定**仅微信 caller**（像包名轴），否则所有 device id 塌缩成官方值、设备绑定全废。

### 周期外置（好维护：以后改周期 = 改一个数）

- 所有周期/阈值（T_soft/T_kill/影子租约/心跳节奏/信任分）**集中一处** + 将来**服务器签名下发**（放 `risk_pack`/租约策略）→ 后台改个数全网生效、连发版都不用、签名防篡改。
- **绝不硬编码散落在各处代码**（单一真源）；周期 = 调节钮非秘密 → **不加密**（区别于配料钥匙要加密）。

---

## 6. 红线遵守（安全官 9 条 + 防封官）

- 红线#1：`isAntiBanReady` **不是客户端布尔**——吊在 `EnvelopeStore`(服务器信封) + `LeaseClock`。
- 红线#3：**不信手机墙钟**——时间用 `LeaseClock`(服务器时间 + elapsedRealtime + 防回拨 `max_trusted_now`)。
- 红线#4：**不因单纯断网误杀**——断网走缓存/宽限；曾授权过走 GRACE 24/72h。
- 红线#5/#6：散沙 = 卸能力，**不删用户数据、不破坏宿主**。
- 红线#9：弹窗**一个策略源** `RiskPromptController`。
- 防封官红线：散沙后宿主判非官方是「平台行为」；模块**不自爆**。KPI 红线见 §8。

---

## 7. ⚠️ 待用户拍板（定了再落代码；同 `PLAN.md §四`）

**已定（C36 / C36 续，2026-06-22）：**
1. ~~`T_kill` 阈值~~ = `T_soft=1h` / `T_kill=72h`（2h 否决）；影子租约 7-10 天。
2. ~~散沙对象~~ = 仅防封 A2 进时间闸；隐私走原有 DRM。
3. ~~惩罚模型~~ **= 暗记 + 影子租约 + 到期不续命**（不再立刻 kill/封；触发线 C 也走此模型，见 §2/§5）。
4. ~~架构~~ = 一个信任大脑 + 两个能力出口；三抽屉（心跳/砸门/蜜罐）；三角色（大脑 SO / 腿 Java / 后台服务器），见 §1A。
5. ~~绑定~~ = 登录 wxid + device(=android_id) 双绑定；永久标签按 wxid+device；**解绑只后台管理员**；没登录不给料（见 §5A）。
6. ~~评价体系~~ = 服务器算信任分默认 60%/及格（红线#1）；心跳 密→10min→1h；周期外置 + 将来服务器签名下发（见 §5A）。
7. ~~黑盒化~~ = 大脑下沉 SO + 真锁服务端 + 状态不落明文 + 现象统一；UI 留 Java（见 §4.3）。
8. ~~弹窗 canary~~ = 只哈希稳定核心段。
9. ~~文档~~ = 本文为单一权威文档（不建 RECON.md；PLAN/A3-0 留档）。

**已定（C36 续2 2026-06-22，指挥拍板）：**
10. ~~决策4（A2 本体接主线）~~ **= 接**（「肯定接，不给人做嫁衣」）→ 防封轴真生效。
11. ~~device×A2 同源~~ **= 修**（A2 接主线时同步把 A2 android_id hook 限定仅微信 caller，见 §0.5/§5A）。
12. ~~「改 vip」归属~~ **= 走 A 诱饵方向**（以方便开发为主、不做脆弱 C 反调试；诱饵保持简单、不过度做假）。
13. ~~时钟回拨~~ **= 接受**（首装首刻墙钟兜底挡普通党；联网后服务器时间收紧）。
14. ~~大脑下沉 SO 排期~~ **= 放后期**（A2 落地 + P18 KPI 基线之后，~5-10 人天）。

**全部决策已锁 → 进入实施（见 §9A 步骤）。**

---

## 8. 验收（L4 + KPI）

- **机制层**：装模块 → `isAntiBanReady` 各分支（首装<T/超时/授权/曾授权断网）行为正确；A2 受闸开/散沙；删弹窗 → 防封散沙（载荷 canary 生效）；蜜罐命中 → 影子期 → 引流；服务器强校验 → 转正。
- **足迹层（接 A2 后必做）**：建 `P18 KPI 基线`（官方包跑 `frida_stats.js` 测基准）→ 对照装模块后 KPI **不超官方基线**（verifiedbootstate=38/PROP=220/normsg=5124/CONN=0.5），确认 A2 hook 没加噪。
- **终验**：真号 L4 长期不被判异常（同 `PLAN.md`/研究线 §10.7 口径，本设计只验机制+足迹层）。

---

## 9. 落地接点（接代码时，决策4 + 工作项B）

- `ModuleMain.handleLoadPackage`(L61)：按 `lpp.packageName` == 目标包 → `if(isAntiBanReady()) A2SignatureSpoof.install(lpp)`。
- `ModuleMain.bindSigningCert`(L280)：扩成 getPackageInfo afterHook 喂官方 DER（替现有对 c$p 无效的 LSPatch SigBypass，见研究线 §九）。
- `GuardRuntime`：新增 `isAntiBanReady()`；`LeaseClock`/`RiskState`/`EncryptedConfigLoader` 复用现有。
- 持久化 2 值：`首装时间`(Bridge 新键)、`是否曾授权`(EnvelopeStore)。
- 发版：`tools/gate_three_axis.js` 自检门 + registry 同源重生成（含载荷弹窗 canary 段）。

---

## 9A. 实施计划 + 步骤（C36 续2 · 决策已锁 · 客户端先行）

> 决策已锁（§7 已定 10–14）：A2 接、device×A2 修、改 vip 走 A、时钟回拨接受、SO 下沉放后期。
> 纪律：每步 = 动代码前 git 快照 → 改 → 装机回归绿 → 再下一步；全程守红线 #1/#3/#4/#5/#6/#9/#23/#27 + PLAN §A.5 四个共享常量禁区 + 不改已验证 hook（铁律29/F-31）。

**5 条蜜罐微调（与现有蜜罐搭配，不推倒）**：①record-only→接不续命 ②铁证(签名/canary)分流→永久标签/不续命；诱饵→影子期 ③埋假 vip 诱饵(简单、不过度做假) ④弹窗 canary 只哈希稳定核心段 ⑤device×A2 scope 隔离。

### 阶段一 · 客户端先行（~3-5 人天）
- **S0 快照**：动代码前 git 快照（git 保姆）。
- **S1 建闸 `isAntiBanReady()`**：`GuardRuntime` 新增；吊 `EnvelopeStore.isAuthorizedNow()` + `LeaseClock`；持久化 2 值（首装时间 = Bridge 新键 / 曾授权 = EnvelopeStore）。先复用到隐私 hook 验证。验收：各分支（首装<T/超时/授权/曾授权断网）行为对。
- **S2 wxid 登录门槛 + 本人绑定**：腿抓登录本人 wxid → 进绑定材料；没登录不给料。验收：未登录不激活。
- **S3 不续命接线**：把现有 `CompatProbe`→`markTampered` 的 record-only 升级成「分流」——铁证(签名/canary)→永久标签 + 不续命；诱饵→影子期。复用租约到期→`isActive`/`isAntiBanReady` 变 false→散（几乎免费）。
- **S4 假 vip 诱饵 + 弹窗 canary 稳定段**：埋假 vip（改 vip 归 A，简单不过度）；弹窗 canary 只哈希稳定核心段。
- 阶段一验收：隐私四链照常 + 闸/绑定/不续命/蜜罐分流行为对 + jadx 明文面不增。

### 阶段二 · 接 A2 防封（决策4，~3-5 人天）
- **S5 A2 本体接主线**：`A2SignatureSpoof`（签名/android_id/包名 喂官方）接 `ModuleMain`；A2 料进加密 registry（§4.1）。
- **S6 device×A2 scope 隔离（必修）**：A2 android_id hook 限定仅微信 caller，放过我方 `computeDeviceHash` 读真值。验收：A2 开后各机 device id 仍各异、envelope `d` 绑定不塌。
- **S7 P18 KPI 基线**：官方包跑 `frida_stats.js` 建基线 → 装 A2 后对照不超红线（§8）。

### 阶段三 · 后期
- **S8 大脑下沉 SO**：判定（isAntiBanReady/宽限计算）+ Ed25519 验签 C 化下沉 `libguardcore.so`（~5-10 天，放 A2 落地 + KPI 之后）。
- **S9 服务端（miyou-server，后接）**：信任评分 + 永久标签 + 解绑后台 + 周期签名下发。没接前只叫「本地加密链路」非真锁。

# End（设计 only 决策已锁；落代码按 §9A 从 S0 git 快照起；本文 = 单一权威真源）
