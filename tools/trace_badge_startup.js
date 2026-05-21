// trace_badge_startup.js — 找发现 tab 互动红点的启动读取路径
Java.perform(function() {
    // 扫所有 FindMoreFriendsUI 方法，看启动时谁被调
    var fmf = Java.use("com.tencent.mm.ui.FindMoreFriendsUI");
    fmf.class.getDeclaredMethods().forEach(function(m) {
        var name = m.getName();
        var pt = m.getParameterTypes();
        fmf[name].overloads.forEach(function(overload) {
            try {
                overload.implementation = function() {
                    var ret = overload.apply(this, arguments);
                    if (name !== "L1") {
                        console.log("[FMF] " + name + "(" + pt.length + ") called, ret=" + ret);
                    }
                    return ret;
                };
            } catch(e) {}
        });
    });
    console.log("[FMF] all methods hooked");

    // hook w1 的所有方法
    var w1 = Java.use("com.tencent.mm.plugin.sns.storage.w1");
    w1.class.getDeclaredMethods().forEach(function(m) {
        var name = m.getName();
        w1[name].overloads.forEach(function(overload) {
            try {
                overload.implementation = function() {
                    var ret = overload.apply(this, arguments);
                    console.log("[W1] " + name + " called, ret=" + ret);
                    return ret;
                };
            } catch(e) {}
        });
    });
    console.log("[W1] all methods hooked");
});
