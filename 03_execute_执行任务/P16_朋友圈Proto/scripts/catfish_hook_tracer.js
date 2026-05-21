// catfish_hook_tracer.js — 抓 Catfish 装的所有 XposedBridge.hookMethod 调用
// 用法: frida -U -f com.tencent.mm --no-pause -l catfish_hook_tracer.js

Java.perform(function () {
    console.log("[TRACER] 启动，等待 hookMethod 调用...");

    var XposedBridge = Java.use("de.robv.android.xposed.XposedBridge");

    XposedBridge.hookMethod.implementation = function (method, callback) {
        try {
            var cls  = method.getDeclaringClass().getName();
            var name = method.getName();

            // 过滤掉我们自己的模块（com.ghost.assist）
            var cbClass = callback.getClass().getName();
            if (cbClass.indexOf("com.ghost.assist") < 0) {
                console.log("[HOOK] " + cls + "." + name + "  ← " + cbClass);
            }
        } catch (e) {}

        return this.hookMethod(method, callback);
    };

    console.log("[TRACER] hookMethod 已拦截，进朋友圈后看输出");
});
