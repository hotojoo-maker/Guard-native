# A3 密群过滤 — 详细实现指针

> 最后更新：2026-05-24
> 对应 HOOKMAP §A3，存储底层指针路径 + 历史方案对比

---

## 一、存储层

| 键 | 类型 | 说明 |
|----|------|------|
| `Bridge.KEY_HIDDEN_GROUPS = "glst"` | MMKV StringSet | 群 ID 集合，形如 `{45592178108@chatroom, ...}` |
| `Bridge.getGroupIds()` | `Set<String>` | 读取 |
| `Bridge.addGroupId(gid)` | — | 写入 + NativeBridge 同步 |
| `Bridge.removeGroupId(gid)` | — | 删除 + NativeBridge 同步 |
| `Bridge.allHiddenIds()` | `Set<String>` | wxids ∪ groupIds，Filter 统一用这个匹配 |
| `Bridge.isGroupId(id)` | `boolean` | `id.endsWith("@chatroom")` |

---

## 二、过滤层指针路径（8.0.71）

### 2.1 会话列表 ConvFilter

```
filterConvList(List<Object> list)
  Phase 1: extractWxid(item)
    → item 字段 d/e/f/a/b/c 取 contact 对象
    → contact.C0() / h1() / j1() ... 取 username
    → isWxid(s): s.endsWith("@chatroom") → true
    → [CF:group] chatroom=xxx@chatroom name=xxx
    → Bridge.addConvWxid(wxid, nick)   ← 存昵称缓存
  Phase 2: active=true
    → hidden = Bridge.allHiddenIds()   ← 含群 ID
    → hidden.contains(wxid) → it.remove() + putCache(wxid, item, null, idx)

cleanMvvmList(mvvmList)
  → 扫字段 o / p / h (MVVMLIST_ARRAY_FIELDS)
  → extractWxid(item) → hidden.contains → putCache(wxid, item, fn, idx)

BUS-H: cleanConvData → cleanMvvmList → sConvCache 存群 item
BUS-V: sConvCache 里的群 item 随密友一起 MvvmList.n(restoreList, false) 恢复
```

### 2.2 通讯录 ContactFilter

```
A–Z 主列表
  ArrayList.addAll → fc5.g → g.d（z3 实例）→ z3.c1() → username
  @chatroom 后缀 → hidden.contains → remove

标签成员列表（F07B）
  ArrayList.addAll → ye5.j → ye5.j.d（wxid_xxx-N-M 或 xxx@chatroom-N-M）
  → 去后缀 `-N-M` → hidden.contains → remove

群聊分组（通讯录"群聊"tab，独立页 ChatroomContactUI）
  ⚠️ 旧假设「同 A–Z，z3.c1() 自动匹配」已被 2026-05-29 probe 证伪——
     ChatroomContactUI 是独立 Activity + 独立 cursor adapter，根本不走 A–Z 的 addAll(fc5.g)。
  正确机制见下方 §六（2026-05-29 L1/L2 收官）。
```

### 2.3 搜索 SearchFilter

```
L-FTS: ArrayList.addAll(fz2.e) → item.g = "SOSItemRelevant:xxx@chatroom"
  → strip "SOSItemRelevant:" → hidden.contains → remove

L-CONV: ArrayList.addAll(kc5.y) → item.d → l4 → l4.C0() = xxx@chatroom
  → hidden.contains → remove
```

### 2.4 朋友圈

密群消息不出朋友圈（微信机制），A3 不涉及 D1/D2/D3。

---

## 三、设置 UI 层（SettingsEntry.java）

### 3.1 添加密群

**当前方案（2026-05-24 改）**：
```
+ 添加 → SettingsEntry.startSelectGroup(context)
  → SelectContactUI (list_type=2, list_attr=16471)
  → 用户多选群聊 → 返回 putExtra("Select_Contact", "xxx@chatroom,yyy@chatroom")
  → installSelectContactHook:
      isConvKey || isGroupKey → hasChatroom=true → isGroup=true
      diff: resultSet vs sPreSelectedGroupIds
      → addGroupId / removeGroupId
      → RefreshBus.notifyHiddenChanged
      → sPendingReopen=true → onResume → showGuardDialog 重开
```

**废弃方案（GroupCardSelectUI）**：
```
GroupCardSelectUI 问题记录：
- group_multi_select=true 但实际单次只返回 1 个群
- 某些群（群类型限制）在 UI 内点不动
- 未确认其 putExtra key；Activity.setResult hook 诊断无命中
- 根因：GroupCardSelectUI 对应 "从群聊导入" 功能，有群类型白名单过滤
```

### 3.2 移除密群

**内联 × 按钮**（直接调用，不依赖 WeChat 返回）：
```java
Bridge.getInstance().removeGroupId(gid)
RefreshBus.getInstance().notifyHiddenChanged(sm.isActive())
dialog.dismiss() → 100ms 后 showGuardDialog(context)
```

**清空按钮**：
```java
for (String gid : new HashSet<>(Bridge.getInstance().getGroupIds()))
    Bridge.getInstance().removeGroupId(gid)
```

### 3.3 群名展示

```java
Bridge.getConvWxids()         // List<String[]> {wxid, nick}
  → filter kv[0].endsWith("@chatroom")
  → Map<gid, nick>
显示优先级: nick > gid.replace("@chatroom","")
```

---

## 四、冷启动限制

**场景**：微信冷启动，某群最近没有消息。

- WeChat 只加载"最近会话"进 MvvmList，不活跃的群不在内存里
- `cleanMvvmList` 扫不到该群 item → `sConvCache` 无此群
- 表现：冷启动后切 VISIBLE，该群不会出现在会话列表

**已知解决路径**：
- 该群有人发消息 → La 热更新路径捕获 → `filterConvList` putCache → 下次 BUS-V 可恢复
- 根本限制（同密友冷启动问题），无绕过方案

---

## 五、诊断日志速查

| 日志 Tag | 含义 |
|---------|------|
| `[CF:group] chatroom=xxx name=yyy` | filterConvList Phase 1 看到群，extractWxid 成功 |
| `[CF:filter] active=true items=N` | Phase 2 正在运行 |
| `[CF:cache] save wxid=xxx@chatroom` | 群 item 进入 sConvCache |
| `[CF:clean] removed=N src=BUS-H` | cleanMvvmList 从 MvvmList 扫到并移除 N 个 |
| `[BUS-V] cache=N restoreList=M` | 恢复时缓存 N 个，实际注入 M 个 |
| `[SET] selectGroup +N -M total=T` | addGroupId N 个，removeGroupId M 个 |
| `[SET] removeGroup gid=xxx` | 内联 × 按钮移除 |
| `[SET] clearAllGroups` | 清空按钮触发 |
| `[SET:grpResult] key=xxx val=yyy` | setResult hook 诊断（GroupCardSelectUI 路径） |

---

## 六、通讯录「群聊」独立页 ChatroomContactUI 隐藏（2026-05-29 L1/L2 收官，P_CV1-G）

> 这一节是 §2.2 旧假设被证伪后的**正确机制**。通讯录主列表的「群聊」入口点进去是一个**独立 Activity + 独立 cursor adapter**，跟 A–Z 主列表（ik3.t0 / AddressLiveList / fc5.g）完全是两套东西。

### 6.1 数据链路（L1 probe + L2 jadx 双证）

| 层 | 实体 | 说明 |
|----|------|------|
| 页面 | `com.tencent.mm.ui.contact.ChatroomContactUI` | 独立 Activity（不是 LauncherUI 主列表） |
| 列表容器 | 旧式 `ListView` + `HeaderViewListAdapter` 包壳 | 不是 RecyclerView，需 `getWrappedAdapter()` 解包 |
| adapter | `com.tencent.mm.ui.contact.s0` extends `com.tencent.mm.ui.s9`（CursorAdapter） | s9 是父类，getCount/getItem 在它身上 |
| item | `com.tencent.mm.storage.z3` | 群条目 |
| 群 id | `z3.c1()` → `xxx@chatroom` | 即 username 列 |
| 群名 | `z3.N0()` | 显示名 |
| 数据源 | `s9.f` = SQLiteCursor（自查库，`@all.chatroom.contact`） | 真源；`s9.g` = HashMap position 缓存；`s9.i` = count 缓存 |
| 关键方法 | `s9.t(Cursor)` 赋 cursor；`s9.g()` 返回 cursor；`getCount()=g().getCount()`；`getItem(i)=g().moveToPosition(i)→d()` 读 `username` 列 | |
| footer 计数 | 「N个群聊」= `ContactCountView`，走**独立 SQL**（与 adapter getCount 无关） | |

### 6.2 三个致命坑（防后人重蹈）

1. **A3 §2.2 旧假设错**：以为群聊同 A–Z 自动匹配。实际 ChatroomContactUI 不走 `addAll(fc5.g)`，主列表过滤碰不到它。
2. **classloader 分裂**（同 CONV_REFRESH_PROBLEM §十一 阶段2~3）：`lpparam.classLoader.loadClass("...s0/s9")` 拿到的是 LSPosed 影子类，hook 它**永不触发**。微信运行时用 Tinker classloader 的另一份。→ 必须从 **live adapter 实例**（`adapter.getClass()`，Tinker 真类）装 hook。
3. **g()/t() 够不着**：`g()` 被 R8 **内联**进 getCount/getItem（hook g() 零命中）；`t(Cursor)` 进页面复用缓存 cursor、**不重调**（零命中）。→ cursor 层 hook 全废。

### 6.3 最终方案（✅ 装机实证）

**实现位置**：`ContactFilter.hookGroupAdapterFromLive()`（由 `ContactDiscoveryHook` 从 live s0 实例回调）+ footer 走 `ContactDiscoveryHook.correctGroupFooterCount()`。

| 子项 | 做法 | L1 证据 |
|------|------|---------|
| **列表隐藏** | 从 live s0 真类 hook `getCount()`/`getItem(int)`/`getView(int,..)`（Adapter 框架方法，虚表调、不被内联）。getCount 隐藏态返回「可见数」+ 按 username 列建「可见位置→真位置」映射；getItem/getView 把 position 重映射跳过隐藏行 | `[CGF] getCount 3→2 hiddenRemoved=1` |
| **footer 计数** | adapter 过滤不影响 footer（独立 SQL）。直接扫 ChatroomContactUI View 树找「N个群聊」TextView，**幂等**设成绝对目标=可见数（仅当显示=原始总数 target+hidden 时才改，防递减到 0） | `[CGF:footerB] 3→2 (hidden=1)` |
| **热切 V↔H** | 群聊子页是 transient，进页面即按当前态过滤；**V→H 实时热切不迫切，本轮不做**（用户口径 2026-05-29） | — |

### 6.4 铁律备注

- 群聊用了 hook `getCount/getItem/getView`，**碰铁律14**——但铁律14 针对 Kotlin RecyclerView Flow（死循环/覆盖），ChatroomContactUI 是**老式 ListView CursorAdapter**，性质不同，已装机验证不崩。**视为铁律14 的例外**。
- 发版重档需跑 `frida_stats.js` 对比 KPI 基线（F-22，本轮新增 hook + Activity 扫描）。
