// probe_ns_class.js - 找 ns.c 真实类名 + f195548x 内容
Java.perform(function() {
    // 通过已知字段 f332588b 的 setter 位置找 ns.c 类
    var FindMoreFriendsUI = Java.use("com.tencent.mm.ui.FindMoreFriendsUI");
    FindMoreFriendsUI.L1.implementation = function() {
        this.L1();
        // 拿 f195548x（newer snsobj = 密友 wxid）
        var newSnsWxid = this.f195548x.value;
        var commentCount = this.f195551y.value;
        console.log("[PROBE] L1() called");
        console.log("[PROBE] f195548x (newer snsobj wxid) = " + newSnsWxid);
        console.log("[PROBE] f195551y (comment count)    = " + commentCount);
    };
    // 找 ns.c 的真实类名
    Java.enumerateLoadedClasses({
        onMatch: function(name) {
            if (name.indexOf("ns") !== -1 && name.length < 20) {
                console.log("[CLASS] " + name);
            }
        },
        onComplete: function() { console.log("[CLASS] scan done"); }
    });
});
