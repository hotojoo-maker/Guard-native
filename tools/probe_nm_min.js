// probe_nm_min.js — 最小 NM.notify 探针
Java.perform(function () {
    console.log('[NM] loaded');
    var NM = Java.use('android.app.NotificationManager');
    NM.notify.overload('java.lang.String', 'int', 'android.app.Notification')
        .implementation = function (tag, id, n) {
            var t = '';
            try { var e = n.extras; if (e) t = e.getString('notification.show.talker') || ''; } catch (x) {}
            console.log('[NM] notify tag=' + tag + ' id=' + id + ' talker=' + t);
            if (t.indexOf('wxid_lzd2va16jd1622') >= 0) {
                console.log('[NM] ★★★ 密友通知！');
                var st = Java.use('java.lang.Thread').currentThread().getStackTrace();
                for (var i = 0; i < st.length; i++) console.log('[NM] STK[' + i + '] ' + String(st[i]));
            }
            return this.notify(tag, id, n);
        };
    NM.notify.overload('int', 'android.app.Notification')
        .implementation = function (id, n) {
            var t = '';
            try { var e = n.extras; if (e) t = e.getString('notification.show.talker') || ''; } catch (x) {}
            console.log('[NM] notify id=' + id + ' talker=' + t);
            if (t.indexOf('wxid_lzd2va16jd1622') >= 0) {
                console.log('[NM] ★★★ 密友通知！');
                var st = Java.use('java.lang.Thread').currentThread().getStackTrace();
                for (var i = 0; i < st.length; i++) console.log('[NM] STK[' + i + '] ' + String(st[i]));
            }
            return this.notify(id, n);
        };
    console.log('[NM] ready — 请让密友发消息');
});
