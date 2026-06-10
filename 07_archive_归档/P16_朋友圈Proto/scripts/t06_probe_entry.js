// t06_probe_entry.js — 验证 MomentsFilter.java 假设是否成立
// 问题1: MvvmList 类存在吗？m(List,bool) / s(List) 存在吗？
// 问题2: y1 adapter 能 Java.choose 到吗？
// 问题3: y1.H 字段类型是什么？

Java.perform(function () {
    var results = {};

    // --- 问题1: MvvmList ---
    try {
        var MvvmList = Java.use("com.tencent.mm.plugin.mvvmlist.MvvmList");
        results["MvvmList_exists"] = "YES";
        var methods = MvvmList.class.getDeclaredMethods();
        var names = [];
        for (var i = 0; i < methods.length; i++) {
            names.push(methods[i].getName() + "(" + methods[i].getParameterCount() + ")");
        }
        results["MvvmList_methods"] = names.join(", ");
    } catch (e) {
        results["MvvmList_exists"] = "NO — " + e.message;
    }

    // --- 问题2: y1 adapter Java.choose ---
    try {
        var found = 0;
        Java.choose("com.tencent.mm.plugin.sns.ui.improve.component.y1", {
            onMatch: function (ad) {
                found++;
                results["y1_found"] = "YES, instance#" + found;
                // 问题3: H 字段类型
                try {
                    var cls = ad.getClass();
                    for (var d = 0; cls != null && d < 5; d++) {
                        try {
                            var f = cls.getDeclaredField("H");
                            f.setAccessible(true);
                            var val = f.get(ad);
                            results["y1_H_type"] = val != null ? val.getClass().getName() : "null";
                            // o / p 字段
                            var hVal = val;
                            if (hVal != null) {
                                var hCls = hVal.getClass();
                                try {
                                    var fo = hCls.getDeclaredField("o");
                                    fo.setAccessible(true);
                                    var oVal = fo.get(hVal);
                                    results["H.o_type"] = oVal != null ? oVal.getClass().getName() : "null";
                                } catch (e2) { results["H.o"] = "NOT_FOUND"; }
                                // methods on H type
                                var hMethods = hCls.getDeclaredMethods();
                                var mNames = [];
                                for (var j = 0; j < hMethods.length && j < 20; j++) {
                                    mNames.push(hMethods[j].getName() + "(" + hMethods[j].getParameterCount() + ")");
                                }
                                results["H_methods_sample"] = mNames.join(", ");
                            }
                            break;
                        } catch (e) {}
                        cls = cls.getSuperclass();
                    }
                } catch (e) { results["y1_H_err"] = e.message; }
            },
            onComplete: function () {
                if (found === 0) results["y1_found"] = "NOT_FOUND (not in heap yet — open moments first)";
            }
        });
    } catch (e) {
        results["y1_choose_err"] = e.message;
    }

    // --- 打印结论 ---
    console.log("\n========== t06 探针结果 ==========");
    for (var k in results) {
        console.log("[" + k + "] " + results[k]);
    }
    console.log("==================================\n");
});
