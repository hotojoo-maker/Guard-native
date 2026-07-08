# FEATURE_MATRIX — 密友功能矩阵

> **铁律**: AI 每次接手必须先读此表。状态变化立即更新。

## 功能地图

| 编号 | 功能 | 状态 | 实现层 | Hook/拦截点 | 风险 | 最近结果 | 脚本 | 下一步 |
|------|------|:--:|--------|-------------|:--:|----------|------|--------|
| F01 | 密友入口显示 | ⬜ 待测 | UI/Java | Settings入口 | 低 | — | — | 量子版验证 |
| F02 | 授权状态显示 | ⬜ 待测 | MyAuth | my_auth_prefs | 低 | — | — | 量子版验证 |
| F03 | 屏蔽列表管理 | ⬜ 待测 | UI/Java | wxid列表管理 | 中 | — | — | 量子版验证 |
| F04 | 会话隐藏 | ✅ 通过 | 数据源 | MvvmList.m/s + notify clean-before | 中 | Guard 8.0.71 P17 验收通过 | `ConvFilter.java` | 回归 |
| F05 | 朋友圈隐藏 | ⚠️ 实验验证通过 / 客户版禁用 | MvvmList源头 | MvvmList h/o/p + y1安全网 | **高** | v7.1: INIT清理有效，notifyItemRange*清洗 → 崩溃/进程终止 | `prod/filter_moments.js` | 走提交前过滤 |
| F06 | 点击拦截 | ❌ 未做 | onClick | item click event | 中 | — | — | F05通过后做 |
| F07 | 通讯录隐藏 | ❌ 未做 | MvvmAddressUIFragment | onBindViewHolder / SQL | 中 | 架构探索完成，数据模型与会话不同 | — | 待排期 |
| F08 | 语音/视频拦截 | ❌ 未做 | — | — | 高 | — | — | 待排期 |
| F09 | 朋友圈点击穿透修复 | ✅ 通过 | — | 从View层改回数据源层 | 低 | 已修复 | `prod/filter_moments.js` | — |

## 方案策略分级

```
✅ 优先: 数据源拦截 — MvvmList h/o/p 列表直接 remove（F04已验证）
✅ 兜底: 渲染前安全网 — notifyDataSetChanged 前清洗 h/o/p（F04/F05共用）
⚠️ 仅兜底: View层 — 只能做点击事件拦截，禁止做数据隐藏主方案
❌ 禁止: RecyclerView 后段改 position — 状态不一致
❌ 禁止: 改 Adapter getCount/getItem — 数据错位
❌ 禁止: View.GONE 做主方案 — ViewHolder复用污染 + 点击穿透
```

## 状态图例

| 符号 | 含义 |
|:--:|------|
| ✅ | 已验证通过 |
| 🔧 | 调试中，有已知阻塞 |
| ⚠️ | 部分有效，有已知问题 |
| ⬜ | 待验证 |
| ❌ | 未开始 |
| ❌🛑 | 禁止方案（已验证失败，不可复用） |

## 废弃/禁止方案

| 方案 | 判定 | 失败原因 | 日期 |
|------|:--:|----------|------|
| 8.0.70架构套8.0.66 | ❌🛑 | va5.y/m2/q9混淆名完全不同，CursorAdapter≠ListView | 2026-05-17 |
| 追h8.L9/g8.f/q3.X/f0.X链 | ❌🛑 | 消息处理链，非会话列表链 | 2026-05-17 |
| WCDB rawQuery | ❌🛑 | h8用r.a()自定义封装，不走标准rawQuery | 2026-05-17 |
| 改SparseArray Key | ❌🛑 | Key不连续，Flow覆盖 | 2026-05-17 |
| Hook K0() + notify | ❌🛑 | Flow重新下发原始数据 | 2026-05-17 |
| Hook K0()+getCount+notify | ❌🛑 | getCount→K0死循环 | 2026-05-17 |
| 重建SparseArray连续Key | ❌🛑 | Flow覆盖 | 2026-05-17 |
| getView层隐藏 | ❌🛑 | View修改不生效，Flow覆盖 | 2026-05-17 |
| v4: RecyclerView后段改position | ❌🛑 | position偏移导致数据错位 | 2026-05-17 |
| v5: View层+数据层混改 | ❌🛑 | 黑洞穿透，GONE item点击跳桌面 | 2026-05-17 |
| Java反射notifyDataSetChanged | ❌🛑 | Method.invoke() → SIGSEGV崩溃 | 2026-05-17 |
| 纯View.GONE隐藏 | ❌🛑 | ViewHolder复用污染 + 点击链未断 | 2026-05-17 |
| v7 notifyItemRange* clean-before | ❌🛑 | RecyclerView position错位 → 滑动崩溃 | 2026-05-18 |
| v7.1 notifyItemRange* clean-after sync | ❌🛑 | RecyclerView状态冲突 → 进程终止 | 2026-05-18 |
| v7.1 notifyItemRange* clean-after deferred | ❌🛑 | JNI local ref GC → SIGABRT | 2026-05-18 |
