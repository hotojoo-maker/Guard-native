/**
 * T10: 轻量内存扫 wxid_
 * 不 hook，只用 Java.choose 枚举 ArrayList 实例
 * 检查 item 是否包含 wxid_ 特征
 *
 * 用法: frida -U -p <微信PID> -l t10_scan_wxid.js
 */

var checked = 0;
var found = 0;
var wxidLists = [];

function extractWxid(item) {
    if (!item) return null;
    try {
        // 尝试常见 wxid getter
        var cls = item.getClass();
        var methods = cls.getMethods();
        for (var i = 0; i < methods.length; i++) {
            var m = methods[i];
            if (m.getParameterCount() !== 0) continue;
            var name = m.getName();
            // 只试已知的 wxid getter，不暴力反射
            if (name === "h1" || name === "j1" || name === "i1" ||
                name === "v" || name === "getUsername" || name === "getUserName") {
                try {
                    var r = m.invoke(item);
                    if (r && typeof r === 'string' && r.startsWith("wxid_")) {
                        return r;
                    }
                } catch(e) {}
            }
        }
        // 也试试 field_username / field_userName
        var f = cls.getDeclaredField("field_username");
        f.setAccessible(true);
        var v = f.get(item);
        if (v && typeof v === 'string' && v.startsWith("wxid_")) return v;
    } catch(e) {}
    return null;
}

function checkArrayList(list) {
    if (!list || list.size() < 2) return; // 跳过太小的列表
    checked++;

    var firstItem = list.get(0);
    if (!firstItem) return;

    var clsName = firstItem.getClass().getName();

    // 跳过明显不是会话/通讯录的类
    if (clsName.startsWith("java.") || clsName.startsWith("android.") ||
        clsName.startsWith("kotlin.") || clsName.startsWith("dalvik.") ||
        clsName.startsWith("com.tencent.mm.plugin.mvvmlist") ||
        clsName.includes("$")) return;

    // 检查前 3 个 item
    var wxids = [];
    var limit = Math.min(3, list.size());
    for (var i = 0; i < limit; i++) {
        var wxid = extractWxid(list.get(i));
        if (wxid) wxids.push(wxid);
    }

    if (wxids.length > 0) {
        found++;
        var entry = {
            cls: clsName,
            size: list.size(),
            wxids: wxids
        };
        wxidLists.push(entry);
        console.log("[T10] ★ List<" + clsName + "> sz=" + list.size() +
                    " wxids=" + JSON.stringify(wxids));
    } else if (wxidLists.length === 0) {
        // 还没找到时，也输出候选类名供参考
        if (checked <= 20 || checked % 50 === 0) {
            console.log("[T10] · List<" + clsName + "> sz=" + list.size());
        }
    }
}

Java.perform(function() {
    console.log("[T10] === Lightweight ArrayList scan for wxid_ ===\n");
    console.log("[T10] Scanning (no hooks, read-only)...");

    var start = Date.now();

    Java.choose("java.util.ArrayList", {
        onMatch: function(list) {
            checkArrayList(list);
        },
        onComplete: function() {
            var elapsed = ((Date.now() - start) / 1000).toFixed(1);
            console.log("\n[T10] === Done in " + elapsed + "s ===");
            console.log("[T10] Checked=" + checked + " Found=" + found);

            if (found === 0) {
                console.log("[T10] No wxid_ lists in memory (cache cleared or data not loaded)");
                console.log("[T10] → Open WeChat, scroll chat list, then re-run");
            } else {
                console.log("[T10] wxid lists found:");
                for (var i = 0; i < wxidLists.length; i++) {
                    var e = wxidLists[i];
                    console.log("  " + (i+1) + ". " + e.cls + " sz=" + e.size +
                                " wxids=" + JSON.stringify(e.wxids));
                }
            }
        }
    });
});
