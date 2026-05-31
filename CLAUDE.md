# Guard Native 守护内核 — 主入口（AI 接手必读）
<!-- 最后更新：2026-05-27，8071 文档隔离；V↔H 热切 ✅（v27 装机收口，v24 + 80ms post-dedup + IK3n install）；FAILURE_LOG F-35+ -->
> 项目代号: **Guard Native (守护内核)**
> 目标: 微信 **8.0.71** 隐私模块 → LSPosed（让安卓应用被改造的框架）阶段 → 改包阶段 → 底层加固
> 当前底座: **微信 8.0.71**（D-014，2026-05-21 切版）
> 立项: 2026-05-19

## 零、AI 接手第一步 — 确认角色，读 skill

> 不读 skill 直接动代码 = 原地打转。四个 skill 覆盖全部场景，接手先判断你是哪个角色。

| 角色 | 触发时机 | skill 路径 |
|------|---------|-----------|
| **总调度** | 新会话第一件事 / 分配 P 任务 / 更新看板 | `.cursor/skills/guard-dispatch_总调度/SKILL.md` |
| **执行** | 领到 P 任务后 / 写代码 / 设备调试 | `.cursor/skills/guard-execute-one_单任务执行/SKILL.md` |
| **质检** | P 任务完成后装机前 / 发版前 | `.cursor/skills/guard-review_质检门控/SKILL.md`（或 `.claude/...`） |
| **终端操作** | build / adb / frida / logcat | `.cursor/skills/guard-terminal_终端操作/SKILL.md` |

**登记日志**：每个 P 任务开始时在 `03_execute_执行任务/P<N>_xxx/worklog.md` 写第一行时间戳。

---

## 一、接手三步铁律

1. 读完本文（10 分钟）
2. 读 [`HOOKMAP.md`](./HOOKMAP.md) 知道当前在哪个功能、哪个模块、什么状态
3. 读 [`TASK_BOARD.md`](./TASK_BOARD.md) 领取你这个窗口的任务
4. 不读完不准动代码

---

## 二、当前进度层次

```
文档层   ████████████ 100%   HOOK_POINTS / CLASS_MAP / FAILURE_LOG / 29 条铁律
代码层   ████████░░░░  65%   ← 我们在这里（v1 功能开发阶段）
验证层   ████████████ 100%   D1/D2/D3 + 会话 + 通讯录 + 密群 装机已验
试错层   ████████████ 100%   31 条已验证失败方案归档 (FAILURE_LOG.md)
```

**v1 已稳定**（有日志原文）：D1/D2/D3 朋友圈 · 会话 V→H · 通讯录 · A3 密群 · P21 Layer0b/Layer2 · P20 搜索联系人 v15.1 · B2/B6 触发
**v1 部分稳定**：ConvFilter **H→V 热切**（热路径 ✅，冷路径 🟡，见 `docs/CONV_REFRESH_PROBLEM.md`）
**v1 待装机/🟡**：B1/B4/B5 部分触发 · P20 搜索群聊/聊天记录 · H→V 热切冷路径（见 `docs/CONV_REFRESH_PROBLEM.md`）· P18 KPI 基线
**下一个**：P26 好友 fresh-fetch / P20 搜索分源 → 详见 [`TASK_BOARD.md`](./TASK_BOARD.md)

---

## 三、29 条铁律（违反即停）

完整清单 → [`FAILURE_LOG.md`](./FAILURE_LOG.md)（F-35 条，最新：v25/v26 跨 List identity / list-visited 双层强 dedup → BUS-V-adapter.p 零条 → RecyclerView 不重绘）

### 战略级
1. **目标版本 8.0.71 锁定**（D-014）— 当前代码主线，禁止以 8.0.66/8.0.70 架构直搬
2. **不引入底层注入三件套**（Pine/bypassmm/shadowhook）— 封号高暴露风险
3. **业务逻辑参考竞品 80%，特征面 100% 自有** — 包名/类名/MMKV/网络/签名独立
4. **修改任何 APK/SO/DEX/smali/MMKV 前先问用户**

### 反检测级
5. **禁止读 `ro.boot.*` 属性**（安卓启动参数）— Matrix（微信自家反作弊工具）反向 hook 监控
6. **LSPosed 启动进程白名单**
   - ✅ 允许：`com.tencent.mm` 主进程
   - ✅ 允许：`com.tencent.mm:push` 进程，仅限 badge/unread 写入链最小拦截
   - `:push` 进程**允许**：读取 hidden 状态 / 读取 hidden wxid set / 拦截密友 wxid 对应的 unread/badge 写入 / 少量限流日志
   - `:push` 进程**禁止**：UI 操作 / `ActivityManager` / `getRunningAppProcesses` / WebServer / Overlay / Toast / 通知栏 / 复杂反射 dump / 全局 List hook / 网络授权请求 / 业务页面过滤
   - ❌ 永久禁止：`:sandboxed_process` `:isolated_*` `:appbrand*`
7. **不调 ActivityManager.getRunningAppProcesses** — 沙箱进程无权限会 FATAL
8. **verifiedbootstate（微信反检测计数器）调用 KPI 红线 = 38**（8.0.68 水平）

### 实现级（FAILURE_LOG F-01 ~ F-35 摘要）
9. 禁止把 8.0.70 架构搬到 8.0.71（混淆名全变）
10. 禁止用 h8.L9/g8.f 调用链（是消息处理链不是会话链）
11. 禁止 WCDB rawQuery 兜底（微信自定义封装）
12. 禁止改 SparseArray Key（非连续）
13. 禁止 hook K0() + notify（Kotlin Flow 覆盖）
14. 禁止 hook Adapter.getCount/getItem/getView（Flow 覆盖/死循环）
15. 禁止 V4 改 RecyclerView position（数据错位）
16. 禁止 V.GONE 做主方案（ViewHolder 污染 + 点击穿透）
17. 禁止 Java 反射调用 notifyDataSetChanged（在安卓 ART 虚拟机里会 SIGSEGV 崩溃）
18. 禁止 notifyItemRange* 三种变体（用 DiffUtil 比对会让位置错位 / SIGABRT 崩）
19. **禁止 hook（钩子）异步回调里持有 `this`**（JNI 局部引用被垃圾回收后 SIGABRT 崩）
20. 禁止全局 hook ArrayList.add（频率过高）
21. **必须 notifyDataSetChanged 时先清后通知**（不是先通知后清）
22. **每个 P 任务关闭必跑 frida_stats.js**（KPI 不增量）
23. **禁止注入微信 JNI 链**（F-23 实证：CodecLooper SIGSEGV + 微信强制下线）
    - ❌ 仍然禁止：`dlopen` 微信自身 SO / 在微信 `JNI_OnLoad` 链中注入 / Hook 任何 native 方法 / `System.loadLibrary` 加载不属于模块自身的 SO
    - ✅ 例外——模块自有 SO（动态库 `libguardcore.so`）：状态机 / AES-GCM（加密算法） / HMAC（消息签名算法） / 授权校验 / wxid 匹配 / 进程角色判断 / hidden 状态持久化
24. **禁止模块用 startService / extends Service**（F-24：Service not found）— Notification 用 `NotificationManager.notify()`，悬浮窗用 `WindowManager.addView()`
25. **所有 XposedHelpers.findAndHookMethod 必须 `catch (Throwable)`**（F-25：NoSuchMethodError 穿透 catch Exception 导致 init 静默中断）
26. **hook protobuf 类方法禁用 `findMethodExact`**（F-26：parseFrom 定义在父类，findMethodExact 不遍历继承链）→ 用 `getMethods()` + `XposedBridge.hookMethod()`
27. **模块启动默认 HIDDEN（隐藏态），底层状态优先**（F-27）
    - 安装任何业务 hook 前，必须先完成 `nativeInit()` + `nativeReloadState()`
    - `:push` 进程内 hook 必须先判断 `nativeIsHidden()`；为 `false` 时直接短路返回
28. **禁止以 `MvvmList.m(List,boolean)` 类级别 hook 作为朋友圈过滤入口**（F-28：8.0.71 朋友圈数据不走 m()，正确路径是 addAll 实例拦截）
29. **禁止对已装机验证通过的 hook 点做任何未经用户同意的修改**（F-31：D1 h1() 被顺手优化后静默失效，无报错无崩溃只是不过滤。现状跑通 = 不动）

---

## 三.五、AI 工作通用禁忌（所有 skill 默认遵守，不再各自重复）

> 任何 skill 的「⛔ 绝对禁止」表只列**角色特有**条款；以下 6 条所有角色（dispatch/execute/review/terminal/auth）共用。

| # | 通用禁忌 |
|:--:|------|
| G1 | **禁止猜测**（一旦想写"应该/可能/估计/大概/推测" → 停） |
| G2 | **禁止推断**——没有 L1 动态日志（logcat / Frida）或 L2 静态反编译（jadx）作证，就不能下结论 |
| G3 | **没有证据/资料/日志 → 立刻停，主动问用户** |
| G4 | **不确定 → 立刻停，主动问，不写成结论**；必要时在文本里标 ❓ |
| G5 | **未经用户明确同意，禁止改任何 .md 文档** |
| G6 | **用户口述/截图/抓包/粘贴 ≠ 已验证**——即使是 L1 日志原文也要先找到磁盘文件再采信，不直接写入文档 |

**触发任意一条 → 中断当前动作，给用户一句话报告 + 一个问题，等回复。**

证据等级定义统一口径（所有 skill 通用）：

| 等级 | 标签 | 标准 |
|:--:|------|------|
| **L1** | 动态已证实 | Frida log / logcat 直接观察 |
| **L2** | 静态已证实 | jadx / 反编译确认 |
| **L3** | 高概率推断 | 多信号收敛，替代解释排除 |
| **L4** | 待验证 | 有线索但未动态确认 |

任何结论必须标注等级，否则视为 L4。

---

## 四、技术路线

```
短期 v1   LSPosed Java + libguardcore.so 基础  →  现在（4-6 周）
          Java 主力，C++ 工具库（状态机/加密/进程桥），禁止底层 hook
中期      Frida 临时验证                        →  验证新版本 hook 点（不当最终方案）
长期 v2+  libguardcore.so 全功能               →  LicenseBox / StateBridge / NotifyPolicy
          禁抄 Pine/bypassmm/shadowhook 三件套
          候补：LSPlant / bytehook / Dobby（与竞品不撞，v3+）
```

**铁律**：底层在 v2+ 只能"补单点性能"，不得作为业务逻辑层。授权校验 / 蜜罐才下沉到 SO（v2 后）。

---

## 五、功能模块速览

详细见 [`HOOKMAP.md`](./HOOKMAP.md) — 模块 + 状态图 + 详细资料。

| 模块 | 含义 | v1 范围 |
|------|------|:-------:|
| **A** | 核心隐私（密友列表/密群/密码/总开关） | ✅ |
| **B** | 隐藏触发（摇一摇/切后台/Home/锁屏/搜索框 111111） | ✅ |
| **C** | 消息控制（防撤回/通知伪装 weixin/未读控制） | 🟡 **代码已部分入 v1，整体未结案**：PushFilter L1/NM ✅ 装机 2026-05-22；L4b/L4c/CA 🟡 待装机；AntiRecall 🟡 代码已写未装机；C4/C5 v2+ |
| **D** | 痕迹隐藏（朋友圈密友帖/点赞/评论屏蔽） | ✅ D1/D2/D3 |
| **E** | 装b 模块（步数/定位/改零钱） | v2/v3 |
| **F** | 商业彩蛋（反盗版引流/独家功能/私域链接） | v2 |

### v1 锁定范围（**待收敛**：本节为初心口径，与 §三/§五 实装表已出现偏离）
```
11 个 hook（HOOK_MAP_V1.md 的 P0+P1）
 + 3 态状态机（显形/隐藏/解锁中）
 + 搜索框 111111 解锁（EditText 文本监听）
 + B 模块 6 个触发事件（摇一摇/切后台/Home/返回/锁屏/搜索）
 + 朋友圈 Proto 层（hookSnsObject 主线 + INIT 兜底）
 + 【代码已入 v1，未结案】PushFilter L1+NM ✅ 装机；L4b/L4c/CA 🟡 / AntiRecall 🟡 代码已写未装机
 + 【代码已入 v1，未结案】P21 朋友圈小红点 Layer0b/Layer2（🟡 L3，证据源 chatfish/frida，待 LSPosed 装机抓 logcat）
```
> **AI 注意**：本"v1 锁定范围"已与现状偏离（C 模块下沉、P21 朋友圈小红点接入等）。新会话以 [`HOOKMAP.md`](./HOOKMAP.md) §二 实体表为准；本节作为产品初心存档，待 v1 收口时统一重写。

v1 完整 hook 名单 → [`./docs/archive/wechat_8066/HOOK_MAP_V1.md`](./docs/archive/wechat_8066/HOOK_MAP_V1.md) P0 + P1（**历史规划**）；8071 事实 → [`./docs/HOOK_MAP_8071_AUTHORITATIVE.md`](./docs/HOOK_MAP_8071_AUTHORITATIVE.md)
P2 / 暂缓 / 禁止 / 系统层破绽点 → **全部不做**

---

## 六、技术架构无分歧共识

### 6.1 状态机
- **3 态**：显形 / 隐藏 / 解锁中（**无失败计数**）
- 进程内字段 `mVipMode`（仿竞品 UserControll）
- 持久化：MMKV `g_<seed>` 命名空间
- 默认：进程启动从 MMKV 恢复，授权下默认隐藏

### 6.2 朋友圈过滤（MvvmList 实例拦截，v12 方案）
- **主线**：`ArrayList.addAll(na4.b)` → `la4.p.field_userName` 直读（fallback 主路径）→ remove post
- `h1()` 路径 null miss（F-31）；**禁止改回 h1() 主路径**
- D2/D3：`LinkedList.add(z15.e56/cs5.di0)` → `entry.d`（或 `f435583d`）= wxid → block
- ⚠️ **已证伪**：`SnsObject.parseFrom(byte[])` Java hook — 8.0.71 反序列化走 JNI/C++，Java hook 零命中（F-27）

### 6.3 会话过滤（MvvmList 三层，已验证不动）
- L1: `MvvmList.n(List, boolean)` 主力（8.0.71）/ `.m`（8.0.66）
- L2: `MvvmList.s(List)` 备用
- L4: `kc5.v0.notifyDataSetChanged` clean-before 兜底

### 6.4 通讯录过滤（8.0.71 已验证）
- `ArrayList.addAll(fc5.g)` → `g.d`（z3 实例）→ `z3.c1()` → remove

### 6.5 搜索拦截（搜索入口 = 放大镜）
- `SearchFilter`：hook `ArrayList.addAll` + `z15.ef6` 按 wxid 过滤
- `SearchUnlock`：hook EditText 构造器，隐藏态 + 密码 `111111` → 切显形
- **假返回"未找到"** = 微信本地无数据时的默认行为（显示"添加好友"）
- `hookSearchContact` 待实现（联系人存在性层）

### 6.6 朋友圈小红点（P21，两个视觉层）
- **MomentsEntryBadge**（朋友圈行入口角标）：`FMF.g1("album_dyna_photo_ui_title", true)` 拦截
- **DiscoverTabBadge**（发现 tab 底部角标）：`TabRedDotChangeEvent`/`WeChatTabRedDotEvent` ctor 清零
- **Layer0b**（互动列表过滤）：`Activity.onResume` 过滤 `SnsMsgUI*`，密友条目不显示 + badge 归零

### 6.7 MMKV
- namespace: `g_<seed4>`（每客户独立 seed）
- key: 4 字符短哈希（**不出现** `hide_list` / `vip_enable` 等明文）

### 6.8 字符串混淆
- 类名/方法名/包名禁用敏感词（vip / hide / pirate / wechat / catfish / myauth / wmiyou）
- Toast / Log TAG / MMKV key 全部 seed 化
- v1 手动换 seed + 按时间定时发版（**不做自动构建**）

---

## 七、防检测 KPI（每个 P 任务关闭必跑）

| 指标 | 8.0.71 基线（P15 实测） | 安全上限 | 红线 | 来源 |
|------|:----:|:--:|:--:|------|
| verifiedbootstate | 4 | 20 | 38 | P15 实测 |
| PROP / 100K | 5（idle） | 150 | 220 | P15 实测 |
| normsg / 100K | — | 4,000 | 5,124 | QE66 P14 |
| CONN 密度 | — | 0.2 | 0.5 | QE66 P14 |

⚠️ **P18 KPI 基线（空白 LSPosed 框架）尚未建立** — frida_stats 对比零点缺失，F-22 实质空转。
工具：`frida_stats.js` v1.1（已有），路径 → [`TOOLS_INDEX.md`](./TOOLS_INDEX.md)

---

## 八、产品形态演进

```
v1  4-6 周   LSPosed 模块, 小米9/8.0.71, 纯 Java, 自用+种子客户
v2  2-3 月   消息控制完整 + 反盗版引流壳 + miyou-server 授权接入
v3  3-4 月   装b 三件套 + LSPosed → 改包（无 root，客户端 APK）
v4  2 月     底层 C++ 蜜罐 + 加盐字幕混合加密
```

---

## 九、开发期工具栈（v1 必做）

| 工具 | 形态 | 难度 |
|------|------|:--:|
| `adb forward + 浏览器` | 手机 USB → 浏览器 localhost:8080 看调试控制台 | ⭐⭐ |
| 状态机可视化 | 通知栏常驻 + 悬浮窗实时事件流 | ⭐⭐⭐ |
| 本地开发模式 | 切断数据流而不改 hook（微信看不到异常） | ⭐⭐⭐ |
| 点击添加 wxid | 反射查微信 ContactStorage 解析昵称/头像 | ⭐⭐⭐ |
| DEV/PROD/HONEY 三态 | 共用代码 + 配置切换（蜜罐复用基础设施） | ⭐⭐ |
| 一次性 Proto 采集 | Frida dump + 抓包 → 离线资料库 | ⭐⭐⭐⭐ |

---

## 十、角色工作流

| 角色 | 职责 | skill 路径 |
|------|------|-----------|
| **总调度** | 制定计划 / 分配 P 任务 / 接手新会话 / 文档维护 / 失败归档 | `guard-dispatch_总调度` |
| **执行** | 写代码 / 跑脚本 / 设备调试（**必须与用户交互**，禁止盲猜） | `guard-execute-one_单任务执行` |
| **审核** | P 任务自审（轻档）+ 发布门控（重档，含 KPI 红线） | `guard-review_质检门控` |
| **终端** | PowerShell / adb / frida / build 全套命令 | `guard-terminal_终端操作` |

**调试铁律（执行角色核心）**：需要设备操作时，必须给用户一个明确指令，等结果回来再推进。禁止假设输出、禁止"估计 XXX"后直接改代码。
**主目录**: `.cursor/skills/`（Cursor 日常用）
**镜像**: `.claude/skills/`（用 `sync_skills.ps1` 自动同步）

---

## 十一、当前 4 窗口速览

详细见 [`TASK_BOARD.md`](./TASK_BOARD.md)

| 窗口 | 主题 | 状态 |
|:--:|------|:---:|
| **W1** | B 模块触发器 + 搜索（P20B/P20） | ⬜ 代码已写，待装机 |
| **W2** | 朋友圈小红点（P21） | ✅ Layer0b/Layer2 实证；DiscoverTabBadge 待触发 |
| **W3** | 会话 LSPosed（P17） | ✅ 完成 |
| **W4** | 离线资料库采集（P18） | ⬜ 未领 |

**并发铁律**：每个窗口只动自己 P 任务目录，根目录看板（HOOKMAP/TASK_BOARD）改动前 git pull。

---

## 十二、关键资料路径（不复制，只记路径）

完整清单 → [`PROJECT_INDEX.md`](./PROJECT_INDEX.md)。高频访问：

```
本仓库内:
  ./docs/README.md                  文档入口（8071 主车道）
  ./docs/HOOK_MAP_8071_AUTHORITATIVE.md  8071 hook 权威
  ./docs/archive/INDEX.md           8066 历史（禁止直搬）
  ./docs/isolation/INDEX_COMPETITOR.md  Catfish 竞品参考
  ./refs/MainEntry.java             竞品入口 989 行
  ./refs/UserControll.java          竞品业务 944 行
  ./refs/filter_moments.js          Frida 朋友圈已验证脚本
  ./06_refs_参考资料/competitor_catfish/CATFISH_REVERSE.md   竞品逆向文档（8.0.70，禁止直搬类名）

外部资料（只读，不复制）:
  I:/apk2/_4__samples/dynamic_fast/HOOK_IMPLEMENTATION_ANALYSIS.md
  I:/apk2/_4__samples/sample_history_research/VERSION_INDEX.md
  I:/apk2/QE66_RESUME.md
  I:/miyou-server/CLAUDE.md         双链路授权服务器
  E:/apk_diff/                      7 个历史 APK（只读样本）
  E:/ios-dylib/shitou-miyou-core/   iOS 蜘蛛密友参考
```

---

## 十三、危险通告 / 一键停用（安全兜底）

**触发场景**：封号潮 / 检测密度暴增 / 新版微信未适配 → 必须能远程拔插
**机制**：
- 客户端启动拉 miyou-server `cs_url` 的危险通告接口
- 接口返回字段 `kill_switch: true|false`
- `true` → 跳过 hook 注册（表现为完全无功能但不崩溃）+ 浮窗显示"已停用，等待更新"
- 用户可手动覆盖（按住返回键 5 秒 → 临时启用，仅自查）

**v1 实现**：客户端启动埋占位实现（默认 false），不调真实接口
**v2 实现**：接入 miyou-server 真实接口 + HMAC 验签
**服务器**：公告系统已就绪 → `I:/miyou-server/CLAUDE.md`

详细决策 → [`DECISION_LOG.md`](./DECISION_LOG.md) D-013

---

## 十四、商业模式（v2 接入）

- **小众高端客户** + 隐私优先 + **不要求量大**（避免泛滥触发封号阈值）
- **每客户独立 seed/签名**（蜜罐溯源 + 反聚类）
- **养破解做私域**：破解校验失败 → 弹"独家功能" → 跳商城客服
- **授权按时间**（miyou-server `cs_url` + `shop_url` + 设备邀请码现成）
- **不批发，只零售**（学原作者退出批发期的策略）

---

## 十五、对外品牌

- **内部代号**：Guard Native（不对外）
- **客户端品牌**：待定（**禁用** "微信"二字 + vip/pirate/hide 等敏感词）
- **域名/服务器实体**：跟产品做切割（zxmqq.shop / miyou.lol 已就绪）
- **卡密销售**：Telegram / USDT 等可切割链路

---

**接手到这里就够了。开始干活前一定要再看一遍 [`HOOKMAP.md`](./HOOKMAP.md) + [`TASK_BOARD.md`](./TASK_BOARD.md)。**
