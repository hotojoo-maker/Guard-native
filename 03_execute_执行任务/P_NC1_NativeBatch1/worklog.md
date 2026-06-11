# P_NC1 Native Batch1 装机验证

## 2026-06-11 18:05 L1 动态验证

目的：补齐 native Batch1 / Phase 1A~1E 的装机冷启动日志证据。

操作：

1. 已先做 git 快照：`snap/release-close/20260611-180031`。
2. 清空 logcat。
3. `adb shell am force-stop com.tencent.mm`。
4. 用户手动重新打开微信，停在主界面。
5. 抓取 `NCL` 关键日志。

结果：PASS。

关键证据见：

```text
03_execute_执行任务/P_NC1_NativeBatch1/logs/native_verify_20260611-180545.log
```

结论：

- `libguardcore.so` 加载成功。
- 证书绑定命中：`certBind set sha256[0..3]=ca421ec3`。
- `decrypt_config_self_test()` 通过。
- encrypted registry 解密并解析成功，`entries=4`。
- `BATCH1_VERIFY PASS`。
- `PHASE1A_VERIFY PASS`。
- `PHASE1B_VERIFY PASS`。
- `PHASE1C_VERIFY PASS`。
- `PHASE1D_VERIFY PASS`。
- `PHASE1E_VERIFY PASS`。

诚实口径：

- 本次只证明本地 encrypted registry + 证书绑定 + recipe 出口在装机冷启动通过。
- 服务器真锁仍未完成：服务器短命 key 必要条件、Ed25519 验签、服务器授时真数据源、真正散沙降级仍未接。
