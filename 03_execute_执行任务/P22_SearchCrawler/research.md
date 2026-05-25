# P22 SearchCrawler — 搜索链路 Hook 点发现

**任务类型**：调研 / 工具运行  
**工具**：`02_tools_工具/dynamic_crawler_动态爬虫/search_crawler.js`  
**目标**：找到 fz2.e c=3 的 UIN→wxid 映射来源，完成搜索聊天记录内联行过滤

---

## 背景（来自 P20 搜索拦截）

| 层 | 类 | 状态 | 问题 |
|----|----|:----:|------|
| L-FTS c≠3 | `fz2.e` | ✅ | 联系人结果，g=SOSItemRelevant:wxid，已过滤 |
| L-CONV | `kc5.y` | ✅ | 会话结果，d→l4→C0()，已过滤 |
| L-FTS c=3 | `fz2.e` | ❌ | **聊天记录内联行，g=UIN 数字，无法直接匹配 wxid** |

**核心问题**：fz2.e c=3 的 `g` 字段存的是 UIN（如 `1484091`），不是 wxid。  
我们的隐藏集是 wxid，无法直接比较。  
需要找到 UIN→wxid 的映射来源，构建缓存。

---

## 假设

1. **l4 对象（contact）同时持有 UIN 和 wxid**  
   ConvFilter/SearchFilter 处理 kc5.y 时能拿到 l4，如果 l4 有 UIN 字段，
   可在 L-CONV 处理时顺带建 `Map<UIN, wxid>` 缓存，供 L-FTS c=3 查询。

2. **fz2.e 构造器调用时 UIN 来自某个 contact 对象**  
   search_crawler.js 的 `§ 6 fz2.e 专项探针` 会 hook 所有 fz2.e 构造器，
   打印 c=3 + UIN 情况的 callstack，指向设置 g=UIN 的源头。

---

## 运行记录

### 运行 1 — 2026-05-23（待跑）

```
关键词:
密友 wxid:
输出: （见 result.md）
```

---

## 关键字段证据（来自 Java l4dump，2026-05-23）

> 等 `[SF:l4dump]` 日志出来后填入

```
[SF:l4dump] wxid=??? fields={...}
```

→ UIN 字段名：**待确认**
