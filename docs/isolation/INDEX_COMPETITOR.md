# 竞品参考 — Catfish（行为参考，非 8071 类名表）

> **隔离原则**：学状态机、触发器、产品行为；**类名 / 方法名 / 包路径以 8071 权威图为准**。  
> 8.0.70 Catfish ≠ 8.0.71 Guard Native，禁止直搬（铁律 9、F-31）。

---

## 文档

| 文件 | 版本 | 内容 |
|------|------|------|
| [`../../06_refs_参考资料/competitor_catfish/CATFISH_REVERSE.md`](../../06_refs_参考资料/competitor_catfish/CATFISH_REVERSE.md) | 8.0.70 逆向笔记 | 架构/行为对照，非实现清单 |

---

## 源码参考（refs/，只读）

| 文件 | 可参考 | 禁止 |
|------|--------|------|
| [`../../refs/MainEntry.java`](../../refs/MainEntry.java) | hook 注册结构、功能清单 | 混淆类名写入 Guard 模块 |
| [`../../refs/UserControll.java`](../../refs/UserControll.java) | 3 态状态机、B 模块触发思路 | MMKV key / 敏感包名 |
| [`../../refs/VipPreference.java`](../../refs/VipPreference.java) | MMKV 封装模式 | 明文 key |
| [`../../refs/filter_moments.js`](../../refs/filter_moments.js) | 朋友圈 **策略**（y1→addAll） | 8066/8070 类名 |

---

## 外部只读样本

| 路径 | 说明 |
|------|------|
| `I:/apk2/_4__samples/dynamic_fast/HOOK_IMPLEMENTATION_ANALYSIS.md` | 甜密友 8.0.66 静态分析 |
| `06_refs_参考资料/competitor_catfish/` | 禁止复制进 `src/` |

---

## 与 archive 的区别

| | Catfish（本索引） | archive/wechat_8066 |
|--|-------------------|---------------------|
| 目的 | 竞品行为、产品对标 | 旧版**微信**混淆名 |
| 能否写进 8071 代码 | 仅思路 | ❌ 类名禁止 |
| 典型误用 | 照搬 hook 方法名 | 照搬 `m3.j1()` 当 8071 wxid getter |

8071 实现入口 → [`../HOOK_MAP_8071_AUTHORITATIVE.md`](../HOOK_MAP_8071_AUTHORITATIVE.md)
