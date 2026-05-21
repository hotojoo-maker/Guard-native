Java.perform(function() {
    // 1. Hook FindMoreFriendsUI.L1() — ns.c 的主要写入方
    try {
        var FMF = Java.use("com.tencent.mm.ui.FindMoreFriendsUI");
        FMF.L1.implementation = function() {
            console.log("\n[FMF.L1] ★ ENTER");
            // dump this 字段中可能含 wxid 的 String/List
            var fields = this.getClass().getDeclaredFields();
            for (var i = 0; i < fields.length; i++) {
                var f = fields[i];
                f.setAccessible(true);
                try {
                    var v = f.get(this);
                    if (v !== null && v !== undefined) {
                        var tn = f.getType().getName();
                        if (tn === "java.lang.String" && v.length() > 0 && v.length() < 80) {
                            console.log("[FMF.L1:STR] " + f.getName() + " = " + v);
                        }
                        if (tn === "int" || tn === "long") {
                            if (f.getName().toLowerCase().indexOf("unread") >= 0
                                || f.getName().toLowerCase().indexOf("count") >= 0
                                || f.getName().toLowerCase().indexOf("red") >= 0
                                || v > 0) {
                                console.log("[FMF.L1:INT] " + f.getName() + " = " + v);
                            }
                        }
                    }
                } catch(e2) {}
            }
            // 打印调用栈
            console.log("[FMF.L1:STACK]");
            var stack = Java.use("java.lang.Thread").currentThread().getStackTrace();
            for (var j = 0; j < Math.min(stack.length, 15); j++) {
                console.log("  " + stack[j].toString());
            }
            return this.L1();
        };
        console.log("[TRACE] FindMoreFriendsUI.L1() hooked");
    } catch(e) {
        console.log("[TRACE] FMF.L1 fail: " + e);
    }

    // 2. Hook DiscoverViewFeatureGroup.build() — 分发方
    try {
        var DVF = Java.use("com.tencent.mm.kara.feature.feature.comm.DiscoverViewFeatureGroup");
        DVF.build.implementation = function() {
            console.log("\n[DVF.build] ★ ENTER — 即将读取 ns.c");
            var ns_c = Java.use("ns.c");
            console.log("[DVF.build] ns.c → b=" + ns_c.b.value + " g=" + ns_c.g.value + " e=" + ns_c.e.value);
            return this.build();
        };
        console.log("[TRACE] DiscoverViewFeatureGroup.build() hooked");
    } catch(e) {
        console.log("[TRACE] DVF.build fail: " + e);
    }

    // 3. 轮询 ns.c 变化（抓非预期写入方）
    var ns_c = Java.use("ns.c");
    var last = {b: ns_c.b.value, g: ns_c.g.value, e: ns_c.e.value,
                a: ns_c.a.value, c: ns_c.c.value, d: ns_c.d.value, f: ns_c.f.value};
    console.log("[TRACE:INIT] a="+last.a+" b="+last.b+" c="+last.c+" d="+last.d+" e="+last.e+" f="+last.f+" g="+last.g);
    console.log("[TRACE] 等待 ns.c 变化...切发现页触发红点写入");
});
