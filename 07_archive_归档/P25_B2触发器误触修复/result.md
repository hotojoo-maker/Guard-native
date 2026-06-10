# P25 — B2 触发器误触发 V→H 修复
> 执行日期：2026-05-25
> 状态：✅ 完成（B2 修复装机实证 PASS；附带发现 P26 独立 bug）
> 涉及文件：`src/main/java/com/ghost/assist/moduleB/TriggerGuard.java`
> 授权检查官：改前 WARN → 改后 PASS（2026-05-25 02:26）

---

## 一、最终定性

**根因**：`TriggerGuard.installScreenAndCloseDialogReceiver` 的 `ACTION_CLOSE_SYSTEM_DIALOGS` 分支判定不严——只检查 `sm.isActive()`，没检查 `sForegroundCount`。`fs_gesture`（全屏手势）reason 在微信内关搜索页/弹窗时也会发此广播，被误判为"用户离开微信"，触发 `enterHidden()` 把 V 又踢回 H。

- ❌ 不是 SearchUnlock 链路问题
- ❌ 不是 StateMachine 冷却问题
- ❌ 不是 ConvFilter 注入问题
- ✅ 根因：B2 兜底广播分支缺前台守门

## 二、症状时间线（修复前）

| 时间 | 事件 | 状态 |
|------|------|:---:|
| T+0     | SU 口令命中 → exitHidden → V | V |
| T+25ms  | BUS-V restoreInPlace 注入 6 项 | V |
| T+26ms  | SU close FTSMainUI | V |
| **T+763ms** | **[TG] B2-close_dialogs(fs_gesture) → enterHidden** 🔴 | V→H |
| T+767ms | BUS-H clean 移除 4 项 | H |

用户感知：切 V 后好友/群闪一下就消失。

## 三、修复（方案 D）

`TriggerGuard.java:153-157` 增加 `sForegroundCount > 0` 守门，跳过 enterHidden。

不动 SearchUnlock / StateMachine / RefreshBus / ConvFilter。

```java
if (sForegroundCount > 0) {
    Log.i(TAG, "[TG] B2-close_dialogs(" + reason + ") foreground="
            + sForegroundCount + ", skip");
    return;
}
```

## 四、装机实证（2026-05-25 03:05:53）

```
03:05:53.189  [SM] exitHidden isBack=false
03:05:53.215  [BUS-V] cache=2
03:05:53.232  [BUS-V] in-place injected=6 on MvvmConvList
03:05:53.235  [BUS:BUS-V-direct] notified adapter=v0 cache=2
...
03:05:54.887  [CF:L4] state=V          ← 1.7 秒后仍 V 态
```

整段 1.7 秒 state=V 稳定，完全没有任何 `[TG] B2-close_dialogs ... → enterHidden`。

DebugServer 状态 API 实证：切后 code=0 (V)。

## 五、4 项验证清单

| 验证项 | 状态 | 备注 |
|--------|:----:|------|
| V 态关搜索页/弹窗不再回 H | ✅ 实证 | 上方时间线证据 |
| V 态按 Home 仍进 H | ⬜ 待补 | 当前未单独跑（与 P26 共测可补）|
| V 态切通讯录 tab 不进 H | ✅ 实证 | 切 tab 期间 state=V 稳定 |
| V 态手势返回桌面仍进 H | ⬜ 待补 | sForegroundCount=0 时仍 enterHidden（代码逻辑保障）|

## 六、保护区登记

`moduleB/TriggerGuard.java` 已加入授权检查官保护区清单（§三 Java 层）。
后续任何对 B1/B2/B5 触发器的改动 → 必须先经授权检查官审。

## 七、连带发现（→ P26）

V→H 误触发修复后，好友 wxid_lzd2va16jd1622 在 V 态仍不显示（独立 bug，群正常）。
根因：L1 hook 不喂养 sConvItemMap，BUS-V 走 used stale cache 路径，好友 stale item 渲染失败。
→ 详见 `P26_好友热切fresh触发/brief.md`
