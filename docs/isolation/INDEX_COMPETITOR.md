# 竞品柜总索引 — Catfish / 旧版本竞品参考（docs/isolation/）

> **隔离原则**：学状态机、触发器、产品行为；**类名 / 方法名 / 包路径以 8071 权威图为准**。
> 8.0.70 Catfish ≠ 8.0.71 Guard Native，禁止直搬（铁律 9、F-31）。
>
> **2026-06-29 竞品归柜（竞品归柜官·并行窗2）**：原 `refs/` 10 份竞品/旧版本参考 + `07_archive_归档/T09_Catfish_L0v3/` 整体迁入本柜（move 非 delete，未跑 git）。
> 已搬清单 + 外部引用「待改链接清单」见同目录 [`_MIGRATION_竞品归柜_20260629.md`](./_MIGRATION_竞品归柜_20260629.md)（CLAUDE.md / PROJECT_INDEX.md / TOOLS_INDEX.md / FAILURE_LOG.md / docs 多处 / 07_archive worklog / skills 仍指向旧 `refs/` 路径，待持有方修订）。

---

## 一、Catfish 源码参考（原 `refs/`，只读，禁直搬类名）

| 文件 | 可参考 | 禁止 |
|------|--------|------|
| [`./MainEntry.java`](./MainEntry.java) | hook 注册结构、功能清单（989 行 / 40+ hook） | 混淆类名写入 Guard 模块 |
| [`./UserControll.java`](./UserControll.java) | 3 态状态机、B 模块触发思路（944 行） | MMKV key / 敏感包名 |
| [`./VipPreference.java`](./VipPreference.java) | MMKV 封装模式 | 明文 key |
| [`./ReflectHelper.java`](./ReflectHelper.java) | 反射工具思路 | 直接复制进 `src/` |
| [`./filter_moments.js`](./filter_moments.js) | 朋友圈**策略**（y1→addAll），Frida v21 | 8066/8070 类名 |

## 二、竞品 / 旧版本资料文档（原 `refs/`）

| 文件 | 版本 | 内容 |
|------|------|------|
| [`./CATFISH_8070_INDEX.md`](./CATFISH_8070_INDEX.md) | 8.0.70 | Catfish 完整资源索引 |
| [`./FEATURE_MATRIX.md`](./FEATURE_MATRIX.md) | 8066 / 8070 | 密友功能矩阵 F01–F09 + 失败 / 状态列 |
| [`./SOURCE_MAP.md`](./SOURCE_MAP.md) | — | refs→apk2 / Catfish 源路径映射（内含外部 `I:/apk2` 路径；如内部互引旧 `refs/` 名待修） |
| [`./VERSION_8065_ANALYSIS.md`](./VERSION_8065_ANALYSIS.md) | 8.0.65 | 微信 8.0.65 静态分析（versionCode 2960）+ 跨版本字段差异 |
| [`./FAILURE_LOG_catfish.md`](./FAILURE_LOG_catfish.md) | apk2 副本 | ⚠️ **竞品 / apk2 失败档副本**（原 `refs/FAILURE_LOG.md`，F-01 起讲「8070 套 8066」）——与根目录 `FAILURE_LOG.md`（Guard 自有 F-01~F-43）**不同源**，已改名区分，勿混 |

## 三、Catfish 竞品调研（原 `07_archive_归档/T09_Catfish_L0v3/`）

| 文件 | 内容 |
|------|------|
| [`./T09_Catfish_L0v3/brief.md`](./T09_Catfish_L0v3/brief.md) | T09 互动红点探针任务（Catfish `com.tencent.mn1`，授权窗口已过期） |
| [`./T09_Catfish_L0v3/result.md`](./T09_Catfish_L0v3/result.md) | Catfish 8.0.70 互动红点 / 通知 / 未读逆向结论 |
| [`./T09_Catfish_L0v3/PITFALLS.md`](./T09_Catfish_L0v3/PITFALLS.md) | Frida attach mn1 调试坑 |
| `./T09_Catfish_L0v3/t09_probe.js` · `t09_output.log` · `t09_output.txt` | 探针脚本 + 原始输出 |

## 四、其它竞品资料（仍在原位，本次未迁）

| 路径 | 说明 |
|------|------|
| [`../../06_refs_参考资料/competitor_catfish/CATFISH_REVERSE.md`](../../06_refs_参考资料/competitor_catfish/CATFISH_REVERSE.md) | Catfish 8.0.70 动态逆向笔记（竞品唯一真源，本次未迁） |
| `I:/apk2/_4__samples/dynamic_fast/HOOK_IMPLEMENTATION_ANALYSIS.md` | 甜密友 8.0.66 静态分析（外部只读） |

---

## 与 `docs/archive` 的区别

| | 竞品柜（本索引 `docs/isolation/`） | `docs/archive/wechat_8066` |
|--|-----------------------------------|----------------------------|
| 目的 | 竞品行为、产品对标（Catfish / 甜密友 / 旧版本逆向） | 旧版**微信官方**混淆名 |
| 能否写进 8071 代码 | 仅思路 | ❌ 类名禁止 |
| 典型误用 | 照搬 hook 方法名 | 照搬 `m3.j1()` 当 8071 wxid getter |

8071 实现入口 → [`../HOOK_MAP_8071_AUTHORITATIVE.md`](../HOOK_MAP_8071_AUTHORITATIVE.md)
