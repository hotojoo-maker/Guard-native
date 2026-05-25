// probe_fire3.js — Frida 17 把 ArrayList 自动转 JS array，用 .length
var T = "[FIRE3]";

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

                var fH = parentCls.getDeclaredField("h"); fH.setAccessible(true);
                var listH = fH.get(mvvm);

                // Frida 17 auto-converts ArrayList → JS array
                console.log(T + " listH type=" + typeof listH + " len=" + listH.length);

                // verify items are Java objects
                if (listH.length > 0) {
                    var item0 = listH[0];
                    console.log(T + " item0=" + item0 + " type=" + typeof item0);
                    if (item0 && item0.getClass) {
                        console.log(T + " item0 class=" + item0.getClass().getName());
                    }
                }

                // Convert back to Java ArrayList for handleEvent
                var JavaArrayList = Java.use("java.util.ArrayList");
                var javaList = JavaArrayList.$new();
                for (var i = 0; i < listH.length; i++) {
                    javaList.add(listH[i]);
                }
                console.log(T + " javaList size=" + javaList.size());

                // find and call handleEvent
                var methods = cls.getDeclaredMethods();
                var he = null;
                for (var i = 0; i < methods.length; i++) {
                    if (methods[i].getName() === "handleEvent") { he = methods[i]; break; }
                }
                if (!he) { console.log(T + " no handleEvent"); return; }
                he.setAccessible(true);

                console.log(T + " calling handleEvent(javaList)...");
                he.invoke(inst, [javaList]);
                console.log(T + " handleEvent(javaList) OK");

                // also try directly with JS array
                console.log(T + " calling handleEvent(jsArray)...");
                try {
                    he.invoke(inst, [listH]);
                    console.log(T + " handleEvent(jsArray) OK");
                } catch(e2) {
                    console.log(T + " handleEvent(jsArray) err: " + e2);
                }

            } catch(e) {
                console.log(T + " ERR: " + e + " stack=" + (e.stack || "none"));
            }
            console.log(T + " DONE");
        },
        onComplete: function() { console.log(T + " scan done"); }
    });
});
