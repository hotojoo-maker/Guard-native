'use strict';
/*
 * P21 安全探针 #2：静态枚举"红点/未读"相关类（仅遍历已加载类表，不挂任何 hook）。
 * 绝不会卡死微信——没有持续 hook，只在 attach 瞬间跑一次。
 * 用法：frida -D 609b4b18 -p <主进程PID> -l probe_reddot_scan.js
 * 跑出 [SCAN] classes done 后即可 kill。
 */
Java.perform(function () {
    var pats = /RedDot|RedPoint|Unread|TipPoint|reddot|TabRed|RedNum|NewMsgTip/;
    var hits = [];
    Java.enumerateLoadedClasses({
        onMatch: function (name) {
            if (name.indexOf('com.tencent') >= 0 && pats.test(name)) hits.push(name);
        },
        onComplete: function () {
            hits.sort();
            console.log('\n[SCAN] count=' + hits.length);
            for (var i = 0; i < hits.length; i++) console.log('  [C] ' + hits[i]);
            console.log('[SCAN] classes done');
        }
    });
});
