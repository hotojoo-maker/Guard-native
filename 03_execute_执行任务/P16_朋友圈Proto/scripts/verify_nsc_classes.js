Java.perform(function() {
    var targets = [
        "com.tencent.mm.ui.FindMoreFriendsUI",
        "com.tencent.mm.kara.feature.feature.comm.DiscoverViewFeatureGroup"
    ];
    targets.forEach(function(clsName) {
        try {
            var cls = Java.use(clsName);
            console.log("[OK] " + clsName);
        } catch(e) {
            console.log("[MISS] " + clsName + " — " + e.message);
        }
    });
});
