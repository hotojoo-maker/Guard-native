Java.perform(function() {
    var FMF = Java.use("com.tencent.mm.ui.FindMoreFriendsUI");
    FMF.L1.implementation = function() {
        console.log("\n=== [L1] ENTER ===");
        var fields = this.getClass().getDeclaredFields();
        for (var i = 0; i < fields.length; i++) {
            var f = fields[i];
            f.setAccessible(true);
            var fn = f.getName();
            var tn = f.getType().getName();
            try {
                var v = f.get(this);
                if (v === null) continue;
                if (tn === "java.util.List" || tn === "java.util.ArrayList" || tn === "java.util.LinkedList") {
                    var size = v.size();
                    console.log("[L1.List] " + fn + " " + tn + " size=" + size);
                    for (var j = 0; j < Math.min(size, 8); j++) {
                        var item = v.get(j);
                        if (item !== null) {
                            var itemCls = item.getClass().getName();
                            // dump item 的 String 字段（wxid 候选）
                            var itemFields = item.getClass().getDeclaredFields();
                            for (var k = 0; k < itemFields.length; k++) {
                                var ifd = itemFields[k];
                                ifd.setAccessible(true);
                                var itn = ifd.getType().getName();
                                if (itn === "java.lang.String") {
                                    try {
                                        var iv = ifd.get(item);
                                        if (iv !== null && iv.length() > 0 && iv.length() < 120) {
                                            console.log("  [" + j + "]." + ifd.getName() + " = " + iv);
                                        }
                                    } catch(e2) {}
                                }
                            }
                        }
                    }
                } else if (tn === "java.util.Map" || tn === "java.util.HashMap") {
                    console.log("[L1.Map] " + fn + " " + tn + " size=" + v.size());
                } else if (tn === "java.lang.String") {
                    if (v.length() > 0 && v.length() < 120) {
                        console.log("[L1.Str] " + fn + " = " + v);
                    }
                } else if (tn === "int" || tn === "long") {
                    if (fn.toLowerCase().indexOf("unread") >= 0 || fn.toLowerCase().indexOf("count") >= 0 || fn.toLowerCase().indexOf("reddot") >= 0 || v > 0) {
                        console.log("[L1.Int] " + fn + " = " + v);
                    }
                }
            } catch(e2) {}
        }
        return this.L1();
    };
    console.log("[L1] hook ready — cold start, switch to Discover tab");
});
