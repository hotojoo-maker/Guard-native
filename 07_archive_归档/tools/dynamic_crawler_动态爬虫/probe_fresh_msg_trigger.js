'use strict';
/**
 * probe_fresh_msg_trigger.js — P26 爬虫层
 * 目标：反向追 kc5.v0.notifyDataSetChanged 调用栈，找 fresh item 写入入口
 *
 * 背景（P26 brief）：
 *   发新消息后 fresh item 绕过 MvvmList.n/m 与 L1 addAll hook，
 *   直接写底层 ArrayList 再 notify；需从栈里定位 WeChat 内部 fetch 入口。
 *
 * 用法：
 *   frida -U -n "com.tencent.mm" -l probe_fresh_msg_trigger.js
 *   脚本就绪后 → 进会话列表 → 给任意好友/密友发一条短消息 → 等 30s 自动汇总
 *   或 REPL：p26Report() 立即输出报告
 *
 * 约束：只读探针 / 限时 30s / MAX_HOOKS ≤ 80 / 不打印聊天正文
 *
 * ⚠️ 副作用（2026-05-25 装机实证）：
 *   attach 期间 hook notifyDataSetChanged + 全局 ArrayList → 读消息后返回列表红点不消失。
 *   仅短时采集；测完 detach + force-stop 微信。日常操作勿挂探针。
 *
 * 版本：WeChat 8.0.71 (D-014)
 */

Java.perform(function () {

    // =========================================================================
    // § 0  配置
    // =========================================================================

    var TAG           = '[P26]';
    var CRAWL_MS      = 30000;
    var MAX_HOOKS     = 80;
    var MAX_HITS      = 15;      // 每个 hook 点最多记录次数
    var NOTIFY_MAX    = 25;      // notifyDataSetChanged 最多全栈记录次数
    var STACK_DEPTH   = 24;
    var ITEM_CLASS    = 'kc5.y'; // 8.0.71 会话 item

    var hookCount     = 0;
    var t0            = Date.now();
    var notifyHits    = [];
    var upstreamHits  = [];
    var writeHits     = [];
    var hookInstalled = {};

    // 已知上游链路（CONV_REFRESH_PROBLEM.md §六，待 P26 实测验证）
    var UPSTREAM_TARGETS = [
        { cls: 'ik3.n',     methods: ['handleEvent'], listParam: true },
        { cls: 'cl0.u',     methods: ['V'] },
        { cls: 'ik3.m',     methods: null },          // 枚举无参/单参
        { cls: 'h45.i',     methods: ['handleMessage'], msgParam: true },
        { cls: 'h45.f',     methods: ['handleMessage'], msgParam: true },
        { cls: 'kc5.r0',    methods: ['d'] },
        { cls: 'kc5.a',     methods: null, maxMethods: 8 },
        { cls: 'fc5.d',     methods: ['onChanged', 'e'] },
        { cls: 'l45.g',     methods: ['notify'] },
        { cls: 'v45.e',     methods: ['handleEvent'] }
    ];

    var MVVM_METHODS = [
        { name: 'n', sig: ['java.util.List', 'boolean'] },
        { name: 'm', sig: ['java.util.List', 'boolean'] },
        { name: 'e', sig: ['java.util.List'] },
        { name: 'w', sig: null },   // 单参，类型运行时匹配
        { name: 's', sig: ['java.util.List'] }
    ];

    // =========================================================================
    // § 1  工具
    // =========================================================================

    function elapsed() {
        return ((Date.now() - t0) / 1000).toFixed(2) + 's';
    }

    function fullStack(depth) {
        try {
            return Java.use('android.util.Log').getStackTraceString(
                Java.use('java.lang.Exception').$new()
            ).split('\n').slice(2, 2 + (depth || STACK_DEPTH)).join('\n');
        } catch (e) { return '(stack err: ' + e + ')'; }
    }

    function parseFrames(stackStr) {
        var out = [];
        var lines = (stackStr || '').split('\n');
        for (var i = 0; i < lines.length; i++) {
            var line = lines[i].trim();
            if (line.indexOf('at ') !== 0) continue;
            var m = line.match(/at ([\w.$]+)\.(\w+)\(/);
            if (m) out.push({ cls: m[1], method: m[2], raw: line });
        }
        return out;
    }

    function isNoiseFrame(cls) {
        if (!cls) return true;
        return /^(android\.|androidx\.|java\.|javax\.|kotlin\.|dalvik\.|com\.android\.)/.test(cls)
            || cls.indexOf('probe_fresh_msg_trigger') >= 0
            || cls === 'kc5.v0';
    }

    function extractCandidates(stackStr) {
        var frames = parseFrames(stackStr);
        var cands = [];
        for (var i = 0; i < frames.length; i++) {
            var f = frames[i];
            if (isNoiseFrame(f.cls)) continue;
            cands.push(f.cls + '.' + f.method);
            if (cands.length >= 8) break;
        }
        return cands;
    }

    function canInstall(key) {
        return hookCount < MAX_HOOKS && !hookInstalled[key];
    }

    function markInstalled(key) {
        hookInstalled[key] = true;
        hookCount++;
    }

    function recordUpstream(tag, detail, stackStr) {
        upstreamHits.push({
            t: elapsed(),
            tag: tag,
            detail: detail || '',
            cands: extractCandidates(stackStr),
            stack: stackStr
        });
        console.log('\n' + TAG + ' UP ' + tag + ' @' + elapsed()
            + (detail ? ' ' + detail : ''));
        console.log(TAG + ' cands: ' + extractCandidates(stackStr).join(' > '));
    }

    function recordWrite(tag, detail, stackStr) {
        writeHits.push({
            t: elapsed(),
            tag: tag,
            detail: detail || '',
            cands: extractCandidates(stackStr),
            stack: stackStr
        });
        console.log('\n' + TAG + ' WR ' + tag + ' @' + elapsed()
            + (detail ? ' ' + detail : ''));
        console.log(TAG + ' cands: ' + extractCandidates(stackStr).join(' > '));
    }

    // =========================================================================
    // § 2  主探针 — kc5.v0.notifyDataSetChanged
    // =========================================================================

    var notifyCount = 0;

    try {
        var V0 = Java.use('kc5.v0');
        var origNotify = V0.notifyDataSetChanged;
        origNotify.implementation = function () {
            notifyCount++;
            var n = notifyCount;

            // 先调原版，再采栈 — 避免 fullStack 阻塞主线程导致 RV 刷新/红点更新异常
            origNotify.call(this);

            if (n <= NOTIFY_MAX) {
                var stackStr = fullStack(STACK_DEPTH);
                var cands = extractCandidates(stackStr);
                notifyHits.push({
                    n: n,
                    t: elapsed(),
                    cands: cands,
                    stack: stackStr
                });
                console.log('\n' + TAG + ' ===== NOTIFY #' + n + ' @' + elapsed() + ' =====');
                console.log(TAG + ' candidates: ' + (cands.length ? cands.join(' > ') : '(none parsed)'));
                console.log(stackStr);
            } else if (n === NOTIFY_MAX + 1) {
                console.log(TAG + ' NOTIFY hit cap (' + NOTIFY_MAX + '), further hits suppressed');
            }
        };
        markInstalled('kc5.v0.notifyDataSetChanged');
        console.log(TAG + ' primary: kc5.v0.notifyDataSetChanged ok (orig.call first)');
    } catch (e) {
        console.log(TAG + ' primary FAIL: ' + e);
    }

    // =========================================================================
    // § 3  上游关联 hook（已知链路 + MvvmList）
    // =========================================================================

    function hookMethod0(clsName, methodName, tag, detailFn) {
        var key = clsName + '.' + methodName + '()';
        if (!canInstall(key)) return false;
        try {
            var C = Java.use(clsName);
            var hits = 0;
            var orig0 = C[methodName].overload();
            orig0.implementation = function () {
                hits++;
                var ret = orig0.call(this);
                if (hits <= MAX_HITS) {
                    var detail = detailFn ? detailFn.call(this, arguments) : '';
                    recordUpstream(tag || key, detail, fullStack(STACK_DEPTH));
                }
                return ret;
            };
            markInstalled(key);
            console.log(TAG + ' hook ok ' + key);
            return true;
        } catch (e) { return false; }
    }

    function hookMethodWithSig(clsName, methodName, sig, tag, detailFn) {
        var key = clsName + '.' + methodName + '(' + sig.join(',') + ')';
        if (!canInstall(key)) return false;
        try {
            var C = Java.use(clsName);
            var hits = 0;
            var fn = C[methodName].overload.apply(C[methodName], sig);
            fn.implementation = function () {
                hits++;
                var args = [].slice.call(arguments);
                var ret = fn.call.apply(fn, [this].concat(args));
                if (hits <= MAX_HITS) {
                    var detail = detailFn ? detailFn.apply(null, args) : '';
                    recordUpstream(tag || key, detail, fullStack(STACK_DEPTH));
                }
                return ret;
            };
            markInstalled(key);
            console.log(TAG + ' hook ok ' + key);
            return true;
        } catch (e) { return false; }
    }

    function hookClassMethods(target) {
        var clsName = target.cls;
        try {
            var C = Java.use(clsName);
            if (target.methods) {
                for (var mi = 0; mi < target.methods.length; mi++) {
                    var mn = target.methods[mi];
                    if (target.msgParam) {
                        hookMethodWithSig(clsName, mn, ['android.os.Message'], clsName + '.' + mn, null);
                    } else if (target.listParam) {
                        hookMethodWithSig(clsName, mn, ['java.util.List'], clsName + '.' + mn,
                            function (list) {
                                return 'sz=' + (list ? list.size() : 0);
                            });
                    } else {
                        hookMethod0(clsName, mn, clsName + '.' + mn, null);
                    }
                }
            } else {
                // 枚举短名方法（ik3.m / kc5.a）
                var methods = C.class.getDeclaredMethods();
                var hooked = 0;
                var cap = target.maxMethods || 6;
                for (var i = 0; i < methods.length && hooked < cap; i++) {
                    var m = methods[i];
                    var name = m.getName();
                    if (name.length > 3) continue;
                    var pts = m.getParameterTypes();
                    if (pts.length > 2) continue;
                    if (pts.length === 0) {
                        if (hookMethod0(clsName, name, clsName + '.' + name, null)) hooked++;
                    } else if (pts.length === 1) {
                        var pt = pts[0].getName();
                        if (hookMethodWithSig(clsName, name, [pt], clsName + '.' + name + '(' + pt + ')', null)) hooked++;
                    } else if (pts.length === 2 && pts[0].getName() === 'java.util.List') {
                        var pt1 = pts[1].getName();
                        if (hookMethodWithSig(clsName, name, ['java.util.List', pt1],
                            clsName + '.' + name + '(List,' + pt1 + ')', null)) hooked++;
                    }
                }
            }
        } catch (e) {
            console.log(TAG + ' upstream ' + clsName + ' fail: ' + e.message);
        }
    }

    for (var ui = 0; ui < UPSTREAM_TARGETS.length; ui++) {
        if (hookCount >= MAX_HOOKS) break;
        hookClassMethods(UPSTREAM_TARGETS[ui]);
    }

    // MvvmList 关键方法
    try {
        var MvvmList = Java.use('com.tencent.mm.plugin.mvvmlist.MvvmList');
        for (var mi = 0; mi < MVVM_METHODS.length; mi++) {
            if (hookCount >= MAX_HOOKS) break;
            var spec = MVVM_METHODS[mi];
            if (spec.sig) {
                hookMethodWithSig('com.tencent.mm.plugin.mvvmlist.MvvmList', spec.name, spec.sig,
                    'MvvmList.' + spec.name, function (list) {
                        return 'sz=' + (list ? list.size() : 0);
                    });
            } else {
                // w(single) — 枚举单参 overload
                var methods = MvvmList.class.getDeclaredMethods();
                for (var j = 0; j < methods.length; j++) {
                    var m = methods[j];
                    if (m.getName() !== spec.name) continue;
                    var pts = m.getParameterTypes();
                    if (pts.length !== 1) continue;
                    var pt = pts[0].getName();
                    hookMethodWithSig('com.tencent.mm.plugin.mvvmlist.MvvmList', spec.name, [pt],
                        'MvvmList.' + spec.name + '(' + pt + ')', null);
                    break;
                }
            }
        }
    } catch (e) {
        console.log(TAG + ' MvvmList fail: ' + e.message);
    }

    // =========================================================================
    // § 4  直接写入路径 — ArrayList.add / add(int,) 仅 kc5.y
    // =========================================================================

    function isConvItem(obj) {
        if (!obj) return false;
        try { return obj.getClass().getName() === ITEM_CLASS; } catch (e) { return false; }
    }

    function tryReadWxid(item) {
        try {
            var d = item.d;
            if (d) {
                try {
                    var wx = d.c0();
                    if (wx) return wx.toString();
                } catch (e0) {}
            }
        } catch (e1) {}
        try { return item.toString().substring(0, 40); } catch (e2) { return '?'; }
    }

    try {
        var AL = Java.use('java.util.ArrayList');
        var addHits = 0;

        var origAdd = AL.add.overload('java.lang.Object');
        origAdd.implementation = function (obj) {
            var ret = origAdd.call(this, obj);
            if (isConvItem(obj) && addHits < MAX_HITS) {
                addHits++;
                var wxid = tryReadWxid(obj);
                recordWrite('ArrayList.add', 'wxid=' + wxid + ' listSz=' + this.size(), fullStack(STACK_DEPTH));
            }
            return ret;
        };
        markInstalled('ArrayList.add(Object)');

        var origAddIdx = AL.add.overload('int', 'java.lang.Object');
        origAddIdx.implementation = function (idx, obj) {
            var ret = origAddIdx.call(this, idx, obj);
            if (isConvItem(obj) && addHits < MAX_HITS) {
                addHits++;
                var wxid = tryReadWxid(obj);
                recordWrite('ArrayList.add(idx)', 'idx=' + idx + ' wxid=' + wxid + ' listSz=' + this.size(),
                    fullStack(STACK_DEPTH));
            }
            return ret;
        };
        markInstalled('ArrayList.add(int,Object)');

        var origSet = AL.set.overload('int', 'java.lang.Object');
        origSet.implementation = function (idx, obj) {
            var ret = origSet.call(this, idx, obj);
            if (isConvItem(obj) && addHits < MAX_HITS) {
                addHits++;
                var wxid = tryReadWxid(obj);
                recordWrite('ArrayList.set', 'idx=' + idx + ' wxid=' + wxid, fullStack(STACK_DEPTH));
            }
            return ret;
        };
        markInstalled('ArrayList.set(int,Object)');

        console.log(TAG + ' write-path: ArrayList add/set (' + ITEM_CLASS + ' filter) ok');
    } catch (e) {
        console.log(TAG + ' write-path FAIL: ' + e);
    }

    // =========================================================================
    // § 5  汇总报告
    // =========================================================================

    function aggregateCandidates(events) {
        var freq = {};
        for (var i = 0; i < events.length; i++) {
            var c = events[i].cands || [];
            for (var j = 0; j < c.length; j++) {
                freq[c[j]] = (freq[c[j]] || 0) + 1;
            }
        }
        var sorted = Object.keys(freq).sort(function (a, b) { return freq[b] - freq[a]; });
        return sorted.slice(0, 12).map(function (k) { return k + '(' + freq[k] + ')'; });
    }

    function printReport() {
        console.log('\n' + TAG + ' ========== P26 REPORT @' + elapsed() + ' ==========');
        console.log(TAG + ' hooksInstalled=' + hookCount + '/' + MAX_HOOKS);
        console.log(TAG + ' notifyTotal=' + notifyCount + ' logged=' + notifyHits.length);
        console.log(TAG + ' upstreamEvents=' + upstreamHits.length + ' writeEvents=' + writeHits.length);

        if (notifyHits.length === 0) {
            console.log(TAG + ' ⚠ NO notify hits — 请确认：微信在前台 / 会话列表可见 / 已发一条消息');
        } else {
            console.log(TAG + ' --- NOTIFY candidate ranking ---');
            console.log(TAG + ' ' + aggregateCandidates(notifyHits).join(' | '));

            console.log(TAG + ' --- last NOTIFY stack (#' + notifyHits[notifyHits.length - 1].n + ') ---');
            console.log(notifyHits[notifyHits.length - 1].stack);

            // 找 NOTIFY 前最近的上游/写入事件（5s 窗口内按时间近似）
            var lastT = parseFloat(notifyHits[notifyHits.length - 1].t);
            console.log(TAG + ' --- events within ~2s before last NOTIFY ---');
            var pool = upstreamHits.concat(writeHits);
            var near = [];
            for (var i = 0; i < pool.length; i++) {
                var dt = lastT - parseFloat(pool[i].t);
                if (dt >= 0 && dt <= 2.0) near.push(pool[i]);
            }
            if (near.length === 0) {
                console.log(TAG + ' (none — fresh 路径可能不经已知 upstream hook)');
            } else {
                for (var j = 0; j < near.length; j++) {
                    console.log(TAG + '  ' + near[j].t + ' ' + near[j].tag + ' ' + near[j].detail
                        + ' → ' + near[j].cands.join(' > '));
                }
            }
        }

        if (writeHits.length > 0) {
            console.log(TAG + ' --- WRITE path candidate ranking ---');
            console.log(TAG + ' ' + aggregateCandidates(writeHits).join(' | '));
        }

        console.log(TAG + ' --- 下一步 ---');
        console.log(TAG + ' 从 NOTIFY 栈顶向下找第一个 WeChat 混淆帧 = fresh-fetch 入口候选');
        console.log(TAG + ' 若 WRITE 有命中且 upstream 无命中 → 入口在 ArrayList 更上游');
        console.log(TAG + ' 结果写入: 03_execute_执行任务/P26_好友热切fresh触发/result.md');
        console.log(TAG + ' ========== END REPORT ==========\n');
    }

    rpc.exports = {
        p26Report: function () { printReport(); },
        p26Reset: function () {
            notifyCount = 0;
            notifyHits = [];
            upstreamHits = [];
            writeHits = [];
            t0 = Date.now();
            console.log(TAG + ' counters reset');
        }
    };

    setTimeout(function () { printReport(); }, CRAWL_MS);

    console.log(TAG + ' === READY (hooks=' + hookCount + '/' + MAX_HOOKS + ', window=' + (CRAWL_MS / 1000) + 's) ===');
    console.log(TAG + ' 操作：进会话列表 → 给好友/密友发一条短消息 → 观察 NOTIFY/UP/WR 行');
    console.log(TAG + ' 手动报告：p26Report()');
});
