// probe_notif_v2.js — 最小验证：NotificationItem.a + x 所有 d 重载
// 用法：frida -U -p <微信主进程pid> -l tools/probe_notif_v2.js

(function () {
    'use strict';
    var ts = function () {
        var d = new Date(), p = function (n) { return n < 10 ? '0' + n : '' + n; };
        return p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds());
    };
    var TARGET = 'wxid_lzd2va16jd1622';

    function s(obj) {
        if (obj === null || obj === undefined) return 'null';
        try { var r = String(obj); return r.length > 120 ? r.substring(0, 120) + '...' : r; } catch (e) { return '<err>'; }
    }

    Java.perform(function () {
        var pkg = 'com.tencent.mm.booter.notification';

        // ── 1. hook NotificationItem.a(Context) ──
        try {
            var NI = Java.use(pkg + '.NotificationItem');
            NI.a.implementation = function (ctx) {
                var hVal = '?';
                try { hVal = s(this.h.value); } catch (e) {}
                var isTarget = (hVal === TARGET || hVal.indexOf(TARGET) >= 0);
                var toStr = '?';
                try { toStr = s(this.toString()); } catch (e) {}

                console.log(ts() + ' [NI.a] this.h=' + hVal +
                    (isTarget ? ' ★MATCH★' : '') +
                    ' | toString=' + toStr);
                return this.a(ctx);
            };
            console.log(ts() + ' [OK] NotificationItem.a hooked');
        } catch (e) {
            console.log(ts() + ' [FAIL] NI.a: ' + e);
        }

        // ── 2. 枚举 x 所有 d 方法，然后 hook ──
        try {
            var X = Java.use(pkg + '.x');
            var allMethods = X.class.getDeclaredMethods();
            var dMethods = [];
            for (var i = 0; i < allMethods.length; i++) {
                var m = allMethods[i];
                if (m.getName() !== 'd') continue;
                var p = m.getParameterTypes();
                var pNames = [];
                for (var j = 0; j < p.length; j++) pNames.push(p[j].getName());
                var mod = m.getModifiers();
                var isStatic = (mod & 0x8) !== 0;
                dMethods.push({
                    params: pNames,
                    ret: m.getReturnType().getName(),
                    static: isStatic,
                    sig: (isStatic ? 'static ' : '') + 'd(' + pNames.join(', ') + ') : ' + m.getReturnType().getName()
                });
            }
            console.log(ts() + ' [x] ' + dMethods.length + ' d() overload(s):');
            for (var k = 0; k < dMethods.length; k++) {
                console.log('    [' + k + '] ' + dMethods[k].sig);
            }

            // hook 每一个 d 重载
            for (var k2 = 0; k2 < dMethods.length; k2++) {
                var dm = dMethods[k2];
                (function (idx, params, isStatic) {
                    try {
                        var overloadArgs = params.map(function(pn) { return pn; });
                        // build overload
                        var hookTarget = X.d;
                        for (var pi = 0; pi < params.length; pi++) {
                            hookTarget = hookTarget.overload.apply(hookTarget, params);
                            break; // only first param in this loop pattern...
                        }
                        // Actually Frida overload chain: X.d.overload('p0', 'p1', ...)
                        if (params.length === 0) {
                            hookTarget = X.d;
                        } else {
                            // build the overload chain properly
                            var overloadStr = 'X.d';
                            for (var pi2 = 0; pi2 < params.length; pi2++) {
                                overloadStr += '.overload(\'' + params[pi2].replace(/'/g, '\\\'') + '\')';
                            }
                            // Use eval as last resort for dynamic overload count
                            hookTarget = eval(overloadStr);
                        }
                        hookTarget.implementation = function () {
                            var parts = [];
                            var isMatch = false;
                            for (var ai = 0; ai < arguments.length; ai++) {
                                var v = s(arguments[ai]);
                                parts.push('arg' + ai + '=' + v);
                                if (v.indexOf(TARGET) >= 0) isMatch = true;
                            }
                            console.log(ts() + ' [x.d#' + idx + '] ' + parts.join(' | ') + (isMatch ? ' ★WXID★' : ''));
                            return this.d.apply(this, arguments);
                        };
                        console.log(ts() + ' [OK] x.d#' + idx + ' hooked');
                    } catch (e) {
                        console.log(ts() + ' [FAIL] x.d#' + idx + ': ' + e);
                    }
                })(k2, dm.params, dm.static);
            }
        } catch (e) {
            console.log(ts() + ' [FAIL] x class: ' + e);
        }

        console.log(ts() + ' === 就绪，切后台让密友发消息 ===');
    });
})();
