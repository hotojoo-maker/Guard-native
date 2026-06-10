'use strict';
/*
 * P21 安全探针 #3：dump 真实红点控制类的方法 + 字段（纯反射，不挂 hook，不会卡死）。
 * 目标类来自 scan #2 的非混淆命中：
 *   - MainTabUnreadMgr         → 底部「发现」tab 角标
 *   - DiscoveryFinderRedDotManager / FinderNewDiscoveryRedDotService → 「发现」页行红点
 *   - render.RenderView / FinderRedDotTextView → 红点数字渲染层
 *   - RedDotManager / FinderRedDotCtrlInfo → 红点状态/控制
 * 用法：frida -D 609b4b18 -p <PID> -l probe_reddot_methods.js  跑出 [DUMP] done 即可 kill。
 */
Java.perform(function () {
    var targets = [
        'com.tencent.mm.ui.MainTabUnreadMgr',
        'com.tencent.mm.plugin.finder.extension.reddot.DiscoveryFinderRedDotManager',
        'com.tencent.mm.plugin.finder.extension.reddot.FinderNewDiscoveryRedDotService',
        'com.tencent.mm.plugin.finder.extension.reddot.render.RenderView',
        'com.tencent.mm.plugin.finder.view.FinderRedDotTextView',
        'com.tencent.mm.plugin.finder.view.FinderHomeTabRedDotTipsBubbleView',
        'com.tencent.wechat.aff.finder.RedDotManager',
        'com.tencent.wechat.aff.newlife.FinderRedDotCtrlInfo'
    ];
    targets.forEach(function (cn) {
        try {
            var c = Java.use(cn);
            var methods = c.class.getDeclaredMethods();
            console.log('\n==== ' + cn + ' (' + methods.length + ' methods) ====');
            for (var i = 0; i < methods.length; i++) {
                var m = methods[i];
                var params = m.getParameterTypes();
                var ps = [];
                for (var j = 0; j < params.length; j++) ps.push(params[j].getSimpleName());
                console.log('  ' + m.getReturnType().getSimpleName() + ' ' + m.getName() + '(' + ps.join(', ') + ')');
            }
            var fields = c.class.getDeclaredFields();
            var fl = [];
            for (var k = 0; k < fields.length; k++) fl.push(fields[k].getType().getSimpleName() + ' ' + fields[k].getName());
            console.log('  FIELDS: ' + fl.join(' | '));
        } catch (e) {
            console.log('\n==== ' + cn + ' -> NOT FOUND: ' + e);
        }
    });
    console.log('\n[DUMP] done');
});
