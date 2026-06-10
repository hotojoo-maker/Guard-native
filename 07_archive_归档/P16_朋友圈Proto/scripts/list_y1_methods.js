// list_y1_methods.js — 列出 y1 所有方法名，不 hook，纯诊断
// 用法: frida -U -n com.tencent.mm --no-pause -l list_y1_methods.js

Java.perform(function () {
    // 设置 WeChat classloader（不设会找不到混淆类）
    try {
        var loader = Java.use("android.app.ActivityThread")
            .currentApplication().getApplicationContext().getClassLoader();
        Java.classFactory.loader = loader;
    } catch (e) { console.log("loader err: " + e); }

    var TARGET = "com.tencent.mm.plugin.sns.ui.improve.component.y1";
    try {
        var cls = Java.use(TARGET).class;

        // 1. 列出所有 declared methods（含包私有）
        console.log("=== y1 DeclaredMethods ===");
        var dm = cls.getDeclaredMethods();
        for (var i = 0; i < dm.length; i++) {
            var m = dm[i];
            var pts = m.getParameterTypes();
            var ptStr = [];
            for (var k = 0; k < pts.length; k++) ptStr.push(pts[k].getSimpleName());
            console.log("[dm] " + m.getName() + "(" + ptStr.join(",") + ")");
        }

        // 2. 列出所有 public methods（含继承）— 找 submitList / differ 等
        console.log("=== y1 PublicMethods (关键词: submit/differ/swap/update/set) ===");
        var pm = cls.getMethods();
        for (var j = 0; j < pm.length; j++) {
            var n = pm[j].getName();
            if (/submit|differ|swap|update|setList|setData|setItem|refresh/i.test(n)) {
                console.log("[pub] " + n + " declared in: " + pm[j].getDeclaringClass().getName());
            }
        }

        // 3. 列出所有字段（找 AsyncListDiffer / mDiffer 等）
        console.log("=== y1 DeclaredFields ===");
        var fields = cls.getDeclaredFields();
        for (var f = 0; f < fields.length; f++) {
            console.log("[field] " + fields[f].getName() + " : " + fields[f].getType().getName());
        }

        console.log("=== DONE ===");
    } catch (e) {
        console.log("ERROR: " + e);
    }
});
