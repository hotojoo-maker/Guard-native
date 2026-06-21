'use strict';
// B29 明文研究：hook normsg 采集器拿明文（z3 设备指纹 / Y8 OAID）+ dispatchEncryptJNIFuncCall 明文入/密文出。warm-attach。
function log(t) { console.log('[PT] ' + t); }
function hex(b) {
  if (!b) return 'null';
  try { var n = Math.min(b.length, 80); var s = ''; for (var i = 0; i < n; i++) { var x = (b[i] & 0xff).toString(16); s += (x.length < 2 ? '0' : '') + x; } return 'len=' + b.length + ' ' + s + (b.length > 80 ? '...' : ''); }
  catch (e) { return '<hex e:' + e + '>'; }
}
Java.perform(function () {
  try {
    var U = Java.use('com.tencent.mm.plugin.normsg.u');
    U.z3.overload('int').implementation = function (i) { var r = this.z3(i); log('z3(' + i + ') 设备指纹明文 => ' + r); return r; };
    log('hooked u.z3');
  } catch (e) { log('z3 ERR ' + e); }
  try {
    var U2 = Java.use('com.tencent.mm.plugin.normsg.u');
    U2.Y8.implementation = function () { var r = this.Y8(); log('Y8 OAID => ' + r); return r; };
    log('hooked u.Y8');
  } catch (e) { log('Y8 ERR ' + e); }
  try {
    var W = Java.use('com.tencent.mm.normsg.WCProbe$Info');
    W.dispatchEncryptJNIFuncCall.implementation = function (b) {
      log('dispatchEncrypt 明文入(protobuf) ' + hex(b));
      var r = this.dispatchEncryptJNIFuncCall(b);
      log('dispatchEncrypt 密文出 ' + hex(r));
      return r;
    };
    log('hooked dispatchEncryptJNIFuncCall');
  } catch (e) { log('dispatch ERR ' + e); }
  try {
    var W2 = Java.use('com.tencent.mm.normsg.WCProbe$Info');
    W2.i.implementation = function (a, b, c, d, e) { var r = this.i(a, b, c, d, e); log('WCProbe.i 设备信息入: i=' + a + ' s=' + b + ' t=' + c + ' et=' + d + ' op=' + e + ' -> 密文 ' + hex(r)); return r; };
    log('hooked WCProbe.i');
  } catch (e) { log('W.i ERR ' + e); }
  log('=== plaintext probe armed ===');
});
