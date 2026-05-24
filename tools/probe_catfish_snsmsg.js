// probe_catfish_snsmsg.js — 追 Catfish hookSnsMsgList 在 8.0.71 的等价织入点
// Catfish: MainEntry.hookSnsMsgList() → addBlackList2(ArrayList) 并入密友 wxid
// 用法: frida -U -p <PID> -l tools/probe_catfish_snsmsg.js
// 操作: 进发现→朋友圈→点「x条新消息」→ 看 [CATFISH] 输出

"use strict";

function stack3() {
    return Java.use("android.util.Log")
        .getStackTraceString(Java.use("java.lang.Exception").$new())
        .split("\n").slice(1, 5).join(" ← ");
}

function scanArrayListHooks() {
    var targets = [
        "com.tencent.mm.plugin.sns.storage.w1",
        "com.tencent.mm.plugin.sns.ui.SnsMsgUI",
        "com.tencent.mm.plugin.sns.ui.SnsMsgUIWithRelevance",
        "com.tencent.mm.plugin.sns.ui.SnsMsgUIWithAll",
    ];
    targets.forEach(function(cn) {
        try {
            var C = Java.use(cn);
            var ms = C.class.getDeclaredMethods();
            for (var i = 0; i < ms.length; i++) {
                var m = ms[i];
                var pts = m.getParameterTypes();
                if (pts.length !== 1) continue;
                var pt = pts[0].getName();
                if (pt.indexOf("ArrayList") === -1 && pt.indexOf("List") === -1) continue;
                (function(mname, sig) {
                    try {
                        C[mname].overload(sig).implementation = function(arg) {
                            var sz = arg ? arg.size() : -1;
                            console.log("[CATFISH] " + cn + "." + mname + " in sz=" + sz + " " + stack3());
                            var r = this[mname](arg);
                            if (r && r.size) console.log("[CATFISH]   ret sz=" + r.size());
                            return r;
                        };
                        console.log("[OK] " + cn + "." + mname + sig);
                    } catch(e) {}
                })(m.getName(), pt);
            }
        } catch(e) {
            console.log("[MISS] " + cn + ": " + e);
        }
    });
}

Java.perform(function() {
    console.log("[CATFISH] probe start — 追 hookSnsMsgList 等价 ArrayList 入口");
    scanArrayListHooks();

    // SnsMsgUI 生命周期
    ["onCreate", "onResume"].forEach(function(mn) {
        try {
            var Base = Java.use("com.tencent.mm.plugin.sns.ui.SnsMsgUI");
            Base[mn].overload("android.os.Bundle").implementation = function(b) {
                console.log("[CATFISH] SnsMsgUI." + mn + " class=" + this.getClass().getName());
                return this[mn](b);
            };
            console.log("[OK] SnsMsgUI." + mn);
        } catch(e1) {
            try {
                var Base2 = Java.use("com.tencent.mm.plugin.sns.ui.SnsMsgUI");
                Base2[mn].implementation = function() {
                    console.log("[CATFISH] SnsMsgUI." + mn + " class=" + this.getClass().getName());
                    return this[mn]();
                };
                console.log("[OK] SnsMsgUI." + mn + "()");
            } catch(e2) {
                console.log("[MISS] SnsMsgUI." + mn + ": " + e2);
            }
        }
    });

    // adapter bm / rm getItem
    ["com.tencent.mm.plugin.sns.ui.bm", "com.tencent.mm.plugin.sns.ui.rm"].forEach(function(cn) {
        try {
            var Ad = Java.use(cn);
            Ad.getItem.overload("int").implementation = function(i) {
                var r = this.getItem(i);
                if (r) console.log("[CATFISH] " + cn + ".getItem(" + i + ") → " + r.getClass().getName());
                return r;
            };
            console.log("[OK] " + cn + ".getItem");
        } catch(e) {
            console.log("[MISS] " + cn + ": " + e);
        }
    });

    console.log("[CATFISH] ready — 点「x条新消息」进互动列表");
});
