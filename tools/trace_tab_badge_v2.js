// trace_tab_badge_v2.js — 找启动时 tab 红点来源
// 策略：hook MMKV 所有读取方法，找返回正数/true 的 key
// 同时枚举 w1 全部方法名（供参考）

Java.perform(function() {

    // ── 1. 枚举 w1 所有方法名，只打印不 hook（避免 ANR）──────────────
    try {
        var w1Cls = Java.use("com.tencent.mm.plugin.sns.storage.w1");
        var methods = w1Cls.class.getDeclaredMethods();
        console.log("[W1] total methods: " + methods.length);
        methods.forEach(function(m) {
            console.log("[W1-METHOD] " + m.getName()
                + "(" + Java.array('java.lang.Class', m.getParameterTypes()).length + " args)"
                + " -> " + m.getReturnType().getName());
        });
    } catch(e) { console.log("[W1] enum error: " + e); }

    // ── 2. MMKV.decodeBool — 找 true 的 key ────────────────────────
    try {
        var MMKV = Java.use("com.tencent.mmkv.MMKV");
        // decodeBool(String key)
        var db1 = MMKV.decodeBool.overload('java.lang.String');
        db1.implementation = function(key) {
            var ret = db1.call(this, key);
            if (ret) console.log("[MMKV-BOOL] " + key + " = true");
            return ret;
        };
        // decodeBool(String key, boolean defaultValue)
        var db2 = MMKV.decodeBool.overload('java.lang.String', 'boolean');
        db2.implementation = function(key, def) {
            var ret = db2.call(this, key, def);
            if (ret) console.log("[MMKV-BOOL] " + key + " = true  (def=" + def + ")");
            return ret;
        };
        console.log("[MMKV] decodeBool hooked");
    } catch(e) { console.log("[MMKV-BOOL] error: " + e); }

    // ── 3. MMKV.decodeInt — 找返回 1-99 的 key ──────────────────────
    try {
        var MMKV2 = Java.use("com.tencent.mmkv.MMKV");
        var di1 = MMKV2.decodeInt.overload('java.lang.String');
        di1.implementation = function(key) {
            var ret = di1.call(this, key);
            if (ret > 0 && ret < 200) console.log("[MMKV-INT] " + key + " = " + ret);
            return ret;
        };
        var di2 = MMKV2.decodeInt.overload('java.lang.String', 'int');
        di2.implementation = function(key, def) {
            var ret = di2.call(this, key, def);
            if (ret > 0 && ret < 200) console.log("[MMKV-INT] " + key + " = " + ret + " (def=" + def + ")");
            return ret;
        };
        console.log("[MMKV] decodeInt hooked");
    } catch(e) { console.log("[MMKV-INT] error: " + e); }

    // ── 4. MMKV.decodeLong — 找返回正数的 key ───────────────────────
    try {
        var MMKV3 = Java.use("com.tencent.mmkv.MMKV");
        var dl2 = MMKV3.decodeLong.overload('java.lang.String', 'long');
        dl2.implementation = function(key, def) {
            var ret = dl2.call(this, key, def);
            if (ret > 0 && ret < 200) console.log("[MMKV-LONG] " + key + " = " + ret);
            return ret;
        };
        console.log("[MMKV] decodeLong hooked");
    } catch(e) { console.log("[MMKV-LONG] error: " + e); }

    console.log("[INIT] all hooks installed, watching for 60s...");
});
