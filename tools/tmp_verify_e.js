Java.perform(function () {
    var TARGET = "wxid_lzd2va16jd1622";  // 已知密友 wxid

    function getField(obj, name) {
        try {
            var f = obj.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(obj);
        } catch(e) { return null; }
    }

    function dumpItem(item, idx) {
        try {
            // item = kc5.y
            // .d → com.tencent.mm.storage.l4 → field_username
            var l4 = getField(item, "d");
            var wxid = null;
            var display = null;
            var isChatroom = false;

            if (l4) {
                wxid = getField(l4, "field_username");
                if (!wxid) {
                    // try C0() method
                    try { wxid = l4.C0(); } catch(e) {}
                }
            }

            // display name from .f
            var f = getField(item, "f");
            if (f) display = f.toString();

            // check if it's a chatroom (wxid ends with @chatroom or starts with a number followed by @)
            if (wxid && wxid.toString().indexOf("@chatroom") >= 0) isChatroom = true;

            var hit = (wxid && wxid.toString() === TARGET);

            var parts = [];
            parts.push("[" + idx + "]");
            parts.push("wxid=" + (wxid || "null"));
            parts.push("name=" + (display || "null"));
            parts.push("HIT=" + hit);
            if (isChatroom) parts.push("CHATROOM");

            console.log("[VFY] " + parts.join(" "));
            return hit;
        } catch(e) {
            console.log("[VFY] [" + idx + "] err=" + e);
            return false;
        }
    }

    try {
        var MvvmList = Java.use("com.tencent.mm.plugin.mvvmlist.MvvmList");
        var methods = MvvmList.class.getDeclaredMethods();
        var hooked = 0;
        for (var mi = 0; mi < methods.length; mi++) {
            var m = methods[mi];
            var mn = m.getName();
            var pt = m.getParameterTypes();
            // hook e(List) — single List param
            if (mn === "e" && pt.length === 1 &&
                (pt[0].getName().indexOf("List") >= 0 || pt[0].getName().indexOf("Collection") >= 0)) {
                (function() {
                    try {
                        MvvmList.e.overloads.forEach(function(ov) {
                            ov.implementation = function(list) {
                                if (list && !list.isEmpty()) {
                                    var sz = list.size();
                                    var max = Math.min(sz, 10);
                                    console.log("[VFY] === MvvmList.e(List) sz=" + sz + " this=" + this.$className + " ===");
                                    var anyHit = false;
                                    for (var i = 0; i < max; i++) {
                                        var item = list.get(i);
                                        if (item) {
                                            if (dumpItem(item, i)) anyHit = true;
                                        }
                                    }
                                    if (anyHit) console.log("[VFY] ★★★ HIT! Target wxid found in e() ★★★");
                                }
                                return ov.call(this, list);
                            };
                            hooked++;
                        });
                    } catch(e) { console.log("[VFY] e hook err: " + e); }
                })();
            }
        }
        console.log("[VFY] e(List) hooks: " + hooked);
    } catch(e) {
        console.log("[VFY] MvvmList FAIL: " + e);
    }

    console.log("[VFY] === verify_e.js 就绪 ===");
    console.log("[VFY] 请发一条消息给密友，观察是否有 HIT=true");
});
