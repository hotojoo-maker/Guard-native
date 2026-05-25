// probe_cl0.js — 找 cl0.u 实例，尝试触发会话刷新链
var T = "[cl0]";

Java.perform(function () {
    // Step 1: find cl0.u class
    var clsName = "";
    Java.enumerateLoadedClassesSync().forEach(function(n) {
        if (n.match(/\.cl0$/)) clsName = n;
    });
    if (!clsName) {
        console.log(T + " cl0 not found");
        return;
    }
    console.log(T + " class: " + clsName);

    try {
        var cls = Java.classFactory.loader.loadClass(clsName);
        var fields = cls.getDeclaredFields();
        console.log(T + " fields:");
        for (var i = 0; i < fields.length; i++) {
            console.log("  " + fields[i].getName() + " : " + fields[i].getType().getName());
        }
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
            console.log("  " + sig);
        }
    } catch(e) {
        console.log(T + " class probe err: " + e);
    }

    // Step 2: find instances
    Java.choose(clsName, {
        onMatch: function(inst) {
            console.log(T + " instance: " + inst);
            global.CL0_INST = inst;
        },
        onComplete: function() {
            console.log(T + " scan done. global.CL0_INST ready");
            console.log(T + " ★ try: Java.use('" + clsName + "').V(global.CL0_INST, ?) or similar");
        }
    });
});
