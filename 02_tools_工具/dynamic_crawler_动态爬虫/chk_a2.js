'use strict';
Java.perform(function () {
    var cls = Java.use('com.tencent.mm.plugin.messenger.foundation.a2');
    var methods = cls.class.getDeclaredMethods();
    console.log('[A2] 8.0.71 methods=' + methods.length);
    for (var i = 0; i < methods.length; i++) {
        var m = methods[i];
        var pts = m.getParameterTypes();
        var sig = m.getName() + ' → ' + m.getReturnType().getName() + '  params=' + pts.length;
        for (var j = 0; j < pts.length; j++) sig += '\n    ' + j + ': ' + pts[j].getName();
        console.log(sig);
    }
});
