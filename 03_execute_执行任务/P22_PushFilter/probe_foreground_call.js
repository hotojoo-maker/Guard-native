// probe_foreground_call.js — 前台来电路径爬虫 v1
// ======================================================
// 目标：WeChat 8.0.71 前台来电时，追踪 UI + 音频触发路径
// 运行：frida -U -n com.tencent.mm -l probe_foreground_call.js
// 操作：保持微信在前台，让密友/任意联系人打来语音或视频电话
//       接听/拒绝均可，关键是来电弹出那一瞬间
// 输出：搜 [FCP:*] tag，每类命中都有独立 tag
// ======================================================

"use strict";

Java.perform(function () {
    var TAG = "[FCP]";

    // ------------------------------------------------------------------
    // 1. Dialog.show — 捕获来电弹窗类名 (如果走 AlertDialog / 自定义 Dialog)
    // ------------------------------------------------------------------
    try {
        var Dialog = Java.use("android.app.Dialog");
        Dialog.show.implementation = function () {
            var name = this.getClass().getName();
            // 只打印 com.tencent.mm 内的
            if (name.indexOf("com.tencent.mm") !== -1 ||
                name.toLowerCase().indexOf("voip") !== -1 ||
                name.toLowerCase().indexOf("call") !== -1 ||
                name.toLowerCase().indexOf("video") !== -1) {
                console.log(TAG + "[Dialog.show] cls=" + name);
            }
            return this.show.apply(this, arguments);
        };
        console.log(TAG + " Dialog.show hooked");
    } catch (e) {
        console.log(TAG + " Dialog.show hook fail: " + e);
    }

    // ------------------------------------------------------------------
    // 2. WindowManagerGlobal.addView — 捕获 overlay/悬浮窗类名 + window type
    //    TYPE_APPLICATION=2, TYPE_APPLICATION_OVERLAY=2038
    // ------------------------------------------------------------------
    try {
        var WMG = Java.use("android.view.WindowManagerGlobal");
        var addViewMethod = WMG.addView.overload(
            "android.view.View",
            "android.view.ViewGroup$LayoutParams",
            "android.view.Display",
            "android.view.IWindowSession"
        );
        if (addViewMethod) {
            addViewMethod.implementation = function (view, params, display, session) {
                var viewCls = view.getClass().getName();
                var type = -1;
                try { type = Java.use("android.view.WindowManager$LayoutParams").cast(params).type.value; } catch (e2) {}
                if (viewCls.indexOf("com.tencent.mm") !== -1 || type > 2000) {
                    console.log(TAG + "[WM.addView] cls=" + viewCls + " type=" + type);
                }
                return this.addView(view, params, display, session);
            };
            console.log(TAG + " WMG.addView hooked (4-arg)");
        }
    } catch (e) {
        // 尝试 5 参数版本 (API 29+)
        try {
            var WMG2 = Java.use("android.view.WindowManagerGlobal");
            WMG2.addView.overload(
                "android.view.View",
                "android.view.ViewGroup$LayoutParams",
                "android.view.Display",
                "android.view.IWindowSession",
                "android.view.IWindowSession"
            ).implementation = function (view, params, display, session, session2) {
                var viewCls = view.getClass().getName();
                var type = -1;
                try { type = Java.use("android.view.WindowManager$LayoutParams").cast(params).type.value; } catch (e2) {}
                if (viewCls.indexOf("com.tencent.mm") !== -1 || type > 2000) {
                    console.log(TAG + "[WM.addView] cls=" + viewCls + " type=" + type);
                }
                return this.addView(view, params, display, session, session2);
            };
            console.log(TAG + " WMG.addView hooked (5-arg)");
        } catch (e2) {
            console.log(TAG + " WMG.addView hook fail: " + e + " / " + e2);
        }
    }

    // ------------------------------------------------------------------
    // 3. AudioManager.requestAudioFocus — 捕获谁在请求音频焦点 (铃声触发点)
    //    AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE=3 = 独占焦点 (电话铃声)
    // ------------------------------------------------------------------
    try {
        var AM = Java.use("android.media.AudioManager");
        // API 26+ OnAudioFocusChangeListener 版本
        AM.requestAudioFocus.overload("android.media.AudioFocusRequest").implementation = function (req) {
            var stack = Java.use("android.util.Log").getStackTraceString(
                Java.use("java.lang.Exception").$new("focus stack"));
            console.log(TAG + "[AudioFocus] requestAudioFocus(FocusRequest)\n  " + stack.split("\n").slice(1, 6).join("\n  "));
            return this.requestAudioFocus(req);
        };
        console.log(TAG + " AudioManager.requestAudioFocus hooked");
    } catch (e) {
        // 老版本 API
        try {
            var AM2 = Java.use("android.media.AudioManager");
            AM2.requestAudioFocus.overload(
                "android.media.AudioManager$OnAudioFocusChangeListener",
                "int", "int"
            ).implementation = function (listener, streamType, durationHint) {
                var stack = Java.use("android.util.Log").getStackTraceString(
                    Java.use("java.lang.Exception").$new("focus stack"));
                console.log(TAG + "[AudioFocus] stream=" + streamType + " hint=" + durationHint +
                    "\n  " + stack.split("\n").slice(1, 6).join("\n  "));
                return this.requestAudioFocus(listener, streamType, durationHint);
            };
            console.log(TAG + " AudioManager.requestAudioFocus (old) hooked");
        } catch (e2) {
            console.log(TAG + " AudioManager.requestAudioFocus hook fail: " + e + " / " + e2);
        }
    }

    // ------------------------------------------------------------------
    // 4. Ringtone.play — 直接铃声播放
    // ------------------------------------------------------------------
    try {
        var Ringtone = Java.use("android.media.Ringtone");
        Ringtone.play.implementation = function () {
            var stack = Java.use("android.util.Log").getStackTraceString(
                Java.use("java.lang.Exception").$new("ringtone stack"));
            console.log(TAG + "[Ringtone.play]\n  " + stack.split("\n").slice(1, 6).join("\n  "));
            return this.play.apply(this, arguments);
        };
        console.log(TAG + " Ringtone.play hooked");
    } catch (e) {
        console.log(TAG + " Ringtone.play hook fail: " + e);
    }

    // ------------------------------------------------------------------
    // 5. Vibrator.vibrate — 震动触发点
    // ------------------------------------------------------------------
    try {
        var Vibrator = Java.use("android.os.Vibrator");
        var vibMethods = Vibrator.vibrate.overloads;
        vibMethods.forEach(function (m) {
            m.implementation = function () {
                var stack = Java.use("android.util.Log").getStackTraceString(
                    Java.use("java.lang.Exception").$new("vib stack"));
                console.log(TAG + "[Vibrator.vibrate] args=" + JSON.stringify(Array.from(arguments)) +
                    "\n  " + stack.split("\n").slice(1, 6).join("\n  "));
                return m.apply(this, arguments);
            };
        });
        console.log(TAG + " Vibrator.vibrate hooked (" + vibMethods.length + " overloads)");
    } catch (e) {
        console.log(TAG + " Vibrator.vibrate hook fail: " + e);
    }

    // ------------------------------------------------------------------
    // 6. Fragment.onResume — 捕获来电 Fragment 类名 (如果走 FragmentTransaction)
    // ------------------------------------------------------------------
    try {
        var Fragment = Java.use("androidx.fragment.app.Fragment");
        Fragment.onResume.implementation = function () {
            var name = this.getClass().getName();
            if (name.indexOf("com.tencent.mm") !== -1) {
                var lower = name.toLowerCase();
                if (lower.indexOf("voip") !== -1 || lower.indexOf("call") !== -1 ||
                    lower.indexOf("video") !== -1 || lower.indexOf("phone") !== -1 ||
                    lower.indexOf("chat") !== -1) {
                    console.log(TAG + "[Fragment.onResume] cls=" + name);
                }
            }
            return this.onResume.apply(this, arguments);
        };
        console.log(TAG + " Fragment.onResume hooked");
    } catch (e) {
        console.log(TAG + " Fragment.onResume hook fail (try android.app.Fragment): " + e);
        try {
            var Frag2 = Java.use("android.app.Fragment");
            Frag2.onResume.implementation = function () {
                var name = this.getClass().getName();
                if (name.indexOf("com.tencent.mm") !== -1) {
                    console.log(TAG + "[android.Fragment.onResume] cls=" + name);
                }
                return this.onResume.apply(this, arguments);
            };
            console.log(TAG + " android.app.Fragment.onResume hooked");
        } catch (e2) {
            console.log(TAG + " android.app.Fragment.onResume hook fail: " + e2);
        }
    }

    // ------------------------------------------------------------------
    // 7. View.onAttachedToWindow 宽网 — 打印所有 com.tencent.mm 前缀、类名含 voip/call/video 的 view
    //    (比较吵但能找到叶子 view 类名)
    // ------------------------------------------------------------------
    try {
        var View = Java.use("android.view.View");
        View.onAttachedToWindow.implementation = function () {
            var name = this.getClass().getName();
            var lower = name.toLowerCase();
            if (name.indexOf("com.tencent.mm") !== -1 &&
                (lower.indexOf("voip") !== -1 || lower.indexOf("call") !== -1 ||
                 lower.indexOf("video") !== -1 || lower.indexOf("phone") !== -1)) {
                console.log(TAG + "[View.attached] cls=" + name);
            }
            return this.onAttachedToWindow.apply(this, arguments);
        };
        console.log(TAG + " View.onAttachedToWindow hooked (filtered)");
    } catch (e) {
        console.log(TAG + " View.onAttachedToWindow hook fail: " + e);
    }

    console.log(TAG + " === All probes armed. Let a contact call you now. ===");
});
