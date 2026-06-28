# 施工提示词 · keystore 锁死（交给 Devin / 外部执行 AI）

> 用法：把本文件**全文复制**粘进 Devin 对话即可。提示词自包含，已限定只读必要文件、并要求执行者先问 Q1/Q2。
> 现状提醒：`build.gradle` 已用 `guardFixed` 固定 keystore 且注释禁回退 debug.keystore，「锁死」骨架已在；**真正待做 = 把临时 debug keystore 换成正式 release keystore + 三端证书 SHA-256 同步固化**。

---

```
【任务】keystore 锁死收口：把临时 debug keystore 切成正式 release keystore，并把证书 SHA-256 三端同步做成单一流程，杜绝 cert 打错误伤正版。

【铁律·防瞎读】只准读下面列出的文件，禁止全仓库漫游、禁止读 .md 以外的历史文档：
  必读(按序)：
    1. build.gradle（看 signingConfigs.guardFixed + buildTypes + productFlavors official/coexist）
    2. src/main/java/com/ghost/assist/core/CompatProbe.java（只看 EXPECTED_CERT 常量，约 36-37 行）
    3. tools/gen_registry_cipher.py（grep _CERT_SHA256，看它怎么折证书进 key）
    4. src/main/java/com/ghost/assist/ModuleMain.java（只看 bindSigningCert + sModulePath，约 280-314 行；确认运行时读 APK 自身签名=自动跟随，无需改）
  其余文件一律不读。拿不准就停下问，禁止猜。

【前置决策（先问用户，别自己定）】
  Q1 是否已有正式 release keystore 文件？还是要新建？
  Q2 官替版 / 共存版用同一把还是各一把 keystore？（共存版改了包名，建议各一把）

【改动点（确认 keystore 后才动；每改一处给 file:line）】
  A. build.gradle signingConfigs.guardFixed：storeFile/storePassword/keyAlias/keyPassword 换成正式值
     （共存若独立 keystore → 加 signingConfigs.guardCoexist，并在 coexist flavor 绑定）
  B. 用 apksigner/keytool 读出新证书 SHA-256（小写无冒号），记下
  C. CompatProbe.EXPECTED_CERT 改成新 SHA-256（共存线不同则按 flavor 区分）
  D. tools/gen_registry_cipher.py 的 _CERT_SHA256 改成新值 → 重跑生成 registry_cipher.inc
  E. 运行时 bindSigningCert 读 APK 自身签名→无需改（仅确认，不动）

【三端一致红线（缺一即回滚）】
  CompatProbe.EXPECTED_CERT == gen_registry_cipher.py _CERT_SHA256 == 实际签名证书 SHA-256
  三者必须逐字节相等；不等会导致正版 registry 散沙、密友隐藏静默全挂(F-31)。

【验证（必须 L1，缺证据不算完成）】
  1. 重新打包 release APK → apksigner verify 证书 = 新 SHA-256
  2. 装机 logcat 必须出现：recipeOk=true、registrySummary 出真 adapter(非 scatter)、各 Filter fallbackSelfTest=ok
  3. 密友隐藏正常([CTF:addAll] removed 等)、无 [cp] cert mismatch
  4. 截屏/日志原文落盘到对应 worklog

【红线】
  - 改 keystore/cert = 高风险写操作，动手前先 git 快照
  - 不动已验证 hook 回调体(铁律29)
  - debug 包可保留 debug keystore；只切 release
  - 共存与官替是两条独立发行线，cert/registry_cipher 各自生成，禁混用
```

---

## 背景常量速查（执行者无需再翻文档）

- 当前临时签名 = `signing/guard-native-debug.keystore`（store/key 口令均 `android`，alias `androiddebugkey`）。
- 当前证书 SHA-256 = `ca421ec3a33708ceb3f70c37f4616751094736c496fe21b3b0e2cea480cdb6a0`（= `CompatProbe.EXPECTED_CERT` = `gen_registry_cipher.py` 的 `_CERT_SHA256`，三处同源）。
- 运行时证书来源 = `ModuleMain.bindSigningCert()` 读**模块自身 APK** 签名 → `NativeBridge.setBindingMaterial` → SO `derive_registry_key` 折入；换证书后自动跟随，无需改运行时代码。
- 发行线：`official`（包名 `com.tencent.mm`，release_id `android_8071`）/ `coexist`（包名 `com.tencent.mn`，release_id `android_8071_mn`）。
