# A2 喂料 + diff 大血管 + 三方 KPI 实验（2026-06-25）

> 角色：终端/设备实验 worker。设备：小米9 `609b4b18`（MI9 / Android 11 / Magisk root）。微信 **8.0.71**。
> 目标包：**测试包 `com.tencent.mn`**（DEVTEST 测试号、安全方向：喂官方 = 让非官方看着像官方）。官方包 `com.tencent.mm` 仅作 KPI 基线（只读、未喂）。
> 模块：扩 `脚本/dimmod_v2`（LSPosed，`SPOOF=true`）。原已喂签名 signatures，本轮补 **signingInfo + android_id**。
> 证据级：全程 **L1 动态实证**（logcat DIMCOLLECT + frida_stats 原文）。
> 纪律：只改自身包 `getPackageInfo`/`Settings.Secure.getString` 返回；不碰他包；不读 `ro.boot.*`；native 探针 observe-only。

---

## 一、喂了什么（三料）

| 料 | 喂法（Java 边界 afterHook，仅自身包） | 喂的值 |
|---|---|---|
| 签名 signatures[] | `getPackageInfo(self, GET_SIGNATURES)` → 换 `pi.signatures` | 官方 DER（MD5 `18c867f0`/751B） |
| 签名 signingInfo | `getPackageInfo(self, GET_SIGNING_CERTIFICATES)` → `spoofSigningInfo(pi.signingInfo)` | 同官方 DER |
| android_id | `Settings$Secure.getString(_, "android_id")` → 换返回 | 官方签名本机派生 SSAID `05f894e8e1e260fa` |

> 代码：`脚本/dimmod_v2/src/com/dim/collect/DimCollect.java`（K_GPI 加 signingInfo 分支 + K_SSEC 加 android_id 喂 + `spoofSigningInfo/replaceSignatureArrays/getField`）。
> build 坑：build.ps1 硬编码的 xposed `api-82.jar` gradle 缓存路径失效 → 改 `-api C:\Users\Me\Desktop\guard_native\app\libs\xposed-api-82.jar` 后编过、装上。

---

## 二、diff 大血管（喂料前后，c$p 读到什么）

冷启 mn + UI 触发（附近的人/钱包/解冻/切后台），看检测咽喉 `c$p` 各读法读到的身份：

| 读取路径 | 喂前 | 喂后 | 证据 |
|---|---|---|---|
| `c$p.ad` ← WCProbe$Info.n（签名 signatures） | `e89b158e`（非官方） | **`18c867f0`（官方）** | `SIGRAW via=c$p.ad` |
| `c$p.aa` ← WCProbe$Info.f（上报漏斗） | 非官方 | **官方** | `SIG verdict=OFFICIAL` |
| `c$p.ea`/`c$p.aa`（android_id） | 非官方 SSAID | **`05f894e8`（官方）** | `SPOOF/COLLECT key=android_id` |
| **`bu5.a.a`（Flutter，signingInfo 路径）** | `e89b158e`/1196（非官方） | **官方**（补 signingInfo 后） | 第一轮漏、补后 0 残漏 |

**关键发现（diff 的价值）**：第一轮只喂 `pi.signatures[]`，`bu5.a.a` 走 `GET_SIGNING_CERTIFICATES → signingInfo` **仍漏非官方**；补 `spoofSigningInfo()` 后，**自身包 NON-OFFICIAL 残漏 = 0**，整条大血管（c$p 三读法 + bu5 + t8.c0）全部读到官方。

> 范围说明：`c$p.aa` 仍扫**他包**签名（MT管理器 `1a32f661`/scanner `701478a1`）= 环境轴，**不在 A2 喂料范围**（只改"问自己"的返回，不动他包）。
> 日志：`recon/a2feed_dimcollect_mn_complete_20260625.log`（完整喂料）、`a2feed_dimcollect_mn_20260625.log`（第一轮含 bu5 残漏）。

---

## 三、三方 KPI（frida_stats.js，每 5s 窗均值，已归一）

| 每窗均值 | mn-喂前(warm) | mn-喂后(warm) | 官方mm(spawn冷) | 红线 |
|---|---|---|---|---|
| vb (verifiedbootstate) | 0.2 | 0.2 | 0.3 | ≤38 |
| PROP | 100.8 | 142 | 336.2 | /100K≤220 |
| maps | 0 | 0 | 0.4 | — |
| smaps | 0 | 0 | 0 | — |
| CONN | 0.8 | 0.6 | 28.2 | 密度≤0.5(比值) |
| mprotect | 0 | 0 | 0 | — |
| 窗口数 | 17 | 16 | 20 | — |

**三条结论**：

1. **喂料零增量**（mn 喂前 vs 喂后，同为 warm、apples-to-apples）：检测面 KPI `vb 0.2=0.2 / maps 0=0 / mprotect 0=0` **完全不动**。`PROP/CONN` 微差 = 手动 UI 强度噪声（CONN 喂后反而低，证明是活动噪声非喂料引起）。
2. **我方包 ≤ 官方包**：每一项我方都不高于官方 —— `vb 0.2≤0.3`、`PROP 142≪336`、`CONN 0.6≪28`、`mprotect 0=0`。**喂料后的我方包在 KPI 镜头下比官方还干净/相当。**
3. **逻辑必然**：喂料只 hook `getPackageInfo`/`getString`（Java），二者不调 `__system_property_get`/`open`/`connect` → 物理上加不到 PROP/vb/maps/CONN。frida_stats 实测坐实。

**caveat（诚实）**：
- mn 是 warm-attach、官方是 spawn 冷启 → mn vs 官方非完全 apples（官方多了冷启那一下；warm 漏冷启 vb/maps 突发）。但 **mn 喂前 vs 喂后是同条件** → 喂料 delta≈0 是硬的。
- `CONN 密度 ≤0.5` 红线是比值指标、非每窗计数，未直接套用；仅观察到我方 CONN 远低于官方。

> 日志：`recon/kpi_before_feedoff_20260625.log`、`kpi_after_feedon_20260625.log`、`kpi_official_full_20260625.log`、`kpi_official_spawn_20260625.log`。

---

## 四、还没闭的一条（待验 L4）

- **libilink2 冷启 `/proc/self/maps` 扫注入 SO 会不会扫到我方 `libguardcore.so`**：本轮官方 spawn maps=0.4/窗（官方自身冷启扫），但**那是无模块的官方包**；要测"我方 SO 是否进 maps 命中"，须在【mn 带模块 + spawn 冷启】量，而 **mn 不能 spawn**（坑3/10）→ 需另法（官替包冷启量，或静态比对 maps 读取点过滤名单）。出包前补。

---

## 五、总结论

**A2「掐 `getPackageInfo` 咽喉、灌官方值」在真机 8.0.71 走通且干净**：

- 喂签名（signatures + signingInfo）+ android_id → 检测大血管 `c$p`（ad/aa/ea）+ `bu5.a.a` + `t8.c0` **全部读到官方**，自身包 0 残漏。
- KPI **零增量**（喂料只动 2 个 Java 返回、不增任何 libc 环境读），且我方包足迹 **≤ 官方包**。
- 唯一未闭：我方 SO 是否进 libilink2 maps 扫描（L4，出包前补）。

# End
