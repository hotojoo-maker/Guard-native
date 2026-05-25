/**
 * 探针 v4：Java.choose 扫描堆内全部 TextView，找微信号相关
 */

'use strict';

Java.perform(function() {
    var found = 0;

    Java.choose("android.widget.TextView", {
        onMatch: function(tv) {
            if (found >= 100) return;
            try {
                var text = tv.getText();
                if (text === null || text.toString().length === 0) return;
                var s = text.toString();
                if (s.length > 200) return;

                found++;
                var id = "0x" + tv.getId().toString(16);
                var idName = "";
                try { idName = tv.getResources().getResourceEntryName(tv.getId()); } catch(e) {}

                // 全部打印（限100条）
                console.log("[TV:" + found + "] id=" + id +
                            (idName ? " name=" + idName : "") +
                            " cls=" + tv.getClass().getSimpleName() +
                            " text=" + JSON.stringify(s.substring(0, 100)));
            } catch(e) {}
        },
        onComplete: function() {
            console.log("[SCAN] total TextViews scanned, printed=" + found);
        }
    });

    console.log("[PROBE4] Java.choose scanning... (确保微信在「我」Tab)");
});
