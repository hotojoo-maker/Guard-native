# P21 工作日志 — 朋友圈气泡小红点守护

> 更新时间：2026-06-09 **P21 主线收尾：Layer0b + WithAll/bm live Cursor 过滤已装机**
> 结论：**红点/气泡 = 独立 unread 链**；**badge = 本地 DB 状态（不随发送方删除同步）**；**Layer0b 入口归零 + P21B WithAll/bm 条目过滤均已 L1**；`rm/SnsMsgUIWithRelevance` 同路径覆盖，后续有入口再复验，不阻塞当前 v1。

---

## 当前状态

✅ **Layer0b 入口归零有效**。进互动列表时 `[MRD:smsg:enter]` 命中，badge 进入后归零。

✅ **P21B WithAll/bm 条目过滤有效**。`bm -> s9.f(Cursor)` live 游标按 `talker` 包装为 `TalkerFilterCursor`，2026-06-09 装机实证 `10→1`，AA熵条目不显示。`rm/SnsMsgUIWithRelevance` 代码同路径覆盖，后续有入口时补 L1，不阻塞当前 v1。


| 层                 | Catfish 8.0.70                                  | Guard 8.0.71 v17                                                | 状态     |
| ----------------- | ----------------------------------------------- | --------------------------------------------------------------- | ------ |
| **Layer 0 黑名单注入** | `hookSnsMsgList()` → `addBlackList2(ArrayList)` | `installCatfishSnsMsgListHook` — 8.0.71 无匹配 ArrayList 入参       | ❌ 0 hooks（跳过） |
| **Layer 0b 消费层**  | 进互动列表消红点                                        | v17: `Activity.class.onResume` 过滤 `SnsMsgUI*`，密友条目不显示 | ✅ **实证有效** |
| **Layer 1 写入拦截**  | `hookSnsCommentOne` / 数据层                       | `w1.v2(arg[1]=wxid)` block — 写入在 `:push` 进程，主进程 hook 打不到 | ❌ 跨进程不可达 |
| **Layer 2 视觉兜底**  | SnsObject 清零后 UI 自然不亮                           | `FMF.g1(..., false)` + g1(true) 拦截（已装，未触发）                | ⏳ 已装待触发 |
| **时间线气泡**         | —                                               | `MomentsFilter` L0v3 `jw1.d` LinkedList.add（P16 已有）             | ✅ D1 侧 |


---

## chatfish版本实证链路（2026-05-21 用户展开 + 截屏）

```
发现 tab 角标 16 ═══ 朋友圈行角标 16（同一 unread 池）
  ↓ 点朋友圈
ImproveSnsTimelineUI
  ↓ 顶部气泡
「16条新消息」                    ← jw1.d / w1.y 计数
  ↓ 点气泡
SnsMsgUIWithRelevance             ← 「朋友的互动消息」
  ↓ 进入/消费列表
角标归零、tab 红点灭               ← Catfish 消费链
```

**Catfish 对应**（`refs/MainEntry.java`，禁止抄类名，只抄语义）：

```java
// 隐藏态：返回密友 wxid 黑名单，供微信 SnsMsg 过滤
hookSnsMsgList() → addBlackList2(ArrayList)  // 并入 wxid，不是 remove
// 显形态 isVipMode：透传空列表
```

P19 brief 确认：`addBlackList2` = **把密友 wxid 并入传入的 String 列表**，微信 UI 按黑名单过滤。

---

## ns.c 聚合桶 → 降级为理解层（非主路径）

早期假设「8.0.71 红点走 `ns.c` 聚合桶」（见 `FINDINGS.md` 2026-05-21 14:05/16:00）：

```
FindMoreFriendsUI.L1()
  → 读 this.x（新帖 wxid List）、this.y（w1.E1 计数）
  → z19 = (!empty(x) || y != 0)
  → 写 ns.c.b / ns.c.g
```

**设备实证推翻主路径价值**：


| 事实                          | 含义              |
| --------------------------- | --------------- |
| Frida 反射清零 ns.c = 逻辑层可清     | 聚合模型本身成立        |
| **红点亮时 ns.c.b = false**     | 视觉红点不读 ns.c.b   |
| **红点亮时 FMF.E = true**       | UI 真正看 FMF 实例字段 |
| ww2.c.b = undefined（8.0.71） | 镜像字段不存在         |


**结论**：`ns.c` 保留作 jadx/链路理解参考；**Guard 实现不再 hook ns.c.b、不再以 L1 聚合为主路径**。wxid 精准过滤改走 `w1.v2(arg[1])`（探针已确认 wxid 直出，无需 L1 内部 List 漏斗）。

---

## 禁止继续（P21 范围外 / 已证伪）


| 方向                                           | 原因                           |
| -------------------------------------------- | ---------------------------- |
| proto repeated count / SnsSyncResponse count | PBCoder 序列化计数 ≠ unread badge |
| SnsObject count 字段                           | 与视觉红点无直接映射                   |
| **ns.c.b 主路径**                               | 红点亮时 b 已为 false，改它无效         |
| **w1.E1() getter**                           | 零调用                          |
| **FinderRedDotTextView View 层**              | v2/v3 探针零命中                  |
| ww2.c.b                                      | 8.0.71 字段不存在                 |
| AbstractCursor / WCDB 逐层 dump                | 零命中 + ANR 风险                 |


代码里 legacy hook（Event/View/E1/ns.c）保留作诊断兜底，**不再扩展、不再当验证主线**。

---

## 探针实证（probe_w1_fields.js）

- `w1` 共 16 方法，**无 insertLike / insertComment**（全混淆）
- `v2(long, String, int, String) → boolean` ← 互动写入，**arg[1] = wxid**
- `w2(long, boolean) → boolean` ← v2 后镜像写入
- 已命中：`arg[1] = wxid_lzd2va16jd1622`（密友 wxid ✅）

---

## 已验证事实（✅ 必须有日志原文）


| 事实                           | 证据                                               |
| ---------------------------- | ------------------------------------------------ |
| D1 朋友圈隐藏密友有效                 | `[MF] D1 blocked poster=wxid_lzd2va16jd1622` × 5 |
| ns.c 全字段（红点气泡亮时）             | `a=false b=false c=false d=false e=1 f=0 g=0`    |
| **ns.c.b = false** 红点气泡亮     | find_reddot_field.log → ns.c 不控视觉                |
| **FMF.E = true（**红点气泡亮**时）** | find_reddot_field.log                            |
| FindMoreFriendsUI.L1 hook 有效 | `[FMF] L1 hooked (1 overload), 78 total`         |
| g1 方法存在（jadx）                | `FindMoreFriendsUI.g1(String, boolean)`          |


---

## v14b 验证清单（装机产出）

### 1. Layer1 — w1.v2 命中日志

**操作**：隐藏态开启 + 密友给你的朋友圈点赞或评论。

**期望 logcat / rawfeed**：

```
[MRD:w1:v2] wxid=wxid_xxx hidden=true
[MRD:w1] blocked v2 wxid=wxid_xxx
```

**判定**：

- ✅ arg[1] 等于触发红点的密友 wxid
- ✅ block 后红点**不出现**（或出现后 g1 层灭掉）
- ❌ 若 v2 零命中 → 8.0.71 还有 parallel 写入路径，需补探针（w2 以外）

### 2. Layer2 — g1 调用日志

**期望**：

```
[MRD:g1] key=album_dyna_photo_ui_title show=true
[MRD:g1:intercept] BLOCKED g1(album_dyna_photo_ui_title,true)
  或
[MRD:g1] g1(album_dyna_photo_ui_title, false) fmfE_before=true fmfE_after=false
```

**判定**：

- ✅ key = `album_dyna_photo_ui_title`
- ✅ `FMF.E` 从 true → false
- ✅ 发现 tab 红点灭

### 3. 复现 / 冷启动


| 场景                         | 期望（Layer1 成功）       |
| -------------------------- | ------------------- |
| 点赞后立即看 tab                 | 红点不出现               |
| 切后台回前台                     | 不复现                 |
| 杀进程冷启动                     | 不复现（w1.y 未持久化增量）    |
| 仅 Layer2 生效、Layer1 未 block | 可能短暂亮后 g1 灭；冷启动可能复现 |


### 4. worklog 回填

压测完成后在本文件「压测记录」节填写：时间、wxid、三层判定、原始 log 片段。

---

## 压测记录


| 日期               | Layer0b smsg enter | Layer1 v2 block | Layer2 g1 + FMF.E | 红点灭 | 备注             |
| ---------------- | --------------- | --------------- | ----------------- | --- | -------------- |
| 2026-05-21 22:03 | ⏳ v15 未触发 | ⏳ 未触发           | ⏳ FMF 未加载         | —   | v15 方案，ART JIT 不触发子类 hook |
| 2026-05-21 23:01 | ✅ **v17 命中** `SnsMsgUIWithRelevance.onResume` | ❌ 跨进程（:push 写入） | ⏳ 已装未触发 | ✅ 进列表后 badge 变化 | v17 Activity.class 方案有效 |


### 2026-05-21 v17 装机（最终测试）

**环境**：小米9 · 微信 8.0.71 · LSPosed 已勾选


| 步骤                                      | 结果                                                 |
| --------------------------------------- | -------------------------------------------------- |
| `gradlew assembleDebug + adb install`   | ✅ BUILD SUCCESSFUL                                 |
| 模块 init v17                             | ✅ `[MRD] install done (v17)`                       |
| 密友列表                                    | ✅ 2 个：`wxid_toghm7m6uqsr12`, `wxid_lzd2va16jd1622` |
| Layer0b v17 安装日志                        | ✅ `Activity.onResume → SnsMsgUI* filter installed (v17)` |
| Layer1 v2 block                         | ❌ 未命中（写入在 `:push` 进程）                             |
| Layer2 g1                               | ⏳ 已装，FMF 加载后可触发                                    |
| 进「互动列表」（SnsMsgUIWithRelevance）           | ✅ `[MRD:smsg:enter] ...SnsMsgUIWithRelevance.onResume` |
| badge 进列表后状态                            | ✅ 变化（进入消费后归零）                                      |
| 密友互动被过滤（不显示）                            | ✅ filterListFields 运行                               |

**logcat 关键片段（2026-05-21 23:01:47）**：

```
[MRD] install done (v17)
[MRD:smsg] Activity.onResume → SnsMsgUI* filter installed (v17)
[MRD:smsg:enter] com.tencent.mm.plugin.sns.ui.SnsMsgUIWithRelevance.onResume
```

### 关键发现：badge 本地状态机制（2026-05-21 用户实证）

```
badge = 本地 DB 计数 (w1.y)
     ≠ 服务端实时状态

写入时机：收到推送 → :push 进程写 DB (w1.v2)   ← 跨进程，主进程 hook 不可达
清除时机：进互动列表 → 消费 → w1.y 归零          ← Layer0b 入口正是此处
断网/对方删除：不影响已写入的本地计数              ← 红点持久化实证
```

**v1 可接受行为**：
- 进互动列表 → 密友条目不显示 → 退出 → badge 归零 ✅
- 新密友互动到来 → `g1(show=true)` 被拦截（不增新红点）✅（未实测，待密友点赞触发）
- 已存 badge 数字 → 进一次互动列表就消费掉 ✅

### 2026-06-09 P21B 修复：bm live Cursor 过滤

**根因（L1）**：`SnsMsgUIWithAll` 屏幕数据不是 adapter 内部 List，而是 `bm` 父类 `com.tencent.mm.ui.s9.f` 的 `Cursor`（`com.tencent.wcdb.compat.ValueCursor`）。游标列含 `talker`，AA熵 wxid `wxid_lzd2va16jd1622` 位于 `talker` 列；旧 `filterListFields()` 只扫 List 字段，所以 `removed=0`。

**修复**：`MomentsRedDotGuard` 在 `bm/rm` 的 `notifyDataSetChanged` 前包装 live cursor 字段 `s9.f`，复用 `TalkerFilterCursor` 按 `talker ∈ Bridge.getWxids()` 跳过密友行；不自调 notify、不做 View.GONE、不改状态机/授权链。

**验收（L1）**：
- `WithAll/bm`：`[MRD:adapter:flt] notify fired on com.tencent.mm.plugin.sns.ui.bm`
- `WithAll/bm`：`[MRD:cursor:live] notify:com.tencent.mm.plugin.sns.ui.bm.f 10→1`
- `WithAll/bm`：`[MRD:adapter:flt] com.tencent.mm.plugin.sns.ui.bm removed=9 (cursor=9, list=0, before notify)`
- 截图：`logs/p21_after_fix_pass_withall.png`，全部互动消息页仅剩非密友“马万能”，AA熵条目不显示。
- Native 回归：同轮日志有 `BATCH1_VERIFY PASS` 与 `PHASE1A`~`PHASE1E_VERIFY PASS`。

**证据文件**：
- 根因探针：`logs/probe_live_bm_cursor_20260609_logcat.txt`
- 修复验收：`logs/p21b_cursor_fix_verify_20260609.txt`
- 截图：`logs/p21_after_fix_pass_withall.png`

**后续复验**：`rm/SnsMsgUIWithRelevance` 代码同路径覆盖，但本轮无可触发的“与我的互动”入口；待下次有顶部互动气泡时补 L1。该项为复验证据，不阻塞当前 v1 收口。

---

## 观测命令

```powershell
# 端口转发 + 浏览器调试台（推荐）
adb forward tcp:8080 tcp:8080
# 打开 http://localhost:8080 ，点「🔴 红点」过滤 rawfeed

# logcat（标签是 NCL，不是 GhostAssist）
adb logcat -s NCL:I 2>&1 | Select-String "MRD:w1|MRD:g1|MRD:v13|MRD:fmf"
```

浏览器调试台 rawfeed 同步可见 `[MRD:w1:v2]` / `[MRD:g1]` 行。

---

## 校准：私有化阶段补的是桌面角标，不是朋友圈红点（2026-05-22）

**结论**：历史回查 `apk2/_1__B_rewrite/05_docs/PROGRESS.md`（23+ smali 改动），私有化阶段（Catfish 8.0.70 授权替换）补的红点/角标相关漏点是：

- **Issue #23** — 桌面角标未过滤密友未读数：修复 `isVipMode()` 语义陷阱（`hookNewCon` 门控），确保 `sHiddenUnread` 始终被计算
- **Issue #24** — 跨 DEX 注入角标过滤：`classes10.dex` 的 `h0.d(int)` 注入 `MainEntry.kc()`，OEM 桌面角标数字减去密友未读

两处均为**桌面图标角标（会话未读数字）**，非朋友圈/发现 tab 红点。

**Catfish `hookSnsMsgList`**：
- 是原版 APK 自带的 Pine REPLACE 模式 hook（native SO 注册），**非私有化阶段新增**
- 签名 `()Ljava/util/ArrayList;`，返回含密友 wxid 的黑名单 ArrayList
- 目标微信类/方法名未知（需 Ghidra 反编译 `libwechatsd.so` native_start() 0x2349b8）

**Guard 8.0.71 现状**：
- `installCatfishSnsMsgListHook` 扫描 5 类，0 hooks（`[MRD:catfish] snsmsg blacklist hooks=0`）
- 不再回查 Catfish 等价入口，P21 走自研三层方案

---

## 竞品确认（Catfish 甜密友）


| 层   | Catfish 8.0.70                     | Guard 8.0.71 v14b                  |
| --- | ---------------------------------- | ---------------------------------- |
| 数据层 | `hookSnsCommentOne` 清 LikeUserList | `w1.v2` block（arg[1]=wxid）         |
| 视觉层 | SnsObject 清零后 UI 自然不亮              | `FMF.g1(..., false)` + g1(true) 拦截 |


> F-27 仅证 8.0.66 Java parseFrom 零命中；8.0.71 parseFrom 未验证，不得外推。

**当前阻塞**：测试账号朋友圈受限，密友无法点赞 → 无法触发 v2。

**解锁**：换正常账号 / 限制解除 → 密友点赞 → 跑验证清单。

---

## 关键文件

- `src/main/java/com/ghost/assist/moduleD/MomentsRedDotGuard.java` — v14b 实现
- `tools/probe_w1_fields.js` — v2 + arg[1]=wxid 探针
- `tools/probe_catfish_snsmsg.js` — 追 Catfish hookSnsMsgList 等价 ArrayList 织入点
- `FINDINGS.md` — 聚合桶早期记录（已降级，以本 worklog 为准）

