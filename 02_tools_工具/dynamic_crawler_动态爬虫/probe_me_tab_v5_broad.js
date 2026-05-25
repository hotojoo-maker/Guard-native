/**
 * 探针 v5：Java.choose 扫 android.view.View（不过滤子类）
 * 找包含中文 + 非空文字的 View，打印 id + 资源名
 */

'use strict';

Java.perform(function() {
    var found = 0;

    Java.choose("android.view.View", {
        onMatch: function(view) {
            if (found >= 200) return;
            try {
                var text = "";
                // 尝试 getText()
                try { var t = view.getText(); if (t !== null) text = t.toString(); } catch(e) {}
                if (!text || text.length === 0) return;
                if (text.length > 200) return;

                found++;
                var id = "0x" + view.getId().toString(16);
                var idName = "";
                try { idName = view.getResources().getResourceEntryName(view.getId()); } catch(e) {}

                // 全部打印
                console.log("[V:" + found + "] id=" + id +
                            (idName ? " name=" + idName : "") +
                            " cls=" + view.getClass().getSimpleName() +
                            " text=" + JSON.stringify(text.substring(0, 100)));
            } catch(e) {}
        },
        onComplete: function() {
            console.log("[SCAN] total=" + found);
        }
    });

    console.log("[PROBE5] scanning all Views (ensure Me tab active)...");
});
