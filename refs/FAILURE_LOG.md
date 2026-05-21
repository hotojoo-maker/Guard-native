# FAILURE_LOG — 失败方案归档

> **铁律**: 已证实失败的方案，任何人不得复用。AI 接手必读。

---

## F-01: 8.0.70 架构套 8.0.66

| 项 | 内容 |
|----|------|
| 日期 | 2026-05-17 |
| 分类 | 架构假设错误 |
| 方案 | 用 Catfish 8.0.70 的 va5.y / m2 / hookNewCon 去套 8.0.66 |
| 症状 | va5.y 类不存在，m2 是 CursorAdapter 不是 ListView |
| 根因 | 8.0.66 和 8.0.70 混淆名、Adapter 类型、数据模型完全不同 |
| 教训 | **不要拿一个版本的架构套另一个版本。每个版本独立验证。** |

---

## F-02: 追 h8.L9/g8.f/q3.X/f0.X 调用链

| 项 | 内容 |
|----|------|
| 日期 | 2026-05-17 |
| 分类 | 追踪目标错误 |
| 方案 | 静态反编译推断的调用链作为 hook 目标 |
| 症状 | 所有 hook 点全零触发（warm-attach + spawn 双模式） |
| 根因 | 这是**消息处理链**，不是会话列表链。静态推断未动态验证。 |
| 教训 | **静态推断的调用链必须动态验证后才能作为 hook 目标。** |

---

## F-03: WCDB rawQuery 兜底

| 项 | 内容 |
|----|------|
| 日期 | 2026-05-17 |
| 分类 | SQL 层拦截失败 |
| 方案 | hook AOSP rawQuery / WCDB 2arg / WCDB 3arg，从 SQL 层拦截会话查询 |
| 症状 | 三种 hook 全零触发 |
| 根因 | h8 用 `r.a(String, Object[], int)` 自定义封装，不走标准 rawQuery() |
| 教训 | **微信数据库有自定义封装层，标准 SQL hook 打不到。** |

---

## F-04: 改 SparseArray Key

| 项 | 内容 |
|----|------|
| 日期 | 2026-05-17 |
| 分类 | 数据层修改失败 |
| 方案 | 修改 MvvmList 内部 SparseArray 的 Key 来跳过隐藏项 |
| 症状 | Key 不连续，getItem 时抛异常 |
| 根因 | SparseArray 非连续索引，跳过 Key 导致遍历逻辑断裂 |
| 教训 | **SparseArray 不能跳过 Key。直接改 h/o/p List。** |

---

## F-05: Hook K0() + notify

| 项 | 内容 |
|----|------|
| 日期 | 2026-05-17 |
| 分类 | Kotlin Flow 覆盖 |
| 方案 | Hook f45.s0.K0() 返回值 → 移除目标项 → notifyDataSetChanged |
| 症状 | 修改后瞬间恢复原状 |
| 根因 | Kotlin StateFlow 检测到变化后重新下发原始数据覆盖 |
| 教训 | **Adapter 层任何修改都会被 Flow 覆盖。必须从 h/o/p 源头改。** |

---

## F-06: Hook K0() + getCount + notify

| 项 | 内容 |
|----|------|
| 日期 | 2026-05-17 |
| 分类 | 死循环 |
| 方案 | Hook K0() + getCount() 联动修改 |
| 症状 | getCount → K0 → Flow → getCount 死循环，界面卡死 |
| 根因 | getCount 和 K0 互相触发形成循环依赖 |
| 教训 | **不要联动 hook Adapter 的多个方法。** |

---

## F-07: 重建 SparseArray（连续 Key）

| 项 | 内容 |
|----|------|
| 日期 | 2026-05-17 |
| 分类 | Flow 覆盖 |
| 方案 | 重建新的 SparseArray 用连续 Key → 替换到 MvvmList |
| 症状 | 替换后 Flow 重新下发原始数据，恢复原状 |
| 根因 | Kotlin Flow 是数据源头，不认可外部修改 |
| 教训 | **外部替换内部数据结构无效，Flow 持有原始引用。** |

---

## F-08: getView 层隐藏

| 项 | 内容 |
|----|------|
| 日期 | 2026-05-17 |
| 分类 | 渲染层修改失败 |
| 方案 | 在 f45.s0.getView() 中返回空 View 或隐藏 |
| 症状 | View 修改不生效 |
| 根因 | Kotlin Flow 直接驱动 UI，绕过 Adapter 传统 getView 路径 |
| 教训 | **8.0.66 MvvmList 下 Adapter 只是投影，getView 层修改无效。** |

---

## F-09: v4 — RecyclerView 后段改 position

| 项 | 内容 |
|----|------|
| 日期 | 2026-05-17 |
| 分类 | 渲染层修改失败 |
| 方案 | 在 onBindViewHolder 中改 position 跳过隐藏项 |
| 症状 | position 偏移 → 数据错位 → 点击 A 打开 B |
| 根因 | RecyclerView 内部持有 position 映射，外部修改导致不一致 |
| 教训 | **禁止在 RecyclerView 后段改 position。** |

---

## F-10: v5 — View 层 + 数据层混改

| 项 | 内容 |
|----|------|
| 日期 | 2026-05-17 |
| 分类 | 多方案混合失败 |
| 方案 | 同时改 View.GONE + MvvmList 列表 → 双重保障 |
| 症状 | "黑洞穿透" — 点击隐藏区域直接跳回桌面 |
| 根因 | View.GONE 断掉了 View 但点击事件链未断；同时两套修改互相干扰 |
| 教训 | **不要同时用两套方案。View 层隐藏不断点击链。** |

---

## F-11: Java 反射 notifyDataSetChanged

| 项 | 内容 |
|----|------|
| 日期 | 2026-05-17 |
| 分类 | API 调用方式错误 |
| 方案 | `ad.getClass().getMethod("notifyDataSetChanged").invoke(ad)` |
| 症状 | SIGSEGV code 1 SEGV_MAPERR，ART 运行时崩溃 |
| 根因 | Method.invoke() 触发 ART 空指针解引用 |
| 教训 | **Frida wrapper 直接调方法：`ad.notifyDataSetChanged()`。不用 Java 反射。** |

---

## F-12: 纯 View.GONE 做主方案

| 项 | 内容 |
|----|------|
| 日期 | 2026-05-17 |
| 分类 | UI 层方案有结构性缺陷 |
| 方案 | onBindViewHolder 中 setVisibility(GONE) 隐藏目标 |
| 症状 | ① ViewHolder 复用时 GONE 残留污染正常帖子 ② 点击事件链未断 |
| 根因 | RecyclerView 复用机制 + View 层修改不影响事件分发 |
| 教训 | **View.GONE 只能做辅助，禁止做主方案。** |

---

## F-13：notifyItemRange* clean-before（v7 原始方案）

| 项 | 内容 |
|----|------|
| 日期 | 2026-05-18 |
| 分类 | RecyclerView 通知层清洗失败 |
| 方案 | 在 notifyItemRangeInserted/Removed 中先清洗 o/p/h 再调原始方法 |
| 症状 | 慢滑几条后崩溃 |
| 根因 | DiffUtil 携带精确 position，提前清洗导致 RecyclerView 内部 position 错位 |
| 教训 | **禁止在 RecyclerView 通知方法中修改底层数据（无论前后）。** |

---

## F-14：notifyItemRange* clean-after deferred（v7 延迟清洗）

| 项 | 内容 |
|----|------|
| 日期 | 2026-05-18 |
| 分类 | JNI 引用生命周期 |
| 方案 | `Java.scheduleOnMainThread` 延迟清洗 → 清洗后 notifyDataSetChanged |
| 症状 | SIGABRT: JNI DETECTED ERROR: use of invalid jobject |
| 根因 | hook 中的 `this` 是 JNI local reference，异步回调执行时已被 GC 回收 |
| 教训 | **禁止在异步回调中捕获 hook 的 `this` 引用。JS 全局变量存 Java 对象是 global ref 不会 GC，但 hook 参数是 local ref。** |

---

## F-15：朋友圈后清洗方案性能不可接受

| 项 | 内容 |
|----|------|
| 日期 | 2026-05-18 |
| 分类 | 架构方案否决 |
| 方案 | 在 RecyclerView/DiffUtil 通知层清洗数据（三种变体：clean-before / clean-after-sync / clean-after-deferred） |
| 症状 | 三种变体均崩溃：position 错位 / 进程终止 / JNI invalid ref SIGABRT |
| 根因 | 数据已进入 RecyclerView/DiffUtil 状态机后再清洗，成本高且状态风险大。RecyclerView 对数据一致性有严格要求，外部修改底层列表必然导致不一致。 |
| 教训 | **notifyDataSetChanged 与 notifyItemRange* 有本质区别。notifyDataSetChanged 是全量刷新不带 position → clean-before 安全（v9.3验证通过）。notifyItemRange* 带 DiffUtil 精确 position → 任何修改都会导致状态不一致。会话和朋友圈都可以用 notifyDataSetChanged clean-before，不需要走 ArrayList.add 源头拦截。** |
| 裁决 | v7/v7.1 实验验证通过（证明目标可识别），notifyItemRange* 三种变体永久禁用。v9.3 (notifyDS clean-before + 定时器) 验证通过 ✅ |

```
Kotlin StateFlow (MvvmList.s/v)
  ↓
是 8.0.66 MvvmList 的单一数据源
  ↓
Adapter 只是投影，不持有数据
  ↓
任何不经过 Flow 的数据修改都会被覆盖
  ↓
唯一可行路径: 修改 Flow 底层的 h/o/p → Flow 最终重读 → UI 更新
  ↓
但 Flow 重读有延迟，且需要防死循环（g_cleaning + g_lastClean）
```
