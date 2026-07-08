// probe_quwei.js — attach 趣味密友(com.tencent.mmqw5)
// 目标: 找密友注入的类 + sns_control_flag 值 + showUnReadMsgCount hook

Java.perform(function() {

    // ── 1. 读 sns_control_flag 当前值 ───────────────────────────────
    setTimeout(function() {
        try {
            var ctx = Java.use("android.app.ActivityThread").currentApplication().getApplicationContext();
            var sp = ctx.getSharedPreferences("com.tencent.mm_preferences", 0);
            var val = sp.getInt("sns_control_flag", -1);
            console.log("[SP] sns_control_flag = " + val + " (0b" + val.toString(2) + ")");
        } catch(e) { console.log("[SP] " + e); }
    }, 1000);

    // ── 2. 找密友注入类（非 com.tencent 包） ───────────────────────
    setTimeout(function() {
        console.log("[SCAN] scanning non-tencent classes...");
        var count = 0;
        Java.enumerateLoadedClasses({
            onMatch: function(name) {
                if (name.indexOf("com.tencent") === -1
                    && name.indexOf("android.") === -1
                    && name.indexOf("java.") === -1
                    && name.indexOf("javax.") === -1
                    && name.indexOf("dalvik.") === -1
                    && name.indexOf("libcore.") === -1
                    && name.indexOf("sun.") === -1
                    && name.indexOf("kotlin") === -1
                    && name.indexOf("com.google") === -1
                    && name.indexOf("com.xiaomi") === -1
                    && name.indexOf("com.miui") === -1) {
                    console.log("[CLASS] " + name);
                    count++;
                }
            },
            onComplete: function() { console.log("[SCAN] done, non-tencent count=" + count); }
        });
    }, 2000);

    // ── 3. hook showUnReadMsgCount（密友refs里有这个） ─────────────
    setTimeout(function() {
        try {
            // 搜所有加载类里有 showUnReadMsgCount 的
            Java.enumerateLoadedClasses({
                onMatch: function(name) {
                    try {
                        var cls = Java.use(name);
                        var methods = cls.class.getDeclaredMethods();
                        for (var i = 0; i < methods.length; i++) {
                            if (methods[i].getName().indexOf("showUnRead") !== -1
                                || methods[i].getName().indexOf("UnReadMsg") !== -1
                                || methods[i].getName().indexOf("unread") !== -1) {
                                console.log("[UNREAD] " + name + "." + methods[i].getName());
                            }
                        }
                    } catch(e2) {}
                },
                onComplete: function() { console.log("[UNREAD-SCAN] done"); }
            });
        } catch(e) { console.log("[UNREAD] " + e); }
    }, 3000);

    console.log("[INIT] done");
});
