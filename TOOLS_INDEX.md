TOOLS_INDEX — 工具/脚本/资源索引

> 维护人：guard-review_质检门控
> 规则：使用任何工具/脚本前先查本表，避免重造轮子
> 路径用绝对路径，**只读不复制**到仓库内

---

## 〇、v1 前置依赖（开 W1~W4 必须就位）

> **当前执行底座：微信 8.0.71（D-014）**。文档入口 → [`docs/README.md`](./docs/README.md)

| 依赖项 | 状态 | 落地路径 | 落地动作 |
|------|:--:|---------|---------|
| **8.0.71 官方包 APK** | ✅ 主线 | 设备装机 `com.tencent.mm` | 日常开发 / 装机验证 |
| **8.0.66 APK（可选）** | ⬜ 对比样本 | `06_refs_参考资料/apk_samples/wechat_8066.apk` | 仅 P18 版本 diff / 见 §A |
| **8.0.66 jadx** | ⬜ 可选 | `06_refs_参考资料/apk_samples/wechat_8066_jadx/` | W4 对比用 |
| 小米9 + Android 11 + Magisk + LSPosed | ✅ | — | 已就绪 |
| Frida CLI 17.11.0 + frida-server 17.11.0 | ✅ | — | 已就绪；B56 后旧 `.js` 取证脚本优先用 CLI，Python runner 需显式 Java bridge |

### §A 8.0.66 APK 落地清单（**可选**，非主车道）

**仅**版本对照 / 离线 diff 时需要；8071 实现 **不依赖** 8066 APK。

1. **下载途径**（按优先）：
   - apk2 项目库（如有）：`I:/apk2/` 下 grep `8.0.66`
   - E:/apk_diff/（只读样本库）下 grep `8066` / `8.0.66`
   - 历史构建：`I:/apk2_build/official/`
   - 第三方源：APKMirror / 旧版微信下载站（最后选项）

2. **落地动作**：
   - 拷贝到 `06_refs_参考资料/apk_samples/wechat_8066.apk`
   - SHA256 写到本文件 §A 末尾（防止后续误装他版）
   - 反编译：`jadx -d 06_refs_参考资料/apk_samples/wechat_8066_jadx/ wechat_8066.apk`
   - 跑完更新本表状态 ⬜ → ✅

3. **验证**：
   - `aapt dump badging wechat_8066.apk | grep versionName` → 应该输出 `8.0.66`
   - PackageName: `com.tencent.mm`

4. **SHA256**（落地后填写）：
   - APK: `<待补>`
   - jadx_out 目录大小: `<待补>`

---

## 一、本仓库自带

| 脚本 | 路径 | 用途 |
|------|------|------|
| 同步 skill | `./sync_skills.ps1` | `.cursor/skills → .claude/skills` 镜像 |
| Frida 朋友圈过滤 | `./docs/isolation/filter_moments.js` | F05 已验证 v21 |
| 伪装订位探针 v1（发位置消息层）| `tools/probe_loc_send_8071.js` | hook wy4.a/q2.F/kwebmap intent + 枚举 location/lbssdk 类（候选已证伪，留作回归）|
| 伪装订位探针 v2（LBS SDK）| `tools/probe_loc_sdk_8071.js` | hook requestLocationUpdates/onLocationChanged + LatLng 构造 |
| 伪装订位探针 v3（坐标源头栈）| `tools/probe_loc_src_8071.js` | 真实坐标 LatLng 调用栈 → 定位 `pz0.h.c → n83.g.onGetLocation` 源头 |
| 伪装订位探针 v4（参数契约）| `tools/probe_loc_inject_8071.js` | dump `pz0.h.c` / `n83.g.onGetLocation` 签名 + 入参值（arg2/arg3=纬经度）|
| 伪装订位 PoC 注入 | `tools/probe_loc_poc_8071.js` | hook `pz0.h.c` 改 arg2/arg3 为天安门，PoC 装机跳点成功（2026-06-07）|
| 伪装订位 选点结果捕获 | `tools/probe_loc_pick_8071.js` | hook setResult 抓选点页返回（发现 KLocationIntent extra）|
| 伪装订位 LocationIntent dump | `tools/probe_loc_intent_8071.js` | 反射 dump LocationIntent 字段 → d=纬度/e=经度/h=POI名 |
| 伪装订位 朋友圈选点排查 | `tools/probe_loc_moments_8071.js` | hook startActivity/setResult 排查朋友圈 POI 选点器（列表式，未采用）|
| normsg 设备指纹明文快照 | `tools/dump_mm_z3.js` | **防封官**：主动调 `u.z3(0)`/`Y8` 读 Java 采集层设备指纹明文（快进快出，低暴露；B35 实证 k33/k49）|
| normsg 采集器明文 hook | `tools/dump_normsg_plaintext.js` | **防封官**：warm-attach hook `u.z3`/`Y8`/`WCProbe$Info.dispatchEncryptJNIFuncCall` 明文入/密文出 |
| normsg native 探针 | `tools/dump_normsg_native.js` | **防封官**：hook libc `__system_property_get`/`lstat`（按 normsg 模块 returnAddress 过滤）+ `getPackageInfo` SIG flags |
| 微信环境检测快照(AccStrike) | `tools/dump_wx_detect.js` | **防封官**：`AccConfigManager` strike XML + `c$q.c29` installed_pkgs + `AccExptService` 因子/evilStackList/evilPkgList |
| normsg S6 解密器 hook | `tools/dump_normsg_s6.js` | **防封官**：hook `u.S6()`（ql3.j XOR 解密器）抓 密文入→明文出 + 主调 `z3(0)` 触发（B35）|
| normsg 启动期捕获(spawn) | `tools/dump_normsg_boot.js` | **防封官**：冷启 spawn 一次拿 ① S6 明文 ② normsg native `__system_property_get`/`lstat` 探测名单（B35 实证 root/解锁属性组）|
| normsg S6 离线解码器 | `tools/s6_decode.py` | **防封官**：S6（ql3.j XOR，**对合**）离线解/验；`--str`/`--hex`/`--hex16`/`--selftest`；配 Ghidra 抠出的密文常量用（B35）|
| normsg SO 暴力 S6 扫描 | `tools/s6_scan_so.py` | **防封官**：扫 `libwechatnormsg.so` 候选串跑 S6 捞可读关键词（B35 实测 0 命中 → 佐证 S6 无活跃密文）|
| 签名轴动态探针 | `tools/dump_sig_check.js` | **防封官**：hook `getPackageInfo`(GET_SIGNATURES/SIGNING)+`Signature.toByteArray`+调用栈；**B35 实证 normsg 上报自身签名 → 官替版签名轴暴露**（spawn 用）|
| ELF 导入符号列举 | `tools/elf_imports.py` | **防封官**：列 `.so` 导入(UNDEF dynsym) 筛 文件/crypto/zip/属性；B35 判 normsg 无文件读/crypto/zip 导入 → 无 native 直读签名能力（签名伪装门控）|

---

## 二、apk2 项目工具（外部，绝对路径）

### Frida 脚本

| 脚本 | 路径 | 用途 |
|------|------|------|
| 反检测 16 指标采集 | `I:/apk2/_3__D_wechat_ban/official_wechat_ban_research/03_anti_frida/frida_stats.js` | KPI 基线对比 |
| 反检测 SOP | 同上目录 `COLLECTION_SOP.md` | 采集标准流程 |
| 集合采集脚本 | `I:/apk2/_tools/frida/` | 各种 hook 调研脚本 |

### 静态分析

| 工具 | 路径 |
|------|------|
| jadx 反编译产物 (历史) | `I:/apk2/_4__samples/dynamic_fast/classes17_decompiled/jadx_out/` |
| Catfish 8070 反编译 | `I:/apk2/_4__samples/dynamic_fast/HOOK_IMPLEMENTATION_ANALYSIS.md`（源码级注释）|
| 历史版本索引 | `I:/apk2/_4__samples/sample_history_research/VERSION_INDEX.md` |

### ADB / Frida 模板

| 文件 | 用途 |
|------|------|
| `I:/apk2/_PROJECT_ENV/00_START_HERE.md` | ADB/Frida 环境规范 |
| `I:/apk2/_PROJECT_ENV/05_Frida运行规范.md` | spawn 失败 → warm-attach 流程 |

---

## 三、构建产物（外部）

| 路径 | 内容 |
|------|------|
| `I:/apk2_build/official/` | 官方版反编译归档 |
| `I:/apk2_build/modified/` | 改包/试错 smali（兜底路径，v1 不用）|

---

## 四、APK 样本（只读样本库）

| 路径 | 内容 |
|------|------|
| `E:/apk_diff/` | 7 个历史 APK |
| `I:/apk2/微密友8070官替稳定版-原版.apk` | 8.0.70 主样本（已无效，要换 8.0.66）|

> **8.0.66 官方 APK 落地** → 见 §〇 §A

---

## 五、授权服务器

| 路径 | 内容 |
|------|------|
| `I:/miyou-server/` | Python 3.12 + SQLite 双链路 |
| `I:/miyou-server/CLAUDE.md` | 架构 + HMAC 公告系统 |
| `I:/miyou-server/USAGE.md` | 客户端接入示例 |

---

## 六、iOS 参考

| 路径 | 内容 |
|------|------|
| `E:/ios-dylib/shitou-miyou-core/` | iOS 蜘蛛密友 dylib 反编译 |
| `D:/appleapp/` | iOS 独立研究分支 |

---

## 七、ADB 命令模板（Git Bash）

```bash
# 单设备直接 -U（不加 -D）
MSYS_NO_PATHCONV=1 adb shell ls /data/local/tmp

# 微信 PID
adb shell ps -A | grep com.tencent.mm | head -1

# warm-attach Frida（spawn 失败用这个）
adb shell am start -n com.tencent.mm/.ui.LauncherUI
sleep 3
PID=$(adb shell ps -A | grep com.tencent.mm | awk '{print $2}' | head -1)
MSYS_NO_PATHCONV=1 frida -U -p $PID -l script.js

# adb forward 调试端口
adb forward tcp:8080 tcp:8080
# 然后浏览器开 http://localhost:8080
```

Frida 17 方法学：旧 `Java.perform` 脚本优先用 `frida` CLI 直接加载；Python `create_script` 普通 agent 不再默认带 Java bridge，需改成显式 bridge agent 后再跑。

---

## 八、新增工具规则

新工具/脚本要登记 → 在本文件加一行 + 写用法
重复工具 → 找资料员合并
废弃工具 → 移到 `07_archive_归档/tools/`
