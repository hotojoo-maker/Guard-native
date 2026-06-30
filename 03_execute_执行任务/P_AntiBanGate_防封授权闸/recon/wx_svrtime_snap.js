// 打一拍：读 jy0.hd 服务器时间 vs 设备墙钟（B 改表用：改钟前后各打一拍对比）
Java.perform(function () {
  try {
    var hd = Java.use('jy0.hd');
    var Sys = Java.use('java.lang.System');
    var dev = Sys.currentTimeMillis();
    var c = hd.c(), b = hd.b();
    console.log('[SNAP] hd.c=' + c + ' hd.b=' + b + ' dev=' + dev
      + ' c-dev(ms)=' + (c - dev)
      + ' iso_c=' + new Date(c).toISOString()
      + ' iso_dev=' + new Date(dev).toISOString());
  } catch (e) { console.log('[SNAP] ERR ' + e); }
});
