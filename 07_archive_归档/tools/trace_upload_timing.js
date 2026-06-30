'use strict';
// 防封官 B36 方向2：测 normsg 上报时机/频率。hook dispatchEncryptJNIFuncCall（AES 上报点）+ 时间戳。
// 用法：frida -U -p <pidof com.tencent.mm> -l tools/trace_upload_timing.js   然后静置观察 ~75s
function log(t) { console.log('[UP] ' + new Date().toISOString().substr(11, 12) + ' ' + t); }
var n = 0;
Java.perform(function () {
  try {
    var W = Java.use('com.tencent.mm.normsg.WCProbe$Info');
    W.dispatchEncryptJNIFuncCall.implementation = function (b) {
      n++;
      var inlen = (b ? b.length : -1);
      var r = this.dispatchEncryptJNIFuncCall(b);
      log('#' + n + ' dispatchEncrypt 明文入len=' + inlen + ' 密文出len=' + (r ? r.length : -1));
      return r;
    };
    log('hooked dispatchEncryptJNIFuncCall（静置等待上报…）');
  } catch (e) { log('hook ERR ' + e); }
});
