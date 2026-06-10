# E2 — 伪装订位（虚拟定位 / 全局伪造位置）

> 状态：✅ 已装机验证 2026-06-07
> 阶段归属：原属 v2/v3「装b 模块」，应用户要求提前于 v1 落地
> 底座：微信 8.0.71，Android 11，LSPosed
> 维护人：guard-execute-one 接手 → guard-dispatch 收尾

---

## 一、任务目标

让密友设置面板「特色功能」里的「伪装定位」从占位变为真功能：

1. 用户在设置页打开开关 → 拉起原生选点页 → 选一个伪坐标 → 保存。
2. 此后微信任何"读取本机定位"的业务（发位置 / 共享位置 / 朋友圈位置 / 附近的人）一律返回伪坐标。
3. 关闭开关 → 立即恢复真实定位。
4. UI 与微信原生选点页一致（复用 `RedirectUI`），不自绘地图控件。

## 二、不做（范围外）

- 不发任何位置消息（误发兜底用 talker=filehelper，详见 worklog 第 4 轮）。
- 不动 LBS SDK 回调（`TencentLocationManager.requestLocationUpdates` 等）—— PoC 实证下游自动继承上游分发的伪坐标。
- 不做"按地区/按 wxid"差异化伪装（v2 才考虑）。
- 不做"模拟轨迹/移动"（v2/v3 待评估）。

## 三、验收标准（每条需有装机日志或现场复测）

| # | 验收点 | 复测方法 | 结果 |
|:--:|------|---------|:--:|
| 1 | 开关默认关 → 真实定位 | 关开关 → 聊天发位置 → 看地图中心 | ✅ |
| 2 | 开关开 + 已设伪坐标 → 全局伪造 | 开开关 → 聊天发位置 / 共享位置 / 朋友圈位置 / 附近的人 | ✅ |
| 3 | 选点页右上角文案 = "保存"（非"发送"）| 进选点页观察右上角按钮 | ✅ |
| 4 | 选点 + 点保存 → 不发到任何真实聊天 | 选点保存后回设置页 → 检查最近聊天 | ✅（兜底进 filehelper）|
| 5 | 关闭开关 → 立即复原 | 关开关 → 再发位置 → 看是否回真实坐标 | ✅ |
| 6 | 设置页显示当前 POI 标签 | 设置好坐标后回看「选择伪装位置」副标题 | ✅ |

## 四、关键技术决策

| 决策点 | 选定方案 | 备选 | 理由 |
|------|---------|------|------|
| 注入点 | `pz0.h.c(pz0.h, boolean, double, double, int, double×3, Bundle)` beforeHook 改 arg2/arg3 | 消息层 `wy4.a.E/F` / Intent `kwebmap_slat/lng` / LBS SDK | 消息层候选全部动态证伪（F-38）；pz0.h.c 是定位分发总源头，单 hook 全局生效 |
| 选点 UI | 复用微信原生 `RedirectUI`（地图拖拽） | 自绘地图 / 朋友圈"所在位置"列表 | 与 A2/A3 套路一致（复用原生 UI）；列表式体验差用户否决 |
| 选点结果捕获 | hook `Activity.setResult` 读 `KLocationIntent.d/e/h` | hook 选点页发送按钮 | RedirectUI 内部直接发消息、不走 caller 结果，setResult 是稳定捕获点 |
| 按钮改名 | hook 选点页 `onResume` 扫视图树找文字=发送的 TextView 改"保存"（0/250/700ms 三波）| 单参 setText 拦截 | 该按钮不走单参 setText，拦截无效；onResume 扫描稳 |
| 误发兜底 | launchPicker 时把 `talker` 置 `filehelper` | hook 发送动作拦截 | RedirectUI 内部直接发消息无视 setResult；filehelper 是私密文件助手，误发可删 |
| 存储 | MMKV `flon/flla/flln/fllb`（double 用 String 保精度）| Bridge 现有 boolean/string getter | 与 Bridge 既有键命名风格一致；seed 化时机延后到 §6.7 |

## 五、依赖 / 阻塞

- 依赖：Bridge 已有 getString/putString/getBool/putBool API（无需扩 Bridge 基础设施）
- 不依赖 native_core（纯 Java/Xposed）
- 不影响任何已验 hook（独立模块 `moduleE`，仅新增 install 调用）

## 六、风险

- 微信升 8.0.72 → `pz0.h` / `n83.g` / `lt5.*` 混淆名必变 → 需走 classmap 重查（P24/P25 工具未上线前手动 jadx）
- 微信支付域反 frida（F-37）→ E2 hook 用 LSPosed 不用 frida，不触发
- 法律 / 监管：定位伪造长期处于行业灰色（区别于金融数据伪造，详见 dispatch §三）

## 七、关联文档

- 实装事实：`docs/HOOK_MAP_8071_AUTHORITATIVE.md` §一.1
- 总图：`HOOKMAP.md` §E + §一 E2 行
- 进度：`TASK_BOARD.md` §五
- 探针登记：`TOOLS_INDEX.md`
- 试错教训：`FAILURE_LOG.md` F-38
- 上线门控：`PROTECTION_MAP.md` §9b
