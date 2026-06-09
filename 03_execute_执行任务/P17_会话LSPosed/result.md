# P17 会话 LSPosed — 进度报告

日期：2026-05-20  
底座：**微信 8.0.71**  
状态：**✅ 验收通过**

---

## 完成项

| 项 | 结论 | 证据 |
|----|------|------|
| F04 会话列表隐藏密友 | ✅ | 用户装机验收 |
| 实现 | `ConvFilter.java` | MvvmList.m/s + notify clean-before + warm-attach |
| 门控 | `StateMachine.isVipMode()` + 密友名单 | 同 P16 |

---

## Hook 架构（8.0.71）

| 层 | 点 | 说明 |
|----|-----|------|
| L1 | `MvvmList.n(List, boolean) (8.0.71) / .m (8.0.66)` | 主入口，filter 列表 |
| L2 | `MvvmList.s(List)` | 备用 |
| L4 | Adapter `notifyDataSetChanged` clean-before | 渲染前兜底 |
| INIT | `ConversationListView` warm-attach | 首次进入清一次 |

wxid：多路径反射（`CONTACT_FIELD_NAMES` + `WXID_GETTER_NAMES`），不绑死 8.0.66 混淆名。

---

## 代码

```
src/main/java/com/ghost/assist/moduleD/ConvFilter.java
src/main/java/com/ghost/assist/ModuleMain.java  → ConvFilter.install()
```

---

## 下一步（非 P17 阻塞）

- KPI：若 P17 关闭前未单独跑 frida_stats，可与下一 P 任务合并采集
- 通讯录 F07：见 `P19_通讯录隐藏/brief.md`
