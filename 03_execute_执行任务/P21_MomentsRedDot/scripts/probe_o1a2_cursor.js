'use strict';
/*
 * P21 probe — 互动列表游标来源确认 (READ-ONLY)
 * 目标: 确认 SnsMsgUIWithAll / SnsMsgUIWithRelevance 打开时,
 *       微信走 w1 的哪个 Cursor 方法 (N1/O1/a2), 游标有没有 talker 列,
 *       密友(含 AA熵) 是否在行里 → 验证 v22 TalkerFilterCursor 一定有效.
 * 安全: 全程只读, 保存并恢复游标位置, 不改任何数据; 每个方法命中一次即 unhook (H5).
 * 用法: frida -U -p <PID> -l probe_o1a2_cursor.js
 *   attach 后在手机: 发现 → 朋友圈 → 顶部气泡 → 全部互动消息 / 与我的互动消息
 * 输出: logcat 标签 NCL, 前缀 [O1A2]
 */
Java.perform(function () {
    var Log = Java.use('android.util.Log');
    function emit(m) {
        var s = '[O1A2] ' + m;
        try { Log.i('NCL', s); } catch (e) {}
        try { console.log(s); } catch (e) {}
    }

    // 密友名单 (模块 Bridge 在 LSPosed classloader, 非默认 app loader)
    var miyou = {}, miyouArr = [];
    (function () {
        var found = false, ls = [];
        try { ls = Java.enumerateClassLoadersSync(); } catch (e) {}
        for (var i = 0; i < ls.length && !found; i++) {
            try {
                var B = Java.ClassFactory.get(ls[i]).use('com.ghost.assist.core.Bridge');
                var it = B.getInstance().getWxids().iterator();
                while (it.hasNext()) { var w = it.next().toString(); miyou[w] = true; miyouArr.push(w); }
                found = true;
            } catch (e) {}
        }
        emit('密友(' + miyouArr.length + '): ' + miyouArr.join(', '));
    })();

    function inspect(mn, cur) {
        if (cur === null) { emit(mn + ' -> null'); return; }
        try {
            var cnt = cur.getCount();
            var cols = cur.getColumnNames(), ca = [];
            for (var i = 0; i < cols.length; i++) ca.push(cols[i]);
            var ti = cur.getColumnIndex('talker');
            emit(mn + ' count=' + cnt + ' talkerIdx=' + ti);
            emit(mn + ' cols=' + ca.join(','));
            if (ti < 0) { emit(mn + ' NO talker col — 需换列名'); return; }
            var saved = cur.getPosition(), mi = 0, non = 0, samp = [];
            for (var r = 0; r < cnt; r++) {
                cur.moveToPosition(r);
                var t = cur.getString(ti);
                if (t && miyou[t]) mi++; else non++;
                if (samp.length < 8) samp.push((t || 'null') + (miyou[t] ? '(密)' : ''));
            }
            cur.moveToPosition(saved); // 只读: 还原位置
            emit(mn + ' 行: 密友=' + mi + ' 非密友=' + non);
            emit(mn + ' sample: ' + samp.join(' | '));
        } catch (e) { emit(mn + ' inspect err ' + e); }
    }

    var W1 = 'com.tencent.mm.plugin.sns.storage.w1';
    var cap = {};
    try {
        var w1 = Java.use(W1);
        ['N1', 'O1', 'a2'].forEach(function (mn) {
            if (!w1[mn]) { emit(mn + ' absent'); return; }
            w1[mn].overloads.forEach(function (o) {
                if (o.returnType.className !== 'android.database.Cursor') return;
                o.implementation = function () {
                    var ret = o.apply(this, arguments);
                    try {
                        if (!cap[mn]) {
                            var c = -1;
                            try { c = (ret !== null) ? ret.getCount() : -1; } catch (e) {}
                            if (c > 0) {                       // 只抓有行的那次, 跳过空游标
                                cap[mn] = true;
                                emit('HIT ' + mn + ' (count=' + c + ')');
                                inspect(mn, ret);
                                w1[mn].overloads.forEach(function (oo) { try { oo.implementation = null; } catch (e) {} });
                                emit(mn + ' unhooked (H5)');
                            } else if (!cap['e_' + mn]) {
                                cap['e_' + mn] = true;
                                emit(mn + ' empty (count=' + c + '), 保持 hook 等有行的那次');
                            }
                        }
                    } catch (e) { emit(mn + ' after err ' + e); }
                    return ret;
                };
                emit('hooked ' + mn + '(' + o.argumentTypes.map(function (t) { return t.className; }).join(',') + ')');
            });
        });
        emit('ready — 打开 全部互动消息 / 与我的互动消息');
    } catch (e) { emit('hook fail ' + e); }
});
