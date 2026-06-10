/**
 * probe_masssend2.js — 深挖 MassSendHistoryUI adapter + wc3.k0
 */
'use strict';

Java.perform(function () {
    var TAG = '[MS2]';
    Java.choose('com.tencent.mm.plugin.masssend.ui.MassSendHistoryUI', {
        onMatch: function (inst) {
            try {
                var cls = inst.getClass();

                // 1. Get MassSendHistoryListView → getAdapter()
                var dField = cls.getDeclaredField('d');
                dField.setAccessible(true);
                var lv = dField.get(inst);
                if (lv) {
                    console.log(TAG + ' ListView=' + lv.getClass().getName());
                    var adapter = lv.getAdapter();
                    if (adapter) {
                        console.log(TAG + ' Adapter=' + adapter.getClass().getName());
                        var adCls = adapter.getClass();
                        var adSuper = adCls.getSuperclass();
                        console.log(TAG + ' Adapter.super=' + (adSuper ? adSuper.getName() : 'null'));
                        console.log(TAG + ' Adapter.count=' + adapter.getCount());
                        // dump adapter fields
                        dumpFields(adapter, TAG + ' AD.');
                        // dump first 3 items
                        for (var i = 0; i < Math.min(adapter.getCount(), 3); i++) {
                            var item = adapter.getItem(i);
                            if (item) {
                                console.log(TAG + ' item[' + i + ']=' + item.getClass().getName());
                                dumpFields(item, TAG + ' IT[' + i + '].');
                            }
                        }
                    }
                }

                // 2. wc3.k0 (f field) - data controller
                var fField = cls.getDeclaredField('f');
                fField.setAccessible(true);
                var wc3k0 = fField.get(inst);
                if (wc3k0) {
                    console.log(TAG + ' wc3.k0=' + wc3k0.getClass().getName());
                    dumpFields(wc3k0, TAG + ' K0.');
                }
            } catch (e) { console.log(TAG + ' err: ' + e); }
        },
        onComplete: function () { console.log(TAG + ' done'); }
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

    console.log(TAG + ' ready');
});
