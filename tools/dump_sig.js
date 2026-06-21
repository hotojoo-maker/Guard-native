'use strict';
// 取 com.tencent.mm 安装包签名证书摘要（MD5/SHA-256），判断是否官方 Tencent 证书。
Java.perform(function () {
  try {
    var AT = Java.use('android.app.ActivityThread');
    var app = AT.currentApplication();
    var ctx = app.getApplicationContext();
    var pm = ctx.getPackageManager();
    var pi = pm.getPackageInfo('com.tencent.mm', 64); // GET_SIGNATURES
    var sigs = pi.signatures.value;
    console.log('[SIG] signatures count=' + sigs.length);
    var MD = Java.use('java.security.MessageDigest');
    var algs = ['MD5', 'SHA-256'];
    for (var k = 0; k < algs.length; k++) {
      var md = MD.getInstance(algs[k]);
      var dig = md.digest(sigs[0].toByteArray());
      var s = '';
      for (var i = 0; i < dig.length; i++) { var x = (dig[i] & 0xff).toString(16); s += (x.length < 2 ? '0' : '') + x; }
      console.log('[SIG] ' + algs[k] + '=' + s);
    }
  } catch (e) { console.log('[SIG] err ' + e); }
});
