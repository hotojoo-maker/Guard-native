// probe_fc5_trigger.js — 找 fc5.d 实例并模拟新消息触发
// 目标：触发 fc5.d.onChanged() → 整条会话刷新链

const TAG = "[fc5-probe]";

Java.perform(function() {
    console.log(TAG + " start");

    // Step 1: 找 fc5 类
    var fc5 = null;
    Java.enumerateLoadedClasses({
        onMatch: function(name) {
            if (name.match(/fc5$/)) {
                console.log(TAG + " class found: " + name);
                fc5 = Java.use(name);
            }
        },
        onComplete: function() {
            if (!fc5) {
                console.log(TAG + " fc5 not found in loaded classes");
                return;
            }
            probeClass(fc5);
        }
    });

    function probeClass(cls) {
        var clz = cls.class;
        console.log(TAG + " superclass: " + clz.getSuperclass().getName());
        console.log(TAG + " fields:");
        var fields = clz.getDeclaredFields();
        for (var i = 0; i < fields.length; i++) {
            var f = fields[i];
            console.log("  ." + f.getName() + " : " + f.getType().getName());
        }
        console.log(TAG + " methods:");
        var methods = clz.getDeclaredMethods();
        for (var j = 0; j < methods.length; j++) {
            console.log("  " + methods[j].getName());
        }

        // Step 2: 找 d 字段实例
        console.log(TAG + " scanning for fc5 instances...");
        Java.choose(clz.getName(), {
            onMatch: function(inst) {
                console.log(TAG + " instance found: " + inst);
                try {
                    var dField = clz.getDeclaredField("d");
                    dField.setAccessible(true);
                    var dVal = dField.get(inst);
                    console.log(TAG + " .d = " + dVal + " type=" + (dVal ? dVal.getClass().getName() : "null"));

                    if (dVal) {
                        console.log(TAG + " .d methods:");
                        var dMethods = dVal.getClass().getDeclaredMethods();
                        for (var k = 0; k < dMethods.length; k++) {
                            console.log("  .d." + dMethods[k].getName());
                        }
                        // 存到全局方便手动调
                        global.fc5_inst = inst;
                        global.fc5_d = dVal;
                        console.log(TAG + " ★ saved to global.fc5_inst / global.fc5_d");
                        console.log(TAG + " ★ try: global.fc5_d.setValue(global.fc5_d.getValue())");
                    }
                } catch(e) {
                    console.log(TAG + " err: " + e);
                }
            },
            onComplete: function() {
                console.log(TAG + " scan done");
                console.log(TAG + " === 下一步 ===");
                console.log(TAG + " 如果是 LiveData: global.fc5_d.setValue(global.fc5_d.getValue())");
                console.log(TAG + " 如果有 onChanged: Java.use('...').onChanged.call(global.fc5_d, currentVal)");
            }
        });
    }
});
