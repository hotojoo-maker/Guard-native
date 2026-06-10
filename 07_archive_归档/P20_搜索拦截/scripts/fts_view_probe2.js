/**
 * fts_view_probe2.js — 枚举 FTS/Search 相关类 + hook View.setVisibility
 *
 * 策略1：枚举所有含 fts/search/FTS 的类 → 找 (Object, View) 参数方法 → hook
 * 策略2：hook View.setVisibility → 搜 hello 时打印 caller（看哪些 View 在搜索结果渲染时变 VISIBLE）
 */

'use strict';

const TAG = '[FVP2]';
const TARGET_WXID = 'wxid_lzd2va16jd1622';

let inSearch = false;
let searchStartTime = 0;

Java.perform(function () {

    // ── Step 1: 枚举 FTS/Search 类 ──
    const ftsClasses = [];
    const viewMethodClasses = [];

    Java.enumerateLoadedClasses({
        onMatch: function (name) {
            const lower = name.toLowerCase();
            if (lower.includes('fts') || lower.includes('search') || lower.includes('search')) {
                ftsClasses.push(name);
            }
        },
        onComplete: function () {
            console.log(TAG + ' FTS/Search classes: ' + ftsClasses.length);
            // 打印前 50 个
            ftsClasses.sort();
            ftsClasses.slice(0, 50).forEach(function (n) { console.log(TAG + '   ' + n); });
            if (ftsClasses.length > 50) console.log(TAG + '   ... +' + (ftsClasses.length - 50) + ' more');

            // 找有 View 参数方法的类
            ftsClasses.forEach(function (name) {
                try {
                    const c = Java.use(name);
                    const methods = c.class.getDeclaredMethods();
                    for (let i = 0; i < methods.length; i++) {
                        const m = methods[i];
                        const params = m.getParameterTypes();
                        for (let j = 0; j < params.length; j++) {
                            const pName = params[j].getName();
                            if (pName.includes('View') || pName.includes('view')) {
                                let sig = m.getName() + '(';
                                for (let k = 0; k < params.length; k++) {
                                    if (k > 0) sig += ',';
                                    sig += params[k].getName().split('.').pop();
                                }
                                sig += ')';
                                viewMethodClasses.push({cls: name, method: m.getName(), sig: sig, paramCount: params.length});
                                break;
                            }
                        }
                    }
                } catch (e) {}
            });

            console.log(TAG + ' methods with View param: ' + viewMethodClasses.length);
            viewMethodClasses.forEach(function (m) {
                console.log(TAG + '   ' + m.cls + ' ' + m.sig);
            });

            // ── Step 2: Hook View.setVisibility ──
            try {
                const View = Java.use('android.view.View');
                View.setVisibility.implementation = function (visibility) {
                    const result = View.setVisibility.call(this, visibility);

                    // Only log when visibility is set to VISIBLE (0) in search context
                    if (visibility === 0) {
                        const cls = this.getClass().getName();
                        if (cls.toLowerCase().includes('fts') || cls.toLowerCase().includes('search') ||
                            cls.includes('fz2') || cls.includes('kc5') || cls.includes('jw1')) {
                            console.log(TAG + ' [setVis] cls=' + cls + ' VISIBLE');
                        }
                    }
                    return result;
                };
                console.log(TAG + ' View.setVisibility hooked');
            } catch (e) {
                console.log(TAG + ' View.setVisibility fail: ' + e);
            }

            // ── Step 3: Hook Activity.onResume for FTSMainUI ──
            try {
                const Activity = Java.use('android.app.Activity');
                Activity.onResume.implementation = function () {
                    const name = this.getClass().getName();
                    if (name.toLowerCase().includes('fts') || name.toLowerCase().includes('search')) {
                        console.log(TAG + ' [ACT] RESUME ' + name);
                    }
                    return Activity.onResume.call(this);
                };
                console.log(TAG + ' Activity.onResume hooked');
            } catch (e) {}

            console.log(TAG + ' ── ready! search hello now ──');
        }
    });
});
