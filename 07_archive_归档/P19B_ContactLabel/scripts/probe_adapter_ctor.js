/**
 * probe_adapter_ctor.js — hook s0 adapter 构造函数 + 所有方法，抓数据来源
 */
'use strict';

Java.perform(function () {
    var TAG = '[S0]';

    try {
        var s0 = Java.use('com.tencent.mm.ui.contact.s0');
        var constructors = s0.class.getDeclaredConstructors();
        console.log(TAG + ' s0 constructors: ' + constructors.length);

        // Hook all constructors
        s0.$init.overloads.forEach(function (ov) {
            ov.implementation = function () {
                console.log(TAG + ' ===== s0 ctor args=' + arguments.length + ' =====');
                for (var i = 0; i < arguments.length; i++) {
                    var arg = arguments[i];
                    var desc = arg === null ? 'null' : arg.getClass().getName() + ' = ' + String(arg).substring(0, 200);
                    console.log(TAG + '  arg[' + i + '] ' + desc);
                    // dump arg fields if it looks like a data source
                    if (arg && typeof arg === 'object' && !String(arg.getClass().getName()).startsWith('android.')) {
                        var cls = arg.getClass();
                        while (cls && cls.getName() !== 'java.lang.Object') {
                            var fields = cls.getDeclaredFields();
                            for (var j = 0; j < fields.length; j++) {
                                try {
                                    fields[j].setAccessible(true);
                                    var v = fields[j].get(arg);
                                    var t = fields[j].getType().getName();
                                    var val = v === null ? 'null' : String(v).substring(0, 100);
                                    console.log(TAG + '   [' + t + '] ' + cls.getName() + '.' + fields[j].getName() + ' = ' + val);
                                } catch (e) {}
                            }
                            cls = cls.getSuperclass();
                        }
                    }
                }
                // Also print stack trace
                var trace = Java.use('java.lang.Throwable').$new();
                var stack = trace.getStackTrace();
                for (var k = 0; k < Math.min(stack.length, 12); k++) {
                    console.log(TAG + '  #' + k + ' ' + stack[k].getClassName() + '.' + stack[k].getMethodName() + ':' + stack[k].getLineNumber());
                }
                return ov.implementation.apply(this, arguments);
            };
        });
        console.log(TAG + ' s0 ctor hooks installed (' + s0.$init.overloads.length + ')');
    } catch (e) {
        console.log(TAG + ' s0 ctor fail: ' + e);
    }

    // Also hook s0 instance methods — getCount, d (getItem), u (getGroupId)
    try {
        var s0 = Java.use('com.tencent.mm.ui.contact.s0');
        // Hook d(int) — the getItem method
        if (s0.d) {
            s0.d.overload('int').implementation = function (pos) {
                var item = this.d(pos);
                if (pos < 2 && item) {
                    console.log(TAG + ' d(' + pos + ') → ' + item.getClass().getName());
                    var cls = item.getClass();
                    while (cls && cls.getName() !== 'java.lang.Object') {
                        var fields = cls.getDeclaredFields();
                        for (var j = 0; j < fields.length; j++) {
                            try {
                                fields[j].setAccessible(true);
                                var v = fields[j].get(item);
                                var t = fields[j].getType().getName();
                                var val = v === null ? 'null' : String(v).substring(0, 100);
                                console.log(TAG + '  [' + t + '] ' + cls.getName() + '.' + fields[j].getName() + ' = ' + val);
                            } catch (e) {}
                        }
                        cls = cls.getSuperclass();
                    }
                }
                return item;
            };
            console.log(TAG + ' s0.d(int) hooked');
        }
    } catch (e) {
        console.log(TAG + ' s0.d hook fail: ' + e);
    }

    // Hook h11.u — might be the data source for the adapter
    try {
        var h11u = Java.use('h11.u');
        console.log(TAG + ' h11.u class found');
    } catch (e) {
        console.log(TAG + ' h11.u not found: ' + e);
    }

    console.log(TAG + ' ready — 请退出群聊页→重新进入触发 adapter 创建');
});
