/**
 * T10: 从 k24.b 出发 dump LikeUserList/CommentUserList 字段路径
 *
 * 目的：确认 k24.b（或其子对象）内有哪些 List 字段，
 *       以及 item 内是否直接含 LikeUserList/CommentUserList，
 *       或者需要通过 k24.b.某字段 → SnsObject → LikeUserList 的路径。
 *
 * 用法：
 *   frida -U -p <WeChat PID> -l t10_dump_k24b_likes.js
 *   然后滑动朋友圈，等待看到有点赞/评论的帖子
 *
 * 注意：只 dump 5 条 k24.b item，避免日志刷爆。
 */

Java.perform(function () {
    console.log("[T10] ready — 滑动朋友圈，等待 k24.b 命中...");

    var ITEM_CLASS = "k24.b";
    var hitCount = 0;
    var MAX_HIT = 5;

    function dumpAllFields(obj, prefix, depth) {
        if (obj == null || depth > 4) return;
        try {
            var cls = obj.getClass();
            var className = cls.getName();
            console.log(prefix + "[class=" + className + "]");
            var fields = cls.getDeclaredFields();
            for (var i = 0; i < fields.length; i++) {
                var f = fields[i];
                try {
                    f.setAccessible(true);
                    var val = f.get(obj);
                    if (val == null) {
                        console.log(prefix + "  ." + f.getName() + " = null (" + f.getType().getName() + ")");
                        continue;
                    }
                    var typeName = f.getType().getName();
                    var valStr = "";
                    try { valStr = String(val); } catch(e) { valStr = "<err>"; }
                    // Truncate long strings
                    if (valStr.length > 80) valStr = valStr.substring(0, 80) + "...";
                    console.log(prefix + "  ." + f.getName() + " = " + valStr + " (" + typeName + ")");
                    // Recurse into non-primitive, non-String objects
                    if (typeName.indexOf("java.lang") < 0 && typeName.indexOf("int") < 0
                        && typeName.indexOf("long") < 0 && typeName.indexOf("boolean") < 0
                        && typeName.indexOf("float") < 0 && typeName.indexOf("double") < 0
                        && typeName.indexOf("[") < 0 && depth < 3) {
                        // For List types, dump first item
                        if (typeName.indexOf("List") >= 0 || typeName.indexOf("java.util") >= 0) {
                            var size = 0;
                            try { size = val.size(); } catch(e) {}
                            console.log(prefix + "    [List size=" + size + "]");
                            if (size > 0) {
                                try {
                                    var first = val.get(0);
                                    if (first != null) {
                                        dumpAllFields(first, prefix + "    [0].", depth + 1);
                                    }
                                } catch(e) {}
                            }
                        } else if (depth < 2) {
                            // Recurse into nested object (limit depth 2)
                            dumpAllFields(val, prefix + "  ." + f.getName() + ".", depth + 1);
                        }
                    }
                } catch (e2) {
                    console.log(prefix + "  ." + f.getName() + " [ERR: " + e2 + "]");
                }
            }
        } catch (e) {
            console.log(prefix + "[dump ERR: " + e + "]");
        }
    }

    // Hook ArrayList.addAll — same entry as MomentsFilter
    var addAll = Java.use("java.util.ArrayList").addAll.overload("java.util.Collection");
    addAll.implementation = function (coll) {
        var result = addAll.call(this, coll);
        if (hitCount >= MAX_HIT) return result;

        try {
            var iter = coll.iterator();
            while (iter.hasNext()) {
                var item = iter.next();
                if (item == null) continue;
                var name = item.getClass().getName();
                if (name !== ITEM_CLASS) break; // Not k24.b batch, skip
                hitCount++;
                console.log("\n========== k24.b ITEM #" + hitCount + " ==========");
                dumpAllFields(item, "", 0);
                if (hitCount >= MAX_HIT) {
                    console.log("[T10] " + MAX_HIT + " items dumped, done. Check output above for LikeUserList/CommentUserList paths.");
                    break;
                }
            }
        } catch (e) {
            console.log("[T10 ERR] " + e);
        }
        return result;
    };

    console.log("[T10] ArrayList.addAll hooked — 现在去滑动朋友圈");
});
