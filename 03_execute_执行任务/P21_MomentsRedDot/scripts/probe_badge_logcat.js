'use strict';
/*
 * P21 badge probe — LOGCAT variant (微信 8.0.71)
 * 与 probe_badge_20.js 同逻辑，但输出走 android.util.Log(tag=NCL [BPRB])，
 * 而不是 console.log —— 规避 frida CLI 在非交互 shell 下的 stdout 缓冲问题。
 * 找「发现」底部 tab 红点数字 + 「朋友圈」行红点数字 的真实渲染/控制点。
 *
 * 用法（warm-attach，保留 HIDDEN 态，靠 keep-alive stdin 不退出）：
 *   powershell -NoProfile -Command 'while($true){Start-Sleep -Seconds 30}' | \
 *     frida -D <devid> -p <主进程PID> -l probe_badge_logcat.js -q
 * 然后手机上 微信 tab <-> 发现 tab 来回切 2-3 次，再 adb logcat -d | Select-String "BPRB"
 */
Java.perform(function () {
    var TextView = Java.use('android.widget.TextView');
    var Exception = Java.use('java.lang.Exception');
    var Log = Java.use('android.util.Log');
    var seen = {};
    var TAG = 'NCL';

    function resName(view) {
        try {
            var id = view.getId();
            if (id === -1 || id === 0) return 'no-id';
            return view.getResources().getResourceEntryName(id);
        } catch (e) { return 'id?'; }
    }

    function tencentStack() {
        var full = Log.getStackTraceString(Exception.$new());
        var lines = full.split('\n');
        var out = [];
        for (var i = 0; i < lines.length && out.length < 12; i++) {
            if (lines[i].indexOf('com.tencent') >= 0) out.push(lines[i].trim());
        }
        return out.join(' || ');
    }

    var setTextCS = TextView.setText.overload('java.lang.CharSequence');
    setTextCS.implementation = function (cs) {
        try {
            var s = (cs !== null) ? cs.toString() : null;
            if (s !== null && /^[0-9]{1,3}$/.test(s)) {
                var cn = this.getClass().getName();
                var rid = resName(this);
                var key = cn + '|' + rid;
                if (!seen[key]) {
                    seen[key] = true;
                    Log.i(TAG, '[BPRB] cls=' + cn + ' id=' + rid + ' text=' + s);
                    Log.i(TAG, '[BPRB] stack: ' + tencentStack());
                }
            }
        } catch (e) {}
        return this.setText(cs);
    };

    Log.i(TAG, '[BPRB] ready');
});
