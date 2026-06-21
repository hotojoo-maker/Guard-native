'use strict';
/**
 * probe_moments_groupicon_8071.js — M6a 阶段A：定位朋友圈"仅可见分组"图标 View
 * 底座：微信 8.0.71 / Android / frida spawn 模式（非交互，自动 dump）
 * 目标：找到"自己发的、仅X可见/部分可见/私密"那条朋友圈右下角图标的
 *       资源 id 名 + View 类名 + 父路径 + 尺寸，给 MomentsGroupIconFilter 用。
 *
 * 为什么不直搬竞品：8.0.66 锚点 hookSnsGroup() 只是开关 getter，真过滤在渲染层；
 *                   8.0.71 渲染类/图标 id 未知（CATFISH 也只留 TODO）→ 必须现场实测。
 *
 * 运行方式（终端操作员代跑，spawn + Tee）：
 *   frida -U -f com.tencent.mm --no-pause -l <本脚本> 2>&1 | Tee-Object <log>
 *   微信冷启动后：用户进朋友圈 → 滑到自己"仅X可见/部分可见/私密"那条 → 停屏 ~10s。
 *   脚本每 4s 自动 dump 一次当前页(仅 sns 页)，共 ~80s 窗口，无需在 REPL 输入。
 *
 * 设计遵守 H1–H5 轻探针铁律：
 *   - 只 hook Activity.onResume(低频)记录当前页，不碰任何高频渲染路径；
 *   - 定时快照单次遍历 decorView，非每帧；只在前台是 sns 页时才全量 dump；
 *   - 只打印 ImageView / TextView（带 id/文案/可见性/尺寸/路径），单次上限 300 行；
 *   - 命中可见性文案的 TextView 用 *** 高亮；窗口结束自动停。
 */
Java.perform(function () {

    var cur = null;

    try {
        var Activity = Java.use('android.app.Activity');
        Activity.onResume.implementation = function () {
            cur = this;
            try { console.log('[GI] onResume front=' + this.getClass().getName()); } catch (e) {}
            return this.onResume();
        };
        console.log('[GI] Activity.onResume hook ok');
    } catch (e) {
        console.log('[GI] onResume hook fail: ' + e);
    }

    var KEYS = ['仅', '分组', '私密', '部分', '可见', '谁可以看', '不给谁看'];

    var TextViewCls = Java.use('android.widget.TextView');
    var ImageViewCls = Java.use('android.widget.ImageView');
    var ViewGroupCls = Java.use('android.view.ViewGroup');

    function isInst(clsWrap, v) {
        try { return clsWrap.class.isInstance(v); } catch (e) { return false; }
    }

    function idName(v) {
        try {
            var id = v.getId();
            if (id === -1 || id === 0) return 'NO_ID';
            return v.getResources().getResourceEntryName(id);
        } catch (e) { return 'NO_ID'; }
    }

    function visName(v) {
        try {
            var vis = v.getVisibility();
            return vis === 0 ? 'VIS' : (vis === 4 ? 'INVIS' : 'GONE');
        } catch (e) { return '?'; }
    }

    var printed = 0;
    var MAX = 150;
    var hitCount = 0;
    var dumping = false;   // 防重入：上一次主线程 dump 没完不再排队

    var buf = [];

    function walk(v, depth, path) {
        if (v == null || printed > MAX) return;
        var cls = v.getClass().getName();

        if (isInst(TextViewCls, v)) {
            var t = '';
            try { t = String(v.getText()); } catch (e) {}
            var hit = false;
            for (var i = 0; i < KEYS.length; i++) { if (t.indexOf(KEYS[i]) >= 0) { hit = true; break; } }
            if (hit) hitCount++;
            buf.push((hit ? '*** ' : '    ')
                + 'TV d' + depth + ' id=' + idName(v) + ' ' + visName(v)
                + ' text="' + (t.length > 30 ? t.substring(0, 30) : t) + '"'
                + ' cls=' + cls + ' @' + path);
            printed++;
        } else if (isInst(ImageViewCls, v)) {
            var w = 0, h = 0;
            try { w = v.getWidth(); h = v.getHeight(); } catch (e) {}
            buf.push('    IV d' + depth + ' id=' + idName(v) + ' ' + visName(v)
                + ' wh=' + w + 'x' + h
                + ' cls=' + cls + ' @' + path);
            printed++;
        }

        if (isInst(ViewGroupCls, v)) {
            var vg = Java.cast(v, ViewGroupCls);
            var n = 0;
            try { n = vg.getChildCount(); } catch (e) { return; }
            var seg = (idName(v) !== 'NO_ID') ? idName(v) : v.getClass().getSimpleName();
            for (var c = 0; c < n; c++) {
                var child = null;
                try { child = vg.getChildAt(c); } catch (e) {}
                walk(child, depth + 1, path + '/' + seg);
            }
        }
    }

    // 必须在【主线程】遍历 View 树：Android View 非线程安全，子线程读会卡死/崩 App（实测已踩）。
    // buffer 后只 console.log 一次，最小化主线程占用；reentrancy 锁防排队堆积。
    function dumpNow(tag) {
        if (cur == null) return;
        if (dumping) return;
        dumping = true;
        Java.scheduleOnMainThread(function () {
            try {
                printed = 0; hitCount = 0; buf = [];
                var page = cur.getClass().getName();
                walk(cur.getWindow().getDecorView(), 0, 'root');
                console.log('\n========= [GI] DUMP ' + tag + ' page=' + page + ' =========\n'
                    + buf.join('\n')
                    + '\n========= [GI] DUMP END ' + tag + ' views=' + printed + ' visTextHits=' + hitCount
                    + (printed > MAX ? ' (达上限)' : '') + ' =========');
            } catch (e) {
                console.log('[GI] walk fail: ' + e);
            } finally {
                dumping = false;
            }
        });
    }

    // ---- 自动 dump 窗口（非交互，由 Python 驱动保活） ----
    var tick = 0;
    var MAX_TICK = 30;          // 30 × 4s ≈ 120s 窗口
    var timer = setInterval(function () {
        tick++;
        if (cur == null) { console.log('[GI] tick=' + tick + ' 还没捕获 Activity'); }
        else {
            var name = '';
            try { name = cur.getClass().getName(); } catch (e) {}
            var sns = name.toLowerCase().indexOf('sns') >= 0;
            if (sns) {
                dumpNow('tick=' + tick);
            } else {
                console.log('[GI] tick=' + tick + ' front=' + name + ' (非sns页,跳过dump)');
            }
        }
        if (tick >= MAX_TICK) {
            clearInterval(timer);
            console.log('\n[GI] ===== PROBE WINDOW DONE (' + MAX_TICK + ' ticks) ===== 可以收了');
        }
    }, 4000);

    // 手动兜底（如有交互 REPL）
    rpc.exports = { dump: function () { dumpNow('manual'); } };

    console.log('[GI] READY — 微信冷启动后进朋友圈，滑到自己"仅X可见"那条停屏，脚本每4s自动dump');
});
