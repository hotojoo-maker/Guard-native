// probe_unread_hook.js — hook MainEntry.showUnReadMsgCount 找调用栈
// attach: frida -D 609b4b18 -p <pid> -l probe_unread_hook.js

Java.perform(function() {
    // ── 1. hook MainEntry.showUnReadMsgCount ───────────────────────
    try {
        var ME = Java.use("com.catfish.newvip.MainEntry");
        ME.showUnReadMsgCount.overloads.forEach(function(ov) {
            ov.implementation = function() {
                var ret = ov.apply(this, arguments);
                console.log("[ME.showUnReadMsgCount] args=("
                    + Array.prototype.slice.call(arguments).join(",")
                    + ") ret=" + ret);
                return ret;
            };
        });
        console.log("[ME] showUnReadMsgCount hooked");
    } catch(e) { console.log("[ME] " + e); }

    // ── 2. hook UserControll.showUnReadMsgCount ────────────────────
    try {
        var UC = Java.use("com.catfish.newvip.core.UserControll");
        UC.showUnReadMsgCount.overloads.forEach(function(ov) {
            ov.implementation = function() {
                var ret = ov.apply(this, arguments);
                console.log("[UC.showUnReadMsgCount] args=("
                    + Array.prototype.slice.call(arguments).join(",")
                    + ") ret=" + ret);
                return ret;
            };
        });
        console.log("[UC] showUnReadMsgCount hooked");
    } catch(e) { console.log("[UC] " + e); }

    // ── 3. hook VipPreference.isShowUnReadMsg ──────────────────────
    try {
        var VP = Java.use("com.catfish.newvip.preference.VipPreference");
        VP.isShowUnReadMsg.overloads.forEach(function(ov) {
            ov.implementation = function() {
                var ret = ov.apply(this, arguments);
                console.log("[VP.isShowUnReadMsg] = " + ret);
                return ret;
            };
        });
        console.log("[VP] isShowUnReadMsg hooked");
    } catch(e) { console.log("[VP] " + e); }

    // ── 4. NativeHelper.getShowUnReadMsg ──────────────────────────
    try {
        var NH = Java.use("com.catfish.newvip.util.NativeHelper");
        NH.getShowUnReadMsg.overloads.forEach(function(ov) {
            ov.implementation = function() {
                var ret = ov.apply(this, arguments);
                console.log("[NH.getShowUnReadMsg] = " + ret);
                return ret;
            };
        });
        console.log("[NH] getShowUnReadMsg hooked");
    } catch(e) { console.log("[NH] " + e); }

    console.log("[INIT] all hooks ready — 进入朋友圈触发互动通知即可");
});
