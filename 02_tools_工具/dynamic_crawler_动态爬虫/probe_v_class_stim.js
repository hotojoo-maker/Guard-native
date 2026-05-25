'use strict';
/**
 * probe_v_class_stim.js — V 态「刺激各种类」穷举
 *
 * 对会话 fresh 链路相关类：找 live 实例 → 穷举短方法 invoke → 看密友是否进列表
 *
 * 用法：
 *   frida -U -n "com.tencent.mm" -l probe_v_class_stim.js
 *   V 态 → 先发 1 条消息（被动 capture）→ REPL：
 *     p26StimAll()           — 按类顺序全刺激（3s/类，看列表）
 *     p26StimClass('kc5.r0') — 只刺激一个类
 *     p26StimMethod('ik3.n','handleEvent') — 只刺激一个方法
 *     p26Captures()          — 已捕获实例/参数
 *     p26SetWxid('wxid_xxx')
 *
 * ⚠️ 测完 detach；attach 期间可能影响红点/刷新
 */

Java.perform(function () {

    var TAG = '[STIM]';
    var TARGET_WXID = 'wxid_lzd2va16jd1622';
    var LIST_FIELDS = ['o', 'p', 'h'];
    var GAP_MS = 2500;

    // P26 链路 + 文档候选（8.0.71）
    var CLASS_TARGETS = [
        'kc5.r0', 'kc5.a', 'kc5.y',
        'ik3.n', 'ik3.m', 'ik3.h0', 'ik3.o0',
        'cl0.u', 'h45.i', 'h45.f',
        'fc5.d', 'l45.g', 'v45.e',
        'com.tencent.mm.plugin.mvvmlist.MvvmList',
        'com.tencent.mm.ui.conversation.adapter.MvvmConvList'
    ];

    var captures = {};  // key: cls.method(sig) → { inst, args, ts }
    var lastList = null;
    var lastO0 = null;

    // ── utils ────────────────────────────────────────────────────────────

    function errChain(e) {
        var out = [String(e)];
        var c = e, n = 0;
        while (c && c.getCause && n++ < 6) {
            try { c = c.getCause(); if (c) out.push('  → ' + c); } catch (x) { break; }
        }
        return out.join('\n');
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
        var f = null;
        Java.choose('com.tencent.mm.ui.conversation.adapter.MvvmConvList', {
            onMatch: function (i) { if (!f) f = i; },
            onComplete: function () {}
        });
        return f;
    }

    function wxidHit() {
        var mv = getMvvm();
        if (!mv) return -1;
        for (var i = 0; i < LIST_FIELDS.length; i++) {
            try {
                var list = readField(mv, LIST_FIELDS[i]);
                if (!list || !list.size) continue;
                for (var j = 0; j < list.size(); j++) {
                    var item = list.get(j);
                    try {
                        var wx = item.d.c0().toString();
                        if (wx === TARGET_WXID) return 1;
                    } catch (e) {}
                }
            } catch (e2) {}
        }
        return 0;
    }

    function listSnap() {
        var mv = getMvvm();
        var o = {};
        if (!mv) return o;
        for (var i = 0; i < LIST_FIELDS.length; i++) {
            try {
                var l = readField(mv, LIST_FIELDS[i]);
                if (l && l.size) o[LIST_FIELDS[i]] = l.size();
            } catch (e) {}
        }
        return o;
    }

    function sigKey(cls, name, paramTypes) {
        var ps = [];
        for (var i = 0; i < paramTypes.length; i++) ps.push(paramTypes[i].getName());
        return cls + '.' + name + '(' + ps.join(',') + ')';
    }

    function defaultArg(typeName) {
        if (typeName === 'boolean') return false;
        if (typeName === 'int' || typeName === 'long' || typeName === 'short' || typeName === 'byte') return 0;
        if (typeName === 'float') return 0.0;
        if (typeName === 'double') return 0.0;
        if (typeName === 'char') return '\0';
        if (typeName === 'java.util.List' && lastList) {
            return Java.use('java.util.ArrayList').$new(lastList);
        }
        if (lastO0 && typeName === lastO0.getClass().getName()) return lastO0;
        return null;
    }

    function buildArgs(method) {
        var pts = method.getParameterTypes();
        var capKey = null;
        var out = [];
        for (var i = 0; i < pts.length; i++) out.push(defaultArg(pts[i].getName()));
        return out;
    }

    function findInstances(className) {
        var found = [];
        try {
            Java.choose(className, {
                onMatch: function (inst) {
                    if (found.length < 3) found.push(inst);
                },
                onComplete: function () {}
            });
        } catch (e) {}
        if (found.length === 0 && className.indexOf('MvvmConvList') >= 0) {
            var m = getMvvm();
            if (m) found.push(m);
        }
        if (found.length === 0 && className === 'com.tencent.mm.plugin.mvvmlist.MvvmList') {
            var m2 = getMvvm();
            if (m2) found.push(m2);
        }
        return found;
    }

    function shouldStimMethod(name, paramCount) {
        if (name.length > 4) return false;
        if (name === '<init>') return false;
        if (paramCount > 2) return false;
        return true;
    }

    // ── passive capture on hot path ──────────────────────────────────────

    function installCaptures() {
        var hot = [
            { cls: 'kc5.r0', names: ['d'] },
            { cls: 'ik3.n', names: ['handleEvent'] },
            { cls: 'cl0.u', names: ['V'] },
            { cls: 'fc5.d', names: ['onChanged', 'e'] }
        ];
        hot.forEach(function (h) {
            try {
                var C = Java.use(h.cls);
                var ms = C.class.getDeclaredMethods();
                for (var i = 0; i < ms.length; i++) {
                    var m = ms[i];
                    if (h.names.indexOf(m.getName()) < 0) continue;
                    (function (method) {
                        try {
                            var sig = [];
                            var pts = method.getParameterTypes();
                            for (var j = 0; j < pts.length; j++) sig.push(pts[j].getName());
                            var ov = sig.length ? C[method.getName()].overload.apply(C[method.getName()], sig)
                                : C[method.getName()].overload();
                            var orig = ov;
                            ov.implementation = function () {
                                var args = [].slice.call(arguments);
                                var key = sigKey(h.cls, method.getName(), pts);
                                captures[key] = { inst: this, args: args, ts: Date.now() };
                                if (args.length === 1 && args[0] && args[0].getClass) {
                                    var cn = args[0].getClass().getName();
                                    if (cn.indexOf('ik3.') === 0) lastO0 = args[0];
                                    if (cn.indexOf('List') >= 0 || cn === 'java.util.ArrayList') lastList = args[0];
                                }
                                return orig.apply(this, arguments);
                            };
                        } catch (he) {}
                    })(m);
                }
            } catch (e) {
                console.log(TAG + ' cap skip ' + h.cls + ': ' + e);
            }
        });

        try {
            var MvvmList = Java.use('com.tencent.mm.plugin.mvvmlist.MvvmList');
            var mets = MvvmList.class.getDeclaredMethods();
            for (var wi = 0; wi < mets.length; wi++) {
                var mw = mets[wi];
                if (['w', 'e', 'n', 'm', 's'].indexOf(mw.getName()) < 0) continue;
                (function (method) {
                    try {
                        var pts = method.getParameterTypes();
                        var sig = [];
                        for (var j = 0; j < pts.length; j++) sig.push(pts[j].getName());
                        if (sig.length === 0) return;
                        var ov = MvvmList[method.getName()].overload.apply(MvvmList[method.getName()], sig);
                        var origW = ov;
                        ov.implementation = function () {
                            var args = [].slice.call(arguments);
                            var key = sigKey('MvvmList', method.getName(), pts);
                            captures[key] = { inst: this, args: args, ts: Date.now() };
                            if (args.length >= 1 && args[0] && args[0].size) lastList = args[0];
                            if (args.length >= 1 && args[0] && args[0].getClass) {
                                var cn = args[0].getClass().getName();
                                if (cn.indexOf('ik3.') === 0) lastO0 = args[0];
                            }
                            return origW.apply(this, arguments);
                        };
                    } catch (he) {}
                })(mw);
            }
        } catch (e2) {}

        console.log(TAG + ' capture hooks ready');
    }

    // ── stimulate ────────────────────────────────────────────────────────

    function stimOneMethod(clsName, inst, method) {
        var name = method.getName();
        var pts = method.getParameterTypes();
        var key = sigKey(clsName, name, pts);
        var label = key;

        var argsVariants = [];
        // variant A: captured args
        if (captures[key] && captures[key].args) {
            argsVariants.push({ tag: 'replay', args: captures[key].args });
        }
        // variant B: default/null args
        argsVariants.push({ tag: 'default', args: buildArgs(method) });
        // variant C: empty list for List param only
        if (pts.length === 1 && pts[0].getName() === 'java.util.List') {
            argsVariants.push({ tag: 'emptyList', args: [Java.use('java.util.ArrayList').$new()] });
        }

        var results = [];
        for (var v = 0; v < argsVariants.length; v++) {
            var variant = argsVariants[v];
            var before = wxidHit();
            var ok = false;
            var err = null;
            try {
                method.setAccessible(true);
                var a = variant.args;
                if (a.length === 0) {
                    method.invoke(inst);
                } else if (a.length === 1) {
                    method.invoke(inst, a[0]);
                } else {
                    method.invoke(inst, Java.array('java.lang.Object', a));
                }
                ok = true;
            } catch (e) {
                err = errChain(e);
            }
            var after = wxidHit();
            var hit = (after === 1 && before !== 1);
            console.log(TAG + ' ' + label + ' [' + variant.tag + '] ok=' + ok
                + ' wxid ' + before + '→' + after + (hit ? ' ***HIT***' : ''));
            if (err) console.log(TAG + '   err: ' + err.split('\n')[0]);
            results.push({ hit: hit, ok: ok, variant: variant.tag });
            if (hit) return results;
        }
        return results;
    }

    function stimClass(clsName) {
        console.log('\n' + TAG + ' === CLASS ' + clsName + ' ===');
        var insts = findInstances(clsName);
        console.log(TAG + ' instances=' + insts.length);
        if (insts.length === 0) {
            console.log(TAG + ' skip (no instance)');
            return [];
        }
        var hits = [];
        try {
            var C = Java.use(clsName);
            var ms = C.class.getDeclaredMethods();
            for (var i = 0; i < ms.length; i++) {
                var m = ms[i];
                if (!shouldStimMethod(m.getName(), m.getParameterTypes().length)) continue;
                for (var j = 0; j < insts.length; j++) {
                    var rs = stimOneMethod(clsName, insts[j], m);
                    for (var k = 0; k < rs.length; k++) {
                        if (rs[k].hit) hits.push({ cls: clsName, method: m.getName(), variant: rs[k].variant });
                    }
                }
            }
        } catch (e) {
            console.log(TAG + ' class fail: ' + errChain(e));
        }
        return hits;
    }

    function stimMethod(clsName, methodName) {
        console.log(TAG + ' === METHOD ' + clsName + '.' + methodName + ' ===');
        var insts = findInstances(clsName);
        if (insts.length === 0) { console.log(TAG + ' no instance'); return; }
        try {
            var C = Java.use(clsName);
            var ms = C.class.getDeclaredMethods();
            for (var i = 0; i < ms.length; i++) {
                if (ms[i].getName() !== methodName) continue;
                for (var j = 0; j < insts.length; j++) stimOneMethod(clsName, insts[j], ms[i]);
            }
        } catch (e) {
            console.log(TAG + ' fail: ' + errChain(e));
        }
    }

    function stimAll() {
        console.log(TAG + ' === STIM ALL START wxid=' + TARGET_WXID + ' gap=' + GAP_MS + 'ms ===');
        printCaptures();
        var idx = 0;
        var allHits = [];
        function next() {
            if (idx >= CLASS_TARGETS.length) {
                console.log(TAG + ' === STIM ALL DONE hits=' + JSON.stringify(allHits) + ' ===');
                return;
            }
            var cls = CLASS_TARGETS[idx++];
            Java.scheduleOnMainThread(function () {
                var h = stimClass(cls);
                allHits = allHits.concat(h);
                setTimeout(next, GAP_MS);
            });
        }
        next();
    }

    function printCaptures() {
        console.log(TAG + ' --- captures ---');
        console.log(TAG + ' wxidInList=' + wxidHit() + ' sizes=' + JSON.stringify(listSnap()));
        console.log(TAG + ' lastO0=' + (lastO0 ? lastO0.getClass().getName() : 'none'));
        console.log(TAG + ' lastList=' + (lastList ? lastList.size() : 'none'));
        var keys = Object.keys(captures);
        console.log(TAG + ' capture keys=' + keys.length);
        for (var i = 0; i < keys.length && i < 12; i++) {
            console.log(TAG + '   ' + keys[i]);
        }
    }

    function scanShortClasses() {
        console.log(TAG + ' --- scan loaded (conv-ish short names) ---');
        var re = /^[a-z][a-z0-9]{0,2}\.[a-z0-9]{1,2}$/;
        var n = 0;
        Java.enumerateLoadedClasses({
            onMatch: function (name) {
                if (!re.test(name)) return;
                if (['ik3', 'kc5', 'fc5', 'cl0', 'h45', 'l45', 'v45'].indexOf(name.split('.')[0]) < 0) return;
                console.log(TAG + '   ' + name);
                if (++n >= 40) return 'stop';
            },
            onComplete: function () {
                console.log(TAG + ' scan done (cap 40)');
            }
        });
    }

    // ── REPL ─────────────────────────────────────────────────────────────

    global.p26StimAll = stimAll;
    global.p26StimClass = function (cls) { Java.scheduleOnMainThread(function () { stimClass(cls); }); };
    global.p26StimMethod = function (cls, mn) { Java.scheduleOnMainThread(function () { stimMethod(cls, mn); }); };
    global.p26Captures = printCaptures;
    global.p26ScanClasses = scanShortClasses;
    global.p26SetWxid = function (w) { TARGET_WXID = w; return w; };
    global.p26Wxid = function () { return wxidHit(); };
    global.p26Help = function () {
        console.log(TAG + ' p26StimAll() | p26StimClass("kc5.r0") | p26StimMethod("ik3.n","handleEvent")');
        console.log(TAG + ' p26Captures() | p26ScanClasses() | p26Wxid() | p26SetWxid(wxid)');
        console.log(TAG + ' classes: ' + CLASS_TARGETS.join(', '));
    };

    installCaptures();

    console.log(TAG + ' READY — V态 + 发1条消息 → p26StimAll() 或 p26StimClass("kc5.r0")');
    console.log(TAG + ' 命中看 ***HIT*** 行；贴回 class.method + variant');
    p26Help();
});
