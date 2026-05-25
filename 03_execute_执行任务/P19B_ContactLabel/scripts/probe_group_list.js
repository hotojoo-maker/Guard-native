/**
 * probe_group_list.js — 专抓 ChatroomContactUI 加载的群列表数据项
 * 已知群数据类 com.tencent.mm.storage.l4 (field_username = xxx@chatroom)
 */
'use strict';

Java.perform(function () {
    var TAG = '[GL]';

    // 全局 addAll — 从 ChatroomContactUI 触发的不限制类名
    var ArrayList = Java.use('java.util.ArrayList');
    ArrayList.addAll.overload('java.util.Collection').implementation = function (c) {
        if (!c || c.isEmpty()) return this.addAll(c);
        var it = c.iterator();
        var first = it.next();
        if (!first) return this.addAll(c);
        var cn = first.getClass().getName();

        var trace = Java.use('java.lang.Throwable').$new();
        var stack = trace.getStackTrace();
        var fromChatroom = false;
        for (var i = 0; i < Math.min(stack.length, 30); i++) {
            var frame = String(stack[i].getClassName());
            if (frame.indexOf('ChatroomContactUI') >= 0) {
                fromChatroom = true; break;
            }
        }
        if (!fromChatroom) return this.addAll(c);

        console.log(TAG + ' ChatroomContactUI → ' + cn + ' sz=' + c.size());

        var it2 = c.iterator();
        var count = 0;
        while (it2.hasNext() && count < 5) {
            var item = it2.next();
            if (!item) continue;
            // 只读 field_username
            try {
                var f = item.getClass().getDeclaredField('field_username');
                f.setAccessible(true);
                var uname = f.get(item);
                if (uname && String(uname).indexOf('@chatroom') >= 0) {
                    console.log(TAG + ' ★ group: ' + uname);
                    count++;
                }
            } catch (e2) {
                // fallback: dump all fields for this item
                dumpFields(item);
                count++;
            }
        }
        return this.addAll(c);
    };

    function dumpFields(obj) {
        var cls = obj.getClass();
        while (cls && cls.getName() !== 'java.lang.Object') {
            var fields = cls.getDeclaredFields();
            for (var i = 0; i < fields.length; i++) {
                try {
                    fields[i].setAccessible(true);
                    var v = fields[i].get(obj);
                    var val = v === null ? 'null' : String(v).substring(0, 80);
                    console.log(TAG + '  ' + cls.getName() + '.' + fields[i].getName() + ' = ' + val);
                } catch (e2) {}
            }
            cls = cls.getSuperclass();
        }
    }

    console.log(TAG + ' ready — 请进通讯录→群聊，群列表加载后输出群ID');
});
