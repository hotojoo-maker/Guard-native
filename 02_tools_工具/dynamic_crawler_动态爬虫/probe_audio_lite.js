"use strict";
var TAG = "[FG_AUDIO]";

// 1. MediaPlayer.start
try {
    var MP = Java.use("android.media.MediaPlayer");
    MP.start.implementation = function() {
        console.log(TAG + " [MP.start]");
        return this.start();
    };
    console.log(TAG + " MediaPlayer.start hooked");
} catch(e) { console.log(TAG + " MP fail: " + e); }

// 2. AudioTrack.play
try {
    var AT = Java.use("android.media.AudioTrack");
    AT.play.implementation = function() {
        var st = -1;
        try { st = this.getStreamType(); } catch(e2) {}
        console.log(TAG + " [AT.play] streamType=" + st);
        return this.play();
    };
    console.log(TAG + " AudioTrack.play hooked");
} catch(e) { console.log(TAG + " AT fail: " + e); }

// 3. SoundPool.play
try {
    var SP = Java.use("android.media.SoundPool");
    SP.play.overload("int","float","float","int","int","float").implementation = function(a,b,c,d,e,f) {
        console.log(TAG + " [SP.play] soundID=" + a);
        return this.play(a,b,c,d,e,f);
    };
    console.log(TAG + " SoundPool.play hooked");
} catch(e) { console.log(TAG + " SP fail: " + e); }

// 4. AudioManager.playSoundEffect
try {
    var AM = Java.use("android.media.AudioManager");
    AM.playSoundEffect.overload("int").implementation = function(effect) {
        console.log(TAG + " [AM.playSoundEffect] effect=" + effect);
        return this.playSoundEffect(effect);
    };
    console.log(TAG + " AM.playSoundEffect hooked");
} catch(e) { console.log(TAG + " AM fail: " + e); }

console.log(TAG + " === ready, trigger now ===");
