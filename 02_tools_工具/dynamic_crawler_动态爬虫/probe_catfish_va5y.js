// probe_catfish_va5y.js — 追踪 hookNewCon 的触发源
// 目标: 找到哪个 WeChat 方法产生了 ArrayList<va5.y>

Java.perform(function() {
    console.log("[VA5-PROBE] Attached");

    // Hook hookNewCon 入口，打印调用栈
    var MainEntry = Java.use("com.catfish.newvip.MainEntry");
    var orig = MainEntry.hookNewCon;
    MainEntry.hookNewCon.implementation = function(list) {
        if (list && list.size() > 0) {
            var item = list.get(0);
            console.log("[VA5-PROBE] hookNewCon list.size=" + list.size() + " item.class=" + item.getClass().getName());
            // 枚举 item 的所有字段
            var fields = item.getClass().getDeclaredFields();
            for (var i = 0; i < fields.length; i++) {
                fields[i].setAccessible(true);
                console.log("[VA5-PROBE]   field: " + fields[i].getName() + " type=" + fields[i].getType().getName());
            }
            // 调用栈
            var e = Java.use("java.lang.Exception").$new();
            console.log("[VA5-PROBE] Stack (WeChat caller):\n" + Java.use("android.util.Log").getStackTraceString(e));
        }
        return orig.call(this, list);
    };

    // 同时 hook va5.y 的构造函数（如果存在）
    try {
        var va5y = Java.use("va5.y");
        console.log("[VA5-PROBE] va5.y found, fields:");
        var fields = va5y.class.getDeclaredFields();
        for (var i = 0; i < fields.length; i++) {
            console.log("[VA5-PROBE]   " + fields[i].getName() + " : " + fields[i].getType().getName());
        }
    } catch(e) {
        console.log("[VA5-PROBE] va5.y not found by name: " + e);
    }

    console.log("[VA5-PROBE] Ready. Trigger H↔V now.");
});
