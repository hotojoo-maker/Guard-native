'use strict';
/*
 * P21 probe — 互动列表数据源 class-trace (EXTRA LIGHT 抓类)
 * 只看 bm/rm 适配器: 构造器 + 带参数的方法(填数据候选)。丢掉 k4(已证是联系人查询)。
 * 每个只在首次调用打一行, 随即 unhook → 极轻, 不卡。
 * 用法: frida -D 609b4b18 -p <PID> -l probe_smsg_source.js
 *   attach 后: 进 发现→朋友圈→气泡→互动列表
 * 输出: logcat 标签 NCL, 前缀 [TRACE]
 */
Java.perform(function () {
    var Log = Java.use('android.util.Log');
    function L(s) { try { Log.i('NCL', '[TRACE] ' + s); } catch (e) {} try { console.log('[TRACE] ' + s); } catch (e) {} }

    var SKIP = {
        getCount: 1, getView: 1, getItem: 1, getItemId: 1, getItemViewType: 1,
        getViewTypeCount: 1, isEnabled: 1, areAllItemsEnabled: 1, hasStableIds: 1,
        isEmpty: 1, notifyDataSetChanged: 1, notifyDataSetInvalidated: 1,
        registerDataSetObserver: 1, unregisterDataSetObserver: 1,
        hashCode: 1, equals: 1, toString: 1
    };

    ['com.tencent.mm.plugin.sns.ui.bm', 'com.tencent.mm.plugin.sns.ui.rm'].forEach(function (cn) {
        try {
            var C = Java.use(cn);
            var shortN = cn.split('.').pop();
            var seen = {};
            // 构造器（数据可能从构造传入）
            try {
                C.$init.overloads.forEach(function (o) {
                    var ats = o.argumentTypes.map(function (t) { return t.className; }).join(',');
                    o.implementation = function () {
                        if (!seen['<init>' + ats]) { seen['<init>' + ats] = 1; L(shortN + '.<init>(' + ats + ')'); }
                        return o.apply(this, arguments);
                    };
                });
            } catch (e) {}
            // 仅带参数的方法（填数据候选），跳过高频 getter
            var ms = C.class.getDeclaredMethods();
            var n = 0;
            for (var i = 0; i < ms.length; i++) {
                var mn = ms[i].getName();
                if (SKIP[mn]) continue;
                if (ms[i].getParameterTypes().length < 1) continue;
                try {
                    C[mn].overloads.forEach(function (o) {
                        if (o.argumentTypes.length < 1) return;
                        var rt = o.returnType.className;
                        var ats = o.argumentTypes.map(function (t) { return t.className; }).join(',');
                        var key = mn + '(' + ats + ')';
                        o.implementation = function () {
                            if (!seen[key]) { seen[key] = 1; L(shortN + '.' + key + ' -> ' + rt); try { o.implementation = null; } catch (e) {} }
                            return o.apply(this, arguments);
                        };
                    });
                    n++;
                } catch (e) {}
            }
            L('hooked ' + shortN + ' (ctor + ' + n + ' param-methods)');
        } catch (e) { L('skip ' + cn + ' : ' + e); }
    });
    L('ready(light) — 进 朋友圈/互动列表');
});
