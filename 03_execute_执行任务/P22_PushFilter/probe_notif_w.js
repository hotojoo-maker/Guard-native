/**
 * probe_notif_w.js
 * 目标：枚举 notification.w 的所有方法（Handler 子类，通知统一入口）
 * 用法：frida -U -n com.tencent.mm -l probe_notif_w.js
 */
'use strict';

try {
    var NotifW = Java.use('com.tencent.mm.booter.notification.w');
    var methods = NotifW.class.getDeclaredMethods();
    console.log('[NW] notification.w methods:');
    methods.forEach(function(m) {
        var params = Array.from(m.getParameterTypes()).map(function(t) {
            return t.getName();
        }).join(', ');
        var ret = m.getReturnType().getName();
        console.log('  ' + ret + ' ' + m.getName() + '(' + params + ')');
    });

    // also show superclass
    var sup = NotifW.class.getSuperclass();
    console.log('[NW] superclass: ' + sup.getName());

    // and interfaces
    var ifaces = NotifW.class.getInterfaces();
    ifaces.forEach(function(i) {
        console.log('[NW] implements: ' + i.getName());
    });
} catch(e) {
    console.log('[NW] not found: ' + e);
}

console.log('[NW] done');
