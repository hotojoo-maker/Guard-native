# 统一风控引擎设计 — 防封授权闸 × A2 三轴 × 蜜罐/引流/延迟弹窗 × 载荷弹窗 canary

> 建立：2026-06-21 / 状态：**⬜ 设计方案 only，未改任何代码**（接代码 = 决策4/工作项B 落地，另需用户拍板 + 装机回归，见 `PLAN.md §四/§五`）
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

**你原有的「蜜罐 / 引流 / 延迟弹窗」和新的「防封授权闸 1h-2h 散沙 + A2 三轴 + 载荷弹窗 canary」本来就是一套**——全部汇到**一个 `RiskState` 等级机 + 一个 `RiskPromptController` 弹窗源 + 一个 `GuardRuntime` 能力闸**。新东西 = 多接一条触发线、多挂一个能力，**不另起炉灶**。

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
- **★闸的范围（用户拍板 2026-06-21）**：`isAntiBanReady()` 的 1-2h 时间闸**只门控 A2 防封能力**；**隐私功能**（会话/通讯录/朋友圈/搜索 隐藏密友）由**原有 DRM** `isConfigReady()`/`isActive()` + registry 加密门控，**不进时间闸、互不散沙**。两闸独立、各管各的能力，只是都向同一 `RiskState`/`RiskPromptController` 汇报。

---

## 2. 两条触发线

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

---

## 5. 两个「延迟」别混

| | 对象 | 计时器 | 触发 | 表现 |
|---|---|---|---|---|
| 蜜罐影子期(7天) | **篡改者** | `SHADOW_HOURS` | 命中蜜罐 | 延迟弹、先不暴露蜜罐 |
| 授权闸宽限(T_grace) | **没篡改、只没付款的真用户** | `LeaseClock` age | 未授权 | 给时间授权，T_soft 起软引流 |

两者不同计时器、不同触发，最后都走同一引流弹窗。**真用户别误进影子期**（那是给篡改的）。

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

1. **`T_kill` 散沙阈值**：2h（用户初版）/ 24h / 72h / 「T_soft=1-2h 软引流但散沙等更久」/「必须叠加蜜罐信号才真散」？
   - 安全官诚实警告：**2h 太短**，防封散沙=可能真被封（比功能失效重），易误伤「还没付款的真用户」。**建议** `T_soft=1h 软引流` + `T_kill≥24–72h` 或叠加篡改信号才散。
2. ~~散沙对象~~ **已定（用户拍板 2026-06-21）= 仅防封 A2**：1-2h 时间闸**只门控 A2 防封**；隐私功能走**原有 DRM**（registry 加密 + `isConfigReady`/`isActive` + RiskState），**不进时间闸**。→ 白嫖不授权满时 = **防封散沙 → 号被平台封（这就是惩罚）**；隐私功能在被封前照常，但号一封就全没了。
3. **时钟回拨**：首装那刻无服务器时间，只能墙钟+elapsedRealtime 兜底（挡普通党、挡不住硬核动态党）——接受否？
4. **影子期来源**：`SHADOW_HOURS` 现硬编码 7 天 → 是否迁服务端签名 `risk_pack` 下发？

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

# End（设计 only，等 §7 拍板 + 决策4 拍板再落代码）
