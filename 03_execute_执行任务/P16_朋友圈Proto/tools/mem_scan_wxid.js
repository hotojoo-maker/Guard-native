/**
 * mem_scan_wxid.js — 轻量版：枚举所有加载类，读 String 字段
 * 不扫 ArrayList、不 Java.choose，纯静态反射
 * 用法: frida -U -p <PID> -l tools/mem_scan_wxid.js
 */
var TARGET = "wxid_lzd2va16jd1622";
var hits = 0;
var found = [];

Java.perform(function() {
    console.log("[SCAN] === mem_scan_wxid  TARGET=" + TARGET + " ===\n");
    var start = Date.now();
    var total = 0;
    var skipped = 0;

    Java.enumerateLoadedClasses({
        onMatch: function(name) {
            total++;
            // 跳过框架类
            if (name.startsWith("java.") || name.startsWith("android.") ||
                name.startsWith("kotlin.") || name.startsWith("dalvik.") ||
                name.startsWith("jdk.") || name.startsWith("sun.") ||
                name.startsWith("[")) { skipped++; return; }

            // 跳过 finder.reddot (大量，与朋友圈无关)
            if (name.indexOf("finder") !== -1 && name.indexOf("reddot") !== -1) { skipped++; return; }
            // 跳过 render / cara / kinda / repairer
            if (name.indexOf("render") !== -1 || name.indexOf("cara") !== -1 ||
                name.indexOf("kinda") !== -1 || name.indexOf("repairer") !== -1) { skipped++; return; }

            var cls = null;
            try { cls = Java.use(name); } catch(e) { return; }
            if (!cls || !cls.class) return;

            try {
                var fields = cls.class.getDeclaredFields();
                for (var i = 0; i < fields.length; i++) {
                    try {
                        var f = fields[i];
                        if (!f.isStatic()) continue;
                        if (f.getType().getName() !== "java.lang.String") continue;
                        f.setAccessible(true);
                        var v = f.get(null);
                        if (v === TARGET) {
                            hits++;
                            found.push("[STATIC] " + name + "." + f.getName());
                            console.log("[HIT-STATIC] " + name + "." + f.getName() + " = " + TARGET);
                        }
                    } catch(e) {}
                }
            } catch(e) {}
        },
        onComplete: function() {
            var elapsed = ((Date.now() - start) / 1000).toFixed(1);
            console.log("\n[SCAN] === DONE in " + elapsed + "s ===");
            console.log("[SCAN] Total=" + total + " Skipped=" + skipped + " Hits=" + hits);
            if (found.length > 0) {
                console.log("[SCAN] Found:");
                found.forEach(function(f) { console.log("  " + f); });
            } else {
                console.log("[SCAN] TARGET wxid NOT FOUND in any static String field");
            }
        }
    });
});
