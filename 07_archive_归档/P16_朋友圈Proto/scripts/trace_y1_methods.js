// trace_y1_methods.js — 抓 y1 在朋友圈刷新时实际调用了哪些方法
// 用法: frida -U -n com.tencent.mm --no-pause -l trace_y1_methods.js

Java.perform(function () {
    var TARGET = "com.tencent.mm.plugin.sns.ui.improve.component.y1";

    try {
        var cls = Java.use(TARGET);
        var methods = cls.class.getMethods();
        var hooked = 0;

        for (var i = 0; i < methods.length; i++) {
            (function (m) {
                try {
                    var name = m.getName();
                    var paramCount = m.getParameterTypes().length;
                    // Skip noisy layout/measure/draw methods
                    if (name === "equals" || name === "hashCode" || name === "toString"
                        || name === "getClass" || name === "wait" || name === "notify"
                        || name === "notifyAll") return;

                    Java.use(TARGET)[name].overload.apply(null, []).implementation = function () {
                        console.log("[y1] " + name + "()");
                        return this[name].apply(this, arguments);
                    };
                    hooked++;
                } catch (e) {
                    // overload needs param types — skip overloaded methods silently
                }
            })(methods[i]);
        }

        // Hook all overloads via XposedBridge-style reflection
        // Broader catch: hook via reflection for all signatures
        var reflectMethods = cls.class.getDeclaredMethods();
        var seen = {};
        for (var j = 0; j < reflectMethods.length; j++) {
            (function (m) {
                try {
                    var name = m.getName();
                    if (seen[name]) return;
                    seen[name] = true;
                    var paramTypes = m.getParameterTypes();
                    var paramNames = [];
                    for (var k = 0; k < paramTypes.length; k++) {
                        paramNames.push(paramTypes[k].getName());
                    }
                    cls[name].overload.apply(cls, paramNames).implementation = function () {
                        console.log("[y1] " + name + "(" + paramNames.join(",") + ")");
                        return this[name].apply(this, arguments);
                    };
                } catch (e) {}
            })(reflectMethods[j]);
        }

        console.log("[TRACER] y1 methods hooked. 进朋友圈下滑触发...");
    } catch (e) {
        console.log("[TRACER] ERROR: " + e);
    }
});
