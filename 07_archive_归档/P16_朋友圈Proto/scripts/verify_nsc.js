Java.perform(function() {
    try {
        var ns_c = Java.use("ns.c");
        console.log("[BEFORE] b=" + ns_c.b.value + " g=" + ns_c.g.value + " e=" + ns_c.e.value);
        ns_c.b.value = false;
        ns_c.g.value = 0;
        console.log("[AFTER]  b=" + ns_c.b.value + " g=" + ns_c.g.value);
    } catch(e) {
        console.log("[ERR] " + e);
    }
});
