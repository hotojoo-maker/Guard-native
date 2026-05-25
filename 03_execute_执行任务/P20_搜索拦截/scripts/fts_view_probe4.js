/**
 * fts_view_probe4.js — hook f0.getView (ListView adapter) + FTSBaseMainUI
 * 确认 f0 是否是 FTS 搜索结果的 Adapter
 */

'use strict';

const TAG = '[FVP4]';

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

let gvCount = 0;
const MAX_GV = 10;

Java.perform(function () {

    // ── f0.getView(int, View, ViewGroup) — 核心 Adapter 方法 ──
    try {
        const f0 = Java.use('com.tencent.mm.plugin.fts.ui.f0');
        f0.getView.overload('int', 'android.view.View', 'android.view.ViewGroup').implementation = function (pos, convertView, parent) {
            const result = f0.getView.call(this, pos, convertView, parent);
            gvCount++;
            if (gvCount <= MAX_GV) {
                const resultCls = result ? result.getClass().getName() : 'null';
                console.log(TAG + ' [f0.getView] #' + gvCount + ' pos=' + pos + ' result=' + resultCls);

                // Try to get the data item at this position (if f0 has getItem)
                try {
                    const item = this.getItem(pos);
                    if (item) {
                        const itemCls = item.getClass().getName();
                        const fields = dumpAll(item, 1);
                        const wxids = findWxids(fields);
                        console.log(TAG + '   item=' + itemCls);
                        console.log(TAG + '   fields=' + JSON.stringify(fields).substring(0, 500));
                        if (wxids.length > 0) console.log(TAG + '   ★ WXID: ' + JSON.stringify(wxids));
                    }
                } catch (e) {
                    console.log(TAG + '   getItem fail: ' + e.message);
                }
            }
            return result;
        };
        console.log(TAG + ' f0.getView(int,View,ViewGroup) hooked ✓');
    } catch (e) {
        console.log(TAG + ' f0.getView fail: ' + e);
    }

    // ── Also hook f0.j (data bind) in case it fires now ──
    try {
        const f0 = Java.use('com.tencent.mm.plugin.fts.ui.f0');
        f0.j.overload('android.view.View', 'jz2.g', 'boolean').implementation = function (view, data, flag) {
            const dataCls = data ? data.getClass().getName() : 'null';
            const fields = dumpAll(data, 1);
            const wxids = findWxids(fields);
            console.log(TAG + ' [f0.j] data=' + dataCls + ' fields=' + JSON.stringify(fields).substring(0, 400));
            if (wxids.length > 0) console.log(TAG + '   ★ WXID: ' + JSON.stringify(wxids));
            return this.j(view, data, flag);
        };
        console.log(TAG + ' f0.j hooked ✓');
    } catch (e) {
        console.log(TAG + ' f0.j fail: ' + e);
    }

    // ── FTSBaseMainUI.m5(View, g, boolean) ──
    try {
        const FTSBase = Java.use('com.tencent.mm.plugin.fts.ui.FTSBaseMainUI');
        const m5ol = FTSBase.m5.overloads;
        for (let i = 0; i < m5ol.length; i++) {
            const args = m5ol[i].argumentTypes;
            if (args.length === 3) {
                const sig = [];
                for (let j = 0; j < args.length; j++) sig.push(args[j].getName());
                console.log(TAG + ' FTSBaseMainUI.m5 found: ' + sig.join(','));
                m5ol[i].implementation = function (view, data, flag) {
                    const dataCls = data ? data.getClass().getName() : 'null';
                    const fields = dumpAll(data, 1);
                    const wxids = findWxids(fields);
                    console.log(TAG + ' [FTSBase.m5] data=' + dataCls);
                    console.log(TAG + '   fields=' + JSON.stringify(fields).substring(0, 400));
                    if (wxids.length > 0) console.log(TAG + '   ★ WXID: ' + JSON.stringify(wxids));
                    return m5ol[i].call(this, view, data, flag);
                };
                console.log(TAG + ' FTSBaseMainUI.m5 hooked ✓');
            }
        }
    } catch (e) {
        console.log(TAG + ' FTSBaseMainUI.m5 fail: ' + e);
    }

    console.log(TAG + ' ── READY, search now ──');
});
