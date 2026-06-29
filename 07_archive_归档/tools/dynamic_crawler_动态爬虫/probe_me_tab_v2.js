/**
 * 探针 v2：8.0.71「我」Tab 微信号 View 发现
 * 即时打印所有含关键字的 setText + 挂钩后自动 dump Activity 顶层视图
 */

'use strict';

var count = 0;

Java.perform(function() {
    var TextView = Java.use("android.widget.TextView");

    // Hook setText — 即时打印可疑命中
    TextView.setText.overload('java.lang.CharSequence').implementation = function(cs) {
        if (cs !== null) {
            var text = cs.toString();
            var id = this.getId();
            var hasKeyword = text.indexOf("微信号") !== -1 ||
                             text.indexOf("wxid") !== -1 ||
                             text.indexOf("微信") !== -1;
            if (hasKeyword || (id >= 0x7f0c6000 && id <= 0x7f0c7fff && text.length > 0 && text.length < 200)) {
                count++;
                console.log("[HIT:" + count + "] id=0x" + id.toString(16) +
                            " cls=" + this.getClass().getSimpleName() +
                            " text=" + JSON.stringify(text.substring(0, 100)));
            }
        }
        this.setText(cs);
    };

    // 拦截 Activity.onResume → 打印当前 Activity 类名
    var Activity = Java.use("android.app.Activity");
    Activity.onResume.implementation = function() {
        var clsName = this.getClass().getName();
        // 只关注 Profile/Settings/Self 相关
        if (clsName.indexOf("rofile") !== -1 ||
            clsName.indexOf("etting") !== -1 ||
            clsName.indexOf("elf") !== -1 ||
            clsName.indexOf("ersonal") !== -1 ||
            clsName.indexOf("About") !== -1) {
            console.log("[ACT] onResume: " + clsName);
        }
        this.onResume();
    };

    console.log("[PROBE2] hooks active");
    console.log("[PROBE2] 切到「我」Tab，观察 HIT 和 ACT 输出");
});
