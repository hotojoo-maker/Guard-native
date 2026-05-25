// probe_ik3n0.js — 探 ik3.n0 的字段和方法
var T = "[n0]";

Java.perform(function () {
    console.log(T + " start");

    // enumerate to find ik3.n0
    Java.enumerateLoadedClassesSync().forEach(function(n) {
        if (n === "ik3.n0") {
            console.log(T + " class found: " + n);
            try {
                var cls = Java.classFactory.loader.loadClass(n);
                // fields
                var fields = cls.getDeclaredFields();
                console.log(T + " fields:");
                for (var i = 0; i < fields.length; i++) {
                    console.log(T + "  ." + fields[i].getName() + " : " + fields[i].getType().getName());
                }
                // methods
                var methods = cls.getDeclaredMethods();
                console.log(T + " methods:");
                for (var j = 0; j < methods.length; j++) {
                    var pts = methods[j].getParameterTypes();
                    var sig = methods[j].getName() + "(";
                    for (var k = 0; k < pts.length; k++) {
                        if (k > 0) sig += ",";
                        sig += pts[k].getName();
                    }
                    sig += ")";
                    console.log(T + "  " + sig);
                }
            } catch(e) {
                console.log(T + " err: " + e);
            }
        }
    });

    // find instances
    Java.choose("ik3.n0", {
        onMatch: function(inst) {
            console.log(T + " instance: " + inst);
            var cls = inst.getClass();
            var fields = cls.getDeclaredFields();
            for (var i = 0; i < fields.length; i++) {
                fields[i].setAccessible(true);
                try {
                    var v = fields[i].get(inst);
                    var tn = v ? v.getClass().getName() : "null";
                    if (tn === "java.util.ArrayList" && v) {
                        console.log(T + "  ." + fields[i].getName() + " = ArrayList[" + v.size() + "]");
                        // dump first 3 items
                        for (var di = 0; di < Math.min(3, v.size()); di++) {
                            console.log(T + "    [" + di + "] " + v.get(di));
                        }
                    } else {
                        console.log(T + "  ." + fields[i].getName() + " = " + v + " (" + tn + ")");
                    }
                } catch(e2) {}
            }
        },
        onComplete: function() {
            console.log(T + " scan done");
        }
    });
});
