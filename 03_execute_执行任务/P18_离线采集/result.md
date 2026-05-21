# P18_离线采集 — 完成报告

> 模板：完成后逐项填写，未完成的留 ⬜，完成的改 ✅

---

## 完成清单

### 第 0 步：空白 LSPosed 基线（最高优先）
- ⬜ 设备装 8.0.66 微信，确认 versionName
- ⬜ LSPosed Manager 启用但无任何模块
- ⬜ `frida_stats.js spawn` 跑 30 分钟（刷朋友圈 + 拉聊天 + 搜索 + 通讯录）
- ⬜ 产出 `frida_stats_baseline_empty_lsposed.log`
- ⬜ 16 指标值记录到本文件 §基线数据

### 第 1 步：业务 Proto dump
- ⬜ SnsObject.parseFrom 入参/返回值 dump
- ⬜ Conversation.parseFrom 等价类定位 + dump
- ⬜ ContactInfo.parseFrom 等价类定位 + dump
- ⬜ MsgInfo.parseFrom 等价类定位 + dump
- ⬜ 产出 `proto_dump.log`

### 第 2 步：网络抓包
- ⬜ tcpdump 30 分钟与 proto dump 同步进行
- ⬜ 产出 `network.pcap`

### 第 3 步：归档到共享资料库
- ⬜ 拷贝到 `06_refs_参考资料/采集快照_dump_snapshots/2026MMDD/`
- ⬜ 更新 `PROJECT_INDEX.md` §三 加路径

---

## 产出文件清单

```
03_execute_执行任务/P18_离线采集/
├── logs/
│   ├── frida_stats_baseline_empty_lsposed.log    ← 基线（必有）
│   ├── proto_dump.log                            ← 30MB 左右
│   └── network.pcap                              ← 50MB 左右
├── scripts/
│   ├── proto_dump.js
│   └── collect.bat                                ← 一键跑
└── result.md                                      ← 本文件
```

---

## 基线数据（待第 0 步完成后填）

| 指标 | 空白 LSPosed 基线值 | KPI 上限 | KPI 红线 |
|------|:----:|:--:|:--:|
| verifiedbootstate | <待填> | 20 | 38 |
| PROP / 100K | <待填> | 150 | 220 |
| normsg / 100K | <待填> | 4000 | 5124 |
| CONN 密度 | <待填> | 0.2 | 0.5 |
| openat / 100K | <待填> | - | - |
| /proc 访问 | <待填> | - | - |
| ... (剩余 10 指标) | <待填> | - | - |

> **基线必须低于 KPI 上限**，否则说明 LSPosed 框架本身可疑，必须先解决再开 W1~W3。

---

## 验收

- ✅ `frida_stats_baseline_empty_lsposed.log` 16 指标全部输出且 < KPI 上限
- ✅ `proto_dump.log` 包含至少 100 条 SnsObject 解析记录
- ✅ `network.pcap` 含微信 mmtls/quic 数据
- ✅ 归档到 `06_refs_参考资料/采集快照_dump_snapshots/`

---

## 风险

- 第 0 步如果基线超 KPI 上限 → **阻塞所有 W1~W3** → 必须先排查 LSPosed 版本/Magisk hook
- 设备 frida-server 与 PC 版本不匹配 → 重新 push 匹配版本

---

## 交接

- **下一步**：W2 朋友圈 用 proto_dump.log 分析 SnsObject 字段；W3 会话 验证 wxid 提取
- **依赖此任务的下游**：W2 / W3 都依赖第 0 步 + 第 1 步产出
