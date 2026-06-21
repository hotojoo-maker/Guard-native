'use strict';
// B29 一次性主动取证：直接调 u.z3(0)/Y8() 拿设备指纹明文（不等自然触发），快进快出降低 attach 暴露。
function log(t) { console.log('[PT] ' + t); }
Java.perform(function () {
  var found = false;
  try {
    Java.choose('com.tencent.mm.plugin.normsg.u', {
      onMatch: function (o) {
        if (found) return; found = true;
        try { log('z3(0) 设备指纹 => ' + o.z3(0)); } catch (e) { log('z3 call err: ' + e); }
        try { log('Y8 OAID => ' + o.Y8()); } catch (e) { log('Y8 err: ' + e); }
      },
      onComplete: function () { if (!found) log('no u instance (normsg 未初始化?)'); }
    });
  } catch (e) { log('choose err: ' + e); }
  log('=== done ===');
});
