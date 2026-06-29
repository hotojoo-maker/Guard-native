// probe_catfish_push_notify.js — Catfish :push 进程通知处理探针
// 抓 setNotification (震动/铃声) + replaceNotification (内容伪装)

Java.perform(function() {
    var TAG = "[PUSH-PROBE]";
    console.log(TAG + " === Catfish Push Notification Probe ===");

    // §1 setNotification — 通知声音/震动控制
    try {
        var UC = Java.use("com.catfish.newvip.core.UserControll");
        var sn = UC.setNotification;
        UC.setNotification.implementation = function(ctx, notification) {
            console.log(TAG + " >>> setNotification()");
            console.log(TAG + "     sound=" + notification.sound + " vibrate=" + notification.vibrate + " defaults=" + notification.defaults);
            var result = sn.call(this, ctx, notification);
            console.log(TAG + "     AFTER → sound=" + result.sound + " vibrate=" + result.vibrate + " defaults=" + result.defaults);
            return result;
        };
        console.log(TAG + " V setNotification");
    } catch(e) { console.log(TAG + " X setNotification: " + e); }

    // §2 replaceNotification — 通知内容伪装 (密友→weixin)
    try {
        var UC2 = Java.use("com.catfish.newvip.core.UserControll");
        var rn = UC2.replaceNotification;
        UC2.replaceNotification.implementation = function(msg) {
            var data = msg.getData();
            var talker = data.getString("notification.show.talker");
            var content = data.getString("notification.show.message.content");
            console.log(TAG + " >>> replaceNotification() talker=" + talker + " content=" + content);
            rn.call(this, msg);
            var newTalker = data.getString("notification.show.talker");
            var newContent = data.getString("notification.show.message.content");
            console.log(TAG + "     AFTER → talker=" + newTalker + " content=" + newContent);
        };
        console.log(TAG + " V replaceNotification");
    } catch(e) { console.log(TAG + " X replaceNotification: " + e); }

    // §3 监控 NotificationManager.notify
    try {
        var NM = Java.use("android.app.NotificationManager");
        var notify = NM.notify.overload('java.lang.String', 'int', 'android.app.Notification');
        notify.implementation = function(tag, id, notification) {
            if (notification != null) {
                var extras = notification.extras;
                var title = extras ? extras.getString("android.title") : "?";
                var text = extras ? extras.getString("android.text") : "?";
                console.log(TAG + " notify() tag=" + tag + " title=" + title + " text=" + text);
            }
            return notify.call(this, tag, id, notification);
        };
        console.log(TAG + " V NotificationManager.notify");
    } catch(e) { console.log(TAG + " X NotificationManager.notify (may not be hookable): " + e); }

    console.log(TAG + " === Ready on :push process. Waiting for notifications... ===");
});
