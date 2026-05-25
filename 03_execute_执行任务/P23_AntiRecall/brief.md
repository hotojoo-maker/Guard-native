# P23 brief — 防撤回（C1 模块）

> 执行窗口直接读这个，不用翻 TASK_BOARD 全文

## 当前状态

🔬 代码已写（2026-05-25），**待装机验证 `[AR:b] revoke blocked` 日志**

## 本任务 hook 点（8.0.71 实证）

| hook | 类 | 方法 | 参数签名 | 状态 |
|------|-----|------|---------|------|
| 撤回消息派发 | `a2` | `b` | `(com.tencent.mm.modelbase.p0, z15.ut4, ge3.z4) → q0` | 🔬 待装机 |

## 版本对照（升版本时核对）

| 参数 | 8.0.70 Catfish | 8.0.71 本版 |
|------|----------------|-------------|
| b-1 envelope | `m05.ys4` → `z15.ut4` | ✅ |
| b-2 callback | `sc3.z4` → `ge3.z4` | ✅ |
| a-1 | `m05.a40` → `z15.g40` | （备查） |
| c-0 | `m05.i4` → `z15.i4` | （备查） |

## 实现逻辑（before/after 快照恢复）

```
beforeHookedMethod:
  isVipAuthorized() && isAntiRecallEnabled() 通过
  → snapshot(args[0] = p0)：保存所有非 static 字段到 param extra

afterHookedMethod:
  restore(p0, snap)：恢复所有非 static/final 字段
  → p0 内容还原为撤回前原始状态
  → log "[AR:b] revoke blocked, p0 restored"
```

## 授权门

```java
isVipAuthorized()                    // 顶层授权（v1 stub true）
  && Bridge.isAntiRecallEnabled()    // f2 开关（默认 false）
```

**不依赖 f1（密友开关），对所有联系人生效。**

## 相关 F-xx

| 编号 | 一句话 |
|------|--------|
| F-25 | XposedHelpers 必须 catch Throwable（install try/catch 已覆盖） |
| F-23 | 禁止 native hook（AntiRecall 纯 Java） |

## 待完成

- [ ] build 模块 → 装机
- [ ] 让另一台手机发消息 → 撤回
- [ ] 确认 logcat 出现 `[AR:b] revoke blocked, p0 restored`
- [ ] 验证消息内容保留原样（非"撤回了一条消息"）
- [ ] DebugServer 加 `/api/set_anti_recall` 切换 f2（可选，当前只能手改 MMKV 或代码默认值改 true）
- [ ] 跑 `frida_stats.js` 对比基线

## 关键文件

| 文件 | 说明 |
|------|------|
| `src/main/java/com/ghost/assist/moduleC/AntiRecall.java` | 正式 hook（🔬 待装机） |
| `src/main/java/com/ghost/assist/core/Bridge.java` | f2 开关（key="f2"，默认 false） |
| `src/main/java/com/ghost/assist/ModuleMain.java` | L223 注册 AntiRecall.install |

## worklog

| 日期 | 事件 |
|------|------|
| 2026-05-25 | 探针确认 a2.b 签名 ✅；AntiRecall.java 正式 hook 写完；🔬 待装机 |
