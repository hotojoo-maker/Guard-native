/**
 * probe_loc_send_8071.js — 微信 8.0.71「发送位置消息」坐标写入点动态验证
 *
 * 目标（伪装订位 调研）：动态确认发位置时坐标在哪一步、走哪个类，
 * 并一锤定音回答「是否存在腾讯地图 LBS SDK（TencentLocation）」这条路线。
 *
 * 基于静态 smali 第一轮结论的候选点：
 *   - wy4.a.E(D) / F(D)        ← 写 纬度/经度 到聊天位置消息 XML（smali 完整）
 *   - Intent.getDoubleExtra("kwebmap_slat"/"kwebmap_lng")  ← 全链路统一坐标键
 *   - com.tencent.mm.ui.chatting.component.mg.t0()         ← 聊天发位置入口
 *   - com.tencent.mm.plugin.location.ui.impl.q2.F(String,String) ← 选点确认发送（方法体静态缺失）
 *   - com.tencent.tencentmap.lbssdk... / TencentLocation   ← 待验证是否加载
 *
 * 用法（warm-attach 主进程）：
 *   $mmpid = (adb shell "ps -ef | grep ' com.tencent.mm$'") -split '\s+' | Select-Object -Index 1
 *   frida -U -p $mmpid -l tools\probe_loc_send_8071.js 2>&1 | tee tools\probe_loc_send_$(Get-Date -f 'HHmmss').log
 *
 * 操作：脚本就绪后 →
 *   微信 → 任意聊天 → ＋号 → 位置 → 发送位置 → 地图选一个点 → 点「发送」
 *   关注输出：
 *     [LOC:scan]   ← 运行时加载的 location/lbssdk 相关类（含 TencentLocation 即 SDK 路线可行）
 *     [LOC:entry]  ← mg.t0 触发（确认发位置入口）
 *     [LOC:intent] ← getDoubleExtra 命中 kwebmap_slat/lng（坐标键路径）
 *     [LOC:write]  ← wy4.a.E/F 命中（发送前写坐标）+ 调用栈
 *     [LOC:q2F]    ← q2.F 命中（点发送时机）+ 参数
 */
Java.perform(function () {
    'use strict';
    var TAG = 'LOC';

    function ts() {
        var d = new Date(), p = function (n) { return n < 10 ? '0' + n : '' + n; };
        return p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds());
    }
    function log(s, m) { console.log(ts() + ' [' + TAG + ':' + s + '] ' + m); }
    function stack(tagExc) {
        return Java.use('android.util.Log').getStackTraceString(
            Java.use('java.lang.Exception').$new(tagExc));
    }
    function printStack(tagExc, n) {
        var lines = stack(tagExc).split('\n').slice(1, (n || 8) + 1);
        lines.forEach(function (l) { console.log('    ' + l.trim()); });
    }

    // ── 1. 运行时类扫描：location 插件 + 腾讯地图 LBS SDK ─────────────
    //   类是懒加载的，开图后才会出现，所以提供可重复调用的扫描函数。
    function scanLocClasses(tag) {
        var hitSdk = false, n = 0;
        Java.enumerateLoadedClassesSync().forEach(function (cn) {
            var lower = cn.toLowerCase();
            var isLbs = lower.indexOf('tencentmap') >= 0 || lower.indexOf('lbssdk') >= 0
                || lower.indexOf('tencentlocation') >= 0;
            var isPlugin = cn.indexOf('com.tencent.mm.plugin.location') === 0;
            if (isLbs || isPlugin) {
                console.log(ts() + ' [' + TAG + ':scan' + (tag ? ':' + tag : '') + '] ' + cn);
                n++;
                if (isLbs) hitSdk = true;
            }
        });
        log('scan' + (tag ? ':' + tag : ''), 'done hits=' + n + ' lbsSdkLoaded=' + hitSdk);
    }
    scanLocClasses('init');

    // ── 2. wy4.a.E/F/u/w（写/读 经纬度，smali 完整）─────────────────
    try {
        var WY = Java.use('wy4.a');
        var ms = WY.class.getDeclaredMethods();
        var want = { 'E': 1, 'F': 1, 'u': 1, 'w': 1 };
        var hooked = 0;
        for (var i = 0; i < ms.length; i++) {
            var nm = ms[i].getName();
            if (!want[nm]) continue;
            var pts = ms[i].getParameterTypes();
            var names = []; for (var j = 0; j < pts.length; j++) names.push(pts[j].getName());
            (function (mName, paramNames) {
                try {
                    var ov = paramNames.length
                        ? WY[mName].overload.apply(WY[mName], paramNames)
                        : WY[mName].overload();
                    ov.implementation = function () {
                        var a = Array.prototype.slice.call(arguments);
                        log('write', 'wy4.a.' + mName + '(' + paramNames.join(',') + ') args=' + a.join(','));
                        if (mName === 'E' || mName === 'F') printStack('wy4-' + mName, 6);
                        return this[mName].apply(this, a);
                    };
                    hooked++;
                } catch (e2) { log('warn', 'wy4.a.' + mName + ' overload fail: ' + e2); }
            })(nm, names);
        }
        log('ok', 'wy4.a hooked methods=' + hooked);
    } catch (e) { log('fail', 'wy4.a: ' + e + ' (可能尚未加载，发位置后看 scan)'); }

    // ── 3. Intent.getDoubleExtra：抓 kwebmap_slat / kwebmap_lng ───────
    try {
        var Intent = Java.use('android.content.Intent');
        Intent.getDoubleExtra.overload('java.lang.String', 'double').implementation = function (key, def) {
            var v = this.getDoubleExtra(key, def);
            if (key && key.toLowerCase().indexOf('webmap') >= 0) {
                log('intent', 'getDoubleExtra("' + key + '")=' + v);
            }
            return v;
        };
        log('ok', 'Intent.getDoubleExtra hooked');
    } catch (e) { log('fail', 'Intent.getDoubleExtra: ' + e); }

    // ── 4. 聊天发位置入口 mg.t0() ────────────────────────────────────
    try {
        var MG = Java.use('com.tencent.mm.ui.chatting.component.mg');
        MG.t0.overload().implementation = function () {
            log('entry', 'mg.t0() 发位置入口触发 → 即将开图');
            var r = this.t0();
            setTimeout(function () { scanLocClasses('afterEntry'); }, 1500);
            return r;
        };
        log('ok', 'mg.t0 hooked');
    } catch (e) { log('fail', 'mg.t0: ' + e); }

    // ── 5. q2.F(String,String)：选点确认发送（类可能开图后才加载）──────
    //   懒绑定：开图扫描后若 q2 已加载再尝试 hook。
    function tryHookQ2() {
        try {
            var Q2 = Java.use('com.tencent.mm.plugin.location.ui.impl.q2');
            var ms2 = Q2.class.getDeclaredMethods();
            var hooked = 0;
            for (var i = 0; i < ms2.length; i++) {
                if (ms2[i].getName() !== 'F') continue;
                var pts = ms2[i].getParameterTypes();
                var names = []; for (var j = 0; j < pts.length; j++) names.push(pts[j].getName());
                (function (paramNames) {
                    try {
                        var ov = Q2.F.overload.apply(Q2.F, paramNames);
                        ov.implementation = function () {
                            var a = Array.prototype.slice.call(arguments);
                            log('q2F', 'q2.F(' + paramNames.join(',') + ') args=' + a.join(' | '));
                            printStack('q2F', 6);
                            return this.F.apply(this, a);
                        };
                        hooked++;
                    } catch (e2) { log('warn', 'q2.F overload fail: ' + e2); }
                })(names);
            }
            log('ok', 'q2.F hooked overloads=' + hooked);
            return hooked > 0;
        } catch (e) { return false; }
    }
    if (!tryHookQ2()) {
        log('info', 'q2 未加载，开图后自动重试（mg.t0 后 2s）');
        setTimeout(tryHookQ2, 3500);
    }

    log('init', '=== 探针就绪：聊天→＋→位置→发送位置→选点→点发送，看 [LOC:*] ===');
});
