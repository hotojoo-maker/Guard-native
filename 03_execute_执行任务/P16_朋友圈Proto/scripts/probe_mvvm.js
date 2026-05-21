// probe_mvvm.js — 枚举 MvvmList 所有方法 + 监控调用
// 目标: 搞清楚朋友圈刷新时到底哪个方法被调用

var HIDE_WXIDS = ["wxid_toghm7m6uqsr12"];
var MVVM = "com.tencent.mm.plugin.mvvmlist.MvvmList";

function log(msg) { console.log("[PROBE] " + msg); }

Java.perform(function () {
    try {
        Java.classFactory.loader = Java.use("android.app.ActivityThread").currentApplication().getClassLoader();
    } catch (e) { log("loader fail: " + e); }

    var MvvmList = Java.use(MVVM);
    var methods = MvvmList.class.getDeclaredMethods();

    log("=== MvvmList declared methods (" + methods.length + " total) ===");

    var hooked = 0;
    for (var i = 0; i < methods.length; i++) {
        var m = methods[i];
        var name = m.getName();
        var params = m.getParameterTypes();
        var paramNames = [];
        for (var j = 0; j < params.length; j++) {
            paramNames.push(params[j].getName());
        }

        // Print all methods
        log("  " + name + "(" + paramNames.join(", ") + ")");

        // Hook all public/protected methods that take List/Collection params
        // or are named m/s/w (known data entry points)
        var shouldHook = false;
        for (var k = 0; k < params.length; k++) {
            var pn = params[k].getName();
            if (pn.indexOf("java.util.List") >= 0 ||
                pn.indexOf("java.util.Collection") >= 0 ||
                pn.indexOf("nd3.o0") >= 0) {
                shouldHook = true;
                break;
            }
        }

        if (shouldHook || name === "m" || name === "s" || name === "w" || name === "a" || name === "b") {
            try {
                // Build overload array
                var overloads = [];
                for (var j = 0; j < params.length; j++) {
                    overloads.push(params[j].getName());
                }

                var methodName = name;
                MvvmList[methodName].overload.apply(MvvmList, overloads).implementation = function () {
                    var args = Array.prototype.slice.call(arguments);
                    var shortArgs = args.map(function (a) {
                        if (a == null) return "null";
                        var cn = a.getClass().getName();
                        if (cn === "java.util.ArrayList" || cn === "java.util.List") {
                            return cn + "(size=" + Java.cast(a, Java.use("java.util.List")).size() + ")";
                        }
                        return cn;
                    });
                    log(">>> " + methodName + "(" + shortArgs.join(", ") + ") CALLED");
                    return this[methodName].apply(this, arguments);
                };
                hooked++;
                log("    ^ hooked");
            } catch (e) {
                log("    ^ hook FAIL: " + e);
            }
        }
    }

    log("=== hooked " + hooked + " methods, waiting for triggers ===");
    log("Now open WeChat → Discover → Moments → scroll/refresh");
});
