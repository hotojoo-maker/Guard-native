// User's approach: dump LikeUserList/CommentUserList item fields from parseFrom

Java.perform(function () {
    console.log("[T05] ready — 滑动朋友圈触发");

    var SnsObject = Java.use("com.tencent.mm.protocal.protobuf.SnsObject");
    SnsObject.parseFrom.overload('[B').implementation = function (bytes) {
        var result = this.parseFrom(bytes);
        if (result == null) return result;
        try {
            var userName = result.Username.value;
            if (!userName || userName.length === 0) return result;
            console.log("\n=== SnsObject wxid=" + userName + " ===");
            var fields = result.getClass().getDeclaredFields();
            for (var i = 0; i < fields.length; i++) {
                var f = fields[i];
                f.setAccessible(true);
                var val = f.get(result);
                if (val == null) continue;
                var typeName = f.getType().getName();
                if (typeName.indexOf("List") >= 0 || typeName.indexOf("java.util") >= 0) {
                    var size = 0;
                    try { size = val.size(); } catch (e) {}
                    console.log("[LIST] field=" + f.getName()
                        + " type=" + typeName + " size=" + size);
                    if (size > 0) {
                        try {
                            var first = val.get(0);
                            console.log("  item[0] class=" + first.getClass().getName());
                            var iFields = first.getClass().getDeclaredFields();
                            for (var j = 0; j < iFields.length; j++) {
                                iFields[j].setAccessible(true);
                                var iv = iFields[j].get(first);
                                console.log("  item[0]." + iFields[j].getName()
                                    + "=" + iv);
                            }
                        } catch (e) {}
                    }
                }
            }
        } catch (e) { console.log("err: " + e); }
        return result;
    };
});
