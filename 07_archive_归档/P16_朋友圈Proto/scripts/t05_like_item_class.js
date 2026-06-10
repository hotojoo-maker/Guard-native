// T05 Part 3: Find LikeUserList/CommentUserList item class in 8.0.66
// Approach: directly inspect SnsObject class for LikeUserList field type

Java.perform(function () {
    console.log("[T05] === Part 3: Inspect LikeUserList item type ===");

    try {
        Java.classFactory.loader = Java.use("android.app.ActivityThread").currentApplication().getClassLoader();
    } catch (e) {}

    var SnsObject = Java.use("com.tencent.mm.protocal.protobuf.SnsObject");

    // Get LikeUserList field type info
    try {
        var likeField = SnsObject.class.getDeclaredField("LikeUserList");
        var genericType = likeField.getGenericType();
        console.log("[T05] LikeUserList genericType: " + genericType);
        console.log("[T05] LikeUserList typeName: " + likeField.getGenericType().getTypeName());
    } catch (e) {
        console.log("[T05] LikeUserList type err: " + e);
    }

    // Try to create an instance and check field types
    try {
        // Use newInstance to create SnsObject
        var builder = SnsObject.$new();
        console.log("[T05] Created SnsObject instance: " + builder.getClass().getName());

        // Get LikeUserList field
        var likeField = SnsObject.class.getDeclaredField("LikeUserList");
        likeField.setAccessible(true);
        var ptype = likeField.getType();
        console.log("[T05] LikeUserList raw type: " + ptype.getName());

        // Check CommentUserList
        var cmtField = SnsObject.class.getDeclaredField("CommentUserList");
        cmtField.setAccessible(true);
        console.log("[T05] CommentUserList raw type: " + cmtField.getType().getName());

        // Try to get the actual list and inspect items
        // The list will be null on new instance, need real data
        // Instead, use getDeclaredField to find what's in the list
        console.log("[T05] SnsObject Username field: " + SnsObject.class.getDeclaredField("Username").getType().getName());

    } catch (e) {
        console.log("[T05] Instance inspect err: " + e);
    }

    // Now hook parseFrom but ONLY process SnsObject instances
    var overloads = SnsObject.parseFrom.overloads;
    console.log("[T05] parseFrom overloads: " + overloads.length);

    var snsObjClass = SnsObject.class;
    var hookCount = 0;
    var snsObjCount = 0;

    for (var i = 0; i < overloads.length; i++) {
        (function (idx) {
            var orig = overloads[idx];
            overloads[idx].implementation = function () {
                hookCount++;
                var result = orig.call(this, arguments[0]);
                if (result == null) return result;

                // Check if result is actually SnsObject
                var resultClass = result.getClass();
                var isSns = false;
                try {
                    // Try casting to SnsObject
                    Java.cast(result, SnsObject);
                    isSns = true;
                } catch (e) {
                    return result;
                }

                snsObjCount++;
                if (snsObjCount <= 3 || snsObjCount % 50 === 0) {
                    console.log("[T05] ====== SnsObject #" + snsObjCount + " (hook#" + hookCount + ") ======");

                    try {
                        var uf = resultClass.getDeclaredField("Username");
                        uf.setAccessible(true);
                        console.log("[T05]   Username = " + uf.get(result));
                    } catch (e) {}

                    try {
                        var lf = resultClass.getDeclaredField("LikeUserList");
                        lf.setAccessible(true);
                        var likeList = lf.get(result);
                        if (likeList != null) {
                            var jl = Java.cast(likeList, Java.use("java.util.List"));
                            console.log("[T05]   LikeUserList size=" + jl.size());
                            if (jl.size() > 0) {
                                var item = jl.get(0);
                                console.log("[T05]   LikeItem CLASS: " + item.getClass().getName());
                                var cls = item.getClass();
                                for (var d = 0; cls != null && d < 4; d++) {
                                    var fields = cls.getDeclaredFields();
                                    for (var fi = 0; fi < fields.length; fi++) {
                                        try {
                                            fields[fi].setAccessible(true);
                                            var v = fields[fi].get(item);
                                            if (v != null) {
                                                console.log("[T05]     field: " + fields[fi].getName() + " : " + fields[fi].getType().getName() + " = " + String(v).substring(0, Math.min(100, String(v).length)));
                                            }
                                        } catch (e2) {}
                                    }
                                    cls = cls.getSuperclass();
                                }
                            }
                        }
                    } catch (e) {
                        console.log("[T05]   LikeUserList err: " + e);
                    }
                }
                return result;
            };
        })(i);
    }

    console.log("[T05] parseFrom hooked with instanceof filter. Scroll Moments NOW.");
});
