/*
 * probe_revoke_path.js — 定位 8.0.71 撤回处理路径
 *
 * 思路：撤回 = 对方发 <sysmsg type="revokemsg"> 系统消息过来，
 *       微信解析后把原消息在本地 DB 标记为"已撤回"。
 *       a2.b 已被动态证伪（不在撤回链上）。
 *       本探针在 messenger.foundation 包里 hook 所有带 String/byte[] 参数的方法，
 *       命中含 "revokemsg" 的调用就打出 类名.方法名 + 入参片段 + 调用栈。
 *
 * 用法：frida -U -n com.tencent.mm -l probe_revoke_path.js
 */
Java.perform(function () {
    var TARGET_PKGS = [
        'com.tencent.mm.plugin.messenger.foundation.'
    ];
    var MARKERS = ['revokemsg', 'revoke', '撤回'];

    function inTarget(name) {
        for (var i = 0; i < TARGET_PKGS.length; i++) {
            if (name.indexOf(TARGET_PKGS[i]) === 0) return true;
        }
        return false;
    }

    var Log = Java.use('android.util.Log');
    var Throwable = Java.use('java.lang.Throwable');
    function stack() {
        return Log.getStackTraceString.call(Log, Throwable.$new());
    }

    function decodeBytes(b) {
        try {
            if (b == null) return '';
            if (b.length > 8192) return '<bytes ' + b.length + '>';
            return Java.use('java.lang.String').$new(b, 'UTF-8');
        } catch (e) { return ''; }
    }

    function argText(a) {
        if (a == null) return '';
        // byte[] 在 frida 里是 array，type 'object'，length 数字
        try {
            if (Object.prototype.toString.call(a) === '[object Array]' || (typeof a === 'object' && a.length !== undefined && typeof a.length === 'number')) {
                return decodeBytes(a);
            }
        } catch (e) {}
        try { return '' + a; } catch (e) { return ''; }
    }

    function hit(s) {
        for (var i = 0; i < MARKERS.length; i++) {
            if (s && s.indexOf(MARKERS[i]) >= 0) return MARKERS[i];
        }
        return null;
    }

    var classes = Java.enumerateLoadedClassesSync();
    var hooked = 0, scanned = 0;
    classes.forEach(function (name) {
        if (!inTarget(name)) return;
        scanned++;
        var cls;
        try { cls = Java.use(name); } catch (e) { return; }
        var methods;
        try { methods = cls.class.getDeclaredMethods(); } catch (e) { return; }
        for (var mi = 0; mi < methods.length; mi++) {
            var m = methods[mi];
            var mname = m.getName();
            var ptypes;
            try { ptypes = m.getParameterTypes(); } catch (e) { continue; }
            var hasStr = false;
            for (var j = 0; j < ptypes.length; j++) {
                var pn = ptypes[j].getName();
                if (pn === 'java.lang.String' || pn === '[B' || pn === 'java.lang.CharSequence') { hasStr = true; break; }
            }
            if (!hasStr) continue;
            var overloads;
            try { overloads = cls[mname].overloads; } catch (e) { continue; }
            overloads.forEach(function (ov) {
                try {
                    ov.implementation = function () {
                        try {
                            for (var k = 0; k < arguments.length; k++) {
                                var t = argText(arguments[k]);
                                var mk = hit(t);
                                if (mk) {
                                    var snippet = t.length > 400 ? t.substring(0, 400) : t;
                                    console.log('\n========== [HIT:' + mk + '] ' + name + '.' + mname + ' arg#' + k + ' ==========');
                                    console.log('ARG: ' + snippet);
                                    console.log('STACK:\n' + stack());
                                    break;
                                }
                            }
                        } catch (e) {}
                        return ov.apply(this, arguments);
                    };
                    hooked++;
                } catch (e) {}
            });
        }
    });
    console.log('[PROBE] ready: scanned ' + scanned + ' classes, hooked ' + hooked + ' methods. markers=' + MARKERS.join(','));
    console.log('[PROBE] >>> 现在请撤回一条消息 <<<');
});
