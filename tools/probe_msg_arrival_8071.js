/**
 * probe_msg_arrival_8071.js — 验证 8.0.71 主进程「密友消息到达」hook 点能否拿到 talker
 *
 * 目标（竞品 competitor_java 路线）：找一个主进程里、消息到达就触发、且能读出 talker(wxid)
 * 的 hook 点，用来在 isActive 下 fireAlert(震动/铃声)。候选：
 *   - com.tencent.mm.booter.notification.w.handleMessage(Message)
 *   - com.tencent.mm.booter.notification.x.d(...)
 *
 * 对每次触发，尝试从参数里提取 talker：
 *   1) 若参数是 NotificationItem → 读字段 h（PushFilter 已知 talker 在 h）
 *   2) 否则递归扫字段，找 wxid_ / @chatroom 字符串
 *
 * 用法（attach 主进程）：
 *   $mmpid = (adb shell "ps -ef | grep ' com.tencent.mm$'") -split '\s+' | Select-Object -Index 1
 *   frida -U -p $mmpid -l tools\probe_msg_arrival_8071.js 2>&1 | tee tools\probe_msg_arrival_$(Get-Date -f 'HHmmss').log
 *
 * 操作：脚本就绪后，
 *   场景A 前台：微信停在会话列表/某聊天页 → 让密友发一条消息
 *   场景B 后台：微信切后台/锁屏 → 让密友发一条消息
 *   每条消息看是否打出 [HIT ...] talker=wxid_xxx
 */
Java.perform(function () {
    'use strict';
    var TAG = 'MAR';
    var PKG = 'com.tencent.mm.booter.notification';
    var NI_CLASS = PKG + '.NotificationItem';

    function ts() {
        var d = new Date(), p = function (n) { return n < 10 ? '0' + n : '' + n; };
        return p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds());
    }
    function log(s, m) { console.log(ts() + ' [' + TAG + ':' + s + '] ' + m); }

    function isSkippable(cn) {
        return cn.indexOf('java.') === 0 || cn.indexOf('android.') === 0
            || cn.indexOf('kotlin.') === 0 || cn.indexOf('androidx.') === 0 || cn.indexOf('[') === 0;
    }

    // 从一个对象里提取 talker：先试字段 h，再递归扫 wxid_/@chatroom 字符串
    function extractTalker(obj, depth, visited) {
        if (obj === null || obj === undefined || depth > 3) return null;
        var cn;
        try { cn = obj.getClass().getName(); } catch (e) { return null; }
        if (isSkippable(cn)) {
            try {
                var s = String(obj);
                if (s.indexOf('wxid_') === 0 || s.indexOf('@chatroom') >= 0) return s;
            } catch (e2) {}
            return null;
        }
        if (visited.indexOf(obj) >= 0) return null;
        visited.push(obj);
        // 优先字段 h
        try {
            var fh = obj.getClass().getDeclaredField('h');
            fh.setAccessible(true);
            var hv = fh.get(obj);
            if (hv !== null) {
                var hs = String(hv);
                if (hs.indexOf('wxid_') === 0 || hs.indexOf('@chatroom') >= 0) return hs + ' (field h)';
            }
        } catch (e) {}
        // 递归扫所有字段
        var cls = obj.getClass(), d = 0;
        while (cls && d < 4) {
            var cname; try { cname = cls.getName(); } catch (e3) { break; }
            if (cname === 'java.lang.Object') break;
            var fields; try { fields = cls.getDeclaredFields(); } catch (e4) { break; }
            for (var i = 0; i < fields.length; i++) {
                try {
                    var f = fields[i]; f.setAccessible(true);
                    var v = f.get(obj);
                    if (v === null) continue;
                    var r = extractTalker(v, depth + 1, visited);
                    if (r !== null) return r + (depth === 0 ? ' via .' + f.getName() : '');
                } catch (e5) {}
            }
            cls = cls.getSuperclass(); d++;
        }
        return null;
    }

    // ── w.handleMessage ──
    try {
        var W = Java.use(PKG + '.w');
        W.handleMessage.implementation = function (msg) {
            try {
                var talker = (msg && msg.obj.value !== null) ? extractTalker(msg.obj.value, 0, []) : null;
                log('HIT w.handleMessage', 'what=' + (msg ? msg.what.value : '?')
                    + ' talker=' + talker);
            } catch (e) { log('w.handleMessage', 'extract err ' + e); }
            return this.handleMessage(msg);
        };
        log('OK', 'w.handleMessage hooked');
    } catch (e) { log('FAIL', 'w.handleMessage: ' + e); }

    // ── x.d(所有重载) ──
    try {
        var X = Java.use(PKG + '.x');
        var methods = X.class.getDeclaredMethods();
        var hooked = 0;
        for (var i = 0; i < methods.length; i++) {
            var m = methods[i];
            if (m.getName() !== 'd') continue;
            var pts = m.getParameterTypes();
            var names = []; for (var j = 0; j < pts.length; j++) names.push(pts[j].getName());
            log('SIG', 'x.d(' + names.join(',') + ')');
            (function (paramNames) {
                try {
                    var ov = X.d.overload.apply(X.d, paramNames);
                    ov.implementation = function () {
                        var args = Array.prototype.slice.call(arguments);
                        var talker = null;
                        for (var k = 0; k < args.length; k++) {
                            talker = extractTalker(args[k], 0, []);
                            if (talker !== null) break;
                        }
                        log('HIT x.d', '(' + paramNames.join(',') + ') talker=' + talker);
                        return this.d.apply(this, args);
                    };
                    hooked++;
                } catch (e2) { log('WARN', 'x.d overload fail: ' + e2); }
            })(names);
        }
        log('OK', 'x.d hooked overloads=' + hooked);
    } catch (e) { log('FAIL', 'x.d: ' + e); }

    log('INIT', '=== 探针就绪：场景A前台发密友消息、场景B切后台发密友消息，各看 [HIT] talker= ===');
});
