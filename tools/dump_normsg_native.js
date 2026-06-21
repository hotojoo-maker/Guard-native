'use strict';
// B29 native 探针 v4（温和 spawn）：不 hook 加载器，只挂 libc property/lstat + 签名。setInterval 轮询 normsg 加载。
function log(t) { console.log('[NAT] ' + t); }
var seen = {};
function once(k) { if (seen[k]) return false; seen[k] = 1; return true; }
var ngB = null, ngE = null;
function ensureNg() {
  if (ngB) return;
  var m = Process.findModuleByName('libwechatnormsg.so');
  if (m) { ngB = m.base; ngE = m.base.add(m.size); log('normsg loaded @' + ngB + ' size=' + m.size); }
}
function inNg(ra) { return ngB && ra.compare(ngB) >= 0 && ra.compare(ngE) < 0; }
function exp(lib, fn) { try { var m = Process.findModuleByName(lib); return m ? m.findExportByName(fn) : null; } catch (e) { return null; } }

var iv = setInterval(function () { ensureNg(); if (ngB) clearInterval(iv); }, 200);

var pg = exp('libc.so', '__system_property_get');
if (pg) Interceptor.attach(pg, {
  onEnter: function (a) { try { this.s = a[0].readCString(); } catch (e) { this.s = '?'; } this.ra = this.returnAddress; },
  onLeave: function (r) { if (!ngB) return; if (inNg(this.ra) && once('P|' + this.s)) log('prop ' + this.s); }
});
var ls = exp('libc.so', 'lstat');
if (ls) Interceptor.attach(ls, {
  onEnter: function (a) { try { this.s = a[0].readCString(); } catch (e) { this.s = '?'; } this.ra = this.returnAddress; },
  onLeave: function (r) { if (!ngB) return; if (inNg(this.ra) && once('S|' + this.s)) log('lstat ' + this.s); }
});

Java.perform(function () {
  try {
    var PM = Java.use('android.app.ApplicationPackageManager');
    PM.getPackageInfo.overload('java.lang.String', 'int').implementation = function (p, f) {
      var r = this.getPackageInfo(p, f);
      if ((f & 64) !== 0 || (f & 0x8000000) !== 0) log('getPackageInfo SIG pkg=' + p + ' flags=' + f);
      return r;
    };
  } catch (e) { log('PM ERR ' + e); }
});
log('=== gentle spawn probe armed (no loader hooks) ===');
