// 验扫脸/SOTER 环境检测面：FaceProNative.checkByNative()(native VA/多开扫描) 返回 0/1 +
//   getVAScanResult()(读到的包名) + j52.b.b()(把 VA 结果 + DevSecurityScan 拼串打包)。
// 对照目的：官方包 com.tencent.mm 应 checkByNative()=0（读官方包名不命中）；共存 com.tencent.mn=1。
// Frida 17: 需 frida-compile 打包 frida-java-bridge（ESM import），同 dump_sig_md5。
import Java from 'frida-java-bridge';
function log(t) { console.log('[VASCAN] ' + t); }
var Exc, LogC;
function stack() { try { return LogC.getStackTraceString(Exc.$new()); } catch (e) { return '<stk ' + e + '>'; } }

Java.perform(function () {
  Exc = Java.use('java.lang.Exception');
  LogC = Java.use('android.util.Log');

  // 1) FaceProNative.checkByNative() —— native VA/多开扫描主判定（0=未命中/官方态，1=命中/非官方）
  try {
    var FPN = Java.use('com.tencent.mm.plugin.facedetect.FaceProNative');
    FPN.checkByNative.implementation = function () {
      var r = this.checkByNative();
      log('checkByNative() = ' + r + (r === 1 ? '  <== 命中(VA/非官方包名!)' : (r === 0 ? '  <== 未命中(0=官方态)' : '  <== 其它=' + r)));
      log('  caller: ' + stack().split(/\n/).slice(1, 7).join(' <- '));
      return r;
    };
    FPN.getVAScanResult.implementation = function () {
      var r = this.getVAScanResult();
      log('getVAScanResult() = "' + r + '"  (native 读到的包名)');
      return r;
    };
    log('hooked FaceProNative.checkByNative / getVAScanResult');
  } catch (e) { log('FaceProNative ERR ' + e); }

  // 2) j52.b.b() —— 打包方法：把 DevSecurityScan(c.*) + VA scan(e0.*) 拼成上报串（触发=结果被组包）
  try {
    var B = Java.use('j52.b');
    B.b.implementation = function () {
      var r = this.b();
      log('j52.b.b() 打包串(触发!) = ' + r);
      log('  caller: ' + stack().split(/\n/).slice(1, 7).join(' <- '));
      return r;
    };
    log('hooked j52.b.b (打包/上报组包点)');
  } catch (e) { log('j52.b ERR ' + e); }

  // 3) DevSecurityScan native 加载点 tpcs（确认设备安全预扫是否跑）
  try {
    var CSO = Java.use('com.tencent.cso.CsoLoader');
    CSO.e.overload('java.lang.String').implementation = function (n) {
      if (n && n.indexOf('tpcs') >= 0) log('CsoLoader.e("' + n + '")  <== DevSecurityScan(tpcs) 加载');
      return this.e(n);
    };
  } catch (e) { log('CsoLoader ERR ' + e); }

  log('=== VA scan probe armed (官方期望 checkByNative=0) ===');
});
