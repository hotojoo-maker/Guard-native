// T09 诊断 #1 — MvvmList.m() 到底被谁调用、传什么 item 类型
// 目标：确认 k24.b 真实身份 + 找到朋友圈 feed 的实际 item 类名

var MvvmList = Java.use("com.tencent.mm.plugin.mvvmlist.MvvmList");

// Hook m(List, boolean)
MvvmList.m.overload('java.util.List', 'boolean').implementation = function(list, z) {
    if (list && list.size() > 0) {
        var classes = {};
        for (var i = 0; i < Math.min(list.size(), 30); i++) {
            var item = list.get(i);
            if (item) {
                var cn = item.getClass().getName();
                classes[cn] = (classes[cn] || 0) + 1;
            }
        }
        console.log("[T09] m() list size=" + list.size() + " | z=" + z);
        for (var c in classes) {
            console.log("  -> " + c + " x" + classes[c]);
        }
    }
    return this.m(list, z);
};

// Hook s(List) too
MvvmList.s.overload('java.util.List').implementation = function(list) {
    if (list && list.size() > 0) {
        var classes = {};
        for (var i = 0; i < Math.min(list.size(), 30); i++) {
            var item = list.get(i);
            if (item) {
                var cn = item.getClass().getName();
                classes[cn] = (classes[cn] || 0) + 1;
            }
        }
        console.log("[T09] s() list size=" + list.size());
        for (var c in classes) {
            console.log("  -> " + c + " x" + classes[c]);
        }
    }
    return this.s(list);
};

console.log("[T09] MvvmList.m/s hooks installed. Scroll moments feed now...");
