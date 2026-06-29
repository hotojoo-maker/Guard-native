'use strict';
/**
 * probe_revoke_msg.js — 防撤回链路探针
 * 目标：发现 WeChat 8.0.71 处理 "revokemsg" 的具体类 + 方法签名
 *
 * 用法：
 *   frida -U -n com.tencent.mm -l probe_revoke_msg.js
 *
 * 操作步骤：
 *   1. attach 之后，让对方发一条消息
 *   2. 对方撤回该消息
 *   3. 看 logcat / Frida console 输出
 *
 * 期望输出（命中时）：
 *   [REVOKE:TEQ] TextUtils.equals "revokemsg" at <ClassName>.<method>
 *   [REVOKE:STK] ... call stack ...
 *   [REVOKE:MSG] msg fields: ...
 *
 * 产出：把「[REVOKE:TEQ]」行复制到 P23_AntiRecall/result.md
 *       填入 AntiRecall.java 的 WX_REVOKE_CLASS / WX_REVOKE_METHOD
 *
 * 版本：v1  2026-05-25
 */

Java.perform(function () {

    // ─────────────────────────────────────────────────────────────────────
    // 层 A：TextUtils.equals 监控 — 最轻量，捕获 "revokemsg" 比较时调用栈
    // ─────────────────────────────────────────────────────────────────────
    try {
        var TextUtils = Java.use('android.text.TextUtils');
        var Exception = Java.use('java.lang.Exception');
        var Log = Java.use('android.util.Log');

        TextUtils.equals.implementation = function (a, b) {
            var sa = a ? a.toString() : '';
            var sb = b ? b.toString() : '';
            if (sa === 'revokemsg' || sb === 'revokemsg') {
                var stk = Log.getStackTraceString(Exception.$new('probe'));
                // 只取前 15 帧，避免刷屏
                var lines = stk.split('\n').slice(0, 15).join('\n');
                console.log('[REVOKE:TEQ] a=' + sa + ' b=' + sb);
                console.log('[REVOKE:STK]\n' + lines);
            }
            return this.equals(a, b);
        };
        console.log('[probe] TextUtils.equals hook OK');
    } catch (e) {
        console.log('[probe] TextUtils.equals hook FAIL: ' + e);
    }

    // ─────────────────────────────────────────────────────────────────────
    // 层 B：String.contains("revoke") 宽网 — 捕获 "revokemsg" / "revoke_by" 等变体
    //        注意：String.contains 频率较高，30s 超时后自动摘钩
    // ─────────────────────────────────────────────────────────────────────
    var stringContainsHook = null;
    try {
        var JavaString = Java.use('java.lang.String');
        var Exception2 = Java.use('java.lang.Exception');
        var Log2 = Java.use('android.util.Log');

        stringContainsHook = JavaString.contains.implementation = function (seq) {
            var result = this.contains(seq);
            if (seq && seq.toString().indexOf('revoke') >= 0 && this.length() < 40) {
                var stk = Log2.getStackTraceString(Exception2.$new('probe'));
                var lines = stk.split('\n').slice(0, 12).join('\n');
                console.log('[REVOKE:STR] "' + this.toString() + '".contains("' + seq + '")=' + result);
                console.log('[REVOKE:STK2]\n' + lines);
            }
            return result;
        };
        console.log('[probe] String.contains hook OK');

        // 30s 后摘钩，避免 KPI 异常
        setTimeout(function () {
            try {
                JavaString.contains.implementation = null;
                console.log('[probe] String.contains hook removed (30s limit)');
            } catch (ex) { }
        }, 30000);
    } catch (e) {
        console.log('[probe] String.contains hook FAIL: ' + e);
    }

    // ─────────────────────────────────────────────────────────────────────
    // 层 C：扫描已加载类，找 "revokemsg" 常量持有者
    //        在 Java.perform 里 enumerateLoadedClasses，过滤 com.tencent.mm 类
    //        输出：持有该字符串常量的类列表（静态扫描补充）
    // ─────────────────────────────────────────────────────────────────────
    try {
        var found = [];
        Java.enumerateLoadedClasses({
            onMatch: function (name) {
                // 只扫微信包内的类，跳过太短（混淆）或已知无关的
                if (name.indexOf('com.tencent.mm') < 0) return;
                try {
                    var cls = Java.use(name);
                    var methods = cls.class.getDeclaredMethods();
                    for (var i = 0; i < methods.length; i++) {
                        var m = methods[i];
                        var params = m.getParameterTypes();
                        // 找签名 (String, Map, Object) 返回 boolean 的方法 ← Catfish 撤回入口特征
                        if (params.length === 3 &&
                            params[0].getName() === 'java.lang.String' &&
                            params[1].getName() === 'java.util.Map' &&
                            m.getReturnType().getName() === 'boolean') {
                            found.push(name + '#' + m.getName() + '(String, Map, Object)→bool');
                        }
                    }
                } catch (ex) { /* 跳过无法加载的类 */ }
            },
            onComplete: function () {
                console.log('[REVOKE:SCAN] (String,Map,Object)→bool candidates:');
                found.forEach(function (s) { console.log('  ' + s); });
                if (found.length === 0) {
                    console.log('[REVOKE:SCAN] no candidates — try layer A/B results');
                }
            }
        });
    } catch (e) {
        console.log('[probe] class scan FAIL: ' + e);
    }

    console.log('[probe] revoke_msg probe loaded. Trigger: let someone recall a message.');
});
