/**
 * dump_sns_structure.js
 * 目标：抓 k24.b item 里 SnsObject (d 字段) 的完整结构
 *       重点：找点赞列表 / 评论列表 的字段名 + 数据类型
 *
 * 用法：
 *   frida -U -f com.tencent.mm --no-pause -l dump_sns_structure.js 2>&1 | tee sns_dump.log
 *   attach 后：进朋友圈，让你自己点过赞/评论过的帖子刷出来
 *   Ctrl+C 停
 *
 * 输出关键词：
 *   [SNS] === item #N  — 每个 k24.b 条目
 *   [LIST] fieldName   — 所有 List 类型字段（点赞/评论就在这里）
 *   [LIKE?] / [CMT?]   — 字段名含 like/comment/zan/pinglun 的命中
 */

"use strict";

var g_dumpCount = 0;
var MAX_ITEMS = 20;          // 最多 dump 20 条，够了
var MAX_LIST_ITEMS = 3;      // 每个 List 最多展开 3 个元素

// ──────────────── 工具函数 ────────────────

function getField(obj, name) {
    if (obj == null) return null;
    var cls = obj.getClass();
    for (var d = 0; cls != null && d < 8; d++) {
        try {
            var f = cls.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(obj);
        } catch (e) {}
        cls = cls.getSuperclass();
    }
    return null;
}

function allFields(obj) {
    if (obj == null) return [];
    var result = [];
    var cls = obj.getClass();
    for (var d = 0; cls != null && d < 5; d++) {
        var fs = cls.getDeclaredFields();
        for (var i = 0; i < fs.length; i++) result.push(fs[i]);
        cls = cls.getSuperclass();
    }
    return result;
}

function safeStr(v) {
    if (v == null) return "null";
    try { return String(v).substring(0, 120); } catch (e) { return "?"; }
}

// ──────────────── 核心 dump ────────────────

function dumpSnsObject(snsObj, idx) {
    if (snsObj == null) { console.log("[SNS] d == null, skip"); return; }

    var className = snsObj.getClass().getName();
    console.log("\n[SNS] === item #" + idx + " class=" + className + " ===");

    var fields = allFields(snsObj);
    for (var i = 0; i < fields.length; i++) {
        var f = fields[i];
        var fname = f.getName();
        try {
            f.setAccessible(true);
            var val = f.get(snsObj);
            if (val == null) continue;

            var typeName = f.getType().getName();
            var isListType = typeName.indexOf("List") >= 0
                          || typeName.indexOf("ArrayList") >= 0
                          || (val.getClass && val.getClass().getName().indexOf("List") >= 0);

            // 判断是否 List (duck-typing)
            var listSize = -1;
            try { listSize = val.size(); } catch (e) {}

            if (listSize >= 0) {
                // 是个集合
                var marker = "";
                var fl = fname.toLowerCase();
                if (fl.indexOf("like") >= 0 || fl.indexOf("zan") >= 0) marker = " ★LIKE?";
                else if (fl.indexOf("comment") >= 0 || fl.indexOf("cmt") >= 0 || fl.indexOf("ping") >= 0) marker = " ★CMT?";
                else if (fl.indexOf("user") >= 0) marker = " ★USER?";

                console.log("[LIST] " + fname + " size=" + listSize + " itemType=" + typeName + marker);

                // 展开前 MAX_LIST_ITEMS 个元素
                var show = Math.min(listSize, MAX_LIST_ITEMS);
                for (var j = 0; j < show; j++) {
                    try {
                        var elem = val.get(j);
                        if (elem == null) { console.log("  [" + j + "] null"); continue; }
                        var eCls = elem.getClass().getName();
                        // 如果是简单字符串/数字直接打印
                        if (eCls === "java.lang.String" || eCls.indexOf("Integer") >= 0
                                || eCls.indexOf("Long") >= 0) {
                            console.log("  [" + j + "] " + safeStr(elem));
                        } else {
                            // 对象：展开一级字段
                            console.log("  [" + j + "] " + eCls + " {");
                            var eFs = allFields(elem);
                            for (var k = 0; k < Math.min(eFs.length, 15); k++) {
                                try {
                                    eFs[k].setAccessible(true);
                                    var ev = eFs[k].get(elem);
                                    if (ev == null) continue;
                                    // 如果是子 List 只打 size
                                    var evSize = -1;
                                    try { evSize = ev.size(); } catch (e) {}
                                    if (evSize >= 0) {
                                        console.log("    ." + eFs[k].getName() + " = List(size=" + evSize + ")");
                                    } else {
                                        console.log("    ." + eFs[k].getName() + " = " + safeStr(ev));
                                    }
                                } catch (e2) {}
                            }
                            console.log("  }");
                        }
                    } catch (e3) {
                        console.log("  [" + j + "] ERROR: " + e3);
                    }
                }
            } else {
                // 非 List，只打简单标量（避免太多噪音）
                var tn = f.getType().getName();
                if (tn === "java.lang.String" || tn === "int" || tn === "long"
                        || tn === "boolean" || tn.indexOf("Integer") >= 0) {
                    var fl2 = fname.toLowerCase();
                    if (fl2.indexOf("user") >= 0 || fl2.indexOf("id") >= 0
                            || fl2.indexOf("type") >= 0 || fl2.indexOf("time") >= 0
                            || fl2.indexOf("count") >= 0 || fname.startsWith("field_")) {
                        console.log("[FLD] " + fname + " = " + safeStr(val) + " (" + tn + ")");
                    }
                }
            }
        } catch (e) {}
    }
}

// ──────────────── 主 hook ────────────────

Java.perform(function () {
    console.log("[DUMP] dump_sns_structure ready — 进朋友圈下滑，会自动抓");
    console.log("[DUMP] 有点赞/评论的帖子刷出来效果最好");

    var ArrayList = Java.use("java.util.ArrayList");

    ArrayList.addAll.overload("java.util.Collection").implementation = function (coll) {
        if (coll != null && g_dumpCount < MAX_ITEMS) {
            try {
                var it = coll.iterator();
                while (it.hasNext() && g_dumpCount < MAX_ITEMS) {
                    var item = it.next();
                    if (item == null) continue;
                    if (item.getClass().getName() !== "k24.b") continue;

                    g_dumpCount++;
                    var d = getField(item, "d");
                    dumpSnsObject(d, g_dumpCount);

                    if (g_dumpCount >= MAX_ITEMS) {
                        console.log("\n[DUMP] 已抓 " + MAX_ITEMS + " 条，停止。Ctrl+C 可退出。");
                    }
                }
            } catch (e) {
                console.log("[DUMP] err: " + e);
            }
        }
        return this.addAll(coll);
    };

    console.log("[DUMP] ArrayList.addAll hook 已装，等待朋友圈数据...");
});
