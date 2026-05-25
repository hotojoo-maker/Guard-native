/**
 * probe_conv_warm.js — P_ConvWarm 爬虫
 * 目标：trace wxid → kc5.y 对象构造的 DB 读路径，找 getConversation(wxid) 入口
 * 用法：frida -U -f com.tencent.mm -l probe_conv_warm.js
 *       或 warm-attach: frida -U -p <PID> -l probe_conv_warm.js
 *
 * 产出：候选方法列表（类名+方法名+参数类型），写 result.md
 * 铁律 33：只读不改，爬虫层 → 探针层 → 正式 hook
 */

var CONFIG = {
    TAG: "[CW]",
    MAX_STACK: 8,           // 调用栈帧数
    MAX_CONSTRUCTOR: 30,    // kc5.y 构造记录上限
    MAX_FC5D: 20,           // fc5.d 方法记录上限
    TARGET_WXID: null,      // 设为 null 则自动从 logcat/NCL 读取，或手动设
};

var stats = {
    yCtorCount: 0,          // kc5.y 构造次数
    fc5dCallCount: 0,       // fc5.d 方法调用次数
    yRecords: [],           // kc5.y 构造记录
    fc5dRecords: [],        // fc5.d 调用记录
    candidateMethods: [],   // 候选方法去重列表
    started: Date.now(),
};

// ── 工具函数 ────────────────────────────────────────────────

function WXID_RE() { return /^(wxid_[A-Za-z0-9_]+|[A-Za-z0-9_]+@chatroom|[A-Za-z0-9_]+@app)$/; }

function isWxidLike(s) {
    return typeof s === 'string' && WXID_RE().test(s);
}

function shortStack(stackStr, maxFrames) {
    if (!stackStr) return "no stack";
    maxFrames = maxFrames || CONFIG.MAX_STACK;
    var lines = stackStr.split("\n");
    // 跳过前 2 行（getStackTrace + 本函数）
    var start = 2;
    var end = Math.min(start + maxFrames, lines.length);
    return lines.slice(start, end).join(" <- ");
}

function threadTag() {
    try {
        var t = Java.use("java.lang.Thread").currentThread();
        return t.getName();
    } catch (e) {
        return "?";
    }
}

function ts() {
    return ((Date.now() - stats.started) / 1000).toFixed(2) + "s";
}

function addCandidate(cls, method, argTypes, source) {
    var key = cls + "." + method + "(" + (argTypes || []).join(",") + ")";
    for (var i = 0; i < stats.candidateMethods.length; i++) {
        if (stats.candidateMethods[i].key === key) {
            stats.candidateMethods[i].count++;
            return;
        }
    }
    stats.candidateMethods.push({
        key: key,
        cls: cls,
        method: method,
        argTypes: argTypes || [],
        source: source,
        count: 1,
    });
}

// ── kc5.y 构造器 hook ───────────────────────────────────────

function hookYConstructor() {
    try {
        var kc5y = Java.use("kc5.y");
        var ctors = kc5y.class.getDeclaredConstructors();

        ctors.forEach(function (ctor) {
            if (!ctor.isAccessible) ctor.setAccessible(true);
            var paramTypes = ctor.getParameterTypes();
            var paramNames = [];
            for (var i = 0; i < paramTypes.length; i++) {
                paramNames.push(paramTypes[i].getName());
            }

            // 用 overload 方式 hook
            try {
                var ol = kc5y.$init.overloads;
                if (!ol) return;
                for (var j = 0; j < ol.length; j++) {
                    var o = ol[j];
                    if (o.argumentTypes.length !== paramTypes.length) continue;
                    var match = true;
                    for (var k = 0; k < paramTypes.length; k++) {
                        if (o.argumentTypes[k].className !== paramTypes[k]) match = false;
                    }
                    if (!match) continue;

                    o.implementation = (function (pNames) {
                        return function () {
                            stats.yCtorCount++;
                            if (stats.yCtorCount > CONFIG.MAX_CONSTRUCTOR) {
                                return this.$init.apply(this, arguments);
                            }

                            // 提取 wxid 相关参数
                            var wxidArgs = [];
                            for (var i = 0; i < arguments.length; i++) {
                                var arg = arguments[i];
                                if (arg == null) continue;
                                var s = arg.toString();
                                if (isWxidLike(s)) {
                                    wxidArgs.push("arg[" + i + "]=" + s);
                                }
                            }

                            var stack = Java.use("java.lang.Thread").currentThread().getStackTrace();
                            var stackStr = "";
                            for (var s = 2; s < Math.min(2 + CONFIG.MAX_STACK, stack.length); s++) {
                                stackStr += stack[s].toString() + " <- ";
                            }

                            var rec = {
                                id: stats.yCtorCount,
                                ts: ts(),
                                thread: threadTag(),
                                paramTypes: pNames.join(","),
                                wxidArgs: wxidArgs.join("|"),
                                stack: stackStr,
                            };
                            stats.yRecords.push(rec);

                            // 记录候选方法：栈中每个非系统帧
                            for (var s = 2; s < Math.min(6, stack.length); s++) {
                                var frame = stack[s];
                                var cn = frame.getClassName();
                                var mn = frame.getMethodName();
                                // 排除系统/框架类
                                if (cn.indexOf("java.") === 0) continue;
                                if (cn.indexOf("android.") === 0) continue;
                                if (cn.indexOf("dalvik.") === 0) continue;
                                addCandidate(cn, mn, pNames, "y-ctor#" + stats.yCtorCount);
                            }

                            return this.$init.apply(this, arguments);
                        };
                    })(paramNames);
                }
            } catch (e) {
                console.log(CONFIG.TAG + " ctor hook err: " + e);
            }
        });

        console.log(CONFIG.TAG + " kc5.y ctors=" + ctors.length + " hooked");
    } catch (e) {
        console.log(CONFIG.TAG + " kc5.y class not found: " + e);
    }
}

// ── fc5.d 方法 hook ──────────────────────────────────────────

function hookFc5d() {
    var seen = {};
    try {
        var cls = Java.use("fc5.d");
        var methods = cls.class.getDeclaredMethods();

        methods.forEach(function (m) {
            var name = m.getName();
            if (seen[name]) return;
            seen[name] = true;

            try {
                var ols = cls[name].overloads;
                if (!ols) return;
                ols.forEach(function (ol) {
                    ol.implementation = function () {
                        stats.fc5dCallCount++;
                        var argTypes = [];
                        for (var i = 0; i < ol.argumentTypes.length; i++) {
                            argTypes.push(ol.argumentTypes[i].className);
                        }

                        // 检查参数中是否有 wxid
                        var wxidArgs = [];
                        for (var i = 0; i < arguments.length; i++) {
                            var arg = arguments[i];
                            if (arg == null) continue;
                            var s = arg.toString();
                            if (isWxidLike(s)) {
                                wxidArgs.push("arg[" + i + "]=" + s);
                            }
                        }

                        if (stats.fc5dCallCount <= CONFIG.MAX_FC5D) {
                            var stack = Java.use("java.lang.Thread").currentThread().getStackTrace();
                            var stackStr = "";
                            for (var s = 2; s < Math.min(2 + CONFIG.MAX_STACK, stack.length); s++) {
                                stackStr += stack[s].toString() + " <- ";
                            }

                            stats.fc5dRecords.push({
                                id: stats.fc5dCallCount,
                                ts: ts(),
                                thread: threadTag(),
                                method: name,
                                argTypes: argTypes.join(","),
                                wxidArgs: wxidArgs.join("|"),
                                stack: stackStr,
                            });

                            // 候选方法
                            for (var s = 2; s < Math.min(6, stack.length); s++) {
                                var frame = stack[s];
                                var cn = frame.getClassName();
                                var mn = frame.getMethodName();
                                if (cn.indexOf("java.") === 0) continue;
                                if (cn.indexOf("android.") === 0) continue;
                                if (cn.indexOf("dalvik.") === 0) continue;
                                addCandidate(cn, mn, argTypes, "fc5d-" + name + "#" + stats.fc5dCallCount);
                            }
                        }

                        return ol.apply(this, arguments);
                    };
                });
            } catch (e) { /* skip */ }
        });

        console.log(CONFIG.TAG + " fc5.d methods=" + Object.keys(seen).length + " hooked");
    } catch (e) {
        console.log(CONFIG.TAG + " fc5.d class not found: " + e);
    }
}

// ── kc5.a.a hook（La 热更新入口）─────────────────────────────

function hookKc5a() {
    try {
        var cls = Java.use("kc5.a");
        cls.a.overloads.forEach(function (ol) {
            ol.implementation = function () {
                var args = [];
                for (var i = 0; i < arguments.length; i++) {
                    if (arguments[i] == null) {
                        args.push("null");
                    } else {
                        var s = String(arguments[i]);
                        if (isWxidLike(s)) args.push("wxid=" + s);
                        else {
                            try { args.push(arguments[i].getClass().getSimpleName()); }
                            catch (e2) { args.push(typeof arguments[i]); }
                        }
                    }
                }
                console.log(CONFIG.TAG + " La(kc5.a.a) @" + ts() + " thread=" + threadTag() + " args=" + args.join(","));
                return ol.apply(this, arguments);
            };
        });
        console.log(CONFIG.TAG + " kc5.a.a hooked");
    } catch (e) {
        console.log(CONFIG.TAG + " kc5.a.a not found: " + e);
    }
}

// ── 扫描 fc5.d / kc5 下的静态方法（候选 getConversation 入口）──

function scanStaticMethods() {
    var classes = ["fc5.d", "kc5.a", "kc5.r0", "fc5.f", "fc5.e"];
    var found = [];

    classes.forEach(function (cn) {
        try {
            var cls = Java.use(cn);
            var methods = cls.class.getDeclaredMethods();
            methods.forEach(function (m) {
                var mod = m.getModifiers();
                var isStatic = (mod & 0x8) !== 0;
                if (!isStatic) return;
                var name = m.getName();
                var pTypes = m.getParameterTypes();
                var hasWxidParam = false;
                for (var i = 0; i < pTypes.length; i++) {
                    if (pTypes[i].getName() === "java.lang.String") {
                        hasWxidParam = true;
                    }
                }
                if (hasWxidParam && pTypes.length <= 2) {
                    var pt = [];
                    for (var i = 0; i < pTypes.length; i++) pt.push(pTypes[i].getName());
                    found.push(cn + "." + name + "(" + pt.join(",") + ")");
                }
            });
        } catch (e) { /* skip */ }
    });

    console.log(CONFIG.TAG + " static scan: " + found.length + " candidates with String param");
    found.forEach(function (f) { console.log(CONFIG.TAG + "   " + f); });
    return found;
}

// ── 报告 ─────────────────────────────────────────────────────

function cwReport() {
    console.log("\n" + CONFIG.TAG + " ========== CONV-WARM REPORT @" + ts() + " ==========");
    console.log(CONFIG.TAG + " kc5.y constructor hits: " + stats.yCtorCount);
    console.log(CONFIG.TAG + " fc5.d method hits: " + stats.fc5dCallCount);
    console.log(CONFIG.TAG + " candidate methods: " + stats.candidateMethods.length);

    console.log(CONFIG.TAG + "\n--- kc5.y constructor traces ---");
    stats.yRecords.forEach(function (r) {
        console.log(CONFIG.TAG + " #" + r.id + " @" + r.ts + " thread=" + r.thread +
            " params=" + r.paramTypes + " wxid=" + (r.wxidArgs || "none"));
        console.log(CONFIG.TAG + "   stack: " + r.stack);
    });

    console.log(CONFIG.TAG + "\n--- fc5.d method traces ---");
    stats.fc5dRecords.forEach(function (r) {
        console.log(CONFIG.TAG + " #" + r.id + " @" + r.ts + " thread=" + r.thread +
            " " + r.method + "(" + r.argTypes + ")" + " wxid=" + (r.wxidArgs || "none"));
        console.log(CONFIG.TAG + "   stack: " + r.stack);
    });

    console.log(CONFIG.TAG + "\n--- candidate methods (ranked) ---");
    stats.candidateMethods.sort(function (a, b) { return b.count - a.count; });
    stats.candidateMethods.forEach(function (c, i) {
        console.log(CONFIG.TAG + " " + (i + 1) + ". " + c.key + " count=" + c.count + " source=" + c.source);
    });

    console.log(CONFIG.TAG + " ========== END REPORT ==========\n");
}

// ── 主入口 ───────────────────────────────────────────────────

Java.perform(function () {
    console.log(CONFIG.TAG + " === P_ConvWarm crawler starting ===");
    console.log(CONFIG.TAG + " target: wxid → kc5.y construction path");

    hookYConstructor();
    hookFc5d();
    hookKc5a();
    scanStaticMethods();

    // auto-report after 90s（足够用户收发消息 + 搜索点会话）
    setTimeout(function () {
        console.log(CONFIG.TAG + " === auto-report ===");
        cwReport();
    }, 90000);

    console.log(CONFIG.TAG + " === READY (auto-report in 90s) ===");
    console.log(CONFIG.TAG + " 1. 另一个号给本机密友发消息（本机收）");
    console.log(CONFIG.TAG + " 2. 搜索框搜密友昵称，点进会话");
    console.log(CONFIG.TAG + " 3. 等 auto-report，或手动 cwReport()");
    console.log(CONFIG.TAG + " hooks: kc5.y ctors + fc5.d methods + kc5.a.a");
});
