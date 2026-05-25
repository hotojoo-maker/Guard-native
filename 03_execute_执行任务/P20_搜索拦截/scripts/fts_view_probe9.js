/**
 * fts_view_probe9.js — 最简：hook f0.getView + item.toString()
 */

'use strict';

const TAG = '[FVP9]';

let gvCount = 0;
const MAX_GV = 6;

Java.perform(function () {

    try {
        const f0 = Java.use('com.tencent.mm.plugin.fts.ui.f0');
        f0.getView.overload('int', 'android.view.View', 'android.view.ViewGroup').implementation = function (pos, convertView, parent) {
            const result = f0.getView.call(this, pos, convertView, parent);
            gvCount++;
            if (gvCount <= MAX_GV) {
                console.log(TAG + ' getView #' + gvCount + ' pos=' + pos);
                try {
                    const item = this.getItem(pos);
                    if (item) {
                        const cls = item.getClass();
                        const clsName = cls.getName();
                        console.log(TAG + '   class=' + clsName);
                        console.log(TAG + '   super=' + cls.getSuperclass().getName());
                        console.log(TAG + '   toString=' + item.toString().substring(0, 200));

                        // Try ONE specific field: "s" (Catfish's wxid field)
                        try {
                            const fs = cls.getDeclaredField('s');
                            fs.setAccessible(true);
                            const vs = fs.get(item);
                            if (vs !== null) {
                                console.log(TAG + '   FIELD s=' + vs.toString().substring(0, 200));
                            }
                        } catch (e2) {
                            console.log(TAG + '   no field "s": ' + e2.message.substring(0, 60));
                        }
                    }
                } catch (e) {
                    console.log(TAG + '   err: ' + e.message.substring(0, 80));
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
