/**
 * fts_view_probe7.js — 暴力枚举字段名 + getFields()
 */

'use strict';

const TAG = '[FVP7]';

// Common proguarded field names
const GUESS_NAMES = ['a','b','c','d','e','f','g','h','i','j','k','l','m','n','o','p','q','r','s','t','u','v','w','x','y','z',
                     'A','B','C','D','E','F','G','H','I','J','K','L','M','N','O','P','Q','R','S','T','U','V','W','X','Y','Z'];

let gvCount = 0;
const MAX_GV = 4;

function probeItem(obj, label) {
    if (!obj) return;
    const cls = obj.getClass();
    const clsName = cls.getName();

    console.log(TAG + ' [' + label + '] ' + clsName);

    // Method 1: getFields() — public fields including inherited
    try {
        const pubFields = cls.getFields();
        console.log(TAG + '   getFields().length=' + pubFields.length);
        for (let i = 0; i < pubFields.length; i++) {
            const fn = pubFields[i].getName();
            const ft = pubFields[i].getType().getName();
            let val = '?';
            try {
                const v = pubFields[i].get(obj);
                val = (v === null) ? 'null' : (typeof v === 'string' ? '"' + v.substring(0, 60) + '"' : v.toString().substring(0, 40));
            } catch(e) { val = 'ERR:' + e.message.substring(0,30); }
            console.log(TAG + '   PUBLIC ' + fn + ':' + ft + '=' + val);
        }
    } catch (e) {
        console.log(TAG + '   getFields err: ' + e.message);
    }

    // Method 2: getMethods — find getters
    try {
        const methods = cls.getMethods();
        let getterCount = 0;
        for (let i = 0; i < methods.length && getterCount < 15; i++) {
            const mn = methods[i].getName();
            if ((mn.startsWith('get') || mn.startsWith('is')) && methods[i].getParameterTypes().length === 0) {
                try {
                    const ret = methods[i].invoke(obj);
                    const retStr = ret === null ? 'null' : ret.toString().substring(0, 50);
                    console.log(TAG + '   getter ' + mn + '()=' + retStr);
                    getterCount++;
                } catch(e2) {}
            }
        }
    } catch (e) {}

    // Method 3: brute-force field names
    try {
        const hits = [];
        for (let i = 0; i < GUESS_NAMES.length; i++) {
            try {
                const f = cls.getDeclaredField(GUESS_NAMES[i]);
                f.setAccessible(true);
                const v = f.get(obj);
                if (v !== null) {
                    const vs = v.toString().substring(0, 60);
                    const marker = (vs.includes('wxid_') || vs.includes('gh_')) ? ' ★WXID' : '';
                    hits.push(GUESS_NAMES[i] + '=' + vs + marker);
                }
            } catch(e2) {}
        }
        if (hits.length > 0) {
            console.log(TAG + '   BRUTE: ' + hits.join(' | '));
        } else {
            console.log(TAG + '   BRUTE: all null/not-found');
        }
    } catch (e) {}
}

Java.perform(function () {

    try {
        const f0 = Java.use('com.tencent.mm.plugin.fts.ui.f0');
        f0.getView.overload('int', 'android.view.View', 'android.view.ViewGroup').implementation = function (pos, convertView, parent) {
            const result = f0.getView.call(this, pos, convertView, parent);
            gvCount++;
            if (gvCount <= MAX_GV) {
                // Use getItem via Adapter interface
                try {
                    const Adapter = Java.use('android.widget.Adapter');
                    const item = Adapter.getItem.call(this, pos);
                    if (item) probeItem(item, 'pos' + pos + '#' + gvCount);
                } catch (e) {
                    console.log(TAG + ' getItem via Adapter err: ' + e.message);
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
