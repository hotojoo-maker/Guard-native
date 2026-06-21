'use strict';
// B56 event probe v2：normsg / WCProbe 事件明文探针。
// 原则：明文优先，不抓泛请求，不研究混淆。
// 输出：长度、hex 前 512B、base64 前 512B、调用栈。
// 用法：frida -U -p <pidof com.tencent.mm> -l tools/dump_normsg_event_probe_v2_B56.js

function log(t) { console.log('[EVT2] ' + new Date().toISOString().substr(11, 12) + ' ' + t); }

var cnt = {};
function cap(k, n) { n = n || 20; cnt[k] = (cnt[k] || 0) + 1; return cnt[k] <= n; }

var Exc = null;
var LogC = null;
var B64 = null;

function stack() {
  try { return LogC.getStackTraceString(Exc.$new()); } catch (e) { return '<stack err ' + e + '>'; }
}

function isByteArray(x) {
  try { return x && x.length !== undefined && typeof x !== 'string'; } catch (e) { return false; }
}

function hexBytes(b, max) {
  if (!b) return 'null';
  max = max || 512;
  var n = Math.min(b.length, max);
  var s = '';
  try {
    for (var i = 0; i < n; i++) {
      var x = (b[i] & 0xff).toString(16);
      s += (x.length < 2 ? '0' : '') + x;
    }
  } catch (e) {
    return '<hex err ' + e + '>';
  }
  return s + (b.length > n ? '...' : '');
}

function asciiBytes(b, max) {
  if (!b) return 'null';
  max = max || 256;
  var n = Math.min(b.length, max);
  var s = '';
  try {
    for (var i = 0; i < n; i++) {
      var c = b[i] & 0xff;
      s += (c >= 32 && c < 127) ? String.fromCharCode(c) : '.';
    }
  } catch (e) {
    return '<ascii err ' + e + '>';
  }
  return s + (b.length > n ? '...' : '');
}

function sliceByteArray(b, max) {
  var n = Math.min(b.length, max || 512);
  var arr = [];
  for (var i = 0; i < n; i++) arr.push(b[i]);
  return Java.array('byte', arr);
}

function b64Bytes(b, max) {
  if (!b) return 'null';
  try { return B64.encodeToString(sliceByteArray(b, max || 512), 2); } catch (e) { return '<b64 err ' + e + '>'; }
}

function dumpBytes(label, b) {
  try {
    log(label + ' len=' + b.length);
    log(label + ' ascii256=' + asciiBytes(b, 256));
    log(label + ' hex512=' + hexBytes(b, 512));
    log(label + ' b64_512=' + b64Bytes(b, 512));
  } catch (e) {
    log(label + ' dump err ' + e);
  }
}

function val(v) {
  if (v === null || v === undefined) return String(v);
  if (isByteArray(v)) return 'byte[] len=' + v.length + ' ascii=' + asciiBytes(v, 80);
  try {
    var cls = v.getClass ? v.getClass().getName() : null;
    if (cls) return '<' + cls + '> ' + String(v).slice(0, 160);
  } catch (e) {}
  try { return String(v).slice(0, 200); } catch (e2) { return '<toString err>'; }
}

function dumpArgs(label, args) {
  for (var i = 0; i < args.length; i++) {
    var a = args[i];
    if (isByteArray(a)) dumpBytes(label + ' arg' + i, a);
    else log(label + ' arg' + i + '=' + val(a));
  }
}

function hookClassMethods(className, names, tag) {
  try {
    var C = Java.use(className);
    names.forEach(function (name) {
      try {
        if (!C[name]) return;
        C[name].overloads.forEach(function (ov, idx) {
          ov.implementation = function () {
            var key = tag + '.' + name + '#' + idx;
            if (cap(key + '.in', 12)) {
              log(key + ' IN');
              dumpArgs(key, arguments);
              log(key + ' STACK\n' + stack());
            }
            var r = ov.apply(this, arguments);
            if (cap(key + '.out', 12)) {
              if (isByteArray(r)) dumpBytes(key + ' RET', r);
              else log(key + ' RET=' + val(r));
            }
            return r;
          };
        });
        log('hooked ' + className + '.' + name + ' overloads=' + C[name].overloads.length);
      } catch (e) {
        log('hook method err ' + className + '.' + name + ' ' + e);
      }
    });
  } catch (e) {
    log('hook class err ' + className + ' ' + e);
  }
}

function activeZ3() {
  var found = false;
  try {
    Java.choose('com.tencent.mm.plugin.normsg.u', {
      onMatch: function (o) {
        if (found) return;
        found = true;
        try { log('ACTIVE z3(0) => ' + o.z3(0)); } catch (e) { log('ACTIVE z3 err ' + e); }
        try { log('ACTIVE Y8 => ' + o.Y8()); } catch (e2) { log('ACTIVE Y8 err ' + e2); }
      },
      onComplete: function () { if (!found) log('ACTIVE no u instance'); }
    });
  } catch (e) {
    log('ACTIVE choose err ' + e);
  }
}

Java.perform(function () {
  Exc = Java.use('java.lang.Exception');
  LogC = Java.use('android.util.Log');
  B64 = Java.use('android.util.Base64');

  hookClassMethods('com.tencent.mm.normsg.c$p', ['aa', 'ad', 'af', 'ae'], 'c$p');
  hookClassMethods('com.tencent.mm.normsg.WCProbe$Info', ['dispatchEncryptJNIFuncCall', 'n', 'f', 'm', 'i', 'c', 'g', 'h', 'j'], 'WCProbe');
  hookClassMethods('com.tencent.mm.plugin.normsg.u', ['z3', 'Y8', 'uc'], 'u');

  try {
    var PM = Java.use('android.app.ApplicationPackageManager');
    PM.getPackageInfo.overload('java.lang.String', 'int').implementation = function (p, f) {
      var r = this.getPackageInfo(p, f);
      if (((f & 64) !== 0 || (f & 0x8000000) !== 0) && cap('SIG', 20)) {
        log('SIG getPackageInfo pkg=' + p + ' flags=0x' + (f >>> 0).toString(16));
        log('SIG STACK\n' + stack());
      }
      return r;
    };
    log('hooked ApplicationPackageManager.getPackageInfo');
  } catch (e) {
    log('PM hook err ' + e);
  }

  log('=== normsg event probe v2 armed ===');
  setTimeout(activeZ3, 1200);
});
