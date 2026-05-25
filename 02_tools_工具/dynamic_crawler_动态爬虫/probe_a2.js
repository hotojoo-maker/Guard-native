'use strict';
/**
 * Hook a2. {a,b,c} — 撤回时哪个触发 + 参数
 */
Java.perform(function () {
    var a2 = Java.use('com.tencent.mm.plugin.messenger.foundation.a2');
    var Exception = Java.use('java.lang.Exception');
    var Log = Java.use('android.util.Log');

    var methods = a2.class.getDeclaredMethods();
    for (var i = 0; i < methods.length; i++) {
        var m = methods[i];
        m.setAccessible(true);
        (function (mName, paramTypes) {
            var overloads = a2[mName].overloads;
            for (var j = 0; j < overloads.length; j++) {
                (function (ov) {
                    ov.implementation = function () {
                        var sig = '[A2:' + mName + ']';
                        for (var k = 0; k < arguments.length; k++) {
                            sig += ' a' + k + '=' + arguments[k];
                        }
                        console.log(sig);
                        console.log(Log.getStackTraceString(Exception.$new('p')).split('\n').slice(0, 10).join('\n'));
                        return ov.apply(this, arguments);
                    };
                })(overloads[j]);
            }
        })(m.getName());
    }
    console.log('[probe] a2 hooked OK. 发消息→撤回，看 [A2:.] 行');
});
