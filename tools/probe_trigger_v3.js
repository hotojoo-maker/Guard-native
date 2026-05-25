// probe_trigger_v3.js — 从父类读 backingList，正确传参调 handleEvent
var T = "[TRG3]";

Java.perform(function () {
    console.log(T + " start");

    Java.choose("ik3.n", {
        onMatch: function(inst) {
            console.log(T + " FOUND ik3.n: " + inst);
            var cls = inst.getClass();

            // get .a = MvvmConvList
            var aField = cls.getDeclaredField("a");
            aField.setAccessible(true);
            var mvvm = aField.get(inst);
            console.log(T + " .a MvvmConvList = " + mvvm);

            // read backing list from parent class MvvmList
            var parentCls = mvvm.getClass().getSuperclass();
            console.log(T + " parent = " + parentCls.getName());

            var backingList = null;
            // try field names: e, f, d, list
            var fieldNames = ["e", "f", "d", "a", "b", "c"];
            for (var fi = 0; fi < fieldNames.length; fi++) {
                try {
                    var f = parentCls.getDeclaredField(fieldNames[fi]);
                    f.setAccessible(true);
                    var v = f.get(mvvm);
                    console.log(T + " parent." + fieldNames[fi] + " = " + v + " (" + (v ? v.getClass().getName() : "null") + ")");
                    if (v && v.getClass().getName() === "java.util.ArrayList") {
                        backingList = v;
                        console.log(T + " backingList FOUND: size=" + v.size());
                    }
                } catch(e) {
                    // field not found, skip
                }
            }

            // also try subclass fields
            var subFields = mvvm.getClass().getDeclaredFields();
            console.log(T + " MvvmConvList own fields (" + subFields.length + "):");
            for (var si = 0; si < subFields.length; si++) {
                subFields[si].setAccessible(true);
                try {
                    var sv = subFields[si].get(mvvm);
                    var stn = sv ? sv.getClass().getName() : "null";
                    console.log(T + "  own." + subFields[si].getName() + " = " + sv + " (" + stn + ")");
                } catch(e) {}
            }

            // find handleEvent
            var methods = cls.getDeclaredMethods();
            var heMethod = null;
            for (var i = 0; i < methods.length; i++) {
                if (methods[i].getName() === "handleEvent") {
                    heMethod = methods[i];
                    break;
                }
            }

            if (!heMethod) {
                console.log(T + " handleEvent not found");
                return;
            }

            // Try handleEvent with backingList (Frida: invoke with array of args)
            if (backingList) {
                console.log(T + " try handleEvent(backingList) sz=" + backingList.size() + "...");
                try {
                    var JavaArray = Java.array('java.lang.Object', [backingList]);
                    heMethod.invoke(inst, JavaArray);
                    console.log(T + " handleEvent(backingList) OK via Java.array");
                } catch(e1) {
                    console.log(T + " Java.array err: " + e1);
                    try {
                        heMethod.invoke(inst, [backingList]);
                        console.log(T + " handleEvent(backingList) OK via JS array");
                    } catch(e2) {
                        console.log(T + " JS array err: " + e2);
                    }
                }
            } else {
                console.log(T + " no backingList found, trying with null...");
                try {
                    heMethod.invoke(inst, [null]);
                    console.log(T + " handleEvent([null]) OK");
                } catch(e3) {
                    console.log(T + " handleEvent([null]) err: " + e3);
                }
            }

            // Also try the mvvm NOTIFY directly
            console.log(T + " try mvvm.notifyDataSetChanged()...");
            try {
                var mvvmCls = mvvm.getClass();
                var ndc = mvvmCls.getMethod("notifyDataSetChanged");
                ndc.invoke(mvvm);
                console.log(T + " notifyDataSetChanged OK");
            } catch(e4) {
                console.log(T + " notifyDataSetChanged err: " + e4);
            }

            console.log(T + " === DONE ===");
        },
        onComplete: function() {
            console.log(T + " scan complete");
        }
    });
});
