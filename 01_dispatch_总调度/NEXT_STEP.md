# NEXT_STEP — 窗口占用板（已收敛 → 见 TASK_BOARD §一）

> ⚠️ **2026-06-10 收敛**：本表旧版停在 2026-05-19 立项日（四窗口全「⬜ 待领」、P15~P18 没人领、T05/T07/T08 全 ⬜），与 [`../TASK_BOARD.md`](../TASK_BOARD.md) §一 重复且早已漂移。
> **实时窗口占用/状态以 [`../TASK_BOARD.md`](../TASK_BOARD.md) §一 为唯一来源**；当前阶段计划见 [`./CURRENT_PLAN.md`](./CURRENT_PLAN.md)。
> 本文件只保留「领取规则 / 跨窗口同步原则」这类流程约定，不再单独维护占用表。

---

## 当前窗口快照（镜像 TASK_BOARD §一，2026-06-09）

| 窗口 | P 任务 | 状态 |
|:--:|------|:--:|
| W1 | B 模块触发器 + 搜索（P20/P20B） | P20 ✅；P20B 🟡（功能不阻塞 v1）|
| W2 | 朋友圈小红点（P21） | ✅ 主线收尾（Layer0b + P21B WithAll/bm）|
| W3 | 会话 LSPosed（P17） | ✅ 已收口（2026-05-20）|
| W4 | 离线资料库采集（P18） | ⬜ 本轮跳过，不阻塞 v1 |

> 改这张表前先看 TASK_BOARD §一；两处不一致一律以 TASK_BOARD 为准。

---

## 领取规则

1. 在 [`../TASK_BOARD.md`](../TASK_BOARD.md) §一 改对应行的「占用至 / 会话 ID」
   - 占用至 = `2026-05-20 18:00` 这种具体时间
   - 会话 ID = Cursor 会话 ID 或自定义编号
2. 状态 ⬜ → 🟡
3. 完成后状态 🟡 → ✅，并写交接快照到 `04_review_审稿复核/W<N>_<日期>.md`
4. 释放：清空「占用至 / 会话 ID」列，状态归 ⬜（同 P 任务下次别人接续做）

---

## 跨窗口同步原则

- **看板写入冲突**：根目录的 `HOOKMAP.md` / `TASK_BOARD.md` / `FAILURE_LOG.md` 修改前 git pull
- **冲突解决**：先到先得，后到者人工 rebase
- **共享数据**：通过 `06_refs_参考资料/`（采集快照/dump 等）
- **依赖确认**：TASK_BOARD §一「模型建议」/ 依赖在各 P 任务 brief 写明

---

## 派出的 T 调研任务（已收敛）

| T 号 | 主题 | 状态 |
|:--:|------|:--|
| T05 | 8.0.66 SnsObject Proto 类定位 | ❌ 已证伪归档（F-27：朋友圈走 MvvmList `addAll` 实例拦截，非 Proto；定义存档 `docs/archive/wechat_8066/tasks/`）|
| T07 | 通讯录 hook 点调研 | ✅ 已结案（P19 通讯录 `fc5.g` → `z3.c1()` 装机 2026-05-20）|
| T08 | 防撤回 hook 点调研 | ✅ 已并入 P23（`jy0.t.f` doRevokeMsg，L1 装机 2026-05-31）|

> 新 T 任务投放 `04_review_审稿复核/T_TASKS/`，由 guard-review_质检门控 skill 的资料功能维护。
