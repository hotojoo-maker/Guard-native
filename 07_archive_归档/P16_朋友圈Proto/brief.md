# P16 brief — 朋友圈过滤（8.0.71）

> 执行窗口直接读这个。详情见 `result.md`（L1 装机结论）。

---

## 当前状态（2026-05-20）

✅ **D1 / D2 / D3 + KPI** 装机验证通过（8.0.71，2026-05-20）  
⬜ L0v3 互动气泡/顶部提醒 · 小红点（另开，非 P16 关单阻塞）

**前提**：**隐藏态**（`isVipMode()==true`）+ 密友名单非空。

---

## 有效代码（真正工作）

| 代码 | 作用 | 状态 |
|------|------|:----:|
| `na4.b` / `la4.p` → `extractPosterWxid` → D1 `toRemove` | 发帖人 wxid 在密友名单 → 整条不显示 | ✅ |
| `extractWxid` → `addFeedWxid` | 调试台「朋友圈发现 wxid」 | ✅ |
| `e2.getItemCount` hook | 删帖后扣 count，防跳顶 | ✅ |
| `dumpItem` / `extractPreview` / `addRawFeedLine` | Web 调试台（DEV/HONEY） | ✅ |
| `LinkedList.add` block | D2/D3：`z15.e56`/`cs5.di0`/`i84.y` → `.d`=wxid | ✅ |
| `installD3Hook` | `getCommentList`/`getLikeUserList` after 过滤（辅线） | ✅ 辅线 |
| L0v3 / unread | 气泡/小红点 | ⬜ 待验证 |

logcat：`[MF] D1 removed=` / `[MF] LLadd blocked:` / `[MF] D2/D3 cleaned`

---

## wxid 双路径（v19，勿混用）

**D1 过滤（发帖人）— `extractPosterWxid`（L1）**

```
na4.b → .d → la4.p → c1() → SnsObject.field_userName
```

**发现列表（外层社交 wxid）— `extractWxid`**

```
na4.b → .d → la4.p.field_userName
la4.p → .field_userName（直读）
```

**L0v3 互动 wxid（v20）**

| 类 | 提取方式 |
|----|----------|
| `SnsMsgUIWithRelevance` | `getUsername` / `username` / `reply_username` / 深度扫描 `wxid_` |
| `jw1.d` | `.f` / `WeakReference.e` 内递归扫描 |

---

## Hook 点（8.0.71）

| 入口 | 类 | 逻辑 | 状态 |
|------|-----|------|:----:|
| D1 | `ArrayList.addAll` + `na4.b`/`la4.p` | `c1().field_userName` → remove | ✅ L1 |
| D2/D3 主线 | `LinkedList.add` | `z15.e56` 等 `.d` → `setResult(true)` 阻断 | ✅ L1 |
| D2/D3 辅线 | `la4.p.getCommentList()` 等 afterHook | list 内 remove | ✅ L1 |
| 防跳顶 | `e2.getItemCount` | pending 扣减 | ✅ L1 |

---

## 类名速查（8.0.71）

| 角色 | 类名 | 备注 |
|------|------|------|
| 好友帖子 | `na4.b` | addAll item |
| 推广/内层 | `la4.p` | ImproveSnsInfo |
| SnsInfo | `rl.ta` 子类 | `b1()` 返回，D1 发帖人 wxid |
| Adapter | `e2` | `getItemCount` 防跳顶 |
| ViewHolder | `za3.c` | 纯 UI，排除 |
| MvvmList item | `jk3.c` | 不在 addAll，排除 |

**8.0.66 归档**：`k24.b` / `i24.p` / `y1` → 见 `result.md` 对照表

---

## 相关 F-xx

| 编号 | 一句话 |
|------|--------|
| F-27 | parseFrom Java hook 零命中 |
| F-28 | MvvmList.m/s 类级 hook 禁止 |
| F-30 | getView/GONE 不适用 8.0.71 |

---

## 待完成

- [x] D1 密友帖过滤（`extractPosterWxid`）
- [x] 调试台 wxid 发现（`extractWxid` + `addFeedWxid`）
- [x] 防跳顶（`getItemCount`）
- [x] D2 密友点赞隐藏
- [x] D3 密友评论隐藏
- [x] KPI `frida_stats.js`（F-22，2026-05-20 通过）
- [ ] L0v3 气泡/小红点

---

## 关键文件

```
src/main/java/com/ghost/assist/moduleD/MomentsFilter.java   v20
03_execute_执行任务/P16_朋友圈Proto/result.md
logs/classnames_8071.log
```
