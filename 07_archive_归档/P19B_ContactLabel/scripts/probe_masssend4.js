/**
 * probe_masssend4.js — dump MassSendHistoryListView hierarchy + fields
 */
'use strict';

Java.perform(function () {
    var TAG = '[MS4]';
    Java.choose('com.tencent.mm.plugin.masssend.ui.MassSendHistoryUI', {
        onMatch: function (inst) {
            try {
                var dField = inst.getClass().getDeclaredField('d');
                dField.setAccessible(true);
                var lv = dField.get(inst);
                if (!lv) { console.log(TAG + ' lv null'); return; }

                // Dump class hierarchy
                var hc = lv.getClass();
                var chain = [];
                while (hc && hc.getName() !== 'java.lang.Object') {
                    chain.push(hc.getName());
                    hc = hc.getSuperclass();
                }
                console.log(TAG + ' hierarchy: ' + chain.join(' → '));

                // Dump ALL fields of MassSendHistoryListView
                dumpFields(lv, TAG + ' LV.');

                // Also check for internal ListView/RecyclerView
                var lvCls = lv.getClass();
                var allM = lvCls.getDeclaredMethods();
                var listMs = [];
                for (var i = 0; i < allM.length; i++) {
                    var name = allM[i].getName();
                    if (name.indexOf('Adapter') >= 0 || name.indexOf('List') >= 0 || name.indexOf('Recycler') >= 0) {
                        listMs.push(name);
                    }
                }
                console.log(TAG + ' Adapter-related methods: ' + listMs.join(', '));

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
                    var val = v === null ? 'null' : String(v).substring(0, 200);
                    console.log(prefix + '[' + t + '] ' + cls.getName() + '.' + fields[i].getName() + ' = ' + val);
                } catch (e) {}
            }
            cls = cls.getSuperclass();
        }
    }

    console.log(TAG + ' ready');
});
