# 任务：anti-ban-gate-extract — A2 防封时间闸拆出 GuardRuntime

> 创建：2026-06-29
> 难度：⭐⭐⭐（中风险 · 拆类 + 改 3 处调用方 · 行为零变更）
> 依赖：无（A `devin/debug-gate-unify` 已合，C `fc5g-anchor-merge` 还没起 · 互不影响）
> 基线：`origin/main`（或最新 main）

---

## 0. 一句话目标

把 `core/GuardRuntime.java` 里 A2 防封时间闸的所有代码（≈ 113-238 行整块）抽到新文件 `core/AntiBanGate.java`，`GuardRuntime` 回归纯「配方出口」职责。**行为零变更**——所有 7 个方法的签名、逻辑、日志格式都保持原样，只换包路径 / 类名。

---

## 1. 允许读取的文件（白名单，超出范围禁读）

```
.devin/GUARD_RULES.md                            ← 开工必读
.devin/CONNECT.md                                ← 项目规约
src/main/java/com/ghost/assist/core/GuardRuntime.java     ← 主要工作目标（搬出 A2 块）
src/main/java/com/ghost/assist/core/LeaseClock.java       ← A2 闸调用方（只读签名）
src/main/java/com/ghost/assist/core/CompatProbe.java      ← A2 闸调用方（只读签名）
src/main/java/com/ghost/assist/ModuleMain.java            ← 3 个调用点要改
src/main/java/com/ghost/assist/core/A2SignatureSpoof.java ← 调用点要改（只改调用引用，不读其他逻辑）
src/main/java/com/ghost/assist/core/A2PkgPathSpoof.java  ← 调用点要改（注释引用 GuardRuntime.isAntiBanReady）
build.gradle                                     ← 编译三变体用（只读）
```

**绝对禁读**（即使工具看到也跳过）：
```
signing/**
native_core/registry_cipher*.inc
src/main/java/com/ghost/assist/net/**           ← 接口名按 GuardRuntime 现有代码原样抄，禁读实现
src/main/java/com/ghost/assist/moduleD/**       ← 跟本任务无关
src/main/java/com/ghost/assist/moduleE/**
```

---

## 2. 具体工作

### Step 1：新建 `src/main/java/com/ghost/assist/core/AntiBanGate.java`

把 `GuardRuntime.java` 第 113-238 行整块（含注释）搬到新文件 `AntiBanGate.java`。

包含 7 项：

| 符号 | 类型 | 行 |
|---|---|---|
| `A2_SIG_GATEWAY` / `A2_SIG_OFFICIAL_DER` | public static final String | 131-132 |
| `ANTIBAN_GRACE_FIRST_MS` / `ANTIBAN_GRACE_EXPIRED_MS` | private static final long | 135-136 |
| `isAntiBanReady(Context, String)` | public static | 138-148 |
| `isWithinAntiBanWindow()` | private static | 158-168 |
| `evalAntiBanWindow(...)` | private static（**注意：纯函数，给 DEBUG branch test 用**） | 176-189 |
| `antiBanGateSelfTest(String, Context, String)` | public static | 200-213 |
| `antiBanBranchSelfTest(String)` | public static | 221-233 |
| `branchCheck(String, String, boolean, boolean)` | private static | 235-238 |

**包路径**：`com.ghost.assist.core`（同包，无 import 变化）。

**注释保留**：第 113-128 行那段「A2 vs 隐私两套失败哲学」+ D-020 决策摘要整块原文抄过去——这是 SSOT 级注释，丢了等于丢决策史。

### Step 2：清 `GuardRuntime.java`

删掉第 113-238 行整块（含注释、含 `}` 之前那个空行）。`GuardRuntime` 末尾只剩配方出口相关代码（`getRecipe` / `hasRecipe` / `getRecipeOrFallback` / `getRecipeListOrFallback` / `isStrictRecipeMode` 等）。

### Step 3：改 3 个调用方

逐个 grep `GuardRuntime.isAntiBanReady` / `antiBanGateSelfTest` / `antiBanBranchSelfTest` / `A2_SIG_GATEWAY` / `A2_SIG_OFFICIAL_DER`，把 `GuardRuntime.` 前缀换成 `AntiBanGate.`：

- `ModuleMain.java`
- `core/A2SignatureSpoof.java`（只改 `GuardRuntime.` → `AntiBanGate.`，**别动其他逻辑**）
- `core/A2PkgPathSpoof.java`（注释引用 `GuardRuntime.isAntiBanReady`，改前缀保持注释准确）

> 注：`debug/DebugServer.java` 只用 `GuardRuntime` 的配方出口方法（`getActiveRegistrySummary`/`isConfigReady`/`getRecipe`，重构后留在 GuardRuntime），**无 A2 符号、无需改**。

### Step 4：编译验证

跑三变体：

```bash
./gradlew assembleOfficialDebug assembleCoexistDebug assembleOfficialRelease
```

三个都过 = OK。

---

## 3. 关键约束

| 约束 | 说明 |
|---|---|
| **行为零变更** | 7 个方法签名、可见性、逻辑、日志 tag (`NCL` / `ANTIBAN-GATE` / `ANTIBAN-BRANCH`)、`branchCheck` 输出格式必须 byte-for-byte 等价 |
| **`net.EnvelopeStore` 只调不读** | 调用 `isAuthorizedNow` / `isCardRevoked` / `getCardRevokedAt` / `hasToken` / `getLicenseExpireSec` 五个方法的方式按 `GuardRuntime` 现有代码原样抄；**禁止打开 `net/` 任何文件** |
| **注释保留** | 113-128 行的 D-020 决策注释整块抄；113 行那个 `// ── A2 防封授权闸（Route B · 本地完整性 + 时间闸）─` 题头改成 `AntiBanGate.java` 顶部的 javadoc |
| **可见性** | `evalAntiBanWindow` 在原文件是 `private`，搬到新类后还要被 `antiBanBranchSelfTest` 调，保持 `private static` 即可（同类内可调） |
| **不动 A2SignatureSpoof 其他逻辑** | 只替换 `GuardRuntime.` 前缀，`OFFICIAL_DER_HEX` / 签名灌注逻辑等一律不碰 |
| **不重复造 `evalAntiBanWindow`** | 它是纯函数，是为 DEBUG branch test 设计的「无 IO 可单测」入口，是设计意图，**不要 inline 或合并到 `isWithinAntiBanWindow`** |

---

## 4. 完成标准

- [ ] `core/AntiBanGate.java` 新建，7 个符号 + 注释全搬
- [ ] `core/GuardRuntime.java` A2 块（113-238）清空，回归纯配方出口
- [ ] 3 个调用方（ModuleMain / A2SignatureSpoof / A2PkgPathSpoof）`GuardRuntime.` → `AntiBanGate.` 全替换（DebugServer 仅用配方出口方法、无 A2 符号，不改）
- [ ] `grep -rn "GuardRuntime\.\(isAntiBanReady\|antiBanGateSelfTest\|antiBanBranchSelfTest\|A2_SIG_\)"` 返回 0 命中
- [ ] 三变体编译全过（officialDebug / coexistDebug / officialRelease）
- [ ] commit + push `devin/anti-ban-gate-extract`
- [ ] PR body 列：影响面（4 个 java 文件）+ 编译结果 + SSOT 落档（`TASK_BOARD.md` `P_AntiBanGateExtract`、`PROTECTION_MAP.md §10.7` 2026-06-29 段、`P_AntiBanGate/worklog.md` 加一笔）

---

## 5. 不做的事（明确范围外）

- ❌ 不要改 `A2SignatureSpoof` 的签名灌注逻辑
- ❌ 不要改 A2 闸的判定逻辑（72h / 7d / fail-OPEN 行为全保留）
- ❌ 不要 inline `evalAntiBanWindow`
- ❌ 不要顺手做 C `fc5g-anchor-merge`（另一个任务）
- ❌ 不要 push 到主仓 96 处脏改动
- ❌ 不要重生成 registry_cipher（要 server seed，禁入）
