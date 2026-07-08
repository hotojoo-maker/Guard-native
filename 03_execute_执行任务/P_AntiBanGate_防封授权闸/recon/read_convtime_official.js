/*
 * read_convtime_official.js — 直接读官方包自己的 field_conversationTime（不依赖我们的模块）
 * 用途：证明官方包会话时间是「服务器授时」而非本机墙钟（改墙钟回拨后该值不掉 = 改表杀不掉）。
 * 用法：先手动打开微信停在会话列表，再 attach：
 *        frida -U -n <pkg> -l read_convtime_official.js
 * 触发：在 frida REPL 起来后，手动滑动/刷新会话列表（让会话对象进堆），脚本每 4s 扫一次。
 * 只读：reflection 读字段，绝不改值、绝不动业务。
 */
'use strict';

var FIELD = 'field_conversationTime';

Java.perform(function () {
  var Modifier = Java.use('java.lang.reflect.Modifier');

  // 递归在对象图里找名为 field_conversationTime 的 long 字段（深度≤3，防环）
  function findConvTimes(rootObj, out) {
    var seen = [];
    function walk(obj, depth) {
      if (obj === null || depth > 3 || out.length > 40) return;
      var cls;
      try { cls = obj.getClass(); } catch (e) { return; }
      // 防环（按引用）
      for (var s = 0; s < seen.length; s++) { if (seen[s] === obj) return; }
      seen.push(obj);
      var fields;
      try { fields = cls.getDeclaredFields(); } catch (e) { return; }
      for (var i = 0; i < fields.length; i++) {
        var f = fields[i];
        try {
          f.setAccessible(true);
          var fname = f.getName();
          var ftype = f.getType().getName();
          var val = f.get(obj);
          if (val === null) continue;
          if (fname === FIELD) {
            var n = -1;
            try { n = parseInt('' + val, 10); } catch (e2) {}
            if (n > 0) out.push(n);
          } else if (ftype.indexOf('com.tencent') === 0 && depth < 3) {
            // 仅顺着微信自己的对象往里走，跳过基础类型/系统类
            walk(val, depth + 1);
          }
        } catch (e3) { /* skip */ }
      }
    }
    walk(rootObj, 0);
  }

  // 在堆里找「直接声明了 field_conversationTime 字段」的类，并读它所有活实例
  function scanOwners() {
    var owners = {};
    Java.enumerateLoadedClasses({
      onMatch: function (name) {
        if (name.indexOf('com.tencent') !== 0) return; // 只看微信自己的类
        try {
          var c = Java.use(name);
          c.class.getDeclaredField(FIELD); // 没这字段会抛
          owners[name] = true;
        } catch (e) { /* 没这字段 */ }
      },
      onComplete: function () {
        var names = Object.keys(owners);
        console.log('[SCAN] field_conversationTime 直属类 = ' + names.length + ' -> ' + names.join(', '));
        names.forEach(function (cn) {
          try {
            Java.choose(cn, {
              onMatch: function (inst) {
                try {
                  var v = inst.field_conversationTime.value;
                  var n = parseInt('' + v, 10);
                  if (n > 0) {
                    var wall = Date.now();
                    console.log('[CONVTIME] cls=' + cn + ' conv=' + n +
                      ' wall=' + wall + ' delta_ms=' + (wall - n) +
                      ' conv_iso=' + new Date(n).toISOString());
                  }
                } catch (e) {}
              },
              onComplete: function () {}
            });
          } catch (e) { console.log('[choose-err] ' + cn + ' ' + e); }
        });
        console.log('[SCAN] 本轮完成。改墙钟前后各跑一次对比。');
      }
    });
  }

  console.log('[READY] read_convtime_official 已就绪。每 4s 扫一次；先把会话列表滑一下让对象进堆。');
  scanOwners();
  setInterval(scanOwners, 4000);
});
