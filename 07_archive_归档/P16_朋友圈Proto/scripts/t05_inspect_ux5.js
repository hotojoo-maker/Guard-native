// T05 Part 4: Inspect gu4.ux5 (LikeUserList item) fields directly

Java.perform(function () {
    console.log("[T05] === Part 4: Inspect gu4.ux5 fields ===");

    try {
        Java.classFactory.loader = Java.use("android.app.ActivityThread").currentApplication().getClassLoader();
    } catch (e) {}

    try {
        var UX5 = Java.use("gu4.ux5");
        console.log("[T05] FOUND gu4.ux5");

        // Dump all declared fields
        var fields = UX5.class.getDeclaredFields();
        console.log("[T05] gu4.ux5 has " + fields.length + " fields:");
        for (var i = 0; i < fields.length; i++) {
            var f = fields[i];
            var fname = f.getName();
            var ftype = f.getType().getName();
            // Highlight likely username fields
            var marker = "";
            if (fname.length <= 2) marker = " *** SHORT NAME (likely wxid)";
            if (ftype.indexOf("String") >= 0) marker = " <<< String";
            console.log("  [" + i + "] " + fname + " : " + ftype + marker);
        }

        // Also check methods
        var methods = UX5.class.getDeclaredMethods();
        console.log("[T05] gu4.ux5 has " + methods.length + " methods:");
        for (var j = 0; j < methods.length; j++) {
            var m = methods[j];
            var mname = m.getName();
            var ret = m.getReturnType().getName();
            var params = [];
            var ptypes = m.getParameterTypes();
            for (var k = 0; k < ptypes.length; k++) params.push(ptypes[k].getName());
            if (mname.length() <= 3 || mname.toLowerCase().indexOf("user") >= 0 ||
                mname.toLowerCase().indexOf("name") >= 0 || mname.toLowerCase().indexOf("wx") >= 0 ||
                mname.toLowerCase().indexOf("get") >= 0) {
                console.log("  " + mname + "(" + params.join(",") + ") → " + ret);
            }
        }
    } catch (e) {
        console.log("[T05] gu4.ux5 NOT FOUND: " + e);
    }

    // Also inspect CommentUserList item type
    try {
        var SnsObject = Java.use("com.tencent.mm.protocal.protobuf.SnsObject");
        var cmtField = SnsObject.class.getDeclaredField("CommentUserList");
        var cmtType = cmtField.getGenericType();
        console.log("[T05] CommentUserList type: " + cmtType.getTypeName());
    } catch (e) {
        console.log("[T05] CommentUserList inspect err: " + e);
    }
});
