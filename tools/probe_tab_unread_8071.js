// probe_tab_unread_8071.js — 微信 8.0.71 底部 Tab 未读数展示入口探针
//
// 背景：Catfish 8.0.70（破解版）动态实证：
//   - showUnReadMsgCount(int)→int 是会话 tab 未读数展示入口，HIDDEN 态恒返回 0
//   - sHiddenUnread 字段存储原始 count，用于 V 态恢复
// 目标：在 com.tencent.mm (8.0.71) 中找到语义等价入口
//
// 用法：
//   frida -U -p $(adb shell "ps -ef|grep tencent.mm$" | awk '{print $2}') \
//         -l probe_tab_unread_8071.js 2>&1 | tee tools/probe_tab_unread_8071_HHMMSS.log
//
// 场景（按顺序操作）：
//   1. 普通好友发一条消息 → 观察哪些方法命中 + in/out
//   2. 密友发一条消息    → 对比 in/out 差异
//   3. 切到微信主界面，观察 tab 数字变化时的调用
//
// 禁止：不改代码 / 不写 DB / 不阻断 push

(function () {
    'use strict';

    var TAG = 'TAB_UNREAD';
    var MAX_HOOKS = 120;   // 防止挂太多导致卡顿
    var hookCount = 0;

    function ts() {
        var d = new Date();
        var p = function (n) { return n < 10 ? '0' + n : '' + n; };
        return p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds()) +
            '.' + (d.getMilliseconds() + '000').substring(0, 3);
    }
    function log(sub, msg) { console.log(ts() + ' [' + TAG + ':' + sub + '] ' + msg); }

    // 取调用栈摘要（最多 6 帧，过滤 Frida 内部帧）
    function stackSummary() {
        try {
            var lines = Java.use('java.lang.Thread').currentThread()
                .getStackTrace();
            var out = [];
            for (var i = 0; i < lines.length && out.length < 6; i++) {
                var s = String(lines[i]);
                if (s.indexOf('com.android.internal') >= 0) continue;
                if (s.indexOf('java.lang.reflect') >= 0) continue;
                if (s.indexOf('dalvik.') >= 0) continue;
                out.push(s.trim());
            }
            return out.join(' ← ');
        } catch (e) { return '?'; }
    }

    // 关键词列表 — 先宽后窄
    var KEYWORDS = [
        'maintab', 'MainTab',
        'LauncherUI', 'launcher',
        'UnreadMgr', 'UnreadCount', 'Unread',
        'BadgeMgr', 'BadgeCount', 'Badge',
        'TabBadge', 'TabUnread',
        'notification', 'Notification',
        'redDot', 'RedDot', 'reddot',
        'TabRed', 'tabRed',
    ];

    function matchesKeyword(name) {
        for (var i = 0; i < KEYWORDS.length; i++) {
            if (name.indexOf(KEYWORDS[i]) >= 0) return true;
        }
        return false;
    }

    // 已 hook 的类集合
    var hookedClasses = {};
    // 命中统计：class.method → {hits, inValues, outValues}
    var hitStats = {};

    function recordHit(key, inVal, outVal) {
        if (!hitStats[key]) hitStats[key] = { hits: 0, ins: [], outs: [] };
        var s = hitStats[key];
        s.hits++;
        if (s.ins.indexOf(inVal) < 0 && s.ins.length < 8) s.ins.push(inVal);
        if (s.outs.indexOf(outVal) < 0 && s.outs.length < 8) s.outs.push(outVal);
    }

    // 为候选类 hook 所有 int→int 方法 + (int)->void 方法
    function hookClass(clsName) {
        if (hookedClasses[clsName]) return;
        if (hookCount >= MAX_HOOKS) return;
        hookedClasses[clsName] = true;

        try {
            var cls = Java.use(clsName);
            var methods = cls.class.getDeclaredMethods();
            var hooked = 0;

            for (var i = 0; i < methods.length; i++) {
                if (hookCount >= MAX_HOOKS) break;
                var m = methods[i];
                var mname = m.getName();
                var ptypes = m.getParameterTypes();
                var rtype = m.getReturnType().getName();
                var pnames = [];
                for (var j = 0; j < ptypes.length; j++) pnames.push(ptypes[j].getName());

                var isIntReturn = (rtype === 'int');
                var hasIntParam = pnames.indexOf('int') >= 0;
                var paramCount  = ptypes.length;

                // 只关心：
                // A. int→int（单参数，参数是 int，返回 int）
                // B. ()→int（无参 getter，返回 int，可能是 unread getter）
                // C. (int)→void（可能是 badge setter）
                var interesting = false;
                if (isIntReturn && paramCount === 1 && hasIntParam) interesting = true;
                if (isIntReturn && paramCount === 0) interesting = true;
                if (rtype === 'void' && paramCount === 1 && hasIntParam) interesting = true;

                if (!interesting) continue;

                (function (methodName, paramTypes, retType) {
                    try {
                        var overload = cls[methodName].overload.apply(cls[methodName], paramTypes);
                        overload.implementation = function () {
                            var args = Array.prototype.slice.call(arguments);
                            var ret = this[methodName].apply(this, args);
                            var key = clsName.split('.').pop() + '.' + methodName +
                                '(' + paramTypes.join(',') + ')→' + retType;
                            var inVal = args.length > 0 ? args[0] : -1;
                            var outVal = (retType !== 'void') ? ret : -1;
                            recordHit(key, inVal, outVal);
                            // 只打前 3 次，避免刷屏
                            if (hitStats[key].hits <= 3) {
                                log('HIT', key + ' in=' + inVal + ' out=' + outVal);
                                log('STK', stackSummary());
                            } else if (hitStats[key].hits === 4) {
                                log('HIT', key + ' [muted after 3 hits, still counting]');
                            }
                            return ret;
                        };
                        hookCount++;
                        hooked++;
                    } catch (e2) { /* overload not found, skip */ }
                })(mname, pnames, rtype);
            }

            if (hooked > 0) {
                log('HOOK', clsName.split('.').pop() + ' hooked ' + hooked + ' methods');
            }
        } catch (e) {
            log('ERR', clsName + ': ' + e);
        }
    }

    // 打印候选排名
    function printRanking() {
        var keys = Object.keys(hitStats);
        keys.sort(function (a, b) { return hitStats[b].hits - hitStats[a].hits; });

        log('RANK', '====== 候选方法排名 (Catfish showUnReadMsgCount 等价) ======');
        log('RANK', '背景：Catfish 8.0.70 破解版实证 showUnReadMsgCount(int)→0 归零 tab badge');
        for (var i = 0; i < Math.min(keys.length, 20); i++) {
            var k = keys[i];
            var s = hitStats[k];
            var isDiffInOut = false;
            for (var j = 0; j < s.ins.length; j++) {
                if (s.outs.indexOf(0) >= 0 && s.ins[j] > 0) { isDiffInOut = true; break; }
                if (s.outs.length > 0 && s.ins[j] !== s.outs[j]) { isDiffInOut = true; break; }
            }
            log('RANK', '[' + (i + 1) + '] ' + k +
                ' hits=' + s.hits +
                ' in=' + JSON.stringify(s.ins) +
                ' out=' + JSON.stringify(s.outs) +
                (isDiffInOut ? ' ⭐ in!=out 候选' : ''));
        }
        log('RANK', '====================================================');
        log('RANK', '⭐ in!=out 且 out 含 0 的方法 = 最可能是 tab unread 展示入口');
        log('RANK', '高频触发 + 随消息到来递增 in = 更上游路径');
    }

    Java.perform(function () {
        log('INIT', '=== probe_tab_unread_8071.js loaded ===');
        log('INIT', '目标: 微信 8.0.71 (com.tencent.mm) — 寻找 Catfish showUnReadMsgCount 等价入口');
        log('INIT', '参考: Catfish 8.0.70 破解版实证: showUnReadMsgCount(int)→int 恒返 0 (HIDDEN 态)');

        // === Phase 1: 先枚举已加载类，匹配关键词 ===
        var candidates = [];
        Java.enumerateLoadedClasses({
            onMatch: function (name) {
                if (name.indexOf('com.tencent.mm') < 0) return;
                if (matchesKeyword(name)) candidates.push(name);
            },
            onComplete: function () {
                log('SCAN', '已加载类中匹配到 ' + candidates.length + ' 个候选类');
                for (var i = 0; i < candidates.length; i++) {
                    log('CAND', candidates[i]);
                    hookClass(candidates[i]);
                }
                log('SCAN', '已安装 hooks: ' + hookCount + ' 个方法');
                log('SCAN', '--- 请操作: 普通好友发消息 → 密友发消息 → 切 H/V ---');

                // === Phase 2: 30s 后打排名 ===
                setTimeout(printRanking, 30000);
                // === Phase 3: 60s 再打一次 ===
                setTimeout(printRanking, 60000);
            }
        });

        // === 额外直接 hook 已知稳定类名（不依赖枚举） ===
        var STABLE_TARGETS = [
            'com.tencent.mm.ui.LauncherUI',
            'com.tencent.mm.ui.maintab.MainTabUI',
            'com.tencent.mm.ui.maintab.MainTabUnreadMgr',
            'com.tencent.mm.plugin.notification.badge.BadgeUtil',
            'com.tencent.mm.plugin.notification.badge.BadgeManager',
            'com.tencent.mm.plugin.notification.helper.NotificationHelper',
        ];
        for (var si = 0; si < STABLE_TARGETS.length; si++) {
            hookClass(STABLE_TARGETS[si]);
        }

        log('INIT', '=== hooks ready — 等待消息触发 ===');
    });
})();
