// console.log 版：一次性 dump 当前 resumed Activity 的 decorView view 树（class + resId + 可见性 + text）
// 配合 frida CLI 使用（CLI 会注入 Java bridge）：frida -U -p <pid> -l tools/dump_views_log.js -o out.txt
Java.perform(function () {
    var TextView = Java.use("android.widget.TextView");
    var ViewGroup = Java.use("android.view.ViewGroup");
    var ActivityThread = Java.use("android.app.ActivityThread");
    var ACR = Java.use("android.app.ActivityThread$ActivityClientRecord");

    var OUT = [];
    function emit(s) { OUT.push(s); }

    function resName(ctx, id) {
        if (id === -1 || id === 0) return "no-id";
        try { return ctx.getResources().getResourceEntryName(id); }
        catch (e) { return "id0x" + (id >>> 0).toString(16); }
    }
    function visStr(v) {
        try { var x = v.getVisibility(); return x === 0 ? "V" : (x === 4 ? "I" : "G"); }
        catch (e) { return "?"; }
    }
    function dump(view, depth, ctx) {
        try {
            var ind = "";
            for (var i = 0; i < depth; i++) ind += "| ";
            var cls = view.getClass().getName();
            var simple = cls.substring(cls.lastIndexOf('.') + 1);
            var line = ind + simple + " #" + resName(ctx, view.getId()) + " [" + visStr(view) + "]";
            if (TextView.class.isInstance(view)) {
                var t = Java.cast(view, TextView).getText();
                if (t !== null) {
                    var s = t.toString();
                    if (s.length > 0) line += " TEXT=\"" + s + "\"";
                }
            }
            emit(line);
            if (ViewGroup.class.isInstance(view)) {
                var vg = Java.cast(view, ViewGroup);
                var n = vg.getChildCount();
                for (var j = 0; j < n; j++) dump(vg.getChildAt(j), depth + 1, ctx);
            }
        } catch (e) { emit("ERR " + e); }
    }

    try {
        var at = ActivityThread.currentActivityThread();
        var acts = at.mActivities.value;
        var keys = acts.keySet().toArray();
        var resumed = [];
        for (var k = 0; k < keys.length; k++) {
            var rec = Java.cast(acts.get(keys[k]), ACR);
            if (rec.paused.value === false && rec.activity.value !== null) {
                resumed.push(rec.activity.value);
            }
        }
        if (resumed.length === 0) { console.log("NO_RESUMED_ACTIVITY"); return; }
        console.log("===RESUMED_COUNT=== " + resumed.length);
        var target = resumed[resumed.length - 1];
        emit("===ACTIVITY=== " + target.getClass().getName());
        Java.scheduleOnMainThread(function () {
            try {
                var decor = target.getWindow().getDecorView();
                emit("===TREE_START===");
                dump(decor, 0, target);
                emit("===TREE_END===");
            } catch (e) { emit("ERR_MAIN " + e); }
            console.log(OUT.join("\n"));
            console.log("===DONE===");
        });
    } catch (e) {
        console.log("ERR_TOP " + e);
    }
});
