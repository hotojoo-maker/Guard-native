'use strict';
/**
 * 直接 hook Catfish WmyRevokeMsg — 抓撤回触发时的参数
 */
Java.perform(function () {
    var Exception = Java.use('java.lang.Exception');
    var Log = Java.use('android.util.Log');
    var clzName = 'com.tencent.mm.plugin.messenger.foundation.a2';

    // 方案1: 直接找 WmyRevokeMsg 类
    try {
        var WmyRevokeMsg = Java.use('com.ghost.assist.WmyRevokeMsg'); // Catfish 包名是 com.tencent.mn1??
        // 不对，这是 Catfish 自己的类，不在微信包里
    } catch (e) {
        console.log('[CAT] WmyRevokeMsg not found via guess');
    }

    // 方案2: hook a2 类 — 之前探针证实它解析 "revokemsg"
    try {
        var a2 = Java.use('com.tencent.mm.plugin.messenger.foundation.a2');
        var methods = a2.class.getDeclaredMethods();
        console.log('[CAT] a2 methods: ' + methods.length);
        for (var i = 0; i < methods.length; i++) {
            var m = methods[i];
            console.log('[CAT]   ' + m.getName() + ' ret=' + m.getReturnType().getName()
                + ' params=' + m.getParameterTypes().length);
            for (var j = 0; j < m.getParameterTypes().length; j++) {
                console.log('[CAT]     p' + j + '=' + m.getParameterTypes()[j].getName());
            }
        }
    } catch (e) {
        console.log('[CAT] a2 FAIL: ' + e);
    }

    // 方案3: 扫描 mn1 包里所有类名含 "revoke" 的
    Java.enumerateLoadedClasses({
        onMatch: function (name) {
            var ln = name.toLowerCase();
            if (ln.indexOf('com.tencent.mn') < 0 && ln.indexOf('com.ghost') < 0) return;
            if (ln.indexOf('revoke') < 0 && ln.indexOf('wmy') < 0 && ln.indexOf('revok') < 0) return;
            console.log('[CAT] FOUND: ' + name);
        },
        onComplete: function () {
            console.log('[CAT] scan done');
        }
    });
});
