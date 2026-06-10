# P_ConvWarm result — 冷友 H→V 立刻显示

> 完成：2026-05-25  
> 状态：✅ 装机验证通过  
> 关联：`docs/CONV_REFRESH_PROBLEM.md`（完整时间线见 §十一，方案见 §十二）

---

## 验证日志（L1 原文）

```
[CF:xFind] kc5.x found via field walk: kc5.x
[CF:xFind] h() resolved
[CF:warm:BUS-V] h(wxid_lzd2va16jd1622) → y ok
[CF:warm:BUS-V] warm=1 skipped(hot)=0
[CF:warm:BUS-V] injected=3
[BUS:BUS-V-done] notified adapter=v0
```

用户视觉确认：H→V 切换后冷密友立刻出现在会话列表。

---

## 实现位置

`src/.../moduleD/ConvHotReload.java`
- `findXFromConvGraph()` — kc5.v0 字段遍历找 kc5.x 实例
- `warmColdFriends(String tag)` — BUS-V 时对冷友调 h(wxid)，注入 MvvmList
- `handleBusVisible()` — 先 warm，再 r0.d，再 notify

---

## 四条死路（禁止再试）

| 方向 | 失败原因 |
|------|---------|
| `kc5.x.k(0, wxid)` invoke | Kotlin 协程 dispatcher 不介入，空壳 |
| `kc5.x.d()` hook via lpparam | classloader 分裂，shadow class，永不触发 |
| `kc5.y.<init>()` bootstrap | classloader 分裂双向阻隔 |
| stale cache 注入 | WeChat ViewModel 静默拒绝过期 JNI ref |

详细根因 → `docs/CONV_REFRESH_PROBLEM.md` §十一时间线

---

## 已知限制

零历史好友（WCDB 无 l4）：`h(wxid)` 返回 null，warmColdFriends 跳过。  
等用户与该好友首次发消息后，La 触发，自动进热路径。这是 WeChat DB 固有限制。
