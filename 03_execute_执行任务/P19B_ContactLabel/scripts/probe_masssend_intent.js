/**
 * 抓 MassSendHistoryUI 启动 MvvmContactListUI 的完整 Intent
 */
'use strict';

Java.perform(function () {
    var TAG = '[MSI]';
    var Activity = Java.use('android.app.Activity');
    Activity.startActivity.overload('android.content.Intent').implementation = function (intent) {
        var cmp = intent.getComponent();
        if (cmp && cmp.getClassName().indexOf('MvvmContactListUI') >= 0) {
            console.log(TAG + ' ===== MvvmContactListUI Intent =====');
            var b = intent.getExtras();
            if (b) {
                var keys = b.keySet();
                var ki = keys.iterator();
                while (ki.hasNext()) {
                    var k = ki.next();
                    try {
                        var v = b.get(k);
                        var s = String(v);
                        console.log(TAG + '  [' + k + '] = ' + s.substring(0, Math.min(s.length, 300)));
                    } catch (e) {
                        console.log(TAG + '  [' + k + '] = (' + v.getClass().getName() + ') ' + String(v).substring(0, 200));
                    }
                }
            }
        }
        return this.startActivity(intent);
    };
    console.log(TAG + ' ready — 点新建群发');
});
