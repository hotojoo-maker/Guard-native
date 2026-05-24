/**
 * probe_h0d.js — 验证 WeChat 8.0.71 :push 进程 h0.d(int)
 */
Java.perform(function () {
    try {
        var h0 = Java.use("h0");
        h0.d.overload("int").implementation = function(n) {
            console.log("[h0.d] in=" + n);
            return this.d(n);
        };
        console.log("[h0] h0.d(int) hooked ✅");
    } catch(e) {
        console.log("[h0] hook fail: " + e);
    }
    console.log("[h0] === probe ready ===");
});
