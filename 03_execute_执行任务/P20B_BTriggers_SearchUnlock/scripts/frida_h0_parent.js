/**
 * Route A 第二路 — h0 父类数据注入探针
 * 目的：h0 extends PreferenceAdapter → 父类的 add/addAll/insert/notifyDataSetChanged
 *       可能才是真正注入数据的入口
 */

const H0_CLASS = "com.tencent.mm.ui.base.preference.h0";

Java.perform(function () {
    console.log("[h0-parent] Java.perform ready");

    try {
        var h0Cls = Java.use(H0_CLASS);
        var startMs = Date.now();

        // 往上走继承链，找父类
        var sup = h0Cls.class.getSuperclass();
        var chain = [];
        while (sup) {
            chain.push(sup.getName());
            sup = sup.getSuperclass();
        }
        console.log("[h0-parent] h0 inheritance chain: " + chain.join(" → "));

        // 对 h0 及其直接父类，hook 常见数据注入方法
        var targetMethods = [
            "add", "addAll", "insert", "remove", "clear",
            "notifyDataSetChanged", "notifyItemChanged", "notifyItemInserted",
            "notifyItemRangeChanged", "notifyItemRangeInserted", "notifyItemRangeRemoved",
            "setList", "setData", "setItems", "bindData", "updateList",
            "replaceAll", "set"
        ];

        // Hook h0 实例的方法（包括继承的）
        var hooked = 0;
        targetMethods.forEach(function (methodName) {
            try {
                // 尝试 h0 自身的 overload
                var methods = h0Cls.class.getDeclaredMethods();
                for (var i = 0; i < methods.length; i++) {
                    if (methods[i].getName() === methodName) {
                        var m = methods[i];
                        var paramTypes = m.getParameterTypes();
                        var overloadArgs = [];
                        for (var j = 0; j < paramTypes.length; j++) {
                            overloadArgs.push(paramTypes[j].getName());
                        }

                        try {
                            var target;
                            if (overloadArgs.length === 0) {
                                target = h0Cls[methodName];
                            } else {
                                target = h0Cls[methodName].overload.apply(h0Cls[methodName], overloadArgs);
                            }

                            var sig = methodName + "(" + overloadArgs.join(",") + ")";
                            target.implementation = function () {
                                var elapsed = Date.now() - startMs;

                                var argInfo = "";
                                for (var k = 0; k < arguments.length; k++) {
                                    var arg = arguments[k];
                                    if (arg instanceof Java.use("java.util.List")) {
                                        argInfo += "List[size=" + arg.size() + "],";
                                        // 打印前 3 条的类型
                                        if (arg.size() > 0) {
                                            var first = arg.get(0);
                                            if (first !== null && first.getClass) {
                                                argInfo += " elem0=" + first.getClass().getName() + ",";
                                            }
                                        }
                                    } else if (arg instanceof Java.use("java.util.Collection")) {
                                        argInfo += "Coll[size=" + arg.size() + "],";
                                    } else if (arg !== null && typeof arg === "object" && arg.getClass) {
                                        argInfo += arg.getClass().getName() + ",";
                                    } else {
                                        argInfo += String(arg).substring(0, 40) + ",";
                                    }
                                }

                                console.log("[h0-parent:" + elapsed + "ms] " + this.mySig + " args=[" + argInfo + "]");
                                return this._orig.apply(this, arguments);
                            }.bind({ _orig: target, mySig: sig });
                            hooked++;
                        } catch (e2) { /* overload mismatch */ }
                    }
                }
            } catch (e) { /* skip */ }
        });

        console.log("[h0-parent] total hooked=" + hooked + " (h0 declared methods only)");

        // ---- 第二招：Java.choose h0 实例，直接调 toString 看内部状态 ----
        var scanCount = 0;
        var scanInterval = setInterval(function () {
            scanCount++;
            var elapsed = Date.now() - startMs;
            Java.choose(H0_CLASS, {
                onMatch: function (inst) {
                    try {
                        var s = inst.toString();
                        console.log("[h0-parent:choose:" + elapsed + "ms] h0 instance found. toString=" +
                            s.substring(0, 200));
                    } catch (e) {
                        console.log("[h0-parent:choose:" + elapsed + "ms] h0 toString fail: " + e);
                    }
                    return "stop"; // 拿一个就停
                },
                onComplete: function () {}
            });

            if (elapsed > 10000) {
                clearInterval(scanInterval);
                console.log("[h0-parent] scan stopped after 10s");
            }
        }, 500); // 每 500ms 扫一次

        console.log("[h0-parent] scanning h0 instances every 500ms...");
    } catch (e) {
        console.log("[h0-parent] ERROR: " + e);
    }
});
