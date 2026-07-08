# Catfish 8.0.70 完整资源索引

> 跨 apk2 + 桌面 apk + E:\apk_diff\ 三个源

## 速查：我要什么 → 去哪找

| 我要 | 去 |
|------|-----|
| 看 Java 源码（可读） | apk2 `_1__B_rewrite/06_jadx/jadx_output_classes17/sources/com/catfish/newvip/` (66 .java) |
| 看最完整的 smali | desk-apk `smali_classes17_未加密备份/com/catfish/newvip/` (184 .smali) |
| 看 hook 注册点 | MainEntry.java (47KB, 82 方法) |
| 看过滤逻辑 | UserControll.java (43KB) |
| 看反射工具 | ReflectHelper.java (14KB) |
| 看防检测机制 | catfish_mainline/03_Catfish本地防护链分析.md + NmsHookInvocationHandler.smali |
| 看签名伪装 | catfish_mainline/04_Catfish签名伪装链分析.md |
| 看 SO 职责 | catfish_mainline/05_Catfish_SO职责分析.md |
| 看功能矩阵 | prod/FEATURE_MATRIX.md |
| 看失败方案 | prod/FAILURE_LOG.md |
| 看调试状态 | prod/DEBUG_STATE.md |
| 看 Frida 探针 | prod/*.js (25个) |
| 看运行时数据 | desk-apk runtime_dump/ (6 快照, 5.5GB) |
| 看 MMKV 数据 | desk-apk _newvip_extract/ (5 阶段 .bin) |
| 看版本差异 | sample_history_research/CROSS_VERSION_DIFF.md |
| 看外部 APK 样本 | E:\apk_diff\ (9 APK, 8 版本) |

## 关键资源统计

| 类别 | 数量 |
|------|------|
| Java 反编译 | 67 |
| Smali 反编译 | 361 (含重叠) |
| 分析文档 | 47 |
| Frida 脚本 | 25 |
| Python 分析脚本 | 12 |
| SO 文件 | 14 |
| DEX 文件 | 4 |
| 运行时快照 | 6 (5.5GB) |
| 外部 APK | 9 |

## Smali 比 jadx 多出的关键类

jadx 反编译失败，只在 smali 中存在：
- NmsHookInvocationHandler — 防检测动态代理
- PmsHookBinderInvocationHandler — Binder 层拦截
- ServiceManagerWraper — 服务管理器包装
- MyAuth — 自定义鉴权
