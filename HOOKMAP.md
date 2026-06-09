# HOOKMAP — 功能模块总图

> ⛔ **AI 读此文件的绝对铁律 / AI HARD RULES FOR THIS FILE**
> 1. ✅ = 有 logcat/Frida 日志原文的才算 — 无日志不得标 ✅
> 2. 🟡 = 代码已写但**未装机验证** — 禁止当作"已完成"推进
> 3. ⬜ = 只读名字，**禁止展开细节，禁止推断实现**
> 4. 文档状态与 result.md 不一致 → **停下来问用户，不自行仲裁**
> 5. 不确定任何字段名/类名 → **停下来问用户，禁止猜测**

更新时间：2026-06-09（v1 收口口径：P21 主线收尾；P18 基线本轮跳过；P22 普通消息通知/铃声转 v1.1；P20 搜索 v15.2 灰白收口；密友/密群导入 UI ✅）
当前底座：**微信 8.0.71**（D-014）
责任人：guard-review_质检门控（资料员/文档员角色已并入此 skill 资料功能档）

---

## 一、模块总开关（给客户看的功能菜单）


| 模块                                    | 状态  | 功能数 | v1 范围                                       | 详情  |
| ------------------------------------- | --- | --- | ------------------------------------------- | --- |
| **A. 核心隐私**（密友列表/密群/密码/总开关）           | 🟡  | 4   | A2/A3/A4 ✅；A1 授权 v2 待接 | §A  |
| **B. 隐藏触发**（摇一摇/切后台/Home/锁屏/搜索框 1111） | 🟡  | 6   | B1/B2/B6 **8071 已验**；B5 ✅ 用户确认已验证 2026-06-01；B4 因用户设备无返回键，按确认不阻塞（不标 ✅） | §B  |
| **C. 消息控制**（防撤回/通知伪装/未读/通知模式/**来电拦截**）         | 🟡  | 5   | 来电/防撤回/Push L1-NM/未读计数过滤(P_NF4) ✅；普通消息通知 + 铃声功能转 v1.1 | §C  |
| **D. 痕迹隐藏**（密友帖/点赞/评论）         | ✅  | 3   | D1+D2+D3 装机确认 2026-05-20（8.0.71） | §D  |
| **E. 装b 模块**（步数/定位/改零钱）               | 🟡   | 3   | E2 伪装定位 ✅ 装机 2026-06-07；E1/E3 v2/v3      | §E  |
| **F. 商业彩蛋**（反盗版引流/独家功能/私域链接）          | ⬜   | 3   | 不在 v1                                       | §F  |


**v1 锁定范围（收口口径）**：

- 已验主线：A2/A3 导入，B1/B2/B5/B6，D1/D2/D3，P17 会话，P19 通讯录，P20 搜索，P21 Layer0b + P21B WithAll/bm，P23 防撤回，P22 来电/通知主拦截。
- 本轮不阻塞：P18 KPI 基线跳过；C 模块普通消息通知 + 铃声功能转 v1.1；P20B KPI 轻采样已记录但不作为当前 v1 阻塞；P26 fresh-warm 已有证据，不重复；B4 因用户设备无返回键，按确认不阻塞（无 B4 专属 L1，不标 ✅）。
- 8071 hook 事实唯一权威：[`docs/HOOK_MAP_8071_AUTHORITATIVE.md`](./docs/HOOK_MAP_8071_AUTHORITATIVE.md)。

门控 / 授权 / 状态机 / 设置入口可见性的权威口径见 [`docs/GUARD_GATE_TRUTH.md`](./docs/GUARD_GATE_TRUTH.md)。

防破解 / 防盗版（注入隔离 / 蜜罐 / 阶段路线图 / 上线前门控）见 [`PROTECTION_MAP.md`](./PROTECTION_MAP.md)；调试驾驶舱见 [`docs/DEBUG_CONSOLE_V2.md`](./docs/DEBUG_CONSOLE_V2.md)。

**v2/v3 增量**：archive `HOOK_MAP_V1` 的 P2 (7) + 暂缓 (5) + E 装b + F 商业彩蛋

---

## 二、关键拦截层（共用基础设施）

> **【本轮门控锁定 · 2026-05-27】** v1 所有 Filter 链（ConvFilter / MomentsFilter / ContactFilter / SearchFilter）只读 `isActive()` 三层门（授权门 + 密友总开关 f1 + HIDDEN 态）。EntryGate（111111 口令）独立于 AuthGate，口令命中只决定 H→V，不启动过滤、不绕授权。**P_CV1 通讯录 V↔H 热切 restore 同样走这三层门**。权威口径：[`docs/GUARD_GATE_TRUTH.md`](./docs/GUARD_GATE_TRUTH.md) + `.cursor/skills/guard-auth-review_授权检查官/SKILL.md` §零.五 / §八.二 / §八.三。

| 层                 | hook 点                                     | 状态         | 跨版本稳定度 | 详情        |
| ----------------- | ------------------------------------------ | ---------- | ------ | --------- |
| ❌ ~~L0 Proto~~   | ~~`SnsObject.parseFrom`~~ — F-27 证伪       | ❌ 永久废弃   | ~~⭐⭐⭐⭐⭐~~ | 8.0.66 走 JNI/C++，Java hook 零命中 |
| **L0v2 朋友圈过滤 D1** | `ArrayList.addAll(na4.b)` → `la4.p` → **`la4.p.field_userName` 直读**（fallback 主路径） → remove post | ✅ 装机确认 2026-05-21 | ⭐⭐⭐ | `h1()` 路径 null miss（F-31）；fallback 命中 5 次实证；**禁止改回 h1() 主路径** |
| **L0v4 赞评过滤 D2/D3** | `LinkedList.add(z15.e56/cs5.di0/i84.y)` → `entry.d`（或 `f435583d`）=wxid → block | ✅ 装机确认 2026-05-20 | ⭐⭐⭐ | 4 字段轮询：`d / f435583d / username / field_userName`；getCommentList/LikeUserList 走 JNI 不可用 |
| **F07 通讯录 8.0.71** | `ArrayList.addAll` → `fc5.g` → `g.d`（z3 实例）→ `z3.c1()` → remove | ✅ P19 2026-05-20 | ⭐⭐⭐ | **仅通讯录**；类常量 `com.tencent.mm.storage.z3`；`MvvmList.n/u` 零触发 |
| **F07B 标签成员 8.0.71** | `ArrayList.addAll(ye5.j)` → 去后缀 wxid → `allHiddenIds()` | ✅ 2026-06-01 复跑实证 | ⭐⭐⭐ | 详情：`03_execute_执行任务/P19B_ContactLabel/result.md` |
| **朋友圈小红点 P21** | Layer0b `Activity.onResume` 入口归零；P21B `bm/rm -> s9.f(Cursor)` live 游标按 `talker` 过滤互动条目；Layer2/v18 备用 | ✅ 主线收尾：Layer0b 2026-05-21；P21B WithAll/bm 2026-06-09 | ⭐⭐⭐ | 证据：`03_execute_执行任务/P21_MomentsRedDot/logs/p21b_cursor_fix_verify_20260609.txt`；`rm` 同路径覆盖，后续有场景再复验；详情：`03_execute_执行任务/P21_MomentsRedDot/worklog.md` |
| **L1 MvvmList.n/m** | `MvvmList.n(List,bool)` 8.0.71 / `.m`（8066）→ `kc5.v0` / `kc5.y` → `y.d`（l4）→ **`l4.C0()`** wxid（8071 主路径） | ✅ **装机确认 2026-05-20** | ⭐⭐⭐ | 8066 曾用 h1() 轮询；8071 以 C0 实证（P20B） |
| **L2 MvvmList.s** | `MvvmList.s(List)`                         | ✅ Frida 验证 | ⭐⭐⭐    | 会话备用      |
| **L4 notify**     | `kc5.v0.notifyDataSetChanged` clean-before → L4-NoDiff（setResult null + Handler.post 全量刷）| ✅ **装机实证 2026-05-23**（F-32 DiffUtil 卡帧修复） | ⭐⭐⭐ | 渲染前兜底；L4 beforeHook 同步更新 `sConvAdapterRef`（仅 kc5.v0）+ `sMvvmListRef`；**禁止把 h0 加入 sConvAdapterRef 更新条件** |
| **V↔H 实时刷新**  | 会话/通讯录热切刷新 | 🟡 会话热路径 ✅；通讯录密友 ✅；冷路径/密群恢复仍有债 | ⭐⭐⭐⭐ | 详情：`docs/CONV_REFRESH_PROBLEM.md`、P_CV1 |
| INIT              | warm-attach 首次进入清理                         | ✅ Frida 验证 | ⭐⭐⭐    | 老数据清理     |
| 实例轮询              | 3s 检查 hashCode 防 StateFlow 替换              | ✅ Frida 验证 | ⭐⭐⭐    | 朋友圈 o/p   |
| **PushFilter L1** | `LinkedList.add(NotificationItem)` talker 拦截 | ✅ 2026-05-22 装机 | ⭐⭐⭐ | 详情：`docs/P22_PushFilter_VoIP.md` |
| **PushFilter NM** | `NotificationManager.notify` cancel/bypass | ✅ 2026-05-22 装机 | ⭐⭐⭐ | 普通消息 + voip channel |
| **CallGuard CA** | `VideoActivity.onResume/onStart` → `moveTaskToBack(true)` 全屏来电屏（后台→前台不露） | ✅ 装机验证 2026-05-29 | ⭐⭐⭐ | 主进程；来电链已拆到 `CallGuard.java`；旧 `onCreate 模糊匹配 finish` 已证伪删除（P22 §七.A）|
| **UNREADFIX（P_NF4）** | 底部 tab `LauncherUIBottomTabView.l(int)` + 顶部标题 `TaskBarContainer.setActionBarTitle` → 减 `ConvFilter.getHiddenUnread()` | ✅ 装机实证 2026-06-06 | ⭐⭐⭐ | 主进程；默认隐藏态密友未读数在底部 tab 红点 + 顶部「微信(N)」标题两处被**过滤/隐藏**；打开开关 `shu`（显示密友未读消息数，默认关）则**显示**密友未读数；证据 `03_execute_执行任务/P22_PushFilter/pnf4_unread_20260606.log` |
| ❌ ~~PushFilter L4b~~ | ~~`MainTabUI.i()` afterHook~~ — 装机零触发 | ❌ 旁路废弃 | — | 2026-06-06 实证全程无 `[PF:L4b] real=`，微信未读不走此路径；已被 UNREADFIX 取代 |
| ❌ ~~PushFilter L4c~~ | ~~`h0.d(int)` 减法~~ — 已禁用 | ❌ 已禁用 stub | — | `installL4c` 仅打 `[PF:L4c] disabled (pending WeChatDND)`，过度扣减问题待 WeChatDND 取代 |
| **WeChatDND（规划）** | 密友加入时自动开官方「消息免打扰」→ 角标/tab 天然不计入 | 📋 设计确认，待 Frida trace 调用链 | ⭐⭐⭐⭐ | 可替代 L4b/L4c；Layer 1 防线；需找内部方法名 |
| **NotifyPolicy（NotifyRouter）** | 密友提醒方式 OFF/VIBRATE/SOUND 三档，Bridge/MMKV 存储，默认 OFF | 🟡 v1.1 backlog | ⭐⭐⭐ | `moduleC/NotifyRouter.java`；静默/震动已有实证，普通消息完整链路与 SOUND 档铃声转 v1.1 |
| ❌ ~~x.a(f9)~~     | ~~`booter.notification.x.a(f9)`~~ — 8.0.71 零命中证伪 | ❌ 永久废弃 | — | hook 注册成功但运行时零触发，不走此路径 |
| ❌ ~~NotificationItem.a(Context)~~ | ~~`final` 方法 + ART AOT 内联~~ | ❌ 永久废弃 | — | Xposed 无法拦截；Frida 可以但模块不用 |
| **搜索框过滤 8.0.71**（主页放大镜 FTS） | `q2/f0.getView` → `tz2.u1/p0/s1` wxid/groupId → GONE + `lp.height=1` | ✅ P20 收口 L1 | ⭐⭐⭐ | 证据：`tools/p20_search_logcat_runner_20260606_180946.log`；详见权威 §8b |
| ❌ ~~ss4.p.onBindViewHolder~~ — F-32x | RecyclerView ss4.p 本体 vis=0 但**父级 ConstraintLayout vis=8 GONE**，根本不上屏；2026-05-27 02:23 误锚定，2026-05-27 04:00 推翻 | ❌ 永久废弃 | — | 真渲染容器是 ListView (HeaderViewListAdapter wraps q2)；fts_tree_v2.log L243/273 铁证；hook 装上永不触发 |
| ❌ ~~q2.j(View, jz2.g, boolean)~~ — F-32y | 8.0.71 搜索结果渲染**不经** q2.j；hook 装上 0 触发（final_v15 终端 AI 实证）；保留陪跑 | ❌ 主路径废弃，仅作 backstop | — | q2 渲染走 q2.getView (从 f0 继承) afterHook 路径；不要试图把 q2.j 当主入口 |
| ❌ ~~SearchFilter 5-hook offset (getCount/getView/getItem/getItemId/getItemViewType)~~ — F-32z | setResult(orig-skip) 干扰 q2 内部 data swap，搜索结果区**完全空白** + ANR；final_v11/v12/v13/v14 装机连续 4 次空白实证 | ❌ 永久废弃 | — | 单 hook afterHook GONE 就够；不要试图缩 ListView count；如想消空白条用 setDivider(null) + lp.margin=0 而非 count 减 |
| **进程白名单**         | LSPosed 启动只 hook com.tencent.mm 主          | ⬜ 待实现      | ⭐⭐⭐⭐⭐  | F-16 铁律   |


---

## 三、模块详细（详细资料链接，⬜ 段只列名字）

### §A 核心隐私 🟡

#### A0 产品总闸（必读）

- **状态机** = 一切开关；**VIP** = 授权（≠ 隐藏态）；**密码** = 放大镜→全局搜索入口
- 公式：`VIP && isActive() && 密友名单` → 藏一切（通知栏 v2+）
- 详细：[`docs/PRODUCT_GATE.md`](./docs/PRODUCT_GATE.md)

#### A1 总开关（状态机）

- 状态：🟡（StateMachine 3 态 + `isActive()` 总闸，Filter 已接；VIP 授权 v2）
- 实现：MMKV `g_<seed>` / key 短哈希
- 详细：`StateMachine.java` + PRODUCT_GATE §三

**状态语义（防歧义）**：

| 状态 | 密友/密群可见？ | id 过滤生效？ | 设置入口可见？ | 如何进入 |
|------|:---:|:---:|:---:|------|
| **HIDDEN（隐藏）** | ❌ | ✅ | ❌ | 首装默认 / B1-B5 任一触发 |
| **VISIBLE（显形）** | ✅ | ❌ | ✅ | B6 放大镜输入 111111 解锁 |
| **UNLOCKING** | ❌ | ✅ | ❌ | 搜索框弹出但密码未输完 |

> HIDDEN 态过滤范围 = **密友 wxid ∪ 密群 groupId（`*@chatroom`）**，覆盖会话 / 通讯录 / 朋友圈 / 搜索四条干道。
> 防撤回/虚拟定位等**非 id 过滤**功能：所有状态下均有效（不受 HIDDEN/VISIBLE 约束）。
> **密友功能入口**（⬜ 待实现）：注入到微信「设置」页顶部；HIDDEN 态自动消失，不留痕迹。

#### A2 密友列表（wxid）

- 状态：✅ 数据层 + 导入 UI 已收口（`SelectContactUI` 原生选人器，预选 + diff 增删一体）
- 实现：`Bridge.addWxid/removeWxid/getWxids/allHiddenIds()` + MMKV `hlst`
- 用户操作：设置入口「密友列表/添加密友」→ 原生选人器 → 返回 `Select_Conv_User` wxid CSV → diff 增删
- 详细：`docs/HOOK_MAP_8071_AUTHORITATIVE.md` §3；`ContactResolver.java` 仅作昵称/头像辅助，不作为导入主路径

#### A3 密群列表（groupId）

- 状态：✅ 数据层 + 过滤链（2026-05-21）+ 导入 UI（2026-06-02）已收口
- 导入入口 ✅：密群列表 → `GroupCardSelectUI` 原生选群器（`already_select_contact` 预选已隐群 + 增删一体），详见 `docs/HOOK_MAP_8071_AUTHORITATIVE.md` §4
- 存储：`Bridge.getGroupIds()` / `addGroupId()` / `removeGroupId()`，键 `glst`
- groupId 形态：`xxxxxxxxxxxxxxxx@chatroom`（微信群唯一标识）
- 拦截层复用：
  - 会话列表（ConvFilter）：`l4.C0()` 取到的 username 已自动包含 `*@chatroom`，直接匹配 `getGroupIds()` 即可，**无需新加 hook**
  - 通讯录（ContactFilter）：微信「群聊」分组走 `z3.c1()`，同字段，同样匹配
  - 朋友圈：密群消息不出朋友圈，**A3 不涉及 D1/D2/D3**
  - 搜索：FTS 中的群聊条目 wxid 字段即 groupId，复用 SearchFilter
- 详细：实现挂 P21（B 触发周）顺手做，与 ContactResolver 群昵称解析联动

#### A4 密码入口（全局搜索）

- 状态：✅ `SearchUnlock.java` **8071 装机 2026-05-22**（`P20B/worklog`）；过滤见 P20 v15.1
- UI：主界面 **放大镜 → FTS 全局搜索** EditText（不是聊天输入框）
- 默认密码 **`111111`**（6 个 1），精确匹配、无回车，仅 **HIDDEN** 态 → **显形** + 自动回主界面，与授权无关
- 口径：`111111` 是入口手势 / 显形开关，**不是授权码**；只要口令正确就可显形并看到入口，禁止在 `SearchFilter` 里扩展授权或状态机写操作
- 设置入口：仅 **VISIBLE** 态可见，**HIDDEN** 态必须消失，不暴露密友功能存在
- 详细：PRODUCT_GATE §五 · `P20_搜索拦截/brief.md`

---

### §B 隐藏触发机制 🟡


> **触发方向铁律：B1-B5 全部单向 → HIDDEN（紧急收纳），B6 单向 → VISIBLE（解锁显形）。没有 toggle。**
> **B2 后台收纳铁律：切后台/Home/手势离开微信/锁屏是隐私需求，VISIBLE 时必须立即单向进入 HIDDEN，禁止改成可选、延迟、toggle 或误触规避项。**
> 忘记密码 = 只能卸载重装（密码存 SharedPreferences，随 app 数据清除）。

| #   | 功能 | 方向 | 状态 | Android 技术点 | 难度 |
| --- | --- | --- | --- | --- | --- |
| B1  | 摇一摇 → 立即隐藏（**默认关闭**，用户可开） | VISIBLE→HIDDEN | ✅ 8071 已验 2026-06-07 | `TriggerGuard.java` SensorManager TYPE_ACCELEROMETER；证据 `03_execute_执行任务/P20B_BTriggers_SearchUnlock/logs/b1_shake_fixedkey.log` | ⭐⭐ |
| B2  | 切后台/Home/手势切 App → 自动隐藏（**默认开启**，不可关） | VISIBLE→HIDDEN | ✅ **8071 已验** | `TriggerGuard.java` ActivityLifecycleCallbacks + CLOSE_SYSTEM_DIALOGS | ⭐ |
| B3  | ~~Home 键单独~~ | — | ❌ | 被 B2 的 CLOSE_SYSTEM_DIALOGS 覆盖，无需单独实现 | — |
| B4  | 返回键 → 隐藏（仅会话/通讯录主页） | VISIBLE→HIDDEN | ⬜ 用户设备无返回键，按确认不阻塞；未取得 B4 专属 L1，不标 ✅ | hook onBackPressed，按页面判断 | ⭐ |
| B5  | 锁屏 → 自动隐藏（解锁后**不**自动显形） | VISIBLE→HIDDEN | ✅ 用户确认已验证（2026-06-01） | `TriggerGuard.java` ACTION_SCREEN_OFF | ⭐ |
| B6  | 主界面放大镜输入 111111 → 解锁显形 | HIDDEN→VISIBLE | ✅ **8071 已验** | `SearchUnlock.java` FTS 搜索页 TextWatcher | ⭐⭐ |

**下一步**：当前 v1 不再以 P22 普通消息通知 + 铃声功能 / P18 KPI 作为阻塞；二者分别转 v1.1 / 正式发布门控补跑。P26C 搜索高亮归 UI 优化。

---

### §C 消息控制 🟡（部分代码已入 v1，整体未结案）

> 🔔 **语音/视频来电拦截 ✅ 装机验证 2026-05-29**（语音+视频 × 静默/震动，零声/零亮屏/零浮窗/零小窗/无挂断嘟；震动=来电单次 onset）。
> **权威文档**：[`docs/P22_PushFilter_VoIP.md`](./docs/P22_PushFilter_VoIP.md)（8.0.71 通知/推送/来电唯一权威，含 12 层 hook + 证伪清单）。
> 实现：`moduleC/PushFilter.java`（计划抽 `moduleC/CallGuard.java`）+ `moduleC/NotifyRouter.java`。

> **v1 实装现状**（按铁律"无 logcat 不得标 ✅"重新整理）：
> - **PushFilter L1 + NM** ✅ 装机实证 2026-05-22（普通消息 block+cancel / voip channel 静默） — 见 §二 表
> - **PushFilter L4b/L4c/CA** ❓🟡 代码已写，未拿到完整命中日志
> - **AntiRecall** ✅ L1 装机实证 2026-05-31：`jy0.t.f`(doRevokeMsg) 拦截 + 原文保留 + 插 type=10000 系统提示(染红)；logcat `[AR] recall blocked + tip inserted` ×4（文字/表情/图片/视频）
> - **C2 通知伪装** 暂由 PushFilter NM 层 cancel 替代实现，无独立类
> - 完整 C2/C3/C4/C5 仍按 v2 节奏推进；现阶段对 §一 状态列 🟡 不得当成 ✅。

| #   | 功能                | 状态  | 备注                                            |
| --- | ----------------- | --- | --------------------------------------------- |
| C1  | 防撤回               | ✅  | `moduleC/AntiRecall.java`：hook `jy0.t.f`(doRevokeMsg)，`setResult(null)` 跳过原地覆盖→**原文(文字/图片/视频)保留** + 插 type=10000 系统提示「─── HH:mm 已拦截对方撤回的消息 ───」(染红，hook `MMNeat7extView` 首参 CharSequence 方法)。自己撤回(isSend==1)放行。**L1 装机 2026-05-31**：logcat `[AR] recall blocked + tip inserted` ×4。旧点 a2.b 已证伪→F-37 |
| C2  | 通知伪装为 weixin wxid | 🟡  | 原计划"构造来自 weixin 的消息"未单独实现；当前由 PushFilter NM 层 cancel 替代（NM ✅装机 2026-05-22） |
| C3  | 未读消息条数控制          | ✅  | UNREADFIX（P_NF4）装机 2026-06-06：默认隐藏态密友未读数在底部 tab 红点 + 顶部「微信(N)」标题两处被**过滤/隐藏**；打开「显示密友未读消息数」开关（默认关）则**显示**密友未读数。旧 L4b（零触发）/ L4c（已禁用）已废弃 |
| C4  | 通知模式（静默/震动/铃声）   | 🟡  | 主拦截/来电/未读已收口；普通消息完整链路 + SOUND 档铃声功能转 v1.1。静默/震动已有实证；铃声仍未实装/未验收 |
| C5  | 语音一键转发（v2）        | ⬜   | 难度高，二阶段                                       |


---

### §D 痕迹隐藏 ✅（D1–D3 装机确认 2026-05-20）


| #   | 功能        | 状态  | 实现（8.0.71） |
| --- | --------- | --- | ----------------- |
| D1  | 密友帖整条隐藏 | ✅   | L0v2 `addAll(na4.b)` + `la4.p.field_userName` 直读（fallback；h1() null miss，F-31） |
| D2  | 密友点赞不显示 | ✅   | L0v4 `LinkedList.add` → `z15.e56.d` 阻断 |
| D3  | 密友评论不显示 | ✅   | 同 D2 + `getCommentList()` after 过滤 |


**铁律**：D 模块默认全部 ON，傻瓜式安全。

---

### §E 装b 模块 ⬜（v2/v3）


| #   | 功能          | 状态     |
| --- | ----------- | ------ |
| E1  | 步数装b（WeRun） | ⬜ 资料待补 |
| E2  | 伪装定位（全局伪造定位）| ✅ 装机 2026-06-07：hook `pz0.h.c` 注入 + 原生选点页设置；详见权威 §一.1 / `moduleE/FakeLocation.java` |
| E3  | 改零钱显示       | ⬜ 资料待补 |


> AI 接手时**只看名字**，不要展开。等用户启动 v2 再调研。

---

### §F 商业彩蛋 ⬜（v2）


| #   | 功能                                      | 状态  |
| --- | --------------------------------------- | --- |
| F1  | "独家功能"入口（仿 Catfish 截图最底部）               | ⬜   |
| F2  | 反盗版引流弹窗（校验失败 → 跳商城/客服）                  | ⬜   |
| F3  | 私域链接（cs_url + shop_url，已接 miyou-server） | ⬜   |


> v2 才激活，v1 可埋种子（壳代码）。

---

## 四、功能-拦截层映射表


| 功能          | 主拦截层                           | 兜底层                       | 状态         |
| ----------- | ------------------------------ | ------------------------- | ---------- |
| F04 会话隐藏    | L1 **MvvmList.n** (8.0.71) / .m（8066 参考） | L4 notify + INIT + V↔H（见 §二） | ✅ P17 装机；**V→H ✅**；**H→V fresh-warm ✅**（普通有历史 hidden id；零历史 wxid 仍受微信 DB 限制） |
| F05 朋友圈隐藏   | **L0v2 addAll D1**             | `e2.getItemCount` 防跳顶   | ✅ W2 装机确认 |
| F05.2 朋友圈点赞 | L0v4 `LinkedList.add`           | getter after 过滤          | ✅ L1 |
| F05.3 朋友圈评论 | L0v4 `LinkedList.add`           | getter after 过滤          | ✅ L1 |
| F07 通讯录隐藏   | **8.0.71** `ArrayList.addAll(fc5.g)` → `g.d`（z3）→ `z3.c1()` | notify + fragResume 兜底 | ✅ P19 装机 2026-05-20 |
| **A3 密群隐藏**  | 复用 F04 + F07，统一走 `Bridge.allHiddenIds()` | — | ✅ 装机已验（2026-05-21）|
| F08 防撤回     | `jy0.t.f`(doRevokeMsg) setResult(null) 保原文 + 插 type=10000 系统提示染红 | — | ✅ **P23 L1 装机 2026-05-31**（旧"待 T08 调研"已结案）|
| F-搜索（放大镜 FTS） | `f0.getView` (q2 父类) afterHook 取 `tz2.u1/tz2.p0/tz2.s1` 中 wxid/groupId → GONE + `lp.height=1` + ListView divider 清除 | `z15.ef6/ch6.e` 仅作聊天记录分源理解层，不作为主过滤路径 | ✅ P20 收口：联系人 / 群聊密群 / 聊天记录关键词场景均已 L1；普通群放行 |


---

## 五、状态图例


| 符号  | 含义                        |
| --- | ------------------------- |
| ✅   | 已动态验证通过                   |
| 🟡  | 实现中（已研究透 hook 点，代码未完成）    |
| ⬜   | 未验证（**AI 禁止当结论引用细节**）     |
| ❌   | 已验证失败（永久禁用，进 FAILURE_LOG） |


---

## 六、更新规则

1. 实现完成一个功能 → 改对应行状态
2. 发现新 hook 点 → 加进"§关键拦截层"
3. 任何 ⬜ 升 🟡 必须填"详细资料"链接
4. 任何 🟡 升 ✅ 必须有"动态已证实"出处
5. 永久不修改 ❌ 行（历史永久保留）


