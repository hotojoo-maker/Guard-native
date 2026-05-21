# 类名 / 字段名速查表（8.0.66 + 8.0.71 对照）

> **用途**：写代码时的快速参考，避免翻 Frida 脚本
> **来源**：filter_conv.js v1 + filter_moments.js v12 动态验证 + 8.0.71 jadx 分析（2026-05-19）
> **当前底座**：微信 **8.0.71**（D-014，2026-05-19 切换）

---

## 8.0.66 → 8.0.71 关键类名映射

| 用途 | 8.0.66 | 8.0.71 | 变化 |
|------|--------|--------|------|
| 朋友圈 Adapter | `y1`（全路径 `.../component.y1`） | `e2`（全路径待确认） | 类名变，结构不变 |
| 朋友圈 item (MvvmList.o) | `k24.b` | `jk3.c` | 类名变；数据在 f278463d 字段 |
| SnsInfo 包装 | `k24.b.d` (SnsObject) | `jk3.c.f278463d` (la4.p / ImproveSnsInfo) | 包装层类型变 |
| wxid 路径 | `k24.b.d.field_userName` | `jk3.c.f278463d → .b1() → .field_userName` | 多一层方法调用 |
| ViewHolder 壳 | `k24.b`（兼任） | `za3.c`（纯 UI 壳，无数据，已排除）| 职责分离 |
| MvvmList 字段 | `o` / `p` | `f135087o` / `f135088p` | 混淆名带序号 |
| MvvmList 类路径 | `com.tencent.mm.plugin.mvvmlist.MvvmList` | 同路径 | ✅ 不变 |
| StateFlow 类型 | `kotlinx.coroutines.flow.h2` | `h2` / `j0` | ✅ 同导入 |

> ⚠️ `rl.ta` 是 jadx 误判（SnsInfo 基类），实际 addAll item 是 `za3.c`（Frida 探针 2026-05-20 确认）

---

## 稳定类（hardcode 安全）

| 类名 | 用途 | 备注 |
|------|------|------|
| `com.tencent.mm.plugin.mvvmlist.MvvmList` | 会话 + 朋友圈数据容器 | 跨版本稳定 |
| `com.tencent.mm.ui.conversation.ConversationListView` | 会话列表 View | 跨版本稳定 |
| `com.tencent.mm.protocal.protobuf.SnsObject` | 朋友圈 protobuf 数据 | proto 生成，稳定 |
| `kotlinx.coroutines.flow.h2` | MutableStateFlow 类型 | kotlin 库，稳定 |
| `java.util.ArrayList` | MvvmList 内部列表 | 标准库 |

---

## 不稳定类（8.0.66 hardcode，升版本需重新找）

### 会话模块

| 类名 | 版本 | 用途 | 如何找 |
|------|------|------|--------|
| `f45.s0` | 8.0.66 | 会话列表 Adapter | `ConversationListView.getAdapter()` |
| `f45.u` | 8.0.66 | 会话列表 item | `MvvmList.o` 取元素，检查有 `d` 字段 |
| `com.tencent.mm.storage.m3` | 8.0.66 | 会话 contact 数据 | `f45.u.d` 字段类型 |
| `nd3.o0` | 8.0.66 | MvvmList 事件对象（L3） | `MvvmList.w(nd3.o0)` 参数类型 |
| `kc5.v0` | **8.0.71** | 会话列表 Adapter | `ConversationListView.getAdapter()` |
| `kc5.y` | **8.0.71** | 会话列表 item | `kc5.v0.f286278p`（MvvmConvList）内部列表元素 |
| `com.tencent.mm.storage.l4` | **8.0.71** | 会话 contact 数据 | `kc5.y.d` 字段类型（动态验证 2026-05-20）|
| `com.tencent.mm.ui.conversation.adapter.MvvmConvList` | **8.0.71** | MvvmList 子类（会话专用）| `kc5.v0.f286278p` 字段类型 |

### 朋友圈模块

| 类名 | 用途 | 版本 | 如何找 |
|------|------|------|--------|
| `com.tencent.mm.plugin.sns.ui.improve.component.y1` | 朋友圈 Adapter | 8.0.66 | `Java.choose` 扫描 |
| `k24.b` | 朋友圈 item | 8.0.66 | `y1.H → MvvmList.o` 取元素 |
| `e2` | 朋友圈 Adapter（全路径待确认） | **8.0.71** | `Java.choose("e2",...)` |
| `za3.c` | 朋友圈 item（变体 za3.i/db3.b） | **8.0.71** | Frida addAll 探针确认，字段 .a/.b/.c/.d/.e |
| ~~`rl.ta`~~ | ~~误判，SnsInfo 基类非 item~~ | ~~8.0.71~~ | ❌ 废弃，用 za3.c |

---

## 字段名（8.0.66，均不稳定）

### MvvmList 内部字段

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `o` | `ArrayList<?>` | 主数据列表（remove 这里） |
| `p` | `ArrayList<?>` | 快照列表（同步 remove） |
| `h` | `ArrayList<?>` | filter_conv 里也清洗（8.0.65 遗留）|
| `h2` | `kotlinx.coroutines.flow.h2` | MutableStateFlow，8.0.66 字段名 |
| `m2` | `StateFlow` | 对外只读 Flow，8.0.66 字段名 |

> ⚠️ 8.0.65 对应字段名是 `s`/`v`，不是 `h2`/`m2`
> **按类型找字段，不按名字找**：`kotlinx.coroutines.flow.h2` 类型不变

### f45.s0 (会话 Adapter) 字段

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `p` | `MvvmList` | adapter 持有的数据源 |

### f45.u (会话 item) 字段

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `d` | `com.tencent.mm.storage.m3` | contact 数据 |

### com.tencent.mm.storage.m3 (contact，8.0.66) 方法/字段

| 名称 | 类型 | 说明 |
|------|------|------|
| `j1()` | method → String | 返回 username (wxid) |
| `field_unReadCount` | int | 未读消息数 |

### com.tencent.mm.storage.l4 (contact，8.0.71) 方法/字段

> iOS 对应：`CContact`，wxid 字段为 `m_nsUsrName`（iOS）→ `C0()` 方法（Android 8.0.71）

| 名称 | 类型 | 说明 | iOS 对应 |
|------|------|------|----------|
| `C0()` | method → String | 返回 wxid（动态验证 2026-05-20）| `m_nsUsrName` |
| `S0()` | method → String | 返回数字 uid（如 "285212721"）| — |
| `h1()` | method → String | 返回账号类型（如 "officialaccounts"）| — |
| `getTableName()` | method → String | 返回 DB 表名（"rconversation"）| — |
| `field_unReadCount` | int | 未读消息数（与 8.0.66 一致）| — |

### iOS CContact 核心 getter（跨端对照参考）

| iOS getter / 字段 | 含义 | Android 8.0.71 对应 |
|-------------------|------|---------------------|
| `m_nsUsrName` | wxid（"wxid_xxx" / "gh_xxx"）| `l4.C0()` ✅ 动态验证 |
| `m_nsNickName` | 昵称 | — |
| `m_nsRemark` | 备注名 | — |
| `m_nsFullPY` | 全拼（用于搜索） | — |
| `m_nsRemarkPYShort` / `m_nsRemarkPYFull` | 备注拼音 | — |
| `m_nsHeadImgUrl` | 头像 URL | — |
| `m_uiSex` | 性别 | — |
| `m_uiType` | 类型整数 | — |
| `m_nsSignature` | 个性签名 | — |
| `m_nsCity` / `m_nsProvince` / `m_nsCountry` | 地区 | — |
| `m_nsAliasName` | 别名/微信号 | — |
| `m_uiConType` | 联系人类型 | — |
| `m_nsEncodeUserName` | 编码后 userName | — |
| `isChatroom()` | 是否群聊 | — |
| `isBrandContact()` | 是否公众号（对应 `gh_` 前缀）| — |
| `isMyContact()` | 是否好友 | — |
| `isNormalContact()` | 是否普通联系人 | — |

### com.tencent.mm.plugin.sns.ui.improve.component.y1 (朋友圈 Adapter) 字段

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `H` | `MvvmList` | adapter 持有的数据源 |

### k24.b (朋友圈 item) 字段

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `d` | `com.tencent.mm.protocal.protobuf.SnsObject` | protobuf 数据 |

### SnsObject (protobuf，稳定) 字段

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `field_userName` | String | 发帖者 wxid，**跨版本稳定** |
| `field_id` | long | 朋友圈 ID，**跨版本稳定** |
| `field_nickname` | String | 发帖者昵称 |

---

## wxid 提取路径汇总

```
会话（8.0.66）：
  f45.u item
    └── .d → com.tencent.mm.storage.m3
               └── .j1() → String wxid

会话（8.0.71）：               ← 动态验证 2026-05-20 ✅
  kc5.y item
    └── .d → com.tencent.mm.storage.l4
               └── .C0() → String wxid

朋友圈（8.0.66）：
  k24.b item
    └── .d → SnsObject
               └── .field_userName → String wxid

朋友圈（8.0.71）：
  za3.c item
    └── .f278463d → la4.p (ImproveSnsInfo)
                     └── .b1() → SnsObject
                                  └── .field_userName → String wxid

反射写法（8.0.71 ConvFilter 实际使用）：
  item.getClass().getDeclaredField("d").get(item) → l4 contact
  contact.getClass().getMethod("C0").invoke(contact) → wxid String
  // WXID_GETTER_NAMES = {"C0", "j1", ...} 优先尝试 C0
```

---

## 版本差异速查

| 项目 | 8.0.65 | 8.0.66 | 8.0.71 | 备注 |
|------|--------|--------|--------|------|
| MvvmList StateFlow 字段 | `s`, `v` | `h2`, `m2` | `f135087o`, `f135088p` | 按类型找，不按名找 |
| 会话 Adapter | 不同混淆名 | `f45.s0` | `kc5.v0` | 每版重新确认 |
| 会话 item 类 | — | `f45.u` | `kc5.y` | 每版重新确认 |
| 会话 contact 类 | — | `com.tencent.mm.storage.m3` | `com.tencent.mm.storage.l4` | 每版重新确认 |
| 会话 wxid getter | — | `m3.j1()` | `l4.C0()` | 动态验证 2026-05-20 ✅ |
| Adapter MvvmList 字段 | — | `f45.s0.p` | `kc5.v0.f286278p` | 每版重新确认 |
| 朋友圈 Adapter | — | `y1` | `e2`（全路径待确认） | 每版重新确认 |
| 朋友圈 item 类 | — | `k24.b` | `za3.c` | 每版重新确认 |
| SnsObject.field_userName | ✅ 稳定 | ✅ 稳定 | ✅ 稳定 | protobuf 生成 |
| MvvmList 类名 | ✅ 稳定 | ✅ 稳定 | ✅ 稳定 | — |

---

## 运行时定位混淆类的通用方法

```java
// 1. 找 Adapter 类名
Class<?> adapterClass = convListView.getAdapter().getClass();
// 若包 HeaderViewListAdapter: getWrappedAdapter().getClass()

// 2. 找 MvvmList 内部 ArrayList 字段（按类型）
for (Field f : MvvmList.class.getSuperclass().getDeclaredFields()) {
    if (f.getType() == ArrayList.class) {
        // f.getName() 就是 o / p / h
    }
}

// 3. 找 MutableStateFlow 字段（按类型）
for (Field f : MvvmList.class.getSuperclass().getDeclaredFields()) {
    if ("kotlinx.coroutines.flow.h2".equals(f.getType().getName())) {
        // 就是 StateFlow 字段
    }
}

// 4. 确认 k24.b 类名（从列表取元素）
Object item = mvvmListO.get(0);
String itemClassName = item.getClass().getName(); // "k24.b"
// 然后检查有没有 "d" 字段且 d 含 "field_userName" → 确认

// 5. 确认朋友圈 Adapter 类名
Java.choose("com.tencent.mm.plugin.sns.ui.improve.component.y1", ...) // Frida
// LSPosed: 监听 Activity.onCreate 或从 Fragment 反射找 adapter
```

---

*本文件来自 filter_conv.js / filter_moments.js 动态 dump + VERSION_8065_ANALYSIS.md 对照*
