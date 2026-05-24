/**
 * probe_pf_path.js — 8.0.71 通知/角标真实调用路径探针
 * 目标：确认 x.a(f9) / h0.d(int) / MainTabUI.i() 是否在真实调用链上
 *
 * 用法（两个终端同时跑）：
 *   frida -U -p 13720 -l tools/probe_pf_path.js 2>&1 | tee tools/probe_pf_main.log
 *   frida -U -p 13955 -l tools/probe_pf_path.js 2>&1 | tee tools/probe_pf_push.log
 *
 * 操作：密友 wxid_lzd2va16jd1622 发消息
 * 45s 后自动打排名
 */

(function () {
    'use strict';

    var TAG = 'PFP';
    var HIT = {}; // key → {hits, talkers, args0, rets, stacks}
    var proc = '?';
    var isPush = false;

    function ts() {
        var d = new Date();
        var p = function (n) { return n < 10 ? '0' + n : '' + n; };
        return p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds()) +
            '.' + (d.getMilliseconds() + '000').substring(0, 3);
    }
    function log(sub, msg) { console.log(ts() + ' [' + TAG + ':' + sub + '] ' + msg); }

    function stack5() {
        try {
            var frames = Java.use('java.lang.Thread').currentThread().getStackTrace();
            var out = [];
            for (var i = 0; i < frames.length && out.length < 5; i++) {
                var s = String(frames[i]);
                if (s.indexOf('java.lang.reflect') >= 0) continue;
                if (s.indexOf('dalvik.') >= 0) continue;
                if (s.indexOf('frida') >= 0) continue;
                out.push(s.trim());
            }
            return out.join(' ← ');
        } catch (e) { return '?'; }
    }

    function shortParam(v) {
        if (v === null || v === undefined) return 'null';
        if (typeof v === 'number') return '' + v;
        var s = String(v);
        return s.length > 50 ? s.substring(0, 50) + '…' : s;
    }

    // 从对象反射提取 wxid
    function findWxid(obj) {
        if (obj === null) return null;
        try {
            var cls = obj.getClass();
            // 先试 getData() bundle
            try {
                var data = obj.getData();
                if (data) {
                    var t = data.getString('talker') ||
                            data.getString('notification.show.talker') ||
                            data.getString('fromUser');
                    if (t && (t.indexOf('wxid_') === 0 || t.indexOf('@chatroom') > 0)) return 'bundle:' + t;
                }
            } catch (e) {}
            // 扫字段
            while (cls && cls.getName() !== 'java.lang.Object') {
                var fields = cls.getDeclaredFields();
                for (var i = 0; i < fields.length; i++) {
                    try {
                        fields[i].setAccessible(true);
                        var v = fields[i].get(obj);
                        if (v !== null) {
                            var sv = String(v);
                            if ((sv.indexOf('wxid_') === 0 || sv.indexOf('gh_') === 0) && sv.length < 40)
                                return 'field:' + fields[i].getName() + '=' + sv;
                        }
                    } catch (e2) {}
                }
                cls = cls.getSuperclass();
            }
        } catch (e) {}
        return null;
    }

    function record(key, arg0, retVal, wxid) {
        if (!HIT[key]) HIT[key] = { hits: 0, args0: [], rets: [], wxids: [], stack: '' };
        var r = HIT[key];
        r.hits++;
        if (arg0 !== undefined && r.args0.length < 5) r.args0.push(arg0);
        if (retVal !== undefined && r.rets.length < 5) r.rets.push(retVal);
        if (wxid && r.wxids.indexOf(wxid) < 0) r.wxids.push(wxid);
        if (!r.stack) r.stack = stack5();
    }

    // ─── hook 一个方法 ──────────────────────────────────────
    function hookOne(clsObj, methodObj) {
        var mname = methodObj.getName();
        var ptypes = methodObj.getParameterTypes();
        var pnames = [];
        for (var j = 0; j < ptypes.length; j++) pnames.push(ptypes[j].getName().split('.').pop());
        var rtypeName = methodObj.getReturnType().getName().split('.').pop();
        var clsShort = clsObj.getName().split('.').pop();
        var sig = pnames.join(',');

        try {
            var ov = clsObj[mname].overload.apply(clsObj[mname], ptypes.map(function(p){ return p.replace('/','.'); }));
            // resolve overload strings properly
        } catch (e) {
            // overload with raw types doesn't work with split names, retry with full names
            try {
                var fullTypes = [];
                for (var k = 0; k < ptypes.length; k++) fullTypes.push(ptypes[k].getName());
                var ov = clsObj[mname].overload.apply(clsObj[mname], fullTypes);
                ov.implementation = function () {
                    var args = Array.prototype.slice.call(arguments);
                    var ret = this[mname].apply(this, args);
                    var arg0 = args.length > 0 ? shortParam(args[0]) : undefined;
                    var wxid = null;
                    for (var ai = 0; ai < args.length && !wxid; ai++) wxid = findWxid(args[ai]);
                    var key = clsShort + '.' + mname + '(' + sig + ')→' + rtypeName;
                    record(key, arg0, ret, wxid);

                    if (HIT[key].hits <= 2) {
                        var line = 'HIT ' + key +
                            (arg0 !== undefined ? ' arg0=' + arg0 : '') +
                            (ret !== undefined ? ' ret=' + shortParam(ret) : '');
                        if (wxid) line += ' 🔔' + wxid;
                        log('HIT', line);
                        log('STK', HIT[key].stack);
                    }
                    return ret;
                };
            } catch (e2) {
                // overload failed, skip
            }
        }
    }

    // ─── hook 一个类的「有价值」方法 ─────────────────────────
    function hookClass(fullName) {
        try {
            var cls = Java.use(fullName);
            var methods = cls.class.getDeclaredMethods();
            var hooked = 0;

            for (var i = 0; i < methods.length; i++) {
                var m = methods[i];
                var mname = m.getName();
                var ptypes = m.getParameterTypes();
                var rtype = m.getReturnType().getName();

                var interesting = false;

                // int→int / void(int) / ()→int — unread/badge patterns
                if (rtype === 'int' && ptypes.length === 0) interesting = true;
                if (rtype === 'int' && ptypes.length === 1 && ptypes[0].getName() === 'int') interesting = true;
                if (rtype === 'void' && ptypes.length === 1 && ptypes[0].getName() === 'int') interesting = true;

                // (String)→void/boolean — talker param
                if (ptypes.length === 1 && ptypes[0].getName() === 'java.lang.String') interesting = true;

                // keyword in method name
                var lm = mname.toLowerCase();
                if (lm.indexOf('unread') >= 0 || lm.indexOf('badge') >= 0 ||
                    lm.indexOf('count') >= 0 || lm.indexOf('notif') >= 0 ||
                    lm.indexOf('talker') >= 0 || lm.indexOf('enqueue') >= 0 ||
                    lm.indexOf('show') >= 0 || lm.indexOf('tab') >= 0) interesting = true;

                if (!interesting) continue;

                hookOne(cls, m);
                hooked++;
            }
            if (hooked > 0) log('HOOK', fullName.split('.').pop() + ' ×' + hooked);

        } catch (e) {
            log('MISS', fullName + ': ' + e.message);
        }
    }

    // ─── 主入口 ─────────────────────────────────────────────
    Java.perform(function () {
        try {
            var app = Java.use('android.app.ActivityThread').currentApplication();
            proc = String(app.getApplicationInfo().processName);
        } catch (e) { proc = '?'; }
        isPush = proc.indexOf(':push') >= 0;
        log('INIT', '进程=' + proc + (isPush ? ' PUSH' : ' MAIN'));

        // ══════════════════════════════
        // 通用目标（两个进程都探）
        // ══════════════════════════════
        var COMMON = [
            'com.tencent.mm.booter.notification.x',
            'com.tencent.mm.booter.notification.h0',
            'com.tencent.mm.booter.notification.NotificationItem',
            'com.tencent.mm.booter.notification.e',
            'com.tencent.mm.booter.notification.y',
            'com.tencent.mm.booter.notification.a',
            'com.tencent.mm.booter.notification.b',
            'com.tencent.mm.booter.notification.c',
            'com.tencent.mm.booter.notification.d',
            'com.tencent.mm.booter.notification.r',
            'com.tencent.mm.booter.notification.s',
            'com.tencent.mm.booter.notification.t',
            'com.tencent.mm.booter.notification.w',
            'com.tencent.mm.booter.notification.z',
        ];
        for (var ci = 0; ci < COMMON.length; ci++) hookClass(COMMON[ci]);

        if (isPush) {
            // ══════════════════════════════
            // PUSH 进程特有 — enqueueNotification / push handler
            // ══════════════════════════════
            var PUSH_ONLY = [
                'com.tencent.mm.plugin.push.PushMessageHandler',
                'com.tencent.mm.plugin.push.PushNotifyProxyManager',
                'com.tencent.mm.app.WeChatApplication',
            ];
            for (var pi = 0; pi < PUSH_ONLY.length; pi++) hookClass(PUSH_ONLY[pi]);

            // 枚举 push 进程已加载的 notification/push 相关类（只做轻量枚举）
            var pushed = {};
            Java.enumerateLoadedClasses({
                onMatch: function (name) {
                    if (name.indexOf('com.tencent.mm') < 0) return;
                    var ln = name.toLowerCase();
                    if (ln.indexOf('notif') >= 0 || ln.indexOf('pushmsg') >= 0 ||
                        ln.indexOf('enqueue') >= 0 || ln.indexOf('badge') >= 0) {
                        if (!pushed[name]) { pushed[name] = true; hookClass(name); }
                    }
                },
                onComplete: function () {
                    log('SCAN', 'push 枚举完成');
                }
            });

        } else {
            // ══════════════════════════════
            // MAIN 进程特有 — UI tab / Launcher
            // ══════════════════════════════
            var MAIN_ONLY = [
                'com.tencent.mm.ui.MainTabUI',
                'com.tencent.mm.ui.LauncherUI',
                'com.tencent.mm.ui.maintab.MainTabUnreadMgr',
            ];
            for (var mi = 0; mi < MAIN_ONLY.length; mi++) hookClass(MAIN_ONLY[mi]);

            // 轻量枚举 MainTab 相关类
            var mained = {};
            Java.enumerateLoadedClasses({
                onMatch: function (name) {
                    if (name.indexOf('com.tencent.mm') < 0) return;
                    var ln = name.toLowerCase();
                    if (ln.indexOf('maintab') >= 0 || ln.indexOf('launcherui') >= 0 ||
                        ln.indexOf('unread') >= 0) {
                        if (!mained[name]) { mained[name] = true; hookClass(name); }
                    }
                },
                onComplete: function () {
                    log('SCAN', '主进程枚举完成');
                }
            });
        }

        log('READY', '=== 请让密友 wxid_lzd2va16jd1622 发消息 ===');

        // 45s / 90s 排名
        setTimeout(function () {
            var keys = Object.keys(HIT);
            keys.sort(function (a, b) { return HIT[b].hits - HIT[a].hits; });
            log('RANK', '══════════ ' + proc + ' @45s 命中排名 ══════════');
            for (var i = 0; i < Math.min(keys.length, 20); i++) {
                var k = keys[i];
                var r = HIT[k];
                var stars = r.wxids.length > 0 ? ' 🔔GOT_WXID' : '';
                log('RANK', '[' + (i + 1) + '] ' + k + ' hits=' + r.hits + stars);
                if (r.args0.length) log('RANK', '    arg0=' + JSON.stringify(r.args0));
                if (r.rets.length) log('RANK', '    ret=' + JSON.stringify(r.rets));
                if (r.wxids.length) log('RANK', '    wxid=' + JSON.stringify(r.wxids));
                if (r.stack) log('RANK', '    STK: ' + r.stack);
            }
        }, 45000);

        setTimeout(function () {
            var keys = Object.keys(HIT);
            keys.sort(function (a, b) { return HIT[b].hits - HIT[a].hits; });
            log('RANK', '══════════ ' + proc + ' @90s 命中排名 ══════════');
            for (var i = 0; i < Math.min(keys.length, 20); i++) {
                var k = keys[i];
                var r = HIT[k];
                var stars = r.wxids.length > 0 ? ' 🔔GOT_WXID' : '';
                log('RANK', '[' + (i + 1) + '] ' + k + ' hits=' + r.hits + stars);
            }
        }, 90000);

        // keepalive
        setInterval(function () {
            log('ALIVE', '等待中... hits=' + Object.keys(HIT).length);
        }, 20000);
    });
})();
