/**
 * scan_tlo_safe.js — 只扫 TimeLineObject 的 List 方法，超安全版
 * frida -U -n com.tencent.mm -l scan_tlo_safe.js
 * 
 * 进朋友圈（密友赞评可见）后再跑，等 2 秒自动输出。
 */
'use strict';
Java.perform(function () {
    setTimeout(function () {
        var found = 0;
        Java.choose('la4.p', {
            onMatch: function (inst) {
                if (found > 0) return;   // 只看第 1 个实例
                found++;
                try {
                    var tlo = inst.h1();
                    if (!tlo) { console.log('[TLO] h1() returned null'); return; }
                    console.log('[TLO] class=' + tlo.class.getName());

                    // 打全部无参方法名+返回类型
                    var methods = tlo.class.getMethods();
                    for (var i = 0; i < methods.length; i++) {
                        try {
                            var m = methods[i];
                            if (m.getParameterTypes().length !== 0) continue;
                            var rn = m.getReturnType().getSimpleName();
                            var mn = m.getName();
                            // 重点：含 Like / Comment / User 关键词，或返回 List
                            var isKey = mn.indexOf('Like') >= 0 || mn.indexOf('Comment') >= 0
                                     || mn.indexOf('User') >= 0 || mn.indexOf('like') >= 0
                                     || mn.indexOf('comment') >= 0 || mn.indexOf('user') >= 0
                                     || rn.indexOf('List') >= 0 || rn === 'LinkedList'
                                     || rn === 'ArrayList' || rn === 'Collection';
                            if (isKey) console.log('[TLO]   ' + mn + '() → ' + rn);
                        } catch(e) {}
                    }

                    // 试调所有返回 List 的方法，打 size 和第一个元素字段
                    for (var i = 0; i < methods.length; i++) {
                        try {
                            var m = methods[i];
                            if (m.getParameterTypes().length !== 0) continue;
                            var rn = m.getReturnType().getName();
                            var isListType = rn.indexOf('List') >= 0
                                         || rn === 'java.util.LinkedList'
                                         || rn === 'java.util.ArrayList'
                                         || rn === 'java.util.Collection';
                            if (!isListType) continue;
                            var result = m.invoke(tlo, []);
                            if (!result) continue;
                            var sz = result.size();
                            if (sz === 0) {
                                console.log('[TLO]   ' + m.getName() + '() size=0');
                                continue;
                            }
                            console.log('[TLO] ★ ' + m.getName() + '() size=' + sz);
                            // 第一个元素字段
                            var elem = result.get(0);
                            if (!elem) continue;
                            console.log('[TLO]   elem=' + elem.class.getName());
                            var ecls = elem.class;
                            for (var d = 0; d < 4 && ecls; d++) {
                                var fields = ecls.getDeclaredFields();
                                for (var fi = 0; fi < fields.length; fi++) {
                                    try {
                                        var f = fields[fi];
                                        f.setAccessible(true);
                                        var v = f.get(elem);
                                        if (v !== null) {
                                            var vn = v.class.getName();
                                            if (vn === 'java.lang.String'
                                                    || vn === 'java.lang.Integer'
                                                    || vn === 'java.lang.Long'
                                                    || vn === 'java.lang.Boolean') {
                                                console.log('[TLO]     .' + f.getName() + '=' + v);
                                            }
                                        }
                                    } catch(e2) {}
                                }
                                ecls = ecls.getSuperclass();
                            }
                        } catch(e) {}
                    }
                } catch(e) {
                    console.log('[TLO] err: ' + e);
                }
            },
            onComplete: function () {
                console.log('[TLO] done, scanned ' + found + ' instance(s)');
            }
        });
    }, 2000);
});
