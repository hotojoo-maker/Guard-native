# P26 brief — 好友 V 态热切 fresh-item 触发
> 状态：⬜ 未开工，已锁定根因 + 候选方案
> 优先级：P0（用户感知阻塞）
> 立项日期：2026-05-25 03:20（P25 结案后）

---

## 当前状态

V 态切换后好友 wxid 不出现，群正常出现。差异源于 stale item 失效，**与 B2 修复（P25）独立**。

## 已锁定事实（2026-05-25 装机日志实证）

| 事实 | 证据 | 置信度 |
|------|------|:----:|
| BUS-V 注入逻辑正常 (injected=6 to o/p/h) | `[BUS-V] in-place injected=6 on MvvmConvList` | ✅ |
| sConvItemMap 从未喂养（lzd2va16/群均无） | 6/6 `[CF:restoreInPlace] used stale cache` | ✅ |
| 群 stale item 渲染正常 | 用户视觉确认（V 态群显示） | ✅ |
| 好友 stale item 渲染失败 | 用户视觉确认（V 态好友不显示） | ✅ |
| **fresh item 路径不走 L1 hook** | 发消息后无 `[CF:L1broad]` / `L1n` / `L1m` 日志，但 `[CF:L4] state=V` fire 且好友立刻出现 | ✅ |

## 根因说明

WeChat 收到新消息时，直接写底层 ArrayList（绕过 MvvmList API），然后 notify。

- `installAddAllHook` 只对 `CONV_MAIN_UI` 类生效（ConvFilter.java:507），fresh item 是 `kc5.y` 类，hook 早返
- L1 hook 永远拿不到 fresh item → `sConvItemMap` 永远是空 → `restoreInPlace` 永远走 `used stale cache`
- stale item 是 19+ 秒前 BUS-H 删除时存下来的对象。群 stale 能渲染（字段稳定）；好友 stale 失效（contact 字段已过期/detached）

## 候选方案（用户提出，方向正确）

**借用 WeChat 自己的"新消息事件"路径**：BUS-V 时对 cache 里每个 wxid，**伪造一次新消息事件**让 WeChat 自己 fetch fresh item + 重写 list + notify。

优点：不修 stale，借用 WeChat 自身逻辑，无 contact 字段对接问题。

## 阻塞点（必须先解）

**WeChat 内部 fresh-fetch 入口未知**。已确认不走 `MvvmList.n/m`（L1 不 fire），但 v0 adapter notifyDataSetChanged 被触发。可能入口：

- `ConvService.onNewMessage(wxid)` 类
- `ConvStorage.markRefresh(wxid)` 类
- `MainUiController.refreshConv(wxid)` 类
- 直接写 `ArrayList.add(int, item)` 路径

## 三层方法论执行计划

### 第 1 层：爬虫（链路发现）

脚本：`02_tools_工具/dynamic_crawler_动态爬虫/probe_fresh_msg_trigger.js`（待写）

- Frida hook `kc5.v0.notifyDataSetChanged` 反向追栈
- 触发：模拟新消息（发一条短文本）
- 输出：调用栈，找出 WeChat 内部 fetch 入口
- 限时 30s，MAX_HOOKS ≤ 80

### 第 2 层：探针（验证）

- 在 BUS-V 中调用第 1 层找到的入口
- 装机看好友是否出现
- 失败 2 次 → 退回第 1 层重新爬

### 第 3 层：正式 hook

- 在 `ConvHotReload.handleBusVisible` 中，对 sConvCache 每个 wxid 调一次 fresh-trigger 入口
- 删除 `restoreInPlace`（或保留为兜底）
- 装机走 P25 的 4 项验证清单

## 相关 hook 点

| Hook | 位置 | 状态 |
|------|------|:---:|
| `ArrayList.addAll(Collection)` on CONV_MAIN_UI | `ConvFilter.java:496` (installAddAllHook) | ✅ fire（但只过滤 MainUI 类，kc5.y 早返）|
| `MvvmList.n/m` | `ConvFilter.java:200` (installMvvmListHooks) | ⚠️ fresh 路径不 fire |
| `kc5.v0.notifyDataSetChanged` | `ConvFilter.java` L4 path | ✅ fire（但只看到 notify，不知道谁触发它）|
| `sConvItemMap.put` | `ConvFilter.java:236` (L1 hook 内) | ❌ 实测从未填充任何 wxid |

## 关键日志（保存供下次接手）

P25 装机原文：`03_execute_执行任务/P25_B2触发器误触修复/result.md` §四
fresh 路径触发日志：本会话 03:14:43（不在固定路径，需自重抓）

## 相关 F-xx 待归档

| 候选 F-xx | 内容 |
|----------|------|
| F-NEW | fresh item 路径不走 `MvvmList.n/m` 也不走 `ArrayList.addAll` 的 L1 入口检测，BUS-V cache 注入永远走 stale 路径 |
| F-NEW | 群 stale item 渲染容忍 / 好友 stale item 渲染拒绝（具体字段差异未实证）|

（待总调度 P26 完成后归档）

## 与 P20C 的关系

P20C 的"L0addAll 覆盖新消息热更新"结论在当前代码已不成立（addAll hook 加了 MainUI 类过滤早返）。**P26 应推翻或更新 P20C 这条结论**。

接手时必读 `03_execute_执行任务/P20C_HVRecovery/` 避免重走弯路。
