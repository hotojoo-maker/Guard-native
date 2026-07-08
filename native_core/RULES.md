# RULES — libguardcore.so 开发铁律

> 本文件是 `CLAUDE.md` 铁律 2 / 6 / 23 / 27 对 native_core 的专项扩展。  
> 与 `CLAUDE.md` 冲突时，以 `CLAUDE.md` 为准。  
> **任何 native_core 任务接手前必读本文件。**

---

## 一、绝对禁止（违反立即停止）

| 禁止事项 | 原因 / 铁律 |
|---------|-----------|
| `dlopen` 微信自身任何 SO | 铁律 23 F-23 |
| 接入微信 `JNI_OnLoad` 链 | 铁律 23 F-23 |
| 重碰微信 native 方法 | 铁律 23 |
| 引入 Pine / bypassmm / shadowhook | 铁律 2（封号高暴露）|
| `System.loadLibrary` 加载非模块自身 SO | 铁律 23 |
| Hook `:sandboxed_process` / `:isolated_*` / `:appbrand*` | 铁律 6 |
| 在 `:push` 进程做 UI 操作 / Overlay / Toast / 通知栏 | 铁律 6 |
| 在 `:push` 进程调 `ActivityManager` / `getRunningAppProcesses` | 铁律 6 + 铁律 7 |
| 在 `:push` 进程做网络请求（授权校验等） | 铁律 6 |
| 在 `:push` 进程 hook 全局 List / 业务页面 | 铁律 6 |
| `JNI_OnLoad` 中调用 `nativeInit()`（微信 JNI 链触发）| 铁律 23 / 铁律 27 |
| nativeInit() 前调用任何业务 hook | 铁律 27 |

---

## 二、libguardcore.so 加载规则

```
正确加载路径（LSPosed 模块初始化流程）：

  ModuleMain.handleLoadPackage()
    └─→ System.loadLibrary("guardcore")     ← 模块 ClassLoader，不是微信
    └─→ NativeBridge.nativeInit(processName, packageName)
    └─→ 注册业务 hook（此时 C++ 已就绪）

禁止路径：
  ❌ 微信 Application.onCreate 内 loadLibrary
  ❌ JNI_OnLoad 内触发 nativeInit
  ❌ 任何非受控时机加载 SO
```

---

## 三、:push 进程规则（铁律 6 细则）

### 允许

- 读取 `nativeIsHidden()` 状态
- 读取 `nativeIsHiddenWxid(wxid)` / `nativeIsHiddenGroup(groupId)`
- 调用 `nativeShouldBlockBadge(wxid)` 判断是否拦截 badge 写入
- 少量限流日志（LogLimiter，每 tag 每 30 秒 ≤ 1 条）
- `nativeInit()`（初始化）

### 禁止

- 写入任何状态（`nativeSetHidden` 在 :push 进程无效）
- UI 操作 / Overlay / Toast / 通知栏
- `ActivityManager` / `getRunningAppProcesses`
- WebServer / HTTP Server
- 复杂反射 dump
- 全局 List hook（如全局 ArrayList.add）
- 网络授权请求
- 业务页面过滤（朋友圈/会话/通讯录 hook）

### :push hook 模板

```java
// :push 进程内 badge hook 模板（铁律 27 短路规则）
@Override
protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
    // 第一步短路：未隐藏直接放行
    if (!NativeBridge.nativeIsHidden()) return;
    // 第二步判断：wxid 是否密友
    String wxid = extractWxidFromParam(param);
    if (NativeBridge.nativeShouldBlockBadge(wxid)) {
        param.setResult(null);  // 拦截 badge 写入
    }
}
```

---

## 四、证据等级系统（所有 native_core 文档强制）

| 等级 | 含义 | 用法 |
|------|------|------|
| ✅ | 动态验证通过（有 logcat / 测试日志） | 已实证的接口、行为 |
| 🟡 | 代码已写，未装机验证 | 写了但未跑过 |
| ⬜ | 只读规划，未有代码 | 设计文档阶段 |
| ❌ | 已证伪，永久禁用 | 失败方案，需写进 FAILURE_LOG.md |

**规则**：
- 文档中所有技术结论必须标注证据等级
- 本目录所有文件当前全部为 ⬜（Phase 0，纯规划）
- 升级到 🟡 需要：代码文件存在 + 编译通过
- 升级到 ✅ 需要：装机日志原文

---

## 五、Anti-Tamper / 蜜罐策略设计（只写设计，Phase 2+ 实现）

> ⬜ 以下为设计意图，不是实现指令。禁止提前实现。

### 5.1 异常降级策略

触发条件 → 降级行为：

| 触发 | 降级 |
|------|------|
| packageName ≠ 期望值 | SAFE_MODE，所有功能静默失效 |
| config_version 校验失败 | SAFE_MODE |
| customer_seed / package_hash 不一致 | 授权状态 = AUTH_TAMPERED，密友功能不可用 |
| 蜜罐字段被修改（破解者改错字段） | 状态机行为异常（抖动、开关失效、badge 异常）|
| device_hash 异常（多设备复制 license）| AUTH_TAMPERED |

**禁止**：降级行为不得写成"攻击逻辑"或主动破坏微信数据，只能是"功能静默失效 + 状态异常"。

### 5.2 蜜罐字段设计意图

```
libguardcore.so 内可预留若干"陷阱字段"：
- 破解者如果修改了这些字段，表现为：
  * nativeIsHidden() 返回值不稳定
  * badge 拦截概率性失效
  * 授权状态随机切换
- 目的：让破解版用户自行发现问题，引导至官方渠道
```

### 5.3 分层授权设计意图

```
Layer 1 基础功能：只需 nativeIsAuthorized() == true
  → 防撤回 / 虚拟定位 / 语音转发 / 改余额显示

Layer 2 密友隐私功能：需 isAuthorized() + isPrivacyEnabled() + 名单非空
  → 密友/密群过滤 / 通知伪装 / badge 拦截

Layer 3 高级功能（v3+）：需独立 feature license
  → 步数装b
```

### 5.4 服务端弹窗设计意图

```
盗版/异常用户 → miyou-server 可下发弹窗策略：
  - 弹窗类型：引导授权 / 跳商城 / 跳客服
  - 触发条件：AUTH_TAMPERED / 破解版包名 / 异常 device_hash
  - 弹窗不得影响微信正常功能，只在模块 UI 内显示
```

---

## 六、与 HOOKMAP.md 冲突处理

发现 native_core 文档与 `HOOKMAP.md` / `CLAUDE.md` 任何字段名、类名、状态描述**不一致**时：

```
❌ 禁止自行仲裁
❌ 禁止"按推断"选一边
✅ 停下来，明确告知用户哪两处冲突，等用户仲裁后再推进
```
