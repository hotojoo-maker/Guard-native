/**
 * probe_wc3k0_items.js — 读 wc3.k0 adapter 数据项
 */
'use strict';

Java.perform(function () {
    var TAG = '[K0]';
    Java.choose('com.tencent.mm.plugin.masssend.ui.MassSendHistoryUI', {
        onMatch: function (inst) {
            try {
                var fField = inst.getClass().getDeclaredField('f');
                fField.setAccessible(true);
                var adapter = fField.get(inst);
                if (!adapter) return;

                var adCls = adapter.getClass();
                var emptyCA = Java.array('java.lang.Class', []);
                var emptyOA = Java.array('java.lang.Object', []);

                var getCount = adCls.getMethod('getCount', emptyCA);
                var cnt = getCount.invoke(adapter, emptyOA);
                console.log(TAG + ' count=' + cnt);

                var intType = Java.use('java.lang.Integer').TYPE;
                var getItem = adCls.getMethod('getItem', Java.array('java.lang.Class', [intType]));
                var getView = adCls.getMethod('getView', Java.array('java.lang.Class', [intType, Java.use('android.view.View').class, Java.use('android.view.ViewGroup').class]));

                for (var i = 0; i < Math.min(cnt, 3); i++) {
                    var item = getItem.invoke(adapter, Java.array('java.lang.Object', [i]));
                    if (item) {
                        console.log(TAG + ' item[' + i + ']=' + item.getClass().getName());
                        dumpFields(item, TAG + ' IT[' + i + '].');
                    }
                }
            } catch (e) { console.log(TAG + ' err: ' + e); }
        },
        onComplete: function () {}
    });

    function dumpFields(obj, prefix) {
        var cls = obj.getClass();
        while (cls && cls.getName() !== 'java.lang.Object') {
            var fields = cls.getDeclaredFields();
            for (var i = 0; i < fields.length; i++) {
                try {
                    fields[i].setAccessible(true);
                    var v = fields[i].get(obj);
                    var t = fields[i].getType().getName();
                    var val = v === null ? 'null' : String(v).substring(0, 150);
                    console.log(prefix + '[' + t + '] ' + cls.getName() + '.' + fields[i].getName() + ' = ' + val);
                } catch (e) {}
            }
            cls = cls.getSuperclass();
        }
    }
    console.log(TAG + ' go');
});
