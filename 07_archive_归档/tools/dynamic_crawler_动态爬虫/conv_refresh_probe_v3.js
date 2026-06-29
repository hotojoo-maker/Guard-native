'use strict';
/**
 * v3 — hook 全链 + Fragment.setUserVisibleHint
 */
Java.perform(function () {
    var T = '[PRB]';
    var printed = {};

    function stack(d) {
        try {
            return Java.use('android.util.Log').getStackTraceString(
                Java.use('java.lang.Exception').$new()
            ).split('\n').slice(2, (d || 16) + 2).join('\n');
        } catch(e) { return '(err)'; }
    }

    function hook0(cls, method) {
        try {
            var C = Java.use(cls);
            C[method].overload().implementation = function() {
                if (!printed[cls + '.' + method]) {
                    printed[cls + '.' + method] = true;
                    console.log('\n[STACK] ' + cls + '.' + method + '()');
                    console.log(stack(16));
                }
                return this[method]();
            };
            console.log(T + ' ' + cls + '.' + method + '() ok');
        } catch(e) { console.log(T + ' ' + cls + '.' + method + ' fail: ' + e.message); }
    }

    function hookAll0(clsName) {
        try {
            var C = Java.use(clsName);
            var methods = C.class.getDeclaredMethods();
            for (var i = 0; i < methods.length; i++) {
                var m = methods[i], mn = m.getName(), pts = m.getParameterTypes();
                if (pts.length !== 0) continue;
                try {
                    C[mn].overload().implementation = (function(name) {
                        return function() {
                            if (!printed[clsName + '.' + name]) {
                                printed[clsName + '.' + name] = true;
                                console.log('\n[STACK] ' + clsName + '.' + name + '()');
                                console.log(stack(16));
                            }
                            return this[name]();
                        };
                    })(mn);
                    console.log(T + ' ' + clsName + '.' + mn + '() ok');
                } catch(e) {}
            }
        } catch(e) { console.log(T + ' ' + clsName + ' fail: ' + e.message); }
    }

    function hookAll1(clsName) {
        try {
            var C = Java.use(clsName);
            var methods = C.class.getDeclaredMethods();
            for (var i = 0; i < methods.length; i++) {
                var m = methods[i], mn = m.getName(), pts = m.getParameterTypes();
                if (pts.length !== 1) continue;
                try {
                    var pt = pts[0].getName();
                    C[mn].overload(pt).implementation = (function(name, pn) {
                        return function(a) {
                            if (!printed[clsName + '.' + name]) {
                                printed[clsName + '.' + name] = true;
                                console.log('\n[STACK] ' + clsName + '.' + name + '(' + pn + ')');
                                console.log(stack(16));
                            }
                            return this[name](a);
                        };
                    })(mn, pt);
                    console.log(T + ' ' + clsName + '.' + mn + '(' + pt + ') ok');
                } catch(e) {}
            }
        } catch(e) { console.log(T + ' ' + clsName + ' fail: ' + e.message); }
    }

    // ── 栈链底层 ──
    hook0('kc5.v0', 'notifyDataSetChanged');
    hook0('kc5.r0', 'd');

    // ── MvvmList 全部无参 + 单参 ──
    hookAll0('com.tencent.mm.plugin.mvvmlist.MvvmList');
    hookAll1('com.tencent.mm.plugin.mvvmlist.MvvmList');

    // ── ik3.m 全方法 ──
    hookAll0('ik3.m');
    hookAll1('ik3.m');

    // ── cl0.u 全方法 ──
    hookAll0('cl0.u');
    hookAll1('cl0.u');

    // ── ik3.n 全方法 ──
    hookAll0('ik3.n');
    hookAll1('ik3.n');

    // ── h45.i.handleMessage ──
    try {
        Java.use('h45.i').handleMessage.overload('android.os.Message').implementation = function(msg) {
            if (!printed['h45.i.handleMessage']) {
                printed['h45.i.handleMessage'] = true;
                console.log('\n[STACK] h45.i.handleMessage(Message)');
                console.log(stack(16));
            }
            return this.handleMessage(msg);
        };
        console.log(T + ' h45.i.handleMessage ok');
    } catch(e) { console.log(T + ' h45.i fail: ' + e.message); }

    // ── Fragment.setUserVisibleHint ──
    try {
        Java.use('androidx.fragment.app.Fragment').setUserVisibleHint.implementation = function(v) {
            var cls = this.getClass().getName();
            if (!/^(android|androidx|com\.android|java)/.test(cls)) {
                console.log('[FRAGX] setUserVisibleHint visible=' + v + ' cls=' + cls);
            }
            return this.setUserVisibleHint(v);
        };
        console.log(T + ' Fragment.setUserVisibleHint ok');
    } catch(e) { console.log(T + ' setUserVisibleHint fail: ' + e.message); }

    // ── Fragment.onResume ──
    try {
        Java.use('androidx.fragment.app.Fragment').onResume.implementation = function() {
            var cls = this.getClass().getName();
            if (!/^(android|androidx|com\.android|java)/.test(cls)) {
                console.log('[FRAGX] onResume cls=' + cls);
            }
            return this.onResume();
        };
        console.log(T + ' Fragment.onResume ok');
    } catch(e) { console.log(T + ' Frag.onResume fail: ' + e.message); }

    console.log(T + ' === ready ===');
});
