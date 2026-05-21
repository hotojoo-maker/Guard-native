# P19 / F07 通讯录隐藏 — Hook 点速查（8.0.71）

> Catfish 逻辑在 `refs/`，**8.0.71 混淆类名待 T07 设备 dump**。本文档供接 P19 实现前阅读。

---

## Catfish 已证实入口（L2，8070/甜密友）

| Hook | 签名 | 作用 | 难度 |
|------|------|------|:----:|
| **hookAddressInfo** | `boolean hookAddressInfo(String user)` | 单个 wxid 是否密友 → `true` = 应隐藏 | ⭐ |
| **hookContactCount** | `int hookContactCount(int count)` | 通讯录总人数减去密友数 | ⭐ |
| **hookAddressUI** | `List<String> hookAddressUI(List<String> list)` | 通讯录 UI 列表注入黑名单 wxid（`addBlackList`） | ⭐⭐ |
| **hookAddressInfo** 重载 | `ArrayList<String> hookAddressInfo(ArrayList<String> list)` | 同上，`addBlackList2` | ⭐⭐ |

**门控**：`isVipMode()==true`（隐藏态）时才过滤；显形时 MainEntry 直接透传。

### Catfish 核心逻辑（`UserControll.java`）

```java
// 单项判断 — 微信侧问「这人要不要显示」
boolean hookAddressInfo(String user) {
    return vipSecret.contains(user);  // true = 密友 → 藏
}

// 计数修正
int hookContactCount(int count) {
    return count - vipMemberCount;  // 或 0 若密友数>=count
}
```

`addBlackList` / `addBlackList2`：把密友 wxid **并入**传入的 String 列表（供 UI 层按黑名单过滤），不是 remove。

---

## 8.0.71 通讯录入口（L1，P19 实机 2026-05-20）

| 项目 | 值 |
|------|-----|
| 数据入口 | `ArrayList.addAll(Collection)` |
| 条目 | `fc5.g`（type=2 联系人） |
| wxid | `fc5.g.d` → `z3.c1()` |
| 日志 | `[CTF:addAll] removed=1/30` |
| 已排除 | `MvvmList.n/u`、AddressLiveList 构造时序、setAdapter |

| 任务 | 状态 |
|------|:----:|
| T07 Contact 字段 / 列表入口 | ✅ addAll+fc5.g |
| Catfish `hookAddressInfo` 等四项 | ⬜ v2 计数/搜索互补 |

### 已知稳定（跨版本倾向）

| 类/概念 | 8.0.66 | 8.0.71 备注 |
|---------|--------|-------------|
| Contact 存储 | `com.tencent.mm.storage.m3` | 待 T07 确认仍成立 |
| wxid getter | `m3.j1()` | ConvFilter 已用多 getter 兜底 |
| 架构提示 | `MvvmAddressUIFragment` | FEATURE_MATRIX；**≠ 会话 MvvmList** |

---

## 实现建议（Guard Native）

| 优先级 | 文件/动作 | 说明 |
|:--:|-----------|------|
| P0 | `ContactFilter.java` | ✅ `installAddAllHook` — **8.0.71 通讯录专用入口** |
| P1 | notify / fragResume 兜底 | ✅ 已装，非主路径 |
| P1 | `hookSearchContact` | ⬜ 与列表隐藏互补 |
| P2 | Catfish `hookAddressInfo` / `hookContactCount` | ⬜ 人数/单项门控，非列表注入 |

**禁止**：把 P19 的 addAll 规律写成 P17 会话主路径；会话见 `ConvFilter`（MvvmList 层）。

---

## 验收

1. 隐藏态 + 密友 wxid 在名单  
2. 打开通讯录 → **看不到该联系人**  
3. 顶部联系人数量合理（`hookContactCount`）  
4. `frida_stats` KPI 无红线  

---

## 参考路径

- `docs/HOOK_MAP_V1.md` §6–7  
- `docs/HOOK_POINTERS.md` §通讯录  
- `refs/MainEntry.java` 303–325, 374–381  
- `refs/UserControll.java` 214–216, 837–843  
- `02_docs_资料员/T_TASKS/T07_ContactStorage_8066.md`
