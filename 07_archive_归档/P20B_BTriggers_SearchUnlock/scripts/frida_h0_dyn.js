/**
 * Route A 冷启动 — h0 动态签名 hook（用 getDeclaredMethods 拿精确参数类型）
 * 解决硬编码参数类型不匹配导致 hook 静默失败的问题
 */
var H0 = "com.tencent.mm.ui.base.preference.h0";
var startMs = 0;

Java.perform(function () {
    console.log("[h0-dyn] enter");
    var h0 = Java.use(H0);
    startMs = Date.now();

    var methods = h0.class.getDeclaredMethods();
    var hooked = 0;
    var skipped = 0;

    for (var i = 0; i < methods.length; i++) {
        var m = methods[i];
        var name = m.getName();
        var pTypes = m.getParameterTypes();
        var overloadArgs = [];
        for (var j = 0; j < pTypes.length; j++) {
            overloadArgs.push(pTypes[j].getName());
        }

        try {
            var target;
            if (overloadArgs.length === 0) {
                target = h0[name];
            } else {
                target = h0[name].overload.apply(h0[name], overloadArgs);
            }

            // 闭包捕获
            (function (orig, sig) {
                target.implementation = function () {
                    var ms = Date.now() - startMs;
                    var summary = "";
                    for (var k = 0; k < arguments.length; k++) {
                        var a = arguments[k];
                        if (a === null || a === undefined) {
                            summary += "null,";
                        } else if (a.getClass && a.getClass().getName() === "java.util.ArrayList") {
                            var sz = a.size();
                            summary += "AL[" + sz + "],";
                        } else if (typeof a === "object" && a.getClass) {
                            summary += String(a.getClass().getName()).split('.').pop() + ",";
                        } else {
                            summary += String(a).substring(0, 20) + ",";
                        }
                    }
                    console.log("[h0:" + ms + "ms] " + sig + " " + summary);
                    return orig.apply(this, arguments);
                };
            })(target, name + "(" + overloadArgs.join(",") + ")");
            hooked++;
        } catch (e) {
            skipped++;
            console.log("[h0-dyn] SKIP " + name + "(" + overloadArgs.join(",") + "): " + e);
        }
    }

    console.log("[h0-dyn] hooked=" + hooked + " skipped=" + skipped + " total=" + methods.length);
});
