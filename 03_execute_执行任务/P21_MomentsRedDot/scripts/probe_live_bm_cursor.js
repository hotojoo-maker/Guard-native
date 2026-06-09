'use strict';
/*
 * P21 probe - live bm/rm adapter Cursor dump (read-only).
 * Attach while SnsMsgUIWithAll / SnsMsgUIWithRelevance is visible.
 */
Java.perform(function () {
    var Log = Java.use('android.util.Log');
    var Modifier = Java.use('java.lang.reflect.Modifier');
    var Cursor = Java.use('android.database.Cursor');

    function emit(msg) {
        var line = '[BMCUR] ' + msg;
        try { Log.i('NCL', line); } catch (e) {}
        try { console.log(line); } catch (e) {}
    }

    var hidden = {};
    var hiddenArr = [];
    try {
        var loaders = Java.enumerateClassLoadersSync();
        for (var li = 0; li < loaders.length; li++) {
            try {
                var B = Java.ClassFactory.get(loaders[li]).use('com.ghost.assist.core.Bridge');
                var it = B.getInstance().getWxids().iterator();
                while (it.hasNext()) {
                    var wx = it.next().toString();
                    hidden[wx] = true;
                    hiddenArr.push(wx);
                }
                break;
            } catch (e) {}
        }
    } catch (e) {}
    emit('hidden=' + hiddenArr.join(','));

    function safeClass(o) {
        try { return o.getClass().getName().toString(); } catch (e) { return '' + o; }
    }

    function trunc(s) {
        if (s === null || s === undefined) return 'null';
        s = String(s);
        return s.length > 72 ? s.substring(0, 72) + '...' : s;
    }

    function dumpCursor(label, cur) {
        try {
            var cnt = cur.getCount();
            var pos = cur.getPosition();
            var cols = cur.getColumnNames();
            var colNames = [];
            for (var c = 0; c < cols.length; c++) colNames.push(cols[c].toString());
            emit(label + ' cursor=' + safeClass(cur) + ' count=' + cnt + ' pos=' + pos);
            emit(label + ' cols=' + colNames.join(','));

            var max = Math.min(cnt, 12);
            var hiddenRows = 0;
            for (var r = 0; r < max; r++) {
                cur.moveToPosition(r);
                var parts = [];
                var rowHidden = false;
                for (var j = 0; j < colNames.length; j++) {
                    var val = null;
                    try { val = cur.getString(j); } catch (e1) {
                        try { val = '' + cur.getLong(j); } catch (e2) {}
                    }
                    if (val !== null && val !== undefined && String(val).length > 0) {
                        if (hidden[String(val)]) rowHidden = true;
                        parts.push(j + ':' + colNames[j] + '=' + trunc(val));
                    }
                }
                if (rowHidden) hiddenRows++;
                emit(label + ' row' + r + (rowHidden ? ' HIDDEN' : '') + ' ' + parts.join(' | '));
            }
            try { cur.moveToPosition(pos); } catch (e3) {}
            emit(label + ' sampleHiddenRows=' + hiddenRows + '/' + max);
        } catch (e) {
            emit(label + ' cursor dump err=' + e);
        }
    }

    function dumpAdapter(tag, obj) {
        emit('FOUND ' + tag + ' inst=' + safeClass(obj));
        var cls = obj.getClass();
        for (var depth = 0; cls !== null && depth < 8; depth++) {
            var cn = cls.getName().toString();
            if (cn.indexOf('android.') === 0 || cn.indexOf('java.') === 0) break;
            emit(tag + ' classDepth' + depth + '=' + cn);
            var fields = cls.getDeclaredFields();
            for (var i = 0; i < fields.length; i++) {
                try {
                    var f = fields[i];
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    var v = f.get(obj);
                    if (v === null) continue;
                    var fn = f.getName().toString();
                    var ft = f.getType().getName().toString();
                    var vc = safeClass(v);
                    if (vc.indexOf('Cursor') >= 0) {
                        emit(tag + ' field ' + cn + '.' + fn + ':' + ft + ' = ' + vc);
                    }
                    try {
                        if (Cursor.class.isInstance(v)) {
                            dumpCursor(tag + '.' + fn, Java.cast(v, Cursor));
                            continue;
                        }
                    } catch (ce) {}
                    if (vc.indexOf('Cursor') >= 0) {
                        try { dumpCursor(tag + '.' + fn, v); } catch (de) {}
                    } else if (vc.indexOf('List') >= 0 || vc.indexOf('Array') >= 0 || vc.indexOf('Linked') >= 0) {
                        var sz = -1;
                        try { sz = v.size(); } catch (se) {}
                        emit(tag + ' field ' + cn + '.' + fn + ':' + ft + ' = ' + vc + ' size=' + sz);
                    }
                } catch (e) {}
            }
            cls = cls.getSuperclass();
        }
    }

    ['com.tencent.mm.plugin.sns.ui.bm', 'com.tencent.mm.plugin.sns.ui.rm'].forEach(function (name) {
        try {
            Java.choose(name, {
                onMatch: function (obj) { dumpAdapter(name.substring(name.lastIndexOf('.') + 1), obj); },
                onComplete: function () { emit('choose done ' + name); }
            });
        } catch (e) {
            emit('choose err ' + name + ' ' + e);
        }
    });
});
