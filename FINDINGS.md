# FINDINGS — 发现即落盘

[2026-05-19 14:30] P15 脚手架完成：13 源文件 + 2 文档落地 src/main/ | 产出: AndroidManifest, LSPosed 骨架, 3态状态机, 拦截计数器, 通知, 悬浮窗, HTTP 8080, HTML 调试页, ContactResolver, 三态配置 | 下一步: guard-review 自审 → 装机小米9 验证
[15:32] 发现：构建环境打通，guard-native-debug.apk 1.9MB 产出 | 证据：BUILD SUCCESSFUL, AGP 8.2.2 + Gradle 8.5 + JDK 21 | 下一步：adb install 到小米9测试
[2026-05-19 17:00] 发现：P16 MomentsFilter 设备测试失败 — MvvmList.m(List, boolean) hook 零命中 | 证据：scan#20 mvvm=0 tracked=0 found=0 | 下一步：jadx 排查
[2026-05-19 17:15] 根因确认：P16 策略错误。v12 filter_moments.js 的真实路径是 y1 adapter → 字段H → MvvmList实例 → 字段o/p → ArrayList实例 → hook addAll。P16 代码错误地 hook 了 MvvmList.m() 全局方法（零命中）+ isMomentsList() 用 k24.b 判 item 类型（会话类非朋友圈类）。需按 v12 策略重写 MomentsFilter.java。
[2026-05-19 18:18] 确认：v12 filter_moments.js Frida warm-attach 直接生效（INSTALL OK o=631884205 size=11），而 LSPosed MvvmList.m() hook 仍零命中。证实问题在策略不在环境。MvvmList 8.0.66 有 30 个 declared methods，m(List, boolean) 存在但朋友圈数据不走它。 | 下一步：LSPosed 版按 v12 策略重写 — hook y1 adapter 构造/初始化获取实例。
[2026-05-19 19:00] 新增样本结论：甜密友 8.0.63 与微密友 8.0.70 朋友圈过滤本质一致 — BaseAdapter.getItem(index) → field_userName → height=1 + GONE。差异仅安装方式（甜密友 smali 织入 vs 微密友 native/Pine hook）。两者依赖旧 ListView + BaseAdapter 架构，不适用于 8.0.66 RecyclerView + MvvmList + StateFlow。P16 不再参考 getView/height=1 作为实现路线，只作为产品行为对照。 | 证据：apk2\_4__samples\dynamic_fast\HOOK_IMPLEMENTATION_ANALYSIS.md（甜密友 hookSns）+ apk\smali_classes17_未加密备份\com\catfish\newvip\core\UserControll.smali（微密友 hookSns）
[2026-05-20 01:xx] iOS Moma逆向 P0-P2 完成 | 证据：WCDataItem ↔ Android SnsInfo 字段映射表已确认（username→field_userName, likeUsers→field_likeList, commentUsers→field_commentList），WCUserComment type字段区分赞/评论 | 下一步：D模块 P2 点赞评论屏蔽参考此映射
[2026-05-21 14:00] 屏蔽更新小红点 8.0.71 类名调研完成 | 证据：fl4.o + SettingsAboutMicroMsgUI + SettingsUI.P7() | 8.0.66 对应类 gd4.o（混淆名不同）| 下一步：B7 UpdateGuard.java 写 hook
[2026-05-21 14:05] 朋友圈小红点 8.0.71 类名调研完成 | 证据：FindMoreFriendsUI.M1()/l0() + SnsCommentStorage.E1() + FriendSnsPreference | 设备实测：7条旧路径全部零触发，8.0.71 红点走 ns.c 聚合桶 | 下一步：追 wxid 级漏斗
[2026-05-22 15:30] hookSnsMsgList Pine目标方法无法定位：libwechatsd.so 0字符串（stripped）；全smali扫描 `()Ljava/util/ArrayList;` 44候选零SNS引用；无原始8.0.70做diff。唯二路径：原始8.0.70 APK diff 或 Ghidra逆向 libwechatsd.so native_start() 0x2349b8 | 证据：strings=0, grep="()Ljava/util/ArrayList;"×40全扫描, SNS类型引用=0 | 下一步：不再回查此线，P21走自研三层

[2026-05-23 19:30] 调研启动：微信「消息免打扰」内部方法名 | 证据：HOOKMAP.md WeChatDND（规划）待 Frida trace 调用链 | P22_PushFilter/probe_dnd_toggle.js 已写，6 条探针线（l4 setter / ChattingUI / WCDB rconversation / MMKV / 延迟类枚举 / model.aj~bk）| 下一步：用户设备跑 probe → 手动切换免打扰 → 发回 [DND] 命中日志

[2026-05-22 15:00] 校准：私有化阶段补的是桌面角标/未读数字，不是朋友圈发现 tab 红点 | 证据：`apk2/_1__B_rewrite/05_docs/PROGRESS.md` Issue #23 (isVipMode语义修复) + #24 (h0.d角标注入) 均为桌面图标角标；Catfish hookSnsMsgList 是原版 Pine hook，非私有化新增；Guard P21 在 8.0.71 扫描 hookSnsMsgList 等价入口 = 0 hooks，不再回查，走自研 Layer0b+Layer2+v18 | 下一步：P21 继续按自研三层验证

[2026-05-21 16:00] 朋友圈小红点 wxid 漏斗 — 卡关总结 | 已证实：① ns.c 聚合桶归零=全屏蔽（Frida 直接反射 OK）；② FindMoreFriendsUI.L1() 入口（`com.tencent.mm.ui.FindMoreFriendsUI`），无参数，内部读 this 某 List → 算 y → 写 ns.c.g；③ bm.b.call() 是上游瓶颈但未 dump 到内部 List。已证伪：7 条旧路径全零触发；AbstractCursor.getString 零命中（微信 WCDB 自有 Cursor 不走 Android AbstractCursor）；逐层 Frida dump 字段太慢。卡点：L1() 内部的 List 字段名 + item 类名 + wxid 字段名三个值未拿到 | 建议：资料员 jadx 静态追溯 FindMoreFriendsUI.L1() → 字段赋值 → 上游数据源，比设备逐层 dump 快