'use strict';
// 防封官 B36 方向2+：冷启登录抓 normsg 上报"重要信息"——完整明文上报体(protobuf) + 可读串 + z3 设备指纹。
// 用法：frida -U -f com.tencent.mm -l tools/dump_login_coldstart.js   （spawn 冷启；静置到登录完成再 Ctrl+C）
function log(t) { console.log('[LOGIN] ' + new Date().toISOString().substr(11, 12) + ' ' + t); }
function hexFull(b) {
  if (!b) { return 'null'; }
  var s = '';
  for (var i = 0; i < b.length; i++) { var x = (b[i] & 0xff).toString(16); s += (x.length < 2 ? '0' : '') + x; }
  return 'len=' + b.length + ' ' + s;
}
function ascii(b) {
  if (!b) { return ''; }
  var s = '';
  for (var i = 0; i < b.length; i++) { var c = b[i] & 0xff; s += (c >= 32 && c < 127) ? String.fromCharCode(c) : '.'; }
  return s;
}
var n = 0;
Java.perform(function () {
  try {
    var W = Java.use('com.tencent.mm.normsg.WCProbe$Info');
    W.dispatchEncryptJNIFuncCall.implementation = function (b) {
      n++;
      log('#' + n + ' 明文入HEX ' + hexFull(b));
      log('#' + n + ' 明文入ASCII ' + ascii(b));
      var r = this.dispatchEncryptJNIFuncCall(b);
      log('#' + n + ' 密文出 len=' + (r ? r.length : -1));
      return r;
    };
    log('hooked dispatchEncryptJNIFuncCall');
  } catch (e) { log('dispatch ERR ' + e); }
  try {
    var U = Java.use('com.tencent.mm.plugin.normsg.u');
    U.z3.overload('int').implementation = function (i) { var r = this.z3(i); log('z3(' + i + ') 设备指纹 => ' + r); return r; };
  } catch (e) { log('z3 ERR ' + e); }
  log('=== login coldstart probe armed (spawn) ===');
});
