# CURRENT_PLAN — 当前阶段计划

> 维护人：guard-dispatch_总调度
> 更新：2026-06-08（对齐 TASK_BOARD 06-06 + E2 06-07 落地）

---

## 当前阶段：v1 阶段 ① 功能开发（D-015 锁定）

### 当前一句话状态（2026-06-08 01:50）

> **E2 伪装订位已收口（应用户要求提前于 v2/v3 落地，06-07 装机验证 + 06-08 文档/git 全收敛）；当前主线收敛到 P22 PushFilter 通知层最后两项缺口：P_NF1 普通消息通知 + P_NF2 铃声功能。**

### 四窗口现状（对齐 TASK_BOARD §一）

```
W1  B 模块触发 + 搜索 (P20/P20B)  ✅ P20 搜索 / 🟡 P20B（B4 不阻塞，KPI 发版前重测）
W2  朋友圈小红点  (P21)            ✅ Layer0b 装机；备用层不阻塞
W3  会话 LSPosed  (P17)            ✅ 已收口（05-20）
W4  离线资料库采集 (P18)           ⬜ 待领（发版前必须补空白 LSPosed KPI 基线）
```

### 临时插入 / 已收口

| 项 | 状态 | 完成日 | 备注 |
|---|:--:|---|---|
| E2 伪装订位 | ✅ | 2026-06-07 装机 + 06-08 文档/git 全收敛 | 应用户要求提前于 v2/v3；`moduleE/FakeLocation` |
| P_NF4 密友未读计数过滤 (UNREADFIX) | ✅ | 2026-06-06 装机 | |
| P_NF3 :push 独立震动 (route1) | 🟡 | 代码已写 | 纯 :push 场景待实证 |

---

## 主线缺口（按优先级）

### P1 — P22 PushFilter 收尾（v1 必须）
1. **P_NF1** 🟡 普通消息通知链路（后台震动已实证；缺前台/路由完整链）
2. **P_NF2** ⬜ 铃声功能（backlog，需决定 SOUND 档方案 — ToneGenerator / Ringtone / native / i3 Handler 候选已探）
3. **P_NF3** 🟡 :push 独立震动（route1 实证）
4. 详见 `docs/P22_PushFilter_VoIP.md`

### P2 — native_core Batch 1 装机
- **P_NC1** 🟡 编译/接入完成，**待 `[native] BATCH1_VERIFY PASS` 装机日志**

### P3 — 待复现 bug
- **P_CF2** ⬜ 会话列表越界崩溃（与 P_PF2 来电拦截重构相关，详见 `docs/P22_PushFilter_VoIP.md`）

### P4 — UI 优化（不阻塞 v1）
- **P26C** 搜索高亮（归 UI 优化）

### P5 — 发版前 KPI 基线（W4 P18）
- ⬜ 跑 `frida_stats.js` 空白 LSPosed KPI 基线
- ⬜ 补跑 E2 启停 KPI 对比（F-22 铁律）

---

## E3 改零钱（用户曾提，**风险阻塞**）

- ⛔ 调研阶段触发反 frida 杀进程（F-37 MallIndexUIv2）
- dispatch 风险评估：金融数据伪造，触法律红线（监管高敏区，封号 + 冻结风险）
- 决策：⬜ 待用户拍板降级方案（B1 仅 debug + 测试 wxid / B2 改余额遮罩 / B3 砍入 REJECTED_OPT）
- 接入前必走：`/guard-auth-review` 合规预审
- 已写入 `PROTECTION_MAP.md` §9b 待办

---

## 下一阶段（D-015 锁定五阶段顺序）

### 阶段 ② 替换/加密预热（v1 收尾）
- P24 docs/classmap/v8071.yaml + tools/check_classmap.ps1
- P25 字符串/类名 seed 化流水线

### 阶段 ②.5 + ③ + ④（v2）
- P26–P30：UI 优化 / LicenseGate / ClassMap 加密 / miyou-server / 通知伪装 C2

### 阶段 ⑤ 打包形态（v3）
- P31–P33：LSPatch 双模式 / 签名校验绕过 / 反盗版引流壳

> 阶段铁律：① 没收口前禁止开 ②；②③ 没收口前禁止开 ⑤。

---

## 收尾债（不阻塞主线，但要补）

- ⬜ `signing/guard-native-debug.keystore` git 处置（A 提交 / B .gitignore 排除 / C 外部管理）— 待用户拍板
- ⬜ 一批"其他领域"modified 待按 P 任务顺序补提交（14 skill / DebugServer / TriggerGuard / NotifyRouter / MomentsRedDotGuard / build.gradle / 6 docs / 06_refs/CATFISH_REVERSE 等）

---

## 历史归档（按时间）

| 日期 | 事件 |
|---|---|
| 2026-05-19 | v1 范围口径锁定（D-011）；P15/P19 底座 |
| 2026-05-19~20 | P15/P16/P17/P19 全部✅ |
| 2026-05-20 | P20 SearchUnlock+SearchFilter 🟡 待装机 |
| 2026-05-21 | A3 密群 Filter union 装机✅；DECISION_LOG D-015 锁定五阶段 |
| 2026-05-29 | P_CV1/P_PF2/P_CF3/P_CF5 一波装机✅ |
| 2026-05-31 | P23 防撤回 C1 ✅ |
| 2026-06-01 | P19B 标签内成员密友过滤 ✅ |
| 2026-06-02 | PROTECTION_MAP Phase 0 完成（DebugServer DEV-gate + proguard 收窄 + AuthManager 接死代码） |
| 2026-06-06 | P_NF4 密友未读计数过滤 UNREADFIX ✅；P20 搜索全场景收口；dispatch §6-06 文档瘦身原则立 |
| 2026-06-07 | E2 伪装订位装机验证 ✅（应用户要求提前于 v2/v3） |
| 2026-06-08 | E2 git 全收敛（6 commit）+ CURRENT_PLAN 同步 |
