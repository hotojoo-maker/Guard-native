/**
 * catfish_push_stack.js — Catfish 8.0.70 :push replaceNotification 调用栈
 * 目标：看到 replaceNotification(Message) 的调用者
 */
Java.perform(function () {

    try {
        Java.enumerateLoadedClasses({
            onMatch: function(name) {
                if (name.indexOf("notification") >= 0 && name.indexOf("com.tencent") >= 0) {
                    console.log("[SCAN] " + name);
                }
            },
            onComplete: function() {
                console.log("[SCAN] done");
            }
        });
    } catch(e) {
        console.log("[SCAN] err: " + e);
    }

    console.log("[STACK] === probe ready, wait for push ===");
});
