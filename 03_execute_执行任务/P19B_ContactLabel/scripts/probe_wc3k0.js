/**
 * probe_wc3k0.js — 直接探 wc3.k0 adapter（MassSendHistoryUI 的 ListAdapter）
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
                if (!adapter) { console.log(TAG + ' adapter null'); return; }
                console.log(TAG + ' adapter=' + adapter.getClass().getName());

                // Use getMethod on AbsListView to find getAdapter/inherited methods
                var adCls = adapter.getClass();
                var adSuper = adCls.getSuperclass();
                console.log(TAG + ' super=' + (adSuper ? adSuper.getName() : 'null'));

                // getAll methods
                var methods = adCls.getMethods();
                var msigs = [];
                for (var i = 0; i < methods.length; i++) {
                    var m = methods[i];
                    var r = m.getReturnType().getName();
                    var name = m.getName();
                    var params = m.getParameterTypes();
                    var pn = [];
                    for (var j = 0; j < params.length; j++) pn.push(params[j].getName());
                    if (name === 'getCount' || name === 'getItem' || name === 'getView' || name === 'getGroup' || name.indexOf('get') >= 0) {
                        msigs.push(r + ' ' + name + '(' + pn.join(',') + ')');
                    }
                }
                console.log(TAG + ' get* methods:');
                for (var k = 0; k < msigs.length; k++) console.log(TAG + '  ' + msigs[k]);

                // Try getCount
                try {
                    var getCount = adCls.getMethod('getCount');
                    var cnt = getCount.invoke(adapter);
                    console.log(TAG + ' count=' + cnt);

                    // Dump getItem for first 3
                    var getItem = adCls.getMethod('getItem', Java.array('java.lang.Class', [Java.use('java.lang.Integer').TYPE]));
                    for (var i = 0; i < Math.min(cnt, 3); i++) {
                        try {
                            var item = getItem.invoke(adapter, Java.array('java.lang.Object', [i]));
                            if (item) {
                                console.log(TAG + ' item[' + i + ']=' + item.getClass().getName());
                                dumpFields(item, TAG + ' IT[' + i + '].');
                            }
                        } catch (e2) { console.log(TAG + ' item[' + i + '] err: ' + e2); }
                    }
                } catch (e) { console.log(TAG + ' count err: ' + e); }

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
