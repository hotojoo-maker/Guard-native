// test_clear_fmf_badge.js
// 目标: 验证 FindMoreFriendsUI.E + AbstractTabChildPreference.m/p 就是红点控制字段
// 用法: frida -U -n com.tencent.mm -l tools/test_clear_fmf_badge.js
//
// 步骤:
//   1. 确保发现 tab 红点亮着（不要进入发现页，只要主界面看到红点就行）
//   2. attach 后等 5 秒，看红点是否消失
//   3. 同时观察 [TEST] 日志确认字段清零成功

Java.perform(function () {

    setTimeout(function () {
        console.log("[TEST] scanning for FindMoreFriendsUI...");

        Java.choose("com.tencent.mm.ui.FindMoreFriendsUI", {
            onMatch: function (inst) {
                console.log("[TEST] FMF instance found: " + inst.getClass().getName());

                // ── 1. 清零 FindMoreFriendsUI.E ────────────────────────────
                try {
                    var eField = inst.getClass().getDeclaredField("E");
                    eField.setAccessible(true);
                    var before = eField.getBoolean(inst);
                    eField.setBoolean(inst, false);
                    console.log("[TEST] FMF.E: " + before + " → false");
                } catch (e) {
                    console.log("[TEST] FMF.E clear FAILED: " + e);
                }

                // ── 2. 清零 AbstractTabChildPreference.m 和 .p ───────────
                var cls = inst.getClass().getSuperclass();
                var found = false;
                while (cls && cls.getName() !== "java.lang.Object") {
                    var simpleName = cls.getSimpleName();
                    console.log("[TEST] checking superclass: " + cls.getName());

                    // 找到含 Tab 或 Preference 字样的父类
                    if (simpleName.indexOf("Tab") >= 0 || simpleName.indexOf("Preference") >= 0
                            || simpleName.indexOf("Abstract") >= 0) {
                        try {
                            var mf = cls.getDeclaredField("m");
                            mf.setAccessible(true);
                            var mBefore = mf.getBoolean(inst);
                            mf.setBoolean(inst, false);
                            console.log("[TEST] " + simpleName + ".m: " + mBefore + " → false");
                            found = true;
                        } catch (e) {}
                        try {
                            var pf = cls.getDeclaredField("p");
                            pf.setAccessible(true);
                            var pBefore = pf.getBoolean(inst);
                            pf.setBoolean(inst, false);
                            console.log("[TEST] " + simpleName + ".p: " + pBefore + " → false");
                        } catch (e) {}
                        if (found) break;
                    }
                    cls = cls.getSuperclass();
                }

                // ── 3. 调用可能的 badge 刷新方法 ─────────────────────────
                // 尝试调用 FMF 上所有 (boolean) 参数的方法，传 false，刷新 tab 标题
                var refreshCalled = false;
                var candidates = ["g1", "h1", "k1", "setRedDot", "setBadge",
                                   "setTabTips", "updateBadge", "refreshBadge"];
                candidates.forEach(function (mn) {
                    try {
                        var m = inst.getClass().getDeclaredMethod(mn,
                            Java.use("java.lang.String").class, Java.use("java.lang.Boolean").class);
                        m.setAccessible(true);
                        m.invoke(inst, "album_dyna_photo_ui_title",
                            Java.use("java.lang.Boolean").FALSE);
                        console.log("[TEST] called " + mn + "(title, false) OK");
                        refreshCalled = true;
                    } catch (e1) {
                        try {
                            // 尝试 (String, boolean) 原始类型
                            var m2 = inst.getClass().getDeclaredMethod(mn,
                                Java.use("java.lang.String").class, Java.use("java.lang.Boolean").TYPE);
                            m2.setAccessible(true);
                            m2.invoke(inst, "album_dyna_photo_ui_title", false);
                            console.log("[TEST] called " + mn + "(title, false) [primitive] OK");
                            refreshCalled = true;
                        } catch (e2) {}
                    }
                });

                // ── 4. 验证清零后的字段值 ──────────────────────────────────
                setTimeout(function () {
                    try {
                        var eCheck = inst.getClass().getDeclaredField("E");
                        eCheck.setAccessible(true);
                        console.log("[TEST:verify] FMF.E after clear = " + eCheck.getBoolean(inst));
                    } catch (e) {}

                    // 检查父类 m/p
                    var cls2 = inst.getClass().getSuperclass();
                    while (cls2 && cls2.getName() !== "java.lang.Object") {
                        try {
                            var mf2 = cls2.getDeclaredField("m");
                            mf2.setAccessible(true);
                            console.log("[TEST:verify] " + cls2.getSimpleName() + ".m = "
                                + mf2.getBoolean(inst));
                        } catch (e) {}
                        try {
                            var pf2 = cls2.getDeclaredField("p");
                            pf2.setAccessible(true);
                            console.log("[TEST:verify] " + cls2.getSimpleName() + ".p = "
                                + pf2.getBoolean(inst));
                        } catch (e) {}
                        cls2 = cls2.getSuperclass();
                    }

                    console.log("[TEST] ==== 验证完毕。请看手机发现 tab 红点是否消失 ====");
                }, 500);
            },
            onComplete: function () {
                console.log("[TEST] FMF scan done");
            }
        });
    }, 3000); // 等 3 秒确保 WeChat 完全恢复前台

    console.log("[TEST] test_clear_fmf_badge loaded. 请把微信切到前台主界面...");
});
