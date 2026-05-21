/**
 * scan_la4p_heap.js
 * 
 * 朋友圈赞评数据定位 — 内存堆扫描版
 * 
 * 目的：在朋友圈已渲染（赞评气泡可见）之后，枚举所有 la4.p 活实例，
 *       逐个调各 getter / 读字段，找出谁返回非空的赞评 List。
 * 
 * 用法：
 *   1. 进朋友圈，等密友赞评气泡出现
 *   2. adb shell "am instrument ..." 或直接：
 *      frida -U -n com.tencent.mm -l scan_la4p_heap.js
 *   3. 控制台输出分析
 */

'use strict';

Java.perform(function () {
    var TAG = '[HEAP]';
    var TARGET_CLASS = 'la4.p';

    // 候选 getter 名（TimeLineObject / SnsObject 路径）
    var GETTERS_TO_TRY = [
        'h1', 'b1', 'c1', 'f1', 'g1', 'i1', 'j1', 'k1', 'l1', 'm1',
        'getTimeLineObject', 'getSnsObject', 'getPostInfo'
    ];

    // TimeLineObject 上找赞评 List 的关键词
    var LIST_KEYWORDS = ['like', 'Like', 'comment', 'Comment', 'user', 'User'];

    function dumpListMethods(obj, label) {
        if (!obj) return;
        var cls = obj.class;
        var found = [];
        try {
            var methods = cls.getMethods();
            for (var i = 0; i < methods.length; i++) {
                var m = methods[i];
                if (m.getParameterTypes().length !== 0) continue;
                var rn = m.getReturnType().getName();
                // 找返回 Collection / List 的
                if (rn.indexOf('List') >= 0 || rn.indexOf('Collection') >= 0
                        || rn === 'java.util.LinkedList' || rn === 'java.util.ArrayList') {
                    found.push(m.getName() + '→' + m.getReturnType().getSimpleName());
                }
            }
            // 也找含 Like/Comment 的所有方法
            for (var i = 0; i < methods.length; i++) {
                var m = methods[i];
                var mn = m.getName();
                var hasKw = LIST_KEYWORDS.some(function(kw){ return mn.indexOf(kw) >= 0; });
                if (hasKw && m.getParameterTypes().length === 0) {
                    found.push('KW:' + mn + '→' + m.getReturnType().getSimpleName());
                }
            }
        } catch(e) {}
        if (found.length > 0) {
            console.log(TAG + ' ' + label + ' class=' + cls.getName());
            console.log(TAG + '   ' + found.join(' | '));
        }
    }

    function inspectList(list, label) {
        if (!list || list.size() === 0) return;
        console.log(TAG + ' ' + label + ' size=' + list.size());
        var elem = list.get(0);
        if (!elem) return;
        console.log(TAG + '   elem=' + elem.class.getName());
        // 打第一个元素的所有基础类型字段
        var cls = elem.class;
        for (var d = 0; d < 5 && cls; d++) {
            var fields = cls.getDeclaredFields();
            for (var fi = 0; fi < fields.length; fi++) {
                var f = fields[fi];
                try {
                    f.setAccessible(true);
                    var v = f.get(elem);
                    if (v !== null && (typeof v === 'string'
                            || v.class.getName() === 'java.lang.String'
                            || v.class.getName() === 'java.lang.Integer'
                            || v.class.getName() === 'java.lang.Long'
                            || v.class.getName() === 'java.lang.Boolean')) {
                        console.log(TAG + '   .' + f.getName() + '=' + v);
                    }
                } catch(e) {}
            }
            cls = cls.getSuperclass ? cls.getSuperclass() : null;
        }
    }

    function scanInstance(inst, idx) {
        // 1. 先在 la4.p 本身找 List getter
        var la4pMethods = inst.class.getMethods();
        for (var i = 0; i < la4pMethods.length; i++) {
            var m = la4pMethods[i];
            if (m.getParameterTypes().length !== 0) continue;
            var rn = m.getReturnType().getName();
            if (rn.indexOf('List') < 0 && rn !== 'java.util.LinkedList'
                    && rn !== 'java.util.ArrayList') continue;
            try {
                var result = m.invoke(inst, []);
                if (result && result.size && result.size() > 0) {
                    inspectList(result, 'la4p[' + idx + '].' + m.getName() + '()');
                }
            } catch(e) {}
        }

        // 2. 尝试各 getter 获取 TimeLineObject / SnsObject
        for (var gi = 0; gi < GETTERS_TO_TRY.length; gi++) {
            var gn = GETTERS_TO_TRY[gi];
            try {
                var child = inst[gn]();
                if (!child) continue;
                var childCls = child.class.getName();
                // 找到一个微信内部对象
                if (childCls.startsWith('java.') || childCls.startsWith('android.')) continue;

                console.log(TAG + ' la4p[' + idx + '].' + gn + '() → ' + childCls);
                dumpListMethods(child, 'la4p[' + idx + '].' + gn);

                // 尝试调子对象上所有返回 List 的方法
                var childMethods = child.class.getMethods();
                for (var ci = 0; ci < childMethods.length; ci++) {
                    var cm = childMethods[ci];
                    if (cm.getParameterTypes().length !== 0) continue;
                    var crn = cm.getReturnType().getName();
                    if (crn.indexOf('List') < 0 && crn !== 'java.util.LinkedList'
                            && crn !== 'java.util.ArrayList') continue;
                    try {
                        var cres = cm.invoke(child, []);
                        if (cres && cres.size && cres.size() > 0) {
                            inspectList(cres, 'la4p[' + idx + '].' + gn + '().' + cm.getName() + '()');
                        }
                    } catch(e) {}
                }
            } catch(e) {}
        }
    }

    // 延迟 2 秒执行，等朋友圈完全渲染
    setTimeout(function () {
        console.log(TAG + ' === 开始 la4.p 堆枚举 ===');
        var count = 0;
        try {
            Java.choose(TARGET_CLASS, {
                onMatch: function (inst) {
                    if (count >= 30) return; // 最多扫 30 个实例
                    scanInstance(inst, count);
                    count++;
                },
                onComplete: function () {
                    console.log(TAG + ' === 扫描完成，共 ' + count + ' 个实例 ===');
                }
            });
        } catch (e) {
            console.log(TAG + ' Java.choose err: ' + e);
        }
    }, 2000);
});
