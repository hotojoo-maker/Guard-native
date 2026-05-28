# FAILURE_LOG — 失败方案归档（34 条铁律）

> **铁律**：已证实失败的方案，**任何人不得复用**。AI 接手必读。
> 旧 15 条详细 → [`./refs/FAILURE_LOG.md`](./refs/FAILURE_LOG.md)
> 新 19 条（F-16 ~ F-34）见下方 §二

更新时间：2026-05-27（F-32/33/34 已补，含 ConvFilter L4 卡帧 + V↔H 刷新 + sConvCache 死循环）

---

## 一、F-01 ~ F-15 摘要（详细看 refs/FAILURE_LOG.md）

| # | 方案 | 根因 | 教训 |
|:--:|------|------|------|
| F-01 | 8.0.70 架构套 8.0.66 | 混淆名/Adapter 类型完全不同 | 每版本独立验证 |
| F-02 | 追 h8.L9/g8.f 静态调用链 | 是消息处理链不是会话链 | 静态推断必须动态验证 |
| F-03 | WCDB rawQuery hook | h8 用 r.a() 自定义封装 | 不走标准 SQL hook |
| F-04 | 改 SparseArray Key | Key 非连续 | 直接改 h/o/p List |
| F-05 | Hook K0() + notify | Kotlin StateFlow 覆盖 | Adapter 层修改必被 Flow 覆盖 |
| F-06 | Hook K0+getCount+notify | 死循环 | 不联动 hook Adapter 多个方法 |
| F-07 | 重建 SparseArray 连续 Key | Flow 持有原始引用 | 外部替换内部结构无效 |
| F-08 | getView 层返回空 View / height=1 + GONE | Flow 绕过 getView；甜密友+微密友均用此方案但仅适用旧 ListView | Adapter 在 8.0.66 RecyclerView 只是投影，不可复用 |
| F-09 | onBindViewHolder 改 position | position 偏移数据错位 | 禁止改 RecyclerView position |
| F-10 | View.GONE + 数据层混改 | 黑洞穿透，点击跳桌面 | 不混用两套方案 |
| F-11 | **Java 反射 invoke notifyDataSetChanged** | SIGSEGV ART 崩溃 | 直接调 `ad.notifyDataSetChanged()` |
| F-12 | 纯 View.GONE 主方案 | ViewHolder 复用污染 + 点击穿透 | View.GONE 只能做辅助 |
| F-13 | notifyItemRange* clean-before | DiffUtil position 错位崩溃 | 禁止 RecyclerView 通知方法修改底层数据 |
| F-14 | **notifyItemRange* clean-after deferred** | JNI local ref GC SIGABRT | 异步回调不能持有 hook 的 this |
| F-15 | 朋友圈 RecyclerView/DiffUtil 状态机内清洗 | 三种变体全崩 | 唯一可行：notifyDataSetChanged clean-before |

---

## 二、F-16 ~ F-34 新增（基于 D 线 + 8.0.66/8.0.71 实证）

### F-16：LSPosed 模块不加进程白名单 → 沙箱进程 FATAL

| 项 | 内容 |
|----|------|
| 日期 | 2026-05-19（基于 apk2/network_compare 封号失败版分析）|
| 分类 | 进程隔离失败 |
| 方案 | LSPosed 模块启动时所有微信子进程都注入业务 hook |
| 症状 | `xweb_sandboxed isolated` 进程调用 `MainEntry.start()` → `getRunningAppProcesses()` → **SecurityException FATAL** |
| 根因 | 沙箱进程无权限调高权限 API；Catfish 原作者犯了这个错 |
| 教训 | **LSPosed 入口第一行必须判断进程名**：只 hook `com.tencent.mm`（主进程），跳过 `:sandboxed_process_*` `:isolated_*` `:push` `:appbrand_*` 等 |
| 强制规则 | ```if (!lpparam.processName.equals("com.tencent.mm")) return;``` |

---

### F-17：调用高权限 API（getRunningAppProcesses 等）

| 项 | 内容 |
|----|------|
| 日期 | 2026-05-19 |
| 分类 | API 权限滥用 |
| 方案 | 业务代码调用 `ActivityManager.getRunningAppProcesses()` 等 |
| 症状 | 沙箱进程 SecurityException / 主进程触发 Matrix 检测点 |
| 根因 | 这些 API 是 Matrix 反向 hook 的监控点，且部分进程无权限 |
| 教训 | **业务代码完全禁用**：`getRunningAppProcesses` / `getProcessesInErrorState` / `getRecentTasks` 等 |
| 强制规则 | 代码扫描这些 API 调用 → 直接拒绝合入 |

---

### F-18：在错误进程 hook 朋友圈/会话相关类

| 项 | 内容 |
|----|------|
| 日期 | 2026-05-19 |
| 分类 | 模块作用域错位 |
| 方案 | 不区分进程，所有进程都 hook MvvmList 等微信主进程类 |
| 症状 | 微信启动慢 / 子进程崩溃 / 检测密度上升 |
| 根因 | MvvmList 等类只在主进程加载，在子进程 hook 会 ClassNotFoundException 或意外行为 |
| 教训 | hook 业务类前必须**进程白名单 + ClassLoader 验证** |

---

### F-19：调用 `__system_property_get("ro.boot.*")`

| 项 | 内容 |
|----|------|
| 日期 | 2026-05-19（基于 QE66 P0~P14 实证 + D 线 verifiedbootstate 演化数据）|
| 分类 | 反检测自伤 |
| 方案 | 为了"反检测"或"伪装 unlock=no"主动读 boot 属性 |
| 症状 | verifiedbootstate 计数+1，触发 Matrix 反向 hook 记录 |
| 根因 | 8.0.66 起 `ilink2` 部署了 `__system_property_get` 监控，调用任何 `ro.boot.*` key 都被记录 |
| 教训 | **不调** `__system_property_get` 传 `ro.boot.*`；**不要**为反检测主动读 boot 属性 |
| 强制规则 | 代码扫描禁止包含 `ro.boot.` / `verifiedbootstate` / `bootloader` 字符串 |

---

### F-20：Java 层 `SystemProperties.get("ro.boot.*")`

| 项 | 内容 |
|----|------|
| 日期 | 2026-05-19 |
| 分类 | 同 F-19 的 Java 等价物 |
| 方案 | 用 `android.os.SystemProperties.get()` 读 boot 属性 |
| 症状 | 同 F-19 |
| 根因 | `SystemProperties.get` 内部就是调 `__system_property_get`，jni_hook 已部署 |
| 教训 | Java 层同样禁用 `SystemProperties.get("ro.boot.*")` |

---

### F-21：引入 native 三件套（Pine + bypassmm + shadowhook）

| 项 | 内容 |
|----|------|
| 日期 | 2026-05-19（基于 apk2/network_compare + ANTI_DETECTION_DEEP_DIVE）|
| 分类 | 战略级封号面 |
| 方案 | 抄 Catfish 用 Pine ARM64 inline hook + bypassmm 签名伪装 + shadowhook linker hook |
| 症状 | 大批量盗版用户用这套出现封号高峰 |
| 根因 | 三件套 SO 是 D 线已采集的封号特征大头（256MB origin.apk + 7.7MB bypassmm + 91KB shadowhook + 82KB pine）|
| 教训 | **v1 纯 Java LSPosed**；**v2+ 如引 native 仅限"性能瓶颈点"**（如朋友圈 SnsObject.parseFrom 单点），不得作为业务逻辑层；候选库 LSPlant / bytehook / Dobby（与 Catfish 不撞） |
| 强制规则 | v1 完全禁止引入 `libpine.so` / `libbypassmm.so` / `libshadowhook.so`；v2+ 引入任何 native 必须经用户授权 + 风险复核 |

---

### F-22：发布前不跑 `frida_stats.js` 基线对比

| 项 | 内容 |
|----|------|
| 日期 | 2026-05-19（基于 QE66 P0~P14 流水线）|
| 分类 | 质量门控失守 |
| 方案 | 写完代码直接装机不跑检测密度对比 |
| 症状 | 某次发包后某指标突增（如 verifiedbootstate 从 15 跳到 30）未被及时发现 |
| 根因 | 没有自动对比机制 = 检测密度演化无可见性 |
| 教训 | **每个 P 任务关闭前必跑** `frida_stats.js spawn` + 对比基线 |
| 强制规则 | KPI: verifiedbootstate ≤ 20 / PROP ≤ 150/100K / normsg ≤ 4000/100K / CONN ≤ 0.2 |
| 红线 | 任一指标超红线 → P 任务**回滚不合入** |

---

### F-23：JniHook 注入（Hidden API bypass via JNI_OnLoad）→ CodecLooper SIGSEGV + 微信强制下线

| 项 | 内容 |
|----|------|
| 日期 | 2026-05-19（小米9 cepheus, Android 11, MIUI 12.5.6，微信 8.0.66 versionCode=2980）|
| 分类 | native 注入自伤 → 登录即下线 |
| 模块 | `com.wxguard.module` v1.0.0（QE66 B 线早期版本，源码 `C:\Users\Me\Desktop\apk2\_1__B_rewrite\wechat-guard\`，入口 `SessionInit.kt`）|
| 症状 | 模块启用时微信 8.0.66 **100% 启动即崩**：`SIGSEGV pid=* tid=* CodecLooper signal 11 SEGV_ACCERR fault addr 0x7a41b208d0 pc 0x38d0 /system/lib64/android.hardware.graphics.mapper@2.0.so`。4 次崩溃完全一致（同地址/同线程/同信号）。微信自身写出 `.dmp + .fulldmp`。进程反复死亡 → **登录成功后被强制下线**。|
| A/B 对照 | 模块启用=100% 崩溃；模块禁用=0 崩溃。同版本微信，同设备，排除版本/系统问题。|
| logcat 关键行 | `JniHook: JNI_OnLoad → Hidden API restrictions disabled`（14:53:18）→ `NativeCrash: Entered signal handler`（14:53:20）→ `Fatal signal 11` → 进程终止 |
| 根因（观测） | `JniHook` 在 `JNI_OnLoad` 阶段向进程注入、禁用 Hidden API 限制，导致 `graphics.mapper@2.0.so` 内存访问异常。时序已确认（注入在先，SIGSEGV 在后）。|
| 不做的推测 | ❌ 不说"微信风控主动触发崩溃"；❌ 不说具体是哪个业务 hook 触发（ConvHook / Application.attach 等均未确认）；❌ 不说 mapper 损坏机理 |
| 教训 | **禁止在 LSPosed 模块中使用任何 JniHook / JNI_OnLoad 注入方式**（含通过反射调用 `VMRuntime.setHiddenApiExemptions` 的 native 封装版本）。Hidden API 访问应通过 `XposedHelpers.findMethod` / `XposedHelpers.findField`（LSPosed 已绕限），无需 JniHook。|
| 强制规则 | 1. `com.ghost.assist` 包内**禁止加载任何自定义 .so**（v1 阶段）。2. 禁止在任何模块代码中出现 `JNI_OnLoad`、`System.loadLibrary`、`dlopen`。3. 如未来 v2+ 引入 native，必须经用户授权 + 风险复核（见 F-21 铁律）。|

---

### F-24：LSPosed 模块内用 `startService` / `startForegroundService` → Service not found

| 项 | 内容 |
|----|------|
| 日期 | 2026-05-19（小米9, Android 11, 微信 8.0.66，com.ghost.assist 首次装机）|
| 症状 | `StatusNotification` / `OverlayWindow` 继承 `Service`，`startForegroundService` 调用后无效，logcat 报 `Service not found` |
| 根因 | LSPosed 模块运行在**宿主进程（微信）内**，不是独立进程。模块自己的 `AndroidManifest` Service 声明对宿主不可见。|
| 教训 | 模块内**禁止** `startService` / `startForegroundService` / `bindService` / `JobScheduler` / `AlarmManager`。|
| 正确替代 | Notification → `NotificationManager.notify()`；悬浮窗 → `WindowManager.addView()`；后台任务 → `Thread` / `HandlerThread`；定时 → `Handler.postDelayed` |
| 强制规则 | 模块代码禁止出现 `extends Service`；`AndroidManifest` 禁止 `<service>` 声明 |

---

### F-25：`catch (Exception e)` 吞不住 `XposedHelpers.findAndHookMethod` 抛出的 `NoSuchMethodError` → init 序列静默中断

| 项 | 内容 |
|----|------|
| 日期 | 2026-05-19（小米9, 微信 8.0.66，P16 MomentsFilter 首次装机）|
| 症状 | logcat 只有 5 行，step 6 之后 init 序列完全中断，MomentsFilter / debug tools 日志全无，无任何报错输出 |
| 根因 | `XposedHelpers.findAndHookMethod` 找不到方法时抛 `NoSuchMethodError`，继承自 `Error` 而非 `Exception`。`catch (Exception e)` **捕获不到 Error**，导致异常穿透并中止后续所有 init 步骤 |
| 教训 | **LSPosed 模块 install() 方法必须 `catch (Throwable t)`**，不能只 catch Exception |
| 正确写法 | `try { XposedHelpers.findAndHookMethod(...); } catch (Throwable t) { Log.e(TAG, "[XX] hook failed: " + t); }` |
| 额外方案 | 换用原始反射 + `XposedBridge.hookMethod(method, callback)` 绕过 LSPosed 的 method 查找，对混淆类更稳 |
| 强制规则 | 模块内所有 `XposedHelpers.findAndHookMethod` / `findAndHookConstructor` 调用必须包在 `catch (Throwable)` 里，**禁止只 catch Exception** |

---

### F-26：`XposedHelpers.findMethodExact` 找不到继承自父类的方法 → `NoSuchMethodError`

| 项 | 内容 |
|----|------|
| 日期 | 2026-05-19（P16 MomentsFilter，微信 8.0.66）|
| 症状 | `XposedHelpers.findMethodExact(SnsObject.class, "parseFrom", byte[].class)` 抛 `NoSuchMethodError` |
| 根因 | `parseFrom(byte[])` 实际定义在父类 `com.tencent.mm.protobuf.f`，不在 `SnsObject` 自身。`findMethodExact` 只查 `getDeclaredMethods()`（本类声明），**不遍历继承链** |
| 教训 | hook protobuf 生成类的 `parseFrom` / `newInstance` 等继承方法，不能用 `findMethodExact`，需用 `getMethods()` 遍历后 `XposedBridge.hookMethod()` |
| 正确写法 | `java.lang.reflect.Method m = null;` <br>`for (Method method : SnsObject.class.getMethods()) {` <br>`  if ("parseFrom".equals(method.getName()) && ...参数匹配...) { m = method; break; }` <br>`}` <br>`if (m != null) XposedBridge.hookMethod(m, callback);` |
| 适用范围 | 所有 protobuf-lite 生成类（`parseFrom` / `newBuilder` 等均继承自 `MessageLite` 或混淆父类）|
| 强制规则 | hook protobuf 类方法**禁用** `findMethodExact`，一律用 `getMethods()` + `XposedBridge.hookMethod()` |

---

### F-27：`SnsObject.parseFrom(byte[])` Java hook 从未命中 — 8.0.66 走 JNI/C++ 路径

| 项 | 内容 |
|----|------|
| 日期 | 2026-05-19（P16 朋友圈，小米9 Android 11，微信 8.0.66）|
| 症状 | `getMethods()` + `XposedBridge.hookMethod` 安装成功无报错，但 F05 计数永远 0，logcat 无任何 `[MF] parseFrom hit` 输出 |
| 根因 | 8.0.66 朋友圈 SnsObject 反序列化走 JNI 直调 C++ protobuf-lite，根本不经过 Java `parseFrom(byte[])`。Java hook 面对的是空气。 |
| 验证 | Frida `SnsObject.parseFrom.overload('[B').implementation` 同样零命中（可通过 attach + 刷朋友圈验证，预期无 HIT）|
| 教训 | **禁止在 v1 使用 `SnsObject.parseFrom` 作为朋友圈过滤 hook 点**。CLAUDE.md §5.2 "Proto 层优先"的理论在 8.0.66 被实践证伪。|
| 替代方案 | `filter_moments.js v12`（L1 验证）：MvvmList + ArrayList 实例拦截。类名 `k24.b`，wxid 字段 `Username`，8.0.66 锁定版本内稳定。|
| 强制规则 | v1 朋友圈过滤只用 v12 方案。parseFrom 方案**永久搁置**，v2 如需重启须先做 JNI 层验证。|

---

### F-28：`MvvmList.m(List, boolean)` 类级别 hook 零命中 — 朋友圈数据不走 m()

| 项 | 内容 |
|----|------|
| 日期 | 2026-05-19（P16 MomentsFilter v12 重写，小米9 Android 11，微信 8.0.66）|
| 症状 | `XposedBridge.hookMethod(MvvmList.m(List,boolean), beforeHookedMethod)` 安装成功无报错，但刷朋友圈 F05 计数永远 0，beforeHookedMethod 从未触发 |
| 根因 | 朋友圈数据**不经过 `MvvmList.m()`**。Frida v12 验证：数据直接通过 `addAll` 写入 adapter `H` 字段里的 `o`/`p` ArrayList 实例，**绕过 m()**。`m()` 存在于 MvvmList（30 个 declared methods 可见），但不是朋友圈数据入口。 |
| 验证 | Frida t06 探针：MvvmList.m(List,boolean) 存在 ✅；filter_moments.js v12 warm-attach INSTALL OK（o size=11）✅；LSPosed hook MvvmList.m() 零命中 ❌ |
| 教训 | **禁止以 `MvvmList.m()` / `MvvmList.s()` 类级别 hook 作为朋友圈过滤入口**。方法存在 ≠ 数据流经该方法。 |
| 正确方案 | v12 真实路径：hook `y1`（`com.tencent.mm.plugin.sns.ui.improve.component.y1`）构造函数 → 反射取 `H` 字段（ViewModel）→ 反射取 `o`/`p` ArrayList → hook `ArrayList.addAll(Collection)` 并按实例身份过滤 |
| 强制规则 | MomentsFilter.java 必须实现 y1→H→o/p→addAll 方案，**禁止复用** MvvmList.m()/s() 类级别 hook |

---

### F-29：y1.notify* / y1.m(o0) 零命中 — 8.0.66 朋友圈不走标准 RecyclerView 通知路径

| 项 | 内容 |
|----|------|
| 日期 | 2026-05-19（P16 MomentsFilter v13/v14，小米9 Android 11，微信 8.0.66）|
| 症状 | hook `y1.notifyDataSetChanged` / `notifyItemRangeInserted` / `notifyItemRangeChanged` / `y1.m(o0)` 全部零命中，F05 计数纹丝不动 |
| 根因 | y1 是被动读取器（只实现 `getItem`/`getItemCount`），数据直接通过 MvvmList StateFlow → ArrayList.addAll 写入，y1 的 notify* 和唯一 declared method `m(o0)` 均不在数据更新链路上 |
| 验证 | Frida `list_y1_methods.js`：y1 仅一个 declared method `m(o0)`，无 submitList / AsyncListDiffer / notify* 调用；logcat 安装成功但零命中确认 |
| 教训 | **禁止以 y1.notify\* 或 y1.m() 作为朋友圈过滤入口**。唯一有效拦截点是 `ArrayList.addAll`（全局 hook，k24.b 类型检测）|
| 遗留问题 | addAll 全局 hook 过滤有效，但 WeChat 在 addAll 之前预捕获 newItems.size() 作为 notify 计数，导致 RecyclerView 收到错误 count → 快速下滑偶发跳顶。StateFlow 上游拦截（`h2.e` / `StateFlow.setValue`）尝试后闪退，暂未解决。|
| 强制规则 | **禁止在 y1 层做任何数据写入 hook**；addAll 全局 hook 是当前唯一可行路径 |

---

### F-30：甜密友/微密友 getView+height=1+GONE 方案不适用于 8.0.66

| 项 | 内容 |
|----|------|
| 日期 | 2026-05-19（P16 样本对比）|
| 结论 | 甜密友（8.0.63 smali 织入）和微密友（8.0.70 native/Pine hook）的朋友圈过滤均通过 `BaseAdapter.getItem(index) → field_userName → height=1 + GONE` 实现，依赖旧 ListView + BaseAdapter 架构 |
| 不适用原因 | 微信 8.0.66 朋友圈使用 RecyclerView + MvvmList + StateFlow，无 ListView / BaseAdapter，`getView`/`height=1`/`V.GONE` 方案在 8.0.66 无效 |
| 强制规则 | **P16 及后续版本禁止参考 getView/height=1/GONE 作为实现路线**，只作产品行为对照 |

---

### F-31："顺手优化"已验证 hook 点 → 导致已通过功能静默失效

| 项 | 内容 |
|----|------|
| 日期 | 2026-05-21（P16 MomentsFilter D1，微信 8.0.71）|
| 症状 | D1 `h1()` 主路径被"优化"后 null miss，朋友圈过滤静默失效（无报错、无崩溃，只是不过滤）|
| 根因 | 执行窗口在非用户要求的情况下对**已装机验证通过**的代码路径做了"顺手优化"，破坏了 fallback 机制之外的调用链 |
| 教训 | 已通过装机验证的功能，任何改动（哪怕看起来是"优化"）都必须先征得用户同意，否则可能引入无症状回归 |
| 强制规则 | **禁止对已装机验证通过的 hook 点做任何未经用户明确同意的修改**（包括"顺手优化"、重构、精简）。现状跑通 = 不动。|
| 当前 D1 正确路径 | `ArrayList.addAll(na4.b)` → `la4.p.field_userName` 直读（fallback）→ remove。h1() 路径已 null miss，**禁止恢复 h1() 为主路径**。|

---

---

### F-32：ConvFilter L4 clean-before + WeChat 原生 notify → DiffUtil ItemAnimator 卡帧 → 左侧裂缝

| 项 | 内容 |
|----|------|
| 日期 | 2026-05-23（P20C，小米9 Android 11，微信 8.0.71）|
| 症状 | 冷启动后 20 秒内会话列表左侧出现约 20% 宽度的裂缝（LauncherUI 从聊天页背后透出），多次返回后消失 |
| 根因 | WeChat 冷启动从 SQLite cache 恢复会话列表，走直接字段写入路径（绕过 L0/L1/La/L3/Le 全部 hook，0 命中）。密友 item 在 MvvmList.h/o/p 内存在约 26ms。当 WeChat 自发 `h0.notifyDataSetChanged` 时，L4 clean-before 先移除 hidden items，再放行 WeChat 的 notify。h0 adapter 内部走 DiffUtil 差值计算，对 2 条被移除的 item 发出 `notifyItemRemoved`，触发 ItemAnimator 删除动画。在 小米9 慢速 GPU 上，动画期间若用户打开聊天，转场与 ItemAnimator 叠加掉帧，slide-back 面板冻结在 ~20% → 持久左侧裂缝 |
| 排查过程 | Step1 禁用 warm-attach clean：无效。Step2 禁用 D2D3 dump：不在时间窗口（+6.2s）。完整冷启动时序（844行）确认 L0addAll+L4 路径为唯一数据流，La/L1/L3 零命中 |
| 修复方案 | **L4-NoDiff**（已实施）：`hookAdapterByClass.beforeHookedMethod` 中，clean 后若 removed>0，执行 `param.setResult(null)` 取消 WeChat 原生 notify，改由 `Handler.post { adapter.notifyDataSetChanged() }` 在下一帧发全量刷新。全量 notify 不触发 DiffUtil，ItemAnimator 无动画帧，<2ms 延迟 |
| 回退方法 | 注释 `param.setResult(null)` 及 Handler.post 块，删除 `l4CleanAndCount()` 辅助方法，`cleanConvData("L4")` 的直接调用方式即为原版 clean-before |
| 风险 | 极少数依赖 h0 DiffUtil 差值通知的内部逻辑可能不触发（目前实测未发现影响）|
| 设备相关 | 小米9（慢 GPU）必现；新设备可能不可见，但修法对所有设备无害 |
| 强制规则 | **禁止在 L4 hook 中同时让 WeChat 原生 DiffUtil-notify 和 hidden-item clean 共存**；clean 后必须 setResult(null) 接管 notify |

---

### F-33：V→H / H→V 刷新链路 — adapter ref 污染与 recreate 黑屏（2026-05-23 完整复盘）

| 项 | 内容 |
|----|------|
| 日期 | 2026-05-23（P20C → ConvFilter 刷新专项，小米9，微信 8.0.71）|
| 症状 | V→H 切后台回来密友未隐藏；H→V 输入 111111 密友未显示 |

#### 根因链

**sConvAdapterRef 全程 null（adapter discovery 冷启动时序差）**

`installAdapterDiscovery` 把 hook 装在 `ConversationListView.setAdapter` 和 `View.onAttachedToWindow`。但微信冷启动时这两个事件在 LSPosed `handleLoadPackage` 完成前已发生，callback 永远未触发。`sConvAdapterRef` 全程 null。

**sAdapterRef 被 h0 覆盖（L4 priming 误包含 h0）**

`notifyConvAdapter` 降级到 `sAdapterRef`（L4 触发的最后一个 adapter）。L4 对 `kc5.v0` 和 `com.tencent.mm.ui.base.preference.h0` 都挂了 hook，h0 在 L4 中比 kc5.v0 **晚触发**，`sAdapterRef` 最终停在 h0。通知 h0 对会话 ListView 无效。

**`recreate()` 方案副作用：B2 误触发（已废弃）**

中途曾用 `act.recreate()` 作为无 ref 时的 nuclear 方案。`recreate()` 导致 `LauncherUI.onStop`，B2 的 `onActivityStopped` 计数归零，`enterHidden` 把 H→V 刚切好的 VISIBLE 态在 150ms 内打回 HIDDEN。`isChangingConfigurations()` 可压制该 B2 误触发，但 recreate 本身带黑屏，已废弃。

#### 最终修复（两行，不得撤销）

**修复 1 — L4 只用 kc5.v0 更新 sConvAdapterRef**（`ConvFilter.java` `hookAdapterByClass.beforeHookedMethod`）

```java
// 只认 kc5.v0，h0 / q2 均不写入
if (ADAPTER_CLASS_71.equals(cn)) {
    sConvAdapterRef = new WeakReference<>(adapter);
    Object mv = findMvvmListOnAdapter(adapter);
    if (mv != null) rememberMvvmList(mv);
}
```

微信冷启动时 kc5.v0 必定调用 `notifyDataSetChanged` 渲染初始列表，L4 此时可靠捕获实例。比任何 setAdapter/onAttachedToWindow hook 都早且稳定。

**修复 2 — notifyConvAdapter 最终 fallback 加类名守卫**（`ConvFilter.java` `notifyConvAdapter`）

```java
// sAdapterRef fallback：只接受 kc5.v0，h0/q2 拒绝
if (ADAPTER_CLASS_71.equals(fallback.getClass().getName())) {
    adapter = fallback;
} else {
    Log.w(TAG, "[BUS:...] sAdapterRef=" + cn + " is not v0, skip");
}
```

#### 强制铁律

| 条目 | 规则 |
|------|------|
| **禁止** | 在 L4 priming 中把 `ADAPTER_CLASS_71_H0`（h0）加入 sConvAdapterRef 更新条件 |
| **禁止** | `notifyConvAdapter` 的任何 fallback 路径使用非 kc5.v0 的 adapter |
| **禁止** | 用 `act.recreate()` 刷新会话列表（黑屏 + B2 误触发）|
| **禁止** | 在 B2 `onActivityStopped` 里删除 `isChangingConfigurations()` 检查（删了旋转屏幕也会误触 enterHidden）|
| **必须** | sConvAdapterRef 的唯一写入点为：L4 beforeHook（cn = kc5.v0）+ ConversationListView.setAdapter hook |
| **必须** | sMvvmListRef 在 L4 priming 同步更新（`findMvvmListOnAdapter` → `rememberMvvmList`）|

#### 验证基准（每次改动 ConvFilter 后必跑）

```
冷启动后：[CF:L4] entry adapter=v0 ... + [CF:mvvmref] remember MvvmConvList
V→H 后：  [BUS:pendingHide] notified adapter=v0    ← 必须 v0，不能 h0/q2
H→V 后：  [BUS:pendingRestore] notified adapter=v0 ← 同上
```

---

### F-34：sConvCache 无条件清空 → H↔V 多轮后 cache=0 死循环

| 项 | 内容 |
|----|------|
| 日期 | 2026-05-23 |
| 症状 | 冷启动 H→V 第一次密友能显示；第二次及之后 `[CF:restore] cache=0`，密友无法注回 |
| 根因 | BUS-H 回调里 `sConvCache.clear()` 无条件执行。此时 MvvmList 已无密友（已被上轮 clean 移走），后续 `cleanMvvmList` removed=0 → `putCache` 从不调用 → 缓存永远是空 |
| 修复 | 把 `sConvCache.clear()` 从 BUS-H 移入 `cleanMvvmList` 内部，**lazy-clear**：第一次真正 remove 时才清，remove=0 时保留旧缓存 |
| 强制规则 | **禁止在 BUS-H / BUS-V / 任何状态切换回调里无条件 `sConvCache.clear()`**；缓存只能由 `cleanMvvmList` 首次 remove 时清，或由 `restoreCachedItems` 过期时清 |

---

### F-35：v25 跨 List identity / v26 list-visited 双层强 dedup → BUS-V-adapter.p 零条 → RecyclerView 不重绘

| 项 | 内容 |
|----|------|
| 日期 | 2026-05-27 |
| 症状 | v25/v26 装机后 H→V 切换：日志 `restoreInPlace o/p/h 各 injected=3`、`BUS-V-adapter.q.d injected=3`，但 `BUS-V-adapter.p` **零条**；肉眼密友密群**全不显示**，等新消息到才出现 |
| 根因 1（v25） | `injectCacheIntoList` / `restoreToMvvmList` 内层加 `item == itemToInject` identity check：`restoreToMvvmList` 先把 fresh kc5.y 注入 MvvmConvList.h/o/p；随后 `restoreAdapterGraphFromCache` 递归 adapter.p（即同一个 MvvmConvList 实例）时，p.h/p.o/p.p 已含 fresh → identity 命中 → `injected=0`。**adapter.p 跨字段后续注入全被拦** |
| 根因 2（v26） | `restoreConvListsInObject` + `restoreToMvvmList` 用 `IdentityHashMap` 跟踪已访问 List：同一 backing List 实例只走一次注入。表象与 v25 等价 |
| 共因 | v24 原本依赖密群 wxid 比对失败导致的「同 List 多次 list.add」**副作用**：每多一次 add 就一次 ArrayList.modCount++，RecyclerView 在 notifyDataSetChanged 时看到 modCount 变化才重绘；v25/v26 把这些「多余 add」全部 dedup 掉 → modCount 不动 → RecyclerView 跳过重绘 |
| 解法 | v27 = v24 wxid-only dedup 保留多次 insert 副作用 + 80ms 异步 `postDedupAdapterGraph` 按 identity 把同对象引用收敛成 1 份（先脏后净，肉眼最多见 80ms 闪一下）。**禁止把 dedup 提前到 notify 之前同步执行** |
| 强制规则 | ❌ 禁止在 `injectCacheIntoList` / `restoreToMvvmList` 加跨 List identity check（v25 已证伪）；❌ 禁止把 list-visited 加到 `restoreConvListsInObject` 或 `restoreToMvvmList` 的 List 层（v26 已证伪）；✅ identity dedup 必须放在 notify 之后异步执行 |
| 关联 | docs/CONV_REFRESH_PROBLEM.md §十六 / §十七、HOOKMAP.md §二 V↔H 实时刷新行 |
| 后续观测 · 2026-05-27 v28 | **仅会话 tab（`kc5.v0` / `MvvmList`）热切路径下 5 轮未复现 `removed 3→12` dedup 锁死**。装箱日志：`bug排查/final_v28_5rounds.log:8181`、`bug排查/final_v28_coldstart.log`。**通讯录 tab（`AddressLiveList` / `ik3.t0`）未进入本轮验证范围 — V 态 hot-restore 未实现，单独归属 P_CV1**。F-35 强制规则保留不动；本行仅作历史追加，**不视为铁律解除** |

### F-36：8.0.71 语音/视频来电拦截 — 6 条已证伪路径（2026-05-29 收口合集）

> 权威设计文档：[`docs/P22_PushFilter_VoIP.md`](./docs/P22_PushFilter_VoIP.md) §七。以下全部装机 L1 实证。

| # | 已证伪路径 | 根因 | 正确做法 |
|---|-----------|------|---------|
| a | **来电持续振动**（单/重复波形）| MIUI 把第三方 app 所有 Vibrator 波形截成一下短的 | 只做来电**单次 onset** 振动（`VIB_CALL={0,400,220,400}` USAGE_ALARM） |
| b | **放行微信官方持续振动**（VV 不拦）| 藏了来电 UI 后微信不知用户已处理 → 官方振动响到 ~60s 超时，对方挂了也停不下 → **死循环** | VV 两个模式都拦微信自身振动；振动只由我们 onset 提供 |
| c | **`Service.stopForeground` 当挂断信号** | 视频 VoIP 服务每 ~10s 循环 start/stopForeground，非挂断标记 → 提前清 `sVoIPCallPending` → 挂断嘟漏 + onset 重触发死循环 | pending **只由 120s fallback 清零**；不 hook stopForeground 判挂断 |
| d | **单 boolean `sRemovedByUs` 辨别 VC 移除** | 视频 attach 两个渲染 view（VoIPRenderTextureView+VoIPMPVoIPVideoView），第 2 个 detach 误判挂断 → 60ms 清 pending → 声音漏 | 用计数器 `sRemovedByUsCount` |
| e | **FB `onAttachedToWindow → removeView`** | view 已 attach 上屏，移除前渲染一帧 → 桌面半透明痕迹 | hook `WindowManagerImpl.addView` 命中 `.plugin.ball.view.*` 直接 `setResult(null)` 不让 add |
| f | **MP/VW 只判 `isActive()`（不判 pending）** | HIDDEN 态全拦所有 MediaPlayer/亮屏 → 误杀非密友媒体/正常亮屏 | 必须加 `sVoIPCallPending` 门，只在来电窗口拦 |
| g | NM L1-gap 写死 `id==-525958226` | 那是某测试密友的通知 id，换人失效 | 靠 `sL1BlockedLastItem` 标志判密友，200ms 窗口无 id 限制 |

**强制规则**：来电拦截**只改 §七 未列为证伪**的层；动任何已装机验证的层前必须问用户（铁律 29）。

---

1. 写代码前先 grep 本文件查"我要做的事"是否已被否决
2. 见到 ❌ 标记或本表中任一条 → **立即停手**，找替代方案
3. 发现新的失败方案 → 立即追加为 F-23/24/25...，**永久不删**
4. 同一类失败不重复登记，但要在原条目追加新症状
