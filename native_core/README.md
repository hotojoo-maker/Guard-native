# native_core — libguardcore.so 模块自有工具库

> **定位**：Guard Native 模块的 C++ 工具库，**不是 native hook 引擎**。  
> **禁止**：dlopen 微信 SO / native hook / 接入微信 JNI_OnLoad / Pine / bypassmm / shadowhook  
> **铁律来源**：`CLAUDE.md` 铁律 2 / 23 / 27

---

## 一句话定位

```
libguardcore.so = 模块自有的"状态机 + 加密 + 进程桥"工具库
                  Java hook 层只问它：nativeIsHidden() / nativeIsHiddenWxid()
                  它不碰微信任何 native 代码
```

---

## 目录结构

```
native_core/
├── README.md           本文件
├── ARCHITECTURE.md     模块结构 + 进程角色 + 状态机设计
├── API.md              所有 nativeXxx() 接口签名 + 规约
├── RULES.md            禁止事项 + push 进程规则 + 蜜罐策略设计
└── ROADMAP.md          Phase 0–6 开发计划
```

代码路径（待 Phase 1 建立）：

```
native_core/
├── CMakeLists.txt
├── include/
│   └── guard_core.h
└── src/
    ├── guard_core.cpp      JNI 入口 + 模块总协调
    ├── state_machine.cpp   状态机 + 持久化
    ├── wxid_matcher.cpp    wxid / groupId 快速查
    ├── license_box.cpp     授权校验 + HMAC + 时间加盐
    ├── process_router.cpp  进程角色判断
    ├── state_bridge.cpp    主进程 ↔ :push 共享状态
    ├── notify_policy.cpp   通知策略判断
    └── log_limiter.cpp     限流日志
```

Java 侧桥接（模块内，待 Phase 3 实现）：

```
src/main/java/com/ghost/assist/native/NativeBridge.java
```

---

## 快速判断：该用 Java 还是 C++？

| 场景 | 用哪边 |
|------|--------|
| hook 微信类方法 / 拦截列表 | **Java（XposedHelpers）** |
| 读 hidden 状态 / 判断 wxid | **C++（nativeIsHiddenWxid）** |
| 授权 HMAC 校验 / AES-GCM 解密 | **C++（LicenseBox）** |
| 跨进程状态桥接 | **C++（StateBridge）** |
| UI / 通知栏 / Overlay | **Java（永远不走 C++）** |
| 微信 native 方法 hook | **❌ 永远禁止** |

---

## 当前状态

⬜ Phase 0 文档初始化（本文件所在阶段）

下一步 → `ROADMAP.md` Phase 1：C++ 最小骨架
