# 调试控制台 V2 — 结构化遥测（找 hook 点专用）

> 路线不变：`adb forward tcp:8080 tcp:8080` → 浏览器 `http://localhost:8080`  
> 目标：比 logcat 强 — **当前页面 + 动态变化事件 + 身份字段 + 分通道指标**

---

## 一、现状 vs 目标

| 已有 | 缺口 |
|------|------|
| `/api/state` `/api/counters` `/api/rawfeed` | 无「当前在哪个页面」 |
| `addRawFeedLine` 文本流水 | 无结构化 wxid/type/page |
| F04/F05 总计数 | 无 seen/parsed/matched/blocked/removed 分离 |
| item dump 按类名 | 无红点/气泡生命周期 |
| 左右两栏原始日志 | 无分面板过滤噪音 |

---

## 二、架构（三层）

```
Hook 层 (MomentsFilter / ConvFilter / …)
    ↓ DebugTelemetry.emit(channel, kind, fields)
    ↓ DebugTelemetry.metric(channel).blocked++

UI 层 (UiContextTracker)
    ↓ Activity.onResume → page=Moments|Conv|Address|…
    ↓ 可选：主线程轮询 DecorView → badge 启发式

HTTP 层 (DebugServer)
    ↓ GET /api/telemetry → JSON 全量快照
    ↓ GET /api/telemetry/events?channel=badge&limit=100

浏览器 (index.html)
    ↓ 6 面板轮询 500ms~1s
```

**原则**：抓**变化**，不抓静态全字段；**matched ≠ blocked ≠ uiHidden**。

---

## 三、事件通道（channel）

| channel | 用途 | 典型 kind |
|---------|------|-----------|
| `page` | 当前界面 | `enter` `leave` |
| `badge` | 红点/气泡 | `appear` `hide` `count_change` |
| `moments` | 朋友圈 feed | `addAll` `d1_remove` `feed_seen` |
| `like_comment` | 赞评 LL.add | `lladd_seen` `lladd_blocked` |
| `conv` | 会话 | `mvvm_m` `notify_clean` `conv_seen` |
| `contact` | 通讯录 | `address_check` `list_filter` |
| `noise` | 低价值跳过 | `skip` |

---

## 四、指标（每 channel 独立）

| 字段 | 含义 |
|------|------|
| `seen` | 看见对象/事件次数 |
| `parsed` | 成功解析出 wxid |
| `matched` | wxid 在密友名单 |
| `blocked` | 代码层阻断（setResult/remove） |
| `removed` | 从集合删除成功 |
| `uiHidden` | 屏幕肉眼不可见（难，后续 UI 探测） |
| `dropped` | 因限速/队列满丢弃的日志条数 |

---

## 五、身份字段（emit 时尽量带齐）

高价值组合：**wxid + type + page**

| 字段 | 说明 |
|------|------|
| `wxid` | wxid_ / gh_ |
| `type` | like / comment / post / update / conv |
| `page` | Moments / Conv / Address / Discover / Profile |
| `className` | 混淆类名 |
| `listSize` / `delta` | 0→3 这类变化 |
| `sourceWxid` | 红点触发者 |
| `contentPreview` | 评论摘要前 30 字 |
| `matched` | true/false |

---

## 六、噪音规则（默认不进主面板，进 noise 通道）

跳过类名含：`WeakReference` `TextView` `AppBrand` `Autofill` `music` `banner` `wq.` `Emoji` `Glide`  
跳过：`java.` `android.` `androidx.` `kotlin.`

**addAll 流水**：只提升 `na4.b` `la4.p` `SnsMsg` `jw1` `z15.e56` `cs5.di0` `kc5` 等到对应 channel；其余 → `noise`。

---

## 七、浏览器六面板

1. **当前界面** — Activity、推断 page、Tab、标题片段、可见 Text 采样  
2. **红点/气泡** — badge_visible、count、sourceWxid、parent_page、appear/hide 时间线  
3. **朋友圈** — feed_seen、visible_wxids、d1_removed、addAll_sz  
4. **赞评** — lladd_seen/blocked、wxid、type、contentPreview  
5. **会话/通讯录** — conv_seen、contact wxid、F04/F07 指标  
6. **噪音** — dropped + 最近 skip 类名（可折叠）

---

## 八、实施分期

| 阶段 | 内容 | 工期 |
|:--:|------|:--:|
| **P0** ✅ | `DebugTelemetry` + `UiContextTracker` + `/api/telemetry` | 1 天 |
| **P1** | MomentsFilter/ConvFilter 改 `emit` 替代部分 rawFeed | 1 天 |
| **P2** | Badge 启发式（DecorView 轮询 + 未读 Text 匹配） | 2 天 |
| **P3** | index.html 六面板 UI | 1 天 |
| **P4** | RecyclerView 可见 item 数（hook getItemCount 快照） | 1 天 |

---

## 九、用法

```bash
adb forward tcp:8080 tcp:8080
# 浏览器打开 http://127.0.0.1:8080
# 新接口：
curl http://127.0.0.1:8080/api/telemetry
curl "http://127.0.0.1:8080/api/telemetry/events?channel=badge&limit=50"
```

模块须 **DEV 或 HONEY** 模式（`AppConfig.isDebugEnabled()`）才安装 UI 追踪，避免 PROD KPI 噪声。

---

## 十、防护驾驶舱 + `/api/native`（2026-06-02 新增）

> 防破解/防盗版的「看得见」层。设计与阶段总账见 [`../PROTECTION_MAP.md`](../PROTECTION_MAP.md)。

**新接口** `GET /api/native`（DebugServer）——只吐**状态枚举，绝不吐密钥/配方/租约原文**：

| 字段 | 含义 | 来源 |
|------|------|------|
| `soLoaded` | libguardcore.so 是否加载 | `NativeBridge.isAvailable()` |
| `role` | 进程角色 MAIN/PUSH/… | `nativeGetProcessRole` |
| `authState` | 授权态 OK/UNKNOWN/TAMPERED/… | `nativeGetAuthState` |
| `risk` | 风险态 NONE/PIRATE/… | `nativeGetRiskState` |
| `configVersion` | 配方版本 | `nativeGetConfigVersion` |
| `leaseValid`/`decryptOk`/`tampered` | Phase 1/2 占位（现 null/false） | 待接入 |

**浏览器**：左栏顶部「守护内核 · 防护驾驶舱」卡——Java 层（状态机/开关/名单）与 Native·SO 层（SO/授权/防篡改/配方）**分栏并列** + 蜜罐行 + 防破解阶段路线图。

**桌面**：`tools/guard_status.cmd`（双击）/ `tools/guard_status.ps1`——adb forward + 控制台版同款状态面板（与 web 面板二选一）。

> ⚠️ 同 §九：整套调试台**仅 DEV/HONEY**，PROD/release 必须关。Phase 0 待收口：`ModuleMain` 的 `DebugServer.start()` 当前**未 gate**（无条件启动），需改成 `isDebugEnabled()` 才启。
