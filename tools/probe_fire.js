// probe_fire.js — 用 MvvmList 的数据列表调 handleEvent 触发刷新
var T = "[FIRE]";

Java.perform(function () {
    console.log(T + " start");

    Java.choose("ik3.n", {
        onMatch: function(inst) {
            var cls = inst.getClass();
            var aField = cls.getDeclaredField("a");
            aField.setAccessible(true);
            var mvvm = aField.get(inst);
            var parentCls = mvvm.getClass().getSuperclass(); // MvvmList

            // get data lists
            var listH = null, listO = null, listP = null;
            try {
                var fH = parentCls.getDeclaredField("h"); fH.setAccessible(true); listH = fH.get(mvvm);
                var fO = parentCls.getDeclaredField("o"); fO.setAccessible(true); listO = fO.get(mvvm);
                var fP = parentCls.getDeclaredField("p"); fP.setAccessible(true); listP = fP.get(mvvm);
            } catch(e) {
                console.log(T + " field err: " + e);
                return;
            }

            console.log(T + " .h size=" + listH.size() + " .o size=" + listO.size() + " .p size=" + listP.size());

            // also dump StateFlow .s
            try {
                var fS = parentCls.getDeclaredField("s"); fS.setAccessible(true);
                var stateFlow = fS.get(mvvm);
                console.log(T + " .s StateFlow = " + stateFlow + " type=" + stateFlow.getClass().getName());
                // try to call getValue() on StateFlow
                try {
                    var sfCls = stateFlow.getClass();
                    var gv = sfCls.getMethod("getValue");
                    var sfVal = gv.invoke(stateFlow);
                    console.log(T + " .s.getValue() = " + sfVal + " type=" + (sfVal ? sfVal.getClass().getName() : "null"));
                } catch(e2) {
                    console.log(T + " .s.getValue err: " + e2);
                }
            } catch(e2) {
                console.log(T + " .s err: " + e2);
            }

            // find handleEvent method
            var heMethod = null;
            var methods = cls.getDeclaredMethods();
            for (var i = 0; i < methods.length; i++) {
                if (methods[i].getName() === "handleEvent") {
                    heMethod = methods[i];
                    break;
                }
            }

            if (!heMethod) { console.log(T + " no handleEvent"); return; }

            // Try handleEvent with each list (Frida: invoke(obj, [arg]))
            var lists = [listH, listO, listP];
            var names = [".h", ".o", ".p"];
            for (var li = 0; li < lists.length; li++) {
                if (!lists[li]) continue;
                console.log(T + " try handleEvent(" + names[li] + ") sz=" + lists[li].size() + "...");
                try {
                    heMethod.invoke(inst, [lists[li]]);
                    console.log(T + " handleEvent(" + names[li] + ") OK");
                } catch(e3) {
                    console.log(T + " handleEvent(" + names[li] + ") err: " + e3);
                }
            }

            console.log(T + " === DONE ===");
        },
        onComplete: function() {
            console.log(T + " scan done");
        }
    });
});
