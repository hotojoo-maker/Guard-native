// probe_trigger_chain.js — 找 cl0.u / ik3.m 实例，尝试触发会话刷新链
var T = "[trigger]";

Java.perform(function () {
    console.log(T + " start");

    // Step 1: 找 cl0.u 实例
    Java.choose("cl0.u", {
        onMatch: function(inst) {
            console.log(T + " cl0.u instance: " + inst);
            global.c0 = inst;

            var cls = inst.getClass();
            var fields = cls.getDeclaredFields();
            console.log(T + " cl0.u fields:");
            for (var i = 0; i < fields.length; i++) {
                fields[i].setAccessible(true);
                try {
                    var v = fields[i].get(inst);
                    var tn = v ? v.getClass().getName() : "null";
                    console.log(T + "  ." + fields[i].getName() + " = " + v + " (" + tn + ")");
                } catch(e) {}
            }

            // 尝试调 E(List) — 需要一个 List 参数
            console.log(T + " trying cl0.u.E(null)...");
            try {
                var E = cls.getDeclaredMethod("E", Java.use("java.util.List").class);
                E.setAccessible(true);
                E.invoke(inst, null);
                console.log(T + " cl0.u.E(null) DONE");
            } catch(e) {
                console.log(T + " cl0.u.E err: " + e);
            }

            // 尝试调 B(Runnable) — 可能触发内部刷新
            console.log(T + " trying cl0.u.B(Runnable)...");
            try {
                var B = cls.getDeclaredMethod("B", Java.use("java.lang.Runnable").class);
                B.setAccessible(true);
                B.invoke(inst, null);
                console.log(T + " cl0.u.B(null) DONE");
            } catch(e) {
                console.log(T + " cl0.u.B err: " + e);
            }
        },
        onComplete: function() {
            console.log(T + " cl0.u scan done");
        }
    });

    // Step 2: 找 ik3.m 实例
    Java.choose("ik3.m", {
        onMatch: function(inst) {
            console.log(T + " ik3.m instance: " + inst);
            global.m0 = inst;

            var cls = inst.getClass();
            var fields = cls.getDeclaredFields();
            console.log(T + " ik3.m fields:");
            for (var i = 0; i < fields.length; i++) {
                fields[i].setAccessible(true);
                try {
                    var v = fields[i].get(inst);
                    var tn = v ? v.getClass().getName() : "null";
                    console.log(T + "  ." + fields[i].getName() + " = " + v + " (" + tn + ")");
                } catch(e) {}
            }

            // 尝试 invoke()
            console.log(T + " trying ik3.m.invoke()...");
            try {
                var inv = cls.getDeclaredMethod("invoke");
                inv.setAccessible(true);
                inv.invoke(inst);
                console.log(T + " ik3.m.invoke() DONE");
            } catch(e) {
                console.log(T + " ik3.m.invoke err: " + e);
            }
        },
        onComplete: function() {
            console.log(T + " ik3.m scan done");
        }
    });

    // Step 3: 找 ik3.n 实例
    Java.choose("ik3.n", {
        onMatch: function(inst) {
            console.log(T + " ik3.n instance: " + inst);
            global.n0 = inst;

            var cls = inst.getClass();
            var fields = cls.getDeclaredFields();
            console.log(T + " ik3.n fields:");
            for (var i = 0; i < fields.length; i++) {
                fields[i].setAccessible(true);
                try {
                    var v = fields[i].get(inst);
                    var tn = v ? v.getClass().getName() : "null";
                    console.log(T + "  ." + fields[i].getName() + " = " + v + " (" + tn + ")");
                } catch(e) {}
            }

            // 尝试 handleEvent(List)
            console.log(T + " trying ik3.n.handleEvent(null)...");
            try {
                var he = cls.getDeclaredMethod("handleEvent", Java.use("java.util.List").class);
                he.setAccessible(true);
                he.invoke(inst, null);
                console.log(T + " ik3.n.handleEvent(null) DONE");
            } catch(e) {
                console.log(T + " ik3.n.handleEvent err: " + e);
            }
        },
        onComplete: function() {
            console.log(T + " ik3.n scan done");
        }
    });

    console.log(T + " all scans launched");
});
