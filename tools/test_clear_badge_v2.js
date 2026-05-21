// test_clear_badge_v2.js
// 目标: 用方法调用（而非直接清字段）消除发现 tab 红点
// 用法: frida -U -n com.tencent.mm -l tools/test_clear_badge_v2.js
//
// 并行测三条路径:
//   A. 零 ns.c.e (int 非零字段，可能是 badge 计数器)
//   B. 在 FMF 实例上调用 g1("album_dyna_photo_ui_title", false)
//   C. 在 LauncherUI 实例上找并零 o/p 字段 + invalidate

Java.perform(function () {

    setTimeout(function () {

        // ══════════════════════════════════════════════════════
        // 路径 A: 零 ns.c.e (INT 字段，红点亮时 = 1)
        // ══════════════════════════════════════════════════════
        try {
            var nsC = Java.use("ns.c");
            var eField = nsC.class.getDeclaredField("e");
            eField.setAccessible(true);
            var eBefore = eField.getInt(null);
            eField.setInt(null, 0);
            console.log("[A] ns.c.e: " + eBefore + " → 0");
        } catch (e) {
            console.log("[A] ns.c.e zero failed: " + e);
        }

        // ══════════════════════════════════════════════════════
        // 路径 B: FMF.g1("album_dyna_photo_ui_title", false)
        //         这是 suppressor 里已有的 callG1OnUi 路径，验证它能否消红点
        // ══════════════════════════════════════════════════════
        Java.choose("com.tencent.mm.ui.FindMoreFriendsUI", {
            onMatch: function (inst) {
                console.log("[B] FMF instance found");

                // 先 dump 所有 (String, boolean) 方法
                var cls = inst.getClass();
                var g1Found = false;
                for (var c = cls; c && c.getName() !== "java.lang.Object"; c = c.getSuperclass()) {
                    c.getDeclaredMethods().forEach(function (m) {
                        var pt = m.getParameterTypes();
                        if (pt.length !== 2) return;
                        if (pt[0].getName() !== "java.lang.String") return;
                        if (pt[1].getName() !== "boolean") return;
                        console.log("[B] candidate: " + c.getSimpleName() + "." + m.getName()
                            + "(String, boolean)");
                        // 尝试用 "album_dyna_photo_ui_title" 调用
                        try {
                            m.setAccessible(true);
                            m.invoke(inst, "album_dyna_photo_ui_title", false);
                            console.log("[B] called " + m.getName()
                                + "(album_dyna_photo_ui_title, false) ← 看红点是否消失");
                            g1Found = true;
                        } catch (e) {
                            console.log("[B] call failed: " + e);
                        }
                    });
                    if (g1Found) break;
                }
                if (!g1Found) console.log("[B] no (String,boolean) method found on FMF");
            },
            onComplete: function () { console.log("[B] FMF scan done"); }
        });

        // ══════════════════════════════════════════════════════
        // 路径 C: LauncherUI.o/p → false + 触发重绘
        // ══════════════════════════════════════════════════════
        Java.choose("com.tencent.mm.ui.LauncherUI", {
            onMatch: function (inst) {
                console.log("[C] LauncherUI instance found");

                // 清 o/p 字段
                var cls = inst.getClass();
                for (var c = cls; c && c.getName() !== "java.lang.Object"; c = c.getSuperclass()) {
                    ["o", "p"].forEach(function (fname) {
                        try {
                            var f = c.getDeclaredField(fname);
                            f.setAccessible(true);
                            var t = f.getType().getName();
                            if (t === "boolean") {
                                var before = f.getBoolean(inst);
                                f.setBoolean(inst, false);
                                console.log("[C] LauncherUI." + fname + ": " + before + " → false");
                            }
                        } catch (e) {}
                    });
                }

                // 找 LauncherUI 上所有 (int, boolean) 方法（tab position + show badge）
                for (var c2 = cls; c2 && c2.getName() !== "java.lang.Object"; c2 = c2.getSuperclass()) {
                    c2.getDeclaredMethods().forEach(function (m) {
                        var pt = m.getParameterTypes();
                        if (pt.length !== 2) return;
                        if (pt[0].getName() !== "int" && pt[0].getName() !== "java.lang.Integer") return;
                        if (pt[1].getName() !== "boolean") return;
                        console.log("[C] LauncherUI candidate: " + m.getName() + "(int, boolean)");
                        // tab 3 = 发现 (0-indexed) or 2, try both
                        [2, 3, 4].forEach(function (tabIdx) {
                            try {
                                m.setAccessible(true);
                                m.invoke(inst, tabIdx, false);
                                console.log("[C] called " + m.getName() + "(" + tabIdx + ", false)");
                            } catch (e) {}
                        });
                    });
                }

                // 找"setTabTips"/"setTabBadge"/"updateTab"类型方法
                var keywords = ["Tab", "Tips", "Badge", "Red", "Dot", "Notify"];
                for (var c3 = cls; c3 && c3.getName() !== "java.lang.Object"; c3 = c3.getSuperclass()) {
                    c3.getDeclaredMethods().forEach(function (m) {
                        var mn = m.getName();
                        var hasKeyword = false;
                        keywords.forEach(function (k) {
                            if (mn.toLowerCase().indexOf(k.toLowerCase()) >= 0) hasKeyword = true;
                        });
                        if (hasKeyword) {
                            console.log("[C] keyword method: " + mn
                                + "(" + m.getParameterTypes().length + " params)");
                        }
                    });
                }
            },
            onComplete: function () { console.log("[C] LauncherUI scan done"); }
        });

        // ══════════════════════════════════════════════════════
        // 路径 D: 直接找 tab bar 视图里的红点 View
        //         通过 LauncherUI 的 View 层次遍历
        // ══════════════════════════════════════════════════════
        Java.choose("com.tencent.mm.ui.LauncherUI", {
            onMatch: function (inst) {
                try {
                    // 找 Window → DecorView → 遍历找 tab bar
                    var window = inst.getWindow();
                    var decorView = window.getDecorView();
                    findAndHideBadgeViews(decorView, 0);
                } catch (e) {
                    console.log("[D] view traverse failed: " + e);
                }
            },
            onComplete: function () {}
        });

        console.log("[V2] all paths fired. 请观察红点变化...");

    }, 2000);

    function findAndHideBadgeViews(view, depth) {
        if (depth > 8 || view === null) return;
        try {
            var cn = view.getClass().getName();
            // 找可疑的红点 View（FinderRedDot 或 tab tips 相关）
            if (cn.indexOf("RedDot") >= 0 || cn.indexOf("TabTips") >= 0
                    || cn.indexOf("Badge") >= 0 || cn.indexOf("Tips") >= 0) {
                var vis = view.getVisibility();
                console.log("[D] badge-like view: " + cn + " visibility=" + vis);
                if (vis === 0) { // VISIBLE
                    // 尝试查 int 字段（badge count）
                    var fields = view.getClass().getDeclaredFields();
                    for (var i = 0; i < fields.length; i++) {
                        try {
                            fields[i].setAccessible(true);
                            var t = fields[i].getType().getName();
                            var v = fields[i].get(view);
                            if ((t === "int" || t === "boolean") && v !== 0 && v !== false) {
                                console.log("[D] *** " + view.getClass().getSimpleName()
                                    + "." + fields[i].getName() + " = " + v);
                            }
                        } catch (e) {}
                    }
                }
            }
            // 递归遍历子 View
            if (view.getClass().getName().indexOf("ViewGroup") >= 0
                    || view instanceof Java.use("android.view.ViewGroup")) {
                try {
                    var vg = Java.cast(view, Java.use("android.view.ViewGroup"));
                    var count = vg.getChildCount();
                    for (var i2 = 0; i2 < count; i2++) {
                        try { findAndHideBadgeViews(vg.getChildAt(i2), depth + 1); } catch (e) {}
                    }
                } catch (e) {}
            }
        } catch (e) {}
    }

    console.log("[V2] test_clear_badge_v2 loaded");
});
