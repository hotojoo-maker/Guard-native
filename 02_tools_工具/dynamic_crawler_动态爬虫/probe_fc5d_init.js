/**
 * probe_fc5d_init.js
 * 目标：冷启动时 fc5.d 哪个方法触发了初始 DB 查询
 * 用法：frida -U -n com.tencent.mm -l probe_fc5d_init.js
 * 轻量：只 hook fc5.d，不超过 10 个方法
 */
Java.perform(function () {
    var TAG = "[fc5d]";
    var seen = {};          // 每个方法只打一次，避免刷屏
    var cls;

    try {
        cls = Java.use("fc5.d");
    } catch (e) {
        console.log(TAG + " class not found: " + e);
        return;
    }

    cls.class.getDeclaredMethods().forEach(function (m) {
        var name = m.getName();
        if (seen[name]) return;
        seen[name] = true;

        try {
            cls[name].overloads.forEach(function (ol) {
                ol.implementation = function () {
                    console.log(TAG + "." + name +
                        "(" + ol.argumentTypes.map(function(t){ return t.className; }).join(",") + ")");
                    return ol.apply(this, arguments);
                };
            });
        } catch (e) { /* 跳过无法 hook 的重载 */ }
    });

    console.log(TAG + " hooked. Cold-start WeChat now.");
});
