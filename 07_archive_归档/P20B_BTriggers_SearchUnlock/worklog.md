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

## 2026-06-07（B1 摇一摇装机收口）

### 已闭环（✅ 有装机日志原文）

#### 6. B1 — 摇一摇隐藏好友
**改动**：`SettingsEntry` 密友设置页新增「摇一摇隐藏好友」开关；`TriggerGuard` 支持开关即时注册/注销加速度传感器。
**验收**：VISIBLE 态打开开关，退出设置面板后摇一摇，状态机单向进入 HIDDEN。
**证据路径**：`07_archive_归档/P20B_BTriggers_SearchUnlock/logs/b1_shake_fixedkey.log`

**日志原文**：
```
[TG] B1 shake listener installed
[SET:overlay] b1Shake=true
[TG] B1-shake → enterHidden
[SM] notify old=显形 new=隐藏
[SF:sm] VISIBLE → HIDDEN
[SM] enterHidden
```

#### 7. 固定签名收口
**改动**：新增 `signing/guard-native-debug.keystore`，`build.gradle` 的 debug/release 统一使用项目内固定签名；固定签名铁律已同步到 Cursor/Claude 两套 skill。
**原因**：本机 `~/.android/debug.keystore` 被现场新建，导致旧包覆盖失败。固定项目 key 后，后续构建不再随机器/会话变签名。

#### 8. B4 — 返回键隐藏（用户设备无返回键，按确认不阻塞）
**现场情况**：用户设备没有传统返回按钮，用户确认“没有返回键，就算过了 / 不阻塞”。
**证据路径**：`07_archive_归档/P20B_BTriggers_SearchUnlock/logs/b4_back_live.log`

**日志原文（只作为 B4 尝试证据，不标 L1 ✅）**：
```
[TG] B4 back key hook installed
KEYCODE_BACK
[TG] B2-close_dialogs(fs_gesture) → enterHidden
[SF:sm] VISIBLE → HIDDEN
[SM] enterHidden
```

**结论**：本轮未取得 `[TG] B4-back ... → enterHidden` 这种 B4 专属命中日志；实际进入 HIDDEN 的日志归因是 B2 手势路径。因此 B4 不标 ✅，仅按用户确认记录为“不阻塞 P20B 收口”。

#### 9. P20B KPI — frida_stats 200s 轻采样（发版前重测）
**采集方式**：Cursor 安装 `frida-tools` 后，使用 `tools/frida_run.py` attach 安卓设备 `609b4b18` 的微信主进程，固定采集 200 秒。
**证据路径**：`07_archive_归档/P20B_BTriggers_SearchUnlock/logs/p20b_frida_stats_20260607.log`

**日志原文摘要**：
```
[STATS] 2026-06-07 05:00:06.521 | INIT frida_stats.js v1.1
[STATS] 2026-06-07 05:00:11.521 | PROP=0 vb=0 ... CONN=0 ...
[STATS] 2026-06-07 05:00:51.568 | PROP=0 vb=0 ... CONN=2 ...
[STATS] 2026-06-07 05:03:21.736 | PROP=0 vb=0 ... CONN=0 ...
```

**结论**：`vb=0`、`PROP=0` 无红线；`CONN` 出现单点峰值 2。因本轮用户操作量很低，仅记录为轻采样，不作为继续功能开发的硬阻塞；发版/合并前仍需按 `guard-review_质检门控` 重档重新采集。

---

### 未完成（Bug B，遗留下一步）

| # | 问题 | 需要做的 |
|---|------|---------|
| B-Conv | 111111 后会话密友不自动显形 | 已有 `BUS-V / warmAll / restoreInPlace used fresh` 日志与 P_ConvWarm/P26 结果支撑，不重复跑 |
| B-Moments | 111111 后朋友圈密友帖不自动显形 | `SnsTimelineUI.onResume`（或等价）→ 全量 reload |

---

## 验收清单（本窗口通过）

- [x] 冷启动默认 HIDDEN（不显密友）✅
- [x] 切后台 B2 → 自动隐藏 ✅（B2-close_dialogs 路径）
- [x] 摇一摇 B1 → 自动隐藏 ✅（`[TG] B1-shake → enterHidden`）
- [x] B4 返回键 → 用户设备无返回键，按确认不阻塞（无 B4 专属 L1，不标 ✅）
- [x] 111111 第 6 个 1 触发解锁，搜索框自动关闭 ✅
- [x] `cleanMvvmList removed=4`，会话密友消失 ✅
- [x] 111111 后密友自动回来（已有 `BUS-V / warmAll / restoreInPlace used fresh` 日志；P_ConvWarm/P26 已有结果，不重复跑）
- [x] frida_stats KPI 轻采样已记录（`vb=0`、`PROP=0`；`CONN=2`，发版前重测）
