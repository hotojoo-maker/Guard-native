/**
 * probe_foreground_audio.js
 * ForegroundMute 爬虫：找微信前台收消息时的音频入口
 *
 * 用法：
 *   adb forward tcp:27042 tcp:27042
 *   frida -U -n com.tencent.mm --no-pause -l probe_foreground_audio.js 2>&1 | tee audio_probe.log
 *
 * 触发：模块切换到 HIDDEN 状态，让密友发一条消息（微信前台）
 *
 * 目标：找到产生"叮"声的 Java 方法（MediaPlayer/AudioTrack/PlaybackParams 等）
 * 结果写 result.md 的 ForegroundMute 节
 */

"use strict";

var TAG = "[FG_AUDIO]";

// ── 1. MediaPlayer ──────────────────────────────────────────────────────────
try {
    var MP = Java.use("android.media.MediaPlayer");
    MP.start.implementation = function() {
        var stack = Java.use("android.util.Log").getStackTraceString(
            Java.use("java.lang.Exception").$new("MP.start trace")
        );
        console.log(TAG + " [MP.start] " + "\n  caller: " + stack.split("\n")[2]);
        return this.start();
    };
    console.log(TAG + " MediaPlayer.start hooked");
} catch(e) {
    console.log(TAG + " MediaPlayer.start hook fail: " + e);
}

// ── 2. AudioTrack.play (stream 정보 포함) ───────────────────────────────────
try {
    var AT = Java.use("android.media.AudioTrack");
    AT.play.implementation = function() {
        try {
            var streamType = this.getStreamType ? this.getStreamType() : -1;
            console.log(TAG + " [AT.play] streamType=" + streamType + " hash=" + this.hashCode());
        } catch(e2) {}
        return this.play();
    };
    console.log(TAG + " AudioTrack.play hooked");
} catch(e) {
    console.log(TAG + " AudioTrack.play hook fail: " + e);
}

// ── 3. SoundPool.play ───────────────────────────────────────────────────────
try {
    var SP = Java.use("android.media.SoundPool");
    SP.play.overload("int","float","float","int","int","float").implementation = function(a,b,c,d,e,f) {
        console.log(TAG + " [SP.play] soundID=" + a + " leftVol=" + b + " rightVol=" + c);
        return this.play(a, b, c, d, e, f);
    };
    console.log(TAG + " SoundPool.play hooked");
} catch(e) {
    console.log(TAG + " SoundPool.play hook fail: " + e);
}

// ── 4. AudioManager.playSoundEffect ────────────────────────────────────────
try {
    var AM = Java.use("android.media.AudioManager");
    AM.playSoundEffect.overload("int").implementation = function(effect) {
        console.log(TAG + " [AM.playSoundEffect] effect=" + effect);
        return this.playSoundEffect(effect);
    };
    AM.playSoundEffect.overload("int","float").implementation = function(effect, vol) {
        console.log(TAG + " [AM.playSoundEffect] effect=" + effect + " vol=" + vol);
        return this.playSoundEffect(effect, vol);
    };
    console.log(TAG + " AudioManager.playSoundEffect hooked");
} catch(e) {
    console.log(TAG + " AudioManager.playSoundEffect hook fail: " + e);
}

// ── 5. Ringtone.play（已知死路，仅确认） ────────────────────────────────────
try {
    var RT = Java.use("android.media.Ringtone");
    RT.play.implementation = function() {
        console.log(TAG + " [RT.play] confirmed hit ← unexpected!");
        return this.play();
    };
    console.log(TAG + " Ringtone.play hooked (confirm dead path)");
} catch(e) {
    console.log(TAG + " Ringtone.play hook fail: " + e);
}

// ── 6. AudioTrack constructor（找是哪个实例） ───────────────────────────────
try {
    var AT2 = Java.use("android.media.AudioTrack");
    // new AudioTrack(int,int,int,int,int,int)
    AT2.$init.overload("int","int","int","int","int","int").implementation = function(a,b,c,d,e,f) {
        console.log(TAG + " [AT.ctor] stream=" + a + " sampleRate=" + b + " channelMask=" + c);
        return this.$init(a,b,c,d,e,f);
    };
    console.log(TAG + " AudioTrack ctor hooked");
} catch(e) {
    console.log(TAG + " AudioTrack ctor hook fail: " + e);
}

// ── 7. 枚举包含 "sound" / "audio" / "notify" / "ring" 的微信类方法调用 ──────
// 用宽松的类名过滤，找微信自己封装的播音类
Java.enumerateClassLoadersSync().forEach(function(loader) {
    try {
        Java.classFactory.loader = loader;
        var loaded = Java.enumerateLoadedClassesSync();
        loaded.forEach(function(cn) {
            // 只关注微信包名下的音频相关类
            if (cn.indexOf("com.tencent.mm") < 0) return;
            var lower = cn.toLowerCase();
            if (lower.indexOf("sound") < 0 && lower.indexOf("audio") < 0 &&
                lower.indexOf("notify") < 0 && lower.indexOf("ring") < 0 &&
                lower.indexOf("media") < 0) return;

            console.log(TAG + " [found-class] " + cn);
        });
    } catch(e) {}
});

console.log(TAG + " === probe ready. Send a foreground message from hidden friend now ===");
