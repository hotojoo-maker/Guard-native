/**
 * Route A — h0 冷启动数据注入路径探针 (修正版)
 * 目的：hook h0 所有方法（含继承），看冷启动窗口谁被调用
 */

const H0_CLASS = "com.tencent.mm.ui.base.preference.h0";

Java.perform(function () {
    console.log("[h0-trace] Java.perform ready");

    try {
        var h0Cls = Java.use(H0_CLASS);
        var startMs = Date.now();
        var hooked = 0;

        // 方法列表 — 包含 h0 自己的方法 + 继承的关键方法
        var methods = h0Cls.class.getDeclaredMethods();
        console.log("[h0-trace] h0 declared methods count=" + methods.length);

        methods.forEach(function (m) {
            var name = m.getName();
            var paramTypes = m.getParameterTypes();
            var overloadArgs = [];
            for (var i = 0; i < paramTypes.length; i++) {
                overloadArgs.push(paramTypes[i].getName());
            }
            var sig = name + "(" + overloadArgs.join(",") + ")";

            try {
                var target;
                if (overloadArgs.length === 0) {
                    target = h0Cls[name];
                } else {
                    target = h0Cls[name].overload.apply(h0Cls[name], overloadArgs);
                }

                target.implementation = function () {
                    var elapsed = Date.now() - startMs;
                    var argsInfo = "";
                    for (var k = 0; k < arguments.length; k++) {
                        var arg = arguments[k];
                        if (arg === null || arg === undefined) {
                            argsInfo += "null,";
                        } else if (arg instanceof Java.use("java.util.List")) {
                            argsInfo += "List[size=" + arg.size() + "],";
                            if (arg.size() > 0) {
                                var first = arg.get(0);
                                if (first !== null && first.getClass) {
                                    argsInfo += "elem0=" + first.getClass().getName() + ",";
                                }
                            }
                        } else if (typeof arg === "object" && arg.getClass) {
                            var cn = arg.getClass().getName();
                            argsInfo += cn.substring(cn.lastIndexOf('.')+1) + ",";
                        } else {
                            var s = String(arg);
                            argsInfo += s.substring(0, 50) + ",";
                        }
                    }
                    console.log("[h0:" + elapsed + "ms] " + this.mySig + " " + argsInfo);
                    return this._orig.apply(this, arguments);
                }.bind({ _orig: target, mySig: sig });
                hooked++;
            } catch (e) {
                // overload mismatch, skip
            }
        });

        console.log("[h0-trace] total hooked=" + hooked);
        console.log("[h0-trace] Waiting for calls... navigate to conversation list");
    } catch (e) {
        console.log("[h0-trace] ERROR: " + e);
    }
});
