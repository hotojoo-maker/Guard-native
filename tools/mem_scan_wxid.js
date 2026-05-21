// mem_scan_wxid.js — 扫进程中指定 wxid 的 Java 对象归属
// 用法: frida -U com.tencent.mm -l mem_scan_wxid.js
// 跑两遍: 一遍无红点, 一遍有红点 → diff

var TARGET = "wxid_lzd2va16jd1622";
var MAX = 30;

Java.perform(function() {
    var seen = {};
    var hits = 0;

    function log(label, info) {
        var key = label + "|" + info.substring(0, 100);
        if (seen[key]) return;
        seen[key] = true;
        hits++;
        if (hits > MAX) return;
        console.log("[" + hits + " " + label + "] " + info.substring(0, 180));
    }

    // 扫特定 holder 类
    var suspects = [
        "com.tencent.mm.model.a",
        "com.tencent.mm.storage.z3",
    ];

    // 通过 ClassLoader 枚举所有已加载的 SNS 相关类
    Java.enumerateLoadedClasses({
        onMatch: function(name) {
            if (hits > MAX) return;
            var l = name.toLowerCase();
            // 只扫 SNS 相关 + 存储类
            if (l.indexOf("sns") === -1 && l.indexOf("contact") === -1
                && l.indexOf("storage") === -1 && l.indexOf("model.") === -1) return;
            if (l.indexOf("ui") !== -1) return; // 跳过 UI 类

            try {
                Java.choose(name, {
                    onMatch: function(inst) {
                        if (hits > MAX) return;
                        try {
                            var s = inst.toString();
                            if (s.indexOf(TARGET) !== -1) {
                                log(name, s);
                            }
                        } catch(e) {}
                    },
                    onComplete: function() {}
                });
            } catch(e) {}
        },
        onComplete: function() {
            console.log("[DONE] total=" + hits + " (max=" + MAX + ")");
        }
    });
});
