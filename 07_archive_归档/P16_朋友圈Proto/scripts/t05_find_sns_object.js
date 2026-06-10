// T05: Find SnsObject.parseFrom equivalent in WeChat 8.0.66
// Usage: frida -U com.tencent.mm -l this.js

var VERBOSE = true;

function log(msg) { console.log("[T05] " + msg); }

// Recursive field lookup
function getField(obj, name) {
    if (obj == null) return null;
    var cls = obj.getClass();
    for (var d = 0; cls != null && d < 10; d++) {
        try { var f = cls.getDeclaredField(name); f.setAccessible(true); return f.get(obj); } catch (e) {}
        cls = cls.getSuperclass();
    }
    return null;
}

function getAllFields(obj) {
    if (obj == null) return [];
    var fields = [];
    var cls = obj.getClass();
    for (var d = 0; cls != null && d < 6; d++) {
        var declared = cls.getDeclaredFields();
        for (var i = 0; i < declared.length; i++) {
            try {
                declared[i].setAccessible(true);
                fields.push({
                    name: declared[i].getName(),
                    type: declared[i].getType().getName(),
                    value: String(declared[i].get(obj))
                });
            } catch (e) {}
        }
        cls = cls.getSuperclass();
    }
    return fields;
}

Java.perform(function () {
    log("=== T05: Searching for SnsObject.parseFrom in 8.0.66 ===");

    // Set classloader to WeChat
    try {
        Java.classFactory.loader = Java.use("android.app.ActivityThread").currentApplication().getClassLoader();
        log("ClassLoader OK");
    } catch (e) { log("ClassLoader FAIL: " + e); }

    // ━━━ Step 1: Check known protobuf SnsObject ━━━
    log("\n--- Step 1: Check protobuf SnsObject ---");
    try {
        var SnsObject = Java.use("com.tencent.mm.protocal.protobuf.SnsObject");
        log("FOUND: com.tencent.mm.protocal.protobuf.SnsObject");

        // Check parseFrom
        var methods = SnsObject.class.getDeclaredMethods();
        for (var i = 0; i < methods.length; i++) {
            var m = methods[i];
            var name = m.getName();
            var paramTypes = m.getParameterTypes();
            if (name.indexOf("parseFrom") >= 0) {
                var params = [];
                for (var j = 0; j < paramTypes.length; j++) params.push(paramTypes[j].getName());
                log("  parseFrom: " + name + "(" + params.join(", ") + ") → " + m.getReturnType().getName());
            }
        }

        // Check key fields via reflection on an instance
        log("SnsObject declared fields:");
        var allFields = SnsObject.class.getDeclaredFields();
        var interesting = [];
        for (var i = 0; i < allFields.length; i++) {
            var f = allFields[i];
            var fname = f.getName();
            var ftype = f.getType().getName();
            if (fname.indexOf("User") >= 0 || fname.indexOf("Like") >= 0 || fname.indexOf("Comment") >= 0 ||
                fname.indexOf("userName") >= 0 || fname.indexOf("Count") >= 0 || fname.indexOf("List") >= 0 ||
                fname.indexOf("field_") >= 0) {
                interesting.push(fname + " : " + ftype);
            }
        }
        log("  Interesting fields in SnsObject:");
        for (var k = 0; k < interesting.length; k++) log("    " + interesting[k]);

        log("  All fields count: " + allFields.length);
        for (var j = 0; j < allFields.length; j++) {
            log("    [" + j + "] " + allFields[j].getName() + " : " + allFields[j].getType().getName());
        }
    } catch (e) {
        log("protobuf SnsObject NOT FOUND: " + e);
    }

    // ━━━ Step 2: Enumerate classes with parseFrom(byte[]) ━━━
    log("\n--- Step 2: Enumerate parseFrom(byte[]) classes ---");
    var parseFromClasses = [];
    Java.enumerateLoadedClasses({
        onMatch: function (className) {
            if (className.indexOf("parseFrom") >= 0) return;
            if (className.indexOf("protobuf") >= 0 || className.indexOf("proto") >= 0 ||
                className.indexOf("SnsObject") >= 0 || className.indexOf("Sns") >= 0 ||
                className.indexOf("sns") >= 0) {
                try {
                    var cls = Java.use(className);
                    var methods = cls.class.getDeclaredMethods();
                    for (var i = 0; i < methods.length; i++) {
                        var m = methods[i];
                        if (m.getName().indexOf("parseFrom") >= 0) {
                            var ptypes = m.getParameterTypes();
                            if (ptypes.length == 1 && ptypes[0].getName() === "[B") {
                                parseFromClasses.push({
                                    className: className,
                                    method: m.getName(),
                                    retType: m.getReturnType().getName()
                                });
                                log("  parseFrom(byte[]): " + className + "." + m.getName() + " → " + m.getReturnType().getName());
                            }
                        }
                    }
                } catch (e) {}
            }
        },
        onComplete: function () {
            log("ParseFrom scan complete. Found " + parseFromClasses.length + " candidate(s).");
        }
    });

    // ━━━ Step 3: Hook protobuf SnsObject.parseFrom if found ━━━
    log("\n--- Step 3: Hook SnsObject.parseFrom ---");
    try {
        var SnsObject = Java.use("com.tencent.mm.protocal.protobuf.SnsObject");

        // Try multiple parseFrom overloads
        var overloads = SnsObject.parseFrom.overloads;
        log("  parseFrom overloads: " + overloads.length);

        for (var i = 0; i < overloads.length; i++) {
            (function (idx) {
                overloads[idx].implementation = function () {
                    var result = overloads[idx].call(this, arguments[0]);
                    log("========== SnsObject.parseFrom#" + idx + " CALLED ==========");

                    // Dump fields of the result
                    if (result != null) {
                        var fields = getAllFields(result);
                        var wxid = getField(result, "field_userName");
                        if (wxid != null) {
                            log("  field_userName = " + wxid);
                        }

                        // Check for user list fields
                        var likeList = getField(result, "LikeUserList");
                        var commentList = getField(result, "CommentUserList");
                        var likeCount = getField(result, "LikeCount");
                        var commentCount = getField(result, "CommentCount");
                        var likeCount2 = getField(result, "LikeUserListCount");
                        var commentCount2 = getField(result, "CommentUserListCount");

                        if (likeList != null) {
                            var jlist = Java.cast(likeList, Java.use("java.util.List"));
                            log("  LikeUserList size=" + jlist.size());
                            if (jlist.size() > 0) {
                                var firstItem = jlist.get(0);
                                log("  LikeUserList[0] class=" + (firstItem != null ? firstItem.getClass().getName() : "null"));
                                if (firstItem != null) {
                                    var ifields = getAllFields(firstItem);
                                    for (var fi = 0; fi < ifields.length; fi++) {
                                        log("    LikeItem field: " + ifields[fi].name + " : " + ifields[fi].type + " = " + ifields[fi].value);
                                    }
                                }
                            }
                        }
                        if (commentList != null) {
                            var jlist2 = Java.cast(commentList, Java.use("java.util.List"));
                            log("  CommentUserList size=" + jlist2.size());
                        }
                        log("  LikeCount=" + likeCount + " CommentCount=" + commentCount);
                        log("  LikeUserListCount=" + likeCount2 + " CommentUserListCount=" + commentCount2);
                    }
                    log("==========================================");
                    return result;
                };
            })(i);
        }
        log("  SnsObject.parseFrom hooked OK");
    } catch (e) {
        log("Hook SnsObject.parseFrom FAILED: " + e);
    }

    // ━━━ Step 4: Also try to find by enumerating parseFrom on loaded classes ━━━
    log("\n--- Step 4: Broad search for parseFrom in loaded classes ---");
    setTimeout(function () {
        Java.perform(function () {
            var found = 0;
            Java.enumerateLoadedClasses({
                onMatch: function (className) {
                    if (found >= 30) return "stop";
                    try {
                        var cls = Java.use(className);
                        var methods = cls.class.getDeclaredMethods();
                        for (var i = 0; i < methods.length; i++) {
                            var m = methods[i];
                            if (m.getName().indexOf("parseFrom") >= 0) {
                                var ptypes = m.getParameterTypes();
                                if (ptypes.length == 1 && ptypes[0].getName() === "[B") {
                                    log("  " + className + "." + m.getName() + "(byte[]) → " + m.getReturnType().getName());
                                    found++;
                                }
                            }
                        }
                    } catch (e) {}
                },
                onComplete: function () {
                    log("Broad search complete. Found " + found + " parseFrom(byte[]) methods.");
                }
            });
        });
    }, 2000);

    log("\n=== T05 ready — scroll Moments feed to trigger ===");
});
