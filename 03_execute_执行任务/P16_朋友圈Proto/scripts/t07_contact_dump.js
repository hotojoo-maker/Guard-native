// ============================================================
// T07: ContactStorage field enumeration for WeChat 8.0.66
// Target: Find Contact class fields for 4-dimension search
//   wxid / alias(微信号) / nickname / remark
// Test wxid: wxid_hmu4qj85aaa812
// ============================================================

var TEST_WXID = "wxid_hmu4qj85aaa812";
var TEST_ALIAS = "yjmhyh200811";
var TEST_NICK = "祀毅矢佴";

function log(msg) { console.log("[T07] " + msg); }

// ---- Generic helpers ----

function getField(obj, name) {
    if (obj == null) return null;
    var cls = obj.getClass();
    for (var d = 0; cls != null && d < 10; d++) {
        try { var f = cls.getDeclaredField(name); f.setAccessible(true); return f.get(obj); } catch (e) {}
        cls = cls.getSuperclass();
    }
    return null;
}

function dumpAllFields(obj, label) {
    if (obj == null) { log(label + ": null"); return; }
    log("=== " + label + " ===");
    log("  class: " + obj.getClass().getName());
    var cls = obj.getClass();
    var depth = 0;
    while (cls != null && depth < 8) {
        var fields = cls.getDeclaredFields();
        for (var i = 0; i < fields.length; i++) {
            try {
                fields[i].setAccessible(true);
                var val = fields[i].get(obj);
                var valStr = (val == null) ? "null" : String(val);
                if (valStr.length > 120) valStr = valStr.substring(0, 120) + "...";
                log("  [" + fields[i].getName() + "] " + fields[i].getType().getName() + " = " + valStr);
            } catch (e) {
                log("  [" + fields[i].getName() + "] " + fields[i].getType().getName() + " = <err:" + e + ">");
            }
        }
        cls = cls.getSuperclass();
        depth++;
    }
}

function dumpMethods(obj, filter, label) {
    if (obj == null) return;
    log("--- " + (label || "methods") + " ---");
    var cls = (typeof obj === "function") ? obj : obj.getClass();
    if (typeof cls !== "function") cls = obj.getClass();
    var methods = cls.getDeclaredMethods();
    for (var i = 0; i < methods.length; i++) {
        var m = methods[i];
        var name = m.getName();
        if (!filter || name.toLowerCase().indexOf(filter.toLowerCase()) >= 0) {
            var params = [];
            var ptypes = m.getParameterTypes();
            for (var j = 0; j < ptypes.length; j++) params.push(ptypes[j].getName());
            log("  " + m.getReturnType().getName() + " " + name + "(" + params.join(", ") + ")");
        }
    }
}

// ---- Main ----

Java.perform(function () {
    log("=== T07 ContactStorage dump for 8.0.66 ===");
    log("Test: wxid=" + TEST_WXID + " alias=" + TEST_ALIAS + " nick=" + TEST_NICK);

    // Set classloader
    try {
        Java.classFactory.loader = Java.use("android.app.ActivityThread").currentApplication().getClassLoader();
        log("ClassLoader OK");
    } catch (e) { log("ClassLoader FAIL: " + e); }

    // ================================================================
    // PHASE 1: Enumerate all contact-related classes
    // ================================================================
    log("\n━━━ PHASE 1: Enumerate contact classes ━━━");
    var contactClasses = [];
    var storageClasses = [];
    var kernelClasses = [];

    Java.enumerateLoadedClasses({
        onMatch: function (className) {
            var lower = className.toLowerCase();
            if (lower.indexOf("contact") >= 0) {
                contactClasses.push(className);
            }
            if (lower.indexOf("storage") >= 0 && lower.indexOf("com.tencent") >= 0) {
                storageClasses.push(className);
            }
            if (lower.indexOf("kernel") >= 0 && lower.indexOf("com.tencent") >= 0) {
                kernelClasses.push(className);
            }
        },
        onComplete: function () {
            log("Contact classes: " + contactClasses.length);
            for (var i = 0; i < contactClasses.length; i++) {
                log("  " + contactClasses[i]);
            }
            log("\nStorage classes (com.tencent): " + storageClasses.length);
            for (var j = 0; j < storageClasses.length; j++) {
                log("  " + storageClasses[j]);
            }
            log("\nKernel classes (com.tencent): " + kernelClasses.length);
            for (var k = 0; k < kernelClasses.length; k++) {
                log("  " + kernelClasses[k]);
            }
        }
    });

    // ================================================================
    // PHASE 2: Inspect known contact class com.tencent.mm.storage.m3
    // ================================================================
    log("\n━━━ PHASE 2: Inspect com.tencent.mm.storage.m3 ━━━");
    try {
        var M3 = Java.use("com.tencent.mm.storage.m3");
        log("FOUND: com.tencent.mm.storage.m3");

        // Dump ALL methods (not just getters)
        var allMethods = M3.class.getDeclaredMethods();
        log("Total methods: " + allMethods.length);
        log("--- Methods returning String ---");
        for (var i = 0; i < allMethods.length; i++) {
            var m = allMethods[i];
            if (m.getReturnType().getName() === "java.lang.String") {
                var ptypes = m.getParameterTypes();
                var params = [];
                for (var j = 0; j < ptypes.length; j++) params.push(ptypes[j].getName());
                log("  String " + m.getName() + "(" + params.join(", ") + ")");
            }
        }

        log("--- Methods with 'get/field/query/find' in name ---");
        for (var i = 0; i < allMethods.length; i++) {
            var name = allMethods[i].getName();
            var lower = name.toLowerCase();
            if (lower.indexOf("get") >= 0 || lower.indexOf("field") >= 0 ||
                lower.indexOf("query") >= 0 || lower.indexOf("find") >= 0 ||
                lower.indexOf("user") >= 0 || lower.indexOf("name") >= 0 ||
                lower.indexOf("nick") >= 0 || lower.indexOf("alias") >= 0 ||
                lower.indexOf("remark") >= 0 || lower.indexOf("wx") >= 0) {
                var ret = allMethods[i].getReturnType().getName();
                var ptypes = allMethods[i].getParameterTypes();
                var params = [];
                for (var j = 0; j < ptypes.length; j++) params.push(ptypes[j].getName());
                log("  " + ret + " " + name + "(" + params.join(", ") + ")");
            }
        }

        // Dump ALL fields
        log("--- All declared fields ---");
        var allFields = M3.class.getDeclaredFields();
        for (var i = 0; i < allFields.length; i++) {
            log("  " + allFields[i].getName() + " : " + allFields[i].getType().getName());
        }
    } catch (e) {
        log("m3 NOT FOUND via Java.use: " + e);
    }

    // ================================================================
    // PHASE 3: Try kernel paths to get ContactStorage
    // ================================================================
    log("\n━━━ PHASE 3: Kernel → ContactStorage paths ━━━");

    // 3a: Try com.tencent.mm.kernel.h (Catfish 8.0.70)
    try {
        var KernelH = Java.use("com.tencent.mm.kernel.h");
        log("FOUND: com.tencent.mm.kernel.h");
        dumpMethods(KernelH.class, "getStorage", "getStorage methods");
    } catch (e) { log("com.tencent.mm.kernel.h NOT FOUND"); }

    // 3b: Try other kernel classes found in phase 1
    for (var ki = 0; ki < kernelClasses.length; ki++) {
        try {
            var kcls = Java.use(kernelClasses[ki]);
            var methods = kcls.class.getDeclaredMethods();
            for (var mi = 0; mi < methods.length; mi++) {
                var mname = methods[mi].getName();
                if (mname.toLowerCase().indexOf("getstorage") >= 0 ||
                    mname.toLowerCase().indexOf("getcontact") >= 0 ||
                    mname.toLowerCase().indexOf("contact") >= 0) {
                    var params = [];
                    var ptypes = methods[mi].getParameterTypes();
                    for (var j = 0; j < ptypes.length; j++) params.push(ptypes[j].getName());
                    log("  " + kernelClasses[ki] + "." + mname + "(" + params.join(",") + ")");
                }
            }
        } catch (e) {}
    }

    // 3c: Try static fields / singleton patterns on storage classes
    log("\n--- Storage class static inspection ---");
    for (var si = 0; si < storageClasses.length; si++) {
        try {
            var scls = Java.use(storageClasses[si]);
            // Check for getInstance / singleton methods
            var smethods = scls.class.getDeclaredMethods();
            for (var smi = 0; smi < smethods.length; smi++) {
                var smname = smethods[smi].getName();
                if (smname === "getInstance" || smname === "get" || smname === "a" ||
                    smname.toLowerCase().indexOf("query") >= 0 ||
                    smname.toLowerCase().indexOf("getcontact") >= 0) {
                    var sparams = [];
                    var sptypes = smethods[smi].getParameterTypes();
                    for (var j = 0; j < sptypes.length; j++) sparams.push(sptypes[j].getName());
                    log("  " + storageClasses[si] + "." + smname + "(" + sparams.join(",") + ") → " +
                        smethods[smi].getReturnType().getName());
                }
            }
        } catch (e) {}
    }

    // ================================================================
    // PHASE 4: Try to actually get a Contact object
    // ================================================================
    log("\n━━━ PHASE 4: Try to get Contact for '" + TEST_WXID + "' ━━━");

    // 4a: Via kernel.h → getStorage(ContactStorage.class) → get(wxid)
    try {
        var KernelH = Java.use("com.tencent.mm.kernel.h");
        // Try getStorage with Class param
        try {
            var ContactStorage = Java.use("com.tencent.mm.storage.ContactStorage");
            var mGetStorage = KernelH.class.getMethod("getStorage", Java.use("java.lang.Class").class);
            var cs = mGetStorage.invoke(null, ContactStorage.class);
            log("Via kernel.h.getStorage(ContactStorage.class): " + cs);
            if (cs != null) {
                dumpMethods(cs, "get", "ContactStorage methods");
                // Try get(wxid)
                try {
                    var mGet = cs.getClass().getMethod("get", Java.use("java.lang.String").class);
                    var contact = mGet.invoke(cs, TEST_WXID);
                    log("ContactStorage.get('" + TEST_WXID + "'): " + contact);
                    if (contact != null) {
                        dumpAllFields(contact, "Contact object (via kernel.h)");
                    }
                } catch (e) { log("ContactStorage.get(String) failed: " + e); }
            }
        } catch (e) { log("Via kernel.h.getStorage: " + e); }
    } catch (e) {}

    // 4b: Try via com.tencent.mm.storage.m3.j1() pattern (session contact known)
    // This is a session contact, not address book contact
    log("\n--- Phase 4b: Try ContactStorage direct singleton ---");
    var contactStoragePaths = [
        "com.tencent.mm.storage.ContactStorage",
        "com.tencent.mm.model.ContactStorage",
        "com.tencent.mm.plugin.contact.model.ContactStorage",
    ];
    for (var csi = 0; csi < contactStoragePaths.length; csi++) {
        try {
            var csCls = Java.use(contactStoragePaths[csi]);
            log("FOUND: " + contactStoragePaths[csi]);
            // Try get(wxid) class method
            try {
                var mGet2 = csCls.class.getMethod("get", Java.use("java.lang.String").class);
                var contact2 = mGet2.invoke(null, TEST_WXID);
                log("Static get('" + TEST_WXID + "'): " + contact2);
                if (contact2 != null) {
                    dumpAllFields(contact2, "Contact object (static get)");
                }
            } catch (e) {}
            // Try a(String) (common WeChat shortcut method naming)
            try {
                var mA = csCls.class.getMethod("a", Java.use("java.lang.String").class);
                var contact3 = mA.invoke(null, TEST_WXID);
                log("Static a('" + TEST_WXID + "'): " + contact3);
                if (contact3 != null) {
                    dumpAllFields(contact3, "Contact object (static a)");
                }
            } catch (e) {}
        } catch (e) {}
    }

    // 4c: Try to find any contact via m3's own static/query methods
    log("\n--- Phase 4c: Try m3 query methods ---");
    try {
        var M3 = Java.use("com.tencent.mm.storage.m3");
        // Check for query/get methods that take String
        var m3Methods = M3.class.getDeclaredMethods();
        for (var mi = 0; mi < m3Methods.length; mi++) {
            var m = m3Methods[mi];
            var ptypes = m.getParameterTypes();
            if (ptypes.length === 1 && ptypes[0].getName() === "java.lang.String") {
                var mname = m.getName();
                log("  Found String-param method: " + mname);
                if (mname.length() <= 3 || mname.toLowerCase().indexOf("get") >= 0 ||
                    mname.toLowerCase().indexOf("find") >= 0 || mname.toLowerCase().indexOf("query") >= 0) {
                    try {
                        var result = m.invoke(null, TEST_WXID);
                        log("  " + mname + "('" + TEST_WXID + "') = " + result);
                        if (result != null) {
                            dumpAllFields(result, "Result of m3." + mname);
                        }
                    } catch (e) {}
                }
            }
        }
    } catch (e) {}

    // ================================================================
    // PHASE 5: Broad approach — hook any method that returns a Contact
    // and try to catch one with our test wxid
    // ================================================================
    log("\n━━━ PHASE 5: Try to hook/detect contact creation ━━━");

    // 5a: Try to access through com.tencent.mm.model.aj (common MM kernel alias)
    try {
        var AJ = Java.use("com.tencent.mm.model.aj");
        log("FOUND: com.tencent.mm.model.aj");
        dumpMethods(AJ.class, "", "aj all methods (limit 30)");
    } catch (e) { log("com.tencent.mm.model.aj NOT FOUND"); }

    // 5b: Try com.tencent.mm.model.c (another common kernel alias)
    try {
        var MC = Java.use("com.tencent.mm.model.c");
        log("FOUND: com.tencent.mm.model.c");
        dumpMethods(MC.class, "", "c all methods (limit 30)");
    } catch (e) { log("com.tencent.mm.model.c NOT FOUND"); }

    // 5c: Try com.tencent.mm.kernel.b (kernel service manager in some versions)
    try {
        var KernelB = Java.use("com.tencent.mm.kernel.b");
        log("FOUND: com.tencent.mm.kernel.b");
        dumpMethods(KernelB.class, "getService", "getService methods");
    } catch (e) { log("com.tencent.mm.kernel.b NOT FOUND"); }

    // 5d: Dump known m3 fields from CLASS_MAP_8066
    log("\n--- Summary: known m3 fields from CLASS_MAP_8066 ---");
    log("  j1() → String wxid (method)");
    log("  field_unReadCount → int");
    log("  These are session-contact fields; address-book contact may differ.");

    log("\n=== T07 script complete ===");
    log("If no Contact object was dumped, try:");
    log("  1. Open Contacts tab in WeChat (force contact classes to load)");
    log("  2. Run: frida -U -p <PID> -l this_script.js");
});
