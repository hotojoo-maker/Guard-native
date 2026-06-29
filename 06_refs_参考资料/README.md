# 06_refs_参考资料

跨窗口共享的资料库。  
**8071 文档主车道** → [`../docs/README.md`](../docs/README.md)  
**8066 历史索引** → [`../docs/archive/INDEX.md`](../docs/archive/INDEX.md)  
**Catfish 竞品** → [`../docs/isolation/INDEX_COMPETITOR.md`](../docs/isolation/INDEX_COMPETITOR.md)

## 子目录

```
06_refs_参考资料/
├── competitor_catfish/   Catfish 逆向笔记（8.0.70，竞品唯一真源）
└── README.md             本说明
```

> 注：本 README 原列 `apk_samples/`、`wechat_refs_微信参考/`、`catfish_refs_原作者参考/`、`frida_refs_动态采集脚本/`、`采集快照_dump_snapshots/` 五个子目录，**均未落地**（实际仅 `competitor_catfish/`）；2026-06-29 据实删除其描述，按「不补空目录占位」处理。

## 规则

1. 资料新增 → 改 `PROJECT_INDEX.md` §三 加链接
2. 大文件（pcap > 50MB）只放本地，**不入 git**
3. APK 样本不复制，每个版本只留一份
4. 跨窗口共享必经此目录，**禁止**窗口之间直接传文件
