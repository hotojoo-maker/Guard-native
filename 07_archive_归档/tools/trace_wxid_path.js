// trace_wxid_path.js — 两次 10s 快扫 diff，找 wxid 在内存中的新增位置
// 用法: frida -U com.tencent.mm -l trace_wxid_path.js
// 第一步：跑脚本 → 贴 BASELINE → 你去点赞 → 回车 → 贴 DELTA

var TARGET = "wxid_lzd2va16jd1622";

var seen1 = {}; // baseline
var seen2 = {}; // after-like

function scanNow(label, bucket) {
    Java.perform(function() {
        // 扫 SNS 存储 + 消息相关类
        var suspects = [
            "com.tencent.mm.plugin.sns.storage.w1",       // SnsCommentStorage 8.0.71
            "com.tencent.mm.plugin.sns.storage.SnsCommentStorage",
            "com.tencent.mm.storage.z3",                   // ContactInfo
            "com.tencent.mm.model.a",                      // 账号
        ];

        suspects.forEach(function(cn) {
            try {
                Java.choose(cn, {
                    onMatch: function(inst) {
                        try {
                            var s = inst.toString();
                            if (s.indexOf(TARGET) !== -1) {
                                var key = cn + "|" + s.substring(0, 80);
                                bucket[key] = true;
                            }
                        } catch(e) {}
                    },
                    onComplete: function() {}
                });
            } catch(e) {}
        });
    });
}

// Baseline
scanNow("BASELINE", seen1);
console.log("[1/3] BASELINE done. Objects with wxid: " + Object.keys(seen1).length);
Object.keys(seen1).forEach(function(k) { console.log("  " + k); });
console.log("[2/3] NOW: 去点赞/评论，等红点出现后按 Enter...");

// stdin 等待
var stdin = new java.io.BufferedReader(new java.io.InputStreamReader(java.lang.System['in']));
setTimeout(function() {
    console.log("[3/3] Scanning DELTA...");
    scanNow("DELTA", seen2);

    var news = [];
    for (var k in seen2) {
        if (!seen1[k]) news.push(k);
    }

    console.log("\n=== DELTA (new after like, " + news.length + " items) ===");
    news.forEach(function(k) { console.log("  [NEW] " + k); });
    console.log("=== DONE ===");
}, 15000); // 15s 给你操作
