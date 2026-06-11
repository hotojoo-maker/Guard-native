# PROTECTION_MAP — 上线前防破解总账 / 路线图

> **定位**：这是**上线前的防护门控文档**。每个阶段发版前，对照 §9 的清单逐条打勾，全绿才上线。
> **维护规矩**：防破解相关只记在这一份，别再散到别处；改相关代码前先看本文。
> **证据基线**：2026-06-02 代码实测（只读核查，见 §附录 A），标 L2 = 静态已证实。
> **关联**：`CLAUDE.md`（29 条铁律）· `docs/GUARD_GATE_TRUTH.md`（门控权威）· `docs/PRODUCT_GATE.md`（四层模型）· `DECISION_LOG.md` D-013（危险通告/kill switch）· `RISK_REGISTER.md` · `docs/DEBUG_CONSOLE_V2.md`（防护驾驶舱）
> **当前进度（2026-06-02）**：**Phase 0 完成 ✅** —— 仪表盘（防护驾驶舱 + `/api/native` + `tools/guard_status`）+ ① DebugServer DEV-gate + ② proguard 收窄（release `BATCH1_VERIFY PASS` + mapping 实锤：NativeBridge 保留、过滤器混淆、诱饵留亮）+ ③ 接 `AuthManager.evaluate()`+`PiracyNotice`（装机 `evaluate=NO_LICENSE`，v1 放行不变）。**下一步 Phase 1**（真锁+心跳，接 miyou-server）。
> **2026-06-11 更新**：Phase 1D-server（S2 服务器真锁）已解冻，建出**出站/信封/心跳骨架（dormant，未接入主流程）**；现状 + 下一受控步骤见 **§10.6**。

---

## 0. 胜利标准（北极星，先记死）

1. **让破解成本 > 软件价值** —— 不是「做到不可破」，是「他研究透还不如自己做」。
2. **简单也是成本** —— 每种手段**只埋一处**，层层设防，不把自己绕死（不维护两份代码）。
3. **只打破解的，绝不误伤付费客户** —— 强制弹窗 / 自毁只针对「确认被撬 / 宽限期真耗尽」。
4. **核心心智**：假设静态包一定被反编译；把「价值」搬到 **服务器裁决 + SO 解密 + 每客户短命钥匙**，而不是藏在客户端代码里。

---

## 1. 现状体检（证据基线 · L2）

| 能力 | 现状 | 证据（file:line） |
|---|---|---|
| 真授权 | **双层假锁** + 你写好的绑定逻辑是**死代码** | `StateMachine.java:92` `isVipAuthorized(){return true;}`；`guard_core.cpp:90` JNI `return JNI_TRUE`；`AuthManager.evaluate()` 全仓库**无人调用** |
| 中央网关 | 所有过滤器funnel到这里 | `StateMachine.java:86` `isActive()=isVipAuthorized()&&isFeatureEnabled()&&mActive` |
| hook 配方 | **明文**散在源码 | `MomentsFilter`(`na4.b`,`la4.p`)、`ConvFilter`(`kc5.v0`)、`ContactFilter`(`fc5.g`)、`SearchFilter`(`z15.ef6`) 等全是 `static final String` |
| 服务器/心跳 | **完全没有** | 全 Java/native 无任何 HTTP/Socket；只有 `AppConfig.java:36` `SHOP_URL` 常量 |
| SO 加密 | **无任何 crypto** | `auth_engine.cpp` 只存状态；AES/HMAC 全是 TODO 注释 |
| DebugServer | **release 永远开 8080** | `ModuleMain.java:165` `DebugServer.start()` 无开关；对比浮窗 `:172` 才有 `isDebugEnabled()` 包裹 |
| 混淆 | **等于没做** | `proguard-rules.pro:4` `-keep class com.ghost.assist.core.** { *; }` 把网关/授权/存储全保住 |
| 持久化 | 实为 SharedPreferences（非 MMKV API） | `Bridge` 命名空间 `g_a7f2`；密友 `hlst` / 密群 `glst`；状态 `smst` / 密码 `smpw` |
| 漏斗零件 | **已有，未接线** | `NativeBridge.java:78` `GRACE_WARN=24h/DEGRADE=72h/LOCKOUT=10天`；`PiracyNotice`（无人调用）；`OverlayWindow`；`SHOP_URL=zxmqq.shop` |

**一句话**：地基（真授权 + 服务器）还没有，城墙（混淆）开着大门，但漏斗零件大多现成。

> ⚠️ **2026-06-11 更新**：本表「服务器/心跳 = 完全没有」一行已过时——`net/` 下 S2 出站/信封/心跳骨架已建（**dormant，未接入主流程**）。本表保留 2026-06-02 基线快照不改；服务器侧现状以 **§10.6** 为准。

---

## 2. 组合拳总览（一假一真 + 服务器为锚）

- **门卫 → 翻译官**：SO 别只回答「放行/不放行」（假门卫一换就破）；要当「唯一能把服务器密信解成真配方的翻译官」（假翻译官没密码本，解出来是乱码）。
- **真锁 = 能否解开配方**（无名的 `decrypt_config`）；**假锁 = 诱饵**（留亮的 `isVipAuthorized`）。
- **服务器是唯一改不了的锚**：客户端整个在对方手里（连运行时 hook SO 都做得到），所有硬防线最终锚在服务器。

---

## 3. 四阶段路线图

| 阶段 | 干什么 | 需要 | 成本/风险 | 蜜罐 & 弹窗 |
|---|---|---|---|---|
| **0 止血+摆诱饵** | 关 release DebugServer；收 proguard（混真留假）；把现成死代码 `AuthManager.evaluate()`+`PiracyNotice` 接上 | 纯本地改，不用服务器 | 低、零风险 | 摆好诱饵；接基础「已篡改」弹窗 |
| **1 地基:真锁+心跳** | SO 加 `decrypt_config()`(AES-GCM)；miyou-server 出心跳端点，发短命钥匙+加密配置（租约字段伪装 `K2i_m`）；解不开=散沙 | 写服务器 + SO 加密 | 中 | 弹窗按 GRACE 阶梯（24h提醒/72h降级） |
| **2 补组合拳+漏斗** | 加密配方（类名服务器下发、随版本）；蜜罐绊线→tampered→后果；水印溯源；防重放 | 接 Phase1 服务器 | 中 | 绊线后果上线；强制弹窗（10天 lockout）引流 `zxmqq.shop` |
| **3 打磨** | 更多决策下沉 SO；控制流混淆；服务器查「一码多机」 | — | 高、选做 | — |

> 蜜罐：诱饵 **Phase 0** 就摆（便宜），「牙」（绊线后果）等 **Phase 2**（真锁就位才有意义）。
> 弹窗：基础 Phase 0、分级 Phase 1、强制引流 Phase 2。

---

## 4. 关节表（每个关节只埋一处）

| # | 关节（位置） | 现状 | 埋哪一招 | 加密? | 维护备注 |
|---|---|---|---|:--:|---|
| 1 | 中央网关 `StateMachine.isVipAuthorized()` `:92` | `return true` + `AuthManager.evaluate()` 死代码 | ① 真授权：接 evaluate() 进 `ModuleMain`，判断下沉 SO | 逻辑进SO | 所有过滤器都走这里，改一处全生效 |
| 2 | 服务器心跳（现无） | 全代码无 server 调用 | ② 心跳续租 + 下发短命钥匙 | 通道加密 | 全新独立模块，不碰现有 hook |
| 3 | 配方存储 SharedPreferences `g_a7f2` | hook 类名明文；`hlst`/`glst` 明文 | ③ 核心 3~5 个类名抽成 registry，服务器下发 | 是 | 只抽核心几个，**验证过的别全搬** |
| 4 | SO 翻译官 `auth_engine.cpp` / 新 `decrypt_config()` | SO 零 crypto | ④ AES-GCM 解密，唯一能产出真配方 | 是（核心） | 新函数，不动现有 native 逻辑 |
| 5 | 失败表现 `StateMachine.isActive()` → `Bridge.allHiddenIds()` | 现 stub true | ⑤ 解不开/过期→喂乱码=散沙，不崩不全开 | — | 半残+重连自愈，**别清用户名单** |
| 6 | 延迟炸弹+引流 `PiracyNotice`（死代码） | 写好没人调 | ⑥ risk 触发→延迟弹窗引流 | — | 1~2 个陷阱够了，别遍地埋 |
| 7 | 水印溯源 seed（`g_a7f2` 已有 seed） | 部分 | ⑦ seed 派生指纹写进 1 个常量 | — | 零检测代价，泄漏可追 |

---

## 5. 蜜罐（一假一真，配对设计）

| 角色 | 落点 | 处理 |
|---|---|---|
| **假锁 / 诱饵** | 现有 `isVipAuthorized()`（明文 true，名字像锁） | 留明文、留亮名（**故意不混淆**）；加绊线 → 设 `tampered` 旗标 |
| **真锁** | 新增 SO `decrypt_config()`（无 vip/auth/license 关键词） | 重混淆 / 干脆无名；解不开 = 散沙 |
| **后果** | `PiracyNotice` + 水印 + (v2)上报 | 咬钩后**延迟**触发，不是当场 |

- 诱饵让关键词党（搜 `is vip`）秒命中、上钩；真锁同一搜索**搜不到**。
- **诚实边界**：蜜罐抓静态/关键词党（90%）；动态高手翻了诱饵发现没反应会去找真锁 —— 挡他靠服务器+SO+短命钥匙。绊线本身可被删，**最稳当「标记+溯源」用**。
- **两种党两个蜜罐**（关键词党 vs 抓包党）的现状与待办 → 见 §10.4。

---

## 6. 弹窗漏斗阶梯（按代码现成的 GRACE 常量）

| 阶段 | 触发 | 表现 |
|---|---|---|
| 提醒 | 断网 / 验证失败 ≥ 24h（`GRACE_WARN_HOURS`） | 轻提示，可关 |
| 降级 | ≥ 72h（`GRACE_DEGRADE_HOURS`） | 偷偷散沙（他以为是 bug），轻提示 |
| 锁定 | 宽限耗尽 10 天（`GRACE_LOCKOUT_DAYS`）或绊线确认被撬 | **强制弹窗（关不掉）→ `zxmqq.shop`** |

**两条铁律级注意**：
1. **强制弹窗只砸「确认破解/过期」的人，绝不砸付费客户**（付费客户离线只走前两级、可恢复）。
2. **让降级去说服，弹窗只给「去哪买」**：散沙负责让他想买，弹窗负责告诉他在哪买。纯弹窗不降级 → 他直接卸载，漏斗断。
3. 关不掉的窗用现成 `OverlayWindow`(WindowManager)/`PiracyNotice`(对话框)，**别用 Service**（铁律 24）。

---

## 7. 通道伪装（K2i_m）

- **伪装 ≠ 加密**：`K2i_m` 看不懂只是减速带；内容**必须真加密+签名**（AES-GCM + HMAC）。伪装盖在真加密上面，不替代它。
- **伪装你自己的通道**，不碰微信真流量（碰微信网络层违铁律，风险另算）。
- **三层叠**：HTTPS(运输) + 内容真加密签名 + 字段伪装(锦上添花)。`K2i_m` 在「他自己装证书 MITM 看自己手机」时才更顶用。

---

## 8. 维护规矩（防止自己把自己套死）

1. **一份源码**，绝不拷两份（两份必然漂移，F-31 教训）。
2. **配方抽成一个 registry 文件**：核心 hook 类名集中一处，引擎代码保持干净；只有这个 registry 走「服务器下发 + 加密」。
3. **本文 = 唯一总账**：谁加密、谁不加密、为什么，全记这里；改代码先看本文。
4. **关节处加注释标记** `// [GUARD-TRAP] 别动/别优化，见 PROTECTION_MAP` —— 专治顺手优化掉陷阱。
5. **不动已验证 hook**（铁律 29 / F-31）：防护只加在「网关 + 配方 + 钥匙」层，不进已跑通的 hook 回调体。
6. **红线**：不为加固去 hook/dlopen 微信自身 SO（铁律 23）；不主动反调试/读 `ro.boot.*`/`getRunningAppProcesses`（铁律 5/7）；先过反检测 KPI（`verifiedbootstate ≤ 38` 等）。

---

## 9. 上线前清单（每阶段发版门控 —— 全绿才上线）

### Phase 0（止血+摆诱饵）✅ 2026-06-02 完成
- [x] DebugServer 门控：`ModuleMain` 改 `if (BuildConfig.DEBUG || AppConfig.isDebugEnabled())`（debug 包/HONEY 才开；release+PROD 不开）—— debug 装机验证面板正常；release+PROD 关闭为逻辑保证（未单独切 PROD 实测）
- [x] `proguard-rules.pro` 收窄：mapping 实锤 `NativeBridge→NativeBridge`（保留，JNI 安全）、`StateMachine→b.j`/`ConvFilter→f.m`/`MomentsFilter→f.r`（混淆）、`isVipAuthorized` 保留可读
- [~] 诱饵：`isVipAuthorized` 经 proguard `-keepclassmembernames` 留亮 ✅；代码内 `// [GUARD-TRAP]` 注释标记 ⬜ 待补
- [x] 接死代码：`AuthManager.evaluate()` 接入 `ModuleMain` 第 6.5 步（record-only）；`PiracyNotice` 已挂（仅 AUTH_TAMPERED 触发）—— 装机日志 `[auth] evaluate=4 NO_LICENSE`
- [x] 回归：release 装机 `BATCH1_VERIFY PASS` + 密友隐藏正常；debug 接死代码后密友隐藏正常（用户确认）
- ⬜ 遗留（发版前）：release 签名链路（当前用 debug keystore 临时签）；release+PROD 关面板的设备实测

### Phase 1（地基:真锁+心跳）

> **Phase 1 本地预制进度（2026-06-08，详见 `07_archive_归档/P1C_Registry加密/worklog.md`）**：
> - [x] **P1A** SO `decrypt_config()` AES-GCM + 自测向量（装机 `PHASE1A_VERIFY PASS`）
> - [x] **P1B/1C** 核心 4 条 hook registry 抽取 + AES-GCM 加密（单一源 `registry_8071.json` → `registry_cipher.inc`；装机 `PHASE1B/1C_VERIFY PASS`）
> - [x] **A-step1** 派生 key（去明文 key 常量，SO 内多段散装 + nonce 随机）
> - [x] **A-step2** registry key 折入模块签名证书 SHA-256（防重打包；装机 `certBind=ca421ec3` + `PHASE1D_VERIFY PASS`）
> - ⚠️ 仍是**本地锁非真锁**：Frida hook `decrypt_config` 出参仍可拿 registry；服务器短命钥匙/设备绑定 = 下面 miyou-server 项。
> - V3 形态：cert 绑定证书源需从模块 APK 改为读宿主自身签名（见 DECISION_LOG D-016）。

- [x] SO `decrypt_config()`（AES-GCM）实现 + 自测向量 —— P1A 装机 PASS（见上）
- [~] miyou-server 心跳端点：发短命钥匙 + 加密配置；字段伪装 `K2i_m` —— **2026-06-11 出站/信封/心跳骨架已建（dormant，未接入主流程），见 §10.6**；短命钥匙折入 SO key（S3a）/ Ed25519 验签（S4）/ 服务器端在线 仍未做
- [ ] `isActive()` 依赖「配方解开成功」；解不开 = 散沙（不崩、不全开）
- [ ] **离线宽限实测**：拔网后正版在宽限期内正常；超期才降级；重连自愈
- [ ] **付费客户不误伤实测**：飞行模式 24/72h 内不出现关不掉弹窗
- [ ] KPI：装 SO 前后跑 `frida_stats`，对比 `verifiedbootstate` 等无超红线

### Phase 2（补组合拳+漏斗）
- [ ] 核心类名改为服务器下发的加密配方（随微信版本）
- [ ] 蜜罐绊线 → `tampered` → 延迟散沙 + 水印
- [ ] 防重放：心跳/零件带一次性 nonce + 签名时间戳
- [ ] 水印溯源：seed 派生指纹可从泄漏样本反查客户
- [ ] 强制弹窗（10天 lockout）→ `zxmqq.shop`，且**只对确认破解/过期**触发
- [ ] 旧版本破解实测：微信升级后旧配方失效 → 自动散沙

### Phase 3（打磨，选做）
- [ ] 更多决策下沉 SO；控制流混淆
- [ ] 服务器侧「一码多机」异常检测

---

## 9b. 业务敏感功能上线前门控（按功能编号 · 每加一个敏感功能补一行）

> 这里登记**业务侧敏感功能**（区别于 §9 的防破解阶段门控）：监管 / 合规 / 风控 / 法律红线相关，必须在该功能 release 包发版前逐条打勾。
> 加新行触发条件：dispatch 派活前自检 3 问命中"碰金融数据 / 伪造身份 / 绕过安全提示"任一时。

### E2 伪装订位（伪造定位）✅ 2026-06-07 装机
- [x] 装机验证通过（4 场景全局生效 + 关闭复原 + 不发真聊天）
- [x] 默认关闭（用户必须主动开开关 + 主动选点才生效）
- [x] 关闭即复原（无残留 hook，next 定位回真实坐标）
- [x] 选点页内部误发兜底（talker=filehelper，最坏情况只进自己的文件传输助手）
- [ ] **release proguard 后 `FakeLocation` 类名 / `flon/flla/flln/fllb` 键名混淆**（增加破解成本；当前是 debug 明文）
- [ ] **kill switch**（远程关闭通道，对齐 D-013 危险通告；当前无）→ 待 Phase 1 miyou-server 上线后接入
- [ ] **风险通告**：发版包内置首次开启时一次性弹窗，告知用户"伪造定位用于自身娱乐，不得用于诈骗/欺骗他人，否则法律责任自负"（当前无）
- [ ] **不在严格合规场景包内启用**（如：政企版 / 海外版需硬关）→ 按 build flavor 区分

### E3 修改余额 UI 层（**用户决定走 UI 层方向 2026-06-08**）
- 方案：b — 用户自设假数字 UI 显示（**不动金融后端**，转账时仍是真数据，不会触发交易纠纷）。
- 装机限制：⛔ F-37 钱包页反 frida 杀进程已实证 → 必须用 LSPosed + 静态 smali / Xposed Java，**禁 frida 进支付域**。
- 接入前必走：`/guard-auth-review` 合规预审 → 产出告知文案 + 截屏水印 + kill switch 路径。
- 发版前必查（每条逐条打勾）：
  - [ ] 默认关闭，需要用户主动开 + 主动设值才生效
  - [ ] 首启风险弹窗：告知"伪造余额仅供自身娱乐，不得用于诈骗/欺骗他人，否则法律责任自负"，用户必须勾选「已知晓」
  - [ ] 截屏 / 录屏在伪余额可见区叠加水印（如客户 seed 派生指纹）
  - [ ] kill switch：远程关闭通道（对齐 D-013 危险通告；Phase 1 miyou-server 上线后接入）
  - [ ] 不动金融后端代码（仅 hook 显示层 TextView.setText 或对应渲染方法）
  - [ ] release proguard 后 `BalanceMask` 类名 / 假数字键名混淆（增加破解成本）
  - [ ] 不在严格合规场景包内启用（如：政企版 / 海外版需硬关，按 build flavor 区分）
- 拒做项（明确不在 E3 范围）：
  - ❌ 不改后端余额（财付通/银行账户真实数据）
  - ❌ 不在转账/付款流程中注入假数据
  - ❌ 不做"自动按规则伪造"（必须用户每次手动设值）

---

## 10. P1F 防护收敛决定（2026-06-10，用户拍板）

> 本节是 2026-06-10 与用户敲定的防护**切分铁律 + 节奏**，作为后续每一步改动的「当切分准绳」。设计全文见 `07_archive_归档/P1F_十字防护整合设计/DESIGN.md`；安全规则见安全官 skill。

### 10.1 大抽屉切分铁律（粗粒度，禁碎拆）

用户原话：「不要把配方拆太碎了，拆大动脉。」对齐安全官 skill「多层但不细碎，粗粒度能力包 + 少数关键出口」。

- 全家 **1 个保险柜**：SO `libguardcore.so`（验真 + 解密 + 风险信号）；v1 不做多 SO 互校验。
- **5 个能力包**（服务器下发，粗粒度）：`license_pack` / `registry_pack` / `risk_pack` / `watermark_pack` / `compat_pack`。
- **3 个唯一出口**（业务全项目只准调这三句）：

```text
GuardRuntime.getRecipe(gateway, key)   // 取 hook 配方（解不开=""）
RiskState.currentLevel()               // 取风险等级
StateMachine.isActive()                // 取中央总闸
```

- **黑名单（碰到就停）**：每功能自造小配方/小网关/小授权；新功能 hook 类名另建 `xxx_pack`（只准往 `registry_pack` 补字段）；Java 侧散落 `decrypt_config`/schema/key/risk 分支；多份弹窗策略；遍地写 if 判风险。
- registry 现就 **4 条大动脉**：`conv.list` / `moments.feed` / `contact.address` / `search.gateway`，不是每个小功能一条。删 fallback 时**整条核账整条删，不抠碎**。

### 10.2 kill↔funnel 拆两个独立闸（拍板）

旧实现把本地 `kill_switch` 当篡改信号塞进引流链，与「停用」语义打架。拍板拆成两根独立线：

- **停用闸 `kill_switch`**：你主动停 / 服务器 kill=true → 跳过全部 hook + Toast「已停用，等待更新」（对齐 CLAUDE.md §十三）。**优先级最高**，`ModuleMain §5` 最先判，命中直接 return。
- **引流闸 `funnel`**：确认篡改超影子期 / 断网超宽限 → `RiskPromptController` 弹窗引流 `zxmqq.shop`，点确定仍可用 + 短冷却。`ModuleMain §6.5` 独立线，只看 `RiskState.shouldFunnel()`，不再看 kill。
- 落地动作（C 刀）：`RiskState.isConfirmedTamper()` 把 `isKillSwitch()` **剥离**（kill 归停用闸，不混进引流篡改链）。

### 10.3 v1 节奏：够用就停（拍板）

- 蓝图（十字防护全套）保留作路线图，别丢。
- v1 只做轻的：**B 钉文档（本节）→ C 拆两闸（纯 Java）**。
- **A 删 Filter fallback 缓做**：收益是兑现 registry 加密，但碰已验证 Filter，必须单独一刀 + 先 git 快照 + 逐条三证核账 + fail-closed + 装机回归。
- **真锁主体 Phase 1D-server 冻结**：服务器短命钥匙 / Ed25519 验签 / LeaseClock 真数据源 / 远程 kill，等「真有客户 / 真有人来破」再启动（skill 估 20~35 人天，现在做属提前优化）。
  - → ⚠️ **2026-06-11 此冻结已解除**（用户拍板②）：重启 Phase 1D-server（S2），先建 **dormant 出站/信封/心跳骨架**，真锁接入仍逐步受控。现状以 **§10.6** 为唯一权威。

### 10.4 两种「党」两个蜜罐（现状 + 待办）

| 攻击者 | 看什么 | 蜜罐 | 现状 |
|---|---|---|---|
| 关键词/静态党 | 反编译搜 `vip` 看代码 | 留亮假锁 `isVipAuthorized(){return true;}`（真锁 `decrypt_config` 无名搜不到） | ✅ 假锁在（`StateMachine.java:94`，proguard 留亮）；⬜ 绊线半截：改假锁 SO 察觉不到，`// [GUARD-TRAP]` 注释 +「isVip 被 hook」检测未接 |
| 抓包党 | 装证书 MITM 看网络流量 | 服务器信封故意摆明牌假字段 `isVip/viptime/endtime` 当诱饵（真值锁在加密 registry + 短命租约） | ⬜ 没有——客户端现无任何自有网络流量（全代码无 HTTP/Socket），归 Phase 1D-server |

两蜜罐的「接上 / 上线」均登记为 **Phase 1D-server 待办**（与真锁同期做）。

### 10.5 A（删 Filter 明文 fallback）—— 探查核账后冻结到 v2（2026-06-10，用户拍板①）

**结论：A 不在 v1 做，整体并入 v2「全字段 registry 化 + 服务器真锁」。** 只读探查（L2）核账依据：

- **能删的仅 17 个字段**（走 `GuardRuntime.getRecipe()` 的可覆盖锚点）：ConvFilter 3 + MomentsFilter 8 + ContactFilter 6，已逐字对账与 `native_core/registry_8071.json` 完全一致；本轮装机实测 `configReady=true`、`entries=4` 解密正常 → 正版机能从 SO 取值，`getRecipe()` scatter 返回 `""` 已 fail-closed。
- **收益有限**：每个 Filter 仍有十几个 `final` 硬编码明文类名（registry 无对应项，删不掉）——ConvFilter（`MvvmConvList`/`ConversationListView`/`MainUI`/`preference.h0`/`kc5.a`/inline `kc5.y`）、MomentsFilter（`SnsMsgUIWithRelevance`/`jw1.d`/`f435583d`）、ContactFilter（`{o,p,h}`/`d`/`e`）。删 17 个，明文暴露面仅降约 20%，hook 意图静态仍可见。
- **回归风险高**：删 fallback 后正版机若 SO 解密抖动（微信 OTA/换机型/binding 材料变化）→ `configReady=false` → 17 锚点全空 → 隐私 hook 静默失效（自己机器密友暴露），且无兜底。当前 fallback = 安全垫（铁律 29 / F-31 红线）。
- **更根本**：`derive_registry_key()` 全程离线可推（key 三段常量在 SO + cert SHA-256，无服务器材料）→ 动态 dump / 自跑 key 仍可全取，删明文只挡 jadx 静态、挡不住动态。真锁＝服务器信封（Phase 1D-server）。

→ 与 §10.1「拆大动脉、不碎拆」/「够用就停」一致：**A close = 冻结**，待 v2 全字段 registry 化 + 真锁一并兑现；`registry_8071.json` 的 contact_fields/l1_methods/e56 等「债」同期补。

---

## 10.6 Phase 1D-server（S2 服务器真锁）解冻 + 现状盘点（2026-06-11，用户拍板②）

> §10.3 的「Phase 1D-server 冻结」已在 2026-06-11 由用户解除。当前已从 dormant 骨架推进到 **v1.1 商业授权最小闭环 + S4 Ed25519 验签 + S3b-A/B LeaseClock 授时/设置页 72h 离线强验**（均 2026-06-11 装机 PASS）：授权码 → token → envelope → 客户端 AuthGate；但仍不是服务器真锁全部完成。本节为 S2/S3a/S3b/S4 的**唯一权威现状**。

### 已建（`net/` 包，L2 代码核查）
- `net/EnvelopeClient`：HTTPS 出站。`activate(卡密)→token`、`fetchEnvelope(token)→签名信封`；按 `AppConfig.guardServerList()` 主备 fallback；强制 https、连不上 / 证书错 = fail-closed。
- `net/AuthEnvelopeVerifier`：**① Ed25519 验签（S4，2026-06-11 装机 PASS）**→ **② 确定性 sanity**（设备绑定 `sha256(deviceId)`、schema / 微信版本、key 材料存在、预过期租约）。`alg` 只接受 `Ed25519`，HS256/缺签名一律判废（fail-closed）。**仍故意不做 HMAC**（不放可伪造 secret 进客户端）；客户端只内置公钥（`ED25519_PUBLIC_B64`），私钥仅在 miyou-server `crypto_utils.GUARD_ED25519_PRIVATE_B64`（env 可覆盖）。验签库 `net.i2p.crypto:eddsa`（minSdk 27 无原生 Ed25519）。
- `net/EnvelopeStore`：token / 信封 / license 到期 / 产品版本 `pv` / 更新通知 `up` 本地缓存；不存用户密友数据。
- `net/GuardHeartbeat`：低频心跳 + 冷启动有 token 时启动；遇 `CARD_BANNED / CARD_DISABLED / CARD_EXPIRED / DEVICE_BANNED / TOKEN_INVALID` 清 token/envelope，网络失败不清，避免断网误杀。
- `net/GuardActivation`：设置页授权码激活入口；token 后必须立刻拉 envelope 成功才算激活成功。
- `core/AppConfig`：`GUARD_SERVER_PRIMARY=https://zxmqq.shop`、`GUARD_SERVER_BACKUP=""`（备机槽留 `miyou.lol`）、`GUARD_PRODUCT_ID=quantum_wechat`、`GUARD_PRODUCT_VERSION=v1.1`、`GUARD_RELEASE_ID=android_8071`。
- `StateMachine.isVipAuthorized()`：已从 v1 stub 改为 `EnvelopeStore.isAuthorizedNow()`（token + verified envelope + license 未过期）。Filter 仍只读 `StateMachine.isActive()`，未直接接触服务器/风控。
- `I:\miyou-server`：主节点 `zxmqq.shop` 已部署 `/api/v1/activate`、`/api/v1/guard/envelope`、后台卡密/设备封停、渠道/release 定向更新通知下发；备节点 8080 已部署，`miyou.lol` HTTPS 反代仍待办。

### 运营弹窗 vs 盗版引流（必须分开）
- **正版运营弹窗**：服务器按 `version/channel/agent/release` 下发 envelope `up`，用于更新提示、渠道活动、联系客服。触发对象是正常授权用户，**不等于盗版引流**。
- **盗版引流弹窗**：只在 `RiskState` 进入 funnel 时弹；文案/URL 后续也可由服务器下发，但触发条件必须是风险态，不能把普通未授权或正常更新误当盗版。
- 验收必须分两条：正常授权卡密收到 `pv/up` 并显示运营提示；封停/风险设备进入 funnel 时才弹引流。

### 仍未做（真锁的「牙」，与诚实口径一致）
1. S3a runtime seed apply 原型已接，但 **PROD server-lock / 真实 S_rel 与 registry_cipher 发布流水线尚未切硬失败**；仍不能宣称服务器真锁完成。
2. ~~无 Ed25519 验签~~ → **S4 已落地（2026-06-11 装机 PASS）**：服务器 Ed25519 私钥签信封，客户端只放公钥验签，HS256 仅留 legacy `/api/v1/config` 公告路径。⚠️ 注意：Ed25519 防的是「伪造/篡改信封」，**不等于真锁**——真锁的「牙」仍是下面第 1 条 S3a 服务器短命 key 折进 SO。
3. `LeaseClock` 已接信封授时（S3b-A，2026-06-11 装机 PASS）：`GuardHeartbeat.syncOnce` 验签后喂 `onServerHeartbeat(sn×1000, exp×1000)` + `RiskState.evaluate()` record-only 重算记日志。**S3b-B 也已落地（2026-06-11 装机 PASS）**：① `EnvelopeStore.isLicenseExpired` 改用 `LeaseClock.trustedNow()`（服务器授时，防回拨/前跳，不信手机墙钟）；② 设置页 `showGuardOverlay` 加"算账检查点"——断网 >72h 进设置页 → `GuardHeartbeat.reverifyIfStale` 强制重验，失败 → `revokeKeepToken`（撤销但留 token 自愈）+ 样式化弹窗"当前时间错误，授权验证失败，请检查时间"。⚠️ 边界：**正常使用（非设置页）断网不掉授权、密友照常隐藏（不误伤/不暴露）**；重连自愈实测通过。72h 阈值当前客户端写死，未走服务器下发。
4. 备节点 HTTPS (`miyou.lol`) 反代未完成；Android 当前只启用主节点。
5. 更新通知 `up` 已下发并被客户端消费，但属于运营提示，不是强制升级/真锁。

### 口径
当前 = 「**v1.1 商业授权最小闭环 + S3a runtime seed apply 原型 + S4 Ed25519 信封验签 + S3b-A/B LeaseClock 授时与设置页 72h 离线强验（均 2026-06-11 装机 PASS）**」。可对内称“授权码→token→envelope→客户端 AuthGate 已通；信封已 Ed25519 防伪造/防篡改；到期判定不信手机时间（trustedNow）；断网>72h 进设置页强制重验、失败撤销且可自愈”；**不得**对外或在文档里宣称「服务器真锁完成」（真锁的牙 = S3a 短命 key 折进 SO + S3a-PROD 硬失败，仍未完成）。

### 发包分发边界（避免误读）
- **服务器真锁 ≠ 服务器打包 / 服务器分发 APK**。
- APK 始终由本地 AI 按包档案构建签名，最终只产出 **官替版 APK** 和 **共存版 APK**。
- 网盘只放 APK；用户想放哪个网盘、哪个目录都可以，路径不属于本项目流水线状态。
- 服务器只负责授权 / 公告 / envelope / 真锁材料登记，不托管 APK，不参与打包，不决定下载路径。

### 下一受控步骤（按序；每步前过授权检查官 + 安全官「改前审查」，并先 git 快照）
1. **S3a-PROD**：把 `prod_server_lock` 发行流水线、S_rel 发版档案、registry_cipher 生成和服务器 envelope 同源打通后，再切无 seed scatter 硬失败。
2. ~~**S3b**：`LeaseClock` / `RiskState` 接信封驱动~~ ✅ **A+B 已完成（2026-06-11 装机 PASS）**：A=心跳喂服务器授时 + record-only 重算；B=到期判定换 `trustedNow`（不信手机墙钟）+ 设置页 72h 离线强制重验/失败撤销（断网正常使用不掉、重连自愈，三段实测通过）。后续可选：72h 阈值改服务器下发、离线散沙更细策略。
3. ~~**S4**：Ed25519 验签（客户端只放公钥）~~ ✅ **已完成（2026-06-11 装机 PASS）**：服务器 `crypto_utils.sign_guard_envelope` 切 Ed25519；客户端 `AuthEnvelopeVerifier` 内置公钥验签 fail-closed；主/备节点已部署。证据：本地 `JAVA_EDDSA_VERIFY=PASS`/`TAMPER_REJECT=PASS`、线上直连 `alg=Ed25519` 公钥验签 PASS、真机 `[hb] synced`+`AUTH_OK` 无 `signature verify failed`。
4. **备机**：完成 `miyou.lol` HTTPS 反代后，客户端再打开 `GUARD_SERVER_BACKUP`。
5. **运营**：更新通知弹窗已通，后续补“强制升级 / 版本灰度 / 下载包托管”再单独审。

### future AI 接手自检（验证「现状是否仍如本节」）
全部命中 = 现状未变；任一项变化 = 已推进，**必须回来更新本节**：
- [ ] `StateMachine.isVipAuthorized()` 是否仍读 `EnvelopeStore.isAuthorizedNow()`？
- [ ] `ModuleMain` 是否仅在本地已有 token 时启动冷启动 heartbeat？
- [ ] `EncryptedConfigLoader` 仍只读本地 SO registry（无服务器 lease）？
- [ ] `derive_registry_key` 仍只折证书指纹（未折信封 `k`）？
- [ ] `RiskState` 仍 record-only（不 gating）？（S3b-A+B 已做：`LeaseClock` 已被 `GuardHeartbeat` 喂服务器授时；`EnvelopeStore.isLicenseExpired` 已换 `trustedNow`；设置页有 72h 离线强验→撤销。若这些被回退/再推进，必须回来更新本节）
- [ ] `AuthEnvelopeVerifier` 是否仍只接受 `alg==Ed25519`（S4 已做）、客户端只内置公钥？

### ⚠️ 与 V3 改包路线（D-016 主攻方向）的冲突 —— 真锁落地前必须先对齐（2026-06-11）

真锁机制「把签名证书折进 registry key」（`bindSigningCert → NativeBridge.setBindingMaterial → derive_registry_key`）与 V3「改包 + 我们的证书重签」（`DECISION_LOG.md` D-016 / D-015）**天生相反**，落地前必须碰头：

- **① 证书源切换（D-016 已记）**：cert-bind 现读 v1 模块 APK 签名；V3 落地要改 `tools/gen_registry_cipher.py` 的 `_CERT_SHA256` + 运行时证书源从 `sModulePath` 改读宿主自身签名。详见 `DECISION_LOG.md` D-016 §影响（**机制不变，只换证书源**）。
- **② 删 fallback = 重签即死（D-016 未串）**：真锁终局（S3a 服务器钥匙 + §10.5「A」删明文 fallback）一旦落地，任何「证书变了却没为它重生成 `registry_cipher`」的重签 / 改包 → 钥匙错 → registry 散沙 → **没有 fallback 兜底 → 隐私 hook 静默全挂**。⇒ 铁律：**「删 fallback」必须与「V3 发版」绑同一条发布流水线**（每个发行证书都重生成 `registry_cipher` 并装机回归），否则 V3 重签包上线即裸奔。
- **③ 共存版改包名 → 误判篡改 → 砸自己客户（D-016 未串）**：`anti_tamper.cpp` 现「`package_name != com.tencent.mm` 即 `PACKAGE_MISMATCH`」→ `RiskState.isConfirmedTamper()` → funnel 弹窗。V3 **共存版**（改了包名）会**整片命中** → 把正版共存客户当盗版引流。⇒ 上共存版前，`tamper_check` 的期望包名必须随打包注入的 `WX_PKG`（D-015）走，不能硬编码 `com.tencent.mm`。

**结论**：S3a / 删 fallback / 真锁终局 在 **V3 改包形态对齐之前不要推进到「硬失败」**；先把上面 ②③ 的发布流水线 + 包名注入接通，再谈删 fallback。否则「防破解」会把「主攻方向 V3」拆台。

---

## 附录 A. 证据明细（2026-06-02 只读核查）

- `StateMachine.java:86-92` —— `isActive()` 链 + `isVipAuthorized(){return true;}`（假锁）
- `core/AuthManager.java` —— `evaluate()`（wxid+设备绑定）/`bindAccount()` 已写，**初始化流程未调用**（死代码）
- `native_core/src/guard_core.cpp:90` —— JNI `nativeIsAuthorized` `return JNI_TRUE`（占位）
- `native_core/src/auth_engine.cpp:25` —— `auth_is_authorized()` 有逻辑但状态默认 UNKNOWN 且 JNI 不调它
- `native_core/src/anti_tamper.cpp` —— 仅查 `package_name=="com.tencent.mm" && config_version==1`，无 HMAC/AES
- `native_core/src/wxid_matcher.cpp:28` —— 硬编码测试 wxid，未从 `hlst`/`glst` 同步
- `ModuleMain.java:165` —— `DebugServer.start()` 无条件；`:172` 浮窗才 `isDebugEnabled()` 包裹
- `debug/DebugServer.java` —— HTTP 8080，`/api/state` `/api/hidden` 等暴露状态与名单
- `proguard-rules.pro:4-10` —— `-keep class com.ghost.assist.core.** { *; }`
- `build.gradle:56-59` —— release `minifyEnabled true`；`:86` `mmkv:1.3.5`（依赖在，Java 未用）
- `core/Bridge.java` —— `g_a7f2`；`hlst`/`glst`/`smst`/`smpw`/`lwxd`/`dvhsh`；`shouldHideId()` `:276`
- `core/AppConfig.java:36` —— `SHOP_URL="https://zxmqq.shop"`；`:86` `isKillSwitch()` 本地 `kl` 默认 false
- `core/NativeBridge.java:78-80` —— `GRACE_WARN_HOURS=24 / DEGRADE_HOURS=72 / LOCKOUT_DAYS=10`；`:200` `shouldHideWxid=isAuthorized()&&isHidden()&&isHiddenWxid()`；`:26` `System.loadLibrary("guardcore")`
- `core/PiracyNotice.java:28` —— TAMPERED 时弹框跳浏览器；**全仓库无调用者**
- 过滤器统一网关：`ConvFilter:531` / `ContactFilter:152` / `MomentsFilter:147` / `SearchFilter:220` / `PushFilter:143` 均 `if(!StateMachine.getInstance().isActive())return;`

## 附录 B. 可复用的现成零件

`PiracyNotice`（弹窗+跳商城）· `OverlayWindow`（关不掉浮窗）· `AppConfig.SHOP_URL`（zxmqq.shop）· `NativeBridge` GRACE 常量（24/72/10天）· `AuthManager`（wxid+设备绑定逻辑）· `AppConfig.Mode.HONEY`（蜜罐态脚手架）· `libguardcore.so`（SO 已加载，待加 crypto）。

---

*本文为上线前防护门控。任何阶段发版前对照 §9 清单逐条打勾，全绿才上线。*
