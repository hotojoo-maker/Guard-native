// ============================================================
// 密友过滤 — 朋友圈 v12
// 策略: 实例级ArrayList拦截 + 定期扫描防替换 + INIT清理
// ============================================================

var CONFIG = {
    HIDE_WXIDS: [
        "wxid_toghm7m6uqsr12"
    ],
    VERBOSE: false,
    INIT_DELAY_MS: 3000,
    SCAN_INTERVAL_MS: 3000
};
var HIDE_WXIDS = CONFIG.HIDE_WXIDS;

function log(msg) { console.log("[M12] " + msg); }
function vlog(msg) { if (CONFIG.VERBOSE) log(msg); }

function getField(obj, name) {
    if (obj == null) return null;
    var cls = obj.getClass();
    for (var d = 0; cls != null && d < 10; d++) {
        try { var f = cls.getDeclaredField(name); f.setAccessible(true); return f.get(obj); } catch (e) {}
        cls = cls.getSuperclass();
    }
    return null;
}

function getWxid(item) {
    try {
        if (item == null || item.getClass().getName() !== "k24.b") return null;
        var d = getField(item, "d");
        if (d == null) return null;
        var u = getField(d, "field_userName");
        return u != null ? u.toString() : null;
    } catch (e) { return null; }
}

function isTarget(wxid) {
    return wxid != null && HIDE_WXIDS.indexOf(wxid) >= 0;
}

var g_filtered = 0;
var g_hooksInstalled = false;
var g_oHash = null;
var g_pHash = null;
var g_adapter = null;
var g_scanCount = 0;

Java.perform(function () {
    log("=== v12 实例级拦截 + 防替换扫描 ===");
    log("目标: " + HIDE_WXIDS.length + " wxids");

    try {
        Java.classFactory.loader = Java.use("android.app.ActivityThread").currentApplication().getClassLoader();
        log("loader OK");
    } catch (e) { log("loader fail: " + e); }

    // ===== 在 ArrayList 实例上安装过滤 =====
    function installOnList(list, label) {
        if (list == null) return false;
        var arrList = Java.cast(list, Java.use("java.util.ArrayList"));

        // add(Object) NOT hooked — too high frequency (every RecyclerView layout pass
        // calls add for view items). Data enters via addAll, which we hook below.

        arrList.addAll.overload('java.util.Collection').implementation = function (coll) {
            if (coll != null) {
                try {
                    var jc = Java.cast(coll, Java.use("java.util.Collection"));
                    var it = jc.iterator();
                    var toRemove = [];
                    while (it.hasNext()) {
                        var item = it.next();
                        if (item != null && item.getClass().getName() === "k24.b" && isTarget(getWxid(item))) {
                            toRemove.push(item);
                        }
                    }
                    for (var r = 0; r < toRemove.length; r++) {
                        jc.remove(toRemove[r]);
                        g_filtered++;
                    }
                    if (toRemove.length > 0)
                        log(label + ".addAll blocked: " + toRemove.length + " (total=" + g_filtered + ")");
                } catch (e) {}
            }
            return this.addAll(coll);
        };

        return true;
    }

    // ===== 清理列表中已有目标 =====
    function cleanList(rawList, label) {
        if (rawList == null) return 0;
        var jl = Java.cast(rawList, Java.use("java.util.List"));
        var size = jl.size();
        var removed = 0;
        for (var i = size - 1; i >= 0; i--) {
            try {
                var item = jl.get(i);
                if (item != null && item.getClass().getName() === "k24.b" && isTarget(getWxid(item))) {
                    jl.remove(i);
                    removed++;
                    g_filtered++;
                }
            } catch (e) {}
        }
        if (removed > 0) log("clean " + label + ": removed " + removed + " (total=" + g_filtered + ")");
        return removed;
    }

    // ===== 扫描 + 安装 =====
    function scanAndInstall() {
        g_scanCount++;
        try {
            Java.choose("com.tencent.mm.plugin.sns.ui.improve.component.y1", {
                onMatch: function (ad) {
                    if (g_hooksInstalled) return;
                    g_adapter = ad;
                    var mv = getField(ad, "H");
                    if (mv == null) return;
                    var o = getField(mv, "o");
                    var p = getField(mv, "p");
                    if (o == null) return;

                    var oHash = o.hashCode();
                    var pHash = (p != null) ? p.hashCode() : 0;

                    g_oHash = oHash;
                    g_pHash = pHash;
                    installOnList(o, "o");
                    if (p != null && pHash !== oHash) {
                        installOnList(p, "p");
                    } else if (p != null) {
                        vlog("o/p same instance");
                    }
                    var oSize = Java.cast(o, Java.use("java.util.List")).size();
                    g_hooksInstalled = true;
                    // NO INIT cleanup — modifying ArrayList during RecyclerView active state = crash
                    log("INSTALL OK — o=" + oHash + " size=" + oSize + " (no clean, hooks guard new data)");
                },
                onComplete: function () {
                    if (!g_hooksInstalled && g_scanCount <= 3) {
                        vlog("scan#" + g_scanCount + " y1 not found, will retry");
                    }
                }
            });
        } catch (e) { vlog("scan err: " + e); }
    }

    // ===== 替换检测（只检测已安装的） =====
    function checkReplace() {
        if (!g_hooksInstalled || g_adapter == null) return;
        try {
            var mv = getField(g_adapter, "H");
            if (mv == null) { g_hooksInstalled = false; return; }
            var o = getField(mv, "o");
            var p = getField(mv, "p");
            if (o == null) { g_hooksInstalled = false; return; }

            var oHash = o.hashCode();
            var pHash = (p != null) ? p.hashCode() : 0;

            if (oHash !== g_oHash) {
                log("REPLACE: o " + g_oHash + "→" + oHash + " p " + g_pHash + "→" + pHash);
                g_oHash = oHash;
                g_pHash = pHash;
                installOnList(o, "o");
                if (p != null && pHash !== oHash) installOnList(p, "p");
                // No clean on replace — same reason: RecyclerView active state = crash risk
                log("hooks re-installed on new instance (total=" + g_filtered + ")");
            }
        } catch (e) {}
    }

    // Start scanning
    setTimeout(function () {
        Java.perform(function () {
            scanAndInstall();
            // Combined scan: installs if not found, checks replace if installed
            setInterval(function () {
                Java.perform(function () {
                    if (!g_hooksInstalled) {
                        scanAndInstall();
                    } else {
                        checkReplace();
                    }
                });
            }, CONFIG.SCAN_INTERVAL_MS);
        });
    }, CONFIG.INIT_DELAY_MS);

    log("========================================");
    log("[READY] v12 — " + CONFIG.SCAN_INTERVAL_MS + "ms scan");
    log("========================================");
});
