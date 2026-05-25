/**
 * probe_dump_groups.js — 直接读 ChatroomContactUI adapter 数据，dump 所有群 ID
 */
'use strict';

Java.perform(function () {
    var TAG = '[QDG]';
    Java.choose('com.tencent.mm.ui.contact.ChatroomContactUI', {
        onMatch: function (instance) {
            try {
                var mField = instance.getClass().getDeclaredField('m');
                mField.setAccessible(true);
                var adapter = mField.get(instance); // com.tencent.mm.ui.contact.s0
                if (!adapter) { console.log(TAG + ' adapter=null'); return; }
                var count = adapter.getCount();
                console.log(TAG + ' adapter count=' + count);
                for (var i = 0; i < count; i++) {
                    var item = adapter.d(i); // getItem
                    if (!item) { console.log(TAG + ' [' + i + '] null'); continue; }
                    var cn = item.getClass().getName();
                    // try u() method for group ID
                    var gid = '';
                    try { gid = String(adapter.u(i)); } catch (e2) {}
                    console.log(TAG + ' [' + i + '] class=' + cn + ' u()=' + gid);
                    // dump item fields for first 2
                    if (i < 2) dumpFields(item);
                }
            } catch (e) { console.log(TAG + ' err: ' + e); }
        },
        onComplete: function () { console.log(TAG + ' done'); }
    });

    function dumpFields(obj) {
        var cls = obj.getClass();
        while (cls && cls.getName() !== 'java.lang.Object') {
            var fields = cls.getDeclaredFields();
            for (var i = 0; i < fields.length; i++) {
                try {
                    fields[i].setAccessible(true);
                    var v = fields[i].get(obj);
                    var val = v === null ? 'null' : String(v).substring(0, 100);
                    console.log(TAG + '  ' + cls.getName() + '.' + fields[i].getName() + ' = ' + val);
                } catch (e2) {}
            }
            cls = cls.getSuperclass();
        }
    }
});
