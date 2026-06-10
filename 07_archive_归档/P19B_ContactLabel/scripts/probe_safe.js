/**
 * probe_safe.js — 零崩溃：只 hook onResume，延时后用 Java.choose 读 adapter cursor
 */
'use strict';

Java.perform(function () {
    var TAG = '[SAFE]';

    var ChatroomContactUI = Java.use('com.tencent.mm.ui.contact.ChatroomContactUI');
    ChatroomContactUI.onResume.implementation = function () {
        console.log(TAG + ' onResume');
        var self = this;
        // wait for data to load (500ms)
        Java.scheduleOnMainThread(function () {
            try {
                var mField = self.getClass().getDeclaredField('m');
                mField.setAccessible(true);
                var adapter = mField.get(self);
                if (!adapter) { console.log(TAG + ' adapter null'); return; }
                console.log(TAG + ' adapter=' + adapter.getClass().getName());

                // try getCursor()
                try {
                    var cursor = adapter.getCursor();
                    if (cursor) {
                        console.log(TAG + ' cursor count=' + cursor.getCount());
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
                    } else {
                        console.log(TAG + ' cursor null');
                    }
                } catch (e) {
                    console.log(TAG + ' getCursor err: ' + e);
                }
            } catch (e) {
                console.log(TAG + ' err: ' + e);
            }
        });
        return this.onResume();
    };

    console.log(TAG + ' ready — 进通讯录→群聊');
});
