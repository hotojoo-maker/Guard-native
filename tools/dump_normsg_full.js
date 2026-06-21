'use strict';
// 防封官 B36：normsg 全链探针。挂原生采集触发 c$p.aa/ad/af/ae(返回 byte[]=采集数据) + 签名读取 + z3 + dispatchEncrypt。
// 用法：frida -U -f com.tencent.mm -l tools/dump_normsg_full.js   （spawn 冷启抓登录采集）
function log(t) { console.log('[NS] ' + new Date().toISOString().substr(11, 12) + ' ' + t); }
function ascii(b) {
  if (!b) { return 'null'; }
  var n = Math.min(b.length, 400);
  var s = '';
  for (var i = 0; i < n; i++) { var c = b[i] & 0xff; s += (c >= 32 && c < 127) ? String.fromCharCode(c) : '.'; }
  return 'len=' + b.length + ' ' + s + (b.length > 400 ? '...' : '');
}
var cnt = {};
function cap(k) { cnt[k] = (cnt[k] || 0) + 1; return cnt[k] <= 8; }

Java.perform(function () {
  // 1) 原生采集触发 c$p.aa/ad/af/ae（返回 byte[] = 实际采集/上报数据）
  try {
    var P = Java.use('com.tencent.mm.normsg.c$p');
    ['aa', 'ad', 'af', 'ae'].forEach(function (m) {
      try {
        P[m].overloads.forEach(function (ov) {
          ov.implementation = function () {
            var r = ov.apply(this, arguments);
            if (cap('c$p.' + m)) { log('c$p.' + m + '() ret ' + ascii(r)); }
            return r;
          };
        });
      } catch (e) { log('hook c$p.' + m + ' err ' + e); }
    });
    log('hooked c$p aa/ad/af/ae');
  } catch (e) { log('c$p ERR ' + e); }

  // 2) 签名读取 getPackageInfo(GET_SIGNATURES / GET_SIGNING_CERTIFICATES)
  try {
    var PM = Java.use('android.app.ApplicationPackageManager');
    PM.getPackageInfo.overload('java.lang.String', 'int').implementation = function (p, f) {
      var r = this.getPackageInfo(p, f);
      if (((f & 64) !== 0 || (f & 0x8000000) !== 0) && cap('sig')) { log('getPackageInfo SIG pkg=' + p + ' flags=' + f); }
      return r;
    };
  } catch (e) { log('PM err ' + e); }

  // 3) z3 设备指纹 + dispatchEncrypt 上报体
  try {
    Java.use('com.tencent.mm.plugin.normsg.u').z3.overload('int').implementation = function (i) {
      var r = this.z3(i); log('z3(' + i + ') 设备指纹 => ' + r); return r;
    };
  } catch (e) { log('z3 hook err ' + e); }
  try {
    var W = Java.use('com.tencent.mm.normsg.WCProbe$Info');
    W.dispatchEncryptJNIFuncCall.implementation = function (b) {
      var r = this.dispatchEncryptJNIFuncCall(b);
      if (cap('disp')) { log('dispatchEncrypt 明文入 ' + ascii(b)); }
      return r;
    };
  } catch (e) { log('disp hook err ' + e); }

  log('=== normsg full probe armed ===');
});
