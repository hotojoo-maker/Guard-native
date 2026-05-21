# P19 通讯录隐藏 — 实机验证通过

**日期**: 2026-05-20  
**状态**: ✅ 装机确认

---

## 验证结果

```
05-20 19:34:44.298 I NCL: [CTF:addAll] removed=1/30
05-20 19:34:45.185 I NCL: [CTF:addAll] removed=1/30
```

分段虚拟滚动（每段 30 条），密友每段被过滤 1 条，符合预期。

---

## 8.0.71 通讯录入口（L1，实机确认）

| 项目 | 值 |
|------|-----|
| 版本 | 微信 **8.0.71** 通讯录 Tab |
| 数据入口 | `ArrayList.addAll(Collection)` beforeHook |
| 条目类型 | `fc5.g`（`e==2` 普通联系人） |
| 分段 | 每批约 **30** 条虚拟滚动 |
| wxid | `fc5.g.d` → `z3.c1()` |
| 动作 | `Iterator.remove()` |
| 验收日志 | `[CTF:addAll] removed=1/30` |

> **口径**：上述 addAll 规律仅对 **8.0.71 通讯录** 成立；P17 会话仍走 MvvmList 层（见 `P17_会话LSPosed/result.md`），勿混写「三模块共性」。

---

## 最终 hook 路径

```
ArrayList.addAll(Collection)          ← 8.0.71 通讯录数据入口（L1）
  └── item instanceof fc5.g           ← 通讯录联系人条目
        └── fc5.g.d → z3.c1()        ← 提取 wxid
              └── Bridge.isHidden()   ← 是否在密友列表
                    └── it.remove()   ← 从 Collection 中删除
```

**关键字段路径**: `fc5.g.d` → `z3` 实例 → `z3.c1()` → `String wxid`

---

## 失败路径（已排除）

| 方案 | 失败原因 |
|------|---------|
| `MvvmList.n(List, boolean)` | 数据不走该方法，零触发 |
| `MvvmList.u(List, boolean)` | 同上 |
| `AddressLiveList` 构造函数 | 启动时序问题，模块 install 晚于 UI 初始化 |
| `RecyclerView.setAdapter` | 能探到 Fragment 但拿不到数据层引用 |
| `fragResume onResume/onHiddenChanged` | Fragment 生命周期回调可以探到，但 `findFieldByType` 反射取 AddressLiveList 不稳定 |

---

## 跨模块对照（勿当共性结论）

| 模块 | 8.0.71 已确认入口 | 证据任务 |
|------|-------------------|----------|
| 通讯录 F07 | **`ArrayList.addAll` + `fc5.g`** | **P19 ✅ 本任务** |
| 朋友圈 D1 | `ArrayList.addAll` + `la4.p` | P16 |
| 会话 F04 | `MvvmList.n/m` + notify 兜底 | P17（非 addAll 主路径） |

---

## ContactFilter.java 关键实现

```java
private static void installAddAllHook() {
    XposedBridge.hookMethod(
        ArrayList.class.getMethod("addAll", java.util.Collection.class),
        new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                Collection<?> col = (Collection<?>) param.args[0];
                if (col == null || col.isEmpty()) return;
                Object first = col.iterator().next();
                if (!first.getClass().getName().equals("fc5.g")) return;
                // 只过滤 type==2 的普通联系人（排除群组/公众号）
                Iterator<?> it = col.iterator();
                int removed = 0;
                while (it.hasNext()) {
                    Object item = it.next();
                    String wxid = extractFc5gWxid(item);
                    if (wxid != null && Bridge.getInstance().isHidden(wxid)) {
                        it.remove();
                        removed++;
                    }
                }
                if (removed > 0) {
                    Log.i(TAG, "[CTF:addAll] removed=" + removed + "/" + before);
                }
            }
        });
}

private static String extractFc5gWxid(Object item) {
    try {
        Object d = item.getClass().getDeclaredField("d").get(item);
        if (d == null) return null;
        return (String) d.getClass().getMethod("c1").invoke(d);
    } catch (Throwable t) { return null; }
}
```

---

## 后续待做（P20+）

- `hookSearchContact`: 搜索拦截（假返回"未找到"）
- `A4 密码解锁 1111`: EditText 文本监听
- `B 模块 6 个触发事件`: 摇一摇/切后台/Home/返回/锁屏/搜索
