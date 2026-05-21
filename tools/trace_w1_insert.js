/**
 * trace_w1_insert.js
 * 目标：在 8.0.71 上找 SnsCommentStorage (w1) 的 insert* 方法
 *       以及 SnsAction 里存 fromUserName 的字段名
 *
 * 用法：
 *   1. 让密友给你点一个赞（或评论你的朋友圈）
 *   2. frida -U -n com.tencent.mm -l tools/trace_w1_insert.js
 *   3. 观察日志，找到 insert 方法 + fromUserName 字段
 *
 * 注意：w1 在 8.0.71 的混淆名可能不同，脚本会自动扫
 */

Java.perform(function () {
    var TAG = "[W1Insert]";

    // ── 1. 先找 SnsCommentStorage 的实际类名 ──────────────────────────────
    // 8.0.66 = com.tencent.mm.plugin.sns.storage.w1
    // 8.0.71 混淆名未知，通过搜索包含 "insertLike"/"insertComment" 的类来定位
    var targetClasses = [
        "com.tencent.mm.plugin.sns.storage.w1",   // 8.0.66
        "com.tencent.mm.plugin.sns.storage.x1",
        "com.tencent.mm.plugin.sns.storage.v1",
        "com.tencent.mm.plugin.sns.storage.u1",
        "com.tencent.mm.plugin.sns.storage.SnsCommentStorage",
    ];

    var found = null;
    for (var i = 0; i < targetClasses.length; i++) {
        try {
            Java.use(targetClasses[i]);
            found = targetClasses[i];
            console.log(TAG + " Found SnsCommentStorage: " + found);
            break;
        } catch (e) {}
    }

    if (!found) {
        console.log(TAG + " Known class names failed, scanning via Java.enumerateLoadedClasses...");
        Java.enumerateLoadedClasses({
            onMatch: function (cls) {
                if (cls.indexOf("sns.storage") !== -1 || cls.indexOf("SnsComment") !== -1) {
                    console.log(TAG + " Candidate: " + cls);
                }
            },
            onComplete: function () {
                console.log(TAG + " Scan done");
            }
        });
    }

    // ── 2. Hook w1 所有 insert* 方法（精准找入口）─────────────────────────
    if (found) {
        var W1 = Java.use(found);
        var methods = W1.class.getDeclaredMethods();
        var hooked = 0;
        methods.forEach(function (m) {
            var name = m.getName();
            // 找所有看起来像"插入"的方法
            if (name.toLowerCase().indexOf("insert") === -1 &&
                name.toLowerCase().indexOf("add") === -1 &&
                name.toLowerCase().indexOf("put") === -1) return;

            try {
                W1[name].overloads.forEach(function (ov) {
                    ov.implementation = function () {
                        var args = Array.prototype.slice.call(arguments);
                        console.log(TAG + " W1." + name + " called, args count=" + args.length);
                        // 对每个参数尝试读 fromUserName 字段
                        args.forEach(function (arg, idx) {
                            if (arg === null || arg === undefined) return;
                            try {
                                var cls = arg.getClass();
                                // 尝试常见字段名
                                ["fromUserName", "a", "b", "c", "d", "f"].forEach(function (fn) {
                                    try {
                                        var f = cls.getDeclaredField(fn);
                                        f.setAccessible(true);
                                        var v = f.get(arg);
                                        if (v && typeof v === "string" && v.length > 3) {
                                            console.log(TAG + "   arg[" + idx + "]." + fn + " = " + v);
                                        }
                                    } catch (e2) {}
                                });
                            } catch (e3) {}
                        });
                        var ret = ov.apply(this, arguments);
                        return ret;
                    };
                    hooked++;
                });
            } catch (e) {}
        });
        console.log(TAG + " Hooked " + hooked + " insert/add/put methods on " + found);
    }

    // ── 3. Hook ns.c 字段 b 的写入（找谁在设置 ns.c.b = true）──────────────
    // 8.0.71 实证：ns.c.b 在探针时为 false，但链路上应该有某个等价字段
    // 通过 hook ns.c 所有方法 + 静态字段监控来找
    try {
        var NsC = Java.use("ns.c");
        var nsMethods = NsC.class.getDeclaredMethods();
        console.log(TAG + " ns.c methods count: " + nsMethods.length);
        nsMethods.forEach(function (m) {
            var mname = m.getName();
            try {
                NsC[mname].overloads.forEach(function (ov) {
                    ov.implementation = function () {
                        var args = Array.prototype.slice.call(arguments);
                        console.log(TAG + " ns.c." + mname + "(" + args + ")");
                        var ret = ov.apply(this, arguments);
                        // 打印调用后 b 字段值
                        try {
                            var bField = NsC.class.getDeclaredField("b");
                            bField.setAccessible(true);
                            console.log(TAG + "   → ns.c.b = " + bField.getBoolean(null));
                        } catch (e2) {}
                        return ret;
                    };
                });
            } catch (e) {}
        });
    } catch (e) {
        console.log(TAG + " ns.c hook failed: " + e);
    }

    // ── 4. 监控 WeChatTabRedDotEvent / TabRedDotChangeEvent ───────────────
    var eventClasses = [
        "com.tencent.mm.ui.base.event.WeChatTabRedDotEvent",
        "com.tencent.mm.ui.base.event.TabRedDotChangeEvent",
    ];
    eventClasses.forEach(function (ecls) {
        try {
            var EC = Java.use(ecls);
            var ctor = EC.$init;
            if (ctor) {
                ctor.overloads.forEach(function (ov) {
                    ov.implementation = function () {
                        console.log(TAG + " " + ecls + " created: " + Array.prototype.slice.call(arguments));
                        // 打印调用栈
                        console.log(TAG + "   stack: " + Java.use("android.util.Log")
                            .getStackTraceString(Java.use("java.lang.Exception").$new()).substring(0, 500));
                        return ov.apply(this, arguments);
                    };
                });
            }
        } catch (e) {}
    });

    console.log(TAG + " Ready. 现在让密友给你点赞，观察 W1 insert 方法被调用。");
});
