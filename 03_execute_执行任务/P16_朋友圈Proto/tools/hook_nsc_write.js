Java.perform(function() {
    var Log = Java.use("android.util.Log");
    function stack3() {
        return Log.getStackTraceString(Java.use("java.lang.Exception").$new())
            .split("\n").slice(1,4).join(" ← ");
    }

    var ns_c = Java.use("ns.c");
    var field_e = ns_c.class.getDeclaredField("e");
    field_e.setAccessible(true);

    var lastVal = field_e.getInt(null);
    console.log("[WATCH] ns.c.e start=" + lastVal);
    console.log("[WATCH] Polling every 2s... trigger red dots now.");

    function poll() {
        Java.perform(function() {
            var cur = field_e.getInt(null);
            if (cur !== lastVal) {
                console.log("[CHANGE] ns.c.e " + lastVal + " → " + cur + " " + stack3());
                lastVal = cur;
            }
        });
        setTimeout(poll, 2000);
    }
    setTimeout(poll, 2000);
});
