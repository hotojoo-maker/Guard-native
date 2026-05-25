// probe_trigger_ik3n.js — 聚焦 ik3.n 实例，尝试 handleEvent 触发刷新
var T = "[ik3n]";

Java.perform(function () {
    console.log(T + " start");

    Java.choose("ik3.n", {
        onMatch: function(inst) {
            console.log(T + " FOUND instance: " + inst);

            var cls = inst.getClass();

            // dump fields
            var fields = cls.getDeclaredFields();
            console.log(T + " fields (" + fields.length + "):");
            for (var i = 0; i < fields.length; i++) {
                fields[i].setAccessible(true);
                try {
                    var v = fields[i].get(inst);
                    var tn = v ? v.getClass().getName() : "null";
                    if (tn === "java.util.ArrayList" && v) {
                        console.log(T + "  ." + fields[i].getName() + " = ArrayList[" + v.size() + "] " + v);
                    } else {
                        console.log(T + "  ." + fields[i].getName() + " = " + v + " (" + tn + ")");
                    }
                } catch(e2) {}
            }

            // also dump non-declared methods
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

            // try handleEvent() — 3 variants: no-arg, List, Object
            console.log(T + " === trying handleEvent variants ===");

            // variant 1: handleEvent(List)
            try {
                var he1 = cls.getDeclaredMethod("handleEvent", Java.use("java.util.List").class);
                he1.setAccessible(true);
                console.log(T + " handleEvent(List) found, invoking with null...");
                he1.invoke(inst, null);
                console.log(T + " handleEvent(null) DONE");
            } catch(e3) {
                console.log(T + " handleEvent(List) err: " + e3);
            }

            // variant 2: handleEvent(Object)
            try {
                var he2 = cls.getDeclaredMethod("handleEvent", Java.use("java.lang.Object").class);
                he2.setAccessible(true);
                console.log(T + " handleEvent(Object) found, invoking with null...");
                he2.invoke(inst, null);
                console.log(T + " handleEvent(Object) DONE");
            } catch(e4) {
                console.log(T + " handleEvent(Object) err: " + e4);
            }

            // variant 3: handleEvent() no args
            try {
                var he0 = cls.getDeclaredMethod("handleEvent");
                he0.setAccessible(true);
                console.log(T + " handleEvent() no-arg found, invoking...");
                he0.invoke(inst);
                console.log(T + " handleEvent() DONE");
            } catch(e5) {
                console.log(T + " handleEvent() err: " + e5);
            }

            console.log(T + " === DONE ===");
        },
        onComplete: function() {
            console.log(T + " scan complete");
        }
    });

    console.log(T + " scan launched");
});
