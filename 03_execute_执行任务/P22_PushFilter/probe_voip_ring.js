/**
 * probe_voip_ring.js — 找 VoIP 铃声真实播放路径
 * 用法: frida -U -n com.tencent.mm -l probe_voip_ring.js --no-pause
 * 来电响铃，不用接，看 [VCR:*] 输出
 */
'use strict';

Java.perform(function () {

    // ── 1. oz4.d 全方法 hook ───────────────────────────────────────────
    // 枚举所有方法名，逐 overload 替换 implementation
    (function hookOz4d() {
        try {
            var Cls = Java.use('oz4.d');
            var seen = {};
            Cls.class.getDeclaredMethods().forEach(function (m) {
                var name = m.getName();
                if (seen[name]) return;
                seen[name] = true;
                try {
                    Cls[name].overloads.forEach(function (o) {
                        o.implementation = function () {
                            console.log('[VCR:oz4.d.' + name + ']');
                            return o.apply(this, arguments);
                        };
                    });
                } catch (e2) { /* static-only or inaccessible */ }
            });
            console.log('[VCR] oz4.d hooked');
        } catch (e) {
            console.log('[VCR] oz4.d fail: ' + e);
            // 备用：扫所有带 voip/VoIP 的微信类
            Java.enumerateLoadedClasses({
                onMatch: function (name) {
                    var lo = name.toLowerCase();
                    if (lo.indexOf('voip') >= 0 && name.indexOf('com.tencent.mm') === 0)
                        console.log('[VCR:scan] ' + name);
                },
                onComplete: function () { console.log('[VCR:scan] done'); }
            });
        }
    })();

    // ── 2. AudioTrack.play + write ─────────────────────────────────────
    // streamType: 0=VOICE_CALL 2=RING 3=MUSIC 4=ALARM 5=NOTIF
    (function hookAudioTrack() {
        try {
            var AT = Java.use('android.media.AudioTrack');
            AT.play.implementation = function () {
                var st = -1;
                try { st = this.getStreamType(); } catch (e) {}
                console.log('[VCR:AT.play] stream=' + st);
                return this.play();
            };
            // write — 每次写音频数据都会触发；只打第一次，避免刷屏
            var atWriteLogged = false;
            AT.write.overloads.forEach(function (o) {
                o.implementation = function () {
                    if (!atWriteLogged) {
                        atWriteLogged = true;
                        console.log('[VCR:AT.write] first write (stream logged above)');
                    }
                    return o.apply(this, arguments);
                };
            });
            console.log('[VCR] AudioTrack hooked');
        } catch (e) {
            console.log('[VCR] AudioTrack fail: ' + e);
        }
    })();

    // ── 3. t8.L1 — 平台振动/声音工具（振动链已确认走这里）─────────────
    (function hookT8() {
        try {
            var T8 = Java.use('com.tencent.mm.sdk.platformtools.t8');
            T8.L1.overloads.forEach(function (o) {
                o.implementation = function () {
                    var args = [];
                    for (var i = 0; i < arguments.length; i++) args.push(String(arguments[i]));
                    console.log('[VCR:t8.L1] args=' + args.join(','));
                    return o.apply(this, arguments);
                };
            });
            console.log('[VCR] t8.L1 hooked');
        } catch (e) {
            console.log('[VCR] t8.L1 fail: ' + e);
        }
    })();

    // ── 4. MediaPlayer.start 无过滤（彻底排除）────────────────────────
    (function hookMP() {
        try {
            var MP = Java.use('android.media.MediaPlayer');
            MP.start.implementation = function () {
                console.log('[VCR:MP.start]');
                return this.start();
            };
            console.log('[VCR] MediaPlayer hooked');
        } catch (e) {
            console.log('[VCR] MediaPlayer fail: ' + e);
        }
    })();

    // ── 5. AudioManager.setStreamVolume + requestAudioFocus ────────────
    (function hookAM() {
        try {
            var AM = Java.use('android.media.AudioManager');
            AM.setStreamVolume.implementation = function (stream, vol, flags) {
                console.log('[VCR:AM.vol] stream=' + stream + ' vol=' + vol);
                return this.setStreamVolume(stream, vol, flags);
            };
            AM.requestAudioFocus.overloads.forEach(function (o) {
                o.implementation = function () {
                    var info = '';
                    try {
                        // API 26+: AudioFocusRequest arg
                        var req = arguments[0];
                        if (req && req.getAudioAttributes) {
                            info = ' usage=' + req.getAudioAttributes().getUsage();
                        }
                    } catch (e2) {}
                    console.log('[VCR:AM.focus]' + info);
                    return o.apply(this, arguments);
                };
            });
            console.log('[VCR] AudioManager hooked');
        } catch (e) {
            console.log('[VCR] AM fail: ' + e);
        }
    })();

    // ── 6. Ringtone.play（最终确认）──────────────────────────────────
    (function hookRingtone() {
        try {
            var R = Java.use('android.media.Ringtone');
            R.play.implementation = function () {
                console.log('[VCR:Ringtone.play]');
                return this.play();
            };
            console.log('[VCR] Ringtone hooked');
        } catch (e) {
            console.log('[VCR] Ringtone fail: ' + e);
        }
    })();

    console.log('[VCR] probe_voip_ring ready — make a call now');
});
