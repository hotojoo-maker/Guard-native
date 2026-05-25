/**
 * probe_masssend.js — 探 MassSendHistoryUI 群发助手页面
 */
'use strict';

Java.perform(function () {
    var TAG = '[MS]';
    Java.choose('com.tencent.mm.plugin.masssend.ui.MassSendHistoryUI', {
        onMatch: function (inst) {
            console.log(TAG + ' found MassSendHistoryUI');
            var cls = inst.getClass();
            var fields = cls.getDeclaredFields();
            for (var i = 0; i < fields.length; i++) {
                try {
                    fields[i].setAccessible(true);
                    var v = fields[i].get(inst);
                    var t = fields[i].getType().getName();
                    var val = v === null ? 'null' : String(v).substring(0, 200);
                    console.log(TAG + ' [' + t + '] ' + fields[i].getName() + ' = ' + val);
                } catch (e) {}
            }
        },
        onComplete: function () { console.log(TAG + ' done'); }
    });

    console.log(TAG + ' ready');
});
