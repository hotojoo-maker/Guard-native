// Dump contact fields for wxid_hmu4qj85aaa812
Java.perform(function() {
    var wxid = "wxid_hmu4qj85aaa812";

    // Try to find contact storage via kernel
    try {
        var kernel = Java.use("com.tencent.mm.kernel.h");
        console.log("[1] kernel class found");

        // List kernel methods to find getStorage
        var methods = kernel.class.getDeclaredMethods();
        console.log("Kernel methods:");
        for (var i = 0; i < methods.length; i++) {
            var m = methods[i];
            var params = m.getParameterTypes();
            if (m.getName().indexOf("get") >= 0 && params.length <= 1) {
                var paramStr = "";
                for (var j = 0; j < params.length; j++) paramStr += params[j].getName() + " ";
                console.log("  " + m.getName() + "(" + paramStr + ") -> " + m.getReturnType().getName());
            }
        }
    } catch (e) {
        console.log("[1] kernel err: " + e);
    }

    // Try to find ContactStorage class
    console.log("\n[2] Searching ContactStorage...");
    Java.enumerateLoadedClasses({
        onMatch: function(className) {
            if (className.indexOf("Contact") >= 0 && className.indexOf("Storage") >= 0) {
                console.log("  FOUND: " + className);
            }
        },
        onComplete: function() { console.log("  done"); }
    });
});
