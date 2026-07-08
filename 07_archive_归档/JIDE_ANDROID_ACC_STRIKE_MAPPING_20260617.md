# JiDe 与 Android 8071 环境检测方向纠偏

> 日期：2026-06-17  
> 主题：JiDe iOS 防封样本与 Android WeChat 8.0.71 hook 注入 / 签名校验 / SO 环境监测方向对照  
> 当前防封权威账：`ANTIBAN_MAP.md`。本文按附录累加工作证据；若旧附录和权威账冲突，以 `ANTIBAN_MAP.md` 最新水位线为准。
> 状态：纠偏记录。旧的 `AccStrike / AutoAccessibility` 映射不作为当前研究方向。

## 1. 纠偏结论

先排除这些作为主线：

```text
clicfg_acc_strike_xml
AccConfigManager / AccExptService
AutoAccessibility
disableScreenshot
needClearNodeInfo / needUseFakeInfo
小程序/辅助功能 UI 策略
```

这些链路静态上存在，但更像微信自身辅助功能、小程序或 UI 策略配置，不是当前要找的 JiDe Android 等价点。

本轮目标收敛为：

```text
hook 注入检测
签名校验
SO/native 环境监测
环境异常上报
```

旧结论“JiDe iOS ExptService ~= Android AccStrike 配置链”降级为无效假设。

## 2. JiDe iOS 已确认事实

资料来源：

```text
E:\ios-dylib\JiDe_analysis.md
E:\ios-dylib\WORKLOG.md
E:\ios-dylib\jide_analysis.txt
```

JiDe 已确认的 5 个 hook：

```text
ExptService.getStringExpt:
MMClientCacheManager.getBasicData
MMClientCacheManager.checkConfig
NormalContactVerifyLogic.startVerifyContact:opcode:verifyMsg:
NormalContactVerifyLogic.sendVerifyUserRequest:
```

其中 `ExptService` / `MMClientCacheManager` 只说明 JiDe 会处理配置和客户端基础数据，不能直接推导到 Android `AccStrike`。

## 3. Android 主线：Normsg

当前真正贴近“环境监测异常”的主线是：

```text
libwechatnormsg.so
libwechatnormsgext.so
com.tencent.mm.normsg.*
com.tencent.mm.plugin.normsg.*
```

### 3.1 加载入口

文件：

```text
I:\apk2\_3__D_wechat_ban\jadx_8071\sources\com\tencent\mm\plugin\zero\LoadNormsgJNIService.java
```

加载：

```java
to.d0.o("stlport_shared");
to.d0.o("wechatxlog");
to.d0.o("wechatnormsg");
```

Provider：

```text
I:\apk2\_3__D_wechat_ban\jadx_8071\sources\com\tencent\mm\plugin\zero\LoadNormsgJNIServiceProvider.java
```

加载进程：

```text
main / push / tools / appbrand / magic_emoji / exdevice
```

结论：`wechatnormsg` 是多进程基础风控库，不是单点业务库。

### 3.2 Java native 桥

核心文件：

```text
I:\apk2\_3__D_wechat_ban\jadx_8071\sources\com\tencent\mm\normsg\c.java
I:\apk2\_3__D_wechat_ban\jadx_8071\sources\com\tencent\mm\normsg\WCProbe$Info.java
I:\apk2\_3__D_wechat_ban\jadx_8071\sources\com\tencent\mm\plugin\normsg\NormsgDataService.java
I:\apk2\_3__D_wechat_ban\jadx_8071\sources\com\tencent\mm\plugin\normsg\u.java
```

`com.tencent.mm.normsg.c$p` 用反转混淆字符串加载：

```java
new StringBuilder("tahcew").reverse();
new StringBuilder("gsmron").reverse();
System.loadLibrary("wechatnormsg");
```

随后暴露大量 native 方法：`aa / ab / ac / ... / fl`。`WCProbe$Info` 将这些 native 方法包装给业务层，典型返回值为 `byte[]`，说明多数风控数据由 native 打包/加密后上报。

### 3.3 NormsgDataService

文件：

```text
I:\apk2\_3__D_wechat_ban\jadx_8071\sources\com\tencent\mm\plugin\normsg\NormsgDataService.java
```

关键 op：

```text
op=1  根据 package_name 取应用 label
op=2  ql3.s.INSTANCE.i5()
op=3  ql3.s.INSTANCE.ri()
op=4  WCProbe$Info.g(Intent)
op=5  WCProbe$Info.i(device/info fields)
op=6  WCProbe$Info.c(camera/audio/process runtime fields)
op=7  WCProbe$Info.h(byte[])
op=8  WCProbe$Info.j(risk results list)
```

op=8 接收 `com.iqoo.secure.antifraud.thirdpart.CheckRiskResult`，说明 Normsg 能接入厂商反欺诈风险结果。

## 4. SO 侧发现

目标 APK：

```text
C:\Users\Me\Desktop\guard_native\官方原版8.0.71-2026-5-19.apk
```

临时提取目录：

```text
C:\Users\Me\AppData\Local\Temp\wechat8071_so\
```

### 4.1 libwechatnormsg.so

导入高价值 API：

```text
__system_property_find
__system_property_get
lstat
popen
getauxval
dlopen
```

PLT 调用扫描：

```text
__system_property_find / __system_property_get：大量调用点
lstat：多个调用点
popen：至少 1 个调用点
getauxval：至少 1 个调用点
dlopen：多个调用点
```

明文字符串较少，说明属性名/路径名大多运行时拼接或解密。

已确认明文：

```text
__system_property_find
__system_property_get
lstat
popen
getauxval
WX_BUILD_INFO
cmdline
^N/status
Bzn4 ne /system /vendorX
```

`popen` 处确认命令：

```text
ip route
```

判断：`ip route` 偏网络路由信息采集，不是签名校验。

### 4.2 libwechatnormsgext.so

导入：

```text
dlopen
getauxval
```

字符串很少，暂定为扩展或辅助 native 层，仍需后续反汇编。

### 4.3 libmarsquic.so

命中：

```text
__system_property_get
/proc/self/status
/proc/self/maps
TracerPid:
UNDOCUMENTED_SECURITY_LIBRARY_STATUS
UNEXPECTED_SECURITY_LIBRARY_STATUS
PAC_STATUS_NOT_OK
ParseProcMaps
```

判断：有反调试/安全状态痕迹，但 `marsquic` 更像 QUIC/网络库，可能来自通用 Chromium/网络安全逻辑。当前列为旁线。

### 4.4 Matrix / crash / backtrace 族

相关 SO：

```text
libmatrix-hookcommon.so
libmatrix-jnihook.so
libmatrix-pthreadhook.so
libmatrix-signaledhook.so
libtrace-canary.so
libsystemcrashprotect.so
libwechatcrash.so
libwechatbacktrace.so
```

常见命中：

```text
/proc/self/maps
android_dlopen_ext
dlopen
xhook
jni_hook
hook symbol
ptrace
```

判断：这些是微信/Matrix 自己的 hook、backtrace、crash、pthread/JNI 监控基础设施。它们可能参与异常采集，但不是 JiDe Android 主线的第一入口。

## 5. 已安装包检测

关键文件：

```text
I:\apk2\_3__D_wechat_ban\jadx_8071\sources\com\tencent\mm\normsg\c.java
```

关键方法：

```text
com.tencent.mm.normsg.c$q.c29()
```

读取远程配置：

```java
((g42.d) ((e0) n0.c(e0.class))).rh(d0.clicfg_android_check_installed_pkgs, "", true);
```

配置 key：

```text
clicfg_android_check_installed_pkgs
```

`d22.d0.java` 中存在该 key：

```text
clicfg_android_check_installed_pkgs
```

判断：微信可通过远程配置下发“要检查哪些已安装包”的列表。这是当前最明确的 Android 环境监测线索之一。

## 6. 签名校验现状

本轮全 SO 字符串扫描中，明确出现签名相关字符串的是：

```text
libentryexpro.so
```

命中：

```text
getPackageManager
getPackageInfo
GET_SIGNATURES
signatures
android/content/pm/Signature
```

继续看 JNI 符号后确认该 SO 属于银联组件：

```text
com_unionpay_mobile_android_...
UPPayEngine
NativeSDWriter
```

判断：`libentryexpro.so` 的签名逻辑不是微信主签名自校验方向，先排除为主线。

当前未确认：

```text
微信自身 APK 签名自校验入口
native 层签名 hash 对比
签名异常是否进入 Normsg 上报
```

后续 Java/SO 检索关键词：

```text
getPackageInfo
GET_SIGNATURES
GET_SIGNING_CERTIFICATES
SigningInfo
getApkContentsSigners
PackageInfo.signatures
MessageDigest
certificate
```

若 Java 层没有明确命中，优先回到 `libwechatnormsg.so` 混淆 native。

## 7. 当前优先级

```text
P0: libwechatnormsg.so
    - __system_property_get/find
    - lstat
    - popen
    - getauxval
    - dlopen
    - installed packages config

P1: com.tencent.mm.normsg.* Java 桥
    - c.java native bridge
    - WCProbe$Info
    - NormsgDataService
    - u.java

P2: libwechatnormsgext.so
    - dlopen/getauxval
    - 字符串少，需反汇编

P3: libmarsquic.so
    - TracerPid/status/maps
    - 暂列旁线

P4: Matrix hook/crash/backtrace 族
    - 异常采集底座
    - 暂列旁线

排除主线: libentryexpro.so
    - 银联签名逻辑
```

## 8. 当前结论

当前最可靠判断：

```text
Android 8.0.71 环境异常优先查 wechatnormsg。
AccStrike/AutoAccessibility 先排除。
libentryexpro.so 的签名字符串属于银联组件，先排除微信主签名自校验。
clicfg_android_check_installed_pkgs 是已安装包检测的关键配置线索。
libwechatnormsg.so 的系统属性、lstat、popen、getauxval、dlopen 调用是 native 环境监测主线。
```

证据等级：

```text
L2: Java jadx / SO ELF 导入 / 字符串 / PLT 调用扫描
L4: 具体上报包内容、实际检测名单、签名自校验入口，仍需动态或更深反编译确认
```
# JiDe 与 Android 环境检测方向纠偏

> 日期：2026-06-17  
> 主题：JiDe iOS 防封样本与 Android WeChat 8.0.71 环境监测方向对照  
> 状态：纠偏记录。旧的 `AccStrike / AutoAccessibility` 映射不作为当前研究方向。

## 1. 纠偏结论

本轮先排除以下方向作为主线：

```text
clicfg_acc_strike_xml
AccConfigManager
AccExptService
AutoAccessibility
disableScreenshot
needClearNodeInfo / needUseFakeInfo
小程序/辅助功能 UI 策略
```

这些链路静态上确实存在，但它们更像微信自身的辅助功能、小程序或 UI 策略配置，不是当前要找的 JiDe 等价点。

用户明确指出：本轮目标不是禁截图、辅助功能、小程序策略，而是：

```text
hook 注入检测
签名校验
SO/native 环境监测
环境异常上报
```

因此，旧结论“JiDe iOS ExptService ~= Android AccStrike 配置链”先降级为无效假设，不继续沿这个方向推进。

## 2. JiDe iOS 已确认事实

资料来源：

```text
E:\ios-dylib\JiDe_analysis.md
E:\ios-dylib\WORKLOG.md
E:\ios-dylib\jide_analysis.txt
```

JiDe 已确认的 5 个 hook：

```text
ExptService.getStringExpt:
MMClientCacheManager.getBasicData
MMClientCacheManager.checkConfig
NormalContactVerifyLogic.startVerifyContact:opcode:verifyMsg:
NormalContactVerifyLogic.sendVerifyUserRequest:
```

其中 `ExptService` / `MMClientCacheManager` 只说明 JiDe 会处理配置和客户端基础数据，不能直接推导到 Android 的 AccStrike。

## 3. Android 当前应查方向

### 3.1 SO/native 反检测

优先检查反编译 SO，而不是继续在 Java AccStrike 链里打转。

重点关键词：

```text
frida
xposed
lsposed
zygisk
magisk
riru
substrate
ptrace
TracerPid
/proc/self/maps
/proc/self/status
ro.boot.verifiedbootstate
verifiedbootstate
maps
inject
hook
signature
certificate
```

### 3.2 hook 注入检测

Android Java 层可作为旁证的方向：

```text
com.tencent.mm.pluginguard.ActivityHookInstrumentation
com.tencent.mm.autogen.mmdata.rpt.SystemServiceHookStatusStruct
com.tencent.mm.autogen.mmdata.rpt.AndroidSensitiveApiCheckStruct
com.tencent.mm.sensitive.ContentProviderHooker$HookInvocationHandler
com.tencent.mm.lib.riskscanner.RiskScannerReqBufferService
```

这些只能说明微信存在 hook/敏感 API/风险扫描相关上报结构，不能直接证明当前异常来自这些点。

### 3.3 签名校验

Java 层优先搜：

```text
getPackageInfo
GET_SIGNATURES
GET_SIGNING_CERTIFICATES
SigningInfo
getApkContentsSigners
MessageDigest
certificate
```

如果 Java 层没有明确命中，继续转 SO/native。

## 4. 当前边界

已确认：

```text
AccStrike/AutoAccessibility 方向不作为当前主线。
JiDe Android 对应点需要转向 SO/native、签名校验、hook 注入和环境异常监测。
Java 层 pluginguard / sensitive / riskscanner 只能作为线索，不足以下结论。
```

未确认：

```text
Android 8.0.71 哪个 SO 负责 hook 注入检测。
Android 8.0.71 是否在 Java 或 native 层做 APK 签名自校验。
环境异常具体上报点。
JiDe 对应 Android 的真实配置/基础数据读取点。
```

下一步：

```text
查看反编译 SO 资料，优先围绕 hook 注入、签名校验、环境监测异常继续定位。
```
# JiDe 与 Android 8071 AccStrike 对应分析

> 日期：2026-06-17
> 主题：JiDe iOS 防加好友封号链路与 Android WeChat 8.0.71 `clicfg_acc_strike_xml` / AccStrike 配置链对照
> 结论等级：Android 侧 `AccConfigManager` / `AccExptService` 为 L2 静态证据；JiDe 已确认 hook 点来自既有动态/静态分析记录。

## 1. 结论

这次新发现的 Android 8071 线索不是 SO 层，而是 Java/Kotlin 层的实验配置与 accessibility strike 风控链：

```text
g42.d / MicroMsg.ExptService
  -> wh("clicfg_acc_strike_xml", "")
  -> AccConfigManager.tryGetExptConfig()
  -> yy4.a 解析 acc_strike_newxml
  -> AccExptService.initAccConfig()
  -> accessibility strike / evil stack / evil package / fake info / intercept action
```

它与 JiDe iOS 的 `ExptService.getStringExpt:`、`MMClientCacheManager.getBasicData`、`MMClientCacheManager.checkConfig` 更接近，属于配置/实验/风控输入层。

目前还不能证明它就是 JiDe `NormalContactVerifyLogic.sendVerifyUserRequest:` 的 Android 等价点；好友申请发送链仍需要单独找。

## 2. Android 8071 关键证据

### 2.1 AccConfigManager 读取 strike XML

文件：

```text
I:\apk2\_3__D_wechat_ban\jadx_8071\sources\com\tencent\mm\accessibility\feature\AccConfigManager.java
```

关键方法：

```text
AccConfigManager.getConfigData()
AccConfigManager.tryGetExptConfig()
AccConfigManager.onReceiveStrikeMsg(yy4.a msg)
```

核心读取：

```java
String wh6 = ((d) ((e0) n0.c(e0.class))).wh("clicfg_acc_strike_xml", "");
```

含义：

- 优先从 MMKV `MMKVName_AccConfig` 读取 `MMKVKey_ConfigXml`。
- 本地没有配置时，从 ExptService 读取 `clicfg_acc_strike_xml`。
- 读取到 XML 后用 `yy4.a.fromXml()` 解析成 `acc_strike_newxml`。

### 2.2 g42.d 是 Android 侧 ExptService

文件：

```text
I:\apk2\_3__D_wechat_ban\jadx_8071\sources\g42\d.java
```

关键事实：

```text
public class d extends w implements e0
Log tag: MicroMsg.ExptService
public String wh(String str, String str2) {
    return Ah(str, str2, true);
}
```

判断：

`g42.d.wh(key, default)` 是 Android 8071 侧读取实验/配置字符串的入口之一。它与 JiDe iOS 的 `ExptService.getStringExpt:` 在语义上接近。

## 3. acc_strike_newxml 字段

文件：

```text
I:\apk2\_3__D_wechat_ban\jadx_8071\sources\yy4\a.java
```

XML serial name：

```text
acc_strike_newxml
```

字段：

```text
hit_white_uin
touchex_delay_confirm
accinfo_random_strike
accinfo_clear_strike
expired_duration
device_uuid
forced_strike
intercept_stack
evil_pkg
screenshot_disable_ui_name
screenshot_disable
```

字段含义初判：

- `hit_white_uin`：白名单命中。
- `touchex_delay_confirm`：触摸/无障碍相关延迟确认时间。
- `accinfo_random_strike`：随机 strike 因子。
- `accinfo_clear_strike`：清理节点信息因子。
- `expired_duration`：配置有效期。
- `device_uuid`：设备标识字段。
- `forced_strike`：强制 strike 开关。
- `intercept_stack`：可疑调用栈匹配列表。
- `evil_pkg`：可疑包名列表。
- `screenshot_disable_ui_name`：禁截图 UI 名单。
- `screenshot_disable`：禁截图开关。

## 4. AccExptService 消费链

文件：

```text
I:\apk2\_3__D_wechat_ban\jadx_8071\sources\com\tencent\mm\accessibility\feature\AccExptService.java
```

`initAccConfig()` 读取 `AccConfigManager.INSTANCE.getConfigData()`，并转为运行时策略：

```text
isWhiteUser
touchExDelayConfirmMs
accInfoStrikeFactor
accInfoClearFactor
isForceStrike
evilStackList
evilPkgList
disableScreenshot
disableScreenshotUIList
```

关键判断方法：

```text
hasEvilConfig()
hasEvilServiceInEvilList()
isEvilTraceNow()
isEvilTraces(...)
needInterceptAccAction()
needClearNodeInfo()
needUseFakeInfo()
needInterceptEvilTouch()
```

这说明 `clicfg_acc_strike_xml` 不是普通 UI 配置，而是会影响 accessibility / 自动化 / 可疑调用栈 / 可疑包名 / fake info / 拦截行为的一条风控配置链。

## 5. 与 JiDe iOS hook 点的对应

JiDe 已确认的 iOS 点：

```text
ExptService.getStringExpt:
MMClientCacheManager.getBasicData
MMClientCacheManager.checkConfig
NormalContactVerifyLogic.startVerifyContact:opcode:verifyMsg:
NormalContactVerifyLogic.sendVerifyUserRequest:
```

Android 当前对应关系：

```text
iOS ExptService.getStringExpt:
  ~= Android g42.d.wh("clicfg_acc_strike_xml", "")

iOS MMClientCacheManager.getBasicData / checkConfig
  ~= Android AccConfigManager.getConfigData()
  ~= Android AccConfigManager.tryGetExptConfig()
  ~= Android AccExptService.initAccConfig()

iOS NormalContactVerifyLogic.startVerifyContact / sendVerifyUserRequest
  Android 等价点仍未确认
```

## 6. 对“马上封号”现象的解释价值

如果账号在没有插件时一执行加好友就触发封号，而装 JiDe 后不触发，那么仅用“限频”解释不够。

更合理的模型是：

```text
客户端存在某个 strike / evil service / evil stack / accinfo 风控输入
  -> 加好友请求前或过程中被读取
  -> 未处理时触发高风险路径
  -> JiDe 在配置/实验层或请求发送层修正该输入
```

Android 8071 的 `clicfg_acc_strike_xml` 证明微信存在一套 accessibility strike 风控配置链。它可能解释“马上封号”的前置条件之一，但还不能证明它直接参与好友申请网络包发送。

## 7. 下一步查证方向

### 7.1 继续找好友申请发送链

在完整 8071 jadx 中继续检索：

```text
micromsg-bin/verifyuser
MMFunc_VerifyUser
VerifyUser
verifyMsg
opcode
sayhello
contact verify
friend verify
```

目标是找到 Android 对应：

```text
NormalContactVerifyLogic.startVerifyContact:opcode:verifyMsg:
NormalContactVerifyLogic.sendVerifyUserRequest:
```

### 7.2 继续追 AccStrike 调用点

围绕这些方法做 xref：

```text
AccExptService.needInterceptAccAction()
AccExptService.needClearNodeInfo()
AccExptService.needUseFakeInfo()
AccExptService.needInterceptEvilTouch()
AccExptService.isEvilTraceNow()
AccExptService.hasEvilServiceInEvilList()
```

目标是确认：

- 哪些 UI / 业务链会读取这些策略。
- 是否与添加好友、联系人资料页、验证消息发送链相交。
- 是否存在“命中后马上触发 strike”的路径。

## 8. 当前边界

已确认：

- Android 8071 存在 `clicfg_acc_strike_xml`。
- 它通过 `MicroMsg.ExptService` 读取。
- 它进入 `AccConfigManager` 和 `AccExptService`。
- 它控制 accessibility strike / evil stack / evil package / fake info / intercept action 等策略。

未确认：

- Android 好友申请发送链的具体混淆类名。
- `clicfg_acc_strike_xml` 是否直接影响加好友请求。
- JiDe 具体修改了哪些好友申请字段。

结论：

```text
AccStrike 配置链是 Android 8071 防封/风控研究的强线索；
它优先解释配置与环境风控，不等同于好友申请发送链本身。
```

---

# 附录 A — Normsg native 桥深挖（2026-06-18 B29 会话补充）

> 来源：jadx 8071 直接反编译。证据等级 L2（Java 反编译结构）；native(aa~fl) 内部判定逻辑与服务器侧用途仍不可证。
> 读取文件：
> - `com/tencent/mm/normsg/c.java`（c$p native 桥 + c$q 数据源桥）
> - `com/tencent/mm/normsg/WCProbe$Info.java`（op→native 映射）
> - `com/tencent/mm/plugin/normsg/NormsgDataService.java`（跨进程 op1-8）
> - `com/tencent/mm/plugin/normsg/u.java`（NormsgSourceImpl 采集实现）
> - `com/tencent/mm/plugin/zero/LoadNormsgJNIService.java`（加载入口）

## A.1 c$p native 方法全表（aa~fl）

`com.tencent.mm.normsg.c$p`，`System.loadLibrary("wechatnormsg")` 后暴露：

```text
aa(int,int,int,byte[])->byte[]          通用分发
ab()/ag()/ah()/ai()/aj()/al()->boolean  布尔探针
ac(String,boolean)->String
ad()->byte[]  ae(int)->byte[]  af(int)->byte[]
ak()->void
am(Intent)->byte[]                      op4 intent 采集
an(String,long,String,int)->byte[]
ao()->int
ap(int,String,long,int,String)->byte[]  op5 设备信息
aq(String x5,int)->byte[]
ar(long,Obj,Obj,long,Obj,Obj)->byte[]   op6 摄像头/录音
as(byte[])->byte[]                      op7
at(int,List<j>,int)->byte[]             op8 风险结果(厂商反欺诈)
au(byte[])->byte[]                      dispatchEncryptJNIFuncCall 通用加密分发
ba/bb(int)->String
bc/bd/be(...PValue...)->boolean          编码/加密(出参 PValue)
ca(Object,Class)->boolean  cb(Object)->boolean   对象/类探测(疑 hook/代理判定, 待 native 证)
da/db/dc(String)->void  dd(String)->boolean  de(String)->byte[]  df(String x3)->void
dg(String,MotionEvent,int,String,int)->void     触摸事件
dh(String)->void  di(String)->boolean  dj(String,Obj)->byte[]  dk(String)->String
dl(String,int)->void  dm(String,MotionEvent,String)->boolean    触摸+判定
dn(String)->byte[]  dp(String)->void
ea(int,int)->byte[]  eb(byte[],int,int)->boolean  ec()->boolean
ed/ee()->byte[]  ef/eg/eh()->String
fa(String,int,int)->void  fb(int,List<h>)->void  fc()->boolean  fd()->void
fe/ff(int)->void  fg(String)->boolean  fh(String)->void  fi(String)->byte[]
fj(String)->void  fk()->void  fl(String)->int
```

## A.2 u.java（NormsgSourceImpl）采集面

Java 采集 → native 打包 byte[] → 上报：

```text
设备指纹: OAID(Y8->np5.e) / z3() 设备信息大采集器(1433 指令) / GSM 信号(c21) / 网络类型(c26)
传感器:   c2 加速度计等采样, 上限 clicfg_sensor_max_sampling_count(默认200)  => 识别模拟器/自动化/真人
已装包:   df() 注册 PACKAGE_ADDED/REMOVED 广播 + 远程名单 clicfg_android_check_installed_pkgs(| 分隔, c$q.c29 读)
进程:     eh() getRunningAppProcesses() 判主进程 standby (微信主进程用, 合法)
摄像头/录音/显示: CSM/ASM/SRD 注册 Camera/Audio/Display 回调; j8 打包摄像头+录音(当前+上次) op6
截屏检测: Cd/X1 -> dispatchEncryptJNIFuncCall, funcID=2 ContactInfoUIScreenShot / funcID=1 PaiedOrderUIScreenShot
触摸点击: Z9(View,Class) 反射 getListenerInfo 包 OnTouch/OnClick(c0/b0); F2/Ii/fi/ii 上报 MotionEvent => AccStrike evil-touch 输入
图片内容: ch/id decodeFile 尺寸 + 扫图二维码(WxQBarResult)
厂商反欺诈: op8 iqoo CheckRiskResult; ORIM 注册 com.oplus.phonemanager.APPRISK 收 OPPO 风险
计数器:   Pg() 72h(259200000ms) 滚动计数
读取配置: clicfg_sensor_max_sampling_count / clicfg_disable_smart_click_for_lucky_money / clicfg_disable_receive_oppo_app_feature
```

## A.3 li0.a/ki0.a EXEC 调用插桩（hellhound AOP；判读已更正，见附录 B.2）

`c$p` 加载库 + `u.ch()` 解码图片都用 `li0.a` 包一层（而非裸调用）：

```text
li0.a.d(obj, args, "com/tencent/mm/normsg/c$p", "<clinit>", "java/lang/System_EXEC_", "loadLibrary", ...)
li0.a.d(obj, args, ".../NormsgSourceImpl", "getSecSdkImageInfos", "android/graphics/BitmapFactory_EXEC_", "decodeFile", ...)
```

判读（⚠️ 已更正，详见附录 B.2）：
- `li0.a` 转发到 `ki0.a`（tag HABBYGE-MALI.hellhound）= 微信自有 AOP/插桩框架，全 App 40+ 文件在用（APM + 生命周期 + 敏感 API before/after 监听 + __HOOK__ 可改返回值回调）。
- 它给敏感 API 调用点（loadLibrary/decodeFile 等）记调用溯源并通知注册的监听器。
- 原写「= 抓 Xposed 的探针 / 核心套路」**过度解读**；它是插桩底座，是否被安全模块用于反 Xposed 判定 = **未证实（L4）**，待 grep 安全用途 li0.c/d + native 证。

## A.4 字符串/库名混淆（实证 = 手段2）

```text
库名 wechatnormsg          = "tahcew".reverse() + "gsmron".reverse()
MMKV 命名空间 normsg_trustdata = "gsmron".reverse() + "atadtsurt_".reverse()  (Ig/rh/wh 读写删 trust 数据)
S6() XOR 解密器            : ch = c ^ 65447 ^ (byte)~(i ^ len)
```

## A.5 对 Guard Native 的防御启示

```text
1. 已装包/evil_pkg 是正面威胁: 查 clicfg_android_check_installed_pkgs / evil_pkg 是否点名 LSPosed 管理器 / frida / 我们辅助 APK。LSPosed 藏模块, 但管理器 App 本身可能被点名。
2. li0.a 溯源探针 再次印证 CLAUDE.md 铁律 23: 别 hook/重定向微信自身 loadLibrary/敏感 API, 否则溯源对不上即暴露。
3. 防线是「不产生信号」: 上报是 native 加密 byte[], 想拦只能注入微信 JNI(红线禁), 不现实。
4. 触摸监控只盯微信自己的 View; 我们 B 模块触发是系统事件(摇一摇/切后台/锁屏)非合成点击 => 低风险, 保持现状。
```

## A.6 证据等级与下一步

```text
L2: c.java / u.java / WCProbe$Info.java / NormsgDataService.java / LoadNormsgJNIService.java jadx 反编译结构
L4: native(aa~fl) 内部判定逻辑、li0.a 实际判 hook 算法、服务器侧用途、evil_pkg/installed_pkgs 实际名单
```

下一步候选：

```text
1. clicfg_android_check_installed_pkgs 默认名单 + evil_pkg 实际内容(是否点名 LSPosed/我们)  <- 对我们最关键
2. li0.a 源码(EXEC 探针怎么判 hook)
3. Ghidra 看 libwechatnormsg.so 的 __system_property_get / popen(ip route) / lstat / dlopen native 侧
```

---

# 附录 B — 检测链路径指针 + #1/#2 结论 + 版本口径（2026-06-18 B29 会话）

> 原则：不复制内容，只记路径指针（CLAUDE.md §十二）。所有结论标 L2/L4。

## B.0 版本口径

```text
研究对象 = 微信 8.0.71 官方原版（不是 8.0.70）
jadx 源树:    I:\apk2\_3__D_wechat_ban\jadx_8071\sources\
目标 APK:     C:\Users\Me\Desktop\guard_native\官方原版8.0.71-2026-5-19.apk（见正文 §4）
临时 SO 目录: C:\Users\Me\AppData\Local\Temp\wechat8071_so\（见正文 §4）
底座一致:     CLAUDE.md 微信 8.0.71（D-014 2026-05-21 切版）
无关项:       8.0.70=Catfish 竞品参考(docs/isolation, CATFISH_8070)；8.0.66/8.0.68=benchmark 对照 .so
证据: L2(路径名 + APK 文件名 + 底座)；versionCode 未读 manifest 钉死(可补)
```

## B.1 #1 结论：installed_pkgs / evil_pkg = 服务器下发，APK 内无名单

```text
clicfg_android_check_installed_pkgs（全树仅 2 处, 均无 baked 名单）:
  读取  I:\apk2\_3__D_wechat_ban\jadx_8071\sources\com\tencent\mm\normsg\c.java   (c$q.c29, split "|")
  常量  I:\apk2\_3__D_wechat_ban\jadx_8071\sources\d22\d0.java
evil_pkg（全树仅 2 处, 均无 baked 名单）:
  消费  I:\apk2\_3__D_wechat_ban\jadx_8071\sources\com\tencent\mm\accessibility\feature\AccExptService.java (evilPkgList)
  解析  I:\apk2\_3__D_wechat_ban\jadx_8071\sources\yy4\a.java (acc_strike_newxml)
结论: 两份名单均服务器运行时下发; 实际是否点名 LSPosed/我们/竞品 = L4, 要运行时:
  frida dump c$q.c29() / AccExptService.evilPkgList, 或 MITM 配置下发
```

## B.2 #2 结论（更正正文 A.3）：li0.a = hellhound AOP 框架，非专用反 Xposed 探针

```text
门面  I:\apk2\_3__D_wechat_ban\jadx_8071\sources\li0\a.java   (转发 ki0.a.h())
本体  I:\apk2\_3__D_wechat_ban\jadx_8071\sources\ki0\a.java   (tag HABBYGE-MALI.hellhound)
事实(L2): ki0.a = 微信自有 AOP/插桩框架(hellhound):
  - Activity/Fragment 生命周期跟踪(APM)
  - 敏感 API 调用点 before/after 监听(li0.d/li0.b) + __HOOK__ 可改返回值回调(li0.c, registerHookCallback)
  - 注册表 key: <owner>_EXEC_|method|sig  /  ...__HOOK__|...  /  ...|...
  - _EXEC_ 包装遍布 MoreTab/remittance/finder/sns/appbrand/webview/chatting 等 40+ 文件 = 全 App APM 底座
更正: 正文 A.3「li0.a=抓 Xposed 探针/核心套路」过度解读;
  准确: 它是 AOP 插桩底座; 是否被安全模块用于反 Xposed 判定 = 未证实(L4)
待证: grep 谁注册安全用途 li0.c/d + libwechatnormsg.so native 侧
```

## B.3 本轮读过文件（路径指针总表）

```text
I:\apk2\_3__D_wechat_ban\jadx_8071\sources\com\tencent\mm\plugin\zero\LoadNormsgJNIService.java
I:\apk2\_3__D_wechat_ban\jadx_8071\sources\com\tencent\mm\plugin\normsg\NormsgDataService.java
I:\apk2\_3__D_wechat_ban\jadx_8071\sources\com\tencent\mm\normsg\c.java
I:\apk2\_3__D_wechat_ban\jadx_8071\sources\com\tencent\mm\normsg\WCProbe$Info.java
I:\apk2\_3__D_wechat_ban\jadx_8071\sources\com\tencent\mm\plugin\normsg\u.java
I:\apk2\_3__D_wechat_ban\jadx_8071\sources\li0\a.java
I:\apk2\_3__D_wechat_ban\jadx_8071\sources\ki0\a.java
```

## B.4 待办（#3 + 运行时）

```text
#3 Ghidra(用户跑) libwechatnormsg.so:
  lib/arm64-v8a/libwechatnormsg.so → Import → JNI_OnLoad(RegisterNatives 对 aa~fl) →
  跟 __system_property_get/find / popen(ip route) / lstat / dlopen 调用点与字符串
运行时(可选): frida dump c29()/evilPkgList 拿实际名单
```

## B.5 复核结果（2026-06-18 B29）

```text
#1 versionCode: 钉不死 —— jadx_8071 为 sources-only 导出, 全树无 AndroidManifest.xml。
   8.0.71 由 路径名 jadx_8071 + APK 文件名 官方原版8.0.71 锁定(L2)。
   要 versionCode: 对 APK 跑 aapt dump badging 官方原版8.0.71-2026-5-19.apk。
#2 hellhound 用途: 抽样 2/2 = APM, 非反 Xposed (佐证 A.3 更正):
   f32.f (li0.b@startActivity/startActivities) -> Activity 跳转埋点, tag HABBYGE-MALI.HellActivityStub
   o32.a (li0.d@Finder fragment 切换)          -> 页面停留计时(n32.f), tag HABBYGE-MALI.FinderHomeMonitor
   残留 L4: 未逐个读完 40+ 实现者; 包族 f32/o32/d42/e32/g32/n32 均页面监控, 优先级低。
#3 native: 待用户开手机动态(frida); 静态见 JIDE 正文 §4 + 附录 B.4。
```

---

# 附录 C — AccStrike 客户端消费链（静态 L2，2026-06-18 B29）

> 文件: I:\apk2\_3__D_wechat_ban\jadx_8071\sources\com\tencent\mm\accessibility\feature\AccExptService.java
>        I:\apk2\_3__D_wechat_ban\jadx_8071\sources\yy4\a.java（acc_strike_newxml 解析）
> 性质: 主要针对「无障碍(accessibility)自动化」(自动加好友等机器人)；客户端动作是软反制, 非直接封号, 封号仍服务器。

## C.1 配置字段（acc_strike_newxml -> AccExptService 运行时）

```text
hit_white_uin(q)          -> isWhiteUser            白名单用户(命中不 strike)
touchex_delay_confirm(u)  -> touchExDelayConfirmMs  触摸延迟确认(反自动点击; 无障碍开则=0)
accinfo_random_strike(k)  -> accInfoStrikeFactor    0-100 概率因子(strike)
accinfo_clear_strike(j)   -> accInfoClearFactor     0-100 概率因子(清节点信息)
expired_duration(o)       配置有效期
device_uuid(l)
forced_strike(p)          -> isForceStrike          强制 strike 开关
intercept_stack(r)        -> evilStackList(;分隔)    可疑调用栈 类名.方法名 黑名单
evil_pkg(n)               -> evilPkgList(;分隔)      可疑包名/无障碍服务 黑名单
screenshot_disable_ui_name(t) -> disableScreenshotUIList
screenshot_disable(s)     -> disableScreenshot      命中则 FLAG_SECURE 禁截图
配置源: AccConfigManager.getConfigData()(clicfg_acc_strike_xml) + clicfg_acc_white_service_list
        服务器 onReceiveStrikeMsg 推送 -> initAccConfig() 重载
```

## C.2 检测/反制动作（client-side soft strike，非封号）

```text
isEvilTraceNow(): hasEvilConfig() && 当前 Throwable().getStackTrace() 任一 ClassName.method 命中 evilStackList(子串)
   = 调用栈类名黑名单匹配; 命中=可疑(自动化/hook 栈)
   hasEvilConfig() = evilStackList 非空 && randomPass(accInfoStrikeFactor)
hasEvilServiceInEvilList(): 命中 evilPkgList -> setFlags(FLAG_SECURE) + onScreenShotDisable
needInterceptAccAction(): 无障碍关 && strikeFactor>0 -> randomPass -> 拦截动作
needClearNodeInfo():      概率(accInfoClearFactor) -> 清无障碍节点信息(读屏工具读不到), 5s 缓存
needUseFakeInfo():        概率(accInfoStrikeFactor) -> 给读屏工具返回假信息, 5s 缓存
needInterceptEvilTouch(): 概率(interceptEvilTouchFactor) -> 拦截可疑触摸
getTouchExDelayConfirmTimeMs(): 无障碍开=0; 否则 touchExDelayConfirmMs
randomPass(f): f>=100 || random(1,101)<=f
关键门: 多数检测先看 AccUtil.isAccessibilityEnabled() -> 主打无障碍自动化
```

## C.3 对 Guard Native 的关系（L2 推断 + L4 待证）

```text
1. AccStrike 主打无障碍自动化(自动加好友 bot); 我们是 LSPosed(Xposed) 非无障碍 -> 多数 strike 门(isAccessibilityEnabled/节点信息)不直接打我们。
2. intercept_stack(evilStackList) = 调用栈类名黑名单, 通用兜底: 若我们 hook 让敏感操作调用栈出现我们/Xposed 类名, 且服务器把这些名推进 intercept_stack -> 可能命中。
   -> 印证红线 §6.8: 类名/包名 seed 混淆必须保持; 实际是否点名我们 = L4(待运行时 dump)。
3. evil_pkg = 安装包/无障碍服务黑名单, 同 normsg 已装包威胁 -> 别留可检测 APK/管理器。
4. 客户端动作是软反制(FLAG_SECURE/假信息/拦截/延迟), 非封号; 封号仍服务器评分(L4)。
待动态(用户手机): frida dump AccExptService 的 evilStackList/evilPkgList/各 factor, 看实际是否点名 LSPosed/我们。
```

---

# 附录 D — Strike 推送链 + 加好友发送链（静态 L2，2026-06-18 B29）

## D.1 AccStrike 配置怎么到客户端

文件: I:\apk2\_3__D_wechat_ban\jadx_8071\sources\com\tencent\mm\accessibility\feature\AccConfigManager.java

```text
两条来源:
1. 服务器 sysmsg 推送(主): onReceiveStrikeMsg(yy4.a msg)
     msg = acc_strike_newxml(xmlPrefixTag=sysmsg) -> 存 MMKV:
       namespace MMKVName_AccConfig / key MMKVKey_ConfigXml(xml) + MMKVKey_ReceiveTime(收到时刻)
2. 实验配置兜底:          tryGetExptConfig() 读 clicfg_acc_strike_xml
读取/过期: getConfigData()
   c17 - receiveTime <= expired_duration(o()) 才有效; 过期 -> 清 MMKV 返回 null
触发链: 服务器对"被标记账号"下发 acc_strike sysmsg -> onReceiveStrikeMsg -> MMKV
        -> AccExptService.initAccConfig() 重载 -> strike 生效
含义: strike 是"定向下发"(针对账号), 非全量; 实际谁被下发/下发什么 = L4(服务器/运行时)
```

## D.2 加好友发送链（JIDE 正文 §7 开口项 -> 找到 Android 候选）

grep "micromsg-bin/verifyuser" 命中:

```text
com.tencent.mm.pluginsdk.model.l3       <- verifyuser 请求模型(实际发送, 最强候选)
as.a (AddContactFeatureService)         <- 加好友预检+编排; Bg() 返回 new l3(...)
   - 预检 cgi: /cgi-bin/micromsg-bin/verifyuserprecheck (cmdid 11568, lv6/mv6)
   - tag: AddContactFeatureServic / MicroMsg.VerifyUserPreCheckUIC
com.tencent.mm.plugin.lite.jsapi.comms.k4  <- JS-API 旁路(次要)
```

对应 iOS JiDe:

```text
iOS NormalContactVerifyLogic.startVerifyContact / sendVerifyUserRequest
  ~= Android com.tencent.mm.pluginsdk.model.l3 (+ as.a 预检)
状态: 候选已定位(L2 grep + as.a 确认 l3 为 verify 模型); l3 内部字段/opcode 待读确认(L4)
```

## D.3 关键开口仍未证（L4）

```text
AccStrike(evilTrace/intercept) 是否直接卡 加好友发送链(l3/as.a)?
  - 本轮未发现 l3/as.a 直接调 isEvilTraceNow()/needInterceptAccAction()
  - 与 JIDE 正文 §6 同结论: AccStrike 解释"秒封"前置, 未证直接进好友申请网络包
  - 待证(静态): 读 l3.java + grep 谁在发送前调 i55.e(isEvilTraceNow)
  - 待证(动态): 手机 frida 跟 加好友点击 -> 看调用栈是否过 AccExptService
```

---

# 附录 E — 动态 L1 实测（com.tencent.mm 小号 + com.tencent.mn 已封共存版，2026-06-18 B29）

> 工具: frida 17.11  -U -p <pid> -l tools/dump_wx_detect.js（只读快照；attach 干净退出，无崩溃/无强制下线）
> 账号: com.tencent.mm = 干净原版小号; com.tencent.mn = 已封共存版小号（用户保留研究，无封号风险）
> 设备: MI 9 (609b4b18), 微信 8.0.71, frida-server /data/local/tmp/frida-server(root)

## E.1 AccStrike 实配（mm 与 mn 完全一致）

```text
strike XML（两账号字节级相同）:
  touchex_delay_confirm = 30000
  accinfo_random_strike = 100
  accinfo_clear_strike  = 100
  intercept_stack(evilStackList, size=4):
    com.hihonor.magicvoice.phoneaction.serviceexecutor.contentsensor.util.ScreenUtil.sendTouchEventToActivity
    com.huawei.vassistant.contentsensor.util.SensorScreenUtil.sendTouchEventToActivity
    com.hihonor.magicvoice.phoneaction.serviceexecutor.contentsensor.viewclick.Action
    com.huawei.vassistant.contentsensor.viewclick.Action
  evil_pkg(evilPkgList, size=1): com.zhipu.agent
AccExpt 运行时: strike=100 clear=100 evilTouch=0 touchDelayMs=30000 disableSS=false
c$q.c29() installed_pkgs: size=0（空，两账号一致）
```

## E.2 结论（L1）

```text
1. AccStrike 配置在 干净号(mm) 与 已封号(mn) 完全相同 -> 全局反自动化策略, 非账号定向。
2. 打击目标 = 手机厂商语音助手自动点击(荣耀 magicvoice / 华为 vassistant contentsensor) + 智谱 AI 手机 agent(com.zhipu.agent)。
3. 未点名我们任何东西(无 com.ghost.assist / com.tencent.mn / libguardcore)。
4. => AccStrike 不是 com.tencent.mn 共存版被封的原因。
5. mn 与官方包的差异应转到 normsg native / 签名 / 包名路径等客户端采集面继续查；服务器侧用途未证，不写封因。
6. c29 installed_pkgs 空 = 此设备未被下发已装包检测名单。
```

## E.3 复现命令

```text
adb shell "su -c '/data/local/tmp/frida-server'"            # 起 server
frida -U -p <pidof com.tencent.mm> -l tools/dump_wx_detect.js
frida -U -p <pidof com.tencent.mn> -l tools/dump_wx_detect.js
脚本 tools/dump_wx_detect.js: AccConfigManager.getConfigData / c$q.c29 / AccExptService 字段
注: frida 无 TTY 时载入脚本同步跑完即退出; 快照类 dump 正好够用, Interceptor 连续 hook 需保活(ping 喂 stdin)。
```

## E.4 待查（ban 真因，转 native/server）

```text
- normsg native(libwechatnormsg.so): 是否读包名 / 数据路径 / 签名 / dlopen 探测 -> 是否进入上报链
- 服务器侧用途未证；没有石锤证据时，不写重打包判定 / 风控封号推测。
- 下一步动态: frida Interceptor 挂 normsg native(__system_property_get/dlopen/lstat) + Java getPackageInfo(GET_SIGNATURES)
```

---

# 附录 F — 明文实锤：共存版包名被 normsg 采集并进入明文报送链（动态 L1，2026-06-18 02:50 CST）

> 取证: frida 17.11 warm-attach  -p <pidof com.tencent.mn>  -l tools/dump_normsg_plaintext.js
> 设备: MI 9 (adb 609b4b18) / 微信 8.0.71 / com.tencent.mn(共存版, 已封小号) PID 15921
> 证据 log: tools/normsg_dump_20260618.log（z3 完整 <softtype> + Y8 OAID 已转存；原始终端 548083.txt 转瞬即逝）
> hook: com.tencent.mm.plugin.normsg.u.z3(int) 设备指纹 / u.Y8() OAID / WCProbe$Info.dispatchEncryptJNIFuncCall / .i

## F.1 实锤（更正 E.4 的"客户端不可直证" —— 已直证）

```text
normsg z3() 设备指纹 <softtype> 上报体(AES 加密前明文) 含:
  k33 = com.tencent.mn               <- 包名(共存版) ★非官方身份数据
  k49 = /data/user/0/com.tencent.mn/ <- 数据路径(共存版) ★非官方身份数据
=> 客户端把"非官方包名"明文塞进上报体。官方包名 = com.tencent.mm。
   服务器如何使用该数据未证，禁止写重打包 / 分身 / 封号推测。
证据等级: 上报含 com.tencent.mn = L1(frida 明文实证); 服务器侧用途 = 未证。
双边坐实(2026-06-18 02:57 CST): 原版 mm 主动调 u.z3(0) 实抓 k33=com.tencent.mm / k49=/data/user/0/com.tencent.mm/
  vs 共存 mn k33=com.tencent.mn。同设备 OAID(k65) 两版相同, 仅包名身份不同 => 证实 k33=进程真实包名,
  官替(com.tencent.mm)不暴露 / 共存(com.tencent.mn)暴露。log: tools/normsg_dump_20260618.log
```

## F.2 z3 设备指纹明文字段（节选，全文见 log）

```text
k33 com.tencent.mn  |  k49 /data/user/0/com.tencent.mn/  |  k65/Y8 OAID 2e9eb064fc427f37
k32 公网IP 117.185.246.250  |  k22 中国电信  |  k30/k47 Wi-Fi
k9/k37/k42 MI 9 / Xiaomi  |  k11 SoC SM8150  |  k34 build Xiaomi/cepheus/...V12.5.6.0
k63 androidId类 Ad5e98956c7b35a7  |  k64 UUID 5e3bf60a-95db-362b-a8fa-4ba76eb626f9
k4/k48 序列号 1234567890ABCDEF  |  k14 MAC 02:00:00:00:00:00  |  k18/k25/k27/k62 各类 hash
```

## F.3 对 Guard Native 的硬启示

```text
1. 共存版(com.tencent.mn) 天生暴露: k33 包名 + k49 数据路径 = 客户端报送的非官方身份数据。
2. 官替版(劫持 com.tencent.mm) 此项不暴露(上报包名=官方) -> 长命应走官替版, 慎用共存版。
3. 若坚持共存版: 须在 normsg 采集前伪装 k33/k49 回 com.tencent.mm == hook 微信采集链(碰原生/敏感, 高风险; 且 k49 是真实文件系统路径, 难伪)。
4. AccStrike(附录 E) 与本轮包名 / 路径采集链不同路；当前只能确认 normsg 设备指纹报送包名 / 路径差异。
```

## F.4 frida 方法学补记（本轮踩坑，留给后续 AI）

```text
- frida 17: Module.findExportByName 静态方法已删 -> 用 Process.findModuleByName(lib).findExportByName(fn)
- frida 17 spawn 默认 auto-resume(--no-pause 已删; --pause 才暂停)
- com.tencent.mn 正常启动 OK; frida spawn/早期注入 -> SIGABRT(JNI nativeLoad) 崩 -> 原生加载链对早期注入敏感(印证铁律23)
- normsg native(libwechatnormsg.so) property/lstat 仅启动期; warm-attach 抓不到 -> 改 hook Java 采集器 u.z3/Y8 拿明文成功
- List 序列化用 String.valueOf(Object) 强制; frida 保活用 ping 喂 stdin
- 工具脚本: tools/dump_wx_detect.js(AccStrike/c29 快照) | tools/dump_normsg_native.js(native探针) | tools/dump_normsg_plaintext.js(明文)
```

---

# 附录 G — B35 会话：normsg 混淆解法 + 实机明文 dump + 签名轴开查（2026-06-18）

> 底座：微信 8.0.71。证据：L1(frida 明文) / L2(jadx_8071 静态)。承接附录 F(包名 / 路径报送 L1 实锤)。
> 角色：防封官。L1 log：`tools/normsg_mm_z3_B35_20260618.log`

## G.1 normsg Java 侧混淆方案（L2 静态，jadx_8071）

```text
层1 反转字符串(藏库名/命名空间):
  库名   wechatnormsg     = "tahcew".reverse()+"gsmron".reverse()
         com/tencent/mm/normsg/c.java  c$p:40-41 (loadLibrary)
  MMKV域 normsg_trustdata = "gsmron".reverse()+"atadtsurt_".reverse()
         com/tencent/mm/plugin/normsg/u.java  Ig:290 读 / rh:1174 写 / wh:1219 删 (存信任/风险数据)

层2 XOR 串解密器 (u.java:368-375, 无名方法):
  明文[j] = (密文[j] ^ 0xFFA7) ^ (byte)( ~((j+1) ^ len) )   // j 从0计, len=串长
  = 拿到公式即可静态还原任意 normsg 混淆串

层3 native(libwechatnormsg.so):
  属性名/路径名 SO 内加密、运行时解 (D线#17) — L4, 需 Ghidra
```

## G.2 读明文最方便的窗口（方法学，L1 验证）

```text
别硬解 native AES 密文。明文口 = Java 采集层 u.z3()/u.Y8()/WCProbe$Info
  报文在此以明文 <kNN> XML 组装, 之后才交 native 加密上报。
工具: tools/dump_mm_z3.js(主动调 z3(0)/Y8 快照, 快进快出) | tools/dump_normsg_plaintext.js(warm-attach hook)
零红线: 只 hook 微信自家 Java 采集器, 不注入 native (守铁律23)。
```

## G.3 实机明文 dump（L1，2026-06-18，com.tencent.mm 测试小号）

```text
设备 MI9(609b4b18)/微信8.0.71/com.tencent.mm pid19012; frida17.11 warm-attach -l tools/dump_mm_z3.js
attach 干净, app 未崩(pid 不变)。关键字段(全文见 tools/normsg_mm_z3_B35_20260618.log):
  k33 包名      = com.tencent.mm                  ★官方=不暴露
  k49 数据路径  = /data/user/0/com.tencent.mm/    ★官方
  k65/Y8 OAID  = 2e9eb064fc427f37                (与附录F com.tencent.mn 同机一致)
  k32 117.185.246.250 / k22 中国电信 / k9 MI9 / k11 SM8150 / k34 build / k3 安卓11 ...
结论(L1): 官替版 k33/k49=官方身份 → normsg 包名 / 路径轴看不出改包;
         对照附录F(com.tencent.mn 暴露)同机仅身份不同 → 官替仅解决包名 / 路径轴。
         注意: 附录 G.4 / H.2 已修正，官替版签名轴仍会暴露，不能再写“长命走官替版”。
```

## G.4 签名轴（已答 L1，spawn 冷启，2026-06-18）

> 工具 `tools/dump_sig_check.js`；log `tools/normsg_sig_B35_20260618.log`。

```text
答案: 微信 8.0.71 运行期【读自身 APK 签名】(GET_SIGNATURES=0x40, Signature.toByteArray len=751=证书DER),
      且 normsg 把签名打进网络上报 protobuf(w15.kg.toProtoBuf → network) —— 与包名同一条上报链。
读签名的调用方(L1 栈):
  ★ normsg c$p.ad(native)→WCProbe.n→u.z3(设备指纹)→ql3.s.z3→w15.kg.toProtoBuf→p2.B2→network.w0.onTransact
  ★ normsg c$p.aa(native)→WCProbe.f→u.uc→ql3.s.h→w15.kg.toProtoBuf→network
  ★ normsg c$p.af(native)→WCProbe.m→plugin.normsg.f.run(checkSoftType, 同读 sourceDir)
    t8.c0→w15.kg.toProtoBuf→network ; oy5.d→modelbase.l3(verifyuser 请求模型)
  合法非风控: Tinker ShareSecurityCheck(热补丁校验) / GMS GoogleSignatureVerifier(系统)
★硬结论: 官替版(占 com.tencent.mm)隐藏包名(k33), 但签名=我方证书≠腾讯官方;
       normsg 上报签名 → 签名属于可区分非官方身份的报送数据 → 【官替版经签名轴暴露】。
       => "官替版长命"修正: 官替版只隐藏包名轴, 签名轴仍暴露。
证据: 上报含自身签名=L1; 服务器是否据此判断 / 处罚未证，不写推测。
旁注: 一处栈见 LSPHooker_/HookBridge = LSPosed 在进程内(我方), 另一独立暴露面(待评估)。
```

## G.5 S6 解密器动态排查（L1 + L2，2026-06-18）

```text
S6 = ql3.j 接口 XOR 解密器 (u.java:367  String S6(String))。
静态(L2): 全树 grep ".S6(" 命中全是别类同名方法 → normsg S6 无 Java 字面量调用点
          → 隐藏串非 Java 内联, 走 ql3.j 服务接口(疑 native 回调/运行时分发) → 静态无法收割。
动态(L1): tools/dump_normsg_s6.js  hook u.S6 + 主调 z3(0):
          z3(0) 触发完成, S6 hits=0 → z3 设备指纹路径不走 S6。
结论(B35 实测闭环): spawn 启动期(dump_normsg_boot.js) + warm-attach 重操作(扫一扫/附近的人/设置/
      发消息/摇一摇, hook 保活~265s) → S6 hits 全程=0。
      S6 不在常规运行路径, 疑 native JNI 回调 / 服务器下发加密 config 才触发(冷路径)。
下一步(静态): Ghidra libwechatnormsg.so 找 ql3.j.S6 的 native 调用方 + 它传的密文常量, 用已知公式离线解:
      明文[j] = (密文[j] ^ 0xFFA7) ^ (byte)~((j+1)^len)
工具: tools/dump_normsg_s6.js(动态留观) | tools/dump_normsg_boot.js(启动期)

定论(B35, 四向证伪):
  ① Java 全树零调用方: ql3/j.java:50 声明; u.java:367 + ql3/s.java:108(委派 n0.c(j).S6) 实现;
     但全树无 .S6( 调 normsg 这条(ql3.s.INSTANCE 仅 h/Zi/x1/C1/ob 等被用, 唯 S6 无人调)。
  ② SO 无明文 JNI 签名: (Ljava/lang/String;)Ljava/lang/String; count=0 → 非标准 JNI 调。
  ③ Java hook u.S6 ~265s(z3/spawn/前后台/扫一扫/附近的人/设置/发消息/摇一摇) 0 hit
     → 任何来源(Java 或 native JNI)调用都会触发本 hook, 仍 0 = 运行期根本不调。
  ④ 离线暴力扫 SO(tools/s6_scan_so.py, 1155563 串 ×Latin1/UTF8 ×S6 ×关键词) 0 命中。
结论: S6 = 有声明+实现但无活跃调用方的遗留 XOR 解密器, 无可捕获的隐藏串原文。
     真正活跃的混淆原文是 property 名(已 G.6 动态拿到)。S6 线收口, 不再投入。
```

## G.6 normsg 启动期 native 探测面（L1，spawn 冷启，2026-06-18）

> 工具 `tools/dump_normsg_boot.js`；log `tools/normsg_boot_probe_B35_20260618.log`；设备 MI9(Magisk root)。
> normsg @0x71db403000 size=11780096(~11.2MB)。过滤：只记 returnAddress 落在 libwechatnormsg.so 内的 libc 调用。

```text
__system_property_get (去重 = 动态解开的混淆属性名原文):
  设备身份: ro.build.* / ro.product.* / ro.hardware / ro.bootimage.build.fingerprint / gsm.*(SIM/基带)
  ★ root/解锁/篡改: ro.bootloader / ro.secure / ro.debuggable / sys.oem_unlock_allowed /
                   ro.boot.flash.locked / ro.boot.verifiedbootstate / ro.boot.veritymode / ro.boot.vbmeta.device_state
lstat:
  无 su/magisk/frida/xposed/riru/zygisk -> 8.0.71 仍走属性检测, 非文件扫描 (L1 印证 D线#1/#10)
  /proc/self/fd/* x682 (fd 枚举, 反注入) + /system/framework/* (boot classpath 扫描)
S6: hits=0 (启动期 + 主调 z3 均不触发) -> 隐藏串走别的触发(网络/服务器下发), 待 warm-attach 被动等
```

防封启示：
- normsg root/解锁检测 = 纯系统属性(上面 8 项)→ 与我方 LSPosed 模块无关, 是**设备层(Magisk)信号**; 锁机真机不暴露, 开发机(Magisk)暴露。
- normsg 不扫 su/magisk/frida 文件 → 我方 hook 不产生文件信号; 注意 **fd 枚举**(别留可疑 fd)。
- `ro.boot.verifiedbootstate` 读取 = KPI 红线指标(CLAUDE §七), 本轮启动期已读到(读取**计数**才是 KPI)。

## G.7 签名伪装缓解 可行性评估（防封官，2026-06-18，待共审 / 未实施）

> 背景：G.4 实证官替版经签名轴暴露。本节评估"让 normsg 看到官方签名"的缓解可行性。
> 性质：评估 / 提案，**未实施**；签名=身份根锁邻域，需 **网络安全官 + 授权检查官 共审**。

```text
技术可行性(L1 支撑):
  - 单拦截点: native c$p.ad/aa/af 均 JNI 回调 Java ApplicationPackageManager.getPackageInfo
    → Java 层 hook 一个 getPackageInfo(pkg=com.tencent.mm + 签名 flag), 把 signatures/signingInfo
      换成官方证书 → 一手覆盖 aa/ad/af/t8.c0/l3 全部读点; Signature.toByteArray 跟随自然返回官方。
  - 官方证书可得: 从官方 APK 抽 751B 证书。
最大风险:
  ① 半伪装更糟: 若 normsg 另有 native-直读 base.apk 签名块(不过 Java),
     Java=官方 / native=我方 → 矛盾 = 比单纯不符更强的篡改信号 (未排除, L4)。
  ② hook artifact: 栈已见 LSPHooker_; normsg 可能检测 getPackageInfo 被 hook。
  ③ 选择性: 只能改 com.tencent.mm 自身; 不能碰 GMS(GoogleSignatureVerifier 需真 GMS 签名)/Tinker/其它, 否则 break。
门控前置(必做, 否则不动):
  Ghidra 验 c$p.aa/ad/af: 只经 JNI 调 Java getPackageInfo? 还是也 native-直读 APK 签名块?
    - 只经 Java → 伪装可行(单 hook)。
    - 有 native-直读 → 放弃(native hook=铁律23, 不做)。
  注: 此门控 = composer 探子签名轴任务第 2/3 点, 其结果可直接回答。
证据: getPackageInfo 唯一观测读法=L1; 无 native-直读=未证(L4); 服务器侧用途未证。
结论: 中等工作量可做, 但收益取决于"无 native-直读", 半成品有反效果风险。
     不建议盲上; 先 Ghidra 门控验证 + 共审, 再决定。
```

**门控结果（B35，L2 静态导入分析 + L1 动态，已答 → 可行）**：

```text
libwechatnormsg.so 导入表(118)中【无任何文件内容读取】(open/openat/read/pread/mmap/fopen 全无, 仅 lstat 取元数据)
  + 无 crypto(EVP/SHA/X509/d2i/BIO) + 无 zip/inflate 导入。
=> normsg native 物理上【读不了 base.apk 字节】, 证书字节只能来自 Java getPackageInfo(L1 已实证 c$p.aa/ad/af → JNI 回调)。
=> Java 层 hook getPackageInfo 伪装【可完整覆盖】aa/ad/af/t8/l3 全部读点 → 【签名伪装 = 技术可行】。
残留 L4: 仅理论 native Binder 直连 PMS(需 ioctl, 无证据, 极罕见) / 裸 syscall; 可 Ghidra headless 反编译 aa/ad/af 终极确认。
工具: tools/elf_imports.py
仍需: 网络安全官 + 授权检查官 共审(选择性 / 官方证书源 / hook artifact 风险) 再实施。
```

---

# 附录 H — B56 会话：官方 8.0.71 检测面复验（2026-06-18）

> 底座：微信 8.0.71 官方 `com.tencent.mm`，设备 MI 9 (`609b4b18`)。  
> 角色：防封官。主线：官方版本优先，抓检测面、明文和上报链，不从密文 / 函数名猜封因。  
> L1 log：
> - `tools/normsg_mm_z3_B56_cli_20260618.log`
> - `tools/normsg_sig_B56_cli_20260618.log`
> - `tools/normsg_boot_probe_B56_cli_20260618.log`

## H.0 Frida 17 方法学修正（B56）

```text
现象:
  Python frida.create_script 直接加载旧 Java.perform 脚本时报:
    ReferenceError: 'Java' is not defined

根因:
  Frida 17 起 Java bridge 不再默认注入到 Python create_script 的普通 agent。
  frida CLI REPL 自带 bridge, 直接 frida -U -p/-f -l old.js 可跑旧 Java.perform 脚本。

本轮处置:
  手机 frida-server 从 17.9.3 更新到 17.11.0。
  之后用 frida CLI 17.11.0 抓 Java 明文 / 签名栈 / boot native 面。
```

方法学结论：后续跑旧 `.js` 取证脚本时，优先用 frida CLI；若要继续用 Python runner，必须把脚本改成显式 `import Java from "frida-java-bridge"` 并编译成 agent。

## H.1 官方包 z3/Y8 明文复验（L1）

```text
工具: tools/dump_mm_z3.js
方式: frida CLI attach 官方 com.tencent.mm 主进程, 主动调 u.z3(0)/Y8()
证据: tools/normsg_mm_z3_B56_cli_20260618.log
```

关键字段：

```text
k33 包名      = com.tencent.mm
k49 数据路径  = /data/user/0/com.tencent.mm/
k57 version  = 3080
k65/Y8 OAID  = 2e9eb064fc427f37
k9/k37       = MI 9 / Xiaomi
k34 build    = Xiaomi/cepheus/cepheus:11/RKQ1.200826.002/V12.5.6.0.RFACNXM:user/release-keys
```

结论（L1）：官方 `com.tencent.mm` 在包名 / 路径轴上呈官方身份；本条只证明 `k33/k49` 轴干净，不证明签名轴安全。

## H.2 签名轴复验：自身签名进入 normsg / protobuf / network（L1）

```text
工具: tools/dump_sig_check.js
方式: frida CLI spawn 官方 com.tencent.mm, hook getPackageInfo / Signature.toByteArray
证据: tools/normsg_sig_B56_cli_20260618.log
```

启动期确认：

```text
getPackageInfo pkg=com.tencent.mm flags=0x40 GET_SIGNATURES
Signature.toByteArray len=751
```

签名进入 normsg 上报链 1：

```text
ApplicationPackageManager.getPackageInfo
com.tencent.mm.normsg.c$p.ad(Native Method)
com.tencent.mm.normsg.WCProbe$Info.n
com.tencent.mm.plugin.normsg.u.z3
ql3.s.z3
w15.kg.toProtoBuf
com.tencent.mm.modelbase.p2.B2
com.tencent.mm.modelbase.t2.U8
com.tencent.mm.network.w0.onTransact
```

签名进入 normsg 上报链 2：

```text
ApplicationPackageManager.getPackageInfo
com.tencent.mm.normsg.c$p.aa(Native Method)
com.tencent.mm.normsg.WCProbe$Info.f
com.tencent.mm.plugin.normsg.u.uc
ql3.s.uc
ql3.s.h
w15.kg.toProtoBuf
com.tencent.mm.modelbase.p2.B2
com.tencent.mm.modelbase.t2.U8
com.tencent.mm.network.w0.onTransact
```

签名读取链 3：

```text
ApplicationPackageManager.getPackageInfo
com.tencent.mm.normsg.c$p.af(Native Method)
com.tencent.mm.normsg.WCProbe$Info.m
com.tencent.mm.plugin.normsg.f.run
```

旁路但重要：

```text
oy5.d.<init> -> com.tencent.mm.modelbase.l3.<init>
```

该链中本轮见到 `HookBridge` / `LSPHooker_` 栈痕迹，说明 LSPosed artifact 是独立暴露面，需后续单独评估；不能仅因出现栈名就直接写成封因。

结论（L1）：8.0.71 官方包会读取自身签名，且签名进入 normsg / protobuf / network 链。官替版即使隐藏包名轴，签名仍属于可区分非官方身份的报送数据。服务器是否据签名判断 / 处罚未证，不写推测。

## H.3 启动期 native 环境检测面复验（L1）

```text
工具: tools/dump_normsg_boot.js
方式: frida CLI spawn 官方 com.tencent.mm, 只记录 returnAddress 落在 libwechatnormsg.so 内的 libc 调用
证据: tools/normsg_boot_probe_B56_cli_20260618.log
```

命中模块：

```text
libwechatnormsg.so @0x71db402000 size=11780096
```

`__system_property_get` 命中：

```text
ro.build.version.sdk
ro.build.version.release
ro.product.model
ro.build.fingerprint
ro.product.board
ro.bootloader
ro.product.brand
ro.product.device
ro.hardware
ro.product.name
ro.product.manufacturer
gsm.sim.state
gsm.sim.operator.iso-country
gsm.sim.operator.numeric
gsm.version.baseband
ro.product.cpu.abilist
gsm.operator.alpha
ro.boot.verifiedbootstate
ro.boot.veritymode
ro.boot.vbmeta.device_state
```

`lstat` 主要命中：

```text
/system/framework/*
/system/framework/arm/*
/system/framework/arm64/*
/proc/self/fd/0 ... /proc/self/fd/685
```

本轮窗口未见 `libwechatnormsg.so` 直接 `lstat` 以下路径：

```text
su / magisk / frida / xposed / riru / zygisk
```

结论（L1）：8.0.71 官方包启动期环境检测主面是系统属性、boot/verity 状态、framework 扫描、fd 枚举。`ro.boot.verifiedbootstate` 已确认在 normsg 启动期读取面里；是否构成 KPI 风险要看读取次数增量，不是只看是否读取。

## H.4 B56 当前收口

```text
已实证:
  1. 官方 z3 明文: k33/k49 为官方身份。
  2. 签名轴: 自身签名 len=751 经 normsg -> toProtoBuf -> network。
  3. 环境轴: boot/verity 属性 + framework lstat + fd 枚举。

仍待证:
  1. 服务器如何使用签名数据 = 未证，不写判断 / 处罚推测。
  2. LSPosed HookBridge/LSPHooker_ 栈是否被 normsg/Matrix 采集为封控信号。
  3. KPI 读取次数是否因我方模块增量。
  4. toProtoBuf 入参结构还需继续拆字段，不能停在栈级别。
```

下一步优先：抓 `w15.kg.toProtoBuf` / `modelbase.p2.B2` 入参周边，把 z3、uc/checkSoftType、签名链对应的 protobuf 字段继续拆成可读明文 / 长度 / hash；同时做 LSPosed artifact 专项评估。

## H.5 第一性原理：根因不是单个 hook，而是可信身份不自洽

根因定义先分层：

```text
客户端根信号：可做到 L1，例如包名、路径、签名、boot 属性、fd 枚举、HookBridge 栈、protobuf 前明文。
服务器最终规则：黑盒，不能直接 L1；当前文档不写服务器判断推测，只记录客户端采集 / 报送事实。
```

第一性原理：微信不是只找“某一个 hook”，而是在验证 **官方客户端可信身份是否自洽**。这套可信身份由四类信号组成：

```text
1. 应用身份: 包名 / 数据路径 / sourceDir / 签名证书 / versionCode / 安装来源
2. 设备环境: ro.boot.* / ro.secure / ro.debuggable / bootloader / build / product / gsm
3. 运行时污染: fd 枚举 / stacktrace / classloader / HookBridge / LSPHooker_ / Frida / LSPosed
4. 行为信号: 触摸 / 点击 / 传感器 / 摄像头 / 录音 / 显示 / 截图 / 厂商反欺诈
```

客户端会把这些信号经 normsg / protobuf / network 上报；服务器如何使用未证。防封研究要找的是：**哪些不变量先破裂，并且是否进入上报链**。

研究纪律：**大动脉优先，明文优先，混淆降级为工具**。

```text
大动脉:
  应用身份 / 设备环境 / 运行时污染 / 行为信号 / protobuf-network 上报链

细枝末节:
  单个混淆函数 / 零散字符串 / 局部 hook 点 / 未进入上报链的静态发现

原则:
  1. 不用混淆复杂度代替检测面结论。
  2. 不把破解混淆当主线。
  3. 混淆只在阻塞明文或字段命名时才查。
  4. 一旦拿到明文, 立即回到"检测点 -> 明文字段 -> 上报链 -> KPI/账号结果"。
```

当前三个根源候选：

```text
1. 共存版根源 = 包名 / 路径轴破裂
   证据: k33=com.tencent.mn, k49=/data/user/0/com.tencent.mn/
   等级: 客户端 L1, 服务器侧用途未证

2. 官替版根源 = 签名轴破裂
   证据: GET_SIGNATURES + Signature.toByteArray len=751 进入 normsg -> toProtoBuf -> network
   等级: 客户端 L1, 服务器侧用途未证

3. LSPosed / 环境根源 = 运行时污染 + boot 状态
   证据: ro.boot.verifiedbootstate / ro.boot.veritymode / ro.boot.vbmeta.device_state,
         /proc/self/fd/*, HookBridge / LSPHooker_ 栈
   等级: 检测面 L1, 服务器侧用途未证
```

真正找根源不能大锅粥，必须做控制变量：

```text
A 官方原版 + 干净设备
B 官方原版 + Magisk/LSPosed 空框架
C 官替版 + 不做签名伪装
D 官替版 + 只做签名伪装
E 官替版 + 签名伪装 + 我方最小模块
F 共存版
```

每组固定抓四类证据：

```text
1. z3/Y8 明文字段
2. 签名读取与 toProtoBuf 链
3. boot/native 环境检测面
4. KPI 计数 + 小号账号结果
```

判定标准：

```text
哪个字段变了
哪条链上报了
哪个 KPI 增了
哪个账号结果变坏了
```

当前优先级：

```text
P0 签名轴
P1 LSPosed / HookBridge 运行时污染
P2 boot/KPI 增量
P3 行为自动化
```

结论：能继续逼近根源，但不是靠继续泛反编译，而是以官方版本为基线，把每个可信身份不变量逐个击穿。

## H.6 B56 event probe v2：normsg 事件明文返回（L1）

> 脚本：`tools/dump_normsg_event_probe_v2_B56.js`  
> 证据：`tools/normsg_event_probe_v2_B56_20260618.log`  
> 目的：不抓泛请求，只抓 normsg / WCProbe 事件层明文、返回 byte[]、调用栈。

本轮 v2 成功 hook：

```text
com.tencent.mm.normsg.c$p.aa/ad/af/ae
com.tencent.mm.normsg.WCProbe$Info.dispatchEncryptJNIFuncCall/n/f/m/i/c/g/h/j
com.tencent.mm.plugin.normsg.u.z3/Y8/uc
ApplicationPackageManager.getPackageInfo(signature flags)
```

### H.6.1 z3 -> WCProbe.n -> c$p.ad（设备指纹链）

主动触发：

```text
u.z3(0)
```

调用链：

```text
u.z3
  -> WCProbe$Info.n
  -> c$p.ad
  -> getPackageInfo(pkg=com.tencent.mm, flags=0x40)
```

`c$p.ad` / `WCProbe.n` 返回：

```text
len=16
hex=18c867f0717aa67b2ab7347505ba07ed
base64=GMhn8HF6pnsqtzR1BboH7Q==
```

同链 z3 明文仍为官方身份：

```text
k33 = com.tencent.mm
k49 = /data/user/0/com.tencent.mm/
k57 = 3080
k65/Y8 = 2e9eb064fc427f37
```

### H.6.2 plugin.normsg.f.run -> WCProbe.m -> c$p.af（checkSoftType / 签名轴）

自然触发：

```text
com.tencent.mm.plugin.normsg.f.run
```

调用链：

```text
plugin.normsg.f.run
  -> WCProbe$Info.m(arg0=1934848488)
  -> c$p.af(arg0=1934848488)
  -> getPackageInfo(pkg=com.tencent.mm, flags=0x40)
```

`c$p.af` / `WCProbe.m` 返回：

```text
len=8
hex=40f3c57757da35fc
base64=QPPFd1faNfw=
```

同一 `plugin.normsg.f.run` 内先触发：

```text
c$p.ae(arg0=1934848488)
```

返回：

```text
len=16
hex=834a771539349623e40f7ccc93d026c0
base64=g0p3FTk0liPkD3zMk9AmwA==
```

### H.6.3 本轮未触发项

本轮已 hook 但未实际触发：

```text
WCProbe$Info.dispatchEncryptJNIFuncCall
WCProbe$Info.i
```

结论：随机 UI / 设置页操作不是可靠事件触发点。当前已拿到 `ad/ae/af` 三个 normsg 事件返回 byte[]；`dispatchEncrypt` protobuf 明文仍需另找触发点。

### H.6.4 当前收口

```text
c$p.ad: z3 设备指纹链的一段 16B 返回，同时读自身签名。
c$p.ae: checkSoftType 前置 16B 返回，arg0=1934848488。
c$p.af: checkSoftType / WCProbe.m 8B 返回，同时读自身签名。
```

下一步：围绕 `plugin.normsg.f.run` / `WCProbe.m` 追 `arg0=1934848488` 的来源和含义；同时继续找能触发 `dispatchEncryptJNIFuncCall` 的真实事件，不再抓泛传输层。

## H.7 B56 f.run 追踪补充：触发链确认，`1934848488` 降级为场景参数待证（L1/L3）

脚本：

```text
tools/dump_normsg_f_run_B56.js
tools/dump_normsg_f_run_loader_B56.js
```

### H.7.1 已确认的触发链（L1）

继续冷启动官方包后，`ApplicationPackageManager.getPackageInfo` 栈再次确认三条大动脉：

```text
c$p.ad
  -> WCProbe$Info.n
  -> com.tencent.mm.plugin.normsg.u.z3
  -> ql3.s.z3
  -> w15.kg.toProtoBuf
  -> com.tencent.mm.modelbase.p2.B2
  -> com.tencent.mm.modelbase.t2.U8
  -> com.tencent.mm.network.w0.onTransact
```

```text
c$p.aa
  -> WCProbe$Info.f
  -> com.tencent.mm.plugin.normsg.u.uc
  -> ql3.s.uc / ql3.s.h
  -> w15.kg.toProtoBuf
  -> com.tencent.mm.modelbase.p2.B2
  -> com.tencent.mm.modelbase.t2.U8
  -> com.tencent.mm.network.w0.onTransact
```

```text
c$p.af
  -> WCProbe$Info.m
  -> com.tencent.mm.plugin.normsg.f.run
  -> java.util.concurrent.FutureTask.run
  -> yq5.l.run
  -> zq5.v.run
  -> ThreadPoolExecutor.runWorker
```

结论：

```text
z3 / uc = 已进入 toProtoBuf + network 的上报链。
f.run / WCProbe.m / c$p.af = 自然冷启动后触发的 checkSoftType 链。
```

### H.7.2 Java 层 method replacement 未接管 f.run（L1）

`dump_normsg_f_run_loader_B56.js` 做了两层：

```text
Java.enumerateClassLoaders()
java.lang.ClassLoader.loadClass(name, resolve)
```

并尝试在每个 loader 内 hook：

```text
com.tencent.mm.plugin.normsg.f.run
com.tencent.mm.normsg.WCProbe$Info.m
com.tencent.mm.normsg.c$p.ae / c$p.af
```

现象：

```text
PM hook 栈能看到 c$p.af -> WCProbe.m -> plugin.normsg.f.run。
但 Java method replacement 没有打印 plugin.normsg.f.run IN / f.run.this field。
```

判断：

```text
继续重复 Java.use / ClassLoader hook 价值下降。
下一步应转到 native/JNI 边界，或静态定位 f.run Unknown Source:44。
```

不要把这个失败解释成“链不存在”。链已由 PM hook 栈确认；失败点只是 Java replacement 没接管实际执行点。

### H.7.3 `1934848488` 不能直接归因成异常码（L3 降级）

`1934848488` 十六进制：

```text
0x735371e8
```

在历史日志里同一数值还反复出现在业务缓存 key / 推荐卡场景：

```text
mmbizresortbuffer_1934848488_<timestamp>
tlcolumn_hottopic_1003_1934848488_<timestamp>_subcolumnindex0
```

因此当前不能把 `arg0=1934848488` 直接解释为：

```text
封禁码
异常码
反作弊命中码
```

更保守的表述是：

```text
WCProbe.m / c$p.ae / c$p.af 的场景参数或业务 type，含义待证。
```

下一步只做两件事：

```text
1. native/JNI hook WCProbe.m / c$p.ae / c$p.af，拿真实入参和返回。
2. 静态定位 plugin.normsg.f.run 的 Unknown Source:44，确认 1934848488 是常量、字段还是上层传参。
```

## H.8 B56 静态索引启动：raw dex fallback（L1/L2 工具链）

用户要求把"全量读混淆"变成索引方法，而不是人肉通读。已更新：

```text
.cursor/skills/guard-antiban_防封官/SKILL.md
tools/build_b56_static_index.py
tools/b56_static_index/
```

### H.8.1 APK 获取与索引方式

本机 `jadx_8071_out/classes9.dex.jadx` 只是 430B jadx 项目文件，指向的 cache 已不存在；PATH 无 `jadx/apktool`。

改走 raw APK/dex fallback：

```text
adb shell cp /data/app/.../base.apk /sdcard/Download/base_8071.apk
split -b 16m /sdcard/Download/base_8071.apk /sdcard/Download/base_8071.apk.part
逐块 pull -> 本地合并 -> zip 校验
```

直接 pull 255MB APK 会截断，分块后成功：

```text
APK size = 255119374
zip valid = True
dex count = 16
```

索引产物：

```text
tools/b56_static_index/base_8071.apk
tools/b56_static_index/dex_classes.tsv
tools/b56_static_index/dex_strings.tsv
tools/b56_static_index/strings.tsv
tools/b56_static_index/constants.tsv
tools/b56_static_index/invokes.tsv
tools/b56_static_index/anchors.md
```

### H.8.2 第一轮 raw-dex 命中

```text
dex_classes = 361886
dex_strings = 163
constants(1934848488 / 0x735371e8) = 0
```

关键类 / 字符串位置：

```text
com.tencent.mm.normsg.WCProbe$Info       classes11.dex / classes12.dex
com.tencent.mm.normsg.c$p                classes12.dex
com.tencent.mm.plugin.normsg.NormsgDataService  classes12.dex
com.tencent.mm.plugin.normsg.f           classes12.dex
com.tencent.mm.plugin.normsg.u           classes12.dex（另在 classes4/5/9 有引用）
dispatchEncryptJNIFuncCall               classes12.dex
wechat.shell.TEST_WCPROBE                classes12.dex
Normsg_AED / Normsg_AED_Errors           classes12.dex
ClickBotCheckHelper listener             classes11.dex
ScreenRecordDetector$ScreenRecordInfo    classes.dex
getWCProbeWaid                           classes15.dex
```

解释：

```text
classes12.dex 是 normsg 主体静态切片入口。
dispatchEncryptJNIFuncCall 和 TEST_WCPROBE 同在 classes12.dex，后续优先围绕这里找触发器。
1934848488 没有以普通 dex int/hex 常量直接出现，继续降级为场景参数/业务 type 待证。
```

### H.8.3 下一步索引切片

当前 fallback 只能给 dex / offset / 字符串 / 类描述符，不能给 Java 行号和 invoke call graph。

下一步：

```text
1. 补 JADX 反编译 classes12.dex，生成 methods/native_methods/invokes。
2. 围绕 classes12.dex 切：
   - WCProbe$Info
   - c$p
   - plugin.normsg.f
   - plugin.normsg.u
   - NormsgDataService
   - dispatchEncryptJNIFuncCall
   - wechat.shell.TEST_WCPROBE
3. 用静态切片反推动态 hook 点：native/JNI hook 或测试 action 触发 dispatchEncrypt。
```
