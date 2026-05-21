/**
 * probe_mvvm_8071.js v4
 * 目标：dump ImproveInfo 全部字段，找 wxid 字段名
 */
"use strict";

function getField(obj, name) {
    var cls = obj.getClass();
    for (var d = 0; cls != null && d < 8; d++) {
        try { var f = cls.getDeclaredField(name); f.setAccessible(true); return f.get(obj); } catch(e) {}
        cls = cls.getSuperclass();
    }
    return null;
}

function dumpAllFields(obj, label, depth) {
    if (obj == null || depth > 2) return;
    var cn = obj.getClass().getName();
    console.log("[" + label + "] class=" + cn);

    var cls = obj.getClass();
    for (var d = 0; cls != null && d < 5; d++) {
        var fs = cls.getDeclaredFields();
        for (var i = 0; i < fs.length; i++) {
            try {
                fs[i].setAccessible(true);
                var v = fs[i].get(obj);
                var tn = fs[i].getType().getName();
                if (v == null) continue;

                var vstr = String(v).substring(0, 100);
                var mark = "";
                // 标记可能是 wxid 的字符串
                if (tn === "java.lang.String") {
                    if (vstr.indexOf("wxid_") >= 0) mark = " ★WXID";
                    else if (vstr.length > 5 && vstr.length < 40) mark = " ?str";
                }
                // 标记 List
                var sz = -1;
                try { sz = v.size(); } catch(e) {}

                if (sz >= 0) {
                    console.log("  ." + fs[i].getName() + " [" + tn + "] size=" + sz);
                } else {
                    console.log("  ." + fs[i].getName() + " [" + tn + "] = " + vstr + mark);
                    // 对非系统子对象递归一层
                    if (depth < 1 && !tn.startsWith("java") && !tn.startsWith("android")
                            && !tn.startsWith("kotlin") && tn.length < 30) {
                        dumpAllFields(v, label + "." + fs[i].getName(), depth + 1);
                    }
                }
            } catch(e2) {}
        }
        cls = cls.getSuperclass();
    }
}

Java.perform(function() {
    console.log("[P4] ready");

    setTimeout(function() {
        Java.perform(function() {
            Java.choose("com.tencent.mm.plugin.mvvmlist.MvvmList", {
                onMatch: function(mv) {
                    var found = false;
                    ["o","p"].forEach(function(fname) {
                        if (found) return;
                        var list = getField(mv, fname);
                        if (list == null) return;
                        // size() 可能被 Kotlin 委托拦截，直接用 get(0) 探活
                        try {
                            var item0 = list.get(0);
                            if (item0 == null) return;
                            found = true;
                            var itemCn = item0.getClass().getName();
                            console.log("[P4] MvvmList." + fname + " get(0) ok, item class=" + itemCn);
                            // 尝试拿 size，失败也无所谓，dump 前 3 个
                            var sz = 3;
                            try { sz = Math.min(list.size(), 3); } catch(e) {}
                            for (var i = 0; i < sz; i++) {
                                var item = list.get(i);
                                if (item == null) continue;
                                dumpAllFields(item, fname + "[" + i + "]", 0);
                                console.log("---");
                            }
                        } catch(e) {
                            console.log("[P4] " + fname + ".get(0) fail: " + e);
                        }
                    });
                    if (!found) console.log("[P4] no populated list in o/p");
                },
                onComplete: function() { console.log("[P4] done"); }
            });
        });
    }, 1500);
});
