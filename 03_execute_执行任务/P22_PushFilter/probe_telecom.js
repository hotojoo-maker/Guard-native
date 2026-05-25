/**
 * probe_telecom.js — 找出哪个进程调了 TelecomManager / ConnectionService
 *
 * 用法：两个进程分别跑
 *   frida -U -p <main_pid>   -l probe_telecom.js
 *   frida -U -p <push_pid>   -l probe_telecom.js
 *
 * 操作：锁屏 → HIDDEN → 密友 VoIP → 看 [TC:*] 输出
 */
'use strict';

Java.perform(function () {
    var TAG = "[TC]";

    // §1 TelecomManager.addNewIncomingCall — 注册来电，系统显示状态栏图标
    try {
        var TM = Java.use('android.telecom.TelecomManager');
        TM.addNewIncomingCall.overload('android.net.Uri', 'android.os.Bundle').implementation = function(uri, extras) {
            var caller = extras ? (extras.getString('android.telecom.extra.INCOMING_CALL_ADDRESS') || '?') : '?';
            console.log(TAG + " TelecomManager.addNewIncomingCall caller=" + caller);
            // print stack top 10 lines
            var e = Java.use('java.lang.Exception').$new();
            var st = Java.use('android.util.Log').getStackTraceString(e);
            var lines = st.split('\n');
            for (var i = 0; i < Math.min(10, lines.length); i++) {
                console.log(TAG + "   " + lines[i]);
            }
            return this.addNewIncomingCall(uri, extras);
        };
        console.log(TAG + " TelecomManager V");
    } catch(e) { console.log(TAG + " TelecomManager X: " + e); }

    // §2 ConnectionService.onCreateIncomingConnection — 系统回调
    try {
        var CS = Java.use('android.telecom.ConnectionService');
        CS.onCreateIncomingConnection.overload('android.telecom.PhoneAccountHandle', 'android.telecom.ConnectionRequest').implementation = function(handle, req) {
            console.log(TAG + " ConnectionService.onCreateIncomingConnection");
            var e = Java.use('java.lang.Exception').$new();
            var st = Java.use('android.util.Log').getStackTraceString(e);
            var lines = st.split('\n');
            for (var i = 0; i < Math.min(10, lines.length); i++) {
                console.log(TAG + "   " + lines[i]);
            }
            return this.onCreateIncomingConnection(handle, req);
        };
        console.log(TAG + " ConnectionService V");
    } catch(e) { console.log(TAG + " ConnectionService X: " + e); }

    // §3 Notification.category — 看谁在建 CATEGORY_CALL
    try {
        var Notif = Java.use('android.app.Notification');
        console.log(TAG + " Notification class loaded, category=CALL=" + Notif.CATEGORY_CALL.value);
    } catch(e) {}

    // §4 Notification.Builder.setCategory — 拦截设置 CALL 类别
    try {
        var NB = Java.use('android.app.Notification$Builder');
        // setCategory is final, can't hook. Use build() instead.
        NB.build.implementation = function() {
            var n = this.build();
            if (n.category === 'call') {
                console.log(TAG + " Notification.build CATEGORY_CALL detected");
                var e = Java.use('java.lang.Exception').$new();
                var st = Java.use('android.util.Log').getStackTraceString(e);
                var lines = st.split('\n');
                for (var i = 0; i < Math.min(8, lines.length); i++) {
                    console.log(TAG + "   " + lines[i]);
                }
            }
            return n;
        };
        console.log(TAG + " Notification.Builder.build V");
    } catch(e) { console.log(TAG + " Notification.Builder.build X: " + e); }

    console.log(TAG + " === ready. lock screen + hidden-friend VoIP now ===");
});
