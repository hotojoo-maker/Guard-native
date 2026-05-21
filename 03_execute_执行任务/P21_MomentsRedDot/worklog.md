# P21 工作日志 — 朋友圈小红点守护

> 更新时间：2026-05-21 v14（改为纯 Frida 方案，无需 LSPosed 模块）
>
> ⚠️ **2026-05-21 待验证：用户指出红点控制逻辑在 .so 层。但 AI 未证明 Java 层无法消除红点——v1/v2 从未成功调用 g1()，无法排除 Java 方案。待装机验证。**

---

## 当前状态

✅ **v13 已 build 成功**，待装机验证。

### v13 修复的三个 bug（jadx 8.0.71 + Frida 实证双重确认）

| Bug | 问题 | 修复 |
|-----|------|------|
| B1 | `callG1OnUi()` 只搜 `getDeclaredMethods()`，g1 若在父类则漏掉 | 改为遍历整个继承链 |
| B2 | `suppressRedDotIfHiddenFriend()` 在 `this.x` 为空时直接 return，g1 从未执行 | 新增 FMF.E 判断分支 + 冷启动激进清除路径 |
| B3 | LauncherUI.onResume 清的是 `ns.c.b`（错字段），视觉不刷新 | 改为通过 sFMFInstance 调 g1(false)，300ms 延迟 fallback |

### jadx 8.0.71 确认的关键信息

- `FMF.g1("album_dyna_photo_ui_title", false)` = 正确的视觉刷新调用
  - 来源：`cd5/j.java:67` `findMoreFriendsUI.g1("find_friends_by_finder_live", false)` 同模式
  - `album_dyna_photo_ui_title` key 在 `ja2/v0.java:82`、`ja2/f.java:40` 确认对应朋友圈 tab
- FMF.java 不在 jadx output（不同 dex），但 g1 接口通过 `cd5/*.java` import 确认

---

## 已验证事实（✅ 必须有日志原文）

| 事实 | 证据 |
|------|------|
| D1 朋友圈隐藏密友有效 | `[MF] D1 blocked poster=wxid_lzd2va16jd1622` × 5 |
| ns.c 全部字段值（红点亮时）| `a=false b=false c=false d=false e=1 f=0 g=0` |
| **ns.c.b = false 但红点亮** ★ | find_reddot_field.log 直接确认 → ns.c 系列不控制红点 |
| FMF.E = true（红点亮时）| find_reddot_field.log `[FMF:FIELD] *** E (boolean) = true` |
| AbstractTabChildPreference.m = true | find_reddot_field.log |
| AbstractTabChildPreference.p = true | find_reddot_field.log |
| LauncherUI.o = true | find_reddot_field.log（候选，待确认是否红点相关） |
| LauncherUI.p = true | find_reddot_field.log |
| ns.c.e = 1 (int) | find_reddot_field.log（非零，但含义未知） |
| ww2.c.b = undefined | find_reddot_field.log → ww2.c 在 8.0.71 无此字段或非 primitive |
| FindMoreFriendsUI.L1 hook 有效 | `[FMF] L1 hooked (1 overload), 78 total` |

---

## 已证伪路径

| 方案 | 结果 | 状态 |
|------|------|------|
| hook ns.c.b → false | ns.c 有 0 个方法，字段本来就是 false，红点照样亮 | ❌ 完全打空 |
| retroactiveZeroOnBoot 清 ns.c.b | 清了也没用，ns.c.b 根本不控制红点 | ❌ |
| hook w1.E1() → 0 | E1 未被调用 | ❌ |
| hook LauncherUI.onResume 清 ns.c.b | 清了错字段 | ❌ |
| 所有针对 ww2.c.b 的操作 | 8.0.71 ww2.c.b 为 undefined | ❌ |

---

## 已解决假设（✅ v13 解决）

| 假设 | 结果 |
|------|------|
| 清零 FMF.E + ATCP.m/p → 红点消失 | ❌ 清字段无效，必须调 g1() 方法 |
| g1("album_dyna_photo_ui_title", false) 是正确调用 | ✅ jadx `cd5/j.java:67` 确认 |
| callG1OnUi 调用路径有问题 | ✅ 三个 bug 已修复（见上方 v13 修复记录） |

## 当前假设（❓ 待验证）

| 假设 | 依据 | 验证方法 |
|------|------|---------|
| ❓ g1() 在 v13 能成功找到并执行 | getDeclaredMethods → getMethods 改为继承链遍历 | 装机看 `[MRD:g1]` log |
| ❓ 冷启动时 sFMFInstance 能在 300ms 内就绪 | FMF.L1 早于 LauncherUI.onResume+300ms | 装机看 `launcher-delayed` vs `launcher-immediate` |

---

## 下一步（只列一步）

**纯 Frida 方案验证（无需 LSPosed，无痕，重启即消失）：**

保持微信主界面有红点：
```powershell
frida -U -n com.tencent.mm -l tools/reddot_clear.js
```
attach 后等 3 秒，红点应消失。一次性清除完成后可 Ctrl+C detach。

持久守护模式（持续自动清除）：
```powershell
frida -U -n com.tencent.mm -l tools/reddot_clear.js --persist
```
hook LauncherUI.onResume + FMF.L1 + FMF.onResume，每次回主界面自动调 g1(false)。

---

## v14 策略变更

**原因：用户怕封号不敢用正常账号登录，LSPosed 模块风险太高。**

| 项 | v13 (LSPosed) | v14 (Frida) |
|----|--------------|-------------|
| 安装方式 | 需装 LSPosed 模块 | frida attach 即用 |
| 持久性 | 重启仍存在 | 重启即消失，零痕迹 |
| 封号风险 | 模块常驻，可被检测 | attach 完 detach，无残留 |
| 核心调用 | 同 g1(false) | 同 g1(false) |
| 验证成本 | 需 build + 装机 | 一条命令 |

`MomentsRedDotGuard.java` (LSPosed) 保留作为参考实现，但主线改为 Frida 方案。

---

## 关键文件

- `tools/reddot_clear.js` — **v14 纯 Frida 红点清除（新，推荐）**
- `tools/find_reddot_field.js` — 诊断脚本（红点亮时 dump 所有相关字段）
- `tools/find_reddot_field.log` — 原始日志 222 行
- `tools/test_clear_fmf_badge.js` — v1 验证脚本（字段清零，g1 未成功调用）
- `tools/test_clear_badge_v2.js` — v2 验证脚本（g1 找到但 invoke 参数类型不匹配）
- `tools/test_clear_fmf_badge.log` — v1 日志：字段清零成功，g1 未执行
- `tools/test_clear_badge_v2.log` — v2 日志：g1(String,boolean) 确认存在，Frida invoke 报类型不匹配（已修复）
- `src/main/java/com/ghost/assist/moduleD/MomentsRedDotGuard.java` — LSPosed 参考实现（不再主线）
