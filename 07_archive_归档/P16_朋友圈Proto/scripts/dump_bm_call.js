Java.perform(function() {
    var bm_b = Java.use("bm.b");
    var TARGET = "wxid_lzd2va16jd1622";

    bm_b.call.implementation = function(arg) {
        console.log("[bm.b.call] arg type=" + (arg != null ? arg.getClass().getName() : "null"));
        // dump argument if it's a List
        if (arg != null) {
            var argCls = arg.getClass().getName();
            if (argCls.indexOf("List") >= 0 || argCls.indexOf("ArrayList") >= 0) {
                var sz = arg.size();
                console.log("[bm.b.arg] sz=" + sz);
                for (var j = 0; j < Math.min(sz, 5); j++) {
                    var item = arg.get(j);
                    if (item != null) {
                        console.log("  ["+j+"] " + item.getClass().getName() + " | " + item.toString());
                    }
                }
            } else {
                console.log("[bm.b.arg] " + arg.toString().substring(0, 200));
            }
        }
        // dump this ArrayList fields
        var cls = this.getClass();
        for (var d = 0; cls != null && d < 6; d++) {
            var fields = cls.getDeclaredFields();
            for (var i = 0; i < fields.length; i++) {
                try {
                    fields[i].setAccessible(true);
                    var v = fields[i].get(this);
                    if (v != null && v.getClass().getName() === "java.util.ArrayList") {
                        var sz = v.size();
                        if (sz > 0 && sz <= 20) {
                            console.log("[bm.b.this] " + fields[i].getName() + " sz=" + sz);
                            for (var j = 0; j < Math.min(sz, 3); j++) {
                                var item = v.get(j);
                                if (item != null) console.log("  ["+j+"] " + item.getClass().getName() + " " + item.toString().substring(0, 120));
                            }
                        }
                    }
                } catch(e) {}
            }
            cls = cls.getSuperclass();
        }
        return this.call(arg);
    };
});
