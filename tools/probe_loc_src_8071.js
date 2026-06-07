/**
 * probe_loc_src_8071.js — 追"我的位置"真实坐标源头（伪装订位 v3）
 *
 * v2 结论：真实坐标以 LatLng(26.268614,107.506889) 反复构造；requestLocationUpdates/onLocationChanged
 *         未触发（微信用缓存/getLastKnownLocation 或启动期已注册的 listener）。
 * 目标：找到微信"读我的当前位置"的源头方法 = 全局注入点。
 *
 * 手段：
 *   A. LatLng.<init> 对"真实坐标"(lat 26.x / lng 107.x) 打调用栈 → 找喂坐标的微信方法
 *   B. hook TencentLocationManager.getLastKnownLocation() + 所有返回 TencentLocation 的方法
 *   C. hook TencentLocationManagerProxy 全部方法名（看微信走代理哪个口）
 *
 * ⚠️ 真实坐标过滤范围按本机实测：lat∈(26,27) lng∈(107,108)。换地点需改 isReal()。
 *
 * 用法：
 *   $mmpid = (adb shell "ps -ef | grep ' com.tencent.mm$'") -split '\s+' | Select-Object -Index 1
 *   frida -U -p $mmpid -l tools\probe_loc_src_8071.js 2>&1 | tee tools\probe_loc_src_$(Get-Date -f 'HHmmss').log
 *
 * 操作：开 发送位置/附近的人，让地图定位到"我的位置"。
 *   [SRC:stack]   真实坐标 LatLng 的调用栈（找微信源头方法）
 *   [SRC:lastKnown] getLastKnownLocation 返回的坐标 + 调用栈
 *   [SRC:ret]     其它返回 TencentLocation 的方法命中
 */
Java.perform(function () {
    'use strict';
    var TAG = 'SRC';
    function ts() {
        var d = new Date(), p = function (n) { return n < 10 ? '0' + n : '' + n; };
        return p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds());
    }
    function log(s, m) { console.log(ts() + ' [' + TAG + ':' + s + '] ' + m); }
    function stackLines(n) {
        return Java.use('android.util.Log').getStackTraceString(
            Java.use('java.lang.Exception').$new('s')).split('\n').slice(1, (n || 12) + 1);
    }
    function printStack(n) { stackLines(n).forEach(function (l) { console.log('    ' + l.trim()); }); }
    function isReal(lat, lng) { return lat > 26 && lat < 27 && lng > 107 && lng < 108; }

    // ── A. LatLng 真实坐标调用栈 ──────────────────────────────────────
    try {
        var LatLng = Java.use('com.tencent.tencentmap.mapsdk.maps.model.LatLng');
        var cnt = 0;
        LatLng.$init.overload('double', 'double').implementation = function (a, b) {
            if (cnt < 4 && isReal(a, b)) {
                cnt++;
                log('stack', 'new LatLng(' + a + ',' + b + ') #' + cnt + ' caller:');
                printStack(14);
            }
            return this.$init(a, b);
        };
        log('ok', 'LatLng real-coord stack hooked');
    } catch (e) { log('fail', 'LatLng: ' + e); }

    // ── B. TencentLocationManager 返回 TencentLocation 的方法 ──────────
    try {
        var MgrName = 'com.tencent.map.geolocation.sapp.TencentLocationManager';
        var Mgr = Java.use(MgrName);
        var ms = Mgr.class.getDeclaredMethods();
        var hooked = 0;
        for (var i = 0; i < ms.length; i++) {
            var m = ms[i];
            var rt = m.getReturnType().getName();
            if (rt.indexOf('TencentLocation') < 0) continue;           // 返回定位对象的方法
            if (m.getParameterTypes().length !== 0) continue;
            var nm = m.getName();
            log('sig', MgrName + '.' + nm + '()->' + rt);
            (function (mName, isLastKnown) {
                try {
                    Mgr[mName].overload().implementation = function () {
                        var r = this[mName]();
                        var lat = '?', lng = '?';
                        try { lat = r.getLatitude(); lng = r.getLongitude(); } catch (e) {}
                        log(isLastKnown ? 'lastKnown' : 'ret', mName + '()-> lat=' + lat + ' lng=' + lng);
                        if (isLastKnown) printStack(10);
                        return r;
                    };
                    hooked++;
                } catch (e2) { log('warn', mName + ' fail: ' + e2); }
            })(nm, nm.toLowerCase().indexOf('lastknown') >= 0);
        }
        log('ok', 'TencentLocationManager TencentLocation-returning hooked x' + hooked);
    } catch (e) { log('fail', 'TencentLocationManager: ' + e); }

    // ── C. TencentLocationManagerProxy 全方法签名（先看口，不改） ──────
    try {
        var ProxyName = 'com.tencent.map.geolocation.sapp.proxy.TencentLocationManagerProxy';
        var Proxy = Java.use(ProxyName);
        var pms = Proxy.class.getDeclaredMethods();
        for (var i = 0; i < pms.length; i++) {
            var pm = pms[i];
            var pnames = []; var pts = pm.getParameterTypes();
            for (var j = 0; j < pts.length; j++) pnames.push(pts[j].getName());
            log('proxySig', ProxyName + '.' + pm.getName() + '(' + pnames.join(',') + ')->'
                + pm.getReturnType().getName());
        }
    } catch (e) { log('warn', 'Proxy sig: ' + e); }

    log('init', '=== v3 探针就绪：开 发送位置/附近的人 让地图定位，看 [SRC:stack]/[SRC:lastKnown] ===');
});
