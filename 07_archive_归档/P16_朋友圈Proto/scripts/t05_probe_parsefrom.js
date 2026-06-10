// T05 Part 2: Hook SnsObject.parseFrom, dump Like/Comment item structure
// Usage: frida -U -p <PID> -l this.js
// Then scroll Moments feed to trigger

function log(msg) { console.log("[T05] " + msg); }

Java.perform(function () {
    log("=== T05 Part 2: Hook parseFrom + dump item structure ===");

    try {
        Java.classFactory.loader = Java.use("android.app.ActivityThread").currentApplication().getClassLoader();
    } catch (e) { log("loader fail: " + e); }

    try {
        var SnsObject = Java.use("com.tencent.mm.protocal.protobuf.SnsObject");
        var overloads = SnsObject.parseFrom.overloads;
        log("parseFrom overloads: " + overloads.length);

        for (var i = 0; i < overloads.length; i++) {
            (function (idx) {
                var orig = overloads[idx];
                overloads[idx].implementation = function () {
                    var result = orig.call(this, arguments[0]);
                    var wxid = null;
                    try {
                        // Try Username field (confirmed in 8.0.66)
                        var uf = result.getClass().getDeclaredField("Username");
                        uf.setAccessible(true);
                        var uVal = uf.get(result);
                        if (uVal != null) wxid = uVal.toString();
                    } catch (e) {}

                    log("========== parseFrom#" + idx + " wxid=" + wxid + " ==========");

                    // Dump LikeUserList items
                    try {
                        var likeField = result.getClass().getDeclaredField("LikeUserList");
                        likeField.setAccessible(true);
                        var likeList = likeField.get(result);
                        if (likeList != null) {
                            var jlist = Java.cast(likeList, Java.use("java.util.List"));
                            log("  LikeUserList size=" + jlist.size());
                            if (jlist.size() > 0) {
                                var item = jlist.get(0);
                                log("  LikeUserList[0] class: " + item.getClass().getName());
                                // Dump all fields
                                var cls = item.getClass();
                                for (var d = 0; cls != null && d < 5; d++) {
                                    var fields = cls.getDeclaredFields();
                                    for (var fi = 0; fi < fields.length; fi++) {
                                        try {
                                            fields[fi].setAccessible(true);
                                            var val = fields[fi].get(item);
                                            log("    " + fields[fi].getName() + " : " + fields[fi].getType().getName() + " = " + (val != null ? String(val) : "null"));
                                        } catch (e) {}
                                    }
                                    cls = cls.getSuperclass();
                                }
                            }
                        }
                    } catch (e) { log("  LikeUserList dump err: " + e); }

                    // Dump CommentUserList item
                    try {
                        var cmtField = result.getClass().getDeclaredField("CommentUserList");
                        cmtField.setAccessible(true);
                        var cmtList = cmtField.get(result);
                        if (cmtList != null) {
                            var jlist2 = Java.cast(cmtList, Java.use("java.util.List"));
                            log("  CommentUserList size=" + jlist2.size());
                            if (jlist2.size() > 0) {
                                var item = jlist2.get(0);
                                log("  CommentUserList[0] class: " + item.getClass().getName());
                                // Dump key fields
                                var cls = item.getClass();
                                for (var d = 0; cls != null && d < 3; d++) {
                                    var fields = cls.getDeclaredFields();
                                    for (var fi = 0; fi < fields.length; fi++) {
                                        try {
                                            fields[fi].setAccessible(true);
                                            var val = fields[fi].get(item);
                                            log("    " + fields[fi].getName() + " : " + fields[fi].getType().getName() + " = " + (val != null ? String(val) : "null"));
                                        } catch (e) {}
                                    }
                                    cls = cls.getSuperclass();
                                }
                            }
                        }
                    } catch (e) { log("  CommentUserList dump err: " + e); }

                    log("  LikeCount=" + getIntField(result, "LikeCount"));
                    log("  CommentCount=" + getIntField(result, "CommentCount"));
                    log("  LikeUserListCount=" + getIntField(result, "LikeUserListCount"));
                    log("  CommentUserListCount=" + getIntField(result, "CommentUserListCount"));
                    log("==========================================");
                    return result;
                };
            })(i);
        }
        log("parseFrom hooked OK — scroll Moments now");
    } catch (e) {
        log("Hook FAILED: " + e);
    }
});

function getIntField(obj, name) {
    try {
        var f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.getInt(obj);
    } catch (e) { return -1; }
}
