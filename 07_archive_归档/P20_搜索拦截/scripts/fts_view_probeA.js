/**
 * fts_view_probeA.js — 遍历继承链找 field "s" + 常见字段 a~z
 */

'use strict';

const TAG = '[FVPA]';
const FIELD_NAMES = ['s','a','b','c','d','e','f','g','h','i','j','k','l','m','n',
                     'o','p','q','r','t','u','v','w','x','y','z'];

let gvCount = 0;
const MAX_GV = 4;

function findFields(obj, label) {
    let c = obj.getClass();
    const hits = [];
    while (c && !c.getName().equals('java.lang.Object')) {
        const cn = c.getName();
        for (let i = 0; i < FIELD_NAMES.length; i++) {
            try {
                const f = c.getDeclaredField(FIELD_NAMES[i]);
                f.setAccessible(true);
                const v = f.get(obj);
                const vs = (v === null) ? 'null' : ('"' + v.toString().substring(0, 80) + '"');
                const marker = (vs.includes('wxid_') || vs.includes('gh_')) ? ' ★WXID' : '';
                hits.push(cn.split('.').pop() + '.' + FIELD_NAMES[i] + '=' + vs + marker);
            } catch(e) {}
        }
        c = c.getSuperclass();
    }
    if (hits.length > 0) {
        console.log(TAG + ' [' + label + '] FOUND:');
        hits.forEach(function(h) { console.log(TAG + '   ' + h); });
    } else {
        console.log(TAG + ' [' + label + '] ALL NULL/NOT-FOUND (tried ' + FIELD_NAMES.length + ' names across hierarchy)');
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
                    if (item) findFields(item, 'pos' + pos + '#' + gvCount);
                } catch(e) {
                    console.log(TAG + ' err: ' + e.message.substring(0, 60));
                }
                console.log('');
            }
            return result;
        };
        console.log(TAG + ' f0.getView hooked ✓');
    } catch (e) {
        console.log(TAG + ' fail: ' + e);
    }

    console.log(TAG + ' ── READY ──');
});
