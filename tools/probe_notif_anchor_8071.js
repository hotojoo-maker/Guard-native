// probe_notif_anchor_8071.js — 微信 8.0.71 通知调用链锚点探针
//
// 策略：从 NotificationManager.notify() 往上追调用栈
//       系统 API 必经，不依赖任何混淆类名
//
// 用法：
//   # 先确认 PID 是 com.tencent.mm（不是 mn1！）
//   adb shell "ps -ef | grep tencent.mm$"
//   frida -U -p <com.tencent.mm主进程pid> -l probe_notif_anchor_8071.js
//
// 操作：attach 后让 wxid_lzd2va16jd1622 发一条消息
// 输出：talker wxid + 完整调用栈 → 找到 8.0.71 实际通知入口

(function () {
    'use strict';

    var ts = function () {
        var d = new Date(), p = function (n) { return n < 10 ? '0' + n : '' + n; };
        return p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds());
    };

    function stack() {
        try {
            var frames = Java.use('java.lang.Thread').currentThread().getStackTrace();
            var out = [];
            for (var i = 0; i < frames.length && out.length < 12; i++) {
                var s = String(frames[i]);
                if (s.indexOf('java.lang.reflect') >= 0) continue;
                if (s.indexOf('dalvik.') >= 0) continue;
                if (s.indexOf('de.robv') >= 0) continue;
                out.push(s.trim());
            }
            return out.join('\n    ');
        } catch (e) { return '?'; }
    }

    Java.perform(function () {
        // 确认进程
        var proc = '?';
        try { proc = String(Java.use('android.app.ActivityThread').currentApplication().getApplicationInfo().processName); } catch(e) {}
        console.log(ts() + ' [ANCHOR] 进程=' + proc);
        if (proc.indexOf('com.tencent.mm') < 0) {
            console.log(ts() + ' [ANCHOR] ⚠️ 不是微信主进程，请重新确认 PID！');
        }

        // ── 锚点1：NotificationManager.notify(String tag, int id, Notification n) ──
        try {
            var NM = Java.use('android.app.NotificationManager');
            NM.notify.overload('java.lang.String', 'int', 'android.app.Notification')
                .implementation = function (tag, id, notif) {
                    var talker = '?';
                    try {
                        var extras = notif.extras;
                        if (extras) {
                            var t = extras.getString('notification.show.talker');
                            if (t) talker = String(t);
                        }
                    } catch (e) {}
                    console.log(ts() + ' [ANCHOR:NM] notify id=' + id + ' talker=' + talker);
                    console.log('    调用栈:\n    ' + stack());
                    return this.notify(tag, id, notif);
                };
            console.log(ts() + ' [ANCHOR] NotificationManager.notify hooked OK');
        } catch (e) {
            console.log(ts() + ' [ANCHOR] NM.notify hook FAIL: ' + e);
        }

        // ── 锚点2：NotificationManager.notify(int id, Notification n) ──
        try {
            var NM2 = Java.use('android.app.NotificationManager');
            NM2.notify.overload('int', 'android.app.Notification')
                .implementation = function (id, notif) {
                    var talker = '?';
                    try {
                        var extras = notif.extras;
                        if (extras) {
                            var t = extras.getString('notification.show.talker');
                            if (t) talker = String(t);
                        }
                    } catch (e) {}
                    console.log(ts() + ' [ANCHOR:NM2] notify id=' + id + ' talker=' + talker);
                    console.log('    调用栈:\n    ' + stack());
                    return this.notify(id, notif);
                };
            console.log(ts() + ' [ANCHOR] NotificationManager.notify(int,Notif) hooked OK');
        } catch (e) {
            console.log(ts() + ' [ANCHOR] NM2 hook FAIL: ' + e);
        }

        console.log(ts() + ' [ANCHOR] === 请让密友 wxid_lzd2va16jd1622 发消息 ===');
        console.log(ts() + ' [ANCHOR] 关注：调用栈里第1个 com.tencent.mm.* 类 = 实际通知入口');
    });
})();
