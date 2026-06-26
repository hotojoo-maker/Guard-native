# DECISION_LOG — 重大决策履历

> 维护人：guard-dispatch_总调度
> 规则：决策一旦写入永久保留；改变方向不删旧条目，新增一行覆盖

---

## 2026-05-19 立项日决策汇总

### D-001：目标版本 = 8.0.66
- **状态**：⛔ **已被 D-014 废止**（2026-05-21 起执行底座为 **8.0.71**）；本条仅作历史记录。
- **决策**：v1 基础版本锁定 8.0.66
- **依据**：apk2 项目 D 线 7 版本对比 + QE66 P0~P14 实证
  - PROP 密度仅 82.5/100K（vs 8.0.68 的 176/100K）
  - 无 weixin110.qq.com 域名跳转
  - 16 个反检测指标全部零归因增量
- **影响**：所有 hook 点资料只跟 8.0.66 强绑定，其他版本视为待验证

### D-002：第一版纯 Java，不引 native
- **决策**：v1 完全用 LSPosed Java，不引入 Pine/bypassmm/shadowhook
- **依据**：apk2 网络对比研究显示三件套 SO 是封号特征大头
- **影响**：性能瓶颈点不解决，但 v1 不发量，可接受

### D-003：业务逻辑抄 Catfish 80%，特征面 100% 自有
- **决策**：状态机/UI/触发事件抄 Catfish；包名/类名/MMKV/网络/签名 100% 重写
- **依据**：Catfish 业务逻辑成熟（944 行 + 989 行已验证），自研要 1-2 周；特征面是封号检测的核心
- **影响**：节省 1-2 周开发；客户端品牌切割

### D-004：朋友圈过滤主线改 Proto 层
- **状态**：⛔ **已被 F-27 动态证伪 + 实装推翻**（2026-05 起）——朋友圈反序列化走 JNI/C++，Java `parseFrom` 零命中；8.0.71 实装主线 = `ArrayList.addAll(na4.b)` → `la4.p.field_userName` 实例拦截（D1，HOOKMAP §6.2 / FAILURE_LOG F-27·F-28）。本条仅作历史记录。
- **决策**：朋友圈用 `SnsObject.parseFrom` Proto 层为主，MvvmList 三层为兜底
- **依据**：protobuf 字段 ID 跨版本永不变；y1/k24/i24 等混淆类版本变就死
- **影响**：v1 朋友圈实现重写；T05 调研先行

### D-005：会话过滤保持 MvvmList 三层方案
- **决策**：会话过滤不动 Proto 层，保留 L1/L2/L4 三层方案
- **依据**：已 Frida 100% 验证通过；改 Proto 层投入产出不划算
- **影响**：W3 直接翻译 Frida → Java

### D-006：4 窗口并行启动 v1
- **决策**：W1 脚手架 / W2 朋友圈 Proto / W3 会话 / W4 离线采集
- **依据**：用户工作流约定
- **影响**：单会话只动一个 P 任务，看板冲突自管

### D-007：5 角色 skill（双 IDE 兼容）
- **决策**：`.cursor/skills/`（主）+ `.claude/skills/`（镜像）+ `sync_skills.ps1` 同步
- **依据**：用户日常用 Cursor 多
- **影响**：日常编辑 .cursor，写完跑 sync 脚本同步

### D-008：文件夹中英文混合命名
- **决策**：`00_start_入口` 这种命名，前缀英文（AI 友好）+ 后缀中文（人类友好）
- **依据**：用户明确要求"文件名中英文，方便维护"

### D-009：状态机持久化
- **决策**：MMKV `g_<seed>` namespace + 4 字符短哈希 key，显隐状态持久化
- **依据**：苹果/安卓主流密友产品默认行为
- **影响**：MMKV key 不含敏感词

### D-010：开发工具栈 `adb forward + 浏览器`
- **决策**：调试控制台用 HTTP Server + 浏览器，不用 IDE 内嵌
- **依据**：手机插 USB → 浏览器看，所有 AI/人类共用，跨 IDE
- **影响**：W1 P15 任务必交付

### D-011：v1 范围口径锁定（铁律，AI 不得擅自扩范围）
- **修正（2026-06-10 收敛）**：① "朋友圈 Proto 层"已被 F-27 证伪（见 D-004），实装走 MvvmList `addAll` 实例拦截；② "C 模块整体延后 v2"口径作废——应用户要求，**C1 防撤回（2026-05-31）/ C3 未读 UNREADFIX（2026-06-06）/ 来电拦截 CA（2026-05-29）/ PushFilter L1+NM（2026-05-22）已落地 v1**，仅 C4 普通消息通知+铃声转 v1.1、C5 转发留 v2。v1 实时范围以 HOOKMAP / TASK_BOARD（06-09）为准。
- **决策**：v1 = **11 个 hook (HOOK_MAP_V1 P0+P1) + 3 态状态机 + 搜索 1111 解锁 + B 模块 6 触发事件 + 朋友圈 Proto 层**；P2/暂缓/系统层破绽点全部**不做**（对齐 Catfish 安全水平，老用户已验证安全）
- **依据**：用户安全优先方针 + 老用户客群 + 不扩张产品复杂度
- **影响**：HOOKMAP.md / TASK_BOARD.md / 所有 P 任务 result.md 范围以此为准；C 模块（消息控制）整体延后到 v2

### D-013：客户端启动拉危险通告（一键停用机制）
- **决策**：客户端启动时拉 miyou-server `cs_url` 的"危险通告"接口；通告含 `kill_switch` 字段，true 时**立即停用所有 hook**，并向用户弹窗"系统检测到风险，已自动停用，请等待更新"
- **依据**：用户"老用户多、安全优先、不能有破绽"方针；封号潮一旦发生需有兜底拔电
- **影响**：
  - v1 客户端启动时埋占位实现（默认 false）
  - v2 接入 miyou-server 真实接口
  - 服务端公告系统已就绪（`I:/miyou-server/CLAUDE.md` HMAC 公告）
- **触发条件**（写到 SERVER 维护手册，**v2 阶段落实**）：
  1. 当前版本检测密度暴增 > 2× 基线
  2. 用户社群反馈封号潮（> 3 例/日）
  3. 微信新版本上线，hook 点未适配
- **kill_switch 行为**：
  - 跳过 hook 注册 → 表现为完全无功能（但不崩溃）
  - 浮窗显示"已停用，等待更新"
  - 用户可手动覆盖（按住返回键 5 秒 → 临时启用，仅自查）

### D-012：native 二阶段使用约束
- **决策**：v1 完全无 native；v2+ 如引 native 仅限"性能瓶颈点"（朋友圈 parseFrom 单点）和"授权/校验/蜜罐"，**禁抄 Catfish Pine/bypassmm/shadowhook 三件套**，候选库 LSPlant / bytehook / Dobby
- **依据**：D 线网络对比研究 + FAILURE_LOG F-21
- **影响**：任何 SO 文件进入仓库前必须经风险复核（guard-risk-check_风险复核）

### D-015：交付形态锁定 + 5 阶段开发顺序（2026-05-21）

- **决策**：长期路线锁定 **LSPosed/LSPatch（不换框架）**；交付形态 **LSPatch 单 APK**；主版本 **劫持模式**（保留 `com.tencent.mm` + hook 签名/哈希/PMS 三层自校验），后期加 **共存模式** 副版本（改包名跟官方并存）
- **依据**：
  - LSPosed/LSPatch 是 mod 生态主流，反作弊"封了一片"成本极高 = 保护色（vs Catfish 小众 Pine 是独家指纹）
  - 跟 Catfish 切割已在业务实现层做足（包名/类名/MMKV key/Hook 引擎/Hook 点路径 5 维全异）
  - 用户家中老婆查岗场景需要"跟官方一样" = 劫持优先
  - 共存模式给"不方便卸原微信"客户，副版本量级小
- **影响**：
  - 业务代码（Filter/StateMachine/Bridge/InterceptCounter/SearchUnlock/SearchFilter）**零改动**
  - 进程白名单 `WX_PKG="com.tencent.mm"` 改为打包时注入变量（v1 收尾）
  - 新增 `SignatureGuard.install()`（劫持模式专用，绕 PMS/CRC/native 三层签名校验）
  - Bridge 命名空间 `g_a7f2` → `g_<embedded_seed>` 每客户独立
- **5 阶段开发顺序**（v1→v3 总图）：

  | 阶段 | 主题 | 关键产出 | 何时 |
  |:---:|---|---|---|
  | ① | 当前功能开发 | P21 B 模块、A3 密群 tab、F08 防撤回（v2 起）| 现在～v1 收尾 |
  | ② | 替换/加密（ClassMap）| `docs/classmap/v8071.yaml` + `check_classmap.ps1` → 后期 `classmap.enc` | v1.5 |
  | ②.5 | UI 设计优化 | 调试页 → 用户设置页（注入到微信「设置」顶部）| v2 起 |
  | ③ | 授权 + 加密 | LicenseGate 离线 + AES-GCM `classmap.enc` + embedded_seed 32 字节 | v2 |
  | ④ | 服务器匹配（miyou-server）| 接入现有半成品 cs_url（kill_switch + danger_notice）| v2 |
  | ⑤ | 包名隔离 + 劫持 + 伪装 | LSPatch 双模式本地打包流程（主劫持 + 副共存）| v3 |

  **铁律**：阶段顺序不可跳。先把功能做稳（①），再做反检测加密（②③），最后再做打包形态（⑤）。**禁止过早优化把 ②③⑤ 提前到 ①**——这是用户明确指示。

### D-014：目标版本从 8.0.66 升级到 8.0.71
- **决策**：开发底座从微信 8.0.66 切换到 **8.0.71**（APK 已落地 `C:\Users\Me\Desktop\guard_native\官方原版8.0.71-2026-5-19.apk`）
- **依据**：用户完成 8.0.71 兼容性验证（2026-05-19）：架构 100% 保留 RecyclerView + MvvmList + StateFlow；P16 关键链路全部留存；字段名 f135087o/f135088p 与 o/p 等价；Adapter y1 → e2；无 ListView/BaseAdapter 回归
- **影响**：
  - CLAUDE.md §一 版本锁定行改为 8.0.71
  - MomentsFilter.java ADAPTER_CLASS / ITEM_CLASS 需映射到 8.0.71 混淆名
  - ~~docs/CLASS_MAP_8066.md~~ → 已迁入 `docs/archive/wechat_8066/`；8071 对照以 `HOOK_MAP_8071_AUTHORITATIVE.md` 为准
  - 所有 Frida 脚本中 `y1` 改 `e2`；MvvmList 字段 `o`→`f135087o`，`p`→`f135088p`
- **待确认**（阻塞代码改动）：① `k24.b` 在 8.0.71 的等价类名；② 该 item 内 SnsObject 的字段名（8.0.66 是 `d`）

### D-016：交付形态优先级提升 = V3 免 root 改包（2026-06-08）
- **决策**：把 **V3（LSPatch 改包、免 root 单 APK）** 从「v3 末阶段」提为**主攻方向**。后续防破解、UI、打包都以「最终跑在我们重签名的改包 APK 里」为目标设计。
- **依据**：用户口径「现在谁还去 root」——LSPosed 需 root/特定环境，普通客户不会弄；免 root 改包才是可分发形态。D-015 早已锁 LSPatch 为交付形态，本条只是提优先级 + 明确后续设计都向 V3 对齐。
- **不撤回 D-015**：五阶段顺序与「功能先稳」原则仍在；本条不是允许跳过功能/加密直接打包，而是要求**并行启动 V3 可行性调研**（LSPatch 能否劫持 8.0.71 / np.protect 加固 / 签名自校验 / Tinker 热补丁冲突），调研清楚才报工期。
- **影响**：
  - 防破解证书绑定（A-step2，2026-06-08 装机✅）**形态边界**：当前读 v1 LSPosed 独立模块 APK 签名；V3 落地时必须改两处——① `tools/gen_registry_cipher.py` 的 `_CERT_SHA256` 换成 V3 发行签名证书；② 运行时证书源从 `sModulePath` 改为读「正在运行的宿主包自身签名」(`getPackageInfo(getPackageName(), GET_SIGNING_CERTIFICATES)`，查自己不被包可见性挡)。机制不变，只换证书源。
    - ⚠️ **2026-06-11 补**：此证书绑定除了「换证书源」，还有两个连带冲突常被漏掉——**删 fallback 后重签即 registry 散沙（隐私全挂）** + **共存版改包名被 `anti_tamper` 误判篡改引流**。详见 `PROTECTION_MAP.md` §10.6 末节「与 V3 改包路线的冲突」。
  - V3 调研任务（V3-T1/T2/T3）入 `01_dispatch_总调度/CURRENT_PLAN.md`，调研产出后再排 P31–P33 工期。
  - 进程白名单 `WX_PKG` 打包时注入、`SignatureGuard` 绕签名校验（D-015 已列）仍是 V3 必做项。

### D-017：防封授权闸 = 重构（按版本轴 官替/共存/管理），不受 D-015 新功能阶段序卡（2026-06-23）

- **决策**：防封授权闸 / A2 防封一线归类为**重构**（既有防破解·授权·反检测架构的重构），按**插件版本轴**组织 —— **官替版 / 共存版 / 管理（后台）**；因此**不按 D-015「新功能 ①→⑤ 阶段顺序」门控**。A2（签名/SSAID/包名 喂官方）是官替/共存版「能活下去」的内在刚需，非「提前的未来功能」。
- **后端统计**：客户端上报须带「包名 / 版本（官替/共存）标识」，确保**管理后台能按版本看到统计**（区分官替/共存靠包名/版本字段，**非签名**）。
- **签名策略**：官替/共存用**同一固定 keystore**（符固定签名铁律 + 好维护）；微信靠**包名**区分（A2 均喂官方 `18c867f0`），后台靠版本字段区分。小风险：A2 失效时两版同签名一并暴露（A2 正常则微信看不到真签名）。
- **不撤回 D-015 / D-016**：五阶段「功能先稳」原则对**新功能**仍有效；本条只裁定「防封授权闸属重构、走版本轴」，**不等于允许新功能跳阶段**。落代码仍须：授权检查官改前/改后审 + 安全官共审（`GuardRuntime`/`EnvelopeStore`/`LeaseClock`/`Bridge`）+ 守 watch（isAntiBanReady 计时自算、不连坐隐私离线门；A2 料只进 `registry_pack`，禁新增 pack）。
- **依据**：指挥拍板（2026-06-23，Vchat C81）；授权检查官独立审 = 架构 WARN（设计only干净，4 落地修正 + 2 watch）；研究线《防封权威账》§0/§八/§十（A2 签名轴 L1 已证）。
- **影响**：① 解 step③「A2 接主线」的时机 BLOCK；② `P_AntiBanGate` 进看板登记；③ 配方卡以 **JSON 存配方** + 包名/打包 **MD5 校验**（避免混乱，对齐 `tools/gate_three_axis.js` 自检门）；④ 分线推进：网络安全官（共审+真锁机制）、服务器运维（miyou-server / 管理版重构，安卓线）。

### D-018：A2 防封闸改吊「本地完整性 + 本地 DER」（取代 D-017 的「授权 + 服务器种子」门控口径）（2026-06-26）

- **决策**：A2 防封 `isAntiBanReady()` 改吊**本地完整性**（模块签名 cert / canary 未被改），**不再吊授权 / `isConfigReady` / server seed**；官方 DER 改**本地可解**（公开值，cert 钥匙锁或本地常量 + 完整性门控）。隐私 `isActive()` / `isConfigReady()` **仍 server-seed-gated，不动**——两闸独立、互不连坐。
- **依据**：首装未授权 = 重打包包最危险窗口（第一次撞官方 `c$p` 自检）；靠服务器种子门控 → 首装裸奔（D-017/早上工作把 A2 料锁进 server-seed registry，致首装/断网/未授权不防封 = 理解偏差源）。官方 DER 本是公开证书（可从官方包抠），锁进 server seed = 锁「功能开关」非锁「秘密」，且误把正版首装锁在外。能抓能骑 L1（`recon/A2_RIDE_TEST_20260625.md`）。
- **取舍（已认）**：防封对「完整但未付费」者免费——丢「白嫖→撤防封→封」这根棍；但防封单独低价值（隐私仍授权锁），防重打包/换壳靠 cert（重签→散沙），减法划算。
- **实现**：B（本地常量 + 完整查，减法版，先上）/ A（cert 钥匙锁，更牢，后续配 SO 下沉）。
- **落地细化（2026-06-26 E87）**：A2 安装门**只认 cert**（`CompatProbe.isIntegrityIntact`：读到证书且确证 ≠ `EXPECTED_CERT` 才散沙；读不到 / 相符 / 异常 = 装，逆序线 fail-open）。**canary 不进 A2 门**（吊编译期 `BASELINE`、漏重算会整片误封），canary 仍走 `CompatProbe.check`→`markTampered`→影子期引流（不变）。改包必重签 → cert 已覆盖重打包场景。官方 DER = `A2SignatureSpoof.OFFICIAL_DER_HEX` 本地常量。
- **状态**：🟢 码已落 + **装机 L1 PASS（E99 2026-06-26）**：Test1 首装未授权 ready=true/der=751B/level=正常、Test2 隐藏不连坐(removed wxid)/0 崩溃；Test3 重签散沙收《红队压测验证任务书_20260626》。lint 净·DER 校验过(751B/md5 `18c867f0`)·改前审查 PASS〔Vchat E87 · 安全官+授权检查官 WARN〕·S0 快照 `snap/A2-routeB-S0/20260626-1900`（在 `snap/A2gate/20260626-1848` 之上）·本轮 commit（A2 范围）。机制真源 = `DESIGN.md §5.1/§6`（DESIGN/skill 文档同步归主控）；规则 = 安全官 skill §防封反白嫖（旧 lock#1/#2 被本条取代）。状态页 = `STATUS_防封加密线.md`；落码细节 = `P_AntiBanGate/worklog.md` 2026-06-26c+d。
- **上线前门控**：红蓝对抗（重签包→散沙 / 首装未授权→防住 / 抽本地 DER 或掐完整查→拿不到隐私 / 正版不误伤）。
- **撤回**：不撤 D-017 全条（版本轴 / 同 keystore / 后台统计仍有效）；仅取代其中「A2 料只进 server-seed registry + `isAntiBanReady` 吊授权」的门控口径。

---

## 决策模板

```markdown
### D-NNN：<标题>
- **决策**：<一句话>
- **依据**：<来源/数据/L1 证据>
- **影响**：<下游受影响的人/任务>
- **撤回**：（如有）<日期 + 新决策号>
```
