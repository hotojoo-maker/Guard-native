# Guard Native 守护内核 — 主入口（AI 接手必读）
<!-- 最后更新：2026-07-01（产品形态铁定 + 文档权威链 + 中间程序打法 + 定死词表；命名：官方包/重新打包，封号→封/账号异常，防破解/逆向直写）；8071 文档隔离；FAILURE_LOG 至 F-43 -->
> 项目代号: **Guard Native (守护内核)**
> 目标: 官方包 **8.0.71** 隐私模块 → v1 收口 → **打包型 APK（LSPatch 为主）** → 底层加固
> 当前底座: **官方包 8.0.71**（D-014，2026-05-21 切版）
> 立项: 2026-05-19

### 产品形态铁定（优先于一切默认口径）

- **打包型 APK**：模块经 LSPatch **打包进** rebuild 后的官方包 APK（出货主形态）；LSPosed 模块 = 开发/验证阶段历史形态，非当前出货默认。
- **核心检测轴 = 是否官方（签名/身份）**，这才是官方真正区分的硬轴；**root/解锁本身正常、非异常、非封因**（L1：开发机 root 调试一月零封、官方包读 unlocked 也不 kill），**不当破解者嫌疑**（产品主要面向普通用户，多非 root；root 至多服务器侧弱信号、付费即正版）。
- **核心打法 = 中间程序「掐官方检测的咽喉、骑它脖子上」**：不自己造检测（守 KPI），而是卡在官方身份检查的咽喉 `getPackageInfo`/c$p 上——**灌官方值**（签名/android_id/包名→官方）= 我们号安全不被判异常；**顺手借官方的眼睛**读「签名变没变」= 抓改包破解。env（`ro.boot.*`）仍官方自己读、我方零读取（详 `03_execute_执行任务/P_AntiBanGate_防封授权闸/DESIGN.md` §0）。
- **环境判定**：靠 `.mm` 官方包自身采集；我方不主动新增环境检测面（KPI 硬约束，见防封研究线 §0）。
- **命名规约（定死词表 · 2026-06-24，以后照此写、别再解释）**：
  - **不写品牌名** → 官方包 / 共存包 / 官替 / 官方客户端 / **原版**。
  - **不写「注入」**（指我方产品形态）→ 写 **重新打包**（用 LSPatch 把模块〔含 C++ 核心 `libguardcore.so`〕重新打包进**原版/官方包**）。例外**照写**：禁止的「底层注入三件套 / 注入 JNI 链 / hook 注入检测」是技术正确词；构建期「flavor 注入 / 填配置」可写「写入/填入」。
  - **「宿主」分两种**：**概念叙述里不写宿主** → 写 原版/官方包；但**打包线技术术语「宿主 APK / 宿主包名」保留**（= 被重新打包进去的那个包，**可官替原版、也可共存克隆**；「原版」覆盖不了共存，故此处保留）。
  - **不写「封号」全词** → 写 **封** 或 **账号异常**。
  - **「防破解 / 逆向 / 反编译」直接写**——我们是正派一方、护自家产品（防守口径，不回避）。
  - 往官方检测回答官方真值 = **灌官方值 / 喂官方值**（不写 spoof / 伪造 / 欺骗）。
  - 核心打法固定叫法：**中间程序「掐咽喉 / 骑脖子」** + **借官方的眼睛**（掐 `getPackageInfo`/c$p 咽喉：灌官方值=保号、借官方的眼睛读「签名变没变」=抓破解）。
  - 技术锚点照写不藏：`签名` / `android_id` / `getPackageInfo` / `c$p` / `LSPatch` / `libguardcore.so`。
- **防封命脉真源**：`C:\Users\Me\Desktop\防封_反检测线\防封权威账_2026年6月.md`；机制设计 = `P_AntiBanGate/DESIGN.md`；时间参数 = `配方卡_SPEC_v1.md`。

## 零、AI 接手第一步 — 确认角色，读 skill

> 不读 skill 直接动代码 = 原地打转。**10 个 skill（4 核心 + 6 专项）** 覆盖全部场景，接手先判断你是哪个角色。

**核心 4 角色（日常主链路）：**

| 角色 | 触发时机 | skill 路径 |
|------|---------|-----------|
| **总调度** | 新会话第一件事 / 分配 P 任务 / 更新看板 | `.cursor/skills/guard-dispatch_总调度/SKILL.md` |
| **执行** | 领到 P 任务后 / 写代码 / 设备调试 | `.cursor/skills/guard-execute-one_单任务执行/SKILL.md` |
| **质检** | P 任务完成后装机前 / 发版前 | `.cursor/skills/guard-review_质检门控/SKILL.md`（或 `.claude/...`） |
| **终端操作** | build / adb / frida / logcat | `.cursor/skills/guard-terminal_终端操作/SKILL.md` |

**专项 6 角色（按场景触发，非每会话必用）：**

| 角色 | 触发时机 | skill 路径 |
|------|---------|-----------|
| **授权检查官** | 动 状态机 / 授权 / 模块边界 / 过滤位置 / 拆代码 / 新增 Filter 链 前必审 | `.cursor/skills/guard-auth-review_授权检查官/SKILL.md` |
| **防封官** | 防封 / 反检测 / 检测面 / KPI / Matrix / normsg / `ro.boot` / LSPosed 进程边界 前必审 | `.cursor/skills/guard-antiban_防封官/SKILL.md` |
| **网络安全官** | 加密 / SO / DRM / 授权防护 / 服务器授权信封 / 蜜罐 / 改 vip 前 | `.cursor/skills/guard-security_网络安全官/SKILL.md` |
| **发版官** | 发布 / 出包 / 官替版 / 共存版 / 换 s_rel / LSPatch 打包 / 装机验证（双版本出包流水线） | `.cursor/skills/guard-release_发版/SKILL.md` |
| **服务器运维** | miyou-server / 卡密 / release_lines / envelope / S_rel·W / 版本状态后台 | `.cursor/skills/guard-server_服务器运维/SKILL.md` |
| **git 保姆** | 备份 / 快照 / 提交 / 回退 / 清 git 垃圾（替不懂 git 的用户跑命令） | `.cursor/skills/guard-git_保姆/SKILL.md` |

**登记日志**：每个 P 任务开始时在 `03_execute_执行任务/P<N>_xxx/worklog.md` 写第一行时间戳。

---

## 一、接手顺序

1. 读完本文 CLAUDE.md（10 分钟）
2. 碰 授权 / 发版 / 防封 → 先读 [`_CORE_现状真源/`](./_CORE_现状真源/) 对应页（现状浓缩 + 深链）
   - 授权 → [`_CORE_现状真源/授权_当前真源.md`](./_CORE_现状真源/授权_当前真源.md)
   - 发版 → [`_CORE_现状真源/发版_当前真源.md`](./_CORE_现状真源/发版_当前真源.md)
   - 防封 → [`_CORE_现状真源/防封_当前真源.md`](./_CORE_现状真源/防封_当前真源.md)
3. 读 [`docs/README.md`](./docs/README.md)（8071 文档车道）
4. 读 [`HOOKMAP.md`](./HOOKMAP.md) + [`TASK_BOARD.md`](./TASK_BOARD.md)
5. 查路径 / 导航 → [`PROJECT_INDEX.md`](./PROJECT_INDEX.md)
6. 不读完不准动代码

---

## 二、当前进度层次

```
文档层   ████████████ 100%   HOOK_POINTS / CLASS_MAP / FAILURE_LOG / 29 条铁律
代码层   ███████████░  上线维护期·hook 点基本完成   ← 转「维护 + 加功能 + 版本适配」（详 CURRENT_PLAN）
验证层   ████████████ 100%   D1/D2/D3 + 会话 + 通讯录 + 密群 装机已验
试错层   ████████████ 100%   F-01~F-43 已验证失败方案归档 (FAILURE_LOG.md)
```

**v1 hook 已稳定**（有日志原文）：D1/D2/D3 朋友圈 · 会话 V→H · 会话 H→V fresh-warm（普通有历史 hidden id）· 通讯录 · A2 密友导入 · A3 密群/密群导入 · P21 Layer0b + P21B WithAll/bm · P20 搜索（联系人/群聊密群/聊天记录关键词场景）· B1/B2/B5/B6 触发 · **C1 防撤回**（2026-05-31）· **CA 语音/视频来电拦截**（2026-05-29）· **C3 未读计数 UNREADFIX**（2026-06-06）· **E2 伪装订位**（2026-06-07）
**已知限制**：零历史 wxid 的会话行创建仍受微信 DB 限制（见 `docs/CONV_REFRESH_PROBLEM.md`）
**上线加固已落地**：防破解 / A2 / 共存 / 官替 / 蜜罐 / 绊线 框架已建，cert-sync 已装机验；A2 闸等部分 🟡 待装机（详 `TASK_BOARD.md` §五）
**当前阶段：上线维护期**（2026-06-29 相位切换）——主线转「① 上线维护 ② 按需加功能 ③ 版本适配」，详 [`01_dispatch_总调度/CURRENT_PLAN.md`](./01_dispatch_总调度/CURRENT_PLAN.md)。按需加功能（C4 通知+铃声 / C5 转发 / E3 改余额 / §6a 等）一功能一卡，见「未做功能索引」。

---

## 三、29 条铁律（违反即停）

完整清单 → [`FAILURE_LOG.md`](./FAILURE_LOG.md)（至 F-43，最新：F-43 克隆宿主签名 bleed-through→cert mismatch（修=先重签 e3e13a49，D-030）· F-42 LSPatch A15 官替闪退（调查中）· F-41 后台标记正常不重置 tier/risk · F-40 重装顶爆假种子 recipeOk=false）

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
8. **verifiedbootstate 等 KPI = 可选抽检项**（非日常红线、非发版硬门，详 §七）；守铁律5 零环境读取故不增量，硬轴 = 签名身份

### 实现级（FAILURE_LOG F-01 ~ F-43 摘要）
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
22. **frida_stats.js KPI = 可选抽检、非发版硬门**（我方零环境读取故 vbs/PROP 不增量；想抽跑就跑、不跑不阻塞发版，详 §七）
23. **禁止注入微信 JNI 链**（F-23 实证：CodecLooper SIGSEGV + 微信强制下线）
    - ❌ 仍然禁止：`dlopen` 微信自身 SO / 在微信 `JNI_OnLoad` 链中注入 / 重碰 native（改返回/替换/广钩） / `System.loadLibrary` 加载不属于模块自身的 SO
    - ✅ 例外——模块自有 SO（动态库 `libguardcore.so`）：状态机 / AES-GCM（加密算法） / HMAC（消息签名算法） / 授权校验 / wxid 匹配 / 进程角色判断 / hidden 状态持久化
24. **禁止模块用 startService / extends Service**（F-24：Service not found）— Notification 用 `NotificationManager.notify()`，悬浮窗用 `WindowManager.addView()`
25. **所有 XposedHelpers.findAndHookMethod 必须 `catch (Throwable)`**（F-25：NoSuchMethodError 穿透 catch Exception 导致 init 静默中断）
26. **hook protobuf 类方法禁用 `findMethodExact`**（F-26：parseFrom 定义在父类，findMethodExact 不遍历继承链）→ 用 `getMethods()` + `XposedBridge.hookMethod()`
27. **模块启动默认 HIDDEN（隐藏态），底层状态优先**（F-27）
    - 安装任何业务 hook 前，必须先完成 `nativeInit()`
    - `:push` 进程内 hook 必须先判断 `nativeIsHidden()`；为 `false` 时直接短路返回
28. **禁止以 `MvvmList.m(List,boolean)` 类级别 hook 作为朋友圈过滤入口**（F-28：8.0.71 朋友圈数据不走 m()，正确路径是 addAll 实例拦截）
29. **禁止对已装机验证通过的 hook 点做任何未经用户同意的修改**（F-31：D1 h1() 被顺手优化后静默失效，无报错无崩溃只是不过滤。现状跑通 = 不动）

---

## 三.五、AI 工作通用禁忌（所有 skill 默认遵守，不再各自重复）

> 任何 skill 的「⛔ 绝对禁止」表只列**角色特有**条款；以下 7 条所有角色（dispatch/execute/review/terminal/auth）共用。

| # | 通用禁忌 |
|:--:|------|
| G1 | **禁止猜测**（一旦想写"应该/可能/估计/大概/推测" → 停） |
| G2 | **禁止推断**——没有 L1 动态日志（logcat / Frida）或 L2 静态反编译（jadx）作证，就不能下结论 |
| G3 | **没有证据/资料/日志 → 立刻停，主动问用户** |
| G4 | **不确定 → 立刻停，主动问，不写成结论**；必要时在文本里标 ❓ |
| G5 | **未经用户明确同意，禁止改任何 .md 文档** |
| G6 | **用户口述/截图/抓包/粘贴 ≠ 已验证**——即使是 L1 日志原文也要先找到磁盘文件再采信，不直接写入文档 |
| G7 | **写文件一律 UTF-8（无 BOM）**——PS 5.1 下 `>` / `Out-File` 默认 UTF-16LE、裸 `Set-Content` 默认 GBK 会把中文写成乱码；改文件优先走编辑器 / AI 文件工具，终端中文乱码先 `chcp 65001`（文件没坏，是控制台 cp936 问题） |
| G8 | **代码即真相**——文档与代码冲突，以代码为准；STATUS 页定期对着代码核（防旧文档把 AI 带偏，如「A2 未接主线」实已接） |
| G9 | **改完必更**——改代码/改方向必同步更新对应 STATUS 页 + `DECISION_LOG.md`，否则视为没做完 |
| G10 | **一结论一处**——同一结论只写单一真源（机制=DESIGN / 规则=skill / 坑=FAILURE_LOG / 决策=DECISION_LOG），别处只放链接（防漂移） |

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
短期 v1   开发验证：LSPosed + Java + libguardcore.so  →  功能收口（4-6 周）
          出货形态：LSPatch 打包型 APK（见上文「产品形态铁定」）
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
| **C** | 消息控制（防撤回/通知伪装 weixin/未读控制/来电拦截） | 🟡 **部分入 v1**：PushFilter L1/NM ✅ 装机 2026-05-22；**CA 来电拦截 ✅ 2026-05-29**；**AntiRecall(C1) ✅ L1 装机 2026-05-31**；**C3 未读 UNREADFIX ✅ 2026-06-06**；L4b/L4c 已废弃(UNREADFIX 取代)；C4 普通消息通知+铃声转 v1.1；C5 转发 v2+ |
| **D** | 痕迹隐藏（朋友圈密友帖/点赞/评论屏蔽） | ✅ D1/D2/D3 |
| **E** | 装b 模块（步数/定位/改零钱） | v2/v3 |
| **F** | 商业彩蛋（授权引流/独家功能/专属链接） | v2 |

### v1 锁定范围（**产品初心存档**：v1 功能探索期已收口，本节为初心口径，与现状实装表有偏离，仅作存档）
```
11 个 hook（HOOK_MAP_V1.md 的 P0+P1）
 + 3 态状态机（显形/隐藏/解锁中）
 + 搜索框 111111 解锁（EditText 文本监听）
 + B 模块 6 个触发事件（摇一摇/切后台/Home/返回/锁屏/搜索）
 + 朋友圈 Proto 层（hookSnsObject 主线 + INIT 兜底）
 + 【已下沉 v1】PushFilter L1+NM ✅（05-22）· 来电拦截 CA ✅（05-29）· C1 防撤回 ✅（05-31）· C3 未读 UNREADFIX ✅（06-06）；L4b/L4c 已废弃；C4 通知+铃声转 v1.1
 + 【已下沉 v1】P21 朋友圈小红点 Layer0b ✅ + P21B WithAll/bm ✅（LSPosed 装机实证；rm/Layer2/v18 tab 备用层保留）
```
> **AI 注意**：v1 功能探索期已收口（2026-06-29 转上线维护期），本节作为产品初心存档保留、与现状有偏离。当前任务框架见 [`01_dispatch_总调度/CURRENT_PLAN.md`](./01_dispatch_总调度/CURRENT_PLAN.md) 与 `TASK_BOARD.md` §一；hook 事实以 [`HOOKMAP.md`](./HOOKMAP.md) §二 实体表为准。

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
- `SearchFilter`：主路径为 `q2/f0.getView` 渲染层精确 id 过滤（`tz2.u1/tz2.p0/tz2.s1` → wxid/groupId）；聊天记录 `z15.ef6/ch6.e` 仅作分源理解层，不作为主过滤路径
- `SearchUnlock`：hook EditText 构造器，隐藏态 + 密码 `111111` → 切显形
- **假返回"未找到"** = 微信本地无数据时的默认行为（显示"添加好友"）
- `hookSearchContact` 待实现（联系人存在性层）

### 6.6 朋友圈小红点（P21，两个视觉层）
- **MomentsEntryBadge**（朋友圈行入口角标）：`FMF.g1("album_dyna_photo_ui_title", true)` 拦截
- **DiscoverTabBadge**（发现 tab 底部角标）：`TabRedDotChangeEvent`/`WeChatTabRedDotEvent` ctor 清零
- **Layer0b**（互动列表入口）：`Activity.onResume` 过滤 `SnsMsgUI*`，badge 归零
- **P21B 互动列表条目过滤**：`bm/rm -> com.tencent.mm.ui.s9.f(Cursor)` live 游标按 `talker` 跳过隐藏 wxid；WithAll/bm ✅ 2026-06-09，rm 待补 L1

### 6.7 MMKV
- namespace: `g_<seed4>`（每客户独立 seed）
- key: 4 字符短哈希（**不出现** `hide_list` / `vip_enable` 等明文）

### 6.8 字符串混淆
- 类名/方法名/包名禁用敏感词（vip / hide / pirate / wechat / catfish / myauth / wmiyou）
- Toast / Log TAG / MMKV key 全部 seed 化
- v1 手动换 seed + 按时间定时发版（**不做自动构建**）

---

## 七、防检测 KPI（出包前体检项，非日常红线）

| 指标 | 8.0.71 基线（P15 实测） | 安全上限 | 红线 | 来源 |
|------|:----:|:--:|:--:|------|
| verifiedbootstate | 4 | 20 | 38 | P15 实测 |
| PROP / 100K | 5（idle） | 150 | 220 | P15 实测 |
| normsg / 100K | — | 4,000 | 5,124 | QE66 P14 |
| CONN 密度 | — | 0.2 | 0.5 | QE66 P14 |

环境类 vbs/PROP 我方零环境读取本就达标；密度类 normsg/CONN 明显异常才查（P18 零点未建 F-22，上表数字作参考）。**日常健康 = 身份(签名) + 卡顿/性能 + 零新增行为**。

> **KPI 已弱化（2026-07-02）**：frida_stats.js 只作**可选抽检**，**不是发版硬门**。2026-07-02 对官替 LSPatch 候选包 warm-attach 实测：16 指标全 ≈0（PROP/vbs/CONN/DNS/proc 扫描/mprotect 均 0），远低红线——印证「零环境读取故不增量」。想抽跑就跑，不跑不阻塞发版。工具 `frida_stats.js`（→ [`TOOLS_INDEX.md`](./TOOLS_INDEX.md)）。

---

## 八、产品形态演进

```
v1  4-6 周   LSPosed 模块, 小米9/8.0.71, 纯 Java, 自用+种子客户
v2  2-3 月   消息控制完整 + 授权引流壳 + miyou-server 授权接入
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
| **授权检查官** | 大框架守门：状态机 / 授权 / 模块边界 / 过滤位置 / 拆-合代码决策 | `guard-auth-review_授权检查官` |
| **防封官** | 反检测 / 防封 / 检测面 / 异常上报链 / KPI 红线 | `guard-antiban_防封官` |
| **网络安全官** | 客户端安全 / 加密配方 / DRM / 授权防护 / 服务器授权信封 / 蜜罐 | `guard-security_网络安全官` |
| **发版官** | 发布出包流水线：官替/共存双版本 + 换 s_rel + LSPatch 打包 + 服务器同步 + 装机 L1（同一套签名/工具/流程/服务器） | `guard-release_发版` |
| **服务器运维** | miyou-server 授权后台：卡密 / release_lines / envelope / S_rel·W / 版本状态 / 主备部署 | `guard-server_服务器运维` |
| **git 保姆** | 替不懂 git 的用户跑命令：快照 / 备份 / 提交 / 回退 / 清 git 垃圾 | `guard-git_保姆` |

> 共 **10 个 Guard 角色 skill**（核心 4 + 专项 6：授权检查官、防封官、网络安全官、发版官、服务器运维、git 保姆）。**发版/发布/做官替/做共存 → 发版官**；服务器后台 → 服务器运维。

**调试铁律（执行角色核心）**：需要设备操作时，必须给用户一个明确指令，等结果回来再推进。禁止假设输出、禁止"估计 XXX"后直接改代码。
**主目录**: `.cursor/skills/`（Cursor 日常用，统一 `SKILL.md` 大写）
**镜像**: `.claude/skills/`（用 `sync_skills.ps1` 自动同步；注意历史上 dispatch/execute 曾是小写 `skill.md`，同步时以主目录大写为准）

---

## 十一、当前阶段：上线维护期（2026-06-29 相位切换）

详细见 [`TASK_BOARD.md`](./TASK_BOARD.md) §一 + [`01_dispatch_总调度/CURRENT_PLAN.md`](./01_dispatch_总调度/CURRENT_PLAN.md)

> v1 探索期旧 4 窗口（W1 P20/P20B · W2 P21 · W3 P17 · W4 P18）已全部收口/归档（去向见 `TASK_BOARD.md` §五）。当前三条主线：

| 主线 | 范围 | 状态 |
|:--:|------|:--:|
| ① 上线维护 | 防破解 / miyou-server 授权 / 发版(官替+共存) / 绊线 / KPI | 🟡 进行中（cert-sync ✅；A2 闸等 🟡） |
| ② 按需加功能 | E3 改余额 / C5 语音转发 / §6a / 选人列表隐私缺口 等（一功能一卡） | ⬜ |
| ③ 版本适配 | 换微信版本的 hook diff 流程 | 📘 `docs/VERSION_UPGRADE_SOP.md` |

**并发铁律**：每个窗口只动自己 P 任务目录，根目录看板（HOOKMAP/TASK_BOARD）改动前 git pull。

---

## 十二、关键资料路径（不复制，只记路径）

完整清单 → [`PROJECT_INDEX.md`](./PROJECT_INDEX.md)。高频访问：

```
本仓库内:
  ./PROTECTION_MAP.md               授权防护总账 + 上线前门控（注入隔离/蜜罐/阶段）
  ./docs/README.md                  文档入口（8071 主车道）
  ./docs/HOOK_MAP_8071_AUTHORITATIVE.md  8071 hook 权威
  ./docs/archive/INDEX.md           8066 历史（禁止直搬）
  ./docs/isolation/INDEX_COMPETITOR.md  Catfish 竞品参考
  ./docs/isolation/MainEntry.java      竞品入口 989 行
  ./docs/isolation/UserControll.java   竞品业务 944 行
  ./docs/isolation/filter_moments.js   Frida 朋友圈已验证脚本
  ./06_refs_参考资料/competitor_catfish/CATFISH_REVERSE.md   竞品逆向文档（⚠️ 老版本 8.0.70，与目标 8.0.71 偏移很大：混淆类名/字段/行号全变，仅作行为·思路参考，禁止直搬类名/字段/偏移；竞品唯一真源，勿删勿移）

外部资料（只读，不复制）:
  I:/apk2/_4__samples/dynamic_fast/HOOK_IMPLEMENTATION_ANALYSIS.md
  I:/apk2/_4__samples/sample_history_research/VERSION_INDEX.md
  I:/apk2/QE66_RESUME.md
  I:/miyou-server/CLAUDE.md         双链路授权服务器
  E:/apk_diff/                      7 个历史 APK（只读样本）
  E:/ios-dylib/shitou-miyou-core/   iOS 蜘蛛密友参考
```

---

## 十三、发布与运营

发布、危险通告、官替版 / 共存版、签名证书和加密发版规则统一收敛到 [`docs/RELEASE_RULES.md`](./docs/RELEASE_RULES.md)；**商业模式 / 对外品牌**已拆到 [`docs/运营_商业与品牌.md`](./docs/运营_商业与品牌.md)。

本文件只保留入口：发版、签名、共存版、`registry_cipher`、`guardWxPkg`、客户包档案相关问题，先读 `docs/RELEASE_RULES.md`，再进入对应 skill。

---

## 十三.五、🔐 签名铁律（单一权威 · cert-converge v2 D-026）

> 所有 skill 的签名规则指回这里；skill 里只保留一行 cert 值 + 指针。深真源 → `docs/RELEASE_RULES.md` + `docs/RELEASE_LINE_SSOT_v2.md`。

- **发版 release（出货）**：`signing/guard-native-official-release.jks`（alias `guardofficial`）→ cert `e3e13a49`；**官替 + 共存共用此把**（build.gradle `guardOfficialRelease`）。密码在 `signing/keystore.properties`（gitignore）。
- **调试 debug（smoke）**：`signing/guard-native-debug.keystore` → cert `ca421ec3`；仅模块更新/公告/C2-smoke，cert ≠ `GUARD_EXPECTED_CERT` 注定 registry 散沙，**不作发版候选**。
- 禁止依赖或重建 `~/.android/debug.keystore`。
- `INSTALL_FAILED_UPDATE_INCOMPATIBLE` 必须先停、比对已装 APK 与对应 key 指纹，未经用户确认禁止卸载。
- 缺少对应 keystore 时停止 build/装机；日志只能写当前 P 任务 `logs/`，禁止写进 docs/skill 目录。
- 官替版和共存版是两条独立发行线，**共用同一把 release jks**（D-026）；各自固定 `packageName` + `versionCode`；官替只覆盖官替，共存只覆盖同包名共存。
- 发版 / 签名 / 共存版任务先读 `docs/RELEASE_RULES.md`；禁止为旧客户旧版本线重新生成 keystore。

---

**接手到这里就够了。开始干活前一定要再看一遍 [`HOOKMAP.md`](./HOOKMAP.md) + [`TASK_BOARD.md`](./TASK_BOARD.md)。**
