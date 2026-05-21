var Log = Java.use("android.util.Log");
function stack3() {
    return Log.getStackTraceString(Java.use("java.lang.Exception").$new())
        .split("\n").slice(1,4).join(" ← ");
}

function hookOne(cf, label, className, methodName, sig) {
    try {
        var cls = cf.use(className);
        var m;
        if (sig === "()") {
            m = cls[methodName].overload();
        } else {
            m = cls[methodName].overload(sig);
        }
        m.implementation = function() {
            var r = this[methodName].apply(this, arguments);
            console.log("[" + label + "] " + className + "." + methodName + " ret=" + r + " " + stack3());
            return r;
        };
        console.log("[OK " + label + "] " + className + "." + methodName);
    } catch(e) {
        console.log("[MISS " + label + "] " + className + "." + methodName + ": " + e);
    }
}

function hookAll(cf, label) {
    hookOne(cf, label, "fl4.o", "Sh", "()");
    hookOne(cf, label, "w1", "E1", "()");
    hookOne(cf, label, "com.tencent.mm.plugin.sns.ui.improve.FindMoreFriendsUI", "l0", "()");
    hookOne(cf, label, "com.tencent.mm.plugin.sns.ui.improve.FriendSnsPreference", "h0", "(int)");
    hookOne(cf, label, "com.tencent.mm.plugin.sns.ui.improve.SnsCommentStorage", "E1", "()");
    // UI 层兜底
    hookOne(cf, label, "com.tencent.mm.plugin.sns.ui.SnsMsgUIWithRelevance", "onResume", "()");
    hookOne(cf, label, "com.tencent.mm.plugin.sns.ui.SnsMsgUIWithAll", "onResume", "()");
}

Java.perform(function() {
    hookAll(Java.classFactory, "default");

    Java.enumerateClassLoaders({
        onMatch: function(loader) {
            var name = loader.toString();
            if (name.indexOf("tinker") !== -1 || name.indexOf("Tinker") !== -1) {
                console.log("[FOUND] " + name);
                try {
                    var cf = Java.ClassFactory.get(loader);
                    hookAll(cf, "tinker");
                } catch(e) {
                    console.log("[ERR] ClassFactory: " + e);
                }
            }
        },
        onComplete: function() {
            console.log("[DONE] loader scan");
        }
    });
});
