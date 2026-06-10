# P26 工作日志

## 2026-05-27 H 态压制 + V↔H 全量热切

### 已验证（✅ L1）
| 事实 | 证据 |
|------|------|
| H 态新消息仅出在 `L4adapter.q.d`（owner=kc5.a） | final_v20/v21/v24 多轮 `[CF:L4adapter.q] removed=1` |
| H→V 热切需 fresh `kc5.y`，stale 不渲染 | docs/CONV_REFRESH_PROBLEM.md §十二 |
| `extractWxid` 在群聊 kc5.y 抽到的是成员 wxid，不是 chatroom id | final_v23_1_wxid_mismatch_grep.log |
| v24 hidden-id-as-key 修复后全量热切生效 | final_v24_full_fresh_restore_grep.log：expanded=3 / injected=3 / V 态同显 |

### 本问题上已证伪、禁止再押的路径
`ik3.n.handleEvent(List)` / `MvvmList.e/k/l` Java hook / `seen.contains(id) -> skip` / `extractWxid(item)` 作热切 dedup key。

### 已收口（2026-05-27 晚 · v28 装机完美通过）
多轮 BUS-V 重复渲染密群的修复：v25 跨 List identity / v26 list-visited 双层强 dedup 两条路装机零显示（F-35 已归档），最终走 **v27 = v24 wxid-only dedup + 80ms 异步 `postDedupAdapterGraph` + IK3n install 补回**。详 `docs/CONV_REFRESH_PROBLEM.md` §十七。

v27 装机后发现两条残留毛刺，**v28 当晚就地补完**（不另开 P27）：
1. `restoreToMvvmList` dup-scan 改 size+get(i) + CME catch；insert 段独立 try/catch CME
2. 新增 `ConvFilter.extractGroupId`；`filterConvList` Phase 2 改快照 + 双 key（wxid 主 / groupId 保底） + 逐项 list.remove + CME catch

### v28 装机实测（2026-05-27 晚 · 证据备份在 `bug排查/final_v28_*.log`）
| 锚点 | 结果 |
|------|------|
| 冷启动 H 态不露 | ✅ |
| H 态新消息不露（密友+密群） | ✅ |
| V↔H 5 轮热切 | ✅ 完美 |
| 冷启动→切 V 全恢复 | ✅ |
| warmAll expanded=3 + WXID-MISMATCH 修复 | ✅ |
| CME / ConcurrentModification | 0 条 |

P26 / P27 / P28 全部就地闭包。
