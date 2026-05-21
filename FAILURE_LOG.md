# FAILURE_LOG — 失败方案归档（28 条铁律）

> **铁律**：已证实失败的方案，**任何人不得复用**。AI 接手必读。
> 旧 15 条详细 → [`./refs/FAILURE_LOG.md`](./refs/FAILURE_LOG.md)
> 新 13 条（F-16 ~ F-28）见下方 §二

更新时间：2026-05-19

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

## 二、F-16 ~ F-22 新增（基于 D 线 + 8.0.66 实证）

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

## 三、铁律使用方法

1. 写代码前先 grep 本文件查"我要做的事"是否已被否决
2. 见到 ❌ 标记或本表中任一条 → **立即停手**，找替代方案
3. 发现新的失败方案 → 立即追加为 F-23/24/25...，**永久不删**
4. 同一类失败不重复登记，但要在原条目追加新症状
