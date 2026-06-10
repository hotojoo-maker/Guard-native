/**
 * probe_poll.js — 每2秒轮询 ChatroomContactUI adapter
 */
'use strict';

Java.perform(function () {
    var TAG = '[POLL]';
    var done = false;

    var interval = setInterval(function () {
        if (done) {
            clearInterval(interval);
            return;
        }
        Java.choose('com.tencent.mm.ui.contact.ChatroomContactUI', {
            onMatch: function (inst) {
                try {
                    var f = inst.getClass().getDeclaredField('m');
                    f.setAccessible(true);
                    var a = f.get(inst);
                    if (!a) return;
                    var cursor = a.getCursor();
                    if (!cursor || cursor.getCount() === 0) return;

                    done = true;
                    console.log(TAG + ' FOUND! count=' + cursor.getCount());
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
                } catch (e) {}
            },
            onComplete: function () {}
        });
    }, 2000);

    console.log(TAG + ' polling started — 进通讯录→群聊');
});
