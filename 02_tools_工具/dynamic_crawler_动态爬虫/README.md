# dynamic_crawler — 动态 Hook 点发现器

> 项目级通用工具 · 只读 · 不改正式代码  
> 当前版本：v1.1（2026-05-23）

---

## 用途

**新链路未知时，先跑 dynamic_crawler，不要手写零散探针。**

自动枚举微信已加载类，动态 hook 搜索/会话/朋友圈/通知相关方法，
记录限时窗口内的命中，按"含 wxid / UIN / 密友"自动评分，
输出候选 hook 点排名，指导正式 hook 编写。

---

## 脚本清单

| 脚本 | 链路 | 状态 |
|------|------|:----:|
| `search_crawler.js` | 搜索框 / FTS / 搜索结果 | ✅ v1.1 |
| `conv_crawler.js` | 会话列表 | ⬜ 待写 |
| `moments_crawler.js` | 朋友圈 | ⬜ 待写 |
| `notification_crawler.js` | 通知 / push 进程 | ⬜ 待写 |
| `crawler_core.js` | 公共工具函数 | ✅ v1.0 |

---

## 快速用法

```bash
# 微信已在运行（warm-attach 模式）
frida -U -n "com.tencent.mm" -l 02_tools_工具/dynamic_crawler_动态爬虫/search_crawler.js

# 然后在微信里操作目标链路（搜索 / 刷会话 / 刷朋友圈 / 收推送）
# 等 30 秒，控制台自动输出评分报告

# 手动触发（frida REPL）：
# scStart("你的搜索词")   ← search_crawler
# scReport()             ← 立即输出当前报告（不等 30s）
```

---

## 输出说明

报告打印在 frida 控制台，手动复制到对应 P 任务的 `result.md`：

```
03_execute_执行任务/P22_SearchCrawler/result.md   ← 搜索链路爬虫输出
03_execute_执行任务/Pxx_.../result.md             ← 其他链路对应 P 任务
```

**不要把爬虫输出放到工具目录（`output/` 仅存模板）。**

---

## 配置

每个脚本顶部 `§ 0 配置` 节：

```javascript
const HIDDEN_WXIDS = new Set([
    'wxid_lzd2va16jd1622',   // 换成当前测试密友
]);
const CRAWL_MS = 30000;      // 爬取窗口时长
const MAX_HOOKS = 300;       // 最大 hook 数量（防爆）
```

---

## 禁止事项

| 禁止 | 原因 |
|------|------|
| 全局 hook 所有 ArrayList / Handler | KPI 爆表 / 崩溃 |
| 打印完整聊天正文 | 隐私 + 日志量爆炸 |
| 修改正式 Java 代码 | 爬虫只用于定位 |
| 不限时运行 | 性能影响微信 |
| 把爬虫结论直接写入文档不经验证 | 见铁律 F-25 |

---

## 评分规则

| 加分项 | 分值 |
|--------|-----:|
| 出现密友 wxid | +100 |
| 出现任意 wxid | +30 |
| 返回可 remove 的 List | +50 |
| 在搜索期间被调用 | +20 |
| 出现 UIN 数字 | +10 |
| 出现关键词命中 | +5 |

| 减分项 | 分值 |
|--------|-----:|
| 高频（>50次）但无身份信息 | -20 |

---

## 已知发现（截至 2026-05-27）

### 8.0.71 搜索框 wxid 真实路径（装机实证）

| 类 | 链路 | wxid 路径 | 状态 |
|----|------|-----------|:----:|
| ⭐ **`com.tencent.mm.plugin.fts.ui.q2`** + `q2.j(View, jz2.g, boolean)` | **View 绑定层**（ListView BaseAdapter） | `g.a==1 → tz2.u1.f.s = wxid` · `g.a==2 → tz2.s1.s = groupId` | ✅ 装机实证 2026-05-27（主过滤路径） |
| `kc5.y` | 搜索会话结果 | `d→l4→C0()` | ✅ 已有 L-CONV |

### 数据层（非 wxid 来源，作为诊断/兜底）

| 类 | 链路 | `g` 字段实测内容 | 状态 |
|----|------|---------------|:----:|
| `fz2.e` c=0 | 分类/相关搜索行 | `"SOSItemRelevant:<关键词>"`（**无 wxid 字面**） | ❌ wxid 误判已纠 |
| `fz2.e` c=2 | 联系人匹配数据行 | `"<UIN>"`（数字 ID，非 wxid） | ❌ wxid 误判已纠 |
| `fz2.e` c=3 | 聊天记录内联行 | `"<UIN>"`（待 UIN→wxid 映射，**可由 q2 layer 替代**） | ❌ |
| `fz2.e` c=4 | 其他 FTS 分区 | `"<UIN>"` | ❌ |
| `z15.ef6` | 聊天记录 FTS 命中 | `d/e/o`=查询词/高亮，`p`=ch6.protobuf（talker 在 byte[] 内未解析） | ⚠️ wxid 不可达，由 q2 layer 兜底 |

### 容器纠偏（vs v1.1 旧版）

| 类 | v1.1 旧描述 | 8.0.71 装机实测 |
|----|-----------|-----------|
| `fz2.e` | LinkedList.add | **ArrayList.addAll** ✅ |
| `z15.ef6` | LinkedList.add | LinkedList.add ✅ |

详细见 → `03_execute_执行任务/P22_SearchCrawler/result.md` · `docs/HOOK_MAP_8071_AUTHORITATIVE.md` §8b/8c/8d
