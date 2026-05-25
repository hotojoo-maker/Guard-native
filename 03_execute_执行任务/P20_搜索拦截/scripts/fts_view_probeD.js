/**
 * fts_view_probeD.js — 极简：只调 getter 方法，不碰字段
 */

'use strict';

const TAG = '[FVPD]';

let gvCount = 0;
const MAX_GV = 6;

Java.perform(function () {

    try {
        const f0 = Java.use('com.tencent.mm.plugin.fts.ui.f0');
        f0.getView.overload('int', 'android.view.View', 'android.view.ViewGroup').implementation = function (pos, convertView, parent) {
            const result = f0.getView.call(this, pos, convertView, parent);
            gvCount++;
            if (gvCount <= MAX_GV) {
                try {
                    const item = this.getItem(pos);
                    if (item) {
                        console.log(TAG + ' #' + gvCount + ' pos=' + pos + ' cls=' + item.getClass().getName());

                        // Call getters directly on item (no reflection)
                        // item IS a jz2.g subclass, so its a()-n() methods are callable
                        // Use Frida's Java bridge to call methods
                        try {
                            const a = item.a(); const b = item.b();
                            const e = item.e(); // fz2.r
                            const g = item.g(); const h = item.h();
                            const k = item.k(); const l = item.l(); const m = item.m();

                            console.log(TAG + '  a=' + a + ' b=' + b + ' g=' + g + ' h=' + h + ' k=' + k + ' l=' + l + ' m=' + m);

                            // e = fz2.r, call its getters
                            if (e) {
                                const ea = e.a(); const eb = e.b(); const ec = e.c();
                                const ed = e.d(); const ee = e.e(); const ef = e.f();
                                console.log(TAG + '  fz2.r: a=' + ea + ' b=' + eb + ' c=' + ec +
                                            ' d="' + (ed ? ed.toString().substring(0,100) : 'null') +
                                            '" e="' + (ee ? ee.toString().substring(0,100) : 'null') +
                                            '" f="' + (ef ? ef.toString().substring(0,100) : 'null') + '"');

                                // Check each fz2.r getter for wxid
                                const checks = [ea, eb, ec, ed, ee, ef];
                                const names = ['a','b','c','d','e','f'];
                                for (let i = 0; i < checks.length; i++) {
                                    if (checks[i] !== null && typeof checks[i] !== 'undefined') {
                                        const s = checks[i].toString();
                                        if (s.includes('wxid_') || s.includes('gh_')) {
                                            console.log(TAG + '  ★★★ WXID at fz2.r.' + names[i] + '() = ' + s + ' ★★★');
                                        }
                                    }
                                }
                            }
                        } catch (e2) {
                            console.log(TAG + '  getter err: ' + e2.message.substring(0, 80));
                        }
                    }
                } catch (e) {
                    console.log(TAG + ' err: ' + e.message.substring(0, 60));
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
