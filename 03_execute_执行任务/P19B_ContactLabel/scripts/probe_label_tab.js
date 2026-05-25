/**
 * probe_label_tab.js v4 — P19B 标签泄漏探针（全量版）
 *
 * v4：窗口内 addAll 全部打印（不限 com.tencent 前缀），确保不漏搜索结果
 *
 * 用法：
 *   frida -U -n "com.tencent.mm" -l "03_execute_执行任务/P19B_ContactLabel/scripts/probe_label_tab.js"
 *
 * 操作：进标签 → 点添加 → 搜索 wxid_lzd2va16jd1622 的昵称 → 贴 [CLB:*]
 */
'use strict';

var clbArm, clbStatus;

Java.perform(function () {
    var TAG = '[CLB]';

    var WINDOW_MS = 60000;
    var MAX_UNIQUE = 20;

    var currentAct = '';
    var probingUntil = 0;
    var seen = {};
    var uniqueCount = 0;

    function actMatchesLabelUi(name) {
        if (!name) return false;
        var n = name.toLowerCase();
        return n.indexOf('label') >= 0
            || n.indexOf('selectcontact') >= 0
            || n.indexOf('searchcontact') >= 0
            || n.indexOf('contactselect') >= 0
            || n.indexOf('contactlabel') >= 0
            || n.indexOf('mvvmcontactlist') >= 0;
    }

    function probingActive() {
        return probingUntil > 0 && Date.now() < probingUntil;
    }

    function armWindow(actName) {
        probingUntil = Date.now() + WINDOW_MS;
        var key = 'arm:' + actName;
        if (!seen[key]) {
            seen[key] = true;
            console.log(TAG + ' ARM window=' + (WINDOW_MS / 1000) + 's act=' + actName);
        }
    }

    function shortStack(skip) {
        try {
            var st = Java.use('java.lang.Thread').currentThread().getStackTrace();
            var lines = [];
            for (var i = skip; i < Math.min(skip + 6, st.length); i++) {
                lines.push('  [' + i + '] ' + st[i].getClassName() + '.' + st[i].getMethodName());
            }
            return lines.join('\n');
        } catch (e) {
            return '';
        }
    }

    function inspectItemWxid(item) {
        if (!item) return null;
        try {
            var cls = item.getClass();
            var fields = cls.getDeclaredFields();
            for (var i = 0; i < fields.length; i++) {
                var f = fields[i];
                f.setAccessible(true);
                try {
                    var val = f.get(item);
                    if (val === null) continue;
                    var s = String(val);
                    if (s.indexOf('wxid_') === 0) {
                        return { wxid: s, field: f.getName(), cls: cls.getName() };
                    }
                } catch (e2) {}
            }
        } catch (e) {}
        return null;
    }

    function dumpWxids(c, label) {
        if (!c || c.isEmpty()) return;
        try {
            var it = c.iterator();
            var found = [];
            while (it.hasNext()) {
                var item = it.next();
                if (!item) continue;
                var r = inspectItemWxid(item);
                if (r) found.push(r.wxid + '(' + r.field + ')');
            }
            if (found.length > 0) {
                console.log(TAG + ' ' + label + ' WXIDS(' + found.length + '): ' + found.join(', '));
            }
        } catch (e) {}
    }

    function getFirstItemClass(c) {
        try {
            var it = c.iterator();
            return it.hasNext() ? it.next().getClass().getName() : '(empty)';
        } catch (e) { return '(err)'; }
    }

    // ── Activity.onResume ──────────────────────────────────────────────
    try {
        var Activity = Java.use('android.app.Activity');
        Activity.onResume.implementation = function () {
            var ret = this.onResume();
            try {
                var name = this.getClass().getName();
                currentAct = name;
                if (actMatchesLabelUi(name)) {
                    armWindow(name);
                }
            } catch (e) {}
            return ret;
        };
        console.log(TAG + ' Activity.onResume ok');
    } catch (e) {
        console.log(TAG + ' Activity hook fail: ' + e);
    }

    // ── ArrayList.addAll — 窗口内全量打印（v4） ────────────────────────
    try {
        var ArrayList = Java.use('java.util.ArrayList');
        var origAddAll = ArrayList.addAll.overload('java.util.Collection');
        origAddAll.implementation = function (c) {
            if (!probingActive()) {
                return origAddAll.call(this, c);
            }
            if (c === null || c.isEmpty()) {
                return origAddAll.call(this, c);
            }
            try {
                var sz = c.size();
                if (uniqueCount >= MAX_UNIQUE) {
                    return origAddAll.call(this, c);
                }
                var fcn = getFirstItemClass(c);

                // 跳过已知噪音
                if (fcn.indexOf('com.tencent.mars') === 0
                    || fcn === 'com.tencent.mm.plugin.performance.watchdogs.b0'
                    || fcn === 'fc5.g') {
                    return origAddAll.call(this, c);
                }

                var key = 'cls:' + fcn + '@' + currentAct;
                if (seen[key]) {
                    return origAddAll.call(this, c);
                }
                seen[key] = true;
                uniqueCount++;

                console.log(TAG + ' addAll itemCls=' + fcn
                    + ' sz=' + sz
                    + ' act=' + currentAct);

                // 打印调用栈（精简）
                console.log(shortStack(3));

                // 拆 wxid
                dumpWxids(c, 'ADDALL');
            } catch (e2) {}
            return origAddAll.call(this, c);
        };
        console.log(TAG + ' ArrayList.addAll ok (all-see v4)');
    } catch (e) {
        console.log(TAG + ' addAll hook fail: ' + e);
    }

    // ── REPL 函数 ─────────────────────────────────────────────────────
    clbArm = function (sec) {
        sec = sec || 60;
        probingUntil = Date.now() + sec * 1000;
        console.log(TAG + ' manual ARM ' + sec + 's act=' + currentAct);
    };

    clbStatus = function () {
        console.log(TAG + ' act=' + currentAct
            + ' probing=' + probingActive()
            + ' leftMs=' + Math.max(0, probingUntil - Date.now())
            + ' logged=' + uniqueCount + '/' + MAX_UNIQUE);
    };

    console.log(TAG + ' v4 ready — 进标签→点添加→搜索→贴 [CLB:*]');
});
