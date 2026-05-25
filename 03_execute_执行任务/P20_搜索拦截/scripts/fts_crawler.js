/**
 * fts_crawler.js v5 — 极简：只查 e(fz2.r) + s(tz2.g0) + fz2.r getters
 */

'use strict';

const TAG = '[CRAWL]';
const seenKeys = new Set();

Java.perform(function () {
    const jz2g = Java.use('jz2.g');
    const fz2r = Java.use('fz2.r');
    const f0 = Java.use('com.tencent.mm.plugin.fts.ui.f0');
    let gvCount = 0;

    f0.getView.overload('int', 'android.view.View', 'android.view.ViewGroup').implementation = function (pos, convertView, parent) {
        const result = f0.getView.call(this, pos, convertView, parent);
        gvCount++;

        if (gvCount <= 4) {
            try {
                const item = this.getItem(pos);
                if (!item) return result;
                const cls = item.getClass();
                const clsName = cls.getName();
                const key = clsName + '@p' + pos;
                if (seenKeys.has(key)) return result;
                seenKeys.add(key);

                console.log(TAG + ' === ' + clsName + ' pos=' + pos + ' ===');

                // 1. Field "s" on child class (Catfish's wxid field)
                try {
                    const fs = cls.getDeclaredField('s');
                    fs.setAccessible(true);
                    const vs = fs.get(item);
                    console.log(TAG + ' ' + clsName.split('.').pop() + '.s = ' + (vs === null ? 'null' : '"' + vs.toString().substring(0, 150) + '"'));
                } catch(e) { console.log(TAG + ' no field s'); }

                // 2. Field "e" (fz2.r) — get it via jz2.g
                try {
                    const fe = jz2g.class.getDeclaredField('e');
                    fe.setAccessible(true);
                    const eObj = fe.get(item);
                    if (eObj) {
                        const eCls = eObj.getClass().getName();
                        console.log(TAG + ' jz2g.e -> ' + eCls);

                        // Call fz2.r methods directly via Frida bridge: eObj.a(), eObj.b(), etc.
                        const methods_to_try = ['a','b','c','d','e','f','g','h','i','j','k','l','m','n','o','p','q','r','s','t'];
                        let printedE = false;
                        for (let i = 0; i < methods_to_try.length; i++) {
                            try {
                                const mn = methods_to_try[i];
                                const v = eObj[mn]();
                                if (v !== null) {
                                    const vs = v.toString().substring(0, 150);
                                    const marker = (vs.includes('wxid_') || vs.includes('gh_')) ? ' ★WXID' : '';
                                    console.log(TAG + '   eObj.' + mn + '()="' + vs + '"' + marker);
                                    printedE = true;
                                }
                            } catch(e2) {
                                // Method doesn't exist or throws
                            }
                        }
                        if (!printedE) {
                            console.log(TAG + '   (no getter methods on fz2.r accessible)');
                            // Try toString as last resort
                            console.log(TAG + '   eObj.toString()=' + eObj.toString().substring(0, 200));
                        }
                    }
                } catch(e) { console.log(TAG + ' no field e: ' + e.message.substring(0, 50)); }

                console.log('');
            } catch(e) {
                console.log(TAG + ' err: ' + e.message.substring(0, 60));
            }
        }
        return result;
    };

    console.log(TAG + ' ✓ READY, search now');
});
