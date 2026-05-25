/**
 * probe_group_tap.js — 检测 ChatroomContactUI 列表点击，捕获被点击的群 ID
 */
'use strict';

Java.perform(function () {
    var TAG = '[GTAP]';

    // Hook startActivity — 从群列表点群时会启动 ChattingUI
    try {
        var Activity = Java.use('android.app.Activity');
        var orig = Activity.startActivity.overload('android.content.Intent');
        orig.implementation = function (intent) {
            var caller = this.getClass().getName();
            if (caller.indexOf('ChatroomContactUI') >= 0 || caller.indexOf('GroupCard') >= 0) {
                console.log(TAG + ' startActivity from ' + caller);
                dumpIntent(intent, TAG + '[OUT]');
            }
            return this.startActivity(intent);
        };
        console.log(TAG + ' startActivity hook ok');
    } catch (e) { console.log(TAG + ' startActivity fail: ' + e); }

    // Hook ChatroomContactUI ListView/RecyclerView item click
    try {
        var ChatroomContactUI = Java.use('com.tencent.mm.ui.contact.ChatroomContactUI');
        // Hook onResume to know when we're on the page
        ChatroomContactUI.onResume.implementation = function () {
            console.log(TAG + ' ChatroomContactUI.onResume');
            return this.onResume();
        };
        console.log(TAG + ' ChatroomContactUI hook ok');
    } catch (e) { console.log(TAG + ' ChatroomContactUI fail: ' + e); }

    // 通用：hook Intent.setClassName / Intent.setComponent 看群聊启动
    try {
        var Intent = Java.use('android.content.Intent');
        Intent.setClassName.overload('java.lang.String', 'java.lang.String').implementation = function (pkg, cls) {
            if (cls.indexOf('ChattingUI') >= 0 || cls.indexOf('Chatroom') >= 0 || cls.indexOf('GroupCard') >= 0) {
                console.log(TAG + ' setClassName → ' + pkg + '/' + cls);
                // 此时 Intent 可能已有 chatroom username
                try {
                    var b = this.getExtras();
                    if (b) {
                        var keys = b.keySet();
                        var kit = keys.iterator();
                        while (kit.hasNext()) {
                            var k = kit.next();
                            try {
                                var v = b.get(k);
                                var vs = String(v);
                                if (vs.indexOf('@chatroom') >= 0) {
                                    console.log(TAG + ' ★ GROUP ID: ' + k + ' = ' + vs);
                                }
                            } catch (e2) {}
                        }
                    }
                } catch (e3) {}
            }
            return this.setClassName(pkg, cls);
        };
        console.log(TAG + ' Intent.setClassName hook ok');
    } catch (e) { console.log(TAG + ' Intent.setClassName fail: ' + e); }

    function dumpIntent(intent, prefix) {
        if (!intent) { console.log(prefix + ' intent=null'); return; }
        var cmp = intent.getComponent();
        if (cmp) console.log(prefix + ' cmp=' + cmp.getClassName());
        var b = intent.getExtras();
        if (!b) return;
        var keys = b.keySet();
        var kit = keys.iterator();
        while (kit.hasNext()) {
            var k = kit.next();
            try {
                var v = b.get(k);
                var vs = String(v);
                if (vs.length > 120) vs = vs.substring(0, 120) + '...';
                console.log(prefix + ' ' + k + ' = ' + vs + ' (' + (v ? v.getClass().getSimpleName() : 'null') + ')');
            } catch (e2) {}
        }
    }

    console.log(TAG + ' ready — 请进通讯录→群聊→点一个群，看控制台输出');
});
