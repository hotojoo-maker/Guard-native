// find_uniq.js — 拦截 SQLite Cursor.getString，只抓目标数字
Java.perform(function() {
    var KEY = "9988776655";
    var Log = Java.use("android.util.Log");
    var Ex = Java.use("java.lang.Exception");

    function stack() {
        return Log.getStackTraceString(Ex.$new()).split("\n").slice(1, 7).join("\n");
    }

    try {
        var c = Java.use("android.database.sqlite.SQLiteCursor");
        c.getColumnIndex.overload('java.lang.String').implementation = function(col) {
            // 不下钩，纯过路
            return this.getColumnIndex(col);
        };
    } catch(e) {}

    // 挂 AbstractCursor.getString — 所有 Cursor 都走它
    try {
        var ac = Java.use("android.database.AbstractCursor");
        ac.getString.overload('int').implementation = function(idx) {
            var s = this.getString(idx);
            if (s.indexOf(KEY) !== -1) {
                console.log("[HIT] " + s + "\n" + stack());
            }
            return s;
        };
        console.log("[OK] AbstractCursor.getString hooked");
    } catch(e) {
        console.log("[ERR] " + e);
    }
});
