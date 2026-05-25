# P26B brief — 本机隐藏自有朋友圈（独家功能）

> ⬜ **待实现** — 只登记需求与约束，**无 L1 日志前禁止写正式 hook**

## 当前状态
⬜ 待实现（2026-05-24 立项待办）

## 产品定义

| 项 | 说明 |
|----|------|
| **做什么** | 用户勾选/标记**自己**的某条朋友圈 → 本机时间线 & 个人主页不再显示该条 |
| **不做什么** | 不删服务器帖、不影响好友看到、不调用微信删帖 API、不写 WCDB |
| **与 D1 区别** | D1 = HIDDEN 态隐藏**密友**帖；P26B = 任意态隐藏**自己**指定帖（独立开关/名单） |

## 设置入口（待设计）
- 位置：`SettingsEntry.showGuardDialog()` → **功能设置**（与「隐藏指定标签」独家占位同卡）
- 形态（候选）：总开关 + 「已隐藏帖子」列表 + 从朋友圈长按/详情页「本机隐藏」入口（❓ 待 UX 定稿）

## 存储（待实现）
| key | 含义 |
|-----|------|
| `Bridge` 短 hash | 例 `homm` — 已隐藏 snsId Set |
| 绑定 | 仅对 `Bridge.getMyWxid()` 的帖子生效；换号清空 |

## Hook 方向（❓ 待 L1 证实）

**候选主路径**（与 D1 同层，禁止改 D1 主逻辑 F-31）：

```
ArrayList.addAll(na4.b)
  └── la4.p.field_userName == selfWxid
  └── snsId（字段名待探）∈ Bridge.getHiddenOwnMomentIds()
        └── Iterator.remove()   // 仅本机列表
```

**待探字段**：timeline item 上 `snsId` / `feedId` / `localId` 的 8.0.71 混淆名（先跑 dynamic_crawler / Frida 探针）

## 相关 F-xx
| 编号 | 一句话 |
|------|--------|
| F-31 | D1 已验路径禁止顺手改 |
| F-28 | 禁止 MvvmList.m 作朋友圈主入口 |
| F-27 | SnsObject Java parseFrom 已证伪 |

## 验收（将来）
1. 隐藏自己的帖 A → 本机朋友圈刷不到 A；好友/另一台仍可见 A
2. 取消隐藏 → A 重新出现
3. D1 密友过滤仍 ✅；KPI 无红线

## 待完成
- [ ] Frida 爬虫：timeline item snsId 字段 + self 帖判定路径
- [ ] Bridge API + SettingsEntry UI
- [ ] `OwnMomentsHideGuard.java`（或 MomentsFilter 扩展分支，独立门控）
- [ ] 装机 logcat：`[OMH] removed snsId=... self=1`

## 关键文件（规划）
- `src/.../moduleD/OwnMomentsHideGuard.java`（新建，待定）
- `MomentsFilter.java` — 只读 D1 路径，扩展时独立方法
- `SettingsEntry.java` — 功能设置 Switch/列表
- `Bridge.java` — MMKV 存储 hidden snsId set
