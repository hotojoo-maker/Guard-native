# P18 brief — 离线资料库采集 + KPI 基线

> **随 P18 暂缓冻结（2026-06-29）** — P18 本轮跳过；其 8.0.66 空白基线计划随 P18 一并暂缓冻结，暂不执行、不作 KPI 门控零点依据。

> 执行窗口直接读这个，不用翻 TASK_BOARD 全文
> 最后更新：2026-05-24

---

## 当前状态

⬜ **未开始**（任务从未领取，基线数据全部缺失）

**关键背景**（与 TASK_BOARD §四 W4 描述的偏差）：
- 原计划用 8.0.66 做空白基线 → 8.0.66 APK 未落地（见 TOOLS_INDEX §〇 §A，状态 ⬜）
- 用户最新指示：**直接用 8.0.71 当前设备 + 现有 Guard 模块做第一版 baseline**
- 无基线 = F-22 铁律空转，P20B/P22 KPI 门控对比零点缺失

---

## 优先级调整（2026-05-24 更新）

**P18 Step 0 改为：8.0.71 + 当前模块 → 第一版 KPI baseline**
- 原因：8.0.66 APK 未就绪，8.0.71 设备在手，不能继续等
- 意义：建立 8.0.71 + Guard v1 当前状态的基线，后续 P 任务以此对比增量
- **注意**：这不是"空白 LSPosed 基线"（理想情况），而是"当前功能已装模块的真实 KPI 快照"

---

## 本任务 hook 点

> 本任务无新 hook，是纯采集任务

| 工具 | 路径 | 用途 |
|------|------|------|
| frida_stats.js | `I:/apk2/_3__D_wechat_ban/official_wechat_ban_research/03_anti_frida/frida_stats.js` | 16 指标 KPI 采集 |
| COLLECTION_SOP.md | `I:/apk2/_3__D_wechat_ban/official_wechat_ban_research/03_anti_frida/COLLECTION_SOP.md` | 采集操作规范 |

---

## 执行步骤（用户需在设备上操作）

### Step 0 — 环境确认

```powershell
# 1. 确认设备连接
adb devices

# 2. 确认微信版本
adb shell dumpsys package com.tencent.mm | findstr versionName

# 3. 确认 frida-server 在跑
adb shell "ps -A | grep frida"
# 若未跑：
adb shell "su -c /data/local/tmp/frida-server &"
```

### Step 1 — 跑 frida_stats（warm-attach 方式）

```bash
# Git Bash（spawn 在 8.0.71 可能需要 warm-attach）
# 先确认微信已启动
adb shell am start -n com.tencent.mm/.ui.LauncherUI
sleep 5

# 取 PID（主进程）
PID=$(adb shell ps -A | grep -w com.tencent.mm | head -1 | awk '{print $2}')
echo "WeChat PID: $PID"

# 挂 frida_stats（官方版无改包，采集 5 分钟即可）
MSYS_NO_PATHCONV=1 frida -U -p $PID \
  -l "I:/apk2/_3__D_wechat_ban/official_wechat_ban_research/03_anti_frida/frida_stats.js" \
  2>&1 | tee "03_execute_执行任务/P18_离线采集/logs/frida_stats_v1_20260524.log"
```

### Step 2 — 采集期间操作（5 分钟内依次做，官方版降级）

```
1. 刷朋友圈（上下滑动约 1 分钟）
2. 打开几个聊天会话（密友 + 普通联系人各 1 个）
3. 发送一条消息给普通联系人
4. 进通讯录搜索联系人
5. 在全局搜索框输入关键词（不输入 111111）
6. 切后台 3 次（按 Home）
7. 锁屏解锁 1 次
8. 其余时间让微信保持前台静置
```

> 说明：当前为官方版（无改包）部署，检测面比改包低，5 分钟采样足够建立指标基线。
> 若后续切到改包版本，需重跑 30 分钟完整版。

### Step 3 — 读取 16 指标结果

```bash
# 采集结束后 Ctrl+C，查看关键指标行
grep -E "verifiedbootstate|PROP|normsg|CONN" \
  "03_execute_执行任务/P18_离线采集/logs/frida_stats_v1_20260524.log"
```

### Step 4 — 把结果写到 result.md §基线数据表

---

## 相关 F-xx（只看这些）

| 编号 | 一句话 |
|------|--------|
| F-22 | 每个 P 任务关闭前必跑 frida_stats，红线: verifiedbootstate≤38/PROP≤220/normsg≤5124/CONN≤0.5 |

---

## 待完成

- [ ] Step 0 环境确认（用户操作）
- [x] Step 0 KPI 基线采集（5 分钟，官方版）✅ 2026-05-24
- [ ] Step 1 frida_stats 对比（后续 P 任务关单时用）
- [ ] Step 2 采集期间标准操作流
- [ ] Step 3 读取 16 指标数值
- [ ] Step 4 填写 result.md §基线数据表
- [ ] 归档 log 到 `06_refs_参考资料/采集快照_dump_snapshots/20260524/`

---

## 关键文件

```
03_execute_执行任务/P18_离线采集/
├── brief.md               ← 本文件
├── result.md              ← 填基线数据
└── logs/
    └── frida_stats_v1_20260524.log  ← 待产出
```
