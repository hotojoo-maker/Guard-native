# P16 T05 调研 — SnsObject.parseFrom 8.0.66 等价类

> **归档**：本文件为 8.0.66 调研记录。当前底盘 **8.0.71**，实现以 `MomentsFilter.java` + `brief.md` / `result.md` 为准。

日期：2026-05-19
设备：小米9 / Android 11 / 微信 8.0.66 (Tinker patched)
证据级别：L2（Frida 动态 dump 字段）

---

## 一、核心结论

| 项目 | 8.0.70 (Catfish ref) | 8.0.66 (本次调研) |
|------|---------------------|-------------------|
| **SnsObject 类** | `com.tencent.mm.protocal.protobuf.SnsObject` | **同** ✅ |
| **wxid 字段** | `field_userName` | **`Username`** ⚠️ |
| **LikeUserList** | `LikeUserList` (LinkedList) | **同** ✅ |
| **CommentUserList** | `CommentUserList` (LinkedList) | **同** ✅ |
| **Like/Comm item 类** | `m05.g46` | **`gu4.ux5`** ⚠️ |
| **item wxid 字段** | `d` (String) | **同** ✅ |
| **LikeCount** | `LikeCount` (int) | **同** ✅ |
| **CommentCount** | `CommentCount` (int) | **同** ✅ |
| **LikeUserListCount** | `LikeUserListCount` (int) | **同** ✅ |
| **CommentUserListCount** | `CommentUserListCount` (int) | **同** ✅ |

## 二、证据

### E1: SnsObject 53 个字段 dump
```
证据：frida -U -p <PID> -l t05_find_sns_object.js
结果：com.tencent.mm.protocal.protobuf.SnsObject 存在，53 declared fields
关键字段：
  [5] CommentUserList : java.util.LinkedList
  [25] LikeCount : int
  [27] LikeUserList : java.util.LinkedList
  [40] Username : java.lang.String   ← 不是 field_userName！
```

### E2: LikeUserList generic type
```
证据：t05_like_item_class.js
结果：LikeUserList → java.util.LinkedList<gu4.ux5>
     CommentUserList → java.util.LinkedList<gu4.ux5>
```

### E3: gu4.ux5 字段 dump
```
证据：t05_inspect_ux5.js
21 个字段，关键：
  [0] A : java.util.LinkedList
  [1] d : java.lang.String  ← wxid
  [3] f : int
  [5] h : java.lang.String
  ...
```

### E4: parseFrom 实时触发
```
证据：t05_final_check.js（40+ hits）
每个 SnsObject 解析触发一次 parseFrom
CommentUserList itemClass=gu4.ux5 已确认
wxid=wxid_11msaqkk21lg22 等真实数据
```

## 三、parseFrom 过滤方法

parseFrom 被大量非 SnsObject 类型调用（protobuf-lite 继承链）。
过滤方法：检查 `Username` 字段是否非空。

```java
String userName = getFieldString(result, "Username");
if (userName == null || userName.isEmpty()) return; // skip non-SnsObject
```

## 四、版本差异要点

- 8.0.70 用 `field_userName`，8.0.66 用 `Username`（不带 field_ 前缀）
- 8.0.70 SNSDATA_CLASS = `m05.g46`，8.0.66 等价类 = `gu4.ux5`
- protobuf 字段 ID 稳定，字段名可能因混淆版变化
- LinkedList（非 ArrayList）用于列表字段
