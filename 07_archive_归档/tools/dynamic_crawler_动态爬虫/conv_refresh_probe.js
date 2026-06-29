'use strict';
/**
 * conv_refresh_probe.js v2 — 会话列表热更新上游 + 通讯录 Fragment 类名
 */
Java.perform(function () {
    var TAG = '[PROBE]';
    var printed = {};

    function stackShort(depth) {
        try {
            return Java.use('android.util.Log').getStackTraceString(
                Java.use('java.lang.Exception').$new()
            ).split('\n').slice(2, 2 + (depth || 14)).join('\n');
        } catch(e) { return '(err)'; }
    }

    // ==== MvvmList.n(List, boolean) — L1 ====
    try {
        var MvvmList = Java.use('com.tencent.mm.plugin.mvvmlist.MvvmList');
        // 枚举找 n(List, boolean)
        var methods = MvvmList.class.getDeclaredMethods();
        var hooked = 0;
        for (var i = 0; i < methods.length && hooked < 3; i++) {
            var m = methods[i];
            var mn = m.getName();
            var pts = m.getParameterTypes();
            if (pts.length === 2 &&
                pts[0].getName() === 'java.util.List' &&
                pts[1].getName() === 'boolean') {
                try {
                    MvvmList[mn].overload('java.util.List', 'boolean').implementation = function(list, z) {
                        if (!printed['mvvm.' + mn]) {
                            printed['mvvm.' + mn] = true;
                            console.log('\n[STACK] MvvmList.' + mn + '(List,bool) sz=' + (list ? list.size() : 0));
                            console.log(stackShort(14));
                        }
                        return this[mn](list, z);
                    };
                    hooked++;
                    console.log('[PROBE] MvvmList.' + mn + '(List,bool) ok');
                } catch(e) {}
            }
        }
        console.log('[PROBE] MvvmList hooked=' + hooked);
    } catch(e) { console.log('[PROBE] MvvmList fail: ' + e); }

    // ==== kc5.v0.notifyDataSetChanged — L4 ====
    try {
        Java.use('kc5.v0').notifyDataSetChanged.implementation = function() {
            if (!printed['kc5.v0']) {
                printed['kc5.v0'] = true;
                console.log('\n[STACK] kc5.v0.notifyDataSetChanged');
                console.log(stackShort(14));
            }
            return this.notifyDataSetChanged();
        };
        console.log('[PROBE] kc5.v0.notify ok');
    } catch(e) { console.log('[PROBE] kc5.v0 fail: ' + e); }

    // ==== kc5.a 关键方法 ====
    try {
        var kc5a = Java.use('kc5.a');
        var methods = kc5a.class.getDeclaredMethods();
        var n = 0;
        for (var i = 0; i < methods.length && n < 6; i++) {
            var m = methods[i];
            var mn = m.getName();
            var pts = m.getParameterTypes();
            if (mn.length <= 2 || pts.length > 2) continue;
            try {
                var sig = Array.from({length: pts.length}, function(_, j) { return pts[j].getName(); });
                var fn = sig.length > 0 ? kc5a[mn].overload.apply(kc5a[mn], sig) : kc5a[mn].overload();
                (function(name) {
                    fn.implementation = function() {
                        if (!printed['kc5a.' + name]) {
                            printed['kc5a.' + name] = true;
                            console.log('\n[STACK] kc5.a.' + name + ' args=' + arguments.length);
                            console.log(stackShort(14));
                        }
                        return this[name].apply(this, arguments);
                    };
                })(mn);
                console.log('[PROBE] kc5.a.' + mn + ' ok');
                n++;
            } catch(e) {}
        }
        console.log('[PROBE] kc5.a hooked=' + n);
    } catch(e) { console.log('[PROBE] kc5.a fail: ' + e); }

    // ==== Fragment.onHiddenChanged ====
    try {
        Java.use('androidx.fragment.app.Fragment').onHiddenChanged.implementation = function(hidden) {
            var cls = this.getClass().getName();
            if (!/^(android|androidx|com\.android|java)/.test(cls)) {
                console.log('[FRAGX] onHiddenChanged hidden=' + hidden + ' cls=' + cls);
            }
            return this.onHiddenChanged(hidden);
        };
        console.log('[PROBE] Fragment.onHiddenChanged ok');
    } catch(e) { console.log('[PROBE] Frag fail: ' + e); }

    console.log('[PROBE] === ready. 好友发消息 + 切通讯录tab ===');
});
