/**
 * fts_view_probeC.js — dump fz2.r (field e) + getter方法调用
 */

'use strict';

const TAG = '[FVPC]';

let gvCount = 0;
const MAX_GV = 4;

Java.perform(function () {

    const jz2g = Java.use('jz2.g');

    // Also load fz2.r for dumping
    let fz2r = null;
    try {
        fz2r = Java.use('fz2.r');
        console.log(TAG + ' fz2.r methods:');
        const methods = fz2r.class.getDeclaredMethods();
        for (let i = 0; i < Math.min(methods.length, 20); i++) {
            console.log(TAG + '   ' + methods[i].getName() + '()');
        }
    } catch (e) {
        console.log(TAG + ' fz2.r fail: ' + e.message);
    }

    try {
        const f0 = Java.use('com.tencent.mm.plugin.fts.ui.f0');
        f0.getView.overload('int', 'android.view.View', 'android.view.ViewGroup').implementation = function (pos, convertView, parent) {
            const result = f0.getView.call(this, pos, convertView, parent);
            gvCount++;
            if (gvCount <= MAX_GV) {
                try {
                    const item = this.getItem(pos);
                    if (item) {
                        const clsName = item.getClass().getName();
                        console.log(TAG + ' ====== pos=' + pos + ' cls=' + clsName + ' ======');

                        // Call all getter methods a()..n()
                        const getterNames = ['a','b','c','d','e','f','g','h','i','j','k','l','m','n'];
                        console.log(TAG + ' getters:');
                        for (let i = 0; i < getterNames.length; i++) {
                            try {
                                const m = jz2g.class.getDeclaredMethod(getterNames[i]);
                                const v = m.invoke(item);
                                if (v !== null) {
                                    const vn = v.getClass().getName();
                                    if (vn === 'java.lang.String') {
                                        const vs = v.toString();
                                        const marker = (vs.includes('wxid_') || vs.includes('gh_')) ? ' ★WXID' : '';
                                        console.log(TAG + '   ' + getterNames[i] + '()="' + vs.substring(0, 120) + '"' + marker);
                                    } else {
                                        console.log(TAG + '   ' + getterNames[i] + '()=' + v.toString().substring(0, 80) + ' (' + vn + ')');
                                    }
                                } else {
                                    console.log(TAG + '   ' + getterNames[i] + '()=null');
                                }
                            } catch (e2) {
                                console.log(TAG + '   ' + getterNames[i] + '() ERR: ' + e2.message.substring(0, 40));
                            }
                        }

                        // Also dump fz2.r (field e) if it exists
                        try {
                            const fe = jz2g.class.getDeclaredField('e');
                            fe.setAccessible(true);
                            const eObj = fe.get(item);
                            if (eObj) {
                                const eCls = eObj.getClass().getName();
                                console.log(TAG + ' field e (' + eCls + ') fields:');
                                let c = eObj.getClass();
                                const allSingle = ['a','b','c','d','e','f','g','h','i','j','k','l','m','n','o','p','q','r','s','t',
                                                   'u','v','w','x','y','z','A','B','C','D','E','F','G','H','I','J','K','L'];
                                while (c && !c.getName().equals('java.lang.Object')) {
                                    const cn = c.getName().split('.').pop();
                                    for (let i = 0; i < allSingle.length; i++) {
                                        try {
                                            const ff = c.getDeclaredField(allSingle[i]);
                                            ff.setAccessible(true);
                                            const fv = ff.get(eObj);
                                            if (fv !== null) {
                                                const fvs = fv.toString();
                                                const marker = (fvs.includes('wxid_') || fvs.includes('gh_')) ? ' ★WXID' : '';
                                                console.log(TAG + '   ' + cn + '.' + allSingle[i] + '="' + fvs.substring(0, 120) + '"' + marker);
                                            }
                                        } catch(e3) {}
                                    }
                                    c = c.getSuperclass();
                                }
                            }
                        } catch(e3) {
                            console.log(TAG + ' field e dump err: ' + e3.message);
                        }
                    }
                } catch (e) {
                    console.log(TAG + ' err: ' + e.message.substring(0, 80));
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
