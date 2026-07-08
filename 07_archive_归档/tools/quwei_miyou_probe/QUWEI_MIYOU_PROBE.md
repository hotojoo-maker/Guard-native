# 天龙探针 / Tianlong Probe

> 目标包名: `com.tencent.mmqw5`（天龙共存版1，密友+全球，MT Manager 改包）
> 原始 APK: `E:\apk_diff\天龙共存版1（密友+全球）.apk`
> 创建日期: 2026-05-21

## 目的

对比天龙（历史版密友）的 hook 实现，找出其朋友圈互动红点抑制的 hook 点，
再映射到 WeChat 8.0.71 对应的混淆类名。

## 脚本清单

| 文件 | 用途 | 目标包 |
|------|------|--------|
| `patch_sns_flag.js` | attach → patch SP `sns_control_flag` | com.tencent.mm |
| `trace_tab_badge_v3.js` | w1 + SP + Java.choose | com.tencent.mm |
| `trace_sns_flag.js` | SP 读写路径 + d6 方法枚举 | com.tencent.mm |
| `trace_d6_methods.js` | d6 全方法枚举 + hook k() | com.tencent.mm |

> **注意**: 天龙版包名是 `com.tencent.mmqw5`，运行前修改脚本里的目标包名。

## 已知结论（来自 WeChat 8.0.71 分析）

| 项目 | 结论 |
|------|------|
| 红点 SP key | `sns_control_flag` in `com.tencent.mm_preferences` |
| 读取类 | `com.tencent.mm.plugin.sns.model.d6.k()` → returns int |
| 调用时机 | `l4.onAccountInitialized()` 启动时 |
| patch 效果 | 清 bit1 后重启红点依然出现 → 来源未定 |
| w1 情况 | 启动 50s 内无实例、无调用 → 不是 tab badge 来源 |

## 待定位

- [ ] 天龙 hook 了哪个微信类/方法来抑制互动红点
- [ ] 8.0.71 对应混淆类名是什么
- [ ] `sns_control_flag` patch 无效的真实原因

## 运行命令模板

```powershell
# attach 到天龙（改包名后）
frida -D 609b4b18 -n com.tencent.mmqw5 -l .\<script>.js

# spawn 天龙
frida -D 609b4b18 -f com.tencent.mmqw5 -l .\<script>.js
```
