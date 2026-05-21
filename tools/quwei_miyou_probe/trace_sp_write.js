// trace_sp_write.js — 找启动时谁写入 sns_control_flag
// spawn 模式：frida -D 609b4b18 -f com.tencent.mm -l trace_sp_write.js

Java.perform(function() {
    var Log = Java.use("android.util.Log");

    // ── SP putInt 写入监控（sns 相关） ───────────────────────────────
    try {
        var EditorImpl = Java.use("android.app.SharedPreferencesImpl$EditorImpl");
        EditorImpl.putInt.implementation = function(key, val) {
            if (key.indexOf("sns") !== -1) {
                var stack = Log.getStackTraceString(
                    Java.use("java.lang.Exception").$new("t")
                ).substring(0, 800);
                console.log("[SP-WRITE] putInt " + key + "=" + val + "\n" + stack);
            }
            return this.putInt(key, val);
        };
        console.log("[SP-WRITE] hooked");
    } catch(e) { console.log("[SP-WRITE] err: " + e); }

    // ── WCDB rawQuery — 找 SnsComment 的读取 ──────────────────────
    try {
        var db = Java.use("com.tencent.wcdb.database.SQLiteDatabase");
        db.rawQuery.overload('java.lang.String', '[Ljava.lang.String;').implementation = function(sql, args) {
            var ret = this.rawQuery(sql, args);
            if (sql.toLowerCase().indexOf("snscomment") !== -1
                || sql.toLowerCase().indexOf("sns_comment") !== -1
                || sql.toLowerCase().indexOf("isread") !== -1) {
                console.log("[WCDB] rawQuery: " + sql.substring(0, 200));
            }
            return ret;
        };
        console.log("[WCDB] rawQuery hooked");
    } catch(e) { console.log("[WCDB] err: " + e); }

    console.log("[INIT] done");
});
