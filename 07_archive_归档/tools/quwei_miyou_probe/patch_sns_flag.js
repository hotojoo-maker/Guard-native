// patch_sns_flag.js — attach 到运行中微信，找 sns_control_flag，清 bit1
// 用法: frida -D 609b4b18 -n com.tencent.mm -l patch_sns_flag.js
// 执行后 kill 微信再重启，看红点是否消失

Java.perform(function() {
    var File = Java.use("java.io.File");
    var ActivityThread = Java.use("android.app.ActivityThread");

    setTimeout(function() {
        try {
            var ctx = ActivityThread.currentApplication().getApplicationContext();
            var filesDir = ctx.getFilesDir().getParent();
            var spDir = File.$new(filesDir + "/shared_prefs");
            var files = spDir.listFiles();

            if (!files || files.length === 0) {
                console.log("[ERROR] no SP files found at: " + filesDir + "/shared_prefs");
                return;
            }

            console.log("[SP] scanning " + files.length + " SP files...");
            var found = false;
            for (var i = 0; i < files.length; i++) {
                var fname = files[i].getName().replace(".xml", "");
                try {
                    var sp = ctx.getSharedPreferences(fname, 0);
                    var val = sp.getInt("sns_control_flag", -9999);
                    if (val !== -9999) {
                        console.log("[FOUND] file=" + fname + "  sns_control_flag=" + val + " (0b" + val.toString(2) + ")");
                        var newVal = val & ~2;  // clear bit1
                        sp.edit().putInt("sns_control_flag", newVal).commit();
                        console.log("[PATCHED] " + val + " -> " + newVal);
                        console.log("[OK] 现在 kill 微信再重启，互动红点应该消失");
                        found = true;
                    }
                } catch(e2) { /* skip files that throw */ }
            }
            if (!found) {
                console.log("[MISS] 未找到 sns_control_flag，尝试 d6.k() 路径...");
                // fallback: Java.choose d6
                Java.choose("com.tencent.mm.plugin.sns.model.d6", {
                    onMatch: function(inst) {
                        try {
                            console.log("[D6] instance found, k()=" + inst.k());
                        } catch(e3) { console.log("[D6] k() err: " + e3); }
                    },
                    onComplete: function() { console.log("[D6] choose done"); }
                });
            }
        } catch(e) {
            console.log("[ERR] " + e);
        }
    }, 2000);
});
