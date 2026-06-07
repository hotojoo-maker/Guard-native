/**
 * probe_loc_intent_8071.js — dump LocationIntent 字段（选点结果坐标载体，伪装订位设置闭环收尾）
 *
 * 上轮结论：原生选点页确认后 setResult(-1)，坐标包在 Intent extra "KLocationIntent"
 *          = com.tencent.mm.pluginsdk.location.LocationIntent 对象。
 * 本探针：在 setResult 时反射 dump 该对象所有字段+值，找经纬度字段名（设置流程读它存 MMKV）。
 *
 * 用法：
 *   $mmpid = (adb shell "ps -ef | grep ' com.tencent.mm$'") -split '\s+' | Select-Object -Index 1
 *   frida -U -p $mmpid -l tools\probe_loc_intent_8071.js 2>&1 | tee tools\probe_loc_intent_$(Get-Date -f 'HHmmss').log
 *
 * 操作：发送位置→选点→点右上角发送。
 *   [INT:dump] LocationIntent 字段名:类型=值（找 lat/lng）
 */
Java.perform(function () {
    'use strict';
    var TAG = 'INT';
    function ts() {
        var d = new Date(), p = function (n) { return n < 10 ? '0' + n : '' + n; };
        return p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds());
    }
    function log(s, m) { console.log(ts() + ' [' + TAG + ':' + s + '] ' + m); }

    function dumpFields(obj, label) {
        if (obj === null || obj === undefined) { log('dump', label + ' = null'); return; }
        try {
            var cls = obj.getClass();
            log('dump', label + ' class=' + cls.getName());
            var depth = 0;
            while (cls && depth < 3) {
                var cn = cls.getName();
                if (cn === 'java.lang.Object') break;
                var fs = cls.getDeclaredFields();
                for (var i = 0; i < fs.length; i++) {
                    try {
                        var f = fs[i]; f.setAccessible(true);
                        var t = f.getType().getName();
                        var v; try { v = '' + f.get(obj); } catch (e) { v = '<?>'; }
                        if (v.length > 80) v = v.substring(0, 80) + '...';
                        console.log('    ' + f.getName() + ':' + t + ' = ' + v);
                    } catch (e2) {}
                }
                cls = cls.getSuperclass(); depth++;
            }
        } catch (e) { log('warn', 'dumpFields: ' + e); }
    }

    try {
        var Activity = Java.use('android.app.Activity');
        Activity.setResult.overload('int', 'android.content.Intent').implementation = function (code, intent) {
            try {
                var cn = this.getClass().getName();
                if (cn.indexOf('com.tencent.mm.plugin.location') === 0 && intent !== null) {
                    var li = intent.getParcelableExtra('KLocationIntent');
                    if (li !== null) {
                        log('dump', '=== ' + cn + ' KLocationIntent ===');
                        dumpFields(li, 'LocationIntent');
                    }
                }
            } catch (e) { log('warn', 'setResult: ' + e); }
            return this.setResult(code, intent);
        };
        log('ok', 'setResult+KLocationIntent dump hooked');
    } catch (e) { log('fail', 'setResult: ' + e); }

    log('init', '=== LocationIntent dump 探针就绪：发送位置→选点→发送，看 [INT:dump] 找 lat/lng 字段 ===');
});
