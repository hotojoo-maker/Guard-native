// java_first 验证：hook FaceProNative.checkByNative() 强制 return 0（不执行 native VA 扫描），
// 看共存包 com.tencent.mn 扫脸能否放行 + 有没有别的调用点/native 旁路绕过 j52.a。
// ⚠ 只 hook native 方法的 Java 入口改返回，不 inline hook / 不 dlopen 微信 SO（不碰真 native，守铁律2/23）。
// 目的：拿证据决定「Java hook 够不够」——够则无需碰 native；不够(旁路)再谈 native 且走双审。
import Java from 'frida-java-bridge';
function log(t) { console.log('[VAPATCH] ' + t); }

Java.perform(function () {
  try {
    var FPN = Java.use('com.tencent.mm.plugin.facedetect.FaceProNative');
    // 强制未命中：直接 return 0，不调原 native（彻底不执行 native VA 扫描）。共存包原本=1（上一轮 L1）。
    FPN.checkByNative.implementation = function () {
      log('checkByNative() 拦截 → 强制 return 0（不执行 native VA；共存包原本=1 已 L1）');
      return 0;
    };
    // 改 0 后 j52.a 的 if(checkByNative==1) 应为 false、不再取 VA 结果；若仍被调 = 有别的调用点绕过
    FPN.getVAScanResult.implementation = function () {
      var r = this.getVAScanResult();
      log('!! getVAScanResult() 仍被调 ="' + r + '"（有别的调用点绕过 checkByNative 分支）');
      return r;
    };
    log('hooked checkByNative(->0) / getVAScanResult');
  } catch (e) { log('FaceProNative ERR ' + e); }

  // 打包串：VA 部分(e0.*)是否变干净 + DevSecurityScan 部分(c.*)是否才是"系统繁忙"真凶
  try {
    var B = Java.use('j52.b');
    B.b.implementation = function () {
      var r = this.b();
      log('j52.b.b() 打包串 = ' + r);
      return r;
    };
    log('hooked j52.b.b');
  } catch (e) { log('j52.b ERR ' + e); }

  log('=== VA patch armed (checkByNative 强制->0，验 Java hook 够不够) ===');
});
