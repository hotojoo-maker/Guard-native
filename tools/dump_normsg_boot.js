'use strict';
// B35 启动期捕获(spawn 冷启): 一次拿两样——
//  1) u.S6() (ql3.j XOR 解密器) 密文入 -> 明文出 = 混淆隐藏串原文
//  2) normsg 模块内 libc __system_property_get / lstat 探测名单 = 真实环境检测面
// 配合: frida -U -f com.tencent.mm -l tools/dump_normsg_boot.js  (frida17 自动 resume)
function log(t) { console.log('[BOOT] ' + t); }
var hits = 0, s6done = false, seen = {};
function once(k) { if (seen[k]) return false; seen[k] = 1; return true; }

// ---------- native: normsg 模块内 property/lstat ----------
var ngB = null, ngE = null;
function ensureNg() { if (ngB) return; var m = Process.findModuleByName('libwechatnormsg.so'); if (m) { ngB = m.base; ngE = m.base.add(m.size); log('normsg @' + ngB + ' size=' + m.size); } }
function inNg(ra) { return ngB && ra.compare(ngB) >= 0 && ra.compare(ngE) < 0; }
function expf(lib, fn) { try { var m = Process.findModuleByName(lib); return m ? m.findExportByName(fn) : null; } catch (e) { return null; } }
var ivNg = setInterval(function () { ensureNg(); if (ngB) clearInterval(ivNg); }, 120);
var pg = expf('libc.so', '__system_property_get');
if (pg) Interceptor.attach(pg, {
  onEnter: function (a) { try { this.s = a[0].readCString(); } catch (e) { this.s = '?'; } this.ra = this.returnAddress; },
  onLeave: function (r) { if (inNg(this.ra) && once('P|' + this.s)) log('prop ' + this.s); }
});
var ls = expf('libc.so', 'lstat');
if (ls) Interceptor.attach(ls, {
  onEnter: function (a) { try { this.s = a[0].readCString(); } catch (e) { this.s = '?'; } this.ra = this.returnAddress; },
  onLeave: function (r) { if (inNg(this.ra) && once('S|' + this.s)) log('lstat ' + this.s); }
});

// ---------- java: S6 解密器 ----------
function tryS6() {
  if (s6done) return true;
  try {
    var U = Java.use('com.tencent.mm.plugin.normsg.u');
    U.S6.implementation = function (s) { var r = this.S6(s); hits++; log('S6 #' + hits + '  in=' + JSON.stringify(s) + '  =>  ' + JSON.stringify(r)); return r; };
    s6done = true; log('hooked u.S6'); return true;
  } catch (e) { return false; }
}
Java.perform(function () {
  if (!tryS6()) { var n = 0, j = setInterval(function () { n++; if (tryS6() || n > 400) clearInterval(j); }, 50); }
  log('=== boot probe armed (S6 + normsg native) ===');
});
