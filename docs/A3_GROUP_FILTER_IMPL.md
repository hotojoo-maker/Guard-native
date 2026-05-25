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

群聊分组（通讯录"群聊"tab）
  同 A–Z，z3.c1() 返回 xxx@chatroom → 自动匹配
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
