# HONEYPOT 蜜罐设计（汇总 + 域名加密引导段 C2）

> **定位**：蜜罐 / 域名混淆 / 引流落地 的**单一汇总入口**。供后续 AI 接手快速理解全貌。
> **权威边界**：防破解总账仍以 [`../PROTECTION_MAP.md`](../PROTECTION_MAP.md) 为唯一权威（§5 蜜罐 · §10.4 两种党 · §10.6 服务器真锁现状）。本文**不重复结论、不另立策略源**，只做汇总 + 登记本轮新增的「域名加密引导段 C2」设计。
> **加密/风控规则**：以网络安全官 skill [`../.cursor/skills/guard-security_网络安全官/SKILL.md`](../.cursor/skills/guard-security_网络安全官/SKILL.md) 为准。
> **状态标注**：⬜ 未实现 / 🟡 设计稿待落地 / ✅ 已落地（装机 PASS）。本文新增项目前均为 **🟡 设计稿**，未写代码、未装机。
> **维护规矩**：改任何 .md 前先得用户同意（G5）；本文为用户 2026-06-11 明确要求新建。

---

## 0. 三条黑字原则（所有设计都服务它）

1. **多绊线、单策略源、单弹窗出口**：可埋 N 个蜜罐探针，但全部汇流到**同一个** `RiskState`，弹窗只走**同一个** `RiskPromptController.maybeShow()`。禁止每个蜜罐各写一套 if/弹窗（PROTECTION_MAP / 安全官 skill 硬红线）。
2. **不当场翻脸**：命中 → 进 `RiskState` L4 影子期（24~48h，表面照常成功）→ 过期才进 L5 引流弹窗。时间上切断「改动」与「后果」的因果，逆向者定位不到绊线。
3. **本地不自洗白、服务器才能转正**：改时间 / 清缓存 / 删文件不能清风险态；只有服务器签名 `risk_reset` 能降级。

---

## 1. 蜜罐诱饵清单（按"最容易被踩到"排）

> 诱饵 = 故意留亮给破解者改的假东西；真功能不依赖它们。诱饵本身可被删，最稳当作「标记」用（PROTECTION_MAP §5 诚实边界）。

### 🔍 关键词党（jadx 搜 vip/auth/license/free/unlock）

| 编号 | 诱饵 | 逻辑 |
|---|---|---|
| **K1** | 假授权布尔：留亮不混淆的 `boolean VIP_ENABLED=true` / `isVip(){return true}` | 真门走 `isActive()`←`EnvelopeStore`（已混淆），诱饵根本不被业务调。改 true / NOP = 零效果；运行时发现它被调/被 hook → 绊线 markTampered |
| **K2** | 明牌常量：`VIP_END_TIME=9999999999L` / `MAX_FRIENDS=999` | 改过期时间想永久 = 无效（真到期走 `trustedNow`+信封） |
| **K3** | 明牌存储键：`vip_enable`/`hide_list`/`is_vip`/`unlock_all` | 真键是 seed 哈希（`g_<seed>`+`hlst` 等）。dump prefs 搜 vip → 命中假键 → 改写无效；读/写假键 = 绊线（正常代码永不碰） |
| **K4** | 诱饵类名：留亮 `LicenseManager.checkLicense(){return true}` / `VipChecker` | 真授权在已混淆的 `EnvelopeStore`/`StateMachine`。看类名直奔它 → 改了无效 |
| **K5** | 诱饵字符串：`"FREE_MODE"`/`"bypass_license"`/`"crack_ok"` 当 Log TAG 或常量 | 全局搜这类词秒命中，顺藤摸瓜 → 进诱饵区 |

### 📡 抓包党（MITM 看 `/api/v1/guard/envelope`）

| 编号 | 诱饵 | 逻辑 |
|---|---|---|
| **B1** | 信封明牌假字段：payload 加 `isVip:true`/`viptime`/`endtime`/`auth:true`/`is_premium:1` | 真值锁在加密 `k`(registry) + 短租约 + Ed25519。改明牌字段 = 签名不过或无效 |
| **B2** | activate 响应假字段：`vip_level`/`is_paid:1`/`days_left:99999` | 改激活响应想白嫖 → 后续信封拿不到 = 无效 |
| **B3** | 假"解锁"接口：明牌端点 `/api/v1/unlock` 返回 `{ok:true}`，业务根本不依赖 | 重放/伪造 = 无效；调用 = 可标记 |

> 现状（2026-06-12 更新）：**首批绊线真检测已落地装机 ✅**（见 PROTECTION_MAP **§10.9**，唯一权威）：
> - 诱饵 `PromoConfig`（明文 URL + base64 token + 开关）+ 绊线 `CompatProbe.check()`（canary 比对，改诱饵→`markTampered`）。canary 基线现【构建期自动从 PromoConfig 算】（`BuildConfig.CANARY_BASELINE`），改诱饵重编自动跟随、无需手算。
> - 签名绊线 `CompatProbe.checkSignature()`（重签→证书 SHA-256 不符→`markTampered`）。
> - 命中 → 7 天影子期 → `TAMPER_FUNNEL` → 引流弹窗 `FunnelPrompt` + **来电拦截散沙**（`CallGuard.active()` 受 `RiskState.isTamperDegraded()` 闸控）。
> 仍 🟡：K1-K5/B1-B3 其余诱饵的绊线、服务器侧「一码多设备」上报（随 Phase 1D-server / Phase 2）。诱饵骨架 `isVipAuthorized` 留亮等仍在（PROTECTION_MAP §1）。

---

## 2. 触发逻辑（大白话四步）

一句话：**诱饵被碰 → 静静记下 → 装作没事（影子期）→ 过几天才开始弹窗引流。**

1. **埋诱饵**：第 1 节的假字段 / 假文件 / 假方法。真功能不靠它们。
2. **绊线检测**：真值（canary）藏在 SO / 服务器；启动时拿诱饵值与 canary 比对，不符 = 有人改过 = 命中。假文件被创建/改写 = 命中。
3. **记账（不当场翻脸）**：命中 → `RiskState.report()` 记可疑 + `tamper_first_seen` → 进 **L4 TAMPER_SHADOW**（表面照常、能用、不弹）。
4. **过期引流**：影子期（24~48h）过 → **L5 TAMPER_FUNNEL**：打开 App / 回前台 / 进设置页弹窗，点确定后短冷却仍可用。只有联网 + 服务器签名确认付费才能取消。

汇流：所有层探针 → 同一 `RiskState.report(reason, weight)` → L4 → L5 → 同一 `RiskPromptController.maybeShow(reason)`。

---

## 3. 多点埋点矩阵（"多处"= 5 层，汇一处）

| 层 | 蜜罐诱饵 | 绊线检测 | 阶段 |
|---|---|---|---|
| L1 假字段/MMKV | seed key 装"授权状态"，真状态在 SO/派生 | 启动比对诱饵值与 SO 内 canary，不符=tamper | 🟡 本地可先做 |
| L2 诱饵方法 | `isVipAuthorized()`/`nativeIsAuthorized()` 显眼诱饵 | 返回值/调用栈异常、被 hook 痕迹 | 🟡 本地（附加摩擦，有限） |
| L3 假文件/缓存 | seed 名"授权缓存"文件，正常端永不写 | 文件被创建/改写 = 有人动手 | 🟡 本地可先做 |
| L4 假 registry 项 | 混入永不命中的假 hook 配方 | 谁依赖/触发它 = 逆向者在试探 | 🟡 本地可先做 |
| L5 服务器假接口/假字段 | envelope `vip=true`/`endtime=9999` 诱饵；旧接口正常端永不调 | 服务器看谁读假字段/调旧接口 → 标 suspicious | 🟡 随 Phase 1D-server |

---

## 4. 域名加密引导段（C2）— 本轮新增设计 🟡

> 用户 2026-06-11 拍板：**C2 方案**（域名独立成 cert-only 引导段）+ **硬 fail-closed**。

### 4.1 为什么要做 / 关键认知
- 域名抓包一看就出来；"域名混淆"现在的作用只剩**抹掉 grep 明文、抬高静态分析成本**（迷彩），**不是安全增量**。
- 真防线已在：`AuthEnvelopeVerifier` 的 **Ed25519 验签 fail-closed**——破解者把域名改指向自己的假服务器，假服务器没私钥签不出合法信封 → 客户端拒收 → 散沙。所以"域名被改"本身就是白改。

### 4.2 为什么是 C2（独立引导段）不是 C1
- `gen_registry_cipher.py` 有 `prod_server_lock` 模式：整份 registry 要**服务器种子 S_rel** 才能解密。
- 若域名和主 registry 同一份，将来切 prod_server_lock → **要联网拿种子才能解出域名，但拿种子又要先有域名联网 = 死锁**。
- **C2**：域名独立成一份 **永不折服务器种子** 的 cert-only 引导段（`derive_bootstrap_key`：折"段+证书 SHA256"+域分隔常量，不折 S_rel）。主 registry 以后照常可切 prod_server_lock，两不误。
- 主 registry 的 4 条目 + `registry_self_test`（硬断言 `entries==4`）**不动**——引导段独立。

### 4.3 行为
- 引导段只放**授权服务器（role A）域名**：`primary=https://zxmqq.shop`（已验证能连；**zxmqq.shop 就是授权服务器，别被 .shop 后缀骗，它不是商城**）、`backup1=https://miyou.lol`（授权备机，HTTPS 反代就绪后自动生效）。
- **miyou.pro 是聊天+引流（role B，客服侧，另一个项目管），绝不进引导段授权列表。**
- 散沙时 `AppConfig.guardServerList()` 返回空 → 无服务器（**硬 fail-closed**）。重打包的包连不上服务器——反盗版正好要这效果。
- `SHOP_URL` 保留明文（公开展示用）。

### 4.6 装机实证 ✅（2026-06-11，小米9 设备 609b4b18）
- 状态升级：C2 域名加密引导段 🟡 设计稿 → **✅ 已落地装机 PASS**。
- L1 日志原文存盘：`03_execute_执行任务/S3a0_ServerSeed设计/C2_bootstrap_装机PASS_20260611_230252.txt`
- 关键行：
  - `[native] registrySummary=... entries=4 ...` + `BATCH1/PHASE1A~1E_VERIFY PASS`（主 registry 未被 C2 碰坏）
  - `[native] bootstrapEndpoints count=2 primary=https://zxmqq.shop`（引导段真机解出授权服务器列表）
  - `[native] C2_VERIFY PASS`
- 验证点：① 主链未坏 ✅ ② 引导段 cert-only 解出 [zxmqq.shop, miyou.lol] ✅ ③ 域名在发出的 SO 里只有密文（grep/strings 搜不到 zxmqq/miyou）✅。
- 编译：`gradlew assembleDebug` 两 ABI（arm64-v8a + armeabi-v7a）BUILD SUCCESSFUL。

### 4.4 涉及文件（9 个）
```
新建 native_core/bootstrap_endpoints.json   域名明文源（唯一真相）
新建 tools/gen_bootstrap_cipher.py          生成加密引导段（镜像 C++ key 派生）
生成 native_core/src/bootstrap_cipher.inc   跑脚本生成（不手改）
改   native_core/src/config_crypto.cpp       加 derive_bootstrap_key()
改   native_core/include/guard_core.h        声明 derive_bootstrap_key / bootstrap_get_endpoint
改   native_core/src/registry_loader.cpp     加 bootstrap_get_endpoint() + 自检
改   native_core/src/guard_core.cpp          加 JNI nativeGetEndpoint
改   src/main/java/com/ghost/assist/core/NativeBridge.java   加 getEndpoint() + native 声明
改   src/main/java/com/ghost/assist/core/AppConfig.java      guardServerList() 改读 native，硬 fail-closed
```
落地后需：重跑 gen 脚本（python + cryptography）→ NDK 重编 SO → 重装机；验收看 `PHASE1*_VERIFY PASS`（主链未坏）+ 新增 bootstrap endpoint 能读出主机 + 能激活/心跳。

### 4.5 三个"服务器角色"澄清（别混，2026-06-11 用户确认）
| 角色 | 干什么 | 端点 | 域名 | C2 是否加密 |
|---|---|---|---|---|
| A 守护授权服务器 | 发激活/签名信封/心跳（真锁） | `/api/v1/activate`、`/api/v1/guard/envelope` | **`zxmqq.shop`（主，别被 .shop 后缀骗，它是授权服务器不是商城）+ `miyou.lol`（备机，HTTPS 待就绪）** | ✅ C2 加密的就是它俩 |
| B 聊天+引流/客服 | 盗版被引流过来聊天/联系客服 | `/api/invite/quick`（落地页） | **`miyou.pro`**（taobaochat = `E:\taobao`） | ❌ 不进 C2；**另一个 AI 负责，本项目不管** |
| C 商城/购买页 | 给用户看的购买入口 | `SHOP_URL` | 现为 `zxmqq.shop`（待复核：引流落地是否应改指 miyou.pro，归 B 侧另一个 AI 协调） | ❌ 明文（公开） |

> ✅ 已确认（用户拍板）：`zxmqq.shop` = 授权服务器（role A），`miyou.pro` = 聊天/引流（role B，不进 C2）。引导段授权列表 = `zxmqq.shop`（主）+ `miyou.lol`（备机）。
> ⚠️ 遗留待复核：`PiracyNotice` 引流弹窗的目标 URL（现 `SHOP_URL=zxmqq.shop`）是否应改指 miyou.pro 的客服落地页——这属 B 侧，需与另一个 AI 协调，本项目暂不动。

---

## 5. 引流落地链（B 系统，本项目不实现，仅记录衔接点）

- 盗版进 L5 funnel → `RiskPromptController` 弹窗 → 文案/URL 指向 **B 客服接待系统**的落地页（`/api/invite/quick`：进页 → 手动输验证码 → 接客服，爬虫爬不了）。
- B 系统 = taobaochat（`E:\taobao`，FastAPI+Vue3，自带邀请码 + 防爬虫），**由另一个 AI 负责**。本项目（guard_native）只负责把 funnel 弹窗的 URL 指过去。
- ⚠️ 遗留：`SHOP_URL`/PiracyNotice 现指 `zxmqq.shop`（授权服务器），引流本该指 `miyou.pro` 客服落地页 —— 待与 B 侧 AI 协调后改。

## 5b. 蜜罐命中上报 + admin 可见（🟡 未做，交另一个 AI / 随 Phase 2）

> 目标：服务端 admin 控制台能看到"哪台设备踩了蜜罐"。

- **上报走授权服务器 `zxmqq.shop`，不走 miyou.pro**（miyou.pro 是客服/引流，职责别混）。
- 链路：客户端命中蜜罐 → `RiskState` 记 tamper → 下次 `GuardHeartbeat`（已有，发 zxmqq.shop）**心跳里带一个 risk 字段**上报 → 服务端标设备 suspicious/tampered → admin 控制台展示。
- **防伪造**：risk 字段必须进心跳签名 / 设备绑定，不能裸上报（否则破解者伪造 risk=0）。
- **前提**：得先埋第 1 节的「绊线真检测」（K1-K5/B1-B3 诱饵被改 → markTampered），否则心跳里永远 risk=0。
- 三段缺一不可：① 绊线真检测（客户端）② 心跳带 risk 上报（走 zxmqq.shop）③ admin 展示（服务端）。

---

## 6. 交叉引用（权威在哪）

- 防破解总账 / 阶段门控：[`../PROTECTION_MAP.md`](../PROTECTION_MAP.md) §5（蜜罐配对）· §6（弹窗漏斗）· §10.4（两种党两个蜜罐现状）· §10.6（服务器真锁现状，唯一权威）
- 加密 / 风控 / 蜜罐规则：网络安全官 skill `guard-security_网络安全官/SKILL.md`（RiskLevel L0~L6 · 影子期 · 转正闭环 · key 派生 · 字段伪装）
- registry 加密链：`native_core/registry_8071.json` → `tools/gen_registry_cipher.py` → `native_core/src/registry_cipher.inc`
