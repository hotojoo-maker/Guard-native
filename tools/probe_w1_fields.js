/**
 * probe_w1_fields.js
 * 读 w1 (SnsCommentStorage) 所有方法名 + 参数字段
 * 用法：frida -U -n com.tencent.mm -l tools/probe_w1_fields.js
 * 然后让密友给你点一个赞，看日志
 */
Java.perform(function () {

    // ── 1. dump w1 全部方法名 ──────────────────────────────────────────────
    var W1 = Java.use("com.tencent.mm.plugin.sns.storage.w1");
    var methods = W1.class.getDeclaredMethods();
    console.log("[W1] total methods: " + methods.length);
    methods.forEach(function (m) {
        var params = m.getParameterTypes().map(function (p) { return p.getSimpleName(); }).join(", ");
        console.log("[W1] method: " + m.getName() + "(" + params + ") → " + m.getReturnType().getSimpleName());
    });

    // ── 2. hook 所有方法，点赞到来时打印调用 + 参数字段 ──────────────────
    methods.forEach(function (m) {
        var name = m.getName();
        try {
            W1[name].overloads.forEach(function (ov) {
                ov.implementation = function () {
                    var args = Array.prototype.slice.call(arguments);
                    console.log("\n[W1:CALL] " + name + " args=" + args.length);

                    // 对每个参数 dump 所有字段
                    args.forEach(function (arg, i) {
                        if (arg === null || arg === undefined) {
                            console.log("  arg[" + i + "] = null");
                            return;
                        }
                        var cls = arg.getClass ? arg.getClass() : null;
                        if (!cls) { console.log("  arg[" + i + "] = " + arg); return; }
                        console.log("  arg[" + i + "] type=" + cls.getName());
                        // dump 本类 + 父类字段
                        var c = cls;
                        while (c && c.getName() !== "java.lang.Object") {
                            c.getDeclaredFields().forEach(function (f) {
                                try {
                                    f.setAccessible(true);
                                    var v = f.get(arg);
                                    // 只打印有值的字段
                                    if (v !== null && v !== undefined && v.toString() !== "") {
                                        console.log("    ." + f.getName() + " [" + f.getType().getSimpleName() + "] = " + v);
                                    }
                                } catch (e) {}
                            });
                            c = c.getSuperclass();
                        }
                    });

                    return ov.apply(this, arguments);
                };
            });
        } catch (e) {}
    });

    console.log("[W1] ready — 让密友点赞，观察哪个方法被调用 + fromUserName 在哪个字段");
});
