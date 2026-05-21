/**
 * trace_catfish_reddot.js
 * 目标：在 Catfish(8.0.70) 活跃时，追踪 FindMoreFriendsUI 上的所有方法调用
 *       找出 Catfish 是怎么让发现 tab 红点消失的
 *
 * 用法：
 *   1. 确保 Catfish 模块已激活（com.tencent.mm 进程已注入 Catfish）
 *   2. frida -U -n com.tencent.mm -l tools/trace_catfish_reddot.js
 *   3. 进微信主界面，让红点亮着，观察日志
 *   4. 进"发现"页，或者等红点消失，观察哪条日志对应了红点消失
 */

Java.perform(function () {

    var TAG = "[CatfishRDT]";

    // ── 1. Hook FindMoreFriendsUI 所有方法 ────────────────────────────────
    try {
        var FMF = Java.use("com.tencent.mm.ui.FindMoreFriendsUI");
        var methods = FMF.class.getDeclaredMethods();
        console.log(TAG + " FMF total declared methods: " + methods.length);

        methods.forEach(function (m) {
            var name = m.getName();
            var params = m.getParameterTypes().map(function (p) { return p.getName(); });
            var retType = m.getReturnType().getName();

            try {
                var overloads = FMF[name].overloads;
                overloads.forEach(function (overload) {
                    overload.implementation = function () {
                        var args = Array.prototype.slice.call(arguments);
                        var argStr = args.map(function (a) { return a; }).join(", ");
                        console.log(TAG + " FMF." + name + "(" + argStr + ") → " + retType);
                        var ret = overload.apply(this, arguments);
                        if (ret !== undefined) console.log(TAG + "   └─ return " + ret);
                        return ret;
                    };
                });
            } catch (e) {
                // 某些方法 hook 失败（native/abstract），忽略
            }
        });
    } catch (e) {
        console.log(TAG + " FMF hook failed: " + e);
    }

    // ── 2. 监控 FMF 字段 E 的变化（直接 trace setter）────────────────────
    // 8.0.71 实证：FMF.E 是红点状态字段，Catfish 可能通过写 E=false 清红点
    try {
        var FMF2 = Java.use("com.tencent.mm.ui.FindMoreFriendsUI");
        // 用 defineProperty 无法对 Java field 生效，改为轮询
        console.log(TAG + " Starting FMF.E field poll (1s interval)...");
        var lastE = null;
        setInterval(function () {
            try {
                Java.choose("com.tencent.mm.ui.FindMoreFriendsUI", {
                    onMatch: function (inst) {
                        var eVal = inst.E.value;
                        if (eVal !== lastE) {
                            console.log(TAG + " FMF.E changed: " + lastE + " → " + eVal
                                + "  (x=" + inst.x.value + " y=" + inst.y.value + ")");
                            lastE = eVal;
                        }
                    },
                    onComplete: function () {}
                });
            } catch (e2) {}
        }, 1000);
    } catch (e) {
        console.log(TAG + " FMF.E poll failed: " + e);
    }

    // ── 3. Hook ns.c 所有方法（如果 8.0.70 ns.c 存在） ───────────────────
    try {
        var NsC = Java.use("ns.c");
        console.log(TAG + " ns.c found, hooking...");
        // 监控 b 字段变化（静态字段，轮询）
        var lastB = null;
        setInterval(function () {
            try {
                var bVal = NsC.b.value;
                if (bVal !== lastB) {
                    console.log(TAG + " ns.c.b changed: " + lastB + " → " + bVal);
                    lastB = bVal;
                    // 打印调用栈
                    console.log(TAG + "   stack: " + Java.use("android.util.Log")
                        .getStackTraceString(Java.use("java.lang.Exception").$new()));
                }
            } catch (e2) {}
        }, 200);
    } catch (e) {
        console.log(TAG + " ns.c not found (8.0.70 may use different name): " + e);
    }

    // ── 4. Hook AbstractTabChildPreference 父类写 m/p 字段的方法 ──────────
    // 找 FMF 的所有父类，hook 其中写 boolean 字段的方法
    try {
        var FMF3 = Java.use("com.tencent.mm.ui.FindMoreFriendsUI");
        var superClass = FMF3.class.getSuperclass();
        while (superClass !== null && !superClass.getName().equals("java.lang.Object")) {
            var scName = superClass.getName();
            console.log(TAG + " FMF superclass: " + scName);
            try {
                var SC = Java.use(scName);
                var scMethods = superClass.getDeclaredMethods();
                scMethods.forEach(function (m) {
                    var mname = m.getName();
                    var params = m.getParameterTypes();
                    // 重点关注带 boolean 参数的方法（可能是 badge setter）
                    var hasBool = false;
                    for (var i = 0; i < params.length; i++) {
                        if (params[i].getName() === "boolean") { hasBool = true; break; }
                    }
                    if (!hasBool) return;
                    try {
                        SC[mname].overloads.forEach(function (ov) {
                            ov.implementation = function () {
                                var args = Array.prototype.slice.call(arguments);
                                console.log(TAG + " " + scName + "." + mname
                                    + "(" + args.join(", ") + ")");
                                return ov.apply(this, arguments);
                            };
                        });
                    } catch (e2) {}
                });
            } catch (e2) {}
            superClass = superClass.getSuperclass();
        }
    } catch (e) {
        console.log(TAG + " superclass scan failed: " + e);
    }

    console.log(TAG + " Ready. 现在进微信，让发现tab红点亮起，然后观察哪个方法调用对应红点消失。");
});
