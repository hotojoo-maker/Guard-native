# P_DeviceFunnel 工作日志 — 装机漏斗 / 设备台账

> 开卡：2026-07-03 · 会话 Q44（chat-mcp） · 执行范围：**客户端**（服务器 `/api/v1/checkin` + 设备表归另一个 AI / 服务器运维）
> 分工纪律：客户端只递增 versionCode 覆盖安装；不在已装机线原地改 s_rel / registry / 证书 / 包名 / schema（新东西 = 服务器 AI 开新 release_id）。

## 一、功能 + 业务场景（用户确认口径 · Q44）

**一句话**：做「装机漏斗 / 设备台账」——让**客服后台**看到**每一台装了我们包的手机**（哪怕未激活 / 未登录 / 未付费），带设备画像，为按机型档次定制套餐 / 价格的营销打底。**纯后台自看。**

**两段式（`device_id` 当「拴绳」，第一次发送就带）：**

| 阶段 | 时机 | 发什么 | 钥匙 |
|---|---|---|---|
| Stage1 checkin（**新**） | 微信首次冷启、未激活 | `device_id` + 设备画像（Build.* + re） | device_id 建档 |
| Stage2 activate（**加画像**） | 输卡密激活 | card_key + `device_id`（已在发）+ 画像 | device_id 绑 card_key |
| health（**已做，不动**） | 登录后 | `device_id` + wxid(`acct`，已在发） | device_id 绑 wxid |

→ 服务器按**同一 device_id** 幂等 upsert：一台机的 **画像 + 卡密 + 微信号** 最终串成一条。

**发送格式**：设备字段**复用现有 wxid(`acct`) 的发送格式**（flat field，不新造嵌套结构）。

## 二、已做 / 不重复（防上下文幻觉）

- ✅ wxid 上报 = `reportHealth` 的 `acct`（`EnvelopeClient.java:201`），激活后走 health，已在发 → **不动**。
- ✅ re（root/RE 弱信号）= `A2SignatureSpoof.observeBorrowed/getBorrowedEnvSignal`，已进 `fetchEnvelope:151` / `reportHealth:200` → **复用**。
- ✅ device_id / dm = `activate/fetchEnvelope/health` 已在发（SHA-256(自然 android_id) 派生），**非 user_key**。
- ✅ item① android_id 停喂自然值 = `A2SignatureSpoof.feedOfficialSsaid:153` 已注释（另一条线，不在本卡）。

## 三、本卡要新写的客户端（收窄）

1. 🔬 Stage1 `EnvelopeClient.checkin(deviceId)` → POST `/api/v1/checkin`（免授权）：device_id + Build 画像 + re + release_id + app_version。
2. 🔬 Stage2 `EnvelopeClient.activate()` body 加 Build 画像 + re（device_id / card_key 本就在）→ 兜底 Stage1 没网没发成。
3. 🔬 触发：`ModuleMain` 冷启 MAIN 段（nativeInit 后、best-effort `runAsync`）；`dr` 标志控一次；失败下次冷启重试（至少一次 / 最终一致）。
4. 🔬 `EnvelopeStore`（`ncl_env`）加 `dr` get/set。

## 四、三官改前审查（Q44 · 一人分饰 · 均 WARN = 可做有条件）

- **防封官 WARN**：MAIN-only / 只读 `Build.*`（非 `ro.boot.*` ✓） / 不枚举包（re 借眼睛 ✓） / 不碰 `:push` ✓。新增 = 一条冷启外发，混启动风暴、KPI 密度非硬门；**无真机封/未封实证 = L4**，装机后抽 `frida_stats` + 真号观察补证，不吹「防封安全」。
- **授权检查官 WARN**：`activate`=授权入口（强制触发 #4/#10）、`EnvelopeStore`/`Bridge`=保护区。铁律：checkin / 画像 = **纯遥测**，不进 Filter/State/Auth 决策、不 `setAuthState`、未授权 **不授予任何能力**（只上报）。
- **网络安全官 WARN**：`post()` 已强制 https ✓；checkin 必 best-effort + `catch(Throwable)` + **绝不污染授权态**（同 reportHealth）；Build.*+re 低敏感；不发 secret / key / envelope 料。
- **结论**：三官 WARN（守「只上报不门控 + fail-open + 防封 L4 不吹」即可）→ 落码后走**改后审查**。

## 五、服务器契约（交接服务器 AI · 本卡不做）

> ✅ **服务器侧已落地 + 部署 + 线上验证（2026-07-03，miyou-server 运维 AI）**。实际端点 = **`POST /api/v1/guard/checkin`**（与 `guard/envelope`/`guard/health` 同族；上文 `/api/v1/checkin` 为早期草稿口径，**以 `guard/checkin` 为准**——已由真机 MI 9 checkin 落库实证客户端确走此路径）。详见 `I:\miyou-server\SCHEMA.md §3.7`。

- ✅ 新 `POST /api/v1/guard/checkin`（免授权，无 card_key/token/验签）：按 `(product_id, device_id)` 幂等 upsert 进**现有** `guard_device_state`（与 activate/envelope/health 同源 device_id，不新建表）；未激活也入表。新增列 `first_checkin_ts/last_checkin_ts/checkin_count/dev_brand/dev_model/dev_os/re_signal`（+ 复用 `install_id`），保持 status=normal/tier=1、不动 risk/fail、**不消耗重装/激活硬上限**，顺带回填自校验 `dm`（牙③）。固定回 `{"status":"ok"}` = 焊死信号。
- ✅ 漏斗：`/admin/api/releases` 透出 `checkin_devices`（装了并上报过）/ `checkin_unactivated`（装了但没激活）；后台「设备总览」KPI + 「发行线」页已显示，设备行可展开看上报画像。
- ✅ activate 侧补画像（Stage2 兜底）**已接**：`activate` body 带 `client{brand,model,os,install_id,re}`（**字段名与 checkin 完全一致**，客户端复用同一个 JSON builder 即可）→ 激活成功用 `_store_device_profile()` 回填画像；**不写 first_checkin_ts/checkin_count**（Stage2 设备不计入 `checkin_devices` 漏斗），老客户端不带 `client` 零副作用。已随 `standard --go` 部署 + selftest T13/T13b 覆盖。
  - **交接客户端**：Stage1 checkin 与 Stage2 activate 用**同一个** `client` 对象（`brand`=Build.BRAND / `model`=Build.MODEL / `os`=Build.VERSION.RELEASE / `install_id`=device_id / `re`=root/RE 弱信号 int）。activate 再带 `device_id`+`card_key`（本就在发）即可。

## 六、待办

- [x] Stage1 `checkin()` + `ModuleMain` 冷启触发 + `dr`
- [x] Stage2 `activate()` 加画像 — **已接**（先取消后补齐，见 §九补记）：客户端 activate 带 `client{brand,model,os,re}`；服务器 activate 回填已部署
- [x] ReadLints（净）
- [x] 改后三官审查（PASS）
- [x] 装机 L1（官替 v1.7，certBind/role/BATCH1/recipeOk + checkin dr set）
- [x] 版本号 → v1.7 / versionCode 15（v1.6 已发 → 递增）
- [ ] 服务器侧确认该 device_id 行 brand/model/os 真落（验嵌套 client 路径端到端）
- [x] 共存版 v1.7 出包 + 装机 L1 PASS（com.tencent.mn：certBind=e3e13a49 / role=1 / BATCH1 / recipeOk=true / checkin dr set / push role=2）

## 七、关键文件

- `src/main/java/com/ghost/assist/net/EnvelopeClient.java`（checkin + activate 加画像）
- `src/main/java/com/ghost/assist/ModuleMain.java`（冷启触发）— **核心保险区**
- `src/main/java/com/ghost/assist/net/EnvelopeStore.java`（dr 标志）— **保护区**
- 服务器：`I:\miyou-server`（另一个 AI）

## 八、进展流水

- 2026-07-03 Q44：开卡。已对代码实核 ①②③ 现状、加载防封官/授权检查官/网络安全官/执行/git 保姆 skill、完成改前三官审查。下一步：git 接入前快照 → 落 Stage1/Stage2 客户端码。

## 九、as-built（2026-07-04 MMQ68 · 全链闭环 · 代码即真相）

> **as-built 契约（防以后误改）**：checkin 走 **`POST /api/v1/guard/checkin`**（非 §五草拟的 `/api/v1/checkin`）；body = `product_id/release_id/device_id/dm/app_version` + **嵌套** `client{brand,model,os,install_id,re}`。`device_id/dm` 在顶层。服务器按 `(product_id,device_id)` 幂等 upsert 进 `guard_device_state`，回 `{status:"ok"}` 才焊 `dr`。

**落码**（`EnvelopeClient.checkin` + `deviceProfile()` memoize `Build.BRAND/MODEL/VERSION.RELEASE` + `EnvelopeStore.dr` + `ModuleMain.reportDeviceCheckinIfNeeded` MAIN 冷启 best-effort runAsync）。改后三官自审 PASS。

**与 §一/§五 计划的 3 处偏离（本会话用户拍板，勿当 bug 改回）**：
1. 字段用**嵌套 `client{}`**（对齐 `reportHealth` 的 `client.acct/re` 真实结构），非 §一「flat」草案。
2. 字段收窄 **brand+model+os 三项**，非 §五六列。
3. ~~Stage2 activate 加画像取消~~ → **补记（同会话稍后补齐）**：服务器接了 activate 回填后，客户端 `activate` 也加 `client{brand,model,os,re}`（同 checkin 嵌套结构，搭现成请求零新增网络事件）。覆盖「冷启没网→checkin deferred→后来有网又激活」边界。activate 画像**只在卡密激活时发**，已激活设备不重发。

**版本**：`build.gradle` 14→15 / 1.6→1.7 / `GUARD_PRODUCT_VERSION` v1.7。连带出车（本就在工作区未提交）A2 android_id no-op hook / SO 16KB 页对齐 / RiskState 注释 / kdf_vectors 重算 → 对老用户零行为变化。

**装机 L1**（MI 9 `609b4b18`，官替 `-r` 覆盖，logcat 实证）：`certBind=e3e13a49` / `role=1 MAIN` / `BATCH1 PASS` / `recipeOk=true`(5) → 老用户授权/密友零回归；checkin 服务器上线前 `[checkin] deferred`（预期），上线后冷启 `[checkin] reported (dr set)` ✅（服务器 `/api/v1/guard/checkin` 已部署 zxmqq.shop 主节点）。

**补记3（借官方眼睛扩面 · 反破解情报）**：`A2SignatureSpoof` 的 `re` 弱信号从 4 包扩为分类 bitmask（`0x1`root/`0x2`RE重打包/`0x4`hook框架/`0x8`RE工具开无障碍）——被动借微信自己的 `getPackageInfo`(c$p.aa) + `Settings.Secure.getString(enabled_accessibility_services)`，**我方零主动读/零 ro.boot/零 native**（守铁律5/23）。BORROW_HASH 扩到 17 条 root/RE/hook 包（哈希存储，明文只在注释）。装机 L1：MI9 冷启约 14s 后 WeChat 扫描 → `[A2SIG] borrowed env=0xb`（root+RE+无障碍）。`re` 经既有 checkin/activate/health 上报，服务器按位判可疑；只上涨不回落。⚠️ 时机：首次 checkin 可能 re=0（扫描未到），后续 health 带真值——机制使然。解锁状态**借不了**（微信 8071 走 native `__system_property_get`，不过 Java；证据 explore 复核账 + `8071_DYNAMIC_DETECT_PLAINTEXT`）。

**交付**（含 Stage2 + re 扩面重出，旧 hash 作废）：`guard_official_v1.7_8071.apk`（SHA-256 `7E896C33…E80B`）+ `guard_coexist_v1.7_8071.apk`（SHA-256 `EFAF61A5…4B7D`，各 ≈250MB）放桌面供网盘分发（含 Stage2 + re 扩面，最新 hash）。官替+共存装机 L1 PASS（certBind=e3e13a49 / role=1 / BATCH1 / recipeOk=true / checkin dr set / borrowed env=0xb）。
- 2026-07-03（服务器运维 AI · miyou-server）：**服务器侧 checkin 端点落地并已部署主节点 `zxmqq.shop`**。
  - 改动：`SCHEMA.md §3.7`+迁移记录（文档先行）→ `db.py`（`guard_device_state` 幂等加 7 列 + `record_guard_checkin()` + `list_releases` 漏斗指标 + `list_health_devices` 透出画像）→ `server.py`（公共路由 `/api/v1/guard/checkin` 无口令）→ `admin_v2.html`（设备总览/发行线漏斗小卡 + 设备行展开看上报信息）→ `API_REFERENCE.md`。
  - 验证 L1：`checkin_selftest.py` 43 项全 PASS；`deploy.py code --go` + `standard --go` 冒烟全绿（ping/server.log clean/KDF 向量/三页面 200）；线上只读核账 = 真机 **MI 9（Xiaomi/os 11/re=0）** 于 2026-07-04 11:16(北京) checkin 落库 `android_8071`，证客户端确走 `guard/checkin`。
  - 备份：`I:\miyou-server\backups\*_pre_checkin_*`；远端回滚点 `/root/deploy_primary_20260703_202111.tgz`（+auth.db.bak）。
  - ⚠️ 客户端 worklog §3.1 写的 `/api/v1/checkin` = 草稿；实装 = `/api/v1/guard/checkin`（已改 §五）。~~Stage2 activate 补画像服务器端未接~~ → **已接**（2026-07-04 MMQ68，见 §五第 49 行 + §九补记；服务器 `_store_device_profile()` 回填已部署）。
