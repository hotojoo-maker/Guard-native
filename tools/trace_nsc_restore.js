// trace_nsc_restore.js v1
// 目标: 找 ns.c.b 冷启动被写回 true 的位置
// 用法 (spawn 模式): frida -U -f com.tencent.mm --no-pause -l tools/trace_nsc_restore.js 2>&1 | tee tools/trace_nsc_restore.log
//
// 修复 trace_tab_badge_v3 的三个缺陷:
//   1. ns.c hook 无 setTimeout，立即挂（v3 用 3s 延时，写回在前 3s 内）
//   2. SP hook 过滤 prefs 来源，排除自身模块 ncl_cfg 的干扰
//   3. 新增 MMKV 探针（微信用 MMKV，不走 Android SharedPrefs）

Java.perform(function () {

    function getStack() {
        try {
            var e = Java.use("java.lang.Exception").$new("TRACE");
            return Java.use("android.util.Log").getStackTraceString(e).substring(0, 1200);
        } catch (x) { return "(stack err: " + x + ")"; }
    }

    // ══════════════════════════════════════════════════════
    // 1. 立即 hook ns.c 全部方法（无延时，v3 的致命缺陷在这里）
    // ══════════════════════════════════════════════════════
    var nsLoaded = false;
    function tryHookNsC() {
        if (nsLoaded) return true;
        try {
            var nsC = Java.use("ns.c");
            var init_b = nsC.b.value;
            console.log("[NRC] ns.c loaded OK. initial b=" + init_b);
            nsLoaded = true;

            nsC.class.getDeclaredMethods().forEach(function (m) {
                var mn = m.getName();
                try {
                    nsC[mn].overloads.forEach(function (ov) {
                        ov.implementation = function () {
                            var bBefore = nsC.b.value;
                            var ret = ov.apply(this, arguments);
                            var bAfter = nsC.b.value;
                            if (bAfter !== bBefore) {
                                console.log("[NRC:nsC." + mn + "] b: " + bBefore + " -> " + bAfter);
                                console.log("[NRC:nsC." + mn + "] STACK:\n" + getStack());
                            } else {
                                console.log("[NRC:nsC." + mn + "] called (b=" + bAfter + ")");
                            }
                            return ret;
                        };
                    });
                } catch (e) { console.log("[NRC] ns.c." + mn + " hook err: " + e); }
            });

            var mc = nsC.class.getDeclaredMethods().length;
            console.log("[NRC] ns.c hooked " + mc + " methods");
            return true;
        } catch (e) {
            return false;
        }
    }

    // 先立即尝试，若类尚未加载则 200ms 轮询重试（最多 30 次 = 6s）
    if (!tryHookNsC()) {
        var retryCount = 0;
        var retryTimer = setInterval(function () {
            retryCount++;
            if (tryHookNsC() || retryCount >= 30) {
                clearInterval(retryTimer);
                if (!nsLoaded) console.log("[NRC] ns.c never loaded in 6s");
            }
        }, 200);
    }

    // ══════════════════════════════════════════════════════
    // 2. 200ms 轮询检测 ns.c.b 变化（前 60s 内）
    // ══════════════════════════════════════════════════════
    var lastB = false;
    var tStart = Date.now();
    var pollTimer = setInterval(function () {
        try {
            var cur = Java.use("ns.c").b.value;
            if (cur !== lastB) {
                var elapsed = Date.now() - tStart;
                console.log("\n[NRC:POLL] *** ns.c.b: " + lastB + " -> " + cur
                    + " @ t=" + elapsed + "ms ***");
                // 轮询本身拿不到写入方的栈，但时序信息很有价值
                lastB = cur;
            }
        } catch (e) {}
        if (Date.now() - tStart > 60000) clearInterval(pollTimer);
    }, 200);

    // ══════════════════════════════════════════════════════
    // 3. FindMoreFriendsUI.L1() 精准字段 dump
    //    关键: this.x (newer snsobj wxid) + this.y (interaction count)
    //    目标: 看冷启动 L1() 调用时 this.x/y 是什么值，ns.c.b 如何变化
    // ══════════════════════════════════════════════════════
    try {
        var fmf = Java.use("com.tencent.mm.ui.FindMoreFriendsUI");
        var fmfMethods = fmf.class.getDeclaredMethods();
        var l1Count = 0;
        fmfMethods.forEach(function (m) {
            if (m.getName() !== "L1") return;
            try {
                fmf.L1.overloads.forEach(function (ov) {
                    ov.implementation = function () {
                        var nsBefore = false;
                        try { nsBefore = Java.use("ns.c").b.value; } catch (x) {}
                        var ret = ov.apply(this, arguments);
                        var nsAfter = false;
                        try { nsAfter = Java.use("ns.c").b.value; } catch (x) {}

                        var fX = "?", fY = "?";
                        try {
                            var fx = this.getClass().getDeclaredField("x");
                            fx.setAccessible(true); fX = "" + fx.get(this);
                        } catch (x) {}
                        try {
                            var fy = this.getClass().getDeclaredField("y");
                            fy.setAccessible(true); fY = "" + fy.get(this);
                        } catch (x) {}

                        console.log("[L1] ns.c.b: " + nsBefore + "->" + nsAfter
                            + " | this.x=" + fX + " | this.y=" + fY);

                        if (nsAfter && !nsBefore) {
                            console.log("[L1] *** b became TRUE inside L1! ***\nSTACK:\n" + getStack());
                        }
                        return ret;
                    };
                });
                l1Count++;
            } catch (e) { console.log("[FMF] L1 overload hook err: " + e); }
        });
        console.log("[FMF] L1 hooked (" + l1Count + " overload(s)), total methods=" + fmfMethods.length);
    } catch (e) { console.log("[FMF] L1 hook failed: " + e); }

    // ══════════════════════════════════════════════════════
    // 4. FindMoreFriendsUI 所有 0-param 方法（找冷启动初始化链）
    //    只在 ns.c.b 发生变化时打印，避免刷屏
    // ══════════════════════════════════════════════════════
    try {
        var fmf2 = Java.use("com.tencent.mm.ui.FindMoreFriendsUI");
        var hookedCnt = 0;
        fmf2.class.getDeclaredMethods().forEach(function (m) {
            if (m.getName() === "L1") return; // 上面已 hook
            if (m.getParameterTypes().length > 0) return;
            var mn = m.getName();
            try {
                fmf2[mn].overloads.forEach(function (ov) {
                    ov.implementation = function () {
                        var nsBefore = false;
                        try { nsBefore = Java.use("ns.c").b.value; } catch (x) {}
                        var ret = ov.apply(this, arguments);
                        var nsAfter = false;
                        try { nsAfter = Java.use("ns.c").b.value; } catch (x) {}
                        if (nsAfter !== nsBefore) {
                            console.log("[FMF:" + mn + "0] b: " + nsBefore + "->" + nsAfter
                                + "\nSTACK:\n" + getStack());
                        }
                        return ret;
                    };
                });
                hookedCnt++;
            } catch (e) {}
        });
        console.log("[FMF] " + hookedCnt + " 0-param methods hooked");
    } catch (e) { console.log("[FMF] 0-param hook failed: " + e); }

    // ══════════════════════════════════════════════════════
    // 5. MMKV 探针（微信用 MMKV，不走 Android SharedPrefs）
    //    hook MMKV.decodeBool，找 ns/badge/sns_new 相关 key
    // ══════════════════════════════════════════════════════
    try {
        var MMKV = Java.use("com.tencent.mmkv.MMKV");
        MMKV.decodeBool.overload("java.lang.String", "boolean").implementation = function (key, def) {
            var ret = this.decodeBool(key, def);
            // 只打印返回 true 且 key 含 sns/badge/tab/red/new 的
            if (ret) {
                var lk = key.toLowerCase();
                if (lk.indexOf("sns") >= 0 || lk.indexOf("badge") >= 0
                        || lk.indexOf("red") >= 0 || lk.indexOf("new") >= 0
                        || lk.indexOf("tab") >= 0 || lk.indexOf("notify") >= 0) {
                    console.log("[MMKV:bool] KEY=" + key + " ret=true\nSTACK:\n" + getStack());
                }
            }
            return ret;
        };
        console.log("[MMKV] decodeBool hooked");
    } catch (e) { console.log("[MMKV] hook failed (may not exist): " + e); }

    // ══════════════════════════════════════════════════════
    // 6. SharedPrefs getBoolean — 过滤掉我们自己的 ncl_cfg
    //    v3 的问题: 把我们模块的 mrd/b2/b5 也打出来了（干扰）
    // ══════════════════════════════════════════════════════
    try {
        var SPImpl = Java.use("android.app.SharedPreferencesImpl");
        SPImpl.getBoolean.implementation = function (key, def) {
            var ret = this.getBoolean(key, def);
            if (ret) {
                // 过滤掉已知的我们自己的 key（ncl_cfg: mrd/b2/b5/kl/ov/ld/enable/auto_init）
                var ownKeys = ["mrd","b2","b5","kl","ov","ld","enable","auto_init"];
                var isOwn = false;
                for (var i = 0; i < ownKeys.length; i++) {
                    if (key === ownKeys[i]) { isOwn = true; break; }
                }
                if (!isOwn) {
                    var lk = key.toLowerCase();
                    if (lk.indexOf("sns") >= 0 || lk.indexOf("badge") >= 0
                            || lk.indexOf("red") >= 0 || lk.indexOf("new") >= 0
                            || lk.indexOf("tab") >= 0 || lk.indexOf("notify") >= 0) {
                        console.log("[SP:bool] KEY=" + key + " =true\nSTACK:\n" + getStack());
                    }
                }
            }
            return ret;
        };
        console.log("[SP] getBoolean hooked (with ncl_cfg filter)");
    } catch (e) { console.log("[SP] getBoolean hook failed: " + e); }

    // ══════════════════════════════════════════════════════
    // 7. ww2.c（ns.c 的镜像字段）同步监控
    // ══════════════════════════════════════════════════════
    try {
        var ww2C = Java.use("ww2.c");
        console.log("[NRC] ww2.c initial b=" + ww2C.b.value);
        ww2C.class.getDeclaredMethods().forEach(function (m) {
            var mn = m.getName();
            try {
                ww2C[mn].overloads.forEach(function (ov) {
                    ov.implementation = function () {
                        var bBefore = ww2C.b.value;
                        var ret = ov.apply(this, arguments);
                        var bAfter = ww2C.b.value;
                        if (bAfter !== bBefore) {
                            console.log("[NRC:ww2C." + mn + "] b: " + bBefore + "->" + bAfter);
                        }
                        return ret;
                    };
                });
            } catch (e) {}
        });
        console.log("[NRC] ww2.c hooked");
    } catch (e) { console.log("[NRC] ww2.c hook failed: " + e); }

    console.log("[NRC] === trace_nsc_restore v1 all probes installed ===");
    console.log("[NRC] 请冷启动微信，观察 ns.c.b 变化和 L1() 调用情况");
});
