'use strict';
// 防封官 B36 v2：定 k33 来源。修正 v1：getPackageName 限频 + cmdline open 打 backtrace 归因（是否 normsg 上游）。
// 用法：frida -U -f com.tencent.mm -l tools/trace_pkg_source.js
function log(t) { console.log('[SRC] ' + t); }
var gpn = 0;
var btDone = 0;

// native：谁读 /proc/<self>/cmdline —— 打调用栈看上游是不是 libwechatnormsg.so
['open', 'openat'].forEach(function (nm) {
  var m = Process.findModuleByName('libc.so');
  var f = m ? m.findExportByName(nm) : null;
  if (!f) { return; }
  Interceptor.attach(f, {
    onEnter: function (a) {
      var idx = (nm === 'openat') ? 1 : 0;
      try { this.p = a[idx].readCString(); } catch (e) { this.p = null; }
      this.ctx = this.context;
    },
    onLeave: function (r) {
      if (this.p && this.p.indexOf('cmdline') >= 0 && btDone < 3) {
        btDone++;
        log('NATIVE ' + nm + ' ' + this.p + '  调用栈:');
        try {
          Thread.backtrace(this.ctx, Backtracer.ACCURATE).slice(0, 10).forEach(function (addr) {
            log('    ' + DebugSymbol.fromAddress(addr));
          });
        } catch (e) { log('    bt err ' + e); }
      }
    }
  });
});

Java.perform(function () {
  // Java 包名链（限前 2 次，避免刷屏）
  try {
    var CW = Java.use('android.content.ContextWrapper');
    CW.getPackageName.implementation = function () {
      var r = this.getPackageName();
      if (gpn < 2) { gpn++; log('JAVA getPackageName => ' + r + '（仅记前2次）'); }
      return r;
    };
  } catch (e) { log('gpn err ' + e); }

  // normsg 采集出口：k33/k49 在返回串里
  try {
    var U = Java.use('com.tencent.mm.plugin.normsg.u');
    U.z3.overload('int').implementation = function (i) {
      var r = this.z3(i);
      log('z3(' + i + ') 采集串(看 k33/k49) => ' + r);
      return r;
    };
  } catch (e) { log('z3 err ' + e); }

  log('=== tracer v2 armed (限频 + cmdline backtrace) ===');
});
