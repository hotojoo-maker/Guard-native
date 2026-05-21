// check_y1.js — 确认新账号下 y1 adapter 是否被创建，以及类名
var Y1 = "com.tencent.mm.plugin.sns.ui.improve.component.y1";

Java.perform(function () {
    console.log("[CHECK] Looking for: " + Y1);
    try {
        var cls = Java.classFactory.loadClass(Y1);
        console.log("[CHECK] y1 CLASS FOUND: " + cls + " loader=" + cls.getClassLoader());
        // Hook constructors
        var ctors = cls.getDeclaredConstructors();
        console.log("[CHECK] y1 has " + ctors.length + " constructors");
        for (var i = 0; i < ctors.length; i++) {
            console.log("[CHECK]   ctor: " + ctors[i]);
        }
    } catch (e) {
        console.log("[CHECK] y1 NOT FOUND: " + e);
    }

    // Also scan loaded classes with similar names
    console.log("[CHECK] Scanning for sns.ui.improve classes...");
    Java.enumerateLoadedClasses({
        onMatch: function (name) {
            if (name.indexOf("sns") >= 0 && name.indexOf("improve") >= 0 && name.indexOf("component") >= 0) {
                console.log("  FOUND: " + name);
            }
        },
        onComplete: function () {
            console.log("[CHECK] Done.");
        }
    });
});
