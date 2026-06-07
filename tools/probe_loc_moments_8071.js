/**
 * probe_loc_moments_8071.js — 找「不发消息」的位置选点入口（朋友圈所在位置 / POI 选择器）
 *
 * 背景：发送位置 RedirectUI(map_view_type=0) 会内部真发消息、无视 talker，不能用作"设置伪坐标"。
 * 目标：抓朋友圈发帖「所在位置」选点器的 Activity + 启动 extras + 返回结果键，
 *       它选完只返回 POI、不发消息，适合复用来设置伪坐标。
 *
 * 用法：
 *   $mmpid = (adb shell "ps -ef | grep ' com.tencent.mm$'") -split '\s+' | Select-Object -Index 1
 *   frida -U -p $mmpid -l tools\probe_loc_moments_8071.js 2>&1 | tee tools\probe_loc_moments_run.log
 *
 * 操作：朋友圈 → 发表（拍照/相册随便选）→ 点「所在位置」→ 选一个 POI → 完成。
 *   [MOM:start]  启动 location 插件 Activity 的类名 + extras（这就是要复用的选点器）
 *   [MOM:result] 该选点器 setResult 的 extras（返回 POI 的键名+值）
 */
Java.perform(function () {
    'use strict';
    var TAG = 'MOM';
    function ts() {
        var d = new Date(), p = function (n) { return n < 10 ? '0' + n : '' + n; };
        return p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds());
    }
    function log(s, m) { console.log(ts() + ' [' + TAG + ':' + s + '] ' + m); }
    var LOC = 'com.tencent.mm.plugin.location';

    function dumpExtras(intent, tag) {
        try {
            var ex = intent.getExtras();
            if (ex === null) { log(tag, 'no extras'); return; }
            var keys = ex.keySet().toArray();
            for (var i = 0; i < keys.length; i++) {
                var k = keys[i], v;
                try { v = '' + ex.get(k); } catch (e) { v = '<?>'; }
                if (v.length > 90) v = v.substring(0, 90) + '...';
                console.log('    ' + k + ' = ' + v);
            }
        } catch (e) { log('warn', 'dumpExtras: ' + e); }
    }

    function targetIsLoc(intent) {
        try {
            var c = intent.getComponent();
            if (c !== null) return c.getClassName().indexOf(LOC) === 0;
            // 无显式 component 时看 action/类名兜底
            return false;
        } catch (e) { return false; }
    }

    // 启动 location 插件 Activity 的 Intent
    try {
        var Activity = Java.use('android.app.Activity');
        Activity.startActivityForResult.overload('android.content.Intent', 'int').implementation =
            function (intent, req) {
                try {
                    if (intent !== null && targetIsLoc(intent)) {
                        log('start', 'forResult from=' + this.getClass().getName()
                            + ' -> ' + intent.getComponent().getClassName() + ' req=' + req);
                        dumpExtras(intent, 'start');
                    }
                } catch (e) {}
                return this.startActivityForResult(intent, req);
            };
        Activity.startActivity.overload('android.content.Intent').implementation = function (intent) {
            try {
                if (intent !== null && targetIsLoc(intent)) {
                    log('start', 'startActivity from=' + this.getClass().getName()
                        + ' -> ' + intent.getComponent().getClassName());
                    dumpExtras(intent, 'start');
                }
            } catch (e) {}
            return this.startActivity(intent);
        };
        log('ok', 'startActivity hooks installed');
    } catch (e) { log('fail', 'startActivity: ' + e); }

    // location 插件 Activity 的 setResult
    try {
        var Act2 = Java.use('android.app.Activity');
        Act2.setResult.overload('int', 'android.content.Intent').implementation = function (code, data) {
            try {
                var cls = this.getClass().getName();
                if (cls.indexOf(LOC) === 0 && data !== null) {
                    log('result', cls + ' setResult(' + code + ') extras:');
                    dumpExtras(data, 'result');
                }
            } catch (e) {}
            return this.setResult(code, data);
        };
        log('ok', 'setResult hook installed');
    } catch (e) { log('fail', 'setResult: ' + e); }

    log('init', '=== 朋友圈选点探针就绪：朋友圈→发表→所在位置→选 POI→完成，看 [MOM:start]/[MOM:result] ===');
});
