# SearchCrawler 动态发现工具 — 结果记录

**目标版本**：微信 8.0.71  
**工具版本**：search_crawler.js v1.1  
**用途**：自动发现搜索相关类 + 方法 hook 点 + UIN→wxid 映射候选

---

## 用法

```powershell
# 微信已在运行
frida -U -n "com.tencent.mm" -l "03_execute_执行任务\SearchCrawler_动态发现工具\scripts\search_crawler.js"

# 然后在微信里：
# 1. 点放大镜打开搜索框
# 2. 输入密友昵称 / wxid / 聊天片段关键词
# 3. 等 30 秒，控制台自动输出报告

# 或在 frida REPL 手动触发：
# scStart("hello")
```

> `HIDDEN_WXIDS` 在脚本顶部 § 0 配置，按需添加密友 wxid。

---

## 结果（粘贴到这里）

### 运行时间
<!-- 填写 -->

### 搜索关键词
<!-- 填写 -->

---

## Top 候选 hook 点

| # | 类#方法 | score | calls | wxid | UIN | 返回可删 List | 搜索中触发 |
|---|---------|------:|------:|:----:|:---:|:------------:|:---------:|
| 1 | | | | | | | |
| 2 | | | | | | | |
| 3 | | | | | | | |
| 4 | | | | | | | |
| 5 | | | | | | | |

---

## fz2.e c=3 UIN 来源候选

| 候选点 | UIN 值 | callstack |
|--------|--------|-----------|
| | | |

**目标**：找到哪个方法/构造器在设置 `fz2.e.g = UIN 数字`，  
那里一定同时能拿到对应的 wxid，是建立 `UIN→wxid` 缓存的注入点。

---

## ArrayList.addAll 新类（搜索中首次出现）

> 已知类：`fz2.e` / `kc5.y` / `jw1.d` / `ik3.i` / `ts4.e` / `af4.a` / `c1` / `f9`

| 类名 | sz | wxid字段 | UIN字段 | 推断用途 |
|------|----|:--------:|:-------:|---------|
| | | | | |

---

## 已排除路径

| 类#方法 | 排除原因 |
|---------|---------|
| | |

---

## 已知结论（截至 2026-05-23）

| 层 | 类 | wxid 路径 | 状态 |
|----|----|-----------|:----:|
| L-FTS c≠3 | `fz2.e` | `g` = `SOSItemRelevant:wxid_xxx` | ✅ |
| L-CONV | `kc5.y` | `d → l4 → C0()` | ✅ |
| L-FTS c=3 | `fz2.e` | `g` = UIN 数字，**无直接 wxid** | ❌ 待 crawler 发现映射 |
| L-CHAT | 未知 | addAll 路径穷尽，非 addAll 路径 | ❌ 待 crawler 发现 |

---

## 性能风险

| 风险 | 说明 | 缓解 |
|------|------|------|
| 类枚举卡顿 | `Java.enumerateLoadedClasses` 首次全量，可能卡 1-2s | 仅在 FTSMainUI 首次进入时触发一次 |
| 超出 MAX_HOOKS | 默认 300 个 hook 上限 | 超限后静默跳过，不崩溃 |
| 高频 addAll hook | 全局无 Activity 限制 | 只在 `crawling=true` 时分析（30s 窗口） |
| fz2.e ctor hook | 低频，安全 | 只打印 c=3+UIN 情况 |
