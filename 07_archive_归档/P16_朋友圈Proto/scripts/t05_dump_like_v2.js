// T05: Dump Liked/Comment posts - using getDeclaredField (confirmed working)

Java.perform(function () {
    console.log("[T05] ready");

    var SnsObject = Java.use("com.tencent.mm.protocal.protobuf.SnsObject");
    var hitCount = 0;

    SnsObject.parseFrom.overload('[B').implementation = function (bytes) {
        var result = this.parseFrom(bytes);
        if (result == null) return result;

        var userName = null;
        try {
            var uf = result.getClass().getDeclaredField("Username");
            uf.setAccessible(true);
            var uv = uf.get(result);
            if (uv != null) userName = String(uv);
        } catch (e) {}
        if (userName == null || userName.length === 0) return result;

        hitCount++;
        console.log("\n=== SnsObject #" + hitCount + " wxid=" + userName + " ===");

        var fields = result.getClass().getDeclaredFields();
        for (var i = 0; i < fields.length; i++) {
            var f = fields[i];
            try {
                f.setAccessible(true);
                var val = f.get(result);
                if (val == null) continue;
                var typeName = f.getType().getName();
                if (typeName.indexOf("List") >= 0) {
                    var size = 0;
                    try { size = val.size(); } catch (e) {}
                    if (size > 0) {
                        console.log("[LIST] " + f.getName() + " type=" + typeName + " size=" + size);
                        var first = val.get(0);
                        console.log("  item[0] class=" + first.getClass().getName());
                        var iFields = first.getClass().getDeclaredFields();
                        for (var j = 0; j < iFields.length; j++) {
                            try {
                                iFields[j].setAccessible(true);
                                var iv = iFields[j].get(first);
                                console.log("  item[0]." + iFields[j].getName()
                                    + " = " + iv + " : " + iFields[j].getType().getName());
                            } catch (e2) {}
                        }
                    }
                }
            } catch (e3) {}
        }

        // Only 5 hits
        if (hitCount >= 10) {
            console.log("[T05] 10 hits, enough.");
            return result;
        }
        return result;
    };
});
