/**
 * probe_nm_notify.js — 8.0.71 通知调用栈探针
 * 锚点：NotificationManager.notify() — 系统API，100%必经
 * 目标：往上追调用栈，找8.0.71的真实通知入口
 *
 * 用法：frida -U -p 13720 -l tools/probe_nm_notify.js 2>&1 | tee tools/probe_nm.log
 * 操作：密友 wxid_lzd2va16jd1622 发消息
 */
(function () {
    'use strict';

    var TAG = 'NM';
    var hits = 0;

    function ts() {
        var d = new Date();
        var p = function (n) { return n < 10 ? '0' + n : '' + n; };
        return p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds()) +
            '.' + (d.getMilliseconds() + '000').substring(0, 3);
    }
    function log(sub, msg) { console.log(ts() + ' [' + TAG + ':' + sub + '] ' + msg); }

    function fullStack() {
        try {
            var frames = Java.use('java.lang.Thread').currentThread().getStackTrace();
            var out = [];
            for (var i = 0; i < frames.length; i++) {
                var s = String(frames[i]);
                if (s.indexOf('java.lang.reflect') >= 0) continue;
                if (s.indexOf('dalvik.') >= 0) continue;
                if (s.indexOf('frida') >= 0) continue;
                out.push(s.trim());
            }
            return out;
        } catch (e) { return ['?']; }
    }

    Java.perform(function () {
        log('INIT', '=== probe_nm_notify.js attached to 8.0.71 ===');

        try {
            var NM = Java.use('android.app.NotificationManager');

            // notify(String, int, Notification)
            NM.notify.overload('java.lang.String', 'int', 'android.app.Notification')
                .implementation = function (tag, id, notif) {
                    hits++;
                    var talker = '';
                    try {
                        var extras = notif.extras;
                        if (extras) {
                            talker = extras.getString('notification.show.talker') || '';
                            if (!talker) talker = extras.getString('talker') || '';
                        }
                    } catch (e) {}

                    if (talker.indexOf('wxid_') >= 0 || hits <= 5) {
                        log('NOTIFY', 'tag=' + tag + ' id=' + id +
                            (talker ? ' 🔔talker=' + talker : ''));
                        if (talker.indexOf('wxid_lzd2va16jd1622') >= 0) {
                            log('★TARGET', '=== 密友通知到达！调用栈 ===');
                            var stk = fullStack();
                            for (var i = 0; i < stk.length; i++) {
                                log('STK', '[' + i + '] ' + stk[i]);
                            }
                        }
                    }
                    return this.notify(tag, id, notif);
                };

            // notify(int, Notification)
            NM.notify.overload('int', 'android.app.Notification')
                .implementation = function (id, notif) {
                    hits++;
                    var talker = '';
                    try {
                        var extras = notif.extras;
                        if (extras) {
                            talker = extras.getString('notification.show.talker') || '';
                            if (!talker) talker = extras.getString('talker') || '';
                        }
                    } catch (e) {}

                    if (talker.indexOf('wxid_') >= 0 || hits <= 5) {
                        log('NOTIFY', 'id=' + id +
                            (talker ? ' 🔔talker=' + talker : ''));
                        if (talker.indexOf('wxid_lzd2va16jd1622') >= 0) {
                            log('★TARGET', '=== 密友通知到达！调用栈 ===');
                            var stk = fullStack();
                            for (var i = 0; i < stk.length; i++) {
                                log('STK', '[' + i + '] ' + stk[i]);
                            }
                        }
                    }
                    return this.notify(id, notif);
                };

            log('INIT', 'NM.notify hooked — 请让密友 wxid_lzd2va16jd1622 发消息');

        } catch (e) {
            log('ERR', 'hook fail: ' + e);
        }
    });
})();
