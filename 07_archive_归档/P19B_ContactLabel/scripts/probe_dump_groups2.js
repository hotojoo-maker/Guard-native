/**
 * probe_dump_groups2.js — v2 fix: use proper Frida Java reflection
 */
'use strict';

Java.perform(function () {
    var TAG = '[QDG2]';
    var emptyClassArray = Java.array('java.lang.Class', []);

    Java.choose('com.tencent.mm.ui.contact.ChatroomContactUI', {
        onMatch: function (instance) {
            try {
                var mField = instance.getClass().getDeclaredField('m');
                mField.setAccessible(true);
                var adapter = mField.get(instance);
                if (!adapter) { console.log(TAG + ' adapter=null'); return; }

                var cls = adapter.getClass();
                console.log(TAG + ' adapter class=' + cls.getName());

                // getCount() — no params
                var getCountMethod = cls.getMethod('getCount', emptyClassArray);
                var count = getCountMethod.invoke(adapter, Java.array('java.lang.Object', []));
                console.log(TAG + ' count=' + count);

                // Find getItem method via getMethods (public, includes inherited)
                var methods = cls.getMethods();
                for (var i = 0; i < methods.length; i++) {
                    var m = methods[i];
                    var params = m.getParameterTypes();
                    if (params.length === 1 && params[0].getName() === 'int') {
                        var rt = m.getReturnType().getName();
                        if (rt !== 'void') {
                            console.log(TAG + ' item-method: ' + rt + ' ' + m.getName() + '(int)');
                        }
                    }
                }

                // Also list all public methods for inspection
                console.log(TAG + ' --- all public methods ---');
                for (var i = 0; i < methods.length; i++) {
                    var m = methods[i];
                    var ptypes = m.getParameterTypes();
                    var pnames = [];
                    for (var j = 0; j < ptypes.length; j++) pnames.push(ptypes[j].getName());
                    console.log(TAG + '  ' + m.getReturnType().getName() + ' ' + m.getName() + '(' + pnames.join(',') + ')');
                }

            } catch (e) {
                console.log(TAG + ' err: ' + e.message + ' / ' + e);
            }
        },
        onComplete: function () { console.log(TAG + ' done'); }
    });
});
