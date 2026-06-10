/**
 * probe_min.js — 极简，只 hook CursorAdapter.swapCursor
 */
'use strict';

Java.perform(function () {
    var TAG = '[MIN]';
    var CursorAdapter = Java.use('android.widget.CursorAdapter');
    CursorAdapter.swapCursor.implementation = function (c) {
        if (c && c.getCount() > 0) {
            var cn = c.getColumnNames();
            // check if has field_username
            for (var i = 0; i < cn.length; i++) {
                if (cn[i] === 'field_username') {
                    c.moveToFirst();
                    var fv = c.getString(i);
                    if (fv && fv.indexOf('@chatroom') >= 0) {
                        console.log(TAG + ' GROUP CURSOR sz=' + c.getCount());
                        for (var k = 0; k < Math.min(c.getCount(), 3); k++) {
                            c.moveToPosition(k);
                            console.log(TAG + ' [' + k + '] ' + c.getString(i));
                        }
                    }
                    break;
                }
            }
        }
        return this.swapCursor(c);
    };
    console.log(TAG + ' ready');
});
