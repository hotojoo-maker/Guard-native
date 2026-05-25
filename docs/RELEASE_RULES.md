# 发布与运营

> 从 CLAUDE.md §十三~十六移出。

## 危险通告 / 一键停用（安全兜底）

**触发场景**：封号潮 / 检测密度暴增 / 新版微信未适配 → 必须能远程拔插
**机制**：
- 客户端启动拉 miyou-server `cs_url` 的危险通告接口
- 接口返回字段 `kill_switch: true|false`
- `true` → 跳过 hook 注册（表现为完全无功能但不崩溃）+ 浮窗显示"已停用，等待更新"
- 用户可手动覆盖（按住返回键 5 秒 → 临时启用，仅自查）

**v1 实现**：客户端启动埋占位实现（默认 false），不调真实接口
**v2 实现**：接入 miyou-server 真实接口 + HMAC 验签
**服务器**：公告系统已就绪 → `I:/miyou-server/CLAUDE.md`

详细决策 → [`DECISION_LOG.md`](../DECISION_LOG.md) D-013

---

## 商业模式（v2 接入）

- **小众高端客户** + 隐私优先 + **不要求量大**（避免泛滥触发封号阈值）
- **每客户独立 seed/签名**（蜜罐溯源 + 反聚类）
- **养破解做私域**：破解校验失败 → 弹"独家功能" → 跳商城客服
- **授权按时间**（miyou-server `cs_url` + `shop_url` + 设备邀请码现成）
- **不批发，只零售**（学原作者退出批发期的策略）

---

## 对外品牌

- **内部代号**：Guard Native（不对外）
- **客户端品牌**：待定（**禁用** "微信"二字 + vip/pirate/hide 等敏感词）
- **域名/服务器实体**：跟产品做切割（zxmqq.shop / miyou.lol 已就绪）
- **卡密销售**：Telegram / USDT 等可切割链路

---

## 发布铁律 / Release Rules

### 1.0 后签名证书不可变

Guard Native / Guard Pack 从 **1.0 正式发布**开始，同一客户包必须永久保持以下不变：

| 不可变项 | 说明 |
|---------|------|
| `packageName` | Android 覆盖安装的身份标识 |
| APK 签名证书 / keystore | 签名不一致 = 无法覆盖安装，客户必须卸载重装 |
| `customerSeed` | 蜜罐溯源 / MMKV 命名空间根 / 混淆基准 |
| 授权绑定规则 | `licensedWxid + deviceHash + customerSeed` 组合不可拆分重组 |

后续版本只允许 **`versionCode` 递增**，禁止降级发布。

### 强制要求

1. **1.0 发布前必须建立客户包档案**，至少记录：
   - `packageName`
   - keystore 文件路径（离线存储位置）
   - key alias
   - `versionCode` 起始值
   - `customerSeed`
   - `licensedWxid` / device 绑定策略说明
2. **keystore 必须离线备份，至少两份**（不同物理位置）
3. **禁止将正式 keystore 放入**：公开仓库 / 聊天窗口 / 日志 / AI 对话 / 临时目录
4. **keystore 丢失 = 该客户包后续无法无损更新**（无法覆盖安装，只能新包重装）
5. **禁止 1.0 后重新生成 keystore 给同一客户继续发包**
6. **每次发版 `versionCode` 必须递增**，不得复用或回滚

### 例外处理

如果必须更换证书或包名，只能视为 **"新产品 / 新客户包"**：
- 不能覆盖安装旧版本
- 必须提前告知客户需要重新安装 + 重新授权
- 旧包档案归档到 `07_archive_归档/`，不得删除
