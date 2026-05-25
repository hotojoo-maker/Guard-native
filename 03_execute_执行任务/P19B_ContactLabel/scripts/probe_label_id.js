/**
 * probe_label_id.js v14 — addAll(d4) dump ALL items' field_labelID
 */
'use strict';

Java.perform(function () {
    var TAG = '[LBL]';
    var done = false;

    try {
        var ArrayList = Java.use('java.util.ArrayList');
        ArrayList.addAll.overload('java.util.Collection').implementation = function (c) {
            if (!done && c && !c.isEmpty()) {
                var it = c.iterator();
                var first = it.next();
                if (first && first.getClass().getName() === 'com.tencent.mm.storage.d4') {
                    done = true;
                    console.log(TAG + ' addAll d4 sz=' + c.size());

                    // dump first item fully
                    dumpAllFields(first);

                    // iterate ALL items for labelID + labelName
                    var it2 = c.iterator();
                    var idx = 0;
                    while (it2.hasNext()) {
                        var item = it2.next();
                        if (!item) continue;
                        var id = readField(item, 'field_labelID');
                        var name = readField(item, 'field_labelName');
                        var hex = '';
                        var s = String(name);
                        for (var k = 0; k < Math.min(s.length, 60); k++) {
                            hex += '\\u' + ('0000' + s.charCodeAt(k).toString(16)).slice(-4);
                        }
                        console.log(TAG + ' [' + idx + '] id=' + id + ' name=' + hex + ' // ' + s);
                        idx++;
                    }
                }
            }
            return this.addAll(c);
        };
        console.log(TAG + ' addAll ok');
    } catch (e) {
        console.log(TAG + ' addAll err: ' + e);
    }

    function dumpAllFields(item) {
        var cls = item.getClass();
        var sc = cls;
        while (sc && sc.getName() !== 'java.lang.Object') {
            console.log(TAG + ' --- fields in ' + sc.getName() + ' ---');
            var fs = sc.getDeclaredFields();
            for (var i = 0; i < fs.length; i++) {
                fs[i].setAccessible(true);
                try {
                    var v = fs[i].get(item);
                    var tn = fs[i].getType().getName();
                    var vs = v === null ? 'null' : String(v).substring(0, 80);
                    console.log(TAG + ' ' + tn + ' ' + fs[i].getName() + '=' + vs);
                } catch (e2) {
                    console.log(TAG + ' ' + fs[i].getType().getName() + ' ' + fs[i].getName() + '=ERR');
                }
            }
            sc = sc.getSuperclass();
        }
    }

    function readField(obj, fname) {
        var c = obj.getClass();
        while (c && c.getName() !== 'java.lang.Object') {
            try { var f = c.getDeclaredField(fname); f.setAccessible(true); return f.get(obj); } catch (e) {}
            c = c.getSuperclass();
        }
        return null;
    }

    console.log(TAG + ' v14 ready');
});
