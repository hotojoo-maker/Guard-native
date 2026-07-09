# result · P_HotUpdateFreeze 装机验证（L1）

> ⚠️ **2026-07-05 决策变更（用户指令 · Chat-mcp `guard_native-F1`，详见 `DECISION_LOG.md` D-033）**：拆成两档独立开关——
> - **远程自动热更新（Tinker：`p53.j.b`/`m53.d0.j`/`m53.d0.d`）** → 新开关 `isAutoHotUpdateFreezeEnabled()` 默认 `false` = **放行**（observe，只 log 不拦）。
> - **整包 / 手动点「检查更新」（`fl4.o.Wg`/`fl4.o.Bg`）** → 沿用 `isHotFreezeEnabled()` 默认 `true` = **仍冻结**（保留 07-05 前行为）。
> 所以以下 §2「Tinker `p53.j.b → blocked`」是**改前历史实证**；改后 Tinker 走 observe，整包 `fl4.o.Wg → blocked` 仍生效。

> 2026-07-01。设备：小米9 `609b4b18` / Android 11 / 8.0.71。出货形态 = LSPatch 重打包 release。
> 结论：**Tinker + 整包更新 两通道 L1 实证冻结；libcso 观测；官替 + 共存 双版本全功能验证通过。**

---

## 1. 验证矩阵

| 版本 | 包名 | certBind | role | A2 | HUF | registry | 结论 |
|------|------|----------|:--:|:--:|:--:|:--:|:--:|
| 官替 release | com.tencent.mm | e3e13a49 | 1 MAIN | installed | mode=FREEZE 6 钩 | recipeOk=true | ✅ |
| 共存 release | com.tencent.mn | e3e13a49 | 1 MAIN | installed(self=mn) | mode=FREEZE 6 钩 | recipeOk=true | ✅† |

> † 共存 ✅ = **2026-07-01 cert 修复后**复测达成（Vchat AK53）。初稿曾误标共存绿（实测旧装机包 certBind=a40da80a、A2 散沙；疑把官替日志当共存）。修复 = 克隆宿主用 official jks 重签 e3e13a49 再 LSPatch（见 §5 末条 + `logs/coexist_verify_20260701.txt`）。official `self=mm`、coexist `self=mn`，两版分别留证。

---

## 2. HUF 冻结 L1 证据（官替）

```text
[HUF] install begin (mode=FREEZE)
[HUF] hook ip.g.a / p53.j.b / m53.d0.j / m53.d0.d / fl4.o.Wg / fl4.o.Bg installed
[HUF] tinker p53.j.b -> blocked (checkAvailableUpdate)     # Tinker 查更被源头掐，下游 d0.j/d 未 fired
[HUF] fullapk fl4.o.Wg -> blocked (checkUpdate)            # 整包更新被掐，用户实测「点检查更新不再后台下载」
```

- OBSERVE 期（新装冷启）曾抓到 Tinker 三钩全 fired（`p53.j.b`→`m53.d0.j`→`m53.d0.d`）= 新装时官方真拉补丁+装 → 证明威胁真实存在、且 freeze 能在源头掐住。
- 证据日志：`logs/huf_updatebtn_live_20260630.txt`、`logs/huf_fullapk_blocked_20260630.txt`、`logs/huf_freshinstall_20260630.txt`

## 3. 共存全功能 L1（com.tencent.mn）

```text
[native] certBind=e3e13a49
[native] role=1 (MAIN)
[A2SIG] installed (self=com.tencent.mm, der=751B)
[HUF] install begin/done (mode=FREEZE)
[hb] registry after seed entries=5 [conv.list][moments.feed][contact.address][search.gateway][a2.sig] recipeOk=true
```

- 证据日志：`logs/coexist_verify_20260701.txt`
- 授权后隐私链（MomentsFilter/ConvFilter/ContactFilter/SelectContactFilter/AntiRecall/CallGuard/PushFilter/MRD）+ E2 FakeLocation + E3 FakeBalance 均装（官替日志 `logs/guanti_features_20260630.txt` 实证）。

---

## 3.1 07-02 双版本冷启现场复验（live logcat 摘录 · 永久存证）

> 设备小米9 `609b4b18` / 用户手动冷启（共存未用 adb 拉起）。原始日志 `logs/live_verify_20260702_ncl.txt`（NCL 过滤）+ `logs/live_verify_20260702_full.txt`（全量，均 gitignored）；关键行摘此以进 git 历史。

```text
官替 com.tencent.mm（pid 31345 · 11:03）:
[native] certBind set sha256[0..3]=e3e13a49
[native] role=1 (expect 1=MAIN) / BATCH1_VERIFY PASS
[A2SIG] installed (self=com.tencent.mm, der=751B)
[hb] registry after seed ... entries=5 [conv.list][moments.feed][contact.address][search.gateway][a2.sig] recipeOk=true
[hb] synced tier=1 lease=1782965004 risk=正常
[hb] health report sent=true result=OK lease=正常 risk=CLEAN registry=ready

共存 com.tencent.mn（pid 772 · 11:05，跑在 cache/lspatch/origin）:
[native] certBind set sha256[0..3]=e3e13a49
[native] role=1 (expect 1=MAIN) / BATCH1_VERIFY PASS
[A2SIG] installed (self=com.tencent.mn, der=751B)
[A2PKG] installed (self=com.tencent.mn)
[hb] registry after seed ... entries=5 [...] recipeOk=true
[native:push] init=true role=2(expect 2=PUSH) hidden=true
```

---

## 4. 出货 APK

- 官替：**正确出货包 = `build/lspatch_out_official_fix/official_e3host_8071-439-lspatched.apk`**（host=`host_official_clean` 经 official jks **重签 e3e13a49** 后 LSPatch；文件签名 + 内嵌 `assets/lspatch/origin.apk` 双 = e3e13a49 → 预判运行时 certBind=e3e13a49，2026-07-01 AK53 验）。⚠️ **不可出货旧产物**：`build/lspatch_out_rel/wx_host`（debug ca421ec3）、`02_tools_工具/lspatch_out/host_official_clean...lspatched.apk`（**origin.apk=0fe4ff85 → 同 F-43 会散沙**）。官替装机 L1 ✅ **2026-07-02 现装官替包冷启实证**（设备 609b4b18 / pid 31345）：`certBind=e3e13a49` + `role=1 MAIN` + `BATCH1_VERIFY PASS` + `[A2SIG] installed(self=mm)` + `registry entries=5 recipeOk=true` + `tier=1 risk=正常`（证据 `logs/live_verify_20260702_ncl.txt`，摘录见 §3.1）。诚实边界：证的是「现装包在本机冷启正常」；现装包与出货候选 `official_e3host_8071-439` 的文件级同一性未逐字节比对。
- 共存：`build/lspatch_out_coexist_fix/mn_e3host_8071-439-lspatched.apk`（host=克隆 com.tencent.mn 8.0.71 **经 official jks 重签为 e3e13a49** 后再 LSPatch）。2026-07-01 装机 L1 全绿（§3 / `logs/coexist_verify_20260701.txt`）。⚠️ `build/lspatch_out_coexist/coexist_host-439-lspatched.apk` 是 debug `ca421ec3` 旧产物，不可出货。
- 出包：`assembleCoexistRelease`（模块 cert e3e13a49）+ **克隆宿主先用 official jks 重签** → `tools/lspatch_pack.ps1 -Flavor coexist -BuildType release -HostApk <重签宿主>`（release 模式 LSPatch `-k` 自动取 official jks，**不是 debug**）。

---

## 5. 过程中修的坑

- **MomentsFilter.java 缺 `BuildConfig` import**（潜在 bug，官替缓存逆过去没暴露、共存鲜编暴露）→ 已补 import（纯编译修复，不动 hook 逻辑）。
- **共存 `install -r` 留旧 dexopt/vdex → NoClassDefFound 启动崩**：clean uninstall + 重装解决（非克隆包问题）。
- **debug 包不能当官替出货**：debug cert ca421ec3 ≠ registry → A2 不装 → 授权异常；release（e3e13a49）才行。
- **共存克隆宿主签名 bleed-through（2026-07-01 · Vchat AK53 实测修复）**：克隆宿主 `mn_clean_origin`(a40da80a) 即便 LSPatch 用 `-k official jks`（输出文件签名确为 e3e13a49），运行时 `certBind` 仍读到 `a40da80a` → A2 cert mismatch → 散沙。根因：LSPatch 签名旁路(`-l 2`)在运行时按「宿主原始签名」上报，`certBind`（读宿主整包）拿到的是克隆原始章。**修复 = 先用 official jks 把克隆宿主重签成 e3e13a49，再 LSPatch**（只设 LSPatch `-k` 不够）。L1 实测（pid 23735）：`certBind=e3e13a49` + `[A2SIG] installed` + `recipeOk=true`，见 `logs/coexist_verify_20260701.txt`。

---

## 6. 诚实口径 / 待补

- 机制 L2 + 通道存在 L2；**无 L1** 证明官方已经此推过新检测 → **预防性冻结**。
- libcso 远程段（第3条线的远程下载）= observe-only，未精准冻（G3，探针 `recon/probe_libcso_remote.js` 就绪，待服务器真推/新装触发）。
- 发版前回归：两 flavor + 隐私链 + KPI（`frida_stats.js`）按发版门控补跑。
