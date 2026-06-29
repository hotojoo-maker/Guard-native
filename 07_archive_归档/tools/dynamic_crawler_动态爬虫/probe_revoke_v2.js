'use strict';
/**
 * probe_revoke_v2.js — 防撤回探针 v2（无超时，String.equals 直钩）
 * 用法: frida -U -p <pid> -l probe_revoke_v2.js
 */

Java.perform(function () {
    var Exception = Java.use('java.lang.Exception');
    var Log = Java.use('android.util.Log');

    // 层 A：String.equals — 直接捕获 "revokemsg" 比较（比 TextUtils.equals 更底层）
    try {
        var JavaString = Java.use('java.lang.String');
        JavaString.equals.implementation = function (other) {
            var result = this.equals(other);
            if ('revokemsg' === this.toString() || 'revokemsg' === String(other)) {
                var stk = Log.getStackTraceString(Exception.$new('probe'));
                console.log('[REVOKE:EQ] this="' + this + '" other="' + other + '"');
                console.log(stk.split('\n').slice(0, 15).join('\n'));
            }
            return result;
        };
        console.log('[probe] String.equals OK');
    } catch (e) {
        console.log('[probe] String.equals FAIL: ' + e);
    }

    // 层 B：String.contains("revoke") 宽网
    try {
        var JavaString2 = Java.use('java.lang.String');
        JavaString2.contains.implementation = function (seq) {
            var result = this.contains(seq);
            if (seq && seq.toString().indexOf('revoke') >= 0 && this.length() < 50) {
                console.log('[REVOKE:CTN] "' + this.toString() + '".contains("' + seq + '")=' + result);
                console.log(Log.getStackTraceString(Exception.$new('probe')).split('\n').slice(0, 12).join('\n'));
            }
            return result;
        };
        console.log('[probe] String.contains OK');
    } catch (e) {
        console.log('[probe] String.contains FAIL: ' + e);
    }

    console.log('[probe] READY — 2分钟窗口，发消息→撤回即可');
});
