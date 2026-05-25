// v5：当前"我"页面 → 遍历所有 TextView → 找昵称/wxid
Java.perform(function() {
    Java.choose("android.widget.TextView", {
        onMatch: function(tv) {
            try {
                var text = tv.getText();
                if (text && text.length() > 0 && text.length() < 60) {
                    var s = text.toString();
                    // 过滤掉明显不是昵称/wxid的
                    if (s.indexOf("发现") >= 0 || s.indexOf("通讯录") >= 0 ||
                        s.indexOf("我") >= 0 && s.length() <= 2 ||
                        s.indexOf("服务") >= 0 || s.indexOf("设置") >= 0 ||
                        s.indexOf("视频") >= 0 || s.indexOf("直播") >= 0 ||
                        s.indexOf("收藏") >= 0 || s.indexOf("朋友圈") >= 0 ||
                        s.indexOf("表情") >= 0 || s.indexOf("卡包") >= 0 ||
                        s.indexOf("搜") >= 0 && s.length() <= 2) {
                        return;
                    }
                    var id = tv.getId();
                    var idName = "(no id)";
                    try { if (id > 0) idName = tv.getResources().getResourceEntryName(id); } catch(e) {}
                    console.log("[PROBE] TEXT id=" + id + "(" + idName + ") cls=" + tv.getClass().getName() + " text=" + s);
                }
            } catch(e) {}
        },
        onComplete: function() {
            console.log("[PROBE] TextViews scan done");
        }
    });
});
