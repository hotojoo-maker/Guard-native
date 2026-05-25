/**
 * probe_voip_sound.js
 * 目标：找出 notification.x.d() 用什么 API 播放铃声
 * 用法：frida -U -n com.tencent.mm -l probe_voip_sound.js --no-pause
 * 操作：加载后让密友打来电 → 看标签 [VCS:*]
 */

'use strict';

// --- MediaPlayer ---
var MP = Java.use('android.media.MediaPlayer');
['start', 'prepare', 'prepareAsync', 'setDataSource'].forEach(function(m) {
    try {
        var overloads = MP[m].overloads;
        overloads.forEach(function(o) {
            o.implementation = function() {
                var stack = getShortStack();
                if (stack.indexOf('com.tencent.mm') >= 0) {
                    console.log('[VCS:MP.' + m + '] args=' + JSON.stringify(Array.from(arguments).map(String)) + '\n  stack: ' + stack);
                }
                return o.apply(this, arguments);
            };
        });
    } catch(e) {}
});

// --- AudioTrack ---
try {
    var AT = Java.use('android.media.AudioTrack');
    AT.play.implementation = function() {
        var stack = getShortStack();
        if (stack.indexOf('com.tencent.mm') >= 0) {
            console.log('[VCS:AT.play]\n  stack: ' + stack);
        }
        return this.play();
    };
} catch(e) {}

// --- Ringtone ---
try {
    var Ringtone = Java.use('android.media.Ringtone');
    Ringtone.play.implementation = function() {
        var stack = getShortStack();
        if (stack.indexOf('com.tencent.mm') >= 0) {
            console.log('[VCS:Ringtone.play]\n  stack: ' + stack);
        }
        return this.play();
    };
} catch(e) {}

// --- RingtoneManager.getRingtone ---
try {
    var RM = Java.use('android.media.RingtoneManager');
    RM.getRingtone.overloads.forEach(function(o) {
        o.implementation = function() {
            var stack = getShortStack();
            if (stack.indexOf('com.tencent.mm') >= 0) {
                console.log('[VCS:RM.getRingtone]\n  stack: ' + stack);
            }
            return o.apply(this, arguments);
        };
    });
} catch(e) {}

// --- SoundPool ---
try {
    var SP = Java.use('android.media.SoundPool');
    SP.play.overloads.forEach(function(o) {
        o.implementation = function() {
            var stack = getShortStack();
            if (stack.indexOf('com.tencent.mm') >= 0) {
                console.log('[VCS:SoundPool.play]\n  stack: ' + stack);
            }
            return o.apply(this, arguments);
        };
    });
} catch(e) {}

// --- AudioManager.playSoundEffect ---
try {
    var AM = Java.use('android.media.AudioManager');
    AM.playSoundEffect.overloads.forEach(function(o) {
        o.implementation = function() {
            var stack = getShortStack();
            if (stack.indexOf('com.tencent.mm') >= 0) {
                console.log('[VCS:AM.playSoundEffect]\n  stack: ' + stack);
            }
            return o.apply(this, arguments);
        };
    });
} catch(e) {}

// --- notification.x.d() — 直接 hook 源头 ---
// 方法名 d 可能被混淆，先枚举 notification.x 的所有方法
try {
    var NotifX = Java.use('com.tencent.mm.booter.notification.x');
    var methods = NotifX.class.getDeclaredMethods();
    console.log('[VCS] notification.x methods:');
    methods.forEach(function(m) {
        var sig = m.getName() + '(' + Array.from(m.getParameterTypes()).map(function(t){return t.getName();}).join(',') + ')';
        console.log('  ' + sig);
    });
} catch(e) {
    console.log('[VCS] notification.x not found: ' + e);
}

// --- 通用工具：短调用栈（前8帧，只保留 tencent 行）---
function getShortStack() {
    var ex = Java.use('java.lang.Exception').$new();
    var frames = ex.getStackTrace();
    var lines = [];
    for (var i = 0; i < frames.length && lines.length < 8; i++) {
        var f = frames[i].toString();
        if (f.indexOf('com.tencent.mm') >= 0) lines.push(f);
    }
    return lines.join(' ← ');
}

console.log('[VCS] probe_voip_sound loaded — call now');
