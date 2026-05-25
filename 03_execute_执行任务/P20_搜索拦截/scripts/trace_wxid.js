/**
 * trace_wxid.js — 搜索瞬间追 wxid 经过的代码路径
 *
 * 策略：hook String.equals / String.contains / HashMap.get / ArrayList.addAll
 *       只要参数/返回值碰了 wxid_lzd2va16jd1622，就打印完整 Java call stack。
 *       这样一次搜索就能看到 wxid 经过的所有类和方法。
 *
 * 用法：
 *   frida -U -f com.tencent.mm -l trace_wxid.js
 *   然后进微信 → 点放大镜 → 搜 hello → 点聊天记录 tab
 *   看 [TRACE:wxid] 输出
 */

'use strict';

const TARGET_WXID = 'wxid_lzd2va16jd1622';
const TAG = '[TRACE:wxid]';

// 去噪：跳过这些方法（太多噪声）
const SKIP_METHODS = [
    'com.ghost.', 'de.robv.',
    'dalvik.', 'libcore.',
];

function skipFrame(frame) {
    const s = frame.toString();
    for (let i = 0; i < SKIP_METHODS.length; i++) {
        if (s.includes(SKIP_METHODS[i])) return true;
    }
    return false;
}

function printStack(label) {
    const stack = Java.use('java.lang.Thread').currentThread().getStackTrace();
    const lines = [];
    for (let i = 0; i < Math.min(stack.length, 25); i++) {
        const s = stack[i].toString();
        if (!skipFrame(stack[i])) {
            lines.push('    ' + s);
        }
    }
    console.log(TAG + ' ' + label + '\n' + lines.join('\n'));
}

Java.perform(function () {

    // ── 1. String.equals — 最精准：只抓 wxid 字符串被比较的瞬间 ──
    const String = Java.use('java.lang.String');
    String.equals.overload('java.lang.Object').implementation = function (obj) {
        const result = this.equals(obj);
        try {
            if (obj !== null) {
                const s = obj.toString();
                if (s === TARGET_WXID || (typeof s === 'string' && s.includes(TARGET_WXID))) {
                    printStack('String.equals(TARGET)');
                }
            }
            if (this.toString().includes(TARGET_WXID)) {
                // 只打一次：当 this 本身就是 wxid 时
            }
        } catch (e) {}
        return result;
    };

    // ── 2. HashMap.containsKey / get — wxid 被查哈希表 ──
    const HashMap = Java.use('java.util.HashMap');
    HashMap.containsKey.overload('java.lang.Object').implementation = function (key) {
        const result = this.containsKey(key);
        try {
            if (key !== null) {
                const s = key.toString();
                if (s === TARGET_WXID || s.includes(TARGET_WXID)) {
                    printStack('HashMap.containsKey(wxid)');
                }
            }
        } catch (e) {}
        return result;
    };

    HashMap.get.overload('java.lang.Object').implementation = function (key) {
        const result = this.get(key);
        try {
            if (key !== null) {
                const s = key.toString();
                if (s === TARGET_WXID || s.includes(TARGET_WXID)) {
                    printStack('HashMap.get(wxid)');
                }
            }
        } catch (e) {}
        return result;
    };

    console.log(TAG + ' hooks ready — search now');
});
