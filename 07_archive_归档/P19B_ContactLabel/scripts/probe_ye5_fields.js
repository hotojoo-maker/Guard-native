/**
 * probe_ye5_fields.js — dump ye5.j 标签成员 item 的所有字段（wxid/昵称/备注）
 * 用户进标签 → 点一个标签 → 查看成员列表后触发
 */
'use strict';

Java.perform(function () {
    var TAG = '[YE5]';
    var done = false;

    try {
        var ArrayList = Java.use('java.util.ArrayList');
        ArrayList.addAll.overload('java.util.Collection').implementation = function (c) {
            if (done || !c || c.isEmpty()) return this.addAll(c);
            var it = c.iterator();
            var first = it.next();
            if (!first || first.getClass().getName() !== 'ye5.j') return this.addAll(c);

            done = true;
            console.log(TAG + ' addAll ye5.j sz=' + c.size());

            // dump 前 3 个 item 的所有字段
            var it2 = c.iterator();
            for (var n = 0; n < Math.min(3, c.size()); n++) {
                var item = it2.next();
                if (!item) continue;
                console.log(TAG + ' === item[' + n + '] ===');
                dumpFields(item);
            }

            return this.addAll(c);
        };
        console.log(TAG + ' ready — 请进标签 → 点一个标签看成员');
    } catch (e) {
        console.log(TAG + ' fail: ' + e);
    }

    function dumpFields(obj) {
        var cls = obj.getClass();
        while (cls && cls.getName() !== 'java.lang.Object') {
            var fields = cls.getDeclaredFields();
            for (var i = 0; i < fields.length; i++) {
                try {
                    fields[i].setAccessible(true);
                    var v = fields[i].get(obj);
                    var type = fields[i].getType().getName();
                    var val = v === null ? 'null' : String(v).substring(0, Math.min(String(v).length, 80));
                    console.log(TAG + '  [' + type + '] ' + cls.getName() + '.' + fields[i].getName() + ' = ' + val);
                } catch (e2) {}
            }
            cls = cls.getSuperclass();
        }
    }
});
