/**
 * probe_contacts.js v3 — MvvmAddressUIFragment 深度探针
 * 目标：找通讯录 MvvmList 字段 + item 类型 + adapter 类名
 */
'use strict';

Java.perform(function () {
    const MVVM_CLASS = 'com.tencent.mm.plugin.mvvmlist.MvvmList';
    const ADDR_FRAG  = 'com.tencent.mm.ui.contact.address.MvvmAddressUIFragment';

    // ── 1. Hook MvvmAddressUIFragment — dump all fields on first call ──
    try {
        const Frag = Java.use(ADDR_FRAG);
        const methods = Frag.class.getDeclaredMethods();
        console.log('[probe] MvvmAddressUIFragment method count=' + methods.length);

        // hook onResume / onStart to dump fields
        ['onResume', 'onStart', 'onViewCreated'].forEach(function(mn) {
            try {
                Frag[mn].overloads.forEach(function(ov) {
                    ov.implementation = function() {
                        const ret = ov.apply(this, arguments);
                        dumpFragFields(this);
                        return ret;
                    };
                });
                console.log('[probe] hooked ' + mn);
            } catch(e) {}
        });
    } catch(e) {
        console.log('[probe] MvvmAddressUIFragment fail: ' + e);
    }

    // ── 2. Hook ALL MvvmList.m() calls — find contact data ────────────
    try {
        const MvvmList = Java.use(MVVM_CLASS);
        const methods = MvvmList.class.getDeclaredMethods();
        methods.forEach(function(m) {
            const mn = m.getName();
            if (mn !== 'm' && mn !== 's') return;
            try {
                MvvmList[mn].overloads.forEach(function(ov) {
                    ov.implementation = function() {
                        const thisClass = this.getClass().getName();
                        // Only log contact-related
                        if (thisClass.toLowerCase().includes('address') ||
                            thisClass.toLowerCase().includes('contact')) {
                            const args = Array.prototype.slice.call(arguments);
                            let info = '[MvvmList.' + mn + '] this=' + thisClass;
                            if (args[0] && args[0].size) {
                                info += ' sz=' + args[0].size();
                                if (!args[0].isEmpty()) {
                                    try {
                                        info += ' itemCls=' + args[0].iterator().next().getClass().getName();
                                    } catch(e) {}
                                }
                            }
                            console.log(info);
                        }
                        return ov.apply(this, arguments);
                    };
                });
            } catch(e) {}
        });
        // Also hook ALL subclasses dynamically
        console.log('[probe] MvvmList.m/s hooks installed');
    } catch(e) {
        console.log('[probe] MvvmList hook fail: ' + e);
    }

    // ── 3. Java.choose — find live MvvmAddressUIFragment instances ──────
    setTimeout(function() {
        console.log('[probe] choosing MvvmAddressUIFragment instances...');
        try {
            Java.choose(ADDR_FRAG, {
                onMatch: function(inst) {
                    console.log('[choose] found instance: ' + inst);
                    dumpFragFields(inst);
                },
                onComplete: function() {
                    console.log('[choose] done');
                }
            });
        } catch(e) {
            console.log('[choose] fail: ' + e);
        }
    }, 5000);

    // ── helper: dump fragment fields looking for MvvmList ──────────────
    function dumpFragFields(frag) {
        try {
            let cls = frag.getClass();
            while (cls && cls.getName() !== 'java.lang.Object') {
                const fields = cls.getDeclaredFields();
                fields.forEach(function(f) {
                    try {
                        f.setAccessible(true);
                        const v = f.get(frag);
                        if (v === null) return;
                        const vn = v.getClass().getName();
                        // Look for MvvmList or adapter-like fields
                        if (vn.includes('MvvmList') || vn.includes('Adapter') ||
                            vn.includes('RecyclerView') ||
                            (vn.includes('tencent') && !vn.includes('Context'))) {
                            console.log('[field] ' + cls.getName() + '.' + f.getName()
                                + ' : ' + vn);
                            // If it's a list, show size + first item
                            try {
                                if (v.size && v.size() > 0) {
                                    const fi = v.iterator().next();
                                    console.log('  → sz=' + v.size()
                                        + ' firstItemCls=' + fi.getClass().getName());
                                }
                            } catch(e2) {}
                        }
                    } catch(e) {}
                });
                cls = cls.getSuperclass();
            }
        } catch(e) {
            console.log('[dumpFragFields] err: ' + e);
        }
    }

    // ── 4. Broad ArrayList.addAll — log ALL tencent items ──────────────
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
                        let line = '[addAll] itemCls=' + fcn + ' sz=' + c.size();
                        for (let i = 3; i < Math.min(7, st.length); i++) {
                            line += '\n  [' + i + '] ' + st[i].getClassName()
                                    + '.' + st[i].getMethodName();
                        }
                        console.log(line);
                    }
                }
            } catch(e) {}
        }
        return orig.call(this, c);
    };

    console.log('[probe] v3 ready — switch to contacts tab now');
});
