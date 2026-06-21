'use strict';
// 防封官 B36 定论：k18(签名)是 Java 源还是 native 源。hook getPackageInfo(GET_SIGNATURES) 打 Java 调用栈，看 normsg/z3 是不是调用者。
// 用法：frida -U -f com.tencent.mm -l tools/trace_sig_caller.js   （spawn 冷启抓启动期签名读 + 调用栈）
function log(t) { console.log('[SIGCALL] ' + t); }
var n = 0;
Java.perform(function () {
  var Log = Java.use('android.util.Log');
  var Thr = Java.use('java.lang.Throwable');
  try {
    var PM = Java.use('android.app.ApplicationPackageManager');
    PM.getPackageInfo.overload('java.lang.String', 'int').implementation = function (p, f) {
      var r = this.getPackageInfo(p, f);
      if (((f & 64) !== 0 || (f & 0x8000000) !== 0) && p === 'com.tencent.mm' && n < 6) {
        n++;
        var st = Log.getStackTraceString(Thr.$new());
        log('#' + n + ' getPackageInfo(SIG) ' + p + ' flags=' + f + ' 调用栈:\n' + st);
      }
      return r;
    };
    log('hooked getPackageInfo + 调用栈');
  } catch (e) { log('PM err ' + e); }
  // 同时也看 Signature.toByteArray 谁调（normsg 可能直接读 Signature）
  try {
    var Sig = Java.use('android.content.pm.Signature');
    var m = 0;
    Sig.toByteArray.implementation = function () {
      var r = this.toByteArray();
      if (m < 4) { m++; log('#sig.toByteArray 调用栈:\n' + Log.getStackTraceString(Thr.$new())); }
      return r;
    };
    log('hooked Signature.toByteArray');
  } catch (e) { log('Sig err ' + e); }
  log('=== sig caller probe armed ===');
});
