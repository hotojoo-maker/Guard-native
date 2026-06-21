'use strict';
// B56 防封官：追 plugin.normsg.f.run 的触发来源与上报内容。
// 目标：解释 WCProbe.m(arg0=1934848488) / c$p.ae/af 的来源，不抓泛请求。
// 用法：frida -U -f com.tencent.mm -l tools/dump_normsg_f_run_B56.js

function log(t) { console.log('[FRUN] ' + new Date().toISOString().substr(11, 12) + ' ' + t); }

var Exc = null;
var LogC = null;
var B64 = null;
var cnt = {};

function cap(k, n) { n = n || 20; cnt[k] = (cnt[k] || 0) + 1; return cnt[k] <= n; }
function stack() { try { return LogC.getStackTraceString(Exc.$new()); } catch (e) { return '<stack err ' + e + '>'; } }
function isByteArray(x) { try { return x && x.length !== undefined && typeof x !== 'string'; } catch (e) { return false; } }

function hexBytes(b, max) {
  if (!b) return 'null';
  max = max || 512;
  var n = Math.min(b.length, max), s = '';
  try {
    for (var i = 0; i < n; i++) {
      var x = (b[i] & 0xff).toString(16);
      s += (x.length < 2 ? '0' : '') + x;
    }
  } catch (e) { return '<hex err>'; }
  return s + (b.length > n ? '...' : '');
}

function asciiBytes(b, max) {
  if (!b) return 'null';
  max = max || 256;
  var n = Math.min(b.length, max), s = '';
  try {
    for (var i = 0; i < n; i++) {
      var c = b[i] & 0xff;
      s += (c >= 32 && c < 127) ? String.fromCharCode(c) : '.';
    }
  } catch (e) { return '<ascii err>'; }
  return s + (b.length > n ? '...' : '');
}

function b64Bytes(b, max) {
  try {
    var n = Math.min(b.length, max || 512);
    var arr = [];
    for (var i = 0; i < n; i++) arr.push(b[i]);
    return B64.encodeToString(Java.array('byte', arr), 2);
  } catch (e) { return '<b64 err ' + e + '>'; }
}

function dumpBytes(label, b) {
  log(label + ' len=' + b.length);
  log(label + ' ascii256=' + asciiBytes(b, 256));
  log(label + ' hex512=' + hexBytes(b, 512));
  log(label + ' b64_512=' + b64Bytes(b, 512));
}

function val(v) {
  if (v === null || v === undefined) return String(v);
  if (isByteArray(v)) return 'byte[] len=' + v.length + ' ascii=' + asciiBytes(v, 80);
  try {
    var cls = v.getClass ? v.getClass().getName() : null;
    if (cls) return '<' + cls + '> ' + String(v).slice(0, 180);
  } catch (e) {}
  try { return String(v).slice(0, 220); } catch (e2) { return '<toString err>'; }
}

function dumpFields(label, obj) {
  try {
    var cls = obj.getClass();
    log(label + ' class=' + cls.getName());
    var fields = cls.getDeclaredFields();
    log(label + ' fieldCount=' + fields.length);
    for (var i = 0; i < fields.length && i < 40; i++) {
      try {
        var f = fields[i];
        f.setAccessible(true);
        log(label + ' field ' + f.getName() + ' type=' + f.getType().getName() + ' val=' + val(f.get(obj)));
      } catch (e) {
        log(label + ' field dump err #' + i + ' ' + e);
      }
    }
  } catch (e2) {
    log(label + ' dumpFields err ' + e2);
  }
}

function hookRun() {
  try {
    var F = Java.use('com.tencent.mm.plugin.normsg.f');
    F.run.implementation = function () {
      if (cap('f.run', 20)) {
        log('plugin.normsg.f.run IN');
        dumpFields('f.run.this', this);
        log('f.run STACK\n' + stack());
      }
      var r = this.run();
      if (cap('f.run.out', 20)) log('plugin.normsg.f.run OUT');
      return r;
    };
    log('hooked plugin.normsg.f.run');
  } catch (e) {
    log('hook f.run err ' + e);
  }
}

function hookByteRet(className, names, tag) {
  try {
    var C = Java.use(className);
    names.forEach(function (name) {
      try {
        C[name].overloads.forEach(function (ov, idx) {
          ov.implementation = function () {
            var key = tag + '.' + name + '#' + idx;
            if (cap(key + '.in', 30)) {
              log(key + ' IN');
              for (var i = 0; i < arguments.length; i++) log(key + ' arg' + i + '=' + val(arguments[i]));
              log(key + ' STACK\n' + stack());
            }
            var r = ov.apply(this, arguments);
            if (cap(key + '.out', 30)) {
              if (isByteArray(r)) dumpBytes(key + ' RET', r);
              else log(key + ' RET=' + val(r));
            }
            return r;
          };
        });
        log('hooked ' + className + '.' + name);
      } catch (e) {
        log('hook method err ' + className + '.' + name + ' ' + e);
      }
    });
  } catch (e2) {
    log('hook class err ' + className + ' ' + e2);
  }
}

Java.perform(function () {
  Exc = Java.use('java.lang.Exception');
  LogC = Java.use('android.util.Log');
  B64 = Java.use('android.util.Base64');

  hookRun();
  hookByteRet('com.tencent.mm.normsg.c$p', ['ae', 'af'], 'c$p');
  hookByteRet('com.tencent.mm.normsg.WCProbe$Info', ['m'], 'WCProbe');

  try {
    var PM = Java.use('android.app.ApplicationPackageManager');
    PM.getPackageInfo.overload('java.lang.String', 'int').implementation = function (p, f) {
      var r = this.getPackageInfo(p, f);
      if (((f & 64) !== 0 || (f & 0x8000000) !== 0) && cap('sig', 30)) {
        log('SIG getPackageInfo pkg=' + p + ' flags=0x' + (f >>> 0).toString(16));
        log('SIG STACK\n' + stack());
      }
      return r;
    };
    log('hooked PM.getPackageInfo');
  } catch (e) {
    log('PM hook err ' + e);
  }

  log('=== normsg f.run probe armed ===');
});
