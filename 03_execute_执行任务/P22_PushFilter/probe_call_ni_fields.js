/**
 * probe_call_ni_fields.js
 * 目标：找 voice/video call 第一波 NotificationItem 里存 caller wxid 的字段
 *
 * 用法：
 *   frida -U -n com.tencent.mm -l probe_call_ni_fields.js --no-pause
 * 密友打来语音/视频 → 看 [NI-CALL] 输出
 */

"use strict";

Java.perform(function () {
    var NI_CLASS = "com.tencent.mm.booter.notification.NotificationItem";
    var TARGET_WXID = "wxid_lzd2va16jd1622";
    var seen = 0;

    var LinkedList = Java.use("java.util.LinkedList");
    LinkedList.add.overload("java.lang.Object").implementation = function (item) {
        var result = this.add(item);

        if (item == null) return result;
        try {
            var cls = item.getClass();
            if (cls.getName() !== NI_CLASS) return result;

            seen++;
            var wave = seen;

            // dump ALL fields — every class in hierarchy
            var out = "[NI-CALL] wave=" + wave + " cls=" + cls.getName()
                    + " loader=" + cls.getClassLoader() + "\n";

            var c = cls;
            while (c != null && c.getName() !== "java.lang.Object") {
                var fields = c.getDeclaredFields();
                out += "  [" + c.getSimpleName() + "] fields=" + fields.length + "\n";
                for (var i = 0; i < fields.length; i++) {
                    var f = fields[i];
                    try {
                        f.setAccessible(true);
                        var v = f.get(item);
                        var mark = (v != null && String(v).indexOf(TARGET_WXID) >= 0) ? " <<<" : "";
                        out += "    " + f.getName()
                             + "[" + f.getType().getSimpleName() + "]"
                             + "=" + v + mark + "\n";
                    } catch (e) {
                        out += "    " + f.getName() + "=ERR:" + e + "\n";
                    }
                }
                c = c.getSuperclass();
            }

            // Also dump Notification.extras if field f is a Notification
            try {
                var Notification = Java.use("android.app.Notification");
                var notifField = cls.getDeclaredField("f");
                notifField.setAccessible(true);
                var notif = notifField.get(item);
                if (notif != null) {
                    var extras = Java.cast(notif, Notification).extras.value;
                    if (extras != null) {
                        var keys = extras.keySet().toArray();
                        out += "  [f.extras] count=" + keys.length + "\n";
                        for (var j = 0; j < keys.length; j++) {
                            var k = keys[j];
                            var v2 = extras.get(k);
                            var mark2 = (v2 != null && String(v2).indexOf(TARGET_WXID) >= 0) ? " <<<" : "";
                            out += "    " + k + "=" + v2 + mark2 + "\n";
                        }
                    }
                }
            } catch (e2) {
                out += "  [f.extras] err: " + e2 + "\n";
            }

            console.log(out);
        } catch (e) {
            console.log("[NI-CALL] err: " + e);
        }

        return result;
    };

    console.log("[NI-CALL] probe ready — waiting for LinkedList.add(NotificationItem)");
});
