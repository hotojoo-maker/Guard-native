// probe_catfish_fg_silence.js — Catfish 前台消息静默机制探针
// 监控: needInterruptActivity / hasChattingUser / emptyChatting / hookConversationUser / hookNewCon

Java.perform(function() {
    var TAG = "[FG-PROBE]";
    console.log(TAG + " === Catfish Foreground Silence Probe ===");

    // §1 needInterruptActivity — 前台 Activity 拦截
    try {
        var AC = Java.use("com.catfish.newvip.core.ActivityControll");
        var nia = AC.needInterruptActivity;
        AC.needInterruptActivity.implementation = function(activity) {
            var name = activity.getClass().getName();
            var intent = activity.getIntent();
            var voip = intent ? intent.getStringExtra("Voip_User") : null;
            var talker = intent ? intent.getStringExtra("Main_User") : null;
            console.log(TAG + " >>> needInterruptActivity");
            console.log(TAG + "     activity=" + name);
            console.log(TAG + "     Voip_User=" + voip + " Main_User=" + talker);
            var e = Java.use("java.lang.Exception").$new();
            console.log(TAG + "     Stack: " + Java.use("android.util.Log").getStackTraceString(e));
            return nia.call(this, activity);
        };
        console.log(TAG + " V needInterruptActivity");
    } catch(e) { console.log(TAG + " X needInterruptActivity: " + e); }

    // §2 hasChattingUser — 查询当前对话者是否为密友
    try {
        var UC = Java.use("com.catfish.newvip.core.UserControll");
        var hcu = UC.hasChattingUser;
        UC.hasChattingUser.implementation = function(user) {
            var result = hcu.call(this, user);
            console.log(TAG + " hasChattingUser(" + user + ") = " + result);
            if (result) {
                var e = Java.use("java.lang.Exception").$new();
                console.log(TAG + "     Stack: " + Java.use("android.util.Log").getStackTraceString(e));
            }
            return result;
        };
        console.log(TAG + " V hasChattingUser");
    } catch(e) { console.log(TAG + " X hasChattingUser: " + e); }

    // §3 emptyChatting — 密友聊天计数归零
    try {
        var UC2 = Java.use("com.catfish.newvip.core.UserControll");
        var ec = UC2.emptyChatting;
        UC2.emptyChatting.implementation = function(count, user) {
            var result = ec.call(this, count, user);
            if (result === 0 && count !== 0) {
                console.log(TAG + " >>> emptyChatting(" + count + ", " + user + ") → 0 (SUPPRESSED!)");
                var e = Java.use("java.lang.Exception").$new();
                console.log(TAG + "     Stack: " + Java.use("android.util.Log").getStackTraceString(e));
            }
            return result;
        };
        console.log(TAG + " V emptyChatting");
    } catch(e) { console.log(TAG + " X emptyChatting: " + e); }

    // §4 hookConversationUser — MainEntry 会话用户检查
    try {
        var ME = Java.use("com.catfish.newvip.MainEntry");
        var hcu2 = ME.hookConversationUser;
        ME.hookConversationUser.implementation = function(user) {
            var result = hcu2.call(this, user);
            if (result) {
                console.log(TAG + " >>> hookConversationUser(" + user + ") = TRUE (is hidden friend)");
                var e = Java.use("java.lang.Exception").$new();
                console.log(TAG + "     Stack: " + Java.use("android.util.Log").getStackTraceString(e));
            }
            return result;
        };
        console.log(TAG + " V hookConversationUser");
    } catch(e) { console.log(TAG + " X hookConversationUser: " + e); }

    // §5 hookNewCon — 实时列表过滤（精简版，只打印触发）
    try {
        var ME2 = Java.use("com.catfish.newvip.MainEntry");
        var hnc = ME2.hookNewCon;
        ME2.hookNewCon.implementation = function(list) {
            console.log(TAG + " hookNewCon size=" + (list ? list.size() : 0));
            return hnc.call(this, list);
        };
        console.log(TAG + " V hookNewCon");
    } catch(e) { console.log(TAG + " X hookNewCon: " + e); }

    // §6 enterVipMode / exitVipMode 精简
    try {
        var UC3 = Java.use("com.catfish.newvip.core.UserControll");
        var evm = UC3.enterVipMode;
        UC3.enterVipMode.implementation = function() {
            console.log(TAG + " >>> enterVipMode() [H→V]");
            return evm.call(this);
        };
        var xvm = UC3.exitVipMode;
        UC3.exitVipMode.implementation = function(isBack) {
            console.log(TAG + " >>> exitVipMode(" + isBack + ") [V→H]");
            return xvm.call(this, isBack);
        };
    } catch(e) {}

    console.log(TAG + " === Ready. Now test foreground message silencing ===");
    console.log(TAG + " Steps: 1. Set Catfish to HIDDEN mode (press Home if needed)");
    console.log(TAG + "       2. Have hidden friend send you a WeChat message");
    console.log(TAG + "       3. Bring Catfish back to foreground in HIDDEN mode");
    console.log(TAG + "       4. Try to open the hidden friend's chat (from search or notification)");
});
