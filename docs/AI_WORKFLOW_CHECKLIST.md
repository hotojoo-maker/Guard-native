# AI 工作流清单 — 防遗忘 / 防原地转圈

> 这是给用户看的中文总控清单。  
> AI 每次接手项目时，必须先读 `CLAUDE.md`，再读本文件，再读 `docs/USER_AI_USAGE_GUIDE.md`，然后按本文件一步一步做。  
> 每完成一步，AI 必须在本文件里把对应复选框打钩，并写一句“实际结果”。

---

## 现在的总目标

做一个微信 8.0.66 的 LSPosed 模块，先实现最重要的功能：

```
F04 会话隐藏
```

也就是：用户指定某个 `wxid` 后，这个人的微信会话在会话列表里不显示。

短期只做 Java / LSPosed，不做 Pine，不做 native SO，不做联网授权。

---

## 给用户看的进度总表

| 顺序 | 阶段 | 目的 | 状态 | 实际结果 |
|---:|------|------|:---:|----------|
| 1 | 最小工程骨架 | 让项目能编译成 LSPosed 模块 APK | ⬜ | 未开始 |
| 2 | F04 最小隐藏 | 先让一个写死的 wxid 会话消失 | ⬜ | 未开始 |
| 3 | F04 稳定补强 | 解决重进微信、收消息、切页面后又出现的问题 | ⬜ | 未开始 |
| 4 | 隐藏名单配置 | 不再写死 wxid，改成可配置名单 | ⬜ | 未开始 |
| 5 | F05 朋友圈隐藏 | 指定 wxid 的朋友圈不显示 | ⬜ | 未开始 |
| 6 | F07 通讯录隐藏 | 指定 wxid 的通讯录条目不显示 | ⬜ | 未开始 |
| 7 | 性能优化 | 只有 Java 方案卡顿时才考虑 Pine/SO | ⬜ | 未开始 |

状态说明：

| 符号 | 意思 |
|:---:|------|
| ⬜ | 未开始 |
| 🔄 | 正在做 |
| ✅ | 已完成并验证 |
| ⚠️ | 部分完成，有问题 |
| ❌ | 失败，不能继续用这个方案 |

---

## 阶段 1：最小工程骨架

### 用户要知道的重点

这一步不是实现隐藏功能，而是先让项目变成一个能安装、能被 LSPosed 识别的模块。

### AI 要做什么

- [ ] 创建 Android 必需目录：`app/src/main/`
- [ ] 创建 `AndroidManifest.xml`
- [ ] 创建 LSPosed / Xposed 模块配置
- [ ] 创建 Java 代码目录：`app/src/main/java/com/guard/wechat/`
- [ ] 创建入口文件：`MainHook.java`
- [ ] 创建反射工具：`ReflectUtils.java`
- [ ] 确认项目能编译 APK

### 最重要的代码点

| 文件 | 作用 |
|------|------|
| `MainHook.java` | LSPosed 入口，微信启动时先进入这里 |
| `handleLoadPackage(...)` | 最重要入口函数，只允许处理 `com.tencent.mm` |
| `ReflectUtils.java` | 专门处理反射，避免代码到处乱写 |

### 验收标准

- [ ] 能生成 APK
- [ ] LSPosed 能看到这个模块
- [ ] 模块只作用于微信：`com.tencent.mm`
- [ ] logcat 能看到 `[guard]` 开头日志

实际结果：

```
未开始
```

---

## 阶段 2：F04 会话隐藏 — 最小可验证

### 用户要知道的重点

这一步先不做漂亮配置，先写死一个测试 `wxid`。  
只要这个人的会话能消失，就说明最核心的路打通了。

### AI 要做什么

- [ ] 创建 `ConvFilter.java`
- [ ] 在 `MainHook.java` 里调用 `ConvFilter.init(...)`
- [ ] hook 微信的 `MvvmList.m(List, boolean)`
- [ ] 可选 hook `MvvmList.s(List)`
- [ ] 从会话 item 里取出 `wxid`
- [ ] 如果 `wxid` 命中隐藏名单，就从列表里移除

### 最重要的代码点

| 点位 | 说明 |
|------|------|
| `com.tencent.mm.plugin.mvvmlist.MvvmList` | 微信会话列表的数据容器 |
| `m(List, boolean)` | 最重要函数，批量数据进入列表前会经过这里 |
| `s(List)` | 备用入口，有些数据可能从这里进 |
| `ConvFilter.filterConvList(...)` | 我们自己的过滤函数 |
| `item.d -> m3.j1()` | 从会话 item 里拿 `wxid` 的路径 |

### 验收标准

- [ ] 微信能正常打开
- [ ] logcat 能看到 F04 hook 安装成功
- [ ] 测试 `wxid` 的会话不显示
- [ ] 普通会话不受影响
- [ ] 滑动会话列表不崩溃

实际结果：

```
未开始
```

---

## 阶段 3：F04 会话隐藏 — 稳定补强

### 用户要知道的重点

阶段 2 只是证明能隐藏。  
阶段 3 是让它更稳：重进微信、收到新消息、切换页面后，隐藏项不要又冒出来。

### AI 要做什么

- [ ] hook `f45.s0.notifyDataSetChanged()`
- [ ] 在刷新界面前清理 `MvvmList` 里的列表
- [ ] 加 `g_cleaning` 防止重复清理造成死循环
- [ ] 加 1000ms cooldown，避免太频繁
- [ ] 做 `weixin` 特殊处理：有未读时保留
- [ ] 做 warm-attach：微信已经运行时也能补清理

### 最重要的代码点

| 点位 | 说明 |
|------|------|
| `f45.s0` | 8.0.66 会话列表 Adapter |
| `notifyDataSetChanged()` | 界面全量刷新前的安全清理点 |
| `MvvmList.o / p / h` | 需要清理的底层列表 |
| `g_cleaning` | 防止自己触发自己，避免死循环 |
| `field_unReadCount` | 判断 `weixin` 是否有未读 |

### 绝对不要做

- [ ] 不用 `notifyItemRangeInserted`
- [ ] 不用 `notifyItemRangeRemoved`
- [ ] 不 hook `getCount`
- [ ] 不 hook `getView`
- [ ] 不用 `View.GONE` 当主方案

### 验收标准

- [ ] 重启微信后隐藏仍生效
- [ ] 收到新消息后隐藏仍生效
- [ ] 切换页面回来隐藏仍生效
- [ ] 连续滑动 5 分钟不崩溃
- [ ] `weixin` 有未读时不被误隐藏

实际结果：

```
未开始
```

---

## 阶段 4：隐藏名单配置

### 用户要知道的重点

前面是写死 `wxid`。  
这一步要让名单可以改，不需要每次重新写代码。

### AI 要做什么

- [ ] 设计隐藏名单存储方式
- [ ] 优先用简单可靠的本地配置
- [ ] 会话隐藏读取 `hide_conv_list`
- [ ] 后续朋友圈读取 `hide_moments_list`
- [ ] 后续通讯录读取 `hide_contact_list`

### 最重要的代码点

| 点位 | 说明 |
|------|------|
| `hide_conv_list` | 会话隐藏名单 |
| `hide_moments_list` | 朋友圈隐藏名单 |
| `hide_contact_list` | 通讯录隐藏名单 |
| `ConvFilter.isHiddenWxid(...)` | 判断某个 wxid 是否要隐藏 |

### 验收标准

- [ ] 改名单后能生效
- [ ] 空名单时不隐藏任何人
- [ ] 名单格式错了也不能导致微信崩溃

实际结果：

```
未开始
```

---

## 阶段 5：F05 朋友圈隐藏

### 用户要知道的重点

这一步比会话隐藏风险更高，必须等 F04 稳定后再做。  
不能边做 F04 边做 F05，否则容易混乱。

### AI 要做什么

- [ ] 先重新核对 `docs/HOOK_POINTS.md`
- [ ] 再核对 `refs/FAILURE_LOG.md`
- [ ] 确认采用哪个已验证方案
- [ ] 创建 `MomentsFilter.java`
- [ ] 只处理朋友圈数据，不影响会话列表

### 最重要的代码点

| 点位 | 说明 |
|------|------|
| `com.tencent.mm.plugin.sns.ui.improve.component.y1` | 8.0.66 朋友圈 Adapter |
| `y1.H` | Adapter 里持有的 `MvvmList` |
| `SnsObject.field_userName` | 朋友圈作者 wxid |
| `MomentsFilter.filterMomentsList(...)` | 我们自己的朋友圈过滤函数 |

### 特别提醒

`docs/HOOK_POINTS.md` 和 `refs/FAILURE_LOG.md` 里对 F05 的最终方案记录不完全一致。  
AI 做 F05 之前，必须先整理清楚，不允许直接开写。

### 验收标准

- [ ] 指定 wxid 的朋友圈不显示
- [ ] 普通朋友圈不受影响
- [ ] 刷新朋友圈不崩溃
- [ ] 慢滑 5 分钟不崩溃

实际结果：

```
未开始
```

---

## 阶段 6：F07 通讯录隐藏

### 用户要知道的重点

通讯录不是会话列表，不能直接套 F04 的代码。  
必须重新确认数据结构后再做。

### AI 要做什么

- [ ] 查 `docs/HOOK_POINTS.md` 是否已有 F07 资料
- [ ] 如果资料不足，先做 Frida 验证
- [ ] 找到通讯录列表的数据入口
- [ ] 创建 `ContactFilter.java`

### 最重要的代码点

| 点位 | 说明 |
|------|------|
| `MvvmAddressUIFragment` | 当前已知通讯录相关入口 |
| `ContactFilter.java` | 后续通讯录隐藏实现文件 |

### 验收标准

- [ ] 指定 wxid 不出现在通讯录
- [ ] 搜索、滑动、进入详情不崩溃
- [ ] 不影响普通联系人

实际结果：

```
未开始
```

---

## 阶段 7：性能优化

### 用户要知道的重点

这一步不是现在做。  
只有 Java / LSPosed 方案已经跑通，并且确实卡顿，才考虑 Pine 或 native SO。

### AI 要做什么

- [ ] 先证明 Java 方案有性能问题
- [ ] 只迁移高频函数
- [ ] 保留 Java 逻辑作为 fallback
- [ ] 不加联网
- [ ] 不加授权
- [ ] 不加反调试

### 最重要的代码点

| 点位 | 说明 |
|------|------|
| Pine/native | 只用于性能瓶颈，不提前做 |
| Java fallback | native 出问题时仍能回退 |

### 验收标准

- [ ] 功能和 Java 版一致
- [ ] 滑动明显不卡
- [ ] APK 仍然简单可控

实际结果：

```
未开始
```

---

## AI 每次开工前必须做的事

- [ ] 读 `CLAUDE.md`
- [ ] 读 `docs/AI_WORKFLOW_CHECKLIST.md`
- [ ] 读 `docs/USER_AI_USAGE_GUIDE.md`
- [ ] 读当前阶段对应的 `docs/HOOK_POINTS.md`
- [ ] 如果要用某个方案，先查 `refs/FAILURE_LOG.md`
- [ ] 只做当前阶段，不跨阶段乱做
- [ ] 做完后更新本文件的状态和实际结果
- [ ] 如果功能状态变化，同步更新 `refs/FEATURE_MATRIX.md`

---

## 防止 AI 原地转圈的规则

1. 不允许重复尝试 `refs/FAILURE_LOG.md` 已经判死刑的方案。
2. 不允许同时做多个大功能，比如 F04 没完成就写 F05。
3. 不允许因为“感觉可以”就换路线，换路线必须先写清楚原因。
4. 不允许先做 Pine/SO，除非 Java 方案已经验证并确认性能不够。
5. 不允许把 8.0.70 的方案直接套到 8.0.66。
6. 每次只推进一个阶段，完成后打钩，再进入下一阶段。

---

## 当前下一步

现在应该做：

```
阶段 1：最小工程骨架
```

完成阶段 1 后，再做：

```
阶段 2：F04 会话隐藏 — 最小可验证
```
