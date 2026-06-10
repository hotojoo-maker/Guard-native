/**
 * dump_sns_8071.js — 8.0.71 SnsInfo 字段 + 点赞/评论列表探针
 *
 * 目标：
 *   1. 确认 wxid_ 好友能被抓到
 *   2. 找 SnsInfo 里 field_likeList / field_commentList 的实际字段名
 *   3. 展开 likeList/commentList 元素，找 wxid 字段
 *
 * 路径：la4.p.b1() → SnsInfo(rl.ta) → field_*
 *
 * 用法（设备已 adb 连接，frida-server 已运行）：
 *   frida -U -f com.tencent.mm -l dump_sns_8071.js
 *   → 进朋友圈下滑，让有点赞/评论的帖子刷出来
 *   → Ctrl+C 停
 */

"use strict";

var g_count = 0;
var MAX = 15;
var ITEM_CLASS = "la4.p";

function allFields(obj) {
    var result = [];
    var cls = obj.getClass();
    for (var d = 0; cls != null && d < 6; d++) {
        var fs = cls.getDeclaredFields();
        for (var i = 0; i < fs.length; i++) result.push(fs[i]);
        cls = cls.getSuperclass();
    }
    return result;
}

function safe(v) {
    if (v == null) return "null";
    try { return String(v).substring(0, 80); } catch(e) { return "?"; }
}

function dumpSnsInfo(snsInfo, idx) {
    var cn = snsInfo.getClass().getName();
    console.log("\n[SNS#" + idx + "] class=" + cn);

    var fields = allFields(snsInfo);
    for (var i = 0; i < fields.length; i++) {
        var f = fields[i];
        var fn = f.getName();
        try {
            f.setAccessible(true);
            var val = f.get(snsInfo);
            if (val == null) continue;

            // 检查是否集合
            var sz = -1;
            try { sz = val.size(); } catch(e) {}

            if (sz >= 0) {
                var tag = "";
                var fnl = fn.toLowerCase();
                if (fnl.indexOf("like") >= 0 || fnl.indexOf("zan") >= 0) tag = " ←LIKE✓";
                else if (fnl.indexOf("comment") >= 0 || fnl.indexOf("cmt") >= 0) tag = " ←CMT✓";
                else if (fnl.indexOf("user") >= 0) tag = " ←USER?";

                console.log("  [LIST] " + fn + " size=" + sz + tag);

                // 展开前2个元素
                for (var j = 0; j < Math.min(sz, 2); j++) {
                    try {
                        var elem = val.get(j);
                        if (elem == null) continue;
                        var ecn = elem.getClass().getName();
                        console.log("    [" + j + "] " + ecn);
                        var eFs = allFields(elem);
                        for (var k = 0; k < Math.min(eFs.length, 12); k++) {
                            try {
                                eFs[k].setAccessible(true);
                                var ev = eFs[k].get(elem);
                                if (ev == null) continue;
                                var evSz = -1;
                                try { evSz = ev.size(); } catch(e) {}
                                if (evSz >= 0) {
                                    console.log("      ." + eFs[k].getName() + " = List(sz=" + evSz + ")");
                                } else {
                                    console.log("      ." + eFs[k].getName() + " = " + safe(ev));
                                }
                            } catch(e) {}
                        }
                    } catch(e) {}
                }
            } else {
                // 标量：只打 field_ 前缀 + 关键字段
                var fnl2 = fn.toLowerCase();
                if (fn.startsWith("field_") || fnl2.indexOf("user") >= 0
                        || fnl2.indexOf("id") >= 0 || fnl2.indexOf("type") >= 0
                        || fnl2.indexOf("time") >= 0 || fnl2.indexOf("count") >= 0) {
                    console.log("  [FLD] " + fn + " = " + safe(val));
                }
            }
        } catch(e) {}
    }
}

function getDeclaredFieldInHierarchy(cls, name) {
    for (var d = 0; cls != null && d < 8; d++) {
        try { return cls.getDeclaredField(name); } catch(e) {}
        cls = cls.getSuperclass();
    }
    return null;
}

Java.perform(function() {
    console.log("[PROBE] dump_sns_8071 ready — 进朋友圈下滑");

    var ArrayList = Java.use("java.util.ArrayList");
    ArrayList.addAll.overload("java.util.Collection").implementation = function(coll) {
        var result = this.addAll(coll);
        if (g_count >= MAX || coll == null) return result;
        try {
            var it = coll.iterator();
            while (it.hasNext() && g_count < MAX) {
                var item = it.next();
                if (item == null) continue;
                if (item.getClass().getName() !== ITEM_CLASS) continue;

                // 拿 wxid
                var wxid = "(unknown)";
                try {
                    var b1 = item.getClass().getMethod("b1");
                    b1.setAccessible(true);
                    var snsInfo = b1.invoke(item);
                    if (snsInfo != null) {
                        g_count++;
                        // wxid
                        try {
                            var uf = getDeclaredFieldInHierarchy(snsInfo.getClass(), "field_userName");
                            if (uf != null) uf.setAccessible(true);
                            wxid = String(uf.get(snsInfo));
                        } catch(e) {}
                        console.log("\n══════ item #" + g_count + " wxid=" + wxid + " ══════");
                        dumpSnsInfo(snsInfo, g_count);
                    }
                } catch(e) {
                    console.log("[PROBE] b1() failed: " + e);
                }
            }
        } catch(e) {
            console.log("[PROBE] err: " + e);
        }
        return result;
    };

    console.log("[PROBE] hook installed, waiting...");
});
