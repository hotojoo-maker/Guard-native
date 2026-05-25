/**
 * 探针：8.0.71「我」Tab 微信号 TextView 发现
 * 目标：找到显示"微信号：xxx"的 View id
 * 用法：frida -U -n com.tencent.mm -l this.js
 */

'use strict';

var hitCount = 0;

Java.perform(function() {
    var TextView = Java.use("android.widget.TextView");

    // Hook setText(CharSequence)
    TextView.setText.overload('java.lang.CharSequence').implementation = function(cs) {
        if (cs === null) {
            this.setText(cs);
            return;
        }
        var text = cs.toString();
        var id = this.getId();
        var idHex = "0x" + id.toString(16);

        // 命中条件：包含"微信号" 或 id 在 0x7f0c6c00~0x7f0c6dff 区间(ouv资源附近)
        var nearTarget = (id >= 0x7f0c6c00 && id <= 0x7f0c6eff);

        if (text.indexOf("微信号") !== -1 || text.indexOf("微信") !== -1 || nearTarget) {
            hitCount++;
            console.log("[PROBE:" + hitCount + "] id=" + idHex +
                        " text=" + text.substring(0, Math.min(text.length, 80)) +
                        " cls=" + this.getClass().getName());

            // 打父 View 链
            var p = this.getParent();
            var chain = "  parent chain:";
            var depth = 0;
            while (p !== null && depth < 6) {
                chain += " " + p.getClass().getSimpleName() +
                         "(id=0x" + p.getId().toString(16) + ")";
                p = p.getParent();
                depth++;
            }
            console.log(chain);
        }

        this.setText(cs);
    };

    console.log("[PROBE] TextView.setText hook active");
    console.log("[PROBE] 现在进微信「我」Tab，观察 id=0x7f0c6c6d 附近命中");
});
