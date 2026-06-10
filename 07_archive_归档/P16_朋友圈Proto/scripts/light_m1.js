Java.perform(function() {
    var FMF = Java.use("com.tencent.mm.ui.FindMoreFriendsUI");

    // M1() — L1() 的直接上游
    var methods = FMF.class.getDeclaredMethods();
    for (var i = 0; i < methods.length; i++) {
        var m = methods[i];
        var mn = m.getName();
        if (mn === "M1" || mn === "L1") {
            var paramTypes = m.getParameterTypes();
            console.log("[HOOK] " + mn + "(" + paramTypes.length + " params)");
        }
    }

    // Hook M1 — dump this + params
    FMF.M1.overload().implementation = function() {
        console.log("\n=== [M1] ENTER ===");
        // dump params
        for (var i = 0; i < arguments.length; i++) {
            if (arguments[i] !== null && arguments[i] !== undefined) {
                var argCls = arguments[i].getClass().getName();
                console.log("[M1.arg" + i + "] " + argCls);
                if (argCls === "java.lang.String") {
                    console.log("[M1.arg" + i + ".val] " + arguments[i]);
                }
                // if arg is a List, dump its items
                if (argCls.indexOf("List") >= 0 || argCls.indexOf("ArrayList") >= 0) {
                    var sz = arguments[i].size();
                    console.log("[M1.arg" + i + ".size] " + sz);
                    for (var j = 0; j < Math.min(sz, 8); j++) {
                        var item = arguments[i].get(j);
                        if (item !== null) {
                            var icls = item.getClass().getName();
                            console.log("[M1.arg" + i + "[" + j + "]] " + icls + " = " + item.toString());
                        }
                    }
                }
            }
        }
        return this.M1();
    };

    // Keep L1 lightweight — just log y and any List fields
    FMF.L1.overload().implementation = function() {
        console.log("[L1] y check...");
        // dump all List/Map/String fields
        var fields = this.getClass().getDeclaredFields();
        for (var i = 0; i < fields.length; i++) {
            var f = fields[i];
            f.setAccessible(true);
            var tn = f.getType().getName();
            try {
                var v = f.get(this);
                if (v === null) continue;
                if (tn.indexOf("List") >= 0) {
                    console.log("[L1.List] " + f.getName() + " size=" + v.size());
                    for (var j = 0; j < Math.min(v.size(), 5); j++) {
                        var item = v.get(j);
                        if (item !== null) {
                            console.log("  [" + j + "] " + item.getClass().getName() + " = " + item.toString());
                        }
                    }
                } else if (tn === "java.lang.String" && v.length() > 0 && v.length() < 120) {
                    console.log("[L1.Str] " + f.getName() + " = " + v);
                }
            } catch(e2) {}
        }
        return this.L1();
    };

    console.log("[M1+L1] hooks ready");
});
