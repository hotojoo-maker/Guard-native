/**
 * probe_contact_storage.js — 探索 ContactStorage 的方法和字段，看能否遍历所有条目
 */
'use strict';

Java.perform(function () {
    var TAG = '[CS]';
    try {
        var kernelClass = Java.use('com.tencent.mm.kernel.h');
        console.log(TAG + ' kernelClass found');
    } catch (e) { console.log(TAG + ' kernel fail: ' + e); }

    // 枚举 ContactStorage 方法
    try {
        var ContactStorage = Java.use('com.tencent.mm.storage.ContactStorage');
        var methods = ContactStorage.class.getDeclaredMethods();
        console.log(TAG + ' ContactStorage methods:');
        for (var i = 0; i < methods.length; i++) {
            var m = methods[i];
            var params = m.getParameterTypes();
            var pnames = [];
            for (var j = 0; j < params.length; j++) pnames.push(params[j].getName());
            console.log(TAG + '  ' + m.getReturnType().getName() + ' ' + m.getName() + '(' + pnames.join(',') + ')');
        }
    } catch (e) { console.log(TAG + ' ContactStorage fail: ' + e); }

    console.log(TAG + ' done');
});
