/**
 * probe_query_groups.js — 反射查群聊列表
 * 尝试通过 WeChat kernel 获取 ConversationStorage，遍历 @chatroom 会话
 */
'use strict';

Java.perform(function () {
    var TAG = '[QG]';

    // 枚举 l4 (conversation) 已加载实例中的 @chatroom
    try {
        var l4 = Java.use('com.tencent.mm.storage.l4');
        console.log(TAG + ' l4 found');
    } catch (e) { console.log(TAG + ' l4 not found: ' + e); }

    // 尝试通过 ChatroomContactUI 的 ListView adapter 直接读数据
    try {
        Java.choose('com.tencent.mm.ui.contact.ChatroomContactUI', {
            onMatch: function (instance) {
                console.log(TAG + ' ChatroomContactUI instance found');
                // 读字段 h (h11.u) — 可能是 adapter
                try {
                    var hField = instance.getClass().getDeclaredField('h');
                    hField.setAccessible(true);
                    var hVal = hField.get(instance);
                    console.log(TAG + ' h=' + hVal + ' class=' + (hVal ? hVal.getClass().getName() : 'null'));
                    // dump hVal methods
                    if (hVal) {
                        var methods = hVal.getClass().getDeclaredMethods();
                        for (var i = 0; i < Math.min(methods.length, 20); i++) {
                            console.log(TAG + ' h.' + methods[i].getName());
                        }
                    }
                } catch (e2) { console.log(TAG + ' h field err: ' + e2); }

                // 读字段 m (com.tencent.mm.ui.contact.s0)
                try {
                    var mField = instance.getClass().getDeclaredField('m');
                    mField.setAccessible(true);
                    var mVal = mField.get(instance);
                    console.log(TAG + ' m=' + mVal + ' class=' + (mVal ? mVal.getClass().getName() : 'null'));
                    if (mVal) {
                        var methods = mVal.getClass().getDeclaredMethods();
                        for (var i = 0; i < Math.min(methods.length, 30); i++) {
                            console.log(TAG + ' m.' + methods[i].getName() + '() → ' + methods[i].getReturnType().getName());
                        }
                    }
                } catch (e2) { console.log(TAG + ' m field err: ' + e2); }

                // 读 ListView d
                try {
                    var dField = instance.getClass().getDeclaredField('d');
                    dField.setAccessible(true);
                    var lv = dField.get(instance);
                    if (lv) {
                        var adapter = lv.getAdapter();
                        console.log(TAG + ' ListView adapter=' + (adapter ? adapter.getClass().getName() : 'null'));
                        if (adapter) {
                            console.log(TAG + ' adapter count=' + adapter.getCount());
                            if (adapter.getCount() > 0) {
                                var item0 = adapter.getItem(0);
                                console.log(TAG + ' item[0]=' + item0 + ' class=' + (item0 ? item0.getClass().getName() : 'null'));
                                if (item0) dumpFields(item0);
                            }
                        }
                    }
                } catch (e2) { console.log(TAG + ' ListView err: ' + e2); }
            },
            onComplete: function () { console.log(TAG + ' choose done'); }
        });
    } catch (e) { console.log(TAG + ' choose err: ' + e); }

    function dumpFields(obj) {
        var cls = obj.getClass();
        while (cls && cls.getName() !== 'java.lang.Object') {
            var fields = cls.getDeclaredFields();
            for (var i = 0; i < fields.length; i++) {
                try {
                    fields[i].setAccessible(true);
                    var v = fields[i].get(obj);
                    var val = v === null ? 'null' : String(v).substring(0, 100);
                    console.log(TAG + '  ' + cls.getName() + '.' + fields[i].getName() + ' = ' + val);
                } catch (e2) {}
            }
            cls = cls.getSuperclass();
        }
    }

    console.log(TAG + ' ready');
});
