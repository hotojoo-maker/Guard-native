'use strict';
/*
 * P21 定向运行时探针（只钩"低频"红点控制/渲染方法，绝不碰 setText 这类热点，不会卡死）。
 * 目标：找到"发现 tab 数字 + 朋友圈行红点"的根源 path / 方法 / 值，给"从根源压制"定位。
 * 用法：frida -D 609b4b18 -p <PID> -l probe_reddot_runtime.js
 * attach 后在手机：朋友圈页 <-> 发现页 来回切，密友点个赞，看 [RDM]/[REN]/[SNS dump]。
 */
Java.perform(function () {
    function L(s) { console.log(s); }

    // ---- (1) 启动时 dump SNS 互动未读类（纯反射，安全）----
    [
        'com.tencent.mm.plugin.sns.ui.improve.component.unread.SnsUnreadSaveList',
        'com.tencent.mm.plugin.sns.ui.improve.component.unread.ImproveUnreadUIC',
        'com.tencent.mm.plugin.sns.ui.improve.component.unread.ImproveUnreadTierView',
        'com.tencent.mm.plugin.sns.ui.improve.component.unread.SnsUnreadSaveItem'
    ].forEach(function (cn) {
        try {
            var c = Java.use(cn);
            var ms = c.class.getDeclaredMethods();
            L('\n==== ' + cn + ' (' + ms.length + ') ====');
            for (var i = 0; i < ms.length; i++) {
                var m = ms[i], pt = m.getParameterTypes(), ps = [];
                for (var j = 0; j < pt.length; j++) ps.push(pt[j].getSimpleName());
                L('  ' + m.getReturnType().getSimpleName() + ' ' + m.getName() + '(' + ps.join(',') + ')');
            }
            var fs = c.class.getDeclaredFields(), fl = [];
            for (var k = 0; k < fs.length; k++) fl.push(fs[k].getType().getSimpleName() + ' ' + fs[k].getName());
            L('  FIELDS: ' + fl.join(' | '));
        } catch (e) { L('==== ' + cn + ' NOTFOUND: ' + e); }
    });

    // ---- (2) 钩 RedDotManager 的 path 方法（低频，afterHook 只读不改）----
    try {
        var RDM = Java.use('com.tencent.wechat.aff.finder.RedDotManager');
        RDM.shouldShowRedDotAtPath.implementation = function (p) {
            var r = this.shouldShowRedDotAtPath(p);
            L('[RDM] shouldShowRedDotAtPath("' + p + '") = ' + r);
            return r;
        };
        RDM.showInfoTypeAtPath.implementation = function (p) {
            var r = this.showInfoTypeAtPath(p);
            L('[RDM] showInfoTypeAtPath("' + p + '") = ' + r);
            return r;
        };
        RDM.getAllRedDotCtrlInfoAtPath.implementation = function (p) {
            var r = this.getAllRedDotCtrlInfoAtPath(p);
            L('[RDM] getAllRedDotCtrlInfoAtPath("' + p + '") size=' + (r != null ? r.size() : -1));
            return r;
        };
        L('[probe] RedDotManager hooks ready');
    } catch (e) { L('[probe] RDM hook fail: ' + e); }

    // ---- (3) 钩渲染层 RenderView.h(int)（红点数字落地值）----
    try {
        var RV = Java.use('com.tencent.mm.plugin.finder.extension.reddot.render.RenderView');
        RV.h.overload('int').implementation = function (n) {
            var sid = '?';
            try { sid = this.getRenderStrategyId(); } catch (e) {}
            L('[REN] RenderView.h(' + n + ')  strategyId=' + sid);
            return this.h(n);
        };
        L('[probe] RenderView hook ready');
    } catch (e) { L('[probe] RV hook fail: ' + e); }

    L('\n[probe_reddot_runtime] ready -- 发现页<->朋友圈 来回切 + 密友点赞');
});
