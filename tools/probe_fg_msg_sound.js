/**
 * probe_fg_msg_sound.js — 定位微信「前台收消息的 in-app 叮声」播放点（ForegroundMute）
 *
 * 背景：微信前台收消息走 in-app 路径（不建通知），x.d 不触发；那声"叮"是 in-app 音效。
 * 静默档要拦掉它、震动档要在它那触发震动。先找出是哪个 API + 微信哪个方法放的。
 *
 * 策略：hook 4 个候选播音 API，每次命中打**过滤后的微信调用栈**（只留 com.tencent.mm 帧）。
 * 看哪个 API 在「前台收密友消息」那一刻触发、栈顶微信方法是谁。
 *
 * 用法（attach 主进程，微信停在前台会话列表/聊天页）：
 *   $mmpid = (adb shell "ps -ef | grep ' com.tencent.mm$'") -split '\s+' | Select-Object -Index 1
 *   frida -U -p $mmpid -l tools\probe_fg_msg_sound.js 2>&1 | tee tools\probe_fg_sound_$(Get-Date -f 'HHmmss').log
 *
 * 操作：脚本就绪后，微信保持前台（会话列表或随便一个聊天页），让密友发一条消息，
 *       听到"叮"的同时看哪个 [HIT] 打出来 + 栈。多发两条对照。
 */
Java.perform(function () {
    'use strict';
    var TAG = 'FGS';

    function ts() {
        var d = new Date(), p = function (n) { return n < 10 ? '0' + n : '' + n; };
        return p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds()) + '.'
            + (d.getMilliseconds() + '').padStart(3, '0');
    }
    function log(s, m) { console.log(ts() + ' [' + TAG + ':' + s + '] ' + m); }

    // 过滤调用栈：只留 com.tencent.mm 帧（最多 8 条），暴露微信播音方法
    function wxStack() {
        try {
            var frames = Java.use('java.lang.Thread').currentThread().getStackTrace();
            var out = [];
            for (var i = 0; i < frames.length && out.length < 8; i++) {
                var s = String(frames[i]);
                if (s.indexOf('com.tencent.mm') < 0) continue;
                out.push(s.trim());
            }
            return out.length ? out.join('\n      ← ') : '(no wx frame)';
        } catch (e) { return '<stack err>'; }
    }

    function hookVoid(clsName, method, label, overloadArgs) {
        try {
            var C = Java.use(clsName);
            var target = overloadArgs ? C[method].overload.apply(C[method], overloadArgs) : C[method];
            target.implementation = function () {
                var args = Array.prototype.slice.call(arguments);
                log('HIT ' + label, 'args=' + JSON.stringify(args.map(function (a) {
                    try { return (a === null) ? 'null' : String(a); } catch (e) { return '?'; }
                })));
                log('STK', wxStack());
                return target.apply(this, args);
            };
            log('OK', label + ' hooked');
        } catch (e) { log('FAIL', label + ': ' + e); }
    }

    hookVoid('android.media.MediaPlayer', 'start', 'MediaPlayer.start', null);
    hookVoid('android.media.AudioTrack', 'play', 'AudioTrack.play', null);
    hookVoid('android.media.SoundPool', 'play', 'SoundPool.play',
        ['int', 'float', 'float', 'int', 'int', 'float']);
    hookVoid('android.media.AudioManager', 'playSoundEffect', 'AudioManager.playSoundEffect', ['int']);

    // 微信常用自封装：MediaPlayer 也可能经 SystemSoundsManager / NotifyManager 之类，
    // 上面 4 个底层 API 必经其一，靠栈反推微信方法即可。
    log('INIT', '=== 探针就绪：微信保持前台，让密友发一条消息，听到"叮"看哪个 [HIT]+栈 ===');
});
