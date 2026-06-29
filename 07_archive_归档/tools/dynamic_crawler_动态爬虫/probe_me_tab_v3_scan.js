/**
 * 探针 v3：直接扫描当前 Activity 视图树，找所有 TextView
 * 不依赖 setText 事件，直接读已渲染的 View
 * 用法：frida -U -n com.tencent.mm -l this.js
 */

'use strict';

Java.perform(function() {
    var ActivityThread = Java.use("android.app.ActivityThread");
    var thread = ActivityThread.currentActivityThread();
    var activities = thread.getActivities();

    if (activities === null) {
        console.log("[SCAN] no activities");
        return;
    }

    var iter = activities.keySet().iterator();
    while (iter.hasNext()) {
        var key = iter.next();
        var ar = activities.get(key);
        var activity = ar !== null ? ar.get() : null;
        if (activity === null) continue;

        var clsName = activity.getClass().getName();
        console.log("[SCAN] Activity: " + clsName);

        var rootView = null;
        try {
            var window = activity.getWindow();
            if (window !== null) {
                rootView = window.getDecorView();
            }
        } catch (e) {}

        if (rootView === null) continue;

        console.log("[SCAN] rootView: " + rootView.getClass().getName());
        scanView(rootView, 0);
    }

    function scanView(view, depth) {
        if (depth > 12) return;
        var cls = view.getClass().getName();
        var id = "0x" + view.getId().toString(16);

        // 打印所有 TextView
        var isTV = cls.indexOf("TextView") !== -1 || cls.indexOf("textview") !== -1;
        if (isTV) {
            var text = "";
            try {
                var cs = view.getText();
                if (cs !== null) text = cs.toString();
            } catch (e) {}
            if (text.length > 0) {
                var pad = "  ".repeat(Math.min(depth, 8));
                console.log(pad + "[TV] id=" + id + " cls=" + cls.split(".").pop() +
                            " text=" + JSON.stringify(text.substring(0, 120)));
            }
        }

        // 递归子 View
        if (view.getClass().getName().indexOf("ViewGroup") !== -1 ||
            view.getClass().getSuperclass().getName().indexOf("ViewGroup") !== -1) {
            try {
                var childCount = view.getChildCount();
                for (var i = 0; i < childCount && i < 200; i++) {
                    var child = view.getChildAt(i);
                    if (child !== null) scanView(child, depth + 1);
                }
            } catch (e) {}
        }
    }

    console.log("[SCAN] done");
});
