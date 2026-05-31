# archive/ — 历史资料总索引

> **不是当前底座。** 微信 **8.0.71** 实现只认 [`../HOOK_MAP_8071_AUTHORITATIVE.md`](../HOOK_MAP_8071_AUTHORITATIVE.md)。  
> 本目录仅供对照、排错、版本 diff；**禁止**把下表类名/方法名直接写进 LSPosed 代码。

---

## wechat_8066/ — 微信 8.0.66

| 文件 | 用途 | 对 8071 | 风险 |
|------|------|:------:|------|
| [`wechat_8066/CLASS_MAP_8066.md`](./wechat_8066/CLASS_MAP_8066.md) | 8066 混淆类速查 | ⚠️ 仅 diff | 直搬类名 |
| [`wechat_8066/HOOK_MAP_V1.md`](./wechat_8066/HOOK_MAP_V1.md) | v1 P0/P1 优先级规划（**DEPRECATED**） | ❌ 非类名表 | 误当 8071 hook 名单 |
| [`wechat_8066/HOOK_POINTS.md`](./wechat_8066/HOOK_POINTS.md) | 8066 Frida 验证 + 伪代码 | ⚠️ 思路参考 | 8066 类名已变 |
| [`wechat_8066/VERSION_CLASSMAP.md`](./wechat_8066/VERSION_CLASSMAP.md) | 跨版本字段对照 | ⚠️ diff | 勿当 8071 实证 |

### tasks/（文档员双份留存）

| 文件 | 说明 |
|------|------|
| [`tasks/T05_SnsObject_文档员.md`](./wechat_8066/tasks/T05_SnsObject_文档员.md) | T05 SnsObject 8066 定位 |
| [`tasks/T05_SnsObject_资料员.md`](./wechat_8066/tasks/T05_SnsObject_资料员.md) | 同上（资料员副本） |
| [`tasks/T07_ContactStorage_文档员.md`](./wechat_8066/tasks/T07_ContactStorage_文档员.md) | T07 ContactStorage 8066 |
| [`tasks/T07_ContactStorage_资料员.md`](./wechat_8066/tasks/T07_ContactStorage_资料员.md) | 同上（资料员副本） |

---

## planning/ — 早期 P 任务调研

| 文件 | 版本 | 说明 |
|------|------|------|
| [`planning/P16_朋友圈Proto_research_8066.md`](./planning/P16_朋友圈Proto_research_8066.md) | 8.0.66 | P16 SnsObject 调研；8071 实现见 `P16_朋友圈Proto/result.md` |

---

## 二进制样本（仍在 06_refs）

| 路径 | 说明 |
|------|------|
| `06_refs_参考资料/apk_samples/wechat_8066.apk` | 可选对比样本，**非**当前装机底座 |
| `06_refs_参考资料/apk_samples/wechat_8066_jadx/` | jadx 产物 |

---

## 何时允许打开 archive

- 写「8071 vs 8066 字段差异」对照表
- 解释 FAILURE_LOG 里某条为何禁止 MvvmList.m() 等
- P18 离线采集做版本 diff

**其余时候**：关闭本目录，只读 8071 主车道（见 [`../README.md`](../README.md)）。

---

## 维护记录

| 日期 | 动作 |
|------|------|
| 2026-05-27 | 8066 迁入 `wechat_8066/`；复检见 [`../DOC_AUDIT_2026-05-27.md`](../DOC_AUDIT_2026-05-27.md) |
