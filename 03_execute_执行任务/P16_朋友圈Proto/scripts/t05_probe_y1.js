Java.perform(function() {
    var TAG = "[Y1PROBE] ";

    // 1. Try Java.choose existing y1 instances
    console.log(TAG + "=== Java.choose y1 ===");
    Java.choose("com.tencent.mm.plugin.sns.ui.improve.component.y1", {
        onMatch: function(inst) {
            console.log(TAG + "FOUND y1 instance: " + inst + " hash=" + inst.hashCode());
            var H = null;
            try {
                var f = inst.getClass().getDeclaredField("H");
                f.setAccessible(true);
                H = f.get(inst);
                console.log(TAG + "  .H = " + H + " type=" + (H != null ? H.getClass().getName() : "null"));
            } catch(e) {
                console.log(TAG + "  .H error: " + e);
            }
            if (H != null) {
                try {
                    var o = null, p = null;
                    var fields = H.getClass().getDeclaredFields();
                    for (var i = 0; i < fields.length; i++) {
                        var fn = fields[i].getName();
                        if (fn === "o" || fn === "p") {
                            fields[i].setAccessible(true);
                            var v = fields[i].get(H);
                            if (v != null) {
                                console.log(TAG + "  ." + fn + " = " + v + " type=" + v.getClass().getName() + " size=" + Java.cast(v, Java.use("java.util.List")).size());
                            }
                        }
                    }
                } catch(e) {
                    console.log(TAG + "  MvvmList fields error: " + e);
                }
            }
        },
        onComplete: function() {
            console.log(TAG + "=== Java.choose y1 DONE ===");
        }
    });

    // 2. Hook y1 constructors
    console.log(TAG + "=== Hook y1 constructors ===");
    try {
        var y1 = Java.use("com.tencent.mm.plugin.sns.ui.improve.component.y1");
        var ctors = y1.class.getDeclaredConstructors();
        console.log(TAG + "y1 declared constructors: " + ctors.length);
        for (var i = 0; i < ctors.length; i++) {
            var ptypes = ctors[i].getParameterTypes();
            var pnames = [];
            for (var j = 0; j < ptypes.length; j++) pnames.push(ptypes[j].getName());
            console.log(TAG + "  ctor[" + i + "](" + pnames.join(",") + ")");
        }
        // Hook via overload
        y1.$init.overloads.forEach(function(ov) {
            var sig = ov.argumentTypes.map(function(t){return t.className}).join(",");
            console.log(TAG + "  overload: (" + sig + ")");
            ov.implementation = function() {
                console.log(TAG + ">>> y1 CONSTRUCTOR CALLED (" + sig + ")");
                var ret = ov.apply(this, arguments);
                console.log(TAG + "    y1 instance hash=" + this.hashCode());
                return ret;
            };
        });
        console.log(TAG + "y1 constructor hooks installed");
    } catch(e) {
        console.log(TAG + "y1 hook error: " + e);
    }

    console.log(TAG + "=== READY, enter moments now ===");
});
