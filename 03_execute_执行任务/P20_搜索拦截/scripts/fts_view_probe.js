/**
 * fts_view_probe.js — Frida 枚举 RecyclerView Adapter，hook onBindViewHolder 找 wxid
 *
 * 策略：枚举已加载类 → 找 RecyclerView$Adapter 子类 → hook onBindViewHolder
 *       dump 传入的 data object 字段，找 wxid 踪迹
 *
 * 用法：
 *   1. 用户先进微信 FTS 搜索页
 *   2. frida -U -p <PID> -l fts_view_probe.js
 *   3. 搜索 hello / 点聊天记录 tab
 *   4. 看 [FVP] 日志
 */

'use strict';

const TAG = '[FVP]';
const TARGET_WXID = 'wxid_lzd2va16jd1622';

function isWxidLike(s) {
    if (!s || typeof s !== 'string' || s.length < 4 || s.length > 80) return false;
    if (s.includes(' ') || s.includes('<') || s.includes('\n')) return false;
    return s.startsWith('wxid_') || s.startsWith('gh_') ||
           s.startsWith('filehelper') || s.startsWith('weixin') ||
           /^[a-zA-Z0-9_\-@\.]{5,60}$/.test(s);
}

// dump 对象所有字段（深度1）
function dumpFields(obj) {
    const out = {};
    if (!obj) return out;
    try {
        let c = obj.getClass();
        while (c && !c.getName().equals('java.lang.Object')) {
            const fields = c.getDeclaredFields();
            for (let i = 0; i < fields.length; i++) {
                const f = fields[i];
                try {
                    f.setAccessible(true);
                    const v = f.get(obj);
                    const name = f.getName();
                    if (v === null) { out[name] = null; continue; }
                    const vn = v.getClass().getName();
                    if (vn === 'java.lang.String') {
                        out[name] = v.toString();
                    } else if (vn === 'java.lang.Integer' || vn === 'java.lang.Long' || vn === 'java.lang.Boolean') {
                        out[name] = '[' + vn.split('.').pop() + ']' + v.toString();
                    } else if (!vn.startsWith('java.') && !vn.startsWith('android.')) {
                        // 递归一层
                        try {
                            const sub = {};
                            let sc = v.getClass();
                            while (sc && !sc.getName().equals('java.lang.Object')) {
                                const sfields = sc.getDeclaredFields();
                                for (let j = 0; j < sfields.length; j++) {
                                    const sf = sfields[j];
                                    try {
                                        sf.setAccessible(true);
                                        const sv = sf.get(v);
                                        if (sv === null) continue;
                                        const svn = sv.getClass().getName();
                                        if (svn === 'java.lang.String') {
                                            sub[sf.getName()] = sv.toString();
                                        } else if (svn === 'java.lang.Integer' || svn === 'java.lang.Long') {
                                            sub[sf.getName()] = '[' + svn.split('.').pop() + ']' + sv.toString();
                                        }
                                    } catch (e2) {}
                                }
                                try { sc = sc.getSuperclass(); } catch(e) { break; }
                            }
                            if (Object.keys(sub).length > 0) out[name] = sub;
                        } catch (e2) {}
                    }
                } catch (e) {}
            }
            try { c = c.getSuperclass(); } catch(e) { break; }
        }
    } catch (e) {}
    return out;
}

// 找所有 wxid-like 字段值
function findWxids(fields, prefix) {
    const hits = [];
    for (const k in fields) {
        const v = fields[k];
        if (typeof v === 'string') {
            if (isWxidLike(v) || v.includes('wxid_')) {
                hits.push(prefix + k + '=' + v.substring(0, 50));
            }
        } else if (typeof v === 'object' && v !== null) {
            hits.push.apply(hits, findWxids(v, prefix + k + '.'));
        }
    }
    return hits;
}

Java.perform(function () {

    // ── Step 1: 枚举所有 RecyclerView$Adapter 子类 ──
    console.log(TAG + ' enumerating RecyclerView Adapter subclasses...');

    let adapterClasses = [];
    Java.enumerateLoadedClasses({
        onMatch: function (name) {
            if (name.includes('Adapter') && name.length < 80) {
                adapterClasses.push(name);
            }
        },
        onComplete: function () {
            console.log(TAG + ' found ' + adapterClasses.length + ' potential adapter classes');

            // 筛选真正的 RecyclerView.Adapter 子类
            const realAdapters = [];
            adapterClasses.forEach(function (name) {
                try {
                    const c = Java.use(name);
                    // 检查是否有 onBindViewHolder 方法 (3-arg variant)
                    if (c.class.getDeclaredMethods) {
                        const methods = c.class.getDeclaredMethods();
                        for (let i = 0; i < methods.length; i++) {
                            const m = methods[i];
                            if (m.getName() === 'onBindViewHolder') {
                                realAdapters.push({name: name, paramCount: m.getParameterTypes().length});
                                break;
                            }
                        }
                    }
                } catch (e) {}
            });

            console.log(TAG + ' real Adapters with onBindViewHolder: ' + realAdapters.length);
            realAdapters.forEach(function (a) {
                console.log(TAG + '   ' + a.name + ' (' + a.paramCount + ' params)');
            });

            // ── Step 2: Hook 所有 2-param onBindViewHolder ──
            // Standard: onBindViewHolder(VH holder, int position)
            realAdapters.forEach(function (adapter) {
                try {
                    const AdapterClass = Java.use(adapter.name);
                    // Try 2-param overload
                    const overloads = AdapterClass.onBindViewHolder.overloads;
                    overloads.forEach(function (overload) {
                        const argTypes = overload.argumentTypes;
                        if (argTypes.length === 2) {
                            overload.implementation = function (holder, position) {
                                // Dump holder.itemView and holder data
                                try {
                                    const holderCls = holder.getClass().getName();
                                    console.log(TAG + ' [BIND] adapter=' + adapter.name +
                                                ' holder=' + holderCls + ' pos=' + position);

                                    // Try to find data field on holder
                                    const holderFields = dumpFields(holder);
                                    const wxids = findWxids(holderFields, 'holder.');
                                    if (wxids.length > 0) {
                                        console.log(TAG + ' [BIND] ★ WXID FOUND in holder!');
                                        console.log(TAG + '   wxids=' + JSON.stringify(wxids));
                                        console.log(TAG + '   full=' + JSON.stringify(holderFields).substring(0, 600));
                                    }
                                } catch (e) {
                                    console.log(TAG + ' [BIND] err: ' + e);
                                }
                                return overload.call(this, holder, position);
                            };
                            console.log(TAG + ' hooked ' + adapter.name + '.onBindViewHolder');
                        }
                    });
                } catch (e) {
                    console.log(TAG + ' fail hook ' + adapter.name + ': ' + e);
                }
            });

            console.log(TAG + ' ── hooks ready! search now ──');
        }
    });
});
