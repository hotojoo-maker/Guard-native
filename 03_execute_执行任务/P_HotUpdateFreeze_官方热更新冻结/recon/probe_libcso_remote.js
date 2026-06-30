/*
 * probe_libcso_remote.js — 只读探针：定位 libcso「远程更新段」(G3)
 *
 * 目标：把「本地 SO 加载(base.apk，放行)」和「远程补丁(<dataDir>/cso，要冻)」当场分开。
 * 纪律：只读 observe —— 全部调原方法、原样返回，不改不拦（不踩 F-23 干预）。
 *
 * 抓什么：
 *   1) CsoLoader.executeByName/executeByPath  → 每次加载的 名字/路径 + ExecuteResult(level)
 *      level 含 LEVEL_2 / LEVEL_1_DOUBLE 这类 = 打了补丁（远程嫌疑）；普通 LEVEL_1 = 本地。
 *   2) CsoLoader.nativeInitialize             → basePath = cso 缓存根目录（<dataDir>/cso）
 *   3) CsoLoader.preloadAllInternal           → 预载入口（早期，warm 可能漏）
 *   4) FileOutputStream 写路径含 /cso/         → 远程补丁「下载落盘点」
 *
 * 跑法（研究线坑：frida17 走 stdout + 必带 Java 桥 + warm-attach 真人冷启后的 pid）：
 *   adb -s 609b4b18 shell pidof com.tencent.mm
 *   frida -D 609b4b18 -p <pid> -l probe_libcso_remote.js
 *   （或用研究线 脚本/frida_cli_runner.py 保活 stdin）
 *
 * 配合目录监控（另开一个终端，需 root）：
 *   adb -s 609b4b18 shell su -c "ls -laR /data/data/com.tencent.mm/cso"
 */
'use strict';

var TAG = '[CSO-PROBE]';
function ts() { return new Date().toISOString(); }

Java.perform(function () {
    // ── 1+2+3) CsoLoader ────────────────────────────────────────────────────
    try {
        var CsoLoader = Java.use('com.tencent.cso.CsoLoader');

        ['executeByName', 'executeByPath'].forEach(function (mn) {
            try {
                var fn = CsoLoader[mn];
                if (!fn) { console.log(TAG, mn, 'not present'); return; }
                fn.overloads.forEach(function (ov) {
                    ov.implementation = function () {
                        var a0 = arguments.length > 0 ? ('' + arguments[0]) : '';
                        var ret = ov.apply(this, arguments);
                        var rs = '';
                        try { rs = '' + ret; } catch (e) { rs = '<ret?>'; }
                        var remote = (a0.indexOf('/cso') >= 0 && a0.indexOf('base.apk') < 0)
                                  || (rs.indexOf('LEVEL_2') >= 0) || (rs.indexOf('DOUBLE') >= 0);
                        console.log(TAG, ts(), mn, 'arg=' + a0, 'result=' + rs,
                                    remote ? '  <<< REMOTE-PATCH 嫌疑' : '');
                        return ret;
                    };
                });
                console.log(TAG, 'hooked', mn, '(' + fn.overloads.length + ' overloads)');
            } catch (e) { console.log(TAG, 'hook ' + mn + ' fail', e); }
        });

        try {
            CsoLoader.nativeInitialize.overloads.forEach(function (ov) {
                ov.implementation = function () {
                    try {
                        // 第二个参数是 basePath（<dataDir>/cso），见 jadx CsoLoader.d()
                        var bp = arguments.length > 1 ? ('' + arguments[1]) : '?';
                        console.log(TAG, ts(), 'nativeInitialize basePath=' + bp);
                    } catch (e) {}
                    return ov.apply(this, arguments);
                };
            });
            console.log(TAG, 'hooked nativeInitialize');
        } catch (e) { console.log(TAG, 'hook nativeInitialize fail', e); }

        try {
            CsoLoader.preloadAllInternal.overloads.forEach(function (ov) {
                ov.implementation = function () {
                    console.log(TAG, ts(), 'preloadAllInternal called');
                    return ov.apply(this, arguments);
                };
            });
            console.log(TAG, 'hooked preloadAllInternal');
        } catch (e) { console.log(TAG, 'hook preloadAllInternal fail', e); }

    } catch (e) {
        console.log(TAG, 'CsoLoader not found (类加载器/混淆变了?)', e);
    }

    // ── 4) FileOutputStream 写 /cso/ = 下载落盘 ───────────────────────────────
    try {
        var FOS = Java.use('java.io.FileOutputStream');
        var hookFosFile = function (f) {
            try { var p = '' + f.getAbsolutePath(); if (p.indexOf('/cso') >= 0) console.log(TAG, ts(), 'WRITE(file) -> ' + p); } catch (e) {}
        };
        FOS.$init.overload('java.io.File').implementation = function (f) { hookFosFile(f); return this.$init(f); };
        FOS.$init.overload('java.io.File', 'boolean').implementation = function (f, b) { hookFosFile(f); return this.$init(f, b); };
        FOS.$init.overload('java.lang.String').implementation = function (s) { try { if (('' + s).indexOf('/cso') >= 0) console.log(TAG, ts(), 'WRITE(str) -> ' + s); } catch (e) {} return this.$init(s); };
        FOS.$init.overload('java.lang.String', 'boolean').implementation = function (s, b) { try { if (('' + s).indexOf('/cso') >= 0) console.log(TAG, ts(), 'WRITE(str) -> ' + s); } catch (e) {} return this.$init(s, b); };
        console.log(TAG, 'hooked FileOutputStream(/cso/)');
    } catch (e) { console.log(TAG, 'FOS hook fail', e); }

    console.log(TAG, 'probe ready (observe-only). 触发：新装冷启 / 登录 / 放置一阵让它查更。');
});
