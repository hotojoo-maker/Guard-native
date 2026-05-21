# HOOKMAP — 功能模块总图

> ⛔ **AI 读此文件的绝对铁律 / AI HARD RULES FOR THIS FILE**
> 1. ✅ = 有 logcat/Frida 日志原文的才算 — 无日志不得标 ✅
> 2. 🟡 = 代码已写但**未装机验证** — 禁止当作"已完成"推进
> 3. ⬜ = 只读名字，**禁止展开细节，禁止推断实现**
> 4. 文档状态与 result.md 不一致 → **停下来问用户，不自行仲裁**
> 5. 不确定任何字段名/类名 → **停下来问用户，禁止猜测**

更新时间：2026-05-21（D1 fallback 路径修正 F-31；P17/P19 ✅；产品总闸见 [`docs/PRODUCT_GATE.md`](./docs/PRODUCT_GATE.md)）
当前底座：**微信 8.0.71**（D-014）
责任人：guard-doc-audit_资料员

---

## 一、模块总开关（给客户看的功能菜单）


| 模块                                    | 状态  | 功能数 | v1 范围                                       | 详情  |
| ------------------------------------- | --- | --- | ------------------------------------------- | --- |
| **A. 核心隐私**（密友列表/密群/密码/总开关）           | 🟡  | 4   | A1/A2/A3 ✅装机；A4 密码⬜待装机                  | §A  |
| **B. 隐藏触发**（摇一摇/切后台/Home/锁屏/搜索框 1111） | ⬜  | 6   | 代码已写（TriggerGuard/SearchUnlock）**待装机验证** | §B  |
| **C. 消息控制**（防撤回/通知伪装/未读/通知模式）         | 🟡  | 5   | **不在 v1**（v2 起）                             | §C  |
| **D. 痕迹隐藏**（密友帖/点赞/评论）         | ✅  | 3   | D1+D2+D3 装机确认 2026-05-20（8.0.71） | §D  |
| **E. 装b 模块**（步数/定位/改零钱）               | ⬜   | 3   | 不在 v1                                       | §E  |
| **F. 商业彩蛋**（反盗版引流/独家功能/私域链接）          | ⬜   | 3   | 不在 v1                                       | §F  |


**v1 锁定范围**（口径以本行为准，AI 禁止擅自扩范围）：

- **11 个 hook**（HOOK_MAP_V1.md 的 P0+P1，hookSnsObject / hookSearchContact / hookFts / hookRecent / hookConverBack / hookAddressInfo / hookContactCount / hookSnsComments / hookSnsLikes / hookFriendStatus / hookFriendStatusItem）
- **3 态状态机**（显形/隐藏/解锁中，A1 总开关 + A2 密友列表 + A3 密群列表）
- **A4 搜索框 1111 解锁**（hook EditText 文本监听，不在 11 hook 内）
- **B 模块 6 个触发事件**（摇一摇/切后台/Home/返回键/锁屏-解锁/搜索框 1111）
- **朋友圈 Proto 层**（hookSnsObject 主线 + INIT 兜底，不引 native）

**v2/v3 增量**：HOOK_MAP_V1 的 P2 (7) + 暂缓 (5) + E 装b + F 商业彩蛋

---

## 二、关键拦截层（共用基础设施）


| 层                 | hook 点                                     | 状态         | 跨版本稳定度 | 详情        |
| ----------------- | ------------------------------------------ | ---------- | ------ | --------- |
| ❌ ~~L0 Proto~~   | ~~`SnsObject.parseFrom`~~ — F-27 证伪       | ❌ 永久废弃   | ~~⭐⭐⭐⭐⭐~~ | 8.0.66 走 JNI/C++，Java hook 零命中 |
| **L0v2 朋友圈过滤 D1** | `ArrayList.addAll(na4.b)` → `la4.p` → **`la4.p.field_userName` 直读**（fallback 主路径） → remove post | ✅ 装机确认 2026-05-21 | ⭐⭐⭐ | `h1()` 路径 null miss（F-31）；fallback 命中 5 次实证；**禁止改回 h1() 主路径** |
| **L0v4 赞评过滤 D2/D3** | `LinkedList.add(z15.e56/cs5.di0/i84.y)` → `entry.d`（或 `f435583d`）=wxid → block | ✅ 装机确认 2026-05-20 | ⭐⭐⭐ | 4 字段轮询：`d / f435583d / username / field_userName`；getCommentList/LikeUserList 走 JNI 不可用 |
| **F07 通讯录 8.0.71** | `ArrayList.addAll` → `fc5.g` → `g.d`（z3 实例）→ `z3.c1()` → remove | ✅ P19 2026-05-20 | ⭐⭐⭐ | **仅通讯录**；类常量 `com.tencent.mm.storage.z3`；`MvvmList.n/u` 零触发 |
| **朋友圈小红点**      | 未知 — `FMF.g1()`路径卡关（见 P21 worklog） | ⬜ **未实现** | ⭐⭐⭐ | FindMoreFriendsUI.L1() 入口，wxid 级漏斗未打通 |
| **L1 MvvmList.n/m** | `MvvmList.n(List,bool)` 8.0.71 / `.m` 8.0.66 → `kc5.v0` 适配器 / `kc5.y` item → `y.d`（l4 实例）→ `l4.h1()` | ✅ **装机确认 2026-05-20** | ⭐⭐⭐ | 会话主线；getter 候选 `h1/j1/i1/k1/getUsername/getUserName` 轮询 |
| **L2 MvvmList.s** | `MvvmList.s(List)`                         | ✅ Frida 验证 | ⭐⭐⭐    | 会话备用      |
| **L4 notify**     | `f45.s0.notifyDataSetChanged` clean-before | ✅ Frida 验证 | ⭐⭐     | 渲染前兜底     |
| INIT              | warm-attach 首次进入清理                         | ✅ Frida 验证 | ⭐⭐⭐    | 老数据清理     |
| 实例轮询              | 3s 检查 hashCode 防 StateFlow 替换              | ✅ Frida 验证 | ⭐⭐⭐    | 朋友圈 o/p   |
| **搜索拦截**          | `SearchFilter` addAll + `hookSearchContact` | ⬜ 代码已写 P20 未装机 | ⭐⭐ | 隐藏态藏 FTS |
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

- 状态：⬜ `SearchUnlock.java` 代码已写，**P20 未装机验证**
- UI：主界面 **放大镜 → FTS 全局搜索** EditText（不是聊天输入框）
- 默认密码 **`111111`**（6 个 1），精确匹配、无回车，仅 **HIDDEN** 态 → **显形** + 自动回主界面
- 详细：PRODUCT_GATE §五 · `P20_搜索拦截/brief.md`

---

### §B 隐藏触发机制 🟡


> **触发方向铁律：B1-B5 全部单向 → HIDDEN（紧急收纳），B6 单向 → VISIBLE（解锁显形）。没有 toggle。**
> 忘记密码 = 只能卸载重装（密码存 SharedPreferences，随 app 数据清除）。

| #   | 功能 | 方向 | 状态 | Android 技术点 | 难度 |
| --- | --- | --- | --- | --- | --- |
| B1  | 摇一摇 → 立即隐藏（**默认关闭**，用户可开） | VISIBLE→HIDDEN | ⬜ 代码已写待装机 | `TriggerGuard.java` SensorManager TYPE_ACCELEROMETER | ⭐⭐ |
| B2  | 切后台/Home/手势切 App → 自动隐藏（**默认开启**，不可关） | VISIBLE→HIDDEN | ⬜ 代码已写待装机 | `TriggerGuard.java` ActivityLifecycleCallbacks + CLOSE_SYSTEM_DIALOGS | ⭐ |
| B3  | ~~Home 键单独~~ | — | ❌ | 被 B2 的 CLOSE_SYSTEM_DIALOGS 覆盖，无需单独实现 | — |
| B4  | 返回键 → 隐藏（仅会话/通讯录主页） | VISIBLE→HIDDEN | ⬜ 待实现 | hook onBackPressed，按页面判断 | ⭐ |
| B5  | 锁屏 → 自动隐藏（解锁后**不**自动显形） | VISIBLE→HIDDEN | ⬜ 代码已写待装机 | `TriggerGuard.java` ACTION_SCREEN_OFF | ⭐ |
| B6  | 主界面放大镜输入 111111 → 解锁显形 | HIDDEN→VISIBLE | ⬜ 代码已写待装机 | `SearchUnlock.java` FTS 搜索页 TextWatcher | ⭐⭐ |

**下一步**：B1/B2/B5 代码已写（TriggerGuard.java），需装机验证；B6 代码已写（SearchUnlock.java），P20 装机时一并验。B4 待实现。

---

### §C 消息控制 🟡


| #   | 功能                | 状态  | 备注                                            |
| --- | ----------------- | --- | --------------------------------------------- |
| C1  | 防撤回               | ⬜   | 抄 Catfish `WmyRevokeMsg` 类                    |
| C2  | 通知伪装为 weixin wxid | ⬜   | **简化：构造来自 weixin 的消息**，不动 NotificationManager |
| C3  | 未读消息条数控制          | ⬜   | hook 计数显示                                     |
| C4  | 通知三档（静默/震/音）      | ⬜   | AudioManager + Vibrator                       |
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
| F04 会话隐藏    | L1 **MvvmList.n** (8.0.71) / .m (8.0.66) | L4 `kc5.v0.notifyDataSetChanged` + INIT | ✅ 8.0.71 装机（P17） |
| F05 朋友圈隐藏   | **L0v2 addAll D1**             | `e2.getItemCount` 防跳顶   | ✅ W2 装机确认 |
| F05.2 朋友圈点赞 | L0v4 `LinkedList.add`           | getter after 过滤          | ✅ L1 |
| F05.3 朋友圈评论 | L0v4 `LinkedList.add`           | getter after 过滤          | ✅ L1 |
| F07 通讯录隐藏   | **8.0.71** `ArrayList.addAll(fc5.g)` → `g.d`（z3）→ `z3.c1()` | notify + fragResume 兜底 | ✅ P19 装机 2026-05-20 |
| **A3 密群隐藏**  | 复用 F04 + F07，统一走 `Bridge.allHiddenIds()` | — | ✅ 装机已验（2026-05-21）|
| F08 防撤回     | 待 T08 调研                       | —                         | ⬜          |
| F-搜索        | SearchFilter `z15.ef6` + hookSearchContact | SearchUnlock 密码入口   | ⬜ 代码已写 P20 未装机 |


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


