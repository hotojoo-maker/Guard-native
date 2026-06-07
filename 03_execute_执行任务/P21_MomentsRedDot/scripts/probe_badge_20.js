'use strict';
/*
 * P21 badge "20" discovery probe (微信 8.0.71)
 * 目的：找出「发现」底部 tab 红点数字 + 「朋友圈」行红点数字 的真实渲染/控制点。
 *
 * 背景（L1 实证）：现有 P21 全部 hook 都装上了，但冷启动+密友点赞后：
 *   - TabRedDotChangeEvent / WeChatTabRedDotEvent 构造器 0 命中
 *   - FMF (FindMoreFriendsUI) 实例 = null，重试 15 次放弃
 *   - w1 (SnsCommentStorage) v2/w2 0 命中
 *   - FinderRedDotTextView setRowCount/onFinishInflate/l 0 命中
 * → 这两个 "20" 走的是另一条没被覆盖的路径，本探针负责找到它。
 *
 * 用法（warm-attach，微信已在跑，不重启、保留 HIDDEN 态）：
 *   frida -U -p <主进程PID> -l probe_badge_20.js
 * attach 成功后在手机上：微信 tab <-> 发现 tab 来回切 2-3 次，触发角标重绘。
 *
 * 输出：每个"显示 1-3 位纯数字"的 TextView（按 类名+资源id 去重）打印一次
 *   class / 资源id / 文本，并附 com.tencent 调用栈 → 调用栈顶就是真正设角标的方法。
 */
Java.perform(function () {
    var TextView = Java.use('android.widget.TextView');
    var Exception = Java.use('java.lang.Exception');
    var Log = Java.use('android.util.Log');
    var seen = {};

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
        for (var i = 0; i < lines.length && out.length < 16; i++) {
            if (lines[i].indexOf('com.tencent') >= 0) out.push(lines[i].trim());
        }
        return out.join('\n      ');
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
                    console.log('\n[BADGE] cls=' + cn + ' id=' + rid + ' text=' + s);
                    console.log('      ' + tencentStack());
                }
            }
        } catch (e) {}
        return this.setText(cs);
    };

    console.log('[probe_badge_20] ready -- 现在请在手机上 微信tab <-> 发现tab 来回切 2-3 次');
});
