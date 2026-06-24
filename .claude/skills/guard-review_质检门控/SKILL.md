---
name: guard-review_质检门控
description: Guard Native 质检门控——P任务自审(轻档) + 发版门控(重档) + 文档盘点/冲突检测/调研任务。P任务完成后用轻档；发版/合并前用重档；发现文档矛盾/路径错误时用资料功能。
---

> ⚠️ 输出前自查：禁止错别字、黑话、客户看不懂的话。

# guard-review — 质检门控（三合一）

## 🔐 固定签名铁律（所有角色必读）
- 项目唯一固定签名文件：`signing/guard-native-debug.keystore`。
- `build.gradle` 的 debug/release 必须都指向该文件；禁止依赖或重建 `~/.android/debug.keystore`。
- `INSTALL_FAILED_UPDATE_INCOMPATIBLE` 必须先停、比对已装 APK 与固定 key 指纹，未经用户确认禁止卸载。
- 缺少固定 key 时停止 build/装机；日志只能写当前 P 任务 `logs/`，禁止写进 docs/skill 目录。

---

## ⛔ 绝对禁止（质检特有；通用 G1–G6 见 `CLAUDE.md` §三.五）

| 禁忌 |
|------|
| **没有 logcat/Frida 日志原文 → 不能标 ✅**——口述/截图不算，必须能定位到磁盘文件 + 关键行 |
| **L3 高概率推断 / L4 待验证结论不得写成已验证事实**——必须保留 ❓ 或 🟡 标签 |
| **发现文档已经标 ✅ 但找不到日志 → 降回 🟡 + 报告用户**，不直接删 |

---

## 适用场景

| 档位 | 触发时机 | 耗时 |
|------|---------|------|
| **P 自审（轻档）** | 每个 P 任务代码完成 → 装机前 | ~15 分钟 |
| **发版门控（重档）** | 合并主线 / 出包 / 发版前 | ~45 分钟 |
| **资料功能** | 文档矛盾 / 路径找不到 / 写调研任务 | 随时 |

---

## 一、P 自审（轻档，4 项）

> **门控/状态机语意识别**：审 ConvFilter / SettingsEntry / SearchUnlock / AuthManager / NativeBridge 等代码前，先看 `guard-auth-review` skill **§零.前 语意速查**——明白「入口口令 ≠ 授权 ≠ 状态机」之后，才识别得出"Filter 里悄悄调 `sm.exitHidden()`"这种乱接。

### ① 铁律检查
grep 代码是否有被禁模式：
- `extends Service` → F-24
- `catch (Exception` 而非 `Throwable` → F-25
- `findMethodExact` 用在 protobuf 类 → F-26
- `notifyItemRange` → F-13~15
- `ro.boot.` → 铁律5
- `JniHook` / 微信自身 SO 加载 / 第三方底层注入 / 非模块自有 `System.loadLibrary` → F-23
- 允许：模块自有 `libguardcore.so` 正常加载（当前 `native_core` 路线）；但不得加载微信自身 SO，不得接 `JNI_OnLoad` 注入链，不得 hook 微信 native 方法
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

### ⑤ KPI 出包前体检（口径见防封官 skill）

**Step 1 — 跑 KPI**

```
frida -U -f com.tencent.mm --no-pause -l "I:/apk2_official_research/official_wechat_ban_research/03_anti_frida/frida_stats.js" 2>&1 | tee logs/frida_stats_release.log
```

| 指标 | 安全上限 | 红线 |
|------|:-------:|:----:|
| verifiedbootstate | 20 | **38** |
| PROP/100K | 150 | 220 |
| normsg/100K | 4000 | 5124 |
| CONN 密度 | 0.2 | 0.5 |

环境类 vbs/PROP 零读取达标；密度类 normsg/CONN 明显异常才查（非即停）

**判定**：全部 ✅ → 可发版 | 任一 🔴 → 写入 `05_reports_报告/RISK_HISTORY.md`

---

## 三、资料功能

### 文档车道（8071，2026-05-27 隔离）

| 用途 | 路径 |
|------|------|
| 文档入口 | `docs/README.md` |
| 8071 hook 事实 | `docs/HOOK_MAP_8071_AUTHORITATIVE.md` |
| 8066 历史 | `docs/archive/INDEX.md`（盘点/冲突时才开） |
| Catfish | `docs/isolation/INDEX_COMPETITOR.md` |

资料盘点时：若发现仍指向 `docs/CLASS_MAP_8066.md` / `docs/HOOK_POINTS.md` 作**默认写码**引用 → 标 🔴，改链到 8071 或 archive 索引。

### 路径表维护
- 新增/移动资料 → 改 `PROJECT_INDEX.md` + 必要时 `docs/archive/INDEX.md`

### 文档瘦身审查（2026-06-06 起）

资料盘点时同时检查"重复写胖"：
- `docs/HOOK_MAP_8071_AUTHORITATIVE.md` = hook 点 / 证据 / 技术细节唯一权威
- `HOOKMAP.md` = 功能总图，只留状态、主路径一句话、证据链接
- `TASK_BOARD.md` = 当前任务，只留下一步和 P 历史一句话
- P 任务 `result.md/worklog.md` = 历史流水和详细日志

发现同一段 hook 链、日志原文、失败原因在 2 个以上总览文档重复展开 → 标 🟡，建议压成"一句话 + 链接"。不要删证据，只把证据集中到权威文档或 P 任务目录。

### 冲突检测
- 同一结论多处矛盾 → 写 `04_review_审稿复核/CONFLICTS.md`：
  ```
  | 日期 | 文件A | 文件B | 分歧点 | 仲裁 |
  ```

### 调研任务（T 任务）
- T 任务放 `04_review_审稿复核/T_TASKS/`，格式：
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

L1/L2/L3/L4 定义见 `CLAUDE.md` §三.五；本 skill 重点是把无 L1 的 ✅ 降回 🟡。

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
