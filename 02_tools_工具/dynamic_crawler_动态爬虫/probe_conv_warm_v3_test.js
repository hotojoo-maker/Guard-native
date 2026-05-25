/**
 * Q1+Q2 probe v2 — 用直接调用判断 static/instance
 */
var TAG = "[Q]";
var TARGET = "wxid_lzd2va16jd1622";
var started = Date.now();
function ts() { return ((Date.now()-started)/1000).toFixed(2)+"s"; }

setTimeout(function() {
    Java.perform(function() {
        console.log(TAG + " @" + ts());

        // ── Q1: try static call first ──
        try {
            var kc5x = Java.use("kc5.x");
            var y = kc5x.h(TARGET);
            console.log(TAG + " Q1: static call OK -> " + (y != null ? y.getClass().getName() : "null"));
            console.log(TAG + " Q1: h() is STATIC");
        } catch(e) {
            console.log(TAG + " Q1: static call failed — h() is INSTANCE method");
            console.log(TAG + " Q1: " + String(e).substring(0, 100));
        }

        // ── Q2: instance call on main thread ──
        Java.scheduleOnMainThread(function() {
            console.log(TAG + " Q2: main thread=" +
                Java.use("java.lang.Thread").currentThread().getName() + " @" + ts());
            Java.choose("kc5.x", {
                onMatch: function(inst) {
                    try {
                        var y = inst.h(TARGET);
                        console.log(TAG + " Q2: inst.h() on main -> " +
                            (y != null ? y.getClass().getName() : "null"));
                    } catch(e2) {
                        console.log(TAG + " Q2: CRASH: " + e2);
                    }
                },
                onComplete: function() { console.log(TAG + " Q2: done @" + ts()); }
            });
        });
        console.log(TAG + " Q2: scheduled @" + ts());
    });
}, 1000);
