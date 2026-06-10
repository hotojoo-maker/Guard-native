'use strict';
/*
 * P21 probe - dump com.tencent.mm.ui.s9 Cursor-related methods.
 * Read-only; no hooks.
 */
Java.perform(function () {
    var Log = Java.use('android.util.Log');
    function emit(msg) {
        var line = '[S9M] ' + msg;
        try { Log.i('NCL', line); } catch (e) {}
        try { console.log(line); } catch (e) {}
    }

    function typeName(t) {
        try { return t.getName().toString(); } catch (e) { return String(t); }
    }

    try {
        var S9 = Java.use('com.tencent.mm.ui.s9');
        var cls = S9.class;
        emit('class=' + cls.getName() + ' super=' + cls.getSuperclass().getName());

        var ctors = cls.getDeclaredConstructors();
        for (var c = 0; c < ctors.length; c++) {
            var cp = ctors[c].getParameterTypes();
            var ca = [];
            for (var ci = 0; ci < cp.length; ci++) ca.push(typeName(cp[ci]));
            emit('ctor(' + ca.join(',') + ')');
        }

        var methods = cls.getDeclaredMethods();
        for (var i = 0; i < methods.length; i++) {
            var m = methods[i];
            var ps = m.getParameterTypes();
            var args = [];
            var hit = false;
            for (var p = 0; p < ps.length; p++) {
                var pn = typeName(ps[p]);
                args.push(pn);
                if (pn.indexOf('Cursor') >= 0) hit = true;
            }
            var rn = typeName(m.getReturnType());
            if (rn.indexOf('Cursor') >= 0) hit = true;
            if (hit || m.getName().toString().toLowerCase().indexOf('cursor') >= 0) {
                emit('method ' + m.getName() + '(' + args.join(',') + '):' + rn);
            }
        }
    } catch (e) {
        emit('err=' + e);
    }
});
