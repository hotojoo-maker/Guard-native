/**
 * probe_group_data.js — dump ChatroomContactUI 列表数据项类 + 字段
 * 只读不拦截，不会触发异常
 */
'use strict';

Java.perform(function () {
    var TAG = '[GDAT]';
    var done = false;

    // 全局 ArrayList.addAll — 找到群列表数据项类
    var ArrayList = Java.use('java.util.ArrayList');
    ArrayList.addAll.overload('java.util.Collection').implementation = function (c) {
        if (done || !c || c.isEmpty()) return this.addAll(c);
        var it = c.iterator();
        var first = it.next();
        if (!first) return this.addAll(c);
        var cn = first.getClass().getName();
        // 关注可能的群列表项类名
        if (!/contact|chatroom|group|ye5|fc5|z15|na4|la4|i84/i.test(cn)) return this.addAll(c);

        // 检查是否在 ChatroomContactUI 可见时触发
        var trace = Java.use('java.lang.Throwable').$new();
        var stack = trace.getStackTrace();
        var inChatroom = false;
        for (var i = 0; i < Math.min(stack.length, 20); i++) {
            if (String(stack[i].getClassName()).indexOf('ChatroomContactUI') >= 0) {
                inChatroom = true; break;
            }
        }
        if (!inChatroom) return this.addAll(c);

        done = true;
        console.log(TAG + ' ChatroomContactUI addAll class=' + cn + ' sz=' + c.size());

        // dump 前 2 项的全部字段
        var it2 = c.iterator();
        for (var n = 0; n < Math.min(2, c.size()); n++) {
            var item = it2.next();
            if (!item) continue;
            console.log(TAG + ' === item[' + n + '] class=' + item.getClass().getName() + ' ===');
            dumpAllFields(item);
        }
        return this.addAll(c);
    };

    function dumpAllFields(obj) {
        var cls = obj.getClass();
        while (cls && cls.getName() !== 'java.lang.Object') {
            var fields = cls.getDeclaredFields();
            for (var i = 0; i < fields.length; i++) {
                try {
                    fields[i].setAccessible(true);
                    var v = fields[i].get(obj);
                    var type = fields[i].getType().getName();
                    var val = v === null ? 'null' : String(v).substring(0, Math.min(String(v).length, 120));
                    console.log(TAG + '  [' + type + '] ' + cls.getName() + '.' + fields[i].getName() + ' = ' + val);
                } catch (e2) {}
            }
            cls = cls.getSuperclass();
        }
    }

    console.log(TAG + ' ready — 请进通讯录→群聊，列表数据字段会自动打印');
});
