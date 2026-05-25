// probe_cl0_v2.js — 匹配短类名 cl0 / cl0.u / fc5 / fv5 / ik3
var T = "[cl0v2]";

Java.perform(function () {
    Java.enumerateLoadedClassesSync().forEach(function(n) {
        if (n === "cl0" || n === "cl0.u" || n === "fc5" || n === "fv5" || n === "ik3" || n === "ik3.m" || n === "ik3.n") {
            console.log(T + " FOUND: '" + n + "'");
            try {
                var cls = Java.classFactory.loader.loadClass(n);
                console.log(T + " methods:");
                var methods = cls.getDeclaredMethods();
                for (var j = 0; j < methods.length; j++) {
                    var name = methods[j].getName();
                    var pts = methods[j].getParameterTypes();
                    var sig = name + "(";
                    for (var k = 0; k < pts.length; k++) {
                        if (k > 0) sig += ",";
                        sig += pts[k].getName();
                    }
                    sig += ")";
                    console.log("  " + sig);
                }
            } catch(e) {
                console.log(T + " err: " + e);
            }
        }
    });
    console.log(T + " scan done");
});
