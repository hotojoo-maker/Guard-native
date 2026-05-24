/**
 * probe_push_notif_8071.js — 微信 8.0.71 :push 进程通知拦截点探针
 *
 * 目标：找 Catfish 8.0.70 replaceNotification(android.os.Message) 的 8.0.71 等价方法
 *
 * 用法（attach 到 :push 进程）：
 *   $pid = adb shell "ps -ef | grep 'com.tencent.mm:push'" | Select-String ':push' | ForEach-Object { ($_ -split '\s+')[1] }
 *   frida -U -p $pid -l tools\probe_push_notif_8071.js 2>&1 | tee tools\probe_push_notif_$(Get-Date -f 'HHmmss').log
 *
 * 操作顺序：
 *   1. 按 Home 切后台（让微信 :push 活跃）
 *   2. 普通好友发一条消息 → 看 "NOTIF_HIT" 哪个方法命中
 *   3. 密友 wxid_xxx 发一条消息 → 对比输出
 *
 * 输出含义：
 *   [NOTIF_HIT] 命中含 Message / NotificationManager.notify 的方法 + 调用栈
 *   [CAND_MSG]  参数含 android.os.Message 的方法（P1 目标：replaceNotification 等价）
 *   [CAND_NMN]  调用 NotificationManager.notify 的方法
 *   [TALKER]    从命中方法里提取到的 talker/fromUser/username
 */
(function () {
    'use strict';

    var TAG = 'NOTIF_P1';
    var hitCount = {};

    function ts() {
        var d = new Date();
        var p = function (n) { return n < 10 ? '0' + n : '' + n; };
        return p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds());
    }
    function log(tag, msg) { console.log(ts() + ' [' + TAG + ':' + tag + '] ' + msg); }

    function stack(n) {
        try {
            var frames = Java.use('java.lang.Thread').currentThread().getStackTrace();
            var out = [];
            for (var i = 0; i < frames.length && out.length < (n || 6); i++) {
                var s = String(frames[i]);
                if (s.indexOf('java.lang.reflect') >= 0) continue;
                if (s.indexOf('dalvik.') >= 0) continue;
                out.push(s.trim());
            }
            return out.join(' ← ');
        } catch (e) { return '?'; }
    }

    // 从 Message/Bundle/任意 obj 暴力提取 talker/fromUser 字段
    function extractTalker(obj) {
        if (obj === null || obj === undefined) return null;
        try {
            var cls = obj.getClass();
            var Bundle = Java.use('android.os.Bundle');
            var Message = Java.use('android.os.Message');

            // 如果是 Message，先看 getData()
            if (obj instanceof Message.class) {
                var bundle = obj.getData();
                if (bundle !== null) {
                    var t = bundle.getString('talker') || bundle.getString('fromUser') ||
                            bundle.getString('username') || bundle.getString('wxid');
                    if (t && t.length > 4) return 'bundle:' + t;
                }
            }
            // 反射扫字段
            while (cls && !cls.equals(Java.use('java.lang.Object').class)) {
                var fields = cls.getDeclaredFields();
                for (var i = 0; i < fields.length; i++) {
                    var f = fields[i];
                    f.setAccessible(true);
                    try {
                        var v = f.get(obj);
                        if (typeof v === 'string' || (v !== null && v instanceof Java.use('java.lang.String').class)) {
                            var s = String(v);
                            if ((s.indexOf('wxid_') === 0 || s.indexOf('gh_') === 0) && s.length < 40)
                                return f.getName() + '=' + s;
                        }
                    } catch (e2) {}
                }
                cls = cls.getSuperclass();
            }
        } catch (e) {}
        return null;
    }

    Java.perform(function () {
        log('INIT', '=== probe_push_notif_8071.js loaded ===');
        log('INIT', '目标：找 replaceNotification(Message) 8.0.71 等价 + talker 提取路径');

        // ─── 策略 1：枚举 notification 包所有类，找含 Message 参数的方法 ─────────────────
        var notifCandidates = [];
        Java.enumerateLoadedClasses({
            onMatch: function (name) {
                if (name.indexOf('com.tencent.mm') < 0) return;
                if (name.indexOf('notification') < 0 && name.indexOf('Notification') < 0) return;
                notifCandidates.push(name);
            },
            onComplete: function () {
                log('SCAN', '通知相关类: ' + notifCandidates.length + ' 个');
                notifCandidates.forEach(function (cn) {
                    log('CAND_CLS', cn);
                    hookNotifClass(cn);
                });
                log('SCAN', '类扫描完成，等待推送触发...');
                setTimeout(printSummary, 60000);
            }
        });

        // ─── 策略 2：直接 hook booter.notification 三个已知候选 ──────────────────────────
        var BOOTER_TARGETS = [
            'com.tencent.mm.booter.notification.e',
            'com.tencent.mm.booter.notification.x',
            'com.tencent.mm.booter.notification.y',
        ];
        BOOTER_TARGETS.forEach(function (cn) {
            hookNotifClass(cn);
        });

        // ─── 策略 3：Hook NotificationManager.notify 追调用者 ──────────────────────────
        try {
            var NM = Java.use('android.app.NotificationManager');
            // notify(int, Notification)
            NM.notify.overload('int', 'android.app.Notification').implementation = function (id, notif) {
                log('NMN', 'NotificationManager.notify(id=' + id + ')');
                log('NMN', 'STK: ' + stack(8));
                return this.notify(id, notif);
            };
            // notify(String, int, Notification)
            try {
                NM.notify.overload('java.lang.String', 'int', 'android.app.Notification').implementation = function (tag, id, notif) {
                    log('NMN', 'NotificationManager.notify(tag=' + tag + ' id=' + id + ')');
                    log('NMN', 'STK: ' + stack(8));
                    return this.notify(tag, id, notif);
                };
            } catch (e2) {}
            log('INIT', 'NotificationManager.notify hooked');
        } catch (e) {
            log('ERR', 'NM.notify hook fail: ' + e);
        }

        log('INIT', '=== hooks ready — 切后台，让好友发消息 ===');
    });

    var hookedClasses = {};

    function hookNotifClass(cn) {
        if (hookedClasses[cn]) return;
        hookedClasses[cn] = true;
        try {
            var cls = Java.use(cn);
            var methods = cls.class.getDeclaredMethods();
            var MessageCls = null;
            try { MessageCls = Java.use('android.os.Message').class; } catch (e) {}

            for (var i = 0; i < methods.length; i++) {
                var m = methods[i];
                var ptypes = m.getParameterTypes();
                var mname = m.getName();
                var hasMessage = false;
                var pnames = [];
                for (var j = 0; j < ptypes.length; j++) {
                    pnames.push(ptypes[j].getName());
                    if (MessageCls && ptypes[j].equals(MessageCls)) hasMessage = true;
                }

                // 只 hook：含 Message 参数 / void 返回 / 1-2 参数 的方法
                var interesting = hasMessage ||
                    (m.getReturnType().getName() === 'void' && ptypes.length >= 1 && ptypes.length <= 3);
                if (!interesting) continue;

                (function (methodName, paramTypeNames, clsName, hasMsg) {
                    try {
                        var fridaMethod = cls[methodName];
                        if (!fridaMethod || typeof fridaMethod.overload !== 'function') return;
                        var overload = fridaMethod.overload.apply(fridaMethod, paramTypeNames);
                        overload.implementation = function () {
                            var key = clsName.split('.').pop() + '.' + methodName +
                                '(' + paramTypeNames.join(',') + ')';
                            hitCount[key] = (hitCount[key] || 0) + 1;
                            var cnt = hitCount[key];
                            var args = Array.prototype.slice.call(arguments);

                            // 尝试提取 wxid talker
                            var talker = null;
                            for (var k = 0; k < args.length && !talker; k++) {
                                if (args[k] !== null) talker = extractTalker(args[k]);
                            }

                            if (cnt <= 3 || (talker && talker.indexOf('wxid_') >= 0)) {
                                if (hasMsg) {
                                    log('CAND_MSG', key + ' hit#' + cnt);
                                } else {
                                    log('NOTIF_HIT', key + ' hit#' + cnt);
                                }
                                if (talker) log('TALKER', key + ' talker=' + talker);
                                if (cnt <= 2) log('STK', stack(7));
                            }
                            return this[methodName].apply(this, args);
                        };
                    } catch (e2) { /* overload not found */ }
                })(mname, pnames, cn, hasMessage);
            }
        } catch (e) {
            // 类不存在或加载失败，静默跳过
        }
    }

    function printSummary() {
        log('SUMMARY', '===== 60s 命中排名 =====');
        var keys = Object.keys(hitCount);
        keys.sort(function (a, b) { return hitCount[b] - hitCount[a]; });
        keys.slice(0, 20).forEach(function (k, idx) {
            log('SUMMARY', '[' + (idx + 1) + '] ' + k + ' hits=' + hitCount[k]);
        });
        log('SUMMARY', '===== CAND_MSG = Message参数方法（P1目标）; TALKER 有 wxid = 命中 =====');
    }
})();
