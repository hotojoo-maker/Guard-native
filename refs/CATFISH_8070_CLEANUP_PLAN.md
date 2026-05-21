# Catfish 8.0.70 清理方案

> 5 代理并行分析 + 1 核对代理复核 | 2026-05-18
> **本轮只出清单，未删除未移动任何文件**

## 1. KEEP — 活跃保留

| 路径 | 理由 |
|------|------|
| `_1__B_rewrite/06_jadx/jadx_output_classes17/sources/com/catfish/newvip/` (66 .java) | 核心过滤引擎 + 82 hook 点 |
| `_1__B_rewrite/wechat-guard/` | LSPosed 模块项目（活跃） |
| `_1__B_rewrite/05_docs/CURRENT_MAINLINE_STATUS.md` | 主线总表 |
| `_1__B_rewrite/B_hookmap_phase1/` | Hook 映射 |
| `_1__B_rewrite/PLAN_B_INDEX.md` | 计划索引 |
| `_1__B_rewrite/04_scripts_tools/` | Python 分析脚本 |
| `_3__D_wechat_ban/official_wechat_ban_research/prod/` | 25 活跃探针 + FAILURE_LOG + FEATURE_MATRIX + DEBUG_STATE |
| `_PROJECT_ENV/` | 工作环境规范 |
| `_tools/` | 工具目录 |
| 根 `CLAUDE.md`, `FINDINGS.md`, `QE66_RESUME.md` | 项目元信息 |
| `guard_native/` 全部 | 新工作区 |

### ⚠️ 保留但需标注

| 文件 | 标注 |
|------|------|
| `wechat-guard/.../LicenseManager.kt` | 含服务器签名验证+设备指纹+AES授权链，属防盗版链非纯功能。封包阶段需剥离 |

## 2. ARCHIVE — 不进主线，保留历史参考

| 路径 | 内容 |
|------|------|
| `8.0.62官方.apk` (256MB) | 建议迁 `I:\apk2_build\` |
| `_archive_docs/` 全部索引 | 已收敛的历史文档 |
| `_archive_docs/catfish_mainline/` | 猫鱼主线历史分析 |
| `_archive_A_smali/catfish_mainline/` | 防检测/签名伪装/C2授权链（不进主线） |
| `_archive_A_smali/RE_bypassmm/`, `RE_native/`, `HARD_GATE_RUN/` | bypassmm 研究 |
| `_archive_old_docs/` (72 目录) | 原始研究阶段 |
| `_1__B_rewrite/02_smali/` | 184 smali，jadx 已覆盖 |
| `_1__B_rewrite/03_native_and_dex/` | SO/DEX 二进制历史 |
| `_4__samples/` | 外部 APK 样本 |

### smali 5 文件判定

| 文件 | 判定 | 原因 |
|------|:--:|------|
| NativeHelper | **KEEP** | libwechatsd.so JNI 桥，含密友配置读写接口（混入授权/反检测，需剥离） |
| NmsHookInvocationHandler | ARCHIVE | apk2 内文件不存在，仅文档描述。桌面源: `C:\Users\Me\Desktop\apk\smali_classes17_未加密备份\com\catfish\newvip\core\NmsHookInvocationHandler.smali` |
| PmsHookBinderInvocationHandler | ARCHIVE | 纯签名伪装 |
| ServiceManagerWraper | ARCHIVE | 签名伪装部署基础设施 |
| SigCracker | ARCHIVE | 深度签名伪装 + 动态 DEX 加载 |

## 3. DELETE_CANDIDATE — 建议删除

| 路径 | 理由 |
|------|------|
| `_archive_docs/test_card.py`, `write_fail_auth.py`, `*.sh` | 一次性测试脚本 |
| `_archive_docs/auth_fail.xml` | 授权失败残留 |
| `_archive_docs/admin_page.html` | 无关 HTML |
| `_archive_docs/wechatsd_symbols_filtered.txt`, `bypassmm_symbols_filtered.txt` | 过期符号快照 |
| `_archive_docs/maps_4863.txt` (611KB), `maps_4863_raw.txt` | 一次性内存快照 |
| `_archive_docs/download_test.ipa`, `*.mobileconfig` (4) | iOS 无关 |
| `_archive_docs/hash_test_output.txt` | 单次 hash 输出 |
| `_archive_docs/tmp_8063/`, `dex_dump/`, `_dump_tmp/`, `_diag_diagnostics/` | 临时产物 |
| `_BUILD_TEST/` | 构建测试残留 |
| `_archive_old_docs/_deprecated_wrong_conclusions/` | 已废弃结论 |
| 根 `monitor*.bat` (8) | 临时监控批处理 |
| `boot_extract/`, `logs/`, `ai-image/`, `Quantum-Miyou-Skills/` | 无关文件 |

### ⚠️ 确认不删

| 文件 | 原因 |
|------|------|
| D 线 `maps.txt` (403KB), `pkg_dump.txt` (72KB) | 仍可能是证据，保留在 D 线研究区 |

## 4. 证据等级修正

核对代理发现代理3报告"23个L1结论"存在膨胀。实测：
- 约 **10-12 条**有 Frida log 直接支撑（真 L1）
- 第 9 条"过渡版本"是推断 (L3)
- 第 18 条"libmeminfo.so 来源待定"自标不确定 (L4)
- 第 12 条子句明确写"高概率推断"

**修正**: 降低 L1 声称至 10-12 条，其余按实际等级标注。

## 5. 核对结果

| 维度 | 结果 |
|------|------|
| 证据等级膨胀 | ⚠️ 有问题，见上 |
| 防检测链误归入功能主线 | ⚠️ LicenseManager.kt 需标注 |
| 误删风险 | ✅ 低风险，maps/pkg_dump 确认不在删除候选 |
| NmsHook 通知过滤遗漏 | ⚠️ apk2 内不存在，已在索引标注桌面源路径 |
| 本轮不删不移动 | ✅ 基本遵守，guard_native/refs/ 9 个副本属交付物 |
