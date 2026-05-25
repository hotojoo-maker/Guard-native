/**
 * probe_current_activity.js — 实时打印每个新 Activity 的类名
 */
'use strict';

Java.perform(function () {
    var Activity = Java.use('android.app.Activity');
    Activity.onCreate.overload('android.os.Bundle').implementation = function (savedInstanceState) {
        var cls = this.getClass().getName();
        if (/contact|group|chatroom|select|address/i.test(cls)) {
            console.log('[ACT] ★ ' + cls);
        }
        return this.onCreate(savedInstanceState);
    };
    console.log('[ACT] ready — 你操作微信，含 contact/group/chatroom/select 的类名会打印');
});
