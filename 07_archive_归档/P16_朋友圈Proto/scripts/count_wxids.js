// count_wxids.js — 扫描朋友圈当前内存中每个 wxid 有多少帖子
// 用法: frida -U -n com.tencent.mm -l count_wxids.js
// 结果一次性输出，下滑后再跑一次看变化

function getField(obj, name) {
    if (obj == null) return null;
    var cls = obj.getClass();
    for (var d = 0; cls != null && d < 10; d++) {
        try { var f = cls.getDeclaredField(name); f.setAccessible(true); return f.get(obj); } catch (e) {}
        cls = cls.getSuperclass();
    }
    return null;
}

var counts = {};

Java.perform(function () {
    try {
        Java.classFactory.loader = Java.use("android.app.ActivityThread").currentApplication().getClassLoader();
        console.log("[COUNT] classloader set OK");
    } catch (e) {
        console.log("[COUNT] classloader set FAIL: " + e);
    }

    Java.choose("k24.b", {
        onMatch: function (item) {
            try {
                if (item.getClass().getName() !== "k24.b") return;
                var d = getField(item, "d");
                if (d == null) return;
                var u = getField(d, "field_userName");
                if (u == null) return;
                var wxid = String(u);
                if (wxid.startsWith("wxid_") && wxid.length > 10) {
                    counts[wxid] = (counts[wxid] || 0) + 1;
                }
            } catch (e) {}
        },
        onComplete: function () {
            var entries = [];
            for (var k in counts) {
                if (counts.hasOwnProperty(k)) entries.push({ wxid: k, count: counts[k] });
            }
            entries.sort(function (a, b) { return b.count - a.count; });

            var total = 0;
            for (var i = 0; i < entries.length; i++) total += entries[i].count;

            console.log("\n===== 朋友圈当前快照 =====");
            console.log("总帖数: " + total + " | 不同wxid: " + entries.length);
            console.log("==========================\n");
            for (var i = 0; i < entries.length; i++) {
                var bar = "";
                for (var b = 0; b < entries[i].count && b < 20; b++) bar += "█";
                console.log((i + 1) + ". " + entries[i].wxid + " [" + entries[i].count + "帖] " + bar);
            }
            console.log("\n[DONE] 下滑加载更多后重新跑脚本看变化");
        }
    });
});
