Java.perform(function() {
    // 检查是否有 ns.c 实例
    var count = 0;
    Java.choose("ns.c", {
        onMatch: function(inst) {
            count++;
            console.log("[INSTANCE#" + count + "] a=" + inst.a.value + " b=" + inst.b.value +
                        " c=" + inst.c.value + " d=" + inst.d.value +
                        " e=" + inst.e.value + " f=" + inst.f.value + " g=" + inst.g.value);
            // 归零
            inst.a.value = false;
            inst.b.value = false;
            inst.c.value = false;
            inst.d.value = false;
            inst.e.value = 0;
            inst.f.value = 0;
            inst.g.value = 0;
            console.log("[INSTANCE#" + count + "] ZEROED");
        },
        onComplete: function() {
            console.log("[DONE] ns.c instances: " + count);
            if (count === 0) {
                console.log("[DONE] No ns.c instances found — fields may be static but unset after restart.");
                console.log("[DONE] Red dots may not be loaded yet. Trigger them first (open Discover tab / get messages).");
            }
        }
    });
});
