/**
 * T10b: 轻量扫 wxid_ — 只扫已知类，不扫 ArrayList
 * 目标: kc5.y (会话item), l4 (联系人), f45.u (旧版item)
 *
 * 用法: frida -U -p <PID> -l t10b_scan_wxid.js
 */

var targets = [
    "kc5.y",                          // 8.0.71 会话 item
    "com.tencent.mm.storage.l4",      // 8.0.71 联系人
    "com.tencent.mm.storage.m3",      // 8.0.66 联系人
    "com.tencent.mm.storage.e4",      // 8.0.70 联系人
];

function extractWxid(item) {
    try {
        var cls = item.getClass();
        // 试 getter
        ["h1","j1","i1","v","getUsername","getUserName"].forEach(function(m) {
            try {
                var r = cls.getMethod(m).invoke(item);
                if (r && typeof r === 'string' && r.startsWith("wxid_")) {
                    throw "FOUND:" + r; // 用异常跳出 forEach
                }
            } catch(e) {
                if (typeof e === 'string' && e.startsWith("FOUND:"))
                    throw e;
            }
        });
    } catch(e) {
        if (typeof e === 'string' && e.startsWith("FOUND:"))
            return e.substring(6);
    }
    return null;
}

Java.perform(function() {
    console.log("[T10b] === Targeted scan for wxid_ ===\n");

    targets.forEach(function(className) {
        try {
            var count = 0;
            var wxids = [];
            Java.choose(className, {
                onMatch: function(obj) {
                    count++;
                    if (count <= 30) {
                        var wxid = extractWxid(obj);
                        if (wxid && wxids.indexOf(wxid) === -1) {
                            wxids.push(wxid);
                        }
                    }
                },
                onComplete: function() {
                    if (count > 0) {
                        console.log("[T10b] " + className + ": instances=" + count +
                                    (wxids.length > 0 ? " wxids=" + JSON.stringify(wxids.slice(0, 10)) : ""));
                    } else {
                        console.log("[T10b] " + className + ": NOT FOUND");
                    }
                }
            });
        } catch(e) {
            console.log("[T10b] " + className + ": ERROR " + e.message);
        }
    });
});
