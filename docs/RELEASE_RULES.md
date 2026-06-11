# 发布与运营

> 从 CLAUDE.md §十三~十六移出。

## 危险通告 / 一键停用（安全兜底）

**触发场景**：封号潮 / 检测密度暴增 / 新版微信未适配 → 必须能远程拔插
**机制**：
- 客户端启动拉 miyou-server `cs_url` 的危险通告接口
- 接口返回字段 `kill_switch: true|false`
- `true` → 跳过 hook 注册（表现为完全无功能但不崩溃）+ 浮窗显示"已停用，等待更新"
- 用户可手动覆盖（按住返回键 5 秒 → 临时启用，仅自查）

**v1 实现**：客户端启动埋占位实现（默认 false），不调真实接口
**v2 实现**：接入 miyou-server 真实接口 + HMAC 验签
**服务器**：公告系统已就绪 → `I:/miyou-server/CLAUDE.md`

详细决策 → [`DECISION_LOG.md`](../DECISION_LOG.md) D-013

---

## 商业模式（v2 接入）

- **小众高端客户** + 隐私优先 + **不要求量大**（避免泛滥触发封号阈值）
- **每客户独立 seed/签名**（蜜罐溯源 + 反聚类）
- **未授权引导**：校验未通过 → 提示独家功能 → 引导至官方购买渠道
- **授权按时间**（miyou-server `cs_url` + `shop_url` + 设备邀请码现成）
- **只零售、不批量分发**（面向终端用户，控制传播规模）

---

## 对外品牌

- **内部代号**：Guard Native（不对外）
- **客户端品牌**：待定（**禁用** "微信"二字 + vip/pirate/hide 等敏感词）
- **域名/服务器实体**：跟产品做切割（zxmqq.shop / miyou.lol 已就绪）
- **授权发放**：经独立销售渠道，与产品实体切割

---

## 发布铁律 / Release Rules

### 1.0 后签名证书不可变

Guard Native / Guard Pack 从 **1.0 正式发布**开始，同一客户包必须永久保持以下不变：

| 不可变项 | 说明 |
|---------|------|
| `packageName` | Android 覆盖安装的身份标识 |
| APK 签名证书 / keystore | 签名不一致 = 无法覆盖安装，客户必须卸载重装 |
| `customerSeed` | 蜜罐溯源 / MMKV 命名空间根 / 混淆基准 |
| 授权绑定规则 | `licensedWxid + deviceHash + customerSeed` 组合不可拆分重组 |

后续版本只允许 **`versionCode` 递增**，禁止降级发布。

### 强制要求

1. **1.0 发布前必须建立客户包档案**，至少记录：
   - `packageName`
   - keystore 文件路径（离线存储位置）
   - key alias
   - `versionCode` 起始值
   - `customerSeed`
   - `licensedWxid` / device 绑定策略说明
2. **keystore 必须离线备份，至少两份**（不同物理位置）
3. **禁止将正式 keystore 放入**：公开仓库 / 聊天窗口 / 日志 / AI 对话 / 临时目录
4. **keystore 丢失 = 该客户包后续无法无损更新**（无法覆盖安装，只能新包重装）
5. **禁止 1.0 后重新生成 keystore 给同一客户继续发包**
6. **每次发版 `versionCode` 必须递增**，不得复用或回滚

### 例外处理

如果必须更换证书或包名，只能视为 **"新产品 / 新客户包"**：
- 不能覆盖安装旧版本
- 必须提前告知客户需要重新安装 + 重新授权
- 旧包档案归档到 `07_archive_归档/`，不得删除

---

## 双版本发布手册（官替版 / 共存版）

> 目标：后续发版由 AI 按包档案自动改包、签名、生成加密配方和打包；用户只选择发哪条版本线，不手动碰签名。

### 两条版本线

| 版本线 | 包名规则 | 覆盖关系 | 典型用途 |
|--------|----------|----------|----------|
| 官替版 | 固定 `com.tencent.mm` | 只覆盖官替版 | 卸官方后安装，体验最接近原微信 |
| 共存版 | 固定一个后缀包名，例如 `com.tencent.mm.xxx` | 只覆盖同包名共存版 | 官方微信保留，隐私版独立共存 |

铁律：

1. **官替版只能覆盖官替版**：包名固定 `com.tencent.mm`，签名证书必须一致。
2. **共存版只能覆盖共存版**：包名固定为首次发布选定的共存包名，签名证书必须一致。
3. **官替版和共存版互不覆盖**：它们是两条独立发行线，分别维护 `versionCode`、keystore、release_id 和客户包档案。
4. **同一版本线禁止换签名**：换签名 = Android 不能覆盖安装，且证书绑定会导致 encrypted registry 散沙。

### 发包 / 分发边界（2026-06-11 用户口径）

**本地打包，网盘只放 APK。**

- AI 的职责是在本机按包档案打出两个可分发 APK：**官替版 APK** + **共存版 APK**。
- 网盘只放 APK；网盘路径、目录名、下载链接由用户随意决定，**不属于项目状态，不写入发版档案**。
- 服务器只用于授权 / 公告 / 真锁材料登记；**不参与打包、不托管 APK、不决定网盘路径**。
- 发版流程不得要求用户说明“APK 放哪个网盘 / 哪个目录”；只要本地 APK 产物和装机验证通过即可。

### AI 发版档案

每条版本线必须有一份本地包档案，AI 发版时只读档案，不临时猜参数。

最少记录：

```text
release_line: hijack | coexist
package_name: com.tencent.mm 或共存后缀包名
release_id: android_8071_hijack 或 android_8071_coexist
keystore_path: 本地离线 keystore 路径
key_alias: 签名 alias
version_code_next: 下一个可用 versionCode
customer_seed: 客户/批次 seed
guard_wx_pkg: 运行时宿主包名
cert_sha256_source: 从 keystore/签名自动读取，不手写
```

禁止事项：

- 禁止把正式 keystore 内容、密码、私钥复制进聊天窗口或文档。
- 禁止 AI 重新生成 keystore 给旧客户继续发包。
- 禁止手工改 `_CERT_SHA256` 后不做 `PHASE1D_VERIFY` / `PHASE1E_VERIFY` 验证。
- 禁止复用已经发过的 `versionCode`。

### 发版时 AI 必须同步的参数

官替版和共存版都必须同源更新以下位置：

| 参数 | 作用 |
|------|------|
| `guardWxPkg` | 注入 C++ `GUARD_EXPECTED_PACKAGE`，供 anti_tamper 判断宿主包 |
| Java 宿主包白名单 | `ModuleMain` 必须识别目标包名和 `:push` 进程 |
| Xposed scope | 独立模块模式下 scope 必须指向目标宿主包 |
| `release_id` | 服务端发行线区分官替 / 共存 |
| 签名证书 SHA-256 | 参与 registry key 派生，签名不一致会 scatter |
| `versionCode` | 每次发版递增，保证覆盖安装 |

当前状态提醒：

- C++ 包名注入已预留：`-PguardWxPkg=...`。
- Java 宿主包白名单和 scope 仍需纳入同一套构建参数，不能只改 C++。
- 共存版如果由 MT 管理器等工具改包名，仍必须把最终包名同步给模块构建链。

### 加密接手清单（AI 发版必读）

当前加密链路是：

```text
native_core/registry_8071.json
  → tools/gen_registry_cipher.py
  → native_core/src/registry_cipher.inc
  → libguardcore.so registry_load_embedded()
  → GuardRuntime.getRecipe()
  → Filter 读取 hook 配方
```

各文件职责：

| 文件 | 职责 | AI 禁忌 |
|------|------|---------|
| `native_core/registry_8071.json` | hook 配方唯一明文源 | 不确认活跃锚点，不新增/删除字段 |
| `tools/gen_registry_cipher.py` | 生成 AES-GCM registry cipher | 不手写 `_CERT_SHA256` 后跳过验证 |
| `native_core/src/config_crypto.cpp` | AES-GCM + key 派生 + 证书/服务器材料折入 | 不把派生 key 改成单个明文常量 |
| `native_core/src/registry_loader.cpp` | 解密 registry + 自测 + recipe getter | 不放宽 scatter 失败策略 |
| `src/main/java/com/ghost/assist/core/GuardRuntime.java` | Java 侧配方唯一出口 | Filter 不得绕过它直接读加密细节 |

加密现状口径：

- 已完成：AES-GCM encrypted registry、签名证书绑定、`GuardRuntime.getRecipe()` 取配方、失败 scatter。
- 仍未完成：服务器短命 key 材料成为 registry 必要条件、Ed25519 验签、服务器授时真数据源、真正散沙降级。
- 对外只能说：**本地 encrypted registry + 证书绑定已接入**。
- 对外禁止说：**服务器真锁已完成** 或 **授权无法破解**。

发版时必须保证：

1. `registry_8071.json` 是唯一配方源；`registry_cipher.inc` 只能由脚本生成，禁止手改。
2. 生成 cipher 时使用的证书 SHA-256，必须等于运行时 `bindSigningCert()` / 后续宿主签名读取到的证书 SHA-256。
3. 官替版和共存版如果签名不同，必须分别生成自己的 `registry_cipher.inc`，不能共用错证书产物。
4. `tools/gen_registry_cipher.py::derive_registry_key()` 必须与 `config_crypto.cpp::derive_registry_key()` 保持一致；漂移会导致 tag 校验失败并 scatter。
5. `registry_loader.cpp::registry_self_test()` 必须通过；它会验证 schema、核心 4 条 registry、tamper cipher、wrong key、miss gateway/field。
6. standalone 测试如果调用 `registry_self_test()`，必须先设置和生成端一致的 binding material；否则 scatter 是正确结果，不是误报。
7. 装机日志必须看到 `PHASE1C_VERIFY PASS`、`PHASE1D_VERIFY PASS`、`PHASE1E_VERIFY PASS`，才说明 encrypted registry、证书绑定、recipe 出口都通。

scatter 排查顺序：

1. 看 `PHASE1D_VERIFY` 是否 FAIL：优先查签名证书 SHA-256 是否和生成 cipher 时一致。
2. 看 `PHASE1C_VERIFY` 是否 FAIL：优先查 `registry_cipher.inc` 是否由当前 `registry_8071.json` 重新生成。
3. 看 `PHASE1E_VERIFY` 是否 FAIL：优先查 `GuardRuntime.getRecipe()` / `EncryptedConfigLoader` / `nativeGetRecipe()` 出口。
4. 共存版整片 scatter：优先查最终包名、运行时证书源、`guardWxPkg`、Java 白名单和 scope 是否同源。
5. 业务 hook 仍生效但加密验证 FAIL：可能是旧 fallback 在兜底，不能当作加密链路通过。

### 标准发版流程

1. 选择版本线：`官替版` 或 `共存版`。
2. AI 读取对应包档案，确认 `packageName`、keystore、`versionCode`、`release_id`。
3. AI 自动读取签名证书 SHA-256，重新生成 encrypted registry。
4. AI 注入宿主包名到 Java / scope / C++。
5. AI 构建并使用对应 keystore 签名 APK。
6. AI 装机或交给用户装机验证。
7. 必须抓到以下日志后才算发布候选：
   - `BATCH1_VERIFY PASS`
   - `PHASE1A_VERIFY PASS`
   - `PHASE1B_VERIFY PASS`
   - `PHASE1C_VERIFY PASS`
   - `PHASE1D_VERIFY PASS`
   - `PHASE1E_VERIFY PASS`
8. 验证业务主链路：显隐切换、会话过滤、通讯录过滤、朋友圈过滤、搜索过滤、红点/通知相关链路。
9. 产物只收口为本地 APK：官替版 APK / 共存版 APK。上传网盘由用户自由处理，路径不进项目流水线。
10. 通过后递增并回写包档案里的 `version_code_next`。

### 共存版特别说明

共存版的核心原则是：**官方微信负责官方身份和第三方跳转，隐私版负责隐私功能**。

注意：

- 共存包名首次确定后不得再改；改包名等于新产品线。
- 共存版签名首次确定后不得再换；换签名无法覆盖安装。
- 共存版更新只覆盖同一共存包名，不影响官方微信。
- 官替版更新只覆盖 `com.tencent.mm`，不影响共存版。
- 共存版上线前必须单独验证登录、推送、聊天记录迁移、微信内支付、第三方支付跳转边界。

### 发布口径

允许对外说：

- 官替版和共存版可分开更新。
- 后续 bug 修复可在同一版本线内覆盖安装。
- 每个客户包有固定签名和固定包名，避免升级混乱。

禁止对外说：

- 禁止承诺官替版和共存版可互相覆盖。
- 禁止承诺换签名后还能无损升级。
- 禁止在服务器真锁未完成前宣称“授权无法破解”。
