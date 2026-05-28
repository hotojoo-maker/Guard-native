# T07 — 8.0.66 ContactStorage 字段盘点（四维搜索）

> 派出窗口：guard-review（资料功能）
> 派出日期：2026-05-19
> 预计耗时：1 天（需设备交互）
> 状态：🟡 脚本已产出，待设备运行

---

## 输入

- 设备：小米9 / Android 11 / 微信 8.0.66
- 已知测试 wxid：`wxid_hmu4qj85aaa812`
  - 微信号(alias)：`yjmhyh200811`
  - 昵称：`祀毅矢佴`
  - 备注：当时可能未设置（待确认）
- 8.0.66 已知 contact 类：`com.tencent.mm.storage.m3`（CLASS_MAP_8066.md）
- 8.0.66 已知 contact 方法：`m3.j1()` → wxid String
- 8.0.70 Catfish 参考：`com.tencent.mm.kernel.h.getStorage(Class)` + `ContactStorage.get(String)`
- 脚本：`03_execute_执行任务/P16_朋友圈Proto/scripts/t07_contact_dump.js`（本次产出）

---

## 任务

1. 在小米9上运行 `t07_contact_dump.js`，Frida attach 微信主进程
2. 获取测试 wxid 的 Contact 对象，dump 全部字段 + 值
3. 找到 `com.tencent.mm.storage.m3` 的所有字段，确认四维映射：
   - 原始 wxid → 字段名 + 类型
   - 微信号 (alias) → 字段名 + 类型
   - 昵称 (nickname) → 字段名 + 类型
   - 备注 (remark) → 字段名 + 类型
4. 找到查询入口（kernel 类 + ContactStorage 类 + get 方法名）

---

## 运行步骤

```bash
# 1. 确保微信在前台（进通讯录页面触发 Contact 类加载）
# 2. 获取 PID
adb shell "ps -A | grep com.tencent.mm$"
# 3. 运行脚本
frida -U -p <PID> -l 03_execute_执行任务/P16_朋友圈Proto/scripts/t07_contact_dump.js
```

---

## 产出格式

写入 `02_docs_文档员/T_TASKS/T07_结果.md`（结果报告）：

```markdown
## 8.0.66 Contact 存储体系

### 查询入口
- Kernel 类: <全名>
- 获取 ContactStorage 方法: <签名>
- ContactStorage 类: <全名>
- 查询方法: <签名>

### Contact 对象字段 (com.tencent.mm.storage.m3)
| 语义 | 字段名 | 类型 | 示例值 |
|------|--------|------|--------|
| 原始wxid | ? | String | wxid_hmu4qj85aaa812 |
| 微信号 | ? | String | yjmhyh200811 |
| 昵称 | ? | String | 祀毅矢佴 |
| 备注 | ? | String | (如有) |
| 头像路径 | ? | String | ? |
| ... | ... | ... | ... |

### 完整字段 dump
(所有字段名 + 类型 + 值)
```

---

## 验收

1. `com.tencent.mm.storage.m3` 全字段 dump 成功
2. 四维（wxid/alias/nickname/remark）字段名全部确认
3. 查询入口（kernel → ContactStorage → get）类名+方法名确认
4. 脚本无 SIGSEGV / 微信不崩
