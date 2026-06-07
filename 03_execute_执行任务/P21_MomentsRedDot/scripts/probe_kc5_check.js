'use strict';
/*
 * P21 probe — kc5.a 是否参与朋友圈 + 找 bm/rm 真正的数据字段 (LIGHT)
 * 在 kc5.a / bm / rm 构造器上, 一次性 dump 全部字段(按值: 类名 + 集合 size), 找数据持有字段。
 * 用法: frida -D 609b4b18 -p <PID> -l probe_kc5_check.js
 *   attach 后: 朋友圈停留+点赞; 然后【完全退出朋友圈再重进】互动列表(逼出新构造)。
 * 输出: logcat NCL [K5]
 */
Java.perform(function () {
    var Log = Java.use('android.util.Log');
    function L(s) { try { Log.i('NCL', '[K5]' + s); } catch (e) {} try { console.log('[K5]' + s); } catch (e) {} }

    function dumpFields(obj, tag) {
        try {
            for (var c = obj.getClass(); c != null
                    && c.getName().indexOf('android.') !== 0
                    && c.getName() !== 'java.lang.Object'; c = c.getSuperclass()) {
                var fs = c.getDeclaredFields();
                for (var i = 0; i < fs.length; i++) {
                    var f = fs[i];
                    try { f.setAccessible(true); } catch (e) { continue; }
                    var v; try { v = f.get(obj); } catch (e) { continue; }
                    if (v === null) continue;
                    var vc; try { vc = v.getClass().getName(); } catch (e) { continue; }
                    var desc = vc;
                    try {
                        if (vc.indexOf('java.util') === 0 || vc.indexOf('List') >= 0
                                || vc.indexOf('Linked') >= 0 || vc.indexOf('Array') >= 0) {
                            desc = vc + '(size=' + v.size() + ')';
                        }
                    } catch (e) {}
                    L('  ' + tag + '.' + f.getName() + ':' + f.getType().getSimpleName() + ' = ' + desc);
                }
            }
        } catch (e) { L('dump err ' + e); }
    }

    ['kc5.a', 'com.tencent.mm.plugin.sns.ui.bm', 'com.tencent.mm.plugin.sns.ui.rm'].forEach(function (cn) {
        try {
            var C = Java.use(cn);
            var sn = cn.split('.').pop();
            try {
                C.$init.overloads.forEach(function (o) {
                    o.implementation = function () {
                        var r = o.apply(this, arguments);
                        try { L(cn + ' <ctor> fields:'); dumpFields(this, sn); } catch (e) {}
                        return r;
                    };
                });
            } catch (e) {}
            L('hooked ctor ' + cn);
        } catch (e) { L('skip ' + cn + ' : ' + e); }
    });
    L('ready — 朋友圈停留+点赞; 然后完全退出朋友圈再重进互动列表');
});
