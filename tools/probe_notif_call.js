// probe_notif_call.js — 语音/视频通话通知探针
// 用法：frida -U -p <微信主进程pid> -l tools/probe_notif_call.js

(function () {
    'use strict';
    var ts = function () {
        var d = new Date(), p = function (n) { return n < 10 ? '0' + n : '' + n; };
        return p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds());
    };
    var TARGET = 'wxid_lzd2va16jd1622';

    function s(obj) {
        if (obj === null || obj === undefined) return 'null';
        try { var r = String(obj); return r.length > 200 ? r.substring(0, 200) + '...' : r; } catch (e) { return '<err>'; }
    }

    Java.perform(function () {
        var NI = Java.use('com.tencent.mm.booter.notification.NotificationItem');

        NI.a.implementation = function (ctx) {
            var hVal = '?', isTarget = false;
            try { hVal = s(this.h.value); isTarget = (hVal === TARGET || hVal.indexOf(TARGET) >= 0); } catch (e) {}

            var toStr = '?';
            try { toStr = s(this.toString()); } catch (e) {}

            // 从 NotificationItem.f (android.app.Notification) 拿 title/text
            var title = '?', text = '?';
            try {
                var notif = this.f.value;
                if (notif) {
                    var extras = notif.extras;
                    if (extras) {
                        var t = extras.getString('android.title');
                        var tx = extras.getString('android.text');
                        if (t) title = String(t);
                        if (tx) text = String(tx);
                    }
                }
            } catch (e) {}

            // 进程名 + 前后台
            var proc = '?', fg = '?';
            try {
                var app = Java.use('android.app.ActivityThread').currentApplication();
                var appInfo = app.getApplicationInfo();
                proc = String(appInfo.processName);
            } catch (e) {}
            try {
                var am = Java.use('android.app.ActivityManager');
                // 不调 getRunningAppProcesses（铁律7），用其他方式判断
                fg = 'n/a';
            } catch (e) {}

            console.log(ts() + ' [CALL] h=' + hVal + (isTarget ? ' ★TARGET★' : ''));
            console.log('        toString=' + toStr);
            console.log('        title=' + title + ' text=' + text);
            console.log('        proc=' + proc);

            return this.a(ctx);
        };

        console.log(ts() + ' [OK] NotificationItem.a hooked, 等语音/视频通话...');
    });
})();
