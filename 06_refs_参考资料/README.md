# 06_refs_参考资料

跨窗口共享的资料库。  
**8071 文档主车道** → [`../docs/README.md`](../docs/README.md)  
**8066 历史索引** → [`../docs/archive/INDEX.md`](../docs/archive/INDEX.md)  
**Catfish 竞品** → [`../docs/isolation/INDEX_COMPETITOR.md`](../docs/isolation/INDEX_COMPETITOR.md)

## 子目录

```
06_refs_参考资料/
├── apk_samples/                    APK 样本
│   ├── wechat_8066.apk             ← 可选对比样本（非主车道）
│   └── wechat_8066_jadx/           ← jadx 反编译产物
├── competitor_catfish/             Catfish 逆向笔记（8.0.70）
├── wechat_refs_微信参考/           微信内部类/方法/字段说明
├── catfish_refs_原作者参考/        Catfish 业务逻辑速查（不复制代码，仅说明）
├── frida_refs_动态采集脚本/        本项目专用 Frida 脚本
└── 采集快照_dump_snapshots/        W4 P18 产出的 dump/pcap/log
    └── 2026MMDD/
        ├── frida_stats_baseline_*.log
        ├── proto_dump.log
        └── network.pcap
```

## 规则

1. 资料新增 → 改 `PROJECT_INDEX.md` §三 加链接
2. 大文件（pcap > 50MB）只放本地，**不入 git**
3. APK 样本不复制，每个版本只留一份
4. 跨窗口共享必经此目录，**禁止**窗口之间直接传文件
