// trace_sns_flag.js — 精确追踪 sns_control_flag 的读写路径
// + 追踪写入 sns_control_flag 的调用栈（找增量路径）
// + 枚举所有 SP 文件名（找是哪个 namespace）

Java.perform(function() {

    var Log = Java.use("android.util.Log");
    function getStack() {
        return Log.getStackTraceString(
            Java.use("java.lang.Exception").$new("trace")
        ).substring(0, 800);
    }

    // ── 1. SharedPreferencesImpl.getInt — 精确拦截 sns_control_flag ──
    try {
        var SPImpl = Java.use("android.app.SharedPreferencesImpl");
        SPImpl.getInt.implementation = function(key, def) {
            var ret = this.getInt(key, def);
            if (key.indexOf("sns") !== -1 || key.indexOf("dot") !== -1
                || key.indexOf("badge") !== -1 || key.indexOf("red") !== -1
                || key.indexOf("notify") !== -1 || key.indexOf("unread") !== -1) {
                console.log("[SP-GET] " + key + "=" + ret + "\nSTACK:\n" + getStack());
            }
            return ret;
        };
        SPImpl.getBoolean.implementation = function(key, def) {
            var ret = this.getBoolean(key, def);
            if (key.indexOf("sns") !== -1 || key.indexOf("dot") !== -1
                || key.indexOf("badge") !== -1 || key.indexOf("red") !== -1) {
                console.log("[SP-GBOOL] " + key + "=" + ret + "\nSTACK:\n" + getStack());
            }
            return ret;
        };
        console.log("[SP] getInt/getBoolean hooked");
    } catch(e) { console.log("[SP] error: " + e); }

    // ── 2. SharedPreferencesImpl$EditorImpl.putInt — 找写入路径 ─────
    try {
        var EditorImpl = Java.use("android.app.SharedPreferencesImpl$EditorImpl");
        EditorImpl.putInt.implementation = function(key, val) {
            if (key.indexOf("sns") !== -1 || key.indexOf("dot") !== -1
                || key.indexOf("badge") !== -1) {
                console.log("[SP-PUT] " + key + "=" + val + "\nSTACK:\n" + getStack());
            }
            return this.putInt(key, val);
        };
        EditorImpl.putBoolean.implementation = function(key, val) {
            if (key.indexOf("sns") !== -1 || key.indexOf("dot") !== -1) {
                console.log("[SP-PUTB] " + key + "=" + val + "\nSTACK:\n" + getStack());
            }
            return this.putBoolean(key, val);
        };
        console.log("[SP-EDITOR] hooked");
    } catch(e) { console.log("[SP-EDITOR] error: " + e); }

    // ── 3. 枚举 SP 文件（找 namespace）─────────────────────────────
    setTimeout(function() {
        try {
            var ctx = Java.use("android.app.ActivityThread").currentApplication().getApplicationContext();
            var spDir = new java.io.File(ctx.getFilesDir().getParent() + "/shared_prefs");
            var files = spDir.listFiles();
            if (files) {
                console.log("[SP-FILES] count=" + files.length);
                for (var i = 0; i < files.length; i++) {
                    console.log("[SP-FILE] " + files[i].getName());
                }
            }
        } catch(e) { console.log("[SP-FILES] error: " + e); }
    }, 5000);

    // ── 4. 再次尝试 Java.choose 晚些时间 ───────────────────────────
    setTimeout(function() {
        console.log("[CHOOSE-LATE] looking for w1...");
        try {
            Java.choose("com.tencent.mm.plugin.sns.storage.w1", {
                onMatch: function(inst) {
                    console.log("[W1-INST] E1=" + inst.E1() + " f=" + inst.f() + " j2=" + inst.j2());
                    var fields = inst.class.getDeclaredFields();
                    for (var i = 0; i < fields.length; i++) {
                        fields[i].setAccessible(true);
                        var tn = fields[i].getType().getName();
                        if (tn === 'int' || tn === 'long') {
                            try {
                                console.log("[W1-FLD] " + fields[i].getName() + "=" + fields[i].get(inst));
                            } catch(e2) {}
                        }
                    }
                },
                onComplete: function() { console.log("[CHOOSE-LATE] done"); }
            });
        } catch(e) { console.log("[CHOOSE-LATE] error: " + e); }
    }, 15000);

    console.log("[INIT] done");
});
