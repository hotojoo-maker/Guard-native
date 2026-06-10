/**
 * probe_group_leak.js — 密群泄漏探针（复用 P19B v4 框架）
 * 抓所有 ArrayList.addAll / LinkedList.add / HashSet.add 中的 @chatroom
 */
'use strict';

var clbArm, clbStatus;

Java.perform(function () {
    var TAG = '[GRP]';
    var WINDOW_MS = 60000;
    var MAX_UNIQUE = 30;

    var currentAct = '';
    var probingUntil = 0;
    var seen = {};
    var uniqueCount = 0;

    function probingActive() {
        return probingUntil > 0 && Date.now() < probingUntil;
    }

    function armWindow(actName) {
        probingUntil = Date.now() + WINDOW_MS;
        if (!seen['arm:' + actName]) {
            seen['arm:' + actName] = true;
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
        } catch (e) { return ''; }
    }

    function hasGroupId(s) {
        return s && s.indexOf('@chatroom') > 0;
    }

    function logFirst(key, msg) {
        if (uniqueCount >= MAX_UNIQUE || seen[key]) return;
        seen[key] = true;
        uniqueCount++;
        console.log(TAG + ' ' + msg);
    }

    // ── Activity ──────────────────────────────────────────────────────
    try {
        var Activity = Java.use('android.app.Activity');
        Activity.onResume.implementation = function () {
            var ret = this.onResume();
            try {
                currentAct = this.getClass().getName();
                armWindow(currentAct);
            } catch (e) {}
            return ret;
        };
        console.log(TAG + ' Activity ok');
    } catch (e) {}

    // ── ArrayList.addAll ─────────────────────────────────────────────
    try {
        var ArrayList = Java.use('java.util.ArrayList');
        ArrayList.addAll.overload('java.util.Collection').implementation = function (c) {
            if (probingActive() && c && !c.isEmpty()) {
                var sz = c.size();
                var it = c.iterator();
                while (it.hasNext()) {
                    var item = it.next();
                    if (item && hasGroupId(String(item))) {
                        var cn = item.getClass().getName();
                        logFirst('addAll:' + cn + '@' + currentAct,
                            'addAll itemCls=' + cn + ' sz=' + sz + ' act=' + currentAct + '\n' + shortStack(3));
                        break;
                    }
                }
            }
            return this.addAll(c);
        };
        console.log(TAG + ' addAll ok');
    } catch (e) {
        console.log(TAG + ' addAll fail: ' + e);
    }

    // ── LinkedList.add ───────────────────────────────────────────────
    try {
        var LinkedList = Java.use('java.util.LinkedList');
        LinkedList.add.overload('java.lang.Object').implementation = function (item) {
            if (probingActive() && item && hasGroupId(String(item))) {
                var cn = item.getClass().getName();
                logFirst('ll:' + cn + '@' + currentAct,
                    'LinkedList.add itemCls=' + cn + ' act=' + currentAct + '\n' + shortStack(3));
            }
            return this.add(item);
        };
        console.log(TAG + ' LinkedList ok');
    } catch (e) {}

    // ── HashSet.add ──────────────────────────────────────────────────
    try {
        var HashSet = Java.use('java.util.HashSet');
        HashSet.add.overload('java.lang.Object').implementation = function (o) {
            if (probingActive() && o) {
                var s = String(o);
                if (hasGroupId(s)) {
                    logFirst('hs:' + s + '@' + currentAct,
                        'HashSet.add val=' + s + ' act=' + currentAct + '\n' + shortStack(3));
                }
            }
            return this.add(o);
        };
        console.log(TAG + ' HashSet ok');
    } catch (e) {}

    // ── REPL ──────────────────────────────────────────────────────────
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

    console.log(TAG + ' ready — 窗口内任何 Activity 都会触达，操作含密群页面即可');
});
