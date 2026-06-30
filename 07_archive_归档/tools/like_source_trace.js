// like_source_trace.js — 定向 SnsStorage 写入溯源（无 ANR 版）
// 目标：找到「他人给你点赞/评论」的原始数据对象（聚合桶上游）
//
// ★ 安全约束：禁止全局 LinkedList/ArrayList hook（微信 ANR）
//   改为定向 hook：
//     1. w1 (SnsCommentStorage) 的全部写入方法
//     2. 枚举 com.tencent.mm.plugin.sns.storage.* 中接受 Object 参数的方法
//     3. LinkedList.add / ArrayList.add 只在 w1 实例持有的 List 上拦截（实例级）
//
// 用法：frida -U com.tencent.mm -l like_source_trace.js
// 操作：启动 → 让朋友给你点赞 → 等 [SRC] 输出

"use strict";

var WINDOW_MS = 30000;
var startTime = Date.now();
var printed = {};     // class → true，防重复打印
var diagSeen = {};    // 诊断去重

function elapsed() { return Date.now() - startTime; }

function dumpObj(label, obj) {
    if (!obj) return;
    var cn = obj.getClass().getName();
    var sb = label + " class=" + cn + "\n";
    try {
        var cls = obj.getClass();
        for (var depth = 0; cls !== null && depth < 4; depth++) {
            var fields = cls.getDeclaredFields();
            for (var i = 0; i < fields.length; i++) {
                var f = fields[i];
                try {
                    f.setAccessible(true);
                    var v = f.get(obj);
                    if (v === null) continue;
                    var typeName = f.getType().getName();
                    if (typeName === "java.lang.String"
                            || typeName === "int" || typeName === "long"
                            || typeName === "boolean") {
                        var vs = v.toString();
                        var mark = (vs.indexOf("wxid_") === 0) ? " ★WXID" : "";
                        sb += "  [d" + depth + "] " + f.getName()
                            + " (" + typeName + ") = "
                            + (vs.length > 60 ? vs.substring(0, 57) + "…" : vs)
                            + mark + "\n";
                    }
                } catch(e) {}
            }
            try { cls = cls.getSuperclass(); } catch(e) { cls = null; }
        }
    } catch(e) { sb += "  dump err: " + e + "\n"; }
    console.log(sb);
}

function hasWxid(obj) {
    if (!obj) return false;
    try {
        var cls = obj.getClass();
        for (var depth = 0; cls !== null && depth < 3; depth++) {
            var fields = cls.getDeclaredFields();
            for (var i = 0; i < fields.length; i++) {
                var f = fields[i];
                if (f.getType().getName() !== "java.lang.String") continue;
                try {
                    f.setAccessible(true);
                    var v = f.get(obj);
                    if (v !== null && v.toString().indexOf("wxid_") === 0) return true;
                } catch(e) {}
            }
            try { cls = cls.getSuperclass(); } catch(e) { cls = null; }
        }
    } catch(e) {}
    return false;
}

Java.perform(function() {
    console.log("[like_src] ⏱ " + WINDOW_MS/1000 + "s 窗口开始 — 定向 SnsStorage hook，无 ANR");

    // ────────────────────────────────────────────────────────────────────
    // 1. 定向：hook w1 (SnsCommentStorage 8.0.71) 的所有写入方法
    //    iOS 8.0.71 实证：StatusAffManager.insertLike:(SnsAction) / insertComment:(SnsCommentInfo)
    //    SnsAction.fromUserName = 点赞/评论者 wxid
    //    目标：在 Android 混淆方法中找到等价写入路径
    // ────────────────────────────────────────────────────────────────────
    var w1Cls = null;
    try {
        w1Cls = Java.use("com.tencent.mm.plugin.sns.storage.w1");
        console.log("[like_src] w1 loaded");

        var w1Methods = w1Cls.class.getDeclaredMethods();
        var hookedCount = 0;
        for (var i = 0; i < w1Methods.length; i++) {
            var m = w1Methods[i];
            var pt = m.getParameterTypes();
            // 只 hook 有参数的方法（getter 无参，跳过）
            if (pt.length === 0) continue;
            // 跳过纯原始类型参数（int/long/boolean 通常是计数写入，不含 wxid）
            var hasObjParam = false;
            for (var j = 0; j < pt.length; j++) {
                if (!pt[j].isPrimitive()) { hasObjParam = true; break; }
            }
            if (!hasObjParam) continue;

            (function(method) {
                try {
                    Java.hookMethod(method, {
                        onEnter: function(args) {
                            var e = elapsed();
                            if (e > WINDOW_MS) return;
                            var mn = method.getName();
                            // 扫每个 Object 参数，找含 wxid_ 的
                            for (var k = 0; k < args.length; k++) {
                                try {
                                    var arg = args[k];
                                    if (!arg) continue;
                                    if (hasWxid(arg)) {
                                        var cn = arg.getClass().getName();
                                        if (!printed[mn + "_" + cn]) {
                                            printed[mn + "_" + cn] = true;
                                            console.log("[SRC:w1." + mn + " +" + e + "ms]");
                                            dumpObj("  arg[" + k + "]", arg);
                                        }
                                    }
                                    // 如果参数是 List，扫第一个元素
                                    if (arg.getClass && arg.getClass().getName().indexOf("List") !== -1) {
                                        try {
                                            var list = Java.cast(arg, Java.use("java.util.List"));
                                            if (list.size() > 0) {
                                                var first = list.get(0);
                                                if (first && hasWxid(first)) {
                                                    var fcn = first.getClass().getName();
                                                    if (!printed["w1_list_" + fcn]) {
                                                        printed["w1_list_" + fcn] = true;
                                                        console.log("[SRC:w1." + mn + "[List] +" + e + "ms]");
                                                        dumpObj("  list[0]", first);
                                                    }
                                                }
                                            }
                                        } catch(le) {}
                                    }
                                } catch(ae) {}
                            }
                        }
                    });
                    hookedCount++;
                } catch(he) {}
            })(w1Methods[i]);
        }
        console.log("[like_src] w1 hooked " + hookedCount + " write methods");

        // 额外：打印 w1 所有方法名，帮助找到 insertLike/insertComment 的混淆名
        if (hookedCount > 0 && !diagSeen["w1_methods"]) {
            diagSeen["w1_methods"] = true;
            console.log("[like_src] w1 all methods:");
            var allMethods = w1Cls.class.getDeclaredMethods();
            for (var mi = 0; mi < allMethods.length; mi++) {
                var mm = allMethods[mi];
                var pts = mm.getParameterTypes();
                var paramStr = "";
                for (var pi = 0; pi < pts.length; pi++) {
                    paramStr += (pi > 0 ? ", " : "") + pts[pi].getSimpleName();
                }
                console.log("  " + mm.getName() + "(" + paramStr + ")"
                    + " → " + mm.getReturnType().getSimpleName());
            }
        }
    } catch(e) {
        console.log("[like_src] w1 not found: " + e);
    }

    // ────────────────────────────────────────────────────────────────────
    // 2. 定向：枚举 sns.storage.* 类，hook 接收 Object 参数的方法
    //    不用 enumerateLoadedClasses（重），改用已知候选类名列表
    // ────────────────────────────────────────────────────────────────────
    var SNS_STORAGE_CANDIDATES = [
        "com.tencent.mm.plugin.sns.storage.SnsCommentStorage",
        "com.tencent.mm.plugin.sns.storage.SnsStorage",
        "com.tencent.mm.plugin.sns.storage.SnsLikeStorage",
        "com.tencent.mm.plugin.sns.storage.SnsInteractionStorage",
        "com.tencent.mm.plugin.sns.storage.SnsNotifyStorage",
    ];

    SNS_STORAGE_CANDIDATES.forEach(function(cn) {
        try {
            var cls = Java.use(cn);
            var methods = cls.class.getDeclaredMethods();
            var cnt = 0;
            for (var i = 0; i < methods.length; i++) {
                var m = methods[i];
                var pt = m.getParameterTypes();
                if (pt.length === 0) continue;
                var hasObj = false;
                for (var j = 0; j < pt.length; j++) {
                    if (!pt[j].isPrimitive()) { hasObj = true; break; }
                }
                if (!hasObj) continue;
                (function(method, shortCn) {
                    try {
                        Java.hookMethod(method, {
                            onEnter: function(args) {
                                if (elapsed() > WINDOW_MS) return;
                                var mn = method.getName();
                                for (var k = 0; k < args.length; k++) {
                                    try {
                                        if (args[k] && hasWxid(args[k])) {
                                            var key = shortCn + "." + mn;
                                            if (!printed[key]) {
                                                printed[key] = true;
                                                console.log("[SRC:" + key + " +" + elapsed() + "ms]");
                                                dumpObj("  arg[" + k + "]", args[k]);
                                            }
                                        }
                                    } catch(ae) {}
                                }
                            }
                        });
                        cnt++;
                    } catch(he) {}
                })(methods[i], cn.substring(cn.lastIndexOf(".") + 1));
            }
            if (cnt > 0) console.log("[like_src] " + cn + " hooked " + cnt);
        } catch(e) {
            // 类不存在，忽略
        }
    });

    // ────────────────────────────────────────────────────────────────────
    // 3. w1.E1() 计数监控：观察何时从 0→N（有新互动写入）
    // ────────────────────────────────────────────────────────────────────
    if (w1Cls) {
        try {
            var lastE1 = -1;
            w1Cls.E1.implementation = function() {
                var result = this.E1();
                if (result !== lastE1) {
                    console.log("[SRC:w1.E1] unread: " + lastE1 + " → " + result
                                + " (+" + elapsed() + "ms)");
                    lastE1 = result;
                }
                return result;
            };
            console.log("[like_src] w1.E1 change-monitor hooked");
        } catch(e) {
            console.log("[like_src] w1.E1 hook failed: " + e);
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // 4. 汇总
    // ────────────────────────────────────────────────────────────────────
    setTimeout(function() {
        console.log("\n[like_src] ✅ 捕获窗口结束 (" + WINDOW_MS/1000 + "s)");
        var keys = Object.keys(printed);
        console.log("[like_src] 新原始载体: " + keys.length + " 个");
        keys.forEach(function(k) { console.log("  → " + k); });
        if (keys.length === 0) {
            console.log("[like_src] ⚠️  无命中 → 检查：");
            console.log("[like_src]   1. 看 w1 方法列表，找包含 insert/add/put/write 的方法名");
            console.log("[like_src]   2. 互动通知可能走 SyncKey protobuf 路径（StatusModelXmlParser 等价类）");
            console.log("[like_src]   3. 下一步：在 w1 方法列表里找 insertLike/insertComment 的混淆名");
        } else {
            console.log("\n[like_src] iOS 对照：");
            console.log("[like_src]   fromUserName 命中 → 对应 iOS SnsAction.fromUserName ✅");
            console.log("[like_src]   命中的方法 → 对应 iOS StatusAffManager.insertLike:/insertComment:");
        }
    }, WINDOW_MS + 200);
});
