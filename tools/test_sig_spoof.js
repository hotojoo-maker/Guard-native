'use strict';
// 防封官 B36 ②：100% 定论 Lv1 是否够。只 spoof normsg 的 getPackageInfo 签名（按调用栈过滤，不碰 Tinker/GMS/本模块），看 z3 的 k18 是否跟着变。
// 变 = k18 走 Java getPackageInfo（Lv1 够）；不变(仍 18c867f0) = native 直读签名（Lv1 不够）。
// 用法：frida -U -f com.tencent.mm -l tools/test_sig_spoof.js
function log(t) { console.log('[SPOOF] ' + t); }
Java.perform(function () {
  var Log = Java.use('android.util.Log');
  var Thr = Java.use('java.lang.Throwable');
  var Sig = Java.use('android.content.pm.Signature');
  var PM = Java.use('android.app.ApplicationPackageManager');
  var n = 0;
  PM.getPackageInfo.overload('java.lang.String', 'int').implementation = function (p, f) {
    var r = this.getPackageInfo(p, f);
    if ((f & 64) !== 0 && p === 'com.tencent.mm') {
      var st = Log.getStackTraceString(Thr.$new());
      if (st.indexOf('normsg') >= 0) { // 只动 normsg 那一路
        try {
          var sigs = r.signatures.value;
          if (sigs && sigs.length > 0) {
            var b = sigs[0].toByteArray();
            b[0] = (b[0] ^ 0xFF); // 翻转第一字节
            sigs[0] = Sig.$new(b);
            r.signatures.value = sigs;
            if (n < 4) { n++; log('SPOOFED normsg getPackageInfo 签名（翻转 byte0）'); }
          }
        } catch (e) { log('spoof err ' + e); }
      }
    }
    return r;
  };
  Java.use('com.tencent.mm.plugin.normsg.u').z3.overload('int').implementation = function (i) {
    var r = this.z3(i);
    var m = ('' + r).match(/<k18>([^<]*)<\/k18>/);
    log('z3(' + i + ') k18=' + (m ? m[1] : '?') + '  (官方=18c867f0717aa67b2ab7347505ba07ed; 变了=Lv1够)');
    return r;
  };
  // 9s 后主动调 z3(0) 触发采集（spoof 已生效），强制比对 k18
  setTimeout(function () {
    Java.perform(function () {
      try {
        Java.choose('com.tencent.mm.plugin.normsg.u', {
          onMatch: function (o) { try { log('主动调 z3(0) 触发...'); o.z3(0); } catch (e) { log('active z3 err ' + e); } },
          onComplete: function () { }
        });
      } catch (e) { log('choose err ' + e); }
    });
  }, 42000);
  log('armed: 只 spoof normsg 的签名读 + 42s 后主动调 z3（等 normsg 初始化），看 k18 变不变');
});
