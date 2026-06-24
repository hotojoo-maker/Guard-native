# R2b · C7 挂空对账（R1 ⇄ R2 逐字段并账）

> **角色**：Guard Native 只读侦察 AI（**只读、未改任何代码 / 未改别处文档正文**；本文件 = 唯一新建产物）
> **任务**：把 R2 数的 ~13 个挂空字段 与 R1 报告的 15 个 **逐个对**，出最终清单 `[字段 | 删/接/缓 | 理由 | 证据等级]`，**分歧明确标出**。
> **输入**：`recon/R1_C7挂空核账.md`（15 挂空，桶分 接4/受限3/删8）+ `recon/R2_内联明文核账.md`（§3 列 ~13 挂空）
> **日期**：2026-06-25 · **红线**：只读 · 不改代码 · 不改别处文档 · 结论标级（L1~L4）· G1 不猜

---

## 0. 一句话结论（L2）

- **并集 = 15 个挂空字段**（R1 的 15 是超集；**R2 的 13 = 15 − 2**）。
- **R2 漏计的 2 个** = `moments.feed/list_addall`、`contact.address/list_addall`——两者都是 **JDK 方法名**（`ArrayList/LinkedList.addAll`），R2 当「非混淆名」剔出视野，但作为 registry 字段它们**确属定义了没人读** → R1 计入正确。**采纳 R1，判删。**
- **分类分歧 3 处**（全部收敛）：`actor_class`、`actor_wxid_field`（R2 曾倾向「留」→ 收敛 **删**）、`actor_field_names`（R1 接·L4 / R2 留 → 收敛 **接·L2**）。
- **证据升级 1 处**：`actor_field_names` 消费点 R1 标 L4「未现场确认」；本对账**现场确认 L2**（`MomentsFilter:632 / 1232 / 1242` 在用）。
- **最终桶**：**删 8 · 接 6 · 缓 1**（与 R1「删 8 / 接 4 / 受限 3」总数一致，差异仅在把 `l1_methods`/`l2_method` 从 R1 的「受限」细化进「接」、`item_class` 独留「缓」——见 §3 分歧说明）。

---

## 1. 数字对账

| 来源 | 挂空计数 | 备注 |
|---|:--:|---|
| R1 | **15** | registry 37 = 真读 22 + 挂空 15（与安全官 skill「37/22/15」口径一致） |
| R2 §3 | **13** | 按「短混淆名锚点」视角，**剔除了 2 个 JDK 方法名 registry 键** |
| **并集（本对账）** | **15** | R2(13) ⊂ R1(15)，无 R2 独有项；缺口 = 2 × `list_addall` |

---

## 2. 最终清单 [字段 | 删/接/缓 | 理由 | 证据等级]（含 R1⇄R2 分歧列）

| # | 动脉 / 字段（值） | **裁决** | 理由 | 级 | R1⇄R2 |
|:--:|---|:--:|---|:--:|---|
| 1 | conv.list / **item_class** (kc5.y) | **缓** | 在用且在**已验证 hook 回调体内**（ConvFilter:352/408/573/1071·ConvHotReload:374/424），铁律29 禁动；install 期能否 resolve 归 C5 评估 | L2 | 一致（R1 受限·R2 A 类，同义） |
| 2 | conv.list / **l1_methods** (n,m) | **接** | 用在 **install 扫描循环**（非回调体，ConvFilter:384/388/486…），install 期 resolve 风险低；C5 快照+装机回归 | L2 | **细化**：R1 列「受限」，本对账判可直接接 |
| 3 | conv.list / **l2_method** (s) | **接** | 同 #2，install 扫描（ConvFilter:437/440/486…） | L2 | **细化**：R1「受限」→ 接 |
| 4 | conv.list / **l4_notify** (notifyDataSetChanged) | **删** | **JDK 方法名**，进 registry 无防护价值（非混淆名） | L2 | 一致 |
| 5 | conv.list / **contact_class** (…storage.l4) | **删** | 无 live 锚点，仅注释 ConvFilter:35；无代码按类名引用 | L2（删前 L4 复核反射全名加载） | 一致 |
| 6 | conv.list / **contact_fields** (d,e,f,a,b,c) | **接** | 在用内联 `CONTACT_FIELD_NAMES`(ConvFilter:72)，仿 `wxid_getters` 走 `recipeArr` 归一 | L2 | 一致 |
| 7 | moments.feed / **list_addall** (ArrayList/LinkedList.addAll) | **删** | **JDK 方法**（MomentsFilter:141-153 直接 `getMethod("addAll")`） | L2 | ★ **R2 漏计** → 采纳 R1 |
| 8 | moments.feed / **actor_class** (z15.e56) | **删** | 代码自证 dead（MomentsFilter:43-44「NOT migrated — dead constant」） | L2 | ⚠ **分歧**：R2 曾「留/标 dangling」→ 收敛 **删** |
| 9 | moments.feed / **actor_wxid_field** (f435583d) | **删** | registry 键与 `actor_field_names` 重复且无人读；冗余 | L2 | ⚠ **分歧**：R2 曾「留」(指代码常量)→ registry 键收敛 **删** |
| 10 | moments.feed / **actor_field_names** (d,f435583d,username,field_userName) | **接** | 内联数组(MomentsFilter:75)**确在消费**：`:632`(collectWxidsDeep)·`:1232`(isHiddenListEntry)·`:1242`(extractWxidFromEntry)；`recipeArr` 归一 | **L2** | ⚠ **分歧+升级**：R1 接·**L4**(消费点待确认) / R2 留 → 收敛 **接·L2**（消费点本对账已证） |
| 11 | contact.address / **contact_field** (d) | **接** | 在用内联 `ITEM_CONTACT_FIELD="d"`(ContactFilter:103→:416)，`recipe()` 归一 | L2 | 一致 |
| 12 | contact.address / **type_field** (e) | **接** | 在用内联 `ITEM_TYPE_FIELD="e"`(ContactFilter:104→:403)，`recipe()` 归一 | L2 | 一致 |
| 13 | contact.address / **list_addall** (ArrayList.addAll) | **删** | **JDK 方法**（ContactFilter:128/178/243） | L2 | ★ **R2 漏计** → 采纳 R1 |
| 14 | search.gateway / **policy** (hide_if_target_in_hidden_union) | **删** | 行为描述串，非代码锚点；无内联引用（DebugServer:307 读的是 HTTP body，与 registry 无关） | L2 | 一致 |
| 15 | search.gateway / **unlock_entry** (excluded_search_unlock_111111) | **删** | 标记/描述串，非代码锚点，无内联引用 | L2 | 一致 |

---

## 3. 分歧明细（逐条说清）

### 3.1 ★ R2 漏计 2 项（#7、#13 · `list_addall`）

- **现象**：R1 计 15、R2 计 13，缺口正是 `moments.feed/list_addall` 与 `contact.address/list_addall`。
- **根因**：R2 以「短混淆名锚点」为搜索轴，把 `ArrayList.addAll` 这类 **JDK 方法名**判为「非目标字面量」而未计入挂空；但站在 **registry 字段「定义了没人读」** 视角，它们是合格的挂空项。
- **裁决**：**采纳 R1，判删**（JDK 方法名进 registry 零防护价值）。R2 漏计属**视角差**，非事实冲突。

### 3.2 ⚠ 实质分歧 3 项（#8、#9、#10 · 全在 moments.feed）

| 字段 | R1 | R2 | 收敛裁决 | 收敛依据 |
|---|---|---|:--:|---|
| `actor_class` (z15.e56) | 删（dead） | 倾向「留/标 dangling」 | **删** | 代码注释自证 dead（:43-44），registry 键无消费 |
| `actor_wxid_field` (f435583d) | 删（与 actor_field_names 重复） | 「留」 | **删** | R2 的「留」指代码常量 `FIELD_E56_WXID`，**registry 键**层面确属重复+未读 → 删键 |
| `actor_field_names` | 接·**L4**（消费点待确认） | 「留」 | **接·L2** | 消费点已证：`MomentsFilter:632/1232/1242` 三处在用 |

> 说明：R2 对 moments e56 一族整体偏「留」，是因 R2 关注**代码侧 proto 字段常量**（`f435583d`/`ACTOR_FIELD_NAMES` 这些是真用的 proto 字段名，代码里不该删）；而 C7 对账的是 **registry 键**——键挂空该删/接，与代码侧常量是否保留是两回事。两者**不矛盾，是层级不同**：`actor_class`/`actor_wxid_field` 两个**键**删、`actor_field_names` 这个**键**接（因为它对应的内联数组真在用，值得归一）。

### 3.3 细化（非分歧）2 项（#2、#3 · `l1_methods`/`l2_method`）

- R1 把 `item_class`+`l1_methods`+`l2_method` 一起放「桶B 接上但受铁律29约束」。
- 本对账**细分**：只有 `item_class(kc5.y)` 真在**回调体内**（铁律29 死线）→ **缓**；`l1_methods`/`l2_method` 在 **install 扫描循环**（R1 自己也注明「非回调体、风险较低」）→ 判 **接**。
- 这是**口径细化**，与 R1 不冲突。

---

## 4. 最终桶（删 8 · 接 6 · 缓 1 = 15）

- **删（8）**：`l4_notify`、`contact_class`(l4)、`moments/list_addall`、`actor_class`、`actor_wxid_field`、`contact/list_addall`、`policy`、`unlock_entry`。
  - 三类：JDK/框架方法名（3）· dead/冗余键（3）· 纯描述串（2）。
- **接（6）**：`l1_methods`、`l2_method`、`contact_fields`、`actor_field_names`、`contact_field`、`type_field`。
  - 共性：值是真锚点/字段名 + 已有在用内联副本 → `recipe()/recipeArr()` 接上、删内联副本即归一。
- **缓（1）**：`item_class`(kc5.y)——铁律29 回调体，C5 评估 install 期取值前不动。

> 对账后 vs R1：总数桶不变（8 删 / 7 非删），仅把 R1「受限 3」拆为「接 2（l1/l2_methods）+ 缓 1（item_class）」、并把 `actor_field_names` 由 L4 升 L2。

---

## 5. 诚实边界 / 待确认

1. **#5 `contact_class`(l4)**：「无 live 引用」为本次 grep 结论（L2）；删前建议再 L4 复核有无**反射按全限定名加载**的隐蔽点。
2. **生成脚本侧未核**：本对账只覆盖 `*.java` 的 `getRecipe*/recipe()` 读取；`native_core/*.cpp`、`tools/gen_*_cipher.py` 是否按 key 遍历生成/校验**未核**——若是，则「删 8 键」需**同步改生成脚本**（归 C5/改前审）。
3. **裁决 ≠ 动手**：删/接/缓 全部会动 `registry_8071.json`（→ `gen_registry_cipher.py` → SO），属改前审范围；落地走任务卡 §5「改前双审 + git 快照 + 改后装机回归」。

## 6. 本次未做（守只读红线）

- 未改任何代码、未改 `registry_8071.json`、未改任务卡/R1/R2 等文档正文。
- 未执行 git 写动作；未对任何字段做实际删/接/缓。

# End（C7 挂空对账 · 只读；并集 15 = 删 8 / 接 6 / 缓 1；R2 漏计 2×list_addall、moments e56 族 3 处分歧已收敛、actor_field_names 升 L2）
