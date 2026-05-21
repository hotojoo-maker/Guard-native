// T05 Final: Quick check w/ Frida wrapper access and user's filter approach

Java.perform(function () {
    console.log("[T05] === Final: find SnsObject in parseFrom stream ===");

    try {
        Java.classFactory.loader = Java.use("android.app.ActivityThread").currentApplication().getClassLoader();
    } catch (e) {}

    var SnsObject = Java.use("com.tencent.mm.protocal.protobuf.SnsObject");
    var hitCount = 0;

    SnsObject.parseFrom.overload('[B').implementation = function (bytes) {
        var result = this.parseFrom(bytes);
        if (result == null) return result;

        // Try BOTH field names — Username and field_userName
        var userName = null;
        try {
            var f = result.getClass().getDeclaredField("Username");
            f.setAccessible(true);
            var v = f.get(result);
            if (v != null) userName = String(v);
        } catch (e) {}
        if (userName == null) {
            try {
                var f = result.getClass().getDeclaredField("field_userName");
                f.setAccessible(true);
                var v = f.get(result);
                if (v != null) userName = String(v);
            } catch (e) {}
        }

        if (userName == null || userName.length === 0) return result;

        hitCount++;
        console.log("[T05] ★ SNS HIT #" + hitCount + " userName=" + userName);

        // Find which field worked
        var whichField = "?";
        try { result.getClass().getDeclaredField("Username"); whichField = "Username"; } catch (e) {}
        try { result.getClass().getDeclaredField("field_userName"); whichField = "field_userName"; } catch (e) {}
        console.log("[T05]   wxid field name: " + whichField);

        // Dump list-type fields with "Like" or "Comment" or "List" in name
        var fields = result.getClass().getDeclaredFields();
        for (var i = 0; i < fields.length; i++) {
            var name = fields[i].getName().toLowerCase();
            if (name.indexOf("like") >= 0 || name.indexOf("comment") >= 0 || name.indexOf("list") >= 0) {
                try {
                    fields[i].setAccessible(true);
                    var val = fields[i].get(result);
                    var valDesc = "null";
                    if (val != null) {
                        valDesc = "type=" + val.getClass().getName();
                        if (val.getClass().getName().indexOf("List") >= 0) {
                            var jl = Java.cast(val, Java.use("java.util.List"));
                            valDesc = "List size=" + jl.size();
                            if (jl.size() > 0) {
                                valDesc += " itemClass=" + jl.get(0).getClass().getName();
                            }
                        }
                    }
                    console.log("[T05]   field: " + fields[i].getName() + " : " + fields[i].getType().getName() + " → " + valDesc);
                } catch (e) {}
            }
        }

        // Only log first 5 hits
        if (hitCount >= 5) {
            console.log("[T05] Got 5 hits, unhooking...");
            return result;
        }
        return result;
    };

    console.log("[T05] Hooked. Scroll Moments feed NOW.");
});
