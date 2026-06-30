2026-06-30 19:xx 建卡；粗扫防封_反检测线热更新支线；UpdateGuard(B7)仅红点、通道未掃；派 AI 出 D3（API 限 fallback 本卡）
2026-06-30 20:xx brief.md 落（jadx 8.0.71 实读 L2）：libcso C2(ip.g.a)/C1(z.f176124s)；Tinker C1(p53.j.b)/C4(d0.j)/C5(d0.d)；★发现官方自带闸 g45.c.f246162e「blocked by assist」可借；A2 上则必掐 Tinker；LSPatch 共存零副作用论证在案
2026-06-30 22:xx 落码 HotUpdateFreeze.java + AppConfig.isHotFreezeEnabled + ModuleMain 接入；编译装机（小米9 609b4b18 官方包 8.0.71）
2026-06-30 22:04 L1 OBSERVE 冒烟（warm WeChat）：4 钩全 installed、零崩；z.f176124s / g45.c.f246162e 两字段 NoSuchFieldError（Tinker classloader 分裂）→ 已去掉走方法钩；warm 态四通道均未 fired（缓存/节流）
2026-06-30 22:06 L1 OBSERVE 新装（pm clear 后冷启）：★Tinker 三钩全 fired = p53.j.b→m53.d0.j→m53.d0.d（真拉补丁+装），证「新装狂触发」；libcso preloadAllInternal 经 cso-p 线程真跑（预载 libbspatch/libhpatchz），但 ip.g.a 未 fired（预载走另一入口）；libcso 兼正常 SO 加载→不可整条冻，ip.g.a 改 observe-only
2026-06-30 22:19 ★L1 FREEZE 新装：[HUF] tinker p53.j.b → blocked（checkAvailableUpdate 源头断），下游 m53.d0.j/d 未 fired，微信正常登录用 = Tinker 冻结 L1 拦截成立。证据 logs/huf_updatebtn_live_20260630.txt
2026-06-30 22:19 ★缺口确认：full-APK 整包更新按钮 = 第3条线，独立于 Tinker/libcso；走 com.tencent.mm.plugin.downloader.model.g0/j0（"后台下载"）；APK URL 匹配 a33.y5 正则 dlied[4|5].myapp.com/.../*.apk；当前未拦（B7 仅红点）→ 待精确定位 client-version 查更/下载触发点
2026-06-30 22:27 ★L1 第3条线拦截成立：定位 fl4.o（MicroMsg.Updater 控制器，B7 红点同类）—— Wg(ZZZ)=checkMMdiffUpdatePatchPkgVersion 查更入口、Bg(Context,String)=checkAndShowInstallPatchDialog 装包弹窗；两处 freeze no-op。装机 L1：[HUF] fullapk fl4.o.Wg → blocked，用户实测点「检查更新」不再后台下载。证据 logs/huf_fullapk_blocked_20260630.txt
2026-06-30 22:3x 写入 HOOKMAP.md B7 行 + docs/HOOK_MAP_8071_AUTHORITATIVE.md §14（两层：UpdateGuard 红点 + HotUpdateFreeze 三通道；含 L1 证据/约束/诚实口径）
2026-06-30 22:31 ★生产化：去掉 FORCE_FREEZE，AppConfig.isHotFreezeEnabled 默认改 true（一劳永逸锁版本）；重编重装 L1 复验 [HUF] install begin (mode=FREEZE) + 6 钩全 installed + 零崩（logs/huf_default_on_20260630.txt）。文档 FORCE_FREEZE 注脚同步移除。当前装机为官方包测试号；发版前对 official+coexist 两 flavor 各跑一次回归
