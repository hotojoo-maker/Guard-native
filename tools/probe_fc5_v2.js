// probe_fc5_v2.js — 扫已加载类 + 拦截 classloader 找 fc5

const TAG = "[fc5-v2]";

// pattern: 任意包名以 .fc5 结尾的类
function findFC5() {
    var found = [];
    Java.enumerateLoadedClassesSync().forEach(function(name) {
        if (name.match(/\.fc5$/)) {
            found.push(name);
        }
    });
    return found;
}

Java.perform(function() {
    console.log(TAG + " loaded classes scan...");
    var loaded = findFC5();
    if (loaded.length > 0) {
        for (var i = 0; i < loaded.length; i++) {
            console.log(TAG + " FOUND loaded: " + loaded[i]);
            probeFC5(loaded[i]);
        }
    } else {
        console.log(TAG + " not loaded yet, hooking classloader...");

        // hook loadClass to catch it
        var clsLoader = Java.classFactory.loader;
        var ClassLoader = Java.use("java.lang.ClassLoader");
        ClassLoader.loadClass.overload('java.lang.String').implementation = function(name) {
            var result = this.loadClass(name);
            if (name.match(/\.fc5$/)) {
                console.log(TAG + " CAUGHT loadClass: " + name);
                // delay probe to avoid recursion
                setTimeout(function() {
                    probeFC5(name);
                }, 100);
            }
            return result;
        };

        // Also try: enumerate all loaded classes again after a delay
        setTimeout(function() {
            var loaded2 = findFC5();
            if (loaded2.length > 0) {
                console.log(TAG + " found after delay: " + loaded2[0]);
            } else {
                console.log(TAG + " still not loaded. loaded class count: " + Java.enumerateLoadedClassesSync().length);
                // dump classes matching .xx5 pattern to find similar ones
                Java.enumerateLoadedClassesSync().forEach(function(n) {
                    if (n.match(/[a-z]{2}5$/)) console.log("  candidate: " + n);
                });
            }
        }, 3000);
    }
});

function probeFC5(fullName) {
    try {
        var cls = Java.classFactory.loader.loadClass(fullName);
        console.log(TAG + " fields of " + fullName + ":");
        var fields = cls.getDeclaredFields();
        for (var i = 0; i < fields.length; i++) {
            console.log("  ." + fields[i].getName() + " : " + fields[i].getType().getName());
        }

        // find instances
        Java.choose(fullName, {
            onMatch: function(inst) {
                console.log(TAG + " instance: " + inst);
                for (var j = 0; j < fields.length; j++) {
                    try {
                        fields[j].setAccessible(true);
                        var val = fields[j].get(inst);
                        console.log(TAG + "  ." + fields[j].getName() + " = " + val);
                    } catch(e) {}
                }
                global.FC5_INST = inst;
                console.log(TAG + " saved to global.FC5_INST");
            },
            onComplete: function() {
                console.log(TAG + " instance scan done");
            }
        });
    } catch(e) {
        console.log(TAG + " probe err: " + e);
    }
}
