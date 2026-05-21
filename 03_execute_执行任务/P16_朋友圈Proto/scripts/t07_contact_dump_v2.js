// ============================================================
// T07 v2: Deep inspect m3 + try b7/c7/k3.get(wxid) + dump a3
// Target: WeChat 8.0.66 address book Contact fields
// ============================================================

var TEST_WXID = "wxid_hmu4qj85aaa812";

function log(msg) { console.log("[T07v2] " + msg); }

function dumpAllFieldsDeep(obj, label, maxDepth) {
    if (obj == null) { log(label + ": null"); return; }
    maxDepth = maxDepth || 6;
    log("=== " + label + " [class=" + obj.getClass().getName() + "] ===");
    var cls = obj.getClass();
    for (var d = 0; cls != null && d < maxDepth; d++) {
        var fields = cls.getDeclaredFields();
        for (var i = 0; i < fields.length; i++) {
            try {
                fields[i].setAccessible(true);
                var val = fields[i].get(obj);
                var valStr = (val == null) ? "null" : String(val);
                if (valStr.length > 150) valStr = valStr.substring(0, 150) + "...";
                log("  [d" + d + "][" + fields[i].getName() + "] " + fields[i].getType().getName() + " = " + valStr);
            } catch (e) {
                log("  [d" + d + "][" + fields[i].getName() + "] " + fields[i].getType().getName() + " = <err>");
            }
        }
        cls = cls.getSuperclass();
    }
}

function dumpAllMethods(obj, label) {
    if (obj == null) return;
    log("--- " + (label || "methods") + " [class=" + obj.getClass().getName() + "] ---");
    var methods = obj.getClass().getDeclaredMethods();
    for (var i = 0; i < methods.length; i++) {
        var m = methods[i];
        var params = [];
        var ptypes = m.getParameterTypes();
        for (var j = 0; j < ptypes.length; j++) params.push(ptypes[j].getName());
        log("  " + m.getReturnType().getName() + " " + m.getName() + "(" + params.join(", ") + ")");
    }
}

function showSuperChain(cls, label) {
    log("--- " + (label || "super chain") + " ---");
    var c = cls;
    for (var d = 0; c != null && d < 10; d++) {
        log("  [" + d + "] " + c.getName());
        c = c.getSuperclass();
    }
}

Java.perform(function () {
    log("=== T07 v2: Deep contact inspection for 8.0.66 ===");
    try {
        Java.classFactory.loader = Java.use("android.app.ActivityThread").currentApplication().getClassLoader();
        log("ClassLoader OK");
    } catch (e) { log("ClassLoader FAIL: " + e); }

    // ================================================================
    // 1: Dump ALL m3 methods + full super chain
    // ================================================================
    log("\n━━━ 1: m3 full method dump + super chain ━━━");
    try {
        var M3 = Java.use("com.tencent.mm.storage.m3");
        dumpAllMethods(M3.class, "m3 ALL methods");
        showSuperChain(M3.class, "m3 super chain");
    } catch (e) { log("m3: " + e); }

    // ================================================================
    // 2: Dump superclass f8 fields (m3's Z1 type)
    // ================================================================
    log("\n━━━ 2: Inspect com.tencent.mm.storage.f8 (m3 super) ━━━");
    try {
        var F8 = Java.use("com.tencent.mm.storage.f8");
        showSuperChain(F8.class, "f8 super chain");
        dumpAllMethods(F8.class, "f8 ALL methods");
        // dump fields statically
        log("--- f8 declared fields ---");
        var f8fields = F8.class.getDeclaredFields();
        for (var i = 0; i < f8fields.length; i++) {
            log("  " + f8fields[i].getName() + " : " + f8fields[i].getType().getName());
        }
    } catch (e) { log("f8: " + e); }

    // ================================================================
    // 3: Try b7.get(wxid) → a3
    // ================================================================
    log("\n━━━ 3: Try b7.get(wxid) → a3 ━━━");
    try {
        var B7 = Java.use("com.tencent.mm.storage.b7");
        log("FOUND b7");
        dumpAllMethods(B7.class, "b7 ALL methods");
        // Try get
        try {
            var result = B7.get(TEST_WXID);
            log("b7.get('" + TEST_WXID + "') = " + result);
            if (result != null) {
                dumpAllFieldsDeep(result, "b7.get result (a3)");
                dumpAllMethods(result, "a3 methods");
                showSuperChain(result.getClass(), "a3 super chain");
            }
        } catch (e) { log("b7.get: " + e); }

        // Try get with other overloads
        var b7methods = B7.class.getDeclaredMethods();
        for (var i = 0; i < b7methods.length; i++) {
            var m = b7methods[i];
            var ptypes = m.getParameterTypes();
            if (ptypes.length === 1 && ptypes[0].getName() === "java.lang.String") {
                var mname = m.getName();
                if (mname !== "get") {
                    try {
                        var r2 = m.invoke(null, TEST_WXID);
                        log("b7." + mname + "('" + TEST_WXID + "') = " + r2);
                    } catch (e) {}
                }
            }
        }
    } catch (e) { log("b7: " + e); }

    // ================================================================
    // 4: Try c7.get(wxid) → a3
    // ================================================================
    log("\n━━━ 4: Try c7.get(wxid) → a3 ━━━");
    try {
        var C7 = Java.use("com.tencent.mm.storage.c7");
        log("FOUND c7");
        try {
            var result = C7.get(TEST_WXID);
            log("c7.get('" + TEST_WXID + "') = " + result);
            if (result != null) {
                dumpAllFieldsDeep(result, "c7.get result (a3)");
            }
        } catch (e) { log("c7.get: " + e); }
    } catch (e) { log("c7: " + e); }

    // ================================================================
    // 5: Try k3.get(wxid) → a3
    // ================================================================
    log("\n━━━ 5: Try k3.get(wxid) → a3 ━━━");
    try {
        var K3 = Java.use("com.tencent.mm.storage.k3");
        log("FOUND k3");
        try {
            var result = K3.get(TEST_WXID);
            log("k3.get('" + TEST_WXID + "') = " + result);
            if (result != null) {
                dumpAllFieldsDeep(result, "k3.get result (a3)");
            }
        } catch (e) { log("k3.get: " + e); }
    } catch (e) { log("k3: " + e); }

    // ================================================================
    // 6: Inspect a3 class directly
    // ================================================================
    log("\n━━━ 6: Inspect com.tencent.mm.storage.a3 ━━━");
    try {
        var A3 = Java.use("com.tencent.mm.storage.a3");
        log("FOUND a3");
        showSuperChain(A3.class, "a3 super chain");
        dumpAllMethods(A3.class, "a3 ALL methods");
        log("--- a3 declared fields ---");
        var a3fields = A3.class.getDeclaredFields();
        for (var i = 0; i < a3fields.length; i++) {
            log("  " + a3fields[i].getName() + " : " + a3fields[i].getType().getName());
        }
    } catch (e) { log("a3: " + e); }

    // ================================================================
    // 7: Hook m3.E1(String) — catch contact being queried
    // ================================================================
    log("\n━━━ 7: Hook m3.E1(String) — monitor contact lookups ━━━");
    try {
        var M3 = Java.use("com.tencent.mm.storage.m3");
        if (M3.E1 && M3.E1.overloads && M3.E1.overloads.length > 0) {
            M3.E1.overload('java.lang.String').implementation = function (arg) {
                var result = this.E1(arg);
                log("★ m3.E1 called: arg='" + arg + "' this.class=" + this.getClass().getName());
                if (arg === TEST_WXID || (arg && arg.indexOf("wxid_hm") >= 0)) {
                    dumpAllFieldsDeep(this, "m3 instance (E1 hit)");
                }
                return result;
            };
            log("m3.E1 hooked");
        }
    } catch (e) { log("m3.E1 hook: " + e); }

    // ================================================================
    // 8: Scan for "ContactStorage" in phone contact plugin
    // ================================================================
    log("\n━━━ 8: Check com.tencent.mm.contact.* ━━━");
    try {
        var ContactD = Java.use("com.tencent.mm.contact.d");
        log("FOUND com.tencent.mm.contact.d");
        dumpAllMethods(ContactD.class, "contact.d methods");
    } catch (e) { log("contact.d: " + e); }

    try {
        var ContactI = Java.use("com.tencent.mm.contact.i");
        log("FOUND com.tencent.mm.contact.i");
        dumpAllMethods(ContactI.class, "contact.i methods");
    } catch (e) {}

    try {
        var ContactJ = Java.use("com.tencent.mm.contact.j");
        log("FOUND com.tencent.mm.contact.j");
        dumpAllMethods(ContactJ.class, "contact.j methods");
    } catch (e) {}

    log("\n=== T07 v2 complete ===");
    log("NEXT STEP: Open WeChat Contacts tab, then re-run this script.");
    log("  The b7/c7/k3.get(wxid) may need address book data loaded first.");
});
