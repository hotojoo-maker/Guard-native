/**
 * probe_adapter_ctor2.js — 最小化版本，只抓 s0 构造参数
 */
'use strict';

Java.perform(function () {
    var TAG = '[S0]';

    var s0 = Java.use('com.tencent.mm.ui.contact.s0');
    s0.$init.overloads.forEach(function (ov, idx) {
        ov.implementation = function () {
            console.log(TAG + ' ctor[' + idx + '] args=' + arguments.length);
            for (var i = 0; i < arguments.length; i++) {
                var arg = arguments[i];
                if (arg === null) {
                    console.log(TAG + '  arg[' + i + '] = null');
                } else {
                    console.log(TAG + '  arg[' + i + '] = ' + arg.getClass().getName());
                }
            }
            return ov.implementation.apply(this, arguments);
        };
    });
    console.log(TAG + ' hooked ' + s0.$init.overloads.length + ' overload(s)');

    // Also hook h11.u constructor
    try {
        var h11u = Java.use('h11.u');
        h11u.$init.overloads.forEach(function (ov, idx) {
            ov.implementation = function () {
                console.log(TAG + ' h11.u ctor[' + idx + '] args=' + arguments.length);
                for (var i = 0; i < arguments.length; i++) {
                    var arg = arguments[i];
                    if (arg === null) {
                        console.log(TAG + '  [h11.u] arg[' + i + '] = null');
                    } else {
                        var cn = arg.getClass ? arg.getClass().getName() : typeof arg;
                        console.log(TAG + '  [h11.u] arg[' + i + '] = ' + cn);
                    }
                }
                return ov.implementation.apply(this, arguments);
            };
        });
        console.log(TAG + ' h11.u ctor hooked (' + h11u.$init.overloads.length + ')');
    } catch (e) {
        console.log(TAG + ' h11.u fail: ' + e);
    }

    console.log(TAG + ' ready — 退出群聊页→重新进入');
});
