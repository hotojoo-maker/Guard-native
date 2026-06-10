/**
 * probe_adapter_ctor3.js — 安全版，每个参数独立 try-catch
 */
'use strict';

Java.perform(function () {
    var TAG = '[S0]';
    var Object = Java.use('java.lang.Object');

    function safeStr(x) {
        if (x === null || x === undefined) return 'null';
        try {
            if (typeof x === 'number') return 'int: ' + x;
            if (typeof x === 'boolean') return 'bool: ' + x;
            if (typeof x === 'string') return 'String: ' + x;
            return x.getClass().getName();
        } catch (e) {
            return '?? ' + typeof x + ' / ' + e.message;
        }
    }

    var s0 = Java.use('com.tencent.mm.ui.contact.s0');
    s0.$init.overloads.forEach(function (ov, idx) {
        ov.implementation = function () {
            console.log(TAG + ' ctor[' + idx + '] args=' + arguments.length);
            for (var i = 0; i < arguments.length; i++) {
                try {
                    console.log(TAG + '  arg[' + i + '] = ' + safeStr(arguments[i]));
                } catch (ee) {
                    console.log(TAG + '  arg[' + i + '] ERR: ' + ee);
                }
            }
            return ov.implementation.apply(this, arguments);
        };
    });
    console.log(TAG + ' s0 hooked');

    // Also hook getCount and getItem for later use
    try {
        s0.getCount.implementation = function () {
            var c = this.getCount();
            console.log(TAG + ' getCount()=' + c);
            return c;
        };
        console.log(TAG + ' s0.getCount hooked');
    } catch (e) {}

    console.log(TAG + ' ready');
});
