Java.perform(function() {
    try {
        var ns_c = Java.use("ns.c");
        var badge = ns_c.b.value;
        var g = ns_c.g.value;
        var e = ns_c.e.value;
        console.log("[BEFORE] b(badge)=" + badge + " g(moments)=" + g + " e(tab)=" + e);
        ns_c.b.value = false;
        ns_c.g.value = 0;
        console.log("[AFTER]  b=" + ns_c.b.value + " g=" + ns_c.g.value);
    } catch(e) {
        console.log("[ERR] " + e);
    }
});
