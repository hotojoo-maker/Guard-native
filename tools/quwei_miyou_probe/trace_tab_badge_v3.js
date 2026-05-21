// trace_tab_badge_v3.js
// 策略1: Java.choose 找 w1 实例，读所有字段和方法返回值
// 策略2: hook SharedPreferences.getInt/getBoolean 找正数读取
// 策略3: hook w1 所有 int/boolean 返回方法，带 stack trace

Java.perform(function() {

    // ── 1. Java.choose 找 w1 实例 ────────────────────────────────────
    setTimeout(function() {
        console.log("[CHOOSE] looking for w1 instances...");
        try {
            Java.choose("com.tencent.mm.plugin.sns.storage.w1", {
                onMatch: function(inst) {
                    console.log("[W1-INST] found! E1=" + inst.E1()
                        + "  f=" + inst.f()
                        + "  j2=" + inst.j2());
                    // dump all fields
                    var fields = inst.class.getDeclaredFields();
                    for (var i = 0; i < fields.length; i++) {
                        fields[i].setAccessible(true);
                        var tn = fields[i].getType().getName();
                        if (tn === 'int' || tn === 'long') {
                            try {
                                var v = fields[i].get(inst);
                                console.log("[W1-FIELD] " + fields[i].getName()
                                    + " (" + tn + ") = " + v);
                            } catch(e) {}
                        }
                    }
                },
                onComplete: function() { console.log("[CHOOSE] done"); }
            });
        } catch(e) { console.log("[CHOOSE] error: " + e); }
    }, 8000); // 等8秒微信完全初始化

    // ── 2. hook w1 所有 int 方法，首次调用打 stacktrace ────────────────
    try {
        var w1 = Java.use("com.tencent.mm.plugin.sns.storage.w1");
        var intMethods = ["E1", "f", "j2", "B1", "o2", "t2"];
        intMethods.forEach(function(name) {
            try {
                w1[name].overloads.forEach(function(overload) {
                    overload.implementation = function() {
                        var ret = overload.apply(this, arguments);
                        if (ret > 0) {
                            var stack = Java.use("android.util.Log")
                                .getStackTraceString(
                                    Java.use("java.lang.Exception").$new("trace")
                                );
                            console.log("[W1-CALL] " + name + "=" + ret
                                + "\n" + stack.substring(0, 600));
                        } else {
                            console.log("[W1-CALL] " + name + "=" + ret);
                        }
                        return ret;
                    };
                });
            } catch(e2) { console.log("[W1] hook " + name + " err: " + e2); }
        });
        console.log("[W1] int methods hooked");
    } catch(e) { console.log("[W1] hook error: " + e); }

    // ── 3. SharedPreferences.getInt / getBoolean ──────────────────────
    try {
        var SPImpl = Java.use("android.app.SharedPreferencesImpl");
        SPImpl.getInt.implementation = function(key, def) {
            var ret = this.getInt(key, def);
            if (ret > 0 && ret < 200) {
                console.log("[SP-INT] " + key + "=" + ret);
            }
            return ret;
        };
        SPImpl.getBoolean.implementation = function(key, def) {
            var ret = this.getBoolean(key, def);
            if (ret) console.log("[SP-BOOL] " + key + "=true");
            return ret;
        };
        console.log("[SP] hooked");
    } catch(e) { console.log("[SP] error: " + e); }

    // ── 4. hook ns.c（新帖红点守卫）所有方法 ─────────────────────────
    setTimeout(function() {
        try {
            var nsC = Java.use("ns.c");
            nsC.class.getDeclaredMethods().forEach(function(m) {
                var mname = m.getName();
                nsC[mname].overloads.forEach(function(ov) {
                    try {
                        ov.implementation = function() {
                            var ret = ov.apply(this, arguments);
                            console.log("[NS.C] " + mname + " called, ret=" + ret);
                            return ret;
                        };
                    } catch(e3) {}
                });
            });
            console.log("[NS.C] all methods hooked");
        } catch(e) { console.log("[NS.C] error: " + e); }
    }, 3000);

    console.log("[INIT] done");
});
