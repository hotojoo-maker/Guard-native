# VERSION_UPGRADE_SOP — 换微信版本「找 hook 点」标准作业流程

> **状态**：草稿 v0.1（2026-06-29 建，镜像SOP官 / 并行窗4）
> **性质**：纯流程文档，**不改任何代码**。本 SOP 只串联现有工具，不新增功能。
> **触发时机**：微信发新版（如 8.0.71 → 8.0.72）、需把模块从旧底座迁到新底座时。
> **一句话**：新版官方 APK → jadx 反编译 + `check_classmap.ps1` 半自动追混淆名漂移 → 更新 `HOOK_MAP_8071` + classmap 字典 → regen registry → 重打包装机验证。

---

## 〇、为什么需要这条 SOP

微信每个大版本**类名/方法名/字段名全部重新混淆**（CLAUDE.md 铁律 9：禁止把旧版架构直搬新版；8.0.66 / 8.0.70 / 8.0.71 三套混淆名互不通用）。
所以「换版本」的真正工作量 = **重新定位每个 hook 点在新版里的混淆名**，而不是改业务逻辑。本 SOP 把这件事固化成可复刻的 5 步，避免每次换版都从零摸索。

证据等级沿用项目统一口径（CLAUDE.md §三.五）：**L1** 动态（logcat/Frida）、**L2** 静态（jadx）、**L3** 推断、**L4** 待验证。任何新混淆名写入字典前至少要 **L2**。

---

## 一、流程总览

```
[1] 取新版官方 APK + jadx 反编译
        ↓
[2] check_classmap.ps1 半自动追漂移
        ├─ FORWARD MISSING  = 旧混淆名在新版失效（要重新找）
        └─ REVERSE UNDOC    = 代码里有未登记的混淆字面量（要补录）
        ↓
[3] 更新对照字典 docs/classmap/v8071.yaml + 权威图 HOOK_MAP_8071_AUTHORITATIVE.md
        ↓（改文档需用户同意）
[4] regen registry：改 registry_8071.json → 跑两个 gen 脚本（生成物，禁手改）
        ↓
[5] 重打包（→ guard-release 发版官）+ 装机 L1 验证
```

---

## 二、Step 1 — 取新版官方 APK + jadx 反编译

| 项 | 内容 |
|------|------|
| **输入** | 新版官方原版 APK（命名参考 `官方原版8.0.71-2026-5-19.apk`），由用户提供，禁止自行下载来路不明包 |
| **工具** | jadx（反编译 dex → Java） |
| **输出目录** | `jadx_8071_out/`（已在 `.gitignore` / `.cursorignore`：大、可重生成、不入库、不进 AI 索引）。换版本时可沿用此目录名，或新建 `jadx_<新版>_out/` 并同步两个 ignore 文件 |
| **预期** | 得到可全文检索的新版 Java 源，供后续 grep 混淆名 |

> ⚠️ jadx 具体命令以本机安装为准（本 SOP 不锁定命令行，避免编造）。反编译产物体积大，只留本地。

---

## 三、Step 2 — `check_classmap.ps1` 半自动追混淆名漂移

这是本 SOP 的核心工具步骤。`tools/check_classmap.ps1` 对 `docs/classmap/v8071.yaml`（人看的混淆名对照字典）与项目 Java 源做**双向核对**：

| 方向 | 含义 | 换版本时的解读 |
|------|------|------|
| **FORWARD** | 字典里每个 `status: ok` 的符号必须仍出现在 `src/main/java/com/ghost/assist` 某处；若列了 `refs` 文件，该文件也要含此字面量 | `[MISSING]` = 这个混淆名在**新版代码里被改名/删除** → 需到 jadx 产物里重新定位新名 |
| **REVERSE** | 扫 src 里短混淆字面量（正则 `"[a-z]{1,4}[0-9]{1,3}\.[a-zA-Z]\w*"`，如 `kc5.v0`）凡未登记进字典 → 警告 | `[UNDOC]` = 代码里有未记录的混淆名 → 换版后补录新名时顺手补齐 |

**运行**（PowerShell，项目根目录）：

```powershell
pwsh tools/check_classmap.ps1
```

可选参数：`-ClassmapPath <yaml>`、`-SrcRoot <java根>`（默认分别指向 `docs/classmap/v8071.yaml` 与 `src/main/java/com/ghost/assist`）。

**退出码**：`0` = 干净（无 MISSING）；`1` = 至少一个 MISSING（字典与代码已漂移）；`2` = 路径错误。

> **本 SOP 不依赖「跨版本 diff」**：当前 `check_classmap.ps1` 是「字典 ↔ 当前代码」一致性核对，**不做新旧两版 APK 的自动 diff**。把新旧版混淆名自动对比是**新功能、单独排期**，本次不碰该脚本（红线）。换版本时用「FORWARD MISSING 逐条人工到 jadx 里找新名」即半自动闭环。

---

## 四、Step 3 — 更新对照字典 + 权威 Hook 图

> ⚠️ **改这两份文档前必须取得用户明确同意**（CLAUDE.md G5 + HOOK_MAP 文件自带铁律 5 + 总调度写入门控）。

### 4.1 更新 `docs/classmap/v8071.yaml`（人看的对照字典）

- 对 Step 2 的每个 `[MISSING]`：到 jadx 产物里定位新版混淆名（至少 **L2** 静态证实），更新该 symbol 的 `name`，保留 `desc/feature/refs`。
- 旧名若仅作跨版本考古，置 `status: deprecated`（脚本不再报缺失）。
- 对每个 `[UNDOC]`：确认用途后补一条 `- name: ... / kind / desc / feature / refs / status: ok`。
- 字段口径见该 yaml 头部「字段说明 / 维护铁律」。**改前改后都要再跑 `check_classmap.ps1` 确认不漂**。

> **文件名注意**：当前工具链硬编码 `v8071` / `registry_8071`。换版本有两种策略——
> - **(a) 原地升级**：直接改 `v8071.yaml` / `registry_8071.json` 内容（工具零改动，最省事，但文件名滞后于真实版本号）；
> - **(b) 新建 `v<新版>` 系列文件**：更清晰，但要调 gen 脚本与 check 脚本的默认路径（属改代码、单独排期）。
> 本 SOP 推荐先走 **(a)** 零改工具完成升级，文件名重命名列为单独清理项。

### 4.2 更新 `docs/HOOK_MAP_8071_AUTHORITATIVE.md`（权威 hook 图）

- 同步〇版本锚定速查（目标版本 / 决策记录 D-xxx / 底座设备）。
- 8 大功能表里，把变动的混淆类名/字段/调用链按新版改写。
- 严守该文件状态标记：`✅` 有 logcat/装机原文 · `🟡` 代码已写/部分实证 · `❌` 未实装 · `⚠️` 缺数据。新版未经 L1 验证前，老的 `✅` 应降级标注。

---

## 五、Step 4 — regen registry（生成物，禁手改）

混淆名落到运行时靠 registry。**单一真源 = `native_core/registry_8071.json`（明文）**；两份生成物**永不手改**，改 json 后重跑脚本即可（杜绝明文/嵌入漂移）。

1. **改明文 SSOT**：`native_core/registry_8071.json` 的 `entries.*`（如 `conv.list.adapter_class`、`moments.feed.item_friend`）按新版混淆名更新。
2. **生成 Java fallback**：

```powershell
python tools/gen_registry_fallback.py
```

   → 生成 `src/debug/.../core/RegistryFallback.java`（真字面量）+ `src/release/.../core/RegistryFallback.java`（全 `""`，fail-closed）。

3. **生成加密 .inc**：

```powershell
python tools/gen_registry_cipher.py
```

   → 生成 `native_core/src/registry_cipher.inc`（AES-GCM，供 `registry_loader.cpp` 用；key 走 `derive_registry_key()` 散段派生 + 签名证书 SHA-256 折入，不落明文）。

> ⚠️ `gen_registry_cipher.py` 的 key 派生折入**模块签名证书 SHA-256**（`kdf_common.CERT_SHA256`）。
> - **release**（官替 / 共存 共用 v2，2026-06-30）：必须等于 `signing/guard-native-official-release.jks` 的证书指纹 = `e3e13a49`（详 `docs/RELEASE_LINE_SSOT_发行线统一口径.md` §2）；这也是 `kdf_common.CERT_SHA256` 的默认值，无需手动改。
> - **debug**（仅模块 smoke）：会用 `~/.android/debug.keystore` 或 `signing/guard-native-debug.keystore` 的 `ca421ec3`；与 release registry cert 不一致 = 故意 scatter，是预期。
> - 换正式 keystore 时要按脚本头部说明用 `keytool` 重取 SHA256 再 regen，否则重打包后 registry 解不开。

---

## 六、Step 5 — 重打包 + 装机 L1 验证

| 项 | 归属 | 说明 |
|------|------|------|
| **重打包（LSPatch 打包型 APK）** | → `guard-release_发版` skill | 真锁种子轮换 + 官替/共存双版本 + LSPatch 打包 + miyou-server 同步，本 SOP 不复制其步骤（G10 一结论一处） |
| **装机 + logcat 验证** | → `guard-terminal_终端操作` skill | 标准装机验证流程（场景 A）；逐个 hook 点确认 L1 命中 |
| **KPI 体检** | → `guard-review_质检门控` / 防封官 | 出包前 `frida_stats.js`：verifiedbootstate ≤ 38、PROP ≤ 220、normsg ≤ 5124、CONN ≤ 0.5 |

新版每个 hook 点必须有 L1 装机日志原文才能在 HOOK_MAP 标 `✅`；仅代码改完 = `🟡`。

---

## 七、红线与边界

| # | 红线 |
|:--:|------|
| 1 | **禁止从旧版直搬混淆名**（铁律 9）。8.0.66/8.0.70 的类名只能作 `deprecated` 考古对照，不得当新版答案 |
| 2 | **改 `HOOK_MAP_8071` / `classmap.yaml` 需用户明确同意**（G5 + 文件自带铁律） |
| 3 | **registry 两份生成物禁手改**——只改 `registry_8071.json` 再 regen |
| 4 | **新混淆名入字典至少 L2**（jadx 静态证实）；标 `✅` 必须 L1（装机/Frida 原文） |
| 5 | **本 SOP 不改 `check_classmap.ps1`**；跨版本自动 diff 是单独排期的新功能 |
| 6 | 换 keystore → 必须按 `gen_registry_cipher.py` 头部重取证书 SHA256 再 regen registry |

---

## 八、相关文件索引（只引用，不复制）

| 文件 | 作用 |
|------|------|
| `tools/check_classmap.ps1` | 字典 ↔ 代码混淆名双向核对（Step 2） |
| `docs/classmap/v8071.yaml` | 人看的混淆名对照字典（Step 3.1） |
| `docs/HOOK_MAP_8071_AUTHORITATIVE.md` | 8.0.71 权威 hook 图（Step 3.2） |
| `native_core/registry_8071.json` | registry 明文单一真源（Step 4） |
| `tools/gen_registry_fallback.py` | 生成 RegistryFallback.java（Step 4.2） |
| `tools/gen_registry_cipher.py` | 生成 registry_cipher.inc（Step 4.3） |
| `docs/archive/wechat_8066/VERSION_CLASSMAP.md` | 8066 历史对照（仅考古，禁直搬） |
| `.cursor/skills/guard-release_发版/SKILL.md` | 重打包出包流水线（Step 5） |
| `.cursor/skills/guard-terminal_终端操作/SKILL.md` | 装机 + logcat 验证（Step 5） |

---

## 九、待办挂钩

- ✅ **已落（2026-07-02）**：`guard-execute-one_单任务执行`（§文档车道下）与 `guard-terminal_终端操作`（§找 hook 指针段）均已加「换微信版本 → 先读 `docs/VERSION_UPGRADE_SOP.md`」指引；同轮把 terminal 的找 hook 探针（场景 A/B + frida spawn/warm-attach + KPI 抽检）挪到 `guard-terminal_终端操作/PROBING_找hook探针.md`（子文档，维护期不自动加载、版本适配时按需 Read）。两镜像已 `sync_skills.ps1` 同步。
