/**
 * probe_mvvm2.js — 抓 MvvmContactListUI Intent + MvvmList 数据
 */
'use strict';

Java.perform(function () {
    var TAG = '[MV2]';
    Java.choose('com.tencent.mm.ui.mvvm.MvvmContactListUI', {
        onMatch: function (inst) {
            try {
                var cls = inst.getClass();

                // 1. Get Intent extras
                var intent = inst.getIntent();
                if (intent) {
                    console.log(TAG + ' Intent: ' + intent);
                    var b = intent.getExtras();
                    if (b) {
                        var keys = b.keySet();
                        var ki = keys.iterator();
                        while (ki.hasNext()) {
                            var k = ki.next();
                            try { console.log(TAG + '  [' + k + '] = ' + String(b.get(k))); }
                            catch (e2) { console.log(TAG + '  [' + k + '] = ???'); }
                        }
                    }
                }

                // 2. Find RecyclerView / ListView
                var dField = cls.getDeclaredField('d');
                dField.setAccessible(true);
                var qu5g = dField.get(inst);
                if (qu5g) {
                    console.log(TAG + ' d=' + qu5g.getClass().getName());
                    var qcls = qu5g.getClass();
                    var qfields = qcls.getDeclaredFields();
                    for (var i = 0; i < qfields.length; i++) {
                        qfields[i].setAccessible(true);
                        try {
                            var v = qfields[i].get(qu5g);
                            var t = qfields[i].getType().getName();
                            var val = v === null ? 'null' : String(v).substring(0, 150);
                            console.log(TAG + '  q.' + qfields[i].getName() + ' = ' + val);
                        } catch (e2) {}
                    }
                }

                // 3. Look for RecyclerView in view hierarchy
                // via decorView
                try {
                    var decor = inst.getWindow().getDecorView();
                    findRecyclerView(decor, 0);
                } catch (e3) {}

            } catch (e) { console.log(TAG + ' err: ' + e); }
        },
        onComplete: function () { console.log(TAG + ' done'); }
    });

    function findRecyclerView(view, depth) {
        if (depth > 4) return;
        var cn = view.getClass().getName();
        if (cn.indexOf('Recycler') >= 0 || cn.indexOf('MvvmList') >= 0) {
            console.log(TAG + ' *** ' + cn + ' at depth=' + depth);
            var adapter = null;
            try {
                var gA = view.getClass().getMethod('getAdapter', Java.array('java.lang.Class', []));
                adapter = gA.invoke(view, Java.array('java.lang.Object', []));
            } catch (e) {}
            if (!adapter) {
                try {
                    var gA2 = view.getClass().getSuperclass().getMethod('getAdapter', Java.array('java.lang.Class', []));
                    adapter = gA2.invoke(view, Java.array('java.lang.Object', []));
                } catch (e) {}
            }
            if (adapter) {
                console.log(TAG + ' adapter=' + adapter.getClass().getName());
                try {
                    var gC = adapter.getClass().getMethod('getItemCount', Java.array('java.lang.Class', []));
                    var cnt = gC.invoke(adapter, Java.array('java.lang.Object', []));
                    console.log(TAG + ' itemCount=' + cnt);
                } catch (e) {}
            }
        }
        if (view instanceof Java.use('android.view.ViewGroup')) {
            var vg = Java.cast(view, Java.use('android.view.ViewGroup'));
            for (var i = 0; i < Math.min(vg.getChildCount(), 5); i++) {
                findRecyclerView(vg.getChildAt(i), depth + 1);
            }
        }
    }

    console.log(TAG + ' go');
});
