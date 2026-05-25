/**
 * probe_fg_sound.js
 * 目标：前台密友消息声音走哪个 API
 * 用法：frida -U -n com.tencent.mm -l probe_fg_sound.js
 * 操作：微信前台 → 让密友发消息 → 看 [FG:*] 标签
 */
'use strict';

function getShortStack() {
    var ex = Java.use('java.lang.Exception').$new();
    var frames = ex.getStackTrace();
    var lines = [];
    for (var i = 0; i < frames.length && lines.length < 12; i++) {
        var f = frames[i].toString();
        if (f.indexOf('com.tencent.mm') >= 0) lines.push(f);
    }
    return lines.join('\n    ');
}

// --- SoundPool (最可能前台消息音) ---
try {
    var SP = Java.use('android.media.SoundPool');
    SP.play.overloads.forEach(function(o) {
        o.implementation = function() {
            var stack = getShortStack();
            if (stack.indexOf('com.tencent.mm') >= 0) {
                console.log('[FG:SoundPool.play]\n  stack:\n    ' + stack);
            }
            return o.apply(this, arguments);
        };
    });
    console.log('[FG] SoundPool hooked');
} catch(e) { console.log('[FG] SoundPool err: ' + e); }

// --- MediaPlayer ---
try {
    var MP = Java.use('android.media.MediaPlayer');
    ['start', 'setDataSource'].forEach(function(m) {
        var overloads = MP[m].overloads;
        overloads.forEach(function(o) {
            o.implementation = function() {
                var stack = getShortStack();
                if (stack.indexOf('com.tencent.mm') >= 0) {
                    var args = Array.from(arguments).map(function(a) {
                        try { return String(a); } catch(e) { return '?'; }
                    });
                    console.log('[FG:MP.' + m + '] ' + JSON.stringify(args) +
                        '\n  stack:\n    ' + stack);
                }
                return o.apply(this, arguments);
            };
        });
    });
    console.log('[FG] MediaPlayer hooked');
} catch(e) { console.log('[FG] MediaPlayer err: ' + e); }

// --- Ringtone ---
try {
    var RT = Java.use('android.media.Ringtone');
    RT.play.implementation = function() {
        var stack = getShortStack();
        if (stack.indexOf('com.tencent.mm') >= 0) {
            console.log('[FG:Ringtone.play]\n  stack:\n    ' + stack);
        }
        return this.play();
    };
    console.log('[FG] Ringtone hooked');
} catch(e) { console.log('[FG] Ringtone err: ' + e); }

console.log('[FG] probe loaded — 请让密友前台发消息');
