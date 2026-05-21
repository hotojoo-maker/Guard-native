// find_reddot_field.js v1
// 目标: 在红点亮着时 attach，找实际控制发现 tab 红点的字段
// 用法 (attach 模式，微信已在后台且红点可见):
//   frida -U -n com.tencent.mm -l tools/find_reddot_field.js 2>&1 | Tee-Object -FilePath tools/find_reddot_field.log
//
// 步骤:
//   1. 保持微信在后台（发现 tab 有红点）
//   2. 执行上面命令 attach
//   3. 把手机切到前台显示微信主界面（让 onResume 触发）
//   4. 观察 [FMF] / [LAUNCH] / [TAB] 开头的日志

Java.perform(function () {

    function getStack() {
        try {
            var e = Java.use("java.lang.Exception").$new("T");
            return Java.use("android.util.Log").getStackTraceString(e).substring(0, 800);
        } catch (x) { return ""; }
    }

    // ══════════════════════════════════════════════════════
    // 1. Java.choose 扫 FindMoreFriendsUI 实例
    //    dump 所有 boolean/int 字段 + 调用所有 0-param 返回 bool/int 的方法
    // ══════════════════════════════════════════════════════
    setTimeout(function () {
        console.log("[SCAN] looking for FindMoreFriendsUI instances...");
        try {
            Java.choose("com.tencent.mm.ui.FindMoreFriendsUI", {
                onMatch: function (inst) {
                    console.log("[FMF:INST] found instance: " + inst);
                    var cls = inst.getClass();
                    for (var c = cls; c && c.getName() !== "java.lang.Object"; c = c.getSuperclass()) {
                        var fields = c.getDeclaredFields();
                        for (var i = 0; i < fields.length; i++) {
                            try {
                                fields[i].setAccessible(true);
                                var t = fields[i].getType().getName();
                                var v = fields[i].get(inst);
                                if (t === "boolean" || t === "int" || t === "long") {
                                    if (v !== false && v !== 0) {
                                        console.log("[FMF:FIELD] *** "
                                            + c.getSimpleName() + "." + fields[i].getName()
                                            + " (" + t + ") = " + v + " ← NON-ZERO/TRUE");
                                    } else {
                                        console.log("[FMF:FIELD] "
                                            + c.getSimpleName() + "." + fields[i].getName()
                                            + " (" + t + ") = " + v);
                                    }
                                }
                            } catch (e) {}
                        }
                    }
                    // 调用所有 0-param 返回 bool/int 的方法
                    var methods = cls.getDeclaredMethods();
                    for (var j = 0; j < methods.length; j++) {
                        var m = methods[j];
                        if (m.getParameterTypes().length !== 0) continue;
                        var rt = m.getReturnType().getName();
                        if (rt !== "boolean" && rt !== "int" && rt !== "long") continue;
                        try {
                            m.setAccessible(true);
                            var r = m.invoke(inst);
                            if (r && r !== 0) {
                                console.log("[FMF:METHOD] *** " + m.getName()
                                    + "() = " + r + " ← NON-ZERO/TRUE");
                            }
                        } catch (e) {}
                    }
                },
                onComplete: function () { console.log("[SCAN] FMF scan done"); }
            });
        } catch (e) { console.log("[SCAN] FMF error: " + e); }

        // ── LauncherUI 扫描（tab bar 在这里）──────────────────────────────
        console.log("[SCAN] looking for LauncherUI instances...");
        try {
            Java.choose("com.tencent.mm.ui.LauncherUI", {
                onMatch: function (inst) {
                    console.log("[LAUNCH:INST] found: " + inst);
                    var cls = inst.getClass();
                    for (var c = cls; c && c.getName() !== "java.lang.Object"; c = c.getSuperclass()) {
                        var fields = c.getDeclaredFields();
                        for (var i = 0; i < fields.length; i++) {
                            try {
                                fields[i].setAccessible(true);
                                var t = fields[i].getType().getName();
                                var v = fields[i].get(inst);
                                if ((t === "boolean" || t === "int") && v !== false && v !== 0) {
                                    console.log("[LAUNCH:FIELD] *** "
                                        + c.getSimpleName() + "." + fields[i].getName()
                                        + " (" + t + ") = " + v);
                                }
                            } catch (e) {}
                        }
                    }
                },
                onComplete: function () { console.log("[SCAN] LauncherUI scan done"); }
            });
        } catch (e) { console.log("[SCAN] LauncherUI error: " + e); }
    }, 2000);

    // ══════════════════════════════════════════════════════
    // 2. hook FindMoreFriendsUI.L1() — 看 this.x / this.y 和冷启动值
    //    同时 dump this 所有 boolean/int 字段（找真正控制红点的）
    // ══════════════════════════════════════════════════════
    try {
        var fmf = Java.use("com.tencent.mm.ui.FindMoreFriendsUI");
        fmf.class.getDeclaredMethods().forEach(function (m) {
            if (m.getName() !== "L1") return;
            fmf.L1.overloads.forEach(function (ov) {
                ov.implementation = function () {
                    var ret = ov.apply(this, arguments);

                    // dump 全部 boolean/int 字段 + 值
                    var sb = "[L1:FIELDS AFTER L1]:\n";
                    var cls = this.getClass();
                    for (var c = cls; c && c.getName() !== "java.lang.Object"; c = c.getSuperclass()) {
                        var fields = c.getDeclaredFields();
                        for (var i = 0; i < fields.length; i++) {
                            try {
                                fields[i].setAccessible(true);
                                var t = fields[i].getType().getName();
                                var v = fields[i].get(this);
                                if (t === "boolean" || t === "int" || t === "long") {
                                    var mark = (v !== false && v !== 0) ? " ★" : "";
                                    sb += "  " + c.getSimpleName() + "."
                                        + fields[i].getName() + "=" + v + mark + "\n";
                                }
                            } catch (e) {}
                        }
                    }
                    console.log(sb);

                    // ns.c.b 和 ww2.c.b 当前值
                    try {
                        var nsCB = Java.use("ns.c").b.value;
                        console.log("[L1] ns.c.b=" + nsCB);
                    } catch (e) {}
                    try {
                        var ww2CB = Java.use("ww2.c").b.value;
                        console.log("[L1] ww2.c.b=" + ww2CB);
                    } catch (e) { console.log("[L1] ww2.c.b read err: " + e); }

                    return ret;
                };
            });
        });
        console.log("[FMF] L1 dump hook installed");
    } catch (e) { console.log("[FMF] L1 hook failed: " + e); }

    // ══════════════════════════════════════════════════════
    // 3. 扫 ns.c 全部字段当前值（不只看 b）
    //    ww2.c 同样扫
    // ══════════════════════════════════════════════════════
    setTimeout(function () {
        // ns.c 全字段
        try {
            var nsC = Java.use("ns.c");
            var nsSb = "[ns.c ALL FIELDS]:\n";
            nsC.class.getDeclaredFields().forEach(function (f) {
                try {
                    f.setAccessible(true);
                    nsSb += "  " + f.getName() + " (" + f.getType().getName() + ") = " + f.get(null) + "\n";
                } catch (e) { nsSb += "  " + f.getName() + " = ERR\n"; }
            });
            console.log(nsSb);
        } catch (e) { console.log("[ns.c] dump err: " + e); }

        // ww2.c 全字段
        try {
            var ww2C = Java.use("ww2.c");
            var ww2Sb = "[ww2.c ALL FIELDS]:\n";
            ww2C.class.getDeclaredFields().forEach(function (f) {
                try {
                    f.setAccessible(true);
                    ww2Sb += "  " + f.getName() + " (" + f.getType().getName() + ") = " + f.get(null) + "\n";
                } catch (e) { ww2Sb += "  " + f.getName() + " = ERR\n"; }
            });
            console.log(ww2Sb);
        } catch (e) { console.log("[ww2.c] dump err: " + e); }
    }, 1000);

    // ══════════════════════════════════════════════════════
    // 4. FindMoreFriendsUI.onResume — 红点真实值时刻 dump
    // ══════════════════════════════════════════════════════
    try {
        var fmf3 = Java.use("com.tencent.mm.ui.FindMoreFriendsUI");
        fmf3.class.getMethods().forEach(function (m) {
            if (m.getName() !== "onResume" || m.getParameterTypes().length !== 0) return;
            fmf3.onResume.overload().implementation = function () {
                var ret = this.onResume();

                var sb = "[onResume:FIELDS]:\n";
                var cls = this.getClass();
                for (var c = cls; c && c.getName() !== "java.lang.Object"; c = c.getSuperclass()) {
                    c.getDeclaredFields().forEach(function (f) {
                        try {
                            f.setAccessible(true);
                            var t = f.getType().getName();
                            var v = f.get(this);
                            if ((t === "boolean" || t === "int") && v !== false && v !== 0) {
                                sb += "  ★ " + c.getSimpleName() + "." + f.getName() + "=" + v + "\n";
                            }
                        } catch (e) {}
                    }.bind(this));
                }
                console.log(sb);
                return ret;
            };
        });
        console.log("[FMF] onResume dump hook installed");
    } catch (e) { console.log("[FMF] onResume hook failed: " + e); }

    console.log("[FIND_RD] find_reddot_field v1 installed. 请切回微信主界面触发 onResume");
});
