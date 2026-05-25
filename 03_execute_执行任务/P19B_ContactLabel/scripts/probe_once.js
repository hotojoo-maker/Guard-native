/**
 * probe_once.js — 单次读取，不 hook 任何东西
 */
'use strict';

Java.perform(function () {
    var TAG = '[ONCE]';
    Java.choose('com.tencent.mm.ui.contact.ChatroomContactUI', {
        onMatch: function (inst) {
            try {
                var f = inst.getClass().getDeclaredField('m');
                f.setAccessible(true);
                var a = f.get(inst);
                if (!a) { console.log(TAG + ' adapter null'); return; }
                console.log(TAG + ' adapter=' + a.getClass().getName());
                var cursor = a.getCursor();
                if (!cursor) { console.log(TAG + ' cursor null'); return; }
                console.log(TAG + ' count=' + cursor.getCount());
                var cols = cursor.getColumnNames();
                console.log(TAG + ' cols: ' + cols.join(', '));
                for (var i = 0; i < Math.min(cursor.getCount(), 5); i++) {
                    cursor.moveToPosition(i);
                    var row = [];
                    for (var c = 0; c < cols.length; c++) {
                        try { row.push(cols[c] + '=' + cursor.getString(c)); }
                        catch (e) { row.push(cols[c] + '=?'); }
                    }
                    console.log(TAG + ' [' + i + '] ' + row.join(' | '));
                }
            } catch (e) { console.log(TAG + ' err: ' + e); }
        },
        onComplete: function () { console.log(TAG + ' done'); }
    });
});
