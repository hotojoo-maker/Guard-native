# PROTECTION_MAP — 上线前防破解总账 / 路线图

> **定位**：这是**上线前的防护门控文档**。每个阶段发版前，对照 §9 的清单逐条打勾，全绿才上线。
> **维护规矩**：防破解相关只记在这一份，别再散到别处；改相关代码前先看本文。
> **证据基线**：2026-06-02 代码实测（只读核查，见 §附录 A），标 L2 = 静态已证实。
> **关联**：`CLAUDE.md`（29 条铁律）· `docs/GUARD_GATE_TRUTH.md`（门控权威）· `docs/PRODUCT_GATE.md`（四层模型）· `DECISION_LOG.md` D-013（危险通告/kill switch）· `RISK_REGISTER.md` · `docs/DEBUG_CONSOLE_V2.md`（防护驾驶舱）
> **当前进度（2026-06-02）**：**Phase 0 完成 ✅** —— 仪表盘（防护驾驶舱 + `/api/native` + `tools/guard_status`）+ ① DebugServer DEV-gate + ② proguard 收窄（release `BATCH1_VERIFY PASS` + mapping 实锤：NativeBridge 保留、过滤器混淆、诱饵留亮）+ ③ 接 `AuthManager.evaluate()`+`PiracyNotice`（装机 `evaluate=NO_LICENSE`，v1 放行不变）。**下一步 Phase 1**（真锁+心跳，接 miyou-server）。

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
- [ ] SO `decrypt_config()`（AES-GCM）实现 + 自测向量
- [ ] miyou-server 心跳端点：发短命钥匙 + 加密配置；字段伪装 `K2i_m`
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
