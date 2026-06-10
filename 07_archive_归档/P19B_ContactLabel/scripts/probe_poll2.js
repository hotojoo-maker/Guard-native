/**
 * probe_poll2.js — verbose polling, logs every attempt
 */
'use strict';

var TAG = '[P2]';
var done = false;

setInterval(function () {
    if (done) return;
    Java.perform(function () {
        var found = false;
        Java.choose('com.tencent.mm.ui.contact.ChatroomContactUI', {
            onMatch: function (inst) {
                found = true;
                try {
                    var f = inst.getClass().getDeclaredField('m');
                    f.setAccessible(true);
                    var a = f.get(inst);
                    if (!a) { console.log(TAG + ' instance ok, adapter=null'); return; }
                    console.log(TAG + ' adapter=' + a.getClass().getName());
                    var cursor = a.getCursor();
                    if (!cursor) { console.log(TAG + ' cursor=null'); return; }
                    var c = cursor.getCount();
                    console.log(TAG + ' cursor count=' + c);
                    if (c === 0) return;

                    done = true;
                    var cols = cursor.getColumnNames();
                    console.log(TAG + ' cols: ' + cols.join(', '));
                    for (var i = 0; i < Math.min(c, 5); i++) {
                        cursor.moveToPosition(i);
                        var row = [];
                        for (var ci = 0; ci < cols.length; ci++) {
                            try { row.push(cols[ci] + '=' + cursor.getString(ci)); }
                            catch (e) { row.push(cols[ci] + '=?'); }
                        }
                        console.log(TAG + ' [' + i + '] ' + row.join(' | '));
                    }
                } catch (e) {
                    console.log(TAG + ' err: ' + e.message);
                }
            },
            onComplete: function () {
                if (!found) console.log(TAG + ' no ChatroomContactUI instance');
                else console.log(TAG + ' scan done, found=' + found);
            }
        });
    });
}, 3000);

console.log(TAG + ' polling started (3s interval)');
