/*
 * hook_convtime.js — 抓官方授时锚点 field_conversationTime 的 L1 证据
 * 目的：证明 ConvFilter.extractConvTime 读到的会话时间是「服务器授时」而非本机墙钟
 *      （改墙钟回拨后该值不跟着掉 = 改表杀不掉 = 可作未授权超窗的可信锚点）
 * 用法：frida -U -f <pkg> -l hook_convtime.js   （<pkg> = 在测的微信包）
 * 注意：纯读 hook，不改返回值、不动业务（铁律29）。
 */
'use strict';

var TARGET = 'com.ghost.assist.moduleD.ConvFilter';
var hooked = false;

function installHook(factory, tag) {
  try {
    var CF = factory.use(TARGET);
    CF.extractConvTime.overload('java.lang.Object').implementation = function (item) {
      var r = this.extractConvTime(item);
      if (r > 0) {
        var wall = new Date().getTime();
        var line = '[CONVTIME] conv=' + r +
          ' wall=' + wall +
          ' delta_ms=' + (wall - r) +
          ' conv_iso=' + new Date(r).toISOString();
        console.log(line);
        try { send(line); } catch (e) {}
      }
      return r;
    };
    hooked = true;
    console.log('[HOOK] extractConvTime hooked via ' + tag);
  } catch (e) {
    console.log('[HOOK] install miss (' + tag + '): ' + e);
  }
}

Java.perform(function () {
  // 先试默认 classloader
  installHook(Java, 'default-loader');
  if (hooked) return;
  // 模块类常在 host 的另一个 classloader（LSPosed/LSPatch 注入）
  console.log('[*] default miss, scanning classloaders for ' + TARGET);
  Java.enumerateClassLoaders({
    onMatch: function (loader) {
      if (hooked) return;
      try {
        loader.loadClass(TARGET);
        installHook(Java.ClassFactory.get(loader), 'scanned-loader');
      } catch (e) { /* this loader can't see it */ }
    },
    onComplete: function () {
      console.log('[*] classloader scan done. hooked=' + hooked);
      if (!hooked) {
        console.log('[!] 没找到 ' + TARGET + ' —— 可能该包是混淆 release 包，类名被改。需换包或按混淆名调整。');
      }
    }
  });
});
