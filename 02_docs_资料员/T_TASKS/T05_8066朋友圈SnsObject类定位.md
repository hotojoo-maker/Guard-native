# T05 — 8.0.66 朋友圈 SnsObject Proto 类定位

> 派出窗口：W2
> 派出日期：2026-05-19
> 预计耗时：1-2 天（便宜模型可做）

---

## 输入

- 微信 8.0.66 APK：`C:/Users/Me/Desktop/apk2/微密友8070官替稳定版-原版.apk`（或同级目录的官方 8.0.66 包）
- jadx 反编译产物：`C:/Users/Me/Desktop/apk2/_4__samples/dynamic_fast/classes17_decompiled/jadx_out/`
- 8.0.70 参考：`./refs/MainEntry.java` 中 `SNSDATA_CLASS = "m05.g46"`
- 8.0.65 静态分析：`./refs/VERSION_8065_ANALYSIS.md`

---

## 任务

1. 在 jadx_out 中搜索 `extends com.google.protobuf.GeneratedMessageLite` 找 protobuf 类
2. 找到朋友圈相关的（关键字段 `userName` / `commentUserList` / `likeUserList` / `contentDesc`）
3. 找到对应类的 `parseFrom(byte[])` 方法
4. 记录：
   - 完整类名（含混淆包名）
   - 类的所有字段（protobuf field ID + 字段名 + 类型）
   - `parseFrom` / `parsePartialFrom` 方法签名

---

## 产出格式

写入 `02_docs_资料员/T_TASKS/T05_结果.md`：

```markdown
## 8.0.66 朋友圈 Proto 类

类: <混淆包名>.<类名>
parseFrom: public static <类名> parseFrom(byte[] data)
跨版本对比:
  8.0.65: <类名>
  8.0.66: <类名>
  8.0.70: m05.g46

字段:
| field_id | name | type | 备注 |
|---|---|---|---|
| 1 | userName | String | wxid |
| 2 | contentDesc | String | 内容 |
| ... |

CommentUserList 类: <类名>
LikeUserList 类: <类名>
```

---

## 验收

1. 类名能在 8.0.66 APK 中 grep 到
2. parseFrom 字节码反汇编看得到 `lcom/google/protobuf/...`
3. 字段 ID 1（userName）能拿到 wxid 格式字符串
4. 不同设备 install 后 hook `parseFrom` 能打 log 出真实数据（W4 离线采集时验证）
