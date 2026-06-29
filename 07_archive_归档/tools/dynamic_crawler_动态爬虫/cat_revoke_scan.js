'use strict';
/**
 * 抓 Catfish 撤回 — hook WmyRevokeMsg.revoke() 看参数和方法签名
 */
Java.perform(function () {
    var found = false;
    Java.enumerateLoadedClasses({
        onMatch: function (name) {
            if (name.indexOf('WmyRevokeMsg') < 0 && name.indexOf('RevokeMsg') < 0
                && name.indexOf('revoke') < 0) return;
            try {
                var cls = Java.use(name);
                var methods = cls.class.getDeclaredMethods();
                console.log('[CAT] class=' + name + ' methods=' + methods.length);
                methods.forEach(function (m) {
                    console.log('  ' + m.getName() + ' ' + m.getReturnType().getName()
                        + ' params=' + m.getParameterTypes().length);
                });
                found = true;
            } catch (e) {}
        },
        onComplete: function () {
            console.log('[CAT] scan done found=' + found);
        }
    });
});
