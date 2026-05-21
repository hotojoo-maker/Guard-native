---
name: guard-execute_执行
description: Guard Native 执行——写代码/跑脚本/设备调试/单个 P 任务。从 TASK_BOARD 领到 P 任务后立即用这个 skill。调试时必须与用户交互，禁止盲猜。
---

# guard-execute — 执行

---

## ⛔ 绝对禁止 / ABSOLUTE PROHIBITIONS

> **这一节优先级高于本 skill 所有其他内容。**
> **This section overrides everything else in this skill.**

| 中文 | English |
|------|---------|
| **禁止猜测** | No guessing |
| **禁止推断**（无 L1 动态日志 / L2 jadx 静态证据） | No inference without L1 logcat/L2 jadx evidence |
| **没有证据 → 停下来，主动问用户** | No evidence → STOP, ask the user |
| **没有资料/日志 → 停下来，主动问用户** | No materials/logs → STOP, ask the user |
| **⬜ 状态的条目只读名字，禁止展开细节** | ⬜ items: read name only, never expand or assume |
| **不确定 = 不能写成结论，必须标 ❓ 并停下来问** | Uncertain = cannot be a conclusion; mark ❓ and ask |
| **HOOKMAP / TASK_BOARD 状态不一致 → 停下来，报告矛盾，问用户** | Status conflict in docs → STOP, report conflict, ask |

**触发停止的具体场景：**
- 想写"这个类应该是 X" → ❌ 停，问"请确认 X 类名是否正确"
- 想写"Hook 点应该在 Y 方法" → ❌ 停，问"Y 方法有没有 L1 日志？"
- result.md 没有装机日志 → ❌ 不能把状态标 ✅
- HOOKMAP 写 ✅ 但没有 logcat 原文 → ❌ 降回 🟡，报告用户

---

## 适用场景

| 场景 | 触发关键词 |
|------|----------|
| 写代码 | "实现 xx"/"写 Java"/"写 hook" |
| 设备调试 | "跑 Frida"/"装机测试"/"logcat" |
| 做 P 任务 | "做 P<N>" |

---

## 工作流（5 步）

### 1. 进入 P 任务目录

路径：`03_execute_执行任务/P<N>_<主题>/`

**优先读 `brief.md`**（如果存在）——它已经列出本任务的 hook 点、相关 F-xx、待完成项，不用翻全文。

---

### 2. 必读检查（按风险分层，不是每次全读）

#### 任务类型判断

| 任务类型 | 必读 | 可跳过 |
|---------|------|--------|
| 改文档 / 改配置 / 更新 result.md | `brief.md` | FAILURE_LOG、HOOKMAP |
| 写非 hook 代码（UI/工具类/配置）| `brief.md` + CLAUDE.md 对应章节 | FAILURE_LOG 全文 |
| **写 hook 代码 / 发版门控** | `brief.md` + FAILURE_LOG 相关 F-xx + HOOKMAP | — |
| 首次接手新 P 任务 | CLAUDE.md + HOOKMAP + TASK_BOARD §一 + FAILURE_LOG 全文 | — |

> **brief.md 不存在时**：读 TASK_BOARD 对应窗口段 + FAILURE_LOG 全文（旧路径）

#### hook 代码必过铁律速查

| # | 铁律 | 出处 |
|---|------|------|
| F-23 | 禁止 JniHook/JNI_OnLoad | 微信强制下线实证 |
| F-24 | 禁止 extends Service | Service not found 实证 |
| F-25 | XposedHelpers 调用必须 `catch (Throwable)` | init 静默中断实证 |
| F-26 | hook protobuf 方法用 `getMethods()` + `XposedBridge.hookMethod` | NoSuchMethodError |
| F-13~15 | 禁止 `notifyItemRange*` | DiffUtil 崩溃实证 |
| F-28~30 | 朋友圈：MvvmList.m/y1.notify*/getView 全部零命中 | 8.0.71 实证 |

---

### 3. 写代码

- 代码路径：`src/main/java/com/ghost/assist/<模块>/`
- 禁止敏感词：`vip` `hide` `pirate` `wechat` `catfish` `myauth` `wmiyou`
- 类名/方法名/MMKV key 全部 seed 化短哈希
- 参考 `./refs/MainEntry.java` `./refs/UserControll.java`，类名必须重写

---

### 4. 设备调试（★ 最高优先铁律）

**调试必须与用户交互，禁止盲猜。**

| 步骤 | 正确做法 | 禁止 |
|------|---------|------|
| 需要 adb/frida 输出 | 给用户一个明确指令，等结果 | 假设输出自行推进 |
| logcat 没有命中 | 问用户"看到 [XX] hit 了吗" | "估计是 XXX" 直接改代码 |
| 需要手机操作 | "请进朋友圈刷新，告诉我 F05 计数" | 自己判断结果 |
| 出现新现象 | 停下来报告，问用户下一步 | 继续猜测原因 |

**调试节奏**：一次给用户**一个操作**，等回复，看结果，再下一步。

---

### ★★★ 卡住自动停规则

> **同一方向连续失败 2 次 → 立刻停止，向用户汇报，等待决策。禁止尝试第 3 次。**

**触发条件**（满足任意一条 = 卡住）：
- hook 安装成功但命中 0 次，改了一次还是 0 次
- 同一报错换了思路还是同类错
- Frida 探针无输出，换了脚本还是无输出
- 找了 2 个类名候选都反射失败

**停止后必须说：**
```
卡住了。连续 [N] 次失败。
问题：[一句话描述现象]
已试过：[方法A] → [结果]；[方法B] → [结果]
可能方向：A. [方向A] / B. [方向B]
请决策：继续A / 继续B / 放弃换方向
```

**失败归档**：发现新铁律用注释标记，不打断执行流：
```java
// TODO F-NEW: [现象一句话] → 汇报总调度归档
```

---

### 5. 收尾

- 写 `brief.md`（如不存在则新建，模板见下方）
- 写 `result.md`：完成清单 / 验收结果 / KPI 数据 / 风险
- 更新 `HOOKMAP.md` 对应行 ⬜→🟡 或 🟡→✅
- 新失败教训 **报告给总调度归档**（不自己写 FAILURE_LOG）
- 切到 `guard-review_质检门控` skill 自审

---

## brief.md 模板（新建 P 任务时用）

```markdown
# P<N> brief — <主题>

> 执行窗口直接读这个，不用翻 TASK_BOARD 全文

## 当前状态
⬜/🟡/✅ [一句话]

## 本任务 hook 点
| hook | 类 | 方法 | 状态 |
|------|-----|------|------|

## wxid / 关键字段提取路径
[类名.字段名 链路]

## 相关 F-xx（只看这些）
| 编号 | 一句话 |
|------|--------|

## 待完成
- [ ] 项目1

## 关键文件
[代码文件 / 脚本 / 日志路径]
```

---

## Frida 调试工具箱

```javascript
// 枚举类的所有方法重载（F-26 铁律必备）
Java.use("com.xxx.ClassName").parseFrom.overloads.forEach(function(o) {
    console.log(o.argumentTypes.map(function(t){return t.className}));
});

// 枚举已加载类（找混淆类名）
Java.enumerateLoadedClasses({ onMatch: function(n) {
    if (n.indexOf("sns") >= 0 || n.indexOf("Sns") >= 0) console.log(n);
}, onComplete: function(){} });

// Dump 对象所有字段
var fields = obj.getClass().getDeclaredFields();
fields.forEach(function(f) {
    f.setAccessible(true);
    try { console.log(f.getName() + " = " + f.get(obj)); } catch(e){}
});
```

---

## ★ 工作日志规范（防上下文幻觉核心机制）

> 每个 P 任务维护一份 `worklog.md`，任何结论**必须标注置信度**，禁止用"已确认"描述未经设备实测的推断。

### 置信度标签（强制使用）

| 标签 | 含义 | 使用场景 |
|------|------|---------|
| ✅ 已验证 | 设备实测命中，logcat/frida 有截图或日志原文 | hook 命中、字段读取正确 |
| 🔬 待验证 | 逻辑推断正确，尚未设备实测 | 新写的代码、新 hook 方案 |
| ❓ 不确定 | 假设/猜测，可能是错的 | 类名候选、字段名推测 |
| ❌ 已证伪 | 设备实测未命中，或有反例证据 | 归档失败路径 |

### worklog.md 模板

```markdown
# P<N> 工作日志

## 当前卡点
[一句话：现在卡在哪，缺什么信息]

## 已验证事实（✅ 必须有日志原文）
| 事实 | 证据（日志/截图关键行） |
|------|----------------------|
| LLadd blocked ×7 | `[MF:LLadd cls=i84.y] d=wxid_lzd2va16jd1622` |

## 尝试过的方案
| 方案 | 结果 | 状态 |
|------|------|------|
| hook w1.E1() 返回 0 | 装上了但从未被调用 | ❌ E1 不在 tab 启动路径 |
| hook w1 write path (Object-param) | 1 method found，非 insertLike | ❌ 路径不对 |

## 当前假设（❓ 未验证）
| 假设 | 依据 | 验证方法 |
|------|------|---------|
| 互动红点从 MMKV 启动读取，不走 E1 | E1 未被调用 | Frida trace startup |

## 下一步（只列一步）
[具体操作：跑什么脚本 / 看什么日志 / 改什么代码]
```

### 执行中的语言铁律

**禁止写：**
- "E1() 是计数出口" → 除非 logcat 有 `scs_E1` 命中日志
- "这条路径已覆盖" → 除非实测 hook 命中过
- "红点来自 xxx" → 除非 Frida trace 原文

**必须写：**
- "❓ 推测 E1() 是出口（依据：iOS 实证，未 Android 验证）"
- "🔬 新写代码，待装机验证"
- "✅ 命中：日志原文 `[MRD:L1] 密友新帖压制 wxid=...`"

---

## 反模式

- ❌ 调试时不等用户回复就推进
- ❌ 同一方向失败 2 次不停，继续挖第 3 次
- ❌ 卡住了去看 Catfish/8.0.70（换话题逃避）
- ❌ logcat 没命中就假设 hook 成功
- ❌ 写 hook 前不过 F-xx 速查表
- ❌ 收尾不跑 frida_stats
- ❌ 自己写 FAILURE_LOG（标注 TODO，让总调度写）
- ❌ 用"已确认"/"命中"描述尚未设备实测的推断（= 上下文幻觉根因）
- ❌ 不维护 worklog.md 就跨多轮推进（= 失忆型螺旋）