/**
 * probe_screen_wake.js — 找 VoIP 来电亮屏的真实路径
 *
 * 三个探测点：
 *   A. NM.notify — 所有进程（主进程 + :push），记录 channel + tag + id + 调用栈
 *   B. WakeLock.acquire — 所有进程，记录 flags + 调用栈
 *   C. WindowManager.addView — 主进程，记录 LayoutParams.flags（查 FLAG_TURN_SCREEN_ON）
 *
 * 用法（两个窗口分别跑，覆盖主进程和 push 进程）：
 *   frida -U -n com.tencent.mm          -l probe_screen_wake.js --no-pause  # 主进程
 *   frida -U -n com.tencent.mm:push     -l probe_screen_wake.js --no-pause  # push 进程
 *
 * 操作：HIDDEN 态 → 密友来电 → 看 [SW:*] 输出
 * 目标：找到第一条能点亮屏幕的调用是哪个进程、哪个 API、什么参数
 */
'use strict';

var PROCESS = Java.androidVersion ? 'android' : 'unknown';
try { PROCESS = java.lang.System.getProperty('ro.product.model') || 'proc'; } catch(e){}

Java.perform(function () {

    // ── A. NotificationManager.notify — 全进程 ─────────────────────────────
    (function hookNM() {
        try {
            var NM = Java.use('android.app.NotificationManager');
            NM.notify.overloads.forEach(function(o) {
                o.implementation = function() {
                    var n = arguments[arguments.length - 1]; // last arg = Notification
                    var ch = '';
                    try { ch = n.getChannelId(); } catch(e) {}
                    var tag = arguments.length >= 3 ? arguments[0] : '(no-tag)';
                    var id  = arguments.length >= 2 ? arguments[arguments.length - 2] : -1;
                    // Print short stack (3 frames) to find caller
                    var stack = '';
                    try {
                        var st = Java.use('java.lang.Thread').currentThread().getStackTrace();
                        var frames = [];
                        for (var i = 3; i < Math.min(st.length, 10); i++) {
                            var f = st[i].toString();
                            if (f.indexOf('android.app') < 0 && f.indexOf('java.lang') < 0)
                                frames.push(f);
                            if (frames.length >= 3) break;
                        }
                        stack = frames.join(' | ');
                    } catch(e) {}
                    console.log('[SW:NM] id=' + id + ' ch=' + ch + ' tag=' + tag
                        + '\n  stack: ' + stack);
                    return o.apply(this, arguments);
                };
            });
            console.log('[SW] NM.notify hooked');
        } catch (e) { console.log('[SW] NM fail: ' + e); }
    })();

    // ── B. PowerManager.WakeLock.acquire — 全进程 ──────────────────────────
    (function hookWL() {
        try {
            var WL = Java.use('android.os.PowerManager$WakeLock');
            // Get flags field
            WL.acquire.overloads.forEach(function(o) {
                o.implementation = function() {
                    var flags = -1;
                    var tag   = '?';
                    try {
                        var f = this.getClass().getDeclaredField('mFlags');
                        f.setAccessible(true);
                        flags = f.getInt(this);
                    } catch(e) {}
                    try {
                        var tf = this.getClass().getDeclaredField('mTag');
                        tf.setAccessible(true);
                        tag = tf.get(this) + '';
                    } catch(e) {}
                    var hex = (flags >>> 0).toString(16);
                    // Screen-related flags
                    var ACQUIRE_CAUSES_WAKEUP = 0x10000000;
                    var SCREEN_BRIGHT         = 0x0000000a;
                    var ON_AFTER_RELEASE      = 0x20000000;
                    var FULL_WAKE             = 0x0000001a;
                    var screenBit = flags & (ACQUIRE_CAUSES_WAKEUP | SCREEN_BRIGHT
                                             | ON_AFTER_RELEASE | FULL_WAKE);
                    var stack = '';
                    try {
                        var st = Java.use('java.lang.Thread').currentThread().getStackTrace();
                        var frames = [];
                        for (var i = 3; i < Math.min(st.length, 12); i++) {
                            var sf = st[i].toString();
                            if (sf.indexOf('reflect') >= 0) continue;
                            frames.push(sf);
                            if (frames.length >= 4) break;
                        }
                        stack = frames.join('\n    ');
                    } catch(e) {}
                    var marker = screenBit !== 0 ? ' ⚡SCREEN' : '';
                    console.log('[SW:WL' + marker + '] flags=0x' + hex + ' tag=' + tag
                        + '\n  stack:\n    ' + stack);
                    return o.apply(this, arguments);
                };
            });
            console.log('[SW] WakeLock.acquire hooked');
        } catch (e) { console.log('[SW] WakeLock fail: ' + e); }
    })();

    // ── C. WindowManager.addView — 查 FLAG_TURN_SCREEN_ON (0x200000) ───────
    (function hookWM() {
        try {
            // Hook WindowManagerGlobal.addView which all WM calls go through
            var WMG = Java.use('android.view.WindowManagerGlobal');
            var addMethods = WMG.class.getDeclaredMethods();
            addMethods.forEach(function(m) {
                if (m.getName() !== 'addView') return;
                m.setAccessible(true);
                Java.use('android.view.WindowManagerGlobal')[m.getName()]
                    .overloads.forEach(function(o) {
                    o.implementation = function() {
                        // Param containing LayoutParams varies by overload; scan all
                        var lpFlags = 0;
                        for (var i = 0; i < arguments.length; i++) {
                            try {
                                var arg = arguments[i];
                                if (arg && arg.flags !== undefined) {
                                    lpFlags = arg.flags;
                                    break;
                                }
                            } catch(e){}
                        }
                        var FLAG_TURN_SCREEN_ON  = 0x00200000;
                        var FLAG_KEEP_SCREEN_ON  = 0x00000080;
                        var viewCls = '?';
                        try { viewCls = arguments[0].getClass().getSimpleName(); } catch(e) {}
                        var screenBit = lpFlags & (FLAG_TURN_SCREEN_ON | FLAG_KEEP_SCREEN_ON);
                        if (screenBit !== 0 || viewCls.toLowerCase().indexOf('voip') >= 0) {
                            var hex = (lpFlags >>> 0).toString(16);
                            console.log('[SW:WM⚡] addView cls=' + viewCls
                                + ' lp.flags=0x' + hex
                                + (screenBit ? ' ←SCREEN_ON' : ''));
                        }
                        return o.apply(this, arguments);
                    };
                });
            });
            console.log('[SW] WindowManagerGlobal.addView hooked');
        } catch (e) { console.log('[SW] WMG fail (try alternate): ' + e);
            // Fallback: hook WindowManagerImpl
            try {
                var WMI = Java.use('android.view.WindowManagerImpl');
                WMI.addView.overloads.forEach(function(o) {
                    o.implementation = function(view, params) {
                        var lpFlags = 0;
                        try { lpFlags = params.flags; } catch(e) {}
                        var FLAG_TURN_SCREEN_ON = 0x00200000;
                        if ((lpFlags & FLAG_TURN_SCREEN_ON) !== 0) {
                            var cls = '?';
                            try { cls = view.getClass().getSimpleName(); } catch(e) {}
                            console.log('[SW:WMI⚡] addView cls=' + cls
                                + ' FLAG_TURN_SCREEN_ON set lp.flags=0x'
                                + (lpFlags >>> 0).toString(16));
                        }
                        return o.apply(this, arguments);
                    };
                });
                console.log('[SW] WindowManagerImpl.addView hooked (fallback)');
            } catch(e2) { console.log('[SW] WMI also fail: ' + e2); }
        }
    })();

    console.log('[SW] probe_screen_wake ready — trigger a hidden-friend VoIP call now');
    console.log('[SW] IMPORTANT: run in BOTH processes: com.tencent.mm AND com.tencent.mm:push');
});
