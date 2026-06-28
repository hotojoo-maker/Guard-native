# 产出 5：PROTECTION_MAP §10.7 P0-1 剩余 5 处内联硬编码

## 5.0 背景

`PROTECTION_MAP.md` §10.7（DeepSeek 压测复盘）P0-1「Release/PROD 删明文 fallback」当前状态 🟡 **C5a 部分落地（2026-06-25 L1）**：

- ✅ 22 个 `RegistryFallback` 常量 release 全 `""`（`gen_registry_fallback.py` 生成，装机 `fallbackSelfTest=ok`×4 验证）。
- ⬜ **仍剩 5 处内联**未迁入加密 registry（PROTECTION_MAP.md:330）：
  > `ContactDiscoveryHook MvvmList`、`ContactLabelHideGuard fc5.g/z3`、`MomentsFilter Like/Comment`

威胁定级：这些是 **L1/L2 级**（能反编译、能 jadx 还原是既定威胁）。内联明文类名/字段名 = 攻击者静态就能看出 hook 意图、且可抄成本地 recipe map 绕过 SO 解密链（§10.7 压测结论「Java fallback 可被抄成本地 recipe map」= 当前 P0 薄弱点）。

> 与 §10.5（:251）那批「registry 无对应项、删不掉」的 `final` 明文类名（如 `MvvmConvList`/`ConversationListView`/`MainUI`）**不同**：那批是已接受的残留债；本文 5 处是 P0 明确要迁的。

## 5.1 逐处定位

### ① ContactDiscoveryHook — `MvvmList` 基类名内联

| 项 | 内容 |
|---|---|
| 文件 | `moduleD/ContactDiscoveryHook.java` |
| 位置 | `:459` `if (n.equals("com.tencent.mm.plugin.mvvmlist.MvvmList")) return true;`（`isMvvmListLike()`）；另 `:268` `"com.tencent.mm.ui.contact.s0"` |
| 内联字面量 | `com.tencent.mm.plugin.mvvmlist.MvvmList`、`com.tencent.mm.ui.contact.s0` |
| 暴露内容 | MvvmList 基类识别逻辑 + 通讯录发现路径 anchor |
| 影响面 | 通讯录 contact discovery 字段图遍历的根识别；明文暴露「我们靠 MvvmList 子类找 backing list」的意图 |
| registry 现状 | 无对应 gateway 项（该类未走 `recipe()`） |
| 难度评级 | **中**。这是基类全限定名（非混淆短名），相对稳定；迁 registry 需新增 gateway key + RegistryFallback 常量 + `resolveRecipes()`，但要确认遍历逻辑在 fail-closed（空值）时安全跳过而非误判 |

### ② ContactLabelHideGuard — `fc5.g` / `z3` / `d4` 内联

| 项 | 内容 |
|---|---|
| 文件 | `moduleD/ContactLabelHideGuard.java` |
| 位置 | `:31` `ADDR_ITEM_CLS = "fc5.g"`；`:35` `Z3_CLS = "com.tencent.mm.storage.z3"`；`:32` `LABEL_STORE_CLS = "com.tencent.mm.storage.d4"` |
| 内联字面量 | `fc5.g`（混淆短名）、`com.tencent.mm.storage.z3`、`com.tencent.mm.storage.d4` |
| 暴露内容 | 标签入口行 item 类 + 用户名存储类 anchor |
| 影响面 | 「隐藏整个标签功能入口」逻辑；`fc5.g` 是混淆短名，**随官方版本易漂移**（脆 anchor），且与 ContactFilter 的 `fc5.g` 重复硬编码（两处各写一份，改版要改两处） |
| registry 现状 | 无对应项；`ContactLabelMemberFilter`（同族）也不走 registry（见文档 2 §2.2） |
| 难度评级 | **低-中**。短名混淆字面量正是 registry 最该收的（漂移风险高）；迁移机械，但要连带统一 ContactFilter / ContactLabelHideGuard / ContactLabelMemberFilter 三处 `fc5.g`，避免再分散 |

### ③ MomentsFilter — Like / Comment 字段名与方法名内联

| 项 | 内容 |
|---|---|
| 文件 | `moduleD/MomentsFilter.java` |
| 位置 | 字段名 `:65-68`：`LikeCount` / `LikeUserListCount` / `CommentCount` / `CommentUserListCount`；方法匹配 `:263-280`（`mn.contains("Comment")` / `mn.contains("Like")`）、探针 `:548` `getCommentList` |
| 内联字面量 | `LikeUserListCount`、`CommentUserListCount`、`getLikeUserList`/`getCommentList`（方法名子串匹配） |
| 暴露内容 | D2/D3 点赞/评论过滤的字段与方法 anchor |
| 影响面 | 朋友圈密友点赞/评论隐藏；这些是英文语义名（非混淆），比短名稳定，但同样把「过滤点赞评论」意图明文摆出 |
| registry 现状 | 无对应项。**注意**：§10.7 P0-1 另起的 `registry-unify-v1`（分支 `devin/registry-unify-v1`，已存在于 GitHub）正是要把 MomentsFilter 剩余混淆字面量（`jw1.d`/`wq.c1`/`wq.y0`/`ii5.b`）迁入 registry——本条 Like/Comment 应并入该任务一起收 |
| 难度评级 | **低**。英文名稳定、迁移直接；但「方法名 `contains` 子串匹配」这种模糊匹配迁 registry 时要决定是存精确名还是存匹配模式，需小设计 |

## 5.2 汇总表

| # | 类 | 内联字面量 | 性质 | 影响面 | 难度 |
|--:|---|---|---|---|:--:|
| ① | ContactDiscoveryHook | `MvvmList`(全名) / `s0` | 基类/路径 anchor | 通讯录发现遍历根识别 | 中 |
| ② | ContactLabelHideGuard | `fc5.g` / `z3` / `d4` | 混淆短名（脆） | 标签入口隐藏；与 ContactFilter 重复 | 低-中 |
| ③ | MomentsFilter | `LikeUserListCount`/`CommentUserListCount` 等 | 英文语义名 | 朋友圈赞/评过滤 | 低 |

> §10.7 把它们计作「5 处」是按字面量簇拆分（①2 个 + ②2~3 个 + ③一簇 ≈ 5）。

## 5.3 收口建议（呼应 §10.7，未落地）

1. **②③优先**：`fc5.g/z3/d4`（脆短名）和 Like/Comment 直接并入 `registry-unify-v1` 一起迁，机械、风险低、收益最直接。
2. **①需小心**：ContactDiscoveryHook 的遍历根识别迁 registry 后，务必保证 registry 空（release fail-closed）时是「跳过该 hook」而非「误判某对象为 MvvmList」——遵守现有铁律「`getRecipe()` 返回空 ⇒ 不安装该敏感 hook」。
3. **统一重复 anchor**：`fc5.g` 现在散在 ≥3 个类，迁移时收成单一 registry 项，避免「改一版微信要改三处」。
4. **删净 D8 + 负向验收**：§10.7 P0-5 要求「人为写 tk/bl/le → isAuthorizedNow=false」「jadx 搜核心 recipe 拿不到可用 fallback map」——这 5 处迁完后需配套补负向用例，否则 P0-1 仍不能算闭。

> 本任务仅盘点定位，未修改任何代码、未迁移任何字面量。
