# SETTINGS_UI_V2 — 密友设置页 v2 实现文档

> 最后更新：2026-05-24  
> 实现文件：`src/main/java/com/ghost/assist/moduleB/SettingsEntry.java`  
> 相关文件：`src/main/java/com/ghost/assist/core/Bridge.java`（`isHighPerfMode/setHighPerfMode`）  
> 相关文件：`src/main/java/com/ghost/assist/moduleD/ConvFilter.java`（启动遮罩逻辑）  
> 装机状态：🟡 **2026-05-24 build 装机，外观待用户确认**

---

## 一、入口路径

```
主界面放大镜 → 输入 111111 → sm.toggle() → VISIBLE
→ 「我的资料」文字替换为「量子密友」（SettingsEntry onBindViewHolder）
→ 点击「量子密友」→ showGuardDialog()
```

**铁律（F-29/F-31 复现防御）**：
- SearchUnlock 只做 sm.toggle + unlockEntry + 清空 EditText + act.finish()
- SettingsEntry 只做文字替换 + showGuardDialog
- ❌ 禁止在 SearchUnlock 里直接调 sm.enterHidden/exitHidden
- ❌ 禁止改 111111 触发条件（已验证，动了 = 静默失效）

---

## 二、对话框布局结构

```
Dialog（全屏，Material Light NoActionBar Fullscreen）
└── root LinearLayout（液态玻璃底背景：#EDF1F6 → #F5F7FA 渐变）
    └── ScrollView
        └── content LinearLayout（padding 16/16/16/32 dp）
            ├── [Banner] 品牌 Banner（液态玻璃三层）
            ├── [Label] 密友保护
            ├── [Card] 密友保护卡
            ├── [Label] 密友设置
            ├── [Card] 授权 & 密友列表卡
            ├── [Label] 功能设置
            ├── [Card] 功能设置卡（含启动防层模式胶囊）
            └── [TextView] v1.0 量子密友
```

---

## 三、Banner 设计（液态玻璃三层叠加）

```
FrameLayout（圆角 18dp）
├── 底层 GradientDrawable TL→BR：#E2EEF5 → #EEF6F2（冷青绿渐变）+ stroke 1dp #C8DFF0
├── 中层 GradientDrawable TL→BR：#90FFFFFF → #00FFFFFF（左上高光折射）
└── 顶层 GradientDrawable BOTTOM→TOP：#28E8F0FF → #00FFFFFF（底部雾感）
```

**内容（LinearLayout VERTICAL）**：
| 控件 | 文本 | 样式 |
|------|------|------|
| `bannerTitle` | 量子密友（TEXT_GUARD_ENTRY） | 24sp BOLD #1A2E3A 深墨蓝 |
| `bannerSub` | ————关系存在，不可观测 | 12sp #4A6A7A 冷灰蓝，top 5dp |
| `bannerEn` | Entangled · Encrypted · Invisible | 10sp ITALIC #7A9AAA，letterSpacing 0.06 |
| `badge` | 隐藏保护中 / 量子保护中（状态胶囊） | 12sp 红/绿，圆角 20dp，极淡底色 |

---

## 四、卡片设计（液态玻璃感）

`makeRoundCard()` 统一样式：
- 背景色：`#FAFCFF`（冷白，非纯白）
- 圆角：14dp
- 边框：1dp `#1A90B4D0`（极淡冷蓝边，模拟毛玻璃边缘光）

`makeSectionLabel()` 颜色：`#7A8FA0`（冷灰蓝，与整体调性统一）

---

## 五、密友保护卡（第一张卡）

| 行 | 控件 | 功能 |
|----|------|------|
| 行1 | 密友功能总开关 + Switch | `br.setFeatureEnabled()` |
| 分隔线 | — | — |
| 行2 | 隐藏标签入口 + Switch | `br.setHideContactLabelEnabled()` |

> ⚠️ 已移除「当前：隐藏保护中 / 显示密友」切换行（2026-05-24，无实用价值）

---

## 六、密友设置卡（第二张卡）

| 行 | 内容 | 备注 |
|----|------|------|
| 授权状态 | 已授权 / AUTH_xxx 状态文字 | 只读展示 |
| 密友列表 | X 位密友 › | 点击 → startSelectContact → SelectContactUI |

---

## 七、功能设置卡（第三张卡）★ 今日新增

### 7.1 启动防层模式（胶囊切换）

**视觉**：两段胶囊，外层 `#E8E8E8` 圆角 20dp，内层选中半白底（圆角 17dp）+ 加粗，未选半透明灰字。

| 模式 | 显示文字 | 行为 | 存储 key |
|------|---------|------|---------|
| 均衡模式（默认） | 均衡模式（左） | 冷启动/锁屏亮屏时显示白色遮罩，遮住残影后淡出 200ms | `hpm = false` |
| 高性能模式 | 高性能（右） | 无遮罩，响应更快；建议保持微信后台不被杀 | `hpm = true` |

**Bridge API**：
```java
br.isHighPerfMode()   // false = 均衡（默认）
br.setHighPerfMode(boolean)
```

**说明文案（胶囊下方小字）**：
> 已对性能与隐私做到最大化平衡。「均衡」：冷启动/解锁屏自动遮蔽，兼容性最佳。「高性能」：不建议强杀微信后台，否则冷启动可能出现极短暂留影。

### 7.2 其余功能行（独家占位 / Phase 2）

- 隐藏指定标签（待实现 P26C）
- 密友通知 / 静音
- 消息防撤回（Phase 2）
- 消息伪装 weixin（Phase 2）
- 密码设置

---

## 八、启动遮罩逻辑（ConvFilter.java）

### 触发条件（2026-05-24 修复前后对比）

| 场景 | 修复前 | 修复后（正确） |
|------|--------|--------------|
| 冷启动（进程刚起）| ✅ 显示 | ✅ 显示 |
| 锁屏解锁（亮屏）| ✅ 显示 | ✅ 显示 |
| 从聊天页返回 | ❌ 错误显示（动画卡顿） | ✅ **跳过** |
| 从设置页返回 | ❌ 错误显示 | ✅ **跳过** |
| 高性能模式 | — | ✅ **跳过** |

### 实现原理

```java
// 1. 首次进 LauncherUI 注册一次 SCREEN_OFF 广播
act.getApplicationContext().registerReceiver(receiver, 
    new IntentFilter(Intent.ACTION_SCREEN_OFF));
// receiver: sScreenWasLocked = true

// 2. showColdStartOverlay() 入口双门控
boolean isColdStart  = !sColdCleanDone;     // 进程刚起，数据未清
boolean isLockScreen = sScreenWasLocked;     // 曾经熄屏
if (!isColdStart && !isLockScreen) return;   // 普通 Activity 切换，跳过
sScreenWasLocked = false;                    // 消费锁屏标记

if (Bridge.getInstance().isHighPerfMode()) return; // 高性能模式跳过

// 3. 遮罩参数
背景色: 0xFFFFFFFF（纯白不透明）
淡出时长: 200ms
安全超时: 500ms（锁屏，数据已干净）/ 5000ms（冷启动）
```

### 字段说明

| 字段 | 类型 | 含义 |
|------|------|------|
| `sColdStartOverlay` | `WeakReference<View>` | 当前遮罩 View 弱引用 |
| `sColdCleanDone` | `volatile boolean` | 进程内首次冷启动清理已完成 |
| `sScreenWasLocked` | `volatile boolean` | 屏幕曾经熄灭（待消费标记）|
| `sScreenReceiverInstalled` | `volatile boolean` | SCREEN_OFF 广播接收器已注册（只注册一次）|

---

## 九、已知约束（F-36 OS 层面）

冷启动残影（C1）和锁屏残影（C2）的根因是 **SurfaceFlinger 在 App 首帧前展示冻结帧**，属于 Android OS 行为，Java 层无法完全消除。白色遮罩是当前最优折中方案，均衡模式兼容老设备，高性能模式适合常驻后台的设备。

详细归档：`FAILURE_LOG.md` F-36（SurfaceFlinger 冻结帧，OS 层面约束）

---

## 十、下一步待做

- [ ] 用户确认 Banner / 卡片液态玻璃视觉效果
- [ ] 用户确认胶囊切换交互
- [ ] 均衡/高性能切换后冷启动实际遮罩效果验证
- [ ] P26C 隐藏指定标签过滤实现（占位 → 真实功能）
