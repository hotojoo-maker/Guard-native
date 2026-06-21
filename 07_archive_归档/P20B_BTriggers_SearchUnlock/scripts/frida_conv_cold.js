/**
 * 会话列表渲染追踪 — warm-attach 版
 * Hook: v0, MvvmList
 */
var startMs = 0;

Java.perform(function () {
    console.log("[trace] ===== WARM ATTACH =====");
    startMs = Date.now();
    var hooked = 0;

    // ---- v0 adapter ----
    try {
        var v0 = Java.use("kc5.v0");
        var getView = v0.getView.overload("int", "android.view.View", "android.view.ViewGroup");
        getView.implementation = function (pos, cv, parent) {
            console.log("[trace:" + (Date.now() - startMs) + "ms] v0.getView pos=" + pos);
            return getView.call(this, pos, cv, parent);
        };
        hooked++;

        var getCount = v0.getCount;
        getCount.implementation = function () {
            var c = getCount.call(this);
            console.log("[trace:" + (Date.now() - startMs) + "ms] v0.getCount=" + c);
            return c;
        };
        hooked++;

        var notify = v0.notifyDataSetChanged;
        notify.implementation = function () {
            console.log("[trace:" + (Date.now() - startMs) + "ms] v0.notifyDataSetChanged ★");
            return notify.call(this);
        };
        hooked++;
    } catch (e) { console.log("[trace] v0: " + e); }

    // ---- MvvmList ----
    try {
        var MvvmList = Java.use("kc5.d");
        var methods = MvvmList.class.getDeclaredMethods();
        for (var i = 0; i < methods.length; i++) {
            var name = methods[i].getName();
            var pts = methods[i].getParameterTypes();
            var args = [];
            for (var j = 0; j < pts.length; j++) args.push(pts[j].getName());

            (function (mName, mArgs) {
                try {
                    var target;
                    if (mArgs.length === 0) target = MvvmList[mName];
                    else target = MvvmList[mName].overload.apply(MvvmList[mName], mArgs);
                    target.implementation = function () {
                        console.log("[trace:" + (Date.now() - startMs) + "ms] MvvmList." + mName);
                        return target.apply(this, arguments);
                    };
                } catch (e2) {}
            })(name, args);
        }
        hooked++;
    } catch (e) { console.log("[trace] MvvmList: " + e); }

    console.log("[trace] hooked=" + hooked + ". Switch to conversation tab now!");
});
