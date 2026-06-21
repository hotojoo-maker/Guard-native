'use strict';
// B56 防封官：normsg 大动脉探针。
// 目标：抓检测点 -> 明文/byte[] -> toProtoBuf -> modelbase/network，不研究混淆。
// 用法：frida -U -f com.tencent.mm -l tools/dump_normsg_artery_B56.js

function log(t) { console.log('[ARTERY] ' + new Date().toISOString().substr(11, 12) + ' ' + t); }

var cnt = {};
function cap(k, n) {
  n = n || 10;
  cnt[k] = (cnt[k] || 0) + 1;
  return cnt[k] <= n;
}

function bytesPreview(a) {
  if (!a) return 'null';
  var len;
  try { len = a.length; } catch (e) { return String(a); }
  if (len === undefined) return String(a);
  var n = Math.min(len, 240);
  var s = '';
  try {
    for (var i = 0; i < n; i++) {
      var c = a[i] & 0xff;
      s += (c >= 32 && c < 127) ? String.fromCharCode(c) : '.';
    }
  } catch (e) {
    return 'len=' + len + ' <preview err ' + e + '>';
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
    if (cls) return '<' + cls + '> ' + String(v).slice(0, 180);
  } catch (e2) {}
  try { return String(v).slice(0, 220); } catch (e3) { return '<toString err>'; }
}

function argsPreview(args) {
  var out = [];
  for (var i = 0; i < args.length; i++) out.push('arg' + i + '=' + val(args[i]));
  return out.join(' | ');
}

function hookMethods(className, methodNames, tag, maxTries) {
  var tries = 0;
  var hooked = false;
  var timer = setInterval(function () {
    if (hooked) return;
    tries++;
    try {
      var C = Java.use(className);
      methodNames.forEach(function (m) {
        try {
          if (!C[m]) return;
          C[m].overloads.forEach(function (ov) {
            ov.implementation = function () {
              var key = tag + '.' + m;
              if (cap(key + '.in', 8)) log(key + ' IN ' + argsPreview(arguments));
              var r = ov.apply(this, arguments);
              if (cap(key + '.out', 8)) log(key + ' OUT ' + val(r));
              return r;
            };
          });
          log('hooked ' + keyName(className, m) + ' overloads=' + C[m].overloads.length);
        } catch (e) {
          log('hook method err ' + className + '.' + m + ' ' + e);
        }
      });
      hooked = true;
      clearInterval(timer);
    } catch (e) {
      if (tries === 1) log('waiting class ' + className + ' (' + e + ')');
      if (tries >= (maxTries || 300)) {
        log('give up class ' + className);
        clearInterval(timer);
      }
    }
  }, 100);
}

function keyName(c, m) { return c + '.' + m; }

Java.perform(function () {
  // normsg native -> Java bridge return values.
  hookMethods('com.tencent.mm.normsg.c$p', ['aa', 'ad', 'af', 'ae'], 'normsg.c$p', 60);

  // WCProbe wrappers seen in signature/z3 chains.
  hookMethods('com.tencent.mm.normsg.WCProbe$Info', ['n', 'f', 'm', 'h', 'i', 'j', 'c', 'g'], 'WCProbe', 120);

  // Java normsg collector.
  hookMethods('com.tencent.mm.plugin.normsg.u', ['z3', 'Y8', 'uc'], 'normsg.u', 120);

  // protobuf / modelbase / network artery from B56 stack.
  hookMethods('w15.kg', ['toProtoBuf'], 'toProtoBuf', 300);
  hookMethods('com.tencent.mm.modelbase.p2', ['B2'], 'modelbase.p2', 300);
  hookMethods('com.tencent.mm.modelbase.t2', ['U8'], 'modelbase.t2', 300);
  hookMethods('com.tencent.mm.network.w0', ['onTransact'], 'network.w0', 300);

  // Signature reads: keep this narrow, only signature flags.
  try {
    var PM = Java.use('android.app.ApplicationPackageManager');
    PM.getPackageInfo.overload('java.lang.String', 'int').implementation = function (p, f) {
      var r = this.getPackageInfo(p, f);
      if (((f & 64) !== 0 || (f & 0x8000000) !== 0) && cap('sig', 16)) {
        log('SIG getPackageInfo pkg=' + p + ' flags=0x' + (f >>> 0).toString(16));
      }
      return r;
    };
    log('hooked ApplicationPackageManager.getPackageInfo');
  } catch (e) {
    log('PM hook err ' + e);
  }

  log('=== normsg artery probe armed ===');
});
