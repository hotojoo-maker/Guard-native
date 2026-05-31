# CONV_REFRESH_PROBLEM — 会话列表 H↔V 状态切换无法立刻显示密友

> 创建：2026-05-24
> 最后更新：2026-05-27（v27 装机收口）
> **核心问题：切换显隐状态（H↔V）后，密友会话无法立刻出现在列表里**
> 关联：HOOKMAP §L1·V↔H、ConvFilter.java、ConvHotReload.java

---

## ⚡ 0. 新接手的 AI / 维护者：只看 §十七

- **当前线上方案** = §十七（v27 = v24 行为 + 80ms post-dedup + IK3n install）
- §一~§十六 全是 2026-05-24~05-27 期间的探索 / 失败路线 / 中间方案，**只是历史防绕圈**
- 想动 ConvFilter / ConvHotReload V↔H 相关逻辑前必须：
  1. 读完 §十七
  2. 再读 [`../FAILURE_LOG.md`](../FAILURE_LOG.md) F-33 / F-34 / F-35
  3. 不动手只问用户

---

## 一、原始问题与早期分析（已归档，由 §十二~§十七 取代）

> **本节及 §二/§四/§五/§六/§七 为 2026-05-24 之前对「H→V 不能立刻显示」的探索讨论，结论已被 §十二（warmColdFriends ✅）、§十四（v24 hidden-id-as-key ✅）、§十三（L4adapter.q.d ✅）取代。新会话直接读 §十~§十六，本节仅作历史防绕圈。**

要点（精简版）：
- V→H：早期已稳定（`sPendingHide` + clean + notify）— 现仍有效，并由 §十三 补 L4adapter.q.d 压制 H 态新消息
- H→V：早期判断"冷路径不可解"，依赖密友"近期有消息"才能注回。该判断**已被 §十二 推翻** —— `kc5.x.h(id)` 可主动 fresh-warm 冷友 `kc5.y`，冷路径同样 ✅ 立刻显示
- `restoreCachedItems`（stale 对象强插）已永久废弃，见 §十

---

## 三、（已删除）2026-05-23 为什么"曾经好用"
## 四、（已删除）正确方向：方案 A/B/C 对比
## 五、（已删除）两个可能的主动触发时机
## 六、（已删除）Frida 探针结论 / WeChat 数据通道全链路
## 七、（已删除）确定方案：ik3.n.handleEvent(List) 回放（方案 C）

> **2026-05-27 集中删除（误导防绕圈）**：
>
> 上述五节为 2026-05-24 探索阶段产物。原推荐方案 C「ik3.n.handleEvent(List) 回放」**已在 v22 / v25 前后两次装机 0 命中**（冷启动不触发，Kotlin coroutine 路径绕开 Java hook），被 §十 永久禁止。
>
> 现行 H→V 立刻显示方案 = §十二 字段遍历 + `kc5.x.h(wxid)` fresh-warm（✅ 装机），无需 ik3/MvvmList.e/k/l 任何 hook。
>
> 现行 V↔H 全量热切 = §十四 `expandCacheWithWarm(hidden id 作 key)` + 双写双清（v24 ✅，v25/v26 残留见 §十五/§十六）。
>
> WeChat 数据通道完整链路图原文（fc5.d → h45.i → ik3.n → cl0.u → ik3.m → MvvmList.w → MvvmList.e → notifyDataSetChanged）在 git 历史可查；当前不再作为 hook 候选。
>
> ContactFilter 在 `MvvmAddressUIFragment.onResume` 上 hook 的修复也已完成（见 `moduleD/ContactFilter.java`）。

---

## 八、2026-05-24 第二轮实证——sConvItemMap 累积方案

### 架构思路

用 `ConcurrentHashMap<wxid, kc5.y>` 累积所有 addAll 批次的会话对象，避免单次 snapshot 被后续批次覆盖。BUS-V 时 `MvvmList.n(map.values(), false)` 原子更新 StateFlow。

### 逐一探索记录

| 轮次 | 尝试 | 结果 | 根因 |
|------|------|------|------|
| R1 | `sLastAddAllSnap` 单快照（只保留 `removed>0` 批次） | 快照 sz=14，密友不在 | 后续 clean 批次（sz=14）覆盖了含密友的批次（sz=15） |
| R2 | `sConvItemMap` 替换 snapshot，L0addAll 累积 | map sz=12，密友不在，无崩溃 | L0addAll 的类名检查 `kc5.y` 通过，但密友压根不在这 15 批 |
| R3 | 加 L1（MvvmList.n hook）累积 | `[CF:L1broad] = 0 hits`，密友仍不在 | L1 冷启动不触发 |
| R4 | 加 La（kc5.a.a hook）累积 | `[CF:La] = 0 hits` | La 冷启动不触发（只在热更新/收消息时触发） |
| R5 | 加 Lh0（h0 adapter hook）累积 | `Lh0 no List/Collection method on h0` | h0 没有可 hook 的 List 参数方法 |

### 诊断结论：密友冷启动时根本不在任何可 hook 的数据结构里

```
BUS-V map wxids=[12个非密友 wxid]
hidden=[wxid_lzd2va16jd1622, wxid_2irmyucg5n3122, ...]
→ 密友不在 map 里
```

- La / L1 / L0addAll / Lh0：全部 0 hit 或 removed=0
- 密友会话**未被我们任何 hook 过滤**，说明 WeChat 冷启动时根本没把密友的会话加载进内存
- 密友的会话条目在 DB 里，但不在 WeChat 冷启动加载的"最近会话"窗口（top N）内

### 为什么"发一条消息就好了"

```
发消息 → La (kc5.a.a) 热更新触发
       → filterConvList 在 H 模式过滤密友
       → sConvItemMap.put(wxid, kc5.y对象) ← 此时才第一次捕获到密友对象
       → 下次 BUS-V → MvvmList.n(map.values()) → 密友出现 ✅
```

这是**正确的热路径**。发消息后，后续每次 H→V 切换都能正常显示。

### 冷启动限制（根本性）

WeChat 的会话列表只加载 top N 最近联系人。如果密友长时间未消息，冷启动时不在 top N：
- 无 kc5.y 对象可捕获
- 无法通过我们的 map 方案恢复
- 这不是 bug，是 WeChat 数据模型的固有限制

---

## 九、当前实际状态（2026-05-27 更新 / v26 装机后）

> **⚠️ 注意**：v25/v26 尝试修密群多拍但反而打破渲染，**当前线上代码 = v26**，V↔H 已**回退到不工作**状态（BUS-V-adapter.p 被 identity check 拦致 fresh item 进了 list 但 RecyclerView 不渲染）。

| 场景 | H 态能入会话列表 | H→V 能立刻同时显示全部隐藏对象 | 说明 |
|------|:--:|:--:|------|
| H 态密友/密群新消息 | ❌ | — | §十三 `L4adapter.q.d` removed ×2 L1 实证（继续生效） |
| 密友近期有消息（热） | — | **v24 ✅ / v26 ❌ 不显示** | v26 identity check 后 BUS-V-adapter.p injected=0，需 v27 |
| 密友长期无消息（冷） | — | **v24 ✅ / v26 ❌ 不显示** | 同上 |
| **密友 + 密群同时隐藏** | — | **v24 ✅ / v26 ❌ 不显示** | §十四 v24 ✅；v25/v26 退化见 §十六 |
| V→H 同时隐藏 | — | ✅ | 与 H→V 对称（V→H 路径仍 OK） |
| 零历史 wxid（从未聊过） | — | ❌ | `h(id)→null`，WeChat DB 无 l4；需另开「创建会话行」任务 |
| 通讯录 H→V | — | 🟡 待验证 | ContactFilter onResume 路径 |

**v26 当前问题（待 v27 修）**：identity check 拦了 `restoreToMvvmList` 之后 `restoreAdapterGraphFromCache` 扫到 adapter.p 时的重复注入，造成 RecyclerView 不渲染。详见 §十六。

**未解决的密群多拍残留（v24 时观察到）**：见 §十五（v26 已"用打破渲染换掉多拍"，本质未解决）。

---

## 十、禁止再做的事（最终版）

- ❌ **禁止再动 `restoreCachedItems`**（stale JNI ref，WeChat ViewModel 静默拒绝渲染）
- ❌ **禁止再尝试 ik3.n.handleEvent / MvvmList.e / MvvmList.n 作为 BUS-V 数据源**（冷启动均不触发）
- ❌ **禁止在 Lh0 上加 hook**（无 List/Collection 参数方法）
- ❌ **禁止 `kc5.x.k(0, wxid)` 直接 invoke**（Kotlin 协程 dispatcher 不介入，空壳）
- ❌ **禁止 hook kc5.x.d() via lpparam.classLoader**（classloader 分裂，shadow class，永不触发）
- ❌ **禁止 hook kc5.y ctor 做 bootstrap**（classloader 分裂双向阻隔，同上）
- ❌ **禁止 Activity.recreate()**（有黑屏，且已有更好方案）
- ✅ **已解决**：`findXFromConvGraph()` 字段遍历 + `kc5.x.h(wxid)` fresh item 注入（见 §十二）

---

## 十一、探索时间线（AI 接手防绕圈）

> 2026-05-25 完整调研过程，每个方向失败原因已锁定。

| 时间线 | 方向 | 失败原因 | 证据 |
|--------|------|---------|------|
| **阶段 0** | stale cache 注回（restoreCachedItems） | WeChat ViewModel 拒绝过期 JNI ref；群聊 stale ok，好友 stale 静默跳过 | P25 装机 `used stale cache` × 6，好友不显示 |
| **阶段 1** | `kc5.x.k(0, wxid)` 直接 invoke | Kotlin 协程状态机，直接调只跑 switch 空壳，h/d/ctor 均 0 触发 | Frida：invoke ok 但 fired=0 |
| **阶段 2** | `kc5.x.d()` hook via lpparam | lpparam.classLoader = LSPosed 影子类；WeChat 运行时用 Tinker classloader 另一份 | hooks=1 但 d() called 从未出现 |
| **阶段 3** | `kc5.y.<init>()` bootstrap hook | 同上，classloader 分裂双向；y-ctor 也装在影子类上 | installed 但 bootstrap cl= 从未出现 |
| **阶段 4** | 字段遍历 `kc5.v0 → kc5.x` + `h(wxid)` | **✅ 成功** — 从 L4 已有 live kc5.v0 出发，不经 lpparam；h() 主线程安全，产出 fresh y | `xFind: kc5.x found`、`warm: injected=3`、好友立刻显示 |

**为什么群聊 stale ok、好友 stale 不 ok？**
群聊 kc5.y 对象的内部字段（可能是时间戳/版本/引用）在 stale 时仍满足 WeChat 渲染条件；
好友 kc5.y 有某个必填字段在 stale 时失效（具体字段未深挖，已无必要）。

**为什么字段遍历能绕过 classloader 分裂？**
不从 lpparam 加载任何类——从 L4 hook 已捕获的 `kc5.v0` live 实例出发，
沿字段图走到 `kc5.m`（`f286214b`）→ `kc5.x`，全程在 Tinker classloader 内，天然互通。

---

## 十二、最终方案（✅ 2026-05-25 装机验证）

### 实现位置
`ConvHotReload.java` — `findXFromConvGraph()` + `warmColdFriends()`

### 验证日志（L1）
```
[CF:xFind] kc5.x found via field walk: kc5.x
[CF:xFind] h() resolved
[CF:warm:BUS-V] h(wxid_lzd2va16jd1622) → y ok
[CF:warm:BUS-V] warm=1 skipped(hot)=0
[CF:warm:BUS-V] injected=3
[BUS:BUS-V-done] notified adapter=v0
```

### BUS-V 完整流程
```
BUS-V 触发
  └─ warmColdFriends("BUS-V")
       ├─ findXFromConvGraph()  → kc5.v0 字段遍历 → kc5.m.f286214b → kc5.x ✅
       ├─ x.h(wxid) per 冷友   → fresh kc5.y（非 stale）✅
       ├─ restoreToMvvmList()  → 注入 o/p/h 三个 backing array ✅
       └─ notifyConvAdapter()  → adapter 渲染 ✅
  └─ triggerFreshConvReload()  → r0.d（热友补充刷新）
```

### 零历史好友（从未聊过）
`h(wxid)` 内部走 `d(l4, z3)`，l4 来自 WCDB。从未聊过 → WCDB 无 l4 → h() 返回 null → warmColdFriends 跳过，等用户发第一条消息后自动进热路径。这是 WeChat DB 的固有限制，不是 bug。

---

## 十三、2026-05-27 H 态新消息不露聊天列表（L4adapter.q.d 锁定）

### 问题口径
H 态下，密友/密群新消息允许落盘，但 LauncherUI 聊天列表不能出现该会话行。不露 = 仅列表可见行。

### 本问题禁止再押的路径
`IK3n` / `MvvmList.e/k/l` Java hook、仅清 `MvvmConvList.h/o/p`。2026-05-27 多版本实测：装上皆 0 enter 或 cleaned>0 但肉眼仍露。原因：上述均在渲染上游或内部存储，不是 RecyclerView 可见列表。

### 最终路径
live `kc5.v0` adapter 字段图：

```
kc5.v0 → q → d : List<kc5.y>   (owner=kc5.a)   ← 可见列表
```

`ConvFilter.cleanAdapterGraph(adapter, "L4adapter")`：L4 clean-before 从 live adapter 递归扫字段图，只处理 List 内是 `kc5.y/MainUI` 的，命中 `q.d` 后删 hidden id。

### L1 实证
```
[CF:L4adapter.q] field=d removed=1 owner=kc5.a   × 连续 6 条消息
```
肉眼确认列表不出现该行。

### 锁定点
- H 态压制仅看可见列表，不拦 DB / 不管通知/红点
- 禁止全局 hook `ArrayList.add`（F-20）

---

## 十四、2026-05-27 V↔H 全量热切（hidden id 作 key + 双写双清）

### 原问题
§十三 压住 H 态新消息后，V↔H 出现「只显示最后一个发消息的」。诊断 log v23.1 石锤：
```
[CF:warmAll:BUS-V] WXID-MISMATCH id=44786160583@chatroom extracted=wxid_lzd2va16jd1622
h(44786160583@chatroom) -> kc5.y wxid=wxid_lzd2va16jd1622
cache=2 expanded=2  ← 密群被当成密友 fresh 替换咬掉
```

### 根因
密群 `kc5.y` 的 `username/field_userName/talker` 被 `extractWxid` 扫出的是 **最后发言成员 wxid**，不是 `xxx@chatroom`。密友场景下 username == talker 一致不暴露；密群场景才暴露。

### v24 修复点
`ConvFilter.expandCacheWithWarm`：**直接用 `Bridge.allHiddenIds()` 授权 id 作 key**，不依赖 `extractWxid` 反推。保留 `WXID-MISMATCH` 警告作哨兵。

```java
String wxidExtracted = extractWxid(item);
String wxid = id;
if (wxidExtracted != null && !id.equals(wxidExtracted)) {
    Log.w(TAG, "[CF:warmAll:" + label + "] WXID-MISMATCH id=" + id
            + " extracted=" + wxidExtracted + " using id as key");
}
ConvHotReload.sConvItemMap.put(wxid, item);
replaceOrAppendWarmItem(out, wxid, item);
```

### v24 L1 实证
```
WXID-MISMATCH id=44786160583@chatroom extracted=wxid_lzd2va16jd1622 using id as key
h(44786160583@chatroom) -> kc5.y wxid=44786160583@chatroom
cache=2 expanded=3  visibleSnap=3
BUS-V-adapter.q field=d injected=3 owner=kc5.a
```
肉眼确认：V 态 3 个（密友×2 + 密群×1）同时出现。

### 架构锁定
- `MvvmConvList.h/o/p` + `adapter.q.d` 双写双清
- `kc5.x.h(id) != null` 时 fresh 总赢 stale
- `hidden id` 是唯一 key，禁止用 `extractWxid(item)` 作热切 dedup key
- 禁止回动 `seen.contains(id) -> skip`

---

## 十五、2026-05-27 残留缺陷：多轮 BUS-V 密群重复渲染（v24 时观察）

多轮 V↔H / BUS-V retry 后同一密群会话行出现多拍（3–4 行同名同消息）。原因：`ConvFilter.injectCacheIntoList` 与 `ConvHotReload.restoreToMvvmList` 的 dup 检测仍走 `extractWxid(item)`，密群 cached.wxid="xxx@chatroom" 与 list 中群条目抽出的成员 wxid 永远不等 → 不算 dup → 重复注入。

> **2026-05-27 后续**：v25 尝试加 `item == itemToInject` identity check（跨 list）→ 跨字段拦太狠 → 不显示；v26 改为 list-visited（同 list 内 identity，IdentityHashMap）→ 仍不显示。**v26 当前线上**，详见 §十六。修法待 v27。

---

## 十六、2026-05-27 v25/v26 多拍修复尝试失败归档

> 本节记录 v25/v26 两轮尝试修密群多拍而打破渲染的细节，防 v27 重蹈。

### v25：跨 list identity check

`injectCacheIntoList` / `restoreToMvvmList` 内层加 `item == itemToInject` 作 primary dup 检：
- 密群首次：itemToInject = sConvItemMap[xxx@chatroom] = fresh kc5.y。第二轮 BUS-V 时同一 fresh 已在 list 内 → identity 命中 → 不重复 ✅
- **但意外副作用**：`restoreToMvvmList` 先注入 fresh 到 MvvmConvList.h/o/p 后，`restoreAdapterGraphFromCache` 递归扫到 adapter.p（同 MvvmConvList 实例）时，p.h/p.o/p.p 已含 fresh → identity 命中 → injected=0 → adapter.p 跨字段全被拦
- 装机：BUS-V-adapter.p 零条、密友+密群都不显示，等新消息才出现

### v26：list-visited（IdentityHashMap）

外层加 `Set<Object> visitedLists = IdentityHashMap.newSetFromMap` 防同 List 重复处理：
- `ConvFilter.restoreConvListsInObject` 共享 `visited`（既装 root 也装 list），同一 list 不会被两个字段路径走两次
- `ConvHotReload.restoreToMvvmList` 独立 `visitedLists`：MvvmConvList.h/o/p 若是同一 List 引用 → 只走一次注入
- **预期**：MvvmConvList 三字段去重 + identity 防重复 → 密群只 1 拍

### v26 装机结果（2026-05-27 19:33）

```
warmAll         expanded=3              ✅
restoreInPlace  field=o injected=3, field=p injected=3, field=h injected=3   ← 9 次注入
BUS-V-adapter.q field=d injected=3 owner=kc5.a   ← 仅 adapter.q.d
BUS-V-adapter.p                          ❌ 零条日志
adapterInjected=3                        ← v24 时这里=6
肉眼：全不显示，等新消息才出现
```

### 失败根因

`restoreToMvvmList` 优先跑 → fresh 注入到 MvvmConvList.h/o/p 的 List 实例（list-visited 让三字段共指同一 List 时只走一次）。
然后 `restoreAdapterGraphFromCache` 跑 → 从 adapter 起递归。扫到 adapter.q.d（独立 List）成功注入 ✅；扫到 adapter.p（即 MvvmConvList 实例）时，p.h/p.o/p.p 对应 List 在内层 `injectCacheIntoList` 用 identity 检 → list 内已有同 fresh item → injected=0。

但**这本身不算错**——fresh 已经在 list 里了。真问题是：v24 时 adapter.p.h/o/p 重复注入 stale item 导致密群 4 拍 + RecyclerView 因 DiffUtil 看到 "list 内容变了" 触发渲染；v25/v26 不重复注入 → DiffUtil 可能"算出无变化"跳过渲染。

### 闭包（2026-05-27）

v27 最终走 **方向 B 的简化版** = v24 wxid-only dedup 保留，注入后另起 80ms post-dedup 按 identity 收敛同对象引用。见 §十七。
方向 A/C 未尝试，但留作未来 regression 时的备选思路。

### 禁止再做的

- ❌ 禁止在 `injectCacheIntoList` 内层加跨 list identity（v25 已证伪、F-35）
- ❌ 禁止 v24 风格的 wxid-only dedup **单独使用**（密群多拍；v27 已用 post-dedup 兜底）
- ❌ 禁止把 `restoreToMvvmList` 拆出 list-visited 而 `restoreConvListsInObject` 仍用同 visited（v26 配置已是分离的，F-35）

---

## 十七、2026-05-27 v27 装机收口方案（**当前线上**）

### 17.1 一句话

**v24 hidden-id-as-key 注入 + 80ms 异步 post-dedup（按 identity 收敛同对象引用） + IK3n install 防抱锁。**

### 17.2 代码骨架

```java
// ConvHotReload.handleBusVisible() — 主 Runnable
int injected = restoreToMvvmList(liveMvvmList, visibleSnap);                   // v24 wxid-only dedup
int adapterInjected = ConvFilter.restoreAdapterGraphFromCache(visibleSnap, "BUS-V-adapter");
notifyConvAdapter("BUS-V-direct");

// 80ms 后再扫一次同 adapter 图，按 identity 把上面两步可能造成的同对象引用重复收敛
new Handler(Looper.getMainLooper()).postDelayed(() -> {
    if (StateMachine.getInstance().getState() != StateMachine.State.VISIBLE) return;
    int removed = ConvFilter.postDedupAdapterGraph("BUS-V-dedup");
    if (removed > 0) notifyConvAdapter("BUS-V-dedup");
}, 80);
```

```java
// ConvFilter.postDedupAdapterGraph(label)
//   ↓ dedupConvListsInObject — 递归 adapter 字段图，IdentityHashMap 跟踪已访问 List
//     ↓ dedupListByIdentity   — 同 List 内 IdentityHashMap<Object,Boolean> 扫一遍、第二次出现就 it.remove()
```

### 17.3 为什么走这条路

| 选项 | 行为 | 结果 |
|------|------|------|
| v24 纯 wxid dedup | 密群 cached.wxid="xxx@chatroom" ↔ list item extractWxid="member_wxid" 永不相等 → 重复注入 | 密群每轮多拍 4 行 |
| v25 跨 List identity | restoreInPlace + restoreAdapterGraph 命中同 fresh → injected=0 | BUS-V-adapter.p 零条 → RecyclerView 不重绘（F-35）|
| v26 list-visited | 同 List 实例只处理一次 | 同上，与 v25 等价表现（F-35）|
| **v27 = v24 + 80ms post-dedup** | 注入保留 v24 的 modCount 副作用触发渲染；80ms 后再用 identity 收掉同对象引用 | 密群 1 拍、密友 1 拍、首次 V 出现可能 < 80ms 内眼花一下 |

### 17.4 IK3n install 补刀

`ConvFilter.installIk3nHandleEventHook(lpparam)` 之前一直只声明、`install()` 没调用。
v27 已在 `installMvvmListEHook` 之后、`installMvvmConvHooks` 之前插上调用。
P26 trace 已证 IK3n 在 NOTIFY 路径不在 WRITE 路径，单上 hook 拦不住 H 态新消息；但保留为后续会话刷新事件的拦截点、防抱锁上主错代价 = hook 不命中。

### 17.5 残留毛刺（v27 装机后发现 → v28 收口已修）

| 现象 | 触发场景 | 性质 | v28 修复 | 装机实测 |
|------|---------|------|---------|---------|
| `[CF:restoreInPlace] field=p fail: ConcurrentModificationException` | restoreInPlace for-each + 微信子线程同时写 MvvmConvList.p | 偶发，被外层 catch (Throwable) 吞掉，该字段本轮注入失败 | `restoreToMvvmList` 内层 dup-scan 改 `size+get(i)+IndexOutOfBoundsException` 兜底；insert 段独立 try/catch CME | ✅ 5 轮 H↔V CME=0 |
| `[BUS-V:dedup] removed` 从 3 涨到 12 后锁死 | H 态 `filterConvList` 用 extractWxid 识别条目，密群 item 抽出「最后发言成员 wxid」→ hidden 比对失败 → 不删 → 后续 V 态 restoreInPlace + restoreAdapterGraph 重复注入 | 数据层夸大被 post-dedup 吃掉所以肉眼正确，但每轮 dedup 数量异常 | 新增 `extractGroupId(item)`：扫 contact-like 字段 field_username 取 `*@chatroom`；`filterConvList` Phase 2 改快照 + 双 key（wxid 主、groupId 兜底）+ 逐项 `list.remove(item)` CME 防御 | ✅ 5 轮 dedup 不锁 12、H 态密群可删除 |

### 17.6 与其他模块的耦合点

- **冷启动**：v27 不动 `scheduleColdStartSweeps` / `cleanConvData("cold-100/300")` 路径。冷启 H 态密友首帧仍按 F-32 走 `setResult null + Handler.post` 全量刷
- **密友新消息**：H 态新消息仍走 `[CF:L4adapter.q.d]` 拦截，不进 V 态恢复链路；V 态新消息直接进 list，与 v27 注入无交集
- **IK3n hook 命中**：尚未在装机日志看到 `[CF] IK3n` 触发，需要后续在 V 态有人发消息时确认；命中后不应当影响 v27 dedup（IK3n 在 NOTIFY 末端、dedup 在 80ms 后异步跑）

### 17.7 禁止再走的死路

- ❌ 禁止把 v25 的跨 List identity check 抄回 `injectCacheIntoList` / `restoreToMvvmList`（F-35）
- ❌ 禁止把 v26 的 list-visited 抄回 `restoreConvListsInObject` / `restoreToMvvmList`（F-35）
- ❌ 禁止把 post-dedup 改成 BEFORE notify 同步执行——v25 实证：identity 在 notify 之前生效 = RecyclerView 不重绘
- ❌ 禁止以 P27_密群去重 为名再开任务做 dedup —— v27 已收口
