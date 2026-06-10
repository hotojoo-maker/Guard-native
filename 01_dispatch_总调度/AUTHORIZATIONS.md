# AUTHORIZATIONS — 二进制 / 装机授权台账

> 目的：记录用户明确授权过的 APK / SO / DEX / smali / MMKV 修改、构建、装机、导出与发布动作。
> 规则：没有明确授权记录的二进制动作，发版门控视为未授权，不倒填历史、不用口述替代记录。

---

## 记录格式

| 日期 | 授权人 | 范围 | 动作 | 目标文件 / 设备 | 状态 | 备注 |
|------|--------|------|------|-----------------|------|------|
| YYYY-MM-DD | 用户 | APK / SO / DEX / smali / MMKV / 装机 / 发布 | 构建 / 修改 / 安装 / 导出 / 发布 | 路径或设备 | 待执行 / 已执行 / 作废 | 证据路径 |

---

## 当前会话记录

| 日期 | 授权人 | 范围 | 动作 | 目标文件 / 设备 | 状态 | 备注 |
|------|--------|------|------|-----------------|------|------|
| 2026-06-09 | 用户 | 文档 / Java 注释 | 清理 P21 旧口径注释 | `src/main/java/com/ghost/assist/ModuleMain.java`、`07_archive_归档/P21_MomentsRedDot/worklog.md` | 已执行 | 非二进制动作；不涉及 APK/SO/DEX/smali/MMKV 修改 |
| 2026-06-09 | 用户 | APK / 装机 | 构建 Debug 包并覆盖安装验证 | `build/outputs/apk/debug/guard-native-debug.apk`；设备 `609b4b18` | 已执行 | 用户指令“切终端操作员skills，自己装机极检查log”；安装输出 `Success`；证据 `07_archive_归档/P1E_Filter读Registry/logs/psec1_search_registry_install_20260609.log` |
| 2026-06-09 | 用户 | APK | 构建 Release 包验证 R8 / wrapper 退出码 | `build/outputs/apk/release/**` | 已执行 | 用户指令处理 `gradlew.bat` exit code 255；`assembleRelease` 已 `BUILD SUCCESSFUL` 且 exit code 0 |

---

## 待补授权

| 日期 | 授权人 | 范围 | 动作 | 目标文件 / 设备 | 状态 | 备注 |
|------|--------|------|------|-----------------|------|------|
| 待定 | 用户 | APK | 正式交付 / 发布 | `build/outputs/apk/**` | 待授权 | 交付给外部或发布前补；本轮仅构建验证 |
| 待定 | 用户 | 装机 | 下一轮安装 / 覆盖安装 | 测试设备 | 待授权 | 后续每次装机前补 |
| 待定 | 用户 | SO | 修改 / 替换 `libguardcore.so` | `native_core/**`、APK 内 so | 待授权 | 若后续改 native 层必须补 |
| 待定 | 用户 | MMKV | 读写 / 迁移 / 清理线上配置 | 微信宿主数据目录 | 待授权 | 涉及用户数据时必须补 |

---

## 当前结论

- 本台账创建时，**没有登记任何已授权的 APK / SO / DEX / smali / MMKV 修改或发布动作**。
- 本轮已授权并执行的只有源代码注释与 P21 工作日志口径收敛。
- 后续进入装机、出包、发布、native 修改或 MMKV 操作前，必须先在本文件追加授权记录。
