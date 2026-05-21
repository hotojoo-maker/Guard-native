// patch_zero.js — 把 sns_control_flag 直接归零
Java.perform(function() {
    setTimeout(function() {
        try {
            var ctx = Java.use("android.app.ActivityThread").currentApplication().getApplicationContext();
            var sp = ctx.getSharedPreferences("com.tencent.mm_preferences", 0);
            var val = sp.getInt("sns_control_flag", -1);
            console.log("[BEFORE] sns_control_flag=" + val);
            sp.edit().putInt("sns_control_flag", 0).commit();
            var after = sp.getInt("sns_control_flag", -1);
            console.log("[AFTER]  sns_control_flag=" + after);
            console.log("[OK] 现在强杀微信重启，看红点是否消失");
        } catch(e) { console.log("[ERR] " + e); }
    }, 1000);
});
