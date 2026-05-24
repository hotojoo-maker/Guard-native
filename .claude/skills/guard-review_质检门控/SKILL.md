---
name: guard-review_质检门控
description: Guard Native 质检门控——P任务自审(轻档) + 发版门控(重档) + 文档盘点/冲突检测/调研任务。P任务完成后用轻档；发版/合并前用重档；发现文档矛盾/路径错误时用资料功能。
---

# guard-review — 质检门控（三合一）

---

## ⛔ 绝对禁止 / ABSOLUTE PROHIBITIONS

> **这一节优先级高于本 skill 所有其他内容。**
> **This section overrides everything else in this skill.**

| 中文 | English |
|------|---------|
| **禁止猜测** | No guessing |
| **禁止推断**（无 L1/L2 证据） | No inference without L1/L2 evidence |
| **没有 logcat/Frida 日志原文 → 不能标 ✅** | No logcat/Frida raw log → cannot mark ✅ |
| **L3/L4 结论不得写成已验证事实** | L3/L4 conclusions must not be stated as verified facts |
| **发现文档 ✅ 但无日志 → 降回 🟡 + 报告用户** | Doc ✅ without logs → downgrade to 🟡, report user |
| **不确定 → 停下来主动问** | Uncertain → STOP and ask |

---

## 适用场景

| 档位 | 触发时机 | 耗时 |
|------|---------|------|
| **P 自审（轻档）** | 每个 P 任务代码完成 → 装机前 | ~15 分钟 |
| **发版门控（重档）** | 合并主线 / 出包 / 发版前 | ~45 分钟 |
| **资料功能** | 文档矛盾 / 路径找不到 / 写调研任务 | 随时 |

---

## 一、P 自审（轻档，4 项）

### ① 铁律检查
grep 代码是否有被禁模式：
- `extends Service` → F-24
- `catch (Exception` 而非 `Throwable` → F-25
- `findMethodExact` 用在 protobuf 类 → F-26
- `notifyItemRange` → F-13~15
- `ro.boot.` → 铁律5
- `JniHook` / `System.loadLibrary` → F-23
- 敏感词 `vip` `hide` `catfish` `wechat` → §5.8

### ② 自洽性
- 类名/字段名与调研结果一致
- 证据等级标注（L1~L4），L4 标"待验证"
- 状态机覆盖 3 态（VISIBLE/HIDDEN/UNLOCKING）

### ③ 编译阻断
- 引用字段是否真实存在
- import 完整
- catch 粒度正确

### ④ 路径核对
- `src/` 路径真实存在
- result.md / research.md 已填写

**判定**：全部 ✅ → 可装机 | 任一 🔴 → 修完再测

---

## 二、发版门控（重档，5 项）

### ① 污染源
- grep `04_review_审稿复核/RETRACTED_CONCLUSIONS.md` 关键词
- 命中 → **阻塞**

### ② 证据升格
- L3/L4 升 L1/L2 必须有动态验证出处
- 无出处 → **降回原级，加 [未验证]**

### ③ 二进制改动授权
- 改 APK/SO/DEX/smali/MMKV 必须有用户授权记录
- 记录位置：`01_dispatch_总调度/AUTHORIZATIONS.md`
- 无记录 → **阻塞**

### ④ 重复文件清理
- 同名 .md / 同功能脚本是否多份
- 有重复 → 合并保留最新

### ⑤ KPI 红线（F-22 铁律）

**Step 1 — 跑 KPI**

```
frida -U -f com.tencent.mm --no-pause -l "I:/apk2/_3__D_wechat_ban/official_wechat_ban_research/03_anti_frida/frida_stats.js" 2>&1 | tee logs/frida_stats_release.log
```

| 指标 | 安全上限 | 红线 |
|------|:-------:|:----:|
| verifiedbootstate | 20 | **38** |
| PROP/100K | 150 | 220 |
| normsg/100K | 4000 | 5124 |
| CONN 密度 | 0.2 | 0.5 |

任一超红线 → **阻塞，回滚不合入**

**判定**：全部 ✅ → 可发版 | 任一 🔴 → 写入 `05_reports_报告/RISK_HISTORY.md`

---

## 三、资料功能

### 路径表维护
- 新增/移动资料 → 改 `PROJECT_INDEX.md`

### 冲突检测
- 同一结论多处矛盾 → 写 `02_docs_资料员/CONFLICTS.md`：
  ```
  | 日期 | 文件A | 文件B | 分歧点 | 仲裁 |
  ```

### 调研任务（T 任务）
- T 任务放 `02_docs_资料员/T_TASKS/`，格式：
  ```
  # T<N>_<主题>
  ## 输入 / ## 任务 / ## 产出格式 / ## 验收
  ```
- T 任务自洽：便宜模型（Haiku）无需上下文独立完成

---

## 输出格式

**轻档**：
```
【P 自审报告】P<N>_xxx
① 铁律:    ✅ / 🔴 (列违反项)
② 自洽性:  ✅ / 🔴
③ 编译阻断: ✅ / 🔴
④ 路径:    ✅ / 🔴
判定: 通过 / 🔴 必须修
```

**重档**：
```
【发布门控报告】
① 污染源:    ✅ / 🔴
② 证据升格:  ✅ / 🔴
③ 二进制授权: ✅ / 🔴
④ 重复文件:  ✅ / 有重复
⑤ KPI:      ✅ / 🔴 (列实测值)
判定: 通过 / 🔴 阻塞
```

---

## 证据分级

| 等级 | 标签 | 标准 |
|------|------|------|
| **L1** | 动态已证实 | Frida log / logcat 直接观察 |
| **L2** | 静态已证实 | jadx / 反编译确认 |
| **L3** | 高概率推断 | 多信号收敛，替代解释排除 |
| **L4** | 待验证 | 有线索但未动态确认 |

**任何结论必须标注等级**，否则视为 L4。

---

## 铁律

- 无 L1 不得写"封号" / "服务端拦截"
- 重档任何一项阻塞 → 整体阻塞，禁止发版
- 不删资料，只搬 `07_archive_归档/`
- T 任务必须自洽，不依赖上下文

---

## 反模式

- ❌ P 任务装机前跳过轻档
- ❌ 发版前跳过 frida_stats
- ❌ catch Exception 没改 Throwable 就通过
- ❌ L4 证据不标注直接当结论
- ❌ 改 APK/DEX 没记录授权
