# PROBING — 找 hook / 探针 / 调试专用（上线维护期不读）

> **性质**：`guard-terminal` skill 的**按需子文档**，不是 SKILL.md，**不会自动加载**。
> **只在这两种时候手动 Read**：① 换微信版本追 hook（③版本适配）② 出问题要 frida 探针 / debug 装机调试。
> **上线维护期出货装机用不到这里**——那条走 `guard-release` + `docs/RELEASE_RULES.md`「双版本发布手册」+ `CLAUDE.md` §十三.五 全局签名铁律。
> **换版本整套流程**（jadx → check_classmap → 更新字典 → regen registry → 重打包）看 `docs/VERSION_UPGRADE_SOP.md`；本文件只放终端命令原文（那份 SOP 故意不锁命令行）。
>
> 纪律照旧走主 SKILL.md：每步先说目的、等用户回话、日志落 `03_execute_执行任务/<当前P任务>/logs/`、卡住立即停。

---

## frida 命令（探针 / KPI 采集用）

### frida warm-attach（微信已在跑）

```powershell
# 1. 先拿 PID（找主进程，不要 :push :tools 后缀的）
adb shell "ps -A | grep tencent"

# 2. attach
frida -U -p <PID> -l "<脚本完整路径.js>"
```

### frida spawn 模式（冷启动 / 找 hook 探针采集）

```powershell
frida -U -f com.tencent.mm --no-pause -l "<脚本完整路径.js>" 2>&1 | Tee-Object "<日志路径.log>"
```

> ⚠️ **LSPatch 打包型候选包禁 spawn**（`-f` 崩 metaloader）→ 改 warm-attach。
> ⚠️ **KPI / 防封出包体检不在这里**（那是上线维护期出包前活动、仍在用）→ 看 `guard-review` skill §⑤ + `guard-antiban` skill。本文件只放"找 hook"探针。

### 确认 frida-server 在跑

```powershell
adb shell "ps -A | grep frida"
# 没跑就启动：
adb shell "/data/local/tmp/frida-server &"
```

---

## 标准装机验证流程（场景 A · debug LSPosed 模块形态；release LSPatch 出货走 RELEASE_RULES）

```
步骤 1  编译调试包
步骤 2  adb install Guard 模块
步骤 3  adb force-stop 微信
步骤 4  清空 logcat
步骤 5  告诉用户：打开微信，执行目标操作
步骤 6  用户回来 → 拉日志
步骤 7  判定结果
```

**步骤 6 日志检查（关键）**

```powershell
# 先确认新代码已加载
adb logcat -d 2>&1 | Select-String "NCL.*init|NCL.*hook|NCL.*ready"

# 再看拦截命中
adb logcat -d 2>&1 | Select-String "NCL|GRD|MomentsFilter" | Select-Object -Last 50
```

- 看到 `NCL.*ready` → 新代码已加载 ✅
- 没有 → 旧代码仍在，执行 `adb shell am force-stop com.tencent.mm` 重开

---

## 8.0.71 类名探针流程（场景 B · 找 hook 专用）

用于找未知混淆类名或字段。必须先明确当前 P 任务和探针脚本路径，不从历史 P 任务默认套用。

```powershell
# 1. 确认 8.0.71 已装
adb shell "dumpsys package com.tencent.mm | grep versionName"

# 2. 确认 frida-server 在跑
adb shell "ps -A | grep frida"

# 3. spawn 跑探针（替换为当前 P 任务脚本和日志路径）
frida -U -f com.tencent.mm --no-pause -l "C:\Users\Me\Desktop\guard_native\03_execute_执行任务\<当前P任务>\scripts\<探针脚本>.js" 2>&1 | Tee-Object "C:\Users\Me\Desktop\guard_native\03_execute_执行任务\<当前P任务>\logs\<日志名>.log"

# 4. 等 [FIND] Hook 就绪 出现 → 告诉用户进朋友圈下滑
# 5. 看到 [ITEM] ★ 或 [ADAPTER] ★ → 复制给用户
```

---

## 不在本文件的（去别处）

- **KPI / 防封出包体检**（`frida_stats.js`，出包前仍用、2026-07-02 弱化为可选）→ `guard-review` skill §⑤（发版门控执行）+ `guard-antiban` skill（红线真源 verifiedbootstate/PROP/normsg/CONN）。属上线维护期活动，**不是"找 hook"、不埋这里**。
