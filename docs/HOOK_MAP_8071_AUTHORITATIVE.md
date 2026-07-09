# HOOK_MAP_8071_AUTHORITATIVE — 微信 8.0.71 权威 Hook 点地图

> ⛔ **AI 读此文件的绝对铁律 / AI HARD RULES FOR THIS FILE**
> 1. 本文件 **只锁微信 8.0.71**（D-014，2026-05-21 切版）。所有混淆类名/字段名仅适用此版本。
> 2. ✅ = 有 logcat / 装机日志原文证据；🟡 = 代码已写或部分实证；❌ = 未实装；⚠️ = 缺数据
> 3. 文档状态与 `03_execute_执行任务/P*/result.md` 不一致 → **停下来问用户，不自行仲裁**
> 4. 不确定任何字段名/类名 → **停下来问用户，禁止猜测，禁止从 8.0.66 / 8.0.70 直搬**
> 5. 修改本文件 → 需用户明确同意（见总调度文档写入门控）
>
> **创建**：2026-05-27
> **资料来源**：
> - `docs/archive/wechat_8066/HOOK_POINTS.md`（8066 历史，仅 diff）
> - `docs/archive/wechat_8066/HOOK_MAP_V1.md`（v1 规划 DEPRECATED）
> - `docs/isolation/INDEX_COMPETITOR.md`（Catfish 行为参考）
> - `docs/isolation/FEATURE_MATRIX.md`（功能矩阵 + 失败档案）
> - `I:\apk2\_4__samples\dynamic_fast\HOOK_IMPLEMENTATION_ANALYSIS.md`（甜密友 Catfish 8.0.66 反编译，**静态已证实**）
> - `03_execute_执行任务/P*/result.md`（14 份 P 任务实证报告）
> - `src/main/java/com/ghost/assist/`（项目代码现状）
>
> 门控 / 授权 / 状态机 / 设置入口可见性的权威口径见 `docs/GUARD_GATE_TRUTH.md`；本文件只记录微信 8.0.71 hook 点事实。

---

## 〇、版本锚定速查

| 项 | 值 |
|------|------|
| **目标版本** | 微信 **8.0.71** |
| **决策记录** | D-014（2026-05-21 切版） |
| **底座设备** | 小米 9 / Android 11 |
| **运行环境** | LSPosed 主进程 + `:push` 进程白名单 |
| **混淆类稳定性** | ⚠️ 类名跨版本变（8.0.66 / 8.0.70 / 8.0.71 三套互不直搬） |

---

## 一、8 大功能锚定表

### 1. 伪装订位（虚拟定位 / 伪造位置）

> 代号「伪装订位」（避敏感词，§6.8）。E2 装b 模块按阶段铁律属 v2/v3，但**注入点 + 选点 UI 已实装装机验证（2026-06-07，见下表 ✅）**。

| 项 | 内容 |
|------|------|
| **8.0.71 状态** | ✅ **已实装装机验证 2026-06-07** — `moduleE/FakeLocation`：注入 + 原生选点设置 + 全局生效 + 关闭复原，用户现场复验通过 |
| **项目代码** | `moduleE/FakeLocation.java`（installInjector / installPickCapture / installButtonRename / launchPicker）+ `Bridge` 键 flon/flla/flln/fllb + `SettingsEntry`「特色功能」行 + `ModuleMain` 注册（app classloader）|
| **★ 全局注入点（主）** | `pz0.h.c(pz0.h, boolean, double, double, int, double, double, double, Bundle)` — 定位结果分发总源头；**arg2=纬度(double)、arg3=经度(double)**；beforeHook 在开关开启且有坐标时把 arg1=true/arg2/arg3 改伪坐标 → 全局生效 |
| **设置入口（已实装）** | 设置页「特色功能」→ 开关「伪装定位」+「选择伪装位置」拉起原生选点页 `RedirectUI` 拖拽选点 → 右上角「发送」改「保存」（hook 选点页 `onResume` 扫视图树找文字=发送的 TextView 改文案，0/250/700ms 三波）→ `Activity.setResult` 捕获 `KLocationIntent.d/e/h` 存 MMKV，外层 RedirectUI 结果改 CANCELED |
| **⚠️ 误发兜底（实证教训）** | RedirectUI **内部直接发送位置消息、无视 `map_talker_name`、不走 caller 结果**（改 RESULT_CANCELED 拦不住）。故 launchPicker 把 talker 设 `filehelper` → 即便误发只进自己的文件传输助手（私密可删）。装机实证：选点保存不再发到真实聊天 |
| **下游回调（继承，可不 hook）** | `n83.g.onGetLocation(boolean, float 经度, float 纬度, int, double×3)` — 接收 pz0.h.c 转发的值；PoC 实证其自动继承上游伪坐标，故单 hook pz0.h.c 即够 |
| **覆盖场景（L1）** | 发送位置 / 共享实时位置 / 朋友圈发帖位置 / 附近的人 — 同一分发源，一改全改 |
| **调用链（L1 栈）** | `pz0.l.run → pz0.h.c → n83.g.onGetLocation → lt5.b.setCenter / location_soso.ViewManager.updateLocationPinLayout → new LatLng(lat,lng)` |
| **LBS SDK（运行时实证存在）** | `com.tencent.map.geolocation.sapp.TencentLocation / TencentLocationListener / TencentLocationManager`；`requestLocationUpdates/onLocationChanged` 本轮未触发（走缓存/分发链，非 SDK 回调）；`getLastKnownLocation()` 已 hook 未命中 |
| **证伪（旁路，禁重试）** | 消息层坐标候选全部动态证伪：`wy4.a.E/F`、`q2.F`、Intent `kwebmap_slat/lng`（getDoubleExtra 返回默认 -1000=键不存在）。8.0.71 发位置坐标**不走消息层** |
| **存储（实装时）** | MMKV：开关 + 经纬度（键名 seed 化，§6.7） |
| **UX 设计（已实装）** | 复用微信原生选点页 `RedirectUI` 选点 → 拦截右上角"发送/保存"读 LatLng 存 MMKV、不发消息（仿 A2/A3 复用原生 UI），2026-06-07 装机验证 |
| **混淆名警告** | `pz0.h` / `n83.g` / `lt5.*` 为 8.0.71 专属混淆名，升版必经 classmap 重查 |
| **探针** | `tools/probe_loc_send_8071.js`(v1) / `probe_loc_sdk_8071.js`(v2) / `probe_loc_src_8071.js`(v3 栈) / `probe_loc_inject_8071.js`(v4 契约) / `probe_loc_poc_8071.js`(PoC 注入) |

---

### 1b. 改余额显示（装b · E3）

| 项 | 内容 |
|------|------|
| **8.0.71 状态** | ✅ 装机验证 2026-07-09（自定义金额 + 零钱通排除 +「我的零钱」页）；`moduleE/FakeBalance.java` |
| **★ 唯一改值点** | `com.tencent.mm.plugin.wallet_core.ui.view.WcPayMoneyLoadingView` = 钱包 / 服务页 / 零钱专页余额的最终显示层（long·分的 `KindaMoneyLoadingView.setMoney` 内部也转调它）。遍历其「首参 String 的金额 setter」（混淆 `e/g/f` + `setMoney/setNewMoney/setFirstMoney`），beforeHook 把入参改成设置页填的假值；按名字排除 `setPrefixSymbol/font/color/typeface/style`（非金额 String） |
| **零钱通排除** | 从金额控件往上逐层找行标签，**精确 `equals`**「零钱通」→ 跳过（显示真实余额）；「零钱」→ 改。命中即停 + 控件分类记忆 `sRowMemo`。**禁用 `contains`**——「我的零钱」页推广文案「转入零钱通，能赚又能花」含「零钱通」三字会误伤整页 |
| **只在显示层改** | 不 hook `KindaMoneyLoadingView.setMoney(long,boolean)`：它在 `onCreateLayout` 早期（控件尚未挂到行）触发、认不出零钱通，在该层改会污染并穿透到 WcPay 显示 |
| **门控** | `isVipAuthorized() && isEditBalanceEnabled() && 已填金额`（杂项口径，不绑 H/V）；`RiskState.isTamperDegraded()` 散沙降级失效 |
| **项目代码 / 存储** | `moduleE/FakeBalance.java`；`Bridge` 键 `ebon`(开关) / `ebvl`(假余额·元)；`SettingsEntry` 输入弹窗（末两位自动小数） |
| **混淆名警告** | `WcPayMoneyLoadingView` 内部 setter `e/g/f` 为 8.0.71 混淆名，升版必经 classmap 复查 |

---

### 2. 防撤回

| 项 | 内容 |
|------|------|
| **8.0.71 状态** | ✅ **L1 装机实证 2026-05-31**（`moduleC/AntiRecall.java` v4） |
| **数据通道** | `cmd == "revokemsg"` 的命令通道 |
| **存储** | MMKV: `revoke_msg`（项目 key=`arc`） |
| **项目代码** | ✅ `moduleC/AntiRecall.java` v4：hook `jy0.t.f`(doRevokeMsg) → `setResult(null)` 保原文 + 插 type=10000 系统提示染红；+ `Bridge.isAntiRecallEnabled()`（key=`arc`，default true） |
| **install** | ✅ `ModuleMain.install:141`（app classloader，本仓库已注册） |
| **8.0.71 实证** | ✅ L1 装机 2026-05-31：`[AR] recall blocked + tip inserted` ×4（文字/表情/图片/视频） |
| **推荐下一步** | 已结案；仅 8.0.72 升级时复验 `jy0.t.f` / `f9` / `h9` 混淆名 |
| **来源** | `HOOK_IMPLEMENTATION_ANALYSIS.md` §2.9 + 项目 `moduleC/AntiRecall.java` |

---

### 3. 添加密友 UI（wxid 解析昵称头像）

| 项 | 内容 |
|------|------|
| **8.0.71 状态** | ✅ 数据层 + UI 全完整（2026-06 装机使用，与密群导入同套路） |
| **数据层** | ✅ `Bridge.addWxid(wxid) / removeWxid(wxid) / getWxids() / allHiddenIds()`（密友+密群 union） |
| **MMKV key** | `hlst`（hidden list） |
| **wxid → 昵称/头像反射链（8.0.66 参考）** | `com.tencent.mm.storage.ContactStorage` → `field_nickname / field_avatar` |
| **8.0.71 等价类** | `com.tencent.mm.storage.l4`（contact）→ `C0()` 返 wxid · nickname/avatar getter（如 `j1() / m2()`）需 8.0.71 重查 |
| **导入 UI（正路）** | ✅ `SettingsEntry「密友列表/添加密友」→ ContactImportGuard.launchSelectContact → com.tencent.mm.ui.contact.SelectContactUI`（原生选人器）。extras: `list_type=1` / `list_attr=16471` / **`already_select_contact`=现有密友 CSV（预选）**；返回 `setResult(-1)` 的 `Select_Conv_User` = wxid CSV → diff 增删一体（与密群 GroupCardSelectUI 同套路） |
| **辅助代码** | `debug/ContactResolver.java`（P15 L4 stub，依赖微信 ContactStorage 反射；SelectContactUI 自身显示昵称头像，不强依赖此 stub） |
| **推荐下一步** | 已结案；仅 8.0.72 升级时复验 SelectContactUI extras 名 |
| **来源** | `src/main/java/com/ghost/assist/moduleB/ContactImportGuard.java` + 装机使用（2026-06） |

---

### 4. 添加密群 UI（groupId 解析）

| 项 | 内容 |
|------|------|
| **8.0.71 状态** | ✅ 数据层 + 过滤链完整装机实证（2026-05-21）；✅ **导入 UI 2026-06-02**（GroupCardSelectUI 原生选群器，预选+增删一体，用户验证通过） |
| **数据层** | ✅ `Bridge.addGroupId(gid) / removeGroupId(gid) / getGroupIds() / isGroupId()` |
| **MMKV key** | `glst`（group list） |
| **groupId 形态** | `xxxxxxxxxxxxxxxx@chatroom` |
| **过滤链复用** | F04（`l4.C0()` 返的 username 已含 `*@chatroom`） + F07（`z3.c1()` 同字段） |
| **8.0.71 chatroom 反射** | 8.0.66 走 `com.tencent.mm.storage.bb`（ChatRoom）· 8.0.71 等价类 ⚠️ 待重查 |
| **项目代码** | ✅ `Bridge.java` 群数据 + 4 Filter 切换到 `allHiddenIds()` |
| **UI 入口** | ✅ `SettingsEntry「密群列表」→ ContactImportGuard.launchSelectGroup → com.tencent.mm.ui.contact.GroupCardSelectUI`。extras: `group_multi_select` / `group_select_need_result` / `group_select_type`=true / `max_limit_num` / **`already_select_contact`=现有密群 CSV（预选）**；返回 `setResult(-1)` 的 `Select_Conv_User`=`@chatroom` CSV → diff 增删一体（与密友 SelectContactUI 同套路）。L1: `bug排查/probe_groupselect_8071.log` + `probe_groupkeys_8071.log` |
| **⚠️ 旧口径证伪** | 「list_type=2=密群」来自竞品 mn1(8.0.70.2)/A3 推断，8.0.71 **不走** SelectContactUI；密群专用 GroupCardSelectUI（见上） |
| **遗留** | 群昵称解析 `ContactResolver.resolveNameOrNull`（L4，依赖 ContactStorage 反射，未在 8.0.71 逐一验证；GroupCardSelectUI 自身显示群名，不强依赖） |
| **来源** | `HOOKMAP.md` §A3 + `Bridge.java` |

---

### 5. 语音消息转发

| 项 | 内容 |
|------|------|
| **8.0.71 状态** | ❌ 未实装（**v2 才做**） |
| **竞品锚点（8.0.66）** | `MainEntry.hookTransFlag()` → `VipPreference.getTransVoiceMsg()`（开关） · 实际转发逻辑在 `np.protect` 加固层 |
| **存储** | MMKV: `trans_voice_msg`（boolean） |
| **难度** | ⭐⭐⭐⭐ 竞品标"难度高，二阶段"（HOOKMAP §C5） · 8.0.66 实际代码被加固，jadx 失败 |
| **项目代码** | ❌ 无 |
| **推荐下一步** | v1 不做，归档到 v2 P 任务 |
| **来源** | `HOOK_IMPLEMENTATION_ANALYSIS.md` §1.3 + `HOOK_MAP_V1.md` §P2-18 |

---

### 6a. 朋友圈"仅可见分组"图标

| 项 | 内容 |
|------|------|
| **8.0.71 状态** | ✅ 时间线(ImproveSnsTimelineUI) + 详情页(SnsCommentDetailUI) L1 装机（2026-06-10）；个人相册页 = Flutter，v1 不做（见下「相册页边界」） |
| **目标** | 自己看自己朋友圈时，受限帖（仅可见分组/部分可见）右下角那个图标隐藏（自己端视觉过滤，不影响别人看） |
| **磁盘实证（dumpsys）** | `07_archive_归档/M6a_MomentsGroupIcon/logs/gi_dump.txt` L1775-1784（ImproveSnsTimelineUI）：item 根 `ha4.q3/k4/s2`(app:id/n9a) > 正文 `n95` > ConstraintLayout > 元信息行 `n93` > LinearLayout > [时间, ViewStub×2, **pt**, pi]；`pt`=#7f090304 app:id/pt = 可见分组图标(WeImageView) ← 藏；`pi`=#7f0902f8 app:id/pi = 删除 ← 不碰。普通帖该行只有折叠 ViewStub，受限帖才把 ViewStub inflate 成 pt/pi |
| **实现（MomentsGroupIconFilter）** | ① `ViewStub.inflate()` afterHook：图标首次 inflate 即 GONE（低频、无闪烁）；② `Activity.onResume`(类名含 `plugin.sns`) 立即扫 decorView + 挂 `ViewTreeObserver.OnGlobalLayout` 监听、节流 60ms 重扫 → 滚动/异步渲染出新帖即藏。两层都只对 `getResourceEntryName==pt` 调 `setVisibility(GONE)`，pi 的 id 不同天然不命中 |
| **开关** | `AppConfig.isMomentsGroupIconEnabled()`（MMKV `mgi`，默认开；纯开关驱动，**独立于 HIDDEN 状态**——外观偏好，不属密友隐私链）；SettingsEntry 行「隐藏朋友圈分组图标」 |
| **绕过的雷** | 不走 `onBindViewHolder`（F-32x 实证 8.0.71 朋友圈 RV onBindViewHolder 0 命中）；不全局 hook `View.setVisibility`（铁律 H1） |
| **L1 证据** | `[MGI] pt GONE (cls=...WeImageView)` + `[MGI] resolved pt id=0x7f090304`（2026-06-10 23:43:52） |
| **相册页边界（L1 实证，`M6a/logs/gi_album.txt` 2026-06-10）** | 个人相册页置顶 Activity = `com.tencent.mm.plugin.flutter.ui.MMFlutterViewActivity`（mResumed=true），整页仅一个 `io.flutter.embedding.android.FlutterView → FlutterTextureView`(1080×2296)，**内部零原生子 View**（无 pt/pi/WeImageView）。图标由 Flutter 画在 texture 上 → 安卓 view hook 物理够不到。**v1 决策 A：相册页不做**（次要入口；走数据层或硬刚 Flutter 均触雷区/铁律 23，不划算） |

### 6b. 通讯录"标签"隐藏

| 项 | 内容 |
|------|------|
| **8.0.71 状态** | ✅ **装机实证 2026-05-24（P19B）**；标签内成员过滤 2026-05-31 重构独立成模块、用户现场复验 ✅ |
| **L1 标签入口行** | `ArrayList.addAll(fc5.g)` 中 `e=8` 行为标签入口 → 移除该 item |
| **L2 标签存储** | `com.tencent.mm.storage.d4` 类 |
| **L3 标签成员/搜索 item** | `ye5.j` → `ye5.j.d` 形如 `wxid_xxx-15-0`，去后缀 `-N-M` 得 wxid；代码 = `moduleD/ContactLabelMemberFilter.java`（日志 `[CLM:label]`，门控 isActive+allHiddenIds） |
| **L4 Activity 拦截** | `ContactLabelManagerUI / MvvmContactListUI / LabelSearchUI` |
| **项目代码** | ① 整标签入口/管理页隐藏（L1 `fc5.g` e=8 / L2 `d4` / L4 Activity，开关 hcl）= ✅ `moduleD/ContactLabelHideGuard.java`（2026-05-27 补 install）；② L3 标签内成员按 wxid 藏密友（门控 isActive+allHiddenIds）= ✅ `moduleD/ContactLabelMemberFilter.java`（2026-05-31 P_CV1 重构丢失后独立成模块，用户现场复验） |
| **存储** | MMKV: `hcl`（`Bridge.isHideContactLabelEnabled`） |
| **来源** | `07_archive_归档/P19B_ContactLabel/result.md` |

### 6c. 隐藏设置页「存储空间」入口

| 项 | 内容 |
|------|------|
| **8.0.71 状态** | ✅ 装机实证 2026-06-30（用户现场复验：有授权 + 隐身态进设置页「存储空间」消失；V→H 即时隐藏） |
| **目标** | 隐身态下隐藏微信「我 → 设置」页的「存储空间」入口行，避免暴露在用模块 |
| **Frida L1 实证（view 树）** | `set_viewtree.txt`（2026-06-30，pid attach 7739；frida 17.x 需 `frida-compile` 打包 `frida-java-bridge`，微信主进程反 frida 枚举 → 改 **pid attach** 绕过；uiautomator dump 抓空层级走不通）：`MainSettingsUI` > `WxRecyclerView#lqa` > 每行 `LinearLayout#no-id` wrapper = [分组标题 `TextView#gzf` + 行主体 `LinearLayout#m7k`]；行标题 = `TextView#title` text==「存储空间」。截图 `after_hide3.png` |
| **实现（SettingsStorageHideGuard）** | `Activity.onResume`(类名含 `SettingsUI`) 扫 decorView + 挂 `OnGlobalLayout` 节流 60ms 重扫；逐个 `getResourceEntryName==title` 的 TextView：是「存储空间」且该隐藏 → 上溯 `#m7k` 取父 wrapper → `setVisibility(GONE)` + `layoutParams.height=0`；其余行强制恢复 `VISIBLE`+`WRAP_CONTENT`（防 RecyclerView 复用继承 height=0 误伤别的行）。`RefreshBus` 回调持 decorView 弱引用，V↔H 切换主线程即时重扫（热切） |
| **授权门控** | `StateMachine.isVipAuthorized() && getState()!=VISIBLE`（有授权 + 隐身态 HIDDEN/UNLOCKING 才隐；无授权或显形态正常显示）。绑授权 = 防白嫖，对齐防撤回 f2 范式，**不绑 f1 密友总开关 / config 配方门**（非密友隐私链，避免误判失效）。只读 StateMachine 不写（Filter 边界；授权检查官 2026-06-30 改前/改后审 PASS） |
| **绕过的雷** | 不走 `onBindViewHolder`（8.0.71 设置 RV 0 命中，P_SE5）；不全局 hook `View.setVisibility`；仅 `GONE` 不够（RecyclerView item 不回收高度 → 留空白缝隙）→ 必须叠 `layoutParams.height=0` + `requestLayout` |
| **项目代码** | `src/main/java/com/ghost/assist/moduleD/SettingsStorageHideGuard.java` + `ModuleMain` 注册（M6a 之后） |
| **L1 证据** | `[SSH] installed (onResume sweep on *SettingsUI)` + `[SSH] 存储空间 row hidden`；官替 debug LSPosed 装机 2026-06-30，进程 `com.tencent.mm`（用户复验 V→H 即时隐藏） |

---

### 7. 过滤通讯录（密友 + 密群）

| 项 | 内容 |
|------|------|
| **8.0.71 状态** | ✅ **装机实证 2026-05-20（P19）** |
| **主路径** | `ArrayList.addAll(fc5.g)` → `fc5.g.d` = `com.tencent.mm.storage.z3` 实例 → `z3.c1()` 返 wxid → `Iterator.remove()` |
| **类常量（8.0.71）** | item: `fc5.g` · contact: `com.tencent.mm.storage.z3` |
| **fragment（8.0.66 参考，8.0.71 待确认）** | `MvvmAddressUIFragment` / `AddressLiveList` |
| **门控** | `StateMachine.isActive()` + `Bridge.allHiddenIds()`（含密群 union） |
| **项目代码** | ✅ `moduleD/ContactFilter.java` · ModuleMain.install ✅ |
| **失败路径（铁律）** | `MvvmList.n/u` 零触发（仅会话） · 通讯录是分段虚拟滚动，每段独立 addAll，**不能用 INIT clean** |
| **来源** | `07_archive_归档/P19_通讯录隐藏/result.md` + `HOOKMAP.md` §F07 |

---

### 8a. 过滤会话列表（F04）

| 项 | 内容 |
|------|------|
| **8.0.71 状态** | ✅ P17 装机；V→H ✅；**H→V fresh-warm ✅**（普通有历史 hidden id 可主动 warm 回来；零历史 wxid 仍受微信 DB 限制） |
| **L1 主路径** | `MvvmList.n(List, boolean)`（8.0.66 是 `.m`） → 入参 List remove 密友 |
| **L2 备用** | `MvvmList.s(List)` |
| **L4 兜底** | `kc5.v0.notifyDataSetChanged` clean-before（**禁止用 notifyItemRange**，F-08~F-14 铁律） |
| **V↔H 刷新链** | V→H：`sPendingHide` → `LauncherUI.onResume` → cleanConvData → notifyConvAdapter(v0)；H→V：`kc5.x.h(wxid)` fresh-warm → `BUS-V` 注回 → notify/dedup |
| **wxid 提取** | `kc5.y.d`（`l4` 实例）→ `extractWxid()` 按序回退 `C0() → h1() → j1() → i1() → k1() → getUsername()` ；C0 为 8.0.71 主路径（field_digestUser，2026-05-22 live broad-scan 确认），h1 对公众号 item 返 `"officialaccounts"`，仅作 fallback |
| **项目代码** | ✅ `moduleD/ConvFilter.java` · ModuleMain.install ✅ |
| **来源** | `docs/archive/wechat_8066/HOOK_POINTS.md` §F04（历史）+ `P17_会话LSPosed/result.md` + `P_ConvWarm/result.md` + `P26_好友热切fresh触发/result.md` + `docs/CONV_REFRESH_PROBLEM.md` |

---

### 8b. 全局搜索 — 聚合过滤器分源进度（P20 收口 ✅）

| 项 | 内容 |
|------|------|
| **8.0.71 状态** | ✅ **P20 搜索收口**：联系人结果 ✅ 装机实证 2026-05-27 v15.1；v15.2 2026-06-02 灰白块收口（`lp.height 0→1`）；群聊/密群/聊天记录关键词场景 ✅ 2026-06-06 L1（`tools/p20_search_logcat_runner_20260606_180946.log`：`z15.ef6` 分源出现，同轮 `tz2.p0` 行 `44786160583@chatroom` / `wxid_lzd2va16jd1622` 被 `[SF:gv] blocked`，普通群 `45592178108@chatroom` 放行） |
| **产品事实** | 全局搜索是聚合大过滤器，来源至少包括最近联系、联系人、群聊、聊天记录；不同来源走不同数据结构和渲染路径 |
| **已验证：联系人** | `ListView.setAdapter` → 检测 q2 → 沿继承链 hookAllMethods("getView") → 命中 `com.tencent.mm.plugin.fts.ui.f0.getView(int,View,ViewGroup)` declared → afterHook 拿 `adapter.getItem(pos)` 为 `tz2.u1` → 抽 `tz2.u1.f.s` = wxid → 命中 hidden set → `View.GONE + lp.height=1 + topMargin=bottomMargin=0`（**v15.2 起 lp.height 必须 =1 不能 =0**，原因见「配套消空白」行）|
| **已验证：群聊/密群** | `f0.getView` afterHook 主路径；adapter item 走 `tz2.p0.s` / `tz2.s1.s` 等 groupId 路径，2026-06-06 L1 实证 `[SF:gv] blocked id=44786160583@chatroom`，未在密群名单的 `45592178108@chatroom` 正常显示 |
| **已验证：聊天记录关键词场景** | `z15.ef6/ch6.e` 分源已出现，但不走 protobuf talker 主过滤；最终 `q2/f0.getView` 渲染层 item 为 `tz2.p0`，同轮按 wxid/groupId 精确命中 hidden set 后折叠隐藏，普通群对照放行 |
| **配套消空白** | ① 同 setAdapter callback 内 `lv.setDivider(null); lv.setDividerHeight(0)` 消除 GONE row divider；② **v15.2 灰白块根因（L1 `[SF:row]` 实证 `pos=2 vis=8 lph=0 h=173`）**：FTS 容器是 ListView(AbsListView)，其 `setupChild()` 仅当 `lp.height>0` 才按 `MeasureSpec.EXACTLY` 量，`height≤0`（含 0）走 `UNSPECIFIED` → 隐藏行按内容原高(~173px)渲染 → 残留灰白块（GONE 对 AbsListView 同样不跳过测量）；故隐藏行必须 `lp.height=1`（EXACTLY 1px≈隐形），`restoreView` 还原判断放宽为「0 或 1」。**禁止用 0/负值折叠 AbsListView 行；消空白只走视图层，禁减 getCount（呼应 F-32z）** |
| **fz2.e 数据层** | ❌ 已证伪 — `fz2.e.g` 实测是 UIN 或 `SOSItemRelevant:<关键词>`，不含 wxid 字面；保留 [SF:ALLseen] 探针作未来 UIN→wxid 映射 |
| **过滤原则** | 能确定 wxid/groupId 且命中名单才隐藏；不能确定 id 时只打限流诊断日志，不得猜字段、不得全量隐藏 |

### 8b.1 搜索来源拆分验收

| 来源 | 当前状态 | 验收要求 |
|------|:--:|------|
| 联系人搜索结果 | ✅ v15.1 装机实证 | HIDDEN 态搜密友不显示；VISIBLE 态恢复 |
| 最近联系 | ⬜ 未单独验 | 需确认是否复用联系人 item，不能默认覆盖 |
| 群聊搜索结果 | ✅ **L1 装机实证 2026-06-06** | `[SF:gv] blocked id=44786160583@chatroom` ×3（已加入密群名单）；未在名单的 `45592178108@chatroom` 正常显示。`tz2.p0.s` = groupId 路径，与联系人 `tz2.u1.f.s` 共用 `f0.getView` afterHook 主路径，日志 `tools/p20_search_logcat_runner_20260606_180946.log` |
| 聊天记录搜索结果 | ✅ **L1 装机实证 2026-06-06** | HIDDEN 态搜密友/密群聊天关键词不显示；证据 `tools/p20_search_logcat_runner_20260606_180946.log`。技术口径：`z15.ef6/ch6.e` 不是主过滤点，最终由 `q2/f0.getView` 的 `tz2.p0` id 折叠隐藏 |

> P20 搜索已按产品场景收口；禁止把 `z15.ef6/ch6.e` 写成已解析 talker 的主过滤路径，当前主路径仍是 `q2/f0.getView` 渲染层精确 id 过滤。

### 8b-路径状态（v15.1 实证后裁决）

| 路径 | 当前口径 |
|----|---------|
| `com.tencent.mm.plugin.fts.ui.f0.getView` (afterHook GONE) | ✅ **主路径**，v15.1 装机实证 ×14 命中 |
| `q2.j(View, jz2.g, boolean)` | ❌ **F-32y 主路径废弃**：装上 0 触发；保留作 backstop 无害 |
| `ss4.p.onBindViewHolder` | ❌ **F-32x 永久废弃**：父级 ConstraintLayout vis=8 GONE 不上屏（fts_tree_v2.log L243 铁证）。**2026-06-09 已从 SearchFilter.java 删除**（0 命中僵尸 hook；删后 L1 装机复验 `f0.getView` 仍 ×29 命中、无回归）|
| `SearchFilter 5-hook offset` (getCount/getView/getItem/getItemId/getItemViewType 联动) | ❌ **F-32z 永久废弃**：setResult(orig-skip) 干扰 q2 内部 data swap，搜索结果区**全空白** + ANR（final_v11..v14 实证）|
| **遗留代码** | SearchFilter.java 内 q2.j / 5-hook offset 用 `if (false)` 保留供考古，不删除；**ss4.p 已删除（非 if(false)）** |

### 8c. 全局搜索 — fz2.e 数据层（已证伪 wxid 路径）

| 项 | 内容 |
|------|------|
| **8.0.71 状态** | ❌ wxid 提取路径**已证伪**——`fz2.e.g` 实测是 UIN 或 `SOSItemRelevant:<关键词>`，**不含 wxid 字面** |
| **fz2.e 字段 schema（widerprobe/deeprobe 实证）** | `c=0` → `g="SOSItemRelevant:<关键词>"`（分类行）<br>`c=2` → `g="<UIN>"`（联系人匹配行，UIN 非 wxid）<br>`c=3` → `g="<UIN>"`（聊天记录内联）<br>`c=4` → `g="<UIN>"`（其他分区） |
| **容器** | `ArrayList.addAll(Collection)`（**不是** LinkedList.add，README v1.1 写错） |
| **项目代码** | 🟡 `SearchFilter.java` 保留 `[SF:ALLseen]` + `[SF:DUMP fz2.e]` 探针用于诊断，但**不再作为直接 wxid 主过滤** |
| **次要价值** | 若 §8b q2 hook 失效或 UIN→wxid 映射建好时，可作为数据层兜底 |
| **来源** | widerprobe_v1.log + deeprobe_v3.log + 探针子代理报告 2026-05-27 |

### 8d. 全局搜索 — 聊天记录 FTS（z15.ef6）

| 项 | 内容 |
|------|------|
| **8.0.71 状态** | ✅ 产品场景已收口；❌ `z15.ef6/ch6.e` 字段直取 talker 路线仍走不通 |
| **dump 结果** | `runtime_search_v3.log`: 3 次 dump 全部 `fields:` 空 / widerprobe 实测 `d/e/o` = 查询词+高亮，`p`=`z15.ch6`(protobuf wrapper)，`ch6.e`=byte[]（talker 可能在 protobuf 内但未解析出） |
| **容器** | `LinkedList.add(Object)`（工作线程） |
| **真实 talker** | ⚠️ 仍不从 `ch6.e` protobuf byte[] 直接解析；当前无需作为主路径 |
| **推荐策略** | 已改由最终 `q2/f0.getView` item id 收口；继续禁止猜 `z15.ef6/ch6.e` 字段 |
| **项目代码** | ✅ `SearchFilter.java` 走 `f0.getView` afterHook；`z15.ef6` dump 探针仅保留作诊断 |
| **来源** | `runtime_search_v3.log` + 探针子代理报告 2026-05-27；`tools/p20_search_logcat_runner_20260606_180946.log` |

### 8e. 朋友圈互动消息 / 小红点（P21）

| 项 | 内容 |
|------|------|
| **8.0.71 状态** | ✅ **P21 主线收尾**：Layer0b 入口归零 2026-05-21；P21B WithAll/bm 条目过滤装机实证 2026-06-09 |
| **Layer0b 入口** | `Activity.onResume` 全局 hook → class name 包含 `SnsMsgUI` 时进入 `handleSnsMsgUIEnter()`，清 `w1.y` / `SnsMsgUI.s` badge 计数；用于互动列表消费后红点归零 |
| **P21B 主路径（WithAll/bm）** | `com.tencent.mm.plugin.sns.ui.bm` 继承 `com.tencent.mm.ui.s9`；屏幕数据源为父类字段 `s9.f: Cursor = com.tencent.wcdb.compat.ValueCursor`；游标列含 `talker`，AA熵 wxid 位于 `talker` 列 |
| **过滤实现** | `BaseAdapter.notifyDataSetChanged` beforeHook（仅 bm/rm）→ 包装 live `s9.f` 为 `TalkerFilterCursor` → `talker ∈ Bridge.getWxids()` 的行跳过 → 交给微信原 notify 重画 |
| **覆盖状态** | ✅ `SnsMsgUIWithAll / bm` L1：10 行过滤为 1 行，AA熵不显示；`SnsMsgUIWithRelevance / rm` 代码同路径覆盖，后续有“与我的互动”入口/顶部气泡时补 L1，不阻塞当前 v1 |
| **铁律** | 禁 `View.GONE`；禁反射自调 `notifyDataSetChanged`；禁 `notifyItemRange*`。游标层只做位置重映射，不写 DB、不改 UI、不碰状态机/授权链 |
| **项目代码** | `src/main/java/com/ghost/assist/moduleD/MomentsRedDotGuard.java` — `installSnsMsgLiveCursorFilter` / `wrapSnsMsgCursorFields` / `TalkerFilterCursor` |
| **证据** | 根因：`07_archive_归档/P21_MomentsRedDot/logs/probe_live_bm_cursor_20260609_logcat.txt`；验收：`07_archive_归档/P21_MomentsRedDot/logs/p21b_cursor_fix_verify_20260609.txt`；截图：`07_archive_归档/P21_MomentsRedDot/logs/p21_after_fix_pass_withall.png`；工作记录：`07_archive_归档/P21_MomentsRedDot/worklog.md` |

---

### 9. 设置入口 banner 注入（量子密友设置行 · 随列表滚动）

| 项 | 内容 |
|------|------|
| **8.0.71 状态** | ✅ **装机验证 2026-06-01（P_SE8）** — banner 跟手滚动、顶部无空白，用户现场复验 ✅ |
| **注入路径** | `SettingsEntry` 在 `MainSettingsUI / CommonSettingsUI` 的 `onResume` 注入「隐私功能 / 量子密友设置」banner（VISIBLE 显、HIDDEN+开关 `hei` 隐）|
| **正解（P_SE8）** | banner 挂到 **RecyclerView 父层**（FrameLayout/RelativeLayout）做顶部悬浮 + 给 RV 设 **top padding = banner 高 & `setClipToPadding(false)`** 腾空间 + 滚动时 `banner.setTranslationY(-min(offset, bannerH))` 跟手。空间由 RV 自身 padding 提供、随内容自然回收 → 真·跟随滚动、不留空槽、不用每帧 requestLayout |
| **症状（避坑）** | 旧实现（P_SE7）把 banner 当**占高度的兄弟 View** 加进 RV 容器 LinearLayout，再用 `setTranslationY` 做 scroll-follow。`setTranslationY` 只是视觉位移、**不回收布局高度** → 一滚动 banner 滑出但 H_banner 空槽还钉在顶部 → 设置页**标题栏与第一行之间出现一条空白**；现象：入口显示 + 下拉时露出、返回重进消失（offset 归 0）|
| **如何避免** | ① 随列表滚动的 header **禁用**「占高度的兄弟 + translationY」凑（必漏空槽）；要么 RV 自身 padding 腾空间（本方案），要么做成真 list header。② RV header item（adapter）路线在 8.0.71 是**死路**：`pz3.g.onBindViewHolder` hook 0 命中，勿走。③ 改 banner 滚动逻辑前必读本行 + 铁律29 |
| **项目代码** | `src/main/java/com/ghost/assist/moduleB/SettingsEntry.java` — `syncLlHeader` / `attachScrollFollow` / `applyRvTopPadding` / `restoreRvPadding` / `overlayHostFor`（带 fallback：父层不可层叠时回退旧兄弟注入）|
| **铁律** | 铁律29：已装机验证 hook，**改前必问用户**；可见性权威口径见 `docs/GUARD_GATE_TRUTH.md` |
| **来源** | 装机验证 2026-06-01（本会话，用户现场复验「收官完美」）|

---

### 10. 当前登录账号采集（自身 wxid + 微信号）+ 服务器绑定

> 采集「当前登录的是哪个微信账号」，随授权信封上报服务器做**换号识别 + 设备画像**（一个授权码绑到哪个号）。当前 release 自动生效的是**微信号(alias)**，已 L1 装机验证；**wxid 自动采集链路缺失**（详「⚠️ wxid 现状」），故上报的 `acct` 实为微信号。

| 项 | 内容 |
|------|------|
| **8.0.71 状态** | 🟡 微信号(alias) ✅ L1 装机实证 2026-06-30（后台 `cur_acct` 出数）；wxid ⚠️ release 自动链路缺失（仅 DEV 调试接口手动设） |
| **微信号(alias) 采集（✅ 生效）** | `moduleB/SelfProfileCapture.install()` hook `TextView.setText(CharSequence)`；用户进「我」Tab 资料页时，按文本以「微信号」开头 + 含冒号（全角 `：`／半角 `:` 均兼容）命中 → 截冒号后子串即微信号 → `Bridge.setMyAlias()`（MMKV key `myal`）。`getMyAlias()` 非空即短路、不重复采集，一次持久化终生复用 |
| **★ 版本鲁棒（本轮修复根因）** | 旧 `SelfProfileCapture` 写死 `VIEW_ID_ALIAS=0x7f0c6c6d`（8.0.66 资源名 `ouv`）；微信每版资源 id 重排，8.0.71 几乎必不等此值 → 永不命中 → 微信号恒空。改为**按文本「微信号：」命中、与资源 id 解耦**，升版免逐版改 id（仅当资料页中文文案变才需复验） |
| **昵称(nick) 采集** | `SelfProfileCapture.tryResolveNickname()` → `debug/ContactResolver.resolveJson(wxid)` 提取 `nickname` → `Bridge.setMyNick()`（key `mynk`）。依赖 wxid，wxid 空时昵称同空 |
| **⚠️ wxid 现状（L2 代码实证）** | `Bridge.getMyWxid()`（key `mwxd`）当前 release **无自动采集链路**：`Bridge.refreshWxid()` 是 no-op stub（空体）；`setMyWxid()` 全项目唯一调用方是 `DebugServer.apiSetMyWxid`（DEV/HONEY 调试 HTTP 接口手动设）。源码注释声称由 `SelfProfileCapture` / `switch_account_preferences` listener 填充，但该链路**未实装**（注释 ≠ 代码）。如需 release 自动绑 wxid，须另补登录态采集（独立任务） |
| **绑定上报（客户端 → 服务器）** | `net/EnvelopeClient.currentAcct()`：微信号(alias) 优先、空则 wxid、再空返 `""` → 作为 `acct` 字段并入 client 信息 JSON，随授权信封 POST 上报 |
| **服务器侧（miyou-server）** | 记 `cur_acct`（当前账号）/ `first_acct`（首见基准）/ `acct_changed`（换号 0→1 风控信号）；`admin/v2` 设备 tab 展示「微信号 / wxid + 首次激活 + 到期进度条」（本轮 `db.py + static/admin_v2.html` 已部署 prod） |
| **L1 证据（原 logcat 临时 dump 已清，关键行留档）** | `06-30 00:50:46 I/NCL [SPC] alias=Markeyno`（微信号抓取成功）；同期反复 `06-30 00:50:55 W/NCL [auth] bindAccount: myWxid not set`（wxid 未采集，印证「wxid 现状」）。源 `logs_mi9_spcfix_20260630.txt` 为临时 logcat dump，已按收尾清理 |
| **Bridge 键位** | `mwxd`=wxid · `myal`=微信号 · `mynk`=昵称（均 MMKV，§6.7 seed 化命名空间） |
| **项目代码** | `moduleB/SelfProfileCapture.java`（采集 alias/nick）· `core/Bridge.java`（存 mwxd/myal/mynk + refreshWxid stub）· `net/EnvelopeClient.java`（currentAcct / acct 上报）· `debug/DebugServer.java`（apiSetMyWxid DEV 接口）|
| **用途** | 后台「一个授权码绑到哪个微信号」识别；换号 → `acct_changed=1` 风控；设备画像 |
| **升版复验** | alias 抽取与资源 id 解耦，正常升版无需改；仅资料页「微信号：」中文文案变（极少）需复验 |

---

### 11. 隐藏触发机制（B 模块：摇一摇 / 切后台 / 锁屏 / 口令解锁）

> 状态机自动切换触发器，主体 `moduleB/TriggerGuard.java`（B6 口令走 `SearchUnlock.java`）。B1/B2/B5 单向 → HIDDEN；B6 → VISIBLE。全部用系统 API、不依赖微信混淆类，升版稳定。

| 触发 | 机制 | hook/监听 | 状态 |
|------|------|-----------|------|
| **B1 摇一摇** | SensorManager 加速度 ≥15m/s²（gForce≥1.5，冷却 1.5s；默认关，用户可开） | `setShakeEnabled` 注册 TYPE_ACCELEROMETER | ✅ **L1 2026-06-30 共存版**（`[TG] B1-shake → enterHidden`，需先显形+开开关） |
| **B2 切后台 · 手势隐藏** | ①前台计数 `onActivityStopped` 归 0 ②广播 `CLOSE_SYSTEM_DIALOGS`(fs_gesture/Home/Recent) → `enterHidden`（默认开，不可关） | `ActivityLifecycleCallbacks` + `BroadcastReceiver(ACTION_CLOSE_SYSTEM_DIALOGS)` | ✅ **L1 2026-06-30 共存版**（见下证据） |
| **B3 Home 键** | 并入 B2（被 CLOSE_SYSTEM_DIALOGS 覆盖） | — | ❌ 不单独实现 |
| **B4 返回键** | hook `Activity.dispatchKeyEvent` 吞 KEYCODE_BACK | `B4_BACK_KEY_ENABLED=false` | 🚫 默认关闭（D-022 入口 bug：MIUI 边缘滑动误触，由 B2 覆盖） |
| **B5 锁屏** | 广播 `ACTION_SCREEN_OFF` → `enterHidden`（解锁后不自动显形） | `BroadcastReceiver(ACTION_SCREEN_OFF)` | ✅ 用户确认 2026-06-01 |
| **B6 口令解锁** | 放大镜 FTS 搜索框输 `111111` → VISIBLE | `SearchUnlock.java` TextWatcher | ✅ 8071 已验（L1 2026-06-30 `[UNLOCK:B6] success`） |

**B2「手势隐藏」L1 实证（2026-06-30 共存版 `com.tencent.mn`）：**

```
[TG] B2-close_dialogs(fs_gesture) → enterHidden
[SM] notify old=显形 new=隐藏
[SF:sm] VISIBLE → HIDDEN
[CF] L4collect removed wxid=...        ← 密友随之隐藏
```

> 同轮还验了 B6：`[SU] unlock matched text=111111` → `[SM] 隐藏→解锁中→显形` → `[UNLOCK:B6] success`（密友 restore 显形）。

**⚠️ 偏移修正**：本节 2026-06-30 补建——此前权威**整体缺 B 触发器章节**（B1–B6 仅根 `HOOKMAP.md` §B 有），属文档偏移，今补齐。

---

### 12. 消息控制（C 模块：未读 / 来电拦截 / 推送过滤）

> C1 防撤回见 §2。本节补 C 模块其余已装机功能，主体 `moduleC/PushFilter.java` + `moduleC/CallGuard.java`。代码核准 2026-06-30。

| 功能 | hook 点（8.0.71 代码核准） | 项目代码 | 状态 |
|------|------|------|------|
| **PushFilter L1 后台消息入队拦截** | `LinkedList.add(NotificationItem)`（`NotificationItem` = `com.tencent.mm.booter.notification.NotificationItem`）→ 密友 talker → `setResult(false)` 不入队 | `moduleC/PushFilter.java`（`[PF:L1]`） | ✅ L1 装机 2026-05-22 |
| **PushFilter NM 通知拦截** | `NotificationManager.notify()` 唯一 hook → 密友 cancel/bypass（普通消息 + voip channel）；VoIP 委托 `CallGuard.handleNmVoip` | `moduleC/PushFilter.java` + `CallGuard.java` | ✅ 装机 2026-05-22 |
| **CallGuard CA 来电屏** | `com.tencent.mm.plugin.voip.ui.VideoActivity` `onResume/onStart` → `moveTaskToBack(true)`（全屏来电后台→前台不露）；+ `onUserLeaveHint` 兜底（通话中按 Home） | `moduleC/CallGuard.java`（`[PF:CA]`/`[PF:UL]`） | ✅ 装机 2026-05-29（语音+视频 × 静默/震动） |
| **UNREADFIX 未读计数过滤（C3）** | 底部 tab `com.tencent.mm.ui.LauncherUIBottomTabView.l(int)` + 顶部标题 `com.tencent.mm.plugin.taskbar.ui.TaskBarContainer.setActionBarTitle("微信(N)")` → 减 `moduleD/ConvFilter.getHiddenUnread()`；开关 `shu` 默认关，开则显示密友未读数 | `moduleC/PushFilter.java`（`[PF:UNREADFIX]`） | ✅ 装机 2026-06-06 |

> **权威账**：通知/推送/来电 12 层 hook + 证伪清单见 `docs/P22_PushFilter_VoIP.md`。`NotifyRouter`（提醒 OFF/VIBRATE/SOUND）+ C4 铃声 → v1.1 backlog。废弃 L4b/L4c 见 `docs/archive/HOOKMAP_废弃拦截层_已证伪归档.md`。
> **⚠️ 偏移修正**：本节 2026-06-30 补建——此前权威仅 §2 防撤回，缺 C 其余已装机功能。

---

### 13. 朋友圈痕迹隐藏（D 模块：密友帖 / 点赞 / 评论）

> 主体 `moduleD/MomentsFilter.java`，D 默认全开。朋友圈互动小红点见 §8e；自己「仅可见分组」图标见 §6a。代码核准 2026-06-30。

| 功能 | hook 点（8.0.71 代码核准） | 项目代码 | 状态 |
|------|------|------|------|
| **D1 密友帖整条隐藏** | `ArrayList.addAll(na4.b)` → `la4.p` extractPosterWxid（`la4.p.field_userName` 直读 = fallback 主路径）→ `remove` post；**禁改回 `h1()`**（F-31 null miss） | `moduleD/MomentsFilter.java`（`[MF]`） | ✅ 装机 2026-05-21（fallback 命中 5 次实证） |
| **D2 密友点赞不显示** | `LinkedList.add/addAll`（含懒加载单条）→ 元素 `z15.e56`(或 `cs5.di0`/`i84.y`) wxid 字段 = `d`(proto field1) 或 `f435583d` → block；+ `la4.p.getLikeUserList()` afterHook 过滤 | `moduleD/MomentsFilter.java` | ✅ 装机 2026-05-20 |
| **D3 密友评论不显示** | 同 D2 链路 + `la4.p.getCommentList()` afterHook 过滤密友评论 | `moduleD/MomentsFilter.java` | ✅ 装机 2026-05-20 |

> 失败铁律：`SnsObject.parseFrom` Java hook 零命中（走 JNI/C++，F-27/F-28）；`getCommentList/getLikeUserList` 本体走 JNI 时用 `addAll/add` 拦截兜底。
> **⚠️ 偏移修正**：本节 2026-06-30 补建——此前权威 §一 无 D1/D2/D3 朋友圈主过滤（仅 §8e 小红点 / §6a 仅可见分组图标）。

---

### 14. 屏蔽官方更新（B7：红点 + 三条热更新通道冻结）

> 锁 8.0.71 重新打包，防用户被官方更新诱导升级（升级 → 8071 hook 全失效、模块不兼容），并防官方静默热更新推新检测 / 改 hook 依赖类。
> 机制研究真源：`防封_反检测线/证据/HOTUPDATE_8071_20260622.md`；落地任务：`03_execute_执行任务/P_HotUpdateFreeze_官方热更新冻结/`。

**两层：**

| 层 | 模块（TAG） | 管什么 |
|----|------|--------|
| UI 红点 | `moduleB/UpdateGuard.java`（`[UG]`） | `fl4.o` 0-param bool getter（`Sh`/`Th`/`Wh`）→false → 设置页红点消失；开关 `isUpdateRedDotEnabled` |
| 通道冻结 | `moduleB/HotUpdateFreeze.java`（`[HUF]`） | 三条热更新通道源头 no-op；开关 `AppConfig.isHotFreezeEnabled` **默认 true（生产锁版本）**，置 false 可临时观测 |

**三条热更新通道（jadx 8.0.71 实读 L2 + 装机 L1，小米9/官方包 8071，2026-06-30）：**

| 通道 | hook 点 | 动作 | L1 证据 |
|------|---------|------|------|
| ① Tinker 热补丁（静默 DEX/SO） | `p53.j.b(Map)` 查更入口（叠加 `m53.d0.j(boolean)`/`m53.d0.d(File)`） | freeze 时 setResult no-op，源头断查更 | ✅ `[HUF] tinker p53.j.b → blocked`，下游 d0.j/d 未 fired，微信正常登录（`logs/huf_updatebtn_live_20260630.txt`） |
| ② 整包客户端更新（关于微信→检查更新→后台下载新 APK） | `fl4.o.Wg(boolean,boolean,boolean)`=checkMMdiffUpdatePatchPkgVersion；`fl4.o.Bg(Context,String)`=checkAndShowInstallPatchDialog | freeze 时 no-op，不查更 / 不弹装包框 | ✅ `[HUF] fullapk fl4.o.Wg → blocked`，点「检查更新」不再后台下载（`logs/huf_fullapk_blocked_20260630.txt`） |
| ③ libcso（SO 热补丁容器） | `ip.g.a(Application,String)` | **仅观测不拦**：libcso 兼微信正常加载自带 SO（新装时 cso-p 线程预载 `libbspatch_utils`/`libhpatchz` 走 `CsoLoader.preloadAllInternal`），整条冻会误伤；真要冻的「远程下载段」(G3) 未定位 | ⚠️ observe：预载实跑；`ip.g.a` 未 fired（预载走 preloadAllInternal 另一入口） |

**关键约束：**
- 全程只 hook Java 方法、不碰 native → 与 F-23 无关；libcso native(mprotect) 干预禁。
- **A2×Tinker 交叉（L3）**：A2 把签名 spoof 成官方后，Tinker `ShareSecurityCheck` 验签可能转通过 → 重开通道 → **上 A2 必同时冻 Tinker**。
- LSPatch 共存零副作用：模块在 base.apk、非 tinker patch，断新 patch 不卸模块。
- 字段写法证伪：`z.f176124s` / `g45.c.f246162e`（官方自带「blocked by assist」闸）运行时 NoSuchField（Tinker classloader 分裂）→ 走方法钩。
- 诚实口径：无 L1 证明官方已经此推过新检测 → **预防性冻结**。
- 调研锚点（FINDINGS 2026-05-21）：UI 类 `com.tencent.mm.ui.setting.SettingsAboutMicroMsgUI`；入口 `SettingsUI.P7()`；红点容器 `fl4.o`（8.0.66 对应 `gd4.o`）。
- 项目代码：`moduleB/UpdateGuard.java`（红点）+ `moduleB/HotUpdateFreeze.java`（通道）；ModuleMain install。

---

## 二、资料盘点（功能就绪度）

### "齐活"可立即落 hook（6 项）

| # | 功能 | 当前状态 |
|:-:|------|------|
| 7 | 过滤通讯录 | ✅ 已 work（P19） |
| 8a | 过滤会话列表 | ✅ P17；V→H ✅；H→V fresh-warm ✅（零历史 wxid 仍受微信 DB 限制） |
| 6b | 通讯录标签隐藏 | ✅ 已 work（P19B + 2026-05-27 补 install） |
| 2 | 防撤回（C1） | ✅ L1 装机实证 2026-05-31（jy0.t.f doRevokeMsg + 原文保留 + 系统提示染红） |
| 4 | 添加密群 UI | ✅ 已完成（2026-06-02 GroupCardSelectUI 原生选群器，预选+增删一体） |
| 3 | 添加密友 UI | ✅ 已完成（ContactImportGuard.launchSelectContact 原生 SelectContactUI，预选+增删一体，与密群同套路） |
| 8b | 全局搜索联系人 / 群聊密群 / 聊天记录关键词场景 | ✅ P20 收口（`f0.getView` afterHook 主路径，`tz2.p0/u1/s1` 共用；聊天记录经最终渲染层 id 折叠） |

### 已 L1 验证（原「需要探针」· 升版需复验探针）（2 项）

| # | 功能 | 缺什么 |
|:-:|------|------|
| 6a | 朋友圈"仅可见分组"图标 | ✅ 时间线+详情页 L1 装机（2026-06-10，view 层 ViewStub.inflate+OnGlobalLayout 扫 pt→GONE）；个人相册页=Flutter，v1 不做 → 详见 §6a |
| 1 | 伪装订位 | ✅ pz0.h.c 注入点已验（2026-06-07，PoC 跳点成功）→ 详见 §一.1 |

### v1 不做（1 项）

| # | 功能 | 理由 |
|:-:|------|------|
| 5 | 语音转发 | HOOKMAP §C5 v2，竞品也加固难以反编译 |

---

## 三、维护规则

1. **修改本文件 → 必须经用户明确同意**（总调度文档写入门控）。
2. 新加功能行 → 必须填齐：8.0.71 状态 / 锚定信息 / 项目代码 / 来源。
3. 8.0.66 / 8.0.70 数据 → 只能作为**参考来源**列出，**不得作为 8.0.71 已锚定的实证**。
4. 跨版本类名（如 `MvvmList.n` vs `.m`）→ 必须标版本号。
5. 任何 ⬜ / 🟡 升 ✅ → 必须有 `03_execute_执行任务/P*/result.md` 装机日志原文。
6. 失败方案 → 不进本表，进 `FAILURE_LOG.md`（F-XX 编号）。

---

## 四、关联文档

| 文档 | 用途 |
|------|------|
| `HOOKMAP.md` | 功能模块总图（A~F 域，v1 / v2 分类） |
| `docs/archive/wechat_8066/HOOK_POINTS.md` | 8066 历史伪代码（仅 diff） |
| `docs/archive/wechat_8066/HOOK_MAP_V1.md` | v1 规划 DEPRECATED |
| `docs/isolation/FEATURE_MATRIX.md` | 功能 × 状态 × 失败档案矩阵 |
| `FAILURE_LOG.md` | F-01 ~ F-43 禁止方案铁律索引（详细正文 → `07_archive_归档/FAILURE_LOG_full_20260629.md`；F-40 重装顶爆假种子 recipeOk=false、F-41 后台标记正常不重置 tier/risk、F-42 LSPatch 439×A15 干净装闪退、F-43 克隆宿主签名 bleed-through→cert mismatch） |
| `07_archive_归档/tools/dynamic_crawler_动态爬虫/README.md` | 动态探针工具集（已归档）|
| `03_execute_执行任务/P*/result.md` | 14 份 P 任务装机实证 |
