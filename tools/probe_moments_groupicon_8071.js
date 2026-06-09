'use strict';
/**
 * probe_moments_groupicon_8071.js — 朋友圈条目"仅可见分组"图标定位
 * Date    : 2026-06-08
 * Target  : 微信 8.0.71
 * Goal    : 找到自己发的"仅 X 可见"朋友圈条目右下角图标的 View 类名 / 资源 ID / 父 ItemView 类
 *
 * 用法：
 *   adb shell pidof com.tencent.mm
 *   frida -U -p <PID> -l probe_moments_groupicon_8071.js
 *   进微信 → 发现 → 朋友圈 → 滑到自己发过"仅 X 可见 / 部分可见"的条目
 *   滑过 5-10 条 → 摁 Ctrl+C 退出，看输出
 *
 * 关键日志 tag：
 *   [MGI:item]   — 触发的 itemView 类
 *   [MGI:hit]    — 命中"仅/分组/私密/部分"文字的子 View
 *   [MGI:adapter] — onBindViewHolder 触发的 Adapter 类
 */

Java.perform(function () {

    var KEYWORDS = ['仅', '分组', '私密', '部分', '可见', '不给'];
    var seenItemCls = {};
    var seenHits = {};
    var bindCount = 0;

    function isKeyword(s) {
        if (!s) return false;
        s = String(s);
        for (var i = 0; i < KEYWORDS.length; i++) {
            if (s.indexOf(KEYWORDS[i]) !== -1) return KEYWORDS[i];
        }
        return null;
    }

    function safeGetText(view) {
        try {
            if (!view) return null;
            var TextView = Java.use('android.widget.TextView');
            if (TextView.class.isAssignableFrom(view.getClass())) {
                var tv = Java.cast(view, TextView);
                var t = tv.getText();
                if (t) return String(t);
            }
        } catch(e) {}
        try {
            var cd = view.getContentDescription();
            if (cd) return String(cd);
        } catch(e) {}
        return null;
    }

    function safeGetIdName(view) {
        try {
            var id = view.getId();
            if (id === -1 || id === 0) return null;
            var res = view.getResources();
            if (res) {
                try { return String(res.getResourceEntryName(id)); } catch(e) {}
            }
            return '0x' + id.toString(16);
        } catch(e) { return null; }
    }

    function classChain(view) {
        try {
            var arr = [];
            var c = view.getClass();
            while (c) {
                arr.push(c.getName());
                if (arr.length >= 4) break;
                c = c.getSuperclass();
                if (!c || c.getName() === 'android.view.View') {
                    if (c) arr.push(c.getName());
                    break;
                }
            }
            return arr.join(' > ');
        } catch(e) { return '?'; }
    }

    function walkView(view, depth, itemCls) {
        if (!view || depth > 8) return;
        try {
            var cls = view.getClass().getName();
            var text = safeGetText(view);
            var hit = isKeyword(text);
            if (hit) {
                var idName = safeGetIdName(view);
                var key = itemCls + '|' + cls + '|' + idName + '|' + hit;
                if (!seenHits[key]) {
                    seenHits[key] = true;
                    console.log('[MGI:hit] kw="' + hit + '" text="' + text.substring(0, 40)
                        + '" cls=' + cls
                        + ' id=' + (idName || 'none')
                        + ' depth=' + depth
                        + ' parents=' + classChain(view));
                }
            }

            var ViewGroup = Java.use('android.view.ViewGroup');
            if (ViewGroup.class.isAssignableFrom(view.getClass())) {
                var vg = Java.cast(view, ViewGroup);
                var n = vg.getChildCount();
                for (var i = 0; i < n; i++) {
                    walkView(vg.getChildAt(i), depth + 1, itemCls);
                }
            }
        } catch(e) {}
    }

    function tryHookAdapter(adapterClsName) {
        try {
            var A = Java.use(adapterClsName);
            if (!A.onBindViewHolder) return false;
            A.onBindViewHolder.overload('androidx.recyclerview.widget.RecyclerView$ViewHolder', 'int')
                .implementation = function(holder, pos) {
                    var ret = this.onBindViewHolder(holder, pos);
                    try {
                        var itemView = holder.itemView.value;
                        var itemCls = itemView.getClass().getName();
                        if (!seenItemCls[itemCls]) {
                            seenItemCls[itemCls] = true;
                            console.log('[MGI:item] cls=' + itemCls + ' adapter=' + adapterClsName);
                        }
                        bindCount++;
                        if (bindCount < 50) walkView(itemView, 0, itemCls);
                    } catch(e) {}
                    return ret;
                };
            console.log('[MGI:adapter] hooked ' + adapterClsName);
            return true;
        } catch(e) { return false; }
    }

    // 候选 1：找到包含 RecyclerView$Adapter 的 app classloader（tinker 下默认 classloader 找不到）
    function findAppClassLoader() {
        var picked = null;
        var loaders = Java.enumerateClassLoadersSync();
        for (var i = 0; i < loaders.length; i++) {
            try {
                var L = loaders[i];
                L.loadClass('androidx.recyclerview.widget.RecyclerView$Adapter');
                picked = L;
                break;
            } catch(e) {}
        }
        return picked;
    }
    try {
        var appCL = findAppClassLoader();
        if (appCL) {
            Java.classFactory.loader = appCL;
            console.log('[MGI:adapter] switched classFactory.loader to app loader');
            var Adapter = Java.use('androidx.recyclerview.widget.RecyclerView$Adapter');
            Adapter.onBindViewHolder.overload('androidx.recyclerview.widget.RecyclerView$ViewHolder', 'int')
                .implementation = function(holder, pos) {
                    this.onBindViewHolder(holder, pos);
                    try {
                        var itemView = holder.itemView.value;
                        var itemCls = itemView.getClass().getName();
                        if (itemCls.indexOf('sns') === -1
                            && itemCls.indexOf('TimeLine') === -1
                            && itemCls.indexOf('Moments') === -1
                            && itemCls.indexOf('plugin.sns') === -1) return;
                        if (!seenItemCls[itemCls]) {
                            seenItemCls[itemCls] = true;
                            console.log('[MGI:item] cls=' + itemCls + ' adapter=' + this.getClass().getName());
                        }
                        bindCount++;
                        if (bindCount < 100) walkView(itemView, 0, itemCls);
                    } catch(e) {}
                };
            console.log('[MGI:adapter] hooked RecyclerView$Adapter (filtered to sns/TimeLine/Moments)');
        } else {
            console.log('[MGI:adapter] no classloader found for RecyclerView$Adapter — skip');
        }
    } catch(e) {
        console.log('[MGI:adapter] RecyclerView$Adapter hook fail: ' + e);
    }

    // 候选 2：传统 BaseAdapter.getView（兜底）
    try {
        var BaseAdapter = Java.use('android.widget.BaseAdapter');
        BaseAdapter.getView.implementation = function(pos, convertView, parent) {
            var ret = this.getView(pos, convertView, parent);
            try {
                if (!ret) return ret;
                var itemCls = ret.getClass().getName();
                if (itemCls.indexOf('sns') === -1
                    && itemCls.indexOf('TimeLine') === -1
                    && itemCls.indexOf('Moments') === -1) return ret;
                if (!seenItemCls[itemCls]) {
                    seenItemCls[itemCls] = true;
                    console.log('[MGI:item] cls=' + itemCls + ' adapter=' + this.getClass().getName() + ' (BaseAdapter)');
                }
                bindCount++;
                if (bindCount < 50) walkView(ret, 0, itemCls);
            } catch(e) {}
            return ret;
        };
        console.log('[MGI:adapter] hooked BaseAdapter.getView (filtered)');
    } catch(e) {}

    console.log('[MGI] ready — 请进朋友圈滑到"仅 X 可见"条目，滑 5~10 条');
    console.log('[MGI] 看完 Ctrl+C，把所有 [MGI:hit] 与 [MGI:item] 行复制回来');
});
