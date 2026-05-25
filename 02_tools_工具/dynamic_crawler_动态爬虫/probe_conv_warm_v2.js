/**
 * probe_conv_warm_v2.js — P_ConvWarm 第二轮爬虫
 * 目标：深挖 kc5.x 全类方法（找 String wxid 参数）+ dump kc5.y 字段
 * 用法：frida -U -f com.tencent.mm -l probe_conv_warm_v2.js
 */
var TAG = "[CW2]";
var started = Date.now();
var yRecords = [];

function ts() { return ((Date.now() - started) / 1000).toFixed(2) + "s"; }
function threadTag() { try { return Java.use("java.lang.Thread").currentThread().getName(); } catch (e) { return "?"; } }

function WXID_RE() { return /^(wxid_[A-Za-z0-9_]+|[A-Za-z0-9_]+@chatroom|[A-Za-z0-9_]+@app)$/; }
function isWxidLike(s) { return typeof s === 'string' && WXID_RE().test(s); }

function shortStack(n) {
    n = n || 6;
    var stack = Java.use("java.lang.Thread").currentThread().getStackTrace();
    var parts = [];
    for (var i = 2; i < Math.min(2 + n, stack.length); i++) {
        var f = stack[i];
        var cn = f.getClassName();
        // 跳过系统帧
        if (cn.indexOf("java.") === 0 || cn.indexOf("android.") === 0 || cn.indexOf("dalvik.") === 0) continue;
        parts.push(cn + "." + f.getMethodName());
    }
    return parts.join(" <- ");
}

function dumpWxidFields(obj, label) {
    label = label || "obj";
    var found = [];
    if (obj == null) return found;
    try {
        var cls = obj.getClass();
        while (cls != null && cls.getName() !== "java.lang.Object") {
            var fields = cls.getDeclaredFields();
            for (var i = 0; i < fields.length; i++) {
                var f = fields[i];
                try {
                    f.setAccessible(true);
                    var v = f.get(obj);
                    if (v == null) continue;
                    var vs = String(v);
                    if (isWxidLike(vs)) {
                        found.push(f.getName() + "=" + vs);
                    }
                } catch (e) { /* skip */ }
            }
            cls = cls.getSuperclass();
        }
    } catch (e) { /* skip */ }
    return found;
}

// ── kc5.y 构造 hook（dump 字段 + trace kc5.x）─────────────────

Java.perform(function () {
    console.log(TAG + " === P_ConvWarm v2 ===");

    // §1 kc5.y 构造 + 字段 dump
    try {
        var kc5y = Java.use("kc5.y");
        var ctors = kc5y.class.getDeclaredConstructors();
        var hookedCtor = 0;

        ctors.forEach(function (ctor) {
            var pTypes = ctor.getParameterTypes();
            var pNames = [];
            for (var i = 0; i < pTypes.length; i++) pNames.push(pTypes[i].getName());

            try {
                var ols = kc5y.$init.overloads;
                if (!ols) return;

                for (var j = 0; j < ols.length; j++) {
                    var ol = ols[j];
                    if (ol.argumentTypes.length !== pTypes.length) continue;
                    var match = true;
                    for (var k = 0; k < pTypes.length; k++) {
                        if (ol.argumentTypes[k].className !== pTypes[k]) match = false;
                    }
                    if (!match) continue;

                    ol.implementation = (function (pN) {
                        return function () {
                            var res = this.$init.apply(this, arguments);

                            // dump 构造后的字段
                            var wxidFields = dumpWxidFields(this, "kc5.y");
                            var rec = {
                                ts: ts(),
                                thread: threadTag(),
                                params: pN.join(","),
                                wxidFields: wxidFields.join("|"),
                                stack: shortStack(8),
                            };
                            yRecords.push(rec);

                            console.log(TAG + " y-ctor#" + yRecords.length + " @" + rec.ts +
                                " thread=" + rec.thread +
                                " params=(" + rec.params + ")" +
                                " wxidFields=[" + (rec.wxidFields || "none") + "]");
                            console.log(TAG + "   stack: " + rec.stack);

                            return res;
                        };
                    })(pNames);
                    hookedCtor++;
                }
            } catch (e) { console.log(TAG + " ctor err: " + e); }
        });

        console.log(TAG + " kc5.y ctors hooked=" + hookedCtor);
    } catch (e) {
        console.log(TAG + " kc5.y not found: " + e);
    }

    // §2 kc5.x 全部方法 hook（找 String 参数的方法）
    if (true) {
        try {
            var kc5x = Java.use("kc5.x");
            var seen = {};
            var methods = kc5x.class.getDeclaredMethods();
            var staticCandidates = [];

            methods.forEach(function (m) {
                var name = m.getName();
                if (seen[name]) return;
                seen[name] = true;

                // 记录静态方法（有 String 参数）
                var isStatic = (m.getModifiers() & 0x8) !== 0;
                var pTypes = m.getParameterTypes();
                var hasString = false;
                for (var i = 0; i < pTypes.length; i++) {
                    if (pTypes[i].getName() === "java.lang.String") hasString = true;
                }
                if (isStatic && hasString && pTypes.length <= 3) {
                    var pt = [];
                    for (var i = 0; i < pTypes.length; i++) pt.push(pTypes[i].getName());
                    staticCandidates.push(name + "(" + pt.join(",") + ")");
                }

                // hook 所有方法（打印带 String 参数或 wxid 的调用）
                try {
                    var ols = kc5x[name].overloads;
                    if (!ols) return;
                    ols.forEach(function (ol) {
                        ol.implementation = function () {
                            var argsWxid = [];
                            var hasStr = false;
                            for (var i = 0; i < arguments.length; i++) {
                                if (arguments[i] == null) continue;
                                var s = String(arguments[i]);
                                if (isWxidLike(s)) argsWxid.push("arg[" + i + "]=" + s);
                                if (ol.argumentTypes[i] && ol.argumentTypes[i].className === "java.lang.String") {
                                    hasStr = true;
                                    argsWxid.push("arg[" + i + "]='" + s.substring(0, 40) + "'");
                                }
                            }
                            if (argsWxid.length > 0 || (hasStr && name.length <= 4)) {
                                console.log(TAG + " kc5.x." + name + " @" + ts() +
                                    " thread=" + threadTag() +
                                    " static=" + ((m.getModifiers() & 0x8) !== 0) +
                                    " args=" + argsWxid.join("|"));
                                if (argsWxid.length > 0) {
                                    console.log(TAG + "   stack: " + shortStack(6));
                                }
                            }
                            return ol.apply(this, arguments);
                        };
                    });
                } catch (e) { /* skip */ }
            });

            console.log(TAG + " kc5.x methods hooked=" + Object.keys(seen).length);
            if (staticCandidates.length > 0) {
                console.log(TAG + " kc5.x static with String param:");
                staticCandidates.forEach(function (s) { console.log(TAG + "   " + s); });
            } else {
                console.log(TAG + " kc5.x: NO static methods with String param");
            }
        } catch (e) {
            console.log(TAG + " kc5.x not found: " + e);
        }
    }

    // §3 kc5.a.a hook（La）
    try {
        var kc5a = Java.use("kc5.a");
        kc5a.a.overloads.forEach(function (ol) {
            ol.implementation = function () {
                var hasWxid = false;
                for (var i = 0; i < arguments.length; i++) {
                    if (arguments[i] != null && isWxidLike(String(arguments[i]))) hasWxid = true;
                }
                if (hasWxid) {
                    console.log(TAG + " La @" + ts() + " thread=" + threadTag() + " hasWxid=YES");
                }
                return ol.apply(this, arguments);
            };
        });
        console.log(TAG + " kc5.a.a hooked");
    } catch (e) { console.log(TAG + " kc5.a.a: " + e); }

    // §3.5 ARG0 类型探针 — 独立轻量，只 hook kc5.x.k
    try {
        var kc5xForArg0 = Java.use("kc5.x");
        if (kc5xForArg0.k && kc5xForArg0.k.overloads) {
            kc5xForArg0.k.overloads.forEach(function (ol) {
                // 只 hook 有 2+ String 参数的重载（wxid 在 arg[1]）
                if (ol.argumentTypes.length < 2) return;
                var hasStr = false;
                for (var i = 0; i < ol.argumentTypes.length; i++) {
                    if (ol.argumentTypes[i].className === "java.lang.String") hasStr = true;
                }
                if (!hasStr) return;

                ol.implementation = function () {
                    var a0 = arguments[0];
                    var a0type = "null";
                    try {
                        a0type = a0.$className || a0.getClass().getName();
                    } catch (e) {
                        a0type = "typeof=" + (typeof a0);
                    }
                    console.log(TAG + " >>> ARG0_CLASS=" + a0type +
                        " | arg[1]=" + (arguments[1] ? String(arguments[1]).substring(0, 40) : "null"));
                    return ol.apply(this, arguments);
                };
                console.log(TAG + " ARG0 probe hooked on k(" + ol.argumentTypes.map(function(t){return t.className;}).join(",") + ")");
            });
        }
    } catch (e) {
        console.log(TAG + " ARG0 probe failed: " + e);
    }

    // §4 auto-report 120s
    setTimeout(function () {
        console.log("\n" + TAG + " ========== REPORT @" + ts() + " ==========");
        console.log(TAG + " kc5.y ctors: " + yRecords.length);
        yRecords.forEach(function (r) {
            console.log(TAG + " " + r.ts + " " + r.thread + " (" + r.params + ") wxid=" + (r.wxidFields || "none"));
        });
        console.log(TAG + " ========== END ==========\n");
    }, 120000);

    console.log(TAG + " === READY (auto-report 120s) ===");
    console.log(TAG + " 另一个号给本机密友发消息 → 搜索点会话");
});
