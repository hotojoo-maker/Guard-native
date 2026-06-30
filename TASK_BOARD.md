# TASK_BOARD — 任务看板（4 窗口 + P 任务进度）

> ⛔ **AI 读此文件的绝对铁律 / AI HARD RULES**
> - ✅ = 有装机日志原文的才算完成，无日志不得推进"下一步"
> - ⬜ = 代码已写但未装机 — 不等于完成，不能当基础继续堆功能
> - 状态不明 → **停下来问用户，禁止猜测，禁止推断**
>
> 接手前看：[`docs/README.md`](./docs/README.md) + [`CLAUDE.md`](./CLAUDE.md) + [`HOOKMAP.md`](./HOOKMAP.md)
> 防破解/防盗版总账（含上线前门控）：[`PROTECTION_MAP.md`](./PROTECTION_MAP.md)
> **账实唯一权威（编号↔任务↔状态↔文件夹）：[`PROJECT_INDEX.md`](./PROJECT_INDEX.md) §零 功能总清单**。本看板若与之冲突，以 §零 为准。重复编号 **P25 / P26 / P26C**（同号两义）已在 §零 摊开，下方相应处已标注。
> 更新时间：2026-06-29（相位收口：v1 探索期→上线维护期；旧 4 窗口已归档）
> **当前底座：微信 8.0.71**（D-014）
> 维护人：guard-dispatch_总调度

---

## 一、当前阶段：上线维护期（2026-06-29 相位切换）

> v1 功能开发探索期（旧 4 窗口 W1–W4）已全部收口/归档，不再作为当前任务框架。当前主线三条：

| 主线 | 范围 | 状态 |
|:--:|------|:--:|
| ① 上线维护 | 防破解 / miyou-server / 发版(官替+共存) / 绊线 / KPI | 🟡 进行中（cert-sync ✅，A2 闸等 🟡，详 §五） |
| ② 按需加功能 | E3 改余额 / C5 语音转发 / §6a / 选人列表隐私缺口 等 | ⬜ 一功能一卡（见「未做功能索引」） |
| ③ 版本适配 | 换微信版本的 hook diff 流程 | 📘 见 `docs/VERSION_UPGRADE_SOP.md` |

> 旧 4 窗口去向：W1 P20/P20B、W2 P21、W3 P17 → `07_archive_归档/`；W4 P18 本轮跳过。历史明细见 §五。

---

## 二、4 窗口任务详细（每个窗口的目标 + 必读 + 产出）

---

### 🟦 W1 B 模块触发器 + 搜索（P20B/P20）

**状态**：P20 搜索 🟡（联系人 L1 已证，全场景待补）；P20B 触发器 🟡（B2/B5/B6 ✅；B1 🟡 logcat 待补；B4 因用户设备无返回键，按确认不阻塞；KPI 轻采样已记录，发版前重测）。

**下一步**：
1. P22 普通消息通知 + 铃声功能转 v1.1，不阻塞当前 v1。
2. 搜索高亮归 UI 优化（旧称 P26C；号已归「隐藏指定通讯录标签」，见 §零），不阻塞 P20。

**详情**：`07_archive_归档/P20_搜索拦截/result.md`、`07_archive_归档/P20B_BTriggers_SearchUnlock/worklog.md`、`docs/HOOK_MAP_8071_AUTHORITATIVE.md` §8b。

---

### ✅ W2 朋友圈小红点 P21

**状态**：✅ 主线收尾。Layer0b 已装机实证；P21B WithAll/bm live Cursor 过滤已装机；rm 代码同路径覆盖，后续有“与我的互动”入口时可补 L1，但不阻塞当前 v1。

**下一步**：仅做证据归档/备用复验；不改 D1/D2/D3 已验 hook。

**详情**：`07_archive_归档/P21_MomentsRedDot/worklog.md`、`HOOKMAP.md` §二。

---

### ✅ W3 会话过滤（P17）

**状态**：✅ 2026-05-20 装机验收通过，不动已验 hook。

**详情**：`07_archive_归档/P17_会话LSPosed/result.md`、`docs/HOOK_MAP_8071_AUTHORITATIVE.md` §8a。

---

### 🟧 W4 离线资料库采集（P18）

**状态**：⬜ 本轮跳过。空白 LSPosed KPI 基线不作为当前 v1 阻塞项。

**下一步**：后续进入正式发布门控或质检重档时再补跑 `frida_stats.js`；业务 Proto/网络采集按 P18 brief 另开。

**详情**：`03_execute_执行任务/P18_离线采集/`、`TOOLS_INDEX.md`。

---

## 三、依赖关系

```
W1 B触发+搜索 ─→ P22 普通消息通知/铃声转 v1.1；搜索高亮归 UI 优化（旧 P26C 号已归隐藏标签）
W2 P21        ─→ P21 主线收尾；rm L1 / Layer2 / v18 tab badge 仅作备用复验
W3 P17        ─→ 会话过滤已验收，不作为新任务入口
W4 P18        ─→ 本轮跳过；正式发布门控再补 KPI
```

**当前启动顺序**：
1. 先做文档/证据收敛与 git 快照，避免当前脏工作区继续漂。
2. P22 普通消息通知 + 铃声功能转 v1.1。
3. P18/KPI 基线本轮跳过；正式发布门控再补。

---

## 四、并发铁律

| # | 规则 |
|---|------|
| 1 | 每窗口开工先改 §一 表"占用至"列 |
| 2 | 只动自己 P 任务目录（`03_execute_执行任务/<你的P任务>/` 等；已归档任务见 `07_archive_归档/`）|
| 3 | 改根目录看板（HOOKMAP / TASK_BOARD / FAILURE_LOG）前先 git pull |
| 4 | 关任务前必跑 `frida_stats.js` 对比基线 |
| 5 | 关任务前更新 [`HOOKMAP.md`](./HOOKMAP.md) 对应行 ⬜→🟡 或 🟡→✅ |
| 6 | 每个窗口完成后写 5-10 行交接快照到 `04_review_审稿复核/W<N>_<日期>.md` |

---

## 五、P 任务历史

| P 号 | 任务 | 状态 | 完成日 |
|:--:|------|:--:|------|
| P15 | 脚手架 | ✅ | 2026-05-19 门控通过；详见 P15 result。|
| P16 | 朋友圈 Proto（8.0.71 适配） | ✅ | D1–D3 + KPI 2026-05-20；L0v3/P19 另开 |
| P17 | 会话 LSPosed | ✅ | 2026-05-20 会话隐藏验收；8071 主路径见权威 §8a。|
| P18 | 离线采集 | ⬜ | 本轮跳过，不阻塞当前 v1；正式发布门控再补 KPI。 |
| P19 | 通讯录隐藏 F07 | ✅ | 2026-05-20 装机验证；详见权威 §6。|
| P19B | 通讯录【标签】成员隐藏 F07B | ✅ | 2026-06-01 复跑实证；详见 `07_archive_归档/P19B_ContactLabel/result.md`。|
| P20 | 搜索 + 密码入口 | 🟡 | 联系人 L1 已证 + B6 收口；证据 `07_archive_归档/P1E_Filter读Registry/logs/search_anchor_verify_20260609.log`（`[SF:gv] blocked` 联系人）+ `07_archive_归档/P20_搜索拦截/result.md`；全场景 runner 待补。|
| A2  | 密友列表（数据层 + 导入 UI） | ✅ | 原生 `SelectContactUI` 导入；详见权威 §3。|
| A3  | 密群（数据层 + Filter union + 导入 UI）| ✅ | 过滤链 + 原生 `GroupCardSelectUI` 导入；详见权威 §4。|
| **P23** | **F08 防撤回（C1）** | ✅ | 2026-05-31 L1 装机；详见 P23 result / 权威 §2。|
| P_NC1 / S3a / S4 / S3b | native 加密 + 授权真锁链 | 🟡 | `BATCH1/PHASE1A~1E PASS`、S4 Ed25519、S3b LeaseClock、当前 `android_8071` server seed 解 registry 已验；仍待删 Filter fallback / V3 发行线对齐 / RiskState 真降级。详见 `03_execute_执行任务/S3a0_ServerSeed设计/result.md` 与 `PROTECTION_MAP.md` §10.6。|
| **P22** | **PushFilter 通知策略层** | 🟡 | 主拦截/来电/未读已收口；普通消息通知 + 铃声功能转 v1.1，详见 `docs/P22_PushFilter_VoIP.md`。|
| **P_CV1** | **通讯录 V↔H 热切** | 🟡 | 原 logcat 缺失，仅存 L1 装机截图(2026-05-27)+L2 代码互证；证据 `07_archive_归档/P_CV1_通讯录V态热切/worklog.md`；待补 logcat 双通原文。|
| **P_PF2** | **语音/视频来电拦截 + CallGuard** | ✅ | 2026-05-29 装机验证；详见 `docs/P22_PushFilter_VoIP.md`。|
| **P_CF3** | **H→V 热切按时间排序** | ✅ | 2026-05-29 装机验证；冷会话问题转 P_CF4。|
| **P_CF2** | **会话列表越界崩溃** | ⬜ | 待复现；详见 P_CF2 记录。|
| **P_CF4** | **冷会话 H→V fresh-warm** | ✅ | 普通有历史 hidden id 已由 P_ConvWarm/P26 收口；零历史 wxid 仍受微信 DB 限制。|
| **P_CF5** | **群成员 wxid 误删修复 + 置顶优先** | ✅ | 2026-05-29 装机；提交 `65c24bb`。|
| **P_NF1** | **密友普通消息通知策略** | 🟡 | 转 v1.1；后台震动已有实证，普通消息完整链路后续收口。|
| **P_NF2** | **铃声功能 / 前台消息声** | ⬜ | 转 v1.1 backlog，需实现可听见的 SOUND 档或复用微信原生播放方式。|
| **P_NF3** | **:push 独立震动** | 🟡 | route1 代码已写，纯 :push 场景待实证。|
| **P_NF4** | **密友未读计数过滤（UNREADFIX）** | ✅ | 2026-06-06 装机实证；默认隐藏态密友未读数在底部 tab + 顶部「微信(N)」标题被**过滤/隐藏**，打开「显示密友未读消息数」开关则**显示**；证据 `03_execute_执行任务/P22_PushFilter/pnf4_unread_20260606.log`。|
| **E2** | **伪装订位（全局伪造定位）** | ✅ | 2026-06-07 装机验证（提前于 v2/v3 应用户要求）：`moduleE/FakeLocation` hook `pz0.h.c` 注入伪经纬度 → 发位置/共享/朋友圈/附近的人全局生效；设置页「特色功能」复用原生选点页设置坐标（talker=filehelper 防误发）；关闭复原已验。详见权威 §一.1。|
| **P_CertSync** | **证书三端同步（cert-sync-v1）** | ✅ | 2026-06-29 commit `20a45b1` push 完成（`devin/cert-sync-v1`）+ 装机验证 PASS（certBind=e3e13a49 三端对齐 / decryptSelfTest=true / tamper=正常）。scatter 是 prod_server_lock 设计预期，跟 cert-sync 无关。|
| **P_CertConverge** | **证书唯一真源收敛（官替/共存各一把 + 硬闸）** | ⬜ | 2026-06-29 登记；**无客户窗口期**。根因 L1：coexistDebug `[cp] cert mismatch`→影子期。任务卡 `03_execute_执行任务/P_CertConverge_证书收敛/任务卡.md`；只读对账 `04_review_审稿复核/CERT_RECON_证书对账表_20260629.md`。待：D-017 标过时、四端脚本、L1 加项、§3.A 文档减法、debug 政策 A/B/C 拍板。|
| **P_RegistryUnify** | **registry 单一真源归一（C5a 收口）** | 🟡 | 2026-06-29 commit `df51951` push 完成（`devin/registry-unify-v1`）：MomentsFilter 4 个混淆字面量（jw1.d/wq.c1/wq.y0/ii5.b）迁入 `registry_8071.json`→`RegistryFallback`；合并时顺手加 `gen_registry_fallback.py` 的 `SKIP_ENTRIES={"a2.sig"}`（防 A2 OFFICIAL_DER 泄漏到 Java fallback）+ pop ACTOR_FIELD_NAMES 归一工作区改动；待装机验证 PR。|
| **P_ArchAudit** | **架构基线盘点（arch-audit-v1）** | ✅ | 2026-06-29 Devin commit `6ca5333` on `full-restore`（未 push，token 失效）：6 份文档 `docs/arch_audit_2026q3/`（本地 `C:\Users\Me\Desktop\gn_fullrestore`）。核心发现见 `PROTECTION_MAP.md §10.7` 2026-06-29 段。|
| **P_DebugGateUnify** | **debug 散点三门归一** | ✅ | 2026-06-29 Devin commit `0b3121a` push（`devin/debug-gate-unify`）：12 处散点收敛到 `AppConfig.isDevBuild/isDiagnostics/isDebugSurface`；三变体（officialDebug/coexistDebug/officialRelease）编译全过；禁入区 + 主仓 96 脏改动未碰。PR: https://github.com/hotojoo-maker/Guard-native/pull/new/devin/debug-gate-unify |
| **P_AntiBanGateExtract** | **GuardRuntime A2 闸拆出** | 🟡 | 2026-06-29 派 Devin：`isAntiBanReady`+`evalAntiBanWindow` 拆到新 `core/AntiBanGate`，GuardRuntime 回归纯配方出口；分支 `devin/anti-ban-gate-extract`。中难度，解 fail-OPEN/CLOSED 哲学冲突。|
| **P_Fc5gMerge** | **fc5.g 三处 anchor 合一** | 🟡 | 2026-06-29 派 Devin：ContactFilter / ContactLabelHideGuard / ContactLabelMemberFilter 三处 `fc5.g` 合并进 `registry_8071.json contact.address.item_class`；分支 `devin/fc5g-anchor-merge`。低-中难度。|
| **P_AntiBanGate** | **防封授权闸（重构·版本轴）** | 🟡 | 配方卡 v1 定稿（设计only）；D-017 定性=重构、按版本轴（官替/共存/管理）、不受 D-015 新功能序卡。**A2-1 签名轴码已落工作区（未提交，快照 5dd0a52 之上）：A2SignatureSpoof(新)+GuardRuntime.isAntiBanReady()+ModuleMain 接线**；改前(2026-06-23 授权检查官 9项 WARN)+改后(2026-06-25 双官 WARN)双审已过；**2026-06-30 回正**：旧「闸空转」已解除——Route B/D-018 改读本地常量 `OFFICIAL_DER_HEX`(751B)，闸已通，未授权冷启 L1 `[A2SIG] installed der=751B`(worklog 2026-06-26d)；**包名轴 `A2PkgPathSpoof` 亦已落码接主线**(ModuleMain L246，仅共存生效)。剩余=android_id 轴未落码(design-only)+牙3 全局 W 未删(Batch3)+D-020 完整时间闸待 L1。EnvelopeStore/LeaseClock/Bridge 本期未改（A2-3/A2-4 再共审）。watch（isAntiBanReady 计时自算不连坐隐私门 / A2 料只进 registry_pack）。详见 `03_execute_执行任务/P_AntiBanGate_防封授权闸/worklog.md` 2026-06-25 节。|
| **M6c** | **隐藏设置页「存储空间」入口（按需加功能）** | ✅ | 2026-06-30 官替 debug LSPosed 装机、用户复验（有授权 + 隐身态 → 设置页「存储空间」消失；V→H 热切即时隐藏）。授权分支 `isVipAuthorized()&&getState()!=VISIBLE`（防白嫖，不绑 f1/config，对齐 f2）；独立 Filter `moduleD/SettingsStorageHideGuard.java` + ModuleMain 注册；详见权威 §6c / `HOOKMAP.md` M6c / D-024。|

> 编号从 P15 起，是接续 apk2 项目 QE66 的 P14（保持跨项目可追溯）。`P_CV*` 系列与 `P_NC*` 同属语义号，不占 v2 路线图 P26–P30 / v3 P31–P33 编号位。
>
> **账实说明（2026-06-10 对齐）**：本表部分编号**无独立任务目录**，实体在别处——`P_NC1`→`07_archive_归档/P1A…P1F`；`P_NF1~4`/`P_PF2`→`03_execute_执行任务/P22_PushFilter`；`A2/A3`→`P_IMPORT` + 权威 §3/§4；`P24`→`docs/classmap/`。完整「编号↔文件夹」对照见 [`PROJECT_INDEX.md`](./PROJECT_INDEX.md) §零。

---

## 六、v1 全部 P 任务规划（按 D-015 五阶段顺序）

**当前阶段：上线维护期**（2026-06-29 相位切换；权威同 §一 / `CLAUDE.md` / `CURRENT_PLAN.md`）。下方 D-015 五阶段（①功能开发 … ⑤打包）= **v1 功能开发探索期的历史规划框架**，2026-06-29 起退居备查，不再以「①功能开发」为活跃阶段。

<details>
<summary>📂 v1 历史五阶段规划（D-015，已退居备查 — 当前=上线维护期；点开看历史）</summary>

```
✅ v1 已完成
  P15 脚手架
  P16 朋友圈 D1/D2/D3
  P17 会话 LSPosed
  P19 通讯录 F07
  A2  密友列表数据层 + 原生 SelectContactUI 导入（预选 + diff 增删）
  A3  密群数据层 + Filter union（2026-05-21 装机已验）
  A3  密群原生 GroupCardSelectUI 导入（2026-06-02，预选 + diff 增删）
  P20 搜索 + 密码入口（B6 ✅ + 联系人 L1 ✅；群聊/聊天记录全场景 → 🟡 待补）
  P21 朋友圈小红点主线（Layer0b + P21B WithAll/bm live Cursor 过滤，2026-06-09 收口；rm 同路径后续复验）
  P23 F08 防撤回（C1）✅ L1 装机 2026-05-31（jy0.t.f doRevokeMsg + 原文保留 + 系统提示染红）

🟡 v1 进行中（阶段 ①）
  P20B 状态机触发事件 B 模块（B2/B5/B6 ✅；B1 🟡 logcat 待补；B4 因用户设备无返回键，按确认不阻塞；KPI 轻采样已记录，发版前重测；P26 fresh-warm 已有证据，不重复；证据见 P20B worklog / P_ConvWarm / P26 result）
  E2   ✅ 伪装订位（应用户要求提前于 v2/v3 落地，2026-06-07 装机；详见权威 §一.1）

⬜ 本轮跳过 / 转 v1.1
  P18  空白 LSPosed KPI 基线（本轮跳过；正式发布门控再补）
  P22  普通消息通知 + 铃声功能（主拦截/来电/未读已收口；完整提醒体验转 v1.1）

⏸️ v1 收尾新增 → 移出 v1，下一版再排（2026-06-10 用户拍板：§6a/C5/E3 推迟 v1.1/v2，v1 收口不含这三项）
  §6a  朋友圈"仅可见分组"图标隐藏：转 v1.1，当前 v1 不做；已验部分以 HOOKMAP §D / 权威 §6a 为准
  C5   语音转发：转 v2，不纳入当前 v1；8.0.71 需重新调研，不复用 8.0.66 锚点当结论
  E3   修改余额 UI 层：转 v2/v3；金融敏感，接入前必须走 /guard-auth-review 合规预审 + PROTECTION_MAP 发版门控

🆕 下一版隐私覆盖缺口（2026-06-10 用户报告）
  D-SNS-VIS  发朋友圈「谁可以看 → 部分可见/不给谁看」选标签/选好友 列表，密友未隐藏；新 hook 点待逆向 → 按 hidden wxid 过滤；性质=隐私一致性缺口（通讯录主列表已隐藏，此入口遗漏 → 密友在选人界面暴露）

🟡 v1 收尾（阶段 ② 替换/加密预热）
  P24  docs/classmap/v8071.yaml + tools/check_classmap.ps1
       人看的混淆类名字典 + 一键校验代码硬编码 ↔ 字典是否同步
  P25  字符串/类名 seed 化本地生成流程（自动出每客户独立 seed 包）
       ⚠️ 编号撞车：P25 已被归档任务「B2 触发器误触发修复」占用（07_archive_归档/P25_B2触发器误触修复）。
          本计划项以后请用名字「seed 化本地生成流程」称呼，不要再用 P25 号。详见 PROJECT_INDEX §零 重复编号表。

⬜ v2（阶段 ②.5 + ③ + ④）
  P26  UI 优化：调试页 → 注入到微信「设置」顶部的用户设置页
       ⚠️ 编号撞车：P26 已被归档任务「好友 V 态热切 fresh-item」占用（07_archive_归档/P26_好友热切fresh触发）；
          且 P26C 在 03_execute 是「隐藏指定通讯录标签」、与旧写法「搜索高亮」也撞。本 v2 计划项以后请用名字
          「v2 设置页 UI」称呼，不要再用 P26 号。详见 PROJECT_INDEX §零 重复编号表。
  P27  LicenseGate 离线授权（embedded_seed + AES-GCM + License Key）
  P28  ClassMap 加密化（classmap.enc，License Key 解密）
  P29  miyou-server 接入：cs_url kill_switch + danger_notice + heartbeat
  P30  通知伪装 C2（来自 weixin wxid）

⬜ v3（阶段 ⑤ 打包形态）
  P31  LSPatch 双模式本地打包流程
       ├─ 主版本：劫持模式（保 com.tencent.mm + SignatureGuard 三层绕过）
       └─ 副版本：共存模式（包名隔离 + 跟官方并存）
  P32  签名校验绕过（PMS / CRC / native 自校验）
  P33  反盗版引流壳 + 私域链接（cs_url + shop_url）
```

**阶段铁律**（D-015）：
- ① 没收口前禁止开 ②（先把功能做稳）
- ②③ 没收口前禁止开 ⑤（先把代码加固再做打包）
- 任何想"现在就加密 classmap"的冲动 → 阻塞，写进 `04_review_审稿复核/REJECTED_OPT.md`

</details>

**T 调研任务**：
- ✅ T08 F08 防撤回路径调研 — 已并入 P23（2026-05-31 `jy0.t.f` doRevokeMsg 实证结案）
- **T09 Catfish L0v3 互动红点探针**（今日执行，Catfish 授权 1 天，产出给 L0v3 实现）

**资料债**：
- ✅ CLAUDE.md 编码损坏已清除（2026-06-02）：全 `.md` 扫描 `?{3,}` / `�`(U+FFFD) 均 0 命中，现版正常，无需重写
