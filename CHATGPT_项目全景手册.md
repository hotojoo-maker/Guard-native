# Guard Native — 项目全景手册（给 ChatGPT / 外部 AI 用）

> **用途**：把本文件整份粘贴给网页版 ChatGPT，即可快速接手 Guard Native 项目。  
> **项目根目录**：`C:\Users\Me\Desktop\guard_native`  
> **目标微信版本**：**8.0.71**（决策 D-014，2026-05-21 切版）  
> **形态**：LSPosed Xposed 模块 + 自有 `libguardcore.so`（仅判断中心，不做 native hook）  
> **更新时间**：2026-05-22

---

## 一、项目一句话

Guard Native 是微信 8.0.71 的**密友/密群隐私 LSPosed 模块**：隐藏态下过滤会话、通讯录、朋友圈、搜索中的密友 wxid 和密群 groupId；通过摇一摇/切后台/锁屏等触发隐藏，通过主界面放大镜搜索框输入 `111111` 解锁显形。

---

## 二、文档主线 — 必读顺序

接手任何任务，**按此顺序读，不读完不动代码**：

| 顺序 | 文件 | 作用 |
|:--:|------|------|
| 0 | `AGENTS.md` | Cursor/Claude 兼容入口，指向 CLAUDE.md |
| 1 | **`CLAUDE.md`** | **主入口**：29 条铁律、技术架构、KPI、4 窗口概览 |
| 2 | **`HOOKMAP.md`** | 功能模块 A~F 总图 + 拦截层状态（✅/🟡/⬜） |
| 3 | **`TASK_BOARD.md`** | 4 窗口分工 + P 任务进度 + 依赖关系 |
| 4 | `docs/PRODUCT_GATE.md` | 产品总闸：状态机/VIP/密码/shouldHide 公式 |
| 5 | `FAILURE_LOG.md` | 28+ 条永久禁用方案（F-01 ~ F-31） |
| 6 | `PROJECT_INDEX.md` | 路径表 + 内外部资料索引 |
| 7 | `docs/HOOK_POINTS.md` | 8.0.66/71 hook 点 Java 伪代码（F04/F05 四层） |
| 8 | `docs/HOOK_MAP_V1.md` | v1 锁定的 11 个 hook 优先级（P0+P1） |
| 9 | `native_core/MAP.md` + `native_core/API.md` | C++ SO 模块地图 + JNI 接口 |

### 状态符号铁律（全项目统一）

| 符号 | 含义 | AI 行为 |
|------|------|---------|
| ✅ | 有 logcat/Frida **日志原文**的装机验证 | 可当作已完成，**禁止擅自改**（F-31） |
| 🟡 | 代码已写，未装机或部分验证 | 不能当"已完成"继续堆功能 |
| ⬜ | 只读名字，禁止展开细节 | 禁止推断实现 |
| ❌ | 已证伪，永久禁用 | 进 FAILURE_LOG，不得复用 |

---

## 三、当前进度快照（2026-05-22）

```
文档层 ████████████ 100%
代码层 ████████░░░░ 65%  ← 当前位置（v1 功能开发）
验证层 ████████████ 100%  D1/D2/D3 · 会话 · 通讯录 · 密群 · P21 Layer0b/Layer2
```

### v1 已装机验证 ✅

| 功能 | 实现文件 | 8.0.71 Hook 路径 |
|------|---------|-----------------|
| D1 朋友圈帖隐藏 | `MomentsFilter.java` | `ArrayList.addAll(na4.b)` → `la4.p.field_userName` |
| D2 点赞隐藏 | `MomentsFilter.java` | `LinkedList.add(z15.e56)` → `entry.d` |
| D3 评论隐藏 | `MomentsFilter.java` | `LinkedList.add(cs5.di0)` → `entry.d` |
| F04 会话隐藏 | `ConvFilter.java` | `MvvmList.n(List,bool)` + `.s(List)` + `notifyDataSetChanged` clean-before |
| F07 通讯录隐藏 | `ContactFilter.java` | `ArrayList.addAll(fc5.g)` → `g.d(z3).c1()` |
| A3 密群 | `Bridge.java` | 复用 F04/F07，`allHiddenIds()` union |
| P21 小红点 Layer0b | `MomentsRedDotGuard.java` | `Activity.onResume` 过滤 `SnsMsgUI*` |
| P21 小红点 Layer2 | `MomentsRedDotGuard.java` | `FMF.g1("album_dyna_photo_ui_title", true)` 拦截 |

### v1 代码已写，待装机 ⬜

| 功能 | 实现文件 |
|------|---------|
| B1 摇一摇隐藏 | `TriggerGuard.java` |
| B2 切后台/Home 隐藏 | `TriggerGuard.java` |
| B5 锁屏隐藏 | `TriggerGuard.java` |
| B6 搜索框 111111 解锁 | `SearchUnlock.java` |
| 搜索 FTS 拦截 | `SearchFilter.java` |
| P21 v18 tab badge | `MomentsRedDotGuard.java`（TabRedDotChangeEvent ctor 清零） |
| native Batch 1 验证 | `NativeBridge.java` + `libguardcore.so` |

### 下一个任务

**P20B**：B 模块触发器 + 搜索拦截装机验证（W1 窗口）

---

## 四、功能模块 A~F（客户功能菜单）

| 模块 | 含义 | v1 范围 | 状态 |
|------|------|---------|:--:|
| **A** | 核心隐私（密友/密群/密码/总开关） | A1/A2/A3 ✅；A4 密码 ⬜ | 🟡 |
| **B** | 隐藏触发（摇一摇/切后台/Home/锁屏/搜索 111111） | 6 事件 | ⬜ 待装机 |
| **C** | 消息控制（防撤回/通知伪装/未读） | **不在 v1** | v2+ |
| **D** | 痕迹隐藏（朋友圈帖/赞/评） | D1/D2/D3 | ✅ |
| **E** | 装b（步数/定位/改零钱） | 不在 v1 | v2/v3 |
| **F** | 商业彩蛋（反盗版/引流） | 不在 v1 | v2 |

### 三态状态机（A1）

| 状态 | 密友可见？ | id 过滤？ | 如何进入 |
|------|:---:|:---:|------|
| **HIDDEN** | ❌ | ✅ | 首装默认 / B1-B5 触发 |
| **VISIBLE** | ✅ | ❌ | B6 输入 `111111` |
| **UNLOCKING** | ❌ | ✅ | 搜索框弹出，密码未输完 |

**触发方向铁律**：B1-B5 全部单向 → HIDDEN；B6 单向 → VISIBLE。**没有 toggle**。

### shouldHide 总闸公式

```text
shouldHide() =
    nativeIsAuthorized()           // v1 占位 true
    AND nativeIsPrivacyEnabled()
    AND nativeIsHidden()           // HIDDEN / UNLOCKING
    AND (密友名单非空 OR 密群名单非空)
    AND !killSwitch

shouldHideId(id) =
    nativeIsHiddenWxid(id) OR nativeIsHiddenGroup(id)
```

---

## 五、Java Hook 完整地图（8.0.71 已验证）

### 5.1 关键拦截层总表

| 层 | Hook 点 | 功能 | 状态 | 实现类 |
|----|---------|------|:--:|--------|
| ~~L0 Proto~~ | ~~`SnsObject.parseFrom`~~ | 朋友圈 Proto | ❌ F-27 证伪 | — |
| **L0v2 D1** | `ArrayList.addAll(na4.b)` → `la4.p.field_userName` | 朋友圈帖过滤 | ✅ | MomentsFilter |
| **L0v4 D2/D3** | `LinkedList.add(z15.e56/cs5.di0/i84.y)` → `entry.d` | 赞评过滤 | ✅ | MomentsFilter |
| **F07 通讯录** | `ArrayList.addAll(fc5.g)` → `z3.c1()` | 通讯录过滤 | ✅ | ContactFilter |
| **L1 会话** | `MvvmList.n(List,bool)` 8.0.71 | 会话主线 | ✅ | ConvFilter |
| **L2 会话** | `MvvmList.s(List)` | 会话备用 | ✅ | ConvFilter |
| **L4 会话** | `kc5.v0.notifyDataSetChanged` clean-before | 渲染兜底 | ✅ | ConvFilter |
| **P21 Layer0b** | `Activity.onResume` + `SnsMsgUI*` | 互动列表过滤 | ✅ | MomentsRedDotGuard |
| **P21 Layer2** | `FMF.g1("album_dyna_photo_ui_title", true)` | 朋友圈行红点 | ✅ | MomentsRedDotGuard |
| **P21 v18 tab** | `TabRedDotChangeEvent`/`WeChatTabRedDotEvent` ctor | 发现 tab 角标 | 🟡 待触发 | MomentsRedDotGuard |
| **搜索 FTS** | `SearchFilter` addAll + `z15.ef6` | 搜索拦截 | ⬜ | SearchFilter |
| **密码解锁** | EditText TextWatcher | 111111 → VISIBLE | ⬜ | SearchUnlock |

### 5.2 wxid / groupId 提取路径（8.0.71）

```
会话：  kc5.y item → y.d(l4 实例) → l4.h1() = wxid
        getter 候选轮询：h1 / j1 / i1 / k1 / getUsername / getUserName

通讯录：fc5.g item → g.d(z3 实例) → z3.c1() = wxid
        类常量：com.tencent.mm.storage.z3

朋友圈帖：na4.b item → la4.p → field_userName（fallback 主路径）
        ⚠️ h1() 路径 null miss（F-31），禁止改回 h1() 主路径

朋友圈赞评：z15.e56 / cs5.di0 → entry.d（或 f435583d / username / field_userName 四字段轮询）

密群：   groupId = xxxxxxxx@chatroom，复用 allHiddenIds() 与会话/通讯录/搜索同路径
```

### 5.3 v1 锁定的 11 个 Hook（HOOK_MAP_V1 P0+P1）

| # | Hook 名 | 功能 | guard_native 实现 |
|---|---------|------|------------------|
| P0-1 | hookSnsObject | 朋友圈 Proto | ❌ F-27；改用 L0v2 addAll |
| P0-2 | hookSearchContact | 精确搜索拦截 | SearchFilter（⬜） |
| P0-3 | hookFts | FTS 全文搜索 | SearchFilter（⬜） |
| P0-4 | hookRecent | 会话列表 | ConvFilter L1/L2 ✅ |
| P0-5 | hookConverBack | 返回键触发 | TriggerGuard B4（⬜） |
| P1-6 | hookAddressInfo | 通讯录单项 | ContactFilter ✅ |
| P1-7 | hookContactCount | 通讯录计数 | ⬜ 未做 |
| P1-8 | hookSnsComments | 评论列表 | MomentsFilter D3 ✅ |
| P1-9 | hookSnsLikes | 点赞列表 | MomentsFilter D2 ✅ |
| P1-10 | hookFriendStatus | 密友圈 Map | v2 |
| P1-11 | hookFriendStatusItem | 密友圈单项 | v2 |

**不在 v1 内**：A4 搜索框 111111 解锁（EditText 监听）、B 模块 6 触发事件、朋友圈 Proto INIT 兜底。

### 5.4 ModuleMain 注册顺序（铁律 27）

```
handleLoadPackage
  → F-16 进程白名单（仅 com.tencent.mm 主进程 + :push）
  → Application.onCreate
      0. NativeBridge.init()          ← 必须在所有 hook 之前
      1. AppConfig.init()
      2. Bridge.init()                 ← MMKV 密友/密群名单
      3. StateMachine.init()
      4. InterceptCounter.init()
      5. killSwitch 检查
      6. StateMachine.restoreState()
      7. 注册业务 hook：
         SearchUnlock / SearchFilter / MomentsFilter / ConvFilter / ContactFilter
         MomentsRedDotGuard / UpdateGuard / TriggerGuard
      8. Debug 工具（DEV/HONEY 模式）
```

Log TAG = **`NCL`**（seed 化，不用 Guard/Vip 等敏感词）

### 5.5 永久禁止的 Hook 方案（摘要）

| 编号 | 禁止做什么 | 原因 |
|------|-----------|------|
| F-05~06 | hook K0/getCount/getView | Kotlin Flow 覆盖 |
| F-11 | Java 反射 invoke notifyDataSetChanged | ART SIGSEGV |
| F-13~15 | notifyItemRange* 三种变体 | DiffUtil 错位/SIGABRT |
| F-19~20 | 读 ro.boot.* 属性 | Matrix 反向 hook |
| F-23 | 注入微信 JNI 链 / dlopen 微信 SO | CodecLooper SIGSEGV + 强制下线 |
| F-25 | findAndHookMethod 只 catch Exception | NoSuchMethodError 静默中断 init |
| F-26 | protobuf 类用 findMethodExact | parseFrom 在父类 |
| F-27 | SnsObject.parseFrom Java hook | 8.0.71 走 JNI/C++，零命中 |
| F-28 | MvvmList.m 类级别 hook 朋友圈 | 8.0.71 不走 m()，走 addAll |
| F-29 | 改已装机验证通过的 hook 点 | 静默失效无报错（F-31 实证） |

---

## 六、C++ libguardcore.so 地图

### 6.1 定位

```
libguardcore.so = 判断中心（状态/授权/wxid 匹配/进程角色）
Java hook 层    = 执行层（XposedHelpers，不归 SO 管）
:push 进程      = 最小守护（只拦 badge/unread 写入）
```

**铁律 F-23 例外**：模块自有 SO 允许做状态机/AES-GCM/HMAC/授权/wxid 匹配/进程判断；**禁止** hook 微信 native 方法。

### 6.2 C++ 模块一览

| 模块 | 源文件 | 职责 | 批次 |
|------|--------|------|:--:|
| **ProcessRouter** | `process_router.cpp` | 读 `/proc/self/cmdline` 判断 MAIN/PUSH/BLOCKED | B1 |
| **StateMachine** | `state_machine.cpp` | 6 态：VISIBLE/HIDDEN/UNLOCKING/LOCKED/SAFE_MODE/EXPIRED | B1 |
| **WxidMatcher** | `wxid_matcher.cpp` | 密友 wxid + 密群 groupId 哈希集合 O(1) 查询 | B1 |
| **PushGuard** | `push_guard.cpp` | `:push` 进程 badge/unread 拦截判断 | B1 |
| **AntiTamper** | `anti_tamper.cpp` | 包名 + config_version 轻量校验 | B1 |
| **LogLimiter** | `log_limiter.cpp` | 限流日志，Release 完全 no-op | B1 |
| **LicenseBox** | *(未实现)* | HMAC-SHA256 + AES-GCM 授权 | B2 |
| **NotifyPolicy** | *(未实现)* | 通知策略 | B2 |
| **FeatureGate** | *(未实现)* | 基础功能开关 | B3 |

### 6.3 JNI 接口速查（14 个，NativeBridge.java）

| JNI 方法 | 进程 | 用途 |
|----------|:----:|------|
| `nativeInit(processName, packageName)` | 主+push | **必须第一个调**，失败进 SAFE_MODE |
| `nativeGetProcessRole()` | 主+push | MAIN=1 / PUSH=2 / BLOCKED=3 |
| `nativeIsAuthorized()` | 主 | B1 占位永远 true |
| `nativeGetAuthState()` | 主 | B1 占位永远 AUTH_OK |
| `nativeIsHidden()` | 主+push | 热路径短路：false 则不过滤 |
| `nativeSetHidden(boolean)` | 主 | B 模块触发 / 解锁 |
| `nativeEnterHidden()` | 主 | B1-B5 触发 |
| `nativeEnterVisible()` | 主 | B6 密码成功 |
| `nativeEnterSafeMode()` | 主 | kill_switch / 异常 |
| `nativeIsHiddenWxid(wxid)` | 主+push | 密友判断 |
| `nativeIsHiddenGroup(groupId)` | 主+push | 密群判断 |
| `nativeShouldBlockBadge(wxid)` | push | badge 写入拦截 |
| `nativeGetConfigVersion()` | 主 | SO 配置版本 |
| `nativeGetRiskState()` | 主 | 风险枚举 |

### 6.4 C++ 文件树

```
native_core/
├── CMakeLists.txt              构建（arm64-v8a + armeabi-v7a）
├── include/
│   └── guard_core.h            所有枚举 + 模块接口声明
├── src/
│   ├── guard_core.cpp          JNI 入口（Java ↔ C++ 桥）
│   ├── core_init.cpp           启动协调：Router→AntiTamper→SM→Matcher
│   ├── process_router.cpp      进程角色检测
│   ├── state_machine.cpp       隐藏状态机
│   ├── wxid_matcher.cpp        密友/密群集合
│   ├── push_guard.cpp          :push badge 守护
│   ├── anti_tamper.cpp         包名/版本校验
│   └── log_limiter.cpp         限流日志
├── MAP.md                      模块地图（中文）
├── API.md                      接口详细说明（中文）
├── ARCHITECTURE.md             架构设计
├── RULES.md                    开发铁律
└── ROADMAP.md                  Phase 0–6 计划
```

### 6.5 Native Phase 路线图

```
Phase 0 文档 ✅
  └─ Phase 1 C++ 骨架 🟡（编译通过，待装机 BATCH1_VERIFY）
       └─ Phase 2 Test App 验证 ⬜
            └─ Phase 3 主进程 LSPosed 接入 ⬜
                 └─ Phase 4 :push badge 拦截 ⬜
                      └─ Phase 5 全面切换 NativeBridge ⬜
                           └─ Phase 6 LicenseBox + P21/P22 ⬜
```

**Batch 1 装机验收关键字**：`adb logcat -s NCL` → 看到 `[native] BATCH1_VERIFY PASS`

---

## 七、Java 源码文件树

```
src/main/java/com/ghost/assist/
├── ModuleMain.java                 LSPosed 入口 + hook 注册顺序
├── core/
│   ├── AppConfig.java              DEV/PROD/HONEY 三态 + killSwitch
│   ├── Bridge.java                 MMKV 密友 wxid / 密群 groupId
│   ├── StateMachine.java           3 态状态机 + MMKV 持久化
│   ├── InterceptCounter.java       F04/F05/F07 拦截计数
│   └── NativeBridge.java           libguardcore.so JNI 桥
├── moduleB/                        B 模块（触发 + 搜索）
│   ├── TriggerGuard.java           B1 摇一摇 / B2 切后台 / B5 锁屏
│   ├── SearchUnlock.java           B6 放大镜 111111 解锁
│   ├── SearchFilter.java           FTS 搜索拦截
│   └── UpdateGuard.java            更新/状态机联动
├── moduleD/                        D 模块（过滤 + 小红点）
│   ├── ConvFilter.java             F04 会话 4 层拦截 ✅
│   ├── ContactFilter.java          F07 通讯录 ✅
│   ├── MomentsFilter.java          D1/D2/D3 朋友圈 ✅
│   └── MomentsRedDotGuard.java     P21 小红点 Layer0b/Layer2/v18
└── debug/                          开发期工具（P15 脚手架）
    ├── DebugServer.java            HTTP :8080 调试控制台
    ├── OverlayWindow.java          悬浮窗事件流
    ├── StatusNotification.java     通知栏状态
    ├── ContactResolver.java        反射查昵称头像
    ├── DebugTelemetry.java         遥测
    └── UiContextTracker.java       UI 上下文追踪

src/main/assets/debug/index.html    浏览器调试页
```

---

## 八、完整项目目录树

```
guard_native/
│
├── 【根目录核心文档】
│   ├── CLAUDE.md                   ★ 主入口（29 铁律 + 架构共识）
│   ├── AGENTS.md                   Cursor/Claude 兼容入口
│   ├── HOOKMAP.md                  ★ 功能模块总图 A~F
│   ├── TASK_BOARD.md               ★ 4 窗口 + P 任务看板
│   ├── PROJECT_INDEX.md            路径表 + 内外部资料
│   ├── FAILURE_LOG.md              28+ 永久禁用方案
│   ├── DECISION_LOG.md             重大决策履历
│   ├── RISK_REGISTER.md            风险表
│   ├── TOOLS_INDEX.md              工具/脚本索引
│   ├── FINDINGS.md                 发现落盘
│   └── CHATGPT_项目全景手册.md       ← 本文件
│
├── 【AI Skills — 四角色工作流】
│   ├── .cursor/skills/             主目录（Cursor 日常编辑）
│   │   ├── guard-dispatch_总调度/SKILL.md
│   │   ├── guard-execute-one_单任务执行/SKILL.md
│   │   ├── guard-review_质检门控/SKILL.md
│   │   └── guard-terminal_终端操作/SKILL.md
│   └── .claude/skills/             镜像（sync_skills.ps1 同步）
│
├── 【编号工作目录】
│   ├── 00_start_入口/              新会话第一站
│   ├── 01_dispatch_总调度/         CURRENT_PLAN / NEXT_STEP
│   ├── 02_docs_资料员/             文档索引 / 冲突 / T 调研任务池
│   │   └── T_TASKS/                T08 防撤回调研等
│   ├── 03_execute_执行任务/        ★ P 任务执行目录
│   │   ├── P15_脚手架/             ✅ 调试控制台 + 状态机
│   │   ├── P16_朋友圈Proto/        ✅ D1/D2/D3 + 大量 Frida 脚本
│   │   ├── P17_会话LSPosed/        ✅ ConvFilter 验收
│   │   ├── P18_离线采集/           ⬜ Proto dump + KPI 基线
│   │   ├── P19_通讯录隐藏/         ✅ ContactFilter 验收
│   │   ├── P20_搜索拦截/           ⬜ SearchFilter + SearchUnlock
│   │   ├── P20B_BTriggers_SearchUnlock/  ⬜ B 模块装机
│   │   ├── P21_MomentsRedDot/      ✅ Layer0b/Layer2 实证
│   │   └── T09_Catfish_L0v3/       Catfish 竞品探针
│   ├── 04_review_审稿复核/         P 任务审稿报告
│   ├── 05_reports_报告/          阶段报告
│   ├── 06_refs_参考资料/         竞品/微信/Frida/采集快照
│   ├── 07_archive_归档/          关闭 30 天的 P 任务
│   └── 08_release_发布/          seed / 签名 / APK 输出
│
├── 【技术文档】
│   └── docs/
│       ├── HOOK_POINTS.md          F04/F05 四层 hook Java 伪代码
│       ├── HOOK_MAP_V1.md          v1 11 hook 优先级
│       ├── PRODUCT_GATE.md         ★ 产品总闸公式
│       ├── CLASS_MAP_8066.md       8.0.66 混淆类速查
│       └── RESEARCH_SUMMARY.md     研究汇总
│
├── 【参考代码（只读，类名必须重写）】
│   └── refs/
│       ├── MainEntry.java          Catfish 入口 989 行
│       ├── UserControll.java       Catfish 业务 944 行
│       ├── filter_moments.js       朋友圈 Frida v21 已验证
│       └── VipPreference.java      MMKV 封装参考
│
├── 【Native C++】
│   └── native_core/                libguardcore.so（见 §六）
│
├── 【源码 + 构建】
│   ├── src/main/java/              LSPosed 模块 Java 代码
│   ├── src/main/assets/            debug/index.html
│   ├── build.gradle                Gradle + externalNativeBuild
│   ├── app/                        辅助 app 模块
│   └── tools/                      独立 Frida 探针脚本
│
└── 【构建产物（gitignore）】
    ├── build/outputs/apk/debug/guard-native-debug.apk
    └── build/intermediates/cmake/debug/obj/*/libguardcore.so
```

---

## 九、P 任务历史

| P 号 | 任务 | 状态 | 完成日 |
|:--:|------|:--:|------|
| P15 | 脚手架（调试控制台/状态机/通知栏/HTTP） | ✅ | 2026-05-19 |
| P16 | 朋友圈 D1/D2/D3 | ✅ | 2026-05-20 |
| P17 | 会话 LSPosed ConvFilter | ✅ | 2026-05-20 |
| P18 | 离线采集 + KPI 基线 | ⬜ | — |
| P19 | 通讯录 F07 ContactFilter | ✅ | 2026-05-20 |
| P20 | 搜索 + 密码入口 | ⬜ 代码已写 | — |
| P20B | B 模块触发器装机 | ⬜ 下一个 | — |
| P21 | 朋友圈小红点 | ✅ Layer0b/Layer2 | 2026-05-21 |
| A3 | 密群数据层 + Filter union | ✅ | 2026-05-21 |
| P_NC1 | native_core Batch 1 | 🟡 编译通过待装机 | — |

---

## 十、4 窗口并行分工

| 窗口 | 主题 | 当前 P 任务 | 状态 |
|:--:|------|------------|:--:|
| **W1** | B 模块触发器 + 搜索 | P20B/P20 | ⬜ 待装机 |
| **W2** | 朋友圈小红点 | P21 | ✅ Layer0b/Layer2 |
| **W3** | 会话 LSPosed | P17 | ✅ 完成 |
| **W4** | 离线资料库采集 | P18 | ⬜ 待领 |

**并发铁律**：每窗口只动自己 P 任务目录；改 HOOKMAP/TASK_BOARD 前先 git pull。

---

## 十一、Skills 四角色 — 完整用法（可复制）

本项目用 **4 个 Cursor Agent Skills** 覆盖全部工作流。在 Cursor 里用 `@` 引用 skill 文件，或在对话开头粘贴下方 **invoke 提示词**。

### 11.1 角色选择表

| 角色 | 何时用 | Skill 路径 | Cursor invoke |
|------|--------|-----------|---------------|
| **总调度** | 新会话接手 / 分配 P 任务 / 更新看板 | `.cursor/skills/guard-dispatch_总调度/SKILL.md` | 见下方 |
| **执行** | 写代码 / 设备调试 / 单个 P 任务 | `.cursor/skills/guard-execute-one_单任务执行/SKILL.md` | 见下方 |
| **质检** | P 任务完成装机前 / 发版前 | `.cursor/skills/guard-review_质检门控/SKILL.md` | 见下方 |
| **终端** | build / adb / frida / logcat | `.cursor/skills/guard-terminal_终端操作/SKILL.md` | 见下方 |

### 11.2 可复制 Invoke 提示词

#### 总调度（新会话第一件事）

```
请读取并严格遵循 skill：.cursor/skills/guard-dispatch_总调度/SKILL.md

我是 Guard Native 项目。请先：
1. 读 CLAUDE.md + HOOKMAP.md + TASK_BOARD.md
2. 输出【总调度报告】：当前窗口、P 任务、下一步、风险提示
3. 不要改任何 md 文档，除非我明确说「写入文档」
```

#### 执行（领到 P 任务后）

```
请读取并严格遵循 skill：.cursor/skills/guard-execute-one_单任务执行/SKILL.md

我要做 P 任务：[填 P 号，如 P20B]
请先读 03_execute_执行任务/P20B_xxx/brief.md（如有），然后：
- 写 hook 代码前过 FAILURE_LOG 相关 F-xx
- 需要设备操作时给我一条明确指令，等我回复，禁止盲猜
- 同一方向失败 2 次立刻停，汇报卡点
```

#### 质检（P 任务完成 / 发版前）

```
请读取并严格遵循 skill：.cursor/skills/guard-review_质检门控/SKILL.md

对 P[填号] 做 [轻档自审 / 重档发版门控]：
- 轻档：铁律 grep + 自洽性 + 编译 + 路径
- 重档：+ KPI frida_stats.js 对比红线
输出【P 自审报告】或【发布门控报告】格式
```

#### 终端（build / adb / 装机）

```
请读取并严格遵循 skill：.cursor/skills/guard-terminal_终端操作/SKILL.md

帮我 [编译 / 装机 / 看 logcat / 跑 frida]：
- Shell 是 PowerShell，用 Select-String 不用 grep
- 每步先说目的再给命令
- 日志 >500 行强制分段分析
```

### 11.3 何时切 Skill vs 不切

| 场景 | 切不切 | 原因 |
|------|:--:|------|
| 单次 adb/frida 命令 | ❌ 不切 | 用户贴结果回来即可 |
| 同一 P 任务连续调试 <5 轮 | ❌ 不切 | 上下文连贯 |
| 全新 P 任务（不同模块） | ✅ 切 execute | 独立上下文 |
| P 任务完成要装机 | ✅ 切 terminal | 专用命令 |
| 发版 / 合并主线 | ✅ 切 review 重档 | KPI 门控 |
| 上下文 50+ 轮膨胀 | ✅ 切新会话 + dispatch | 轻装上阵 |

### 11.4 Skills 同步（Cursor ↔ Claude Code）

```powershell
# 项目根目录执行，把 .cursor/skills 镜像到 .claude/skills
.\sync_skills.ps1
```

主目录：`.cursor/skills/`（日常编辑这里）  
镜像：`.claude/skills/`（Claude Code 终端用）

### 11.5 四 Skill 核心铁律（共同）

1. **禁止猜测** — 无 L1 logcat / L2 jadx 证据不得写结论
2. **⬜ 只读名字** — 禁止展开未验证功能细节
3. **文档写入门控** — 改 HOOKMAP/TASK_BOARD/worklog 前必须用户明确同意
4. **设备调试必须交互** — 一次给一个操作，等用户回复
5. **关 P 任务必跑 frida_stats.js** — KPI 对比基线（F-22）

---

## 十二、常用终端命令（PowerShell）

```powershell
# 项目根
cd "C:\Users\Me\Desktop\guard_native"

# 编译
.\gradlew assembleDebug 2>&1 | Select-String "error:|cannot|symbol|BUILD"

# 装机 Guard 模块
adb install -r "build\outputs\apk\debug\guard-native-debug.apk"

# 强停微信
adb shell am force-stop com.tencent.mm

# 只看 Guard 日志
adb logcat -s NCL:I GRD:I SM:I -v time

# 调试 Web UI
adb forward tcp:8080 tcp:8080
# 浏览器 http://localhost:8080

# KPI 门控（每个 P 任务关闭必做）
frida -U -f com.tencent.mm --no-pause -l "I:/apk2/_3__D_wechat_ban/official_wechat_ban_research/03_anti_frida/frida_stats.js" 2>&1 | Tee-Object "logs\kpi.log"
```

### KPI 红线

| 指标 | 8.0.71 基线 | 安全上限 | 红线 |
|------|:--:|:--:|:--:|
| verifiedbootstate | 4 | 20 | **38** |
| PROP/100K | 5 | 150 | 220 |
| normsg/100K | — | 4000 | 5124 |
| CONN 密度 | — | 0.2 | 0.5 |

---

## 十三、外部资料（只读，不复制进仓库）

```
I:/apk2/
├── QE66_RESUME.md                  P0~P14 防封号水位线
├── _3__D_wechat_ban/.../frida_stats.js   16 指标 KPI 工具
├── _4__samples/dynamic_fast/HOOK_IMPLEMENTATION_ANALYSIS.md  Catfish 全 hook
└── _4__samples/sample_history_research/VERSION_INDEX.md      7 版本索引

I:/miyou-server/CLAUDE.md           授权服务器（v2 cs_url kill_switch）

E:/apk_diff/                        7 个历史密友 APK 样本
E:/ios-dylib/shitou-miyou-core/     iOS 蜘蛛密友参考
```

---

## 十四、给 ChatGPT 的使用建议

1. **先读 §二文档主线**，再回答任何实现问题  
2. **查 Hook 点**：§五 Java Hook 地图 + `docs/HOOK_POINTS.md`  
3. **查 C++**：§六 + `native_core/API.md`  
4. **做任何 hook 前**：grep `FAILURE_LOG.md` 对应 F-xx  
5. **状态标注**：没有 logcat 原文不得标 ✅  
6. **8.0.71 锁定**：禁止直搬 8.0.66/8.0.70 类名（F-01/F-09）  
7. **已验收 hook 禁止改**（F-31）：D1 fallback 路径、ConvFilter、ContactFilter 等  
8. **需要设备验证时**：给用户一条可复制命令，等输出，禁止假设结果  

---

## 十五、快速定位表

| 你的问题 | 先读 |
|---------|------|
| 项目在做什么 | 本文件 §一 + `CLAUDE.md` |
| 当前任务进度 | `TASK_BOARD.md` §一 |
| 某功能怎么实现 | `HOOKMAP.md` 对应 §A~F |
| 8.0.71 类名 | `docs/CLASS_MAP_8066.md`（参考）+ P 任务 worklog |
| 能不能做 X | `FAILURE_LOG.md` |
| Catfish 怎么做 | `refs/MainEntry.java` + `refs/UserControll.java` |
| C++ 接口 | `native_core/API.md` |
| 产品逻辑（VIP/隐藏/密码） | `docs/PRODUCT_GATE.md` |
| 怎么跑 build/adb | 本文件 §十二 或 terminal skill |

---

*本手册由 Cursor Agent 自动生成，基于 2026-05-22 仓库快照。细节以各源 md 为准。*
