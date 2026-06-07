/**
 * probe_loc_poc_8071.js — 伪装订位 PoC 注入（验证 pz0.h.c / n83.g.onGetLocation 注入点）
 *
 * 注入点（v4 实证契约）：
 *   pz0.h.c(pz0.h, boolean, double LAT, double LNG, int, double,double,double, Bundle)  ← 分发源头
 *   n83.g.onGetLocation(boolean, float LNG, float LAT, int, double,double,double)        ← 下游回调(顺序反)
 *
 * PoC：把真实坐标替换成天安门，双保险注入，验证地图是否跳到伪坐标。
 *
 * 用法：
 *   $mmpid = (adb shell "ps -ef | grep ' com.tencent.mm$'") -split '\s+' | Select-Object -Index 1
 *   frida -U -p $mmpid -l tools\probe_loc_poc_8071.js 2>&1 | tee tools\probe_loc_poc_$(Get-Date -f 'HHmmss').log
 *
 * 操作：开 发送位置/附近的人 → 地图应跳到天安门。
 */
Java.perform(function () {
    'use strict';
    var TAG = 'POC';
    var FAKE_LAT = 39.9087;   // 天安门 纬度
    var FAKE_LNG = 116.3975;  // 天安门 经度

    function ts() {
        var d = new Date(), p = function (n) { return n < 10 ? '0' + n : '' + n; };
        return p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds());
    }
    function log(s, m) { console.log(ts() + ' [' + TAG + ':' + s + '] ' + m); }

    // ── 注入点 1：pz0.h.c（分发源头，double）arg2=lat arg3=lng ──────────
    try {
        var H = Java.use('pz0.h');
        H.c.overload('pz0.h', 'boolean', 'double', 'double', 'int', 'double', 'double', 'double',
            'android.os.Bundle').implementation =
            function (self, ok, lat, lng, a4, a5, a6, a7, bundle) {
                log('pz0', 'orig lat=' + lat + ' lng=' + lng + ' -> FAKE ' + FAKE_LAT + ',' + FAKE_LNG);
                return this.c(self, ok, FAKE_LAT, FAKE_LNG, a4, a5, a6, a7, bundle);
            };
        log('ok', 'pz0.h.c injected');
    } catch (e) { log('fail', 'pz0.h.c: ' + e); }

    // ── 注入点 2：n83.g.onGetLocation（下游回调，float，arg1=lng arg2=lat）─
    try {
        var G = Java.use('n83.g');
        G.onGetLocation.overload('boolean', 'float', 'float', 'int', 'double', 'double', 'double')
            .implementation = function (ok, lng, lat, a3, a4, a5, a6) {
                log('n83', 'orig lng=' + lng + ' lat=' + lat + ' -> FAKE ' + FAKE_LNG + ',' + FAKE_LAT);
                return this.onGetLocation(ok, FAKE_LNG, FAKE_LAT, a3, a4, a5, a6);
            };
        log('ok', 'n83.g.onGetLocation injected');
    } catch (e) { log('fail', 'n83.g.onGetLocation: ' + e); }

    log('init', '=== PoC 注入就绪（天安门 ' + FAKE_LAT + ',' + FAKE_LNG
        + '）：开 发送位置/附近的人，看地图是否跳到天安门 ===');
});
