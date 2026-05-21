Java.perform(function() {
    var Log = Java.use("android.util.Log");
    function stack3() {
        return Log.getStackTraceString(Java.use("java.lang.Exception").$new())
            .split("\n").slice(1,4).join(" ← ");
    }

    // 在 Tinker ClassLoader 里找类
    var tinkerFactory = null;
    Java.enumerateClassLoadersSync().forEach(function(loader) {
        try {
            var cls = loader.loadClass("com.tencent.mm.plugin.sns.ui.improve.w1");
            if (cls != null) {
                console.log("[TINKER] found loader: " + loader);
                tinkerFactory = Java.ClassFactory.get(loader);
            }
        } catch(e) {}
    });

    if (tinkerFactory == null) {
        console.log("[TINKER] NOT FOUND, falling back to default");
        tinkerFactory = Java;
    }

    // fl4.o.Sh — 更新小红点
    try {
        var fl4_o = tinkerFactory.use("fl4.o");
        fl4_o.Sh.implementation = function() {
            var r = this.Sh();
            console.log("[fl4.o.Sh] ret=" + r + " " + stack3());
            return r;
        };
        console.log("[OK] fl4.o.Sh");
    } catch(e) { console.log("[MISS] fl4.o.Sh: " + e); }

    // w1.E1 — 朋友圈消息未读数
    try {
        var w1 = tinkerFactory.use("com.tencent.mm.plugin.sns.ui.improve.w1");
        w1.E1.implementation = function() {
            var r = this.E1();
            console.log("[w1.E1] getNotifyCount=" + r + " " + stack3());
            return r;
        };
        console.log("[OK] w1.E1");
    } catch(e) { console.log("[MISS] w1.E1: " + e); }

    // FindMoreFriendsUI.l0
    try {
        var fmf = tinkerFactory.use("com.tencent.mm.plugin.sns.ui.improve.FindMoreFriendsUI");
        fmf.l0.implementation = function() {
            console.log("[FindMoreFriendsUI.l0] called " + stack3());
            this.l0();
        };
        console.log("[OK] FindMoreFriendsUI.l0");
    } catch(e) { console.log("[MISS] FindMoreFriendsUI.l0: " + e); }

    // FriendSnsPreference.h0
    try {
        var fsp = tinkerFactory.use("com.tencent.mm.plugin.sns.ui.improve.FriendSnsPreference");
        fsp.h0.implementation = function(x) {
            console.log("[FriendSnsPreference.h0] arg=" + x + " " + stack3());
            this.h0(x);
        };
        console.log("[OK] FriendSnsPreference.h0");
    } catch(e) { console.log("[MISS] FriendSnsPreference.h0: " + e); }
});
