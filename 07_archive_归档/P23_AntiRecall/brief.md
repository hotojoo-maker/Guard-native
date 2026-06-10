# P23 brief — 防撤回（C1 模块）

> 执行窗口直接读这个，不用翻 TASK_BOARD 全文

## 当前状态

✅ **已装机 L1 实证（2026-05-31）**：`jy0.t.f`(doRevokeMsg) 拦截 + 原文保留 + 系统提示染红。
logcat 原文：`[AR] recall blocked + tip inserted` ×4（文字/表情/图片/视频）。

## 本任务 hook 点（8.0.71 实证，L2 静态 + L1 装机）

| hook | 类 | 方法 | 参数签名 | 状态 |
|------|-----|------|---------|------|
| 撤回统一出口 | `jy0.t` | `f` | `(String talker, long svrMsgId, com.tencent.mm.modelbase.p0, String replaceMsg, String, String) → void` = doRevokeMsg | ✅ L1 |
| 插系统提示 | `com.tencent.mm.storage.h9` | `r9(f9)→J` | 取 storage = `jy0.c9.b().u()`；构造 `f9`：setType(10000)/y1(talker)/j1(0)/c1(text)/d1(time-1)/s1(2) | ✅ L1 |
| 提示染红 | `com.tencent.mm.ui.widget.MMNeat7extView` | 首参 CharSequence 的方法（混淆名，枚举 hook） | 命中 TIP_MARK → ForegroundColorSpan(0xFFFA5151) | ✅ L1 |

> ⚠️ 旧点 `a2.b(p0, z15.ut4, ge3.z4)` 已**动态证伪**（收撤回零触发，从竞品 8.0.70 直搬）→ FAILURE_LOG **F-37**。勿恢复。

## 实现逻辑（v4：原文保留 + 插提示 + 染红）

```
beforeHookedMethod(jy0.t.f):
  isVipAuthorized() && isAntiRecallEnabled() 通过
  talker=args[0], svrMsgId=args[1]
  storage = jy0.c9.b().u() (h9);  orig = storage.k3(talker, svrMsgId)
  若 orig.isSend(D0)==1（自己撤回自己的）→ return 放行，原生撤回正常
  否则（对方撤回收到的消息）：
    setResult(null)                       // 跳过原地覆盖 → 原文(含图片/视频)保留
    构造 type=10000 系统提示 f9，createTime=orig.getCreateTime()-1（排原文上方）
    storage.r9(tip)                        // 插入，实时刷新
  日志 "[AR] recall blocked + tip inserted"

installTipColor（独立 UI hook，启动时装一次）:
  hook MMNeat7extView 首参 CharSequence 的方法 + 框架 TextView.setText 兜底
  命中 TIP_MARK("已拦截对方撤回") → 包 SpannableString + ForegroundColorSpan(红)
  （用 span 而非 setTextColor，微信后续 setTextColor(灰) 覆盖不掉）
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

## 待完成 / 已完成

- [x] build 模块 → 装机
- [x] 另一台手机发消息 → 撤回（文字/表情/图片/视频均测）
- [x] logcat 出现 `[AR] recall blocked + tip inserted` ×4
- [x] 验证原文保留原样（含图片/视频），非"撤回了一条消息"
- [x] 系统提示「─── HH:mm 已拦截对方撤回的消息 ───」染红、横线连续
- [ ] DebugServer 加 `/api/set_anti_recall` 切换开关（可选；当前 Bridge key=`arc` 默认 true）
- [ ] 跑 `frida_stats.js` 对比基线（发版门控时做）

## 关键文件

| 文件 | 说明 |
|------|------|
| `src/main/java/com/ghost/assist/moduleC/AntiRecall.java` | 正式 hook（🔬 待装机） |
| `src/main/java/com/ghost/assist/core/Bridge.java` | f2 开关（key="f2"，默认 false） |
| `src/main/java/com/ghost/assist/ModuleMain.java` | L223 注册 AntiRecall.install |

## worklog

| 日期 | 事件 |
|------|------|
| 2026-05-25 | （已作废）"探针确认 a2.b 签名 ✅" 实为无磁盘 log 的虚记录；后被动态证伪 |
| 2026-05-31 | a2.b 收撤回零触发 → 动态证伪（F-37）。frida 实时 trace 被 8.0.71 反 frida 杀进程挡住，改静态反编译 |
| 2026-05-31 | 静态(tinker dex)定位真实点 `jy0.t.f`(doRevokeMsg)；v3 setResult(null) 装机 → 拦截成功（原文留、无提示） |
| 2026-05-31 | v4：原文保留 + 插 type=10000 系统提示（h9.r9）；L1 装机 `[AR] recall blocked + tip inserted` ×4 |
| 2026-05-31 | 提示染红：聊天文字走自绘 MMNeat7extView（绕过框架 TextView），改 hook 其首参 CharSequence 方法 → ForegroundColorSpan 红；装机生效 |
| 2026-05-31 | 文案定稿「─── HH:mm 已拦截对方撤回的消息 ───」（box-drawing 连续线，每边 3 个）；✅ 收尾 |
