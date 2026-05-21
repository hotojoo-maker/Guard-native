// trace_d6_methods.js — 枚举 d6 所有方法 + hook k() 看返回类型/值
Java.perform(function() {
    try {
        var d6 = Java.use("com.tencent.mm.plugin.sns.model.d6");
        var methods = d6.class.getDeclaredMethods();
        console.log("[D6] total methods: " + methods.length);
        methods.forEach(function(m) {
            var ptypes = [];
            var params = m.getParameterTypes();
            for (var i = 0; i < params.length; i++) ptypes.push(params[i].getName());
            console.log("[D6-M] " + m.getName()
                + "(" + ptypes.join(", ") + ")"
                + " -> " + m.getReturnType().getName()
                + "");
        });

        // hook k() — 看返回值
        d6.k.overloads.forEach(function(ov) {
            ov.implementation = function() {
                var ret = ov.apply(this, arguments);
                console.log("[D6.k] called, ret=" + ret + " (type=" + (typeof ret) + ")");
                return ret;
            };
        });
        console.log("[D6] k() hooked");

        // hook 所有写方法（返回 void 或 boolean）带写 sns_control_flag 的
        // 先 hook 全部方法，有返回值就打印
        methods.forEach(function(m) {
            var mname = m.getName();
            if (mname === "k") return; // already hooked
            try {
                d6[mname].overloads.forEach(function(ov) {
                    ov.implementation = function() {
                        var ret = ov.apply(this, arguments);
                        console.log("[D6." + mname + "] called, ret=" + ret);
                        return ret;
                    };
                });
            } catch(e2) {}
        });
        console.log("[D6] all methods hooked");
    } catch(e) { console.log("[D6] error: " + e); }
});
