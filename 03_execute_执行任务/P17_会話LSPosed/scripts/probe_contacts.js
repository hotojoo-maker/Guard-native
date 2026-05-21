/**
 * probe_contacts.js v4 — logcat 输出模式
 * 所有输出通过 android.util.Log.i("FRIDA", ...) 写 logcat
 * 用法: frida -U -f com.tencent.mm -l probe_contacts.js
 *       另一窗口: adb logcat -s FRIDA
 */
'use strict';

Java.perform(function () {
    const Log = Java.use('android.util.Log');
    function log(msg) {
        Log.i('FRIDA', msg);
    }

    const MVVM_CLASS = 'com.tencent.mm.plugin.mvvmlist.MvvmList';
    const ADDR_FRAG  = 'com.tencent.mm.ui.contact.address.MvvmAddressUIFragment';

    // ── 1. Hook MvvmAddressUIFragment lifecycle ────────────────────────
    try {
        const Frag = Java.use(ADDR_FRAG);
        log('MvvmAddressUIFragment loaded, method count='
            + Frag.class.getDeclaredMethods().length);

        ['onResume', 'onStart', 'onViewCreated'].forEach(function(mn) {
            try {
                Frag[mn].overloads.forEach(function(ov) {
                    ov.implementation = function() {
                        const ret = ov.apply(this, arguments);
                        log('lifecycle: ' + mn);
                        dumpFragFields(this);
                        return ret;
                    };
                });
            } catch(e) {}
        });
    } catch(e) {
        log('MvvmAddressUIFragment fail: ' + e);
    }

    // ── 2. Hook ALL MvvmList subclass m/s calls ────────────────────────
    try {
        const MvvmList = Java.use(MVVM_CLASS);
        ['m', 's'].forEach(function(mn) {
            try {
                MvvmList[mn].overloads.forEach(function(ov) {
                    ov.implementation = function() {
                        const thisCls = this.getClass().getName();
                        const args = Array.prototype.slice.call(arguments);
                        let info = 'MvvmList.' + mn + ' this=' + thisCls;
                        try {
                            const arg0 = args[0];
                            if (arg0 && arg0.size) {
                                info += ' sz=' + arg0.size();
                                if (!arg0.isEmpty()) {
                                    info += ' itemCls=' + arg0.iterator().next().getClass().getName();
                                }
                            } else {
                                info += ' arg0cls=' + (arg0 ? arg0.getClass().getName() : 'null');
                            }
                        } catch(e) {}
                        log(info);
                        return ov.apply(this, arguments);
                    };
                });
            } catch(e) {}
        });
        log('MvvmList m/s hooks ok');
    } catch(e) {
        log('MvvmList hook fail: ' + e);
    }

    // ── 3. Broad addAll — tencent items ───────────────────────────────
    const ArrayList = Java.use('java.util.ArrayList');
    const orig = ArrayList.addAll.overload('java.util.Collection');
    let seen = {};
    orig.implementation = function(c) {
        if (c && !c.isEmpty()) {
            try {
                const first = c.iterator().next();
                if (first) {
                    const fcn = first.getClass().getName();
                    if (fcn.includes('tencent') && !seen[fcn]) {
                        seen[fcn] = true;
                        const st = Java.use('java.lang.Thread').currentThread().getStackTrace();
                        let line = 'addAll cls=' + fcn + ' sz=' + c.size();
                        for (let i = 3; i < Math.min(7, st.length); i++) {
                            line += ' | ' + st[i].getClassName() + '.' + st[i].getMethodName();
                        }
                        log(line);
                    }
                }
            } catch(e) {}
        }
        return orig.call(this, c);
    };

    // ── helper: dump fragment fields ──────────────────────────────────
    function dumpFragFields(frag) {
        try {
            let cls = frag.getClass();
            let depth = 0;
            while (cls && cls.getName() !== 'java.lang.Object' && depth++ < 5) {
                const fields = cls.getDeclaredFields();
                fields.forEach(function(f) {
                    try {
                        f.setAccessible(true);
                        const v = f.get(frag);
                        if (v === null) return;
                        const vn = v.getClass().getName();
                        if (vn.includes('MvvmList') || vn.includes('Adapter') ||
                            (vn.includes('tencent') && !vn.includes('Context') &&
                             !vn.includes('Bundle') && !vn.includes('Layout'))) {
                            let info = 'fragField ' + f.getName() + ':' + vn;
                            try {
                                if (v.size) info += ' sz=' + v.size();
                            } catch(e2) {}
                            log(info);
                        }
                    } catch(e) {}
                });
                cls = cls.getSuperclass();
            }
        } catch(e) {}
    }

    // ── 4. Java.choose after 4s ───────────────────────────────────────
    setTimeout(function() {
        log('choosing MvvmAddressUIFragment...');
        try {
            Java.choose(ADDR_FRAG, {
                onMatch: function(inst) {
                    log('choose: found instance');
                    dumpFragFields(inst);
                },
                onComplete: function() {
                    log('choose: done');
                }
            });
        } catch(e) {
            log('choose fail: ' + e);
        }
    }, 4000);

    log('probe v4 ready');
});
