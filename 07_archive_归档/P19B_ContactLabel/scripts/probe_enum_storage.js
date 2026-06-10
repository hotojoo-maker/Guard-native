/**
 * probe_enum_storage.js — 枚举已加载类中与 contact/storage/chatroom 相关的类
 */
'use strict';

Java.perform(function () {
    var TAG = '[ENUM]';
    Java.enumerateLoadedClasses({
        onMatch: function (cls) {
            if (/contact|chatroom|storage|conversation/i.test(cls)) {
                console.log(TAG + ' ' + cls);
            }
        },
        onComplete: function () {
            console.log(TAG + ' === DONE ===');
        }
    });
});
