/**
 * fts_view_probe3.js v2 — hook FTS ListView 数据绑定方法
 * 修正：直接用 jz2.g 类型
 */

'use strict';

const TAG = '[FVP3]';

function isWxidLike(s) {
    if (!s || typeof s !== 'string' || s.length < 4 || s.length > 80) return false;
    return s.startsWith('wxid_') || s.startsWith('gh_') ||
           s.startsWith('filehelper') || /^[a-zA-Z0-9_\-@\.]{5,60}$/.test(s);
}

function dumpAll(obj, depth) {
    if (!obj || depth > 2) return '(max depth)';
    const out = {};
    try {
        let c = obj.getClass();
        while (c && !c.getName().equals('java.lang.Object')) {
            const fields = c.getDeclaredFields();
            for (let i = 0; i < fields.length; i++) {
                const f = fields[i];
                try {
                    f.setAccessible(true);
                    const v = f.get(obj);
                    const nm = f.getName();
                    if (v === null) { out[nm] = null; continue; }
                    const vn = v.getClass().getName();
                    if (vn === 'java.lang.String') {
                        out[nm] = v.toString();
                    } else if (vn === 'java.lang.Integer' || vn === 'java.lang.Long' || vn === 'java.lang.Boolean') {
                        out[nm] = '[' + vn.split('.').pop() + ']' + v.toString();
                    } else if (depth < 2 && !vn.startsWith('java.') && !vn.startsWith('android.')) {
                        out[nm] = dumpAll(v, depth + 1);
                    }
                } catch (e2) {}
            }
            try { c = c.getSuperclass(); } catch(e) { break; }
        }
    } catch (e) {}
    return out;
}

function findWxids(fields) {
    const hits = [];
    for (const k in fields) {
        const v = fields[k];
        if (typeof v === 'string' && isWxidLike(v)) hits.push(k + '=' + v.substring(0, 50));
        if (typeof v === 'object' && v !== null) {
            const sub = findWxids(v);
            for (let i = 0; i < sub.length; i++) hits.push(k + '.' + sub[i]);
        }
    }
    return hits;
}

let bindCount = 0;
const MAX_BIND = 20;

Java.perform(function () {

    // f0.j(View, jz2.g, boolean) — 数据绑定
    try {
        const f0 = Java.use('com.tencent.mm.plugin.fts.ui.f0');
        f0.j.overload('android.view.View', 'jz2.g', 'boolean').implementation = function (view, data, flag) {
            bindCount++;
            if (bindCount <= MAX_BIND) {
                const dataFields = dumpAll(data, 1);
                const wxids = findWxids(dataFields);
                const viewCls = view ? view.getClass().getName() : 'null';
                const dataCls = data ? data.getClass().getName() : 'null';
                console.log(TAG + ' [f0.j] #' + bindCount + ' view=' + viewCls + ' data=' + dataCls);
                console.log(TAG + '   fields=' + JSON.stringify(dataFields).substring(0, 500));
                if (wxids.length > 0) {
                    console.log(TAG + '   ★ WXID: ' + JSON.stringify(wxids));
                }
            }
            return this.j(view, data, flag);
        };
        console.log(TAG + ' f0.j(View,jz2.g,boolean) hooked ✓');
    } catch (e) {
        console.log(TAG + ' f0.j fail: ' + e);
    }

    // w0.m5(View, jz2.g, boolean)
    try {
        const w0 = Java.use('com.tencent.mm.plugin.fts.ui.w0');
        w0.m5.overload('android.view.View', 'jz2.g', 'boolean').implementation = function (view, data, flag) {
            bindCount++;
            if (bindCount <= MAX_BIND) {
                const dataFields = dumpAll(data, 1);
                const wxids = findWxids(dataFields);
                const dataCls = data ? data.getClass().getName() : 'null';
                console.log(TAG + ' [w0.m5] #' + bindCount + ' data=' + dataCls);
                console.log(TAG + '   fields=' + JSON.stringify(dataFields).substring(0, 400));
                if (wxids.length > 0) {
                    console.log(TAG + '   ★ WXID: ' + JSON.stringify(wxids));
                }
            }
            return this.m5(view, data, flag);
        };
        console.log(TAG + ' w0.m5(View,jz2.g,boolean) hooked ✓');
    } catch (e) {
        console.log(TAG + ' w0.m5 fail: ' + e);
    }

    // q2.j — keep overloads iteration since it worked
    try {
        const q2 = Java.use('com.tencent.mm.plugin.fts.ui.q2');
        const overloads = q2.j.overloads;
        for (let i = 0; i < overloads.length; i++) {
            const args = overloads[i].argumentTypes;
            if (args.length === 3) {
                overloads[i].implementation = function (view, data, flag) {
                    bindCount++;
                    if (bindCount <= MAX_BIND) {
                        const dataCls = data ? data.getClass().getName() : 'null';
                        const dataFields = dumpAll(data, 1);
                        const wxids = findWxids(dataFields);
                        console.log(TAG + ' [q2.j] #' + bindCount + ' data=' + dataCls);
                        console.log(TAG + '   fields=' + JSON.stringify(dataFields).substring(0, 400));
                        if (wxids.length > 0) console.log(TAG + '   ★ WXID: ' + JSON.stringify(wxids));
                    }
                    return overloads[i].call(this, view, data, flag);
                };
                console.log(TAG + ' q2.j hooked ✓');
            }
        }
    } catch (e) {
        console.log(TAG + ' q2.j fail: ' + e);
    }

    console.log(TAG + ' ── READY, search now ──');
});
