---
name: guard-server-ops
description: Guard Native 服务器运维与发行线接手。Use when the user mentions miyou-server、服务器、授权后台、卡密、release_id、版本更新、update_config、guard/envelope、S_rel/W、Ed25519、设备列表、健康看板、zxmqq.shop、miyou.lol，或需要检查/修改 I:\miyou-server。
---

# guard-server-ops — 服务器运维 / 发行线管理员

## 定位

本 skill 专管 `I:\miyou-server` 授权服务器与 Guard Native 发行线：

- 授权码 `activate`、token、`/api/v1/guard/envelope`
- `release_id` / `release_lines` / 卡密绑线
- Ed25519 signed envelope、`k/n`、`S_rel/W`
- 更新通知 `update_config`、按 channel / release 下发
- 设备健康看板、设备封停、风险 tier、心跳租约
- 主节点 `zxmqq.shop` 与备节点 `miyou.lol`

一句话原则：

**服务器只负责授权、发行线、短命钥匙和运营通知；核心 hook 配方写死在 APK 的 encrypted registry 里，不做热更新配方。**

## 接手必读

先读这些文件，只读确认现状，不要直接改：

1. `I:\miyou-server\SCHEMA.md` — 数据库与发行线权威
2. `I:\miyou-server\OPS_GUARD_NATIVE.md` — 服务器操作手册（若存在）
3. `I:\miyou-server\server.py` — API / 管理后台
4. `I:\miyou-server\db.py` — 表结构、发行线、设备状态
5. `I:\miyou-server\crypto_utils.py` — Ed25519 envelope 与 `k/n`
6. 本仓库 `PROTECTION_MAP.md` §10.6 — 客户端真锁现状
7. 本仓库 `docs/RELEASE_RULES.md` 加密接手清单

权威分工：

- 数据库结构、字段含义、迁移记录：以 `SCHEMA.md` 为准。
- 运维流程、后台入口、release 状态语义：以 `OPS_GUARD_NATIVE.md` + 当前代码核查为准。
- 客户端真锁阶段口径：以本仓库 `PROTECTION_MAP.md` §10.6/§10.7 为准。

## 硬红线

1. **禁止把密钥原文写进文档或聊天**：SSH 密码、Ed25519 私钥、`S_rel` 原文、`W` 原文、批量卡密明文都不能写。
2. **只能展示指纹**：需要对账时用 `sha256[:8]`、key id、release_id、schema、版本号。
3. **不做配方热更新**：新微信版本必须重新出包；服务器只按发行线发 envelope seed。
4. **不伤旧版本线 / 旧客户**：已发布 release line 只允许新增、暂停、废弃或重大事故 kill；禁止删除历史线，禁止在已装机线上原地改 `S_rel` / registry / 证书 / 包名 / schema 影响存量客户。
5. **新版本开新 `release_id`**：新微信版本、新官替/共存包、新证书、新包名、新 registry，默认开新发行线，不复用旧线硬改。
6. **不把 `kill_switch` 和盗版引流混用**：kill 是停用闸，引流只看风险态。
7. **不因断网误伤正版**：网络失败保留缓存和宽限；确认封停 / 过期 / 风险才收紧。
8. **不清用户隐私数据**：服务器封停、撤销、降级都不得要求客户端清密友名单、密码、配置。
9. **线上改动先备份 `auth.db`**：任何迁移、批量更新、部署前先备份数据库。
10. **后台只存指纹和状态**：release 档案只能存 fingerprint/hash/key id/status/admin_note；禁止保存 APK、`S_rel` 原文、`W` 原文、私钥、keystore 密码、token 或 envelope 原文。

## 当前模型

客户端链路：

```text
授权码
  -> POST /api/v1/activate
  -> token
  -> POST /api/v1/guard/envelope
  -> Ed25519 signed envelope
  -> k/n
  -> 客户端 SO unwrap S_rel
  -> registry_cipher 解密
  -> GuardRuntime.getRecipe()
```

版本更新模型：

```text
每个微信版本 / 发行包 = 一条 release_id
客户端内置 encrypted registry
服务器按 release_id 发对应 S_rel 包装后的 k/n
更新通知走 envelope.up
```

当前后台能力（L2 代码/OPS 可见）：

- Android 运营台 / 版本状态：展示每条 release 的激活、心跳、失败率、门控和真锁指纹。
- 发版档案：只登记 `package_line`、包名、版本号、证书指纹、`S_rel/W` 指纹、registry hash、Ed25519 key id。
- 健康事件：记录 envelope/health 结果、release、设备 hash、schema、证书前缀、risk/lease 状态。
- 设备总览：按 Android release / 官替 / 共存聚合设备；支持全部/需处理/正常筛选，显示新设备、观察中、稳定设备、需处理、健康分、Envelope 结果、risk/tier、心跳/失败活跃条。
- 需处理设备：按 release 展开 tier>=2 / seed 或配方失败 / 封停 / 风险态设备；新设备和观察期不算异常。可恢复正常 / 标记可疑 / 封设备 / 改授权时间 / 清设备记录。
- 测试清理：支持“重置当前筛选设备状态”和“清空当前筛选设备列表”；只清设备状态/健康事件，不删卡密和激活，适合清测试脏状态。
- 卡密清理：卡密列表支持单卡删除；浏览器确认后删除该卡、对应激活、设备状态和健康事件，不影响其它卡和任何 release 配方。
- 管理员登录：`/admin` 登录态保存在浏览器 localStorage，刷新保持登录；退出登录会清除本地登录态。
- 代理后台：代理只能看自己渠道开的卡和设备；管理员看全量。
- 更新通知：可按 `all`、channel、release 下发；它是运营提示，不是热更新配方。

## 发行线规则

`release_id` 是服务器与客户端的主索引。新增版本时必须保持这些同源：

- 客户端 `AppConfig.GUARD_RELEASE_ID`
- 服务端 `release_lines.release_id`
- 卡密 `cards.release_id`
- 服务器 `GUARD_REL_KEYS[release_id]`
- 本地发版 recipe 的 `S_rel/W`
- `registry_cipher.inc` 生成时折入的 `S_rel`
- 发版档案里的证书指纹、包名、versionCode

任一不同源，结果应当是 registry scatter，而不是全开。

release 状态语义：

- `draft`：草稿，不给正式客户。
- `testing`：内测，只给测试卡 / 测试设备。
- `active`：正常发布，允许新激活和 envelope。
- `paused`：暂停新激活，尽量不影响已有客户；用于灰度观察和临时止血。
- `deprecated`：旧线退役，先通知升级，不硬杀存量。
- `killed`：重大事故人工二次确认后使用，停止新激活、停止 envelope，并打开 kill switch。

门控字段：

- `allow_activation`：是否允许新激活。
- `allow_envelope`：是否继续给该 release 发 envelope。
- `kill_switch`：重大事故停用闸，不等于盗版引流。

## 新版本流程

新微信版本或新发行包不要改服务器机制，按顺序做：

> 最短可执行流程看本仓库 `docs/RELEASE_RULES.md` 的「下次发新版最短流程（先看这里）」。服务器只负责同名 `release_id` 的 `release_lines` 和 `GUARD_REL_KEYS`，不做 hook 热更新。

1. 客户端探查新版本 hook 点，生成新版 registry 明文源。
2. 本地发版档案生成 / 记录该 `release_id` 的 `S_rel/W`，只写指纹到可见文档。
3. `gen_registry_cipher.py` 用同一 `S_rel` 生成 `registry_cipher.inc`。
4. 本地打包 APK，装机确认 Ed25519 验签、server seed unwrap、`recipeOk=true`、业务隐藏链正常。
5. 服务端后台登记 release 档案，只填 fingerprint/hash/status，不填密钥原文。
6. 服务端配置同一 `release_id` 的 `GUARD_REL_KEYS`，并确认 `release_lines` 的 schema、微信版本、证书前缀、租约策略同源。
7. 新线先 `testing` 灰度，再 `active` 发卡/envelope。
8. 后台按 release 下发更新通知，旧线按节奏 `deprecated`，禁止删除旧线，禁止立即误杀存量客户。

## 排错口径

- `signature verify failed`：先查服务器 Ed25519 私钥 / 客户端公钥 / `alg` / 签名消息格式。
- `envelope invalid`：查 token、device binding、schema、微信版本、`k/n` 长度。
- `recipeOk=false`：先查请求 `release_id` 是否命中正确 release 档案，再查 `allow_envelope`、`kill_switch`、`S_rel/W` 是否同源、`registry_cipher.inc` 是否用当前 recipe 生成、证书绑定是否一致。
- `tier=2`：服务端认为该设备可疑或仍处观察短租；查 `guard_device_state.reasons`、设备漂移、token churn、install_id、health 事件。
- `tier>=3` / decoy：服务端可能发垃圾 seed，客户端 scatter 是预期；先查风险分、重装 churn、后台是否需要恢复正常。
- 设备列表暴增：优先查 `device_id` 生成策略和后台展示聚合；不要直接改成只绑 wxid。
- 更新通知不弹：查 `server_settings update_*`、release/channel 过滤、envelope `up`、客户端是否已授权。
- 旧客户突然不可用：先查是否误改旧线 `S_rel` / registry / 证书 / `allow_envelope` / `kill_switch`，不要先改客户端。

## 后台操作边界

- 发卡：可做专卡（指定 `release_id`）或通用卡（空 release）。
- 改授权时间：只允许改对应卡密 `expire_time`，不改设备绑定、激活记录、release 配方或 S_rel/W。
- 删除卡密：只用于测试/脏数据清理；删除单张卡密及其关联激活、设备状态、健康事件，必须二次确认完整卡密。
- 重置设备状态：只清 risk/tier/错误计数/状态，保留设备记录、卡密和激活。
- 清空设备列表：只清设备状态和健康事件；设备下次心跳会重新出现，不删除卡密和激活。
- 删除单台设备记录：只删该设备状态和健康事件，不删卡密、不删激活。
- 更新通知：可按 `all`、channel、release 下发；它是正版运营提示，不是盗版引流。
- 暂停发行线：优先用 `paused` 停新激活，观察存量 envelope，不要一刀切。
- 废弃旧线：先通知升级，再 `deprecated`，避免存量正版突然失效。
- 紧急停用：只有重大事故才用 `killed`，需要二次确认 `release_id`，并记录审计原因。
- 删除历史线：默认禁止；保留历史状态、指纹、健康事件用于回溯和不伤旧客。

## 部署铁律

部署前：

- 备份远端 `server.py`、`db.py`、`crypto_utils.py`、`config.py` 和 `data/auth.db`。
- 确认远端基线与本地预期一致，尤其是 `config.py` 中 `GUARD_REL_KEYS`。
- 只展示指纹，不把 SSH 密码、密钥、`S_rel/W` 原文写进聊天或日志。

部署后：

- 远端执行 `python3 -m py_compile server.py db.py crypto_utils.py config.py`。
- 重启服务。
- 验证 `/api/v1/ping`。
- 验证 `/admin` 前端脚本可用。
- 验证 `/admin/api/releases` 能返回 release 列表，并抽查当前 active release 的门控和指纹。

## 输出要求

回答用户时用清楚的业务口径：

- 可以说：**当前服务器支持多发行线、卡密绑线、Ed25519 envelope、S3a seed、按 release 更新通知。**
- 不要说：**服务器能热更新 hook 配方。**
- 不要说：**授权无法破解 / 真锁终局完成。**
- 涉及证据必须标注 L1/L2：日志为 L1，代码核查为 L2，设计未落地为 L4。
