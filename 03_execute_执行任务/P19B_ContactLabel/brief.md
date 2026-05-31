# P19B brief — 通讯录【标签】泄漏修复

> 执行窗口直接读这个，不用翻 TASK_BOARD 全文

## 当前状态
✅ 装机 build 验证通过（2026-05-24）；HOOKMAP F07B 已更新

## 本任务 hook 点
| hook | 类 | 方法/入口 | 状态 |
|------|-----|------|:--:|
| 标签 list | `ye5.j` | `ArrayList.addAll(Collection)` beforeHook | ✅ |
| 主列表（P19，不动） | `fc5.g` | 同上 | ✅ 已有 |

## wxid 提取路径
`ye5.j.d` = `"wxid_xxx-N-M"` → 去后缀 `-N-M` → 匹配 `Bridge.allHiddenIds()`

## 覆盖路径
- **A** 已有标签 → 成员列表看不到密友
- **B** 标签内「添加」→ 搜索密友不命中

## 相关 F-xx
| 编号 | 一句话 |
|------|--------|
| F-31 | 已验证 hook 禁止顺手优化 |
| 铁律30 | 主进程过滤读 Java StateMachine |

## 关键文件
- `src/main/java/com/ghost/assist/moduleD/ContactLabelMemberFilter.java` — `install()` / `extractLabelWxid()`（2026-05-31 独立成模块；原在 `ContactFilter.installLabelAddAllHook()`）
- `scripts/probe_label_tab.js` — 发现探针（v2 轻量）
- `docs/HOOK_MAP_8071_AUTHORITATIVE.md` §6b — hook 事实权威（HOOKMAP 只记 F07 主列表；标签事实不入 HOOKMAP，见 worklog 2026-05-31 口径）
