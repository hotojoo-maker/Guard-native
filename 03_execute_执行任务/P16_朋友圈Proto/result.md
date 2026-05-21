# P16 result — 朋友圈 Proto 过滤

> 最后更新：2026-05-20 08:08 ✅ KPI 通过，P16 正式关闭
> 底座：微信 8.0.71 / 小米9 / LSPosed

---

## 完成状态

✅ D1 密友帖子整条隐藏 — 装机验证通过  
✅ D2 密友点赞隐藏 — 装机验证通过  
✅ D3 密友评论隐藏 — 装机验证通过  
✅ KPI frida_stats — 4 分钟采集，全部 ✅（见下表）

**P16 §D 主线可关单**；L0v3 气泡/小红点仍为独立待办。

---

## 最终 hook 架构

### D1 — 帖子过滤

| 项目 | 值 |
|------|-----|
| hook 点 | `ArrayList.addAll(Collection)` beforeHookedMethod |
| 触发条件 | collection 包含 `na4.b` 或 `la4.p` 元素 |
| posterWxid 提取路径 | `na4.b` → `.d`(la4.p) → `.c1()`(SnsObject) → `field_userName` |
| 过滤动作 | `list.remove(i)` + `sPendingRemoved++` |
| 防跳顶 | `getItemCount()` 回调减去 `sPendingRemoved`（e2 adapter，8.0.71 需重新找类名）|

### D2/D3 — 赞评过滤

| 项目 | 值 |
|------|-----|
| hook 点 | `LinkedList.add(Object)` beforeHookedMethod |
| 触发类 | `z15.e56` / `cs5.di0` / `i84.y` |
| wxid 字段 | `.d`（String） |
| 昵称字段 | `.e`（String） |
| 过滤动作 | `param.setResult(true)` 阻断 add |

---

## 关键 class 速查（8.0.71）

| 混淆类名 | 含义 |
|----------|------|
| `na4.b` | 朋友圈 feed 批次容器（含 N 条 la4.p） |
| `la4.p` | 单条帖子 ViewModel |
| `z15.e56` | 点赞/评论用户数据（主类） |
| `cs5.di0` | 点赞/评论用户数据（副类） |
| `i84.y` | wxid 轻量容器 |
| `qu5.g` | 懒加载包装（`l1`/`p1` 字段类型） |

## 关键方法速查（8.0.71）

| 方法 | 返回 | 说明 |
|------|------|------|
| `la4.p.c1()` | SnsObject | 帖子 protobuf 对象，含 `field_userName` |
| `la4.p.h1()` | TimeLineObject | 无 Like/Comment List getter（已证伪） |
| `la4.p.b1()` | SnsInfo | DB 层对象，含 `getLikeFlag/getUserName` |
| `la4.p.getCommentList()` | LinkedList | addAll 时刻始终 size=0（懒加载，已证伪） |

---

## 死路归档（本任务实证，F 编号待总调度分配）

| 路径 | 失败原因 |
|------|----------|
| `SnsObject.parseFrom` Java hook | F-27：走 JNI/C++，Java 层零命中 |
| `la4.p.getCommentList()` addAll 时拦截 | 懒加载，size 始终 =0 |
| `TimeLineObject(h1())` List getter | 无任何 getLike/getComment 方法 |
| `SnsObject(c1()).LikeUserList` 字段 | addAll 时刻字段为空 |
| `ArrayList.addAll` 拦截赞评 | 赞评不走 addAll，走 LinkedList.add 单条 |

---

## 验收日志（真实截图对应）

```
[MF:LLadd cls=z15.e56] d=wxid_toghm7m6uqsr12 e=马孔多在下雨
[MF:LLadd cls=cs5.di0] d=wxid_lzd2va16jd1622 e=熵
[MF] LLadd blocked: cs5.di0    ← 密友熵的评论被阻断
[MF] D2/D3 cleaned like=1 cmt=3
```

---

## KPI（frida_stats — 2026-05-20，4 分钟持续采集，8.0.71 + D1–D3）

| 指标 | 稳态 | 峰值 | 8.0.66 基线 | 安全上限 | 红线 | 判定 |
|------|:----:|:----:|:-----------:|:--------:|:----:|:----:|
| verifiedbootstate | 0 | 0 | 15 | 20 | 38 | ✅ |
| PROP (per 5s) | ~257 | 645 | 82.5 | 150 | 220 | ✅ |
| CONN (per 5s) | 0 | 11 | — | — | — | ✅ |
| DNS (per 5s) | 0 | 6 | — | — | — | ✅ |
| bl / dbg / maps / smaps | 0 | 0 | — | — | — | ✅ |
| KV_O (MMKV 写) | 0–3 | 7 | — | — | — | ✅ |

**门控结论**：全部未触红线，**KPI ✅ 通过**（F-22）。

---

## 代码文件

- `src/main/java/com/ghost/assist/moduleD/MomentsFilter.java` — 主实现
- `03_execute_执行任务/P16_朋友圈Proto/scripts/scan_la4p_heap.js` — 堆扫描（调试用）
- `03_execute_执行任务/P16_朋友圈Proto/scripts/scan_tlo_safe.js` — TimeLineObject 方法扫描（调试用）
