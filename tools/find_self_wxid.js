/**
 * find_self_wxid.js
 * 用途: 找出微信里存当前登录 wxid 的 SharedPreferences 文件 + 键名
 *
 * 运行: frida -U -n com.tencent.mm -l find_self_wxid.js
 */

Java.perform(function () {

    var TARGET = "wxid_";  // 只打印包含这个前缀的值

    // ── 1. Hook getSharedPreferences ──────────────────────────────────────
    var ContextWrapper = Java.use("android.content.ContextWrapper");
    ContextWrapper.getSharedPreferences.overload("java.lang.String", "int")
        .implementation = function (name, mode) {
        var sp = this.getSharedPreferences(name, mode);
        try {
            var all = sp.getAll();
            var keys = all.keySet().toArray();
            for (var i = 0; i < keys.length; i++) {
                var k = keys[i].toString();
                var v = all.get(keys[i]);
                if (v && v.toString().indexOf(TARGET) >= 0) {
                    console.log("[PREF] file=" + name + " key=" + k + " val=" + v);
                }
            }
        } catch (e) {}
        return sp;
    };

    // ── 2. 直接 dump switch_account_preferences 所有键 ───────────────────
    var app = Java.use("android.app.ActivityThread").currentApplication();
    if (app) {
        var sp = app.getSharedPreferences("switch_account_preferences", 0);
        var all = sp.getAll();
        var keys = all.keySet().toArray();
        console.log("[DUMP] switch_account_preferences keys: " + keys.length);
        for (var i = 0; i < keys.length; i++) {
            console.log("  " + keys[i] + " = " + all.get(keys[i]));
        }
    }

    // ── 3. 也 dump com.tencent.mm_preferences ────────────────────────────
    if (app) {
        var sp2 = app.getSharedPreferences("com.tencent.mm_preferences", 0);
        var all2 = sp2.getAll();
        var keys2 = all2.keySet().toArray();
        console.log("[DUMP] mm_preferences keys: " + keys2.length);
        for (var i = 0; i < keys2.length; i++) {
            var k = keys2[i].toString();
            var v = all2.get(keys2[i]);
            // 只打印可能含 wxid 的短字符串
            if (v && typeof v.toString() === "string" && v.toString().length < 60) {
                console.log("  " + k + " = " + v);
            }
        }
    }

    console.log("[find_self_wxid] hooks installed, watching getSharedPreferences...");
});
