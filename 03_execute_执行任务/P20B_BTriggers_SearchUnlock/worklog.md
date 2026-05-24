# P20B worklog — B 模块触发器 + 搜索解锁

> 执行窗口：W1 / 2026-05-22
> 对应 brief：`brief.md`

---

## 2026-05-22 04:00–04:36（本窗口）

### 已闭环（✅ 有装机日志原文）

#### 1. Bug A — extractWxid / MVVMLIST_ARRAY_FIELDS
**现象**：`cleanMvvmList removed=0`，密友始终可见。
**根因**：`WXID_GETTER_NAMES` 缺少 `C0`，broad-scan 兜底有时失效；`dumpMvvmListFields` 加上后确认 MvvmConvList 字段 `o/p/h` 正确，item 类为 `kc5.y`，wxid getter 是 `l4.C0()` 不是 `l4.h1()`。
**修法**：`WXID_GETTER_NAMES` 首位加 `"C0"`。
**日志原文**：
```
[CF:mvvmdump] .o[List sz=11 item=kc5.y]
[CF] wxid found via broad-scan: C0()=wxid_lzd2va16jd1622  ← 修前
[CF:clean] removed=4  ← 修后快路径
```

#### 2. Bug F-27 — 冷启动恢复 VISIBLE
**现象**：111111 解锁后，下次冷启动恢复 `显形`，密友可见。
**根因**：`StateMachine.restoreState()` 无条件恢复持久化状态，违反 F-27（native 状态优先，启动默认 HIDDEN）。
**修法**：`restoreState()` 强制任何非 HIDDEN 状态 → HIDDEN。
**日志原文**：
```
[SM] restoreState: saved=显形 → forced HIDDEN (F-27)   ← 修后
[SM] restored state=隐藏 ✅
```

#### 3. Bug B6 — SearchUnlock 需要 7 个 1 才触发
**现象**：`111111`（6个）无效，`1111111`（7个）才触发，靠 SearchFilter z15.ef6 兜底路径。
**根因链**：
1. 构造器 hook → `PasterEditText.addTextChangedListener` 构造时 `mTextWatcherList=null` → NPE → TextWatcher 没装上
2. 改 `EditText.onAttachedToWindow` → PasterEditText 不声明此方法，只命中基类 View 的版本，仍没装上
3. **正确修法**：hook `android.view.View.onAttachedToWindow` + `instanceof EditText && cls.contains("EditText")` + per-instance `setTag` 去重

**真实 EditText 类名**：`com.tencent.mm.ui.tools.ActionBarSearchView$ActionBarEditText`（View 子类，属 EditText 体系）

**日志原文**：
```
[SU] View.onAttachedToWindow hook ok pwdLen=6
[SU] view attached class=...ActionBarEditText id=0x7f091b21
[SU] watcher installed id=0x7f091b21
[SU] afterTextChanged text=111111 len=6 cls=ActionBarEditText
[SU] unlock matched text=111111 len=6
[SU] activity finished=FTSMainUI ✅
```

---

### 只读分析闭环

#### 4. web 刷新"触发"隐藏 — 时序巧合
所有 DebugServer GET API 均只读无副作用，hiding 来自 warm-attach 2s 定时器巧合触发。无需改代码。

#### 5. MomentsFilter H→V 后密友帖不显形 — 只读分析
- `filterOn = StateMachine.isActive() && !hidden.isEmpty()` 每次实时计算，无缓存
- VISIBLE 后 `filterOn=false`，新 addAll 不过滤 ✅
- **根因是"旧分页数据回不来"**：H 状态下已被 remove 的 page item 不会重新 load
- 下拉刷新只拉新帖，不重载旧页
- **临时绕过**：退出朋友圈 → 重进，WeChat 从头 reload → 密友帖出现

---

### 未完成（Bug B，遗留下一步）

| # | 问题 | 需要做的 |
|---|------|---------|
| B-Conv | 111111 后会话密友不自动显形 | `LauncherUI.onResume` hook → RefreshBus → 触发 ConvFilter reload |
| B-Moments | 111111 后朋友圈密友帖不自动显形 | `SnsTimelineUI.onResume`（或等价）→ 全量 reload |

---

## 验收清单（本窗口通过）

- [x] 冷启动默认 HIDDEN（不显密友）✅
- [x] 切后台 B2 → 自动隐藏 ✅（B2-close_dialogs 路径）
- [x] 111111 第 6 个 1 触发解锁，搜索框自动关闭 ✅
- [x] `cleanMvvmList removed=4`，会话密友消失 ✅
- [ ] 111111 后密友自动回来（Bug B，未实现）
- [ ] frida_stats KPI 对比（待跑）
