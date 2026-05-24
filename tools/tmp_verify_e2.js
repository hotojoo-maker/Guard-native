Java.perform(function () {
    var TARGET = "wxid_lzd2va16jd1622";

    function getFieldDeep(obj, name) {
        var cls = obj.getClass();
        while (cls) {
            try {
                var f = cls.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(obj);
            } catch(e) {}
            cls = cls.getSuperclass();
        }
        return null;
    }

    function dumpFields(obj, depth) {
        if (!obj || depth > 2) return;
        var cls = obj.getClass();
        var out = [];
        while (cls) {
            try {
                var fields = cls.getDeclaredFields();
                for (var fi = 0; fi < fields.length; fi++) {
                    try {
                        var f = fields[fi];
                        f.setAccessible(true);
                        var v = f.get(obj);
                        var name = f.getName();
                        if (name.indexOf("user") >= 0 || name.indexOf("wxid") >= 0 || name.indexOf("UserName") >= 0 || name.indexOf("talker") >= 0 || name.indexOf("username") >= 0) {
                            out.push("  " + name + "=" + v + " (in " + cls.getSimpleName() + ")");
                        }
                    } catch(e) {}
                }
            } catch(e) {}
            cls = cls.getSuperclass();
        }
        return out;
    }

    function dumpItem(item, idx) {
        try {
            var l4 = getFieldDeep(item, "d");
            var wxid = null;
            var display = getFieldDeep(item, "f");
            if (display) display = display.toString();

            if (l4) {
                wxid = getFieldDeep(l4, "field_username");
                if (!wxid) wxid = getFieldDeep(l4, "field_userName");
                if (!wxid) wxid = getFieldDeep(l4, "username");
                if (!wxid) wxid = getFieldDeep(l4, "userName");
                if (!wxid) {
                    // try method
                    try { wxid = l4.getClass().getMethod("C0").invoke(l4); } catch(e) {}
                }
                if (!wxid) {
                    try { wxid = l4.getClass().getMethod("getUsername").invoke(l4); } catch(e) {}
                }
            }

            // If still null, dump all user-related fields
            var dump = (wxid === null || wxid === undefined) ? dumpFields(l4, 1) : [];
            if (dump.length === 0 && l4) dump = dumpFields(l4, 1);

            var hit = (wxid && wxid.toString && wxid.toString() === TARGET);
            var isChatroom = (wxid && wxid.toString() && wxid.toString().indexOf("@chatroom") >= 0);

            console.log("[VFY] [" + idx + "] wxid=" + wxid + " name=" + display + " HIT=" + hit + (isChatroom ? " CHATROOM" : ""));
            if (dump.length > 0) console.log("[VFY]   field dump: " + dump.join(" | "));
            return hit;
        } catch(e) {
            console.log("[VFY] [" + idx + "] err=" + e);
            return false;
        }
    }

    try {
        var MvvmList = Java.use("com.tencent.mm.plugin.mvvmlist.MvvmList");
        MvvmList.e.overloads.forEach(function(ov) {
            if (ov.argumentTypes.length !== 1) return;
            ov.implementation = function(list) {
                if (list && !list.isEmpty()) {
                    var sz = list.size();
                    var max = Math.min(sz, 10);
                    console.log("[VFY] === MvvmList.e(List) sz=" + sz + " ===");
                    var anyHit = false;
                    for (var i = 0; i < max; i++) {
                        var item = list.get(i);
                        if (item) {
                            if (dumpItem(item, i)) anyHit = true;
                        }
                    }
                    if (anyHit) console.log("[VFY] ★★★ HIT! ★★★");
                }
                return ov.call(this, list);
            };
        });
        console.log("[VFY] e(List) hooked, target=" + TARGET);
    } catch(e) {
        console.log("[VFY] FAIL: " + e);
    }

    console.log("[VFY] === verify_e2.js 就绪 ===");
});
