# TASK_BOARD — 任务看板（4 窗口 + P 任务进度）

> ⛔ **AI 读此文件的绝对铁律 / AI HARD RULES**
> - ✅ = 有装机日志原文的才算完成，无日志不得推进"下一步"
> - ⬜ = 代码已写但未装机 — 不等于完成，不能当基础继续堆功能
> - 状态不明 → **停下来问用户，禁止猜测，禁止推断**
>
> 接手前看：[`docs/README.md`](./docs/README.md) + [`CLAUDE.md`](./CLAUDE.md) + [`HOOKMAP.md`](./HOOKMAP.md)
> 防破解/防盗版总账（含上线前门控）：[`PROTECTION_MAP.md`](./PROTECTION_MAP.md)
> 更新时间：2026-06-09（v1 收口口径：P21 主线收尾；P18 基线本轮跳过；P22 普通消息通知/铃声转 v1.1）
> **当前底座：微信 8.0.71**（D-014）
> 维护人：guard-dispatch_总调度

---

## 一、当前 4 窗口分工（同时开启）

| 窗口 | 主题 | P 任务 | 状态 | 占用至 | 模型建议 |
|:--:|------|-------|:--:|------|:----:|
| **W1** | B模块触发器 + 搜索 | **P20B/P20** | P20 搜索 ✅；P20B 🟡（功能侧不阻塞；KPI 本轮不作为 v1 阻塞） | — | Sonnet |
| **W2** | 朋友圈小红点 | **P21_朋友圈小红点** | ✅ 主线收尾：Layer0b + P21B WithAll/bm 已验；rm 同路径覆盖，后续有场景再复验 | — | Sonnet |
| **W3** | 会话 LSPosed 翻译 | P17_会话LSPosed | ✅ 已完成（2026-05-20 会话隐藏验收通过）| — | Sonnet |
| **W4** | 离线资料库采集 | P18_离线采集 | ⬜ 本轮跳过，不阻塞当前 v1 | — | Haiku |

**领取规则**：新会话第一件事是更新本表对应行的"占用至"列，写入会话 ID + 截止时间。

---

## 二、4 窗口任务详细（每个窗口的目标 + 必读 + 产出）

---

### 🟦 W1 B 模块触发器 + 搜索（P20B/P20）

**状态**：P20 搜索 ✅ 已收口；P20B 触发器 🟡（B1/B2/B5/B6 ✅；B4 因用户设备无返回键，按确认不阻塞；KPI 轻采样已记录，发版前重测）。

**下一步**：
1. P22 普通消息通知 + 铃声功能转 v1.1，不阻塞当前 v1。
2. P26C 搜索高亮归 UI 优化，不阻塞 P20。

**详情**：`03_execute_执行任务/P20_搜索拦截/result.md`、`03_execute_执行任务/P20B_BTriggers_SearchUnlock/worklog.md`、`docs/HOOK_MAP_8071_AUTHORITATIVE.md` §8b。

---

### ✅ W2 朋友圈小红点 P21

**状态**：✅ 主线收尾。Layer0b 已装机实证；P21B WithAll/bm live Cursor 过滤已装机；rm 代码同路径覆盖，后续有“与我的互动”入口时可补 L1，但不阻塞当前 v1。

**下一步**：仅做证据归档/备用复验；不改 D1/D2/D3 已验 hook。

**详情**：`03_execute_执行任务/P21_MomentsRedDot/worklog.md`、`HOOKMAP.md` §二。

---

### ✅ W3 会话过滤（P17）

**状态**：✅ 2026-05-20 装机验收通过，不动已验 hook。

**详情**：`03_execute_执行任务/P17_会话LSPosed/result.md`、`docs/HOOK_MAP_8071_AUTHORITATIVE.md` §8a。

---

### 🟧 W4 离线资料库采集（P18）

**状态**：⬜ 本轮跳过。空白 LSPosed KPI 基线不作为当前 v1 阻塞项。

**下一步**：后续进入正式发布门控或质检重档时再补跑 `frida_stats.js`；业务 Proto/网络采集按 P18 brief 另开。

**详情**：`03_execute_执行任务/P18_离线采集/`、`TOOLS_INDEX.md`。

---

## 三、依赖关系

```
W1 B触发+搜索 ─→ P22 普通消息通知/铃声转 v1.1；P26C 搜索高亮归 UI 优化
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
| 2 | 只动自己 P 任务目录（`03_execute_执行任务/P15/` 等）|
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
| P19B | 通讯录【标签】成员隐藏 F07B | ✅ | 2026-06-01 复跑实证；详见 `03_execute_执行任务/P19B_ContactLabel/result.md`。|
| P20 | 搜索 + 密码入口 | ✅ | 搜索全场景 + B6 收口；证据 `tools/p20_search_logcat_runner_20260606_180946.log`。|
| A2  | 密友列表（数据层 + 导入 UI） | ✅ | 原生 `SelectContactUI` 导入；详见权威 §3。|
| A3  | 密群（数据层 + Filter union + 导入 UI）| ✅ | 过滤链 + 原生 `GroupCardSelectUI` 导入；详见权威 §4。|
| **P23** | **F08 防撤回（C1）** | ✅ | 2026-05-31 L1 装机；详见 P23 result / 权威 §2。|
| P_NC1 | native_core Batch 1 装机验证 | 🟡 | 编译/接入完成，待 `[native] BATCH1_VERIFY PASS` 装机日志。|
| **P22** | **PushFilter 通知策略层** | 🟡 | 主拦截/来电/未读已收口；普通消息通知 + 铃声功能转 v1.1，详见 `docs/P22_PushFilter_VoIP.md`。|
| **P_CV1** | **通讯录 V↔H 热切** | ✅ | 2026-05-29 装机收口；证据 `bug排查/final_pcv1_v8_双通成功.log`。|
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

> 编号从 P15 起，是接续 apk2 项目 QE66 的 P14（保持跨项目可追溯）。`P_CV*` 系列与 `P_NC*` 同属语义号，不占 v2 路线图 P26–P30 / v3 P31–P33 编号位。

---

## 六、v1 全部 P 任务规划（按 D-015 五阶段顺序）

**当前阶段：① 功能开发**（D-015 锁定，禁止跳阶段）

```
✅ v1 已完成
  P15 脚手架
  P16 朋友圈 D1/D2/D3
  P17 会话 LSPosed
  P19 通讯录 F07
  A2  密友列表数据层 + 原生 SelectContactUI 导入（预选 + diff 增删）
  A3  密群数据层 + Filter union（2026-05-21 装机已验）
  A3  密群原生 GroupCardSelectUI 导入（2026-06-02，预选 + diff 增删）
  P20 搜索 + 密码入口（B6 / 联系人 / 群聊密群 / 聊天记录关键词场景全部收口）
  P21 朋友圈小红点主线（Layer0b + P21B WithAll/bm live Cursor 过滤，2026-06-09 收口；rm 同路径后续复验）
  P23 F08 防撤回（C1）✅ L1 装机 2026-05-31（jy0.t.f doRevokeMsg + 原文保留 + 系统提示染红）

🟡 v1 进行中（阶段 ①）
  P20B 状态机触发事件 B 模块（B1/B2/B5/B6 ✅；B4 因用户设备无返回键，按确认不阻塞；KPI 轻采样已记录，发版前重测；P26 fresh-warm 已有证据，不重复；证据见 P20B worklog / P_ConvWarm / P26 result）
  E2   ✅ 伪装订位（应用户要求提前于 v2/v3 落地，2026-06-07 装机；详见权威 §一.1）

⬜ 本轮跳过 / 转 v1.1
  P18  空白 LSPosed KPI 基线（本轮跳过；正式发布门控再补）
  P22  普通消息通知 + 铃声功能（主拦截/来电/未读已收口；完整提醒体验转 v1.1）

⬜ v1 收尾新增（应用户要求拉入 v1，2026-06-08 派出）
  §6a  朋友圈"仅可见分组"图标隐藏（竞品 8.0.66 锚点已知；8.0.71 视图层 hook 点待重查；moments_visibility_crawler.js 已就绪）
  C5   语音转发（原 v2，现纳入 v1；竞品锚点 MainEntry.hookTransFlag → VipPreference.getTransVoiceMsg；np.protect 加固层 8.0.66 jadx 失败，8.0.71 需重攻；难度 ⭐⭐⭐⭐）
  E3   修改余额 UI 层（金融敏感；用户决定走 UI 层方向，b=用户自设假数字 UI 显示；接入前必走 /guard-auth-review 合规预审；发版门控见 PROTECTION_MAP §9b）

🟡 v1 收尾（阶段 ② 替换/加密预热）
  P24  docs/classmap/v8071.yaml + tools/check_classmap.ps1
       人看的混淆类名字典 + 一键校验代码硬编码 ↔ 字典是否同步
  P25  字符串/类名 seed 化流水线（自动出每客户独立 seed 包）

⬜ v2（阶段 ②.5 + ③ + ④）
  P26  UI 优化：调试页 → 注入到微信「设置」顶部的用户设置页
  P27  LicenseGate 离线授权（embedded_seed + AES-GCM + License Key）
  P28  ClassMap 加密化（classmap.enc，License Key 解密）
  P29  miyou-server 接入：cs_url kill_switch + danger_notice + heartbeat
  P30  通知伪装 C2（来自 weixin wxid）

⬜ v3（阶段 ⑤ 打包形态）
  P31  LSPatch 双模式打包流水线
       ├─ 主版本：劫持模式（保 com.tencent.mm + SignatureGuard 三层绕过）
       └─ 副版本：共存模式（包名隔离 + 跟官方并存）
  P32  签名校验绕过（PMS / CRC / native 自校验）
  P33  反盗版引流壳 + 私域链接（cs_url + shop_url）
```

**阶段铁律**（D-015）：
- ① 没收口前禁止开 ②（先把功能做稳）
- ②③ 没收口前禁止开 ⑤（先把代码加固再做打包）
- 任何想"现在就加密 classmap"的冲动 → 阻塞，写进 `04_review_审稿复核/REJECTED_OPT.md`

**T 调研任务**：
- ✅ T08 F08 防撤回路径调研 — 已并入 P23（2026-05-31 `jy0.t.f` doRevokeMsg 实证结案）
- **T09 Catfish L0v3 互动红点探针**（今日执行，Catfish 授权 1 天，产出给 L0v3 实现）

**资料债**：
- ~~CLAUDE.md 编码损坏（大量 `?` 字符），需要专门一个 P 任务用原始备份恢复或重写~~ → ✅ 已清除（2026-06-02）：全 `.md` 扫描 `?{3,}` / `�`(U+FFFD) 均 0 命中，现版 CLAUDE.md 正常，无需重写
