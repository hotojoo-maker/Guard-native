'use strict';
/**
 * probe_visibility_adapter.js — hook RecyclerView.setAdapter + ListView.setAdapter
 * 直接拦截 adapter 设置，dump 所有 item 的 wxid 字段
 */
Java.perform(function () {

    var HIDDEN_SET = { 'wxid_lzd2va16jd1622': true };
    var scanned = {};
    var TAG = '[VIS]';

    function isWxidLike(s) {
        return typeof s === 'string' && /^(wxid_[A-Za-z0-9_]+|[A-Za-z0-9_]+@chatroom)$/.test(s);
    }

    function dumpOne(obj) {
        var r = {};
        if (!obj) return r;
        try {
            var cls = obj.getClass();
            var n = 0;
            while (cls && cls.getName() !== 'java.lang.Object' && n < 25) {
                try {
                    var fs = cls.getDeclaredFields();
                    for (var i = 0; i < fs.length && n < 25; i++) {
                        try {
                            fs[i].setAccessible(true);
                            var v = fs[i].get(obj);
                            if (v !== null) {
                                var vs = String(v);
                                if (vs.length > 80) vs = vs.substring(0, 80);
                                r[fs[i].getName()] = vs;
                                n++;
                            }
                        } catch(e) {}
                    }
                } catch(e) {}
                cls = cls.getSuperclass();
            }
        } catch(e) {}
        return r;
    }

    function scanAdapter(adapter) {
        if (!adapter) return;
        var clsName = adapter.getClass().getName();
        if (scanned[clsName]) return;
        scanned[clsName] = true;

        console.log(TAG + ' adapter=' + clsName);
        try {
            var count = 0;
            try { count = adapter.getItemCount ? adapter.getItemCount() : adapter.getCount(); } catch(e) {}
            console.log(TAG + ' count=' + count);
            var n = Math.min(count, 5);
            for (var i = 0; i < n; i++) {
                try {
                    var item = null;
                    try { item = adapter.getItem(i); } catch(e2) {}
                    if (!item) continue;
                    var itemCls = item.getClass().getName();
                    var fields = dumpOne(item);
                    var wxids = [];
                    for (var k in fields) {
                        if (isWxidLike(fields[k])) wxids.push(k + '=' + fields[k]);
                    }
                    if (wxids.length > 0) {
                        console.log(TAG + ' [' + i + '] ' + itemCls + ' => ' + wxids.join(' | '));
                    } else {
                        // 列出所有字段名
                        var fkeys = Object.keys(fields).join(',');
                        if (fkeys.length < 120) {
                            console.log(TAG + ' [' + i + '] ' + itemCls + ' fields=' + fkeys);
                        }
                    }
                } catch(e) {}
            }
        } catch(e) { console.log(TAG + ' scan err: ' + e); }
    }

    // ── RecyclerView.setAdapter ──
    try {
        var RecyclerView = Java.use('androidx.recyclerview.widget.RecyclerView');
        RecyclerView.setAdapter.implementation = function(adapter) {
            console.log(TAG + ' RecyclerView.setAdapter cls=' + (adapter ? adapter.getClass().getName() : 'null'));
            setTimeout(function() { scanAdapter(adapter); }, 500);
            return this.setAdapter(adapter);
        };
        console.log(TAG + ' RecyclerView.setAdapter hook ok');
    } catch(e) { console.log(TAG + ' RecyclerView.setAdapter fail: ' + e); }

    // ── ListView.setAdapter ──
    try {
        var ListView = Java.use('android.widget.ListView');
        ListView.setAdapter.implementation = function(adapter) {
            console.log(TAG + ' ListView.setAdapter cls=' + (adapter ? adapter.getClass().getName() : 'null'));
            setTimeout(function() { scanAdapter(adapter); }, 500);
            return this.setAdapter(adapter);
        };
        console.log(TAG + ' ListView.setAdapter hook ok');
    } catch(e) { console.log(TAG + ' ListView.setAdapter fail: ' + e); }

    // ── 立刻扫描已存在的 adapter ──
    console.log(TAG + ' 扫描当前已存在的 RecyclerView/ListView...');
    try {
        Java.choose('android.view.ViewGroup', {
            onMatch: function(vg) {
                var name = vg.getClass().getName();
                if (!/recycler|Recycler|list|List/.test(name)) return;
                for (var i = 0; i < vg.getChildCount(); i++) {
                    try {
                        var child = vg.getChildAt(i);
                        var cName = child.getClass().getName();
                        if (/recycler|Recycler/.test(cName)) {
                            try {
                                var adapter = child.getAdapter();
                                if (adapter) scanAdapter(adapter);
                            } catch(e2) {}
                        } else if (/list|List/.test(cName)) {
                            try {
                                var adapter = child.getAdapter();
                                if (adapter) scanAdapter(adapter);
                            } catch(e2) {}
                        }
                    } catch(e2) {}
                }
            },
            onComplete: function() { console.log(TAG + ' 扫描完成'); }
        });
    } catch(e) { console.log(TAG + ' Java.choose fail: ' + e); }

    // ── 兜底：hook notifyDataSetChanged ──
    try {
        // RecyclerView.Adapter 的 notifyDataSetChanged 是 public final，不能 hook
        // 用 BaseAdapter 的（ListView 用的）
    } catch(e) {}

    console.log(TAG + ' 已就绪。滚动列表或搜索好友触发 setAdapter。');
});
