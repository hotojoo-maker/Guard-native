// probe_fire2.js — 简化版：读 MvvmList 数据列表 → 调 handleEvent
var T = "[FIRE2]";

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
                console.log(T + " parent=" + parentCls.getName());

                // read data lists
                var fH = parentCls.getDeclaredField("h"); fH.setAccessible(true);
                var listH = fH.get(mvvm);
                console.log(T + " listH=" + listH + " class=" + listH.getClass().getName());

                var sz = listH.size();
                console.log(T + " listH.size=" + sz);

                // find and call handleEvent
                var methods = cls.getDeclaredMethods();
                var he = null;
                for (var i = 0; i < methods.length; i++) {
                    if (methods[i].getName() === "handleEvent") { he = methods[i]; break; }
                }
                if (!he) { console.log(T + " no handleEvent"); return; }
                he.setAccessible(true);

                console.log(T + " calling handleEvent(listH)...");
                he.invoke(inst, [listH]);
                console.log(T + " handleEvent(listH) OK");

            } catch(e) {
                console.log(T + " ERR: " + e);
            }
            console.log(T + " DONE");
        },
        onComplete: function() { console.log(T + " scan done"); }
    });
});
