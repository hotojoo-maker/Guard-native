# 版本停用 VersionKill — 设计稿

> 目标：能**精确停某个版本号**（如只停 `android_8071 + v1.6` 坏包），不误伤同线其它版本、不误伤共存线。
> 立稿：2026-07-01（架构师 FSK94）。**客户端侧已落码**（见 §四），**服务器侧待 服务器运维 落地**（见 §五，可直接转发）。
> 关联：`_CORE_现状真源/授权_当前真源.md`（授权现状）· `PROTECTION_MAP.md §10.2`（应急三档）· `docs/RELEASE_RULES.md`（发版/危险通告）

## 一、大白话

- 现在服务器能「整条线」停（官替/共存整条），但**不能只停某一个版本号**。
- 本设计加一档：**按版本号停**——比如发现 v1.6 是坏包，只把 v1.6 停掉，v1.5 照常、共存照常。
- 用途：**发版安全阀**（停坏包 / 逼用户升级），**不是防破解**（防破解另有 cert / registry / Ed25519）。

## 二、三档应急（本设计补第 2 档，复用同一执行路径）

| 档 | 停的单位 | 错误码 | 触发 |
|---|---|---|---|
| 整线停用 | 整条 release_id（官替/共存全部版本） | `RELEASE_KILLED` | release_lines.status=killed |
| **版本停用（本设计）** | (release_id, pv) 精确到某版本 | `VERSION_KILLED` | 版本停用名单命中 |
| 单卡/设备停 | 某张卡/设备 | `cardRevoked=1` / `CARD_BANNED` 等 | 后台封卡/封设备 |

三档**共用同一客户端执行路径**：服务器回这些码 → 客户端 `GuardHeartbeat.isHardAuthError` → 清 token → `EnvelopeStore.isAuthorizedNow()=false` → 下次心跳/冷启动即撤隐私 + A2。

## 三、键与粒度

- 键 = **(product_id, release_id, app_version)**。
- 例：停官替 v1.6 坏包 → 名单加 `{quantum_wechat, android_8071, v1.6}`。
- v1.5（同线）、v1.6（共存线 `android_8071_coexist`）均**不受影响**。
- 可选 `min_supported_version`（按 release_id）：app_version < 此值 → 视为停用 = 强制升级一批旧版。

## 四、客户端（已落码 2026-07-01，待装机验证）

| 改动 | 文件 | 说明 |
|---|---|---|
| `isHardAuthError` 加 `RELEASE_KILLED` + `VERSION_KILLED` | `net/GuardHeartbeat.java` | 收到即清 token（下次心跳/冷启动生效） |
| `authErrorText` 加 `VERSION_KILLED → 「该版本已停用，请更新」` | `net/EnvelopeClient.java` | 用户可见文案 |
| `activate()` body 补 `app_version`(pv) | `net/EnvelopeClient.java` | 激活时也带版本，服务器可在激活阶段挡 |

> pv 来源 = `AppConfig.GUARD_PRODUCT_VERSION`（BuildConfig，当前 v1.6）。`fetchEnvelope`/`health` 早已带 `client.app_version`；本次补的是 `activate`。
> auth-review：纯加性、不新建状态、不碰 StateMachine/Filter/边界。改前/改后审 PASS（2026-07-01）。

## 五、服务器侧（待 服务器运维 落地 · 本节可直接转发）

> 目标服务器：`I:\miyou-server`（主节点 zxmqq.shop）。客户端已就绪，只等服务器按版本判停并回 `VERSION_KILLED`。

**要做 3 件：**

### 1. 版本停用名单（存储）

- 新增配置/表：`killed_versions`，每条 = `(product_id, release_id, version)`；可选 `reason` / `created_at`。
- 可选 `min_supported_version`（按 release_id）：`app_version` < 此值 → 视为停用。

### 2. 判定接入（3 个端点）

- `/api/v1/guard/envelope`（心跳，最重要）、`/api/v1/activate`、`/api/v1/guard/health`。
- 取请求里的 `product_id` + `release_id` + `app_version`（envelope/health 在 `client.app_version`；activate 在 body 顶层 `app_version`，客户端本次已补）。
- 命中 `killed_versions`（或 `< min_supported_version`）→ 返回：

```json
{ "status": "error", "code": "VERSION_KILLED", "message": "该版本已停用，请更新" }
```

- 注意：判定应在「签发 envelope 之前」短路，killed 版本拿不到有效信封。

### 3. 后台控制

- 管理界面/接口加「停某版本 / 解停」：选 release_id + 输 version → 写入/移除 `killed_versions`。
- 与现有 release_lines（整线 killed/paused）后台同处，多一个「版本」维度。

**回归点**：客户端已认 `VERSION_KILLED`；服务器一旦返回该码，受影响版本的设备下次心跳即撤授权（不误伤其它版本/线）。建议先在测试卡密 + 测试版本上验。

## 六、诚实边界

- `app_version` 是客户端自报（BuildConfig）。破解者改 APK 可改 pv 骗版本判定——但改 APK 必重签 → cert 不符 → A2 散沙 / 绊线已挡。
- 所以版本停用 = **对正版用户的发版安全阀**（停坏包/逼升级），**不是防破解武器**。防破解仍锚在 cert / registry / Ed25519 信封。

## 七、验证（装机后）

1. 客户端装 v1.6（官替）+ 激活 → 正常授权。
2. 服务器把 `{android_8071, v1.6}` 加进 `killed_versions`。
3. 等一次心跳（或冷启动）→ logcat 见 `[hb] hard auth error — token cleared` + 隐私失效 + 设置页文案「该版本已停用，请更新」。
4. 反证：v1.5 / 共存 v1.6 不受影响（仍授权）。

## 八、文档待同步（落地后）

- `PROTECTION_MAP.md §10.2`：原写「RELEASE_KILLED → token 清」现已与代码一致（客户端已加 RELEASE_KILLED）；新增 `VERSION_KILLED` 一并登记。
- `PROTECTION_MAP.md §10.6:272`：「硬错只清 5 码」已过时 → 现 7 码（加 RELEASE_KILLED / VERSION_KILLED），需更新。
- `_CORE_现状真源/授权_当前真源.md §⑥`：已同步（路A 已改码待装机）。
