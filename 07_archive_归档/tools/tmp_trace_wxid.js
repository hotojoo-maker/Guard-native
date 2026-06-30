Java.perform(function () {
    var TAG = "[WXIDTRACE]";

    // 工具：截断长字符串
    function shortStr(s) {
        if (!s) return "null";
        var str = s.toString();
        if (str.length > 60) return str.substring(0, 60) + "...";
        return str;
    }

    // ── kc5.v0 — 所有方法，特别关注 String 参数 ────────────────────────────
    try {
        var Kc5v0 = Java.use("kc5.v0");
        var methods = Kc5v0.class.getDeclaredMethods();
        var hooked = 0;
        for (var mi = 0; mi < methods.length; mi++) {
            var m = methods[mi];
            var mn = m.getName();
            var pt = m.getParameterTypes();
            if (pt.length > 3) continue;  // 跳过参数太多的
            (function(methodName, paramTypes) {
                try {
                    Kc5v0[methodName].overloads.forEach(function(ov) {
                        if (ov.argumentTypes.length !== paramTypes.length) return;
                        ov.implementation = function() {
                            var parts = [];
                            for (var ai = 0; ai < arguments.length; ai++) {
                                var arg = arguments[ai];
                                try {
                                    if (arg === null || arg === undefined) {
                                        parts.push("null");
                                    } else if (arg.getClass && arg.getClass().getName() === "java.lang.String") {
                                        parts.push('"' + shortStr(arg) + '"');
                                    } else {
                                        parts.push(arg.$className || arg.getClass().getName());
                                    }
                                } catch(e) {
                                    parts.push("?");
                                }
                            }
                            var sig = "kc5.v0." + methodName + "(" + parts.join(", ") + ")";
                            console.log(TAG + " " + sig);
                            return ov.apply(this, arguments);
                        };
                        hooked++;
                    });
                } catch(e) {}
            })(mn, pt);
        }
        console.log("[INFO] kc5.v0 methods hooked: " + hooked);
    } catch(e) {
        console.log("[INFO] kc5.v0 FAIL: " + e);
    }

    // ── MvvmConvList — 所有方法 ──────────────────────────────────────────────
    try {
        var MCL = Java.use("com.tencent.mm.ui.conversation.adapter.MvvmConvList");
        var mclMethods = MCL.class.getDeclaredMethods();
        var mclHooked = 0;
        for (var mi = 0; mi < mclMethods.length; mi++) {
            var m = mclMethods[mi];
            var mn = m.getName();
            var pt = m.getParameterTypes();
            if (pt.length > 3) continue;
            (function(methodName, paramTypes) {
                try {
                    MCL[methodName].overloads.forEach(function(ov) {
                        if (ov.argumentTypes.length !== paramTypes.length) return;
                        ov.implementation = function() {
                            var parts = [];
                            for (var ai = 0; ai < arguments.length; ai++) {
                                var arg = arguments[ai];
                                try {
                                    if (arg === null || arg === undefined) {
                                        parts.push("null");
                                    } else if (arg.getClass && arg.getClass().getName() === "java.lang.String") {
                                        parts.push('"' + shortStr(arg) + '"');
                                    } else {
                                        parts.push(arg.$className || arg.getClass().getName());
                                    }
                                } catch(e) { parts.push("?"); }
                            }
                            console.log("[MCL] " + methodName + "(" + parts.join(", ") + ")");
                            return ov.apply(this, arguments);
                        };
                        mclHooked++;
                    });
                } catch(e) {}
            })(mn, pt);
        }
        console.log("[INFO] MvvmConvList methods hooked: " + mclHooked);
    } catch(e) {
        console.log("[INFO] MvvmConvList FAIL: " + e);
    }

    // ── notifyDataSetChanged on kc5.v0 ────────────────────────────────────────
    try {
        var Kc5v0b = Java.use("kc5.v0");
        var ndsMethods = Kc5v0b.class.getMethods();
        for (var ni = 0; ni < ndsMethods.length; ni++) {
            if (ndsMethods[ni].getName() === "notifyDataSetChanged" &&
                ndsMethods[ni].getParameterTypes().length === 0) {
                Kc5v0b.notifyDataSetChanged.overload().implementation = function() {
                    console.log("[NOTIFY] kc5.v0.notifyDataSetChanged()");
                    return this.notifyDataSetChanged();
                };
                console.log("[INFO] notifyDataSetChanged hooked");
                break;
            }
        }
    } catch(e) {}

    console.log("[INFO] === tmp_trace_wxid.js 就绪 ===");
    console.log("[INFO] 现在发消息给密友，观察 kc5.v0 上哪些方法被调用，参数是什么");
});
