/**
 * probe_push_notif_v2.js — 精简版，直接针对 booter.notification 三个已知类
 * + NotificationManager.notify 调用栈
 *
 * 用法：attach :push 进程
 *   frida -U -p <push_pid> -l tools\probe_push_notif_v2.js
 * 操作：按 Home → 让好友发一条消息
 */
(function () {
    'use strict';

    var TAG = 'PNV2';
    function ts() {
        var d = new Date();
        var p = function (n) { return n < 10 ? '0' + n : '' + n; };
        return p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds());
    }
    function log(sub, msg) { console.log(ts() + ' [' + TAG + ':' + sub + '] ' + msg); }

    function stackTop(n) {
        try {
            var frames = Java.use('java.lang.Thread').currentThread().getStackTrace();
            var out = [];
            for (var i = 0; i < frames.length && out.length < (n || 6); i++) {
                var s = String(frames[i]);
                if (s.indexOf('java.lang.reflect') >= 0) continue;
                if (s.indexOf('dalvik.') >= 0) continue;
                if (s.indexOf('com.android.internal') >= 0) continue;
                out.push(s.trim());
            }
            return out.join(' ← ');
        } catch (e) { return '?'; }
    }

    // 用反射从 Message 或任意 obj 里找 talker 字符串
    function findTalker(obj) {
        if (obj === null || obj === undefined) return null;
        try {
            // 如果是 Bundle
            var bun = null;
            try {
                var msgCls = Java.use('android.os.Message');
                if (obj instanceof msgCls.class) {
                    bun = obj.getData();
                }
            } catch(e) {}
            if (bun) {
                var keys = ['talker','fromUser','username','from','wxid'];
                for (var ki = 0; ki < keys.length; ki++) {
                    var v = bun.getString(keys[ki]);
                    if (v && v.length > 4) return keys[ki] + '=' + v;
                }
            }
            // 反射扫字段（最多 2 层）
            var cls = obj.getClass();
            for (var depth = 0; depth < 2 && cls && !cls.getName().equals('java.lang.Object'); depth++) {
                var fields = cls.getDeclaredFields();
                for (var fi = 0; fi < fields.length; fi++) {
                    try {
                        fields[fi].setAccessible(true);
                        var fv = fields[fi].get(obj);
                        if (fv === null) continue;
                        var fvs = String(fv);
                        if ((fvs.indexOf('wxid_') === 0 || fvs.indexOf('gh_') === 0) && fvs.length < 40)
                            return fields[fi].getName() + '=' + fvs;
                    } catch(e2) {}
                }
                cls = cls.getSuperclass();
            }
        } catch(e) {}
        return null;
    }

    Java.perform(function () {
        log('INIT', '=== probe_push_notif_v2.js ===');

        // ──────────────────────────────────────────────────────────────────
        // 1. 枚举 :push 进程加载的 booter.notification 类
        // ──────────────────────────────────────────────────────────────────
        var booterCandidates = [];
        Java.enumerateLoadedClasses({
            onMatch: function (name) {
                if (name.indexOf('booter.notification') >= 0 ||
                    name.indexOf('booter') >= 0 && name.indexOf('notif') >= 0) {
                    booterCandidates.push(name);
                }
            },
            onComplete: function () {
                log('SCAN', 'booter.notification 候选类: ' + booterCandidates.length);
                booterCandidates.forEach(function (cn) { log('CAND', cn); });
                // hook 每一个找到的类
                booterCandidates.forEach(function (cn) { hookAllMethods(cn); });
                log('SCAN', '=== hooks 安装完毕，请按 Home 然后让好友发消息 ===');
            }
        });

        // ──────────────────────────────────────────────────────────────────
        // 2. 用全类名枚举方式安装 hook（避免 cls[name] 失败）
        // ──────────────────────────────────────────────────────────────────
        function hookAllMethods(cn) {
            try {
                var clsJava = Java.use('java.lang.Class').forName(cn);
                var methods = clsJava.getDeclaredMethods();
                log('HOOK', cn.split('.').pop() + ' ' + methods.length + ' 个方法');

                for (var i = 0; i < methods.length; i++) {
                    (function(method) {
                        try {
                            method.setAccessible(true);
                            method.implementation = function() {
                                var args = Array.prototype.slice.call(arguments);
                                var mn = method.getName();
                                var ptypes = method.getParameterTypes();
                                var pnames = [];
                                for (var j = 0; j < ptypes.length; j++) pnames.push(ptypes[j].getName().split('.').pop());
                                var key = cn.split('.').pop() + '.' + mn + '(' + pnames.join(',') + ')';

                                // 找 talker
                                var talker = null;
                                for (var k = 0; k < args.length && !talker; k++) {
                                    if (args[k]) talker = findTalker(args[k]);
                                }

                                log('HIT', key + (talker ? ' TALKER=' + talker : ''));
                                log('STK', stackTop(5));

                                return method.invoke(this, args);
                            };
                        } catch(e2) {
                            // log('WARN', cn.split('.').pop() + '.' + method.getName() + ' hook fail: ' + e2);
                        }
                    })(methods[i]);
                }
            } catch(e) {
                log('ERR', cn + ': ' + e);
            }
        }

        // ──────────────────────────────────────────────────────────────────
        // 3. NotificationManager.notify 调用栈（追调用者）
        // ──────────────────────────────────────────────────────────────────
        try {
            var NM = Java.use('android.app.NotificationManager');
            NM.notify.overload('int', 'android.app.Notification').implementation = function (id, notif) {
                log('NMN', 'notify(id=' + id + ')');
                log('NMN_STK', stackTop(8));
                return this.notify(id, notif);
            };
            try {
                NM.notify.overload('java.lang.String', 'int', 'android.app.Notification').implementation = function (tag, id, notif) {
                    log('NMN', 'notify(tag=' + tag + ' id=' + id + ')');
                    log('NMN_STK', stackTop(8));
                    return this.notify(tag, id, notif);
                };
            } catch(e) {}
            log('INIT', 'NotificationManager.notify hooked');
        } catch(e) {
            log('ERR', 'NM.notify: ' + e);
        }

        // keepalive
        setInterval(function () {
            log('ALIVE', 'waiting for push...');
        }, 15000);

        log('INIT', '=== 等待推送，请按 Home 后让好友发消息 ===');
    });
})();
