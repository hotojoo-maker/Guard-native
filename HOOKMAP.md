# HOOKMAP — 功能模块总图

> ⛔ **AI 读此文件的绝对铁律 / AI HARD RULES FOR THIS FILE**
> 1. ✅ = 有 logcat/Frida 日志原文的才算 — 无日志不得标 ✅
> 2. 🟡 = 代码已写但**未装机验证** — 禁止当作"已完成"推进
> 3. ⬜ = 只读名字，**禁止展开细节，禁止推断实现**
> 4. 文档状态与 result.md 不一致 → **停下来问用户，不自行仲裁**
> 5. 不确定任何字段名/类名 → **停下来问用户，禁止猜测**

更新时间：2026-05-27（P20 搜索 v15.1；文档隔离见 [`docs/README.md`](./docs/README.md)；产品总闸见 [`docs/PRODUCT_GATE.md`](./docs/PRODUCT_GATE.md)）
当前底座：**微信 8.0.71**（D-014）
责任人：guard-review_质检门控（资料员/文档员角色已并入此 skill 资料功能档）

---

## 一、模块总开关（给客户看的功能菜单）


| 模块                                    | 状态  | 功能数 | v1 范围                                       | 详情  |
| ------------------------------------- | --- | --- | ------------------------------------------- | --- |
| **A. 核心隐私**（密友列表/密群/密码/总开关）           | 🟡  | 4   | A1/A2/A3 ✅装机；A4 密码⬜待装机                  | §A  |
| **B. 隐藏触发**（摇一摇/切后台/Home/锁屏/搜索框 1111） | 🟡  | 6   | B2/B6 **8071 已验**；B1/B5 待确认；B4 ⬜ | §B  |
| **C. 消息控制**（防撤回/通知伪装/未读/通知模式/**来电拦截**）         | 🟡  | 5   | **语音/视频来电拦截 ✅装机 2026-05-29**（→ `docs/P22_PushFilter_VoIP.md`）；PushFilter L1/NM ✅装机 2026-05-22；L4b ❓；L4c 🟡；AntiRecall 🟡 未装机；C4/C5 v2 起 | §C  |
| **D. 痕迹隐藏**（密友帖/点赞/评论）         | ✅  | 3   | D1+D2+D3 装机确认 2026-05-20（8.0.71） | §D  |
| **E. 装b 模块**（步数/定位/改零钱）               | ⬜   | 3   | 不在 v1                                       | §E  |
| **F. 商业彩蛋**（反盗版引流/独家功能/私域链接）          | ⬜   | 3   | 不在 v1                                       | §F  |


**v1 锁定范围**（**⚠️ 待收敛**：本节为产品初心口径，已与 §二 实体表/`ModuleMain.java` 实际注册偏离；新决策以 §二 + 代码 ModuleMain.install 列表为准）：

- **11 个 hook**（历史规划见 [`docs/archive/wechat_8066/HOOK_MAP_V1.md`](./docs/archive/wechat_8066/HOOK_MAP_V1.md) P0+P1；8071 事实见 [`docs/HOOK_MAP_8071_AUTHORITATIVE.md`](./docs/HOOK_MAP_8071_AUTHORITATIVE.md)）
- **3 态状态机**（显形/隐藏/解锁中，A1 总开关 + A2 密友列表 + A3 密群列表）
- **A4 搜索框 1111 解锁**（hook EditText 文本监听，不在 11 hook 内）
- **B 模块 6 个触发事件**（摇一摇/切后台/Home/返回键/锁屏-解锁/搜索框 1111）
- **朋友圈 Proto 层**（hookSnsObject 主线 + INIT 兜底，不引 native）
- **【部分入位 v1，未结案】**
  - PushFilter L1 + NM ✅ 装机实证 2026-05-22（普通消息 + voip 通知拦截）
  - PushFilter L4b ❓ 未触发 / L4c 🟡 减法逻辑待装机 / CA 🟡 备用层（C3 未读控制对应实现）
  - AntiRecall 🟡 代码已写 + 已注册 install，**LSPosed 装机 `[AR] *` 命中日志尚未抓到**（C1 防撤回）
  - C2 通知伪装：暂由 PushFilter NM 层 cancel 替代，**未单独实现**
  - C4 通知三档 / C5 语音转发：v2+
- **【已下沉 v1】P21 朋友圈小红点 Layer0b/Layer2 🟡**（注：当前实证来源仅 chatfish 反编译 + frida trace，尚无 LSPosed 装机日志原文，证据级 L3）

门控 / 授权 / 状态机 / 设置入口可见性的权威口径见 [`docs/GUARD_GATE_TRUTH.md`](./docs/GUARD_GATE_TRUTH.md)。

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
| **朋友圈小红点 P21** | **Layer0b**：`Activity.onResume` 过滤 `SnsMsgUI*`（互动列表入口，进列表后密友条目不显示 + `w1.y` 归零）；**Layer2**：`FMF.g1("album_dyna_photo_ui_title", true)` 拦截（朋友圈行红点）；**v18 tab badge**：`TabRedDotChangeEvent`/`WeChatTabRedDotEvent` ctor int 字段清零 | 🟡 **L3 待装机实证**（证据源：chatfish 反编译 + frida trace，**尚无 LSPosed 自有 logcat 原文**，不得标 ✅）；v18 tab badge 代码已写，未触发 | ⭐⭐⭐ | ❌ Layer1 `w1.v2` **跨进程不可达**（`:push` 进程写，主进程 hook 打不到）；badge 本地状态，进互动列表消费后自然归零；`w1.E1()` getter 零调用（badge 不走 getter）；`g1(true)` 已拦截（阻止新红点）|
| **L1 MvvmList.n/m** | `MvvmList.n(List,bool)` 8.0.71 / `.m`（8066）→ `kc5.v0` / `kc5.y` → `y.d`（l4）→ **`l4.C0()`** wxid（8071 主路径） | ✅ **装机确认 2026-05-20** | ⭐⭐⭐ | 8066 曾用 h1() 轮询；8071 以 C0 实证（P20B） |
| **L2 MvvmList.s** | `MvvmList.s(List)`                         | ✅ Frida 验证 | ⭐⭐⭐    | 会话备用      |
| **L4 notify**     | `kc5.v0.notifyDataSetChanged` clean-before → L4-NoDiff（setResult null + Handler.post 全量刷）| ✅ **装机实证 2026-05-23**（F-32 DiffUtil 卡帧修复） | ⭐⭐⭐ | 渲染前兜底；L4 beforeHook 同步更新 `sConvAdapterRef`（仅 kc5.v0）+ `sMvvmListRef`；**禁止把 h0 加入 sConvAdapterRef 更新条件** |
| **V↔H 实时刷新**  | V→H：`sPendingHide` + LauncherUI.onResume → `cleanConvData` + `cleanAdapterGraph(L4adapter.q.d)` + `notifyConvAdapter(v0)`；H→V：`sPendingRestore` + `expandCacheWithWarm(kc5.x.h(id) fresh)` + `restoreToMvvmList(h/o/p, CME-safe)` + `restoreAdapterGraphFromCache(adapter.q.d)` + `notifyConvAdapter(v0)` + **80ms post-dedup（v27）**：`postDedupAdapterGraph` 按 identity 把同对象引用收敛成 1 份；filterConvList 加密群保底检 `extractGroupId` + 快照式 CME 防御（v28） | 🟡 **2026-05-27 v28 仅会话 tab 收口**：冷启动 H 态不露 / H 态新消息不露 / V↔H 5 轮热切完美 / 冷启动→V 全恢复 / CME=0 / warmAll expanded=3；证据：`bug排查/final_v28_5rounds.log` + `final_v28_coldstart.log`。⛔ **通讯录 tab V 态 hot-restore 未实现**（`ContactFilter.RefreshBus` 只含 cleanLiveList，无 restore 分支）；同路径密群在通讯录 ⛔ 未实现 — 现象：H→V 后通讯录看不到密友/密群；归属 P_CV1（见 TASK_BOARD §五） | ⭐⭐⭐⭐ | sConvAdapterRef 仅 kc5.v0；禁止 h0/q2 覆盖；禁止 v25 跨 List identity + v26 list-visited 双层强 dedup（已 F-35）；详见 [`docs/CONV_REFRESH_PROBLEM.md`](./docs/CONV_REFRESH_PROBLEM.md) §十二~§十七 |
| INIT              | warm-attach 首次进入清理                         | ✅ Frida 验证 | ⭐⭐⭐    | 老数据清理     |
| 实例轮询              | 3s 检查 hashCode 防 StateFlow 替换              | ✅ Frida 验证 | ⭐⭐⭐    | 朋友圈 o/p   |
| **PushFilter L1** | `LinkedList.add(NotificationItem)` → `this.h` = talker wxid → `shouldHideId` → setResult(false) | ✅ **装机实证 2026-05-22**（普通消息拦截 block+cancel 双层生效） | ⭐⭐⭐ | 主进程；tinker classloader — 用 `obj.getClass().getDeclaredField("h")` 绕开 |
| **PushFilter NM** | `NM.notify(tag,id,Notification)` → ① voip channel → HIDDEN 直接 cancel；② L1 block 后 200ms 内 cancel bypass | ✅ **装机实证 2026-05-22**（普通消息 gap=3ms；语音/视频 ch=voip_norify_channel_silent* cancel） | ⭐⭐⭐ | 主进程；voip channel 名含 `voip`/`ringtone` 即拦 |
| **PushFilter CA** | `Activity.onCreate` 模糊匹配 voip/call/video → HIDDEN 直接 finish | 🟡 代码已写（备用层，voip NM cancel 生效时 Activity 不启动） | ⭐⭐ | 主进程；8.0.71 Flutter VOIP — wxid 不在 Intent/字段，全局静音策略 |
| **PushFilter L4b** | `MainTabUI.i()` afterHook → 隐藏态 return 0 | ❓ 代码已写，装机未触发（无 `[PF:L4b] real=` 日志）— 方法名可能 8.0.71 已变 | ⭐⭐ | 主进程；底部 tab 未读数字；**方法名需 jadx 重查** |
| **PushFilter L4c** | `h0.d(int)` beforeHook → `max(0, in - sHiddenBlocked)` 减法 | 🟡 代码已改（减法逻辑），待装机验证 | ⭐⭐ | 主进程；OEM 桌面角标；全归零有误（非密友角标消失），改为减法；`sHiddenBlocked` 仅计本 session 拦截数 |
| **WeChatDND（规划）** | 密友加入时自动开官方「消息免打扰」→ 角标/tab 天然不计入 | 📋 设计确认，待 Frida trace 调用链 | ⭐⭐⭐⭐ | 可替代 L4b/L4c；Layer 1 防线；需找内部方法名 |
| **NotifyPolicy（规划）** | OFF/VIBRATE/SOUND 三档，Bridge/MMKV 存储，默认 OFF | 📋 方案已出，待 Phase 2 实现 | ⭐⭐⭐ | OFF 已完成；VIBRATE/SOUND 依赖 Phase 2 |
| ❌ ~~x.a(f9)~~     | ~~`booter.notification.x.a(f9)`~~ — 8.0.71 零命中证伪 | ❌ 永久废弃 | — | hook 注册成功但运行时零触发，不走此路径 |
| ❌ ~~NotificationItem.a(Context)~~ | ~~`final` 方法 + ART AOT 内联~~ | ❌ 永久废弃 | — | Xposed 无法拦截；Frida 可以但模块不用 |
| **搜索框过滤 8.0.71**（主页放大镜 FTS 全局搜索） | `ListView.setAdapter` → q2 → 沿继承链 hookAllMethods("getView") → `com.tencent.mm.plugin.fts.ui.f0.getView(int,View,ViewGroup)` declared → afterHook `adapter.getItem(pos)` 拿 `tz2.u1` → `g.f.s` = wxid → `Bridge.allHiddenIds().contains` → `View.GONE + lp.height=0 + margin=0`；同 callback 内 `lv.setDivider(null) + lv.setDividerHeight(0)` 消除 row gap | ✅ **装机实证 2026-05-27 v15.1**（`[SF:gv] blocked pos=1 id=wxid_lzd2va16jd1622` ×14） | ⭐⭐⭐ | **仅"主页放大镜"FTS 搜索**；精确 wxid 匹配不伤同昵称非密友；q2.j hook 保留作 backstop（实测 0 触发）；遗留：①"最常使用" section header 下 ~100px 空白（疑 UI 设计）；②群聊行"包含:密友名"高亮（P26C 范畴） |
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

- 状态：🟡（Bridge/MMKV 完成，ContactResolver L4 stub，待 W4 Proto dump 验证）
- 实现：MMKV 列表存储 + 状态机持久化
- 用户操作：点击添加 wxid → 反射查微信本地数据库取昵称/头像 → 入名单
- 详细：W1 P15 已产出 `ContactResolver.java`（L4 待验证）

#### A3 密群列表（groupId）

- 状态：✅（**2026-05-21 装机已验**）
- 存储：`Bridge.getGroupIds()` / `addGroupId()` / `removeGroupId()`，键 `glst`
- groupId 形态：`xxxxxxxxxxxxxxxx@chatroom`（微信群唯一标识）
- 拦截层复用：
  - 会话列表（ConvFilter）：`l4.h1()` 取到的 username 已自动包含 `*@chatroom`，直接匹配 `getGroupIds()` 即可，**无需新加 hook**
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
| B1  | 摇一摇 → 立即隐藏（**默认关闭**，用户可开） | VISIBLE→HIDDEN | ⬜ 代码已写待装机 | `TriggerGuard.java` SensorManager TYPE_ACCELEROMETER | ⭐⭐ |
| B2  | 切后台/Home/手势切 App → 自动隐藏（**默认开启**，不可关） | VISIBLE→HIDDEN | ✅ **8071 已验** | `TriggerGuard.java` ActivityLifecycleCallbacks + CLOSE_SYSTEM_DIALOGS | ⭐ |
| B3  | ~~Home 键单独~~ | — | ❌ | 被 B2 的 CLOSE_SYSTEM_DIALOGS 覆盖，无需单独实现 | — |
| B4  | 返回键 → 隐藏（仅会话/通讯录主页） | VISIBLE→HIDDEN | ⬜ 待实现 | hook onBackPressed，按页面判断 | ⭐ |
| B5  | 锁屏 → 自动隐藏（解锁后**不**自动显形） | VISIBLE→HIDDEN | ⬜ 代码已写待装机 | `TriggerGuard.java` ACTION_SCREEN_OFF | ⭐ |
| B6  | 主界面放大镜输入 111111 → 解锁显形 | HIDDEN→VISIBLE | ✅ **8071 已验** | `SearchUnlock.java` FTS 搜索页 TextWatcher | ⭐⭐ |

**下一步**：B1/B5 待 8071 装机确认；B4 返回键待实现；111111 后密友自动回显 → P20B Bug B / P26。

---

### §C 消息控制 🟡（部分代码已入 v1，整体未结案）

> 🔔 **语音/视频来电拦截 ✅ 装机验证 2026-05-29**（语音+视频 × 静默/震动，零声/零亮屏/零浮窗/零小窗/无挂断嘟；震动=来电单次 onset）。
> **权威文档**：[`docs/P22_PushFilter_VoIP.md`](./docs/P22_PushFilter_VoIP.md)（8.0.71 通知/推送/来电唯一权威，含 12 层 hook + 证伪清单）。
> 实现：`moduleC/PushFilter.java`（计划抽 `moduleC/CallGuard.java`）+ `moduleC/NotifyRouter.java`。

> **v1 实装现状**（按铁律"无 logcat 不得标 ✅"重新整理）：
> - **PushFilter L1 + NM** ✅ 装机实证 2026-05-22（普通消息 block+cancel / voip channel 静默） — 见 §二 表
> - **PushFilter L4b/L4c/CA** ❓🟡 代码已写，未拿到完整命中日志
> - **AntiRecall** 🟡 代码已写、已注册 install（ModuleMain L138），**LSPosed 装机 `[AR] *` 命中日志尚未抓到**
> - **C2 通知伪装** 暂由 PushFilter NM 层 cancel 替代实现，无独立类
> - 完整 C2/C3/C4/C5 仍按 v2 节奏推进；现阶段对 §一 状态列 🟡 不得当成 ✅。

| #   | 功能                | 状态  | 备注                                            |
| --- | ----------------- | --- | --------------------------------------------- |
| C1  | 防撤回               | 🟡  | `moduleC/AntiRecall.java` 已 install（L138）；**LSPosed `[AR] *` 装机日志待抓**，不得标 ✅ |
| C2  | 通知伪装为 weixin wxid | 🟡  | 原计划"构造来自 weixin 的消息"未单独实现；当前由 PushFilter NM 层 cancel 替代（NM ✅装机 2026-05-22） |
| C3  | 未读消息条数控制          | 🟡  | PushFilter L4b/L4c 已写（MainTabUI.i / h0.d 减法）；**L4b ❓ 装机未触发**；**L4c 减法逻辑待装机验证** |
| C4  | 通知三档（静默/震/音）      | ⬜   | AudioManager + Vibrator，v2                       |
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
| E2  | 虚拟定位        | ⬜ 资料待补 |
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
| F04 会话隐藏    | L1 **MvvmList.n** (8.0.71) / .m（8066 参考） | L4 notify + INIT + V↔H（见 §二） | ✅ P17 装机；**V→H ✅**；**H→V 🟡**（`docs/CONV_REFRESH_PROBLEM.md`） |
| F05 朋友圈隐藏   | **L0v2 addAll D1**             | `e2.getItemCount` 防跳顶   | ✅ W2 装机确认 |
| F05.2 朋友圈点赞 | L0v4 `LinkedList.add`           | getter after 过滤          | ✅ L1 |
| F05.3 朋友圈评论 | L0v4 `LinkedList.add`           | getter after 过滤          | ✅ L1 |
| F07 通讯录隐藏   | **8.0.71** `ArrayList.addAll(fc5.g)` → `g.d`（z3）→ `z3.c1()` | notify + fragResume 兜底 | ✅ P19 装机 2026-05-20 |
| **A3 密群隐藏**  | 复用 F04 + F07，统一走 `Bridge.allHiddenIds()` | — | ✅ 装机已验（2026-05-21）|
| F08 防撤回     | 待 T08 调研                       | —                         | ⬜          |
| F-搜索（放大镜 FTS） | 联系人：`f0.getView` (q2 父类) afterHook 取 `tz2.u1.f.s` = wxid → GONE + ListView divider 清除 | 群聊：groupId 路径待验；聊天记录：z15.ef6 talker 未解析；fz2.e 数据层 c=3 走 UIN 待映射 | 🟡 联系人✅；群聊/聊天记录⬜ |


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


