# E2 — 伪装订位 工作日志

> 时间线按用户与执行 AI 的实际对话还原（用户事后粘贴回放）。
> 装机轮次：共 **5 次重装**，每次都有用户现场复测。

---

## 时间线

### 2026-06-07 23:10 — 编码完成（接需求）
4 处改动，无编译/lint 错误，未动任何已验逻辑：
1. 新建 `moduleE/FakeLocation.java`
   - injector：hook `pz0.h.c` beforeHook → 开启且有坐标时把 arg1=true / arg2=纬度 / arg3=经度
   - 捕获：hook `Activity.setResult` 读 `KLocationIntent.d/e/h` → 存 Bridge（`sExpectPick` 门控，不误吞正常发位置）
   - launchPicker：拉起原生 `RedirectUI` 选点页（`map_view_type=0`）
2. `Bridge.java`：加 `flon/flla/flln/fllb` 存储 + `isFakeLocationEnabled/getFakeLat/Lng/setFakeLocation` 等
3. `moduleB/SettingsEntry.java`：「特色功能」里把伪装定位占位换成真功能（开关 + 「选择伪装位置」按钮 + 显示当前 POI + 回来即时刷新）
4. `ModuleMain.java`：注册 `FakeLocation.install(lpparam, app.getClassLoader())`

### 23:11 — 用户：装机

### 23:20 — 第 1 次装机（编译 6m29s）
APK 装机 Success；微信已强停。让用户按 6 步流程测：
- 进微信 → 解锁显形态（搜索 111111）→ 我 → 设置 → 量子密友设置 → 特色功能 → 开关 + 选点 → 验证

### 23:23 — 用户报 bug v1
> "现在有个 bug！设置位置的时候，点右上角发送，居然真的发送到刚才的对话了！应该是保存！！"

### 23:28 — 第 2 次装机（修复 v1）
方案：拦住外层 RedirectUI 的回传结果 → 改 `RESULT_CANCELED` + 清 data。
预期：聊天调用方收到取消就不会真发。

### 23:31 — 用户反馈：仍发送
> "错了，右上角发送还是到真实聊天"

诊断：日志显示抓取成功、取消也执行了，但消息还是发了 → **RedirectUI 是内部直接发消息的，不走 caller 结果，也无视 talker** → "改取消结果" 拦不住。

用户建议换路线："能不能做一个不存在的 vxid？或者一个对话或者调用朋友圈的方式"

### 23:34 — 探针拓朋友圈"所在位置"选点器
让用户走：朋友圈 → 选图 → 编辑页 → 所在位置 → 选 POI。
意图：朋友圈选点器只返回 POI 不发消息。

### 23:36 — 用户拒绝：列表形式不可接受
> "这样体验不好，我看了！这样是列表形式，就不能写一个不存在 vxid 吗？或者自己？或者不存在的，或者不发送？"

### 23:39 — 第 3 次装机（兜底方案）
方案：launchPicker 时把 `talker` 设 `filehelper`，保留地图选点 UX。
**即便 RedirectUI 内部误发，也只进自己的文件传输助手**（私密、可删）。

### 23:40 — 用户：生效了
> "可以了，生效了，现在我试试关闭之后，能不能复原，右上角的发送能 hook 变成保存按钮吗"

### 23:43 — 第 4 次装机（按钮改名 v1）
方案：拦截选点页右上角按钮的 setText → 改"保存"。
关闭复原 + 再开生效 用户复测通过。

### 23:47 — 用户：完美收敛文档（**首次"收敛"信号**）

### 23:49 — 上一 AI 第一次写"收敛报告"
归档：权威 §一.1 / HOOKMAP §E / TASK_BOARD §五 / TOOLS_INDEX 八探针 / FAILURE_LOG F-38。
代码改动列出但 **未提 git commit**。

### 23:51 — 用户：按钮没成功改成保存
> "还没！提示，hook 按钮变成保存，没有成功"

诊断：上版 setText 拦截没拦到 → **该按钮不走单参 setText**。

### 23:52 — 第 5 次装机（按钮改名 v2）
方案：hook 选点页 `onResume` → 扫整个视图树找文字=「发送」的 TextView 改成「保存」（0/250/700ms 三波重试防渲染延迟）。

### 23:55 — 用户：成功了，收敛吧

### 23:56 — 上一 AI 第二次"收敛"
重写收敛报告（含按钮 v2 修正）。**仍未提 git**。

### 2026-06-08 00:05 — 用户切话题"研究装b 功能，改零钱"

### 00:07 — 上一 AI 转入 E3 改零钱调研

### 00:09 — 用户报"微信卡死"

### 00:11 — 探针抓到 `MallIndexUIv2` 后微信被杀
诊断：8.0.71 钱包/支付区有反 frida 检测（已记 F-37）。

### 00:13~（推断）— 上一 AI 掉线

---

## 2026-06-08 dispatch 收尾接手（本会话）

### 00:30 接手
切换工作目录到 `C:\Users\Me\Desktop\guard_native`，进 dispatch skill。

### 01:00 收敛核查
检查 E2 是否真正收敛 → **发现代码 0 commit、无 P 任务目录、无交接快照、PROTECTION_MAP 无 E2**。

### 01:15 用户拍板 "先收敛改定位"
按 git 保姆 skill 拟 5 个 commit 方案 + .gitignore 加固方案，用户回 "可以走，开发的探针遗留物干净即可"。

### 01:30 拍快照
`git stash push -u -m "snap/E2_FakeLocation/20260608-0130"` + `git stash apply --index`（保留工作树）。

### 01:35 .gitignore 加固
排除 `node_modules/` / `**/_probe_build/` / `tools/frida_build/` / `*.compiled.js` / `tools/re_8071/` / `.cursor/mcp.json.bak-*`。
工作区 untracked 数从约 700 项（含 node_modules）降到 41 项。

### 01:40 顺序提交 5 个 commit
| 顺序 | hash | 主题 | 体量 |
|:--:|------|------|------|
| C0 | `17095385` | chore(gitignore) | +14 |
| C1 | `53c26a47` | feat(moduleE) E2 装机验证 2026-06-07 | +385 -13（FakeLocation.java 新建）|
| C2 | `648cbc43` | tools(probe) 8 探针归档 | +823 |
| C3 | `93532de7` | docs(E2) 文档收敛 + dispatch §6-06 瘦身 | +157 -281 |
| C4 | `1fd0614e` | docs(总览) CLAUDE.md 刷新 | +6 -6 |

### 01:50 写本 P 任务三件套 + W 交接快照 + PROTECTION_MAP §9b（本次提交）

---

## 试错教训速查（已沉淀到 FAILURE_LOG F-38）

| # | 错误假设 | 反证 | 正解 |
|:-:|---------|-----|------|
| 1 | 消息层 `wy4.a.E/F` 写经纬度 | 发位置/共享/朋友圈全场景零命中 | hook 定位分发源 `pz0.h.c` arg2/arg3 |
| 2 | Intent `kwebmap_slat/lng` 传坐标 | `getDoubleExtra` 返回默认 -1000 = 键不存在 | 同上 |
| 3 | LBS SDK `requestLocationUpdates / onLocationChanged` 是关键回调 | 本轮全未触发（走缓存 + pz0.h.c 分发链） | 锚分发链 pz0.h.c，不锚 SDK 回调 |
| 4 | "改 setResult 为 CANCELED 拦截选点页发送" | RedirectUI 内部直接发消息，不走 caller 结果 | talker=filehelper 兜底 |
| 5 | 单参 setText 拦截改按钮文案 | 该按钮不走单参 setText | onResume 扫视图树 |

## 装机环境

- 设备：小米 9 / Android 11
- 微信：8.0.71
- LSPosed 主进程
- Guard 模块已启用
- 测试 wxid：filehelper（兜底误发目标）
- 测试坐标：天安门 39.9087 / 116.3975（PoC）+ 用户现场选点（北京）
