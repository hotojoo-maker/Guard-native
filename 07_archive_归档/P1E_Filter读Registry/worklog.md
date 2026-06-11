# P1E Filter 读 Registry — 收入 SO 设计 worklog

> 开始时间戳：2026-06-09 02:5x（安全与加密官，guard-security-officer）
> 阶段：**设计 / 改前审查阶段**，未动任何业务代码（不违反阶段铁律）。
> 上游：承接 P1C（AES-GCM + encrypted registry + search.gateway 收敛 + P1D-local 派生 key + A-step2 证书绑定，均装机 PASS）。
> 本任务目标：消除「类名双份」——让 Filter 从 SO 解密后的 registry 读类名，删掉 Filter 里的明文硬编码。

---

## 一、背景：为什么要做（现状勘察证据）

### 致命「双份」问题（L2，本轮源码勘察）
- SO 侧：核心类名/字段名已加密进 registry（`native_core/registry_8071.json` → `registry_cipher.inc`，P1C 装机 PASS）。
- Java 侧：**同一批锚点仍明文硬编码在各 Filter**。证据：
  - `ConvFilter.java:58-83`：`MVVMLIST_CLASS`、`ADAPTER_CLASS_71="kc5.v0"`、`WXID_GETTER_NAMES`、`MVVMLIST_HOLDER_FIELDS="f286278p"...` 全明文常量。
  - 与 `registry_8071.json` 的 `conv.list`（`adapter_class:"kc5.v0"` 等）逐字段重复。
- 结论：**当前这层加密暂时什么都没保护**。攻击者根本不用碰 SO，直接反编译 `ConvFilter/SearchFilter/PushFilter` 就看到明文类名。
- worklog 自证：P1C worklog §55 明写「registry **未接管** Filter 过滤逻辑——Filter 仍各自硬编码类名」。

### SO→Java 接口现状（L2，`NativeBridge.java`）
- 现有：`registrySelfTest()`→boolean、`registrySummary()`→一行字符串、`decryptConfig(...)`→整块 JSON（自测用）。
- **缺口**：没有「按 gateway/key 取单条配方」的受控接口。解密后的 registry 留在 SO 内，没有回传 Java 的常规通道。

---

## 二、用户已拍板的决策

| 决策点 | 选择 | 备注 |
|---|---|---|
| 类名怎么从 SO 出来 | **方案 a（字符串查询）** | `nativeGetRecipe("conv.list","adapter_class")` → `"kc5.v0"` |
| 敏感功能收成一类 | 是（来电=独立 gateway） | 对齐 `docs/P22` §九 CallGuard 拆分计划 |
| 本设计落盘 | 是（本文件） | 用户 2026-06-09 明确同意 |
| 授权检查官共审 | **待用户拍板**（问题2-b 未答） | 改「配方注入入口/模块边界」前需共审 |

### 方案 a 的诚实代价（写死，别忘）
- ✅ 改造最小、JNI 不频繁、好调试。
- ⚠️ 运行时 Java 内存会短暂出现明文类名：**反编译看不到（主要威胁已挡），但 Frida dump 内存能拿（高级威胁）**。
- 高级威胁不在这一层解决，靠 Phase 1D-server 短命租约 + 设备/客户绑定 + risk 记录 + 服务端轮换。

---

## 三、设计方案

### 3.1 gateway 分类表
| gateway | registry entry | 对应 Filter | 状态 |
|---|---|---|:--:|
| `privacy.conv` | conv.list ✅ | ConvFilter | 已在 registry |
| `privacy.contact` | contact.address ✅ | ContactFilter / ContactLabelMemberFilter | 已在 registry |
| `trace.moments` | moments.feed ✅ | MomentsFilter | 已在 registry |
| `search.gateway` | 已收敛 ✅ | SearchFilter | 已在 registry |
| **`comm.call`** | 新增 ⚠️ | CallGuard（从 PushFilter 抽） | 待建 |
| **`comm.notify`** | 新增 ⚠️ | PushFilter 消息部分 | 待建 |

### 3.2 来电 `comm.call` entry 草案
把 `docs/P22_PushFilter_VoIP.md` §八 常量速查搬成 registry：
```json
"comm.call": {
  "render_views": "VoIPRenderTextureView,VoIPMPVoIPVideoView",
  "fullscreen_activity": "VideoActivity",
  "float_ball": ".plugin.ball.view.",
  "channel_keywords": "voip,ringtone,call,ring,reminder",
  "call_text_keywords": "通话,通話,call,视频,语音",
  "channel_id": "reminder_channel_id",
  "policy_mmkv": "cnfy",
  "pending_ttl_ms": "120000"
}
```
> 注意：来电现有行为 = **藏掉**来电（拦 UI/铃声/亮屏/浮窗），通话本身不挂断、不拒接。本任务只收类名来源，**不改这个语义**。「真·拒接/挂断」是另立新功能，需重查 hook 点，不在 P1E 范围。

### 3.3 SO→Java 接口（方案 a）
新增（设计草案，未实现）：
- C++：`registry_get_recipe(const char* gateway, const char* key)` → 返回该字段值；registry 散沙时返回空串。
- JNI：`nativeGetRecipe(String gateway, String key)` → String。
- Java 包装：`GuardRuntime.getRecipe(gateway, key)`，内部经 `getActiveRegistry()`（受 LeaseClock + EncryptedConfigLoader + RiskState 门控，决定正常/缓存/降级/空 registry）。

### 3.4 Filter→registry 注入入口
- 统一出口：`GuardRuntime.getActiveRegistry()`（skill 既定命名）。
- Filter install 时取本 gateway 配方，替换硬编码常量。
- **fail-closed**：registry 散沙 → Filter 拿到空类名 → install 跳过（不 hook）→ 无功能但不崩。正好实现 skill「功能失效靠拿不到配方」。

### 3.5 改造顺序（分批降风险）
- **Step 0（本设计）**：方案落盘 + 授权检查官共审。
- **Step 1**：加 `nativeGetRecipe` + `GuardRuntime` 骨架，**不接任何 Filter**，自测出 `PHASE1E_VERIFY PASS`（照 P1A-1D 模式）。
- **Step 2**：SearchFilter 试点（search.gateway 已收敛 + 渲染层 getView，非核心 MvvmList，风险最低）。装机复验行为不变。
- **Step 3**：来电 CallGuard 抽出 + comm.call registry（行为不变，文档 §九 早有计划）。
- **Step 4**：逐个迁移 conv/contact/moments（每个单独装机复验，铁律 29）。
- 每步：旧硬编码常量留作 **fallback**（registry 拿不到时用旧值），稳定后再删。

---

## 四、铁律 / 边界

1. **不动已验证 hook 回调体**（铁律 29 / F-31）：只改「类名来源」，不改过滤逻辑。
2. **阶段铁律**：Step 2+ 属加密阶段（②），需 v1 功能（P22 通知 / §6a / C5 / E3）收口 **或** 用户拍例外，才可动 Filter 代码。
3. **真锁仍是 Phase 1D-server**：本方案是「让加密真正生效」的中间层，**不等于服务器真锁**。对外口径不得宣称「真锁完成」。
4. **需授权检查官共审**：「配方注入入口 / 模块边界」是 `guard-auth-review` 的最高权威，Step 1 动手前必须过它。
5. 方案 a 内存明文是已知接受代价，记录在案，不得当作「已彻底加密」对外宣称。

---

## 五、授权检查官 × 安全官 改前共审报告（2026-06-09，设计阶段）

> 触发：P1E 设计「让 Filter 从 SO registry 读类名 + GuardRuntime 注入入口 + comm.call gateway」。
> 涉及保护区：`NativeBridge.java`（新增 nativeGetRecipe）、新建 GuardRuntime、各 Filter（类名来源）。

### 必答 9 项
1. 乱接状态机？**否** — Filter 不写状态机，GuardRuntime 不切态，recipe 只读。
2. 混淆口令与授权？**否** — search.gateway 的 `unlock_entry` 只是标记；SearchUnlock(111111) 显式分离（P1C 已证）。
3. 未授权越权？**否** — 反而加强：registry 散沙 → fail-closed → 不 hook。
4. 绕过 AuthGate？**否** — `getActiveRegistry()` 受 LeaseClock/RiskState 门控。
5. 破坏 SearchUnlock→sm→RefreshBus→Filter 链？**否**（⚠️ 条件：SearchFilter 试点不得碰 SearchUnlock）。
6. Java/C++ 分层？**⚠️ 关键** — recipe=配置走冷路径(install)可接受；但 Filter 隐藏决策**仍须走 `isActive()`+`isHiddenWxid`**，不得因 registry 让 Filter 读 C++ 态做过滤（铁律30）。
7. 保护区未审先改？**否** — 本次即改前审；NativeBridge 新增需本审通过 + 改后再审。
8. 模块边界？**⚠️** — Filter 新增对 GuardRuntime 依赖。需 (a) recipe-fetch 只读 config 不含 state/auth；(b) GuardRuntime 新独立模块不混 StateMachine/AuthManager；(c) 旧常量留 fallback。**拆代码：是** — CallGuard 从 PushFilter(~1000行)抽出，命中拆代码触发条件1 + 文档§九。
9. 最小修复建议：见下边界条件。

### 门控状态
- EntryGate ✅　AuthGate ✅　StateGate ✅　**RiskGate ⚠️**（registry散沙→Filter skip 是 RiskGate 新生效路径，语义一致但须单一出口）　**分层合规 ⚠️**（冷路径 recipe OK，热路径隐藏决策仍须 isActive）

### 最终结论：**WARN**（设计方向通过，实施前必须落实 5 条边界）
1. recipe-fetch 只读 config，**不得**在 Filter 里加 state/auth/risk 判断（门控只在 GuardRuntime 内部，Filter 只拿空/非空）。
2. Filter 隐藏决策**仍走 `isActive()`+`isHiddenWxid`**；recipe 只决定「hook 哪个类」，不决定「藏不藏」。
3. 旧硬编码常量**保留为 fallback**，registry 拿不到用旧值，不回归；稳定后再删。
4. NativeBridge 新 native 方法保持 **JNI 静态名绑定 + proguard keep + isAvailable() 前置**。
5. GuardRuntime 独立成模块，**不得内嵌 AuthGate/StateGate 逻辑**；Step 1 只加骨架不接 Filter，Step 2+ 逐个装机复验。

> 改后必须再走一轮「授权检查官改后审查」（验证未新增越权）。

---

## 六、当前状态 / 待办

- [x] 现状勘察（ConvFilter 硬编码 / NativeBridge 接口缺口 / registry 结构）— L2 完成。
- [x] 设计方案 v0.1 落盘（本文件）。
- [x] 授权检查官改前共审 — **WARN，5 条边界条件**（见 §五）。
- [ ] Step 1 实现（`nativeGetRecipe` + GuardRuntime 骨架）— **待 v1 收口 / 用户拍例外**。
- [ ] Step 1 改后审查（授权检查官）。

> （§五/§六为设计 + 改前审查阶段存档。以下 §七 起为实施记录。）

---

## 七、实施记录（2026-06-09，Step1 → Step2-ContactFilter）

> 所有结论均有 L1 装机 logcat 背书，日志落盘在本任务 `logs/`。多窗口并行（执行窗口写码 / 本安全官窗口审码 + 装机取证）。

### Step1 — 加密「取件口」骨架（不接 Filter）✅
- 新增 SO→Java 配方查询通道，**不接任何 Filter**：
  - `registry_loader.cpp` `registry_get_recipe(gateway,key)`：解密 registry 查字段；散沙/未知项 → 空串（fail-closed）。
  - `guard_core.cpp` + `guard_core.h` `nativeGetRecipe` 只读 JNI（静态名绑定，proguard 整类 keep 覆盖）。
  - `NativeBridge.java` `getRecipe()`（isAvailable 前置，null→""）。
  - 新建 `GuardRuntime.java`：配方注入**单一出口**骨架（`isRegistryActive`/`getRecipe`，fail-closed，不碰 StateMachine/AuthManager）。
  - `ModuleMain.java` 加 `PHASE1E_VERIFY` 自测。
- 验证（pid 23776/27332）：`recipeGet conv.list/adapter_class=kc5.v0`；`PHASE1E_VERIFY PASS`；BATCH1/1A-1D 不回归。
- 代码审查（本窗口读 5 文件）：5 条边界全守，PASS。lint 无错。

### 插曲 — Step2a 误判 + 回滚 + ss4.p 死代码清理
- **Step2a 误判（执行窗口，已回滚）**：被 `SearchFilter.java` 一句 2026-05-27 的过期注释「TRUE PRIMARY: ss4.p」误导，往 `registry_8071.json` 加了 `primary_adapter=ss4.p`/`primary_render_hook=onBindViewHolder`/`fts_result_item=z15.ef6`/`contact_result_item=fz2.e`，并改了 `registry_loader.cpp`/`ModuleMain.java` 自测去校验这批死/证伪锚点（把废弃路径标成主锚点）。
- **装机实证纠偏（本窗口）**：清日志 → 隐藏态搜密友 → `[SF:gv] blocked` ×23/29（f0.getView 干活），`[SF:ss4p]` **0 次**（ss4.p 父容器 GONE，onBindViewHolder 永不触发）。证据 `logs/search_anchor_verify_20260609.log`。→ **registry 的 q2/f0/getView 本来就对**，方案①（扩 ss4.p）作废。
- **探查代理彻查**：ss4.p = FTSMainUI 挂在 GONE 父容器下的 RecyclerView Adapter，僵尸 hook；05-27 02:23 误锚、04:00 已被 F-32x 推翻。
- **回滚（执行窗口，本窗口核验）**：删 `registry_8071.json` 那 4 行 + `registry_loader.cpp:335-338` 自测 + `ModuleMain.java` primary_adapter 校验，重生 cipher。核验：grep 4 文件污染锚点零命中；logcat（pid 27332）registrySummary 4 entries 无 ss4.p、PHASE1A-1E PASS。证据 `logs/phase1e_step2a_rollback_20260609.log`。
- **ss4.p 代码删除（代理删 + 本窗口审）**：`SearchFilter.java` 删 ss4.p 字段/hook 整块/过期注释 + 专用 `resolveAdapterItem`；`dumpQ2DataItem`（q2 共用）保留。`assembleDebug` BUILD SUCCESSFUL，lint 无错。装机复验（pid 29280）：`[SF:gv]` ×29 仍命中、`[SF:ss4p]` 0、无崩溃（日志里 `media.extractor` SIGABRT 是系统进程 uid 1040，与本改动无关，已查实）。
- **文档债已修**：`HOOK_MAP_8071_AUTHORITATIVE.md` §8b ss4.p 行改为「2026-06-09 已删除」+ 遗留代码行去掉 ss4.p；`P20/result.md` 去掉对 FAILURE_LOG 的错误引用。

### Step2-ContactFilter — 第一个真迁移（Filter 读 registry）✅
- 改前共审（授权检查官 9 项）PASS（范围收窄到 ContactFilter）。
- `ContactFilter.java`：5 锚点 + c1 getter 改从 `GuardRuntime.getRecipe("contact.address",*)` 读，旧字面量留 fallback；常量 final→非 final；`resolveRecipes()` 在 install 最前调（幂等 + fallback 自证）。`MVVMLIST_FIELDS{o,p,h}` 不在 registry，本轮保持硬编码（范围外）。
- **不改过滤逻辑**：隐藏决策仍走 `isActive()+isHiddenWxid`，recipe 只决定「hook 哪个类」。
- 改后审（授权检查官 9 项）PASS。
- 代码审查（本窗口读磁盘）：改动干净，5 边界守住。
- 装机验收（pid 30879/32355）：`[CTF] recipes adapter=ik3.t0 ... fallbackSelfTest=ok`（值=旧字面量，行为不变）；隐藏态进通讯录 `[CTF:addAll] removed=1/30` + `[CDH:found] owner=ik3.t0`（registry 解出的 ik3.t0 生效，密友藏住）；PHASE1A-1E 不回归；无崩溃。证据 `logs/phase1e_step2_contactfilter_20260609.log`。

### Step3-MomentsFilter — 第二个真迁移（朋友圈过滤读 registry）✅
- **先对账（吸取 ss4.p 教训）**：逐个核 registry moments.feed vs 代码实际活跃锚点。
  - ✅ 活跃且对得上（迁 8 个）：`ITEM_FRIEND na4.b→item_friend`、`ITEM_PROMO la4.p→item_promo`（195 loadClass）、`ADAPTER_CLASS e2→adapter_class`（268 loadClass）、`FIELD_WXID field_userName→wxid_field`、`FIELD_INNER d→inner_field`、`METHOD_SNS_OBJ h1→sns_getter`、`FIELD_LIKE_LIST LikeUserList→like_list`、`FIELD_COMMENT_LIST CommentUserList→comment_list`。
  - ⚠️ registry 有但代码对不上 → **本轮不迁**：`actor_class z15.e56`（MomentsFilter 无此类锚点，靠遍历 LinkedList<e56>+ACTOR_FIELD_NAMES 取 wxid，不 hook 该类）；`actor_wxid_field f435583d`（对应 `FIELD_E56_WXID` 是死常量，全程未引用）。
- 改前共审（授权检查官 9 项）PASS（范围=8 活跃锚点）。
- `MomentsFilter.java`：8 锚点改从 `GuardRuntime.getRecipe("moments.feed",*)` 读，旧字面量留 fallback；常量 final→非 final；`resolveRecipes()` 在 install 最前调（幂等 + fallback 自证）。**不动 addAll 拦截 / remove 回调体逻辑（铁律 28/29）**，只换类名来源。
- 改后审（授权检查官 9 项）PASS。
- 装机验收（pid 4266，14:43:07）：`[MF] recipes friend=na4.b promo=la4.p adapter=e2 wxid=field_userName inner=d sns=h1 like=LikeUserList cmt=CommentUserList fallbackSelfTest=ok`（值=旧字面量，行为不变）；PHASE1A-1E 不回归；`[CTF] recipes ... ok`（ContactFilter 同时仍正常）；UI 验收朋友圈密友帖仍屏（用户确认）。证据 `logs/phase1e_step3_momentsfilter_20260609.log`。

### Step4-ConvFilter — 第三个真迁移（会话列表，最谨慎）✅
- **先对账（吸取 ss4.p 教训）**：核 registry conv.list vs 代码实际，关键原则=**只迁「具名常量/数组」（改声明+resolve，不碰回调体）；内联字面量不动**。
  - ✅ 安全可迁（3 个）：`MVVMLIST_CLASS→mvvmlist_class`（loadClass 197/327/431）、`ADAPTER_CLASS_71 kc5.v0→adapter_class`（66/H0 不在 registry 保持硬编码）、`WXID_GETTER_NAMES→wxid_getters`（C0,h1,j1,i1,k1,getUsername,getUserName 完全一致）。
  - ⚠️ 不迁（报告用户、本轮跳过）：`item_class kc5.y` / `l4_notify notifyDataSetChanged`（内联字面量在 hook 回调体里，迁要改回调体 → 撞雷区 13-22）；`contact_class l4`（无 live 锚点）。
- 改前共审（授权检查官 9 项）PASS（范围=3 安全具名锚点）。
- `ConvFilter.java`：3 锚点改从 `GuardRuntime.getRecipe("conv.list",*)` 读，旧值留 fallback；`final→非 final`（`ADAPTER_CLASS_71` 被 `ConvHotReload:580` 跨文件读，保持 package 可见）；`resolveRecipes()` 在 install 最前调。**未碰 L1/L2/L4 回调体、clean-before、notifyDataSetChanged、H↔V 热刷新（铁律 13-22）**。
- 改后审（授权检查官 9 项）PASS。
- 装机验收（pid 8352，14:56:41）：`[CF] recipes mvvm=...MvvmList adapter=kc5.v0 getters=[C0,h1,j1,i1,k1,getUsername,getUserName] fallbackSelfTest=ok`（值=旧字面量，行为不变）；PHASE1A-1E 不回归；MF/CTF recipes 同时正常；**无崩溃**（com.tencent.mm pid 8352 存活；日志中 SIGABRT 是系统进程 `media.extractor` pid 5711，与本改动无关）；UI 验收会话列表密友隐藏 + 111111 解锁恢复（H↔V 双向，用户确认）。证据 `logs/phase1e_step4_convfilter_20260609.log`。

### registry conv.list 漂移债 — 已核准（2026-06-09 核账结案）
- **contact_fields**：~~registry `d,e` 是代码子集~~ → **已补全** registry `d,e` → `d,e,f,a,b,c`（= 代码 `CONTACT_FIELD_NAMES` + HOOK_MAP §178 kc5.y.d 主 + 群兜底 f/a/b/c）。重生 cipher（pt 1330），装机 PHASE1A-1E PASS、registrySummary entries=4。证据 `logs/phase1e_conv_drift_reconcile_20260609.log`。
- **l1_methods**：**核准=本就正确，无漂移（误报撤销）**。代码 `installMvvmListHooks:379` hook `n`+`m`；HOOK_MAP §174 `L1 主路径=MvvmList.n(List,boolean)`（8.0.66 是 .m）→ n 主 m 旧都 hook。之前疑漂移的 `w/e`（ConvFilter:306）实为 `IK3n.handleEvent→MvvmList.w/e` 链路叙述 + L0w 写入路径（hook e/k/l），**非 L1**。registry `n,m` 不变。
- **l2_method**：`s` 核准正确（`installMvvmListHooks:432` hook 单参 `s`）。

### P_SEC1 + SearchFilter 粗粒度 registry 壳 — 装机通过（2026-06-09）✅
- 新增 `EncryptedConfigLoader`，`GuardRuntime` 改为统一走 loader 判断 registry 是否 ready；不接服务器心跳，不宣称真锁完成。
- `SearchFilter.java` 只接 `search.gateway` 粗粒度壳：`gateway / adapter_family / render_hook / extractor_profile / scope`；旧值 `q2,f0 / getView / wechat8071_fts_mixed` 保留 fallback。
- **未改 SearchUnlock**：`111111` 入口仍走原 `beginUnlock()` / `attemptUnlock()`；registry 注释明确排除 `SearchUnlock`。
- **未改隐藏判断**：搜索过滤仍走 `StateMachine.isActive()` + hidden set；recipe 只决定“hook 哪个 adapter family / 方法名”。
- 装机信息：设备 `609b4b18`，微信 8.0.71 `versionCode=3080`，主进程 pid `24801`，`:push` pid `25004`。
- 关键 L1 日志：
  ```text
  [native] configReady=true summary=schema=r8071_v1 ver=8.0.71 entries=4 ... [search.gateway]
  [native] PHASE1E_VERIFY PASS
  [SF] recipes gateway=fts_result_view adapterFamily=q2,f0 renderHook=getView profile=wechat8071_fts_mixed scope=result_render_only fallbackSelfTest=ok
  [SF:q2] setAdapter q2 detected adapterCls=com.tencent.mm.plugin.fts.ui.q2
  [SF:gv] single-hook getView filter installed on com.tencent.mm.plugin.fts.ui.f0
  [SF:gv] blocked pos=1 id=wxid_lzd2va16jd1622
  ```
- 异常核查：日志中 `SIGABRT` 属系统进程 `media.extractor`（pid `18180`），非微信主进程；微信主进程存活，不判模块崩溃。
- 证据：
  - `logs/psec1_search_registry_install_20260609.log`
  - `logs/psec1_search_registry_runtime_20260609.log`

### 当前状态 / 诚实边界
- ✅ 取件口通 + 三个 Filter（ContactFilter / MomentsFilter / ConvFilter）真从 registry 读类名且装机验过；SearchFilter 粗粒度 `search.gateway` 壳也已装机命中 → 加密最初一步真走通。
- ⚠️ 现为「registry + 旧常量 fallback 双份」，明文双份**未完全消除**；删 fallback 是后续步（需多跑几天稳定后再删，防回归）。
- ✅ conv.list 漂移债已核准结案（contact_fields 补全为 d,e,f,a,b,c；l1_methods n,m 确认正确）。
- ⬜ 待迁：ConvFilter 剩余内联锚点（kc5.y/notify，在回调体内，撞雷区暂缓）；SearchFilter 目前只做粗粒度 profile 壳，不删 fallback。
- ⬜ 真锁（服务器短命钥匙）= Phase 1D-server，仍未做，对外不得宣称真锁完成。

---

## 2026-06-11 加密链路维护护栏（host/CI parity 测试，非新功能）

> 背景：体检加密「是否方便维护」时发现——`derive_registry_key()` 在 Python(`gen_registry_cipher.py`) 与 C++(`config_crypto.cpp`) 双实现、必须 byte-for-byte 一致；漂移会静默散沙（隐私 hook 无声失效）。原本只有装机 logcat `PHASE1C/1D_VERIFY` 才暴露，太晚。

### 改了什么（范围严格限定）
- 只动 `tools/test_config_crypto.cpp`：跑 `decrypt_config_self_test()` 之外，新增
  - `set_binding_material(证书 SHA-256 32 字节)`，与 `gen_registry_cipher.py::_CERT_SHA256` 逐字一致（cert-only 构建，`clear_server_seed` 不折服务器种子）；
  - `registry_self_test()` + `registry_dump_summary()`；退出码同时看两个 self_test。
- 新增 `tools/run_native_tests.ps1`：一键编译+跑（host clang/g++ 优先=CI 友好；无 host 编译器则 NDK clang 交叉编译→adb push→设备跑，非装 APK、非 logcat）。
- **未动** SO 主逻辑 / hook / registry 内容 / `gen_registry_cipher.py` 配方。

### 价值
- 把「Python 生成密文 ↔ C++ `derive_registry_key` 解密」的跨语言 key 一致性，从「装机才知道」提前到「编译/CI 就知道」。任一端改漂移 → `registry_self_test` 当场 FAIL。

### L1 证据（真机跑，设备 609b4b18 / arm64-v8a，NDK→adb 模式）
```text
[run_native_tests] device abi=arm64-v8a target=aarch64-linux-android21
decrypt_config_self_test=PASS
registry_self_test=PASS
registry_summary=schema=r8071_v1 ver=8.0.71 entries=4 [conv.list adapter=kc5.v0 l4=notifyDataSetChanged] [moments.feed adapter=e2] [contact.address adapter=ik3.t0] [search.gateway]
ALL=PASS
[run_native_tests] RESULT: PASS
```

### 诚实边界
- 这是**维护护栏**（防漂移 + 可离线/CI 自测），不改变安全等级：仍是本地加密 + cert 绑定，**不宣称服务器真锁完成**（Phase 1D-server 仍 dormant）。
- 临时产物（`.tmp_native_test.elf` / 设备 `/data/local/tmp` 二进制）由脚本自清。
