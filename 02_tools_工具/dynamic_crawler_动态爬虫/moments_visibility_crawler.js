'use strict';
/**
 * moments_visibility_crawler.js v2 — 朋友圈「选择可见好友」界面 Hook 点发现
 * Version : 2.0 (修复 global / BaseAdapter / 立刻启动)
 * Date    : 2026-05-24
 *
 * 用法：
 *   进入朋友圈 → 发帖 → "谁可以看" → 选择好友界面
 *   frida -U -p <PID> -l moments_visibility_crawler.js
 *   立即开始爬取 30s，期间滚动列表/搜索好友
 *   30s 后自动输出报告
 */

Java.perform(function () {

    // =========================================================================
    // § 0  配置
    // =========================================================================

    var HIDDEN_WXIDS = ['wxid_lzd2va16jd1622'];
    var HIDDEN_SET = {};
    HIDDEN_WXIDS.forEach(function(w) { HIDDEN_SET[w] = true; });

    var CRAWL_MS = 30000;

    // =========================================================================
    // § 1  状态
    // =========================================================================

    var crawling = true;  // 立刻开始
    var candidates = {};
    var addAllSeen = {};
    var reportPrinted = false;

    // =========================================================================
    // § 2  工具函数
    // =========================================================================

    function isWxidLike(s) {
        if (typeof s !== 'string') return false;
        return /^(wxid_[A-Za-z0-9_]+|[A-Za-z0-9_]+@chatroom|[A-Za-z0-9_]+@app)$/.test(s);
    }

    function isUinLike(s) {
        return typeof s === 'string' && /^\d{5,12}$/.test(s);
    }

    function dumpFieldsFlat(obj) {
        var result = {};
        if (obj == null) return result;
        try {
            var cls = obj.getClass();
            var total = 0;
            while (cls != null && cls.getName() !== 'java.lang.Object') {
                var fields;
                try { fields = cls.getDeclaredFields(); } catch(e) { break; }
                for (var i = 0; i < fields.length; i++) {
                    if (total++ > 25) return result;
                    try {
                        fields[i].setAccessible(true);
                        var v = fields[i].get(obj);
                        if (v != null) {
                            var vs = String(v);
                            if (vs.length > 100) vs = vs.substring(0, 100);
                            result[fields[i].getName()] = vs;
                        }
                    } catch(e) {}
                }
                cls = cls.getSuperclass();
            }
        } catch(e) {}
        return result;
    }

    function analyze(fields) {
        var wxids = [], uins = [], hidden = [];
        for (var k in fields) {
            var v = fields[k];
            if (typeof v !== 'string') continue;
            if (isWxidLike(v)) {
                wxids.push(k + '=' + v);
                if (HIDDEN_SET[v]) hidden.push(k + '=' + v);
            } else if (isUinLike(v)) {
                uins.push(k + '=' + v);
            }
        }
        return { wxids: wxids, uins: uins, hidden: hidden };
    }

    // =========================================================================
    // § 3  ArrayList.addAll 宽探针
    // =========================================================================

    var ArrayList = Java.use('java.util.ArrayList');
    ArrayList.addAll.overload('java.util.Collection').implementation = function(coll) {
        var ret = this.addAll(coll);
        if (!crawling) return ret;

        try {
            if (!coll || coll.size() === 0) return ret;
            var it = coll.iterator();
            if (!it.hasNext()) return ret;
            var first = it.next();
            if (!first) return ret;
            var cls = first.getClass().getName();
            if (addAllSeen[cls]) return ret;
            addAllSeen[cls] = true;

            var fields = dumpFieldsFlat(first);
            var a = analyze(fields);

            console.log('[MV:addAll] cls=' + cls + ' sz=' + coll.size()
                + ' wxids=' + JSON.stringify(a.wxids)
                + (a.hidden.length ? ' *** HIDDEN x' + a.hidden.length : ''));

            // 即使没有 wxid 也列出字段名
            if (a.wxids.length === 0 && a.uins.length === 0) {
                var fkeys = Object.keys(fields).slice(0, 15).join(',');
                console.log('[MV:addAll]   fields=' + fkeys);
            }

            if (!candidates[cls]) candidates[cls] = { cls: cls, calls: 0, wxids: {}, uins: {}, hidden: 0, size: coll.size() };
            var c = candidates[cls];
            c.calls++;
            c.size = coll.size();
            a.wxids.forEach(function(w) { c.wxids[w] = true; });
            a.uins.forEach(function(u) { c.uins[u] = true; });
            if (a.hidden.length) c.hidden += a.hidden.length;
        } catch(e) {}
        return ret;
    };

    console.log('[MV] ArrayList.addAll hook ok');

    // =========================================================================
    // § 4  SelectContactUI 探针（确认界面）
    // =========================================================================

    try {
        var SelectContactUI = Java.use('com.tencent.mm.ui.contact.SelectContactUI');
        SelectContactUI.onCreate.implementation = function(savedInstanceState) {
            console.log('[MV] === SelectContactUI onCreate ===');
            try {
                var intent = this.getIntent();
                if (intent) {
                    var b = intent.getExtras();
                    if (b) {
                        var keys = b.keySet();
                        var kit = keys.iterator();
                        while (kit.hasNext()) {
                            var k = String(kit.next());
                            try {
                                var v = b.get(k);
                                console.log('[MV] intent: ' + k + ' = ' + String(v).substring(0, 80));
                            } catch(e2) {}
                        }
                    }
                }
            } catch(e) {}
            return this.onCreate(savedInstanceState);
        };
        console.log('[MV] SelectContactUI hook ok');
    } catch(e) {
        console.log('[MV] SelectContactUI hook fail: ' + e);
    }

    // =========================================================================
    // § 5  计时 + 报告
    // =========================================================================

    function printReport() {
        if (reportPrinted) return;
        reportPrinted = true;
        crawling = false;

        console.log('\n');
        console.log('===========================================================');
        console.log('  MomentsVisibilityCrawler v2  报告  (WeChat 8.0.71)');
        console.log('===========================================================');
        console.log('addAll 新类数: ' + Object.keys(addAllSeen).length);

        var sorted = [];
        for (var k in candidates) {
            var c = candidates[k];
            c.wxidCount = 0;
            for (var w in c.wxids) c.wxidCount++;
            c.uinCount = 0;
            for (var u in c.uins) c.uinCount++;
            c.score = c.hidden * 100 + c.wxidCount * 30 + c.uinCount * 10;
            sorted.push(c);
        }
        sorted.sort(function(a, b) { return b.score - a.score; });

        console.log('\n--- Top 候选 -------------------------------------------------');
        if (sorted.length === 0) {
            console.log('  !! 无候选 — 列表可能已预加载，滚动列表触发 addAll');
            console.log('  !! 尝试：返回上一页 → 重新进入"谁可以看"');
        }
        sorted.forEach(function(c, i) {
            if (c.score <= 0 && i > 5) return;
            console.log('#' + (i+1) + ' [' + c.score + '] ' + c.cls
                + '  calls=' + c.calls + ' size=' + c.size);
            var wl = Object.keys(c.wxids);
            if (wl.length) console.log('  wxids: ' + wl.join(', '));
            var ul = Object.keys(c.uins);
            if (ul.length) console.log('  uins: ' + ul.join(', '));
            if (c.hidden) console.log('  *** HIDDEN HIT x' + c.hidden + ' ***');
        });

        console.log('\n--- 所有 addAll 类 -------------------------------------------');
        Object.keys(addAllSeen).forEach(function(n) { console.log('  ' + n); });

        console.log('\n[MV] 完成。复制以上内容到 P 任务 result.md');
    }

    setTimeout(printReport, CRAWL_MS);
    console.log('[MV] v2 已启动，爬取 ' + CRAWL_MS + 'ms，请滚动列表/搜索好友...');

    // =========================================================================
    // § 6  REPL 手动接口 (使用 rpc.exports)
    // =========================================================================

    rpc.exports = {
        mvReport: printReport,
        mvStart: function() {
            crawling = true;
            reportPrinted = false;
            console.log('[MV] 手动重启爬取 ' + CRAWL_MS + 'ms');
            setTimeout(printReport, CRAWL_MS);
        }
    };

    console.log('[MV] REPL: rpc.exports.mvReport()  /  rpc.exports.mvStart()');
});
