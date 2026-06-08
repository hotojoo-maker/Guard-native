# CURRENT_PLAN — 当前阶段计划

> 维护人：guard-dispatch_总调度
> 更新：2026-06-08（对齐 TASK_BOARD 06-06 + E2 06-07 落地）

---

## 当前阶段：v1 阶段 ① 功能开发（D-015 锁定）

### 当前一句话状态（2026-06-08 02:10）

> **E2 伪装订位已收口；v1 收尾清单加 3 项（§6a 朋友圈分组图标 / C5 语音转发 / E3 修改余额 UI 层）。这 3 项 + P_NF1/P_NF2 收口后即可进 PROTECTION_MAP Phase 1（真锁 + 心跳）。**

### 四窗口现状（对齐 TASK_BOARD §一）

```
W1  B 模块触发 + 搜索 (P20/P20B)  ✅ P20 搜索 / 🟡 P20B（B4 不阻塞，KPI 发版前重测）
W2  朋友圈小红点  (P21)            ✅ Layer0b 装机；备用层不阻塞
W3  会话 LSPosed  (P17)            ✅ 已收口（05-20）
W4  离线资料库采集 (P18)           ⬜ 待领（发版前必须补空白 LSPosed KPI 基线）
```

### 临时插入 / 已收口

| 项 | 状态 | 完成日 | 备注 |
|---|:--:|---|---|
| E2 伪装订位 | ✅ | 2026-06-07 装机 + 06-08 文档/git 全收敛 | 应用户要求提前于 v2/v3；`moduleE/FakeLocation` |
| P_NF4 密友未读计数过滤 (UNREADFIX) | ✅ | 2026-06-06 装机 | |
| P_NF3 :push 独立震动 (route1) | 🟡 | 代码已写 | 纯 :push 场景待实证 |

---

## 主线缺口（按优先级）

### P1 — P22 PushFilter 收尾（v1 必须）
1. **P_NF1** 🟡 普通消息通知链路（后台震动已实证；缺前台/路由完整链）
2. **P_NF2** ⬜ 铃声功能（backlog，需决定 SOUND 档方案 — ToneGenerator / Ringtone / native / i3 Handler 候选已探）
3. **P_NF3** 🟡 :push 独立震动（route1 实证）
4. 详见 `docs/P22_PushFilter_VoIP.md`

### P2 — native_core Batch 1 装机
- **P_NC1** 🟡 编译/接入完成，**待 `[native] BATCH1_VERIFY PASS` 装机日志**

### P3 — v1 收尾新增（2026-06-08 应用户要求拉入 v1，合 P_NF1/P_NF2 一道收口才进 Phase 1 防破解）
- **§6a 朋友圈"仅可见分组"图标隐藏** ⬜
  - 竞品 8.0.66 锚点 `MainEntry.hookSnsGroup → UserControll.hookSnsGroup → isHideGroup`
  - 8.0.71 视图层 hook 点未查（boolean 返回类候选 / SnsObject item View 渲染层可见性候选）
  - 探针就绪：`moments_visibility_crawler.js`（2026-05-27 已就绪）
  - 存储：MMKV `hide_group` boolean
- **C5 语音转发** ⬜
  - 原 v2，纳入 v1；竞品锚点 `MainEntry.hookTransFlag → VipPreference.getTransVoiceMsg`
  - 难度 ⭐⭐⭐⭐：`np.protect` 加固层 8.0.66 jadx 失败 → 8.0.71 需重攻（动态 frida 探针 + smali patch 候选）
  - 存储：MMKV `trans_voice_msg` boolean
- **E3 修改余额 UI 层** ⬜
  - 用户决定走 UI 层方向（**不动金融后端**），按 b 方案：用户自设假数字 UI 显示
  - **金融敏感**：接入前必走 `/guard-auth-review` 合规预审，产出告知文案 + 截屏水印 + kill switch
  - 详见 `PROTECTION_MAP.md` §9b E3 行
  - 反 frida 风险：F-37 钱包页杀进程已实证，必须用 LSPosed + 静态 smali（禁 frida 进支付域）

### P4 — 待复现 bug
- **P_CF2** ⬜ 会话列表越界崩溃（与 P_PF2 来电拦截重构相关，详见 `docs/P22_PushFilter_VoIP.md`）

### P5 — UI 优化（不阻塞 v1）
- **P26C** 搜索高亮（归 UI 优化）

### P6 — 发版前 KPI 基线（W4 P18）
- ⬜ 跑 `frida_stats.js` 空白 LSPosed KPI 基线
- ⬜ 补跑 E2 启停 KPI 对比（F-22 铁律）

---

## 下一阶段（D-015 锁定五阶段顺序）

### 阶段 ② 替换/加密预热（v1 收尾）
- P24 docs/classmap/v8071.yaml + tools/check_classmap.ps1
- P25 字符串/类名 seed 化流水线

### 阶段 ②.5 + ③ + ④（v2）
- P26–P30：UI 优化 / LicenseGate / ClassMap 加密 / miyou-server / 通知伪装 C2

### 阶段 ⑤ 打包形态（v3）— **已提为主攻方向（D-016, 2026-06-08）**
- P31–P33：LSPatch 双模式 / 签名校验绕过 / 反盗版引流壳

> 阶段铁律：① 没收口前禁止开 ②；②③ 没收口前禁止开 ⑤。
> **D-016 例外**：不跳功能/加密阶段，但**并行启动 V3 可行性调研**（调研≠实现，不违背阶段铁律）。

---

## V3 免 root 改包路线（D-016 提优先级，调研先行）

> 铁律：V3 技术难点（微信自带签名校验 + Tinker 热补丁 + np.protect 加固）没查清**禁止报工期**。先调研，产出可行性报告再排 P31–P33。

### 调研任务（必须先做，G1–G4：无证据不报方案）
- **V3-T1 LSPatch 劫持 8.0.71 可行性** 🟡 用户装机口述实证（2026-06-08，待补 logcat/截图）
  - 工具：`02_tools_工具/lspatch.jar`（JingMatrix LSPatch v0.8）；产物 `02_tools_工具/lspatch_out/`（git 忽略，勿入库）。
  - 命令：`java -jar lspatch.jar 微信8.0.71.apk -m 模块.apk -l 2 -k 固定keystore android androiddebugkey android -o 输出 -v`。
  - 结果（VIVO V2361GA / Android 15 / 免 root）：LSPatch 打包零报错 ✅（`Embedding modules - com.ghost.assist`）；手动装机能起、过 np.protect、能登录 ✅；模块加载、加密友、隐藏/显示均正常 ✅（用户装机口述，logcat 因 VIVO USB 不稳未抓，待补）。
  - **关键限制（用户实证）**：改包签名 = 我们的（非腾讯），**微信支付/跳转支付用不了**（支付域签名校验）→ 需要支付的客户必须保留官方 → 见下方共存版需求。
  - `-l 2` 签名绕过（PM+openat）在 8.0.71 实测够用（np.protect 未拦启动）。
  - 待补：多天稳定性 + 反检测 KPI（verifiedbootstate 等）+ 防破解证书源切换（V3 改读宿主自身签名，见 D-016）+ logcat/截图存档。
- **V3-T2 微信签名自校验点定位** ⬜
  - 问题：8.0.71 的 PMS getPackageInfo / CRC 校验 / native 自校验在哪几处？改包重签后哪个会触发下线/闪退？
  - 产出：`SignatureGuard` 需绕过的目标清单（jadx L2 + frida L1）
- **V3-T3 Tinker 热补丁 × 改包冲突** ⬜
  - 问题：8.0.71 带 Tinker，改包后热补丁会不会覆盖注入代码 / 触发完整性校验 / 类加载器错位？
  - 产出：冲突点 + 规避策略

### V3 副版本：包名隔离共存版（用户 2026-06-08 拉入，刚需）
- **动机（用户实证）**：劫持改包**用不了微信支付**（签名变→支付域校验拒）。不愿卸官方、或需要支付的客户，必须官方+我们改包**并存**：官方负责支付，改包负责隐私。
- **做法**：改包名（com.tencent.mm → 独立包名）+ 重写 provider 授权/资源/manifest 引用（微信分身/多开技术）。LSPatch **不支持**改包名（已查 --help 证实），需另开工具链。
- **难点（D-015 标记的副版本难度）**：微信硬编码包名/provider authority/推送/登录校验多，改名易登不上/推送挂；需专项调研 V3-T4。
- **首选技术路线（比竞品强）**：**静态改包名 + LSPatch 注入模块** = 独立包名共存 + 无虚拟化容器。竞品（甜密友/蜘蛛密友"分身版"）多用容器/虚拟化（高检测面）；我们用静态改包名（真独立 APK、无沙箱特征）→ **反检测面更低** = 差异化卖点。
- **竞品印证（L3，来自官网/营销页，非逆向）**：蜘蛛密友官网明列三版（苹果 / 安卓卸官方版=劫持 / 安卓分身共存版）；甜密友称「包名隔离」（指向静态改包名）且能登录 → 证明 8.0.71 改包名共存可行。⚠️ 未逆向证实其引擎，不当 L1。
- **V3-T4 调研（待派）**：8.0.71 改包名共存可行性 + 工具链选型（apktool 手改 manifest+provider authority+资源+smali 引用 / MT 管理器应用共存 / 现成分身引擎）→ 重签 → LSPatch 注入 → 验登录/推送是否存活。
  - 工具卡点：PC 连不上 github，apktool 需用户提供（同 lspatch.jar）；或先用手机 MT「应用共存」试登出信号。
- **支付**：改包名/重签后支付依旧不可用；共存版定位 = 官方管支付 + 我们包管隐私（正好互补）。

### 调研通过后的实现任务（工期待 T1–T3 出报告再定）
- **P31** LSPatch 双模式打包流水线（主：劫持 com.tencent.mm + SignatureGuard 三层绕过；副：共存改包名）
- **P32** 签名校验绕过 `SignatureGuard.install()`（PMS / CRC / native 自校验，目标来自 V3-T2）
- **P33** 反盗版引流壳 + 私域链接（cs_url + shop_url）
- **防破解证书源切换**（V3 落地时，见 D-016）：`_CERT_SHA256` 换 V3 发行证书 + 运行时改读宿主自身签名。

### V3 进程白名单注入（D-015 已列，V3 必做）
- `WX_PKG="com.tencent.mm"` 改为打包时注入变量（劫持模式保留原包名；共存模式注入新包名）。

---

## 收尾债（不阻塞主线，但要补）

- ⬜ `signing/guard-native-debug.keystore` git 处置（A 提交 / B .gitignore 排除 / C 外部管理）— 待用户拍板
- ⬜ 一批"其他领域"modified 待按 P 任务顺序补提交（14 skill / DebugServer / TriggerGuard / NotifyRouter / MomentsRedDotGuard / build.gradle / 6 docs / 06_refs/CATFISH_REVERSE 等）

---

## 历史归档（按时间）

| 日期 | 事件 |
|---|---|
| 2026-05-19 | v1 范围口径锁定（D-011）；P15/P19 底座 |
| 2026-05-19~20 | P15/P16/P17/P19 全部✅ |
| 2026-05-20 | P20 SearchUnlock+SearchFilter 🟡 待装机 |
| 2026-05-21 | A3 密群 Filter union 装机✅；DECISION_LOG D-015 锁定五阶段 |
| 2026-05-29 | P_CV1/P_PF2/P_CF3/P_CF5 一波装机✅ |
| 2026-05-31 | P23 防撤回 C1 ✅ |
| 2026-06-01 | P19B 标签内成员密友过滤 ✅ |
| 2026-06-02 | PROTECTION_MAP Phase 0 完成（DebugServer DEV-gate + proguard 收窄 + AuthManager 接死代码） |
| 2026-06-06 | P_NF4 密友未读计数过滤 UNREADFIX ✅；P20 搜索全场景收口；dispatch §6-06 文档瘦身原则立 |
| 2026-06-07 | E2 伪装订位装机验证 ✅（应用户要求提前于 v2/v3） |
| 2026-06-08 | E2 git 全收敛（6 commit）+ CURRENT_PLAN 同步 |
