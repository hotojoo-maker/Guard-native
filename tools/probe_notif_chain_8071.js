// probe_notif_chain_8071.js — 8.0.71 通知链路：hook x.d + w.handleMessage + dump NotificationItem
// 用法：frida -U -p <微信主进程pid> -l tools/probe_notif_chain_8071.js

(function () {
    'use strict';

    var ts = function () {
        var d = new Date(), p = function (n) { return n < 10 ? '0' + n : '' + n; };
        return p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds());
    };

    function brief(obj) {
        if (obj === null || obj === undefined) return 'null';
        try {
            var s = String(obj);
            return s.length > 200 ? s.substring(0, 200) + '...' : s;
        } catch (e) { return '<err>'; }
    }

    Java.perform(function () {
        var pkg = 'com.tencent.mm.booter.notification';

        // ── 1. hook w.handleMessage ──
        try {
            var W = Java.use(pkg + '.w');
            W.handleMessage.implementation = function (msg) {
                console.log(ts() + ' [w.handleMessage] what=' + msg.what + ' arg1=' + msg.arg1 + ' obj=' + brief(msg.obj));
                return this.handleMessage(msg);
            };
            console.log(ts() + ' [OK] w.handleMessage hooked');
        } catch (e) {
            console.log(ts() + ' [FAIL] w.handleMessage: ' + e);
        }

        // ── 2. hook x.d ──
        try {
            var X = Java.use(pkg + '.x');
            // 枚举 x 的所有方法，hook 名为 d 的
            var methods = X.class.getDeclaredMethods();
            for (var i = 0; i < methods.length; i++) {
                var m = methods[i];
                var mName = m.getName();
                if (mName === 'd') {
                    var params = m.getParameterTypes();
                    console.log(ts() + ' [x.d] found: ' + mName + '(' + params.map(function(p){return p.getName();}).join(',') + ')');
                    // 根据参数个数做重载
                    if (params.length === 1) {
                        X.d.overload(params[0].getName()).implementation = function (a) {
                            console.log(ts() + ' [x.d(1)] arg=' + brief(a));
                            return this.d(a);
                        };
                    } else if (params.length === 2) {
                        X.d.overload(params[0].getName(), params[1].getName()).implementation = function (a, b) {
                            console.log(ts() + ' [x.d(2)] arg0=' + brief(a) + ' arg1=' + brief(b));
                            return this.d(a, b);
                        };
                    } else if (params.length === 3) {
                        X.d.overload(params[0].getName(), params[1].getName(), params[2].getName()).implementation = function (a, b, c) {
                            console.log(ts() + ' [x.d(3)] arg0=' + brief(a) + ' arg1=' + brief(b) + ' arg2=' + brief(c));
                            return this.d(a, b, c);
                        };
                    }
                }
            }
        } catch (e) {
            console.log(ts() + ' [FAIL] x.d: ' + e);
        }

        // ── 3. dump NotificationItem 字段 ──
        try {
            var NI = Java.use(pkg + '.NotificationItem');
            var fields = NI.class.getDeclaredFields();
            console.log(ts() + ' [NotificationItem] ' + fields.length + ' fields:');
            for (var i = 0; i < fields.length; i++) {
                var f = fields[i];
                console.log('    ' + f.getName() + ' : ' + f.getType().getName());
            }
            // 也列一下方法
            var niMethods = NI.class.getDeclaredMethods();
            var methodNames = [];
            for (var j = 0; j < niMethods.length; j++) {
                var nm = niMethods[j];
                var paramTypes = nm.getParameterTypes();
                methodNames.push(nm.getName() + '(' + paramTypes.map(function(p){return p.getName().substring(p.getName().lastIndexOf('.')+1);}).join(',') + ')');
            }
            console.log(ts() + ' [NotificationItem] ' + niMethods.length + ' methods (a-z):');
            methodNames.sort();
            for (var k = 0; k < methodNames.length; k++) {
                console.log('    ' + methodNames[k]);
            }
        } catch (e) {
            console.log(ts() + ' [FAIL] NotificationItem: ' + e);
        }

        // ── 4. 顺便 dump 传入 notify 的 Notification.extras 全部 key ──
        try {
            var NM = Java.use('android.app.NotificationManager');
            NM.notify.overload('java.lang.String', 'int', 'android.app.Notification')
                .implementation = function (tag, id, notif) {
                    var keys = [];
                    try {
                        var extras = notif.extras;
                        if (extras) {
                            var ks = extras.keySet();
                            var it = ks.iterator();
                            while (it.hasNext()) { keys.push(String(it.next())); }
                        }
                    } catch (e) {}
                    console.log(ts() + ' [NM.notify] id=' + id + ' extras keys=' + JSON.stringify(keys));
                    return this.notify(tag, id, notif);
                };
            console.log(ts() + ' [OK] NM.notify extras probe hooked');
        } catch (e) {
            console.log(ts() + ' [FAIL] NM.notify extras: ' + e);
        }

        console.log(ts() + ' === 探针就绪，切后台让密友发消息 ===');
    });
})();
