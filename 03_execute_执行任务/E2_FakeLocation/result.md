# E2 — 伪装订位 结果（result.md）

> ✅ 装机验证通过 2026-06-07 23:55（用户现场复测）
> ✅ git 入库 2026-06-08 01:40（commit C1-C4 共 4 个）
> ✅ 文档收敛 2026-06-08 01:50（本批次）

---

## 一、验收对照

| # | 验收点 | 结果 | 证据 |
|:--:|------|:--:|------|
| 1 | 关开关 → 真实定位 | ✅ | 用户现场复测（贵州真坐标）|
| 2 | 开开关 + 已设伪坐标 → 全局生效 | ✅ | 发位置 / 共享 / 朋友圈 / 附近的人 4 场景均到伪坐标（用户现场）|
| 3 | 选点页右上角文案 = "保存" | ✅ | 2026-06-07 23:55 用户：成功了 |
| 4 | 选点保存 → 不发到真实聊天 | ✅（tier 1 = filehelper 兜底）| 23:39 装机后用户复测不再误发到其他人 |
| 5 | 关开关 → 立即复原 | ✅ | 23:43 装机后用户复测复原 |
| 6 | 设置页显示当前 POI 标签 | ✅ | 设置好后副标题显示选定地名 |

---

## 二、代码改动（已入 git）

| commit | 文件 | 变更 |
|:--:|------|------|
| `53c26a47` | `src/main/java/com/ghost/assist/moduleE/FakeLocation.java` | 新建（13186 字节 / 385 行）|
| `53c26a47` | `src/main/java/com/ghost/assist/ModuleMain.java` | +3 行（app classloader 注册）|
| `53c26a47` | `src/main/java/com/ghost/assist/core/Bridge.java` | +39 行（flon/flla/flln/fllb 存储 API）|
| `53c26a47` | `src/main/java/com/ghost/assist/moduleB/SettingsEntry.java` | +87 行（特色功能·伪装定位 UI）|
| `648cbc43` | `tools/probe_loc_{send,sdk,src,inject,poc,pick,intent,moments}_8071.js` | 8 探针新建 +823 行 |
| `93532de7` | `docs/HOOK_MAP_8071_AUTHORITATIVE.md` / `HOOKMAP.md` / `TASK_BOARD.md` / `TOOLS_INDEX.md` / `FAILURE_LOG.md` | E2 文档 + dispatch §6-06 瘦身（+157 -281）|
| `1fd0614e` | `CLAUDE.md` | 总览刷新（+6 -6）|
| `17095385` | `.gitignore` | 排除 frida 探针编译垃圾（+14）|

合计：4 个 java 文件 + 8 个 js 探针 + 6 个 md 文档 + 1 个 .gitignore，**+1385 -300 行**。

---

## 三、关键 hook 点（已写入权威 §一.1）

```
// 主注入点（全局生效）
pz0.h.c(pz0.h thiz, boolean fixed, double lat, double lng,
        int accuracy, double a1, double a2, double a3,
        Bundle extras)
  beforeHook:
    if (FakeLocationEnabled && hasFakeLocation()) {
        arg1 = true            // 固定为有定位
        arg2 = getFakeLat()    // 伪纬度
        arg3 = getFakeLng()    // 伪经度
    }

// 选点结果捕获
Activity.setResult(int resultCode, Intent data)
  beforeHook(when sExpectPick):
    KLocationIntent ki = parseFromIntent(data);
    Bridge.setFakeLocation(ki.d, ki.e, ki.h);  // 纬/经/POI 标签

// 按钮改名（onResume 扫描视图树，0/250/700ms 三波）
RedirectUI.onResume()
  afterHook:
    for each TextView in viewTree:
      if (tv.text.equals("发送")) tv.text = "保存"
```

下游回调 `n83.g.onGetLocation(boolean, float lng, float lat, int, double×3)` 自动继承上游伪坐标，**不需要单独 hook**。

---

## 四、覆盖场景（L1 装机实证）

| 场景 | 入口 | 上游链 | 实证 |
|------|------|------|:--:|
| 发位置消息 | 聊天 → + → 位置 → 发送 | `pz0.l.run → pz0.h.c → lt5.b.setCenter` | ✅ |
| 共享实时位置 | 聊天 → + → 共享实时位置 | 同上 | ✅ |
| 朋友圈位置 | 朋友圈 → 编辑 → 所在位置 | 同上 | ✅ |
| 附近的人 | 发现 → 附近的人 | 同上 | ✅ |

---

## 五、KPI 基线（待补 ⬜）

> F-22 铁律：关任务前必跑 `frida_stats.js` 对比基线。

- ⬜ E2 启用前/后 `verifiedbootstate / SELinux / phoneActiveCount` 等 LSPosed KPI 对比
- 现有数据：`03_execute_执行任务/P20B_BTriggers_SearchUnlock/logs/p20b_frida_stats_20260607.log`（P20B 的，非 E2 专用）
- 补跑责任：下一会话切 `/guard-terminal_终端操作` 跑 `tools/frida_stats.js` 对比 E2 启停。
- 不阻塞 git 收敛，但**阻塞发版**——发版前必须补。

---

## 六、上线门控（已写入 PROTECTION_MAP §9b）

见 `PROTECTION_MAP.md` §9b 业务敏感功能门控 — E2 行。

---

## 七、后续 / 收尾债

- ⬜ 补跑 frida_stats KPI 对比基线（见 §五）
- ⬜ `01_dispatch_总调度/CURRENT_PLAN.md` 仍停在 5-21 P21 状态机，需刷到现状（dispatch 后续批次）
- ⬜ 8.0.72 升级时 classmap 重查 `pz0.h` / `n83.g` / `lt5.*`（v1 P24 工具上线后自动化，当前手动 jadx）
- ⬜ `signing/guard-native-debug.keystore`（项目固定签名）git 处置待用户拍板（A 提交 / B .gitignore 排除 / C 外部管理）
- ⬜ `_commit_msg.txt`（5 月旧 commit 草稿）建议删除

## 八、关联文档

- 简介：`brief.md`
- 工作日志：`worklog.md`
- 装机日志：`logs/`（本批次未单独存档；下次装机配 `adb logcat -d 2>&1 | findstr "FLOC"` 截取存这里）
- 权威 hook：`docs/HOOK_MAP_8071_AUTHORITATIVE.md` §一.1
- 总图：`HOOKMAP.md` §E + §一 E2 行
- 进度：`TASK_BOARD.md` §五 E2 完成行
- 探针：`TOOLS_INDEX.md`（8 个 probe_loc_*_8071.js）
- 试错：`FAILURE_LOG.md` F-38
- 交接：`04_review_审稿复核/E2_FakeLocation_20260607.md`
