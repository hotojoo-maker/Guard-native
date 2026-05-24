// probe_push_unread_8071.js — 微信 8.0.71 push 进程 + 主进程 双进程探针
//
// 目标：榨干底部气泡（会话 tab 未读数）+ 朋友圈红点的共同上游链路
//
// 背景（Catfish 8.0.70 破解版动态实证）：
//   - replaceNotification(android.os.Message) 是 :push 进程总闸，1-2次/秒高频
//     talker = wxid_ahvd1wejo02f22，密友消息到达时拦截
//   - showUnReadMsgCount(int)→int 恒返 0，归零会话 tab 数字
//   - sHiddenUnread 字段存原始 count，用于 H→V 时恢复
//   - NmsHookInvocationHandler 当前版本未命中（已排除）
//
// 本脚本自动检测进程，分别注入不同策略：
//   :push 进程 → 找 Message 处理链 + talker 提取 + 通知/未读写入
//   主进程     → 找 tab 未读数展示入口 + badge setter
//
// 用法（两个终端同时跑）：
//   # 主进程
//   frida -U -p $(adb shell "ps -ef|grep ' com.tencent.mm$'" | awk '{print $2}') \
//         -l probe_push_unread_8071.js 2>&1 | tee tools/probe_main_HHMMSS.log
//   # push 进程
//   frida -U -p $(adb shell "ps -ef|grep 'com.tencent.mm:push'" | awk '{print $2}') \
//         -l probe_push_unread_8071.js 2>&1 | tee tools/probe_push_HHMMSS.log
//
// 操作顺序：
//   1. 微信主界面可见（底部 tab 数字可见）
//   2. 普通好友发消息 → 看 tab 数字
//   3. 密友 wxid_ahvd1wejo02f22 发消息 → 对比
//   4. 按 Home → 密友再发消息（push 进程触发）
//   5. 45s 后自动打候选排名

(function () {
    'use strict';

    var TAG = 'PU8071';
    var MAX_HOOKS = 200;
    var hookCount = 0;
    var hitStats = {};  // key → {hits, ins, outs, stacks}
    var procName = '?';

    // ─── 工具 ────────────────────────────────────────────────

    function ts() {
        var d = new Date();
        var p = function (n) { return n < 10 ? '0' + n : '' + n; };
        return p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds()) +
            '.' + (d.getMilliseconds() + '000').substring(0, 3);
    }
    function log(sub, msg) { console.log(ts() + ' [' + TAG + ':' + procName + ':' + sub + '] ' + msg); }

    function stackTop(n) {
        try {
            var frames = Java.use('java.lang.Thread').currentThread().getStackTrace();
            var out = [];
            for (var i = 0; i < frames.length && out.length < (n || 5); i++) {
                var s = String(frames[i]);
                if (s.indexOf('java.lang.reflect') >= 0) continue;
                if (s.indexOf('dalvik.') >= 0) continue;
                if (s.indexOf('com.android.internal') >= 0) continue;
                out.push(s.trim());
            }
            return out.join(' ← ');
        } catch (e) { return '?'; }
    }

    function safeStr(v, max) {
        if (v === null || v === undefined) return 'null';
        var s = String(v);
        return s.length > (max || 80) ? s.substring(0, max || 80) + '…' : s;
    }

    // 从 Message/Bundle 或任意对象反射提取 wxid 相关字段
    function extractWxidFromObj(obj) {
        if (obj === null) return null;
        try {
            var cls = obj.getClass();
            var fields = cls.getDeclaredFields();
            for (var i = 0; i < fields.length; i++) {
                var f = fields[i];
                f.setAccessible(true);
                var v = f.get(obj);
                if (v === null) continue;
                var sv = String(v);
                if (sv.indexOf('wxid_') === 0 || sv.indexOf('@chatroom') > 0) return sv;
            }
            // 也试试 Bundle extras
            try {
                var extras = obj.getData();
                if (extras !== null) {
                    var talker = extras.getString('talker');
                    if (talker !== null) return String(talker);
                    talker = extras.getString('notification.show.talker');
                    if (talker !== null) return String(talker);
                    talker = extras.getString('fromUser');
                    if (talker !== null) return String(talker);
                }
            } catch (e2) {}
        } catch (e) {}
        return null;
    }

    function recordHit(key, inVal, outVal, wxid) {
        if (!hitStats[key]) hitStats[key] = { hits: 0, ins: [], outs: [], wxids: [] };
        var s = hitStats[key];
        s.hits++;
        if (inVal !== undefined && s.ins.indexOf(inVal) < 0 && s.ins.length < 6) s.ins.push(inVal);
        if (outVal !== undefined && s.outs.indexOf(outVal) < 0 && s.outs.length < 6) s.outs.push(outVal);
        if (wxid && s.wxids.indexOf(wxid) < 0 && s.wxids.length < 4) s.wxids.push(wxid);
    }

    // ─── Hook 工厂 ────────────────────────────────────────────

    var hookedClasses = {};

    // hook 一个类的所有「有价值」方法
    function hookClass(clsName, opts) {
        if (hookedClasses[clsName]) return 0;
        if (hookCount >= MAX_HOOKS) return 0;
        hookedClasses[clsName] = true;
        opts = opts || {};
        var hooked = 0;

        try {
            var cls = Java.use(clsName);
            var methods = cls.class.getDeclaredMethods();

            for (var i = 0; i < methods.length; i++) {
                if (hookCount >= MAX_HOOKS) break;
                var m = methods[i];
                var mname = m.getName();
                var ptypes = m.getParameterTypes();
                var rtype = m.getReturnType().getName();
                var pnames = [];
                for (var j = 0; j < ptypes.length; j++) pnames.push(ptypes[j].getName());

                // 过滤策略
                var ok = false;
                if (opts.all) { ok = true; }
                // int→int
                if (rtype === 'int' && pnames.length === 1 && pnames[0] === 'int') ok = true;
                // ()→int getter
                if (rtype === 'int' && pnames.length === 0) ok = true;
                // (int)→void setter
                if (rtype === 'void' && pnames.length === 1 && pnames[0] === 'int') ok = true;
                // (android.os.Message)→* 消息处理
                if (pnames.indexOf('android.os.Message') >= 0) ok = true;
                // (android.app.Notification)→* 通知处理
                if (pnames.indexOf('android.app.Notification') >= 0) ok = true;
                // 方法名含语义关键词
                var lm = mname.toLowerCase();
                if (lm.indexOf('unread') >= 0 || lm.indexOf('badge') >= 0 ||
                    lm.indexOf('count') >= 0 || lm.indexOf('notify') >= 0 ||
                    lm.indexOf('notif') >= 0 || lm.indexOf('talker') >= 0 ||
                    lm.indexOf('push') >= 0 || lm.indexOf('replace') >= 0) ok = true;

                if (!ok) continue;

                (function (mn, pt, rt) {
                    try {
                        var ov = cls[mn].overload.apply(cls[mn], pt);
                        ov.implementation = function () {
                            var args = Array.prototype.slice.call(arguments);
                            var ret = this[mn].apply(this, args);

                            var key = clsName.split('.').pop() + '.' + mn +
                                '(' + pt.map(function(p){ return p.split('.').pop(); }).join(',') + ')→' + rt.split('.').pop();

                            // 提取 wxid（从 Message 参数）
                            var wxid = null;
                            for (var ai = 0; ai < args.length; ai++) {
                                if (args[ai] !== null) {
                                    wxid = extractWxidFromObj(args[ai]);
                                    if (wxid) break;
                                }
                            }

                            var inVal = args.length > 0 ? (typeof args[0] === 'number' ? args[0] : safeStr(args[0], 40)) : undefined;
                            var outVal = (rt !== 'void' && ret !== null) ? ret : undefined;

                            recordHit(key, inVal, outVal, wxid);

                            var prev = hitStats[key].hits;
                            if (prev <= 2) {
                                log('HIT', key +
                                    (inVal !== undefined ? ' in=' + inVal : '') +
                                    (outVal !== undefined ? ' out=' + outVal : '') +
                                    (wxid ? ' wxid=' + wxid : ''));
                                log('STK', stackTop(4));
                            } else if (prev === 3) {
                                log('HIT', key + ' [静默，仍计数]');
                            }
                            return ret;
                        };
                        hookCount++;
                        hooked++;
                    } catch (e2) {}
                })(mname, pnames, rtype);
            }

            if (hooked > 0) log('HOOK', clsName.split('.').pop() + ' ×' + hooked);
        } catch (e) {
            log('ERR', clsName + ': ' + e.message);
        }
        return hooked;
    }

    // ─── 排名输出 ────────────────────────────────────────────

    function printRanking(label) {
        var keys = Object.keys(hitStats);
        if (keys.length === 0) { log('RANK', label + ': 无命中'); return; }

        keys.sort(function (a, b) { return hitStats[b].hits - hitStats[a].hits; });

        log('RANK', '══════════════ ' + label + ' 候选排名 ══════════════');
        log('RANK', '参考：Catfish 8.0.70 破解版 showUnReadMsgCount(int)→0 = tab badge 归零总闸');
        log('RANK', '参考：Catfish 8.0.70 破解版 replaceNotification(Message) = :push 进程消息拦截');
        log('RANK', '');

        for (var i = 0; i < Math.min(keys.length, 25); i++) {
            var k = keys[i];
            var s = hitStats[k];

            var stars = '';
            // ⭐ in!=out 且出现 0 = 可能是展示修正（showUnReadMsgCount 特征）
            if (s.outs.indexOf(0) >= 0 && s.ins.some(function(v){ return v > 0; })) stars += '⭐ZERO_OUT ';
            // 🔔 有 wxid = 消息链路（replaceNotification 特征）
            if (s.wxids.length > 0) stars += '🔔TALKER ';
            // 📊 高频 = 主路径
            if (s.hits >= 10) stars += '📊HIGH_FREQ ';

            log('RANK', '[' + (i + 1) + '] ' + k);
            log('RANK', '    hits=' + s.hits +
                ' in=' + JSON.stringify(s.ins) +
                ' out=' + JSON.stringify(s.outs) +
                (s.wxids.length ? ' wxid=' + JSON.stringify(s.wxids) : '') +
                (stars ? '  →  ' + stars : ''));
        }
        log('RANK', '');
        log('RANK', '⭐ZERO_OUT  = Guard v2 tab badge 归零候选（等价 showUnReadMsgCount）');
        log('RANK', '🔔TALKER   = Guard v2 push 拦截候选（等价 replaceNotification）');
        log('RANK', '📊HIGH_FREQ = 主路径，优先复刻');
        log('RANK', '════════════════════════════════════════════════');
    }

    // ─── 主入口 ───────────────────────────────────────────────

    Java.perform(function () {

        // 获取进程名
        try {
            var app = Java.use('android.app.ActivityThread').currentApplication();
            procName = String(app.getApplicationInfo().processName);
        } catch (e) { procName = 'unknown'; }

        var isPush = procName.indexOf(':push') >= 0;
        log('INIT', '进程=' + procName + (isPush ? ' → PUSH模式' : ' → 主进程模式'));
        log('INIT', '参考 Catfish 8.0.70 破解版实证：replaceNotification=总闸 showUnReadMsgCount=tab归零');

        // ══════════════════════════════
        // PUSH 进程专用 hooks
        // ══════════════════════════════
        if (isPush) {
            log('MODE', '=== PUSH 进程 — 找 replaceNotification 等价 ===');

            // 已知稳定类名（push 进程常驻的）
            var PUSH_STABLE = [
                'com.tencent.mm.plugin.notification.helper.NotificationHelper',
                'com.tencent.mm.plugin.notification.helper.NotificationHandlerHelper',
                'com.tencent.mm.plugin.push.PushMessageHandler',
                'com.tencent.mm.plugin.push.PushNotifyProxyManager',
                'com.tencent.mm.app.WeChatApplication',
            ];
            for (var i = 0; i < PUSH_STABLE.length; i++) hookClass(PUSH_STABLE[i]);

            // 枚举 push 进程所有已加载类
            var pushCandidates = [];
            Java.enumerateLoadedClasses({
                onMatch: function (name) {
                    if (name.indexOf('com.tencent.mm') < 0) return;
                    var ln = name.toLowerCase();
                    if (ln.indexOf('notif') >= 0 || ln.indexOf('push') >= 0 ||
                        ln.indexOf('badge') >= 0 || ln.indexOf('unread') >= 0 ||
                        ln.indexOf('msg') >= 0 || ln.indexOf('handler') >= 0) {
                        pushCandidates.push(name);
                    }
                },
                onComplete: function () {
                    log('SCAN', 'push 进程候选类: ' + pushCandidates.length);
                    for (var j = 0; j < pushCandidates.length; j++) {
                        log('CAND', pushCandidates[j]);
                        hookClass(pushCandidates[j]);
                    }
                    log('SCAN', '已安装 ' + hookCount + ' hooks');
                    log('SCAN', '--- 请让密友 wxid_ahvd1wejo02f22 发消息（后台/锁屏）---');
                    setTimeout(function(){ printRanking('PUSH进程@45s'); }, 45000);
                    setTimeout(function(){ printRanking('PUSH进程@90s'); }, 90000);
                }
            });

        // ══════════════════════════════
        // 主进程 hooks
        // ══════════════════════════════
        } else {
            log('MODE', '=== 主进程 — 找 showUnReadMsgCount 等价 ===');

            // 已知稳定类名
            var MAIN_STABLE = [
                'com.tencent.mm.ui.LauncherUI',
                'com.tencent.mm.ui.maintab.MainTabUI',
                'com.tencent.mm.ui.maintab.MainTabUnreadMgr',
                'com.tencent.mm.plugin.notification.badge.BadgeManager',
                'com.tencent.mm.plugin.notification.badge.BadgeUtil',
                'com.tencent.mm.plugin.notification.helper.NotificationHelper',
            ];
            for (var si = 0; si < MAIN_STABLE.length; si++) hookClass(MAIN_STABLE[si]);

            // 关键词枚举
            var MAIN_KEYWORDS = [
                'maintab', 'MainTab', 'LauncherUI',
                'UnreadMgr', 'UnreadCount', 'Unread',
                'BadgeMgr', 'Badge', 'RedDot', 'reddot',
                'TabRed', 'TabUnread', 'TabBadge',
            ];

            var mainCandidates = [];
            Java.enumerateLoadedClasses({
                onMatch: function (name) {
                    if (name.indexOf('com.tencent.mm') < 0) return;
                    for (var ki = 0; ki < MAIN_KEYWORDS.length; ki++) {
                        if (name.indexOf(MAIN_KEYWORDS[ki]) >= 0) {
                            mainCandidates.push(name);
                            return;
                        }
                    }
                },
                onComplete: function () {
                    log('SCAN', '主进程候选类: ' + mainCandidates.length);
                    for (var mi = 0; mi < mainCandidates.length; mi++) {
                        log('CAND', mainCandidates[mi]);
                        hookClass(mainCandidates[mi]);
                    }
                    log('SCAN', '已安装 ' + hookCount + ' hooks');
                    log('SCAN', '--- 请操作: 普通好友发消息 → 密友发消息 → 切 H/V ---');
                    setTimeout(function(){ printRanking('主进程@45s'); }, 45000);
                    setTimeout(function(){ printRanking('主进程@90s'); }, 90000);
                }
            });
        }
    });
})();
