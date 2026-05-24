# TASK_BOARD — 任务看板（4 窗口 + P 任务进度）

> ⛔ **AI 读此文件的绝对铁律 / AI HARD RULES**
> - ✅ = 有装机日志原文的才算完成，无日志不得推进"下一步"
> - ⬜ = 代码已写但未装机 — 不等于完成，不能当基础继续堆功能
> - 状态不明 → **停下来问用户，禁止猜测，禁止推断**
>
> 接手前看：[`CLAUDE.md`](./CLAUDE.md) + [`HOOKMAP.md`](./HOOKMAP.md)
> 更新时间：2026-05-22
> **当前底座：微信 8.0.71**（D-014）
> 维护人：guard-dispatch_总调度

---

## 一、当前 4 窗口分工（同时开启）

| 窗口 | 主题 | P 任务 | 状态 | 占用至 | 模型建议 |
|:--:|------|-------|:--:|------|:----:|
| **W1** | B模块触发器 + 搜索 | **P20B/P20** | 🟡 **装机部分通过（2026-05-22）**：B2✅ B5✅ B6(6个1)✅ extractWxid(C0)✅ F-27冷启动HIDDEN✅；**Bug B (H→V自动刷新) ⬜ 待实现** | — | Sonnet |
| **W2** | 朋友圈小红点 | **P21_朋友圈小红点** | ✅ **v18 装机**：Layer0b（SnsMsgUI 进列表过滤+badge zero）✅实证；Layer2（g1拦截）✅实证；v18 tab badge counter suppressor 已装机待触发 | — | Sonnet |
| **W3** | 会话 LSPosed 翻译 | P17_会话LSPosed | ✅ 已完成（2026-05-20 会话隐藏验收通过）| — | Sonnet |
| **W4** | 离线资料库采集 | P18_离线采集 | ⬜ 待领 | — | Haiku |

**领取规则**：新会话第一件事是更新本表对应行的"占用至"列，写入会话 ID + 截止时间。

---

## 二、4 窗口任务详细（每个窗口的目标 + 必读 + 产出）

---

### 🟦 W1 主开发：脚手架（最优先）

**目标**：把"开发期可视化 + 本地开发模式 + adb forward 浏览器调试"做出来，让后面所有窗口都能跑代码看效果。

**为什么先做**：没有可视化 = 调代码靠盲猜 = AI 跑代码效率低 10 倍。

**必读**：
- [`CLAUDE.md`](./CLAUDE.md) §五 5.1 状态机 / §八 开发工具栈
- [`HOOKMAP.md`](./HOOKMAP.md) §A 核心隐私
- [`FAILURE_LOG.md`](./FAILURE_LOG.md) 全部 22 条
- `./refs/MainEntry.java` + `./refs/UserControll.java`（参考 Catfish 状态机+UI）

**任务清单（按顺序）**：
1. 创建 LSPosed 模块骨架（AndroidManifest + xposed_init + Application）
2. 状态机类（`StateMachine.java`）：3 态 + MMKV 持久化 + 触发事件总线
3. 拦截计数器（`InterceptCounter.java`）：F04/F05/F07 各计数 + 最近事件队列
4. 通知栏常驻 Notification（显示当前状态/拦截总数）
5. 悬浮窗 Overlay（实时事件流，可开关）
6. **Mini HTTP Server**（NanoHTTPD 或 com.sun.net.httpserver，端口 8080）
7. HTML 调试页面（轮询状态机/拦截数/触发事件按钮）
8. 本地开发模式开关（拦截但 `not invoke notify`，数据保留供调试）
9. 添加 wxid 解析昵称头像 UI（反射查 `com.tencent.mm.storage.ContactStorage`）
10. DEV/PROD/HONEY 三态配置切换

**产出文件**（写在 `./src/`）：
```
src/main/java/.../moduleA/StateMachine.java
src/main/java/.../moduleA/InterceptCounter.java
src/main/java/.../moduleA/MMKVStore.java
src/main/java/.../debug/DebugServer.java       (HTTP Server)
src/main/java/.../debug/OverlayWindow.java
src/main/java/.../debug/StatusNotification.java
src/main/assets/debug/index.html               (调试页面)
src/main/java/.../debug/ContactResolver.java   (查昵称头像)
```

**验收**：
- 装到小米9 → 通知栏看到 "[Guard] 状态=隐 / 拦截 0 / 密友 0"
- 电脑 `adb forward tcp:8080 tcp:8080`
- 浏览器 http://localhost:8080 看到调试页面
- 点"添加 wxid" 输入一个 → 显示昵称 + 头像

**预计**：5-7 天

---

### ✅ W2 朋友圈小红点 P21（v18 装机实证）

**状态**：✅ **核心功能实证通过**，v18 tab badge counter 已装机待触发

**已完成（✅ 不动）**：
- D1 密友帖整条隐藏 — `MomentsFilter.java` `addAll(na4.b)` ✅
- D2 密友点赞隐藏 — `LinkedList.add(z15.e56)` ✅
- D3 密友评论隐藏 — `LinkedList.add(cs5.di0)` ✅

**P21 v18 实证结果（2026-05-21）**：
- **Layer0b 实证**：`Activity.class.onResume` 过滤 `SnsMsgUI*` ✅，进互动列表时密友条目被过滤
- **Layer2 实证**：`FMF.g1("album_dyna_photo_ui_title", true)` 已拦截 ✅，防止朋友圈行新增红点
- **badge 本地状态实证**：badge = `w1.y`（本地 DB），进互动列表消费后自然归零 ✅
- **v18 新增**：`TabRedDotChangeEvent`/`WeChatTabRedDotEvent` ctor 清零 int 字段（tab 角标数字）；进互动列表后主动 `zeroW1FieldY`

**已证伪、永久禁止**：
- ❌ Layer1 `w1.v2` 跨进程不可达（`:push` 写入，主进程 hook 打不到）
- ❌ ns.c 系列字段（不控制视觉红点，`ns.c.b=false` 时红点仍亮）
- ❌ `ww2.c.b`（8.0.71 不存在）
- ❌ `w1.E1()` getter（零调用，badge 不走 Java getter）

**v18 待验证**：密友点赞 → 观察 `[MRD:tab] TabRedDotChangeEvent.xxx N→0` 日志

**必读**：
- `03_execute_执行任务/P21_MomentsRedDot/worklog.md`（所有已证实/证伪路径）
- `HOOKMAP.md` §二 朋友圈小红点行

**禁止**：
- ❌ 不用 Frida 方案（用 LSPosed，打包用）
- ❌ 不改 D1/D2/D3 已验收的代码

---

### ✅ W3 会话过滤（P17 已验收 2026-05-20）

**状态**：✅ 装机验证通过，不动。`ConvFilter.java` 已稳。

**为什么单独窗口**：会话 hook 已 100% 验证，是确定性最高的任务，便宜模型也能做。

**必读**：
- `./docs/HOOK_POINTS.md` §F04（4 层 hook 完整 Java 伪代码）
- `./refs/FAILURE_LOG.md` F-05/F-06/F-11（hook K0 / Adapter / 反射 invoke 三个死路）
- 没有 `./refs/filter_conv.js`，但 HOOK_POINTS §F04 已经把 Frida 翻译成 Java 伪代码

**任务清单**：
1. 写 `ConvFilter.java`：
   - L1 hook `MvvmList.m(List, boolean)` beforeHookedMethod → filterConvList(list)
   - L2 hook `MvvmList.s(List)` beforeHookedMethod → 同上
   - L4 hook `f45.s0.notifyDataSetChanged` beforeHookedMethod → cleanS0
   - wxid 提取：`f45.u.d → m3.j1()` 反射链
2. weixin 特殊处理：`weixin` 条目有未读时保留
3. INIT 清理（warm-attach）：找 ConversationListView → adapter → 清一次 → notify
4. `g_cleaning` 防重入 + 1000ms cooldown
5. 接 W1 的计数器（每拦截一条 ++）

**禁止**（FAILURE_LOG 摘要）：
- ❌ 不 hook `K0` / `getCount` / `getView`
- ❌ 不改 SparseArray Key
- ❌ 不用 `notifyItemRange*`
- ❌ 不 Java 反射 `Method.invoke`，直接调 `ad.notifyDataSetChanged()`

**产出**：
```
03_execute_执行任务/P17_会话LSPosed/
├── result.md
├── scripts/                    (验证脚本)
└── logs/
src/main/java/.../moduleD/ConvFilter.java
```

**验收**：
- 加 1 个 wxid 到密友列表
- 该 wxid 给你发消息
- 聊天列表刷新 → **看不到这个会话**
- 跑 `frida_stats.js` 对比基线 → 无增量

**预计**：3-5 天（简单直翻）

---

### 🟧 W4 离线资料库采集（基础数据）

**目标**：在 root 测试机上一次性跑完所有 hook 点采集 + 网络抓包 + **空白 LSPosed 基线**，**产出离线资料库**，后续 AI 都吃这份料。

**为什么先做**：抓一次能省后面所有窗口反复跑 Frida 的时间。**没有基线对比，KPI 门控（F-22 铁律）就是空话**。

**前置检查**（开工前必须 ✅）：
- ⬜ 8.0.66 APK 已落地 `06_refs_参考资料/apk_samples/wechat_8066.apk`（见 `TOOLS_INDEX.md` §〇 §A）
- ⬜ 设备已装 8.0.66（adb shell dumpsys package com.tencent.mm | grep versionName 输出 8.0.66）
- ⬜ frida-server 已 push 到设备 /data/local/tmp/ + 已运行

**必读**：
- [`CLAUDE.md`](./CLAUDE.md) §三 5.2 Proto 层 / §六 KPI 表 / §八 开发工具栈
- [`TOOLS_INDEX.md`](./TOOLS_INDEX.md) §〇 + §七 ADB 命令模板
- `I:/apk2/_3__D_wechat_ban/official_wechat_ban_research/03_anti_frida/frida_stats.js`
- `I:/apk2/_3__D_wechat_ban/official_wechat_ban_research/03_anti_frida/COLLECTION_SOP.md`

**任务清单**：

**🔵 第 0 步：空白 LSPosed 基线（最高优先级，必须先跑）**
- 装空白 LSPosed Manager（无任何模块启用）
- 跑 `frida_stats.js spawn` 30 分钟，做真实操作（刷朋友圈 + 拉聊天 + 搜索）
- 产出 `frida_stats_baseline_empty_lsposed.log`（16 指标基线）
- 这是后续所有 P 任务关闭对比的"零点"

**🟢 第 1 步：业务 Proto dump**
1. hook `SnsObject.parseFrom` + 入参/返回值/字段 dump 到 log
2. 类似 hook：`Conversation.parseFrom` / `ContactInfo.parseFrom` / `MsgInfo.parseFrom`（找等价类）
3. 朋友圈 op() 字段访问（参考 Catfish `m05.g46`）

**🟡 第 2 步：网络抓包**
- 同时跑 `tcpdump` 抓 30 分钟
- 产出 `network.pcap`

**收集产出物**：
- `frida_stats_baseline_empty_lsposed.log` ← **基线**
- `proto_dump.log`（Frida 日志，30MB 左右）
- `network.pcap`（tcpdump，50MB 左右）

**产出**：
```
03_execute_执行任务/P18_离线采集/
├── result.md
├── scripts/
│   ├── proto_dump.js           (Frida 脚本)
│   └── collect.bat             (一键跑)
└── logs/
    ├── proto_dump.log          (30MB 左右)
    ├── network.pcap            (50MB 左右)
    └── frida_stats_baseline.log
```

**这份资料库给谁用**：
- W2 朋友圈：分析 SnsObject 真实数据结构
- W3 会话：参考 ConversationItem 数据结构
- 未来 T07/T08 调研：通讯录/防撤回的真实数据样本

**验收**：
- 跑完后 `proto_dump.log` 有真实 SnsObject 解析记录
- `frida_stats_baseline.log` 16 指标全部输出
- 把日志上传或拷到 `06_refs_参考资料/采集快照_dump_snapshots/`

**预计**：2-3 天（第 0 步基线 1 天 + 第 1-2 步业务采集 1-2 天）

---

## 三、依赖关系

```
W4 离线采集 ─→ W2 朋友圈 (用 proto_dump 分析字段)
W4 离线采集 ─→ W3 会话 (验证 wxid 提取链路)
W1 脚手架   ─→ W2 / W3 (拦截计数器接入)
W1 脚手架   ─→ 后续所有功能模块
```

**最优启动顺序**：
1. **Day 1**：W4 开跑（采集）+ W1 开始（不依赖采集）
2. **Day 2**：W4 完成 → W2/W3 开跑（吃 W4 产出）
3. **Day 3+**：4 窗口并行

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
| P15 | 脚手架 | ✅ | 2026-05-19 全部门控通过（verifiedbootstate=4，PROP idle=5）。ContactResolver L4 stub 移交 W4。|
| P16 | 朋友圈 Proto（8.0.71 适配） | ✅ | D1–D3 + KPI 2026-05-20；L0v3/P19 另开 |
| P17 | 会话 LSPosed | ✅ | 2026-05-20 会话隐藏验收通过。链路：kc5.y.d(l4).h1() = wxid（HOOKMAP 已修 C0→h1），L4 notifyDataSetChanged 主门控。|
| P18 | 离线采集 | ⬜ | — |
| P19 | 通讯录隐藏 F07 | ✅ | 2026-05-20 装机验证。路径：ArrayList.addAll(fc5.g×30) → fc5.g.d→z3.c1()→wxid → remove。分段虚拟滚动，每段过滤。|
| P20 | 搜索 + 密码入口 | 🟡 | 2026-05-22 B6装机验证通过：6个1触发✅ `View.onAttachedToWindow` hook `ActionBarEditText`命中；`extractWxid C0`路径✅；F-27冷启动HIDDEN✅；**Bug B（H→V后密友不自动显示）⬜ 待实现** |
| A3  | 密群（数据层 + Filter union）| ✅ | 2026-05-21 装机验证。`Bridge.getGroupIds/allHiddenIds/isGroupId`；4 Filter 切换到 `allHiddenIds()`。|
| P_NC1 | native_core Batch 1 装机验证 | 🟡 | SO 编译通过 + 14 JNI 符号导出；ModuleMain 验证桩已接入（`runNativeBridgeVerification`）；**待装机跑 logcat，看 `[native] BATCH1_VERIFY PASS`**；通过后更新 ROADMAP Phase 1 ⬜→✅ |
| **P22** | **PushFilter 通知策略层** | 🟡 | **2026-05-22 装机实证**：L1 block ✅；NM cancel 普通消息 gap=3ms ✅；NM voip channel cancel ✅；tinker classloader fix ✅；密友总开关 `Bridge.isFeatureEnabled()` 已接 🟡；CA 备用层 🟡（代码完成，因 NM voip cancel 先行，未实际触发）；**剩余坑**：L4b `MainTabUI.i()` 方法名需 jadx 重查 ❓；NotifyPolicy VIBRATE/SOUND 未实现 📋；DebugServer HTML 开关未加 📋；WeChatDND 归档 Phase 2 📋；frida_stats 未跑 ⚠️ |

> 编号从 P15 起，是接续 apk2 项目 QE66 的 P14（保持跨项目可追溯）

---

## 六、v1 全部 P 任务规划（按 D-015 五阶段顺序）

**当前阶段：① 功能开发**（D-015 锁定，禁止跳阶段）

```
✅ v1 已完成
  P15 脚手架
  P16 朋友圈 D1/D2/D3
  P17 会话 LSPosed
  P19 通讯录 F07
  P20 搜索 + 密码入口（B6装机✅；Bug B H→V刷新⬜）
  A3  密群数据层 + Filter union（2026-05-21 装机已验）

🟡 v1 进行中（阶段 ①）
  P20B 状态机触发事件 B 模块（B1 摇一摇 / B2 切后台 / B5 锁屏）
  P22  PushFilter 通知策略层（2026-05-22 装机实证 L1/NM ✅；CA 🟡 未实际触发；剩余：L4b 重查 + NotifyPolicy VIBRATE/SOUND + DebugServer HTML 开关）
  P23  F08 防撤回（资料 T08 调研先行）

🟡 v1 收尾（阶段 ② 替换/加密预热）
  P24  docs/classmap/v8071.yaml + tools/check_classmap.ps1
       人看的混淆类名字典 + 一键校验代码硬编码 ↔ 字典是否同步
       目的：8.0.72 升级时只改 yaml 跑一条命令即可
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
- T08 F08 防撤回路径调研（P23 前置）
- **T09 Catfish L0v3 互动红点探针**（今日执行，Catfish 授权 1 天，产出给 L0v3 实现）

**资料债**：
- CLAUDE.md 编码损坏（大量 `?` 字符），需要专门一个 P 任务用原始备份恢复或重写
