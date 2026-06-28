---
icon: 🔧
cn: 单任务执行
name: guard-execute-one_单任务执行
description: Guard Native 执行——写代码/跑脚本/设备调试/单个 P 任务。从 TASK_BOARD 领到 P 任务后立即用这个 skill。调试时必须与用户交互，禁止盲猜。
---
> ⚠️ 输出前自查：禁止错别字、黑话、客户看不懂的话。

# guard-execute — 执行

## 🔐 固定签名铁律（所有角色必读）
- 项目唯一固定签名文件：`signing/guard-native-debug.keystore`。
- `build.gradle` 的 debug/release 必须都指向该文件；禁止依赖或重建 `~/.android/debug.keystore`。
- `INSTALL_FAILED_UPDATE_INCOMPATIBLE` 必须先停、比对已装 APK 与固定 key 指纹，未经用户确认禁止卸载。
- 缺少固定 key 时停止 build/装机；日志只能写当前 P 任务 `logs/`，禁止写进 docs/skill 目录。

---

## ⛔ 绝对禁止（执行特有；通用 G1–G6 见 `CLAUDE.md` §三.五）

| 禁忌 |
|------|
| **⬜ 状态条目只读名字**，禁止展开细节或假设实现方式 |
| **不确定的事不能写成结论**——必须标 ❓ 并停下来主动问用户 |
| **HOOKMAP 与 TASK_BOARD 状态不一致 → 停下来报告矛盾**，不自己拍板仲裁 |

**触发停止的具体场景：**
- 想写"这个类应该是 X" → 停，问"请确认 X 类名"
- 想写"Hook 点应该在 Y 方法" → 停，问"Y 有没有 L1 日志？"
- result.md 没有装机日志 → 不能标 ✅
- HOOKMAP 写 ✅ 但没有 logcat 原文 → 降回 🟡，报告用户

**文档车道（8071）**：先读 [`docs/README.md`](../../docs/README.md)。写 hook 以 `docs/HOOK_MAP_8071_AUTHORITATIVE.md` 为准；**禁止**从 `docs/archive/wechat_8066/` 或 Catfish `refs/` 直搬类名（见 `docs/isolation/INDEX_COMPETITOR.md`）。

**门控/状态机语意**：动 StateMachine / AuthManager / SearchUnlock / Filter 的 `isActive()` 调用前，**必须先读** `guard-auth-review` skill **§零.前 语意速查**。该节解释：① 入口口令 / 授权 / 状态机三者互不替代；② HIDDEN/VISIBLE/UNLOCKING 三态用户实际看到什么；③ Filter 只能 **只读** `isActive()`，禁止 `enterHidden/exitHidden`。

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
| **写 hook 代码 / 发版门控** | `brief.md` + FAILURE_LOG 相关 F-xx + HOOKMAP + `docs/HOOK_MAP_8071_AUTHORITATIVE.md` | archive / HOOK_MAP_V1 |
| 首次接手新 P 任务 | CLAUDE.md + `docs/README.md` + HOOKMAP + TASK_BOARD §一 + FAILURE_LOG 全文 | — |

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

> **一次只发一个 Step，等用户贴结果再发下一个。** 禁止一次堆 3-5 个 Step 让用户复制——会让 Claude Code 窗口爆炸。

#### 单 Step 输出格式（必须遵守）

**结构 = 一行目的 + 一个 code block + 一行观察要求**

```markdown
**Step N — 干什么**

[一行命令，code block 内只放命令，不混说明]

**请观察**：[一句话告诉用户看什么 / 贴什么回来]
```

#### 正确范例

**Step 1 — 装机 debug apk**

```
adb install -r build/outputs/apk/debug/guard-native-debug.apk
```

**请观察**：是否打印 `Success`，失败贴报错。

**Step 2 — 抓搜索过滤命中**

```
adb logcat -d 2>&1 | findstr "SF:gv MRD"
```

**请观察**：贴前 20 行，关注是否有 `[SF:gv] blocked` 字样。

#### 禁止

- ❌ 一次发多个 Step 让用户连贯执行（违反「一次一个」铁律）
- ❌ code block 里塞 `# 这一步会做...` 注释（用户复制会一起带走）
- ❌ 把「请观察」塞进 code block（必须独立 markdown 行）
- ❌ 用散文「你跑一下 xxx 然后告诉我」（用户不知道复制什么）
- ❌ 多行 `&&` 串联命令（出问题不知道哪步崩，且违反「一次一个」）

#### 互动节奏速查

| 场景 | 正确 | 禁止 |
|------|------|------|
| 需要 adb/frida 输出 | 发 1 Step，等贴回来 | 假设输出自行推进 |
| logcat 没命中 | 问「看到 `[XX] hit` 了吗」 | 「估计是 XXX」直接改代码 |
| 需要手机点击 | 「请打开微信，点放大镜，输入'熵'」 | 自己脑补流程 |
| 出现新现象 | 停下来报告，问下一步 | 继续猜测原因 |

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

- **文档写入门控**：写/改 `worklog.md` `result.md` `brief.md` `HOOKMAP.md` `TASK_BOARD.md` 前，**必须先向用户展示拟写入草稿，等明确同意后再改**
- 写 `brief.md`（如不存在则新建，模板见下方；须用户同意）
- 写 `result.md`：完成清单 / 验收结果 / KPI 数据 / 风险（须用户同意）
- 更新 `HOOKMAP.md` 对应行 ⬜→🟡 或 🟡→✅（须用户同意）
- 新失败教训 **报告给总调度归档**（不自己写 FAILURE_LOG，除非用户明确同意）
- 切到 `guard-review_质检门控` skill 自审

**瘦身写法（2026-06-06 起强制）**：
- `result.md/worklog.md` 可写细节；`HOOKMAP.md/TASK_BOARD.md` 只写一句状态 + 证据路径
- 不把日志原文、长 hook 链、失败推理反灌进总览文档
- P 任务完成后，优先把 `TASK_BOARD.md` 的进行中描述压成一行历史摘要
- 需要保留历史时写"详见 result/worklog"，不要复制正文

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

## ★ Frida 探针轻量化铁律（HEAVY-PROBE 反模式）

> 重探针 = 微信进程被杀 / 时序错过 / 用户上下文炸 / 一晚白付。
>
> **已留下血证**：
> - `bug排查/probe_yz2b0.log` 14 行 → `Process terminated`
> - v3 用 `setTimeout(Java.choose, 2000)`：抢在搜词前拽 q2、List 全 size=0
> - v6 maxDepth=3 + 递归展开 fz2.r/fz2.y + 全局 `BaseAdapter.notifyDataSetChanged` hook → 用户当场标"太重"

### 五条铁律（H1–H5）

| # | 铁律 | 反例 |
|---|------|-----|
| **H1** | **目标精确**：用 `Java.choose` 限定实例、或类名字符串内联过滤；禁止 hook 全局基类后再用 if 过滤 | 全局 `BaseAdapter.notifyDataSetChanged` → 每个 ListView 触发 |
| **H2** | **递归 ≤ 1 层**：默认 dump 一层、第二层只打 size + className + toString(≤80)，不展开字段；要再下一层必须新写一版探针 | v6 maxDepth=3 + 自动展开非 java/android 嵌套对象 |
| **H3** | **采样 ≤ 5 元素**：超 50 元素的 List 不深入、大 List 只打 size | dumpListElements 拿前 10、List 全集 |
| **H4** | **小步迭代**：每轮探针只挖下一层、出结果才动下一版，禁止一把铺满 4 层 | v6 一把 a.n → iz2.i → f → 元素 → 字段 全拽 |
| **H5** | **拽到就 unhook**：dump 完锁标志 + 主动把 `BA.ndc.implementation = null` 还原 | 只锁标志、hook 留着、每次 ndc 都跑判断 |

### 探针迭代节奏（推荐节奏）

```
v1（轻）→ 拽顶层类的字段名表          目标：知道哪个字段是 List
v2（轻）→ 拽 List 元素 className     目标：知道元素是什么类
v3（轻）→ 拽元素的 String 字段       目标：找 wxid 候选
v4（仅在 v3 没找到时）→ 拽嵌套对象一层
```

**禁止跳级**：v1 还没出结果就写 v3 + 一把递归 3 层。

### 探针体积自检（写完 .js 之前自问）

- [ ] maxDepth 是多少？> 2 就停下来想一想需不需要
- [ ] hook 的方法是不是高频路径？（BaseAdapter / ArrayList / View / Activity 全是高频 → 必须类名过滤后只跑一次再 unhook）
- [ ] dump 一次预计输出多少行？> 200 行就要砍
- [ ] `enumerateLoadedClasses` 用了吗？只在初探时一次性 + 必须有过滤词
- [ ] hook 完成后会主动把自己 `.implementation = null` 吗？

### 探针失败汇报

跑空（Process terminated / size=0 / hook 无命中）时**不要直接加深递归**，按下方报告格式停下来问用户：

```
[探针 P20-PROBE-yz2b0-00X] 跑空
现象：[一句话]
可能原因：A. 时序早了（hook 在数据前装）/ B. 探针太重（进程被杀）/ C. hook 类名错
建议下一步：（A/B/C 三选一）
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

## 文档写入门控（强制）

**默认只读；用户明确说「写入文档」「更新 worklog」等之前，禁止改 md。**

**正确流程：**
1. 回复里给出拟写入草稿
2. 问：「确认写入 [文件名] 吗？」
3. 用户同意后再改文件

**禁止：**
- ❌ 把用户聊天内容直接标成 ✅ L1 写入 worklog
- ❌ 把 iOS/Android 跨端推断写成「必然等价」
- ❌ 用户只提供半截抓包表格 → 自行补结论并归档

---

## 反模式

- ❌ 调试时不等用户回复就推进
- ❌ **未经用户同意改 md 文档**
- ❌ 同一方向失败 2 次不停，继续挖第 3 次
- ❌ 卡住了去看 Catfish/8.0.70（换话题逃避）
- ❌ logcat 没命中就假设 hook 成功
- ❌ 写 hook 前不过 F-xx 速查表
- ❌ 收尾不跑 frida_stats
- ❌ 自己写 FAILURE_LOG（标注 TODO，让总调度写）
- ❌ 用"已确认"/"命中"描述尚未设备实测的推断（= 上下文幻觉根因）
- ❌ 不维护 worklog.md 就跨多轮推进（= 失忆型螺旋）