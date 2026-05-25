// probe_trigger_v2.js — 迭代找 handleEvent 方法并调用
var T = "[TRG2]";

Java.perform(function () {
    console.log(T + " start");

    Java.choose("ik3.n", {
        onMatch: function(inst) {
            console.log(T + " FOUND ik3.n: " + inst);
            var cls = inst.getClass();

            // field .a = MvvmConvList
            var aField = cls.getDeclaredField("a");
            aField.setAccessible(true);
            var mvvm = aField.get(inst);
            console.log(T + " .a (MvvmConvList) = " + mvvm);

            // find handleEvent via iteration
            var methods = cls.getDeclaredMethods();
            var heMethod = null;
            for (var i = 0; i < methods.length; i++) {
                if (methods[i].getName() === "handleEvent") {
                    heMethod = methods[i];
                    console.log(T + " found handleEvent: " + heMethod);
                    break;
                }
            }

            if (!heMethod) {
                console.log(T + " handleEvent not found");
                return;
            }

            // Try to invoke — first get the current data list from MvvmConvList
            heMethod.setAccessible(true);

            // try with null first
            console.log(T + " try handleEvent(null)...");
            try {
                heMethod.invoke(inst, null);
                console.log(T + " handleEvent(null) OK");
            } catch(e) {
                console.log(T + " handleEvent(null) err: " + e);
            }

            // try with empty ArrayList
            console.log(T + " try handleEvent(emptyList)...");
            try {
                var ArrayList = Java.use("java.util.ArrayList");
                var emptyList = ArrayList.$new();
                heMethod.invoke(inst, emptyList);
                console.log(T + " handleEvent(emptyList) OK");
            } catch(e2) {
                console.log(T + " handleEvent(emptyList) err: " + e2);
            }

            // try with mvvm data
            console.log(T + " try handleEvent(mvvm)...");
            try {
                // MvvmList has .e field (backing list) — try to get it
                var mvvmCls = mvvm.getClass();
                var eField = mvvmCls.getDeclaredField("e");
                eField.setAccessible(true);
                var backingList = eField.get(mvvm);
                console.log(T + " MvvmList.e (backing) = " + backingList + " type=" + (backingList ? backingList.getClass().getName() : "null"));
                if (backingList) {
                    console.log(T + " backingList size=" + (typeof backingList.size === 'function' ? backingList.size() : "?"));
                    heMethod.invoke(inst, backingList);
                    console.log(T + " handleEvent(backingList) OK");
                }
            } catch(e3) {
                console.log(T + " handleEvent(backingList) err: " + e3);
            }

            console.log(T + " === DONE ===");
        },
        onComplete: function() {
            console.log(T + " scan complete");
        }
    });
});
