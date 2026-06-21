'use strict';
// B35 签名轴动态: 微信运行期是否读"自己"的 APK 签名?
//   GET_SIGNATURES=0x40(64) / GET_SIGNING_CERTIFICATES=0x8000000
// 命中自身包(com.tencent.mm) + 打调用栈 -> 判 是否签名自校验 -> 官替版(我方签名)是否暴露。
// 配合: frida -U -f com.tencent.mm -l tools/dump_sig_check.js (spawn 抓启动期自校验)
function log(t) { console.log('[SIG] ' + t); }
var Exc, LogC;
function stack() { try { return LogC.getStackTraceString(Exc.$new()); } catch (e) { return '<stk err ' + e + '>'; } }
Java.perform(function () {
  Exc = Java.use('java.lang.Exception');
  LogC = Java.use('android.util.Log');
  var OWN = 'com.tencent.mm';
  try {
    var APM = Java.use('android.app.ApplicationPackageManager');
    APM.getPackageInfo.overload('java.lang.String', 'int').implementation = function (p, f) {
      var r = this.getPackageInfo(p, f);
      var s = (f & 64) !== 0, c = (f & 0x8000000) !== 0;
      if (s || c) {
        log('getPackageInfo pkg=' + p + ' flags=0x' + (f >>> 0).toString(16) + (p === OWN ? '  <== 自身签名!' : '') + (s ? ' GET_SIGNATURES' : '') + (c ? ' GET_SIGNING_CERTIFICATES' : ''));
        if (p === OWN) log('stack:\n' + stack());
      }
      return r;
    };
    log('hooked getPackageInfo');
  } catch (e) { log('PM ERR ' + e); }
  try {
    var APM2 = Java.use('android.app.ApplicationPackageManager');
    APM2.getPackageArchiveInfo.implementation = function (path, f) {
      var r = this.getPackageArchiveInfo(path, f);
      if (((f & 64) !== 0) || ((f & 0x8000000) !== 0)) log('getPackageArchiveInfo path=' + path + ' flags=0x' + (f >>> 0).toString(16));
      return r;
    };
    log('hooked getPackageArchiveInfo');
  } catch (e) { log('PAI ERR ' + e); }
  try {
    var Sig = Java.use('android.content.pm.Signature');
    Sig.toByteArray.implementation = function () { var r = this.toByteArray(); log('Signature.toByteArray len=' + (r ? r.length : 0) + '\n' + stack()); return r; };
    log('hooked Signature.toByteArray');
  } catch (e) { log('Sig ERR ' + e); }
  log('=== sig probe armed ===');
});
