/**
 * probe_dnd_toggle.js
 * 目标: 捕获微信「消息免打扰」toggle 触发的内部方法 + 存储写入
 *
 * 跑法:
 *   frida -U -n com.tencent.mm -l probe_dnd_toggle.js --no-pause
 *   然后手动切换一次「消息免打扰」开关（聊天右上角 → 查找聊天记录 旁边的 ···）
 *
 * 输出格式:
 *   [DND] className.methodName(args) → result
 *   [DND]   stack: ...
 *
 * 层次:
 *   L1  SQLiteDatabase.update/execSQL — WCDB 写 notifyType 字段（最可靠，字段名不混淆）
 *   L2  MMKV.encode — 部分版本用 MMKV 持久化 notify 状态
 *   L3  SharedPreferences.putBoolean/putInt — 兜底
 *   L4  类扫描 — hook com.tencent.mm.* 中名字含 notify/mute/disturb 的方法
 */

"use strict";

Java.perform(function () {
    var TAG = "[DND]";

    // ── 工具：取精简调用栈（前 10 帧，去掉 Frida 自身） ──────────────────────
    function stack() {
        var s = "";
        try {
            var frames = Java.use("java.lang.Thread")
                .currentThread()
                .getStackTrace();
            for (var i = 2; i < Math.min(frames.length, 14); i++) {
                var f = frames[i];
                var cls = f.getClassName();
                // 只保留微信自己的帧 + android framework 入口
                if (cls.indexOf("com.tencent.mm") >= 0
                        || cls.indexOf("android.") >= 0
                        || cls.indexOf("java.lang.reflect") >= 0) {
                    s += "\n    " + cls + "." + f.getMethodName()
                       + "(" + f.getFileName() + ":" + f.getLineNumber() + ")";
                }
            }
        } catch (e) { s = " [stack err: " + e + "]"; }
        return s;
    }

    // ── L1: SQLiteDatabase ───────────────────────────────────────────────────
    // WCDB 继承 SQLiteDatabase，所有写操作最终走这里
    // notifyType 是 rcontact 表里不混淆的列名 → 这层最可靠
    try {
        var SQLiteDB = Java.use("android.database.sqlite.SQLiteDatabase");

        var _update = SQLiteDB.update.overload(
            "java.lang.String",
            "android.content.ContentValues",
            "java.lang.String",
            "[Ljava.lang.String;"
        );
        _update.implementation = function (table, cv, where, wArgs) {
            var res = _update.call(this, table, cv, where, wArgs);
            try {
                var cvStr = cv != null ? cv.toString() : "null";
                if (/notify|mute|disturb|dnd|silent/i.test(cvStr + table)) {
                    console.log("\n" + TAG + " [SQLite.update]"
                        + "\n    table=" + table
                        + "\n    cv=" + cvStr
                        + "\n    where=" + where
                        + "\n    → rows=" + res
                        + stack());
                }
            } catch (e) {}
            return res;
        };

        // execSQL(String) — 有时直接 INSERT OR REPLACE
        var _exec1 = SQLiteDB.execSQL.overload("java.lang.String");
        _exec1.implementation = function (sql) {
            if (/notify|mute|disturb|dnd|silent/i.test(sql)) {
                console.log("\n" + TAG + " [SQLite.execSQL]"
                    + "\n    sql=" + sql
                    + stack());
            }
            return _exec1.call(this, sql);
        };

        // execSQL(String, Object[]) — 带参数的变体
        var _exec2 = SQLiteDB.execSQL.overload("java.lang.String", "[Ljava.lang.Object;");
        _exec2.implementation = function (sql, bindArgs) {
            if (/notify|mute|disturb|dnd|silent/i.test(sql)) {
                var argsStr = bindArgs != null
                    ? Java.use("java.util.Arrays").toString(bindArgs)
                    : "null";
                console.log("\n" + TAG + " [SQLite.execSQL+args]"
                    + "\n    sql=" + sql
                    + "\n    args=" + argsStr
                    + stack());
            }
            return _exec2.call(this, sql, bindArgs);
        };

        console.log(TAG + " L1 SQLite hooks ready");
    } catch (e) {
        console.log(TAG + " L1 SQLite hook ERR: " + e);
    }

    // ── L2: MMKV ────────────────────────────────────────────────────────────
    // 找 MMKV 类（类名在不同版本可能变）
    try {
        var mmkvFound = null;
        Java.enumerateLoadedClasses({
            onMatch: function (name) {
                if (!mmkvFound && name === "com.tencent.mmkv.MMKV") {
                    mmkvFound = name;
                }
            },
            onComplete: function () {
                if (!mmkvFound) {
                    console.log(TAG + " L2 MMKV class not loaded yet, skip");
                    return;
                }
                var MMKV = Java.use(mmkvFound);
                var overloads = [
                    ["boolean",         "boolean"],
                    ["int",             "int"],
                    ["long",            "long"],
                    ["java.lang.String","java.lang.String"],
                ];
                overloads.forEach(function (pair) {
                    try {
                        var ov = MMKV.encode.overload("java.lang.String", pair[0]);
                        ov.implementation = function (k, v) {
                            var r = ov.call(this, k, v);
                            if (/notify|mute|disturb|dnd|silent/i.test(k)) {
                                console.log("\n" + TAG + " [MMKV.encode<" + pair[1] + ">]"
                                    + "\n    key=" + k + "  val=" + v
                                    + stack());
                            }
                            return r;
                        };
                    } catch (e2) { /* 不是所有重载都有 */ }
                });
                console.log(TAG + " L2 MMKV hooks ready (" + mmkvFound + ")");
            }
        });
    } catch (e) {
        console.log(TAG + " L2 MMKV hook ERR: " + e);
    }

    // ── L3: SharedPreferences ───────────────────────────────────────────────
    try {
        // Android 内部实现类名
        var EditorImpl = Java.use("com.android.internal.util.XmlUtils");  // 先 probe 一下
    } catch (e) { /* 不要紧，下面改 */ }

    try {
        var SP = Java.use("android.app.SharedPreferencesImpl$EditorImpl");

        SP.putBoolean.implementation = function (k, v) {
            if (/notify|mute|disturb|dnd|silent/i.test(k)) {
                console.log("\n" + TAG + " [SP.putBoolean]"
                    + "\n    key=" + k + "  val=" + v
                    + stack());
            }
            return this.putBoolean(k, v);
        };
        SP.putInt.implementation = function (k, v) {
            if (/notify|mute|disturb|dnd|silent/i.test(k)) {
                console.log("\n" + TAG + " [SP.putInt]"
                    + "\n    key=" + k + "  val=" + v
                    + stack());
            }
            return this.putInt(k, v);
        };
        console.log(TAG + " L3 SharedPreferences hooks ready");
    } catch (e) {
        console.log(TAG + " L3 SP hook ERR (normal on some ROMs): " + e);
    }

    // ── L4: 类扫描 — com.tencent.mm.* 方法名含关键词 ───────────────────────
    // 扫描时机：enumerateLoadedClasses 对已加载类有效；
    // 如果点击时还未命中，说明相关类尚未加载（微信懒加载）——此时 L1 SQLite 必然已命中。
    var DND_METHOD_RE = /[Nn]otif[yY]|[Ss]etNotif|[Dd][Nn][Dd]|[Dd]isturb|[Ss]ilent|[Mm]uteSet|[Mm]uteSetting|setMute/;
    var scanned = 0;
    var hooked  = 0;

    Java.enumerateLoadedClasses({
        onMatch: function (className) {
            if (!className.startsWith("com.tencent.mm")) return;
            scanned++;
            try {
                var cls    = Java.use(className);
                var clsObj = cls.class;
                var methods = clsObj.getDeclaredMethods();

                for (var i = 0; i < methods.length; i++) {
                    var m     = methods[i];
                    var mName = m.getName();
                    if (!DND_METHOD_RE.test(mName)) continue;

                    // 立即调用，闭包捕获 className + mName
                    (function (cn, mn) {
                        try {
                            var overloads = cls[mn].overloads;
                            overloads.forEach(function (ov) {
                                ov.implementation = function () {
                                    var args    = Array.prototype.slice.call(arguments);
                                    var argsStr = args.map(function (a) {
                                        try { return String(a); } catch (e) { return "?"; }
                                    }).join(", ");

                                    var result;
                                    try {
                                        result = ov.apply(this, arguments);
                                    } catch (err) {
                                        console.log("\n" + TAG + " " + cn + "." + mn
                                            + "(" + argsStr + ") THREW " + err
                                            + stack());
                                        throw err;
                                    }
                                    console.log("\n" + TAG + " " + cn + "." + mn
                                        + "(" + argsStr + ") → " + result
                                        + stack());
                                    return result;
                                };
                                hooked++;
                            });
                        } catch (e2) { /* unhookable（native/abstract/interface），跳过 */ }
                    })(className, mName);
                }
            } catch (e) { /* 个别类 use() 失败，跳过 */ }
        },
        onComplete: function () {
            console.log(TAG + " L4 class scan done: scanned=" + scanned
                + "  method-hooks=" + hooked);
            console.log(TAG + " ✅ 所有层就绪，请手动点击「消息免打扰」开关...");
        }
    });

    console.log(TAG + " probe loading (L1/L2/L3 done, L4 scanning...)");
});
