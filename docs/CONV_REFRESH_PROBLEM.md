# CONV_REFRESH_PROBLEM — 会话列表 H↔V 状态切换无法立刻显示密友

> 创建：2026-05-24  
> 最后更新：2026-05-25  
> **核心问题：切换显隐状态（H↔V）后，密友会话无法立刻出现在列表里**  
> 关联：HOOKMAP §L1·V↔H、ConvFilter.java、ConvHotReload.java

---

## 一、问题定义（唯一口径）

**切换状态，无法立刻显示密友。**

| 方向 | 期望 | 实际 |
|------|------|------|
| **V→H**（隐藏） | 密友会话马上从列表消失 | ✅ 已稳定（`sPendingHide` + clean + notify） |
| **H→V**（显形） | 密友会话马上回到列表 | ❌ **不能立刻显示**；有时等几秒，有时要等收消息，有时一直不出现 |

其它现象（点不动、只回来一部分等）都是 **「H→V 无法立刻显示」** 的衍生表现，不是独立问题。

---

## 二、为什么 H→V 不能立刻显示

H 态下我们把密友从 **MvvmList 内存列表** 里清掉了。切 V 时列表要立刻出现，必须 **马上有数据可画**——目前只有两条路：

| 路径 | 能否立刻显示 | 说明 |
|------|:----------:|------|
| **热路径** | ✅ 可以 | 密友 **近期有消息** → La/addAll 曾推过 `kc5.y` → `sConvItemMap` / `sConvCache` 有对象 → BUS-V in-place 注回 |
| **冷路径** | ❌ 不能 | 密友 **长期无消息** → WeChat 内存里根本没有该会话 → BUS-V 无对象可注回 → **只能等 WeChat 自己推**（收消息或 reload） |

所以：**不是缓存丢了，是 H→V 那一刻列表侧没有可显示的会话数据。**

```
H→V 触发 BUS-V
  ├─ map/cache 里有该 wxid 的 kc5.y → 注回 → 立刻显示 ✅
  └─ map/cache 里没有 → notify 重绘空列表 → 不能立刻显示 ❌
```

群聊更容易「一切就回来」，是因为群聊通常有近期消息，WeChat 内存里一直有对象。

### 实现层已修（F-34，R-04）

旧 bug：BUS-H 无条件 `sConvCache.clear()` → 明明有过对象也写不进 cache → H→V 更不可能立刻显示。  
现行：`lazy-clear`，只在真正 remove 时更新 cache。这是 **让热路径更可靠**，不解决冷路径「无对象」的限制。

### 已废弃：stale 对象强插（`restoreCachedItems`）

能勉强立刻显示，但点会话 **点不动**（ViewModel 不认）。已停用。

---

## 三、2026-05-23 为什么"曾经好用"

当时只有 1 个密友，且 **刚有过消息**（La 热路径已捕获对象）。H→V 一切 **能立刻显示**。

加更多密友、或某 wxid 长期无消息后 H→V：

- H→V 不能立刻显示：map/cache 里当时没有可注回的会话对象

---

## 四、正确方向

**核心原则**：不自己管缓存，让 WeChat 的数据管道重新推一次完整列表。

### 方案对比

| 方案 | 原理 | 是否有黑屏 | 前提条件 |
|------|------|:--------:|---------|
| **C（推荐）** | 找到 WeChat ViewModel.reload() 入口，H→V 时直接 invoke | ❌ 无 | 需 Frida 探针先找到入口 |
| **B** | `Activity.recreate()` 在 H→V 时重建 | ✅ 短暂黑屏（与竞品 Catfish 一致） | 无需 Frida，随时可做 |
| **A（当前现状）** | 等 WeChat 自然 Flow 推送（收消息时触发） | ❌ 无黑屏 | 可能有数秒延迟 |

---

## 五、两个可能的主动触发时机

### 时机 1 — 进入密友设置页时（SettingsEntry.showGuardDialog）

用户打开设置页时，系统已知当前状态。**此时触发对当前态没有意义**（用户还没改）。  
不适合在这里做刷新，除非进设置页时需要"先确认当前列表是否正确"。

### 时机 2 — 解锁密友后（BUS-V / SettingsEntry 确认切换）

**这是最合适的触发点**：
- 用户点确认解锁 → `StateMachine.exitHidden()` → `RefreshBus.notifyHiddenChanged(false)` → BUS-V
- BUS-V handler 是我们控制的，可以在此处触发 WeChat reload

**如果能调 WeChat reload（方案 C）**：在 BUS-V handler 里直接 invoke，0ms 延迟，无闪屏。  
**如果用 recreate（方案 B）**：在 BUS-V handler 里 `LauncherActivity.recreate()`，有一次黑屏但可接受。

### 时机 3 — 放弃 stale 注回，等 WeChat 自然推（降级）

H→V 时不做 `restoreCachedItems` / stale 注入，只 `notifyConvAdapter`。  
对 **已在 map/cache 中有 live 对象的密友** 仍走 BUS-V in-place 注回；对 **从未加载进内存的 wxid** 只能等收消息触发 La。  
可配合「进密友设置页后等 Flow 跑一轮」的体验优化。

---

## 六、Frida 探针结论（2026-05-24 实测）

### 会话列表热更新完整链路

```
fc5.d.e()                           ← 数据源变更 Observer
  ↓
fc5.d.onChanged()
  ↓
MvvmObserverOwner.LifecycleBoundObserver.a()
  ↓
l45.g.notify() → v45.e.handleEvent()
  ↓
h45.f.handleMessage() (后台线程)
  ↓
h45.i.handleMessage(Message) (主线程)
  ↓
ik3.n.handleEvent(List)             ← ★ 最佳 hook 点（List 参数，可保存+回放）
  ↓
cl0.u.V(fv5.a)
  ↓
ik3.m.invoke()
  ↓
MvvmList.w(ik3.o0)
  ↓
MvvmList.e(List)                    ← Kotlin coroutine 实际更新
  ↓
kc5.v0.notifyDataSetChanged()
```

### 四个 Tab 的 Fragment 类名（实测）

| Tab | Fragment 类名 |
|-----|--------------|
| 会话 | `com.tencent.mm.ui.conversation.MainUI` |
| **通讯录** | **`com.tencent.mm.ui.contact.address.MvvmAddressUIFragment`** |
| 发现 | `com.tencent.mm.ui.FindMoreFriendsUI` |
| 我 | `com.tencent.mm.ui.MoreTabUI` |

---

## 七、确定方案：ik3.n.handleEvent(List) 回放（方案 C）

### 为什么选这个入口

| 条件 | 说明 |
|------|------|
| 参数是普通 `List` | 可以直接 `new ArrayList<>(list)` 保存，不需要构造复杂对象 |
| 位于我们过滤点的上游 | 回放时，WeChat 自己的 MvvmList.n hook（我们的 L1）以 `isActive()=false` 放行 → 密友自然出现 |
| 对象没有被"驱逐" | 不同于 restoreCachedItems（把已删的 stale 对象插回去），这里的对象是 WeChat 主动推来的，数据结构合法 |
| 无黑屏 | 走正常数据管道，无 recreate |

### 工作流程

```
正常态：
  ik3.n.handleEvent(list) 命中
  → 保存 sIk3nRef（WeakRef）+ sIk3nMethod + sLastFullList（过滤前完整列表）
  → 正常走后续流程（我们的 L1 hook 在 HIDDEN 时移除密友）

H→V（BUS-V）：
  sIk3nRef.handleEvent(new ArrayList<>(sLastFullList))  ← 主线程 post
  → 此时 isActive()=false → L1 hook 直接放行 → 密友立刻出现
  → 无 sConvCache，无 stale 注入，无点不动
```

### ContactFilter 修复

当前 ContactFilter 用的是 `android.app.Fragment.onHiddenChanged`，而实测类名是  
`com.tencent.mm.ui.contact.address.MvvmAddressUIFragment`（extends `androidx.fragment.app.Fragment`）。

修复路径：
- hook `MvvmAddressUIFragment.onResume()` 或 `onHiddenChanged(false)`
- 在进入通讯录 Tab 时触发 `refilterContacts()`

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

## 九、当前实际状态（2026-05-25）

| 场景 | H→V 能否立刻显示密友 | 说明 |
|------|:------------------:|------|
| 密友近期有消息（热路径已捕获） | ✅ 能 | BUS-V in-place 注回 |
| 密友长期无消息 | ❌ 不能 | 等 WeChat 推消息或找到 reload 入口 |
| V→H 隐藏 | ✅ 能 | 与 H→V 对称，已稳定 |
| 通讯录 H→V | 🟡 待验证 | ContactFilter onResume 路径 |

**待解决**：让 **所有** H→V 切换都能立刻显示密友（方案 C reload / 方案 B recreate）。

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
