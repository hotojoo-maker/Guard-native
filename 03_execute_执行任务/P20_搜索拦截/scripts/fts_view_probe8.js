/**
 * fts_view_probe8.js — 最轻量：hook TextView.setText 找 wxid
 * 策略：搜索结果显示时，wxid 可能以文本或 tag 形式出现在 View 上
 */

'use strict';

const TAG = '[FVP8]';
const TARGET = 'wxid_lzd2va16jd1622';

let hitCount = 0;
const MAX_HIT = 5;

Java.perform(function () {

    // Hook TextView.setText(CharSequence)
    try {
        const TextView = Java.use('android.widget.TextView');
        TextView.setText.overload('java.lang.CharSequence').implementation = function (text) {
            if (hitCount < MAX_HIT && text !== null) {
                const s = text.toString();
                if (s.includes('wxid_') || s.includes('gh_') || s.includes(TARGET)) {
                    hitCount++;
                    // Print stack trace to find which FTS class called this
                    const stack = Java.use('java.lang.Thread').currentThread().getStackTrace();
                    const relevant = [];
                    for (let i = 0; i < Math.min(stack.length, 30); i++) {
                        const frame = stack[i].toString();
                        if (frame.includes('fts') || frame.includes('search') || frame.includes('plugin') ||
                            frame.includes('tz2') || frame.includes('jz2') || frame.includes('f0') ||
                            frame.includes('wxid')) {
                            relevant.push(frame);
                        }
                    }
                    console.log(TAG + ' [setText] wxid HIT! text="' + s.substring(0, 100) + '"');
                    if (relevant.length > 0) {
                        console.log(TAG + '   stack:');
                        relevant.forEach(function(f) { console.log(TAG + '     ' + f); });
                    } else {
                        // Print top 5 non-system frames
                        let printed = 0;
                        for (let i = 0; i < stack.length && printed < 5; i++) {
                            const f = stack[i].toString();
                            if (!f.includes('java.') && !f.includes('android.') && !f.includes('dalvik.') &&
                                !f.includes('de.robv.') && !f.includes('com.ghost.')) {
                                console.log(TAG + '     ' + f);
                                printed++;
                            }
                        }
                    }
                }
            }
            return this.setText(text);
        };
        console.log(TAG + ' TextView.setText hooked ✓');
    } catch (e) {
        console.log(TAG + ' TextView.setText fail: ' + e);
    }

    // Hook View.setTag — wxid might be stored as tag
    try {
        const View = Java.use('android.view.View');
        View.setTag.overload('java.lang.Object').implementation = function (tag) {
            if (hitCount < MAX_HIT && tag !== null) {
                const s = tag.toString();
                if (s.includes('wxid_') || s.includes(TARGET)) {
                    hitCount++;
                    const viewCls = this.getClass().getName();
                    console.log(TAG + ' [setTag] wxid HIT! view=' + viewCls + ' tag="' + s.substring(0, 100) + '"');
                }
            }
            return this.setTag(tag);
        };
        console.log(TAG + ' View.setTag hooked ✓');
    } catch (e) {
        console.log(TAG + ' View.setTag fail: ' + e);
    }

    console.log(TAG + ' ── READY ──');
});
