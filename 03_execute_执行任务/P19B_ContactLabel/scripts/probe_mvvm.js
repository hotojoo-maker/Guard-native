/**
 * probe_mvvm.js — 探 MvvmContactListUI
 */
'use strict';

Java.perform(function () {
    var TAG = '[MVVM]';
    Java.choose('com.tencent.mm.ui.mvvm.MvvmContactListUI', {
        onMatch: function (inst) {
            console.log(TAG + ' found');
            var cls = inst.getClass();
            var fields = cls.getDeclaredFields();
            for (var i = 0; i < fields.length; i++) {
                try {
                    fields[i].setAccessible(true);
                    var v = fields[i].get(inst);
                    var t = fields[i].getType().getName();
                    var val = v === null ? 'null' : String(v).substring(0, 200);
                    console.log(TAG + ' [' + t + '] ' + fields[i].getName() + ' = ' + val);
                    // recurse non-null interesting objects
                    if (v && t.indexOf('com.tencent') >= 0 && t.indexOf('String') < 0 && t.indexOf('int') < 0 && t.indexOf('boolean') < 0) {
                        dumpFieldsShort(v, TAG + '  .');
                    }
                } catch (e) {}
            }
        },
        onComplete: function () { console.log(TAG + ' done'); }
    });

    function dumpFieldsShort(obj, prefix) {
        var cls = obj.getClass();
        var c = 0;
        while (cls && cls.getName() !== 'java.lang.Object' && c < 2) {
            var fields = cls.getDeclaredFields();
            for (var i = 0; i < Math.min(fields.length, 15); i++) {
                try {
                    fields[i].setAccessible(true);
                    var v = fields[i].get(obj);
                    var t = fields[i].getType().getName();
                    var val = v === null ? 'null' : String(v).substring(0, 100);
                    console.log(prefix + '[' + t + '] ' + cls.getName() + '.' + fields[i].getName() + ' = ' + val);
                } catch (e) {}
            }
            cls = cls.getSuperclass();
            c++;
        }
    }

    console.log(TAG + ' go');
});
