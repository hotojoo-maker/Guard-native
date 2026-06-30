# 共存版包名轴【能喂 + 分叉定死】实证（2026-06-25）

> 角色：终端/设备实验 worker。设备：小米9 `609b4b18`（MI9 / Android 11 / Magisk root）。微信 **8.0.71**。
> 测试包：共存包 `com.tencent.mn`（非官方 testkey）。官方包 `com.tencent.mm` 同机共存（喂路径时借它的真路径）。
> 纪律：只动自身包 `getPackageInfo`/`getApplicationInfo`/`Settings.Secure` 返回值；**不动全局 `getPackageName`（365 处，会崩，见 PKGNAME_READERS §七.1）**；不碰他包；不 hook native c$p；不增检测面。
> 工具：`防封_反检测线/脚本/dimmod_v2`（DimCollect，`SPOOF=true`，caller-selective 包名+路径喂）。原始日志：`防封_反检测线/logs/pkgname_feed_20260625.txt`（256 行，L1，无 ERR、无崩溃）。
> 前置：`PKGNAME_READERS_20260621.md`（读取点枚举）、`A2_RIDE_TEST_20260625.md`（签名轴咽喉同处）、`A2_FEED_DIFF_KPI_实验_20260625.md`（KPI 中性）。

---

## ⓪ 实验设计（在签名/android_id 喂料上加两轴）

`dimmod_v2` 冷启注入，在「检测/上报」caller（`c$p`/`normsg`/`bu5`/`t8`）下，对自身包 `getPackageInfo(self)` / `getApplicationInfo(self)` 的返回值额外喂：

- **包名轴**：`pi.packageName` → `com.tencent.mm`。
- **路径轴**：`applicationInfo.sourceDir/publicSourceDir/dataDir/nativeLibraryDir` → **借真实官方包 `com.tencent.mm` 的 ApplicationInfo** 灌真官方路径（拿不到则字符串替换兜底）。
- 叠加既有：签名 → 官方 DER（18c867f0/751）；`android_id` → 官方 SSAID。
- **caller-selective**：包名/路径仅在检测栈命中时喂（`isDetectionCaller()` 比对锚点），不动 mn 自身业务路径。

---

## ① 能喂 = ✅ L1（检测咽喉读到的身份全翻官方）

冷启 mn + 全量 UI（切前后台×N / 附近的人 / 钱包 / 解冻页 / 主屏 / 后台）。检测/上报 caller 读到的自身身份：

| 检测 caller | 读什么 | 喂前 was | 喂后读到 | 证据行 |
|---|---|---|---|---|
| `com.tencent.mm.normsg.c$p.ad` | getPackageInfo 包名 | `com.tencent.mn` | **`com.tencent.mm`** | 231 |
| `com.tencent.mm.plugin.normsg.u.z3` | getPackageInfo 包名 | （缓存）mm | **`com.tencent.mm`** | 235 |
| `bu5.a.onMethodCall` | getPackageInfo 包名 + 路径 | `com.tencent.mn` | **mm + 官方路径** | 86-87 |
| `bu5.a.a` | getPackageInfo signingInfo | testkey | **官方(18c867f0/751)** | 88 |
| `com.tencent.mm.normsg.c$p.aa` | getApplicationInfo sourceDir | `…com.tencent.mn-…/base.apk` | **`/data/app/~~3b70…/com.tencent.mm-…/base.apk`** | 128 / 239 |
| （叠加）`Performance.checkAppPlatformSigned` 等 | 签名 | — | **18c867f0 OFFICIAL** | 39 |
| （叠加）`Settings.Secure` | android_id | — | **官方 SSAID** | 见 A2_FEED_DIFF_KPI |

关键原文（节选）：

```
231 | SPOOF | com.tencent.mn | getPackageInfo.packageName | fed=com.tencent.mm;was=com.tencent.mn | via=com.tencent.mm.normsg.c$p.ad
235 | SPOOF | com.tencent.mn | getPackageInfo.packageName | fed=com.tencent.mm;was=com.tencent.mm | via=com.tencent.mm.plugin.normsg.u.z3
 86 | SPOOF | com.tencent.mn | getPackageInfo.packageName | fed=com.tencent.mm;was=com.tencent.mn | via=bu5.a.onMethodCall
128 | PATH  | com.tencent.mn | sourceDir | /data/app/~~3b70A491-…/com.tencent.mm-6Z-…/base.apk | via=com.tencent.mm.normsg.c$p.aa
```

→ **检测动脉（c$p.aa/ad、normsg.u.z3、bu5）读到的「自身包名 + apk 路径 + 签名 + android_id」全是官方。共存包名轴能纯 Java 喂。**

---

## ② 分叉定死：「c$p 用自己 SELF（getPackageName）」——被 L1 否定

研究线此前的 open 点：c$p 报的包名，是**查回来的**（getPackageInfo 结果）还是**自己已知的 SELF**（来自 `getPackageName`/常量）？若是后者 → Java 喂不动（getPackageName 365 处不可全局改）。

**实测裁定**：`c$p.ad`（231 行）显示 `was=com.tencent.mn` → 被我们改成 `com.tencent.mm` 成功 —— 即 **c$p 的包名是从 `getPackageInfo` 结果读的**，不是从独立 `getPackageName` 取的。`normsg.u.z3`、`bu5` 同构。

→ **包名轴与签名轴同咽喉（`getPackageInfo(self)`）、同证据级（L1）、同治法。** open 分叉关闭：**走 getPackageInfo 可治**这一支成立。

---

## ③ caller-selective 成立（不动 getPackageName 也精准）

`com.qualcomm.qti.Performance.checkAppPlatformSigned`（系统 QTI 性能提示，非检测轴）本轮**只拿到签名喂**（38/156/169 行）、**没有**对应的 `getPackageInfo.packageName` 喂行 —— 证明包名喂只命中检测 caller，未波及系统/业务调用方。

→ **不触碰全局 `getPackageName`（365 处、含文件路径/authority，会崩）也能精准只喂检测点。** 与 PKGNAME_READERS §七.2「选择性 spoof」设计一致、活体复现。

---

## ④ 残留（诚实；只官替能除 / 或低优先级）

1. **真实 OS 路径 / 数据目录** `/data/data/com.tencent.mn` + 真实 apk 路径：native 直读绕 Java，洗不掉。冷启实测 **native cmdline/路径读 = 0**（PKGNAME_READERS §六补），检测链未踩；**真号 L4 终验**为准。彻底洗只能官替。
2. **SDK 泄露**：定位 SDK / Cronet / XWeb / LiteApp 用 `getPackageName` 把 mn 带进**各自**网络上行（多半发给 SDK 自己的服务器，不一定进风控）。优先级低于 normsg。
3. **缓存对象副作用**：99/235 行出现 `was=com.tencent.mm`（重复调用读到已被我们改过的**缓存 PackageInfo**）—— 说明 PM 缓存了被改实例。实验下无害（喂料更黏），**生产须 clone-不改缓存**，避免功能性调用方意外拿到 mm。
4. **信封 L4**：本轮证「检测 caller 读到 mm」（同签名轴证据档）；最终「normsg protobuf 信封 field 真带 mm」需 dump/解信封确认 —— 与签名轴同档待验。

---

## ⑤ 工程结论（一句话）

**共存版包名轴可纯 Java 喂干净到检测动脉**：c$p.aa/ad、normsg.u.z3、bu5 读到的自身包名/路径全翻官方，与签名/android_id 同咽喉、caller-selective、不崩、KPI 中性（见 A2_FEED_DIFF_KPI）。用户担心的「c$p 用自己 SELF」分叉 **L1 否定**。→ **「检测链的包名」不必被迫官替**；官替只为洗 native 硬命门（真实 OS 路径/data 目录），而那条冷启实测 native 读 = 0。终态以真号 L4 + 信封 dump 收口。

# End
