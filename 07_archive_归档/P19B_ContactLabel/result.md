# P19B 通讯录【标签】泄漏 — 实机验证通过

**日期**: 2026-05-24  
**状态**: ✅ build 装机确认（用户验证 2026-05-24）；2026-05-31 重构期从 `ContactFilter` 丢失后独立成 `moduleD/ContactLabelMemberFilter.java`，用户现场复验 ✅

---

## 验证结果

| 路径 | 操作 | 结果 |
|------|------|:--:|
| **A** | 通讯录 → 标签 → 进入含密友的标签列表 | 密友不可见 ✅ |
| **B** | 标签内 → 添加 → 搜索密友昵称/wxid | 不可搜到 ✅ |

**预期 logcat**（隐藏态 + 密友在名单）：

```
I NCL: [CLM] ArrayList.addAll(ye5.j) label-member hook ok
I NCL: [CLM:label] removed=1/N
```

DebugServer raw feed：`[CLM:label] ye5.j removed=1/N`

---

## 8.0.71 标签入口（F07B）

| 项目 | 值 |
|------|-----|
| 版本 | 微信 **8.0.71** 通讯录【标签】Tab |
| UI 上下文 | `MvvmContactListUI`（标签成员 + 添加搜索） |
| 数据入口 | `ArrayList.addAll(Collection)` beforeHook |
| 条目类型 | **`ye5.j`**（≠ P19 的 `fc5.g`） |
| wxid | **`ye5.j.d`**，String 形如 `wxid_xxx-15-0` → 去后缀 `-N-M` |
| 动作 | `Iterator.remove()` |
| 门控 | `StateMachine.isActive()` + `Bridge.allHiddenIds()` |

> **口径**：F07（`fc5.g`）= 通讯录 A–Z 主列表；**F07B（`ye5.j`）= 标签专用**，两条 addAll 入口并列，禁止混写。

---

## 最终 hook 路径

```
ArrayList.addAll(Collection)
  └── first item class == ye5.j
        └── ye5.j.d → strip "-N-M" suffix → wxid
              └── hidden.contains(wxid)
                    └── it.remove()
```

**实现位置**: `moduleD/ContactLabelMemberFilter.java` → `install()` / `extractLabelWxid()`（2026-05-31 起独立模块；2026-05-24 原在 `ContactFilter.installLabelAddAllHook()`，P_CV1 重构期丢失）

---

## 发现过程

1. Frida `probe_label_tab.js` v2 → 命中 `ye5.j`（Activity 窗口 gated，避免卡顿）
2. 字段探针 → `d` = `wxid_xxx-N-M`
3. 正式 hook 合入 ContactFilter，build 装机双路径验收

---

## 与 P19 对照

| 模块 | item 类 | wxid 路径 |
|------|---------|-----------|
| P19 通讯录主列表 | `fc5.g` | `g.d` → `z3.c1()` |
| **P19B 标签** | **`ye5.j`** | **`j.d` 去后缀** |

---

## 风险

- `ye5.j` 为 8.0.71 混淆名，升版需 classmap 重查（P24）
- 与 F07 共用同一 `ArrayList.addAll` hook 点，靠 first-item 类名分流，勿删 fc5.g 分支

---

## 路径 C（P26C 独家 · 指定标签隐藏）

🟡 探针已归档 — `d4` / `field_labelID` / WCDB lazy load 限制 / Web `/api/labels`  
→ 详见 [`P26C_HideSelectedLabels/result.md`](../P26C_HideSelectedLabels/result.md)
