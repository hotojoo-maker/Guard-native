// T09 诊断 #2 — k24.b 真实身份确认
// 直接查这个类的继承链、字段、接口，判断是会话 item 还是朋友圈 item

Java.perform(function() {
    try {
        var k24_b = Java.use("k24.b");
        console.log("[T09] k24.b found");
        console.log("  class: " + k24_b.class.getName());
        console.log("  super: " + k24_b.class.getSuperclass().getName());

        var fields = k24_b.class.getDeclaredFields();
        console.log("  fields (" + fields.length + "):");
        for (var i = 0; i < fields.length; i++) {
            console.log("    [" + i + "] " + fields[i].getType().getName() + " " + fields[i].getName());
        }
    } catch (e) {
        console.log("[T09] k24.b NOT FOUND: " + e);
    }

    // Also check k24.a and k24 itself
    try {
        var k24 = Java.use("k24");
        console.log("[T09] k24 class found");
        var inner = k24.class.getDeclaredClasses();
        console.log("  inner classes (" + inner.length + "):");
        for (var i = 0; i < inner.length; i++) {
            console.log("    " + inner[i].getName());
        }
    } catch (e) {
        console.log("[T09] k24 NOT FOUND: " + e);
    }

    // Also probe known container classes
    var suspects = [
        "com.tencent.mm.plugin.mvvmlist.MvvmList",
        "com.tencent.mm.plugin.timeline.TimelineMvvmList",
        "com.tencent.mm.plugin.sns.SnsMvvmList",
        "com.tencent.mm.plugin.sns.ui.SnsTimeLineUI",
    ];
    suspects.forEach(function(name) {
        try {
            var cls = Java.use(name);
            console.log("[T09] FOUND: " + name);
        } catch (e) {
            // not found, skip
        }
    });
});
