'use strict';
// B29 动态取证 v5（com.tencent.mn 共存版 + 已封小号）：strike XML + c29 + AccStrike 因子/名单
function log(t) { console.log('[DUMP] ' + t); }
Java.perform(function () {
  var S = Java.use('java.lang.String');
  var vo = S.valueOf.overload('java.lang.Object');
  function L(lst) {
    if (lst === null) return 'null';
    var n = lst.size(); var a = [];
    for (var i = 0; i < n; i++) { a.push(vo.call(S, lst.get(i))); }
    return 'size=' + n + ' [' + a.join(' ; ') + ']';
  }
  log('=== START (com.tencent.mn 共存版/已封) ===');

  // 1) strike XML（重点看有没有点名我们）
  try {
    var ACM = Java.use('com.tencent.mm.accessibility.feature.AccConfigManager');
    var cfg = ACM.INSTANCE.value.getConfigData();
    log('AccConfig strike XML = ' + (cfg === null ? 'null（无下发/已过期）' : cfg.toXml()));
  } catch (e) { log('AccConfig ERR: ' + e); }

  // 2) c29 installed_pkgs 待查名单
  try { log('c$q.c29() installed_pkgs = ' + L(Java.use('com.tencent.mm.normsg.c$q').c29())); }
  catch (e) { log('c29 ERR: ' + e); }

  // 3) AccExpt 运行时因子 + evil 名单
  try {
    Java.choose('com.tencent.mm.accessibility.feature.AccExptService', {
      onMatch: function (o) {
        try {
          log('factors: strike=' + o.accInfoStrikeFactor.value + ' clear=' + o.accInfoClearFactor.value + ' evilTouch=' + o.interceptEvilTouchFactor.value + ' touchDelayMs=' + o.touchExDelayConfirmMs.value + ' disableSS=' + o.disableScreenshot.value);
          log('evilStackList = ' + L(o.evilStackList.value));
          log('evilPkgList = ' + L(o.evilPkgList.value));
        } catch (e2) { log('field ERR: ' + e2); }
      },
      onComplete: function () {}
    });
  } catch (e) { log('choose ERR: ' + e); }

  log('=== DONE ===');
});
