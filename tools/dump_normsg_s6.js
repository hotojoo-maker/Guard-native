'use strict';
// B35 混淆研究：hook normsg u.S6()(ql3.j XOR 解密器, u.java:367) 抓 密文入→明文出。
// 静态已证：S6 无 Java 字面量调用点(.S6( 全是别类同名方法)，故只能动态。
// 策略：hook S6 记录每次解密；再主动调 z3(0) 触发一轮设备指纹采集，看是否内部走 S6。
function log(t) { console.log('[S6] ' + t); }
Java.perform(function () {
  var hits = 0;
  try {
    var U = Java.use('com.tencent.mm.plugin.normsg.u');
    U.S6.implementation = function (s) {
      var r = this.S6(s);
      hits++;
      log('dec #' + hits + '  in=' + JSON.stringify(s) + '  =>  out=' + JSON.stringify(r));
      return r;
    };
    log('hooked u.S6');
  } catch (e) { log('S6 hook ERR ' + e); }
  // 主动触发一轮采集（z3 设备指纹），观察是否内部调用 S6 解密隐藏串
  try {
    Java.choose('com.tencent.mm.plugin.normsg.u', {
      onMatch: function (o) { try { o.z3(0); log('z3(0) 触发完成'); } catch (e) { log('z3 err ' + e); } },
      onComplete: function () {}
    });
  } catch (e) { log('choose err ' + e); }
  log('=== S6 hits=' + hits + ' (若为0 说明 z3 路径不走 S6, 需 warm-attach 被动等其它触发) ===');
});
