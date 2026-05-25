/**
 * 探针 v6：Hook Activity.onResume → 拿到 Activity → walk view tree
 * 不过滤，把所有有文字的 View 打印出来
 */

'use strict';

Java.perform(function() {
    var Activity = Java.use("android.app.Activity");

    Activity.onResume.implementation = function() {
        var act = this;
        var clsName = act.getClass().getName();
        console.log("[ACT] onResume: " + clsName);

        // 延迟 500ms 等布局完成再扫
        var Handler = Java.use("android.os.Handler");
        var Looper = Java.use("android.os.Looper");
        var handler = Handler.$new(Looper.getMainLooper());

        var Runnable = Java.use("java.lang.Runnable");
        var scanTask = Java.registerClass({
            name: "com.guard.ProbeRunnable" + Date.now(),
            implements: [Runnable],
            methods: {
                run: function() {
                    try {
                        var decorView = act.getWindow().getDecorView();
                        console.log("[SCAN:" + clsName.split(".").pop() + "] decor=" + decorView.getClass().getName());
                        walkView(decorView, 0);
                    } catch(e) {
                        console.log("[SCAN] err: " + e);
                    }
                }
            }
        });

        handler.postDelayed(scanTask.$new(), 800);
        this.onResume();
    };

    function walkView(view, depth) {
        if (depth > 15) return;
        try {
            var cls = view.getClass().getName();
            var id = "0x" + view.getId().toString(16);
            var text = "";

            // 尝试 getText
            try {
                var t = view.getText();
                if (t !== null) text = t.toString();
            } catch(e) {}

            var idName = "";
            try {
                if (view.getId() !== -1) {
                    idName = view.getResources().getResourceEntryName(view.getId());
                }
            } catch(e) {}

            // 只打印有文字或有意义的资源名
            var hasContent = text.length > 0 || idName.length > 0;
            if (hasContent) {
                var pad = "  ".repeat(Math.min(depth, 8));
                var shortCls = cls.split(".").pop();
                if (text.length > 80) text = text.substring(0, 80) + "...";
                console.log(pad + "[V:" + depth + "] id=" + id +
                            (idName ? " name=" + idName : "") +
                            " cls=" + shortCls +
                            " text=" + JSON.stringify(text));
            }

            // 递归子 View
            var childCount = 0;
            try { childCount = view.getChildCount(); } catch(e) {}
            for (var i = 0; i < childCount && i < 200; i++) {
                var child = view.getChildAt(i);
                if (child !== null) walkView(child, depth + 1);
            }
        } catch(e) {}
    }

    console.log("[PROBE6] onResume hook active");
    console.log("[PROBE6] 切到「我」Tab 触发");
});
