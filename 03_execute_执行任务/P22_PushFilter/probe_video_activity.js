/**
 * probe_video_activity.js
 * 目标：dump VideoActivity Intent extras 里 FlutterPageInfo 的全部字段，找 caller wxid
 *
 * 用法：
 *   frida -U -n com.tencent.mm -l probe_video_activity.js --no-pause
 * 密友发起语音/视频通话 → 看 [CA] 输出
 */

"use strict";

Java.perform(function () {
    var TARGET_WXID = "wxid_lzd2va16jd1622";

    function dumpObj(label, obj, depth) {
        if (obj == null || depth > 3) return;
        var cls = obj.getClass();
        var fields = cls.getDeclaredFields();
        // walk superclass chain
        var c = cls;
        while (c != null && c.getName() !== "java.lang.Object") {
            var fs = c.getDeclaredFields();
            for (var i = 0; i < fs.length; i++) {
                try {
                    fs[i].setAccessible(true);
                    var v = fs[i].get(obj);
                    var mark = (v != null && String(v).indexOf(TARGET_WXID) >= 0) ? " <<<" : "";
                    console.log(label + c.getSimpleName() + "." + fs[i].getName()
                        + "[" + fs[i].getType().getSimpleName() + "]=" + v + mark);
                    // recurse into non-primitive, non-String objects one level
                    if (v != null && depth < 2) {
                        var tn = fs[i].getType().getName();
                        if (!tn.startsWith("java.lang") && !tn.startsWith("java.util")
                                && !tn.startsWith("[") && tn.indexOf("tencent") >= 0) {
                            dumpObj(label + "  ", v, depth + 1);
                        }
                    }
                } catch (e) {
                    console.log(label + fs[i].getName() + "=ERR");
                }
            }
            c = c.getSuperclass();
        }
    }

    var Activity = Java.use("android.app.Activity");
    Activity.onCreate.overload("android.os.Bundle").implementation = function (savedState) {
        this.onCreate(savedState);

        var name = this.getClass().getName().toLowerCase();
        if (!name.includes("video") && !name.includes("voip")
                && !name.includes("call") && !name.includes("dial")) return;

        console.log("\n[CA] === " + this.getClass().getSimpleName() + " ===");

        try {
            var intent = this.getIntent();
            console.log("[CA] action=" + intent.getAction());
            var extras = intent.getExtras();
            if (extras == null) { console.log("[CA] extras=null"); return; }

            var keys = extras.keySet().toArray();
            console.log("[CA] extras.size=" + keys.length);

            for (var i = 0; i < keys.length; i++) {
                var k = keys[i];
                var v = extras.get(k);
                var type = v != null ? v.getClass().getSimpleName() : "null";
                var mark = (v != null && String(v).indexOf(TARGET_WXID) >= 0) ? " <<<" : "";
                console.log("[CA] key=" + k + " type=" + type + " val=" + v + mark);

                // Dump inner fields of Parcelable/object extras
                if (v != null && type !== "String" && type !== "Integer"
                        && type !== "Boolean" && type !== "Long") {
                    dumpObj("[CA]   ", v, 0);
                }
            }
        } catch (e) {
            console.log("[CA] err: " + e);
        }
    };

    console.log("[CA] probe ready — waiting for call Activity");
});
