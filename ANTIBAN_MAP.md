# ANTIBAN_MAP — 防封官权威账

> Last updated: 2026-06-18 / B58  
> 维护角色：`guard-antiban_防封官`  
> 当前底座：微信 8.0.71 官方 `com.tencent.mm`

> **★单一数据源约定（2026-06-21 收口）**：防封**研究结论**（轴 / 算法 / 命脉 / 喂什么 / 已坐实）唯一真源 = **研究线 `C:\Users\Me\Desktop\防封_反检测线\防封权威账_2026年6月.md`（§0–§十七）**。本文（ANTIBAN_MAP）**降级为主线落地账**：只镜像「主线接到哪、装机回归状态」+ 指回研究线权威账，**不再并行维护研究结论**。下方 06-18/B58 研究段 = 历史快照，**已冻结**（与研究线冲突一律以研究线权威账为准）。
>
> **研究线最新水位线回灌（2026-06-21）**：
> - 隐藏血管：静态全 190 .so + 动态广扫(`so_identity_scan`) **双证无新增** → 检测仍**单血管 = normsg/c$p**（研究线 §十二）。
> - 包名命门松动：native `/proc/self/cmdline` 冷启动补验 **0 读** → 包名检测走 Java `getPackageInfo`、c$p 咽喉 spoof 够，**不因包名被迫官替**（研究线 §13.4）。
> - 三方跳转：跨进程，共存收不到 / 官替需 root 系统级 → **裁定放弃硬目标**（研究线 §十五）。
> - 落地收敛：三轴（签名/android_id/包名）全在 `getPackageInfo`/`getApplicationInfo` afterHook 喂官方，落点 `ModuleMain.bindSigningCert` + 发版 `tools/gate_three_axis.js` 自检门（研究线 §十六/§十七）。

本文件 = Guard Native 防封**落地状态账**。`JIDE_ANDROID_ACC_STRIKE_MAPPING_20260617.md`、`tools/*.log`、外部 8071 报告和 skill 只作为证据来源；**研究结论以研究线权威账为准**，本文只跟主线落地（下方 06-18 段为冻结历史）。

## 一、判定纪律

1. 任何结论必须带版本底座、证据等级、更新时间。
2. 客户端采集、组包、报送链可做到 L1；服务器最终如何判断是黑盒，**没有石锤证据就不写推测**。
3. 不能把密文、函数名、栈名、单个常量直接写成封因。
4. 未建立 P18 KPI 基线前，禁止宣称某方案“防封安全 / 长命稳定”。
5. 官替版、共存版、竞品样本只能做对照，不能替代官方 8.0.71 基线。

证据等级沿用防封官 skill：

- L1：动态已证实，真机 / Frida / logcat 直采。
- L2：静态已证实，jadx / raw dex / Ghidra 等可定位。
- L3：高概率推断，多证据收敛但仍未直接证明。
- L4：待验证，只有线索或理论风险。

本文件对服务器侧规则不使用 L3 包装推测；没有服务器侧石锤时，一律写“服务器侧用途未证”。

允许写“最可能候选 / 优先级 / 遏制点”，但必须满足两条：一是基于客户端 L1/L2 证据；二是明确标为模拟和验证方向，不能写成服务器结论。

## 二、当前水位线

最新水位线：**B56 官方 8.0.71 检测面复验**。

主要证据：

- `tools/normsg_mm_z3_B56_cli_20260618.log`
- `tools/normsg_sig_B56_cli_20260618.log`
- `tools/normsg_boot_probe_B56_cli_20260618.log`
- `tools/normsg_event_probe_v2_B56_20260618.log`
- `JIDE_ANDROID_ACC_STRIKE_MAPPING_20260617.md` 附录 H

## 三、字段分类

当前只按客户端可见事实分类，不写服务器判定逻辑。

### 1. 可区分非官方身份的数据

这些字段 / 信号只能说明客户端采集到“身份不自洽或非官方差异”，不能直接写成服务器封因：

- 包名：`k33`，例如 `com.tencent.mm` / `com.tencent.mn`。
- 数据路径：`k49`，例如 `/data/user/0/com.tencent.mm/` / `/data/user/0/com.tencent.mn/`。
- sourceDir / 安装路径：出现在 `checkSoftType` / `plugin.normsg.f.run` 相关链路，字段含义待继续拆。
- 自身签名：`getPackageInfo(GET_SIGNATURES)` / `Signature.toByteArray len=751`，已进入 normsg / protobuf / network 链。
- versionCode：`k57=3080`，只作为版本身份字段，不单独解释为风险。

### 2. 异常设备 / 环境数据

这些字段 / 信号只能说明客户端采集到设备或运行环境状态，不直接写成封因：

- boot / verified boot：`ro.boot.verifiedbootstate`、`ro.boot.veritymode`、`ro.boot.vbmeta.device_state`、`ro.bootloader`。
- build / product / gsm：`ro.build.*`、`ro.product.*`、`gsm.*`。
- framework 扫描：`/system/framework/*`、`/system/framework/arm*`。
- fd 枚举：`/proc/self/fd/*`。
- 运行时污染候选：`HookBridge` / `LSPHooker_` 栈痕迹，当前只算待验证采集面，不写成已上报或已判定。

## 四、大动脉候选与遏制点

防封研究不是泛反编译，而是尽量模拟官方可信客户端，沿大动脉找最明显、最可能、最有证据的差异点，再把差异点变成可验证的遏制点。

当前优先级：

1. **应用身份轴**：包名、数据路径、sourceDir、签名、versionCode、安装来源。
   - 已有证据：`k33/k49` 明文、签名读取链、`plugin.normsg.f.run` / `checkSoftType` 相关链。
   - 遏制点候选：包名 / 路径一致性、签名一致性、sourceDir / 安装路径一致性。
   - 边界：只能说这些是最明显的非官方身份数据，服务器侧用途未证。
2. **设备环境轴**：boot / verified boot / build / product / gsm。
   - 已有证据：B56 boot probe 采到 `ro.boot.verifiedbootstate`、`ro.boot.veritymode`、`ro.boot.vbmeta.device_state` 等。
   - 遏制点候选：测试设备与真实用户设备差异、Magisk/root/解锁状态、KPI 读取次数增量。
   - 边界：这是异常设备 / 环境数据，不直接等于账号风险结论。
3. **运行时污染轴**：fd、classloader、stacktrace、HookBridge / LSPHooker_。
   - 已有证据：fd 枚举 L1；签名链旁路栈中出现 HookBridge / LSPHooker_。
   - 遏制点候选：调试工具残留 fd、hook 栈痕迹、非必要进程注入。
   - 边界：当前只算采集面或候选风险，是否进入最终报送链还要继续抓。
4. **行为自动化轴**：点击、触摸、无障碍、传感器、显示 / 截图。
   - 已有证据：AccStrike / Accessibility 方向是独立线索，和当前 normsg 身份轴不是同一条链。
   - 遏制点候选：自动化频率、调用栈、无障碍配置、行为密度。
   - 边界：当前不是 B56 最强证据链，优先级低于身份轴和环境轴。

模拟方法：

1. 先做官方原版基线，记录字段和上报链。
2. 再做自有 / 共存 / 官替对照，只看哪些字段变了。
3. 对每个变化字段问四件事：谁采集、采了什么、是否组包、是否进 network。
4. 只把“字段变化 + 上报链”写成证据；把“服务器如何用”留为空白，直到有石锤。

## 五、已确认结论

### 1. normsg 是当前反检测大动脉

结论：微信 8.0.71 的身份、环境、签名、事件检测主线应优先围绕 `libwechatnormsg.so`、`WCProbe`、`u.z3/Y8/uc`、`plugin.normsg.f.run`、`toProtoBuf -> network` 查。

证据：L1/L2，2026-06-18。

- L1：B35/B56 Frida 调用栈和明文 log。
- L2：8071 静态结构、raw dex 索引。

### 2. 共存版包名 / 路径轴暴露

结论：共存版 `com.tencent.mn` 会在 z3 设备指纹明文里暴露：

```text
k33 = com.tencent.mn
k49 = /data/user/0/com.tencent.mn/
```

证据：客户端 L1，2026-06-18，`tools/normsg_dump_20260618.log`。

边界：只能证明 `k33/k49` 被采集并进入明文设备指纹；不能写服务器如何判断。

### 3. 官替版只解决包名 / 路径轴，不解决签名轴

结论：官替版占用 `com.tencent.mm` 后，`k33/k49` 可呈官方身份；但微信 8.0.71 会读取自身 APK 签名，签名进入 `normsg -> toProtoBuf -> network` 链。签名属于可区分非官方身份的数据之一。

证据：客户端 L1，2026-06-18。

关键链：

```text
getPackageInfo(pkg=com.tencent.mm, flags=0x40)
Signature.toByteArray len=751
-> c$p.ad / c$p.aa / c$p.af
-> WCProbe.n / WCProbe.f / WCProbe.m
-> u.z3 / u.uc / plugin.normsg.f.run
-> w15.kg.toProtoBuf
-> network
```

边界：只能证明签名被读取并进入上报链；不能写服务器是否据签名判断或处罚。

### 4. 启动期环境检测面已实证

结论：8.0.71 官方微信启动期由 `libwechatnormsg.so` 发起系统属性读取、framework 扫描和 fd 枚举。

证据：L1，2026-06-18，`tools/normsg_boot_probe_B56_cli_20260618.log`。

已见属性：

```text
ro.boot.verifiedbootstate
ro.boot.veritymode
ro.boot.vbmeta.device_state
ro.bootloader
ro.build.*
ro.product.*
gsm.*
```

已见文件面：

```text
/system/framework/*
/proc/self/fd/0 ... /proc/self/fd/685
```

本轮未见 `libwechatnormsg.so` 直接 `lstat` `su/magisk/frida/xposed/riru/zygisk`，但这只代表当前窗口未见，不代表绝不存在其它检测路径。

### 5. `1934848488` 只能写成场景参数待证

结论：`1934848488` / `0x735371e8` 当前只能写成 `WCProbe.m / c$p.ae / c$p.af` 的场景参数或业务 type 待证。

证据：L1 客户端事件采集；字段含义待证，2026-06-18。

禁止写法：

```text
封禁码
异常码
反作弊命中码
```

原因：历史日志中同值也出现在业务缓存 key / 推荐卡场景；raw-dex fallback 未确认它的直接来源。

## 六、当前不允许宣称的内容

- 不允许宣称“官替版长命安全”。当前准确口径是：官替版包名 / 路径轴干净，但签名轴仍被采集并进入上报链。
- 不允许宣称“签名伪装必然安全”。它只是技术可行候选，仍需网络安全官 + 授权检查官共审，并验证 hook artifact 风险。
- 不允许宣称“未见 su/magisk/frida 文件扫描 = 不检测 root / 注入”。当前只证明本轮 `libwechatnormsg.so` 的 `lstat` 窗口未见。
- 不允许宣称“服务器封号规则已 L1 证明”。没有服务器侧石锤时，不写服务器判断逻辑，只写客户端采集 / 报送事实。
- 不允许在 P18 KPI 基线缺失时放行发版级防封结论。

## 七、当前阻塞项

1. P18 空白 LSPosed KPI 基线未建立。
2. `w15.kg.toProtoBuf` 入参字段还没完全拆成可读字段 / hash / 长度。
3. `HookBridge` / `LSPHooker_` 栈是否被 normsg 或 Matrix 采集为封控信号仍待专项验证。
4. 官替版签名伪装只完成可行性评估，未做控制变量账号结果。
5. `b56_static_index` 目前 raw dex 索引可辅助定位，但 `methods.tsv` / `native_methods.tsv` 仍基本为空，不能替代 JADX/Ghidra 切片。

## 八、下一步顺序

1. 补 P18 / `frida_stats.js` 空白 LSPosed KPI 基线。
2. 拆 `w15.kg.toProtoBuf` 和 `modelbase.p2.B2` 入参，确认 z3、uc、checkSoftType、签名链对应字段。
3. 做 LSPosed artifact 专项：`HookBridge`、`LSPHooker_`、fd、classloader、stacktrace 是否进入上报链。
4. 对官替版做控制变量：不做签名伪装、只做签名伪装、签名伪装 + 最小模块。
5. 每组都固定抓 z3/Y8、签名链、boot/fd、KPI、小号账号结果。

## 九、资料索引

内部：

- `.cursor/skills/guard-antiban_防封官/SKILL.md`
- `JIDE_ANDROID_ACC_STRIKE_MAPPING_20260617.md`
- `TOOLS_INDEX.md`
- `CLAUDE.md` §三 / §七
- `tools/dump_mm_z3.js`
- `tools/dump_sig_check.js`
- `tools/dump_normsg_boot.js`
- `tools/dump_normsg_event_probe_v2_B56.js`
- `tools/dump_normsg_f_run_loader_B56.js`
- `tools/b56_static_index/anchors.md`

外部只读资料以 `PROJECT_INDEX.md` 为准；若 skill 与 `PROJECT_INDEX.md` 路径冲突，先核对真实路径再更新 skill。
