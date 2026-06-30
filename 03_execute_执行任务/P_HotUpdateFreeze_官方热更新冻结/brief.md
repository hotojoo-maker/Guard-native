# brief · P_HotUpdateFreeze D3 落地设计

> 2026-06-30。**设计稿（未落码）**。证据级：jadx 8.0.71 实读（L2）。
> jadx 源：`I:\apk2\_3__D_wechat_ban\jadx_8071\sources\`
> 机制真源：`防封_反检测线\证据\HOTUPDATE_8071_20260622.md`（只链不复制）

---

## 0. 一句话

主进程纯 Java no-op 官方两条热更新通道（libcso 主 / Tinker 次）= 预防性冻结。**全程 hook Java 方法、不碰 native → 与 F-23 无关**。

---

## 1. libcso 通道（主）

### 锚点（L2 实读确认）

- 总开关：`com.tencent.mm.sdk.platformtools.z.f176124s`（静态 bool），在 `com.tencent.mm.app.v5.a(Context)` **第 57 行**置 `true`
- 入口校验：`ip.g.a(Application, String revision)` **第 35 行** `if (!z.f176124s) { ...skip...; return; }`（即开关 false → 整条 CSO 不初始化）

### 掐点

| # | hook | 类型 | 动作 | 风险 |
|---|------|------|------|:--:|
| **C2（首选）** | `ip.g.a(Application, String)` | before | `param.setResult(null)`（方法 void，直接 return） | 低 |
| C1（叠加/兜底） | `com.tencent.mm.app.v5.a(Context)` | after | 反射置 `z.f176124s = false` | 低 |

- **首选 C2**：单方法、void、一处拦死整条 libcso 启动注册（`CsoLoader.f30747d` 不再注册）。
- C1 作兜底：万一别处也读 `z.f176124s`，after-`v5.a` 把它按 false，覆盖面更广（但 `v5.a` 早于我方 install，需确认时序，见 §5）。

### 禁区

- libcso native（`CsoLoader.nativeInitialize` / `executeBy*` / mprotect）= **C11，干预型，禁**（F-23 不重碰）。libcso 本体无网络符号，断「远程更新」在 Java 即足够。

---

## 2. Tinker 通道（次）

### 锚点（L2 实读确认）

| 类.方法 | 行 | 角色 |
|---------|:--:|------|
| `p53.j.b(Map)` | 26–49 | checkAvailableUpdate；第 48 行 `j1.d().g(new n53.g(...))` 发查更 netscene |
| `n53.g.doScene(s, u0)` | 49 | prconfig CGI(type 3899) 实发 |
| `m53.d0.j(boolean)` | 442 | 处理 syncResponse（查更结果调度下载） |
| `m53.d0.d(File)` | 246 | 下载后 `verifyPatchMetaSignature`(249) → apply(`ni0.n.d`, 283) |

### 掐点

| # | hook | 类型 | 动作 | 风险 |
|---|------|------|------|:--:|
| **C1** | `p53.j.b(Map)` | before | setResult(null)（void，断查更源头） | 低 |
| C3 | `n53.g.doScene(s,u0)` | before | setResult(-1)（int，netscene 不发） | 中 |
| **C4** | `m53.d0.j(boolean)` | before | setResult(false) | 低 |
| **C5** | `m53.d0.d(File)` | before | setResult(null)（void，已下载不验不装） | 低 |

### ★ 更优：借官方自带闸（设计层强烈建议优先评估）

实读 `m53.d0.d(File)` 发现微信**自己**就有两个「主动关闭」开关：

- **第 265 行**：`if (g45.c.f246162e) { Log.w("...patch applying is blocked by assist"); return; }`
  → 反射置 `g45.c.f246162e = true`，**让微信自己的代码 return**，不 apply。比覆盖我方 hook 更隐蔽、更稳（用的是它设计内的 gate）。
- **第 446 行**：`j(boolean)` 开头 `if (RepairerConfig_Updater_CloseAutoPatch_Int == 1) return false;`
  → 走它的「关闭自动 patch」配置位。

> 这两个是**官方既有 kill 路径**，优先于硬 no-op；落地时让 worker 先确认 `g45.c.f246162e` 的可写性与读取时机（L4→需实机/静态再核）。

### 禁区

- `m53.d0.d` 内 `ShareSecurityCheck.verifyPatchMetaSignature` **改返回恒 true** = 危险（放行坏 patch），禁。
- 拦「已应用 patch 冷启加载」（`TinkerLoader` 早退 / C8/C11）= 第二梯队，与 LSPatch loader 冲突，**非必要不做**。

---

## 3. A2×Tinker 交叉（L3，落地必评）

- `m53.d0.d` 第 249 行 `verifyPatchMetaSignature(file)` = 验「patch 内证书 == 当前安装包签名」。
- 共存包（`com.tencent.mn`）签名非官方 → 本就验签失败、收不到 patch。
- **A2 把 `getPackageInfo` 签名 spoof 成官方后 → 此验签可能转为通过 → 重新打开 Tinker patch 通道**。
- **结论**：**上了 A2 必须同时掐 Tinker**（至少 C1/C4/C5 或 `g45.c.f246162e`）。否则 spoof 反而更易被推 patch。此为 L3 设计推断，须 8.0.71 动态验。

---

## 4. 与 LSPatch 共存（安全性论证）

- 我方模块经 LSPatch 打进 **base.apk**，不是 Tinker patch → 断 Tinker 下载/apply **不会卸掉我方代码**。
- 我方 hook 运行时类已用 `app.getClassLoader()`（`ModuleMain` 第 267–269 行注释在案：Tinker `DelegateLastClassLoader`）→ 冻结**新** patch 不影响**已合并**的运行时类加载。
- 故冻结「查更 + 下载 + apply」对我方零副作用；唯一要避开的是第二梯队「冷启 TinkerLoader 早退」（不做）。

---

## 5. 落地计划（改码前过 auth-review + 用户点头）

1. 新增 `com.ghost.assist.moduleB.HotUpdateFreeze`（与 B7 `UpdateGuard` 同包，分工：UpdateGuard=UI红点，本类=通道层）。
2. `ModuleMain` 在 `UpdateGuard.install(lpparam)` 旁加 `HotUpdateFreeze.install(lpparam, app.getClassLoader())`（Tinker 类用 app classloader）。
3. 每个 hook：`findAndHookMethod` / `hookMethod` 必 `catch(Throwable)`（F-25）；开关走 `AppConfig`（仿 `isUpdateRedDotEnabled`），默认可关，便于 A/B。
4. **时序核查**：C1（`v5.a` after 置位）需确认 `v5.a` 是否早于我方 `handleLoadPackage`；若早于 → C1 改为 install 时直接反射置 `z.f176124s=false`（而非 hook after）。C2 不受此影响（`ip.g.a` 在 CSO 首次触发时才调）。
5. 进程：仅主进程；`:push` 不装（铁律 6）。

---

## 6. DoD / 验证（设备侧，AI 碰不到）

- **G7 产品门**：掐 libcso C1/C2 + Tinker 后，官方客户端正常**登录 / 收发消息 / 朋友圈**（A/B：开关 on vs off）。
- **G3**：`adb shell ls -l <dataDir>/cso`、logcat 过 `Tinker.` / `MicroMsg.CsoStartup` / `prconfig` → 看冻结前后是否真有查更/落盘。
- 出包前 `frida_stats.js` 基线（F-22）。

---

## 7. 诚实口径

机制 L2 坐实、通道存在 L2；**无 L1** 证明官方已经此推过新检测 → **预防性冻结**，不写「不掐必被打」。
