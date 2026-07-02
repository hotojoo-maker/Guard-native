# 任务 cert-sync-v1 · 证书三端同步收口

> ⚠️ **已被 D-026 (cert-converge-v2) 取代（2026-06-30）**：本卡按「两把印章两套配方」（官替 `e3e13a49` / 共存 `8f47a47a`）写就；现口径 = **官替 + 共存 release 共用 `e3e13a49` 一套配方**，共存 `8f47a47a` 已退役。下文表格/命令仅作历史留档，现状真源以 `docs/RELEASE_LINE_SSOT_发行线统一口径.md` v2 + `DECISION_LOG.md` D-026 为准。
>
> **开工前必读** `.devin/GUARD_RULES.md`，严格遵守（最小读取 / 禁止推测 / 命名规约无品牌名）。
> **工作分支** `devin/cert-sync-v1`（已建好）。完成后 `git push origin devin/cert-sync-v1` + `gh pr create`。

---

## 背景（必须理解，3 分钟）

本项目对官替版/共存版 APK 做了「证书绑定」防盗版——registry 解密 key 折入模块签名证书 SHA-256，重签名 = registry 散沙 = 功能静默全挂（F-31 教训）。

目前有 **2 条发行线**，各用独立 keystore 签名：

| 发行线 | 包名 | keystore 文件 | 证书 SHA-256 |
|---|---|---|---|
| **official**（官替） | `com.tencent.mm` | `signing/guard-native-official-release.jks` | `e3e13a4974fe4c40432a583b11309edb4705c8a50b03f474d6b693817adecf36` |
| **coexist**（共存） | `com.tencent.mn` | `signing/guard-native-coexist-release.jks` | `8f47a47afde078c47713db78668e6ed9ab027bc9b86d58a9e646a1f3998d5685` |

证书 SHA-256 必须在**三个地方严格一致**，否则 F-31：

```
① build.gradle GUARD_EXPECTED_CERT (per-flavor)
② tools/kdf_common.py CERT_SHA256 (python 脚本端)
③ native_core/src/registry_cipher.inc (构建产物，由脚本生成)
```

---

## 你需要做的（按序，每步确认再下一步）

### Step 0 · 核实当前三端状态（只读，先搞清现状再动手）

**只读以下 4 个文件**，其他不读：

1. `build.gradle`（找 `GUARD_EXPECTED_CERT`，各 flavor 是多少）
2. `tools/kdf_common.py`（找 `CERT_SHA256`，是多少）
3. `tools/gen_registry_cipher.py`（看它怎么引用 `kdf_common.CERT_SHA256`）
4. `native_core/src/registry_cipher.inc`（前几行，看 `GUARD_REGISTRY_REQUIRES_SERVER_SEED` 是否为 1）

列出核查表：哪端已对齐、哪端未对齐。

---

### Step 1 · 修复 coexist 线 kdf_common.py（核心）

`kdf_common.py` 的 `CERT_SHA256` 目前只有 official 证书（`e3e13a49...`）。
需要改为**按发行线传参**，让脚本能分别生成 official 和 coexist 的 registry cipher。

具体改动方向（你自行判断最优写法，但约束如下）：
- `gen_registry_cipher.py` 调用时能区分 `--release official` / `--release coexist`
- official 用 cert `e3e13a4974fe4c40432a583b11309edb4705c8a50b03f474d6b693817adecf36`
- coexist 用 cert `8f47a47afde078c47713db78668e6ed9ab027bc9b86d58a9e646a1f3998d5685`
- 不改 `derive_registry_key` / `derive_bootstrap_key` / `derive_wrap_key` 的实际算法（防三端漂移）

---

### Step 2 · 重新生成两条发行线的 registry_cipher

改完脚本后，分别跑（脚本里有服务器种子参数，按脚本已有用法跑；不知道怎么跑就停下来问我）：

```
python tools/gen_registry_cipher.py --release official  → native_core/src/registry_cipher.inc（官替）
python tools/gen_registry_cipher.py --release coexist   → 生成 coexist 版本（路径/命名按脚本输出）
```

⚠️ 如果脚本需要 server seed 参数而你没有 → **停下来，告诉我缺什么**，不要用占位值。

---

### Step 3 · 验证三端一致（运行自检）

```
python tools/gen_registry_cipher.py --self-test
```

若无 `--self-test`，手动确认：`build.gradle` 各 flavor 的 `GUARD_EXPECTED_CERT` 与 `kdf_common.py` 实际传入值逐字节相等。

---

### Step 4 · 核查 .gitignore 保护

确认以下三行在 `.gitignore` 中（只读不改，已经在了）：

```
signing/keystore.properties
signing/*.jks
```

如果不在 → 补上，并向我报告。

---

### Step 5 · 提交 + 开 PR

```bash
git add build.gradle tools/kdf_common.py tools/gen_registry_cipher.py native_core/src/registry_cipher.inc .gitignore
# 不要 git add -A（其他未点名文件禁止裹入）
git commit -m "feat(cert-sync): 证书三端同步收口 — coexist 独立 cert 折入 registry_cipher"
git push origin devin/cert-sync-v1
gh pr create --title "cert-sync-v1: 证书三端同步收口" --body "官替/共存各自 cert 折入 registry，三端 GUARD_EXPECTED_CERT / CERT_SHA256 / registry_cipher.inc 对齐。详见 .devin/tasks/cert-sync-v1.md。"
```

---

## 完成标准（PR 描述里必须逐条回答）

- [ ] `build.gradle` official flavor `GUARD_EXPECTED_CERT` = `e3e13a49...`
- [ ] `build.gradle` coexist flavor `GUARD_EXPECTED_CERT` = `8f47a47a...`
- [ ] `kdf_common.py` 两条线均有对应 cert 常量
- [ ] official `registry_cipher.inc` 按 official cert 重新生成（`GUARD_REGISTRY_REQUIRES_SERVER_SEED=1` 保留）
- [ ] coexist `registry_cipher` 按 coexist cert 生成
- [ ] `signing/*.jks` 未被 git 跟踪（`git check-ignore signing/guard-native-official-release.jks` 有输出）
- [ ] PR 描述写明哪些脚本跑了、哪些跳过、原因是什么

---

## ⛔ 本任务范围之外（不做）

- A2 防封线、`A2SignatureSpoof`、`isAntiBanReady`、`getPackageInfo` hook — 不在范围
- 装机验证（`recipeOk=true` logcat）— 需要连设备，你做不到，在 PR 描述标注「待人工装机」
- 删 Filter 明文 fallback — 另一个任务
- 推 main / merge PR — 由人工决定

---

## 拦截规则（遇到即停）

遇到下列任何一条 → **停手，向用户报告，等指示**，不要继续猜改：
- 脚本缺少参数或依赖（server seed、keystore 口令等）
- 三端对账发现不一致，但原因不明
- 改完某处发现其他文件也需要同步但不在上面列表里
- 两次修改后问题仍未解决
