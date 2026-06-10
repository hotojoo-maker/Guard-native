// Broad search for contact data in WeChat 8.0.66
Java.perform(function() {
    var wxid = "wxid_hmu4qj85aaa812";

    console.log("[1] Searching classes with 'Contact' or 'contact' or 'storage'...");
    var found = [];
    Java.enumerateLoadedClasses({
        onMatch: function(className) {
            var lower = className.toLowerCase();
            if (lower.indexOf("contact") >= 0 ||
                (lower.indexOf("storage") >= 0 && lower.indexOf("com.tencent") >= 0)) {
                found.push(className);
            }
        },
        onComplete: function() {
            console.log("Found " + found.length + " matching classes:");
            for (var i = 0; i < found.length; i++) {
                console.log("  " + found[i]);
            }

            console.log("\n[2] Trying WCDB direct read via com.tencent.wcdb...");
            try {
                var dbp = Java.use("com.tencent.wcdb.database.SQLiteDatabase");
                console.log("  WCDB SQLiteDatabase found");
            } catch (e) { console.log("  WCDB not found: " + e); }

            console.log("\n[3] Trying common Contact paths:");
            var paths = [
                "com.tencent.mm.model.ContactStorage",
                "com.tencent.mm.storage.ContactStorage",
                "com.tencent.mm.model.aj",
                "com.tencent.mm.storage.aj",
                "com.tencent.mm.storage.bp",
                "com.tencent.mm.plugin.contact.model.Contact",
            ];
            for (var j = 0; j < paths.length; j++) {
                try {
                    var cls = Java.use(paths[j]);
                    console.log("  FOUND: " + paths[j]);
                    // dump methods
                    var ms = cls.class.getDeclaredMethods();
                    for (var k = 0; k < ms.length; k++) {
                        var m = ms[k];
                        if (m.getName().toLowerCase().indexOf("get") >= 0 ||
                            m.getName().toLowerCase().indexOf("query") >= 0 ||
                            m.getName().toLowerCase().indexOf("search") >= 0) {
                            console.log("    " + m.getName() + "(" + m.getParameterTypes().length + ")");
                        }
                    }
                } catch (e) {}
            }
        }
    });
});
