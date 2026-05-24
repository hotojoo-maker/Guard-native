/**
 * probe_video_fields.js
 * 扫 VideoActivity 所有实例字段，找含 wxid_ 的值
 * 如果 wxid 在 ViewModel/管理类里，会递归进去
 *
 * frida -U -n com.tencent.mm -l probe_video_fields.js --no-pause
 * 密友打来电话，看 [VA:FOUND] 行
 */
"use strict";

Java.perform(function () {
    var TARGET  = "wxid_lzd2va16jd1622";
    var VISITED = new WeakMap();

    function scan(label, obj, depth) {
        if (obj == null || depth > 4) return;
        try {
            if (VISITED.has(obj)) return;
            VISITED.set(obj, true);
        } catch (e) { return; }

        var cls = obj.getClass();
        var c = cls;
        while (c != null && c.getName() !== "java.lang.Object") {
            var fields = c.getDeclaredFields();
            for (var i = 0; i < fields.length; i++) {
                var f = fields[i];
                try {
                    f.setAccessible(true);
                    var v = f.get(obj);
                    if (v == null) continue;
                    var sv = String(v);
                    if (sv.indexOf(TARGET) >= 0) {
                        console.log("[VA:FOUND] " + label + c.getSimpleName()
                            + "." + f.getName()
                            + "[" + f.getType().getSimpleName() + "]=" + sv + " <<<");
                    }
                    // recurse into WeChat/tencent objects
                    var tn = f.getType().getName();
                    if (depth < 3 && tn.indexOf("tencent") >= 0) {
                        scan(label + c.getSimpleName() + "." + f.getName() + "→", v, depth + 1);
                    }
                } catch (e) { /* skip inaccessible */ }
            }
            c = c.getSuperclass();
        }
    }

    var Activity = Java.use("android.app.Activity");
    // Hook onResume — activity is fully initialized by then
    Activity.onResume.implementation = function () {
        this.onResume();
        var name = this.getClass().getName();
        if (!name.toLowerCase().includes("video") && !name.toLowerCase().includes("voip")) return;

        console.log("\n[VA] scanning " + this.getClass().getSimpleName() + " for wxid...");
        scan("", this, 0);
        console.log("[VA] scan done");
    };

    console.log("[VA] probe ready");
});
