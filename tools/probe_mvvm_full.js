// probe_mvvm_full.js — 递归 dump MvvmList 及其所有父类的全部字段
var T = "[MVVM]";

Java.perform(function () {
    console.log(T + " start");

    Java.choose("ik3.n", {
        onMatch: function(inst) {
            console.log(T + " ik3.n: " + inst);
            var aField = inst.getClass().getDeclaredField("a");
            aField.setAccessible(true);
            var mvvm = aField.get(inst);
            console.log(T + " MvvmConvList = " + mvvm);

            // walk class hierarchy and dump all fields
            var cls = mvvm.getClass();
            while (cls && cls.getName() !== "java.lang.Object") {
                console.log(T + " --- " + cls.getName() + " ---");
                var fields = cls.getDeclaredFields();
                for (var i = 0; i < fields.length; i++) {
                    fields[i].setAccessible(true);
                    try {
                        var v = fields[i].get(mvvm);
                        var tn = v ? v.getClass().getName() : "null";
                        var valStr = v ? String(v) : "null";
                        if (valStr.length > 120) valStr = valStr.substring(0, 120) + "...";
                        console.log(T + "  " + cls.getSimpleName() + "." + fields[i].getName() + " = " + valStr + " [" + tn + "]");
                    } catch(e2) {
                        console.log(T + "  " + cls.getSimpleName() + "." + fields[i].getName() + " = <err: " + e2 + ">");
                    }
                }
                cls = cls.getSuperclass();
            }

            console.log(T + " === DONE ===");
        },
        onComplete: function() { console.log(T + " scan done"); }
    });
});
