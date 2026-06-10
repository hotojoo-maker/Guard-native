# P26 — 好友 V 态热切 fresh-item 触发（爬虫层结论）
> 执行日期：2026-05-25
> 状态：🟡 第 1 层爬虫完成（fresh-fetch 入口已锁定）；第 2 层探针待做
> 探针脚本：`02_tools_工具/dynamic_crawler_动态爬虫/probe_fresh_msg_trigger.js`
> 授权检查官：本次未涉及（爬虫只读）；第 3 层正式 hook 前必须预审

---

## 一、任务目标

V 态切换后好友 wxid 不出现（群正常）。根因：BUS-V 只能注回 stale `kc5.y`，好友 stale 渲染失败；需找到 WeChat 原生 **fresh-fetch 入口**，第 2 层在 BUS-V 时伪造触发。

---

## 二、爬虫结论（✅ 两轮采集一致）

### 2.1 微信原生 fresh-fetch 链路（NOTIFY #1–#15，0–75s）

```
kc5.r0.d                          ← 🔴 新鲜数据入口（第 2 层候选 hook 点）
  → MvvmList.w(ik3.o0)            ← ik3.o0 = 数据事件模型
    → ik3.m.invoke                ← Kotlin 协程回调
      → cl0.u.V                   ← 协程调度
        → ik3.n.handleEvent       ← 事件分发
          → h45.i.handleMessage   ← Handler 桥
            → Looper.loop
              → kc5.v0.notifyDataSetChanged
```

**入口锁定**：`kc5.r0.d()` — 网络/缓存新数据到达时触发，位于 `MvvmList.w` 上游。

### 2.2 WR 写路径（数据进 ArrayList，28 次全命中）

```
MvvmList.e
  ← MvvmList.k / MvvmList.l
    ← ik3.h0.invokeSuspend       ← Kotlin 协程挂起点
```

fresh item **写入**走 `MvvmList.e`，不经 L1 `MvvmList.n/m`，不经 L0 `ArrayList.addAll(kc5.y)` — 与 brief 根因一致。

### 2.3 两条 NOTIFY 链分工（✅ 实测可区分）

| 链 | 触发源 | 出现时段 | NOTIFY 编号 |
|----|--------|:--------:|:-----------:|
| **微信原生** `kc5.r0.d → MvvmList.w` | 网络/缓存新数据到达 | 0–75s | #1–#15 |
| **Guard 模块** `ConvHotReload.notifyConvAdapter` | Activity 生命周期（onStart/onFocus） | 154–179s | #16–#25 |

第 2 层探针验证时必须 **过滤 Guard 链**（#16+），只看 #1–#15 时段或发消息触发窗口内的栈。

---

## 三、与既有文档对照

| 文档 | 结论 | P26 验证 |
|------|------|:--------:|
| `CONV_REFRESH_PROBLEM.md` §六 | `ik3.n.handleEvent` 为最佳回放点 | ✅ 在链上，但 **更上游入口是 `kc5.r0.d`** |
| P26 brief | fresh 路径不走 L1 / L0 addAll | ✅ WR 28 次全走 `MvvmList.e`，0 次 L1 |
| P20C L0addAll 覆盖新消息 | 曾认为 addAll 可捕获热更新 | ❌ **应推翻** — addAll 对 fresh 路径无效 |

---

## 四、第 2 层探针方案（待做）

**目标**：BUS-V 时对 cache 里每个 wxid 调一次 fresh-trigger，让 WeChat 自己 fetch + 写 list + notify。

**候选入口（按优先级）**：

| 优先级 | 入口 | 依据 | 风险 |
|:------:|------|------|------|
| P0 | `kc5.r0.d()` | 两轮 Frida 栈顶 WeChat 帧，0–75s 原生链起点 | 需探针确认参数/实例获取 |
| P1 | `MvvmList.w(ik3.o0)` | 紧接 r0.d 下游，事件模型已构造 | 需构造合法 `ik3.o0` |
| P2 | `ik3.n.handleEvent(List)` | 文档 §七 方案 C 原选点 | 在链中段，可能缺 fresh 构造 |

**验证步骤**：
1. Frida 或 Java 内嵌探针：V 态手动 invoke `kc5.r0.d()`（或等价实例方法）
2. 观察密友 wxid 是否立刻出现在列表
3. 失败 2 次 → 退回重新爬 `kc5.r0` 方法签名 / 实例来源

---

## 五、禁止事项（继承 brief + 本轮新增）

- ❌ 第 3 层前不得改 `ConvHotReload.handleBusVisible` 正式逻辑（需授权检查官）
- ❌ 不得把 `ik3.n.handleEvent` 回放当唯一方案（冷启动/P26 实证：入口在更上游 `kc5.r0.d`）
- ❌ 不得再依赖 L0 addAll / L1 n/m 捕获 fresh item（已证伪）
- ❌ NOTIFY #16+ 栈含 `ConvHotReload` — 分析 fresh 路径时必须排除

---

## 六、验收清单

| 项 | 状态 | 备注 |
|----|:----:|------|
| `kc5.v0.notifyDataSetChanged` 调用栈采集 | ✅ | 两轮一致 |
| fresh-fetch 入口类名+方法名 | ✅ | **`kc5.r0.d`** |
| WR 写路径类名+方法名 | ✅ | **`MvvmList.e` ← `ik3.h0.invokeSuspend`** |
| 原生链 vs Guard 链分离 | ✅ | 时段 + 栈帧可区分 |
| 第 2 层 BUS-V 探针 invoke | ⬜ | 下一步 |
| 第 3 层 ConvHotReload 正式 hook | ⬜ | 需授权检查官 |

---

## 七、下一步（只列一步）

**第 2 层探针**：写 `probe_r0d_invoke.js`（或 Java 内嵌 1 hook），V 态手动 trigger `kc5.r0.d()`，装机看密友是否出现。

---

## 八、2026-05-27 收尾节

原 §七/§四 计划的 `kc5.r0.d / MvvmList.w / ik3.n.handleEvent` 本轮未采用（装机 0 enter 或不及验证）。实际装机走的是：
live adapter 字段图 + `kc5.x.h(id)` fresh-warm + `adapter.q.d` 双写双清 + **hidden id 作 key**。

### L1 实证
`final_v24_full_fresh_restore_grep.log`：
```
WXID-MISMATCH id=44786160583@chatroom extracted=wxid_lzd2va16jd1622 using id as key
h(44786160583@chatroom) -> kc5.y wxid=44786160583@chatroom
cache=2 expanded=3  visibleSnap=3
BUS-V-adapter.q field=d injected=3 owner=kc5.a
```
V 态 3 个会话（密友×2 + 密群×1）同时出现。

### 残留项（已在本任务内闭包）
2026-05-27 v25/v26 尝试在 `injectCacheIntoList` / `restoreToMvvmList` 加 `item == itemToInject` identity check + `IdentityHashMap` list-visited 双层强 dedup → 装机实证 `BUS-V-adapter.p` 零条、肉眼全不显示（F-35 已归档）。

最终走 **v27 = v24 wxid-only dedup 保留 + 80ms 异步 `postDedupAdapterGraph` 按 identity 收敛同对象引用**，IK3n install 顺手补回 ConvFilter.install()。当前线上方案细节见 `docs/CONV_REFRESH_PROBLEM.md` §十七。

### v28 = v27 + CME 防御 + 密群保底检（**装机完美通过**）

v27 装机暴露两条残留毛刺，当晚就地补完：
1. `restoreToMvvmList` dup-scan / insert 加 CME catch；下标访问替代 for-each
2. 新增 `ConvFilter.extractGroupId`；`filterConvList` Phase 2 重写为 CME-safe + 双 key 兜底

#### v28 L1 装机实测
| 锚点 | 结果 |
|------|------|
| 冷启动 H 态不露 | ✅ |
| H 态新消息不露（密友+密群） | ✅ |
| V↔H 5 轮热切 | ✅ 完美 |
| 冷启动→切 V 全恢复 | ✅ |
| warmAll expanded=3 + WXID-MISMATCH 修复 | ✅ |
| CME / ConcurrentModification | 0 条 |

证据：`bug排查/final_v28_5rounds.log` + `bug排查/final_v28_coldstart.log`

P27_密群去重 / P28_CME 防御 **均不开**，全部就地在 ConvFilter / ConvHotReload 主体补完。
