# P_CV1 — 通讯录 V↔H 热切（ContactView 第 1 轮）worklog

- **2026-05-27 21:33** 任务建档（接班自 Guard Native141 会话 210015-3eb0e；上一会话已完成会话 tab V↔H v28 收口实证）。
- **2026-05-27 21:33** 用户拍板：P 号 = `P_CV1`；动作顺序 = 先落文档 + 建任务目录 + worklog 首行 + 贴框架合规预审，再切 `/guard-execute-one_单任务执行` 开干。
- **2026-05-27 21:33** 三份主档草稿写盘完毕：
  - `HOOKMAP.md` §二 表头加门控备注重申；line 63 V↔H 行改为 v28 仅会话 tab 收口 + 通讯录 ⛔ 未实现归属 P_CV1。
  - `FAILURE_LOG.md` F-35 末尾追加「2026-05-27 v28 后续观测」一行（**铁律不解除**，仅历史追加）。
  - `TASK_BOARD.md` §五新增 P_CV1 行 + 语义号说明。

---

## 一、起因（L1 + L2 双重证据）

| 证据源 | 内容 |
|--------|------|
| L1 装机截图（用户提供，2026-05-27 21:22） | 通讯录 tab V 态列表里**看不到密友 / 密群（如「测试密群」）**；同时段微信 tab（最近联系）能看到「测试密群」下午 5:27 |
| L2 代码互证 | `src/main/java/com/ghost/assist/moduleD/ContactFilter.java` `RefreshBus.register(...)` 回调内**只有 `cleanLiveList(...)`**，无 V 态 restore 分支；对比 `ConvFilter` 已实现 `sPendingRestore` + `expandCacheWithWarm` + `restoreToMvvmList` + `restoreAdapterGraphFromCache` + 80ms post-dedup（v28 收口） |
| 推论 | 缺口 = ContactFilter V 态 hot-restore 链路缺失，与现象一一对应 |

---

## 二、框架合规预审（`guard-auth-review` 输出，需在开干前已存档）

```
【框架合规预审】P_CV1 通讯录 V↔H 热切

门控涉及：State（只读 isActive() + 监听 RefreshBus）。Entry / Auth / Risk 均不碰。

允许动：
  - src/main/java/com/ghost/assist/moduleD/ContactFilter.java
    增 V 态 restore 分支 + warmAll 预热缓存 + 80ms post-dedup（对标 ConvFilter v28 链路，但仅落在 ContactFilter）
  - （可选）若 ContactFilter 因增量后接近 800 行，按 auth-review §零.六.C 抽 ContactHotReload.java
    （执行 AI 评估后决定，不在本轮强制）

禁止动：
  - StateMachine.java / AuthManager.java / RefreshBus.java / Bridge.java
  - SearchUnlock.java / SettingsEntry.java / TriggerGuard.java
  - ConvFilter.java（会话路径 v28 已收口，本轮零修改）
  - native_core/ 所有 .cpp / .h
  - DebugServer.java（不新增写接口）

执行 AI 只能：
  - 通过 RefreshBus 回调里的 hidden 参数判 V/H，不得在 Filter 里再 new 状态判定
  - 调 StateMachine.isActive() 只读取（间接覆盖三层门：授权 + f1 + mActive）
  - 复用 Bridge.allHiddenIds() / Bridge.isGroupId() / extractGroupId 已有 API
  - 参考 ConvFilter v28 设计模式（先脏后净 + 80ms 异步 post-dedup），但不复制 ConvFilter 代码主体

附加禁止动（防御 v1→v2 升级时踩坑）：
  - 不得在 V 态 restore 分支里用 `mActive` / `sm.getState()` 单独判断绕过 isActive() 三层门
  - 不得在 ContactFilter 任何位置直接调 isVipAuthorized() / isAuthOk() / AuthManager — 全部走 isActive() 间接取
  - 不得为「加速热切」自创跨 List identity dedup 或 list-visited 双层 dedup（F-35 已永久禁，禁在 inject/restore 主路径同步去重）

结论：可派活。
备注：装机后补跑 frida_stats.js 对比基线（F-22 铁律：发版重档要；P_CV1 自审轻档不阻断）。
```

---

## 三、开干前 checklist（执行 AI 自查）

- [ ] 已读 `.cursor/skills/guard-execute-one_单任务执行/SKILL.md`
- [ ] 已读 `src/main/java/com/ghost/assist/moduleD/ConvFilter.java`（参考 v28 链路，**不修改**）
- [ ] 已读 `src/main/java/com/ghost/assist/moduleD/ContactFilter.java` 全文
- [ ] 已读 `docs/CONV_REFRESH_PROBLEM.md` §十二 ~ §十七（v25/v26 → v27 → v28 演进史 + F-35 教训）
- [ ] 已读 `FAILURE_LOG.md` F-35（强制规则不得违反）
- [ ] 已读本 worklog §二 预审 — 允许动 / 禁止动 / 附加禁止动 三段全部记住

---

## 四、动手记录

| 时间 | 动作 | 文件 / 命令 | 结果 |
|------|------|-----------|------|
| 2026-05-27 22:00 | 授权检查官改前审查 | （由用户外注） | 🔬 PASS（附 3 条补丁必加：install 不装 hook + 注释 / `isWxid` 仅加 1 行 `Bridge.isGroupId` / 文件顶 4 行门控锁定注释） |
| 2026-05-27 22:10 | 新建 `ContactHotReload.java` | `src/main/java/com/ghost/assist/moduleD/ContactHotReload.java`（~330 行） | 🔬 含 4 行门控锁定头注释（补丁-3） / `install()` 注明「不装 hook、复用 ContactFilter.installFragResumeHook」（补丁-1） / `putCache` / `handleBusHidden` / `handleBusVisible` / `restoreToLiveList`（in-place + CME-safe）/ `postDedupGraph`（异步 80ms，F-35 合规） |
| 2026-05-27 22:12 | 改 `ContactFilter.java` | line 40+：`TAG`/`MVVMLIST_DATA`/`ADDR_ITEM_CLS` private → package-private（让 ContactHotReload 跨类访问）；`sLiveListRef` / `sAdapterRef` / `cleanLiveList` / `extractWxid` / `findFieldInHierarchy` 同样降可见性 | 🔬 |
| 2026-05-27 22:13 | `ContactFilter.install()` 改造 | 加 `ContactHotReload.install(lpparam)` 调用；改 `RefreshBus.register` 回调 — 按 `StateMachine.State` 分发 H→`handleBusHidden` / V→`handleBusVisible` | 🔬 |
| 2026-05-27 22:14 | 新增 `ContactFilter.notifyContactAdapter(String)` | 封装 `ik3.t0.notifyDataSetChanged` 给 ContactHotReload 调用 | 🔬 |
| 2026-05-27 22:15 | `filterContactList` 增 idx 计数器 + `ContactHotReload.putCache` 调用 | first-remove 时存入 cache，fieldName 取 `MVVMLIST_DATA` | 🔬 |
| 2026-05-27 22:15 | `installAddAllHook` 同上（fieldName=null 表示 pre-list） | line 98 内 idx 计数 + `putCache` | 🔬 |
| 2026-05-27 22:16 | **补丁-2** `isWxid()` 加 `if (Bridge.isGroupId(s)) return true;` | line 603 单行原子插入；package-private 可见性不变 | 🔬 |
| 2026-05-27 22:17 | Lint 校验 | `ReadLints` 两个文件全清 | ✅ |
| 2026-05-27 22:17 | 铁律 grep 自查 | `extends Service` / `findMethodExact` / `notifyItemRange` / `ro.boot.` / `System.loadLibrary` / `catch (Exception` 在新代码均零命中 | ✅ |
| 2026-05-27 23:30 | **新会话接手**（Claude Opus 4.7，session 232616-ae1fed63） | grep `bug排查\final_pcv1_v2_5rounds.log` 2189 行 | 🔬 **L1 根因定位**：`sLiveListRef` 永久 NULL —— `fragResume hooked 4 lifecycle methods` 装载成功但全程 0 hits（`MvvmAddressUIFragment` 在 8.0.71 lifecycle 不点火）；`L4 hooked: notifyDataSetChanged on f2 (filter=ik3.t0)` 装载成功但 0 [CTF:L4fire]（contact adapter 不走 notifyDataSetChanged 或 ik3.t0 类名变化）；唯一会点的是 `[CTF:addAll]`，但 thisObject 是 backing ArrayList、非 AddressLiveList 本身。用户截图说明 8.0.71 通讯录 tab 实际有 7+ section（新的朋友/仅聊天的朋友/群聊/标签/公众号/服务号/企业微信联系人/我的企业/A-Z），单一 fragment 假设可能错。 |
| 2026-05-27 23:35 | 用户拍板「上诊断补丁」（仅装诊断点、不动业务逻辑） | `ContactFilter.install()` | 🔬 |
| 2026-05-27 23:38 | 诊断 V0：`ContactFilter.install()` 后追加 3 个被冷冻 hook 调用 | `installWarmAttach(lpparam)` + `installFragMethodProbe(lpparam)` + `installRvSetAdapterProbe(lpparam)` | 🔬 不动业务逻辑、只装探针 |
| 2026-05-27 23:39 | `installWarmAttach` AddressLiveList ctor 加一行 `sLiveListRef = param.thisObject;` + 拉宽 log（带类全名 + `ref-set=1` 标记） | line 386-394 区段 | 🔬 |
| 2026-05-27 23:39 | `installWarmAttach` MvvmList 基类 ctor 加一行 `sLiveListRef = param.thisObject;`（仅 subCls 含 Address/address 时）+ `[CTF:mvvmCtor] addr-like` 区分日志 | line 410-422 区段 | 🔬 |
| 2026-05-27 23:40 | Lint | `ReadLints(ContactFilter.java)` 全清 | ✅ |
| 2026-05-27 23:48 | 用户：「你只需要解决，V状态H状态，热切通讯录群聊，联系正常隐藏，显示！」明确放权 | 口头 | 🔬 |
| 2026-05-27 23:50 | **V1 落地**：新建 `ContactDiscoveryHook.java`（~230 行） | `src/main/java/com/ghost/assist/moduleD/ContactDiscoveryHook.java` | 🔬 |
|  |  · 装 `Application.registerActivityLifecycleCallbacks`（与 TriggerGuard 同一 API，铁律 7 合规——不是 ActivityManager.getRunningAppProcesses） |  |  |
|  |  · onActivityResumed 命中白名单（LauncherUI / MainTabUI / Address / Contact / Chatroom 等）后 post-600ms 扫 DecorView View 树 |  |  |
|  |  · 找 RecyclerView (含子类) → `getAdapter()` → 反射 adapter 字段图最深 3 层找 MvvmList 子类（首元素 = `fc5.g`） |  |  |
|  |  · 找到后写 `ContactFilter.sLiveListRef = liveList` + `ContactFilter.sAdapterRef = WeakReference<adapter>` |  |  |
|  |  · 首次写盘后 latch 锁死、不再重扫（KPI 控制） |  |  |
| 2026-05-27 23:51 | `ModuleMain.onApplicationCreated` 内 `TriggerGuard.install(app)` 后追加一行 `ContactDiscoveryHook.install(app)` | `src/main/java/com/ghost/assist/ModuleMain.java:152` | 🔬 |
| 2026-05-27 23:52 | Lint | `ReadLints(ContactDiscoveryHook.java + ModuleMain.java)` 全清 | ✅ |
| 2026-05-28 00:30 | V1 装机：`[CDH:found]=0`（15 扫全 no-rv），密友 / 密群 仍未恢复。**根因**：8.0.71 通讯录可能用 `ListView` (旧版 `AddressUIFragment`)、不是 RecyclerView 子类——也可能 8.0.71 `WxRecyclerView extends X2CRecyclerView extends RecyclerView` 链路在某种情况下 isSubclassOf 走不到。| `bug排查\pcv1_v1_diag.log` 2395 行 | 🔬 |
| 2026-05-28 00:32 | jadx 验证：用户取 `I:\apk2\_3__D_wechat_ban\jadx_8071\sources\` 查 5 项 — AddressLiveList ✅ (Kotlin, extends MvvmList<fc5.g>) / MvvmAddressUIFragment ✅ (类存在但 lifecycle 不点) / **AddressUI.AddressUIFragment** (旧版用 ListView) / **ChatroomContactUI** 独立 Activity + s0 adapter + `"@all.chatroom.contact"` 不走 fc5.g / AddressLiveList.e() 内 `for + add()` 不调 addAll | 用户 paste jadx 实测 | ✅ L2 |
| 2026-05-28 00:35 | **V1.1 扩容**：`CDH.findAdapterHosts` 加 `ListView` 支持（含 AbsListView 子类）；首次失败 → dump View 树前 30 个节点类名（`[CDH:tree]`）兜底诊断 | `ContactDiscoveryHook.java` 加 `LV_CLS` 常量、加 `dumpTree` 方法、`scanActivity` 失败分支调 `dumpTree` | 🔬 |
| 2026-05-28 00:36 | Lint | `ReadLints(ContactDiscoveryHook.java)` 全清 | ✅ |
| 2026-05-28 01:05 | V1.1 装机：CDH:tree dump 揭示 **VASFragmentContainerView<FragmentContainerView>** + **OverScrollMultiTaskRecyclerView<WxRecyclerView>**；扫 11 次 no-rv-no-lv；addAll(fc5.g) 在 01:01:23 触发（CDH scan 早 29 秒）| `bug排查\pcv1_v1_1_diag.log` 1843 行 + tree dump | 🔬 **关键定位**：onResume 时 contact Fragment 的 RV 尚未 attach；fc5.g addAll 触发即数据真实落地信号 |
| 2026-05-28 01:08 | **V1.2 改造**：把 CDH 触发时机从「Activity.onResume」改为「addAll(fc5.g) 命中即触发」| `ContactDiscoveryHook.java` 加 `scheduleScanFromAddAll()` 公开方法（2s 冷却防抖） + `ContactFilter.installAddAllHook` fc5.g 命中后立调 | 🔬 V1.2 |
| 2026-05-28 01:09 | Lint | `ReadLints(CDH + CTF)` 全清 | ✅ |
| 2026-05-28 01:15 | V1.2 装机：`[CDH:fromAddAll] scan` ×4 触发链路打通 ✅；但 13 次扫均 no-rv-no-lv（VASFragmentContainerView 树深处仍未抓到 RV/LV）| `bug排查\pcv1_v1_2_diag.log` 2795 行 | 🔬 |
| 2026-05-28 01:18 | **V1.3 多时机扫描 + tree dump 扩容**：scanFromAddAll 调三次（200ms / 800ms / 2000ms）；dumpTree 扩到 80 节点 + 标 cc（childCount）；首次失败 dump 次数 2→4 | `ContactDiscoveryHook.java` 改 `scheduleScanFromAddAll` + `scanAtDelay` + `dumpTree` | 🔬 V1.3 |
| 2026-05-28 01:19 | Lint | `ReadLints(CDH)` 全清 | ✅ |
| 2026-05-28 01:35 | **L2 根因突破（终端 AI + jadx 互证）**：`MvvmAddressUIFragment` 不是标准 androidx Fragment——它 `extends BaseAddressUIFragment extends AbstractTabChildActivity.AbStractTabFragment`（微信自定义 tab fragment）。生命周期方法是混淆名 `q0(Bundle)=onTabCreate / t0()=onResume / r0()/s0()/u0()/v0()`。**这就是 fragResume 永远 0 hit 的真因**。同时 jadx 确认：`MvvmAddressUIFragment.F0()` 直接返回 `AddressLiveList`、`f188251p` 字段 = `WxRecyclerView` 实例 | jadx 验 `I:\apk2\_3__D_wechat_ban\jadx_8071\sources\com\tencent\mm\ui\contact\address\` | ✅ L2 |
| 2026-05-28 01:40 | **V2.0 落地**：`ContactFilter.java` 加 `installTabFragmentHook(lpparam)` + `captureLiveListAndAdapterFromFragment(fragment, src)`。Hook `MvvmAddressUIFragment` 上的 `q0(Bundle)` 和 `t0()` 方法（按名字 + 参数签名筛选）；afterHook 调 `F0()` 反射拿 AddressLiveList → 写 `sLiveListRef` + 跑 `cleanLiveList`；同步反射字段图找 `WxRecyclerView` → `getAdapter()` → 写 `sAdapterRef` | `src/main/java/com/ghost/assist/moduleD/ContactFilter.java` 新增 `installTabFragmentHook` + `captureLiveListAndAdapterFromFragment`；`install()` line 64 之后追加调用 | 🔬 V2.0 |
| 2026-05-28 01:41 | Lint | `ReadLints(ContactFilter.java)` 全清 | ✅ |
|| 2026-05-28 01:43 | **V3 落地（G8 会话）** 核心突破：sLiveListRef 依赖全部移除，ddAll(fc5.g) param.thisObject 即 f135087o backing list；getItemCount hook 可靠捕获 adapter | ContactFilter.java + ContactHotReload.java | 🔬 V3 |
|| 2026-05-28 01:43 | Lint | ReadLints 两个文件全清 | ✅ |

| 2026-05-29 02:14 | **V4 落地（Guard Native9 会话）** 钩 ik3.t0 ctor 写 sAdapterRef + captureDataField；getItemCount 探针 V4.1 | ContactFilter.java | 🔬 装机后 `[CTF:adapterCap] ctor`=0、`adapterProbe`=0、`rv.setAdapter`=0 → adapter 全抓空，BUS-V `no liveList no backingList` |
| 2026-05-29 02:52 | **V5 根因突破**：CDH `findAdapterHosts` maxDepth=8，但通讯录 RV 在 d17（tree dump 实证 `WxRecyclerView<X2CRecyclerView>` + AlphabetScrollBar）→ 扫不到。两处 `findAdapterHosts(root, 8→24)` | ContactDiscoveryHook.java line 157 + 420 | ✅ L1：`[CDH:found] liveListCls=AddressLiveList owner=ik3.t0`、adapter+liveList 抓到、notify 打通 |
| 2026-05-29 02:52 | **诊断 injected=0 根因**：`diagDumpLiveListFields` 一次性 dump 字段图 | ContactHotReload.java | ✅ L1：`[CTHR:diag] field=o sz=2855 first=fc5.g` / `field=p sz=2855` / `field=h sz=0` —— 真 backing = MvvmList 基类 **o/p/h**，硬找的 `f135087o` 根本不存在 |
| 2026-05-29 03:02 | **V7 修 V 态注入**：`MVVMLIST_FIELDS={"o","p","h"}`（镜像 ConvFilter）；`restoreToLiveList` 改逐字段遍历 o/p/h 注 fc5.g | ContactFilter.java + ContactHotReload.java | ✅ L1：`field=o injected=2`/`field=p injected=2` → `in-place injected=4` → 密友显示（用户肉眼确认） |
| 2026-05-29 03:1x | **V8 修 H 态隐藏**：`cleanLiveList` 同样改遍历 o/p/h（旧版找 f135087o → `sz=-1` 不删 → V→H 不隐藏）；与 V 态对称 | ContactFilter.java cleanLiveList | ✅ L1：`[CTF:BUS-H] field=o filtered=2/2857`/`field=p filtered=2` → `totalRemoved=4` |
| 2026-05-29 03:18-03:19 | **V8 双通装机验证** | `bug排查/final_pcv1_v8_双通成功.log` | ✅ **L1 反复 V↔H 3 轮全稳**：每轮 V 注 4 / H 删 4，不需冷启动、不重复、不卡。用户肉眼确认双通收官 |

### 🏁 P_CV1 里程碑（2026-05-29，Guard Native9 会话）

**密友（联系人）通讯录 V↔H 热切已收官**（✅ L1 装机实证 `final_pcv1_v8_双通成功.log`）：

- 根因链：① CDH 扫描深度 8 < RV 实际深度 17 → 抓不到 adapter；② 真 backing 字段是 MvvmList 基类 `o/p/h`（非废弃的 `f135087o`）。
- 修复：① `findAdapterHosts` 8→24；② `restoreToLiveList`（V 注入）+ `cleanLiveList`（H 清理）双双改遍历 `o/p/h`，与会话 tab 完全对称。
- 反复 V↔H 不依赖冷启动、连续 3 轮稳定（V 注 4 / H 删 4）。

**遗留（下一轮）**：通讯录内**「群聊」分组**的密群热切。当前 cache 只命中密友（联系人），密群可能走通讯录"群聊"入口的另一个 LiveList，需单独定位。

---

## 五、装机验证清单（待用户跑）

**预期 logcat 关键 tag**（命中表示链路通）：

| tag | 触发场景 | 期望含义 |
|-----|---------|---------|
| `[CTHR] install (no hooks; fragResume reused from ContactFilter)` | LSPosed 启动 | ContactHotReload 已 install |
| `[CTHR:put] id=... idx=... field=...` | H 态进通讯录、有密友被 filterContactList / addAll 命中 | cache 填充正常 |
| `[CTHR:BUS-V] cache=N` | H→V 切换瞬间（按 B6/111111 解锁） | 看到快照数 N（应 > 0） |
| `[CTHR:restoreInPlace] id=... pos=... fresh/stale` | BUS-V Runnable 跑起来 | 按 originalIndex 注回 backing list |
| `[CTHR:restoreInPlace] injected=K listSz=M` | 注回完成 | K > 0 且 M = backing size |
| `[CTHR:notify:BUS-V-direct] done` | adapter notify | ik3.t0.notifyDataSetChanged 调成功 |
| `[CTHR:BUS-V:dedup] removed=...` | 注回 + 80ms 后 | identity dedup 异步执行（F-35 合规） |

**预期产品行为**：
- HIDDEN 态进通讯录：密友 / 密群（如 `测试密群` 在通讯录中存在）**不显示** ✅（与现有 H 路径一致）
- VISIBLE 态进通讯录（H→V 切换后）：密友 / 密群**重新显示** ✅（**P_CV1 新功能**）
- 来回切 H↔V 5 轮：每轮都正确，CME / FATAL = 0
- 不影响会话 tab（最近联系）已有 v28 收口行为

**❓ 装机后才能确定的事**：
- 通讯录 `fc5.g` 列表里是否包含 `*@chatroom` 密群条目？看 `[CTHR:put]` 日志 id= 形态。
- 如果通讯录里没有密群条目（密群单独走"群聊"分组的另一个 LiveList）→ P_CV1 仅覆盖密友；密群恢复另开 P 任务。

---

## 六、装机命令（给终端 AI 跑）

**Step 1 — Build**

```
.\gradlew assembleDebug
```

**Step 2 — 安装到设备**

```
adb install -r "build\outputs\apk\debug\guard-native-debug.apk"
```

**Step 3 — 强停微信清进程**

```
adb shell am force-stop com.tencent.mm
```

**Step 4 — 清空 logcat 缓冲**

```
adb logcat -c
```

**Step 5 — 启动微信、抓 logcat 到文件**（命名规则：`final_pcv1_v1_<场景>.log`）

```
adb shell am start -n com.tencent.mm/com.tencent.mm.ui.LauncherUI
adb logcat -v time | findstr "NCL" | Out-File -Encoding utf8 bug排查\final_pcv1_v1_coldstart.log
```

**Step 6 — 装机后人工操作**（按序）

1. 冷启动微信 → 等 5 秒 → 进通讯录 tab → 看一眼 → **应不显示密友**（H 态）
2. 进微信 tab → 放大镜 → 输 `111111` → H→V → 退出搜索
3. 进通讯录 tab → 看一眼 → **应能看到密友 / 密群**
4. 进微信 tab → 按 B 触发器（摇一摇或返回桌面 5 秒）→ V→H
5. 进通讯录 tab → **应再次不显示密友**
6. 重复 2-5 五轮

**Step 7 — 把 log 发回**

把 `bug排查\final_pcv1_v1_coldstart.log` 全文（或前 500 行 + 关键 grep）发我，我做改后审查 + HOOKMAP 升级草稿。

---

## 七、回滚预案（万一翻车）

如装机后出现：
- 通讯录列表完全空（连普通联系人都不显示）
- 微信 tab 受影响（v28 已稳收口的会话路径出问题）
- CME / FATAL / SIGABRT

**回滚步骤**：
1. `git diff src/main/java/com/ghost/assist/moduleD/ContactFilter.java` 看 diff
2. 撤回 `ContactFilter.java` 改动（保留 ContactHotReload.java 文件即可，未被 install 不会触发）
3. 重打包装机

**回滚的最小动作**：删 `ContactFilter.install()` 中那段 `ContactHotReload.install(lpparam)` 调用 + 改回旧 `RefreshBus.register` 回调即可。
