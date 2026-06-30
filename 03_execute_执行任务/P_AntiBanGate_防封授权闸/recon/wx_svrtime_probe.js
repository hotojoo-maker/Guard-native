// WX_SVRTIME recon L1 探针 — 验 jy0.hd (MicroMsg.TimeHelper) 是否=微信真服务器授时
// 用法: frida -U -p <wechat-main-pid> -l wx_svrtime_probe.js
// 验: ① hd.c()=服务器时间(对比设备墙钟差) ② c() 随 elapsedRealtime 持续外推(每5s前进)
//     ③ hd.g(long) 被 newsync 喂数(实时刷新、非消息驱动) ④ hd.b()=MMKV 地板(重启安全)
Java.perform(function () {
  try {
    var hd = Java.use('jy0.hd');
    var Sys = Java.use('java.lang.System');
    function snap(tag) {
      var dev = Sys.currentTimeMillis();
      var c, b;
      try { c = hd.c(); } catch (e) { c = -1; }
      try { b = hd.b(); } catch (e) { b = -1; }
      var diff = (c > 0) ? (c - dev) : 0;
      console.log('[SVRTIME ' + tag + '] hd.c=' + c + ' hd.b=' + b
        + ' dev=' + dev + ' c-dev(ms)=' + diff
        + ' iso_c=' + (c > 0 ? new Date(c).toISOString() : 'n/a'));
    }
    snap('init');
    try {
      hd.g.overload('long').implementation = function (t) {
        console.log('[SVRTIME feed] hd.g(' + t + ') iso=' + new Date(t).toISOString()
          + '  <-- newsync 喂数(实时刷新证据)');
        return this.g(t);
      };
      console.log('[SVRTIME] hooked hd.g(long) — 等 newsync 喂数');
    } catch (e) { console.log('[SVRTIME] hook g fail: ' + e); }
    var n = 0;
    var iv = setInterval(function () {
      Java.perform(function () { snap('t' + (++n) + '(+' + (n * 5) + 's)'); });
      if (n >= 6) { clearInterval(iv); console.log('[SVRTIME] done (30s)'); }
    }, 5000);
  } catch (e) {
    console.log('[SVRTIME] FATAL (类不存在?): ' + e);
  }
});
