# 技术架构无分歧共识

> 从 CLAUDE.md §六移出。完整铁律见 FAILURE_LOG.md。

## 6.1 状态机
- **3 态**：显形 / 隐藏 / 解锁中（**无失败计数**）
- 进程内字段 `mVipMode`（仿竞品 UserControll）
- 持久化：MMKV `g_<seed>` 命名空间
- 默认：进程启动从 MMKV 恢复，授权下默认隐藏

## 6.2 朋友圈过滤（MvvmList 实例拦截，v12 方案）
- **主线**：`ArrayList.addAll(na4.b)` → `la4.p.field_userName` 直读（fallback 主路径）→ remove post
- `h1()` 路径 null miss（F-31）；**禁止改回 h1() 主路径**
- D2/D3：`LinkedList.add(z15.e56/cs5.di0)` → `entry.d`（或 `f435583d`）= wxid → block
- ⚠️ **已证伪**：`SnsObject.parseFrom(byte[])` Java hook — 8.0.71 反序列化走 JNI/C++，Java hook 零命中（F-27）

## 6.3 会话过滤（MvvmList 三层，已验证不动）
- L1: `MvvmList.n(List, boolean)` 主力（8.0.71）/ `.m`（8.0.66）
- L2: `MvvmList.s(List)` 备用
- L4: `kc5.v0.notifyDataSetChanged` clean-before 兜底

## 6.4 通讯录过滤（8.0.71 已验证）
- `ArrayList.addAll(fc5.g)` → `g.d`（z3 实例）→ `z3.c1()` → remove

## 6.5 搜索拦截（搜索入口 = 放大镜）
- `SearchFilter`：hook `ArrayList.addAll` + `z15.ef6` 按 wxid 过滤
- `SearchUnlock`：hook EditText，隐藏态 + 入口口令命中（默认 `111111`）→ `unlockEntry()` + 关闭搜索页
- `SettingsEntry`：v10 文字替换——入口可见时「我的资料」→「量子密友」（点击弹设置对话框）；进 HIDDEN → 自动还原
- **假返回"未找到"** = 微信本地无数据时的默认行为（显示"添加好友"）
- `hookSearchContact` 待实现（联系人存在性层）

## 6.6 朋友圈小红点（P21，两个视觉层）
- **MomentsEntryBadge**（朋友圈行入口角标）：`FMF.g1("album_dyna_photo_ui_title", true)` 拦截
- **DiscoverTabBadge**（发现 tab 底部角标）：`TabRedDotChangeEvent`/`WeChatTabRedDotEvent` ctor 清零
- **Layer0b**（互动列表过滤）：`Activity.onResume` 过滤 `SnsMsgUI*`，密友条目不显示 + badge 归零

## 6.7 MMKV
- namespace: `g_<seed4>`（每客户独立 seed）
- key: 4 字符短哈希（**不出现** `hide_list` / `vip_enable` 等明文）

## 6.8 字符串混淆
- 类名/方法名/包名禁用敏感词（vip / hide / pirate / wechat / catfish / myauth / wmiyou）
- Toast / Log TAG / MMKV key 全部 seed 化
- v1 手动换 seed + 按时间定时发版（**不做自动构建**）

## 6.9 C++ 层权责边界（v1 固定，违反 = 触发铁律 30）

**v1 权威状态源分工**

| 进程 | 状态权威源 | wxid 判断来源 | 决策入口 |
|------|-----------|--------------|---------|
| `com.tencent.mm`（主进程） | Java `StateMachine` | `Bridge.getWxids()` Java 集合 | `StateMachine.isActive()` |
| `com.tencent.mm:push` | C++ `libguardcore.so` | C++ `unordered_set`（Batch 1 测试数据） | `NativeBridge.shouldBlockBadge()` |

**v1 C++ 的真实职责（仅此三项，不直接实现隐藏）**：
1. `:push` 进程 badge/unread **早期判断**（`push_should_block_badge`，Java hook 层待接线）
2. 启动校验（进程路由 + 包名反篡改 + 安全默认值 `not_ready()`）
3. 提供 SAFE_MODE / LOCKED 兜底终态（反盗版 + killSwitch 基础设施）+ auth state 存储

**通知/声音/震动/亮屏的真正拦截** → Java 主进程 **PushFilter + CallGuard + ForegroundMute**（moduleC，P22/A5 v1 核心隐私）。

**C++ 升级路线（P0~P3 已完成，P4 待做）**：

| 阶段 | 内容 | 当前状态 |
|------|------|:-------:|
| P0 | 明确双 sm 权责，禁止主进程过滤链碰 C++ | ✅ 本铁律 |
| P1 | Java Bridge add/remove wxid/group → 实时推送 C++ matcher | ✅ 已验证（2026-05-23 DebugServer 密友 J/N=2/2 同步） |
| P2 | Java `StateMachine` H/V/U 切换 → 同步写 C++ sm（双写桥）| ✅ 已验证（2026-05-23 Java态=C++态=VISIBLE） |
| P3 | 主进程 + `:push` 冷启动 `reloadIds()` 初始化 | ✅ 已验证（进程=MAIN，SO已加载，状态同步） |
| P4 | 授权 LicenseBox + miyou-server killSwitch 接入 C++ | 🟡 **P4-1 已写待装机**（见 §6.10）|

P1~P3 全部完成并经 DebugServer 实证。主进程过滤链切到 C++ 唯一状态源条件已具备，但 v1 继续沿用 Java 主链（稳定不动原则）。

---

## 6.10 授权双绑定模型（wxid + 设备，P4-1 已实装，待装机验证）

**两个完全独立的判断**：

| 判断层 | 决定什么 | 实现位置 |
|--------|---------|---------|
| **授权判断**（wxid + 设备） | 功能能不能用 | `AuthManager.java` + C++ `auth_engine.cpp` |
| **入口判断**（本地密码） | 设置入口/显形入口能不能打开 | `SearchUnlock.java` |

**4 种授权状态**：

| 状态 | 条件 | 功能 hook | 入口 | 触发弹窗 |
|------|------|:--------:|:----:|:-------:|
| `AUTH_OK` | wxid ✅ + 设备 ✅ | ✅ 全开 | 按密码显隐 | — |
| `AUTH_ACCOUNT_MISMATCH` | 设备 ✅，wxid ❌ | ✅ v1 放行 | 静默 | — |
| `AUTH_DEVICE_MISMATCH` | wxid ✅，设备 ❌ | ✅ v1 放行 | 静默 | — |
| `AUTH_NO_LICENSE` | 首装/未绑定 | ✅ v1 放行 | 静默 | — |
| `AUTH_TAMPERED` | 包名/证书被篡改 | ❌ 全关 | 隐藏 | ✅ 引流弹窗 → `zxmqq.shop` |

**v1 说明**：ACCOUNT_MISMATCH / DEVICE_MISMATCH / NO_LICENSE 三种状态 hook 仍注册（v1 自用），仅 AUTH_TAMPERED 真正拦截并弹引流窗。

**wxid 自动捕获**（零反射，2026-05-23 实证）：
```java
// 主源（8.0.71 实测）：单账号/多账号均有效
SharedPreferences sp = app.getSharedPreferences("com.tencent.mm_preferences", 0);
String selfWxid = sp.getString("login_weixin_username", "");
// 实测值: wxid_toghm7m6uqsr12 ✅

// 备用源（仅多账号用户，曾切换过账号才有值）：
// switch_account_preferences → last_switch_account_to_wx_username
```

**昵称/微信号采集**（`SelfProfileCapture.java`）：
- `View.onAttachedToWindow` hook 过滤 id=`0x7f0c6c6d`（资源名 `ouv` = "我"Tab 微信号 TextView）
- 300ms 后读 getText()，去前缀 "微信号：" → 写 `Bridge.KEY_MY_ALIAS`
- `ContactResolver.resolveJson(wxid)` 三层回退补昵称 → 写 `Bridge.KEY_MY_NICK`

**绑定流程（v1）**：
```
访问 localhost:8080 → 点"绑定当前账号" → POST /api/bind_account
→ AuthManager.bindAccount() → 写 licensedWxid + deviceHash
→ NativeBridge.setAuthState(AUTH_OK) → 重启微信后正式生效
```

**铁律**：搜索框密码正确 → 只能打开入口/设置页，永远不能绕过 wxid 授权；换号 → ConvFilter/PushFilter/MomentsFilter 全部不工作，不暴露原账号密友列表。
