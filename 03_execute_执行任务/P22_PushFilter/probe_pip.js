/**
 * probe_pip.js — 找出微信VoIP来电时的悬浮窗到底是哪个View/Activity
 *
 * 用法（两个进程都要跑）：
 *   frida -U -p <main_pid>  -l probe_pip.js
 *   frida -U -p <push_pid>  -l probe_pip.js
 *
 * 操作：HIDDEN → 密友VoIP来电 → 前台→切后台触发悬浮窗 → 看 [FP:*] 输出
 *
 * 探测点：
 *   A. WindowManagerGlobal.addView — 所有进程，记录 View 类名 + flags
 *   B. Activity.onCreate — 主进程，看有没有 ILinkVoIPSmallView 之类
 */
'use strict';

Java.perform(function () {
    var TAG = "[FP]";

    // ── A. WindowManagerGlobal.addView — 抓所有 addView ──────────────────────
    try {
        var WMG = Java.use('android.view.WindowManagerGlobal');
        var addMethods = WMG.class.getDeclaredMethods();
        var hooked = 0;
        addMethods.forEach(function(m) {
            if (m.getName() !== 'addView') return;
            m.setAccessible(true);
            Java.use('android.view.WindowManagerGlobal')[m.getName()]
                .overloads.forEach(function(o) {
                hooked++;
                o.implementation = function() {
                    var viewCls = '?';
                    try { viewCls = arguments[0].getClass().getName(); } catch(e) {}
                    var lpFlags = 0;
                    var lpType = -1;
                    for (var i = 0; i < arguments.length; i++) {
                        try {
                            if (arguments[i] && arguments[i].flags !== undefined) {
                                lpFlags = arguments[i].flags;
                                lpType = arguments[i].type;
                                break;
                            }
                        } catch(e){}
                    }
                    // Log ALL views whose class name contains "voip" "call" "small" "pip" "link" "float" "overlay" (case-insensitive)
                    var lower = viewCls.toLowerCase();
                    var interesting = lower.indexOf('voip') >= 0 || lower.indexOf('call') >= 0
                        || lower.indexOf('small') >= 0 || lower.indexOf('pip') >= 0
                        || lower.indexOf('link') >= 0 || lower.indexOf('float') >= 0
                        || lower.indexOf('overlay') >= 0 || lower.indexOf('window') >= 0
                        || lower.indexOf('ilink') >= 0;
                    if (interesting) {
                        var hexFlags = (lpFlags >>> 0).toString(16);
                        console.log(TAG + " addView cls=" + viewCls
                            + " flags=0x" + hexFlags + " type=" + lpType);
                        // Print stack top 6
                        var e = Java.use('java.lang.Exception').$new();
                        var st = Java.use('android.util.Log').getStackTraceString(e);
                        var lines = st.split('\n');
                        for (var i = 0; i < Math.min(6, lines.length); i++) {
                            console.log(TAG + "   " + lines[i]);
                        }
                    }
                    return o.apply(this, arguments);
                };
            });
        });
        console.log(TAG + " WindowManagerGlobal.addView hooked (" + hooked + " overloads)");
    } catch(e) { console.log(TAG + " WMG fail: " + e); }

    // ── B. Activity.onCreate — 抓 VoIP 相关 Activity ─────────────────────────
    try {
        var Activity = Java.use('android.app.Activity');
        Activity.onCreate.overload('android.os.Bundle').implementation = function(bundle) {
            var cls = this.getClass().getName();
            var lower = cls.toLowerCase();
            if (lower.indexOf('voip') >= 0 || lower.indexOf('call') >= 0
                || lower.indexOf('small') >= 0 || lower.indexOf('pip') >= 0
                || lower.indexOf('link') >= 0 || lower.indexOf('float') >= 0
                || lower.indexOf('overlay') >= 0) {
                console.log(TAG + " Activity.onCreate cls=" + cls);
                var e = Java.use('java.lang.Exception').$new();
                var st = Java.use('android.util.Log').getStackTraceString(e);
                var lines = st.split('\n');
                for (var i = 0; i < Math.min(8, lines.length); i++) {
                    console.log(TAG + "   " + lines[i]);
                }
            }
            return this.onCreate(bundle);
        };
        console.log(TAG + " Activity.onCreate hooked");
    } catch(e) { console.log(TAG + " Activity.onCreate fail: " + e); }

    // ── C. onMultiWindowModeChanged — 分屏/多窗口进入 ────────────────────────
    try {
        var Activity = Java.use('android.app.Activity');
        Activity.onMultiWindowModeChanged.overload('boolean').implementation = function(isInMultiWindow) {
            if (isInMultiWindow) {
                console.log(TAG + " onMultiWindowModeChanged→entering multi-window cls="
                    + this.getClass().getName());
                var e = Java.use('java.lang.Exception').$new();
                var st = Java.use('android.util.Log').getStackTraceString(e);
                console.log(TAG + "   " + st.split('\n').slice(0,5).join('\n    '));
            }
            return this.onMultiWindowModeChanged(isInMultiWindow);
        };
        // Also hook the Configuration overload
        Activity.onMultiWindowModeChanged.overload('boolean', 'android.content.res.Configuration')
            .implementation = function(isInMultiWindow, cfg) {
            if (isInMultiWindow) {
                console.log(TAG + " onMultiWindowModeChanged(Config)→entering cls="
                    + this.getClass().getName());
            }
            return this.onMultiWindowModeChanged(isInMultiWindow, cfg);
        };
        console.log(TAG + " onMultiWindowModeChanged hooked");
    } catch(e) { console.log(TAG + " multiWindow fail: " + e); }

    // ── D. View.onAttachedToWindow — 抓 ILinkVoIPSmallView ───────────────────
    try {
        var View = Java.use('android.view.View');
        View.onAttachedToWindow.implementation = function() {
            var cls = this.getClass().getName();
            var lower = cls.toLowerCase();
            if (lower.indexOf('voip') >= 0 || lower.indexOf('ilink') >= 0
                || lower.indexOf('small') >= 0 || lower.indexOf('call') >= 0 && lower.indexOf('view') >= 0) {
                console.log(TAG + " View.onAttachedToWindow cls=" + cls);
                var e = Java.use('java.lang.Exception').$new();
                var st = Java.use('android.util.Log').getStackTraceString(e);
                var lines = st.split('\n');
                for (var i = 0; i < Math.min(6, lines.length); i++) {
                    console.log(TAG + "   " + lines[i]);
                }
            }
            return this.onAttachedToWindow();
        };
        console.log(TAG + " View.onAttachedToWindow hooked");
    } catch(e) { console.log(TAG + " View.onAttached fail: " + e); }

    console.log(TAG + " === ready. HIDDEN + VoIP call + switch to background ===");
});
