# 任务：工作项 A — registry 单一真源归一（归一 C5a 收口）

> 创建：2026-06-29  
> 难度：⭐⭐（纯 Java 重构，无 native / 无设备）  
> 预计：1-2 小时  
> 负责人：Devin

---

## 0. 背景（一句话）

混淆类名目前有两份：① `native_core/registry_8071.json`（加密真源）和 ② Java Filter 文件里的硬编码字面量。  
**目标**：让 ② 归零，只靠 `RegistryFallback.*`（由 ① 自动生成）。

---

## 1. 允许读取的文件（白名单，不在列表里的禁止读取）

```
native_core/registry_8071.json               ← 所有混淆名的单一真源
src/debug/java/com/ghost/assist/core/RegistryFallback.java   ← 当前已生成的 debug 常量
src/release/java/com/ghost/assist/core/RegistryFallback.java ← release 版（全为空串）
src/main/java/com/ghost/assist/moduleD/MomentsFilter.java    ← 主要工作目标
src/main/java/com/ghost/assist/moduleD/ConvFilter.java       ← 对照参考（已归一）
src/main/java/com/ghost/assist/moduleD/ContactFilter.java    ← 对照参考（已归一）
tools/gen_registry_fallback.py               ← 生成 RegistryFallback 的脚本
build.gradle                                 ← 编译配置（只读，不改）
```

**绝对禁止读取**（即使 glob 命中也跳过）：
```
signing/**                    # 私钥
native_core/registry_cipher*.inc  # 加密 registry，需要 server seed 才能重生成
src/main/java/com/ghost/assist/net/**   # 服务器授权链，本次不动
src/main/java/com/ghost/assist/core/A2SignatureSpoof.java  # anti-ban 线，本次不动
```

---

## 2. 具体任务（按顺序执行）

### Step 1：审计 MomentsFilter.java 的剩余硬编码

扫 MomentsFilter.java，找出所有 **不是** 来自 `RegistryFallback.*` 的混淆字面量（如 `"jw1.d"` / `"wq.c1"` 等）。

记录格式：

| 变量名 | 当前值 | 是否已在 registry_8071.json | 是否混淆（非全包名） |
|---|---|---|---|
| ITEM_NOTIFY | "jw1.d" | ❌ | ✅ |
| ITEM_BUBBLE | "com.tencent.mm.plugin.sns.ui.SnsMsgUIWithRelevance" | ❌ | ❌（包名稳定） |
| ... | ... | ... | ... |

**判定规则**：
- 全包名（`com.tencent.mm.*`）= **稳定名**，不进 registry，保持 `private static final String`。  
- 短混淆名（2-5 字符 + 点 + 1-3 字符，如 `jw1.d`）= **不稳定**，必须进 registry。

---

### Step 2：把缺失的混淆名加进 registry_8071.json

在 `moments.feed` 对象下追加缺失字段，例如：

```json
"moments.feed": {
  "item_friend": "na4.b",
  "item_promo":  "la4.p",
  ...现有字段保留...
  "item_notify": "jw1.d",
  "item_wq_c1":  "wq.c1",
  "item_wq_y0":  "wq.y0",
  "item_ii5_b":  "ii5.b"
}
```

⚠️ 只追加 `moments.feed` 下的缺失字段。**不改 conv.list / contact.address / search.gateway / a2.sig**。

---

### Step 3：重新生成 RegistryFallback.java

```powershell
cd C:\Users\Me\Desktop\guard_native
python tools/gen_registry_fallback.py
```

确认 debug/RegistryFallback.java 里出现了新增的常量（`MOMENTS_FEED__ITEM_NOTIFY` 等），release 变体对应常量为空串 `""`。

---

### Step 4：迁移 MomentsFilter.java

把混淆字面量替换成对应 `RegistryFallback.*` 常量：

```java
// 改前
private static final String ITEM_NOTIFY = "jw1.d";

// 改后（NON-FINAL，resolveRecipes() 运行时可覆盖）
private static String ITEM_NOTIFY = RegistryFallback.MOMENTS_FEED__ITEM_NOTIFY;
```

**一并把 `private static final String` 改为 `private static String`**（方便 resolveRecipes 覆盖）。  
包名稳定类（`com.tencent.mm.*`）保持 `final`，不迁移。

在 `resolveRecipes()` 方法里（仿照 ConvFilter 的写法）补上对新增常量的赋值：

```java
String v = rt.getRecipe("moments.feed", "item_notify");
if (!v.isEmpty()) ITEM_NOTIFY = v;
```

---

### Step 5：完成度核查

运行（不需要连设备）：

```powershell
.\gradlew assembleOfficialDebug 2>&1 | Select-String "error:|warning:"
```

确认编译 0 error。

然后产出完成度报告：

```
## 完成度报告

### MomentsFilter 归一
| 变量 | 状态 |
|---|---|
| ITEM_PROMO | ✅ 已用 RegistryFallback |
| ITEM_FRIEND | ✅ 已用 RegistryFallback |
| ITEM_NOTIFY | ✅/❌ ... |
| ...          | ... |

### registry_8071.json 新增字段
- moments.feed.item_notify = "jw1.d"  ✅
- ...

### 编译结果
assembleOfficialDebug: ✅/❌ ...
```

---

## 3. 不做的事（本次范围外）

| 项目 | 原因 |
|---|---|
| 重新生成 `registry_cipher*.inc` | 需要 server seed，不在 Devin 权限内 |
| SearchFilter 内联字面量 | 已拍板 2026-06-21 保留（z15.ef6 / q2.j / fz2.e 是 8.0.71 死锚） |
| PushFilter 迁移 | 范围较大，本次只做 MomentsFilter |
| A2/anti-ban 任何代码 | 不在本次任务范围 |
| 装机验证 | 需要连接设备，由用户做 |

---

## 4. 完成标准

- [ ] `registry_8071.json` `moments.feed` 下无缺漏混淆名
- [ ] `RegistryFallback` debug 变体包含所有新常量
- [ ] `MomentsFilter.java` 无手写混淆短名字面量（包名稳定类除外）
- [ ] `resolveRecipes()` 覆盖新常量
- [ ] `assembleOfficialDebug` 0 error
- [ ] 提交 commit 并推送到 `devin/registry-unify-v1` 分支
