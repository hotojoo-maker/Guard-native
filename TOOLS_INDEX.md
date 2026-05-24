# TOOLS_INDEX — 工具/脚本/资源索引

> 维护人：guard-doc-audit_资料员
> 规则：使用任何工具/脚本前先查本表，避免重造轮子
> 路径用绝对路径，**只读不复制**到仓库内

---

## 〇、v1 前置依赖（开 W1~W4 必须就位）

| 依赖项 | 状态 | 落地路径 | 落地动作 |
|------|:--:|---------|---------|
| **8.0.66 官方微信 APK** | ⬜ **待补** | `06_refs_参考资料/apk_samples/wechat_8066.apk` | 见下方 §A |
| **8.0.66 jadx 反编译产物** | ⬜ 待跑 | `06_refs_参考资料/apk_samples/wechat_8066_jadx/` | W4 第 0 步 |
| 小米9 + Android 11 + Magisk + LSPosed | ✅ | — | 已就绪 |
| Frida 17.9.3 PC + frida-server | ✅ | — | 已就绪 |
| 微信 8.0.66 装机版 | ✅ | 设备 `com.tencent.mm` 已是 8.0.63（需升 66）| 装机 |

### §A 8.0.66 APK 落地清单

**v1 整个项目都依赖 8.0.66 APK 文件，没它寸步难行：**

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
| Frida 朋友圈过滤 | `./refs/filter_moments.js` | F05 已验证 v21 |

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

---

## 八、新增工具规则

新工具/脚本要登记 → 在本文件加一行 + 写用法
重复工具 → 找资料员合并
废弃工具 → 移到 `07_archive_归档/tools/`
