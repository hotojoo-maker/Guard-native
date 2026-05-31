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

### 1. 虚拟定位（伪造位置）

| 项 | 内容 |
|------|------|
| **8.0.71 状态** | ❌ 未实装 |
| **竞品锚点（8.0.66）** | `MainEntry.hookLocation(Object tencentLocation)` → `MyLocation.getLocation(loc, json)` |
| **辅助 hook（8.0.66）** | `UserControll.ckSetLocation(Activity, View cancelBtn, Object addr)` — 从地址选择 UI 捕获用户选择 |
| **目标类（8.0.66 已知）** | `com.tencent.tencentmap.lbssdk.service.TencentLocation` 或类似 LBS SDK |
| **存储** | MMKV: `fake_location` (boolean) + `location_info` (JSONObject) |
| **address 反射字段** | `r / d / t / u / g / f`（竞品记录） |
| **8.0.71 ⚠️ 缺** | 当前混淆类名（LBS SDK 可能稳定，但具体 hook 签名待 jadx 8.0.71 重查） |
| **项目代码** | ❌ 无 |
| **推荐下一步** | jadx 反编译 8.0.71 找 `TencentLocation` hook 点 + 复用竞品 `MyLocation` 思路 |
| **来源** | `HOOK_IMPLEMENTATION_ANALYSIS.md` §2.8 |

---

### 2. 防撤回

| 项 | 内容 |
|------|------|
| **8.0.71 状态** | 🟡 代码已写未装机（`moduleC/AntiRecall.java`） |
| **竞品关键类（8.0.66）** | **`WmyRevokeMsg`**（Catfish 自有类） |
| **竞品 hook 入口** | `MainEntry.revoke(p1, p2)` → `WmyRevokeMsg.init` · `MainEntry.revoke(cmd, Map, obj)` → `WmyRevokeMsg.revoke` |
| **数据通道** | `cmd == "revokemsg"` 的命令通道 |
| **存储** | MMKV: `revoke_msg`（项目 key=`arc`） |
| **项目代码** | ✅ `moduleC/AntiRecall.java` + `Bridge.isAntiRecallEnabled()`（key=`arc`，default true） |
| **install** | ✅ ModuleMain.install:138（本仓库已注册） |
| **8.0.71 ⚠️ 缺** | 装机后是否真拦截（AntiRecall.java 仿写自竞品，需 logcat `[AR] *` 命中验证） |
| **推荐下一步** | 装机让密友撤回消息 → 看 logcat 是否有 `[AR] *` 拦截命中 → 若失败需 Frida 找 8.0.71 真实 revokemsg 处理路径 |
| **来源** | `HOOK_IMPLEMENTATION_ANALYSIS.md` §2.9 + 项目 `moduleC/AntiRecall.java` |

---

### 3. 添加密友 UI（wxid 解析昵称头像）

| 项 | 内容 |
|------|------|
| **8.0.71 状态** | 🟡 数据层完整、UI 入口缺 |
| **数据层** | ✅ `Bridge.addWxid(wxid) / removeWxid(wxid) / getWxids() / allHiddenIds()`（密友+密群 union） |
| **MMKV key** | `hlst`（hidden list） |
| **wxid → 昵称/头像反射链（8.0.66 参考）** | `com.tencent.mm.storage.ContactStorage` → `field_nickname / field_avatar` |
| **8.0.71 等价类** | `com.tencent.mm.storage.l4`（contact）→ `C0()` 返 wxid · nickname/avatar getter（如 `j1() / m2()`）需 8.0.71 重查 |
| **现有项目代码** | ✅ `debug/ContactResolver.java`（P15 W1 已产出 L4 stub）· DebugServer 添加 wxid 接口已实现 |
| **UI 入口** | ⚠️ `SettingsEntry.showGuardDialog()` 当前只有 3 个按钮（切显隐 / 密友 ON-OFF / 关闭），**缺添加密友按钮** |
| **推荐下一步** | (a) 完成 ContactResolver L4 wxid→昵称/头像验证 (b) SettingsEntry.showGuardDialog 加"添加密友"按钮触发 wxid 输入对话框 |
| **来源** | `HOOK_IMPLEMENTATION_ANALYSIS.md` §2.7 + `HOOK_POINTS.md` §F04 |

---

### 4. 添加密群 UI（groupId 解析）

| 项 | 内容 |
|------|------|
| **8.0.71 状态** | ✅ 数据层 + 过滤链完整装机实证（2026-05-21） |
| **数据层** | ✅ `Bridge.addGroupId(gid) / removeGroupId(gid) / getGroupIds() / isGroupId()` |
| **MMKV key** | `glst`（group list） |
| **groupId 形态** | `xxxxxxxxxxxxxxxx@chatroom` |
| **过滤链复用** | F04（`l4.C0()` 返的 username 已含 `*@chatroom`） + F07（`z3.c1()` 同字段） |
| **8.0.71 chatroom 反射** | 8.0.66 走 `com.tencent.mm.storage.bb`（ChatRoom）· 8.0.71 等价类 ⚠️ 待重查 |
| **项目代码** | ✅ `Bridge.java` 群数据 + 4 Filter 切换到 `allHiddenIds()` |
| **UI 入口** | ⚠️ `SettingsEntry.showGuardDialog()` 缺"添加密群"按钮 |
| **推荐下一步** | (a) SettingsEntry.showGuardDialog 加"添加密群"按钮（与添加密友合并入口，按 `*@chatroom` 自动分流） (b) 群昵称解析 L5 stub 写入 ContactResolver |
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
| **8.0.71 状态** | ✅ **装机实证 2026-05-24（P19B）** |
| **L1 标签入口行** | `ArrayList.addAll(fc5.g)` 中 `e=8` 行为标签入口 → 移除该 item |
| **L2 标签存储** | `com.tencent.mm.storage.d4` 类 |
| **L3 标签成员/搜索 item** | `ye5.j` → `ye5.j.d` 形如 `wxid_xxx-15-0`，去后缀 `-N-M` 得 wxid |
| **L4 Activity 拦截** | `ContactLabelManagerUI / MvvmContactListUI / LabelSearchUI` |
| **项目代码** | ✅ `moduleD/ContactLabelHideGuard.java`（2026-05-27 已补 ModuleMain.install） |
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
| **8.0.71 状态** | ✅ P17 装机；V→H ✅；**H→V 热切 🟡**（冷路径见 `CONV_REFRESH_PROBLEM.md`） |
| **L1 主路径** | `MvvmList.n(List, boolean)`（8.0.66 是 `.m`） → 入参 List remove 密友 |
| **L2 备用** | `MvvmList.s(List)` |
| **L4 兜底** | `kc5.v0.notifyDataSetChanged` clean-before（**禁止用 notifyItemRange**，F-08~F-14 铁律） |
| **V↔H 刷新链** | `sPendingHide` → `LauncherUI.onResume` → cleanConvData → notifyConvAdapter(v0) |
| **wxid 提取** | `kc5.y.d`（`l4` 实例）→ `extractWxid()` 按序回退 `C0() → h1() → j1() → i1() → k1() → getUsername()` ；C0 为 8.0.71 主路径（field_digestUser，2026-05-22 live broad-scan 确认），h1 对公众号 item 返 `"officialaccounts"`，仅作 fallback |
| **项目代码** | ✅ `moduleD/ConvFilter.java` · ModuleMain.install ✅ |
| **来源** | `docs/archive/wechat_8066/HOOK_POINTS.md` §F04（历史）+ `P17_会话LSPosed/result.md` + `HOOKMAP.md` |

---

### 8b. 全局搜索 — 聚合过滤器分源进度（联系人 v15.1 ✅）

| 项 | 内容 |
|------|------|
| **8.0.71 状态** | 🟡 **部分完成**：联系人结果 ✅ 装机实证 2026-05-27 v15.1；群聊 ⬜ 未完成；聊天记录 ⬜ 未完成 |
| **产品事实** | 全局搜索是聚合大过滤器，来源至少包括最近联系、联系人、群聊、聊天记录；不同来源走不同数据结构和渲染路径 |
| **已验证：联系人** | `ListView.setAdapter` → 检测 q2 → 沿继承链 hookAllMethods("getView") → 命中 `com.tencent.mm.plugin.fts.ui.f0.getView(int,View,ViewGroup)` declared → afterHook 拿 `adapter.getItem(pos)` 为 `tz2.u1` → 抽 `tz2.u1.f.s` = wxid → 命中 hidden set → `View.GONE + lp.height=0 + topMargin=bottomMargin=0` |
| **待补：群聊** | 理论候选为 `tz2.s1.s` = groupId，但当前未有密群搜索装机实证；不得标完成 |
| **待补：聊天记录** | `z15.ef6` / FTS 聊天记录行尚未拿到可靠 talker wxid/groupId，当前不能直接拦 |
| **配套消空白** | 同 setAdapter callback 内 `lv.setDivider(null); lv.setDividerHeight(0)` 消除 GONE row 的视觉残留 |
| **fz2.e 数据层** | ❌ 已证伪 — `fz2.e.g` 实测是 UIN 或 `SOSItemRelevant:<关键词>`，不含 wxid 字面；保留 [SF:ALLseen] 探针作未来 UIN→wxid 映射 |
| **过滤原则** | 能确定 wxid/groupId 且命中名单才隐藏；不能确定 id 时只打限流诊断日志，不得猜字段、不得全量隐藏 |

### 8b.1 搜索来源拆分验收

| 来源 | 当前状态 | 验收要求 |
|------|:--:|------|
| 联系人搜索结果 | ✅ v15.1 装机实证 | HIDDEN 态搜密友不显示；VISIBLE 态恢复 |
| 最近联系 | ⬜ 未单独验 | 需确认是否复用联系人 item，不能默认覆盖 |
| 群聊搜索结果 | ⬜ 未完成 | HIDDEN 态搜密群名称/groupId 不显示；日志必须能确认 `*@chatroom` |
| 聊天记录搜索结果 | ⬜ 未完成 | HIDDEN 态搜密友聊天关键词不显示；必须解析真实 talker wxid/groupId |

> 禁止把“联系人搜索已拦截”写成“搜索整体完成”。全局搜索只有四类来源均有 L1 日志后，才能标 ✅。

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
| **8.0.71 状态** | ❌ **空 marker class**，按字段过滤路线走不通 |
| **dump 结果** | `runtime_search_v3.log`: 3 次 dump 全部 `fields:` 空 / widerprobe 实测 `d/e/o` = 查询词+高亮，`p`=`z15.ch6`(protobuf wrapper)，`ch6.e`=byte[]（talker 可能在 protobuf 内但未解析出） |
| **容器** | `LinkedList.add(Object)`（工作线程） |
| **真实 talker** | ⚠️ 在 `ch6.e` protobuf byte[] 里，需 protobuf 解析或 FTS DB 读路径 |
| **推荐策略** | 先以最新装机日志确认 q2/ss4 是否覆盖聊天记录绑定项；未确认 talker 前只做探针或保守兜底，禁止猜字段 |
| **项目代码** | 🟡 `SearchFilter.java` z15.ef6 dump 探针 + `containsHiddenWxid` 兜底（实测零命中） |
| **来源** | `runtime_search_v3.log` + 探针子代理报告 2026-05-27 |

---

## 二、资料盘点（功能就绪度）

### "齐活"可立即落 hook（5 项）

| # | 功能 | 当前状态 |
|:-:|------|------|
| 7 | 过滤通讯录 | ✅ 已 work（P19） |
| 8a | 过滤会话列表 | ✅ P17；V→H ✅；H→V 热路径 ✅ / 冷路径 🟡 |
| 6b | 通讯录标签隐藏 | ✅ 已 work（P19B + 2026-05-27 补 install） |
| 4 | 添加密群 UI | 数据层完整，只缺 SettingsEntry 按钮 |
| 3 | 添加密友 UI | 数据层完整，只缺 SettingsEntry 按钮 + ContactResolver L4 验证 |

### 需要探针 / Frida 验证（4 项）

| # | 功能 | 缺什么 |
|:-:|------|------|
| 8b | 全局搜索群聊/聊天记录分源 | P20-S1/S2 待补；联系人主路径 f0.getView ✅ v15.1 |
| 2 | 防撤回 | 装机验证 AntiRecall.java 真拦截（当前可能是 stub） |
| 6a | 朋友圈"分组可见"图标 | 8.0.71 视图层 hook 点（需写 `moments_crawler.js` 跑朋友圈分组发布） |
| 1 | 虚拟定位 | 8.0.71 TencentLocation hook 点（jadx + Frida 重查） |

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
