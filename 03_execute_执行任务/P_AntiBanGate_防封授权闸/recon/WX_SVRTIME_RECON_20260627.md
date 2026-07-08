# RECON 任务单 · 找微信真服务器授时点（WX server-time）

> 角色：防封官 / 研究线（侦察）。要 device + frida + jadx。
> 触发：未授权解体恢复线（拟推翻 D-018）需 `LeaseClock.trustedNow` 的**第二独立时间源防冻结**；现用 `field_conversationTime` 是「地板」、空闲偏旧（A2接入设计稿 §5 自承），用户 F86 要求换微信自己更准的服务器对时。
> 真源对齐：`A2接入设计稿_签名轴_20260625.md §5`（双授时源）/ `DESIGN.md §6`（双授时源防冻结）/ `DECISION_LOG.md D-018`（拟推翻）。
> 命名/红线：照 `CLAUDE.md` 定死词表 + 借官方眼睛、零新增检测面优先。

---

## 0. 一句话
找到微信内部「服务器对时」值（serverTime，随长连接/mmtls 对时**实时刷新**），作为 `LeaseClock.trustedNow` 的第二独立时间源——比 `field_conversationTime`（最近消息地板、空闲偏旧）更准、更难冻。

## 1. 背景 / 为什么要它
- 未授权解体的**命门 = 可信时间不能被冻结**：盗版「屏蔽我方服务器 `sn` + 反复重启（`elapsedRealtime` 归零）」可冻住 `trustedNow` → 48h 窗口永不到 → 白嫖不死。
- 第二源必须**独立于我方服务器**。现选 `field_conversationTime`（借官方眼睛，L1 2026-05-29，`ConvFilter.extractConvTime`），但它是**地板**（只在收到消息时刷新，空闲旧）。
- 微信自身有更准的服务器对时（消息排序 / 防重放需要），实时性更好、更难冻。找到它 = 更强的防冻结。

## 2. 假设（L4 待证，不可当结论）
- 微信靠 **longlink（长连接）/ mmtls 握手响应**携带服务器时间戳，本地维护 `serverTime ≈ SystemClock.elapsedRealtime() + offset`，每次对时刷新 offset。
- 8.0.71 相关类 / 字段 / 方法**混淆**，名字未知 → 必须 recon，**不得猜**（G1）。

## 3. recon 方法（先静态后动态）

### 3.1 jadx 静态（找候选锚点）
- 搜未混淆字符串：`server time` / `svr` / `ntp` / `时间` / `clockOffset` / `timeDiff` / `serverTimeMs` / `getServerTime` / `kvStat`。
- 搜 `SystemClock.elapsedRealtime()` 的调用者，挑「elapsedRealtime + 某 long offset」形态（= serverTime 计算点）。
- 搜 longlink / mmtls 收包链里的服务器时间戳字段（protobuf 解析里的 time/ts/svrTime）。
- 候选清单（类/方法/字段 + 路径）写进 recon 日志，每条标 **L2**（jadx 确认）。

### 3.2 frida 动态（确认实时刷新 + 取真值）
- spawn 8.0.71，hook 候选 getter/字段读点，打印值 + 调用栈。
- 对比：候选值 vs 真实北京时间 vs `field_conversationTime`。确认候选「实时刷新（不随消息空闲变旧）+ 等于服务器盖戳」。
- **冻结测试**：断我方服务器 + 重启，看候选值是否仍前进（不被冻）。
- L1 日志原文落 `recon/`。

### 3.3 对比基准（判定）
- 候选实时刷新 + 难冻 + 等服务器时间 → 选为第二源。
- 若候选也能被简单冻（仅本地缓存不刷新）→ **证伪，退回 `field_conversationTime` 地板**（不硬凑）。

## 4. DoD（验收，缺一不可）
- **L2**：jadx 定位候选类/字段/方法（混淆名 + 路径）。
- **L1**：frida 实证候选值 = 服务器时间、实时刷新、断我方服务器+重启不被冻（或**证伪 → 记结论退回地板**）。
- **接法草稿**：候选 → `LeaseClock.noteOfficialTime(ms)`（只抬不降 + 未来上限兜底），交授权检查官 + 安全官审。
- **零新增检测面核**：确认 hook/读点不新增环境读取、不破坏微信（守铁律5 / KPI）。

## 5. 红线
1. **借官方眼睛、纯读**，不改微信对时本体、不注入微信 JNI（铁律23）。
2. **零新增检测面优先**；若候选要新 hook，过 KPI 体检（verifiedbootstate/normsg/PROP/CONN）。
3. 找不到 / 证伪 → 老实**退回 `field_conversationTime` 地板**（不硬凑、不猜）。
4. **不动已验证 hook**（铁律29 / F-31），尤其 `ConvFilter` P_CF3 排序链不碰。

## 6. 设备 / 工具
- device `609b4b18`（Mi 9 / cepheus / 微信 8.0.71）。
- frida-server（先确认在跑）、jadx（8.0.71 反编译源）。
- 日志落 `03_execute_执行任务/P_AntiBanGate_防封授权闸/recon/` + `logs/`。

## 7. 产出 → 下游
- 找到 → 写回**未授权解体恢复设计**（`trustedNow` 第二源从 `field_conversationTime` 升级为真授时）+ `LeaseClock.noteOfficialTime` 接法。
- 关联：A2接入设计稿 §5、DESIGN §6、未授权解体恢复线（拟推翻 D-018）。

## 8. 状态
- ⬜ 未领（建单 2026-06-27 · F86）。前置：device 在、frida-server、jadx 8.0.71 源。
- 兜底：找到前，恢复设计先用 `field_conversationTime` 地板（对解体只偏保守、不误杀）。

# End
