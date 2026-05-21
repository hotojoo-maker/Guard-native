/**
 * find_8071_classnames.js v2 — 宽泛探测，不假设任何字段名/类名
 *
 * 策略：
 *   1. Hook ArrayList.addAll，把所有出现的非系统类名打印出来
 *   2. 统计频次，Top 类就是数据流的核心类
 *   3. 对高频类展开第一层字段，找 String 类型字段（field_userName 等）
 *
 * 用法：
 *   frida -U -f com.tencent.mm --no-pause -l find_8071_classnames.js 2>&1 | tee classnames.log
 *   启动后进朋友圈，下滑 20 秒，Ctrl+C
 */

"use strict";

var classCounts = {};   // className → count
var classFields = {};   // className → fields (first time only)
var g_total = 0;

var SKIP_PREFIXES = [
    "java.", "javax.", "android.", "kotlin.", "kotlinx.",
    "sun.", "com.google.", "androidx.", "org."
];

function shouldSkip(cn) {
    for (var i = 0; i < SKIP_PREFIXES.length; i++) {
        if (cn.indexOf(SKIP_PREFIXES[i]) === 0) return true;
    }
    return false;
}

function dumpFields(obj) {
    var lines = [];
    var cls = obj.getClass();
    for (var d = 0; cls != null && d < 4; d++) {
        var fs = cls.getDeclaredFields();
        for (var i = 0; i < fs.length; i++) {
            try {
                fs[i].setAccessible(true);
                var v = fs[i].get(obj);
                var tn = fs[i].getType().getName();
                var vstr = (v == null) ? "null" : String(v).substring(0, 80);
                lines.push("  ." + fs[i].getName() + " : " + tn + " = " + vstr);
            } catch(e) {
                lines.push("  ." + fs[i].getName() + " : [err]");
            }
        }
        cls = cls.getSuperclass();
    }
    return lines;
}

function printReport() {
    var entries = [];
    for (var k in classCounts) {
        if (classCounts.hasOwnProperty(k)) {
            entries.push([k, classCounts[k]]);
        }
    }
    entries.sort(function(a, b) { return b[1] - a[1]; });

    console.log("\n========== addAll 类名统计（total=" + g_total + "）==========");
    for (var i = 0; i < Math.min(15, entries.length); i++) {
        var cn = entries[i][0];
        console.log("[TOP" + (i+1) + "] " + cn + "  ×" + entries[i][1]);
        if (classFields[cn]) {
            var flines = classFields[cn];
            for (var j = 0; j < flines.length; j++) console.log(flines[j]);
        }
    }
    console.log("==================================================\n");
}

Java.perform(function () {
    console.log("[FIND2] 宽泛探测 ready — 进朋友圈下滑");

    var ArrayList = Java.use("java.util.ArrayList");

    ArrayList.addAll.overload("java.util.Collection").implementation = function (coll) {
        try {
            if (coll != null && !coll.isEmpty()) {
                var it = coll.iterator();
                while (it.hasNext()) {
                    var item = it.next();
                    if (item == null) continue;
                    var cn = item.getClass().getName();
                    if (shouldSkip(cn)) continue;

                    g_total++;
                    classCounts[cn] = (classCounts[cn] || 0) + 1;

                    // 第一次见到这个类 → 展开字段
                    if (!classFields[cn]) {
                        try {
                            classFields[cn] = dumpFields(item);
                        } catch(e) {
                            classFields[cn] = ["  [dump failed: " + e + "]"];
                        }
                        console.log("[NEW] " + cn + " (首次出现，字段见统计报告)");
                    }
                }
            }
        } catch(e) {}
        return this.addAll(coll);
    };

    // 每 8 秒打一次报告
    setInterval(function() {
        if (g_total > 0) printReport();
    }, 8000);

    console.log("[FIND2] Hook 就绪，进朋友圈下滑...");
});
