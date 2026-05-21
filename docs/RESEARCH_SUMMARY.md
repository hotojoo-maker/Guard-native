# 研究成果汇总 — 可直接用于 guard_native 开发

> 来源：`I:\tianmiyou_re\` + `C:\Users\Me\Desktop\apk2\` 两轮分析
> 整理日期：2026-05-18

---

## 1. 微信 8.0.66 数据架构（已动态验证）

### MvvmList（会话 + 朋友圈共用架构）

```
MvvmList
  ├── o   : ArrayList<?>   ← 主数据列表（remove 这里）
  ├── p   : ArrayList<?>   ← 快照列表（同步 remove）
  ├── h2  : MutableStateFlow  ← Kotlin Flow 数据源（8.0.66 字段名）
  └── m2  : StateFlow        ← 对外只读 Flow（8.0.66 字段名）
```

> ⚠️ 字段名 `h2`/`m2` 是 8.0.66 特有，按类型找字段不要按名找：
> - MutableStateFlow: `kotlinx.coroutines.flow.h2` 类型
> - ArrayList: `java.util.ArrayList` 类型

### 已验证唯一可行过滤方案（F04/F05 共用）

```
clean-before + notifyDataSetChanged：
1. 从 MvvmList.o 和 MvvmList.p 中 remove 目标 wxid 的 item
2. 调用 adapter.notifyDataSetChanged()
```

> 禁止 notifyItemRange* 系列（15 种失败方案已存档 → FAILURE_LOG.md）

### wxid 提取路径

- 会话：`ConversationItem.contact.username` 字段
- 朋友圈：`SnsObject` protobuf 字段（field_id 稳定跨版本）

---

## 2. MMKV Key 常量（libwechatsd.so .debug_str 提取）

| 常量名 | 含义 |
|--------|------|
| `ACTIVE_CODE` | 激活码 |
| `EXPIRED_TIME` | 过期时间戳 |
| `FAKE_LOCATION` | 虚拟定位开关 |
| `VIP_ENABLE` | VIP 功能总开关 |
| `HIDE_CONTACT_LIST` | 通讯录隐藏名单 |
| `HIDE_CONV_LIST` | 会话隐藏名单 |
| `HIDE_MOMENTS_LIST` | 朋友圈隐藏名单 |

guard_native 独立 MMKV 命名空间，不与上面冲突：
```
mmkv_id = "guard_" + seed[:4]
key     = "hide_list"   ← 逗号分隔 wxid
```

---

## 3. 关键 native 函数（libwechatsd.so 8061 版本）

| 函数 | RVA (8061) | 说明 |
|------|-----------|------|
| `native_start` | `0x1b8378` | JVM 初始化入口（OLLVM CFF 混淆） |
| `native_verifyUser` | `0x1ca220` | 授权验证（栈 2032B，getPackSign+RSA） |
| `native_activeCard` | `0x1cd298` | 卡密激活（BIO_free_all 确认 RSA） |
| `encryptNative` | `0x1cf25c` | byte[]→HEX 工具函数 |
| `publicKeyStringDecrypt` | `0x2552a4` | RSA 解密（DNS 信道取公钥） |
| `publicKeyStringVerifyRsaSign` | `0x4efae4` | RSA-MD5 签名验证 |
| `RegisterNatives` 调用点 | `0x339ff0` | JNI_OnLoad 内 |

> **对 guard_native 的影响**：这些函数均不需要 hook 或复用。
> 只作为理解原系统架构的背景知识。

---

## 4. 授权协议（已逆向，对重写无直接用途）

```
ver1 响应:
  key      → base64(PEM RSA 公钥)
  svr_key  → "ddwmy"（服务器签名盐）
  buySwitch, s_ver 等配置字段

verifyUser 请求（客户端→服务器，RSA 加密）:
  {"csign":"...","cver":"8.0.70.2","username":"xxx","ut":N}

verifyUser 响应校验:
  RSA_verify(NID_md5=4, MD5(data+"ddwmy"), 16, sig_bytes, siglen, pubkey)
  → 服务器用私钥签名，客户端用公钥验签
```

> ⚠️ 以上是甜密友现有服务器协议，guard_native **不联网、不实现这套**。

---

## 5. 混淆技术清单（8070/8061 libwechatsd.so）

| 技术 | 应用位置 |
|------|---------|
| OLLVM 控制流平坦化 (CFF) | native_start, native_verifyUser, native_activeCard |
| Hikari FunctionWrapper | encrypt.cpp 全部函数 |
| 动态域名组装 | domainArr + domainIdx（静态扫描看不到 URL）|
| 字符串运行时解密 | JSON key 常量（BSS .debug_str 有原名）|
| DNS 信道隐写 | publicKeyStringDecrypt 用 Curl_resolver |

---

## 6. 跨版本稳定性（Pine hook 选点原则）

| 层 | 跨版本稳定 | 选点建议 |
|----|:----:|------|
| SnsObject protobuf 字段 ID | ✅ 稳定 | 直接用字段 ID hook |
| MvvmList 架构（o/p/h2/m2）| ✅ 结构稳定 | 按类型反射找字段 |
| kotlinx StateFlow 类名 | ✅ 稳定 | `kotlinx.coroutines.flow.h2` |
| UI 数据模型（k24/i24 等）| ❌ 每版本变 | 不依赖混淆类名 |
| Adapter 类名 | ❌ 每版本变 | 通过 MvvmList 反向定位 |

---

## 7. 设备环境

```
设备：小米9 (MI 9)
系统：Android 11
Root：Magisk
微信：8.0.66 官方版 com.tencent.mm
Frida：17.9.3
LSPosed：已安装
```

---

## 8. iOS ↔ Android 数据流对照（参考）

> 来源：iOS 独立研究，2026-05-18

### iOS 已证实的 hook 点

| 层 | iOS Hook 点 | 时机 | Android 对应 |
|----|------------|------|-------------|
| L1 | `WCTimelineDataProvider.-converListToList:` | 网络数据到达，SnsObject 数组 | `MvvmList.m(List, boolean)` ✅ |
| L1 | `WCDataItem.+fromServerObject:` | SnsObject → WCDataItem 转换 | 工厂方法（**未找到**） ❓ |
| L2 | `WCDataItem.+fromServerObject:adDynamicXml:` | 与 L1 成对调用，附加广告数据 | 同名混淆方法 |
| L3 | `tableView:cellForRowAtIndexPath:` | 每次 cell 渲染 | RecyclerView adapter bind |

### iOS 优势（证实）
- `SnsObject` 有 `username`、`nickname` 属性，KVC 直读
- 所有类名/方法名零混淆
- `fromServerObject:` 返回 nil → `WCDataItem` 不创建（比 Android 原地 remove 更干净）

### 对 guard_native 的影响
- `fromServerObject:` 等价方法在 Android 上**尚未找到**，`probe_k24b_factory.js` 待跑
- 当前 Android 侧走 `MvvmList.m` 入参 remove，已验证可行，不阻塞开工
- iOS 研究确认了 `SnsObject.field_userName` 是跨平台稳定锚点

---

## 9. 下一步（guard_native Phase 1 → Phase 2）

### Phase 1：最小 SO 骨架
```
src/cpp/
├── CMakeLists.txt
├── jni_entry.cpp      ← JNI_OnLoad：Pine init + MMKV init
├── guard_mmkv.cpp     ← 读 hide_list
├── guard_filter.cpp   ← 过滤回调（空实现）
└── guard_config.h     ← build_id / seed / tag 差异化常量
```
验证：logcat 输出 `[guard] init ok, hide_list=N items`

### Phase 2：LSPosed 模块 F04（会话隐藏）
```
直接翻译 filter_conv.js：
1. 找 MvvmList 实例（通过 ConversationFragment）
2. 从 o/p 中 remove 目标 wxid
3. adapter.notifyDataSetChanged()
```
验证：8.0.66 上指定 wxid 会话不可见

---

---

> 详细 Hook 点参考 → `docs/HOOK_POINTS.md`
> 类名速查表 → `docs/CLASS_MAP_8066.md`
> 失败方案档案 → `refs/FAILURE_LOG.md`

*本文件整合自 tianmiyou_re 三步分析（SO_A/B/C）+ apk2 动态验证结果 + iOS 研究对照。*
