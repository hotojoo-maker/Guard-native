/**
 * fts_view_probe5.js — dump tz2.g0 / tz2.u1 的完整字段 + 找 wxid
 */

'use strict';

const TAG = '[FVP5]';

function dumpObj(obj, label, maxDepth) {
    if (!obj || maxDepth <= 0) return;
    try {
        const cls = obj.getClass();
        const clsName = cls.getName();
        console.log(TAG + ' [' + label + '] class=' + clsName);

        // Walk class hierarchy and print ALL fields
        let c = cls;
        while (c && !c.getName().equals('java.lang.Object')) {
            const fields = c.getDeclaredFields();
            for (let i = 0; i < fields.length; i++) {
                const f = fields[i];
                try {
                    f.setAccessible(true);
                    const v = f.get(obj);
                    const fn = f.getName();
                    const ft = f.getType().getName();
                    if (v === null) {
                        console.log(TAG + '   ' + fn + ' (' + ft + ') = null');
                    } else if (ft === 'java.lang.String') {
                        const sv = v.toString();
                        const marker = (sv.includes('wxid_') || sv.includes('gh_')) ? ' ★WXID' : '';
                        console.log(TAG + '   ' + fn + ' (String) = "' + sv.substring(0, 80) + '"' + marker);
                    } else if (ft === 'int' || ft === 'long' || ft === 'boolean' || ft === 'byte' || ft === 'short' || ft === 'float' || ft === 'double') {
                        console.log(TAG + '   ' + fn + ' (' + ft + ') = ' + v);
                    } else if (ft === 'java.lang.Integer' || ft === 'java.lang.Long' || ft === 'java.lang.Boolean') {
                        console.log(TAG + '   ' + fn + ' (' + ft + ') = ' + v);
                    } else {
                        const vcn = v.getClass().getName();
                        console.log(TAG + '   ' + fn + ' (' + ft + ') -> {' + vcn + '}');
                        // Recurse one level for sub-objects
                        if (maxDepth > 1 && !vcn.startsWith('java.') && !vcn.startsWith('android.')) {
                            dumpObj(v, label + '.' + fn, 1);
                        }
                    }
                } catch (e2) {
                    console.log(TAG + '   ' + f.getName() + ' ERR: ' + e2);
                }
            }
            c = c.getSuperclass();
        }
    } catch (e) {
        console.log(TAG + ' [' + label + '] dump err: ' + e);
    }
}

let gvCount = 0;
const MAX_GV = 6;

Java.perform(function () {

    try {
        const f0 = Java.use('com.tencent.mm.plugin.fts.ui.f0');
        f0.getView.overload('int', 'android.view.View', 'android.view.ViewGroup').implementation = function (pos, convertView, parent) {
            const result = f0.getView.call(this, pos, convertView, parent);
            gvCount++;
            if (gvCount <= MAX_GV) {
                console.log(TAG + ' ====== getView #' + gvCount + ' pos=' + pos + ' ======');
                try {
                    const item = this.getItem(pos);
                    if (item) {
                        const itemCls = item.getClass().getName();
                        dumpObj(item, 'item#' + gvCount + '-' + itemCls, 2);
                    }
                } catch (e) {
                    console.log(TAG + ' getItem fail: ' + e);
                }
                console.log('');
            }
            return result;
        };
        console.log(TAG + ' f0.getView hooked ✓');
    } catch (e) {
        console.log(TAG + ' f0.getView fail: ' + e);
    }

    // Also hook f0.getCount to know total items
    try {
        const f0 = Java.use('com.tencent.mm.plugin.fts.ui.f0');
        f0.getCount.implementation = function () {
            const c = this.getCount();
            console.log(TAG + ' getCount=' + c);
            return c;
        };
        console.log(TAG + ' f0.getCount hooked ✓');
    } catch (e) {
        console.log(TAG + ' f0.getCount fail: ' + e);
    }

    console.log(TAG + ' ── READY, search now ──');
});
