# A2 android_id 同源 · 修复方案 v1（design-only · 单层简化）

> 出具：2026-06-24 · 网络安全官（**design-only，未改任何代码**；真改码须设备在场 + 双官审 + 另行批）。
> **取代**：v0 方案（自读闸 + memoize 双层）+ 落地清单_v0 → 合并简化为本稿（2026-06-24 减法）；更早见 git history。
> 新账本对齐：研究线真源 `C:\Users\Me\Desktop\防封_反检测线\防封权威账_2026年6月.md`（SSAID 逐机派生 §九 / A2 落地 §十 / 三轴速查 §十四）。
> 上层：`../P_AntiBanGate_防封授权闸/DESIGN.md` §4（A2 三轴）。设备绑定钥匙细节：`../P_RB1_重放绑定_ReplayBind/钥匙加固_KeyHardening设计.md`。
> 成色：L1 动态 / L2 静态 / L4 待验证 / 设计only。命名：官方包 / 原版 / 灌官方值（不写品牌名）。

---

## 0. 问题（一句话）

A2 要 hook `Settings.Secure.getString(_, "android_id")` 给**官方包**灌官方 SSAID；但**我方 device 绑定（`AuthManager.computeDeviceHash`）读的是同一个 API、同一进程** → A2 一开，我方 device 读也被灌成官方 SSAID → 全机 device hash 塌成同一官方值 → 设备绑定真锁全废（永久标签 / 防共享 / 防转卖 / 不续命全失效）。这是 DESIGN 决策#11 / **D1 最高危**。

---

## 1. 新账本关键（灌什么值）

- android_id **逐机派生**：`HMAC-SHA256(user_key, BE32(len)‖官方签名DER)[:8]`（研究线 §九，L1），**非全局常量**。
- 灌值 = **本机官方 SSAID**（`脚本/ssaid_calc.py` 按官方 DER + 本机 user_key 现算；本机实测 = `05f894e8e1e260fa`）。**不能硬编码别机的值**。
- **必须连带签名一起灌**（A2 签名轴 + android_id 轴 同模块、同 scope、同冷启），否则「签名↔android_id」对不上、露馅。

---

## 2. 修复（单层简化：memoize 预热，砍掉自读闸）

**主方案 = device 材料「装 A2 前预热 + memoize」一层**——**不要** v0 的 ThreadLocal 自读闸、**不要** afterHook 的 caller 区分。

**依据（L2 实证，原落地清单 §0）：**
- android_id 全仓**唯一物理读点** = `AuthManager.java:106`。
- android_id **进程内单值、与 ctx 无关** → 可安全缓存。
- device 首读（`ModuleMain` L165/L193）**天然早于** hook install（L207+）。

**做法：**
1. `AuthManager` 收口唯一读点 `rawAndroidId(ctx)`（把现 L106 的 `Settings.Secure.getString` 移进去）+ `volatile` memoize 缓存。
2. 冷启动 **A2 install 之前**（step2 `Bridge.init` 之后）显式预热 `computeDeviceHash` 一次（**即便 `hasToken()==false` 也预热**）→ 真值落 memoize。
3. 之后 #1–#8 所有我方读（含懒读 `EnvelopeStore:155` / `SettingsEntry:1421`）命中缓存 → **不再调 getString → 不触发 A2 hook** → 拿到真值。
4. A2 android_id afterHook：**无条件**灌本机官方 SSAID（到这里的 `android_id` 调用必然是官方包/第三方库——我方读已被缓存拦在 hook 前）。

→ **砍掉 v0 的 A2Scope（ThreadLocal）+ caller 区分**：memoize + 时序已保证我方读不触发 hook，afterHook 不必判「谁在读」，也免了「包名混淆 / 栈扫描」那套脆弱判断。

---

## 3. 安装时序（钉死）

```
冷启动：① Bridge.init → ② 预热 computeDeviceHash 落 memoize
        → ③【fail-safe 断言】device hash 已缓存？否 → 不装 A2（宁可防封不开，绝不污染 device 误伤正版）
        → ④ A2 装载 = isAntiBanReady()（防封授权判定，非开关）+ 断言通过 → install
        （A2 = 签名轴 + android_id afterHook + 包名轴，step7 L207+）
```

- **铁律1：A2 android_id hook 绝不早于 ②**；否则首次 device 读就被污染。
- **铁律2（fail-safe）：device hash 没缓存就不装 A2**——单层 memoize 的兜底（砍了 v0 自读闸后补此运行时保险），最坏只是「防封没开」，绝不「误伤正版」。
- 作用域勾**实际出货包名**（共存 / 官替），**LSPosed 冷启动注入**（frida spawn 崩、warm attach 漏最早段）。

---

## 4. 落地（改哪些文件 · design-only）

| 文件 | 动作 |
|---|---|
| `core/AuthManager.java` | 新增 `rawAndroidId(ctx)`（唯一读 + `volatile` memoize）；`computeDeviceHash` L106 改调它 |
| `ModuleMain.java` | step2 后预热一次 device 读（落 memoize）；step7 装 A2 前**先断言 device hash 已缓存**（没缓存 → 不装 A2，fail-safe 防误伤），再按 `isAntiBanReady()` 决定 install |
| A2 hook 段（新建 `A2SignatureSpoof`，签名+android_id+包名 三轴收一处） | android_id afterHook **无条件**灌官方 SSAID；料进加密 `registry_pack`（**不明文写死**） |
| 防封授权判定 | **新建** `GuardRuntime.isAntiBanReady()`（授权检查官拍定：新建、不复用 isSensitiveConfigReady，两闸独立互不连坐）。**它不是用户开关 / 功能开关**——是「正版到期 7 天续费宽限后才撤 A2 / 从未授权走影子期不续命 / 红线篡改进影子期」的反白嫖判定，吊 `EnvelopeStore` 授权 + `LeaseClock` 租约 + 影子租约（DESIGN §6 / 配方卡 C18）|

- **落码 gate**：全仓 grep `Settings\.Secure.*ANDROID_ID` 必须**只剩 `rawAndroidId` 一处**（同源不再二次裸读的硬验收）。
- **不纳入** `computeDeviceMaterial`（全 32B）：那是钥匙加固（W_dev）的事、现仓未实装；若将来落地，其 android_id 读**必须同走 `rawAndroidId`**（别再开污染口）。

---

## 5. 验收（两机 D1 硬测 + frida L1）

- **判据1（不塌）**：两机冷启（A2 已装）→ `d(机A) != d(机B)`。
- **判据2（读真值）**：每台 `d` == 装 A2 前基线 `d`，且 `!=` `SHA-256(官方SSAID)[:8]`（塌缩值）。
- **判据3（官方包读官方）**：官方包侧 `getString(_,android_id)` == 官方 SSAID（基线参考 `05f894e8e1e260fa`，配方卡 §C）。
- **frida L1**（`脚本/a2_androidid_probe.js`）：官方包 caller → 官方 SSAID；我方 `rawAndroidId` 路径 → 真 android_id。
- **回归**：隐私四链（会话/通讯录/朋友圈/搜索）+ 来电/通知 仍绿（铁律29/F-31）；签名轴与 android_id afterHook 共存不互踩；CONN 红线 0.5（probe 别打密心跳）。
- 任一不过 = **停、回报、不进下一步**。

---

## 6. ❓ 待验证（L4）

1. 官方包 android_id 读路是否**仅** `Settings.Secure.getString`？若另有 `ContentResolver.query` / native（研究线 §九未完全排除）→ **memoize 思路不变**，但 afterHook 点要补（frida 全程 trace 定）。
2. 预热落点是否破坏现冷启序 → 授权检查官 + 装机回归确认。
3. memoize 安全性（android_id 进程内单值）= L3；主线靠它 + 时序钉死。
4. 官方 SSAID 常量 vs 逐机（研究线 §九已定逐机）——只影响 probe 对照值，不影响修复。

---

## 7. 前置（缺一 BLOCK）

git 快照 → 授权检查官 + 安全官 改前审 PASS → 两机 D1 硬测 → 设备在场另行批。

---

# End（v1 design-only 未改码 · 合并取代 v0 方案 + v0 落地清单 · 单层简化 = memoize 预热，砍自读闸 + caller 区分）
