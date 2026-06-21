'use strict';
// B56 防封官：ClassLoader-aware f.run 触发源探针。
// 目标：解释 plugin.normsg.f.run -> WCProbe.m(arg0=1934848488) -> c$p.ae/af 的触发与字段。
// 用法：frida -U -f com.tencent.mm -l tools/dump_normsg_f_run_loader_B56.js

function log(t) { console.log('[FRUNL] ' + new Date().toISOString().substr(11, 12) + ' ' + t); }

var Exc = null;
var LogC = null;
var B64 = null;
var hooked = {};
var cnt = {};

function cap(k, n) { n = n || 20; cnt[k] = (cnt[k] || 0) + 1; return cnt[k] <= n; }
function stack() { try { return LogC.getStackTraceString(Exc.$new()); } catch (e) { return '<stack err ' + e + '>'; } }
function key(loader, className) { return String(loader) + '::' + className; }
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
  } catch (e) { return '<hex err ' + e + '>'; }
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
  } catch (e) { return '<ascii err ' + e + '>'; }
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
    if (cls) return '<' + cls + '> ' + String(v).slice(0, 220);
  } catch (e) {}
  try { return String(v).slice(0, 240); } catch (e2) { return '<toString err>'; }
}

function dumpFields(label, obj) {
  try {
    var cls = obj.getClass();
    log(label + ' class=' + cls.getName());
    var fields = cls.getDeclaredFields();
    log(label + ' fieldCount=' + fields.length);
    for (var i = 0; i < fields.length && i < 60; i++) {
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

function hookRun(factory, loaderLabel) {
  var className = 'com.tencent.mm.plugin.normsg.f';
  var hk = loaderLabel + '::' + className + '.run';
  if (hooked[hk]) return;
  try {
    var F = factory.use(className);
    F.run.implementation = function () {
      if (cap(hk + '.in', 30)) {
        log('plugin.normsg.f.run IN loader=' + loaderLabel);
        dumpFields('f.run.this', this);
        log('f.run STACK\n' + stack());
      }
      var r = this.run();
      if (cap(hk + '.out', 30)) log('plugin.normsg.f.run OUT loader=' + loaderLabel);
      return r;
    };
    hooked[hk] = true;
    log('hooked ' + hk);
  } catch (e) {}
}

function hookByteRet(factory, loaderLabel, className, names, tag) {
  try {
    var C = factory.use(className);
    names.forEach(function (name) {
      try {
        if (!C[name]) return;
        C[name].overloads.forEach(function (ov, idx) {
          var hk = loaderLabel + '::' + tag + '.' + name + '#' + idx;
          if (hooked[hk]) return;
          ov.implementation = function () {
            if (cap(hk + '.in', 30)) {
              log(tag + '.' + name + '#' + idx + ' IN loader=' + loaderLabel);
              for (var i = 0; i < arguments.length; i++) log(tag + '.' + name + '#' + idx + ' arg' + i + '=' + val(arguments[i]));
              log(tag + '.' + name + '#' + idx + ' STACK\n' + stack());
            }
            var r = ov.apply(this, arguments);
            if (cap(hk + '.out', 30)) {
              if (isByteArray(r)) dumpBytes(tag + '.' + name + '#' + idx + ' RET', r);
              else log(tag + '.' + name + '#' + idx + ' RET=' + val(r));
            }
            return r;
          };
          hooked[hk] = true;
        });
        log('hooked ' + loaderLabel + '::' + className + '.' + name);
      } catch (e) {
        log('hook method err ' + loaderLabel + '::' + className + '.' + name + ' ' + e);
      }
    });
  } catch (e2) {}
}

function hookLoader(loader) {
  var loaderLabel = String(loader);
  try {
    var factory = Java.ClassFactory.get(loader);
    hookRun(factory, loaderLabel);
    hookByteRet(factory, loaderLabel, 'com.tencent.mm.normsg.WCProbe$Info', ['m'], 'WCProbe');
    hookByteRet(factory, loaderLabel, 'com.tencent.mm.normsg.c$p', ['ae', 'af'], 'c$p');
  } catch (e) {}
}

function hookClassLoaderLoads() {
  try {
    var CL = Java.use('java.lang.ClassLoader');
    CL.loadClass.overload('java.lang.String').implementation = function (name) {
      var cls = this.loadClass(name);
      if (name === 'com.tencent.mm.plugin.normsg.f' ||
          name === 'com.tencent.mm.normsg.WCProbe$Info' ||
          name === 'com.tencent.mm.normsg.c$p') {
        if (cap('loadClass.' + name, 20)) log('loadClass ' + name + ' loader=' + String(this));
        hookLoader(this);
      }
      return cls;
    };
    CL.loadClass.overload('java.lang.String', 'boolean').implementation = function (name, resolve) {
      var cls = this.loadClass(name, resolve);
      if (name === 'com.tencent.mm.plugin.normsg.f' ||
          name === 'com.tencent.mm.normsg.WCProbe$Info' ||
          name === 'com.tencent.mm.normsg.c$p') {
        if (cap('loadClass.' + name, 20)) log('loadClass ' + name + ' resolve=' + resolve + ' loader=' + String(this));
        hookLoader(this);
      }
      return cls;
    };
    log('hooked ClassLoader.loadClass for dynamic normsg loaders');
  } catch (e) {
    log('ClassLoader hook err ' + e);
  }
}

Java.perform(function () {
  Exc = Java.use('java.lang.Exception');
  LogC = Java.use('android.util.Log');
  B64 = Java.use('android.util.Base64');

  hookClassLoaderLoads();
  hookLoader(Java.classFactory.loader);

  Java.enumerateClassLoaders({
    onMatch: function (loader) {
      hookLoader(loader);
    },
    onComplete: function () {
      log('classloader scan complete, hooks=' + Object.keys(hooked).length);
    }
  });

  try {
    var PM = Java.use('android.app.ApplicationPackageManager');
    PM.getPackageInfo.overload('java.lang.String', 'int').implementation = function (p, f) {
      var r = this.getPackageInfo(p, f);
      if (((f & 64) !== 0 || (f & 0x8000000) !== 0) && cap('sig', 25)) {
        log('SIG getPackageInfo pkg=' + p + ' flags=0x' + (f >>> 0).toString(16));
        log('SIG STACK\n' + stack());
      }
      return r;
    };
    log('hooked PM.getPackageInfo');
  } catch (e) {
    log('PM hook err ' + e);
  }

  log('=== normsg loader-aware f.run probe armed ===');
});
