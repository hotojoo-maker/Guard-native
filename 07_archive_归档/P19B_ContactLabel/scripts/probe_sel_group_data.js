/**
 * probe_sel_group_data.js — 抓 SelectContactUI（list_type=2）加载的数据项类+字段
 * 先 dump onCreate Intent 确认参数，再 hook addAll 抓列表数据
 */
'use strict';

Java.perform(function () {
    var TAG = '[SEL2]';
    var dumped = false;

    // 1. Hook SelectContactUI.onCreate — dump 接收到的 Intent
    try {
        var SelectContactUI = Java.use('com.tencent.mm.ui.contact.SelectContactUI');
        SelectContactUI.onCreate.implementation = function (savedInstanceState) {
            console.log(TAG + ' ====== onCreate ======');
            var intent = this.getIntent();
            if (intent) {
                var b = intent.getExtras();
                if (b) {
                    var keys = b.keySet();
                    var kit = keys.iterator();
                    while (kit.hasNext()) {
                        var k = kit.next();
                        try {
                            var v = b.get(k);
                            console.log(TAG + ' [IN] ' + k + ' = ' + v);
                        } catch (e2) {}
                    }
                }
            }
            return this.onCreate(savedInstanceState);
        };
        console.log(TAG + ' SelectContactUI onCreate hook ok');
    } catch (e) { console.log(TAG + ' onCreate fail: ' + e); }

    // 2. Hook ArrayList.addAll — 抓列表数据项类 + 字段
    var ArrayList = Java.use('java.util.ArrayList');
    ArrayList.addAll.overload('java.util.Collection').implementation = function (c) {
        if (dumped || !c || c.isEmpty()) return this.addAll(c);
        var it = c.iterator();
        var first = it.next();
        if (!first) return this.addAll(c);
        var cn = first.getClass().getName();
        // 只关注列表数据项（跳过 String/Integer）
        if (cn.startsWith('java.')) return this.addAll(c);

        // 检查调用栈是否来自 SelectContactUI
        var trace = Java.use('java.lang.Throwable').$new();
        var stack = trace.getStackTrace();
        var fromSel = false;
        for (var i = 0; i < Math.min(stack.length, 30); i++) {
            var frame = String(stack[i].getClassName());
            if (frame.indexOf('SelectContactUI') >= 0 || frame.indexOf('ChatroomContactUI') >= 0) { fromSel = true; break; }
        }
        if (!fromSel) return this.addAll(c);

        dumped = true;
        console.log(TAG + ' addAll class=' + cn + ' sz=' + c.size());

        // dump 前 3 项所有字段
        var it2 = c.iterator();
        for (var n = 0; n < Math.min(3, c.size()); n++) {
            var item = it2.next();
            if (!item) continue;
            console.log(TAG + ' === item[' + n + '] ===');
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

    console.log(TAG + ' ready — 请点密群列表（会打开 SelectContactUI list_type=2）');
});
