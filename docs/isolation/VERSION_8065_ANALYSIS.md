# 8.0.65 官方版 — 静态分析

> 来源: `C:\Users\Me\Downloads\125_2e2a7afbc3f61ff149bba763ac2989f2.apk`
> 归档: `I:\apk2_build\official\8.0.65官方.apk`
> jadx: `I:\apk2_build\official\8.0.65_jadx\`
> 日期: 2026-05-18

## 基本信息

| 项 | 值 |
|----|-----|
| 包名 | com.tencent.mm |
| versionCode | 2960 |
| versionName | 8.0.65 |
| targetSdkVersion | 34 |
| compileSdkVersion | 36 |
| DEX 数量 | 15 (classes.dex ~ classes15.dex) |
| SO 数量 | 182 (arm64) |
| SO 构建日期 | 2025-11-07 |
| 文件大小 | 255.2 MB (255,216,422 bytes) |
| MD5 | ffdb636ccd44fcbf55818ebe4a59a222 |
| SHA256 | 8f74e6f5537573ee7beabee6041c6871... |
| jadx 类数 | 145,575 (130 errors) |

## 版本链位置

```
8.0.51 → 8.0.56 → 8.0.63(设备) → 8.0.65(NEW) → 8.0.66(目标) → 8.0.70
                                      ↑ 微密友市场版基座
```

## 与微密友市场版(8061)的关系

已有分析 (`OEM_MARKET_ANALYSIS.md`) 确认:
- 微密友市场版基于 8.0.65 官方
- 16 个微信标准 SO 完全相同 CRC（直接复制）
- Catfish/Matrix SO 全部重新编译（不同 CRC）
- 市场版有 20 DEX（Catfish 注入 5 个新 DEX）
- 市场版用了 Epic 全家桶（4 Hook 引擎），真作者 8070 只用 Pine+ShadowHook

**此官方 8.0.65 的价值：作为对照基线，验证市场版哪些是微信原生、哪些是 Catfish 注入。**

## 关键类对比

### MvvmList

| 属性 | 8.0.65 | 8.0.66 |
|------|--------|--------|
| 所在 DEX | classes4.dex | classes4.dex (推测) |
| 主 ArrayList (数据) | `o` | `o` |
| 快照 ArrayList | `p` | `p` |
| MutableStateFlow 字段名 | **`s`** | **`h2`** |
| StateFlow 字段名 | **`v`** | **`m2`** |
| StateFlow 类型 | `kotlinx.coroutines.flow.h2` | `kotlinx.coroutines.flow.h2` |
| 构造: s = q2.b(...) | ✓ | ✓ |
| 构造: v = s | ✓ | ✓ |

**结论: 字段名变了(`s`→`h2`, `v`→`m2`)，但类型名(`h2`/`m2`)是 kotlinx 库类不变。必须按类型找字段，不能按名称。**

### SnsObject

| 属性 | 8.0.65 | 8.0.66/8.0.70 |
|------|--------|---------------|
| 所在 DEX | classes4.dex | classes4.dex |
| 字段结构 | 完全一致 | 完全一致 |
| CommentUserList | ✓ (field 12) | ✓ |
| LikeUserList | ✓ (field 9) | ✓ |
| protobuf 序列化代码 | 一致 | 一致 |

**SnsObject 是 protobuf 生成代码，跨版本完全稳定。Proto 层 hook 可以硬编码字段 ID。**

### k24 / i24

| 版本 | k24 | i24 |
|------|-----|-----|
| **8.0.65** | `fs4.k24` = protobuf 类 (templateTopicId/templateType) | `fs4.i24` = protobuf 类 (contacts) |
| **8.0.66** | `k24.b` = UI 数据模型 (d = i24.p, field_userName = wxid) | `i24.p` = UI 数据项 |

**类名完全不同！8.0.66 的 k24(UI模型) 在 8.0.65 中对应另一个混淆名。不能跨版本依赖类名。**

## 核心发现：混淆变化规律

| 层 | 跨版本稳定 | 跨版本不稳定 |
|----|:--:|:--:|
| Protobuf 类 (SnsObject, 字段ID) | ✅ 稳定 | 包路径偶尔变 |
| kotlinx 库类 (h2/m2/q2) | ✅ 类型名稳定 | SDK 版本可能变 |
| MvvmList 架构 (o/p ArrayList) | ✅ 字段位置稳定 | ❌ 字段名变化 |
| UI 数据模型 (k24/i24) | ❌ 全变 | 类名/字段名/包名全变 |
| Adapter 类 (y1等) | ❌ 全变 | 每个版本不同 |

## 对 Pine Hook 的启示

1. **Proto 层**: SnsObject 字段 ID 稳定 → 可直接用 Pine hook `SnsObject.op(int, Object...)`
2. **Adapter 层**: 类名全变 → 需要通过 MvvmList 的 ArrayList 实例反向定位
3. **MvvmList**: 按类型找 ArrayList 字段 (java.util.ArrayList)，按类型找 StateFlow 字段 (kotlinx.coroutines.flow.h2)
4. **wxid 提取**: 不能依赖 k24.b.d 链 → 需要通过 field_userName 反射搜索或直接从 protobuf Nickname/Username 取
