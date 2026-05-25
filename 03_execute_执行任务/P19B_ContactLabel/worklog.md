# P19B 工作日志

## 当前卡点
无。build 装机验证通过，任务关闭。

## 已验证事实（✅）
| 事实 | 证据 |
|------|------|
| 标签成员列表密友不可见 | 用户 build 装机验证 2026-05-24 |
| 标签内「添加」搜索密友不可见 | 同上 |
| hook 安装 | 代码 `[CTF] ArrayList.addAll(ye5.j) label hook ok` |
| 过滤命中日志 tag | `[CTF:label] ye5.j removed=N/M`（ContactFilter.java） |
| item 类 | `ye5.j`（Frida 探针 `[CLB] addAll itemCls=ye5.j`） |
| wxid 字段 | `ye5.j.d`，格式 `wxid_xxx-N-M`，去后缀后匹配 |

## 尝试过的方案
| 方案 | 结果 | 状态 |
|------|------|------|
| P19 `fc5.g` addAll 扩到标签 | 标签页不走 fc5.g，仍泄漏 | ❌ |
| Frida probe_label_tab v1 | 全局 hook 卡顿 | ❌ 换 v2 |
| `ArrayList.addAll(ye5.j)` remove | 路径 A+B 均生效 | ✅ |

## 下一步
无（已关闭）。HOOKMAP F07B + TASK_BOARD 已同步。
