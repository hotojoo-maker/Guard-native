/**
 * fts_view_probeB.js — 只查 jz2.g.s（父类字段），一次访问
 */

'use strict';

const TAG = '[FVPB]';

let gvCount = 0;
const MAX_GV = 6;

Java.perform(function () {

    // Try to get jz2.g class first
    let jz2g = null;
    try {
        jz2g = Java.use('jz2.g');
        console.log(TAG + ' jz2.g class loaded, methods:');
        const methods = jz2g.class.getDeclaredMethods();
        for (let i = 0; i < Math.min(methods.length, 20); i++) {
            console.log(TAG + '   ' + methods[i].getName() + '()');
        }
    } catch (e) {
        console.log(TAG + ' jz2.g use fail: ' + e);
    }

    try {
        const f0 = Java.use('com.tencent.mm.plugin.fts.ui.f0');
        f0.getView.overload('int', 'android.view.View', 'android.view.ViewGroup').implementation = function (pos, convertView, parent) {
            const result = f0.getView.call(this, pos, convertView, parent);
            gvCount++;
            if (gvCount <= MAX_GV) {
                try {
                    const item = this.getItem(pos);
                    if (item && jz2g) {
                        const clsName = item.getClass().getName();
                        console.log(TAG + ' #' + gvCount + ' pos=' + pos + ' cls=' + clsName);

                        // Try field "s" on jz2.g (parent class)
                        try {
                            const fs = jz2g.class.getDeclaredField('s');
                            fs.setAccessible(true);
                            const vs = fs.get(item);
                            if (vs !== null) {
                                const vss = vs.toString();
                                console.log(TAG + '   jz2.g.s="' + vss.substring(0, 150) + '"');
                                if (vss.includes('wxid_')) console.log(TAG + '   ★★★ WXID FOUND! ★★★');
                            } else {
                                console.log(TAG + '   jz2.g.s=null');
                            }
                        } catch (e2) {
                            console.log(TAG + '   no jz2.g.s: ' + e2.message.substring(0, 60));
                        }

                        // Try field "a" through "g"
                        const guessNames = ['a','b','c','d','e','f','g','h','k','l','m','n'];
                        const found = [];
                        for (let i = 0; i < guessNames.length; i++) {
                            try {
                                const f = jz2g.class.getDeclaredField(guessNames[i]);
                                f.setAccessible(true);
                                const v = f.get(item);
                                if (v !== null) {
                                    found.push(guessNames[i] + '=' + v.toString().substring(0, 50));
                                }
                            } catch(e3) {}
                        }
                        if (found.length > 0) {
                            console.log(TAG + '   jz2.g fields: ' + found.join(' | '));
                        }
                    }
                } catch (e) {
                    console.log(TAG + '   err: ' + e.message.substring(0, 60));
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
