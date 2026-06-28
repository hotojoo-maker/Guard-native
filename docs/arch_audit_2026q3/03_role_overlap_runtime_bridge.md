# 产出 3：GuardRuntime / NativeBridge / Bridge 三角色职责重叠分析

## 3.1 各自的「自述」职责

| 角色 | 定位（源码 javadoc + 实测） | 是否持久化 | 是否走 SO/JNI |
|---|---|:--:|:--:|
| **Bridge** | 存储/状态层。SharedPreferences(`g_a7f2`) 薄封装 + 一堆内存缓存。所有持久配置（密友/密群名单、各功能开关、通知策略、伪装定位、授权绑定 lwxd/dvhsh）和运行期缓存（UIN↔wxid、feed/conv 环、item dump）都在这。**无业务逻辑、无加密、无原生。** | ✅ SP + 内存 | ❌ |
| **NativeBridge** | `libguardcore.so` 的 JNI 网关。进程角色 / 授权态 / 隐藏态 / wxid·群匹配 / registry 解密 / recipe·endpoint 取件 / 绑定材料下推。全 `static`，SO 缺失即 fail-closed 返回安全默认。自述「除桥本身什么都不拥有，业务逻辑留在各 hook 模块」。 | ❌（态在 C++ 侧） | ✅ |
| **GuardRuntime** | 配方（类名/字段名）**唯一出口** + A2 防封时间闸。配方查询委托 `EncryptedConfigLoader`；自述「只在 config/key 层、**不读写 StateMachine / AuthManager / 任何 AUTH_* 态**」。 | ❌ | 间接（经 EncryptedConfigLoader → NativeBridge） |

## 3.2 重叠点分析

### 重叠 A（真重叠，有技术债）：「这个 wxid 是不是密友」有两个真相源

- `Bridge.shouldHideId(id)` / `Bridge.allHiddenIds()` / `getWxids()`：从 **Java SharedPreferences 名单** 判定（当前 4 个 Filter 实际都走这条）。
- `NativeBridge.isHiddenWxid(wxid)` / `isHiddenGroup(groupId)` / `shouldHideWxid()` / `shouldHideGroup()`：从 **C++ SO 内的名单** 判定（O(1) 热路径设计）。

`NativeBridge.java:196-203` 注释明写：
> "Combined shouldHide helper … **Phase 3: replace `Bridge.getWxids().contains()` calls with this.**"

**结论**：同一个语义（密友判定）当前由 Java 名单权威、却又预留了原生权威，迁移（Phase 3）未做 → **双真相源债**。风险：哪天名单写入只更新 SP 没下推 SO（或反之），两边会不一致。
**建议**：明确「写多读一」——Bridge 仍是写入/持久权威，名单变更后统一经 `NativeBridge.setHidden*`/绑定材料下推 SO；读侧热路径走 NativeBridge，冷路径/UI 走 Bridge。把这条写成铁律，避免再各调各的。

### 重叠 B（分层，不算真重叠，但命名易混）：`getRecipe`

- `NativeBridge.getRecipe(gateway, key)`：裸 JNI，直接问 SO。
- `GuardRuntime.getRecipe(gateway, key)` → `EncryptedConfigLoader.getRecipe()` → （最终）`NativeBridge`：受控出口，叠加了 strict/scatter/fallback 决策。

二者**同名不同层**：`GuardRuntime` 是 `NativeBridge` 之上的策略壳。这是合理分层，但同名 `getRecipe` 让人误以为可绕过 GuardRuntime 直接调 NativeBridge（那样会丢掉 strict-mode 与 fail-closed 决策）。
**建议**：把 `NativeBridge.getRecipe/getEndpoint` 收紧为包内可见或改名（如 `nativeRecipeRaw`），强制业务侧只经 `GuardRuntime`。呼应 §10.7 P1.3「JNI 边界降噪」。

### 重叠 C（职责扩张）：GuardRuntime 同时是「配方出口」+「A2 防封闸」

`GuardRuntime` 自述只在 config/key 层、不碰授权态；但 `isAntiBanReady()` / `isWithinAntiBanWindow()`（:138-189）**读了 `net.EnvelopeStore`**（`isAuthorizedNow`/`isCardRevoked`/`hasToken`/`getLicenseExpireSec`）和 `LeaseClock`、`CompatProbe`——这已经是**授权/计时语义**，与「不碰授权」的自述有张力。

- 注意：A2 闸是 **fail-OPEN**（拿不准就装，宁不撤勿误杀正版），与隐私 registry 的 **fail-CLOSED** 方向相反（源码 :113-128 大段说明）。两套相反的失败哲学塞在同一个类里，认知负担高。
**建议**：把 A2 防封时间闸（`isAntiBanReady` / `evalAntiBanWindow` / 两个 DEBUG self-test）拆到独立 `AntiBanGate` 类，`GuardRuntime` 回归纯「配方出口」。纯函数 `evalAntiBanWindow` 已经无 IO、可独立单测，拆出去几乎零成本，且能消除「配方类里混授权逻辑」的误读。

## 3.3 该合并 / 该拆分 一览

| 项 | 判定 | 动作建议 | 难度 |
|---|---|---|:--:|
| Bridge vs NativeBridge 的密友判定 | **不合并**，但要定权威 | 立铁律：写经 Bridge→下推 SO；热路径读 NativeBridge。补一致性自检 | 中 |
| NativeBridge.getRecipe vs GuardRuntime.getRecipe | **分层正确**，收口可见性 | NativeBridge 裸 recipe 接口收为包内/改名，业务只经 GuardRuntime | 低 |
| GuardRuntime 里的 A2 防封闸 | **应拆分** | 抽出 `AntiBanGate`，GuardRuntime 只留配方出口 | 低-中 |
| Bridge 自身 | **可考虑拆** | Bridge 既是持久配置又是一堆调试内存缓存（item dump / rawFeed / feed-conv 环），调试态可拆到 `debug/` 下，让 Bridge 更纯 | 中（非紧急） |

## 3.4 边界结论

三角色**主线边界是清晰的**：Bridge=存储、NativeBridge=原生网关、GuardRuntime=配方策略出口。
真正要还的债只有两条：**①密友判定双真相源（Phase 3 未收口）**、**②GuardRuntime 兼了 A2 闸**。其余是命名/可见性层面的「易误用」，低成本即可收敛。
