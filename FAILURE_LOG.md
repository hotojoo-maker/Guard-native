# FAILURE_LOG — 失败方案索引（F-01~F-43）

> **铁律**：已证实失败的方案，**任何人不得复用**。AI 接手必读。
> 📦 详细试错正文（全文，F-01~F-42）已于 2026-06-29 相位收口迁入 [`07_archive_归档/FAILURE_LOG_full_20260629.md`](./07_archive_归档/FAILURE_LOG_full_20260629.md)（完整快照·搬柜不销毁·永久保留）。本文件保留一行索引 + 指针。
> 旧 15 条（F-01~F-15）原始详细 → [`docs/isolation/FAILURE_LOG_catfish.md`](./docs/isolation/FAILURE_LOG_catfish.md)（apk2/竞品副本，与本项目 F 系列不同源）。

更新时间：2026-07-01（增 F-43 LSPatch metaloader 重签模块导致 cert mismatch；相位收口 F-01~F-42 详细正文仍在 archive，F-43 详细见 `DECISION_LOG.md` D-027）

---

## 一、F-01 ~ F-15 摘要（apk2 继承坑；详细 → `docs/isolation/FAILURE_LOG_catfish.md`）

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

## 二、F-16 ~ F-42 索引（一行一条 · 全文 → `07_archive_归档/FAILURE_LOG_full_20260629.md`，按 F-编号搜索）

- **F-16** LSPosed 模块不加进程白名单 → 沙箱进程 FATAL — 入口第一行必须判进程名，只 hook `com.tencent.mm` 主进程
- **F-17** 调用高权限 API（getRunningAppProcesses 等）— 业务代码完全禁用这些 Matrix 监控点 API
- **F-18** 在错误进程 hook 朋友圈/会话类 — hook 业务类前必须进程白名单 + ClassLoader 验证
- **F-19** 调用 `__system_property_get("ro.boot.*")` — 不为反检测主动读 boot 属性（verifiedbootstate +1）
- **F-20** Java 层 `SystemProperties.get("ro.boot.*")` — 同 F-19，Java 层一样禁用
- **F-21** 引入 native 三件套（Pine+bypassmm+shadowhook）— v1 纯 Java；native 是封号特征大头，v2+ 仅限性能单点
- **F-22** 发布前不跑 `frida_stats.js` 基线 — 每个 P 任务关闭前必跑对比基线（KPI 红线）
- **F-23** 重碰 native（广钩/改返回/`JNI_OnLoad` 注入）→ CodecLooper SIGSEGV + 强制下线 — 禁重碰；只读观测不在此限
- **F-24** 模块内 `startService`/`extends Service` → Service not found — 用 `NotificationManager.notify()` / `WindowManager.addView()`
- **F-25** `catch(Exception)` 吞不住 `NoSuchMethodError` → init 静默中断 — `findAndHookMethod` 必须 `catch(Throwable)`
- **F-26** `findMethodExact` 找不到父类方法 → `NoSuchMethodError` — hook protobuf 类用 `getMethods()`+`hookMethod()`
- **F-27** `SnsObject.parseFrom` Java hook 零命中（走 JNI/C++）— 朋友圈过滤只用 MvvmList 实例拦截，parseFrom 永久搁置
- **F-28** `MvvmList.m(List,boolean)` 类级 hook 零命中 — 用 y1→H→o/p→addAll 实例过滤（方法存在≠数据流经）
- **F-29** `y1.notify*`/`y1.m(o0)` 零命中 — 唯一拦截点 `ArrayList.addAll`，禁在 y1 层做数据写入 hook
- **F-30** 甜密友/微密友 getView+height=1+GONE 不适用 8.0.66 — 8.0.66 走 RecyclerView，禁参考该路线
- **F-31** "顺手优化"已验证 hook 点 → 静默失效 — 已装机验证的 hook 现状跑通=不动，改前必征用户同意
- **F-32** ConvFilter L4 clean-before + 原生 notify → DiffUtil 卡帧裂缝 — clean 后必须 `setResult(null)` 接管 notify
- **F-33** V→H/H→V 刷新 adapter ref 污染 / `recreate()` 黑屏 — sConvAdapterRef 只认 `kc5.v0`；禁用 `recreate()` 刷新
- **F-34** sConvCache 无条件清空 → cache=0 死循环 — 缓存 lazy-clear，只在首次 remove 时清
- **F-35** v25/v26 双层强 dedup → adapter.p 零条不重绘 — identity dedup 必须 notify 之后异步执行
- **F-36** 8.0.71 来电拦截 6 条已证伪 — 只改 §七未证伪层；onset 单次振动 + pending 仅 120s fallback 清
- **F-37** a2.b 8.0.70 直搬 8.0.71 收撤回零触发 — 真出口 `jy0.t.f`(doRevokeMsg)；tinker 类用 `app.getClassLoader()`
- **F-38** 伪装订位消息层坐标候选全证伪 — 正解 LBS 分发源 `pz0.h.c` 改 arg2/arg3（全局生效）
- **F-39** ContactLabelHideGuard `getMethod("onResume")` 命中父类 → 误 finish 搜索/设置页 — 回调里按真实类名二次校验
- **F-40** 反复重装顶爆 risk → decoy 假种子 → recipeOk=false 全功能空转 — 开发期别狂 `install -r`，会触发自家蜜罐
- **F-41** 后台"标记正常"只改 device_status 不重置 tier/risk — 隐藏失效查 `tier/risk`，不查 `status`
- **F-42** LSPatch 439×Android15 干净装官替仍闪退（调查中）— LSPatch 免 root 客户包 🔴 阻塞，详见 `03_execute_执行任务/P_LSPatch_A15_VivoCrash/INVESTIGATION.md`
- **F-43** LSPatch metaloader 重打包重签模块 → `bindSigningCert` 读 `sModulePath` 拿到 `ca421ec3`（LSPatch 内置 debug keystore）≠ 编译产物 cert `e3e13a49` → registry 派生 key mismatch → 散沙、设置页报"授权异常" — release 流程下改读宿主整包 `app.getApplicationInfo().sourceDir`（LSPatch `-k` 那把 = 我方可控）。L1 实证 2026-07-01 02:42 PID 16979 `[native] certBind set sha256[0..3]=ca421ec3`、嵌入文件 sha256 与编译产物一字节不差但运行时 LSPatch extract 时换了 keystore；修复 = D-027。**强制**：以后所有 cert binding / A2 cert 完整性 / 重签蜜罐 都走宿主 sourceDir，禁回 sModulePath；除非 LSPatch 出 `--keep-module-cert` 类选项。

> ☝ 全文 / 多段根因·修复·强制铁律·验证基准 → [`07_archive_归档/FAILURE_LOG_full_20260629.md`](./07_archive_归档/FAILURE_LOG_full_20260629.md)（按 F-编号搜索）。`CLAUDE.md` §三 29 条现行红线即由本表 F-xx 提炼。

---

1. 写代码前先 grep 本索引 + 归档全文查"我要做的事"是否已被否决
2. 见到 ❌ 标记或本表中任一条 → **立即停手**，找替代方案
3. 发现新的失败方案 → 立即追加 F-43...（本文件加一行索引 + 全文进 `07_archive_归档/FAILURE_LOG_full_20260629.md`），**永久不删**
4. 同一类失败不重复登记，但要在原条目追加新症状
