# M6a — 朋友圈"仅可见分组"图标隐藏

> 编号：M6a（对齐权威 §6a；M=Moments）
> 阶段归属：v1 收尾（应用户要求 2026-06-08 拉入 v1）
> 底座：微信 8.0.71，Android 11，LSPosed
> 当前状态：✅ 交付（2026-06-10）——时间线 + 详情页 L1 装机；个人相册页 = Flutter，v1 不做（决策 A）

---

## 一、任务目标

**自己手机看自己发的朋友圈时**，发过"仅 XX 分组可见 / 仅自己可见 / 部分可见"的条目右下角那个图标 → 全部 GONE。

让自己的朋友圈时间线视觉清爽，不再被一堆"分组可见"提示图标干扰。

> ⚠️ **能力边界**：LSPosed 只跑在自己设备上 → 这是**自己端**视觉过滤，不影响别人看你朋友圈时的图标。如要"对他人完全隐藏分组限制"需 v2 服务器配合（暂不在范围）。

## 二、不做（范围外）

- 不改自己发布朋友圈时的分组可见**选择**逻辑（开关/分组功能照常用）
- 不藏别人发的朋友圈（无意义，别人发的本就没"仅你可见"标记）
- 不藏密友列表里"对密友可见/不可见"那个图标（属 A2 范围）

## 三、验收标准

| # | 验收点 | 复测 | 结果 |
|:-:|------|------|:--:|
| 1 | 开关关 → 图标正常显示 | 关开关 → 朋友圈滑到自己发的"仅 X 可见"条目 | ✅ 时间线/详情页 |
| 2 | 开关开 → 图标消失 | 开开关 → 同上 → 看右下角无图标 | ✅ 时间线/详情页（L1 `[MGI] pt GONE`） |
| 3 | 关闭 → 立即复原 | 再关开关 → 滑回去看图标回来 | ✅ 开关驱动 |
| 4 | 不影响别人头像/点赞/评论等其他元素 | 同条目其余 View 正常 | ✅ 只命中 id=pt，pi/其他不动 |
| 5 | 个人相册页 | 进自己相册 → 看受限帖图标 | ⛔ **v1 不做**：页面是 Flutter（见下决策 A） |

> **实装结论（2026-06-10）**：view 层方案 = `MomentsGroupIconFilter`（`ViewStub.inflate` + `Activity.onResume`/`OnGlobalLayout` 扫 `id=pt`→GONE，仅匹配 `plugin.sns` 页）。原 brief 的 `onBindViewHolder` 探针方案**已弃用**（F-32x：8.0.71 朋友圈 RV onBindViewHolder 0 命中）。

> **决策 A — 个人相册页不做（2026-06-10，L1 实证 `logs/gi_album.txt`）**：相册页置顶 Activity = `com.tencent.mm.plugin.flutter.ui.MMFlutterViewActivity`，整页只有一个 `FlutterView → FlutterTextureView`(1080×2296)、内部零原生子 View（无 `pt`/`pi`/WeImageView），图标由 Flutter 画在 texture 上 → 安卓 view hook 物理够不到。走数据层(B)未知雷区大、硬刚 Flutter(C) 触铁律 23 高暴露 → v1 收口阶段次要入口，**接受现状不做**。

## 四、调研路径（D-015 阶段 ①）

### 阶段 A — 静态 + 动态探针定位 hook 点（**当前**）
- 竞品 8.0.66 锚点（CATFISH_REVERSE.md §3）：
  - `UserControll.hookSnsGroup() → boolean` 是查询开关 getter
  - `NativeHelper.getHideGroup() → boolean` 存储
  - **真过滤在渲染层**——hook `SnsObject item View` 的图标可见性
- 8.0.71 未知：
  - 朋友圈 feed item 渲染类（候选：`com.tencent.mm.plugin.sns.ui.*` 下的 `SnsFeedItemView` / `BaseTimeLineUI` / item adapter）
  - 图标 View 的资源 ID / 父类
  - "仅可见"/"分组可见"等文案的 string resource ID
- 探针方案：`tools/probe_moments_groupicon_8071.js`（本任务新写）
  - hook `RecyclerView.Adapter.onBindViewHolder` 拿 itemView
  - 扫 itemView 子 View 树，找文字含"仅"/"分组"/"私密"/"部分"的 TextView 或 ImageView
  - 输出：View className / id / parent path

### 阶段 B — 实装
- 新建 `moduleD/MomentsGroupIconFilter.java`
  - 在阶段 A 定位的 onBindViewHolder afterHook 里，扫子 View 树 → 命中图标 → `setVisibility(GONE)`
- `Bridge.java` 加 `hg`（hide_group）boolean
- `moduleB/SettingsEntry.java` 加开关「隐藏朋友圈分组可见图标」
- `ModuleMain.java` 注册

### 阶段 C — 装机验证 + 收敛
- 跑验收 4 条
- HOOKMAP §6a ❌→✅
- 权威 §6a 整章更新
- 写 result.md / W 交接快照

## 五、依赖 / 阻塞

- 不依赖任何已验 hook（独立模块）
- 不动 StateMachine / Bridge 已有授权链路
- 不动 native_core
- 调研需要用户在终端跑 1 次 frida 探针（5-10 分钟）

## 六、风险

- 8.0.71 朋友圈渲染类未知 → 阶段 A 必须先跑探针
- 微信反 frida：朋友圈区**目前未实证有反 frida**（F-37 仅钱包域），可用 frida 探查
- 升 8.0.72 → item 渲染类混淆名变 → 需 classmap 重查（P24/P25 工具未上线前手动）

## 七、关联文档

- 权威 §6a：`docs/HOOK_MAP_8071_AUTHORITATIVE.md` §6a
- Catfish 锚点：`06_refs_参考资料/competitor_catfish/CATFISH_REVERSE.md` §295
- 既有探针参考：`02_tools_工具/dynamic_crawler_动态爬虫/moments_visibility_crawler.js`（探"选好友"界面的，本任务不用）
