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
> - `refs/FEATURE_MATRIX.md`（功能矩阵 + 失败档案）
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

> 代号「伪装订位」（避敏感词，§6.8）。E2 装b 模块；按阶段铁律属 v2/v3，本条为**调研结论存档**（注入点 L1 已验，未实装成模块）。

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
| **UX 设计（待实装）** | 复用微信原生选点页 `RedirectUI` 选点 → 拦截右上角"发送/保存"读 LatLng 存 MMKV、不发消息（仿 A2/A3 复用原生 UI）；右上角确认方法 + 选中坐标捕获点待补探针 |
| **混淆名警告** | `pz0.h` / `n83.g` / `lt5.*` 为 8.0.71 专属混淆名，升版必经 classmap 重查 |
| **探针** | `tools/probe_loc_send_8071.js`(v1) / `probe_loc_sdk_8071.js`(v2) / `probe_loc_src_8071.js`(v3 栈) / `probe_loc_inject_8071.js`(v4 契约) / `probe_loc_poc_8071.js`(PoC 注入) |
| **历史竞品锚点（8.0.66 参考）** | `MainEntry.hookLocation` → `MyLocation.getLocation`；`ckSetLocation`；目标类 `lbssdk.service.TencentLocation`；address 反射字段 `r/d/t/u/g/f`；来源 `HOOK_IMPLEMENTATION_ANALYSIS.md` §2.8 |

---

### 2. 防撤回

| 项 | 内容 |
|------|------|
| **8.0.71 状态** | ✅ **L1 装机实证 2026-05-31**（`moduleC/AntiRecall.java` v4） |
| **竞品关键类（8.0.66）** | **`WmyRevokeMsg`**（Catfish 自有类） |
| **竞品 hook 入口** | `MainEntry.revoke(p1, p2)` → `WmyRevokeMsg.init` · `MainEntry.revoke(cmd, Map, obj)` → `WmyRevokeMsg.revoke` |
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
| **8.0.71 状态** | ❌ 未实装 |
| **竞品锚点（8.0.66）** | `MainEntry.hookSnsGroup()` → `UserControll.hookSnsGroup()` → `isHideGroup()` 检查 |
| **目标** | 朋友圈条目右下角的"仅可见分组"图标隐藏 |
| **存储** | MMKV: `hide_group`（boolean） |
| **8.0.71 ⚠️ 缺** | 当前 hook 点（竞品是 boolean 返回，需重查 8.0.71 朋友圈视图层调用栈） |
| **推荐下一步** | dynamic_crawler 跑朋友圈分组发布场景（需先写 `moments_crawler.js`） |
| **来源** | `HOOK_IMPLEMENTATION_ANALYSIS.md` §2.4 hookSnsGroup |

### 6b. 通讯录"标签"隐藏

| 项 | 内容 |
|------|------|
| **8.0.71 状态** | ✅ **装机实证 2026-05-24（P19B）**；标签内成员过滤 2026-05-31 重构独立成模块、用户现场复验 ✅ |
| **L1 标签入口行** | `ArrayList.addAll(fc5.g)` 中 `e=8` 行为标签入口 → 移除该 item |
| **L2 标签存储** | `com.tencent.mm.storage.d4` 类 |
| **L3 标签成员/搜索 item** | `ye5.j` → `ye5.j.d` 形如 `wxid_xxx-15-0`，去后缀 `-N-M` 得 wxid；代码 = `moduleD/ContactLabelMemberFilter.java`（日志 `[CLM:label]`，门控 isActive+allHiddenIds） |
| **L4 Activity 拦截** | `ContactLabelManagerUI / MvvmContactListUI / LabelSearchUI` |
| **项目代码** | ① 整标签入口/管理页隐藏（L1 `fc5.g` e=8 / L2 `d4` / L4 Activity，开关 hclb）= ✅ `moduleD/ContactLabelHideGuard.java`（2026-05-27 补 install）；② L3 标签内成员按 wxid 藏密友（门控 isActive+allHiddenIds）= ✅ `moduleD/ContactLabelMemberFilter.java`（2026-05-31 P_CV1 重构丢失后独立成模块，用户现场复验） |
| **存储** | MMKV: `hcl`（`Bridge.isHideContactLabelEnabled`） |
| **来源** | `03_execute_执行任务/P19B_ContactLabel/result.md` |

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
| **来源** | `03_execute_执行任务/P19_通讯录隐藏/result.md` + `HOOKMAP.md` §F07 |

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
| `ss4.p.onBindViewHolder` | ❌ **F-32x 永久废弃**：父级 ConstraintLayout vis=8 GONE 不上屏（fts_tree_v2.log L243 铁证）|
| `SearchFilter 5-hook offset` (getCount/getView/getItem/getItemId/getItemViewType 联动) | ❌ **F-32z 永久废弃**：setResult(orig-skip) 干扰 q2 内部 data swap，搜索结果区**全空白** + ANR（final_v11..v14 实证）|
| **遗留代码** | SearchFilter.java 内 q2.j / ss4.p / 5-hook offset 用 `if (false)` 保留供考古，不删除 |

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

### 需要探针 / Frida 验证（1 项）

| # | 功能 | 缺什么 |
|:-:|------|------|
| 6a | 朋友圈"分组可见"图标 | 8.0.71 视图层 hook 点（需写 `moments_crawler.js` 跑朋友圈分组发布） |
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
| `refs/FEATURE_MATRIX.md` | 功能 × 状态 × 失败档案矩阵 |
| `FAILURE_LOG.md` | F-01 ~ F-34 禁止方案铁律（F-32 ConvFilter L4 卡帧、F-33 V↔H adapter ref 污染、F-34 sConvCache 死循环） |
| `02_tools_工具/dynamic_crawler_动态爬虫/README.md` | 动态探针工具集 |
| `03_execute_执行任务/P*/result.md` | 14 份 P 任务装机实证 |
