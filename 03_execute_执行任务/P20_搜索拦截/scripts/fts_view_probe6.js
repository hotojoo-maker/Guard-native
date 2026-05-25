/**
 * fts_view_probe6.js v2 — 打印类继承链 + 字段
 */

'use strict';

const TAG = '[FVP6]';

let gvCount = 0;
const MAX_GV = 6;

function fullDump(obj, label) {
    if (!obj) return;
    const cls = obj.getClass();
    const clsName = cls.getName();

    // Print class hierarchy
    const hierarchy = [];
    let c = cls;
    while (c) {
        hierarchy.push(c.getName());
        c = c.getSuperclass();
    }
    console.log(TAG + ' [' + label + '] ' + clsName);
    console.log(TAG + '   hierarchy: ' + hierarchy.join(' → '));

    // Print all fields from all levels
    const allFields = [];
    c = cls;
    while (c && !c.getName().equals('java.lang.Object')) {
        const fields = c.getDeclaredFields();
        for (let i = 0; i < fields.length; i++) {
            const fn = fields[i].getName();
            const ft = fields[i].getType().getName();
            let val = '?';
            try {
                fields[i].setAccessible(true);
                const v = fields[i].get(obj);
                if (v === null) {
                    val = 'null';
                } else if (ft === 'java.lang.String') {
                    val = '"' + v.toString().substring(0, 80) + '"';
                } else {
                    val = v.toString().substring(0, 40);
                }
            } catch (e2) { val = 'ERR'; }
            allFields.push(fn + ':' + ft + '=' + val);
        }
        c = c.getSuperclass();
    }

    if (allFields.length === 0) {
        console.log(TAG + '   NO FIELDS AT ALL! Trying getMethods for getters...');
        // Try to find getter methods
        const methods = cls.getDeclaredMethods();
        for (let i = 0; i < Math.min(methods.length, 20); i++) {
            const mn = methods[i].getName();
            if (mn.startsWith('get') || mn.startsWith('is')) {
                console.log(TAG + '   method: ' + mn + '()');
            }
        }
    } else {
        console.log(TAG + '   fields (' + allFields.length + '):');
        allFields.forEach(function(f) { console.log(TAG + '     ' + f); });
    }
}

Java.perform(function () {

    try {
        const f0 = Java.use('com.tencent.mm.plugin.fts.ui.f0');
        f0.getView.overload('int', 'android.view.View', 'android.view.ViewGroup').implementation = function (pos, convertView, parent) {
            const result = f0.getView.call(this, pos, convertView, parent);
            gvCount++;
            if (gvCount <= MAX_GV) {
                try {
                    const item = this.getItem(pos);
                    if (item) fullDump(item, 'pos' + pos + '#' + gvCount);
                } catch (e) {
                    console.log(TAG + ' getItem err: ' + e.message);
                }
            }
            return result;
        };
        console.log(TAG + ' f0.getView hooked ✓');
    } catch (e) {
        console.log(TAG + ' fail: ' + e);
    }

    console.log(TAG + ' ── READY ──');
});
