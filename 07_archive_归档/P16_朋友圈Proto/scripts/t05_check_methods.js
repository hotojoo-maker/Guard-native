Java.perform(function() {
    var cls = Java.use("com.tencent.mm.protocal.protobuf.SnsObject");
    var methods = cls.class.getMethods();
    console.log("SnsObject parseFrom overloads (getMethods):");
    for (var i = 0; i < methods.length; i++) {
        var m = methods[i];
        if (m.getName().indexOf("parseFrom") >= 0) {
            console.log("  " + m.getName() + " params=" + m.getParameterTypes().length +
                " return=" + m.getReturnType().getName() +
                " declaring=" + m.getDeclaringClass().getName());
        }
    }

    // Also check declared methods on SnsObject
    console.log("\nSnsObject declared methods with 'parse':");
    var declMethods = cls.class.getDeclaredMethods();
    for (var j = 0; j < declMethods.length; j++) {
        var dm = declMethods[j];
        if (dm.getName().indexOf("parse") >= 0) {
            console.log("  " + dm.getName() + " params=" + dm.getParameterTypes().length);
        }
    }

    // Check parent classes
    console.log("\nParent class: " + cls.class.getSuperclass().getName());
    var parent = cls.class.getSuperclass();
    var pmethods = parent.getDeclaredMethods();
    console.log("Parent declared parseFrom:");
    for (var k = 0; k < pmethods.length; k++) {
        var pm = pmethods[k];
        if (pm.getName().indexOf("parseFrom") >= 0) {
            var ptypes = pm.getParameterTypes();
            console.log("  " + pm.getName() + " params=" + ptypes.length +
                " static=" + java.lang.reflect.Modifier.isStatic(pm.getModifiers()));
            for (var l = 0; l < ptypes.length; l++) {
                console.log("    param[" + l + "]=" + ptypes[l].getName());
            }
        }
    }
});
