/**
 * probe_masssend3.js — 全用反射，不靠 Frida 包装
 */
'use strict';

Java.perform(function () {
    var TAG = '[MS3]';
    Java.choose('com.tencent.mm.plugin.masssend.ui.MassSendHistoryUI', {
        onMatch: function (inst) {
            try {
                var cls = inst.getClass();

                // Get ListView
                var dField = cls.getDeclaredField('d');
                dField.setAccessible(true);
                var lv = dField.get(inst);
                if (!lv) { console.log(TAG + ' lv null'); return; }
                console.log(TAG + ' lv=' + lv.getClass().getName());

                // Use reflection to call getAdapter()
                var lvCls = lv.getClass();
                var getAdM = null;
                // search up hierarchy for getAdapter
                var searchCls = lvCls;
                while (searchCls && searchCls.getName() !== 'java.lang.Object') {
                    try {
                        getAdM = searchCls.getDeclaredMethod('getAdapter');
                        break;
                    } catch (e) {
                        searchCls = searchCls.getSuperclass();
                    }
                }

                if (!getAdM) { console.log(TAG + ' no getAdapter'); return; }
                getAdM.setAccessible(true);
                var adapter = getAdM.invoke(lv);
                if (!adapter) { console.log(TAG + ' adapter null'); return; }

                var adCls = adapter.getClass();
                console.log(TAG + ' adapter=' + adCls.getName());
                var adSuper = adCls.getSuperclass();
                console.log(TAG + ' adapter.super=' + (adSuper ? adSuper.getName() : 'null'));

                // getCount via reflection
                var getCountM = findMethod(adapter, 'getCount');
                if (getCountM) {
                    var count = getCountM.invoke(adapter);
                    console.log(TAG + ' count=' + count);
                }

                // getItem(int) via reflection
                var getItemM = findMethod1int(adapter);
                if (getItemM && getCountM) {
                    var count = getCountM.invoke(adapter);
                    for (var i = 0; i < Math.min(count, 3); i++) {
                        var item = getItemM.invoke(adapter, Java.array('java.lang.Object', [i]));
                        if (item) {
                            console.log(TAG + ' item[' + i + ']=' + item.getClass().getName());
                            dumpFields(item, TAG + ' IT[' + i + '].');
                        }
                    }
                }

                // dump wc3.k0
                try {
                    var fField = cls.getDeclaredField('f');
                    fField.setAccessible(true);
                    var k0 = fField.get(inst);
                    if (k0) {
                        console.log(TAG + ' wc3.k0=' + k0.getClass().getName());
                        dumpFields(k0, TAG + ' K0.');
                    }
                } catch (e) {}

            } catch (e) { console.log(TAG + ' err: ' + e); }
        },
        onComplete: function () { console.log(TAG + ' done'); }
    });

    function findMethod(obj, name) {
        var cls = obj.getClass();
        while (cls && cls.getName() !== 'java.lang.Object') {
            try {
                return cls.getDeclaredMethod(name);
            } catch (e) {
                cls = cls.getSuperclass();
            }
        }
        return null;
    }

    function findMethod1int(obj) {
        var cls = obj.getClass();
        while (cls && cls.getName() !== 'java.lang.Object') {
            var methods = cls.getDeclaredMethods();
            for (var i = 0; i < methods.length; i++) {
                var params = methods[i].getParameterTypes();
                if (params.length === 1 && params[0].getName() === 'int') {
                    methods[i].setAccessible(true);
                    return methods[i];
                }
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

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
