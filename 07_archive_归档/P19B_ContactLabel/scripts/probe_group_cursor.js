/**
 * probe_group_cursor.js — 轻量：hook CursorAdapter.swapCursor 抓 @chatroom Cursor
 */
'use strict';

Java.perform(function () {
    var TAG = '[GC]';

    var CursorAdapter = Java.use('android.widget.CursorAdapter');
    CursorAdapter.swapCursor.implementation = function (cursor) {
        if (cursor && cursor.getCount() > 0) {
            // check first row for @chatroom
            if (cursor.moveToFirst()) {
                var ci = cursor.getColumnIndex('field_username');
                if (ci >= 0) {
                    var uname = cursor.getString(ci);
                    if (uname && uname.indexOf('@chatroom') >= 0) {
                        console.log(TAG + ' ===== group cursor sz=' + cursor.getCount() + ' =====');
                        // dump column names
                        var cols = cursor.getColumnNames();
                        console.log(TAG + ' cols: ' + cols.join(', '));
                        // dump first 5 rows
                        cursor.moveToPosition(-1);
                        var n = 0;
                        while (cursor.moveToNext() && n < 5) {
                            var row = [];
                            for (var c = 0; c < cols.length; c++) {
                                try {
                                    row.push(cols[c] + '=' + String(cursor.getString(c)));
                                } catch (e) {
                                    row.push(cols[c] + '=ERR');
                                }
                            }
                            console.log(TAG + ' [' + n + '] ' + row.join(' | '));
                            n++;
                        }
                        // print stack
                        var trace = Java.use('java.lang.Throwable').$new();
                        var stack = trace.getStackTrace();
                        for (var k = 0; k < Math.min(stack.length, 10); k++) {
                            console.log(TAG + '  #' + k + ' ' + stack[k].getClassName() + '.' + stack[k].getMethodName());
                        }
                    }
                }
            }
        }
        return this.swapCursor(cursor);
    };

    console.log(TAG + ' ready — 重新进通讯录→群聊');
});
