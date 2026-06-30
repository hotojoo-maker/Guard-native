# HOOKMAP 废弃拦截层 — ❌ 已证伪 / 永久废弃归档

> 来源：从 [`../../HOOKMAP.md`](../../HOOKMAP.md) §二 关键拦截层 表移出（2026-06-29，方案 B 收敛）。
> 目的：主表只留**现行有效层**，接手者一开始不被历史废弃层绕晕；**历史不删，永久保留于此**（HOOKMAP §六 规则5「永久不修改 ❌ 行」+ 质检铁律「不删资料只搬归档」）。
> 详细失败根因见 [`../../FAILURE_LOG.md`](../../FAILURE_LOG.md) 对应 F 编号（F-27 / F-31 / F-32x/y/z 等）。

---

## ❌ 已证伪 / 永久废弃拦截层（原 HOOKMAP §二）

| 层 | hook 点 | 状态 | 跨版本稳定度 | 详情 |
| --- | --- | --- | --- | --- |
| ❌ ~~L0 Proto~~ | ~~`SnsObject.parseFrom`~~ — F-27 证伪 | ❌ 永久废弃 | ~~⭐⭐⭐⭐⭐~~ | 8.0.66 走 JNI/C++，Java hook 零命中 |
| ❌ ~~PushFilter L4b~~ | ~~`MainTabUI.i()` afterHook~~ — 装机零触发 | ❌ 旁路废弃 | — | 2026-06-06 实证全程无 `[PF:L4b] real=`，微信未读不走此路径；已被 UNREADFIX 取代 |
| ❌ ~~PushFilter L4c~~ | ~~`h0.d(int)` 减法~~ — 已禁用 | ❌ 已禁用 stub | — | `installL4c` 仅打 `[PF:L4c] disabled (pending WeChatDND)`，过度扣减问题待 WeChatDND 取代 |
| ❌ ~~x.a(f9)~~ | ~~`booter.notification.x.a(f9)`~~ — 8.0.71 零命中证伪 | ❌ 永久废弃 | — | hook 注册成功但运行时零触发，不走此路径 |
| ❌ ~~NotificationItem.a(Context)~~ | ~~`final` 方法 + ART AOT 内联~~ | ❌ 永久废弃 | — | Xposed 无法拦截；Frida 可以但模块不用 |
| ❌ ~~ss4.p.onBindViewHolder~~ — F-32x | RecyclerView ss4.p 本体 vis=0 但**父级 ConstraintLayout vis=8 GONE**，根本不上屏；2026-05-27 02:23 误锚定，2026-05-27 04:00 推翻 | ❌ 永久废弃 | — | 真渲染容器是 ListView (HeaderViewListAdapter wraps q2)；fts_tree_v2.log L243/273 铁证；hook 装上永不触发 |
| ❌ ~~q2.j(View, jz2.g, boolean)~~ — F-32y | 8.0.71 搜索结果渲染**不经** q2.j；hook 装上 0 触发（final_v15 终端 AI 实证）；保留陪跑 | ❌ 主路径废弃，仅作 backstop | — | q2 渲染走 q2.getView (从 f0 继承) afterHook 路径；不要试图把 q2.j 当主入口 |
| ❌ ~~SearchFilter 5-hook offset (getCount/getView/getItem/getItemId/getItemViewType)~~ — F-32z | setResult(orig-skip) 干扰 q2 内部 data swap，搜索结果区**完全空白** + ANR；final_v11/v12/v13/v14 装机连续 4 次空白实证 | ❌ 永久废弃 | — | 单 hook afterHook GONE 就够；不要试图缩 ListView count；如想消空白条用 setDivider(null) + lp.margin=0 而非 count 减 |

---

> ⚠️ 这些是 FAILURE_LOG 式「试错防踩坑」记录，**禁止当作可重试方案**。下个 AI 想动 `q2.j` / `SnsObject.parseFrom` / `ss4.p` / `x.a(f9)` / `MainTabUI.i()` 前，先看本表 + FAILURE_LOG 对应 F 编号，避免重踩。
