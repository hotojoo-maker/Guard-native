'use strict';
// 防封官 B36 ①：发版前三轴自检门。spawn/attach 候选包，调 z3，断言 k33/k49/k18 = 官方。任一不对 = FAIL = 阻发。
// 用法：frida -U -p <pidof 候选包> -l tools/gate_three_axis.js   （或 -f spawn）
var OFFICIAL = {
  k33: 'com.tencent.mm',
  k49: '/data/user/0/com.tencent.mm/',
  k18: '18c867f0717aa67b2ab7347505ba07ed'
};
function log(t) { console.log('[GATE] ' + t); }
Java.perform(function () {
  var done = false;
  Java.choose('com.tencent.mm.plugin.normsg.u', {
    onMatch: function (o) {
      if (done) { return; }
      done = true;
      try {
        var s = '' + o.z3(0);
        function f(k) { var m = s.match(new RegExp('<' + k + '>([^<]*)</' + k + '>')); return m ? m[1] : null; }
        var k33 = f('k33'), k49 = f('k49'), k18 = f('k18');
        var p33 = (k33 === OFFICIAL.k33);
        var p49 = (k49 && k49.indexOf(OFFICIAL.k49) === 0);
        var p18 = (k18 === OFFICIAL.k18);
        log('k33(包名) = ' + k33 + '   ' + (p33 ? 'PASS' : 'FAIL'));
        log('k49(路径) = ' + k49 + '   ' + (p49 ? 'PASS' : 'FAIL'));
        log('k18(签名) = ' + k18 + '   ' + (p18 ? 'PASS' : 'FAIL'));
        log('===== 三轴自检门：' + ((p33 && p49 && p18) ? 'PASS（可发）' : 'FAIL（阻发！三轴有异常值）') + ' =====');
      } catch (e) { log('z3 err ' + e); }
    },
    onComplete: function () { if (!done) { log('no u instance（normsg 未初始化；换 -f spawn 或等登录后再跑）'); } }
  });
});
