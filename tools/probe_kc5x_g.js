/**
 * probe_kc5x_g.js — 探针：kc5.x.g() 冷启动 PATH3:B 会话单条 add 路径
 *
 * 背景：
 *   ConvFilter 已有 kc5.a.a(List) 扼流点 (La)，覆盖冷启动/热更新。
 *   但 [PATH3:B] = kc5.x.g() → ArrayList.add(单条) 路径在 H→V 切换后
 *   密友重新出现（0.3s 闪烁根因假设）。
 *
 * 本探针三件事：
 *   1. 确认 kc5.x.g() 存在 + 方法签名
 *   2. 看它调用的是 ArrayList.add 还是其他集合方法
 *   3. Dump kc5.x 的全部字段（找 MvvmList 引用字段名）
 *
 * 用法：attach 主进程
 *   $pid = adb shell "ps -ef | grep ' com.tencent.mm$'" | Select-String 'com.tencent.mm' | ForEach-Object { ($_ -split '\s+')[1] }
 *   frida -U -p $pid -l tools\probe_kc5x_g.js 2>&1 | tee tools\probe_kc5x_g_$(Get-Date -f 'HHmmss').log
 *
 * 操作：脚本加载后，切到会话列表刷新一次（收消息 / 切后台再切回来）。
 */
Java.perform(function () {
    'use strict';

    var TAG = 'KC5X';
    var hitCount = 0;

    function ts() {
        var d = new Date();
        var p = function (n) { return n < 10 ? '0' + n : '' + n; };
        return p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds());
    }
    function log(sub, msg) { console.log(ts() + ' [' + TAG + ':' + sub + '] ' + msg); }

    function stack(n) {
        try {
            var frames = Java.use('java.lang.Thread').currentThread().getStackTrace();
            var out = [];
            for (var i = 0; i < frames.length && out.length < (n || 6); i++) {
                var s = String(frames[i]);
                if (s.indexOf('java.lang.reflect') >= 0) continue;
                if (s.indexOf('dalvik.') >= 0) continue;
                out.push(s.trim());
            }
            return out.join(' ← ');
        } catch (e) { return '?'; }
    }

    // ─── 1. Dump kc5.x 全部字段（一次性，判断哪个字段持有 MvvmList/ArrayList）──────
    function dumpKc5xFields(instance) {
        try {
            var sb = '[KC5X:FIELDS cls=' + instance.getClass().getName() + ']';
            var cls = instance.getClass();
            var depth = 0;
            while (cls && !cls.equals(Java.use('java.lang.Object').class) && depth < 5) {
                var fields = cls.getDeclaredFields();
                for (var i = 0; i < fields.length; i++) {
                    var f = fields[i];
                    f.setAccessible(true);
                    try {
                        var v = f.get(instance);
                        if (v === null) continue;
                        var vt = v.getClass().getName();
                        sb += ' .' + f.getName() + '[' + vt.split('.').pop() + ']';
                        if (vt === 'java.util.ArrayList' || vt.indexOf('MvvmList') >= 0 ||
                            vt.indexOf('kc5') >= 0) {
                            sb += '(!)';
                        }
                    } catch (e2) {}
                }
                cls = cls.getSuperclass();
                depth++;
            }
            log('FIELDS', sb);
        } catch (e) {
            log('ERR', 'dumpFields: ' + e);
        }
    }

    var kc5xFieldsDumped = false;

    // ─── 2. Hook kc5.x — 所有方法，找 g() ──────────────────────────────────────────
    try {
        var Kc5x = Java.use('kc5.x');
        var methods = Kc5x.class.getDeclaredMethods();

        log('INIT', 'kc5.x 共 ' + methods.length + ' 个方法');

        // 打印全部方法签名（一次性侦察）
        for (var i = 0; i < methods.length; i++) {
            var m = methods[i];
            var ptypes = m.getParameterTypes();
            var pnames = [];
            for (var j = 0; j < ptypes.length; j++) pnames.push(ptypes[j].getName());
            log('SIG', m.getName() + '(' + pnames.join(', ') + ')→' + m.getReturnType().getName());
        }

        // 只 hook g() (0参 或 1参 均尝试)
        var gHooked = 0;
        for (var k = 0; k < methods.length; k++) {
            var m2 = methods[k];
            if (m2.getName() !== 'g') continue;
            var pt = m2.getParameterTypes();
            var pn = [];
            for (var l = 0; l < pt.length; l++) pn.push(pt[l].getName());

            (function (mname, paramNames) {
                try {
                    var ov = Kc5x[mname].overload.apply(Kc5x[mname], paramNames);
                    ov.implementation = function () {
                        hitCount++;
                        var args = Array.prototype.slice.call(arguments);

                        if (!kc5xFieldsDumped) {
                            kc5xFieldsDumped = true;
                            dumpKc5xFields(this);
                        }

                        if (hitCount <= 5) {
                            log('HIT', 'kc5.x.g(' + paramNames.join(',') + ')'
                                + (args.length > 0 ? ' arg0=' + args[0] : ''));
                            log('STK', stack(6));
                        } else if (hitCount === 6) {
                            log('HIT', 'kc5.x.g [...muted after 5...]');
                        }
                        return this[mname].apply(this, args);
                    };
                    gHooked++;
                    log('INIT', 'g(' + paramNames.join(',') + ') hooked');
                } catch (e2) {
                    log('WARN', 'g(' + paramNames.join(',') + ') overload fail: ' + e2);
                }
            })(m2.getName(), pn);
        }
        if (gHooked === 0) {
            log('WARN', 'kc5.x.g() not found — 列出全部方法签名见上方 SIG 行');
        }

    } catch (e) {
        log('ERR', 'kc5.x load fail: ' + e);
        log('INFO', '如果类不存在，请从 jadx 搜索 ArrayList.add + 会话数据类 kc5.y 的调用关系');
    }

    // ─── 3. 同时 hook ArrayList.add(Object) 限定 kc5.y 类型 ────────────────────────
    // 用于确认 kc5.x.g() 确实走 add 单条路径（不是 addAll）
    var addHitKc5 = 0;
    try {
        var ArrayList = Java.use('java.util.ArrayList');
        var origAdd = ArrayList.add.overload('java.lang.Object');
        origAdd.implementation = function (obj) {
            if (obj !== null) {
                var cn = obj.getClass().getName();
                if (cn === 'kc5.y' || cn === 'com.tencent.mm.ui.conversation.MainUI') {
                    addHitKc5++;
                    if (addHitKc5 <= 5) {
                        log('ADD', 'ArrayList.add(' + cn + ')');
                        log('ADD_STK', stack(6));
                    }
                }
            }
            return this.add(obj);
        };
        log('INIT', 'ArrayList.add(Object) probe installed (filter kc5.y only)');
    } catch (e) {
        log('ERR', 'ArrayList.add hook fail: ' + e);
    }

    log('INIT', '=== probe ready — 刷新会话列表触发 ===');
});
