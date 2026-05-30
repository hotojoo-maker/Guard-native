'use strict';
// 临时 KPI 探针（frida_stats.js 缺失时的替代）。
// 采集反检测核心指标 2 分钟：PROP 读取数 / verifiedbootstate 读取数 / ro.boot.* / CONN。
// 注意：这是新工具，与 F-22 的 frida_stats v1.1 不是同一脚本，基线不可直接对比，仅作 sanity check。

function resolve(name) {
  var cands = ['findGlobalExportByName', 'getGlobalExportByName'];
  for (var i = 0; i < cands.length; i++) {
    try { if (Module[cands[i]]) { var p = Module[cands[i]](name); if (p) return p; } } catch (e) {}
  }
  try { return Module.findExportByName(null, name); } catch (e) {}
  return null;
}

var stats = { prop: 0, vbs: 0, roboot: 0, conn: 0 };
var t0 = Date.now();

var pg = resolve('__system_property_get');
if (pg) {
  Interceptor.attach(pg, {
    onEnter: function (args) {
      stats.prop++;
      try {
        var n = args[0].readCString();
        if (n) {
          if (n.indexOf('verifiedbootstate') !== -1) stats.vbs++;
          else if (n.indexOf('ro.boot') === 0) stats.roboot++;
        }
      } catch (e) {}
    }
  });
  console.log('[KPI] hooked __system_property_get');
} else {
  console.log('[KPI] WARN __system_property_get not found');
}

var cn = resolve('connect');
if (cn) {
  Interceptor.attach(cn, { onEnter: function () { stats.conn++; } });
  console.log('[KPI] hooked connect');
}

function dump(tag) {
  var secs = ((Date.now() - t0) / 1000).toFixed(0);
  console.log('[KPI] t=' + secs + 's  PROP=' + stats.prop +
    '  verifiedbootstate=' + stats.vbs +
    '  ro.boot.*=' + stats.roboot +
    '  CONN=' + stats.conn + '  (' + tag + ')');
}

var elapsed = 0;
var iv = setInterval(function () {
  elapsed += 30;
  dump('tick');
  if (elapsed >= 120) { clearInterval(iv); dump('FINAL-2min'); }
}, 30000);
console.log('[KPI] === started, 2min foreground window ===');
