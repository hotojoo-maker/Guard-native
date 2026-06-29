'use strict';
/**
 * probe_r0d_invoke.js — P26 第 2 层探针
 * 目标：捕获 kc5.r0 实例 + 主线程 invoke d()，验证能否触发 fresh-fetch
 *
 * 前置：第 1 层已锁定链路 kc5.r0.d → MvvmList.w → … → notify
 *
 * 用法：
 *   frida -U -n "com.tencent.mm" -l probe_r0d_invoke.js
 *
 * 流程：
 *   1. attach 后先给好友发一条消息（自然捕获 r0 实例 + d() 签名）
 *   2. H→V 切显形
 *   3. Frida REPL 输入：p26InvokeReplay()  →  p26Report()
 *
 * REPL 全局函数（本脚本已暴露，可直接输入，不必 rpc.exports）：
 *   p26Scan()  p26InvokeR0d()  p26InvokeReplay()  p26Report()  p26SetWxid("wxid")
 *   p26Snapshot()  p26Help()
 *
 * 管道 / --eval 示例：
 *   frida -U -n "com.tencent.mm" -l probe_r0d_invoke.js -e "p26Help()"
 *
 * 验收：invoke 后 MvvmList.e / notify 命中 + 用户目视密友是否出现
 * 约束：探针只读 invoke，不改 Guard 模块 / 不碰 StateMachine
 *
 * ⚠️ 副作用（2026-05-25 装机实证）：
 *   attach 期间 hook notifyDataSetChanged → 读消息后返回列表红点不消失。
 *   探针仅用于短时采集，测完必须 detach + force-stop 微信，勿长期挂着做日常操作。
 *
 * 版本：WeChat 8.0.71 (D-014)
 */

Java.perform(function () {

    var TAG             = '[P26-L2]';
    var R0_CLASS        = 'kc5.r0';
    var ADAPTER_CLASS   = 'kc5.v0';
    var MVVM_CLASS      = 'com.tencent.mm.ui.conversation.adapter.MvvmConvList';
    var ITEM_CLASS      = 'kc5.y';
    var LIST_FIELDS     = ['o', 'p', 'h'];
    var ADAPTER_MVVM_FIELDS = ['f286278p', 'p', 'q', 'o', 'r', 'a', 'b'];

    var t0              = Date.now();
    var probeInvoking   = false;
    var savedR0         = null;
    var savedR0Source   = '';
    var savedLastSig    = [];
    var savedLastArgs   = [];
    var naturalDHits    = 0;
    var invokeHits      = 0;
    var invokeErrors    = [];

    var chainHits = {
        'r0.d': 0,
        'MvvmList.w': 0,
        'MvvmList.e': 0,
        'notify': 0
    };

    var lastInvokeSnapshot = null;

    // =========================================================================
    // § 1  工具
    // =========================================================================

    function elapsed() {
        return ((Date.now() - t0) / 1000).toFixed(2) + 's';
    }

    function shortStack(n) {
        try {
            return Java.use('android.util.Log').getStackTraceString(
                Java.use('java.lang.Exception').$new()
            ).split('\n').slice(2, 2 + (n || 10)).join('\n');
        } catch (e) { return '(stack err)'; }
    }

    function sigOf(method) {
        var pts = method.getParameterTypes();
        var out = [];
        for (var i = 0; i < pts.length; i++) out.push(pts[i].getName());
        return out;
    }

    function sigKey(sig) {
        return sig.join(',');
    }

    function setSavedR0(inst, source) {
        if (!inst) return;
        savedR0 = inst;
        savedR0Source = source;
        console.log(TAG + ' r0 captured via ' + source + ' hash=' + inst.hashCode());
    }

    function readField(obj, name) {
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

    function findFieldsOfType(obj, typeName, prefix) {
        var out = [];
        if (!obj) return out;
        prefix = prefix || '';
        try {
            var cls = obj.getClass();
            while (cls && cls.getName() !== 'java.lang.Object') {
                var fields = cls.getDeclaredFields();
                for (var i = 0; i < fields.length; i++) {
                    var f = fields[i];
                    try {
                        f.setAccessible(true);
                        var v = f.get(obj);
                        if (v && v.getClass().getName() === typeName) {
                            out.push({ path: prefix + f.getName(), value: v });
                        }
                    } catch (e) {}
                }
                cls = cls.getSuperclass();
            }
        } catch (e2) {}
        return out;
    }

    function getConvMvvmList() {
        // 1) Java.choose MvvmConvList
        var found = null;
        try {
            Java.choose(MVVM_CLASS, {
                onMatch: function (inst) { if (!found) found = inst; },
                onComplete: function () {}
            });
        } catch (e) {}
        if (found) return found;

        // 2) kc5.v0 adapter → f286278p 等
        try {
            Java.choose(ADAPTER_CLASS, {
                onMatch: function (adapter) {
                    if (found) return;
                    for (var i = 0; i < ADAPTER_MVVM_FIELDS.length; i++) {
                        var mv = readField(adapter, ADAPTER_MVVM_FIELDS[i]);
                        if (mv && mv.getClass().getName() === MVVM_CLASS) {
                            found = mv;
                            break;
                        }
                    }
                },
                onComplete: function () {}
            });
        } catch (e2) {}
        return found;
    }

    function listSizesOnMvvm(mvvm) {
        var snap = { mvvm: mvvm ? mvvm.getClass().getName() : null, fields: {} };
        if (!mvvm) return snap;
        for (var i = 0; i < LIST_FIELDS.length; i++) {
            var fn = LIST_FIELDS[i];
            try {
                var list = readField(mvvm, fn);
                if (list && list.size) snap.fields[fn] = list.size();
            } catch (e) {}
        }
        return snap;
    }

    function countHiddenInList(mvvm, wxid) {
        if (!mvvm || !wxid) return -1;
        var n = 0;
        for (var i = 0; i < LIST_FIELDS.length; i++) {
            try {
                var list = readField(mvvm, LIST_FIELDS[i]);
                if (!list || !list.size) continue;
                for (var j = 0; j < list.size(); j++) {
                    var item = list.get(j);
                    if (!item) continue;
                    try {
                        var d = item.d;
                        if (d) {
                            var wx = d.c0();
                            if (wx && wx.toString() === wxid) { n++; break; }
                        }
                    } catch (e) {}
                }
            } catch (e2) {}
        }
        return n;
    }

    function snapshot(label) {
        var mvvm = getConvMvvmList();
        return {
            label: label,
            t: elapsed(),
            chain: JSON.parse(JSON.stringify(chainHits)),
            mvvm: listSizesOnMvvm(mvvm),
            wxidHit: countHiddenInList(mvvm, TARGET_WXID)
        };
    }

    // 改这里：测试密友 wxid（仅用于 list 计数，不打印正文）
    var TARGET_WXID = 'wxid_lzd2va16jd1622';

    // =========================================================================
    // § 2  链路计数 hook（轻量，确认 invoke 是否走原生链）
    // =========================================================================

    function bumpChain(key) { chainHits[key] = (chainHits[key] || 0) + 1; }

    try {
        var V0 = Java.use('kc5.v0');
        var origNotify = V0.notifyDataSetChanged;
        origNotify.implementation = function () {
            bumpChain('notify');
            if (probeInvoking) {
                console.log(TAG + ' notify during PROBE invoke @' + elapsed());
            }
            origNotify.call(this); // 禁止 this.notifyDataSetChanged() — 会干扰 RV 刷新链
        };
        console.log(TAG + ' hook ok kc5.v0.notifyDataSetChanged (orig.call)');
    } catch (e) {
        console.log(TAG + ' hook fail notify: ' + e);
    }

    try {
        var MvvmList = Java.use('com.tencent.mm.plugin.mvvmlist.MvvmList');
        var methods = MvvmList.class.getDeclaredMethods();
        for (var mi = 0; mi < methods.length; mi++) {
            var m = methods[mi];
            if (m.getName() !== 'e') continue;
            var pts = m.getParameterTypes();
            if (pts.length !== 1 || pts[0].getName() !== 'java.util.List') continue;
            var origE = MvvmList.e.overload('java.util.List');
            origE.implementation = function (list) {
                bumpChain('MvvmList.e');
                if (probeInvoking) {
                    console.log(TAG + ' MvvmList.e sz=' + (list ? list.size() : 0)
                        + ' during PROBE @' + elapsed());
                }
                return origE.call(this, list);
            };
            console.log(TAG + ' hook ok MvvmList.e(List)');
            break;
        }
        for (var wi = 0; wi < methods.length; wi++) {
            var mw = methods[wi];
            if (mw.getName() !== 'w') continue;
            var wpts = mw.getParameterTypes();
            if (wpts.length !== 1) continue;
            var wpt = wpts[0].getName();
            (function (ptName) {
                var origW = MvvmList.w.overload(ptName);
                origW.implementation = function (arg) {
                    bumpChain('MvvmList.w');
                    if (probeInvoking) {
                        console.log(TAG + ' MvvmList.w(' + ptName + ') during PROBE @' + elapsed());
                    }
                    return origW.call(this, arg);
                };
            })(wpt);
            console.log(TAG + ' hook ok MvvmList.w(' + wpt + ')');
            break;
        }
    } catch (e2) {
        console.log(TAG + ' hook fail MvvmList: ' + e2);
    }

    // =========================================================================
    // § 3  kc5.r0.d — 捕获实例 + 记录自然调用参数
    // =========================================================================

    var dOverloads = [];

    function installR0dHooks() {
        try {
            var R0 = Java.use(R0_CLASS);
            var methods = R0.class.getDeclaredMethods();
            var installed = 0;
            for (var i = 0; i < methods.length; i++) {
                var m = methods[i];
                if (m.getName() !== 'd') continue;
                var sig = sigOf(m);
                var key = sigKey(sig);
                if (dOverloads.indexOf(key) >= 0) continue;
                dOverloads.push(key);

                (function (methodSig) {
                    try {
                        var origD = R0.d.overload.apply(R0.d, methodSig);
                        origD.implementation = function () {
                            var fromProbe = probeInvoking;
                            var args = [].slice.call(arguments);
                            if (!fromProbe) {
                                naturalDHits++;
                                setSavedR0(this, 'natural.d(' + methodSig.join(',') + ')');
                                savedLastSig = methodSig;
                                savedLastArgs = args;
                                console.log(TAG + ' natural r0.d(' + methodSig.join(',') + ') #' + naturalDHits
                                    + ' @' + elapsed());
                            } else {
                                invokeHits++;
                                console.log(TAG + ' probe r0.d(' + methodSig.join(',') + ') #' + invokeHits
                                    + ' @' + elapsed());
                            }
                            bumpChain('r0.d');
                            return origD.call.apply(origD, [this].concat(args));
                        };
                        installed++;
                        console.log(TAG + ' hook ok r0.d(' + methodSig.join(',') + ')');
                    } catch (he) {
                        console.log(TAG + ' hook fail r0.d(' + methodSig.join(',') + '): ' + he);
                    }
                })(sig);
            }
            if (installed === 0) {
                console.log(TAG + ' WARN: no r0.d overload hooked — 类名可能错或方法未加载');
            }
        } catch (e) {
            console.log(TAG + ' installR0dHooks FAIL: ' + e);
        }
    }

    installR0dHooks();

    // =========================================================================
    // § 4  扫描 / invoke
    // =========================================================================

    function scanR0() {
        var results = [];

        // choose 全进程实例
        try {
            Java.choose(R0_CLASS, {
                onMatch: function (inst) {
                    results.push({ source: 'Java.choose', inst: inst });
                },
                onComplete: function () {}
            });
        } catch (e) {}

        // MvvmConvList 字段图
        var mvvm = getConvMvvmList();
        if (mvvm) {
            var fromMvvm = findFieldsOfType(mvvm, R0_CLASS, 'mvvm.');
            for (var i = 0; i < fromMvvm.length; i++) {
                results.push({ source: fromMvvm[i].path, inst: fromMvvm[i].value });
            }
        }

        // kc5.v0 adapter 字段图
        try {
            Java.choose(ADAPTER_CLASS, {
                onMatch: function (adapter) {
                    var fromAd = findFieldsOfType(adapter, R0_CLASS, 'adapter.');
                    for (var j = 0; j < fromAd.length; j++) {
                        results.push({ source: fromAd[j].path, inst: fromAd[j].value });
                    }
                },
                onComplete: function () {}
            });
        } catch (e2) {}

        console.log(TAG + ' scanR0 found=' + results.length);
        for (var k = 0; k < results.length; k++) {
            console.log(TAG + '  [' + k + '] ' + results[k].source
                + ' hash=' + results[k].inst.hashCode());
        }

        if (results.length > 0 && !savedR0) {
            setSavedR0(results[0].inst, results[0].source);
        }
        return results.length;
    }

    function invokeR0d(useLastArgs) {
        if (!savedR0) {
            var n = scanR0();
            if (!savedR0) {
                console.log(TAG + ' invoke ABORT: no r0 instance. 请先收发一条消息或 p26Scan()');
                return 'no-instance';
            }
        }

        var sig = useLastArgs && savedLastSig.length >= 0 ? savedLastSig : [];
        var args = useLastArgs && savedLastArgs ? savedLastArgs : [];

        var before = snapshot('before-invoke');
        console.log(TAG + ' invoke START r0.d(' + sig.join(',') + ') source=' + savedR0Source);
        console.log(TAG + ' before: ' + JSON.stringify(before));

        Java.scheduleOnMainThread(function () {
            probeInvoking = true;
            var chainBefore = JSON.parse(JSON.stringify(chainHits));
            try {
                var R0 = Java.use(R0_CLASS);
                if (sig.length === 0) {
                    savedR0.d();
                } else {
                    R0.d.overload.apply(R0.d, sig).call(savedR0, args);
                }
                console.log(TAG + ' invoke OK @' + elapsed());
            } catch (e) {
                invokeErrors.push(String(e));
                console.log(TAG + ' invoke FAIL: ' + e);
                console.log(shortStack(8));
            } finally {
                probeInvoking = false;
            }

            var after = snapshot('after-invoke');
            lastInvokeSnapshot = { before: before, after: after, chainBefore: chainBefore,
                chainAfter: JSON.parse(JSON.stringify(chainHits)) };

            console.log(TAG + ' after: ' + JSON.stringify(after));
            console.log(TAG + ' delta chain: r0.d+' + (chainHits['r0.d'] - chainBefore['r0.d'])
                + ' w+' + (chainHits['MvvmList.w'] - chainBefore['MvvmList.w'])
                + ' e+' + (chainHits['MvvmList.e'] - chainBefore['MvvmList.e'])
                + ' notify+' + (chainHits.notify - chainBefore.notify));
            console.log(TAG + ' >>> 请看会话列表：密友 ' + TARGET_WXID + ' 是否出现？');
        });
        return 'scheduled';
    }

    function printReport() {
        console.log('\n' + TAG + ' ========== L2 REPORT @' + elapsed() + ' ==========');
        console.log(TAG + ' r0 instance: ' + (savedR0 ? ('yes source=' + savedR0Source) : 'NO'));
        console.log(TAG + ' d overloads: ' + dOverloads.join(' | '));
        console.log(TAG + ' natural d hits=' + naturalDHits + ' probe invoke hits=' + invokeHits);
        console.log(TAG + ' chain totals: ' + JSON.stringify(chainHits));
        if (savedLastSig.length) {
            console.log(TAG + ' last natural sig: d(' + savedLastSig.join(',') + ') args=' + savedLastArgs.length);
        }
        if (savedR0) {
            console.log(TAG + ' savedR0 hash=' + savedR0.hashCode() + ' source=' + savedR0Source);
        }
        if (lastInvokeSnapshot) {
            console.log(TAG + ' last invoke before: ' + JSON.stringify(lastInvokeSnapshot.before));
            console.log(TAG + ' last invoke after:  ' + JSON.stringify(lastInvokeSnapshot.after));
        }
        if (invokeErrors.length) {
            console.log(TAG + ' invoke errors: ' + invokeErrors.join(' | '));
        }
        console.log(TAG + ' commands: p26Scan() | p26InvokeR0d() | p26InvokeReplay() | p26Report() | p26Help()');
        console.log(TAG + ' ========== END ==========\n');
    }

    function printHelp() {
        console.log(TAG + ' REPL globals:');
        console.log(TAG + '   p26Scan()           — scan r0 instances');
        console.log(TAG + '   p26InvokeR0d()      — invoke r0.d() no-arg');
        console.log(TAG + '   p26InvokeReplay()   — invoke with last natural args (preferred)');
        console.log(TAG + '   p26Report()         — full report');
        console.log(TAG + '   p26SetWxid("wxid")  — target wxid for list check');
        console.log(TAG + '   p26Snapshot()       — JSON snapshot now');
        console.log(TAG + '   p26Help()           — this help');
        if (savedR0) {
            console.log(TAG + ' savedR0: hash=' + savedR0.hashCode() + ' source=' + savedR0Source);
        } else {
            console.log(TAG + ' savedR0: (none — send a message first)');
        }
    }

    // =========================================================================
    // § 5  RPC + REPL 全局暴露
    // =========================================================================

    rpc.exports = {
        p26Scan: function () { return scanR0(); },
        p26InvokeR0d: function () { return invokeR0d(false); },
        p26InvokeReplay: function () { return invokeR0d(true); },
        p26Report: function () { printReport(); },
        p26SetWxid: function (wxid) { TARGET_WXID = wxid; return TARGET_WXID; },
        p26Snapshot: function () { return JSON.stringify(snapshot('manual')); },
        p26Help: function () { printHelp(); }
    };

    // Frida REPL / -e / pipe 注入用（rpc.exports 仅 Python 客户端）
    global.p26Scan = function () { return scanR0(); };
    global.p26InvokeR0d = function () { return invokeR0d(false); };
    global.p26InvokeReplay = function () { return invokeR0d(true); };
    global.p26Report = function () { printReport(); };
    global.p26SetWxid = function (wxid) { TARGET_WXID = wxid; return TARGET_WXID; };
    global.p26Snapshot = function () { return JSON.stringify(snapshot('manual')); };
    global.p26Help = function () { printHelp(); };

    console.log(TAG + ' === READY ===');
    console.log(TAG + ' Step1: 收发一条消息（自然捕获 r0.d，目标 hash 应稳定）');
    console.log(TAG + ' Step2: H→V 切显形');
    console.log(TAG + ' Step3: REPL → p26InvokeReplay()  等 1s  →  p26Report()');
    console.log(TAG + ' Step4: 目视密友是否出现；测完 detach Frida');
    console.log(TAG + ' target wxid=' + TARGET_WXID + ' | type p26Help() for commands');
});
