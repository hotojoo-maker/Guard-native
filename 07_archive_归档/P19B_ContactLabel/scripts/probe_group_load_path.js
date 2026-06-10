/**
 * probe_group_load_path.js — hook ArrayList.addAll，按 @chatroom 数据特征抓群加载路径
 * 不过滤调用栈，只过滤数据项类 = l4 (field_username = xxx@chatroom)
 */
'use strict';

Java.perform(function () {
    var TAG = '[GLP]';
    var logged = {};

    var ArrayList = Java.use('java.util.ArrayList');
    ArrayList.addAll.overload('java.util.Collection').implementation = function (c) {
        if (!c || c.isEmpty()) return this.addAll(c);

        var it = c.iterator();
        var first = it.next();
        if (!first) return this.addAll(c);
        var cn = first.getClass().getName();

        // 只关注 l4 (群数据类)
        if (cn !== 'com.tencent.mm.storage.l4') return this.addAll(c);

        var dedupKey = cn + '|' + c.size();
        if (logged[dedupKey]) return this.addAll(c);

        logged[dedupKey] = true;
        console.log(TAG + ' ===== l4 addAll sz=' + c.size() + ' =====');

        // dump 调用栈前 15 帧
        var trace = Java.use('java.lang.Throwable').$new();
        var stack = trace.getStackTrace();
        for (var i = 0; i < Math.min(stack.length, 20); i++) {
            console.log(TAG + '  #' + i + ' ' + stack[i].getClassName() + '.' + stack[i].getMethodName() + ':' + stack[i].getLineNumber());
        }

        // dump 前 3 项字段
        var it2 = c.iterator();
        for (var n = 0; n < Math.min(3, c.size()); n++) {
            var item = it2.next();
            if (!item) continue;
            console.log(TAG + ' --- item[' + n + '] ---');
            dumpFields(item);
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
                    var type = fields[i].getType().getName();
                    var val = v === null ? 'null' : String(v).substring(0, 100);
                    console.log(TAG + '  [' + type + '] ' + cls.getName() + '.' + fields[i].getName() + ' = ' + val);
                } catch (e2) {}
            }
            cls = cls.getSuperclass();
        }
    }

    console.log(TAG + ' ready — 请重新进通讯录→群聊，触发数据加载');
});
