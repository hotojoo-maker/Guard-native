'use strict';
/**
 * probe_v_trigger_enum.js — V 态穷举 fresh-trigger 候选
 *
 * 用法（微信已 V 态、Guard 已装、勿挂其他探针）：
 *   frida -U -n "com.tencent.mm" -l probe_v_trigger_enum.js
 *
 * 先收发一条消息（capture 自然参数），再 REPL：
 *   p26Status()     — 看已捕获实例/参数
 *   p26Try(1)       — 单试策略 #1
 *   p26EnumAll()    — 依次试全部（每条间隔 3s，看列表）
 *
 * 策略表见 STRATEGIES；命中后把 #N + 日志贴回。
 */

Java.perform(function () {

    var TAG = '[P26-ENUM]';
    var TARGET_WXID = 'wxid_lzd2va16jd1622';
    var LIST_FIELDS = ['o', 'p', 'h'];

    var cap = {
        r0: null,
        r0Method: null,
        r0Args: [],
        lastO0: null,
        lastList: null,
        ik3n: null,
        mvvm: null
    };

    // ── 工具 ─────────────────────────────────────────────────────────────

    function errChain(e) {
        var lines = [String(e)];
        var c = e;
        var n = 0;
        while (c && c.getCause && n < 8) {
            try {
                c = c.getCause();
                if (c) lines.push('  cause: ' + c.toString());
            } catch (x) { break; }
            n++;
        }
        return lines.join('\n');
    }

    function readField(obj, name) {
        if (!obj) return null;
        try {
            var cls = obj.getClass();
            while (cls) {
                try {
                    var f = cls.getDeclaredField(name);
                    f.setAccessible(true);
                    return f.get(obj);
                } catch (e) {}
                cls = cls.getSuperclass();
            }
        } catch (e2) {}
        return null;
    }

    function getMvvm() {
        if (cap.mvvm) return cap.mvvm;
        var found = null;
        Java.choose('com.tencent.mm.ui.conversation.adapter.MvvmConvList', {
            onMatch: function (i) { if (!found) found = i; },
            onComplete: function () {}
        });
        cap.mvvm = found;
        return found;
    }

    function wxidInList(wxid) {
        var mv = getMvvm();
        if (!mv || !wxid) return -1;
        for (var i = 0; i < LIST_FIELDS.length; i++) {
            try {
                var list = readField(mv, LIST_FIELDS[i]);
                if (!list || !list.size) continue;
                for (var j = 0; j < list.size(); j++) {
                    var item = list.get(j);
                    if (!item) continue;
                    try {
                        var d = item.d;
                        var wx = d.c0();
                        if (wx && wx.toString() === wxid) return 1;
                    } catch (e) {}
                }
            } catch (e2) {}
        }
        return 0;
    }

    function listSizes() {
        var mv = getMvvm();
        var o = {};
        if (!mv) return o;
        for (var i = 0; i < LIST_FIELDS.length; i++) {
            try {
                var list = readField(mv, LIST_FIELDS[i]);
                if (list && list.size) o[LIST_FIELDS[i]] = list.size();
            } catch (e) {}
        }
        return o;
    }

    function getR0() {
        if (cap.r0) return cap.r0;
        var found = null;
        Java.choose('kc5.r0', {
            onMatch: function (i) { if (!found) found = i; },
            onComplete: function () {}
        });
        cap.r0 = found;
        return found;
    }

    function invokeOnMain(fn) {
        Java.scheduleOnMainThread(function () {
            try { fn(); } catch (e) {
                console.log(TAG + ' main err: ' + errChain(e));
            }
        });
    }

    function tryInvoke(tag, fn) {
        var before = { wxid: wxidInList(TARGET_WXID), sizes: listSizes() };
        console.log('\n' + TAG + ' === TRY #' + tag + ' === before wxid=' + before.wxid + ' sz=' + JSON.stringify(before.sizes));
        var ok = false;
        var err = null;
        try {
            fn();
            ok = true;
        } catch (e) {
            err = errChain(e);
        }
        var after = { wxid: wxidInList(TARGET_WXID), sizes: listSizes() };
        console.log(TAG + ' #' + tag + ' ok=' + ok + ' after wxid=' + after.wxid + ' sz=' + JSON.stringify(after.sizes));
        if (err) console.log(TAG + ' #' + tag + ' ERR:\n' + err);
        if (after.wxid === 1 && before.wxid !== 1) {
            console.log(TAG + ' *** HIT #' + tag + ' — 密友出现了！贴回此 # ***');
        }
        return { ok: ok, before: before, after: after, err: err };
    }

    // ── Capture hooks ────────────────────────────────────────────────────

    try {
        var R0 = Java.use('kc5.r0');
        var ms = R0.class.getDeclaredMethods();
        for (var i = 0; i < ms.length; i++) {
            var m = ms[i];
            if (m.getName() !== 'd') continue;
            (function (method) {
                var sig = sigOf(method);
                var ov = R0.d.overload.apply(R0.d, sig);
                var origD = ov;
                ov.implementation = function () {
                    cap.r0 = this;
                    cap.r0Method = method;
                    cap.r0Args = [].slice.call(arguments);
                    console.log(TAG + ' cap r0.d' + sigStr(method) + ' args=' + cap.r0Args.length);
                    return origD.apply(this, arguments);
                };
            })(m);
            console.log(TAG + ' hook r0.d' + sigStr(m));
        }
    } catch (e) {
        console.log(TAG + ' r0 hook fail: ' + e);
    }

    function sigOf(method) {
        var pts = method.getParameterTypes();
        var s = [];
        for (var i = 0; i < pts.length; i++) s.push(pts[i].getName());
        return s;
    }

    function sigStr(method) {
        var s = sigOf(method);
        return s.length ? '(' + s.join(',') + ')' : '()';
    }

    try {
        var MvvmList = Java.use('com.tencent.mm.plugin.mvvmlist.MvvmList');
        var mets = MvvmList.class.getDeclaredMethods();
        for (var wi = 0; wi < mets.length; wi++) {
            var mw = mets[wi];
            if (mw.getName() !== 'w') continue;
            var wpts = mw.getParameterTypes();
            if (wpts.length !== 1) continue;
            var wpt = wpts[0].getName();
            (function (pt) {
                var ow = MvvmList.w.overload(pt);
                var origW = ow;
                ow.implementation = function (arg) {
                    cap.lastO0 = arg;
                    cap.mvvm = this;
                    console.log(TAG + ' cap MvvmList.w(' + pt + ')');
                    return origW.call(this, arg);
                };
            })(wpt);
            console.log(TAG + ' hook MvvmList.w(' + wpt + ')');
            break;
        }
    } catch (e2) {
        console.log(TAG + ' MvvmList.w hook fail: ' + e2);
    }

    try {
        var Ik3n = Java.use('ik3.n');
        var origHe = Ik3n.handleEvent.overload('java.util.List');
        var origHeImpl = origHe;
        Ik3n.handleEvent.overload('java.util.List').implementation = function (list) {
            cap.ik3n = this;
            cap.lastList = list;
            console.log(TAG + ' cap ik3.n.handleEvent sz=' + (list ? list.size() : 0));
            return origHeImpl.call(this, list);
        };
        console.log(TAG + ' hook ik3.n.handleEvent');
    } catch (e3) {
        console.log(TAG + ' ik3.n hook fail: ' + e3);
    }

    // ── 穷举策略 ─────────────────────────────────────────────────────────

    var STRATEGIES = [
        {
            id: 1,
            name: 'r0.d REPLAY last natural args',
            run: function () {
                var r0 = getR0();
                if (!r0 || !cap.r0Method) throw new Error('no r0 capture — 先发一条消息');
                var sig = sigOf(cap.r0Method);
                var ov = Java.use('kc5.r0').d.overload.apply(Java.use('kc5.r0').d, sig);
                var a = cap.r0Args;
                if (a.length === 0) ov.call(r0);
                else if (a.length === 1) ov.call(r0, a[0]);
                else ov.call.apply(ov, [r0].concat(a));
            }
        },
        {
            id: 2,
            name: 'r0.d() no-arg (each overload)',
            run: function () {
                var r0 = getR0();
                if (!r0) throw new Error('no r0');
                var R0 = Java.use('kc5.r0');
                var ms = R0.class.getDeclaredMethods();
                var n = 0;
                for (var i = 0; i < ms.length; i++) {
                    if (ms[i].getName() !== 'd') continue;
                    if (sigOf(ms[i]).length !== 0) continue;
                    R0.d.overload().call(r0);
                    n++;
                }
                if (n === 0) throw new Error('no no-arg d()');
            }
        },
        {
            id: 3,
            name: 'r0.d(lastO0) if captured',
            run: function () {
                if (!cap.lastO0) throw new Error('no lastO0 — 先发消息');
                var r0 = getR0();
                if (!r0) throw new Error('no r0');
                var pt = cap.lastO0.getClass().getName();
                Java.use('kc5.r0').d.overload(pt).call(r0, cap.lastO0);
            }
        },
        {
            id: 4,
            name: 'MvvmList.w(lastO0)',
            run: function () {
                if (!cap.lastO0) throw new Error('no lastO0');
                var mv = getMvvm();
                if (!mv) throw new Error('no MvvmConvList');
                var pt = cap.lastO0.getClass().getName();
                Java.use('com.tencent.mm.plugin.mvvmlist.MvvmList').w.overload(pt).call(mv, cap.lastO0);
            }
        },
        {
            id: 5,
            name: 'ik3.n.handleEvent(copy lastList)',
            run: function () {
                if (!cap.lastList || !cap.ik3n) throw new Error('no list/ik3n capture');
                var ArrayList = Java.use('java.util.ArrayList');
                var copy = ArrayList.$new(cap.lastList);
                cap.ik3n.handleEvent(copy);
            }
        },
        {
            id: 6,
            name: 'MvvmList.e(copy lastList)',
            run: function () {
                if (!cap.lastList) throw new Error('no lastList');
                var mv = getMvvm();
                if (!mv) throw new Error('no mvvm');
                var ArrayList = Java.use('java.util.ArrayList');
                var copy = ArrayList.$new(cap.lastList);
                Java.use('com.tencent.mm.plugin.mvvmlist.MvvmList').e.overload('java.util.List').call(mv, copy);
            }
        },
        {
            id: 7,
            name: 'kc5.v0.notifyDataSetChanged only',
            run: function () {
                var ad = null;
                Java.choose('kc5.v0', {
                    onMatch: function (a) { if (!ad) ad = a; },
                    onComplete: function () {}
                });
                if (!ad) throw new Error('no v0 adapter');
                ad.notifyDataSetChanged();
            }
        },
        {
            id: 8,
            name: 'r0 ALL d() overloads sequential',
            run: function () {
                var r0 = getR0();
                if (!r0) throw new Error('no r0');
                var R0 = Java.use('kc5.r0');
                var ms = R0.class.getDeclaredMethods();
                for (var i = 0; i < ms.length; i++) {
                    if (ms[i].getName() !== 'd') continue;
                    var sig = sigOf(ms[i]);
                    try {
                        var args = [];
                        if (sig.length === 1 && cap.lastO0) args = [cap.lastO0];
                        else if (sig.length === cap.r0Args.length) args = cap.r0Args;
                        R0.d.overload.apply(R0.d, sig).call.apply(R0.d, [r0].concat(args));
                        console.log(TAG + '   sub d' + sigStr(ms[i]) + ' ok');
                    } catch (e) {
                        console.log(TAG + '   sub d' + sigStr(ms[i]) + ' fail: ' + e);
                    }
                }
            }
        }
    ];

    function printStatus() {
        console.log('\n' + TAG + ' === STATUS ===');
        console.log(TAG + ' target wxid=' + TARGET_WXID);
        console.log(TAG + ' r0=' + (getR0() ? 'yes' : 'NO') + ' lastArgs=' + cap.r0Args.length);
        console.log(TAG + ' lastO0=' + (cap.lastO0 ? cap.lastO0.getClass().getName() : 'NO'));
        console.log(TAG + ' lastList=' + (cap.lastList ? cap.lastList.size() : 'NO'));
        console.log(TAG + ' ik3n=' + (cap.ik3n ? 'yes' : 'NO'));
        console.log(TAG + ' wxidInList=' + wxidInList(TARGET_WXID) + ' sizes=' + JSON.stringify(listSizes()));
        console.log(TAG + ' strategies:');
        for (var i = 0; i < STRATEGIES.length; i++) {
            console.log(TAG + '   p26Try(' + STRATEGIES[i].id + ') ' + STRATEGIES[i].name);
        }
    }

    function runTry(id) {
        var s = null;
        for (var i = 0; i < STRATEGIES.length; i++) {
            if (STRATEGIES[i].id === id) { s = STRATEGIES[i]; break; }
        }
        if (!s) {
            console.log(TAG + ' unknown id=' + id);
            return;
        }
        invokeOnMain(function () {
            tryInvoke(s.id + ' ' + s.name, s.run);
        });
    }

    function runEnumAll() {
        var idx = 0;
        function next() {
            if (idx >= STRATEGIES.length) {
                console.log(TAG + ' === ENUM ALL DONE ===');
                printStatus();
                return;
            }
            var s = STRATEGIES[idx++];
            console.log(TAG + ' enum next in 3s: #' + s.id + ' ' + s.name);
            invokeOnMain(function () {
                tryInvoke(s.id + ' ' + s.name, s.run);
                setTimeout(next, 3000);
            });
        }
        console.log(TAG + ' === ENUM ALL START (3s/条，请看列表) ===');
        printStatus();
        next();
    }

    global.p26Status = printStatus;
    global.p26Try = runTry;
    global.p26EnumAll = runEnumAll;
    global.p26SetWxid = function (w) { TARGET_WXID = w; return w; };
    global.p26Help = function () {
        console.log(TAG + ' p26Status() | p26Try(1-8) | p26EnumAll() | p26SetWxid("wxid")');
    };

    rpc.exports = {
        p26Status: printStatus,
        p26Try: runTry,
        p26EnumAll: runEnumAll,
        p26SetWxid: function (w) { TARGET_WXID = w; return w; }
    };

    console.log(TAG + ' READY — V态 → 发1条消息 capture → p26EnumAll() 或 p26Try(N)');
    printStatus();
});
