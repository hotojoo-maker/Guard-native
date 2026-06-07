/**
 * probe_loc_pick_8071.js — 捕获原生选点页"确认/右上角发送"时的选中坐标（伪装订位设置闭环）
 *
 * 目标：复用微信原生选点页设置伪坐标。需要知道：用户选点+点右上角时，
 *       选中坐标在哪个对象/哪个返回里，以便拦截存 MMKV。
 *
 * 手段：
 *   A. Activity.setResult(int, Intent)  — 选点页返回结果时 dump 全部 Intent extras（看坐标键名+值）
 *   B. LocationInfo — dump 构造入参 + 所有 double 参/返回方法（选中点模型）
 *   C. plugin.location Activity.finish() — 标记确认时机
 *   D. LocationInfo.toString —（若有）一眼看选中点
 *
 * 用法：
 *   $mmpid = (adb shell "ps -ef | grep ' com.tencent.mm$'") -split '\s+' | Select-Object -Index 1
 *   frida -U -p $mmpid -l tools\probe_loc_pick_8071.js 2>&1 | tee tools\probe_loc_pick_$(Get-Date -f 'HHmmss').log
 *
 * 操作：聊天→＋→位置→发送位置→在地图选一个点→点右上角"发送"。
 *   [PICK:result]  setResult 的 Intent extras（坐标键名+值 ← 设置流程要截获的）
 *   [PICK:info]    LocationInfo 方法命中 + double 值
 *   [PICK:finish]  选点页 finish 时机
 */
Java.perform(function () {
    'use strict';
    var TAG = 'PICK';
    function ts() {
        var d = new Date(), p = function (n) { return n < 10 ? '0' + n : '' + n; };
        return p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds());
    }
    function log(s, m) { console.log(ts() + ' [' + TAG + ':' + s + '] ' + m); }
    function isLocActivity(cn) {
        return cn && cn.indexOf('com.tencent.mm.plugin.location') === 0;
    }

    // ── A. Activity.setResult(int, Intent) ────────────────────────────
    try {
        var Activity = Java.use('android.app.Activity');
        Activity.setResult.overload('int', 'android.content.Intent').implementation = function (code, intent) {
            try {
                var cn = this.getClass().getName();
                if (isLocActivity(cn) && intent !== null) {
                    var ex = intent.getExtras();
                    if (ex !== null) {
                        var keys = ex.keySet().toArray();
                        log('result', cn + ' setResult(' + code + ') extras:');
                        for (var i = 0; i < keys.length; i++) {
                            var k = keys[i];
                            var v; try { v = '' + ex.get(k); } catch (e) { v = '<?>'; }
                            if (v.length > 90) v = v.substring(0, 90) + '...';
                            console.log('    ' + k + ' = ' + v);
                        }
                    } else {
                        log('result', cn + ' setResult(' + code + ') no extras');
                    }
                }
            } catch (e) { log('warn', 'setResult: ' + e); }
            return this.setResult(code, intent);
        };
        log('ok', 'Activity.setResult hooked');
    } catch (e) { log('fail', 'setResult: ' + e); }

    // ── C. plugin.location Activity.finish() ──────────────────────────
    try {
        var Act2 = Java.use('android.app.Activity');
        Act2.finish.overload().implementation = function () {
            try {
                var cn = this.getClass().getName();
                if (isLocActivity(cn)) log('finish', cn);
            } catch (e) {}
            return this.finish();
        };
        log('ok', 'Activity.finish hooked');
    } catch (e) { log('fail', 'finish: ' + e); }

    // ── B. LocationInfo dump ──────────────────────────────────────────
    try {
        var LiName = 'com.tencent.mm.plugin.location.model.LocationInfo';
        var Li = Java.use(LiName);
        // 构造
        var ctors = Li.class.getDeclaredConstructors();
        for (var c = 0; c < ctors.length; c++) {
            var pts = ctors[c].getParameterTypes();
            var names = []; for (var j = 0; j < pts.length; j++) names.push(pts[j].getName());
            (function (paramNames) {
                try {
                    var ov = paramNames.length ? Li.$init.overload.apply(Li.$init, paramNames) : Li.$init.overload();
                    ov.implementation = function () {
                        var a = Array.prototype.slice.call(arguments);
                        log('info', 'new LocationInfo(' + paramNames.join(',') + ') = [' + a.join(' | ') + ']');
                        return this.$init.apply(this, a);
                    };
                } catch (e2) {}
            })(names);
        }
        // double 参/返回方法
        var ms = Li.class.getDeclaredMethods();
        var hooked = 0;
        for (var i = 0; i < ms.length; i++) {
            var m = ms[i];
            var rt = m.getReturnType().getName();
            var pts2 = m.getParameterTypes();
            var hasDouble = (rt === 'double');
            for (var k = 0; k < pts2.length; k++) if (pts2[k].getName() === 'double') hasDouble = true;
            if (!hasDouble) continue;
            var nm = m.getName();
            var pnames = []; for (var k2 = 0; k2 < pts2.length; k2++) pnames.push(pts2[k2].getName());
            log('sig', LiName + '.' + nm + '(' + pnames.join(',') + ')->' + rt);
            (function (mName, paramNames) {
                try {
                    var ov = paramNames.length
                        ? Li[mName].overload.apply(Li[mName], paramNames)
                        : Li[mName].overload();
                    ov.implementation = function () {
                        var a = Array.prototype.slice.call(arguments);
                        var r = this[mName].apply(this, a);
                        log('info', LiName + '.' + mName + '(' + a.join(',') + ')' + (rt === 'double' ? '=' + r : ''));
                        return r;
                    };
                    hooked++;
                } catch (e2) { log('warn', LiName + '.' + mName + ' fail: ' + e2); }
            })(nm, pnames);
        }
        log('ok', LiName + ' double methods hooked x' + hooked);
    } catch (e) { log('fail', 'LocationInfo: ' + e); }

    log('init', '=== 选点捕获探针就绪：发送位置→选点→点右上角发送，看 [PICK:result]/[PICK:info] ===');
});
