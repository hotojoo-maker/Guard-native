'use strict';
/**
 * probe_revoke_light.js — 防撤回探针（精简版，无类扫描，秒挂载）
 * 用法: frida -U -p <pid> -l probe_revoke_light.js
 * 产出: [REVOKE:TEQ] 行 → 填 AntiRecall.java
 */

Java.perform(function () {
    // 层 A：TextUtils.equals — 捕获 "revokemsg" 字符串比较
    try {
        var TextUtils = Java.use('android.text.TextUtils');
        var Exception = Java.use('java.lang.Exception');
        var Log = Java.use('android.util.Log');

        TextUtils.equals.implementation = function (a, b) {
            var sa = a ? a.toString() : '';
            var sb = b ? b.toString() : '';
            if (sa === 'revokemsg' || sb === 'revokemsg') {
                var stk = Log.getStackTraceString(Exception.$new('probe'));
                var lines = stk.split('\n').slice(0, 15).join('\n');
                console.log('[REVOKE:TEQ] a=' + sa + ' b=' + sb);
                console.log('[REVOKE:STK]\n' + lines);
            }
            return this.equals(a, b);
        };
        console.log('[probe] TextUtils.equals OK');
    } catch (e) {
        console.log('[probe] TextUtils.equals FAIL: ' + e);
    }

    // 层 B：String.contains("revoke") — 30s 自动摘钩
    try {
        var JavaString = Java.use('java.lang.String');
        var Exception2 = Java.use('java.lang.Exception');
        var Log2 = Java.use('android.util.Log');

        JavaString.contains.implementation = function (seq) {
            var result = this.contains(seq);
            if (seq && seq.toString().indexOf('revoke') >= 0 && this.length() < 40) {
                var stk = Log2.getStackTraceString(Exception2.$new('probe'));
                var lines = stk.split('\n').slice(0, 12).join('\n');
                console.log('[REVOKE:STR] "' + this.toString() + '".contains("' + seq + '")=' + result);
                console.log('[REVOKE:STK2]\n' + lines);
            }
            return result;
        };
        console.log('[probe] String.contains OK');

        setTimeout(function () {
            try { JavaString.contains.implementation = null; console.log('[probe] String.contains removed'); }
            catch (ex) {}
        }, 30000);
    } catch (e) {
        console.log('[probe] String.contains FAIL: ' + e);
    }

    console.log('[probe] READY. 让对方发一条消息再撤回，看 [REVOKE:TEQ] 行');
});
