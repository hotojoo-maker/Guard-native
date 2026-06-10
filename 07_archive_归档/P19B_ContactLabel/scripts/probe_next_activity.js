/**
 * probe_next_activity.js — 抓 MassSendHistoryUI 启动的下一页 Activity
 */
'use strict';

Java.perform(function () {
    var TAG = '[NXT]';

    // Hook Activity.startActivity to see what's launched
    var Activity = Java.use('android.app.Activity');
    Activity.startActivity.overload('android.content.Intent').implementation = function (intent) {
        var cmp = intent.getComponent();
        if (cmp) {
            var cn = cmp.getClassName();
            console.log(TAG + ' startActivity → ' + cn);
            // dump extras
            var b = intent.getExtras();
            if (b) {
                var keys = b.keySet();
                var ki = keys.iterator();
                while (ki.hasNext()) {
                    var k = ki.next();
                    try {
                        var v = b.get(k);
                        console.log(TAG + '  [' + k + '] = ' + String(v).substring(0, 100));
                    } catch (e) {}
                }
            }
        }
        return this.startActivity(intent);
    };

    console.log(TAG + ' ready — 点新建群发');
});
