// scan_l4.js — 枚举内存中所有 l4 实例，找 wxid_ 返回的方法/字段
// 日志走 android.util.Log，用 adb logcat -s SCAN 抓取

Java.perform(function () {
    var Log = Java.use("android.util.Log");

    function tryLog(msg) {
        try { Log.i("SCAN", msg); } catch(e) {}
    }

    var seen = {};
    var count = 0;

    tryLog("[scan_l4] start Java.choose l4...");

    Java.choose("com.tencent.mm.storage.l4", {
        onMatch: function (inst) {
            if (count >= 20) return; // 只取前20个样本
            count++;

            var clsName = inst.getClass().getName();
            if (seen[clsName]) return;
            seen[clsName] = true;

            tryLog("[scan_l4] found instance: " + clsName);

            // 1. 试所有无参 String 方法
            var methods = inst.getClass().getDeclaredMethods();
            for (var i = 0; i < methods.length; i++) {
                var m = methods[i];
                if (m.getParameterTypes().length !== 0) continue;
                if (!m.getReturnType().getName().equals("java.lang.String")) continue;
                try {
                    m.setAccessible(true);
                    var val = m.invoke(inst, []);
                    if (val && (
                        val.startsWith("wxid_") ||
                        val.startsWith("gh_") ||
                        val === "weixin" ||
                        val === "filehelper"
                    )) {
                        tryLog("[scan_l4] METHOD " + m.getName() + "() = " + val);
                    }
                } catch(e) {}
            }

            // 2. 试所有 String 字段
            var fields = inst.getClass().getDeclaredFields();
            for (var j = 0; j < fields.length; j++) {
                var f = fields[j];
                if (!f.getType().getName().equals("java.lang.String")) continue;
                try {
                    f.setAccessible(true);
                    var fval = f.get(inst);
                    if (fval && (
                        fval.startsWith("wxid_") ||
                        fval.startsWith("gh_") ||
                        fval === "weixin" ||
                        fval === "filehelper"
                    )) {
                        tryLog("[scan_l4] FIELD " + f.getName() + " = " + fval);
                    }
                } catch(e) {}
            }
        },
        onComplete: function () {
            tryLog("[scan_l4] done. total matched=" + count);
        }
    });
});
