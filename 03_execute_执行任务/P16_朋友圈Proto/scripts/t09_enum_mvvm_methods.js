// T09 诊断 #3 — 枚举 MvvmList 所有方法，hook 全部，看朋友圈刷新时谁被调用
// 目标：找到 8.0.66 朋友圈 feed 实际走的数据写入方法

Java.perform(function() {
    var MvvmList = Java.use("com.tencent.mm.plugin.mvvmlist.MvvmList");
    var methods = MvvmList.class.getDeclaredMethods();
    var hooked = [];

    methods.forEach(function(m) {
        var name = m.getName();
        var params = m.getParameterTypes();
        var paramStr = [];
        for (var i = 0; i < params.length; i++) {
            paramStr.push(params[i].getName());
        }
        var sig = name + "(" + paramStr.join(",") + ")";

        // Skip Object methods, getClass, hashCode etc
        if (name === "equals" || name === "hashCode" || name === "toString" ||
            name === "getClass" || name === "notify" || name === "notifyAll" ||
            name === "wait" || name === "finalize") return;

        try {
            var overloads = MvvmList[name].overloads;
            overloads.forEach(function(overload) {
                var key = name + "(" + overload.argumentTypes.map(function(t){return t.getName()}).join(",") + ")";
                if (hooked.indexOf(key) >= 0) return;
                hooked.push(key);
                overload.implementation = function() {
                    var args = [];
                    for (var i = 0; i < arguments.length; i++) {
                        var a = arguments[i];
                        if (a === null) args.push("null");
                        else if (a instanceof Java.use("java.util.List")) args.push("List(size=" + a.size() + ")");
                        else args.push(String(a).substring(0, 40));
                    }
                    console.log("[T09] " + key + " | args=" + JSON.stringify(args));
                    return overload.apply(this, arguments);
                };
            });
        } catch (e) {
            // overload resolution failed, skip
        }
    });

    console.log("[T09] Hooked " + hooked.length + " MvvmList methods:");
    hooked.forEach(function(h) { console.log("  " + h); });
    console.log("[T09] Ready. Scroll moments feed now...");
});
