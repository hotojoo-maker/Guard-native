// probe_foreground_call_v2.js — 前台来电路径探针 Phase 2
// ======================================================
// 目标 1: 枚举 WindowManagerGlobal.addView 真实签名并 hook
// 目标 2: 枚举 oz4.d 全部方法并 hook，找来电 UI 入口
// 运行：frida -U -n com.tencent.mm -l probe_foreground_call_v2.js
// 操作：attach 后让密友打来电话，观察 [FCP2:*] 日志
// ======================================================

"use strict";

Java.perform(function () {
    var TAG = "[FCP2]";

    // ------------------------------------------------------------------
    // 1. 枚举 WindowManagerGlobal.addView 所有重载，打印签名并全 hook
    // ------------------------------------------------------------------
    try {
        var WMG = Java.use("android.view.WindowManagerGlobal");
        var overloads = WMG.addView.overloads;
        console.log(TAG + " WMG.addView has " + overloads.length + " overloads:");
        overloads.forEach(function (m, i) {
            var args = m.argumentTypes.map(function (t) { return t.className; });
            console.log(TAG + "   [" + i + "] " + args.join(", "));
        });

        // Hook every overload to capture view class when a call overlay is added
        overloads.forEach(function (m, i) {
            m.implementation = function () {
                var view = arguments[0];
                var viewCls = view ? view.getClass().getName() : "null";
                var type = -1;
                try {
                    var lp = arguments[1];
                    if (lp) {
                        type = Java.use("android.view.WindowManager$LayoutParams")
                            .cast(lp).type.value;
                    }
                } catch (e) {}
                // 只打印 WeChat 包内的 + type > 1000 的系统窗口
                if (viewCls.indexOf("com.tencent.mm") !== -1 || type > 1000) {
                    console.log(TAG + "[WM.addView:" + i + "] cls=" + viewCls + " type=" + type);
                    // 打印调用堆栈前 8 帧
                    var stack = Java.use("android.util.Log").getStackTraceString(
                        Java.use("java.lang.Exception").$new("wm stack"));
                    stack.split("\n").slice(1, 8).forEach(function (line) {
                        console.log(TAG + "   " + line.trim());
                    });
                }
                return m.apply(this, arguments);
            };
        });
        console.log(TAG + " WMG.addView all overloads hooked");
    } catch (e) {
        console.log(TAG + " WMG.addView fail: " + e);
    }

    // ------------------------------------------------------------------
    // 2. 枚举 oz4.d 所有方法并 hook，找来电 UI 入口
    //    oz4.d = VoIP 通话管理类 (confirmed 2026-05-24)
    // ------------------------------------------------------------------
    try {
        var OZ4D = Java.use("oz4.d");
        var methods = OZ4D.class.getDeclaredMethods();
        console.log(TAG + " oz4.d has " + methods.length + " methods:");

        methods.forEach(function (m) {
            var params = m.getParameterTypes().map(function (p) { return p.getName(); });
            var ret = m.getReturnType().getName();
            console.log(TAG + "   " + m.getName() + "(" + params.join(", ") + ") → " + ret);
        });

        // Hook 所有方法，记录调用时机
        methods.forEach(function (m) {
            try {
                var name = m.getName();
                var methodRef = OZ4D[name];
                if (!methodRef) return;
                methodRef.overloads.forEach(function (overload) {
                    overload.implementation = function () {
                        console.log(TAG + "[oz4.d." + name + "] called"
                            + " args=" + overload.argumentTypes.length);
                        var result = overload.apply(this, arguments);
                        return result;
                    };
                });
            } catch (e) {
                // some methods may fail to hook (constructors, etc.)
            }
        });
        console.log(TAG + " oz4.d all methods hooked");
    } catch (e) {
        console.log(TAG + " oz4.d hook fail: " + e);
    }

    // ------------------------------------------------------------------
    // 3. 追踪 oz4.d.c 和 oz4.d.Te 具体参数
    //    这两个方法在 AudioFocus 路径中确认存在
    // ------------------------------------------------------------------
    ["c", "Te"].forEach(function (methodName) {
        try {
            var OZ4D2 = Java.use("oz4.d");
            OZ4D2[methodName].overloads.forEach(function (m) {
                var argTypes = m.argumentTypes.map(function (t) { return t.className; });
                console.log(TAG + " oz4.d." + methodName + " overload: (" + argTypes.join(", ") + ")");
                m.implementation = function () {
                    console.log(TAG + "[oz4.d." + methodName + "] called args:");
                    for (var i = 0; i < arguments.length; i++) {
                        var arg = arguments[i];
                        var type = arg === null ? "null" : (typeof arg === "object"
                            ? arg.getClass().getName() : typeof arg);
                        console.log(TAG + "   arg[" + i + "] type=" + type + " val=" + arg);
                    }
                    var stack = Java.use("android.util.Log").getStackTraceString(
                        Java.use("java.lang.Exception").$new("oz4 stack"));
                    stack.split("\n").slice(1, 6).forEach(function (line) {
                        console.log(TAG + "   " + line.trim());
                    });
                    return m.apply(this, arguments);
                };
            });
        } catch (e) {
            console.log(TAG + " oz4.d." + methodName + " hook fail: " + e);
        }
    });

    // ------------------------------------------------------------------
    // 4. 追踪 c4.g.b 和 c4.h.b — AudioFocus 调用链上游
    // ------------------------------------------------------------------
    [["c4.g", "b"], ["c4.h", "b"]].forEach(function (pair) {
        var clsName = pair[0], mName = pair[1];
        try {
            var CLS = Java.use(clsName);
            CLS[mName].overloads.forEach(function (m) {
                m.implementation = function () {
                    console.log(TAG + "[" + clsName + "." + mName + "] called");
                    return m.apply(this, arguments);
                };
            });
            console.log(TAG + " " + clsName + "." + mName + " hooked");
        } catch (e) {
            console.log(TAG + " " + clsName + "." + mName + " hook fail: " + e);
        }
    });

    console.log(TAG + " === Phase 2 probes armed. Make a call now. ===");
});
