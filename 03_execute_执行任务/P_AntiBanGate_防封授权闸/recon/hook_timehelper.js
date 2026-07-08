/*
 * hook_timehelper.js — 真机 L1 验证 jy0.hd (MicroMsg.TimeHelper) 是官方服务器授时
 * 来源：jadx 8.0.71 静态 L2（用户 G92 提供）；本脚本取 L1。
 * 证什么：
 *   - hd.c() ≈ 真实服务器时间（与墙钟 delta 很小）
 *   - hd.b() = MMKV 重启地板
 *   - hd.g(long) = newsync 喂数（证「实时刷新、非消息驱动」）
 *   - 改墙钟回拨后 hd.c() 不跟着掉 = 改表杀不掉（关键）
 * 用法：先有微信进程在跑（主进程或 :push 都行，跨进程 MMKV），再 attach：
 *   frida -U -n <pkg> -l hook_timehelper.js        （主进程）
 *   frida -U -p <push_pid> -l hook_timehelper.js   （:push 进程）
 * 只读：仅 hook hd.g 打印后照常调用原方法；hd.c()/hd.b() 是只读计算。
 */
'use strict';

Java.perform(function () {
  var ALog = Java.use('android.util.Log');
  var LogI = ALog.i.overload('java.lang.String', 'java.lang.String');
  function out(s) { console.log(s); try { LogI('GUARDTIME', s); } catch (e) { console.log('[LOGI-ERR]' + e); } }
  function iso(ms) { try { return new Date(parseInt('' + ms, 10)).toISOString(); } catch (e) { return '?'; } }

  function dump(tag) {
    try {
      var hd = Java.use('jy0.hd');
      var c = hd.c();
      var b = hd.b();
      var wall = Date.now();
      out('[' + tag + '] hd.c=' + c + ' hd.b=' + b +
        ' wall=' + wall +
        ' c_delta_ms=' + (wall - c) +
        ' c_iso=' + iso(c) + ' wall_iso=' + iso(wall));
    } catch (e) { out('[DUMP-ERR] ' + e); }
  }

  try {
    var hd = Java.use('jy0.hd');
    hd.g.overload('long').implementation = function (t) {
      out('[hd.g FEED] server_t=' + t + ' iso=' + iso(t));
      return this.g(t);
    };
    out('[HOOK] jy0.hd.g(long) hooked');
  } catch (e) {
    out('[HOOK-ERR] jy0.hd not in default loader: ' + e);
  }

  dump('INIT');
  setInterval(function () { dump('TICK'); }, 4000);
  console.log('[READY] timehelper probe up. 步骤：先看 INIT/TICK 的 c_delta_ms 是否很小；再改墙钟回拨，看 hd.c 跳不跳。');
});
