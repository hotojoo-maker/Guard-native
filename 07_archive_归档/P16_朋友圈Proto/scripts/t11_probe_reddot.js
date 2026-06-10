/**
 * T11: 小红点 hook 点探针
 *
 * 目的：找到朋友圈"新消息"小红点/角标的 Java 方法，确认是否经过
 *       hookFriendStatus / hookFriendStatusItem 相关路径。
 *
 * 探针策略：
 *   1. 打开微信 → 发现 tab 上看到红点 → 进入朋友圈 → 红点消失
 *   2. 观察哪些方法被调用
 *
 * 已知候选类（来自 Catfish 参考）：
 *   - com.tencent.mm.plugin.sns.ui.SnsTimeLinePage（朋友圈主页）
 *   - com.tencent.mm.plugin.sns.model.SnsDataFacade（数据门面）
 *   - com.tencent.mm.plugin.discover.ui.LauncherUI$DiscoverRow（发现页行）
 *   - com.tencent.mm.ui.base.BaseUI（通用 Badge 设置）
 *
 * 用法：
 *   frida -U -p <WeChat PID> -l t11_probe_reddot.js
 *   然后切到微信 → 发现 tab 查看红点状态变化
 */

Java.perform(function () {
    console.log("[T11] ready — 查看发现tab红点触发...");

    // Probe 1: 找所有含 "badge" / "unread" / "new_count" / "dot" 关键词的方法
    var targetKeywords = ["badge", "unread", "new_count", "dot", "redpoint", "red_dot",
                          "newMsgCount", "NewCount", "unreadCount", "SnsNewCount"];

    // Probe 2: Hook SnsDataFacade 相关类
    var snsClasses = [
        "com.tencent.mm.plugin.sns.model.SnsDataFacade",
        "com.tencent.mm.plugin.sns.ui.SnsTimeLinePage",
        "com.tencent.mm.plugin.discover.ui.LauncherUI",
    ];

    snsClasses.forEach(function(clsName) {
        try {
            var cls = Java.use(clsName);
            var methods = cls.class.getDeclaredMethods();
            console.log("\n[T11] " + clsName + " 方法列表 (" + methods.length + " 个):");
            for (var i = 0; i < methods.length; i++) {
                var m = methods[i];
                var mName = m.getName();
                // Print all methods, mark badge-related ones
                var mark = "";
                for (var k = 0; k < targetKeywords.length; k++) {
                    if (mName.toLowerCase().indexOf(targetKeywords[k].toLowerCase()) >= 0) {
                        mark = " ★★★ BADGE CANDIDATE";
                        break;
                    }
                }
                console.log("  " + mName + "(" + m.getParameterTypes().length + " params)" + mark);
            }
        } catch(e) {
            console.log("[T11] " + clsName + " NOT FOUND: " + e);
        }
    });

    // Probe 3: 搜索 SnsTimeLineResponse 相关类
    try {
        var SnsTimeLineResponse = Java.use("com.tencent.mm.protocal.protobuf.SnsTimeLineResponse");
        console.log("\n[T11] SnsTimeLineResponse fields:");
        var fields = SnsTimeLineResponse.class.getDeclaredFields();
        for (var i = 0; i < fields.length; i++) {
            var f = fields[i];
            console.log("  " + f.getName() + " : " + f.getType().getName());
        }
    } catch(e) {
        console.log("[T11] SnsTimeLineResponse: " + e);
    }

    // Probe 4: 搜索含 "Friend" + "Status" 的类（hookFriendStatus 目标）
    // 注意：这需要枚举已加载类，可能较慢
    console.log("\n[T11] 枚举含 Friend/Status 关键类 (可能需要 10s)...");
    Java.enumerateLoadedClasses({
        onMatch: function(name) {
            if ((name.indexOf("Friend") >= 0 || name.indexOf("friend") >= 0)
                && (name.indexOf("Status") >= 0 || name.indexOf("Badge") >= 0 || name.indexOf("Dot") >= 0)) {
                console.log("[T11] CANDIDATE: " + name);
            }
        },
        onComplete: function() {
            console.log("[T11] 枚举完成");
        }
    });
});
