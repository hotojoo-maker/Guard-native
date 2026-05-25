'use strict';
/**
 * 最小集 — 只 hook RecyclerView.setAdapter + ListView.setAdapter
 * 退出重进"谁可以看"即可触发
 */
Java.perform(function () {
    var TAG = '[VIS]';
    var scanned = {};

    function dumpOne(obj) {
        var r = {};
        if (!obj) return r;
        try {
            var cls = obj.getClass();
            var n = 0;
            while (cls && cls.getName() !== 'java.lang.Object' && n < 20) {
                try {
                    var fs = cls.getDeclaredFields();
                    for (var i = 0; i < fs.length && n < 20; i++) {
                        try {
                            fs[i].setAccessible(true);
                            var v = fs[i].get(obj);
                            if (v !== null) r[fs[i].getName()] = String(v).substring(0, 80);
                            n++;
                        } catch(e) {}
                    }
                } catch(e) {}
                cls = cls.getSuperclass();
            }
        } catch(e) {}
        return r;
    }

    function scan(adapter, source) {
        if (!adapter || scanned[String(adapter)]) return;
        scanned[String(adapter)] = true;
        var clsName = adapter.getClass().getName();
        console.log('\n' + TAG + ' === ' + source + ' adapter=' + clsName + ' ===');
        try {
            var count = 0;
            try { count = adapter.getItemCount(); } catch(e) {
                try { count = adapter.getCount(); } catch(e2) {}
            }
            console.log(TAG + ' count=' + count);
            var n = Math.min(count, 5);
            for (var i = 0; i < n; i++) {
                try {
                    var item = adapter.getItem(i);
                    if (!item) continue;
                    var f = dumpOne(item);
                    console.log(TAG + ' [' + i + '] ' + item.getClass().getName());
                    for (var k in f) {
                        var v = f[k];
                        var tag = /^wxid_/.test(v) ? ' *** WXID' : '';
                        if (tag || /d$|userName$|username$|wxid$/i.test(k))
                            console.log(TAG + '   ' + k + '=' + v + tag);
                    }
                } catch(e) {}
            }
        } catch(e) { console.log(TAG + ' scan err: ' + e); }
    }

    // RecyclerView.setAdapter
    try {
        Java.use('androidx.recyclerview.widget.RecyclerView')
            .setAdapter.implementation = function(a) {
                scan(a, 'RV');
                return this.setAdapter(a);
            };
        console.log(TAG + ' RV setAdapter ok');
    } catch(e) { console.log(TAG + ' RV err: ' + e); }

    // ListView.setAdapter
    try {
        Java.use('android.widget.ListView')
            .setAdapter.overload('android.widget.ListAdapter')
            .implementation = function(a) { scan(a, 'LV'); return this.setAdapter(a); };
        console.log(TAG + ' LV setAdapter ok');
    } catch(e) { console.log(TAG + ' LV err: ' + e); }

    console.log(TAG + ' === 退出 → 重进"谁可以看" ===');
});
