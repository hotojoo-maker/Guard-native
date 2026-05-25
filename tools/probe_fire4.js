// probe_fire4.js — Java.cast 正确拿到 ArrayList，调 handleEvent 触发刷新
var T = "[FIRE4]";

Java.perform(function () {
    console.log(T + " start");

    Java.choose("ik3.n", {
        onMatch: function(inst) {
            try {
                var cls = inst.getClass();
                var aField = cls.getDeclaredField("a");
                aField.setAccessible(true);
                var mvvm = aField.get(inst);
                console.log(T + " mvvm=" + mvvm);

                var parentCls = mvvm.getClass().getSuperclass();

                // get .h field — Frida 17 wraps Java objects
                var fH = parentCls.getDeclaredField("h"); fH.setAccessible(true);
                var rawH = fH.get(mvvm);
                console.log(T + " rawH class=" + rawH.getClass().getName());

                // Cast to java.util.List for proper method access
                var List = Java.use("java.util.List");
                var listH = Java.cast(rawH, List);
                var sz = listH.size();
                console.log(T + " listH.size=" + sz);

                // Also dump first 2 items to confirm data
                for (var di = 0; di < Math.min(2, sz); di++) {
                    var item = listH.get(di);
                    console.log(T + " [" + di + "] " + item + " class=" + item.getClass().getName());
                }

                // Find handleEvent
                var methods = cls.getDeclaredMethods();
                var he = null;
                for (var i = 0; i < methods.length; i++) {
                    if (methods[i].getName() === "handleEvent") { he = methods[i]; break; }
                }
                if (!he) { console.log(T + " no handleEvent"); return; }
                he.setAccessible(true);

                // Try handleEvent with .h data
                console.log(T + " calling handleEvent(listH) sz=" + sz);
                he.invoke(inst, [listH]);
                console.log(T + " handleEvent(listH) OK");

                // Also try .o and .p
                var fO = parentCls.getDeclaredField("o"); fO.setAccessible(true);
                var listO = Java.cast(fO.get(mvvm), List);
                console.log(T + " listO.size=" + listO.size());
                if (listO.size() !== sz) {
                    console.log(T + " trying handleEvent(listO)...");
                    he.invoke(inst, [listO]);
                    console.log(T + " handleEvent(listO) OK");
                }

                var fP = parentCls.getDeclaredField("p"); fP.setAccessible(true);
                var listP = Java.cast(fP.get(mvvm), List);
                console.log(T + " listP.size=" + listP.size());

            } catch(e) {
                console.log(T + " ERR: " + e);
            }
            console.log(T + " DONE");
        },
        onComplete: function() { console.log(T + " scan done"); }
    });
});
