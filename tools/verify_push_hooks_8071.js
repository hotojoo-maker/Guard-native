// verify_push_hooks_8071.js — 验证 8.0.71 push 拦截三点
//
// 静态分析结论（jadx 全量 8.0.71）：
//   L1  com.tencent.mm.booter.notification.x.a(f9)   push 总闸，f9.O0()=talker
//   L4b com.tencent.mm.ui.MainTabUI.i()               tab 未读数展示
//   L4c com.tencent.mm.booter.notification.h0.d(int)  OEM 角标分发
//
// 用法：
//   # 主进程（L4b）
//   frida -U -p <main_pid> -l verify_push_hooks_8071.js
//   # push 进程（L1 + L4c）
//   frida -U -p <push_pid> -l verify_push_hooks_8071.js
//
// 操作：密友 wxid_ahvd1wejo02f22 发一条消息，观察哪些层命中

(function () {
    'use strict';

    var ts = function () {
        var d = new Date(), p = function (n) { return n < 10 ? '0' + n : '' + n; };
        return p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds());
    };

    Java.perform(function () {
        var proc = '?';
        try { proc = String(Java.use('android.app.ActivityThread').currentApplication().getApplicationInfo().processName); } catch (e) {}
        var isPush = proc.indexOf(':push') >= 0;
        console.log(ts() + ' [VERIFY] 进程=' + proc);

        // ── L1: com.tencent.mm.booter.notification.x.a(f9) ─────────────
        try {
            var X = Java.use('com.tencent.mm.booter.notification.x');
            X.a.overload('com.tencent.mm.storage.f9').implementation = function (f9) {
                var talker = '?';
                try { talker = String(f9.O0()); } catch (e) {}
                console.log(ts() + ' [L1:x.a] ✅ FIRED talker=' + talker + ' — push 总闸');
                return this.a(f9);
            };
            console.log(ts() + ' [HOOK] L1 x.a(f9) OK');
        } catch (e) {
            console.log(ts() + ' [HOOK] L1 x.a(f9) FAIL: ' + e);
        }

        // ── L4b: com.tencent.mm.ui.MainTabUI.i() ───────────────────────
        try {
            var MainTabUI = Java.use('com.tencent.mm.ui.MainTabUI');
            MainTabUI.i.overload().implementation = function () {
                var real = this.i();
                console.log(ts() + ' [L4b:MainTabUI.i] ✅ FIRED real=' + real + ' — tab 未读数');
                return real;
            };
            console.log(ts() + ' [HOOK] L4b MainTabUI.i() OK');
        } catch (e) {
            console.log(ts() + ' [HOOK] L4b MainTabUI.i() FAIL: ' + e);
        }

        // ── L4c: com.tencent.mm.booter.notification.h0.d(int) ──────────
        try {
            var H0 = Java.use('com.tencent.mm.booter.notification.h0');
            H0.d.overload('int').implementation = function (count) {
                console.log(ts() + ' [L4c:h0.d] ✅ FIRED count=' + count + ' — OEM 角标分发');
                return this.d(count);
            };
            console.log(ts() + ' [HOOK] L4c h0.d(int) OK');
        } catch (e) {
            console.log(ts() + ' [HOOK] L4c h0.d(int) FAIL: ' + e);
        }

        console.log(ts() + ' [VERIFY] === 请让密友 wxid_ahvd1wejo02f22 发消息 ===');
    });

    setInterval(function () {
        console.log(ts() + ' [KEEPALIVE]');
    }, 10000);
})();
