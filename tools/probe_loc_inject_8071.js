/**
 * probe_loc_inject_8071.js — 锁定微信定位回调注入点的参数契约（伪装订位 v4）
 *
 * v3 结论：真实坐标源头 = n83.g.onGetLocation(...) → setCenter/定位针；分发链 pz0.h.c → pz0.l.run。
 * 目标：dump onGetLocation / pz0.h.c 的完整签名 + 每个入参类型与值，确认 lat/lng 是哪两个参数。
 *       拿到契约后，正式注入只需在 onGetLocation beforeHook 把 lat/lng 参数替换为伪造值。
 *
 * 用法：
 *   $mmpid = (adb shell "ps -ef | grep ' com.tencent.mm$'") -split '\s+' | Select-Object -Index 1
 *   frida -U -p $mmpid -l tools\probe_loc_inject_8071.js 2>&1 | tee tools\probe_loc_inject_$(Get-Date -f 'HHmmss').log
 *
 * 操作：开 发送位置/附近的人 让地图定位。
 *   [INJ:sig]   方法签名
 *   [INJ:hit]   命中 + 每个入参 (idx:type=value)
 */
Java.perform(function () {
    'use strict';
    var TAG = 'INJ';
    function ts() {
        var d = new Date(), p = function (n) { return n < 10 ? '0' + n : '' + n; };
        return p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds());
    }
    function log(s, m) { console.log(ts() + ' [' + TAG + ':' + s + '] ' + m); }

    function describe(v) {
        if (v === null || v === undefined) return 'null';
        try {
            var cn = v.getClass ? v.getClass().getName() : (typeof v);
            var s;
            try { s = String(v); } catch (e) { s = '<?>'; }
            if (s.length > 80) s = s.substring(0, 80) + '...';
            return cn + '=' + s;
        } catch (e) { return '' + v; }
    }

    // 通用：按"类.方法名"hook 所有重载，dump 每个入参
    function dumpHook(className, methodName) {
        try {
            var C = Java.use(className);
            var ms = C.class.getDeclaredMethods();
            var hooked = 0;
            for (var i = 0; i < ms.length; i++) {
                if (ms[i].getName() !== methodName) continue;
                var pts = ms[i].getParameterTypes();
                var names = []; for (var j = 0; j < pts.length; j++) names.push(pts[j].getName());
                log('sig', className + '.' + methodName + '(' + names.join(',') + ')->'
                    + ms[i].getReturnType().getName());
                (function (paramNames) {
                    try {
                        var ov = C[methodName].overload.apply(C[methodName], paramNames);
                        ov.implementation = function () {
                            var a = Array.prototype.slice.call(arguments);
                            var parts = [];
                            for (var k = 0; k < a.length; k++) {
                                var tn = paramNames[k];
                                var val = (tn === 'float' || tn === 'double' || tn === 'int'
                                    || tn === 'long' || tn === 'boolean') ? a[k] : describe(a[k]);
                                parts.push(k + ':' + tn + '=' + val);
                            }
                            log('hit', className + '.' + methodName + ' [' + parts.join(' | ') + ']');
                            return this[methodName].apply(this, a);
                        };
                        hooked++;
                    } catch (e2) { log('warn', className + '.' + methodName + ' overload fail: ' + e2); }
                })(names);
            }
            log('ok', className + '.' + methodName + ' hooked x' + hooked);
        } catch (e) { log('fail', className + '.' + methodName + ': ' + e); }
    }

    dumpHook('n83.g', 'onGetLocation');
    dumpHook('pz0.h', 'c');

    log('init', '=== v4 探针就绪：开 发送位置/附近的人 定位，看 [INJ:sig]/[INJ:hit] 找 lat/lng 参数 ===');
});
