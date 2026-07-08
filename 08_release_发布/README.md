# 08_release_发布

发布产物归档。

## 子目录

```
08_release_发布/
├── seeds/                            每客户独立 seed 配置
│   └── client_<seed4>/
│       ├── config.json               包名/类名/MMKV ns
│       └── signing.keystore          签名（不入 git, .gitignore）
├── signed_apks/                      已签名待发布 APK
│   ├── v1.0.0_seed_a3f2.apk
│   └── ...
└── release_notes/                    发版说明
    └── v1.0.0.md
```

## 规则

1. 每客户独立 seed（反聚类）
2. 签名 keystore 必须 `.gitignore`，绝不入仓
3. 发版前由 `guard-review_质检门控` skill 跑 5 项检查（发版门控重档）
4. 发版历史可追溯（保留所有签名版本）

## 发版前必跑（guard-review 质检门控 · 重档 5 项）

1. 污染源（撤回结论引用清查）
2. 证据升格（L3/L4 → L1 出处）
3. 二进制改动授权（用户授权记录）
4. 重复脚本/markdown 清理
5. KPI 红线（frida_stats 30 分钟对比基线）

任一阻塞 → 不发版。
