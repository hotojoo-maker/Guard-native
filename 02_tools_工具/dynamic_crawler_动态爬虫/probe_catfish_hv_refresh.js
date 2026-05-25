// probe_catfish_hv_refresh.js — Catfish H↔V 会话刷新机制探针
// 目标: com.tencent.mn1 (Catfish 8.0.70.2 破解版)
// 用途: 实时观察 H→V 切换时 Catfish 调了哪些方法刷列表

const TAG = "[CF-PROBE]";

Java.perform(function() {
    console.log(TAG + " ====== Catfish H/V Refresh Probe Attached ======");

    // ── §1 UserControll 状态切换 ──
    try {
        var UC = Java.use("com.catfish.newvip.core.UserControll");
        var uc_enter = UC.enterVipMode;
        UC.enterVipMode.implementation = function() {
            console.log(TAG + " >>> enterVipMode() [H→V]");
            var e = Java.use("java.lang.Exception").$new();
            var stack = Java.use("android.util.Log").getStackTraceString(e);
            console.log(TAG + " Stack:\n" + stack);
            return uc_enter.call(this);
        };
        var uc_exit = UC.exitVipMode;
        UC.exitVipMode.implementation = function(isBack) {
            console.log(TAG + " >>> exitVipMode(isBack=" + isBack + ") [V→H]");
            var e = Java.use("java.lang.Exception").$new();
            var stack = Java.use("android.util.Log").getStackTraceString(e);
            console.log(TAG + " Stack:\n" + stack);
            return uc_exit.call(this, isBack);
        };
        console.log(TAG + " ✓ UserControll hooked");
    } catch(e) { console.log(TAG + " ✗ UserControll: " + e); }

    // ── §2 ActivityControll ──
    try {
        var AC = Java.use("com.catfish.newvip.core.ActivityControll");
        var ac_nsc = AC.notifyStateChanged;
        AC.notifyStateChanged.implementation = function() {
            console.log(TAG + " >>> notifyStateChanged()");
            var e = Java.use("java.lang.Exception").$new();
            var stack = Java.use("android.util.Log").getStackTraceString(e);
            console.log(TAG + " Stack:\n" + stack);
            return ac_nsc.call(this);
        };
        var ac_rl = AC.restartLauncher;
        AC.restartLauncher.implementation = function(flag) {
            console.log(TAG + " >>> restartLauncher(" + flag + ")");
            var e = Java.use("java.lang.Exception").$new();
            var stack = Java.use("android.util.Log").getStackTraceString(e);
            console.log(TAG + " Stack:\n" + stack);
            return ac_rl.call(this, flag);
        };
        var ac_btl = AC.backToLauncher;
        AC.backToLauncher.implementation = function(isBack) {
            console.log(TAG + " >>> backToLauncher(" + isBack + ")");
            return ac_btl.call(this, isBack);
        };
        console.log(TAG + " ✓ ActivityControll hooked");
    } catch(e) { console.log(TAG + " ✗ ActivityControll: " + e); }

    // ── §3 MainEntry 会话过滤 ──
    try {
        var ME = Java.use("com.catfish.newvip.MainEntry");
        var me_hr = ME.hookRecent;
        ME.hookRecent.implementation = function(list) {
            var sz = list ? list.size() : -1;
            console.log(TAG + " hookRecent(List) size=" + sz);
            return me_hr.call(this, list);
        };
        var me_hnc = ME.hookNewCon;
        ME.hookNewCon.implementation = function(list) {
            var sz = list ? list.size() : -1;
            console.log(TAG + " hookNewCon(List) size=" + sz);
            return me_hnc.call(this, list);
        };
        var me_hc = ME.hookConversation;
        ME.hookConversation.implementation = function(list) {
            var sz = list ? list.size() : -1;
            console.log(TAG + " hookConversation(List) size=" + sz);
            return me_hc.call(this, list);
        };
        console.log(TAG + " ✓ MainEntry hooked");
    } catch(e) { console.log(TAG + " ✗ MainEntry: " + e); }

    // ── §4 isVipMode 调用追踪（看谁在读状态） ──
    try {
        var UC2 = Java.use("com.catfish.newvip.core.UserControll");
        var ivm = UC2.isVipMode;
        UC2.isVipMode.implementation = function() {
            var result = ivm.call(this);
            // 只在高频路径外打印 (用采样)
            return result;
        };
        console.log(TAG + " ✓ isVipMode hooked (sampled)");
    } catch(e) { console.log(TAG + " ✗ isVipMode: " + e); }

    console.log(TAG + " ====== All hooks ready. Trigger H↔V now. ======");
});
