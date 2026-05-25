/**
 * probe_voip_classnames.js
 * 目标：在来电时捞出真实 VoIP View 类名 + Vibrator.vibrate 来源
 * 用法：
 *   frida -U -n com.tencent.mm -l probe_voip_classnames.js --no-pause
 * 手机端：打开微信 → 让密友拨打来电 → 看输出
 *
 * 输出解读：
 *   [VCV:attach] cls=XXX  ← 来电时出现的 View 类名（含 voip/call/video 关键字的就是目标）
 *   [VCV:vib]    cls=XXX  ← 谁在 vibrate（WeChat 自己震动的来源）
 */

'use strict';

var View = Java.use('android.view.View');
var Vibrator = Java.use('android.os.Vibrator');

// --- 1. onAttachedToWindow 全拦截，过滤含 voip/call/video/ring 的类名 ---
var voipKeywords = ['voip', 'call', 'video', 'ring', 'VoIP', 'Call', 'Video', 'Ring',
                    'incoming', 'Incoming', 'audio', 'Audio'];

View.onAttachedToWindow.implementation = function () {
    var cls = this.getClass().getName();
    var lower = cls.toLowerCase();
    var match = voipKeywords.some(function(k) { return lower.indexOf(k.toLowerCase()) >= 0; });
    if (match) {
        console.log('[VCV:attach] cls=' + cls);
    }
    return this.onAttachedToWindow();
};

// --- 2. Vibrator.vibrate 全拦截，打调用栈（找 WeChat 自己的震动来源）---
var vibrateOverloads = Vibrator.vibrate.overloads;
vibrateOverloads.forEach(function(overload) {
    overload.implementation = function() {
        var stack = Java.use('android.util.Log').getStackTraceString(
            Java.use('java.lang.Exception').$new('vib-trace')
        );
        // 只打微信内部的震动，过滤系统调用
        if (stack.indexOf('com.tencent.mm') >= 0) {
            console.log('[VCV:vib] sig=' + overload.argumentTypes.map(function(t){return t.className;}).join(','));
            // 打前5行栈（关键帧）
            var lines = stack.split('\n').slice(1, 6);
            lines.forEach(function(l) { console.log('  ' + l.trim()); });
        }
        return this[overload.methodName].apply(this, arguments);
    };
});

// --- 3. 全量 View attach 日志（前30秒采样，关键字匹配不到时用这个）---
//  注释掉的版本，按需打开（输出量大）
/*
View.onAttachedToWindow.implementation = function () {
    var cls = this.getClass().getName();
    if (cls.startsWith('com.tencent.mm')) {
        console.log('[VCV:all] cls=' + cls);
    }
    return this.onAttachedToWindow();
};
*/

console.log('[VCV] probe_voip_classnames loaded — make a call now');
