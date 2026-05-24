# T09 — Catfish L0v3 互动红点探针

> 窗口: W2 | 日期: 2026-05-21 | 授权窗口: Catfish 1天
> 状态: 🟡 进行中

## 目标

通过 Frida attach 到原版 Chatfish (com.tencent.mm 主进程)，找到：
1. **互动红点的 hook 点**：Chatfish 用哪个类/方法抑制"发现"tab 互动红点
2. **写入路径**：谁在启动时写 `sns_control_flag`，值如何决定
3. **8.0.71 对应混淆类名**：Chatfish 版本里的类名 → 8.0.71 对应

## 已知背景

| 项目 | 结论 |
|------|------|
| SP key | `sns_control_flag` in `com.tencent.mm_preferences` |
| 8.0.71 读取类 | `d6.k()` → `l4.onAccountInitialized()` |
| Catfish qw1 读取类 | `c6.k()` → `k4.onAccountInitialized()` |
| qw1 值 | `sns_control_flag=0`（无互动 or Catfish 清零） |
| 原版 8.0.71 值 | `sns_control_flag=3`（有互动红点），patch 到 0 重启后复原 |
| SP 写入 | 启动时被重写（来源未确认，WCDB query 可能） |
| Catfish 注入类包 | `com.catfish.newvip.*` |
| `showUnReadMsgCount` | spawn 35s 未被调用（可能时机更早或路径不同） |

## 今日任务清单

- [ ] T09-1: attach 原版 Chatfish，hook `MainEntry.showUnReadMsgCount` + SP 写入，找互动红点写路径
- [ ] T09-2: 确认 `sns_control_flag` 的写入者（谁把3写进去，在 `d6.k()` 读之前）
- [ ] T09-3: 对比 qw1/qw5 里 Catfish 如何清零（是否直接写 SP？用哪个时机？）
- [ ] T09-4: 产出 8.0.71 hook 实现方案

## 产出（完成后填写）

```
result.md      ← 最终结论
scripts/       ← 验证脚本
logs/          ← 关键 frida 日志
```

## WCDB 追踪状态

`trace_sp_write.js` 运行结果：
- SP 写入: 无 sns 相关写入被捕获（可能写入在 hook 安装前发生）
- WCDB hook: `rawQuery` overload 签名不匹配（需修正）
