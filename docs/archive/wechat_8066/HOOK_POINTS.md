# Hook 点完整参考 — guard_native 8.0.66

> ⛔ **ARCHIVE** — 8066 Frida 验证。8071 → [`../../HOOK_MAP_8071_AUTHORITATIVE.md`](../../HOOK_MAP_8071_AUTHORITATIVE.md)。

> **状态**：8066 历史实证；8071 禁止直搬类名
> **整理日期**：2026-05-18
> **设备**：小米9 / Android 11 / 微信 8.0.66 官方版

---

## 一、数据流全景图

```
网络响应 (SnsTimeLineResponse / 会话响应)
        │
        ▼
[F05-L1] MvvmList.m(List, boolean)   ← 朋友圈批量数据入口  ✅ 验证
[F04-L1] MvvmList.m(List, boolean)   ← 会话批量数据入口    ✅ 验证
[F04-L2] MvvmList.s(List)            ← 备用路径             ✅ 验证
[F04-L3] MvvmList.w(nd3.o0)          ← 官方隐藏事件通道     ✅ 有触发
        │
        ▼ 数据存入 MvvmList
  MvvmList
    ├── o  : ArrayList<?>      ← 主数据，直接 remove
    ├── p  : ArrayList<?>      ← 快照，同步 remove
    └── h  : ArrayList<?>      ← 8.0.65 遗留字段（filter_conv 里 h/o/p 三个全清）
        │
        ▼ Flow 下发 → Adapter
[F04-L4] f45.s0.notifyDataSetChanged()  ← 渲染前最后关卡    ✅ 验证
[F05-L4] ArrayList.addAll(Collection)   ← 实例级拦截(安装在 o/p 上)  ✅ 验证
        │
        ▼ RecyclerView / ListView 渲染
```

---

## 二、F04 — 会话隐藏（4 层拦截）

### 已验证类名（8.0.66 hardcode）

| 角色 | 类名 | 稳定性 |
|------|------|:------:|
| Adapter | `f45.s0` | ⚠️ 跨版本变 |
| 会话 item | `f45.u` | ⚠️ 跨版本变 |
| 会话 contact | `com.tencent.mm.storage.m3` | ⚠️ 跨版本变 |
| MvvmList | `com.tencent.mm.plugin.mvvmlist.MvvmList` | ✅ 稳定 |
| 会话 ListView | `com.tencent.mm.ui.conversation.ConversationListView` | ✅ 稳定 |
| Flow 类型 | `kotlinx.coroutines.flow.h2` (MutableStateFlow) | ✅ 稳定 |

### wxid 提取路径

```java
// 8.0.66: f45.u item → wxid
Field dField = uItem.getClass().getDeclaredField("d");
dField.setAccessible(true);
Object m3 = dField.get(uItem);          // com.tencent.mm.storage.m3
String wxid = m3.j1();                   // m3.j1() 返回 username string

// 8.0.71: kc5.y item → wxid  ← 动态验证 2026-05-20 ✅
// item.d = com.tencent.mm.storage.l4（iOS 对应 CContact，wxid getter m_nsUsrName）
Field dField71 = item.getClass().getDeclaredField("d");
dField71.setAccessible(true);
Object l4 = dField71.get(item);         // com.tencent.mm.storage.l4
Method c0 = l4.getClass().getMethod("C0");
String wxid = (String) c0.invoke(l4);   // l4.C0() 返回 wxid (iOS: m_nsUsrName)

// 反射通用写法（ConvFilter.java 实际使用，WXID_GETTER_NAMES 优先试 C0）：
// extractWxid(item) → 遍历 CONTACT_FIELD_NAMES("d","e",...) 找 contact，
//   再遍历 WXID_GETTER_NAMES("C0","j1",...) 调方法，isWxid() 验证格式
```

### 4 层 Hook 实现（Java 伪代码）

```java
// === L1: MvvmList.m(List, boolean) — 批量数据入口 ===
// 在入参 List 里直接 remove 目标 wxid 的 item
XposedHelpers.findAndHookMethod(
    "com.tencent.mm.plugin.mvvmlist.MvvmList", lpparam.classLoader,
    "m", List.class, boolean.class,
    new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            List list = (List) param.args[0];
            filterConvList(list);   // remove 目标
        }
    }
);

// === L2: MvvmList.s(List) — 备用路径 ===
XposedHelpers.findAndHookMethod(
    "com.tencent.mm.plugin.mvvmlist.MvvmList", lpparam.classLoader,
    "s", List.class,
    new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            List list = (List) param.args[0];
            filterConvList(list);
        }
    }
);

// === L4: f45.s0.notifyDataSetChanged() — 渲染前最后关卡 ===
// 在 notify 之前清洗 h/o/p 三个字段
XposedHelpers.findAndHookMethod(
    "f45.s0", lpparam.classLoader,
    "notifyDataSetChanged",
    new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            if (g_cleaning) return;
            Object mvvmList = getFieldValue(param.thisObject, "p"); // s0.p = MvvmList
            cleanMvvmListFields(mvvmList); // 清洗 h/o/p
        }
    }
);
```

### INIT 清理（warm-attach）

```java
// 找到 ConversationListView → getAdapter() → 清洗 + notifyDataSetChanged
// 延迟 2000ms 执行（等界面稳定）
new Handler(Looper.getMainLooper()).postDelayed(() -> {
    // Java.choose 等价: 遍历 ConversationListView 实例
    // 清洗完调 notifyDataSetChanged
}, 2000);
```

### weixin 特殊处理

```java
// weixin 条目有未读消息时保留（服务通知等）
if ("weixin".equals(wxid)) {
    int unread = getFieldInt(m3, "field_unReadCount");
    if (unread > 0) continue; // 保留
}
```

---

## 三、F05 — 朋友圈隐藏（实例级 ArrayList 拦截）

### 已验证类名（8.0.66 hardcode）

| 角色 | 类名 | 稳定性 |
|------|------|:------:|
| Adapter | `com.tencent.mm.plugin.sns.ui.improve.component.y1` | ⚠️ 跨版本变 |
| y1 持有 MvvmList 的字段 | `H` (field name) | ⚠️ 跨版本变 |
| 朋友圈 item | `k24.b` | ⚠️ 跨版本变 |
| SnsObject item 内的 contact 字段 | `d` (field on k24.b) | ⚠️ 跨版本变 |
| SnsObject protobuf | `com.tencent.mm.protocal.protobuf.SnsObject` | ✅ 稳定 |
| field_userName 字段名 | `field_userName` (protobuf generated) | ✅ 稳定 |

### wxid 提取路径

```java
// k24.b item → wxid
if (!"k24.b".equals(item.getClass().getName())) return null;
Field dField = getFieldRecursive(item, "d");    // SnsObject 类型
String wxid = getFieldString(dField, "field_userName"); // protobuf 生成字段，稳定
```

### 核心：实例级 ArrayList.addAll hook

```java
// 找到 y1 adapter → 拿到 MvvmList → 拿到 o/p ArrayList 实例
// 在 o/p 实例上 hook addAll，不是在类级别 hook
// 这样只拦截朋友圈的 ArrayList，不影响全局

// 等价 Java 思路（LSPosed 里用 Pine 做实例 hook 或反射替换）：
Object adapter = findInstance("com.tencent.mm.plugin.sns.ui.improve.component.y1");
Object mvvmList = getField(adapter, "H");
Object listO = getField(mvvmList, "o");   // ArrayList
Object listP = getField(mvvmList, "p");   // ArrayList

// 然后在这两个 ArrayList 实例的 addAll 前过滤
```

> ⚠️ **关键约束**：不能在 RecyclerView active state 时调用 cleanList（会崩溃）
> 只在 addAll 被调用时（数据即将进入）过滤，不做主动清理

### 防替换扫描

```java
// MvvmList 内部 ArrayList 可能被替换（hashCode 变化）
// 每 3 秒检查 o/p 的 hashCode，变了就重新安装 hook
// 同样不做 clean — 替换时 RecyclerView 也在 active state
```

---

## 四、跨版本稳定性分析

```
稳定（跨版本可 hardcode）：
  ✅ com.tencent.mm.plugin.mvvmlist.MvvmList  — 类名不变
  ✅ MvvmList.m(List, boolean)               — 方法签名不变
  ✅ MvvmList.s(List)                         — 方法签名不变
  ✅ com.tencent.mm.ui.conversation.ConversationListView
  ✅ com.tencent.mm.protocal.protobuf.SnsObject
  ✅ SnsObject.field_userName                 — protobuf 生成，稳定
  ✅ kotlinx.coroutines.flow.h2 (MutableStateFlow 类型)

不稳定（每版本需重新找）：
  ❌ f45.s0           — Adapter 类名
  ❌ f45.u            — 会话 item 类名
  ❌ com.tencent.mm.storage.m3 — contact 类名
  ❌ y1               — 朋友圈 Adapter（全路径 y1）
  ❌ k24.b            — 朋友圈 item 类名
  ❌ MvvmList.o/p/h   — 内部 ArrayList 字段名
  ❌ MvvmList.h2/m2   — StateFlow 字段名（8.0.65 是 s/v，8.0.66 是 h2/m2）
```

**运行时定位不稳定类的策略**：
1. 从 `ConversationListView.getAdapter()` 反射找 `f45.s0` 等价类
2. 从 `MvvmList` 实例按类型找字段（类型 `kotlinx.coroutines.flow.h2` 稳定）
3. `k24.b` 从 MvvmList.o/p 列表取 item，检查 `field_userName` 字段存在性确认类名

---

## 五、iOS ↔ Android 数据流对照（研究参考）

| 层 | iOS（零混淆） | Android（8.0.66） | 稳定性 |
|----|:-------------|:-----------------|:------:|
| L1 数据转换 | `WCTimelineDataProvider.-converListToList:` | `MvvmList.m(List, boolean)` | ✅ |
| L1 item 创建 | `WCDataItem.+fromServerObject:` → return nil | 工厂方法（**未找到**） | ❓ |
| L2 item 存储 | VC 某 ivar（未找到） | `MvvmList.o/p` ArrayList | — |
| L3 UI 渲染 | `tableView:cellForRowAtIndexPath:` | RecyclerView `onBindViewHolder` | — |
| wxid 读取 | `item.valueForKey_("username")` KVC | `SnsObject.field_userName` 反射 | ✅ |
| 过滤方式 | `fromServerObject:` return nil（不创建） | `jlist.remove(i)` 原地 mutate | — |

> **iOS 优势**：从 `fromServerObject:` 返回 nil，item 根本不进入数组，最干净
> **Android 现状**：没找到等价工厂方法，用 MvvmList.m 入参 remove，已验证可行
> **待探索**：`probe_k24b_factory.js` 可以找到 `k24.b` 构造调用栈，找到工厂方法后可实现更干净的 L0 拦截

---

## 六、禁止方案速查（15 个已验证失败）

| 方案 | 失败原因 |
|------|---------|
| notifyItemRange* 任何变体 | DiffUtil position 错位 → 崩溃 |
| Hook Adapter 层（K0/getCount/getView）| Kotlin Flow 覆盖 / 死循环 |
| 改 SparseArray Key | 非连续 Key → 遍历异常 |
| View.GONE 做主方案 | ViewHolder 复用污染 + 点击穿透 |
| Java 反射 invoke notifyDataSetChanged | ART SIGSEGV |
| 在 hook 异步回调中捕获 `this` | JNI local ref GC → SIGABRT |
| 改 RecyclerView 后段 position | 数据错位 |

> 完整失败档案 → `refs/FAILURE_LOG.md`

---

## 七、guard_native 实现优先级

```
Phase 2a — 最先做（最小可验证）:
  → F04 L1: MvvmList.m hook + filterConvList
  → 验证：指定 wxid 会话消失

Phase 2b — F04 完善:
  → L4: notifyDataSetChanged cleanS0
  → INIT: warm-attach 初始清理
  → weixin 特殊处理

Phase 3 — 朋友圈:
  → F05: y1 adapter 扫描 + o/p addAll hook
  → 先用 3s 定时扫描（等 Adapter 出现）
  → 验证：指定 wxid 朋友圈不出现
```

---

*本文档由 filter_conv.js v1 + filter_moments.js v12 动态验证结果整理*
*Frida 脚本原件 → `refs/filter_conv.js` + `refs/filter_moments.js`*
