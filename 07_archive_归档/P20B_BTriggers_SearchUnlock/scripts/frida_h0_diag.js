// Minimal diagnostic: can we even see h0 class on cold start?
console.log("[diag] start " + Date.now());

setTimeout(function () {
    Java.perform(function () {
        console.log("[diag] Java.perform entered");
        try {
            var h0 = Java.use("com.tencent.mm.ui.base.preference.h0");
            console.log("[diag] h0 class found: " + h0);
            console.log("[diag] h0 superclass: " + h0.class.getSuperclass().getName());
            var methods = h0.class.getDeclaredMethods();
            console.log("[diag] methods count: " + methods.length);
            for (var i = 0; i < Math.min(methods.length, 30); i++) {
                console.log("[diag] method " + i + ": " + methods[i].getName() + " " + methods[i].getParameterTypes().length + " params");
            }
        } catch (e) {
            console.log("[diag] ERROR: " + e);
        }
        console.log("[diag] done");
    });
}, 3000);
