'use strict';
// B56 防封官：ClassLoader 感知版 normsg 大动脉探针。
// 目标：解决 Tinker / 多 ClassLoader 下 Java.use 命中但实际调用不进 hook 的问题。
// 用法：frida -U -f com.tencent.mm -l tools/dump_normsg_artery_loader_B56.js

function log(t) { console.log('[ARTERY2] ' + new Date().toISOString().substr(11, 12) + ' ' + t); }

var cnt = {};
function cap(k, n) { n = n || 12; cnt[k] = (cnt[k] || 0) + 1; return cnt[k] <= n; }

function bytesPreview(a) {
  if (!a) return 'null';
  var len;
  try { len = a.length; } catch (e) { return String(a); }
  if (len === undefined) return String(a);
  var n = Math.min(len, 180);
  var s = '';
  try {
    for (var i = 0; i < n; i++) {
      var c = a[i] & 0xff;
      s += (c >= 32 && c < 127) ? String.fromCharCode(c) : '.';
    }
  } catch (e2) {
    return 'len=' + len + ' <preview err>';
  }
  return 'len=' + len + ' ' + s + (len > n ? '...' : '');
}

function val(v) {
  if (v === null || v === undefined) return String(v);
  try {
    if (v.length !== undefined && typeof v !== 'string') return bytesPreview(v);
  } catch (e) {}
  try {
    var cls = v.getClass ? v.getClass().getName() : null;
    if (cls) return '<' + cls + '> ' + String(v).slice(0, 160);
  } catch (e2) {}
  try { return String(v).slice(0, 180); } catch (e3) { return '<toString err>'; }
}

function argsPreview(args) {
  var out = [];
  for (var i = 0; i < args.length; i++) out.push('arg' + i + '=' + val(args[i]));
  return out.join(' | ');
}

var hookedKeys = {};
function loaderName(loader) {
  try { return String(loader); } catch (e) { return '<loader>'; }
}

function hookInFactory(factory, className, methods, tag, loaderLabel) {
  var C;
  try { C = factory.use(className); } catch (e) { return false; }
  methods.forEach(function (m) {
    try {
      if (!C[m]) return;
      C[m].overloads.forEach(function (ov, idx) {
        var key = loaderLabel + '|' + className + '.' + m + '#' + idx;
        if (hookedKeys[key]) return;
        hookedKeys[key] = true;
        ov.implementation = function () {
          var shortKey = tag + '.' + m;
          if (cap(shortKey + '.in', 16)) log(shortKey + ' IN loader=' + loaderLabel + ' ' + argsPreview(arguments));
          var r = ov.apply(this, arguments);
          if (cap(shortKey + '.out', 16)) log(shortKey + ' OUT loader=' + loaderLabel + ' ' + val(r));
          return r;
        };
      });
      log('hooked ' + tag + '.' + m + ' loader=' + loaderLabel + ' overloads=' + C[m].overloads.length);
    } catch (e2) {
      log('hook err ' + tag + '.' + m + ' loader=' + loaderLabel + ' ' + e2);
    }
  });
  return true;
}

function scanLoaders() {
  var targets = [
    ['com.tencent.mm.normsg.c$p', ['aa', 'ad', 'af', 'ae'], 'c$p'],
    ['com.tencent.mm.normsg.WCProbe$Info', ['n', 'f', 'm', 'h', 'i', 'j', 'c', 'g'], 'WCProbe'],
    ['com.tencent.mm.plugin.normsg.u', ['z3', 'Y8', 'uc'], 'u'],
    ['w15.kg', ['toProtoBuf'], 'kg'],
    ['com.tencent.mm.modelbase.p2', ['B2'], 'p2'],
    ['com.tencent.mm.modelbase.t2', ['U8'], 't2'],
    ['com.tencent.mm.network.w0', ['onTransact'], 'w0']
  ];

  try {
    Java.enumerateClassLoaders({
      onMatch: function (loader) {
        var label = loaderName(loader).replace(/\s+/g, ' ').slice(0, 120);
        var factory = Java.ClassFactory.get(loader);
        targets.forEach(function (t) { hookInFactory(factory, t[0], t[1], t[2], label); });
      },
      onComplete: function () {}
    });
  } catch (e) {
    log('enumerate loaders err ' + e);
  }
}

Java.perform(function () {
  // Framework class: default loader is enough.
  try {
    var PM = Java.use('android.app.ApplicationPackageManager');
    PM.getPackageInfo.overload('java.lang.String', 'int').implementation = function (p, f) {
      var r = this.getPackageInfo(p, f);
      if (((f & 64) !== 0 || (f & 0x8000000) !== 0) && cap('sig', 24)) {
        log('SIG getPackageInfo pkg=' + p + ' flags=0x' + (f >>> 0).toString(16));
      }
      return r;
    };
    log('hooked PM.getPackageInfo');
  } catch (e) {
    log('PM hook err ' + e);
  }

  scanLoaders();
  var n = 0;
  var timer = setInterval(function () {
    n++;
    scanLoaders();
    if (n >= 40) clearInterval(timer);
  }, 500);

  log('=== loader-aware normsg artery probe armed ===');
});
