'use strict';
// 防封官 B36：在 :push 进程抓请求明文。hook MMProtocalJni.pack(主力对称) + UtilsJni 非对称 + native mmcrypto::AesGcmEncrypt(传输层).
// 用法：frida -U -p <pidof com.tencent.mm:push> -l tools/dump_req_plaintext.js
function log(t) { console.log('[REQ] ' + new Date().toISOString().substr(11, 12) + ' ' + t); }
function bA(a) {
  if (!a) { return 'null'; }
  var n = Math.min(a.length, 360);
  var s = '';
  for (var i = 0; i < n; i++) { var c = a[i] & 0xff; s += (c >= 32 && c < 127) ? String.fromCharCode(c) : '.'; }
  return 'len=' + a.length + ' ' + s + (a.length > 360 ? '...' : '');
}
var cnt = {};
function cap(k) { cnt[k] = (cnt[k] || 0) + 1; return cnt[k] <= 10; }

Java.perform(function () {
  // 主力：业务层对称 pack
  try {
    var P = Java.use('com.tencent.mm.protocal.MMProtocalJni');
    ['pack', 'packHybrid', 'packHybridEcdh', 'aesEncrypt'].forEach(function (m) {
      try {
        if (!P[m]) { return; }
        P[m].overloads.forEach(function (ov) {
          ov.implementation = function () {
            var r = ov.apply(this, arguments);
            try { for (var i = 0; i < arguments.length; i++) { var a = arguments[i]; if (a && a.length !== undefined && a.length > 4 && cap(m)) { log(m + ' arg' + i + ' ' + bA(a)); } } } catch (e) { }
            return r;
          };
        });
        log('hooked MMProtocalJni.' + m);
      } catch (e) { log('hook ' + m + ' err ' + e); }
    });
  } catch (e) { log('MMProtocalJni err ' + e); }

  // 非对称：UtilsJni
  try {
    var U = Java.use('com.tencent.mm.jni.utils.UtilsJni');
    ['AesGcmEncryptWithCompress', 'HybridEcdhEncrypt', 'AesGcmEncrypt'].forEach(function (m) {
      try {
        if (!U[m]) { return; }
        U[m].overloads.forEach(function (ov) {
          ov.implementation = function () {
            var r = ov.apply(this, arguments);
            try { var a0 = arguments[0]; if (a0 && a0.length !== undefined && cap('U.' + m)) { log('UtilsJni.' + m + ' in ' + bA(a0)); } } catch (e) { }
            return r;
          };
        });
        log('hooked UtilsJni.' + m);
      } catch (e) { }
    });
  } catch (e) { log('UtilsJni err ' + e); }

  log('=== req plaintext probe armed (:push) ===');
});
