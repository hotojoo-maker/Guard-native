# SKILL_WORKFLOW — AI 角色工作流规范

> 关联：`CLAUDE.md` §九、`native_core/RULES.md`  
> 所有 AI 接手任务前必读本文件 + `CLAUDE.md` + `HOOKMAP.md` + `TASK_BOARD.md`

---

## 一、角色分工

| 角色 | 职责 | Skill |
|------|------|-------|
| **总调度** | 制定计划 / 分配 P 任务 / 接手新会话 / 更新 TASK_BOARD | `guard-dispatch_总调度` |
| **执行** | 写代码 / 跑脚本 / 设备调试 / 单 P 任务 | `guard-execute-one_单任务执行` |
| **终端** | adb / frida / build / logcat 命令 | `guard-terminal_终端操作` |
| **审核** | P 任务自审（轻档）+ 发版门控（重档）+ 资料盘点 | `guard-review_质检门控` |

---

## 二、接手新会话三步铁律

```
1. 读 CLAUDE.md（10 分钟）
2. 读 HOOKMAP.md（⬜ 行只读名字，禁止展开）
3. 读 TASK_BOARD.md（只有 ✅ + 有日志的算完成）
```

**禁止**：不读完就动代码。

---

## 三、Java hook 任务接手流程

```
接手 P 任务
  └─→ 读 CLAUDE.md + HOOKMAP.md + TASK_BOARD.md
  └─→ 确认 P 任务目录（03_execute_执行任务/P<N>/brief.md）
  └─→ 检查依赖的已验收 hook（⬜ 态不当基础）
  └─→ 写代码（src/main/java/.../）
  └─→ 给用户可复制的装机命令
  └─→ 等用户反馈 logcat
  └─→ 根据 logcat 迭代（禁止盲猜）
  └─→ 通过后跑 frida_stats.js（F-22 铁律）
  └─→ 向用户展示拟写入草稿，等确认后更新文档
```

---

## 四、native_core 任务接手流程

> ⬜ 所有 native_core 接口当前为 Phase 0，未有实现代码。

### 4.1 接手前必读清单

```
✅ CLAUDE.md              铁律 2 / 6 / 23 / 27
✅ docs/PRODUCT_GATE.md   产品总闸：授权/状态机/通知策略
✅ native_core/RULES.md   native_core 专项禁止事项
✅ native_core/API.md     所有 nativeXxx() 接口规约
✅ native_core/ROADMAP.md 当前 Phase + 门控条件
✅ HOOKMAP.md             不动已验收 hook（铁律 29）
```

### 4.2 写 C++ 前必须确认的 3 条

```
① 不接微信 native（不 dlopen 微信 SO，不 hook 微信 native 方法）
② 不写 native hook 引擎（不引 Pine / bypassmm / shadowhook）
③ 不在 JNI_OnLoad 中调用 nativeInit（走模块 handleLoadPackage 受控路径）
```

### 4.3 Phase 门控（禁止跳 Phase）

```
每个 Phase 完成验收（✅ 有日志）后，才能开启下一 Phase。
当前 Phase 未验收时，下一 Phase 的代码禁止写入。
```

### 4.4 与 Java hook 的协作规则

```
Java hook 调 C++ 的唯一路径：NativeBridge.java（静态 native 方法）
C++ 不得主动调用 Java hook 业务代码
C++ 不得操作微信任何类/方法/字段
```

---

## 五、证据等级系统（所有任务强制）

| 等级 | 含义 | 升级条件 |
|------|------|---------|
| ✅ | 动态验证通过 | 有 logcat / frida 日志原文 |
| 🟡 | 代码已写，未装机 | 代码文件存在，编译通过 |
| ⬜ | 只读规划 | 文档阶段 |
| ❌ | 已证伪，永久禁用 | 有失败日志，写入 FAILURE_LOG.md |

**规则**：
- 文档中每个技术结论必须标注等级
- 🟡 不得当 ✅ 用（不得以"代码已写"推断功能可用）
- 发现等级标注错误 → 停下来报告给用户，不自行修改

---

## 六、文档写入门控（强制）

以下文件默认只读，写入前必须先向用户展示拟写入草稿，等明确同意后再改：

```
根目录：HOOKMAP.md / TASK_BOARD.md / FAILURE_LOG.md / CLAUDE.md
P 任务：03_execute_执行任务/**/worklog.md / result.md / brief.md
审稿：04_review_审稿复核/**
native_core：所有文件（Phase 0 规划阶段）
```

---

## 七、设备操作铁律

```
需要设备输出时：
  1. 给用户一个明确的、可复制的指令
  2. 等用户贴回 logcat / frida 输出
  3. 根据实际输出推进

禁止：
  ❌ 假设输出（"应该是 XXX"）
  ❌ 估计后直接改代码
  ❌ 不等设备反馈就标 ✅
```

---

## 八、何时切窗口

| 场景 | 决策 |
|------|:----:|
| 单次 adb / frida 命令 | ❌ 不切，用户敲完贴回 |
| 同一 P 任务连续调试 | ❌ 不切，上下文连贯 |
| 全新 P 任务（不同模块）| ✅ 切 |
| 上下文 50+ 轮膨胀 | ✅ 切 |

切窗口时必须说出目标 skill 全名，让用户可复制 invoke。

---

## 九、native_core Phase 1 接手快速指引

```
当用户说"开始 native_core Phase 1 / C++ 最小骨架"时：

Step 1 确认状态
  读 ROADMAP.md → 确认 Phase 0 门控通过 → 确认用户同意开始

Step 2 建立文件
  新建 native_core/CMakeLists.txt
  新建 native_core/include/guard_core.h
  新建 native_core/src/guard_core.cpp（全 stub）
  新建 native_core/src/process_router.cpp
  新建 native_core/src/log_limiter.cpp
  新建 src/main/java/.../native/NativeBridge.java

Step 3 修改构建
  修改 build.gradle：加 externalNativeBuild + abiFilters

Step 4 装机验证
  给用户可复制的 build + install 命令
  等 logcat 确认 nativeInit 无崩溃

Step 5 更新文档（用户同意后）
  ROADMAP.md：Phase 1 状态 ⬜→🟡/✅
  TASK_BOARD.md：新增 P_NC1 行
```
