Java.perform(function() {
    try {
        var ns_c = Java.use("ns.c");
        console.log("[BEFORE] a=" + ns_c.a.value + " b=" + ns_c.b.value +
                    " c=" + ns_c.c.value + " d=" + ns_c.d.value +
                    " e=" + ns_c.e.value + " f=" + ns_c.f.value +
                    " g=" + ns_c.g.value);
        // 归零所有 boolean 和 int
        ns_c.a.value = false;
        ns_c.b.value = false;
        ns_c.c.value = false;
        ns_c.d.value = false;
        ns_c.e.value = 0;
        ns_c.f.value = 0;
        ns_c.g.value = 0;
        console.log("[AFTER] a=" + ns_c.a.value + " b=" + ns_c.b.value +
                    " c=" + ns_c.c.value + " d=" + ns_c.d.value +
                    " e=" + ns_c.e.value + " f=" + ns_c.f.value +
                    " g=" + ns_c.g.value);
        console.log("[ZERO] Done. Check Discover tab.");
    } catch(e) {
        console.log("[ERR] " + e);
    }
});
