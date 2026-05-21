# Guard Native �?主入口（AI 接手必读�?
> 项目代号: **Guard Native (守护内核)**
> 目标: 微信 **8.0.66** 隐私模块 �?LSPosed 阶段 �?改包阶段 �?native 加固
> 日期: 2026-05-19 立项

## 接手三步铁律

1. 读完本文�?0 分钟�?2. �?[`HOOKMAP.md`](./HOOKMAP.md) 知道当前在哪个功能、哪个模块、什么状�?3. �?[`TASK_BOARD.md`](./TASK_BOARD.md) 领取你这个窗口的任务
4. 不读完不准动代码

---

## 一、当前层�?
```
文档�? ████████████ 100%   HOOK_POINTS / CLASS_MAP / FAILURE_LOG / 22 条铁�?代码�? ░░░░░░░░░░░░   0%   �?我们在这�? 4 窗口并行启动�?验证�? ████████████ 100%   Frida filter_conv.js v1 + filter_moments.js v21 已稳�?试错�? ████████████ 100%   22 条已验证失败方案归档 (FAILURE_LOG.md)
```

**下一�?*: 4 窗口并行 �?详见 [`TASK_BOARD.md`](./TASK_BOARD.md)

---

## 二�?7 条铁律（违反即停�?
完整清单 �?[`FAILURE_LOG.md`](./FAILURE_LOG.md)

### 战略�?1. **目标版本 8.0.66 锁定** �?D �?7 版本数据证实最优（PROP 最�?�?weixin110/可登录）
2. **不引�?native 三件�?*（Pine/bypassmm/shadowhook）�?是封号高暴露�?3. **业务逻辑�?Catfish 80%，特征面 100% 自有** �?包名/类名/MMKV/网络/签名独立
4. **修改任何 APK/SO/DEX/smali/MMKV 前先问用�?*

### 反检测级
5. **禁止�?`ro.boot.*` 属�?* �?Matrix 反向 hook 监控�?6. **LSPosed 启动必须进程白名�?* �?�?hook 主进程，**�?hook** `:sandboxed_process` `:isolated_*` `:push` `:appbrand*`
7. **不调 ActivityManager.getRunningAppProcesses** �?沙箱进程无权限会 FATAL（封号失败版前车之鉴�?8. **verifiedbootstate 调用 KPI 红线 = 38**�?.0.68 水平�?
### 实现级（FAILURE_LOG F-01 ~ F-15 摘要�?9. 禁止 8.0.70 架构�?8.0.66（混淆名全变�?10. 禁止�?h8.L9/g8.f 调用链（是消息处理链不是会话链）
11. 禁止 WCDB rawQuery 兜底（微信自定义封装�?12. 禁止�?SparseArray Key（非连续�?13. 禁止 hook K0() + notify（Kotlin Flow 覆盖�?14. 禁止 hook Adapter.getCount/getItem/getView（Flow 覆盖/死循环）
15. 禁止 V4 �?RecyclerView position（数据错位）
16. 禁止 V.GONE 做主方案（ViewHolder 污染 + 点击穿透）
17. 禁止 Java 反射 invoke notifyDataSetChanged（ART SIGSEGV�?18. 禁止 notifyItemRange* 三种变体（DiffUtil position 错位/SIGABRT�?19. **禁止 hook 异步回调中持�?`this`**（JNI local ref GC SIGABRT�?20. 禁止全局 hook ArrayList.add（频率过高）
21. **必须 notifyDataSetChanged clean-before**（不�?clean-after�?22. **每个 P 任务关闭必跑 frida_stats.js**（KPI 不增量）
23. **禁止 JniHook / JNI_OnLoad 注入**（F-23 实证：CodecLooper SIGSEGV + 微信强制下线）�?禁止 `System.loadLibrary` / `dlopen` / 自定�?JNI_OnLoad；Hidden API 访问�?XposedHelpers，无需 JniHook
24. **禁止模块�?startService / extends Service**（F-24 实证：Service not found）�?模块在宿主进程内无独�?Service；Notification �?`NotificationManager.notify()`，悬浮窗�?`WindowManager.addView()`
25. **所�?XposedHelpers.findAndHookMethod 必须 `catch (Throwable)`**（F-25 实证：NoSuchMethodError 穿�?catch Exception �?init 序列静默中断�?26. **hook protobuf 类方法禁�?`findMethodExact`**（F-26 实证：parseFrom 定义在父�?`com.tencent.mm.protobuf.f`，findMethodExact 不遍历继承链）�?�?`getMethods()` + `XposedBridge.hookMethod()`
28. **禁止以 `MvvmList.m(List,boolean)` 类级别 hook 作为朋友圈过滤入口**
29. **禁止对已装机验证通过的 hook 点做任何未经用户明确同意的修改**（含"顺手优化"/重构/精简）—— F-31 实证：D1 h1() 被顺手优化后静默失效，无报错无崩溃只是不过滤。现状跑通 = 不动。（F-28 实证：8.0.66 朋友圈数据不走 m()，正确路径是 y1→H→o/p→ArrayList.addAll 实例拦截）— 禁止 MvvmList.m()/s() 类级别 hook，只用 y1 构造+addAll 方案

---

## 三、技术路�?
```
短期 v1   LSPosed Java       �?现在�?(4-6 �? �?Java, 完全禁止 native 三件�?
中期      Frida 临时验证      �?验证新版�?hook 点（不当最终方案）
长期 v2+  自选开�?native     �?仅作"性能瓶颈�?使用（如 SnsObject.parseFrom 单点�?                                禁抄 Pine/bypassmm/shadowhook 三件�?                                候�? LSPlant / bytehook / Dobby（与 Catfish 不撞�?```

**铁律**：native �?v2+ 只能"补单点性能"，不得作为业务逻辑层。授�?校验/蜜罐才下沉到 SO（v2 后）�?
---

## 四、功能模块速览

详细�?[`HOOKMAP.md`](./HOOKMAP.md)�? 模块 + 状态图�?+ 详细资料�?
| 模块 | 含义 | v1 必做 |
|------|------|:--:|
| **A** | 核心隐私（密友列�?密群/密码/总开关） | �?�?|
| **B** | 隐藏触发（摇一�?切后�?Home/锁屏/搜索�?1111�?| �?�?|
| **C** | 消息控制（防撤回/通知伪装�?weixin/未读控制�?| �?**不在 v1**（v2 起）|
| **D** | 痕迹隐藏（朋友圈点赞/评论屏蔽，由 hookSnsLikes/Comments 实现�?| �?D2/D3；D1 v2 |
| **E** | 装b 模块（步�?定位/改零钱） | v2/v3 |
| **F** | 商业彩蛋（反盗版引流/独家功能/私域链接�?| v2 |

### v1 锁定范围（铁律口径，AI 不得扩展�?
```
11 �?hook (HOOK_MAP_V1.md �?P0+P1)
 + 3 态状态机 (显形/隐藏/解锁�?
 + 搜索�?1111 解锁 (EditText 文本监听)
 + B 模块 6 个触发事�?(摇一�?切后�?Home/返回/锁屏/搜索)
 + 朋友�?Proto �?(hookSnsObject 主线 + INIT 兜底)
```

v1 完整 hook 名单 �?[`./docs/HOOK_MAP_V1.md`](./docs/HOOK_MAP_V1.md) P0 + P1
P2 / 暂缓 / 禁止 / 系统层破绽点 �?**全部不做**（对�?Catfish 安全水平�?
---

## 五、技术架构无分歧共识

### 5.1 状态机
- **3 �?*：显�?/ 隐藏 / 解锁中（**无失败计�?*�?- 进程内字�?`mVipMode`（仿 Catfish UserControll�?- 持久化：MMKV `g_<seed>` 命名空间
- 默认：进程启动从 MMKV 恢复，授权下默认隐藏

### 5.2 朋友圈过滤（**MvvmList 实例拦截，v12 方案**�?- **新增样本结论**：甜密友 8.0.63 与微密友 8.0.70 的朋友圈过滤本质一致，都是 `BaseAdapter.getItem(index)` → `field_userName` → `height=1 + GONE`。差异只是安装方式：甜密友 smali 织入，微密友 native/Pine hook。两者都依赖旧 ListView + BaseAdapter 架构，不适用于 Guard Native 8.0.66 的 RecyclerView + MvvmList + StateFlow。P16 不再参考 getView/height=1 作为实现路线，只作为产品行为对照。
- **主线：filter_moments.js v12 直接翻译**（L1 验证�?.0.66 稳定�?  - MvvmList + ArrayList 实例拦截（`k24.b` / `Username` / `gu4.ux5.d`�?  - 不在 RecyclerView active state �?clean（安全约束）
  - 3s 轮询 hashCode �?StateFlow 替换
- **⚠️ 已证伪：`SnsObject.parseFrom(byte[])` Java hook �?8.0.66 从未命中**
  - 根因�?.0.66 反序列化�?JNI/C++ protobuf-lite，不经过 Java parseFrom（F-27�?  - v1 永久禁用此方案；v2 重启须先�?JNI 层验�?- INIT 兜底：warm-attach 首次进入清理已有数据

### 5.3 会话过滤�?*MvvmList 三层，已验证不动**�?- L1: `MvvmList.m(List, boolean)` 主力
- L2: `MvvmList.s(List)` 备用
- L4: `f45.s0.notifyDataSetChanged` clean-before 兜底

### 5.4 通讯录过�?- 复用 F04 思路，等 T07 调研产出 hook �?
### 5.5 搜索拦截�?*隐藏的灵魂判�?*�?- hook `hookSearchContact` + `hookFts`
- **假返�?未找�?** �?微信本地无数据时的默认行为（显示"添加好友"�?- 不需要构造任何数据，�?`return null` / `return false`

### 5.6 通知伪装（简化）
- 构造来�?**`weixin` wxid** 的消息（不动 NotificationManager�?- 用户点开 = 看到微信团队会话（伪装得透明�?
### 5.7 MMKV
- namespace: `g_<seed4>`（每客户独立 seed�?- key: 4 字符短哈希（**不出�?* `hide_list` / `vip_enable` 等明文）

### 5.8 字符串混�?- 类名/方法�?包名禁用敏感词（vip / hide / pirate / wechat / catfish / myauth / wmiyou�?- Toast / Log TAG / MMKV key 全部 seed �?- v1 手动�?seed + 按时间定时发版（**不做自动构建**�?
---

## 六、防检�?KPI（每�?P 任务关闭必跑�?
| 指标 | 8.0.66 基线 | 安全上限 | 红线 | 来源 |
|------|:----:|:--:|:--:|------|
| verifiedbootstate | 15 | 20 | 38 | D �?vs 8.0.68 |
| PROP / 100K �?| 82.5 | 150 | 220 | 同上 |
| normsg / 100K | 2,401 | 4,000 | 5,124 | 同上 |
| CONN 密度 | 0.081 | 0.2 | 0.5 | QE66 P14 |

工具：`frida_stats.js` v1.1（已有），路�?�?[`TOOLS_INDEX.md`](./TOOLS_INDEX.md)

---

## 七、产品形态演�?
```
v1  4-6 �? LSPosed 模块, 小米9/8.0.66, �?Java, 自用+种子客户
v2  2-3 �? 消息控制完整 + 反盗版引流壳 + miyou-server 授权接入
v3  3-4 �? 装b 三件�?+ LSPosed �?改包 (�?root, 客户�?APK)
v4  2 �?   native C++ 蜜罐 + 加盐字幕混合加密
```

---

## 八、开发期工具栈（v1 必做�?
| 工具 | 形�?| 难度 |
|------|------|:--:|
| `adb forward + 浏览器` | 手机�?USB �?浏览�?localhost:8080 看调试控制台 | ⭐⭐ |
| 状态机可视�?| 通知栏常�?+ 悬浮窗实时事件流 | ⭐⭐�?|
| 本地开发模�?| 切断数据流而不�?hook（微信看不到异常�?| ⭐⭐�?|
| 点击添加 wxid | 反射查微�?ContactStorage 解析昵称/头像 | ⭐⭐�?|
| DEV/PROD/HONEY 三�?| 共用代码 + 配置切换（蜜罐复用基础设施�?| ⭐⭐ |
| 一次�?Proto 采集 | Frida dump + 抓包 �?离线资料�?| ⭐⭐⭐⭐ |

---

## 九�? 角色工作流（精简后）

| 角色 | 职责 | skill 路径 |
|------|------|-----------|
| **总调�?* | 制定计划 / 分配 P 任务 / 接手新会�?/ 文档维护 / 失败归档 | `guard-dispatch_总调度` |
| **执行** | 写代�?/ 跑脚�?/ 设备调试�?*必须与用户交�?*，禁止盲猜） | `guard-execute-one_单任务执行` |
| **审核** | P 任务自审（轻档）+ 发布门控（重档，�?KPI 红线�?| `guard-review_审稿复核` |

**调试铁律（执行角色核心）**：需要设备操作时，必须给用户一个明确指令，等结果回来再推进。禁止假设输出、禁�?估计�?XXX"后直接改代码�?
**主目�?*: `.cursor/skills/`（你日常�?Cursor 用得多）
**镜像**: `.claude/skills/`（用 `sync_skills.ps1` 自动同步�?
---

## 十、当�?4 窗口分工速览

详细�?[`TASK_BOARD.md`](./TASK_BOARD.md)

| 窗口 | 主题 | 产出 | 模型建议 |
|:--:|------|------|:----:|
| **W1** | 主开发：脚手�?| adb forward + 浏览�?+ 状态机可视�?+ 本地开发模�?| Sonnet |
| **W2** | 朋友�?Proto | T05 调研 + 实现 `SnsObject.parseFrom` 拦截 | Sonnet |
| **W3** | 会话 | 翻译 `filter_conv.js` �?`ConvFilter.java`（已验证可直翻） | Sonnet |
| **W4** | 离线资料�?| 一次性抓�?+ Frida dump �?所有窗口共�?| Haiku |

**并发铁律**：每个窗口只动自�?P 任务目录，根目录看板（HOOKMAP/TASK_BOARD）改动前 git pull�?
---

## 十一、关键资料路径（绝对路径，不复制�?
完整清单 �?[`PROJECT_INDEX.md`](./PROJECT_INDEX.md)。高频访问：

```
本仓库内:
  ./docs/HOOK_POINTS.md         8.0.66 已验�?hook �?  ./docs/CLASS_MAP_8066.md      混淆类速查
  ./refs/MainEntry.java         Catfish 入口 989 �?  ./refs/UserControll.java      Catfish 业务 944 �?  ./refs/filter_moments.js      Frida 朋友圈已验证脚本

外部资料 (只读, 不复�?:
  C:/Users/Me/Desktop/apk2/_4__samples/dynamic_fast/HOOK_IMPLEMENTATION_ANALYSIS.md
  C:/Users/Me/Desktop/apk2/_4__samples/sample_history_research/VERSION_INDEX.md
  C:/Users/Me/Desktop/apk2/QE66_RESUME.md
  I:/miyou-server/CLAUDE.md     双链路授权服务器
  E:/apk_diff/                  7 个历�?APK (只读样本�?
  E:/ios-dylib/shitou-miyou-core/  iOS 蜘蛛密友参�?```

---

## 十二、危险通告 / 一键停用（安全兜底�?
**触发场景**：封号潮 / 检测密度暴�?/ 新版微信未适配 �?必须能远程拔�?
**机制**�?- 客户端启动拉 miyou-server `cs_url` 的危险通告接口
- 接口返回字段 `kill_switch: true|false`
- `true` �?跳过 hook 注册（表现为完全无功能但不崩溃）�?浮窗显示"已停用，等待更新"
- 用户可手动覆盖（按住返回�?5 �?�?临时启用，仅自查�?
**v1 实现**：客户端启动埋占位实现（默认 false），不调真实接口
**v2 实现**：接�?miyou-server 真实接口 + HMAC 验签
**服务�?*：公告系统已就绪 �?`I:/miyou-server/CLAUDE.md`

详细决策 �?[`DECISION_LOG.md`](./DECISION_LOG.md) D-013

---

## 十三、本项目商业模式（v2 接入�?
- **小众高端客户** + 隐私优先 + **不要求量�?*（避免泛滥触发封号阈值）
- **每客户独�?seed/签名**（蜜罐溯�?+ 反聚类）
- **养破解做私域**（参�?Catfish）：破解校验失败 �?�?独家功能"�?�?跳商�?客服
- **授权按时�?*（miyou-server `cs_url` + `shop_url` + 设备邀请码现成�?- **不批发，只零�?*（学原作者退出批发期的策略）

---

## 十四、对外品�?
- **内部代号**：Guard Native（不对外�?- **客户端品�?*：待定（**禁用** "微信"二字 + vip/pirate/hide 等敏感词�?- **域名/服务器实�?*：跟产品做切割（zxmqq.shop / miyou.lol 已就绪）
- **卡密销�?*：Telegram / USDT 等可切割链路

---

**接手到这里就够了。开始干活前一定要再看一�?[`HOOKMAP.md`](./HOOKMAP.md) + [`TASK_BOARD.md`](./TASK_BOARD.md)�?*
