'use strict';
// 防封官 B36：抓 WeChat 业务层加密前的"请求明文"。Java 层 hook UtilsJni.AesGcm*/HybridEcdh*（libwechatmm 导出，已确认）。
// 用法：frida -U -p <main pidof com.tencent.mm> -l tools/dump_biz_plaintext.js
function log(t) { console.log('[BIZ] ' + new Date().toISOString().substr(11, 12) + ' ' + t); }
function bA(a) {
  if (!a) { return 'null'; }
  var n = Math.min(a.length, 360);
  var s = '';
  for (var i = 0; i < n; i++) { var c = a[i] & 0xff; s += (c >= 32 && c < 127) ? String.fromCharCode(c) : '.'; }
  return 'len=' + a.length + ' ' + s + (a.length > 360 ? '...' : '');
}
var cnt = {};
function cap(k) { cnt[k] = (cnt[k] || 0) + 1; return cnt[k] <= 12; }

Java.perform(function () {
  var cls = 'com.tencent.mm.jni.utils.UtilsJni';
  var U;
  try { U = Java.use(cls); } catch (e) { log('no ' + cls + ' (换进程?) ' + e); return; }
  ['AesGcmEncryptWithCompress', 'AesGcmEncrypt', 'AesGcmEncryptWithNonce', 'HybridEcdhEncrypt'].forEach(function (name) {
    try {
      if (!U[name]) { log('no method ' + name); return; }
      U[name].overloads.forEach(function (ov) {
        ov.implementation = function () {
          var r = ov.apply(this, arguments);
          try {
            var a0 = arguments[0];
            if (a0 && a0.length !== undefined && cap(name)) { log(name + ' 明文入 ' + bA(a0)); }
          } catch (e) { }
          return r;
        };
      });
      log('hooked ' + name + ' (' + U[name].overloads.length + ' overloads)');
    } catch (e) { log('hook ' + name + ' err ' + e); }
  });
  log('=== biz plaintext probe armed ===');
});
