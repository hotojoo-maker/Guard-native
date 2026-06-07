/**
 * probe_loc_sdk_8071.js — 微信 8.0.71 LBS SDK 定位结果注入点定位（伪装订位 v2）
 *
 * v1 结论：发位置/共享/朋友圈坐标不走消息层（wy4.a/q2.F/kwebmap intent 全证伪），
 *         坐标来自腾讯地图 SDK。产品要求：设置页提前改好坐标 → 全局生效 = hook SDK 定位结果。
 *
 * 本探针锁定"定位结果产出点"，找一个改了就全局生效的注入靶子：
 *   A. com.tencent.map.geolocation.sapp.TencentLocationManager.requestLocationUpdates(...)
 *      → 抓到微信注册的 listener 实例，动态 hook 它的 onLocationChanged
 *   B. com.tencent.tencentmap.lbssdk.sapp.service.e（TencentLocation 具体实现）
 *      → dump 方法，hook 无参 double 返回（getLatitude/getLongitude）
 *   C. com.tencent.tencentmap.mapsdk.maps.model.LatLng.<init>(double,double)
 *      → 地图坐标构造（选点/相机中心），看坐标从哪来
 *
 * 用法（warm-attach 主进程）：
 *   $mmpid = (adb shell "ps -ef | grep ' com.tencent.mm$'") -split '\s+' | Select-Object -Index 1
 *   frida -U -p $mmpid -l tools\probe_loc_sdk_8071.js 2>&1 | tee tools\probe_loc_sdk_$(Get-Date -f 'HHmmss').log
 *
 * 操作：脚本就绪后，开「附近的人」或「发送位置」或「共享实时位置」任一即可触发定位。
 *   [SDK:reqUpd]  requestLocationUpdates → listener 类名（微信定位回调实现）
 *   [SDK:onLoc]   onLocationChanged 命中 → lat/lng（这就是全局注入点）
 *   [SDK:getter]  TencentLocation 实现的 double getter 命中 + 值
 *   [SDK:latlng]  LatLng 构造坐标
 */
Java.perform(function () {
    'use strict';
    var TAG = 'SDK';
    function ts() {
        var d = new Date(), p = function (n) { return n < 10 ? '0' + n : '' + n; };
        return p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds());
    }
    function log(s, m) { console.log(ts() + ' [' + TAG + ':' + s + '] ' + m); }
    function printStack(tagExc, n) {
        var st = Java.use('android.util.Log').getStackTraceString(
            Java.use('java.lang.Exception').$new(tagExc));
        st.split('\n').slice(1, (n || 6) + 1).forEach(function (l) { console.log('    ' + l.trim()); });
    }

    // 动态 hook 某个 listener 实例类的 onLocationChanged（避免接口 hook 不生效）
    var hookedListenerClasses = {};
    function hookListenerClass(cn) {
        if (!cn || hookedListenerClasses[cn]) return;
        hookedListenerClasses[cn] = true;
        try {
            var L = Java.use(cn);
            var ms = L.class.getDeclaredMethods();
            var done = 0;
            for (var i = 0; i < ms.length; i++) {
                if (ms[i].getName() !== 'onLocationChanged') continue;
                var pts = ms[i].getParameterTypes();
                var names = []; for (var j = 0; j < pts.length; j++) names.push(pts[j].getName());
                (function (paramNames) {
                    try {
                        var ov = L.onLocationChanged.overload.apply(L.onLocationChanged, paramNames);
                        ov.implementation = function () {
                            var a = Array.prototype.slice.call(arguments);
                            var loc = a[0], lat = '?', lng = '?';
                            try { lat = loc.getLatitude(); lng = loc.getLongitude(); } catch (e) {}
                            log('onLoc', cn + '.onLocationChanged lat=' + lat + ' lng=' + lng
                                + ' err=' + (a.length > 1 ? a[1] : '?'));
                            return this.onLocationChanged.apply(this, a);
                        };
                        done++;
                    } catch (e2) { log('warn', cn + '.onLocationChanged overload fail: ' + e2); }
                })(names);
            }
            log('ok', 'listener hooked ' + cn + ' onLocationChanged x' + done);
        } catch (e) { log('warn', 'hookListenerClass ' + cn + ': ' + e); }
    }

    // ── A. TencentLocationManager.requestLocationUpdates ──────────────
    try {
        var MgrName = 'com.tencent.map.geolocation.sapp.TencentLocationManager';
        var Mgr = Java.use(MgrName);
        var ms = Mgr.class.getDeclaredMethods();
        var hooked = 0;
        for (var i = 0; i < ms.length; i++) {
            if (ms[i].getName() !== 'requestLocationUpdates') continue;
            var pts = ms[i].getParameterTypes();
            var names = []; for (var j = 0; j < pts.length; j++) names.push(pts[j].getName());
            (function (paramNames) {
                try {
                    var ov = Mgr.requestLocationUpdates.overload.apply(Mgr.requestLocationUpdates, paramNames);
                    ov.implementation = function () {
                        var a = Array.prototype.slice.call(arguments);
                        // 找 listener 参数（实现 TencentLocationListener 的那个）
                        for (var k = 0; k < a.length; k++) {
                            if (a[k] === null || a[k] === undefined) continue;
                            try {
                                var lcn = a[k].getClass().getName();
                                if (paramNames[k].indexOf('Listener') >= 0
                                    || lcn.indexOf('com.tencent.mm') === 0) {
                                    log('reqUpd', 'listener[' + k + ']=' + lcn);
                                    hookListenerClass(lcn);
                                }
                            } catch (e) {}
                        }
                        return this.requestLocationUpdates.apply(this, a);
                    };
                    hooked++;
                } catch (e2) { log('warn', 'reqUpd overload fail: ' + e2); }
            })(names);
        }
        log('ok', 'TencentLocationManager.requestLocationUpdates hooked x' + hooked);
    } catch (e) { log('fail', 'TencentLocationManager: ' + e); }

    // ── B. TencentLocation 具体实现 e 的 double getter ────────────────
    try {
        var ImplName = 'com.tencent.tencentmap.lbssdk.sapp.service.e';
        var Impl = Java.use(ImplName);
        var ms2 = Impl.class.getDeclaredMethods();
        var hooked = 0;
        for (var i = 0; i < ms2.length; i++) {
            var m = ms2[i];
            var rt = m.getReturnType().getName();
            var pc = m.getParameterTypes().length;
            if (rt !== 'double' || pc !== 0) continue;
            var nm = m.getName();
            log('sig', ImplName + '.' + nm + '()D');
            (function (mName) {
                try {
                    Impl[mName].overload().implementation = function () {
                        var v = this[mName]();
                        log('getter', ImplName + '.' + mName + '()=' + v);
                        return v;
                    };
                    hooked++;
                } catch (e2) { log('warn', ImplName + '.' + mName + ' fail: ' + e2); }
            })(nm);
        }
        log('ok', ImplName + ' double getters hooked x' + hooked);
    } catch (e) { log('fail', 'lbssdk.sapp.service.e: ' + e + ' (可能非 TencentLocation 实现)'); }

    // ── C. LatLng.<init>(double,double) ───────────────────────────────
    try {
        var LatLng = Java.use('com.tencent.tencentmap.mapsdk.maps.model.LatLng');
        var ctors = LatLng.class.getDeclaredConstructors();
        for (var i = 0; i < ctors.length; i++) {
            var pts = ctors[i].getParameterTypes();
            var names = []; for (var j = 0; j < pts.length; j++) names.push(pts[j].getName());
            // 只 hook (double,double)，避免噪音
            if (!(names.length === 2 && names[0] === 'double' && names[1] === 'double')) continue;
            (function (paramNames) {
                try {
                    var ov = LatLng.$init.overload.apply(LatLng.$init, paramNames);
                    var cnt = 0;
                    ov.implementation = function (a, b) {
                        if (cnt < 8) { log('latlng', 'new LatLng(' + a + ',' + b + ')'); cnt++; }
                        return this.$init(a, b);
                    };
                    log('ok', 'LatLng.<init>(double,double) hooked');
                } catch (e2) { log('warn', 'LatLng ctor fail: ' + e2); }
            })(names);
        }
    } catch (e) { log('fail', 'LatLng: ' + e); }

    log('init', '=== v2 SDK 探针就绪：开 附近的人/发送位置/共享位置 任一即可触发，看 [SDK:onLoc]/[SDK:getter] ===');
});
