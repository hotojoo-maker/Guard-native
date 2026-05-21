# 架构师任务提示词 — 调试控制台 V2（结构化遥测）

> **用法**：把本文全文复制给架构师 Agent，无需额外上下文。  
> **项目**：Guard Native · 微信 8.0.71 · LSPosed · 小米9  
> **路线**：`adb forward tcp:8080 tcp:8080` → 浏览器 `http://localhost:8080`（已有 NanoHTTP `DebugServer`）

---

## 一、任务目标

在**现有 Web 调试控制台**上升级 **V2 结构化遥测**，用于找 hook 点、验证过滤、排查红点/气泡泄露。

**比 logcat 强**：浏览器里实时看到「当前页面 + 动态变化事件 + wxid 身份 + 分通道指标」，而不是一堆无结构文本。

**不要**：
- 新建独立 App / 不用 adb forward 的另一套方案
- 引 native（JniHook / Pine / shadowhook）
- PROD 模式开 UI 扫描（仅 DEV/HONEY）
- 全局 hook `ArrayList.add` 刷屏不加分类

---

## 二、必读文件（动手前读完）

| 优先级 | 路径 | 用途 |
|:--:|------|------|
| P0 | `docs/DEBUG_CONSOLE_V2.md` | V2 规格（已写，可增补） |
| P0 | `src/main/java/com/ghost/assist/debug/DebugServer.java` | HTTP 路由 |
| P0 | `src/main/assets/debug/index.html` | 浏览器 UI |
| P0 | `src/main/java/com/ghost/assist/debug/DebugTelemetry.java` | **Phase0 已落地**，可扩展 |
| P0 | `src/main/java/com/ghost/assist/debug/UiContextTracker.java` | **Phase0 已落地** |
| P1 | `src/main/java/com/ghost/assist/moduleD/MomentsFilter.java` | 朋友圈 hook，需接 emit |
| P1 | `src/main/java/com/ghost/assist/moduleD/ConvFilter.java` | 会话 hook，需接 emit |
| P1 | `src/main/java/com/ghost/assist/core/Bridge.java` | 密友名单 `getWxids()` |
| P2 | `CLAUDE.md` §5.8 / `FAILURE_LOG.md` | 铁律 |
| P2 | `03_execute_执行任务/P16_朋友圈Proto/result.md` | 8.0.71 类名速查 |

---

## 三、产品需求（必须实现的控制台能力）

### 3.1 当前界面面板

打开朋友圈时应显示类似：

```json
{
  "page": "Moments",
  "activity": "com.tencent.mm.plugin.sns.ui.SnsTimeLineUI",
  "tab": "",
  "visible_feed_count": 8,
  "visible_wxids": [{"wxid":"wxid_xxx","nick":"…","hidden":false}]
}
```

还要支持（逐步）：
- 当前 Activity / Fragment 类名
- 页面标题（Activity.getTitle）
- 底部 Tab（发现/微信/通讯录/我）— 可启发式或 hook LauncherUI
- 当前可见 TextView 摘要（采样，勿全量扫爆）
- RecyclerView 可见 item 数量（hook adapter getItemCount 快照）

### 3.2 红点 / 气泡面板（第二优先）

重点抓**变化**，不是静态截图：

| 字段 | 说明 |
|------|------|
| `red_dot_visible` | true/false |
| `badge_count` / `unread_count` | 数字 |
| `tip_text` | 如「3条新消息」 |
| `bubble_view_class` | View 类名 |
| `bubble_parent_page` | Moments / Discover / Conv |
| `sourceWxid` | 谁触发的 |
| `type` | like / comment / update |
| `bubble_show_time` / `bubble_hide_time` | 出现/消失时间 |

目标事件示例：

```
06:48:20  channel=badge  kind=appear
  page=DiscoverTab  sourceWxid=wxid_xxx  type=comment  matched=true  visible=true
```

### 3.3 动态变化事件（第三优先）

有价值的变化类型：

- List size：`0 → 3`
- 字段：`null → wxid_xxx`
- View：`invisible → visible`
- TextView：`"" → "3条新消息"`
- badge count：`0 → 1`
- `LinkedList.add` 加入 `z15.e56` / `cs5.di0`（8.0.71 赞评）
- `ArrayList.addAll` 仅对 **目标类** 上报，非全局噪音

### 3.4 身份字段（第四优先）

出现即记录：`wxid` `username` `field_userName` `nickname` `remark` `alias` `snsId` `type` `commentType` `content` `refUserName`

**高价值组合**：`wxid + type + page`  
例：`wxid=xxx, type=1, page=Moments` → 点赞；`type=2` + content → 评论。

### 3.5 功能结果计数（第五优先，必须分离）

每通道独立计数，**禁止混用**：

| 指标 | 含义 |
|------|------|
| `seen` | 看见对象次数 |
| `parsed` | 解析出 wxid 成功 |
| `matched` | wxid 在密友名单 |
| `blocked` | 代码阻断（setResult/remove） |
| `removed` | 从集合删除成功 |
| `uiHidden` | 屏幕肉眼不可见（后续 UI 探测） |
| `dropped` | 队列满/限速丢弃 |

**铁律**：`matched ≠ blocked ≠ uiHidden`。控制台禁止只显示一个总数误导用户。

---

## 四、浏览器六面板（index.html）

| # | 面板名 | 数据源 |
|---|--------|--------|
| 1 | 当前界面 | `/api/telemetry` → `page` |
| 2 | 红点/气泡 | `channel=badge` events |
| 3 | 朋友圈 | `channel=moments` + metrics |
| 4 | 赞评 | `channel=like_comment` |
| 5 | 会话/通讯录 | `channel=conv` + `channel=contact` |
| 6 | 噪音 | `channel=noise`（可折叠） |

左栏已有 **当前界面 V2** 雏形；请扩成 Tab 或分栏，轮询 500ms。

保留现有 **addAll 流水** 作考古；主分析用结构化 API。

---

## 五、技术架构（已定，可演进）

```
Hook 层 → DebugTelemetry.emit(channel, kind, fields)
       → DebugTelemetry.incBlocked / incRemoved
UI 层   → UiContextTracker (Activity.onResume)
       → [待做] BadgeScanner (主线程 DecorView 启发式，2s 间隔)
HTTP    → DebugServer /api/telemetry + /api/telemetry/events?channel=
浏览器  → index.html 六面板
```

### 事件通道 `channel`

| channel | 用途 |
|---------|------|
| `page` | Activity 进入 |
| `badge` | 红点/气泡 |
| `moments` | 朋友圈 feed |
| `like_comment` | LinkedList.add 赞评 |
| `conv` | 会话 MvvmList |
| `contact` | 通讯录 |
| `noise` | 低价值跳过 |

### 噪音规则（进 noise，不进主面板）

类名含：`WeakReference` `TextView` `AppBrand` `Autofill` `music` `banner` `wq.` `Emoji` `Glide`  
包前缀：`java.` `android.` `androidx.` `kotlin.`

**8.0.71 高价值类**（参考 P16 result.md）：

- 朋友圈：`na4.b` `la4.p`
- 赞评：`z15.e56` `cs5.di0` `i84.y`
- 气泡：`com.tencent.mm.plugin.sns.ui.SnsMsgUIWithRelevance` `jw1.d`
- 会话：`kc5.*` `MvvmList` `f45.s0`（71 需运行时诊断）

`DebugTelemetry.channelForClass(String cn)` 已提供分类入口。

---

## 六、实施分期（架构师按序交付）

| 阶段 | 交付物 | 验收 |
|:--:|--------|------|
| **P0** ✅ | DebugTelemetry + UiContextTracker + `/api/telemetry` + index 左栏 | curl 有 page；进朋友圈 page=Moments |
| **P1** | MomentsFilter + ConvFilter 关键路径 `emit` | blocked 时 metrics.blocked++；事件带 wxid+page |
| **P2** | BadgeScanner + badge 面板 | 密友点赞后 badge 事件含 sourceWxid |
| **P3** | index.html 六面板 Tab UI | 500ms 刷新；可按 channel 过滤 |
| **P4** | getItemCount 快照 → visible_feed_count | 与屏幕条数大致一致 |

---

## 七、Hook 层接入示例（P1 必做）

在 `MomentsFilter` 的 `LinkedList.add` 阻断处：

```java
if (isHiddenListEntry(item, hidden)) {
    param.setResult(true);
    String wxid = extractWxidFromEntry(item); // 已有逻辑
    DebugTelemetry.getInstance().emit("like_comment", "lladd_blocked",
        DebugTelemetry.fields("wxid", wxid, "className", cn, "page", "Moments"));
    DebugTelemetry.getInstance().incBlocked("like_comment");
}
```

在 D1 `removed` 处：

```java
DebugTelemetry.getInstance().incRemoved("moments", removed);
// emit 带 posterWxid
```

**门控**：仅 `StateMachine.isVipMode() && !hidden.isEmpty()` 时计 matched/blocked。

---

## 八、API 契约

### `GET /api/telemetry`

```json
{
  "page": {
    "activity": "...",
    "page": "Moments",
    "tab": "",
    "visible_feed_count": 8,
    "visible_wxids": [{"wxid":"...","nick":"...","hidden":false}],
    "ts": "12:34:56.789"
  },
  "metrics": {
    "moments": {"seen":10,"parsed":8,"matched":2,"blocked":1,"removed":3,"uiHidden":0,"dropped":0},
    "like_comment": { ... }
  },
  "events": [ {"ts":"...","channel":"badge","kind":"appear","fields":{...}} ]
}
```

### `GET /api/telemetry/events?channel=badge&limit=100`

返回 `{ "events": [ ... ] }`

---

## 九、铁律与约束

1. **仅 DEV/HONEY** 安装 `UiContextTracker` / `BadgeScanner`（见 `ModuleMain.startDebugTools`）
2. **catch (Throwable)**，禁止裸 `catch (Exception)` 吞 NoSuchMethodError
3. **禁止** `notifyItemRange*`、hook Adapter getCount/getView 做主方案（FAILURE_LOG）
4. **禁止** 敏感词进类名：vip/hide/catfish/wechat
5. **不修改** APK/DEX/smali
6. 主进程白名单 `com.tencent.mm` 已存在，勿破坏
7. 事件 ring buffer 上限（建议 800 总 / 200 per channel），超限 `dropped++`
8. 单 kind 限速（已有 30ms），防刷屏

---

## 十、验收清单（架构师自检）

- [ ] DEV 模式，`adb forward` 后浏览器可开
- [ ] 进入朋友圈 → `/api/telemetry` 中 `page.page` = `Moments`
- [ ] 密友点赞 → `like_comment.blocked` 增加，且 `matched` 仅在名单非空时增加
- [ ] `noise` 通道有 dropped/skip，主面板不刷 TextView 垃圾
- [ ] PROD 模式不注册 UiContextTracker（KPI 无额外噪声）
- [ ] 文档更新 `docs/DEBUG_CONSOLE_V2.md` 变更记录
- [ ] 不改 HOOKMAP 业务状态，仅 debug 设施

---

## 十一、输出要求（交给调度员）

1. **设计摘要**（1 页）：数据流图 + 类职责
2. **改动文件清单**
3. **P1~P4 提交顺序** + 每步验收命令
4. **未解风险**（如 badge 无稳定 hook 点时的 Plan B）
5. 不要写空话；**8.0.71 类名以 P16/P17 result 为准**，勿假设 parseFrom 可用（F-27 已死）

---

## 十二、一句话任务

> 在 Guard Native 现有 HTTP 调试台（8080）上，以 `DebugTelemetry` 为核心，实现「页面上下文 + 分通道动态事件 + seen/parsed/matched/blocked/removed 分离指标 + 六面板浏览器 UI」，并把 `MomentsFilter`/`ConvFilter` 的关键拦截点接入；第二优先打通红点/气泡生命周期，支撑 L0v3 小红点 hook 调研。

---

*Phase0 代码已存在于仓库，架构师从 P1 接力即可；若重构请先读现有 `DebugTelemetry.java` 再扩展，避免重复造轮子。*
